"""Run the deterministic Tiqo planner benchmark against Qwen3-0.6B locally."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import tempfile
import time
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import date, time as local_time
from pathlib import Path
from typing import Any, Iterable


REPO_ROOT = Path(__file__).resolve().parents[2]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from ai.plannergemma.benchmark.schema import SCHEMA_BY_NAME, TOOL_SCHEMAS  # noqa: E402


RUNNER_VERSION = 1
DEFAULT_MODEL = "Qwen/Qwen3-0.6B"
DEFAULT_DATASET = Path(__file__).with_name("gold_manual_pl.jsonl")
DEFAULT_OUTPUT = Path(__file__).with_name("results") / "qwen3_0.6b"
RAW_FILE = "qwen3_0.6b_raw_results.jsonl"
FAILURES_FILE = "qwen3_0.6b_failures.jsonl"
SUMMARY_FILE = "qwen3_0.6b_summary.json"
META_FILE = "qwen3_0.6b_run_meta.json"
MUTATING_TOOLS = {"create_item", "update_item", "delete_item", "set_completed"}
TOOL_CALL_RE = re.compile(r"<tool_call>\s*(.*?)\s*</tool_call>", re.DOTALL | re.IGNORECASE)
THINK_RE = re.compile(r"<think>.*?</think>", re.DOTALL | re.IGNORECASE)
LEGACY_CALL_RE = re.compile(r"(?:<start_function_call>)?\s*call:(?P<name>[a-z_]+)\s*(?P<args>\{.*\})", re.DOTALL)


@dataclass(frozen=True)
class ParsedCall:
    tool: str | None
    arguments: dict[str, Any] | None
    status: str


def parse_tool_call(raw_output: str) -> ParsedCall:
    """Parse Qwen's Hermes XML plus two useful fallback formats."""
    clean = THINK_RE.sub("", raw_output).strip()
    xml_payloads = TOOL_CALL_RE.findall(clean)
    if len(xml_payloads) > 1:
        return ParsedCall(None, None, "multiple_tool_calls")
    if len(xml_payloads) == 1:
        return _parse_json_payload(xml_payloads[0], "ok")

    legacy = LEGACY_CALL_RE.search(clean)
    if legacy:
        try:
            arguments = json.loads(legacy.group("args"))
        except json.JSONDecodeError:
            return ParsedCall(None, None, "invalid_json")
        if isinstance(arguments, dict):
            return ParsedCall(legacy.group("name"), arguments, "ok_legacy")
        return ParsedCall(None, None, "arguments_not_object")

    if clean.startswith("{"):
        return _parse_json_payload(clean, "ok_plain_json")
    return ParsedCall(None, None, "no_tool_call")


def _parse_json_payload(payload: str, success_status: str) -> ParsedCall:
    try:
        parsed = json.loads(payload.strip())
    except json.JSONDecodeError:
        return ParsedCall(None, None, "invalid_json")
    if not isinstance(parsed, dict):
        return ParsedCall(None, None, "payload_not_object")
    if isinstance(parsed.get("function"), dict):
        parsed = parsed["function"]
    tool = parsed.get("name") or parsed.get("tool")
    arguments = parsed.get("arguments")
    if isinstance(arguments, str):
        try:
            arguments = json.loads(arguments)
        except json.JSONDecodeError:
            return ParsedCall(str(tool) if tool else None, None, "invalid_arguments_json")
    if not isinstance(tool, str) or not tool:
        return ParsedCall(None, arguments if isinstance(arguments, dict) else None, "missing_tool_name")
    if not isinstance(arguments, dict):
        return ParsedCall(tool, None, "arguments_not_object")
    return ParsedCall(tool, arguments, success_status)


