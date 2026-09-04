# Tiqo model compatibility

## Supported now

- Runtime: `com.google.ai.edge.litertlm:litertlm-android:0.16.1`.
- Coroutine runtime: `kotlinx-coroutines-android:1.11.0` is pinned directly because the LiteRT-LM `Conversation.onDone` bytecode calls the interface-static `SendChannel.close$default` bridge (upstream issue #2812).
- Backend selected by Tiqo: CPU.
- Import artifact: one ready-to-run `.litertlm` file.
- Roles: Planner, Chat or both. Roles are user/catalog metadata; the on-device test verifies actual behavior.
- Storage: private `files/models`; imported model metadata and active choices persist across process restarts.

The extension identifies the container expected by the current runtime, but it is not a promise that every `.litertlm` created for another LiteRT-LM version, platform, backend or prompt template will initialize. Activation calls `Engine.initialize()` and fails without replacing the previously active model if initialization is unsuccessful.

Planner compatibility is stricter than Chat compatibility. A Planner model must support the conversation/tool API and emit a tool call using Tiqo's unchanged schema. The Model Manager test presents the real tool definitions without connecting them to `PlannerTools`, so the test cannot mutate Room, reminders or planner state. Real chat/planner requests still pass through `PlannerAgent`, `PlannerToolProtocol` and `PlannerTools`; a model never receives DAO or SQL access.

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
