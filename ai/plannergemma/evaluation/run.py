"""Evaluate FunctionGemma-compatible checkpoints on the held-out Tiqo benchmark."""

from __future__ import annotations

import argparse
import json
import time
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

from ai.plannergemma.benchmark.schema import (
    DATE_KEYS,
    MUTATING_TOOLS,
    SCHEMA_BY_NAME,
    TIME_KEYS,
    TOOL_SCHEMAS,
    developer_message,
    parse_function_call,
    validate_tool_call,
)


def read_rows(path: Path, limit: int | None) -> list[dict[str, Any]]:
    with path.open(encoding="utf-8") as handle:
        rows = [json.loads(line) for line in handle if line.strip()]
    return rows if limit is None else rows[:limit]


def required_arguments_match(name: str, expected: dict[str, Any], actual: dict[str, Any]) -> bool:
    schema = SCHEMA_BY_NAME.get(name, {})
    return all(actual.get(key) == expected.get(key) for key in schema.get("required", []))


def optional_arguments_match(name: str, expected: dict[str, Any], actual: dict[str, Any]) -> bool:
    required = set(SCHEMA_BY_NAME.get(name, {}).get("required", []))
    return all(actual.get(key) == value for key, value in expected.items() if key not in required)


def field_group_matches(keys: set[str], expected: dict[str, Any], actual: dict[str, Any]) -> bool | None:
    present = keys & set(expected)
    return None if not present else all(actual.get(key) == expected[key] for key in present)


def score_row(row: dict[str, Any], parsed: tuple[str, dict[str, Any]] | None) -> dict[str, bool | None]:
    expected_name = row["expected"]["tool"]
    expected_args = row["expected"]["arguments"]
    if parsed is None:
        return {
            "parsed": False, "valid": False, "tool": False, "required": False,
            "optional": False, "full_action": False, "date": None, "time": None,
            "recurrence": None, "delegate": expected_name == "delegate_to_chat" and False,
            "clarification": expected_name == "request_clarification" and False,
            "false_positive_mutation": expected_name not in MUTATING_TOOLS and False,
        }
    actual_name, actual_args = parsed
    tool_match = actual_name == expected_name
    validation_errors = validate_tool_call(actual_name, actual_args)
    required = tool_match and required_arguments_match(expected_name, expected_args, actual_args)
    optional = tool_match and optional_arguments_match(expected_name, expected_args, actual_args)
    recurrence = None
    if "recurrence" in expected_args:
        recurrence = tool_match and actual_args.get("recurrence") == expected_args["recurrence"]
    return {
        "parsed": True,
        "valid": not validation_errors,
        "tool": tool_match,
        "required": required,
        "optional": optional,
        "full_action": tool_match and not validation_errors and actual_args == expected_args,
        "date": tool_match and field_group_matches(DATE_KEYS, expected_args, actual_args),
        "time": tool_match and field_group_matches(TIME_KEYS, expected_args, actual_args),
        "recurrence": recurrence,
        "delegate": None if expected_name != "delegate_to_chat" else tool_match,
        "clarification": None if expected_name != "request_clarification" else tool_match,
        "false_positive_mutation": expected_name not in MUTATING_TOOLS and actual_name in MUTATING_TOOLS,
    }


def rate(values: list[bool | None], *, invert: bool = False) -> float | None:
    selected = [value for value in values if value is not None]
    if not selected:
        return None
    result = sum(bool(value) for value in selected) / len(selected)
    return 1.0 - result if invert else result


def aggregate(scored: list[dict[str, Any]]) -> dict[str, Any]:
    keys = [
        "parsed", "valid", "tool", "required", "optional", "full_action", "date", "time",
        "recurrence", "delegate", "clarification",
    ]
    metrics = {key: rate([row["scores"][key] for row in scored]) for key in keys}
    metrics["malformed_rate"] = rate([row["scores"]["parsed"] for row in scored], invert=True)
    metrics["false_positive_mutation_rate"] = rate(
        [row["scores"]["false_positive_mutation"] for row in scored]
    )
    return metrics


