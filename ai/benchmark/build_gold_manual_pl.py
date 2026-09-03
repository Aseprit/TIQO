"""Serialize Tiqo's hand-authored Polish gold benchmark.

Every input variant and every expected label below is explicitly reviewed source data.
The script performs no model calls, paraphrasing, or random generation.
"""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parent
DEFAULT_OUTPUT = ROOT / "gold_manual_pl.jsonl"
STYLES = ("clean", "colloquial", "no-diacritics", "typo", "stt")
EXPECTED_CATEGORY_COUNTS = {
    "create": 45,
    "recurrence": 35,
    "update": 30,
    "list": 20,
    "read": 10,
    "search": 25,
    "completion": 20,
    "delete": 15,
    "reminder": 20,
    "ambiguous": 25,
    "delegate": 25,
    "statistics": 10,
}
BASE_CONTEXT: dict[str, Any] = {
    "current_date": "2026-09-03",
    "current_time": "09:00",
    "timezone": "Europe/Warsaw",
    "locale": "pl-PL",
    "selected_date": "2026-09-03",
    "candidate_items": [],
}


def variants(clean: str, colloquial: str, no_diacritics: str, typo: str, stt: str) -> dict[str, str]:
    return dict(zip(STYLES, (clean, colloquial, no_diacritics, typo, stt), strict=True))


def candidate(item_id: int, title: str, date: str, time: str | None, item_type: str = "TASK") -> dict[str, Any]:
    return {"item_id": item_id, "title": title, "date": date, "time": time, "type": item_type}


records: list[dict[str, Any]] = []
group_numbers: Counter[str] = Counter()


def add_group(
    category: str,
    inputs: dict[str, str],
    tool: str,
    arguments: dict[str, Any],
    *,
    candidates: list[dict[str, Any]] | None = None,
    history: list[dict[str, str]] | None = None,
    free_text_arguments: list[str] | None = None,
) -> None:
    assert tuple(inputs) == STYLES
    group_numbers[category] += 1
    group = group_numbers[category]
    for style, input_text in inputs.items():
        expected_arguments = {
            key: input_text if value == "$INPUT" else value
            for key, value in arguments.items()
        }
        context = {**BASE_CONTEXT, "candidate_items": candidates or []}
        if history:
            context["history"] = history
        expected: dict[str, Any] = {
            "tool": tool,
            "arguments": expected_arguments,
            "required_arguments": list(expected_arguments),
        }
        if free_text_arguments:
            expected["required_arguments"] += free_text_arguments
            expected["free_text_arguments"] = free_text_arguments
        records.append(
            {
                "id": f"{category}-{group:02d}-{style}",
                "input": input_text,
                "expected": expected,
                "category": category,
                "style": style,
                "context": context,
            }
        )


