"""Programmatic validation and exact deduplication for Tiqo planner datasets."""

from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path
from typing import Any, Iterable

from .schema import validate_tool_call


REQUIRED_ROW_KEYS = {
    "id", "schema_version", "split", "category", "style", "template_family", "prompt", "context", "expected",
}


def read_jsonl(path: Path) -> Iterable[tuple[int, dict[str, Any]]]:
    with path.open(encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, 1):
            if line.strip():
                yield line_number, json.loads(line)


def validate(paths: list[Path]) -> dict[str, Any]:
    errors: list[str] = []
    ids: set[str] = set()
    fingerprints: dict[str, str] = {}
    counts: Counter[str] = Counter()
    styles: Counter[str] = Counter()
    recurrence_frequencies: Counter[str] = Counter()
    total = 0
    families_by_split: dict[str, set[str]] = {}

    for path in paths:
        for line_number, row in read_jsonl(path):
            total += 1
            prefix = f"{path.name}:{line_number}"
            missing = REQUIRED_ROW_KEYS - set(row)
            if missing:
                errors.append(f"{prefix}: missing row keys {sorted(missing)}")
                continue
            if row["id"] in ids:
                errors.append(f"{prefix}: duplicate id {row['id']}")
            ids.add(row["id"])
            if not isinstance(row["prompt"], str) or not row["prompt"].strip():
                errors.append(f"{prefix}: empty prompt")
            context = row["context"]
            for key in ("current_date", "current_time", "timezone", "locale", "selected_date", "candidate_items"):
                if key not in context:
                    errors.append(f"{prefix}: missing context {key}")
            expected = row["expected"]
            if not isinstance(expected.get("arguments"), dict):
                errors.append(f"{prefix}: expected arguments must be an object")
            else:
                for error in validate_tool_call(expected.get("tool", ""), expected["arguments"]):
                    errors.append(f"{prefix}: {error}")
                recurrence = expected["arguments"].get("recurrence")
                if isinstance(recurrence, dict):
                    recurrence_frequencies[recurrence.get("frequency", "MISSING")] += 1
            canonical = json.dumps(
                {"prompt": row["prompt"].strip().casefold(), "context": context},
                ensure_ascii=False,
                sort_keys=True,
                separators=(",", ":"),
            )
            fingerprint = hashlib.sha256(canonical.encode()).hexdigest()
            if fingerprint in fingerprints:
                errors.append(f"{prefix}: exact duplicate of {fingerprints[fingerprint]}")
            fingerprints[fingerprint] = row["id"]
            counts[row["category"]] += 1
            styles[row["style"]] += 1
            families_by_split.setdefault(row["split"], set()).add(row["template_family"])

    if families_by_split.get("test", set()) & families_by_split.get("train", set()):
        errors.append("test and train use overlapping template families")
    missing_recurrence = {"DAILY", "WEEKLY", "MONTHLY", "YEARLY"} - set(recurrence_frequencies)
    if missing_recurrence:
        errors.append(f"missing recurrence coverage: {sorted(missing_recurrence)}")
    return {
        "valid": not errors,
        "total": total,
        "categories": dict(sorted(counts.items())),
        "styles": dict(sorted(styles.items())),
        "recurrence_frequencies": dict(sorted(recurrence_frequencies.items())),
        "errors": errors,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("paths", nargs="+", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    report = validate(args.paths)
    rendered = json.dumps(report, ensure_ascii=False, indent=2)
    print(rendered)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(rendered + "\n", encoding="utf-8")
    if not report["valid"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