def evaluate_model(model_id: str, rows: list[dict[str, Any]], max_new_tokens: int) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    import torch
    from transformers import AutoModelForCausalLM, AutoTokenizer

    tokenizer = AutoTokenizer.from_pretrained(model_id)
    model = AutoModelForCausalLM.from_pretrained(
        model_id,
        torch_dtype="auto",
        device_map="auto",
    )
    model.eval()
    predictions: list[dict[str, Any]] = []
    latencies: list[float] = []
    for position, row in enumerate(rows, 1):
        messages = [
            {"role": "developer", "content": developer_message(row["context"])},
            {"role": "user", "content": row["prompt"]},
        ]
        encoded = tokenizer.apply_chat_template(
            messages,
            tools=TOOL_SCHEMAS,
            add_generation_prompt=True,
            return_tensors="pt",
            return_dict=True,
        ).to(model.device)
        started = time.perf_counter()
        with torch.inference_mode():
            generated = model.generate(
                **encoded,
                max_new_tokens=max_new_tokens,
                do_sample=False,
                pad_token_id=tokenizer.eos_token_id,
            )
        latency = time.perf_counter() - started
        completion = tokenizer.decode(generated[0, encoded["input_ids"].shape[1]:], skip_special_tokens=False)
        parsed = parse_function_call(completion)
        scores = score_row(row, parsed)
        predictions.append({
            "id": row["id"], "category": row["category"], "style": row["style"],
            "prompt": row["prompt"], "expected": row["expected"], "raw": completion,
            "parsed": None if parsed is None else {"tool": parsed[0], "arguments": parsed[1]},
            "latency_seconds": latency, "scores": scores,
        })
        latencies.append(latency)
        if position % 25 == 0 or position == len(rows):
            print(f"{position}/{len(rows)}")
    summary = {
        "model": model_id,
        "examples": len(rows),
        "metrics": aggregate(predictions),
        "by_category": {
            category: aggregate([row for row in predictions if row["category"] == category])
            for category in sorted({row["category"] for row in predictions})
        },
        "by_style": {
            style: aggregate([row for row in predictions if row["style"] == style])
            for style in sorted({row["style"] for row in predictions})
        },
        "latency_seconds": {
            "mean": sum(latencies) / len(latencies),
            "min": min(latencies),
            "max": max(latencies),
        },
        "confusion_matrix": dict(Counter(
            f"{row['expected']['tool']} -> {row['parsed']['tool'] if row['parsed'] else 'MALFORMED'}"
            for row in predictions
        )),
    }
    return predictions, summary


def render_markdown(summary: dict[str, Any]) -> str:
    def percent(value: float | None) -> str:
        return "n/a" if value is None else f"{value * 100:.2f}%"

    lines = [
        f"# Tiqo Planner Benchmark — {summary['model']}", "",
        f"Examples: {summary['examples']}", "", "## Overall", "",
        "| Metric | Result |", "|---|---:|",
    ]
    lines.extend(f"| {key} | {percent(value)} |" for key, value in summary["metrics"].items())
    for group_name, title in (("by_style", "Style"), ("by_category", "Category")):
        lines.extend(["", f"## By {title.lower()}", "", f"| {title} | Tool | Full action | Malformed |", "|---|---:|---:|---:|"])
        for name, values in summary[group_name].items():
            lines.append(
                f"| {name} | {percent(values['tool'])} | {percent(values['full_action'])} | {percent(values['malformed_rate'])} |"
            )
    lines.extend(["", "## Latency (host benchmark)", "", json.dumps(summary["latency_seconds"], indent=2), ""])
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True)
    parser.add_argument("--dataset", type=Path, default=Path(__file__).resolve().parents[1] / "datasets" / "planner_pl_v1_test.jsonl")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--limit", type=int)
    parser.add_argument("--max-new-tokens", type=int, default=160)
    args = parser.parse_args()
    rows = read_rows(args.dataset, args.limit)
    predictions, summary = evaluate_model(args.model, rows, args.max_new_tokens)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "predictions.jsonl").write_text(
        "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in predictions), encoding="utf-8"
    )
    (args.output / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (args.output / "report.md").write_text(render_markdown(summary), encoding="utf-8")
    print(json.dumps(summary["metrics"], indent=2))


if __name__ == "__main__":
    main()