# Create: nine distinct intents, each expressed in all five input styles.
add_group("create", variants(
    "Dodaj mi jutro dentystę o 10.", "ej wrzuć mi jutro dentystę na 10", "dodaj mi jutro dentyste o 10", "dodaj mi jutro dentysye o 10", "ustaw mi na jutro wizytę u dentysty na godzinę dziesiątą",
), "create_item", {"title": "Dentysta", "type": "TASK", "date": "2026-09-04", "time": "10:00", "all_day": False})
add_group("create", variants(
    "Dodaj kolację z Anią w sobotę o 18:00.", "w sobotę o 18 kolacja z Anią, dodaj", "dodaj kolacje z Ania w sobote o 18", "dodaj kolacje z Anią w sobte o 18", "dodaj wydarzenie kolacja z Anią w sobotę na godzinę osiemnastą",
), "create_item", {"title": "Kolacja z Anią", "type": "EVENT", "date": "2026-09-05", "time": "18:00", "all_day": False})
add_group("create", variants(
    "Zapisz urodziny Eli 15 września.", "wrzuć urodziny Eli na 15 września", "zapisz urodziny Eli 15 wrzesnia", "zapisz urodizny Eli 15 września", "dodaj całodniowe urodziny Eli piętnastego września",
), "create_item", {"title": "Urodziny Eli", "type": "BIRTHDAY", "date": "2026-09-15", "all_day": True})
add_group("create", variants(
    "Dodaj na dziś zadanie: kupić mleko.", "na dziś wrzuć kupić mleko", "dodaj na dzis kupic mleko", "dodaj na dziś kupić mlekp", "utwórz na dzisiaj całodniowe zadanie kupić mleko",
), "create_item", {"title": "Kupić mleko", "type": "TASK", "date": "2026-09-03", "all_day": True})
add_group("create", variants(
    "Utwórz spotkanie projektowe w poniedziałek o 14:30.", "poniedziałek wpół do trzeciej spotkanie projektowe", "utworz spotkanie projektowe w poniedzialek o 14 30", "utwórz spotkanie projektowe w poniedizałek o 14:30", "dodaj wydarzenie spotkanie projektowe w najbliższy poniedziałek na godzinę czternastą trzydzieści",
), "create_item", {"title": "Spotkanie projektowe", "type": "EVENT", "date": "2026-09-07", "time": "14:30", "all_day": False})
add_group("create", variants(
    "Dodaj wizytę u lekarza 10 września o 8:15.", "10 września 8:15 lekarz, dopisz", "dodaj lekarza 10 wrzesnia o 8 15", "dodaj wizyte u lekzrza 10 września o 8:15", "zapisz wizytę u lekarza dziesiątego września na ósmą piętnaście rano",
), "create_item", {"title": "Wizyta u lekarza", "type": "EVENT", "date": "2026-09-10", "time": "08:15", "all_day": False})
add_group("create", variants(
    "Dodaj jutro o 19:00 zadanie zadzwonić do mamy.", "jutro 19 telefon do mamy", "jutro o 19 dodaj zadzwonic do mamy", "jutro o 19 zadzwonić do mamy dodja", "utwórz na jutro na godzinę dziewiętnastą zadanie zadzwonić do mamy",
), "create_item", {"title": "Zadzwonić do mamy", "type": "TASK", "date": "2026-09-04", "time": "19:00", "all_day": False})
add_group("create", variants(
    "Dodaj koncert 3 października o 20:00.", "3 października 20 koncert, wrzuć", "dodaj koncert 3 pazdziernika o 20", "dodaj konert 3 października o 20", "zapisz wydarzenie koncert trzeciego października na godzinę dwudziestą",
), "create_item", {"title": "Koncert", "type": "EVENT", "date": "2026-10-03", "time": "20:00", "all_day": False})
add_group("create", variants(
    "Utwórz całodniowe zadanie „Wysłać wniosek” na 30 września.", "30 września deadline: wysłać wniosek", "utworz na 30 wrzesnia zadanie wyslac wniosek", "utwórz zadanie wysłać wniosek na 30 wrzesnia", "dodaj całodniowe zadanie wysłać wniosek z terminem trzydziestego września",
), "create_item", {"title": "Wysłać wniosek", "type": "TASK", "date": "2026-09-30", "all_day": True})

