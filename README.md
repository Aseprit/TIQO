# Tiqo

Tiqo to lokalny, natywny planner na Androida z polskim interfejsem, spokojnym glassmorphism UI i opcjonalną warstwą lokalnego AI. Planner działa w pełni bez modelu i bez połączenia z siecią.

## Stan projektu — 0.2.0

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
- trwała historia czatu, streaming i rzeczywiste zatrzymanie generowania,
- adapter LiteRT-LM oraz menedżer ręcznie importowanych modeli.

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

`LlmEngine` oddziela Compose, dane planera i konkretny runtime. Pierwsza implementacja korzysta z LiteRT-LM i obsługuje ładowanie, streaming, anulowanie oraz zwalnianie modelu. Załadowany model pozostaje ciepły między promptami i jest zwalniany po pięciu minutach aplikacji w tle.

Modele nie są dołączone do repozytorium i nie są pobierane z gated repozytoriów. Menedżer modeli używa Androidowego selektora plików i kopiuje zgodny plik `.litertlm` do prywatnego katalogu aplikacji `files/models/`. Użytkownik odpowiada za licencję i zgodność importowanego pliku. Tokeny Hugging Face nigdy nie trafiają do APK.

Obecne ograniczenie: warstwa `PlannerTools` i walidator polskich dat względnych są gotowe do integracji, ale odpowiedzi modelu nie są jeszcze automatycznie mapowane na tool calls. Do działania czatu potrzebny jest zgodny, ręcznie zaimportowany model LiteRT-LM. FunctionGemma/PlannerGemma nie jest dołączona ani automatycznie pobierana.

## Architektura

```text
Compose → ViewModel/Flow → domain contracts → Room/DataStore
                           ↑
PlannerTools → validation ┘

Chat UI → LlmEngine → LiteRT-LM
```

Projekt pozostaje jednym modułem Gradle, ale pakiety oddzielają `feature`, `domain`, `data`, `notification`, `core/designsystem` i `ai`. Room jest jednym źródłem prawdy dla Home, Calendar, Statistics, reminders i przyszłych tool calls. Szczegóły: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Prywatność i repozytorium

Dane planera, historia czatu i modele pozostają lokalnie na urządzeniu. `.gitignore` wyklucza `local.properties`, keystore'y, modele, pliki częściowe i artefakty builda. Repozytorium nie zawiera tokenów ani kluczy podpisujących.

## Roadmapa

Bieżący stan i kolejne kroki opisuje [ROADMAP.md](ROADMAP.md).

## Licencja

Licencja kodu projektu nie została jeszcze wybrana. Do czasu jej dodania obowiązują prawa autorskie właściciela repozytorium. Modele mają własne, niezależne warunki licencyjne.
