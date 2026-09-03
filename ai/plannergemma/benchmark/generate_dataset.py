"""Generate deterministic, compact Polish planner datasets with disjoint template families."""

from __future__ import annotations

import argparse
import json
import random
import unicodedata
from collections import Counter
from datetime import date, datetime, time, timedelta
from pathlib import Path
from typing import Any


SEED = 20260902
STYLES = ("correct", "colloquial", "no_diacritics", "typo", "stt")
CATEGORY_WEIGHTS = {
    "create": 20,
    "update": 15,
    "list": 12,
    "search": 10,
    "complete": 10,
    "delete": 8,
    "recurrence": 10,
    "statistics": 5,
    "delegate": 7,
    "clarification": 3,
}
TITLES = [
    "Dentysta", "Siłownia", "Pranie", "Spotkanie z Kubą", "Nauka", "Telefon do mamy",
    "Zakupy", "Wizyta kontrolna", "Raport", "Spacer", "Lekarz", "Trening",
]
GENERAL_REQUESTS = [
    "dlaczego niebo jest niebieskie",
    "napisz program w Pythonie",
    "co sądzisz o energetyce jądrowej",
    "wyjaśnij jak działa transformator Tesli",
    "opowiedz mi historię Internetu",
    "jak ugotować dobre risotto",
]
NATURAL_PREFIXES = (
    "", "proszę, ", "na szybko: ", "hej, ", "możesz ", "potrzebuję, żebyś ",
    "jak możesz, ", "w planerze ", "dobra, ", "słuchaj, ", "jeszcze jedno: ", "teraz ",
)
NATURAL_SUFFIXES = (
    "", " proszę", " dzięki", " jeśli możesz", " na spokojnie", " od razu",
    " bo układam dzień", " żebym nie zapomniał", " w moim planie", " na ten moment",
    " i tyle", " okej",
)

TRAIN_TEMPLATES = {
    "create": [
        "dodaj {relative} {title} o {hour}",
        "wpisz mi {relative} o {hour} {title}",
        "przypomnij mi {relative} o {hour} że mam {title}",
        "ustaw {title} {relative} {hour}",
    ],
    "recurrence": [
        "{schedule_pl} {title} o {hour}",
        "ustaw cyklicznie {title} {schedule_pl} na {hour}",
        "powtarzaj {title} {schedule_pl} o {hour}",
    ],
    "list": ["co mam {relative}", "pokaż plan na {relative}", "jak wygląda {relative}"],
    "search": ["znajdź mi {title}", "kiedy mam następne {title}", "wyszukaj {title}"],
    "update": ["przenieś {title} na {hour}", "zmień godzinę {title} na {hour}", "{title} jednak o {hour}"],
    "complete": ["oznacz {title} jako zrobione", "{title} zrobione", "odhacz {title}"],
    "delete": ["usuń {title}", "wywal {title}", "skasuj {title}"],
    "statistics": ["ile tasków zrobiłem w tym tygodniu", "pokaż statystyki tego tygodnia", "jak mi poszło w tym tygodniu"],
    "delegate": ["{general}", "mam pytanie {general}", "możesz powiedzieć {general}"],
    "clarification": ["przenieś to", "usuń spotkanie", "ustaw to później"],
}

TEST_TEMPLATES = {
    "create": [
        "{relative} {title} o {hour}",
        "ej wrzuć mi {relative} {title} {hour}",
        "{title} {relative} {hour} pls",
        "zapisz proszę {title} na {relative} o {hour}",
    ],
    "recurrence": [
        "{schedule_pl} {title} o {hour}",
        "wpisz cyklicznie {title} {schedule_pl} {hour}",
        "niech {title} wpada {schedule_pl} o {hour}",
    ],
    "list": ["co ja mam wgl {relative}", "mam coś {relative}", "rozpiska na {relative}"],
    "search": ["czy mam gdzieś zapisane {title}", "gdzie w planie jest {title}", "następne {title} kiedy"],
    "update": ["jednak {title} na {hour}", "przełóż mi {title} godzina {hour}", "{title} później o {hour}"],
    "complete": ["{title} ogarnięte", "możesz odhaczyć {title}", "skończyłem {title}"],
    "delete": ["wywal mi {title}", "nie chcę już {title}", "usuń z planu {title}"],
    "statistics": ["jaki mam wynik za ten tydzień", "ile ogarnąłem od poniedziałku", "podsumuj mi tydzień"],
    "delegate": ["{general}", "tak w ogóle {general}", "wytłumacz {general}"],
    "clarification": ["przełóż to gdzieś", "usuń ten task", "zrób to później"],
}


def strip_diacritics(value: str) -> str:
    return "".join(char for char in unicodedata.normalize("NFKD", value) if not unicodedata.combining(char))


