# Tiqo model compatibility

## Supported now

- Runtime: `com.google.ai.edge.litertlm:litertlm-android:0.16.1`.
- Backend selected by Tiqo: CPU.
- Import artifact: one ready-to-run `.litertlm` file.
- Roles: Planner, Chat or both. Roles are user/catalog metadata; the on-device test verifies actual behavior.
- Storage: private `files/models`; imported model metadata and active choices persist across process restarts.

The extension identifies the container expected by the current runtime, but it is not a promise that every `.litertlm` created for another LiteRT-LM version, platform, backend or prompt template will initialize. Activation calls `Engine.initialize()` and fails without replacing the previously active model if initialization is unsuccessful.

Planner compatibility is stricter than Chat compatibility. A Planner model must support the conversation/tool API and emit a tool call using Tiqo's unchanged schema. The Model Manager test presents the real tool definitions without connecting them to `PlannerTools`, so the test cannot mutate Room, reminders or planner state. Real chat/planner requests still pass through `PlannerAgent`, `PlannerToolProtocol` and `PlannerTools`; a model never receives DAO or SQL access.

## Not supported

- GGUF and llama.cpp packages.
- MediaPipe `.task` model bundles.
- raw Hugging Face repositories, Safetensors or Transformers checkpoints.
- gated downloads with an embedded Hugging Face token.
- arbitrary URL entry, on-device conversion or automatic license acceptance.
- GPU/NPU selection in the current Tiqo UI.

The recommended catalog may provide a download button only when it points to an exact, already-converted `.litertlm` artifact with known metadata. All other compatible models use Android's system file picker.