# Recurrence.
add_group("recurrence", variants(
    "Dodaj siłownię w każdy wtorek i czwartek o 17:00.", "we wtorki i czwartki siłka o 17", "we wtorki i czwartki silka o 17", "we wtorki i czwartki siłka o 17t", "ustaw cykliczne zadanie siłownia w każdy wtorek i czwartek na godzinę siedemnastą",
), "create_item", {"title": "Siłownia", "type": "TASK", "date": "2026-09-03", "time": "17:00", "all_day": False, "recurrence": {"frequency": "WEEKLY", "interval": 1, "weekdays": ["TUESDAY", "THURSDAY"]}})
add_group("recurrence", variants(
    "Codziennie o 8:00 przypisz mi zadanie wziąć leki.", "codziennie 8 leki", "codziennie o 8 wziac leki", "codziennie o 8 wziąć lekii", "dodaj powtarzające się codziennie o godzinie ósmej zadanie wziąć leki",
), "create_item", {"title": "Wziąć leki", "type": "TASK", "date": "2026-09-03", "time": "08:00", "all_day": False, "recurrence": {"frequency": "DAILY", "interval": 1}})
add_group("recurrence", variants(
    "Dodaj opłatę czynszu co miesiąc, dziesiątego dnia miesiąca.", "co miesiąc 10 czynsz", "co miesiac 10 dnia czynsz", "co miesiąć dziesiątego czynsz", "utwórz całodniowe zadanie opłacić czynsz powtarzane co miesiąc dziesiątego",
), "create_item", {"title": "Opłacić czynsz", "type": "TASK", "date": "2026-09-10", "all_day": True, "recurrence": {"frequency": "MONTHLY", "interval": 1}})
add_group("recurrence", variants(
    "Zapisz urodziny Marka 21 września i powtarzaj co roku.", "21 września urodziny Marka, co roku", "urodziny Marka 21 wrzesnia co roku", "urodziny Marka 21 września co rku", "dodaj całodniowe urodziny Marka dwudziestego pierwszego września powtarzane każdego roku",
), "create_item", {"title": "Urodziny Marka", "type": "BIRTHDAY", "date": "2026-09-21", "all_day": True, "recurrence": {"frequency": "YEARLY", "interval": 1}})
add_group("recurrence", variants(
    "Od jutra podlewaj kwiaty co 2 dni.", "od jutra kwiaty co drugi dzień", "od jutra podlewaj kwiaty co 2 dni", "od jutra podlewaj kwiaty co 2 dno", "utwórz od jutra całodniowe zadanie podlewać kwiaty powtarzane co dwa dni",
), "create_item", {"title": "Podlewać kwiaty", "type": "TASK", "date": "2026-09-04", "all_day": True, "recurrence": {"frequency": "DAILY", "interval": 2}})
add_group("recurrence", variants(
    "W każdy poniedziałek o 9:00 dodaj planowanie tygodnia.", "poniedziałki 9 plan tygodnia", "w kazdy poniedzialek o 9 planowanie tygodnia", "w każdy poniedziałęk o 9 planowanie tygodnia", "dodaj cykliczne spotkanie planowanie tygodnia w każdy poniedziałek na godzinę dziewiątą",
), "create_item", {"title": "Planowanie tygodnia", "type": "EVENT", "date": "2026-09-07", "time": "09:00", "all_day": False, "recurrence": {"frequency": "WEEKLY", "interval": 1, "weekdays": ["MONDAY"]}})
add_group("recurrence", variants(
    "Dodaj rocznicę 12 maja, powtarzaną co roku.", "12 maja rocznica co rok", "dodaj rocznice 12 maja co roku", "dodaj rocznicę 12 mjaa co roku", "utwórz całodniowe wydarzenie rocznica dwunastego maja i powtarzaj je każdego roku",
), "create_item", {"title": "Rocznica", "type": "EVENT", "date": "2027-05-12", "all_day": True, "recurrence": {"frequency": "YEARLY", "interval": 1}})

# Update/reschedule. Candidate IDs are deterministic and supplied to the model.
c101 = [candidate(101, "Dentysta", "2026-09-04", "10:00")]
add_group("update", variants(
    "Jednak przesuń to o godzinę później.", "dobra, daj to godzinę później", "jednak przesun to o godzine", "jednak przesuń to o godiznę", "zmień poprzednio dodaną wizytę u dentysty z dziesiątej na godzinę jedenastą",
), "update_item", {"item_id": 101, "time": "11:00"}, candidates=c101, history=[{"role": "user", "content": "Dodaj mi jutro dentystę o 10."}, {"role": "assistant", "content": "Dodałem dentystę jutro o 10:00."}])
c102 = [candidate(102, "Siłownia", "2026-09-04", "17:00")]
add_group("update", variants(
    "Przenieś siłownię z jutra na poniedziałek na 18:00.", "siłkę z jutra przełóż na poniedziałek 18", "przenies silownie z jutra na poniedzialek na 18", "przenieś siłownię z jutra na poniedizałek 18", "zmień termin siłowni z jutra z siedemnastej na poniedziałek na godzinę osiemnastą",
), "update_item", {"item_id": 102, "date": "2026-09-07", "time": "18:00"}, candidates=c102)
c103 = [candidate(103, "Projekt", "2026-09-30", None)]
add_group("update", variants(
    "Zmień nazwę zadania Projekt na Oddać projekt.", "projekt przemianuj na oddać projekt", "zmien nazwe Projekt na Oddac projekt", "zmień nazwę Projekt na Oddać projket", "zmień tytuł zadania projekt tak aby nowy tytuł brzmiał oddać projekt",
), "update_item", {"item_id": 103, "title": "Oddać projekt"}, candidates=c103)
c104 = [candidate(104, "Spotkanie zespołu", "2026-09-03", "14:00", "EVENT")]
add_group("update", variants(
    "Dodaj do spotkania zespołu opis: online przez Meet.", "do spotkania zespołu dopisz online przez Meet", "dodaj do spotkania zespolu opis online przez Meet", "dodaj do spotkania zespołu ops online przez Meet", "uzupełnij opis spotkania zespołu tekstem online przez Meet",
), "update_item", {"item_id": 104, "description": "Online przez Meet"}, candidates=c104)
c105 = [candidate(105, "Rocznica", "2026-09-10", None, "TASK")]
add_group("update", variants(
    "Zmień Rocznicę na wydarzenie o 20:00.", "rocznicę zrób jako wydarzenie na 20", "zmien Rocznice na wydarzenie o 20", "zmień Rocznicę na wydarznie o 20", "ustaw element rocznica jako wydarzenie z godziną dwudziestą i wyłącz tryb całodniowy",
), "update_item", {"item_id": 105, "type": "EVENT", "time": "20:00", "all_day": False}, candidates=c105)
c106 = [candidate(106, "Trening", "2026-09-03", "07:00")]
add_group("update", variants(
    "Od teraz ustaw Trening codziennie.", "trening ma być teraz codziennie", "od teraz ustaw Trening codziennie", "od teraz ustaw Trening codzinnie", "zmień powtarzanie zadania trening tak aby odbywało się codziennie",
), "update_item", {"item_id": 106, "recurrence": {"frequency": "DAILY", "interval": 1}}, candidates=c106)

