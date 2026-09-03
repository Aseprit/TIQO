# Tiqo local AI architecture

```text
User message
    │
    ▼
PlannerGemma (local LiteRT-LM)
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

PlannerGemma must emit exactly one of ten actions: `create_item`, `list_items`, `get_item`, `update_item`, `delete_item`, `set_completed`, `search_items`, `get_statistics`, `delegate_to_chat` or `request_clarification`.

- Planner action: validate, execute, persist a tool call/result record and render a result card.
- Ambiguous target: request clarification. Candidate IDs are supplied explicitly; the model may not invent an ID outside that set.
- General request: `delegate_to_chat`. If the optional chat model is absent, Tiqo explains that it is required and links to Model Manager.

Planner context contains the current local date, time, timezone, locale, selected UI date and at most 20 repository-derived candidate items. The model never supplies the current date from its weights. Conversation history is stored as separate Room rows; PlannerGemma receives minimal context, while the chat engine receives only recent non-tool turns.

## Manual tool execution and safety

LiteRT-LM is configured with `automaticToolCalling = false`. `OpenApiTool.execute` deliberately fails if called by the runtime. Tiqo reads `Message.toolCalls`, parses JSON with an allow-list for every function, validates domain invariants, and only then calls `PlannerTools`.

Unknown tools, unknown arguments, invalid dates/times/enums, empty changes, missing targets, unsupported reminder offsets and targets outside the candidate set become normal user-facing validation results rather than crashes. Bulk delete and bulk reschedule are not exposed. A single resolved delete is reversible; recurring-series scope expansion remains a future explicit-confirmation feature.

Mutations return a `PlannerActionCard` with action, stable item ID, title/date/time and an opaque in-memory Undo token. Create, update, delete and complete/reopen all register inverse operations. Room repository saves automatically cancel and reschedule Android alarms, so edits and Undo update reminders through the same deterministic path.

## Models and lifecycle

Planner and chat roles use separate `LiteRtLlmEngine` instances. PlannerGemma is asynchronously warmed when the app enters foreground and remains loaded for five minutes after backgrounding. The larger chat model unloads after one minute. Android memory pressure unloads both. Stop calls LiteRT-LM `cancelProcess()` so generation is actually cancelled.

The Model Manager supports manual import for gated PlannerGemma and real public download for compatible chat bundles, including progress, cancellation, free-space check, exact size, SHA-256 verification, load, unload and delete. No token or cloud fallback is embedded.

## Privacy and logging

Inference, planner data, chat history and tool execution are local. Production logging does not record prompt content. Debug builds log only selected tool, validation outcome and duration. No analytics, remote prompt logging or cloud LLM fallback is present.

## Future executive-assistant boundary

The domain can later add `goalId`, `projectId`, deadline, estimated duration, priority, dependencies and `FIXED`/`FLEXIBLE`/`PARKED` scheduling mode. A larger LLM may propose plans, but a deterministic scheduler—not an LLM—will place tasks using constraints and availability. Phase 3 intentionally does not implement that scheduler.
