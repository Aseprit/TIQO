# Tiqo model compatibility

## Supported now

- Runtime: `com.google.ai.edge.litertlm:litertlm-android:0.16.1`.
- Coroutine runtime: `kotlinx-coroutines-android:1.11.0` is pinned directly because the LiteRT-LM `Conversation.onDone` bytecode calls the interface-static `SendChannel.close$default` bridge (upstream issue #2812).
- Backend selected by Tiqo: CPU.
- Import artifact: one ready-to-run `.litertlm` file.
- Roles: Planner, Chat or both. Roles are user/catalog metadata; the on-device test verifies actual behavior.
- Storage: private `files/models`; imported model metadata and active choices persist across process restarts.

The extension identifies the container expected by the current runtime, but it is not a promise that every `.litertlm` created for another LiteRT-LM version, platform, backend or prompt template will initialize. Activation calls `Engine.initialize()` and fails without replacing the previously active model if initialization is unsuccessful.

Planner compatibility is stricter than Chat compatibility. A Planner model must support the conversation/tool API and emit valid calls for three isolated scenarios: create, delegate to chat and clarification. Model Manager uses the same native/Qwen XML/strict JSON adapter and `PlannerToolProtocol` as `PlannerAgent`, but never connects the test to `PlannerTools`, so it cannot mutate Room, reminders or planner state. Only a fully compatible result may be activated for the Planner role; partial models remain available for Chat.

Planner inference uses a request-specific 96-token output budget and a 30-second timeout. Timeout calls `stopGeneration()` and surfaces the concrete error. Chat keeps an independent 512-token budget and normal streaming.

## Pinned catalog

- Small / experimental — `litert-community/Qwen3-0.6B-int4` at `6aa2daf8aba4aa456797fb8040b36a3948bcfda7`, file `qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm`, 347251840 bytes, SHA-256 `2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139`. It is a Planner/Small Chat candidate, not a guaranteed Planner recommendation.
- Recommended Chat candidate — `litert-community/Qwen3-1.7B` at `d9b8a9126e5ac18591306eacd4311ba43b92421e`, file `Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm`, 977184032 bytes, SHA-256 `2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b`.
- Qwen 2.5 1.5B Q8 remains available as a larger Chat alternative; the catalog makes no untested claim that any candidate is best for Polish.

PlannerGemma is not shown in the downloadable catalog because Tiqo has no pinned, available compatible artifact for it.

Downloads reuse the Google AI Edge Gallery Range/append approach. Tiqo additionally validates `206 Content-Range` before appending, restarts safely when a server returns `200`, follows Hugging Face/Xet redirects, retries transient failures, preserves `.partial` on cancel/failure, and validates exact size/SHA-256 before atomic rename.

## GGUF status

`.gguf` is recognized as `ModelFormat.Gguf` / `ModelBackend.LlamaCpp`, but is intentionally reported as `RuntimeUnavailable` and is not copied or activated in this APK. This is not extension-only fake support.

The official [llama.cpp Android example](https://github.com/ggml-org/llama.cpp/tree/master/examples/llama.android) is currently a source/CMake project, not a versioned Maven/AAR dependency. Its library build currently specifies NDK 29, CMake 3.31.6 and minSdk 33; Tiqo targets minSdk 26. Integrating it correctly requires a pinned upstream revision, an owned JNI/module update policy, ABI/device testing, streaming/cancellation/KV lifecycle tests and license notices. `hokanosekai/llama.kt` was reviewed as a useful reference, but it also requires source vendoring/submodules and is not automatically added as a dependency.

The prepared `LlamaCppEngine` boundary fails explicitly with `NativeBackend` until that native module exists. The eventual implementation must use the GGUF `tokenizer.chat_template` through llama.cpp, keep tool parsing behind `PlannerToolProtocol`, use a reasonable bounded context, forward native diagnostics to logcat and never give a model direct DAO access.

## Not supported in this APK

- GGUF execution (format recognition is present; native runtime is not packaged).
- MediaPipe `.task` model bundles.
- raw Hugging Face repositories, Safetensors or Transformers checkpoints.
- gated downloads with an embedded Hugging Face token.
- arbitrary URL entry, on-device conversion or automatic license acceptance.
- GPU/NPU selection in the current Tiqo UI.

The recommended catalog may provide a download button only when it points to an exact, already-converted `.litertlm` artifact with known metadata. All other compatible models use Android's system file picker.
