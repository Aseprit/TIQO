# Phase 4 handoff — local runtime, streaming and fresh planner state

Stan na 2026-09-04.

## Root cause crasha

Tiqo używał LiteRT-LM `0.16.1`, ale `debugRuntimeClasspath` rozwiązywał `kotlinx-coroutines-core/android:1.9.0`. Bezpośrednia inspekcja bytecode AAR `0.16.1` pokazała, że `Conversation$sendMessageAsync$1$1.onDone()` wywołuje interface-static `SendChannel.close$default(...)`. Tego symbolu nie ma w coroutines 1.9.0; jest w 1.11.0. To ten sam mechanizm co [google-ai-edge/LiteRT-LM#2812](https://github.com/google-ai-edge/LiteRT-LM/issues/2812): inference kończy się, callback `onDone` wchodzi na brakujący symbol i proces upada przez `NoSuchMethodError` na wątku callbacku.

Naprawa: bezpośrednia runtime dependency `kotlinx-coroutines-android:1.11.0`. Końcowy `dependencyInsight` potwierdza `core:1.11.0` i `android:1.11.0`, mimo że POM LiteRT-LM nadal prosi o 1.9.0. Nie dodano workaroundu przechwytującego/ukrywającego błąd.

## Wersje i Android backend

- LiteRT-LM: 0.16.1 (CPU w Tiqo).
- kotlinx-coroutines: 1.11.0.
- Kotlin: 2.3.0.
- Compose BOM: 2024.10.01; rozwiązuje Compose UI 1.7.5 i Material3 1.3.1.
- AGP: 8.7.3; compile/target SDK 35; minSdk 26.
- APK zawiera `liblitertlm_jni.so` dla `arm64-v8a` i `x86_64`.
- Manifest ma opcjonalne `libvndksupport.so` i `libOpenCL.so`, wymagane przez oficjalną dokumentację dla backendu GPU. Tiqo wybiera CPU, więc nie wymaga bibliotek NPU ani `nativeLibraryDir`.

Oficjalne API referencyjne: [LiteRT-LM Kotlin getting started](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md) i bieżący [Conversation.kt](https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt).

## Lifecycle, streaming i diagnostyka

- `LlmEngine.generate()` pozostaje wspólnym `Flow<LlmEvent>`; LiteRT-LM używa oficjalnego `Conversation.sendMessageAsync(...): Flow<Message>`.
- Chat utrzymuje persistent Conversation/KV pod stabilnym session ID. Planner tworzy świeżą jednorazową Conversation dla każdego polecenia.
- UI tworzy pustą wiadomość assistant przed tokenem, pokazuje status Generating i dopisuje fragmenty paczkami (24 znaki lub 50 ms), aby ograniczyć zapisy Room/recomposition.
- STOP najpierw wywołuje `cancelProcess()`, potem anuluje i dołącza collection job. Buforowany partial jest zapisywany w `NonCancellable`, a przerwana runtime Conversation jest zamykana.
- „Nowa rozmowa” anuluje inference, resetuje oba runtime Conversation/KV, usuwa wiadomości bieżącej rozmowy w Room i czyści Undo. Nie tworzy archiwum nazwanych chatów.
- `TiqoLiteRt` loguje model load, inference, native/backend, OOM, cancellation, close/reset oraz zagregowane użycie pamięci. Prompt i treść odpowiedzi nie trafiają do logów.

## Planner state

Room/`PlannerRepository` jest jedynym źródłem prawdy. `PlannerAgent` pobiera świeżą listę kandydatów dla każdego polecenia. `PlannerTools` ponownie czyta ID przed get/update/delete/complete, a list/search/statistics zawsze wykonują aktualny repository Flow. Chat history jest tylko kontekstem językowym. Testy obejmują usunięcie wpisu poza chatem (kolejne search zwraca brak) oraz ręczną zmianę 10:00 → 12:00 (kolejny search zwraca 12:00).

## GGUF / llama.cpp

Nie ma pozorowanego wsparcia. `.gguf` mapuje się na `ModelBackend.LlamaCpp`, istnieje jawny `LlamaCppEngine` boundary, lecz bieżący APK zgłasza `RuntimeUnavailable` i nie kopiuje pliku.

Konkretny blocker: oficjalny [llama.cpp Android example](https://github.com/ggml-org/llama.cpp/tree/master/examples/llama.android) nie jest publikowany jako stabilny AAR/Maven i obecnie wymaga source/CMake, NDK 29, CMake 3.31.6 oraz minSdk 33. Tiqo ma minSdk 26. Włączenie kodu bez przypięcia revision, polityki aktualizacji JNI i testów ABI/urządzeń byłoby udawaniem wsparcia. `llama.kt` oceniono jako referencję cienkiego bindingu/Flow/chat template, ale nie dodano automatycznie: nie ma publikowanego AAR i wymaga vendoringu/submodule.

## Weryfikacja

- `testDebugUnitTest`: 28/28 testów przeszło.
- Scenariusze: fresh planner delete, fresh planner edit, clear/reset context, multi-chunk streaming, STOP/partial.
- `assembleDebug`: przeszedł.
- APK: `app/build/outputs/apk/debug/app-debug.apk` (67,024,574 B).
- Nie wykonano testu inference Qwen na urządzeniu: ADB nie wykrył podłączonego urządzenia. Naprawa classpath i bytecode jest zweryfikowana, ale końcowego smoke testu sprzętowego nie należy raportować jako wykonanego.

## Następne kroki (następna faza)

1. Zainstalować APK na urządzeniu z Qwen `.litertlm`, zebrać `adb logcat -s TiqoLiteRt` i potwierdzić zakończenie generation bez `NoSuchMethodError`.
2. Osobno zaprojektować/pinować moduł upstream llama.cpp Android, zdecydować czy podnieść minSdk czy utrzymywać własny wariant CMake, a następnie dodać device matrix i model licencyjny.
3. Dopiero po realnym JNI load/stream/cancel/chat-template/KV test włączyć import i aktywację GGUF w Model Managerze.