def schema_errors(tool: str | None, arguments: dict[str, Any] | None) -> list[str]:
    if tool not in SCHEMA_BY_NAME:
        return [f"unknown tool: {tool}"]
    if not isinstance(arguments, dict):
        return ["arguments must be an object"]
    schema = SCHEMA_BY_NAME[tool]
    errors: list[str] = []
    properties = schema.get("properties", {})
    unknown = set(arguments) - set(properties)
    if unknown:
        errors.append(f"unknown arguments: {sorted(unknown)}")
    for key in schema.get("required", []):
        if key not in arguments:
            errors.append(f"missing required argument: {key}")
    for key, value in arguments.items():
        spec = properties.get(key)
        if spec is not None:
            _validate_value(key, value, spec, errors)
    if tool == "update_item" and set(arguments) == {"item_id"}:
        errors.append("update_item has no changes")
    recurrence = arguments.get("recurrence")
    if isinstance(recurrence, dict) and recurrence.get("frequency") == "WEEKLY" and not recurrence.get("weekdays"):
        errors.append("weekly recurrence requires weekdays")
    return errors


def _validate_value(path: str, value: Any, spec: dict[str, Any], errors: list[str]) -> None:
    expected_type = spec.get("type")
    valid_type = {
        "string": lambda item: isinstance(item, str),
        "integer": lambda item: isinstance(item, int) and not isinstance(item, bool),
        "boolean": lambda item: isinstance(item, bool),
        "object": lambda item: isinstance(item, dict),
        "array": lambda item: isinstance(item, list),
    }.get(expected_type, lambda _item: True)
    if not valid_type(value):
        errors.append(f"{path} must be {expected_type}")
        return
    if "enum" in spec and value not in spec["enum"]:
        errors.append(f"invalid enum for {path}: {value}")
    if expected_type == "integer" and "minimum" in spec and value < spec["minimum"]:
        errors.append(f"{path} is below minimum")
    if expected_type == "object":
        nested_properties = spec.get("properties", {})
        if spec.get("additionalProperties") is False:
            unknown = set(value) - set(nested_properties)
            if unknown:
                errors.append(f"unknown arguments in {path}: {sorted(unknown)}")
        for key in spec.get("required", []):
            if key not in value:
                errors.append(f"missing required argument: {path}.{key}")
        for key, nested in value.items():
            if key in nested_properties:
                _validate_value(f"{path}.{key}", nested, nested_properties[key], errors)
    if expected_type == "array":
        item_spec = spec.get("items", {})
        for index, item in enumerate(value):
            _validate_value(f"{path}[{index}]", item, item_spec, errors)
    if expected_type == "string" and path.split(".")[-1] in {"date", "from", "to", "occurrence_date", "end_date"}:
        try:
            date.fromisoformat(value)
        except ValueError:
            errors.append(f"invalid ISO date in {path}")
    if expected_type == "string" and path.split(".")[-1] == "time":
        try:
            parsed = local_time.fromisoformat(value)
            if parsed.second or parsed.microsecond or len(value) != 5:
                raise ValueError
        except ValueError:
            errors.append(f"invalid HH:MM time in {path}")


def compare_case(case: dict[str, Any], parsed: ParsedCall) -> dict[str, Any]:
    expected = case["expected"]
    errors = schema_errors(parsed.tool, parsed.arguments)
    tool_correct = parsed.tool == expected["tool"]
    arguments = parsed.arguments or {}
    required_keys = set(expected.get("required_arguments", expected.get("arguments", {})))
    exact_keys = set(arguments) == required_keys
    exact_values = all(
        _values_equal(key, expected_value, arguments.get(key))
        for key, expected_value in expected.get("arguments", {}).items()
    )
    free_text_ok = all(
        isinstance(arguments.get(key), str) and bool(arguments[key].strip())
        for key in expected.get("free_text_arguments", [])
    )
    full_correct = parsed.status.startswith("ok") and tool_correct and not errors and exact_keys and exact_values and free_text_ok
    return {
        "parse_status": parsed.status,
        "schema_errors": errors,
        "tool_correct": tool_correct,
        "arguments_exact": exact_keys and exact_values and free_text_ok,
        "full_correct": full_correct,
    }


def _values_equal(key: str, expected: Any, actual: Any) -> bool:
    if key == "candidate_item_ids" and isinstance(expected, list) and isinstance(actual, list):
        return sorted(expected) == sorted(actual)
    return expected == actual


