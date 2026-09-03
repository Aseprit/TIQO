# Architektura Tiqo

## Zasady

1. Room jest jedynym źródłem prawdy dla danych planera.
2. UI nie zna DAO, DataStore ani typów LiteRT-LM.
3. Warstwa danych implementuje kontrakty z `domain`.
4. Model AI nigdy nie otrzymuje SQL ani DAO; tool calls przechodzą przez walidację i repozytorium.
5. Planner działa bez modelu, sieci i aktywnego procesu UI.

## Przepływ zależności

```text
Compose screen
      ↓
ViewModel + immutable UI state + Flow
      ↓
domain models / repository contracts
      ↑
Room repositories ← Room database

LLM output → PlannerTools → validation → PlannerRepository
Chat UI → LlmEngine ← LiteRtLlmEngine
```

## Pakiety

- `core/designsystem` — centralny Material theme, semantyczne kolory i lekkie glass surfaces.
- `domain/model` — `PlannerItem`, recurrence, occurrence, statystyki i modele czatu bez zależności od Android UI.
- `domain/recurrence` — deterministyczne obliczanie wystąpień bez materializowania przyszłości.
- `data/database` — Room encje i DAO; completion ma klucz `(itemId, occurrenceDate)`.
- `data/repository` — mapowanie encji i implementacje wspólnych repozytoriów Home/Calendar/Statistics/Chat.
- `data/settings` — DataStore Preferences i trwałe ustawienia UI/planera.
- `notification` — alarmy, receivers, kanał, akcja completion oraz rescheduling.
- `feature/*` — Compose screens, routes, ViewModels i UI state.
- `ai` — kontrakt `LlmEngine`, adapter LiteRT-LM, manifest modeli oraz bezpieczna warstwa narzędzi.

## Baza danych

Schema v1 zawiera:

- `planner_items` — jedna definicja taska/serii,
- `occurrence_completions` — tylko rzeczywiście zakończone wystąpienia,
- `chat_conversations`,
- `chat_messages`.

Nie istnieje tabela z wygenerowanymi przyszłymi wystąpieniami. `RecurrenceEngine` oblicza wystąpienia tylko dla zakresu żądanego przez ekran lub statystyki. Usunięcie definicji serii kaskadowo usuwa jej completion records.

To pierwsza wersja Room projektu; poprzedni skeleton przechowywał dane wyłącznie w pamięci, dlatego nie ma źródłowej bazy do migracji. Kolejna zmiana schematu musi dodać jawną `Migration`; aplikacja nie używa `fallbackToDestructiveMigration`.

## Przypomnienia

`RoomPlannerRepository` po zapisie anuluje stary alarm i planuje najbliższe poprawne wystąpienie. Exact alarm jest używany wyłącznie, kiedy pozwala na to system; w przeciwnym razie scheduler zachowuje funkcję przez alarm niedokładny. Receivery odbudowują plan po restarcie urządzenia, zmianie czasu i strefy.

## AI

`LlmEngine` udostępnia load/unload, streaming, stop oraz obserwowalny stan. `LiteRtLlmEngine` jest pierwszym backendem, ale feature czatu zależy wyłącznie od interfejsu.

`ModelManager` przechowuje manifest poza UI i importuje pliki do prywatnego `files/models`. Nie ma automatycznego pobierania gated modeli ani tokenów w APK.

`PlannerTools` waliduje typowane wywołania, daty i potwierdzenia operacji destrukcyjnych. `PlannerAgent` przekazuje aktywnemu modelowi niezależny od checkpointu schemat, parsuje ręczny tool call i dopiero po walidacji wykonuje operację przez repozytorium. Model czatu nie otrzymuje narzędzi ani dostępu do danych planera.

## Composition root

`AppContainer` tworzy pojedynczą bazę, repozytoria, settings, scheduler, engine i model manager. Proste wstrzykiwanie przez konstruktory utrzymuje testowalność bez dodawania Hilt do niewielkiego, jednomodułowego projektu.