# List and read.
add_group("list", variants(
    "Pokaż otwarte zadania na dziś.", "co mam dziś otwarte", "pokaz otwarte zadania na dzis", "pokaż otwatre zadania na dziś", "wyświetl wszystkie niewykonane zadania zaplanowane na dzisiaj",
), "list_items", {"from": "2026-09-03", "to": "2026-09-03", "type": "TASK", "status": "OPEN"})
add_group("list", variants(
    "Co mam zaplanowane na jutro?", "co jest jutro", "co mam na jutro", "co mam zaplanowane na jutroo", "wyświetl wszystkie elementy planera zaplanowane na jutrzejszy dzień",
), "list_items", {"from": "2026-09-04", "to": "2026-09-04"})
add_group("list", variants(
    "Pokaż wydarzenia w ten weekend.", "jakie eventy mam w weekend", "pokaz wydarzenia w ten weekend", "pokaż wydarzenia w ten weekned", "wyświetl wydarzenia od soboty piątego września do niedzieli szóstego września",
), "list_items", {"from": "2026-09-05", "to": "2026-09-06", "type": "EVENT"})
add_group("list", variants(
    "Pokaż wykonane zadania w przyszłym tygodniu.", "co odhaczone w następnym tygodniu", "pokaz wykonane zadania w przyszlym tygodniu", "pokaż wykonane zadania w przysżłym tygodniu", "wyświetl ukończone zadania od poniedziałku siódmego do niedzieli trzynastego września",
), "list_items", {"from": "2026-09-07", "to": "2026-09-13", "type": "TASK", "status": "COMPLETED"})
c201 = [candidate(201, "Dentysta", "2026-09-04", "10:00")]
add_group("read", variants(
    "Pokaż szczegóły wizyty Dentysta.", "otwórz dentystę", "pokaz szczegoly Dentysty", "pokaż szczegóły Dentysyt", "wyświetl wszystkie szczegóły elementu dentysta zaplanowanego na jutro",
), "get_item", {"item_id": 201}, candidates=c201)
c202 = [candidate(202, "Koncert", "2026-10-03", "20:00", "EVENT")]
add_group("read", variants(
    "O której jest zapisany Koncert?", "pokaż mi ten koncert", "o ktorej jest Koncert", "o której jest Konert", "odczytaj szczegóły zapisanego wydarzenia koncert trzeciego października",
), "get_item", {"item_id": 202}, candidates=c202)

# Search.
add_group("search", variants(
    "Znajdź wpisy zawierające słowo dentysta.", "wyszukaj dentystę", "znajdz wpisy z dentysta", "znajdź wpisy z dentysat", "przeszukaj tytuły i opisy planera pod kątem słowa dentysta",
), "search_items", {"query": "dentysta"})
add_group("search", variants(
    "Wyszukaj projekt między 1 a 30 września.", "szukaj projektu we wrześniu", "wyszukaj projekt od 1 do 30 wrzesnia", "wyszukaj projket we wrześniu", "znajdź elementy ze słowem projekt w okresie od pierwszego do trzydziestego września",
), "search_items", {"query": "projekt", "from": "2026-09-01", "to": "2026-09-30"})
add_group("search", variants(
    "Znajdź wszystkie wpisy z frazą Anna.", "poszukaj Anny w planerze", "znajdz wpisy z Anna", "znajdź wpisy z frazą Anan", "wyszukaj w tytułach i opisach planera frazę Anna",
), "search_items", {"query": "Anna"})
add_group("search", variants(
    "Wyszukaj bilety na pociąg.", "znajdź bilety pociąg", "wyszukaj bilety na pociag", "wyszukaj bilety na pocąig", "przeszukaj planer używając frazy bilety na pociąg",
), "search_items", {"query": "bilety na pociąg"})
add_group("search", variants(
    "Znajdź fakturę w tym miesiącu.", "szukaj faktury we wrześniu", "znajdz fakture w tym miesiacu", "znajdź fakturę w tym miesąicu", "wyszukaj frazę faktura od pierwszego do trzydziestego września",
), "search_items", {"query": "faktura", "from": "2026-09-01", "to": "2026-09-30"})