def load_dataset(path: Path) -> list[dict[str, Any]]:
    cases: list[dict[str, Any]] = []
    seen: set[str] = set()
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, 1):
            if not line.strip():
                continue
            case = json.loads(line)
            missing = {"id", "input", "expected", "category", "style", "context"} - set(case)
            if missing:
                raise ValueError(f"{path}:{line_number}: missing fields {sorted(missing)}")
            if case["id"] in seen:
                raise ValueError(f"{path}:{line_number}: duplicate id {case['id']}")
            seen.add(case["id"])
            cases.append(case)
    if not cases:
        raise ValueError(f"Dataset is empty: {path}")
    return cases


def build_messages(case: dict[str, Any]) -> list[dict[str, str]]:
    context = case["context"]
    candidates = json.dumps(context.get("candidate_items", []), ensure_ascii=False, separators=(",", ":"))
    system = (
        "Jesteś lokalnym routerem planera Tiqo. Zawsze wywołaj dokładnie jedno z dostarczonych narzędzi i nie odpowiadaj tekstem.\n"
        "Polecenia planera mapuj na właściwe narzędzie. Pytania ogólne zawsze kieruj przez delegate_to_chat, zachowując oryginalny tekst.\n"
        "Gdy cel lub wymagana zmiana są niejednoznaczne, użyj request_clarification zamiast zgadywać. "
        "Nigdy nie wymyślaj item_id spoza Candidate planner items. Daty i godziny zwracaj w formacie ISO.\n"
        f"Current local date: {context['current_date']}\n"
        f"Current local time: {context.get('current_time', '09:00')}\n"
        f"Timezone: {context['timezone']}\n"
        f"Locale: {context['locale']}\n"
        f"Selected date: {context.get('selected_date', context['current_date'])}\n"
        f"Candidate planner items: {candidates}"
    )
    messages: list[dict[str, str]] = [{"role": "system", "content": system}]
    for item in context.get("history", []):
        if item.get("role") in {"user", "assistant"} and isinstance(item.get("content"), str):
            messages.append({"role": item["role"], "content": item["content"]})
    messages.append({"role": "user", "content": case["input"]})
    return messages


class LocalQwen:
    def __init__(self, model_name: str, device: str, dtype_name: str, local_files_only: bool) -> None:
        try:
            import torch
            from transformers import AutoModelForCausalLM, AutoTokenizer
        except ImportError as error:
            raise RuntimeError("Missing benchmark dependencies. Use run_qwen_benchmark.ps1.") from error

        self.torch = torch
        self.device = self._resolve_device(device)
        self.dtype = self._resolve_dtype(dtype_name)
        print(f"Loading {model_name} on {self.device} ({self.dtype})...")
        self.tokenizer = AutoTokenizer.from_pretrained(model_name, local_files_only=local_files_only)
        self.model = AutoModelForCausalLM.from_pretrained(
            model_name,
            torch_dtype=self.dtype,
            local_files_only=local_files_only,
            low_cpu_mem_usage=True,
        ).to(self.device)
        self.model.eval()

    def _resolve_device(self, requested: str) -> str:
        if requested == "auto":
            return "cuda" if self.torch.cuda.is_available() else "cpu"
        if requested == "cuda" and not self.torch.cuda.is_available():
            raise RuntimeError("CUDA was requested but torch.cuda.is_available() is false.")
        return requested

    def _resolve_dtype(self, requested: str) -> Any:
        if requested == "float32" or self.device == "cpu":
            return self.torch.float32
        if requested == "float16":
            return self.torch.float16
        if requested == "bfloat16":
            return self.torch.bfloat16
        return self.torch.bfloat16 if self.torch.cuda.is_bf16_supported() else self.torch.float16

    def generate(self, case: dict[str, Any], max_new_tokens: int) -> str:
        messages = build_messages(case)
        model_inputs = self.tokenizer.apply_chat_template(
            messages,
            tools=TOOL_SCHEMAS,
            add_generation_prompt=True,
            enable_thinking=False,
            tokenize=True,
            return_dict=True,
            return_tensors="pt",
        ).to(self.device)
        prompt_tokens = model_inputs["input_ids"].shape[-1]
        with self.torch.inference_mode():
            output = self.model.generate(
                **model_inputs,
                max_new_tokens=max_new_tokens,
                do_sample=False,
                pad_token_id=self.tokenizer.pad_token_id,
            )
        generated = output[0, prompt_tokens:]
        return self.tokenizer.decode(generated, skip_special_tokens=True).strip()


