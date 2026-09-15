# Tiqo — minimal local foundation

Android 8+ / Kotlin / Compose / Room / llama.cpp JNI. This version implements only Tasks,
a day screen and a local advisor chat with explicitly approved proposals.

## Build

```sh
git clone --recurse-submodules https://github.com/Aseprit/Tiqo.git
cd Tiqo
git submodule update --init --recursive
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Use JDK 17, Android SDK 35, NDK 29.0.14206865 and CMake 3.31.6. Set `sdk.dir` in
`local.properties` or `ANDROID_HOME`. On Windows use `gradlew.bat` and Git long-path
support (`git -c core.longpaths=true submodule update --init --recursive`). ARM64 and
x86_64 JNI libraries are compiled from the pinned llama.cpp submodule. The initial
native build takes longer than subsequent builds. No model download happens in the app.

## Use

1. In **Dzień**, select a date (ISO or previous/next day). Add, edit, delete or check Tasks.
   Manually created Tasks are locked. Changes are persisted in Room immediately.
2. In **Chat AI**, choose **Importuj GGUF** and select a local instruction/chat model.
   The app checks GGUF magic and atomically imports it into private persistent storage.
   Bonsai 8B Q1_0 is the preferred test model:
   https://huggingface.co/prism-ml/Bonsai-8B-gguf (file `Bonsai-8B-Q1_0.gguf`).
3. After a process restart choose **Załaduj zapisany**. **Zwolnij model** releases RAM;
   the imported file stays on disk. Stop and app backgrounding cancel generation.
4. Ask `co mam jutro?`, `co mam w tym tygodniu?`, or request a Task change.
   Advisor text streams as it is generated. Actions appear only after complete JSON
   has passed validation. Review the old/new values and approve or reject.

## Data and authorization

`TaskDatabase` uses the existing `tiqo.db` filename, schema version 3 (migrates both the upstream v1 and the previously tested local v2). Tasks have exactly
id, title, date (epoch day), completed, locked and createdAt (epoch milliseconds).
Room is the only source of planner state. Home observes a Room Flow; every chat request
queries Room again through `ContextBuilder`. Chat history is display-only and is never
used as planner memory. It survives rotation in the ViewModel, not process death.

The context resolver supports Polish/English today, tomorrow, Polish day after tomorrow,
yesterday, current/next week and ISO dates. Ambiguous queries receive a bounded seven-day
snapshot with explicit dates. It does not silently imply complete knowledge outside that
range. More than 80 Tasks or a prompt exceeding the 4096-token native context is rejected
with an instruction to narrow the query. Cross-date move requests include source/target
ranges. Arbitrary natural-language dates and pronoun resolution are not implemented.

Flow: fresh Room snapshot -> one constrained JSON generation -> semantic validation ->
proposal card -> explicit approval -> Kotlin transaction -> Room. No agent loop, direct
model writes, tool side effects or cloud. Grammar constrains syntax; Kotlin validates IDs,
action shapes, dates, title bounds and duplicate targets. Approval rereads the full queried
range inside the transaction and refuses stale proposals before any write. AI-created
Tasks have locked=false. A locked Task can be changed only by a manual edit or explicit
approval of the displayed proposal; approval preserves the lock.

## Existing repository audit

Starting revision: d1f2c412ae218542860b576c00c8952967cef7bb.
Retained: Android application identity and SDK baseline, Gradle wrapper/version catalog,
Compose/Room infrastructure, launcher resources, database filename and migration source
schema. Replaced: multi-kind planner domain, automatic tool execution and the unavailable
GGUF placeholder. The new application source set is `app/src/foundation/java`; tests are
`app/src/foundationTest/java`. Old `src/main/java` and `src/test/java` files are retained
for recoverable comparison and excluded from compilation. They are not shipped behavior.
The old AI training/benchmark material is also historical, not part of the APK.

Migration copies existing one-off TASK/NONE rows with original IDs, titles, dates,
creation times and completion states. Legacy event/recurrence/chat tables remain untouched
in existing databases to avoid data loss; no UI or runtime code implements these features.
No destructive fallback is enabled. Notification permissions and receivers were removed
from the active manifest. Navigation, statistics, reminders, speech and LiteRT are absent
from the new runtime.

## Inference and licensing

`LocalInference` is the replaceable engine contract. `LlamaCppEngine` owns one serialized
native session on a dedicated thread, with a mutex covering load/generation/free. JNI
keeps model/context/sampler resources together, clears KV memory between requests, checks
context capacity, reports native failures and uses an atomic cancellation flag during
decode. Token pieces cross JNI as bytes to preserve UTF-8 boundaries. Model templates
come from GGUF metadata; unsupported architectures/templates fail explicitly. The MVP
uses CPU inference and does not promise compatibility with every GGUF or device RAM size.

ToolNeuron was reviewed as the requested reference, specifically its generation callbacks,
load/unload and teardown patterns. Its current native GGUF layer is a prebuilt AAR, so this
project builds its own small JNI adapter against upstream sources instead of shipping an
opaque binary. Provenance and full licenses are in `THIRD_PARTY_NOTICES.md` and APK assets.
No model weights are included in source or APK.

## Verification

Unit/integration tests use Robolectric and real Room: manual CRUD, fresh snapshots, file
persistence across reopen, migration, explicit approval, stale proposal rejection without
partial writes, invalid IDs/dates and bounded context. Native instrumentation is opt-in:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# Import a model through the app first, then:
adb shell am instrument -w -e modelPath /data/user/0/com.aseprit.tiqo/files/gguf/model.gguf com.aseprit.tiqo.test/androidx.test.runner.AndroidJUnitRunner
```

