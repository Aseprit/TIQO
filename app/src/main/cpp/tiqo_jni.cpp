#include <jni.h>
#include "llama.h"
#include "ggml-backend.h"
#include <chrono>
#include <deque>
#include <cstdio>
#include <cstdlib>
#ifdef TIQO_OPENCL
#include <CL/cl.h>
#include <dlfcn.h>
#endif
#include <algorithm>
#include <atomic>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
using Clock = std::chrono::steady_clock;
double millis() { return std::chrono::duration<double, std::milli>(Clock::now().time_since_epoch()).count(); }
std::mutex log_mutex;
std::deque<std::string> log_queue;
std::string request_id;
size_t dropped = 0;
std::atomic<int> resolved_mmap{-1};
std::atomic<int> offloaded_layers{0};
std::string opencl_reason;
bool opencl_found = false;
std::string quote(const std::string &s) {
    std::string out = "\"";
    for (unsigned char c : s) {
        if (c == '"' || c == '\\') { out += '\\'; out += c; }
        else if (c >= 32) out += c;
        else out += ' ';
    }
    return out + "\"";
}
void diagnostic(const std::string &stage, std::string message = "") {
    for (auto &c : message) if (c == '\n' || c == '\r' || c == '\t') c = ' ';
    std::lock_guard<std::mutex> lock(log_mutex);
    if (log_queue.size() >= 128) { log_queue.pop_front(); ++dropped; }
    log_queue.push_back(request_id + "\t" + std::to_string(millis()) + "\t" + stage + "\t" + message.substr(0, 8192) + "\n");
}
void discover_opencl() {
#ifdef TIQO_OPENCL
    diagnostic("OPENCL_LOADER", "Khronos ICD=link-time only, excluded from APK; runtime=vendor standard OpenCL API (no ICD requirement)");
    Dl_info info = {};
    if (dladdr(reinterpret_cast<void *>(&clGetPlatformIDs), &info))
        diagnostic("OPENCL_RUNTIME", std::string("clGetPlatformIDs resolved from=") + (info.dli_fname ? info.dli_fname : "unknown"));
    for (const char *path : {"libOpenCL.so", "/vendor/lib64/libOpenCL.so", "libOpenCL_adreno.so", "/vendor/lib64/libOpenCL_adreno.so"}) {
        void *lib = dlopen(path, RTLD_NOW | RTLD_LOCAL);
        if (lib) { diagnostic("OPENCL_LIBRARY", std::string(path) + " loadable (not a GPU success criterion)"); dlclose(lib); }
        else { const char *e = dlerror(); diagnostic("OPENCL_LIBRARY", std::string(path) + " unavailable/inaccessible: " + (e ? e : "unknown")); }
    }
    cl_uint platforms = 0;
    const cl_int rc = clGetPlatformIDs(0, nullptr, &platforms);
    diagnostic("OPENCL_PLATFORMS", "vendor clGetPlatformIDs rc=" + std::to_string(rc) + " count=" + std::to_string(platforms));
    opencl_found = rc == CL_SUCCESS && platforms > 0;
    if (!opencl_found) { opencl_reason = "Vendor clGetPlatformIDs rc=" + std::to_string(rc) + " platforms=" + std::to_string(platforms); return; }
    std::vector<cl_platform_id> ids(platforms);
    const cl_int list_rc = clGetPlatformIDs(platforms, ids.data(), nullptr);
    if (list_rc != CL_SUCCESS) { opencl_found = false; opencl_reason = "Vendor platform enumeration rc=" + std::to_string(list_rc); return; }
    cl_uint total_devices = 0;
    for (auto platform : ids) {
        char platform_name[1024] = {};
        const cl_int platform_name_rc = clGetPlatformInfo(platform, CL_PLATFORM_NAME, sizeof(platform_name), platform_name, nullptr);
        diagnostic("OPENCL_PLATFORM", "name_rc=" + std::to_string(platform_name_rc) + " name=" + platform_name);
        cl_uint count = 0;
        const cl_int device_rc = clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, 0, nullptr, &count);
        diagnostic("OPENCL_DEVICES", "clGetDeviceIDs rc=" + std::to_string(device_rc) + " count=" + std::to_string(count));
        if (device_rc != CL_SUCCESS || count == 0) continue;
        std::vector<cl_device_id> devices(count);
        const cl_int devices_rc = clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, count, devices.data(), nullptr);
        if (devices_rc != CL_SUCCESS) { diagnostic("OPENCL_DEVICES", "enumeration rc=" + std::to_string(devices_rc)); continue; }
        total_devices += count;
        for (auto device : devices) {
            char name[1024] = {};
            const cl_int name_rc = clGetDeviceInfo(device, CL_DEVICE_NAME, sizeof(name), name, nullptr);
            diagnostic("OPENCL_DEVICE", "name_rc=" + std::to_string(name_rc) + " name=" + name);
        }
    }
    diagnostic("OPENCL_ENUMERATION", "platforms=" + std::to_string(platforms) + " devices=" + std::to_string(total_devices));
    if (total_devices == 0) { opencl_found = false; opencl_reason = "Vendor OpenCL platforms found but no devices enumerated; see OPENCL_DEVICES rc"; }