def evaluate_one(case: dict[str, Any], model: LocalQwen, model_name: str, max_new_tokens: int) -> dict[str, Any]:
    started = time.perf_counter()
    raw_output = model.generate(case, max_new_tokens)
    latency = time.perf_counter() - started
    parsed = parse_tool_call(raw_output)
    comparison = compare_case(case, parsed)
    return {
        "case_id": case["id"],
        "input": case["input"],
        "expected": case["expected"],
        "actual": None if parsed.tool is None else {"tool": parsed.tool, "arguments": parsed.arguments},
        **comparison,
        "category": case["category"],
        "style": case["style"],
        "latency_seconds": round(latency, 4),
        "raw_output": raw_output,
        "model": model_name,
    }


def metric_block(results: Iterable[dict[str, Any]]) -> dict[str, Any]:
    items = list(results)
    total = len(items)
    tool_correct = sum(bool(item["tool_correct"]) for item in items)
    full_correct = sum(bool(item["full_correct"]) for item in items)
    malformed = sum(not str(item["parse_status"]).startswith("ok") for item in items)
    return {
        "total": total,
        "correct_tool_selection": {"count": tool_correct, "total": total, "rate": _rate(tool_correct, total)},
        "full_structured_accuracy": {"count": full_correct, "total": total, "rate": _rate(full_correct, total)},
        "malformed_output": {"count": malformed, "total": total, "rate": _rate(malformed, total)},
    }


def build_summary(results: list[dict[str, Any]], dataset_total: int, model_name: str) -> dict[str, Any]:
    by_category: dict[str, list[dict[str, Any]]] = defaultdict(list)
    by_style: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for result in results:
        by_category[result["category"]].append(result)
        by_style[result["style"]].append(result)
    expected_delegate = [item for item in results if item["expected"]["tool"] == "delegate_to_chat"]
    expected_clarification = [item for item in results if item["expected"]["tool"] == "request_clarification"]
    predicted_deletes = [item for item in results if (item.get("actual") or {}).get("tool") == "delete_item"]
    true_positive_deletes = sum(item["expected"]["tool"] == "delete_item" for item in predicted_deletes)
    false_positive_deletes = len(predicted_deletes) - true_positive_deletes
    false_positive_mutations = sum(
        (item.get("actual") or {}).get("tool") in MUTATING_TOOLS and item["expected"]["tool"] not in MUTATING_TOOLS
        for item in results
    )
    latencies = [float(item.get("latency_seconds", 0)) for item in results]
    return {
        "runner_version": RUNNER_VERSION,
        "model": model_name,
        "dataset_total": dataset_total,
        "completed_cases": len(results),
        "total_cases": len(results),
        "overall": metric_block(results),
        "delegate_accuracy": metric_block(expected_delegate)["full_structured_accuracy"],
        "clarification_accuracy": metric_block(expected_clarification)["full_structured_accuracy"],
        "delete_safety": {
            "expected_delete_cases": sum(item["expected"]["tool"] == "delete_item" for item in results),
            "predicted_delete_calls": len(predicted_deletes),
            "true_positive_delete_calls": true_positive_deletes,
            "precision": _rate(true_positive_deletes, len(predicted_deletes)),
            "false_positive_destructive_calls": false_positive_deletes,
            "false_positive_destructive_rate": _rate(false_positive_deletes, len(results)),
            "false_positive_mutating_calls": false_positive_mutations,
        },
        "per_category": {key: metric_block(value) for key, value in sorted(by_category.items())},
        "per_style": {key: metric_block(value) for key, value in sorted(by_style.items())},
        "latency_seconds": {
            "total": round(sum(latencies), 4),
            "mean": round(sum(latencies) / len(latencies), 4) if latencies else None,
        },
        "failure_count": sum(not item["full_correct"] for item in results),
        "parse_status_counts": dict(sorted(Counter(item["parse_status"] for item in results).items())),
    }