Without `modelPath`, the native test is skipped, not reported as verified inference.
See the session verification report for commands actually run and any remaining limits.

## Bonsai verification and limits (2026-09-13)

The native instrumentation uses real Bonsai 8B Q1_0 through the production JNI/grammar,
with a separate on-disk Room database. It checks COMPLETE, UPDATE, MOVE and CREATE in
sequence, exact full-record differences after approval, no writes before approval,
lock preservation, an unrelated Task, unknown-ID rejection and fresh Room snapshots.
The final model query asks about the newly changed day and must return no actions.
Without `modelPath` the test skips; a skipped test does not verify inference.

The raw transcript includes system instructions, grammar, snapshots, formatted JNI input
and generated output, in `files/inference-test-transcript.txt`. It contains only fixtures.
Test and UI share prompt assembly; ordinary user chats do not log prompts.

The investigation found three distinct issues:
- llama.cpp's built-in ChatML formatter omitted Bonsai's static empty-think suffix from
  its GGUF template. JNI now preserves that suffix when declared by template content;
  instrumentation compares the actual formatted UTF-8 input with the expected template.
- The generic action grammar allowed populated fields that the semantic validator rejected.
  Separate action rules now enforce the same field/null contract shown below.
- Bonsai misinterpreted locked/completed flags, refused a new Task absent from the snapshot,
  confused COMPLETE with UPDATE, and proposed a redundant action on a state question.
  The system prompt now has explicit action-selection rules, permitted flag semantics and
  a final distinction between read-only questions and change requests. No Kotlin intent
  fallback, action repair or automatic execution was added.

| Action | taskId | title | date |
| --- | --- | --- | --- |
| CREATE_TASK | null | string | YYYY-MM-DD |
| UPDATE_TASK | existing ID | string | null |
| MOVE_TASK | existing ID | null | YYYY-MM-DD |
| COMPLETE_TASK | existing ID | null | null |

The validator additionally checks snapshot membership, real dates (1900–2200), nonblank
bounded titles/opinion, at most eight actions and no duplicate target IDs. Approval checks
freshness transactionally. Grammar constrains structure, not these semantic rules or the
quality of the model's recommendation. Passing the fixture suite does not guarantee correct
behavior for all natural-language requests; user review remains essential.

MVP limits: CPU only, context 4096 tokens and output 1024 tokens; at most 80 Tasks per
snapshot. Templates use a finite llama.cpp C API implementation, not arbitrary Jinja.
Model import is required; weights are not bundled. Real runtime verification uses an API35
x86_64 emulator; ARM64 is compiled but has not been tested on a phone. CPU emulator timings
are not device performance measurements. Bonsai can produce awkward Polish and inaccurate
reasoning. Chat is transient, date parsing is intentionally bounded, and no future-phase
features are implemented. Legacy sources/resources are retained; lint warnings concern
unused legacy resources and dependency/catalog updates.

