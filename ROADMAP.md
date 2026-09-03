# Tiqo — roadmap

Status fazy 3, 2026-09-02. Każdy etap kończy się działającym plannerem i kompilowalnym APK.

## Audyt wejściowy fazy 2

### DONE przed rozpoczęciem

- projekt Kotlin/Compose tworzył debug APK,
- istniały pakiety `domain`, `data`, `feature`, `core/designsystem` i `ai`,
- Home pokazywał datę, tydzień i demonstracyjną listę,
- działały podstawowe toggle, prosty add/edit, long press, delete i Undo,
- istniały `ViewModel`, `Repository`, `StateFlow` i pierwszy kontrakt `LlmEngine`.

### PLACEHOLDER / problemy znalezione

- Calendar i Chat były pustymi ekranami,
- Settings, Statistics, Room, DataStore i notifications nie istniały,
- dane znikały po restarcie procesu,
- formularz nie obsługiwał typu, daty, czasu, reminderów ani recurrence,
- cykliczny task miał jeden globalny completion state,
- theme nie definiował kompletu semantycznych kolorów i groził ciemnym tekstem na ciemnych kontrolkach,
- Home i przyszły Calendar nie miały wspólnego trwałego źródła danych.

## Faza 2 — realny lokalny planner

### DONE — stabilizacja i design system

- [x] semantyczne kolory dark i light dla tekstu, surface, outline, accent, destructive i success,
- [x] dark jako domyślny oraz Light/System,
- [x] siedem kolorów akcentu zmienianych na żywo,
- [x] lekki glass effect bez blur na każdej karcie,
- [x] DataStore: theme, accent, animations, glass, pierwszy dzień tygodnia, widoczność wykonanych oraz domyślne czas/reminder.

### DONE — dane i UX planera

- [x] Room schema v1: definicje elementów, completion per occurrence i osobne tabele czatu,
- [x] `TASK`, `EVENT`, `BIRTHDAY`,
- [x] jednorazowe, daily, weekly, selected weekdays, monthly, yearly, interval i opcjonalny end date w domenie,
- [x] formularz create/edit z DatePicker i TimePicker,
- [x] reminder i tryb all-day,
- [x] long press, szczegóły, edycja, potwierdzenie usunięcia,
- [x] animowane completion, haptics i rzeczywiste Undo,
- [x] Home, Calendar i Statistics korzystają z tego samego `PlannerRepository`/Room,
- [x] kalendarz 6×7 z agendą, wskaźnikami, poprzednim/następnym miesiącem i Today,
- [x] statystyki tygodniowe z prawdziwych wystąpień; eventy i urodziny nie obniżają wyniku.

### DONE — przypomnienia

- [x] kanał i lokalne powiadomienia Android,
- [x] runtime permission Android 13+,
- [x] sprawdzanie `canScheduleExactAlarms()` i fallback do alarmu niedokładnego,
- [x] systemowy ekran dostępu „Alarms & reminders”,
- [x] synchronizacja po zapisie, edycji i usunięciu,
- [x] odbudowa po boot, zmianie czasu i timezone,
- [x] akcja powiadomienia „Gotowe” zapisująca completion wystąpienia,
- [x] planowanie kolejnego wystąpienia zamiast tysięcy alarmów.

### DONE — Chat i fundament lokalnego AI

- [x] pełny interfejs czatu z empty state, bąbelkami, inputem, Send/Stop i statusem,
- [x] lokalna historia rozmowy w osobnych tabelach Room,
- [x] `LlmEngine` niezależny od UI i bazy,
- [x] rzeczywisty adapter LiteRT-LM: load, stream, cancel, unload,
- [x] katalog i menedżer modeli z ręcznym importem, stanem loaded/unloaded i usuwaniem,
- [x] lifecycle modeli: brak przeładowania między promptami i unload po pięciu minutach w tle,
- [x] typowane `PlannerTools` korzystające wyłącznie z repozytorium,
- [x] walidacja destrukcyjnych operacji i deterministyczny kontekst daty/czasu/strefy/locale,
- [x] dataset-friendly test cases dla przyszłych polskich promptów.