def _rate(numerator: int, denominator: int) -> float | None:
    return round(numerator / denominator, 6) if denominator else None


def load_existing_results(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    results: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, 1):
            if not line.strip():
                continue
            try:
                results.append(json.loads(line))
            except json.JSONDecodeError:
                print(f"Warning: ignoring incomplete trailing result at {path}:{line_number}", file=sys.stderr)
                break
    return results


def latest_results(results: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    """Keep the latest attempt per case while preserving stable dataset order."""
    latest: dict[str, dict[str, Any]] = {}
    order: list[str] = []
    for result in results:
        case_id = result.get("case_id")
        if not isinstance(case_id, str):
            continue
        if case_id not in latest:
            order.append(case_id)
        latest[case_id] = result
    return [latest[case_id] for case_id in order]


def append_jsonl(path: Path, payload: dict[str, Any]) -> None:
    with path.open("a", encoding="utf-8", newline="\n") as handle:
        handle.write(json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n")
        handle.flush()
        os.fsync(handle.fileno())


def atomic_write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", newline="\n", dir=path.parent, delete=False) as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
        temporary = Path(handle.name)
    os.replace(temporary, path)


def rewrite_failures(path: Path, results: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", newline="\n", dir=path.parent, delete=False) as handle:
        for result in results:
            if result["full_correct"]:
                continue
            failure = {
                "case_id": result["case_id"],
                "input": result["input"],
                "expected": result["expected"],
                "actual": result["actual"],
                "parse_status": result["parse_status"],
                "schema_errors": result["schema_errors"],
                "category": result["category"],
                "style": result["style"],
                "raw_output": result["raw_output"],
            }
            handle.write(json.dumps(failure, ensure_ascii=False, separators=(",", ":")) + "\n")
        temporary = Path(handle.name)
    os.replace(temporary, path)


def dataset_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def prepare_output(output: Path, metadata: dict[str, Any], resume: bool) -> tuple[Path, list[dict[str, Any]]]:
    output.mkdir(parents=True, exist_ok=True)
    raw_path = output / RAW_FILE
    meta_path = output / META_FILE
    if not resume:
        for path in (raw_path, output / FAILURES_FILE, output / SUMMARY_FILE, meta_path):
            path.unlink(missing_ok=True)
    if meta_path.exists():
        existing_meta = json.loads(meta_path.read_text(encoding="utf-8"))
        comparable = {key: existing_meta.get(key) for key in metadata}
        if comparable != metadata:
            raise RuntimeError(
                f"Cannot resume incompatible run in {output}. Use a different --output or explicitly pass --no-resume."
            )
    else:
        atomic_write_json(meta_path, metadata)
    return raw_path, load_existing_results(raw_path)


def print_summary(summary: dict[str, Any], paths: dict[str, Path]) -> None:
    overall = summary["overall"]
    print("\n=== TIQO QWEN BENCHMARK ===")
    print(f"Completed: {summary['completed_cases']}/{summary['dataset_total']}")
    print(f"Correct tool: {_format_rate(overall['correct_tool_selection'])}")
    print(f"Full structured: {_format_rate(overall['full_structured_accuracy'])}")
    print(f"Malformed: {_format_rate(overall['malformed_output'])}")
    print(f"Delegate: {_format_rate(summary['delegate_accuracy'])}")
    print(f"Clarification: {_format_rate(summary['clarification_accuracy'])}")
    safety = summary["delete_safety"]
    precision = "n/a" if safety["precision"] is None else f"{100 * safety['precision']:.2f}%"
    print(f"Delete precision: {precision}; false positives: {safety['false_positive_destructive_calls']}")
    print(f"Summary:  {paths['summary']}")
    print(f"Failures: {paths['failures']}")
    print(f"Raw:      {paths['raw']}")


def _format_rate(metric: dict[str, Any]) -> str:
    if metric["rate"] is None:
        return "n/a"
    return f"{metric['count']}/{metric['total']} ({100 * metric['rate']:.2f}%)"


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", default=DEFAULT_MODEL, help="Hugging Face model id or local Transformers directory.")
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    parser.add_argument("--device", choices=("auto", "cuda", "cpu"), default="auto")
    parser.add_argument("--dtype", choices=("auto", "float32", "float16", "bfloat16"), default="auto")
    parser.add_argument("--limit", type=int, help="Evaluate only the first N cases (for example 20 for smoke).")
    parser.add_argument("--resume", action=argparse.BooleanOptionalAction, default=True)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT, help="Directory for summary, failures and raw JSONL.")
    parser.add_argument("--max-new-tokens", type=int, default=256)
    parser.add_argument("--local-files-only", action="store_true", help="Never access Hugging Face; require an already cached model.")
    parser.add_argument("--validate-only", action="store_true", help="Validate dataset/tooling without loading a model.")
    args = parser.parse_args(argv)
    if args.limit is not None and args.limit < 1:
        parser.error("--limit must be positive")
    if args.max_new_tokens < 32:
        parser.error("--max-new-tokens must be at least 32")
    return args


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    dataset_path = args.dataset.resolve()
    output_path = args.output.resolve()
    cases = load_dataset(dataset_path)
    for case in cases:
        expected = case["expected"]
        gold_arguments = dict(expected.get("arguments", {}))
        gold_arguments.update({key: "ręcznie oceniany niepusty tekst" for key in expected.get("free_text_arguments", [])})
        validation = schema_errors(expected["tool"], gold_arguments)
        if validation:
            raise ValueError(f"Invalid gold label in {case['id']}: {validation}")
    if args.validate_only:
        print(f"OK: {len(cases)} cases, {len(TOOL_SCHEMAS)} tools, deterministic context in {dataset_path}")
        return 0

    selected_cases = cases[: args.limit] if args.limit else cases
    metadata = {
        "runner_version": RUNNER_VERSION,
        "model": args.model,
        "dataset": str(dataset_path),
        "dataset_sha256": dataset_sha256(dataset_path),
        "thinking": False,
        "decoding": "greedy",
        "max_new_tokens": args.max_new_tokens,
    }
    raw_path, results = prepare_output(output_path, metadata, args.resume)
    known_ids = {case["id"] for case in cases}
    results = latest_results(result for result in results if result.get("case_id") in known_ids)
    completed_ids = {
        result["case_id"]
        for result in results
        if result.get("parse_status") != "inference_error"
    }
    pending = [case for case in selected_cases if case["id"] not in completed_ids]
    paths = {
        "raw": raw_path,
        "failures": output_path / FAILURES_FILE,
        "summary": output_path / SUMMARY_FILE,
    }
    if pending:
        model = LocalQwen(args.model, args.device, args.dtype, args.local_files_only)
        for index, case in enumerate(pending, 1):
            print(f"[{len(completed_ids) + index}/{len(selected_cases)}] {case['id']}: {case['input']}")
            try:
                result = evaluate_one(case, model, args.model, args.max_new_tokens)
            except KeyboardInterrupt:
                print("Interrupted. Completed cases are already checkpointed.", file=sys.stderr)
                break
            except Exception as error:
                result = {
                    "case_id": case["id"],
                    "input": case["input"],
                    "expected": case["expected"],
                    "actual": None,
                    "parse_status": "inference_error",
                    "schema_errors": [f"{type(error).__name__}: {error}"],
                    "tool_correct": False,
                    "arguments_exact": False,
                    "full_correct": False,
                    "category": case["category"],
                    "style": case["style"],
                    "latency_seconds": 0.0,
                    "raw_output": "",
                    "model": args.model,
                }
                print(f"Inference error: {error}", file=sys.stderr)
            append_jsonl(raw_path, result)
            results = latest_results([*results, result])
            summary = build_summary(results, len(cases), args.model)
            rewrite_failures(paths["failures"], results)
            atomic_write_json(paths["summary"], summary)
            if result["parse_status"] == "inference_error":
                print("Run stopped after an inference error; the failed case will be retried on resume.", file=sys.stderr)
                break

    summary = build_summary(results, len(cases), args.model)
    rewrite_failures(paths["failures"], results)
    atomic_write_json(paths["summary"], summary)
    print_summary(summary, paths)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