Final verification: 11 Robolectric/Room tests passed (0 skipped/failures/errors), lintDebug
completed with 0 errors / 146 warnings, debug and instrumentation APK builds succeeded.
The real Bonsai instrumentation passed all four actions and the final state-query check:
`OK (1 test)`, 617.601 seconds, API35 x86_64. The final prompt included the fresh moved and
created Tasks and returned `actions:[]`. Its opinion was generic rather than listing Tasks;
this test verifies fresh input and absence of mutations, not semantic answer quality.

## AI diagnostics

Settings → AI Diagnostics → export ZIP after reproducing a slow request (or pressing Stop).
The export warns about prompt/task content. It allowlists diagnostic text/JSON files only;
GGUF, credentials, app preferences and databases are not exported. Keep the ZIP private.
The last three sessions are retained; each artifact is capped at 256 KiB and session logs
rotate at 256 KiB with one previous segment. Native logging has a bounded queue and reports
dropped records. An export includes the current session plus up to two older sessions.

One request ID connects UI, Room/context, formatted prompt, tokenization, native prefill,
generation and parsing. Model/config artifacts link back to the model-load session.
Monotonic timestamps, prefill X/Y (completed batches), throughput and first-token latency
help distinguish slow evaluation from decoding failure. Progress can remain at X while a
single llama_decode batch runs. Cancellation records the interrupted stage and stays
CANCELLED even when the abort callback makes decode return an error. No inference parameters
were tuned as part of this diagnostic change. Physical-device latency still needs a ZIP
from the affected phone; unit tests cannot establish its cause.

Windows: `./scripts/build-debug.ps1` saves full Gradle output under `build-logs/` and copies
a successful debug APK to `S:\Tiqo\builds\Tiqo-debug.apk`. If the drive is unavailable, the
normal `app/build/outputs/apk/debug/app-debug.apk` remains available.

## Adreno OpenCL backend

ARM64 builds enable upstream GGML_OPENCL, embedded kernels and Adreno kernels; binary
kernel packs remain disabled. Q1_0 has upstream Adreno and generic OpenCL matmul paths.
Reference: https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/OPENCL.md
Khronos builds a shared link-time libOpenCL.so, excluded from the APK. Android resolves
the dependency to the phone vendor runtime; standard clGetPlatformIDs/clGetDeviceIDs
enumerate platforms/devices without requiring cl_khr_icd. No vendor binary is shipped.

JNI selects an upstream OpenCL Adreno GPU and requests n_gpu_layers=-1 (all eligible
layers). Configuration logs include openclFound, device, backend, requested n_gpu_layers,
actual offloadedLayers (from llama.cpp), and fallbackReason. BACKEND_DETECTED and
DEVICE_DETECTED enumerate registered backends/devices. Missing vendor/ICD/platform/device
falls back to CPU with an explicit reason; GPU model/context load failure stops with an
error instead of silently retrying CPU. A selected GPU with zero offloaded layers is rejected.
CPU operators may still be used where the upstream scheduler requires them.

The Nothing A059 CPU baseline ZIP showed 671 prompt tokens, 239624 ms prefill (2.800 tok/s),
1.989 generation tok/s, 258434 ms end-to-end and 1652 ms load. This change alters only backend
selection/offload, not the model, quantization, prompt, context, threads or batch sizes.
Physical Adreno performance must be measured by the user; no long local inference was run.

Loader correction: the previous static Khronos ICD loader and Tiqo preflight both required
clIcdGetPlatformIDsKHR. That establishes ICD compatibility, not availability of a standard
vendor OpenCL implementation. The shared Khronos library now serves only as a link-time ABI
library, with DT_NEEDED libOpenCL.so resolved by Android to the vendor runtime. The APK must
not package libOpenCL.so. Diagnostics distinguish loader role, resolved vendor symbol path,
loadable/inaccessible library candidates (including libOpenCL_adreno.so), platform/device
query return codes and counts. A missing ARM64 vendor dependency produces OPENCL_LINK_ERROR,
not a crash or a claim of GPU availability. Device detection/performance still requires the
phone test; no phone was attached during this change.