#else
    opencl_reason = "OpenCL not compiled for this ABI (arm64-v8a required)";
#endif
}
struct Session {
    llama_model *model = nullptr;
    llama_context *context = nullptr;
    llama_sampler *sampler = nullptr;
    int generated = 0;
    double request_start = 0;
    double generation_start = 0;
    std::atomic<bool> cancelled{false};
    ~Session() {
        if (sampler) llama_sampler_free(sampler);
        if (context) llama_free(context);
        if (model) llama_model_free(model);
    }
};
Session &session(jlong h) {
    if (!h) throw std::runtime_error("Model is not loaded");
    return *reinterpret_cast<Session *>(h);
}
std::string utf(JNIEnv *env, jstring value) {
    const char *p = env->GetStringUTFChars(value, nullptr);
    std::string out(p);
    env->ReleaseStringUTFChars(value, p);
    return out;
}
std::string bytes(JNIEnv *env, jbyteArray value) {
    std::string out(env->GetArrayLength(value), '\0');
    env->GetByteArrayRegion(value, 0, out.size(), reinterpret_cast<jbyte *>(out.data()));
    return out;
}
void fail(JNIEnv *env, const std::exception &e) {
    diagnostic(std::string(e.what()).find("CANCELLED") == 0 ? "NATIVE_CANCELLED" : "NATIVE_ERROR", e.what());
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), e.what());
}
void reset(Session &s) {
    if (s.sampler) { llama_sampler_free(s.sampler); s.sampler = nullptr; }
    if (s.context) llama_memory_clear(llama_get_memory(s.context), true);
    s.generated = 0;
}

