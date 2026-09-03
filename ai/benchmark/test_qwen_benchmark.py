from __future__ import annotations

import json
import tempfile
import unittest
from collections import Counter
from pathlib import Path

from ai.benchmark.build_gold_manual_pl import EXPECTED_CATEGORY_COUNTS, STYLES, render
from ai.benchmark.run_qwen_benchmark import (
    ParsedCall,
    build_summary,
    compare_case,
    latest_results,
    load_dataset,
    parse_tool_call,
    schema_errors,
)


DATASET = Path(__file__).with_name("gold_manual_pl.jsonl")


class GoldDatasetTest(unittest.TestCase):
    def test_committed_dataset_matches_reviewed_source(self) -> None:
        self.assertEqual(render(), DATASET.read_text(encoding="utf-8"))

    def test_distribution_and_deterministic_context(self) -> None:
        cases = load_dataset(DATASET)
        self.assertEqual(280, len(cases))
        self.assertEqual(Counter(EXPECTED_CATEGORY_COUNTS), Counter(case["category"] for case in cases))
        self.assertEqual(Counter({style: 56 for style in STYLES}), Counter(case["style"] for case in cases))
        for case in cases:
            context = case["context"]
            self.assertEqual("2026-09-03", context["current_date"])
            self.assertEqual("Europe/Warsaw", context["timezone"])
            self.assertEqual("pl-PL", context["locale"])

    def test_no_diacritics_style_really_has_no_polish_diacritics(self) -> None:
        forbidden = set("ąćęłńóśźżĄĆĘŁŃÓŚŹŻ")
        for case in load_dataset(DATASET):
            if case["style"] == "no-diacritics":
                self.assertTrue(forbidden.isdisjoint(case["input"]), case["id"])

    def test_every_gold_call_is_schema_valid(self) -> None:
        for case in load_dataset(DATASET):
            expected = case["expected"]
            arguments = dict(expected["arguments"])
            arguments.update({key: "niepusty tekst" for key in expected.get("free_text_arguments", [])})
            self.assertEqual([], schema_errors(expected["tool"], arguments), case["id"])


class ParserAndScoringTest(unittest.TestCase):
    def test_parses_qwen_hermes_tool_call(self) -> None:
        parsed = parse_tool_call('<tool_call>\n{"name":"delete_item","arguments":{"item_id":7}}\n</tool_call>')
        self.assertEqual(ParsedCall("delete_item", {"item_id": 7}, "ok"), parsed)

    def test_parses_plain_and_stringified_arguments(self) -> None:
        raw = json.dumps({"name": "get_item", "arguments": json.dumps({"item_id": 9})})
        self.assertEqual(ParsedCall("get_item", {"item_id": 9}, "ok_plain_json"), parse_tool_call(raw))

    def test_rejects_multiple_calls(self) -> None:
        raw = '<tool_call>{"name":"get_item","arguments":{"item_id":1}}</tool_call>' * 2
        self.assertEqual("multiple_tool_calls", parse_tool_call(raw).status)

    def test_free_text_clarification_is_scored_semantically(self) -> None:
        case = {
            "expected": {
                "tool": "request_clarification",
                "arguments": {"candidate_item_ids": [2, 1]},
                "required_arguments": ["candidate_item_ids", "question", "reason"],
                "free_text_arguments": ["question", "reason"],
            }
        }
        parsed = ParsedCall(
            "request_clarification",
            {"question": "Które?", "reason": "Kilka pasuje.", "candidate_item_ids": [1, 2]},
            "ok",
        )
        self.assertTrue(compare_case(case, parsed)["full_correct"])

    def test_delete_precision_and_false_positive(self) -> None:
        base = {
            "parse_status": "ok",
            "schema_errors": [],
            "tool_correct": True,
            "arguments_exact": True,
            "full_correct": True,
            "category": "delete",
            "style": "clean",
            "latency_seconds": 0.1,
        }
        results = [
            {**base, "expected": {"tool": "delete_item"}, "actual": {"tool": "delete_item"}},
            {
                **base,
                "tool_correct": False,
                "full_correct": False,
                "category": "ambiguous",
                "expected": {"tool": "request_clarification"},
                "actual": {"tool": "delete_item"},
            },
        ]
        summary = build_summary(results, 2, "test")
        self.assertEqual(0.5, summary["delete_safety"]["precision"])
        self.assertEqual(1, summary["delete_safety"]["false_positive_destructive_calls"])

    def test_latest_retry_replaces_inference_error(self) -> None:
        attempts = [
            {"case_id": "one", "parse_status": "inference_error"},
            {"case_id": "two", "parse_status": "ok"},
            {"case_id": "one", "parse_status": "ok_plain_json"},
        ]
        self.assertEqual(
            ["ok_plain_json", "ok"],
            [result["parse_status"] for result in latest_results(attempts)],
        )


if __name__ == "__main__":
    unittest.main()