# Completion/reopen.
c301 = [candidate(301, "Dentysta", "2026-09-04", "10:00")]
add_group("completion", variants(
    "Oznacz jutrzejszego Dentystę jako wykonane.", "odhacz dentystę na jutro", "oznacz jutrzejszego Dentyste jako wykonane", "oznacz jutrzejszego Dentystę jako wykonne", "ustaw wystąpienie zadania dentysta z czwartego września jako ukończone",
), "set_completed", {"item_id": 301, "occurrence_date": "2026-09-04", "completed": True}, candidates=c301)
c302 = [candidate(302, "Siłownia", "2026-09-03", "17:00")]
add_group("completion", variants(
    "Przywróć dzisiejszą Siłownię jako niewykonaną.", "cofnij odhaczenie dzisiejszej siłki", "przywroc dzisiejsza Silownie jako niewykonana", "przywróć dzisiejszą Siłownię jako niewykonanną", "oznacz dzisiejsze wystąpienie zadania siłownia ponownie jako otwarte",
), "set_completed", {"item_id": 302, "occurrence_date": "2026-09-03", "completed": False}, candidates=c302)
c303 = [candidate(303, "Zakupy", "2026-09-03", None)]
add_group("completion", variants(
    "Zakończ zadanie Zakupy na dziś.", "zakupy zrobione", "zakoncz zadanie Zakupy na dzis", "zakończ zadanie Zakpuy", "oznacz dzisiejsze zadanie zakupy jako wykonane",
), "set_completed", {"item_id": 303, "occurrence_date": "2026-09-03", "completed": True}, candidates=c303)
c304 = [candidate(304, "Wziąć leki", "2026-09-04", "08:00")]
add_group("completion", variants(
    "Odhacz leki na jutro.", "jutrzejsze leki zrobione", "odhacz leki na jutro", "odhacz lekii na jutro", "oznacz wystąpienie zadania wziąć leki przypadające jutro jako ukończone",
), "set_completed", {"item_id": 304, "occurrence_date": "2026-09-04", "completed": True}, candidates=c304)

# Unambiguous deletes.
c401 = [candidate(401, "Stare notatki", "2026-09-03", None)]
add_group("delete", variants(
    "Usuń zadanie Stare notatki.", "wywal stare notatki", "usun zadanie Stare notatki", "usuń zadanie Stare nottaki", "usuń jednoznacznie wskazane zadanie stare notatki",
), "delete_item", {"item_id": 401}, candidates=c401)
c402 = [candidate(402, "Koncert", "2026-10-03", "20:00", "EVENT")]
add_group("delete", variants(
    "Usuń wydarzenie Koncert 3 października.", "skasuj koncert z 3 października", "usun koncert 3 pazdziernika", "usuń konert 3 października", "usuń wydarzenie koncert zaplanowane trzeciego października",
), "delete_item", {"item_id": 402}, candidates=c402)
c403 = [candidate(403, "Dentysta", "2026-09-04", "10:00")]
add_group("delete", variants(
    "Anuluj jutrzejszą wizytę u dentysty.", "wywal dentystę z jutra", "anuluj jutrzejsza wizyte u dentysty", "anuluj jutrzejszą wizytę u dentysyt", "usuń z planera wizytę u dentysty zaplanowaną na jutro",
), "delete_item", {"item_id": 403}, candidates=c403)

