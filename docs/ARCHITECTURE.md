# Architektura Tiqo

## Zasady

1. UI nie zna Room, DataStore ani LiteRT-LM.
2. Warstwa danych implementuje kontrakty z `domain`.
3. Modele runtime'u AI nie przeciekają do feature'ów.
4. Operacje modelu AI są traktowane jak nieufne wejście i zawsze walidowane.
5. Każda faza pozostawia projekt w stanie kompilowalnym.

## Przepływ zależności

```text
Compose screen
      ↓
ViewModel / UI state
      ↓
domain contracts + models
      ↑
data implementations (in-memory → Room)

Chat / planner feature
      ↓
AI orchestration + validation
      ↓
LlmEngine
      ↑
LiteRT-LM engines (small planner / optional chat model)
```

## Aktualne granice

### `core/designsystem`

Kolory, typografia i wspólne komponenty. `GlassSurface` symuluje szkło transparentną powierzchnią, obramowaniem i cieniem. Rozmycie jest używane dla dużych, statycznych dekoracji tła, nie dla każdego elementu przewijanej listy.

### `domain`

Modele niezależne od Android SDK: `PlannerTask`, `RecurrenceRule` oraz `TaskRepository`. Room dostanie osobne encje i mappery; encje bazy nie staną się modelami UI.

### `data`

Obecnie zawiera `InMemoryTaskRepository`. W fazie trwałych danych implementacja zostanie zastąpiona repozytorium opartym o Room, bez zmiany kontraktu używanego przez ekran Home.

W bazie completion nie powinno być pojedynczym polem na tasku cyklicznym. Potrzebny będzie rekord wykonania identyfikowany taskiem i datą wystąpienia, dzięki czemu historia pozostanie poprawna.

### `feature`

Każdy feature posiada ekran/route, ViewModel i UI state. Nawigacja przekazuje tylko stabilne identyfikatory. Home jest pierwszym pionowym wycinkiem; Calendar i Chat są świadomie placeholderami.

### `ai`

`LlmEngine` jest wąską granicą dla silników lokalnych. Planowana warstwa orkiestracji wybierze engine według wymaganej capability:

- mały model: klasyfikacja intencji i tool calls planera,
- większy model: opcjonalny chat i trudniejsze przekształcenia,
- brak właściwego modelu: czytelny błąd lub deterministyczny fallback.

Wynik modelu nie może bezpośrednio modyfikować bazy. Najpierw przechodzi parsowanie, walidację schematu, reguły domenowe i — przy operacjach destrukcyjnych lub zbiorczych — potwierdzenie użytkownika.

## Dlaczego jeden moduł Gradle na początku

Projekt ma obecnie niewielki zespół i krótki czas kompilacji. Pakiety dają wystarczające granice bez kosztu wielu modułów. Kandydaci do późniejszego wydzielenia to `core:designsystem`, `core:database`, `core:ai` oraz niezależne feature'y. Wydzielenie nastąpi, gdy pojawi się co najmniej jeden konkretny powód: równoległa praca, niekontrolowane zależności albo zauważalna poprawa czasu buildów.

## Wstrzykiwanie zależności

Pierwszy skeleton używa prostych konstruktorów i domyślnych implementacji. Przed Room zostanie dodany mały composition root. Framework DI zostanie wybrany dopiero wtedy, gdy graf zależności uzasadni jego koszt; architektura nie wymaga Hilt do zachowania testowalności.

