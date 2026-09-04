# Tiqo local AI architecture

```text
User message
    │
    ▼
Active Planner model (local LiteRT-LM)
    │ manual tool call only
    ▼
PlannerToolProtocol ── rejects malformed/unknown fields and enums
    │
    ▼
PlannerTools ── validates dates, times, ranges, targets and destructive scope
    │
    ▼
PlannerRepository ── one source of truth
    │
    ├── Room / SQLite
    ├── recurrence expansion
    └── Android reminder scheduler
```

Room flows already drive Home and Calendar, so an AI-created or AI-updated item appears through the same path as a manually edited item. AI does not write UI state or SQL directly.

## Router

The active Planner model must emit exactly one of ten actions: `create_item`, `list_items`, `get_item`, `update_item`, `delete_item`, `set_completed`, `search_items`, `get_statistics`, `delegate_to_chat` or `request_clarification`.

- Planner action: validate, execute, persist a tool call/result record and render a result card.
- Ambiguous target: request clarification. Candidate IDs are supplied explicitly; the model may not invent an ID outside that set.
- General request: `delegate_to_chat`. If the optional chat model is absent, Tiqo explains that it is required and links to Model Manager.

Planner context contains the current local date, time, timezone, locale, selected UI date and at most 20 repository-derived candidate items. That candidate list is rebuilt from `PlannerRepository` for every request. Conversation history is language context only and is never a planner snapshot: read/search/statistics query the repository again, while get/update/delete/complete re-read the selected ID immediately in `PlannerTools`. A current tool result always wins over an earlier chat turn.

## Manual tool execution and safety

LiteRT-LM is configured with `automaticToolCalling = false`. `OpenApiTool.execute` deliberately fails if called by the runtime. Tiqo reads `Message.toolCalls`, parses JSON with an allow-list for every function, validates domain invariants, and only then calls `PlannerTools`.

Unknown tools, unknown arguments, invalid dates/times/enums, empty changes, missing targets, unsupported reminder offsets and targets outside the candidate set become normal user-facing validation results rather than crashes. Bulk delete and bulk reschedule are not exposed. A single resolved delete is reversible; recurring-series scope expansion remains a future explicit-confirmation feature.

Mutations return a `PlannerActionCard` with action, stable item ID, title/date/time and an opaque in-memory Undo token. Create, update, delete and complete/reopen all register inverse operations. Room repository saves automatically cancel and reschedule Android alarms, so edits and Undo update reminders through the same deterministic path.

## Model registry, selection and lifecycle

Planner and chat roles use separate `LiteRtLlmEngine` instances. Chat requests use a stable session ID, so the runtime `Conversation` and KV state survive ordinary screen navigation. Planner calls remain one-shot because their system context and repository candidates must be fresh. Stop calls LiteRT-LM `cancelProcess()` before cancelling the collection job and discards the interrupted runtime conversation; the next turn can rebuild it from the persisted current chat. “Nowa rozmowa” cancels inference, resets both engine conversations/KV state, clears the current Room conversation and clears Undo handles.

The manager maps `.litertlm` to LiteRT-LM and `.gguf` to llama.cpp. Only LiteRT-LM is packaged in the current APK, so GGUF is recognized but rejected as `RuntimeUnavailable` before copying; it is never sent to LiteRT-LM and no non-working activation control is exposed. The `LlamaCppEngine` boundary documents that unavailable build state until a pinned, tested JNI module is included. A Planner test supplies the real Tiqo tool schema but never invokes the executor; a Chat test uses a short text prompt.

File extension validation is only the first gate. `Engine.initialize()` remains the authoritative runtime compatibility check, while the planner tool test checks whether the checkpoint/template can emit a tool call. Details and the concrete GGUF blocker are in `MODEL_COMPATIBILITY.md` and `PHASE4_RUNTIME_HANDOFF.md`.

## Privacy and logging

Inference, planner data, chat history and tool execution are local. Runtime diagnostics use the `TiqoLiteRt` tag and record lifecycle event, failure class, backend and aggregate JVM/native memory, never prompt content. Native LiteRT logs remain available in logcat. No analytics, remote prompt logging or cloud LLM fallback is present.

## Future executive-assistant boundary

The domain can later add `goalId`, `projectId`, deadline, estimated duration, priority, dependencies and `FIXED`/`FLEXIBLE`/`PARKED` scheduling mode. A larger LLM may propose plans, but a deterministic scheduler—not an LLM—will place tasks using constraints and availability. Phase 3 intentionally does not implement that scheduler.