### DONE — jakość

- [x] testy recurrence, completion per occurrence, zakresów, synchronizacji reminders, statistics i parsera dat,
- [x] schemat Room eksportowany do repozytorium,
- [x] modele, tokeny, keystore'y i pliki lokalne wykluczone z Git,
- [x] debug APK budowany przez Gradle.

## Faza 3 — lokalny agent

### ACTIVE — pivot Qwen3-0.6B (bez fine-tuningu)

- [x] zabezpieczyć zastany WIP PlannerGemma bez usuwania datasetu, treningu, ewaluacji i eksperymentów,
- [x] wstrzymać custom SFT PlannerGemma 270M oraz długie benchmarki FunctionGemma,
- [x] dodać ręcznie kontrolowany polski gold benchmark 280 przypadków z deterministycznym kontekstem,
- [x] dodać lokalny runner Qwen3-0.6B non-thinking z metrykami, failure report i resume,
- [x] dodać launchery Windows dla smoke i pełnego runu,
- [ ] uruchomić lokalnie pełny benchmark Qwen3-0.6B i przeanalizować zapisane failures,
- [ ] dopiero po wyniku podjąć decyzję: zero-shot Qwen, fine-tuning czy powrót do PlannerGemma.

### DONE — benchmark i pipeline

- [x] niezależny katalog `ai/plannergemma` z generatorem, walidatorem, treningiem, ewaluacją i konwersją,
- [x] `planner-pl-v1`: 10 000 train, 1 000 validation, 720 holdout test; pięć równych stylów polskiego,
- [x] programowa walidacja schematu, dat, czasu, recurrence, duplikatów i rozdzielenia rodzin template,
- [x] metryki ogólne, per kategoria/styl, confusion matrix, malformed i false-positive mutation,
- [x] powtarzalny SFT z manifestem ustawień oraz dynamic-int8 `.litertlm` conversion entry point,
- [ ] zakończyć wersjonowany benchmark kandydatów i wybrać checkpoint spełniający próg jakości,
- [ ] przekonwertować finalne wagi na Linux/Colab i zweryfikować `.litertlm` na fizycznym Androidzie.

### DONE — Android agent i modele

- [x] manualne LiteRT-LM tool calling (`automaticToolCalling = false`) i prawdziwe `cancelProcess()`,
- [x] 10 narzędzi, parser allow-list, walidacja, kontekst lokalny i blokada hallucinated item IDs,
- [x] wykonanie przez wspólny `PlannerRepository`/Room z synchronizacją Home, Calendar i reminderów,
- [x] structured results oraz Undo dla create, update, delete i complete/reopen,
- [x] osobne silniki planner/chat, routing `delegate_to_chat` i brak improwizacji małego modelu,
- [x] oddzielne rekordy user/assistant/tool call/tool result w historii Room,
- [x] statusy AI, prawdziwe Stop i model lifecycle: warm foreground, timeout per rola, memory pressure,
- [x] legalny import gated PlannerGemma i prawdziwe pobieranie publicznego chat modelu z progressem, cancel, rozmiarem i SHA-256,
- [x] test end-to-end fake inference → parser → repository → reminder fields → Undo.

## TODO — po fazie 3

- [ ] testy wydajności i pełny offline scenario na docelowym telefonie po dostarczeniu finalnego `.litertlm`,
- [ ] testy instrumentacyjne Room/migracji, gdy powstanie schema v2; v1 jest pierwszą bazą i nie ma poprzedniej bazy Room do migracji,
- [ ] testy UI/accessibility na fizycznych urządzeniach i benchmark dużego zbioru danych,
- [ ] export/import danych oraz wybór własnego koloru akcentu,
- [ ] snooze i automatyczne otwieranie panelu szczegółów z powiadomienia (obecnie otwierany jest właściwy dzień),
- [ ] release signing, polityka prywatności i dystrybucja Google Play.

## Poza zakresem

Chmura, konta, współdzielenie, własne STT/TTS, RAG, poczta, pogoda i sterowanie telefonem nie należą do obecnego kierunku produktu.