# Reminder-specific creates and updates.
add_group("reminder", variants(
    "Dodaj mi jutro dentystę o 10 i przypomnij 30 minut wcześniej.", "jutro 10 dentysta, przypomnij pół godziny przed", "jutro dentysta o 10 przypomnij 30 minut wczesniej", "jutro dentysya o 10 przypomnij 30 minut wcześniej", "ustaw mi jutro dentystę na godzinę dziesiątą i przypomnij pół godziny wcześniej",
), "create_item", {"title": "Dentysta", "type": "TASK", "date": "2026-09-04", "time": "10:00", "all_day": False, "reminder_minutes_before": 30})
add_group("reminder", variants(
    "Dodaj spotkanie w poniedziałek o 9:00 z przypomnieniem 10 minut wcześniej.", "poniedziałek 9 spotkanie, alarm 10 min przed", "dodaj spotkanie w poniedzialek o 9 przypomnij 10 minut wczesniej", "dodaj spotkanie w poniedziałek o 9 z przypomneniem 10 minut przed", "utwórz wydarzenie spotkanie w poniedziałek o dziewiątej i ustaw przypomnienie dziesięć minut wcześniej",
), "create_item", {"title": "Spotkanie", "type": "EVENT", "date": "2026-09-07", "time": "09:00", "all_day": False, "reminder_minutes_before": 10})
c404 = [candidate(404, "Badanie", "2026-09-10", "08:00", "EVENT")]
add_group("reminder", variants(
    "Ustaw dla Badania przypomnienie godzinę wcześniej.", "badanie alarm godzinę przed", "ustaw dla Badania przypomnienie godzine wczesniej", "ustaw dla Badania przypomninie godzinę wcześniej", "zmień wydarzenie badanie tak aby przypomnienie pojawiło się sześćdziesiąt minut przed terminem",
), "update_item", {"item_id": 404, "reminder_minutes_before": 60}, candidates=c404)
c405 = [candidate(405, "Spotkanie z Olą", "2026-09-05", "12:00", "EVENT")]
add_group("reminder", variants(
    "Usuń przypomnienie ze Spotkania z Olą.", "wyłącz alarm przy spotkaniu z Olą", "usun przypomnienie ze Spotkania z Ola", "usuń przypomnienie ze Spotkania z Ołą", "wyłącz istniejące przypomnienie dla wydarzenia spotkanie z Olą",
), "update_item", {"item_id": 405, "remove_reminder": True}, candidates=c405)

# Ambiguous commands must never mutate planner state.
c501 = [candidate(501, "Spotkanie z Olą", "2026-09-04", "10:00", "EVENT"), candidate(502, "Spotkanie zespołu", "2026-09-04", "14:00", "EVENT")]
add_group("ambiguous", variants(
    "Usuń spotkanie.", "skasuj to spotkanie", "usun spotkanie", "usuń spotknie", "usuń spotkanie ale nie podaję które z dwóch jutrzejszych spotkań",
), "request_clarification", {"candidate_item_ids": [501, 502]}, candidates=c501, free_text_arguments=["question", "reason"])
c502 = [candidate(503, "Dentysta kontrola", "2026-09-04", "10:00"), candidate(504, "Dentysta leczenie", "2026-09-08", "16:00")]
add_group("ambiguous", variants(
    "Przenieś dentystę na 12:00.", "dentystę daj na 12", "przenies dentyste na 12", "przenieś dentysę na 12", "zmień godzinę dentysty na dwunastą ale mam zapisane dwie różne wizyty",
), "request_clarification", {"candidate_item_ids": [503, 504]}, candidates=c502, free_text_arguments=["question", "reason"])
c503 = [candidate(505, "Zakupy", "2026-09-03", None), candidate(506, "Raport", "2026-09-03", None)]
add_group("ambiguous", variants(
    "Oznacz zadanie jako zrobione.", "odhacz zadanie", "oznacz zadanie jako zrobione", "oznacz zadnie jako zrobione", "oznacz jedno z dzisiejszych zadań jako wykonane ale nie wskazuję które",
), "request_clarification", {"candidate_item_ids": [505, 506]}, candidates=c503, free_text_arguments=["question", "reason"])
c504 = [candidate(507, "Rozmowa z bankiem", "2026-09-04", "11:00"), candidate(508, "Odbiór paczki", "2026-09-04", "16:00")]
add_group("ambiguous", variants(
    "Jednak przesuń to o godzinę.", "przesuń to godzinę później", "no i przesun ten wpis o godzine", "jednak przesuń ten wpis o godiznę", "przesuń poprzednio omawiany element o jedną godzinę ale rozmowa nie wskazuje jednoznacznego elementu",
), "request_clarification", {"candidate_item_ids": [507, 508]}, candidates=c504, history=[{"role": "user", "content": "Co mam jutro?"}, {"role": "assistant", "content": "Rozmowę z bankiem o 11:00 i odbiór paczki o 16:00."}], free_text_arguments=["question", "reason"])
c505 = [candidate(509, "Lekarz", "2026-09-10", "08:00", "EVENT")]
add_group("ambiguous", variants(
    "Ustaw przypomnienie dla Lekarza.", "dodaj alarm do lekarza", "ustaw przypomnienie dla Lekarza", "ustaw przypomnienie dla Lekrza", "dodaj przypomnienie do wizyty u lekarza ale nie podaję ile czasu wcześniej",
), "request_clarification", {"candidate_item_ids": [509]}, candidates=c505, free_text_arguments=["question", "reason"])