std::string format_chat(Session &s, const std::string &sys, const std::string &user) {
    llama_chat_message messages[] = {{"system", sys.c_str()}, {"user", user.c_str()}};
    const char *tmpl = llama_model_chat_template(s.model, nullptr);
    if (!tmpl) throw std::runtime_error("GGUF has no chat template; use an instruction/chat GGUF");
    std::vector<char> buffer((sys.size() + user.size()) * 2 + 4096);
    int n = llama_chat_apply_template(tmpl, messages, 2, true, buffer.data(), buffer.size());
    if (n < 0) throw std::runtime_error("Unsupported GGUF chat template");
    if (n > static_cast<int>(buffer.size())) {
        buffer.resize(n + 1);
        n = llama_chat_apply_template(tmpl, messages, 2, true, buffer.data(), buffer.size());
    }
    if (n < 0 || n > static_cast<int>(buffer.size())) throw std::runtime_error("Chat template formatting failed");
    std::string result(buffer.data(), n);
    const std::string assistant = "<|im_start|>assistant\n";
    // The built-in ChatML formatter omits this static suffix declared by Bonsai's GGUF.
    // Match the template, not a model filename; other templates retain their own prefix.
    if (std::string(tmpl).find("<|im_start|>assistant\\n<think>\\n\\n</think>\\n\\n") != std::string::npos &&
        result.size() >= assistant.size() &&
        result.compare(result.size() - assistant.size(), assistant.size(), assistant) == 0) {
        result += "<think>\n\n</think>\n\n";
    }
    return result;
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_aseprit_tiqo_LlamaCppEngine_nativeLoad(JNIEnv *env, jobject, jstring path, jint ctx, jint threads) {
    try {
        const double started = millis();
        resolved_mmap.store(-1);
        offloaded_layers.store(0);
        static std::once_flag initialized;
        std::call_once(initialized, [] {
            llama_log_set([](ggml_log_level level, const char *text, void *) {
                const std::string line(text);
                const auto pos = line.find("offloaded ");
                int layers = 0, total = 0;
                if (pos != std::string::npos && std::sscanf(line.c_str() + pos, "offloaded %d/%d", &layers, &total) == 2) offloaded_layers.store(layers);
                if (line.find("load_mode = mmap") != std::string::npos) resolved_mmap.store(1);
                else if (line.find("load_mode = ") != std::string::npos) resolved_mmap.store(0);
                diagnostic("LLAMA", "level=" + std::to_string(level) + " " + text);
            }, nullptr);
            discover_opencl();
            llama_backend_init();
        });
        auto s = std::make_unique<Session>();
        auto mp = llama_model_default_params();
        mp.n_gpu_layers = 0;
        ggml_backend_dev_t gpu = nullptr;
        for (size_t i = 0; i < ggml_backend_reg_count(); ++i)
            diagnostic("BACKEND_DETECTED", ggml_backend_reg_name(ggml_backend_reg_get(i)));
        for (size_t i = 0; i < ggml_backend_dev_count(); ++i) {
            const auto d = ggml_backend_dev_get(i);
            const std::string backend = ggml_backend_reg_name(ggml_backend_dev_backend_reg(d));
            const std::string name = ggml_backend_dev_description(d);
            diagnostic("DEVICE_DETECTED", backend + " name=" + ggml_backend_dev_name(d) + " description=" + name + " type=" + std::to_string(ggml_backend_dev_type(d)));
            if (opencl_found && backend == "OpenCL" && ggml_backend_dev_type(d) == GGML_BACKEND_DEVICE_TYPE_GPU && name.find("Adreno") != std::string::npos) gpu = d;
        }
        if (!gpu && opencl_reason.empty()) opencl_reason = "OpenCL runtime found but upstream registered no Adreno GPU device";
        ggml_backend_dev_t selected_devices[] = {gpu, nullptr};
        if (gpu) { mp.devices = selected_devices; mp.n_gpu_layers = -1; }
        const std::string backend_used = gpu ? "OpenCL" : "CPU";
        const auto device = gpu ? gpu : ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_CPU);
        const std::string fallback = gpu ? "" : opencl_reason;
        diagnostic("BACKEND_SELECTION", "requested=OpenCL selected=" + backend_used + " n_gpu_layers=" + std::to_string(mp.n_gpu_layers) + " fallbackReason=" + fallback);
        diagnostic("LOAD_MODE", std::string("requested=") + llama_load_mode_name(mp.load_mode));
        diagnostic("CONFIG", "{\"backend\":" + quote(backend_used) + ",\"openclFound\":" + (opencl_found ? "true" : "false") + ",\"fallbackReason\":" + quote(fallback) + ",\"n_gpu_layers\":" + std::to_string(mp.n_gpu_layers) + ",\"contextRequested\":" + std::to_string(ctx) +
            ",\"threads\":" + std::to_string(threads) + ",\"batch\":256,\"ubatch\":256,\"mmap\":" + (resolved_mmap.load() < 0 ? "null" : (resolved_mmap.load() ? "true" : "false")) +
            ",\"mlock\":" + ((mp.load_mode == LLAMA_LOAD_MODE_MLOCK || mp.load_mode == LLAMA_LOAD_MODE_MMAP_MLOCK) ? "true" : "false") + ",\"outputLimit\":1024,\"sampler\":\"grammar+greedy\"}");
        s->model = llama_model_load_from_file(utf(env, path).c_str(), mp);
        if (!s->model) throw std::runtime_error(backend_used + " model load failed; no automatic CPU retry. See LLAMA diagnostics.");
        auto cp = llama_context_default_params();
        cp.n_ctx = ctx;
        cp.n_batch = 256;
        cp.n_ubatch = 256;
        cp.n_threads = threads;
        cp.n_threads_batch = threads;
        cp.abort_callback = [](void *data) { return static_cast<Session *>(data)->cancelled.load(); };
        cp.abort_callback_data = s.get();
        s->context = llama_init_from_model(s->model, cp);
        if (!s->context) throw std::runtime_error(backend_used + " context initialization failed; no automatic CPU retry. See LLAMA diagnostics.");
        auto meta = [&](const char *key) { char value[1024] = {}; llama_model_meta_val_str(s->model, key, value, sizeof(value)); return quote(value); };
        char description[1024] = {};
        llama_model_desc(s->model, description, sizeof(description));
        diagnostic("MODEL", "{\"description\":" + quote(description) + ",\"architecture\":" + meta("general.architecture") +
            ",\"fileType\":" + meta("general.file_type") + ",\"quantizationVersion\":" + meta("general.quantization_version") +
            ",\"name\":" + meta("general.name") + ",\"trainingContext\":" + std::to_string(llama_model_n_ctx_train(s->model)) +
            ",\"loadMs\":" + std::to_string(millis() - started) + "}");
        if (gpu && offloaded_layers.load() == 0) throw std::runtime_error("OpenCL selected but zero layers offloaded; refusing silent CPU execution");
        diagnostic("CONFIG", "{\"backend\":" + quote(backend_used) + ",\"device\":" + quote(device ? ggml_backend_dev_description(device) : "unknown") +
            ",\"openclFound\":" + (opencl_found ? "true" : "false") + ",\"fallbackReason\":" + quote(fallback) + ",\"offloadedLayers\":" + std::to_string(offloaded_layers.load()) +
            ",\"n_gpu_layers\":" + std::to_string(mp.n_gpu_layers) + ",\"contextRequested\":" + std::to_string(ctx) + ",\"contextActual\":" + std::to_string(llama_n_ctx(s->context)) +
            ",\"threads\":" + std::to_string(cp.n_threads) + ",\"threadsBatch\":" + std::to_string(cp.n_threads_batch) +
            ",\"batch\":" + std::to_string(llama_n_batch(s->context)) + ",\"ubatch\":" + std::to_string(llama_n_ubatch(s->context)) +
            ",\"mmap\":" + (resolved_mmap.load() < 0 ? "null" : (resolved_mmap.load() ? "true" : "false")) + ",\"mlock\":" + ((mp.load_mode == LLAMA_LOAD_MODE_MLOCK || mp.load_mode == LLAMA_LOAD_MODE_MMAP_MLOCK) ? "true" : "false") +
            ",\"flashAttention\":" + std::to_string(cp.flash_attn_type) + ",\"cacheTypeK\":" + std::to_string(cp.type_k) + ",\"cacheTypeV\":" + std::to_string(cp.type_v) +
            ",\"outputLimit\":1024,\"sampler\":\"grammar+greedy\"}");
        diagnostic("BACKEND", "used=" + backend_used + " device=" + (device ? ggml_backend_dev_description(device) : "unknown") + " offloaded=" + std::to_string(offloaded_layers.load()) + " system=" + llama_print_system_info());
        diagnostic("CONTEXT", "actual=" + std::to_string(llama_n_ctx(s->context)) + " batch=" + std::to_string(llama_n_batch(s->context)) +
            " ubatch=" + std::to_string(llama_n_ubatch(s->context)) + " threads=" + std::to_string(cp.n_threads) + " threads_batch=" + std::to_string(cp.n_threads_batch) +
            " flash_attn=" + std::to_string(cp.flash_attn_type) + " type_k=" + std::to_string(cp.type_k) + " type_v=" + std::to_string(cp.type_v));
        diagnostic("LOAD_DONE", "ms=" + std::to_string(millis() - started));
        return reinterpret_cast<jlong>(s.release());
    } catch (const std::exception &e) { fail(env, e); return 0; }
}