def stylize(value: str, style: str, index: int) -> str:
    if style == "correct":
        return value[:1].upper() + value[1:] + "."
    if style == "colloquial":
        return f"ej {value} pls" if not value.startswith("ej ") else f"{value} no"
    if style == "no_diacritics":
        return strip_diacritics(value)
    if style == "typo":
        replacements = (("dentysta", "dentysya"), ("siłownia", "silownai"), ("przenieś", "przeluz"), ("usuń", "usn"), ("jutro", "jtro"))
        result = value
        for source, target in replacements:
            if source in result.lower():
                result = result.lower().replace(source, target)
                break
        else:
            pos = min(max(2, index % max(3, len(result) - 1)), len(result) - 1)
            result = result[:pos] + result[pos + 1 :]
        return result
    if style == "stt":
        return value.lower().replace(":", " ").replace(",", "").replace(".", "")
    raise ValueError(style)


def context_for(index: int, split: str, holdout: bool) -> dict[str, Any]:
    if holdout:
        current = date(2026, 9, 2)
    else:
        split_base = date(2020, 1, 1) if split == "train" else date(2032, 1, 1)
        current = split_base + timedelta(days=(index * 17) % 3_650)
    current_time = time(21, 55) if holdout else time((7 + index * 3) % 24, (index * 5) % 60)
    return {
        "current_date": current.isoformat(),
        "current_time": current_time.strftime("%H:%M"),
        "timezone": "Europe/Warsaw",
        "locale": "pl-PL",
        "selected_date": current.isoformat(),
        "candidate_items": [],
    }


def relative_date(current: date, index: int) -> tuple[str, date]:
    target = current + timedelta(days=(0, 1, 2, 7, 3, 5, 4)[index % 7])
    month_names = (
        "stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca",
        "lipca", "sierpnia", "września", "października", "listopada", "grudnia",
    )
    weekday_names = ("poniedziałek", "wtorek", "środę", "czwartek", "piątek", "sobotę", "niedzielę")
    labels = (
        "dzisiaj",
        "jutro",
        "pojutrze",
        "za tydzień",
        target.strftime("%d.%m.%Y"),
        f"{target.day} {month_names[target.month - 1]}",
        f"w {weekday_names[target.weekday()]}",
    )
    return labels[index % len(labels)], target


