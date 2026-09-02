# Tiqo

Tiqo to natywna aplikacja Android do spokojnego planowania dnia. Projekt łączy prosty planner z lekkim, nowoczesnym interfejsem inspirowanym glassmorphismem i — w dalszych fazach — lokalną warstwą AI.

## Stan projektu

Repozytorium zawiera działający szkielet aplikacji (`0.1.0`):

- Kotlin + Jetpack Compose + Material 3,
- dolna nawigacja Calendar / Home / Chat,
- ekran Home z datą, paskiem tygodnia i listą tasków,
- oznaczanie taska jako wykonany,
- dodawanie i edycja podstawowych danych taska,
- long-press otwierający szczegóły,
- usuwanie z możliwością cofnięcia,
- ciemny design system z paletą konfigurowalnych accent colors,
- kontrakt `LlmEngine`, niezależny od konkretnego runtime'u/modelu.

Dane są na tym etapie celowo przechowywane wyłącznie w pamięci. Room, DataStore i runtime lokalnych modeli są opisane w [ROADMAP.md](ROADMAP.md), ale nie zostały przedwcześnie włączone do pierwszej fazy.

## Uruchomienie

Wymagania:

- Android Studio z JDK 17,
- Android SDK 35,
- urządzenie lub emulator z Androidem 8.0 (API 26) albo nowszym.

1. Otwórz katalog repozytorium w Android Studio.
2. Poczekaj na zakończenie synchronizacji Gradle.
3. Wybierz urządzenie i uruchom konfigurację `app`.

## Debug APK

W PowerShellu:

```powershell
.\gradlew.bat :app:assembleDebug
```

Gotowy plik znajdziesz w:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Kontrola jakości:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

## Struktura

```text
app/src/main/java/com/aseprit/tiqo/
├── ai/                 kontrakty lokalnych modeli
├── core/designsystem/  motyw i współdzielone komponenty UI
├── data/               implementacje źródeł danych
├── domain/             modele i kontrakty repozytoriów
└── feature/            ekrany Calendar, Home i Chat
```

Na początku projekt pozostaje jednym modułem aplikacyjnym, ale ma wyraźne granice pakietów. Pozwala to iterować szybko; wydzielenie modułów Gradle nastąpi dopiero wtedy, gdy zacznie skracać buildy lub chronić realne granice zależności. Szczegóły znajdują się w [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Kierunek wizualny

- dark mode jako ustawienie domyślne,
- light mode jako opcja,
- pojedynczy accent color użytkownika,
- przezroczyste powierzchnie, subtelne obramowania i miękkie światło,
- blur tylko tam, gdzie daje zauważalny efekt i nie szkodzi płynności list,
- krótkie animacje i czytelne stany zaznaczenia.

## Licencja

Licencja nie została jeszcze wybrana. Do czasu jej dodania obowiązują standardowe prawa autorskie właściciela repozytorium.

