# Tiqo

Tiqo to lokalny, natywny planner na Androida z polskim interfejsem, spokojnym glassmorphism UI i opcjonalną warstwą lokalnego AI. Planner działa w pełni bez modelu i bez połączenia z siecią.

## Stan projektu — 0.4.0

W aplikacji działają:

- Home z wyborem dnia i tygodnia oraz wspólną listą tasków, wydarzeń i urodzin,
- trwałe dane Room/SQLite i ustawienia DataStore,
- taski jednorazowe, codzienne, tygodniowe, w wybrane dni, miesięczne i coroczne,
- completion zapisane osobno dla każdego wystąpienia cyklicznego taska,
- tworzenie i edycja typu, opisu, daty, godziny, trybu całodziennego, przypomnienia i recurrence,
- long press ze szczegółami, edycją i potwierdzeniem usunięcia,
- odhaczanie z animacją, haptic feedback i działającym Undo,
- prawdziwy kalendarz miesięczny z agendą i wskaźnikami dni,
- statystyki tygodniowe liczone wyłącznie z tasków,
- dark/light/system theme, siedem kolorów akcentu i intensywność szkła,
- lokalne powiadomienia z akcją „Gotowe”, obsługa restartu, czasu i strefy czasowej,
- jedna trwała bieżąca rozmowa, resetowana razem z runtime/KV przez „Nowa rozmowa”,
- manualny agent LiteRT-LM: 10 walidowanych narzędzi → Room → reminder → structured result → Undo,
- osobne lokalne modele planner/chat, routing, streaming i rzeczywiste zatrzymanie generowania,
- trwały katalog własnych modeli z importem systemowym, rolami Planner/Chat, aktywnym wyborem, load/unload/delete i testem na urządzeniu,
- katalog rekomendowanych gotowych pakietów z kontrolowanym pobieraniem, progressem, anulowaniem i SHA-256,
- polski benchmark `planner-pl-v1`, powtarzalny SFT i pipeline konwersji `.litertlm`.

## Uruchomienie

Wymagania:

- JDK 17,
- Android SDK 35,
- Android Studio lub terminal,
- urządzenie/emulator z Androidem 8.0 (API 26) lub nowszym.

Otwórz repozytorium w Android Studio, zsynchronizuj Gradle i uruchom konfigurację `app`.

Debug APK można zbudować w PowerShellu:

```powershell
.\gradlew.bat :app:assembleDebug
```

Artefakt powstaje jako `app/build/outputs/apk/debug/app-debug.apk`.

Kontrola jakości:

```powershell
.\gradlew.bat test
.\gradlew.bat :app:lintDebug
```

## Przypomnienia

Tiqo prosi o zgodę na powiadomienia na Androidzie 13+. Gdy system zezwala na dokładne alarmy, używa dokładnego `AlarmManager`; bez tego dostępu bezpiecznie przechodzi na alarm niedokładny i pokazuje status oraz odnośnik do ustawień systemowych. Dla serii planowane jest tylko najbliższe wystąpienie, a po jego obsłużeniu kolejne.

## Lokalne AI

`LlmEngine` oddziela Compose, dane planera i konkretny runtime. Implementacja LiteRT-LM korzysta z manualnego tool callingu: runtime nigdy nie wykonuje funkcji sam. Tiqo odczytuje call, waliduje allow-listę i argumenty, blokuje nieznane identyfikatory, a dopiero potem używa wspólnego repozytorium Room. Planner pozostaje ciepły przez pięć minut tła, większy chat przez minutę; oba są zwalniane pod presją pamięci.

Modele nie są dołączone do repozytorium. Użytkownik może zaimportować gotowy pakiet `.litertlm`, nadać mu rolę Planner, Chat albo obie, a następnie aktywować i przetestować go bez modyfikowania danych planera. Przypisanie roli Planner wymaga przejścia izolowanego self-testu create/delegate/clarification. Katalog zawiera przypięte i weryfikowane SHA-256 artefakty Qwen3 0.6B No-think (small/experimental), Qwen3 1.7B Dynamic INT4 (Chat candidate) oraz większą alternatywę Qwen 2.5 1.5B Q8. Pobieranie zachowuje `.partial` i wznawia transfer przez HTTP Range.

Obecny wykonujący backend to LiteRT-LM 0.16.1 na CPU i obsługuje gotowe pakiety `.litertlm`. `.gguf` jest poprawnie rozpoznawany jako backend llama.cpp, lecz obecny APK nie zawiera jeszcze zweryfikowanego modułu JNI i odrzuca import z jawnym komunikatem. Sama końcówka pliku nie gwarantuje zgodności checkpointu; prawdziwą weryfikacją jest inicjalizacja runtime i test modelu. Szczegóły: [docs/MODEL_COMPATIBILITY.md](docs/MODEL_COMPATIBILITY.md).

## Architektura

```text
Compose → ViewModel/Flow → domain contracts → Room/DataStore
                           ↑
PlannerTools → validation ┘

Chat UI → aktywny Planner model → ToolCall → Validator → PlannerTools → Room
                           └→ delegate_to_chat → aktywny lokalny Chat model
```

Projekt pozostaje jednym modułem Gradle, ale pakiety oddzielają `feature`, `domain`, `data`, `notification`, `core/designsystem` i `ai`. Room jest jednym źródłem prawdy dla Home, Calendar, Statistics, reminders i tool calls. Szczegóły: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) i [docs/AI_ARCHITECTURE.md](docs/AI_ARCHITECTURE.md).

## Prywatność i repozytorium

Dane planera, historia czatu i modele pozostają lokalnie na urządzeniu. `.gitignore` wyklucza `local.properties`, keystore'y, modele, pliki częściowe i artefakty builda. Repozytorium nie zawiera tokenów ani kluczy podpisujących.

## Roadmapa

Bieżący stan i kolejne kroki opisuje [ROADMAP.md](ROADMAP.md).

## Licencja

Licencja kodu projektu nie została jeszcze wybrana. Do czasu jej dodania obowiązują prawa autorskie właściciela repozytorium. Modele mają własne, niezależne warunki licencyjne.