def build_case(index: int, split: str, category: str, holdout: bool) -> dict[str, Any]:
    context = context_for(index, split, holdout)
    current = date.fromisoformat(context["current_date"])
    style = STYLES[index % len(STYLES)]
    title = TITLES[(index * 5 + (3 if holdout else 0)) % len(TITLES)]
    hour_value = 8 + (index * 3) % 13
    hour = f"{hour_value:02d}:00"
    spoken_hours = {8: "ósmej", 9: "dziewiątej", 10: "dziesiątej", 11: "jedenastej", 12: "dwunastej", 17: "siedemnastej", 18: "osiemnastej", 20: "dwudziestej"}
    input_hour = spoken_hours.get(hour_value, hour) if index % 9 == 4 else hour
    relative, target_date = relative_date(current, index + (1 if holdout else 0))
    template = (TEST_TEMPLATES if holdout else TRAIN_TEMPLATES)[category][index % len((TEST_TEMPLATES if holdout else TRAIN_TEMPLATES)[category])]
    expected_name: str
    expected_args: dict[str, Any]
    fields = {"relative": relative, "title": title.lower(), "hour": input_hour, "general": GENERAL_REQUESTS[index % len(GENERAL_REQUESTS)]}

    if category == "create":
        reminder = 30 if index % 4 == 0 else None
        if reminder is not None:
            template += " i przypomnij 30 minut wcześniej"
        expected_name = "create_item"
        expected_args = {
            "title": title,
            "type": "TASK",
            "date": target_date.isoformat(),
            "time": hour,
            "all_day": False,
        }
        if reminder is not None:
            expected_args["reminder_minutes_before"] = reminder
    elif category == "recurrence":
        recurrence_options = [
            ("WEEKLY", ["TUESDAY", "THURSDAY"], "we wtorki i czwartki", current, "TASK"),
            ("WEEKLY", ["MONDAY", "WEDNESDAY", "FRIDAY"], "w poniedziałki środy i piątki", current, "TASK"),
            ("DAILY", [], "codziennie", current, "TASK"),
            ("MONTHLY", [], "pierwszego dnia każdego miesiąca", current.replace(day=1), "TASK"),
            ("YEARLY", [], "co roku 7 maja", date(current.year if current <= date(current.year, 5, 7) else current.year + 1, 5, 7), "BIRTHDAY"),
        ]
        frequency, weekdays, schedule_pl, start_date, item_type = recurrence_options[index % len(recurrence_options)]
        fields["schedule_pl"] = schedule_pl
        expected_name = "create_item"
        expected_args = {
            "title": title,
            "type": item_type,
            "date": start_date.isoformat(),
            "time": hour,
            "all_day": False,
            "recurrence": {"frequency": frequency, "interval": 1},
        }
        if weekdays:
            expected_args["recurrence"]["weekdays"] = weekdays
    elif category == "list":
        expected_name = "list_items"
        if index % 4 == 3:
            fields["relative"] = "następny tydzień"
            start = current + timedelta(days=(7 - current.weekday()))
            end = start + timedelta(days=6)
        else:
            start = end = target_date
        expected_args = {"from": start.isoformat(), "to": end.isoformat()}
    elif category == "search":
        expected_name = "search_items"
        expected_args = {"query": title}
    elif category in {"update", "complete", "delete"}:
        item_id = 1000 + (index % 50)
        item_date = current + timedelta(days=1)
        context["candidate_items"] = [{"item_id": item_id, "title": title, "date": item_date.isoformat(), "time": "10:00", "type": "TASK"}]
        if category == "update":
            expected_name = "update_item"
            expected_args = {"item_id": item_id, "time": hour, "all_day": False}
        elif category == "complete":
            expected_name = "set_completed"
            expected_args = {"item_id": item_id, "occurrence_date": item_date.isoformat(), "completed": True}
        else:
            expected_name = "delete_item"
            expected_args = {"item_id": item_id}
    elif category == "statistics":
        start = current - timedelta(days=current.weekday())
        expected_name = "get_statistics"
        expected_args = {"from": start.isoformat(), "to": (start + timedelta(days=6)).isoformat()}
    elif category == "delegate":
        expected_name = "delegate_to_chat"
        expected_args = {"user_request": fields["general"]}
    elif category == "clarification":
        if index % 3 == 1:
            context["candidate_items"] = [
                {"item_id": 41, "title": "Spotkanie zespołu", "date": (current + timedelta(days=1)).isoformat(), "type": "EVENT"},
                {"item_id": 42, "title": "Spotkanie z Kubą", "date": (current + timedelta(days=2)).isoformat(), "type": "EVENT"},
            ]
        expected_name = "request_clarification"
        expected_args = {
            "question": "Który element masz na myśli?",
            "reason": "Nie można jednoznacznie wskazać elementu lub zmiany.",
        }
        if context["candidate_items"]:
            expected_args["candidate_item_ids"] = [item["item_id"] for item in context["candidate_items"]]
    else:
        raise ValueError(category)

    rendered = template.format(**fields)
    prefix = NATURAL_PREFIXES[(index // 7) % len(NATURAL_PREFIXES)]
    suffix = NATURAL_SUFFIXES[(index // 11) % len(NATURAL_SUFFIXES)]
    prompt = stylize(f"{prefix}{rendered}{suffix}".strip(), style, index)
    return {
        "id": f"tiqo-{split}-{index:05d}",
        "schema_version": "tiqo-tools-v1",
        "split": split,
        "category": category,
        "style": style,
        "template_family": "holdout" if holdout else "training",
        "prompt": prompt,
        "context": context,
        "expected": {"tool": expected_name, "arguments": expected_args},
    }


def category_schedule(size: int) -> list[str]:
    weighted = [category for category, weight in CATEGORY_WEIGHTS.items() for _ in range(weight)]
    return [weighted[index % len(weighted)] for index in range(size)]


def generate(size: int, split: str, holdout: bool) -> list[dict[str, Any]]:
    rows = [build_case(index, split, category, holdout) for index, category in enumerate(category_schedule(size))]
    random.Random(SEED + len(split)).shuffle(rows)
    return rows


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path, default=Path(__file__).resolve().parents[1] / "datasets")
    parser.add_argument("--train-size", type=int, default=10_000)
    parser.add_argument("--validation-size", type=int, default=1_000)
    parser.add_argument("--test-size", type=int, default=720)
    args = parser.parse_args()

    outputs = {
        "train": generate(args.train_size, "train", holdout=False),
        "validation": generate(args.validation_size, "validation", holdout=False),
        "test": generate(args.test_size, "test", holdout=True),
    }
    for split, rows in outputs.items():
        write_jsonl(args.output_dir / f"planner_pl_v1_{split}.jsonl", rows)
        print(split, len(rows), dict(sorted(Counter(row["category"] for row in rows).items())))
    manifest = {
        "version": "planner-pl-v1",
        "seed": SEED,
        "sizes": {split: len(rows) for split, rows in outputs.items()},
        "styles": list(STYLES),
        "category_weights": CATEGORY_WEIGHTS,
        "test_template_family": "holdout",
    }
    (args.output_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