extern "C" JNIEXPORT void JNICALL
Java_com_aseprit_tiqo_LlamaCppEngine_nativeBegin(JNIEnv *env, jobject, jlong h, jbyteArray system, jbyteArray prompt, jstring grammar) {
    try {
        auto &s = session(h);
        reset(s);
        s.request_start = millis();
        if (s.cancelled.load()) throw std::runtime_error("CANCELLED before prompt");
        auto sys = bytes(env, system), user = bytes(env, prompt);
        const auto formatted = format_chat(s, sys, user);
        const int n = formatted.size();
        diagnostic("FORMATTED_PROMPT", "bytes=" + std::to_string(n));
        diagnostic("TOKENIZING");
        const auto *vocab = llama_model_get_vocab(s.model);
        int count = -llama_tokenize(vocab, formatted.data(), n, nullptr, 0, true, true);
        if (count <= 0 || count + 1024 > static_cast<int>(llama_n_ctx(s.context)))
            throw std::runtime_error("Snapshot exceeds model context. Ask about a shorter date range.");
        std::vector<llama_token> tokens(count);
        count = llama_tokenize(vocab, formatted.data(), n, tokens.data(), count, true, true);
        if (count < 0) throw std::runtime_error("Tokenization failed");
        diagnostic("TOKENIZED", "bytes=" + std::to_string(n) + " tokens=" + std::to_string(count));
        auto g = utf(env, grammar);
        auto *constraint = llama_sampler_init_grammar(vocab, g.c_str(), "root");
        if (!constraint) throw std::runtime_error("Cannot initialize output grammar");
        s.sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
        llama_sampler_chain_add(s.sampler, constraint);
        llama_sampler_chain_add(s.sampler, llama_sampler_init_greedy());
        const double eval_start = millis();
        diagnostic("EVALUATING", "0/" + std::to_string(count));
        for (int offset = 0; offset < count; offset += 256) {
            if (s.cancelled.load()) throw std::runtime_error("CANCELLED during prompt evaluation");
            auto batch = llama_batch_get_one(tokens.data() + offset, std::min(256, count - offset));
            const int rc = llama_decode(s.context, batch);
            diagnostic("DECODE", "phase=prompt offset=" + std::to_string(offset) + " rc=" + std::to_string(rc) + " cancelled=" + std::to_string(s.cancelled.load()));
            if (s.cancelled.load()) throw std::runtime_error("CANCELLED during prompt evaluation");
            if (rc != 0) throw std::runtime_error("Prompt evaluation failed rc=" + std::to_string(rc));
            diagnostic("EVALUATING", std::to_string(std::min(offset + 256, count)) + "/" + std::to_string(count));
        }
        const double eval_ms = millis() - eval_start;
        diagnostic("PROMPT_EVAL_DONE", "ms=" + std::to_string(eval_ms) + " tokens=" + std::to_string(count) + " tok_s=" + std::to_string(count * 1000.0 / std::max(1.0, eval_ms)));
        s.generation_start = millis();
        diagnostic("GENERATING", "0 tokens");
    } catch (const std::exception &e) { fail(env, e); }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_aseprit_tiqo_LlamaCppEngine_nativeNext(JNIEnv *env, jobject, jlong h) {
    try {
        auto &s = session(h);
        if (s.cancelled.load()) throw std::runtime_error("CANCELLED during generation");
        if (!s.sampler) throw std::runtime_error("Generation not initialized");
        if (s.generated >= 1024) throw std::runtime_error("Response token limit reached; no proposal was applied");
        auto token = llama_sampler_sample(s.sampler, s.context, -1);
        const auto *vocab = llama_model_get_vocab(s.model);
        if (llama_vocab_is_eog(vocab, token)) {
            diagnostic("GENERATION_DONE", "tokens=" + std::to_string(s.generated) + " ms=" + std::to_string(millis() - s.generation_start) +
                " tok_s=" + std::to_string(s.generated * 1000.0 / std::max(1.0, millis() - s.generation_start)));
            return nullptr;
        }
        std::vector<char> piece(256);
        int n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
        if (n < 0) { piece.resize(-n); n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false); }
        if (n < 0) throw std::runtime_error("Token decoding failed");
        auto batch = llama_batch_get_one(&token, 1);
        const int rc = llama_decode(s.context, batch);
        if (s.cancelled.load()) throw std::runtime_error("CANCELLED during generation");
        if (rc != 0) throw std::runtime_error("Token evaluation failed rc=" + std::to_string(rc));
        ++s.generated;
        if (s.generated == 1) diagnostic("FIRST_TOKEN", "native_ttft_ms=" + std::to_string(millis() - s.request_start));
        if (s.generated % 16 == 0) diagnostic("GENERATION_PROGRESS", "tokens=" + std::to_string(s.generated) + " tok_s=" + std::to_string(s.generated * 1000.0 / std::max(1.0, millis() - s.generation_start)));
        auto out = env->NewByteArray(n);
        env->SetByteArrayRegion(out, 0, n, reinterpret_cast<const jbyte *>(piece.data()));
        return out;
    } catch (const std::exception &e) { fail(env, e); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_com_aseprit_tiqo_LlamaCppEngine_nativeReset(JNIEnv *env, jobject, jlong h) {
    try { auto &s = session(h); reset(s); s.cancelled.store(false); } catch (const std::exception &e) { fail(env, e); }
}
extern "C" JNIEXPORT void JNICALL
Java_com_aseprit_tiqo_LlamaCppEngine_nativeFree(JNIEnv *, jobject, jlong h) {
    delete reinterpret_cast<Session *>(h);
}

extern "C" JNIEXPORT void JNICALL
Java_com_aseprit_tiqo_LlamaCppEngine_nativePrepare(JNIEnv *, jobject, jlong h) {
    if (h) reinterpret_cast<Session *>(h)->cancelled.store(false);
}

extern "C" JNIEXPORT void JNICALL
Java_com_aseprit_tiqo_LlamaCppEngine_nativeCancel(JNIEnv *, jobject, jlong h) {
    if (h) reinterpret_cast<Session *>(h)->cancelled.store(true);
    diagnostic("CANCEL_SIGNAL", "native abort flag set");
}

extern "C" JNIEXPORT void JNICALL
Java_com_aseprit_tiqo_LlamaCppEngine_nativeDiagnosticRequest(JNIEnv *env, jobject, jstring id) {
    const auto value = utf(env, id);
    std::lock_guard<std::mutex> lock(log_mutex);
    request_id = value;
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_aseprit_tiqo_LlamaCppEngine_nativeDiagnosticDrain(JNIEnv *env, jobject) {
    std::string out;
    {
        std::lock_guard<std::mutex> lock(log_mutex);
        if (dropped) out += request_id + "\t" + std::to_string(millis()) + "\tDROPPED\t" + std::to_string(dropped) + " native log records\n";
        dropped = 0;
        for (const auto &line : log_queue) out += line;
        log_queue.clear();
    }
    auto bytes = env->NewByteArray(out.size());
    env->SetByteArrayRegion(bytes, 0, out.size(), reinterpret_cast<const jbyte *>(out.data()));
    return bytes;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_aseprit_tiqo_LlamaCppEngine_nativeFormat(JNIEnv *env, jobject, jlong h, jbyteArray system, jbyteArray prompt) {
    try {
        const auto result = format_chat(session(h), bytes(env, system), bytes(env, prompt));
        auto out = env->NewByteArray(result.size());
        env->SetByteArrayRegion(out, 0, result.size(), reinterpret_cast<const jbyte *>(result.data()));
        return out;
    } catch (const std::exception &e) { fail(env, e); return nullptr; }
}
