# Third-party components

## llama.cpp

Source: https://github.com/ggml-org/llama.cpp

Pinned revision: `56381e407c0ccfb3a6f71e668a27a901001d22ce` (git submodule `vendor/llama.cpp`).
MIT license; full notice is preserved in the submodule and packaged in
`app/src/main/assets/licenses/llama.cpp-MIT.txt`. No upstream source modifications.
Tiqo compiles the CPU backend, including upstream Q1_0 support, and owns its small JNI adapter.

## ToolNeuron reference

Source: https://github.com/Siddhesh2377/ToolNeuron

Reviewed revision: `5df5f8eaa1f199d6920ebe5cdd30cdf0db37f290`.
MIT, Copyright (c) 2026 Siddhesh Sonar. Full license is packaged in
`app/src/main/assets/licenses/ToolNeuron-MIT.txt`.

Reference files: `app/src/main/java/com/dark/tool_neuron/service/inference/InferenceService.kt`
and `InferenceClient.kt`, the generation callback AIDL interfaces, and app Gradle dependencies.
The reference uses `libs/gguf_lib-release.aar`; its JNI implementation is not present as source
in this checkout. Tiqo does not copy or ship this opaque AAR or the rest of ToolNeuron.
Tiqo independently implements the relevant patterns: stop before teardown, serialize native
ownership, stream tokens with explicit completion/error handling, import GGUF into app storage,
and release model/context/sampler resources. ToolNeuron's server, plugins, speech, notifications,
RAG, downloads, and multi-process service architecture are not included.

## Test model

Bonsai 8B Q1_0: https://huggingface.co/prism-ml/Bonsai-8B-gguf
Model card identifies Apache-2.0. Weights are not shipped in the APK or source repository.
Users import their own local instruction/chat GGUF. Other models must be compatible with the
pinned llama.cpp architecture, quantization and supported chat-template implementation.

## OpenCL (Khronos / llama.cpp upstream)

OpenCL-Headers and OpenCL-ICD-Loader are pinned Git submodules from KhronosGroup.
Apache-2.0 licenses are preserved in their sources and packaged in assets/licenses.
The ARM64 APK statically links the upstream ICD loader and llama.cpp OpenCL backend,
with embedded upstream Adreno kernels (including Q1_0). It loads the phone's vendor
libOpenCL.so; no vendor binary is distributed. Android declares that library optional.
