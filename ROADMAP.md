# Tiqo — roadmap

Roadmapa jest podzielona na pionowe, testowalne etapy. Każda faza powinna kończyć się działającym debug APK, a nie tylko zbiorem niepołączonych klas.

## Faza 0 — fundament i skeleton (obecna)

- [x] projekt Android/Gradle od zera,
- [x] Kotlin, Jetpack Compose i Material 3,
- [x] podstawowy design system oraz glass surfaces,
- [x] nawigacja Calendar / Home / Chat,
- [x] interaktywny prototyp Home,
- [x] in-memory `TaskRepository`,
- [x] niezależny kontrakt `LlmEngine`,
- [x] README, architektura i automatyczny build CI.

Kryterium zakończenia: projekt synchronizuje się, testy przechodzą, a `:app:assembleDebug` tworzy instalowalny APK.

## Faza 1 — trwałe taski

- Room/SQLite: encje tasków, reguł powtarzania i wykonań,
- migracje schematu od pierwszej wersji,
- mapowanie encji bazodanowych na modele domenowe,
- tworzenie i edycja daty, czasu oraz rodzaju taska,
- reguły: jednorazowa, tygodniowa i coroczna,
- osobny completion state dla konkretnego wystąpienia taska,
- soft-delete lub dziennik operacji umożliwiający pewne undo,
- testy DAO i reguł występowania tasków.

Kryterium zakończenia: taski oraz wykonania przeżywają restart procesu, a cykle nie nadpisują historii poprzednich dni.

## Faza 2 — ustawienia i dopracowanie Home

- DataStore Preferences,
- dark / light / system (z domyślnym dark),
- wybór accent color i własnego koloru,
- intensywność efektu szkła oraz ograniczenie animacji,
- pełny formularz taska,
- lepsze stany puste, błędy i accessibility,
- animacje dodania, wykonania, usunięcia i zmiany dnia,
- powiadomienia i przypomnienia jako osobna decyzja produktowa.

Kryterium zakończenia: wygląd i preferencje są trwałe, lista pozostaje płynna przy dużej liczbie tasków, a podstawowy flow jest dostępny dla TalkBack.

## Faza 3 — Calendar i statystyki

- widok miesiąca i agenda,
- podgląd obciążenia dni,
- historia wykonań,
- streaks bez karania użytkownika za przerwy,
- proste wykresy tygodniowe/miesięczne,
- eksport danych do JSON/CSV.

## Faza 4 — lokalny planner AI

- implementacja małego `LlmEngine` na LiteRT-LM,
- zestaw ściśle typowanych narzędzi planera,
- walidacja każdego tool call przed zapisem,
- ekran potwierdzenia operacji wieloelementowych i ryzykownych,
- deterministyczny fallback parser dla prostych komend,
- benchmark czasu ładowania, RAM-u i temperatury urządzenia,
- polityka lifecycle: warm timeout zamiast ładowania/unloadowania przy każdej wiadomości.

Kryterium zakończenia: prostą wiadomością można lokalnie utworzyć, zmienić lub odznaczyć task bez dostępu do internetu, a model nie zapisuje niewalidowanych danych.

## Faza 5 — opcjonalny większy model i routing

- manager pobierania modeli z kontrolą miejsca i integralności,
- osobny silnik dla większego modelu czatowego,
- routing oparty o typ żądania, dostępne capabilities i walidację wyniku,
- jawny komunikat, gdy większy model nie jest zainstalowany,
- ręczny wybór modelu i możliwość wyłączenia automatycznego routingu,
- streaming odpowiedzi oraz przerwanie generowania,
- pomiary na docelowym urządzeniu z 12 GB RAM.

Mały model nie powinien sam oceniać swojej „inteligencji”. Router podejmuje decyzję na podstawie klasyfikacji intencji, wymaganej funkcji, wyniku walidacji i ustawień użytkownika.

## Faza 6 — wydanie i współdzielenie

- podpisywanie release APK/AAB bez sekretów w repo,
- backup/restore oraz polityka prywatności,
- crash reporting wyłącznie opt-in,
- testy UI i urządzeń o słabszej wydajności,
- przygotowanie listingów oraz zamkniętych testów Google Play,
- dopiero później: synchronizacja i współdzielenie z innymi użytkownikami.

## Poza zakresem obecnego etapu

- logowanie i chmura,
- współdzielenie plannerów,
- Google Play release,
- pobieranie wag modeli,
- pełny chat AI,
- rozbudowane statystyki.