# General-chat routing. The exact original request must be preserved.
add_group("delegate", variants(
    "Jak działa transformator?", "ej, o co chodzi z transformatorami w AI", "jak dziala transformator", "jak działa transfomrator", "wyjaśnij mi proszę jak działa architektura transformera w sztucznej inteligencji",
), "delegate_to_chat", {"user_request": "$INPUT"})
add_group("delegate", variants(
    "Podaj pomysł na szybką kolację.", "co szybko zjeść na kolację", "podaj pomysl na szybka kolacje", "podaj pomysł na szybką kolcaję", "zaproponuj mi proszę prosty przepis na szybką kolację",
), "delegate_to_chat", {"user_request": "$INPUT"})
add_group("delegate", variants(
    "Przetłumacz „good morning” na polski.", "jak jest good morning po polsku", "przetlumacz good morning na polski", "przetłumacz god morning na polski", "powiedz jak przetłumaczyć angielskie wyrażenie good morning na język polski",
), "delegate_to_chat", {"user_request": "$INPUT"})
add_group("delegate", variants(
    "Dlaczego niebo jest niebieskie?", "czemu niebo jest niebieskie", "dlaczego niebo jest niebieskie", "dlaczego niebo jest niebieskei", "wyjaśnij z czego wynika niebieski kolor nieba w ciągu dnia",
), "delegate_to_chat", {"user_request": "$INPUT"})
add_group("delegate", variants(
    "Ile wynosi 17 razy 24?", "policz mi 17 razy 24", "ile wynosi 17 razy 24", "ile wynosi 17 rzy 24", "oblicz proszę iloczyn liczby siedemnaście i liczby dwadzieścia cztery",
), "delegate_to_chat", {"user_request": "$INPUT"})

# Statistics.
add_group("statistics", variants(
    "Pokaż statystyki z tego tygodnia.", "jak mi idzie w tym tygodniu", "pokaz statystyki z tego tygodnia", "pokaż statystyki z tego tyogdnia", "policz wykonanie zadań od poniedziałku trzydziestego pierwszego sierpnia do niedzieli szóstego września",
), "get_statistics", {"from": "2026-08-31", "to": "2026-09-06"})
add_group("statistics", variants(
    "Jakie miałem statystyki w zeszłym miesiącu?", "pokaż wyniki za sierpień", "jakie mialem statystyki w zeszlym miesiacu", "jakie miałem statystyki w zeszłym miesąicu", "oblicz statystyki ukończenia zadań od pierwszego do trzydziestego pierwszego sierpnia",
), "get_statistics", {"from": "2026-08-01", "to": "2026-08-31"})


def render() -> str:
    assert len(records) == 280
    assert Counter(record["category"] for record in records) == Counter(EXPECTED_CATEGORY_COUNTS)
    assert Counter(record["style"] for record in records) == Counter({style: 56 for style in STYLES})
    assert len({record["id"] for record in records}) == len(records)
    assert len({record["input"] for record in records}) == len(records)
    return "".join(json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n" for record in records)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--check", action="store_true", help="Fail if the committed JSONL differs from this reviewed source.")
    args = parser.parse_args()
    payload = render()
    if args.check:
        if not args.output.exists() or args.output.read_text(encoding="utf-8") != payload:
            raise SystemExit(f"Gold dataset is stale: {args.output}")
        print(f"OK: {len(records)} reviewed cases in {args.output}")
        return 0
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(payload, encoding="utf-8", newline="\n")
    print(f"Wrote {len(records)} reviewed cases to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
