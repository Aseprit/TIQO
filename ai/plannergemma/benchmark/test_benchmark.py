from __future__ import annotations

import unittest

from ai.plannergemma.benchmark.generate_dataset import generate
from ai.plannergemma.benchmark.schema import parse_function_call, validate_tool_call
from ai.plannergemma.evaluation.run import score_row


class BenchmarkTest(unittest.TestCase):
    def test_generated_rows_are_unique(self):
        rows = generate(720, "test", holdout=True)
        fingerprints = {(row["prompt"].casefold(), str(row["context"])) for row in rows}
        self.assertEqual(len(rows), len(fingerprints))

    def test_parser_supports_functiongemma_format(self):
        parsed = parse_function_call('<start_function_call>call:create_item{"title":"Dentysta","type":"TASK","date":"2026-09-03","all_day":true}<end_function_call>')
        self.assertEqual("create_item", parsed[0])
        self.assertEqual("Dentysta", parsed[1]["title"])

    def test_validator_rejects_invalid_time(self):
        errors = validate_tool_call("create_item", {"title": "X", "type": "TASK", "date": "2026-09-03", "time": "28:15", "all_day": False})
        self.assertTrue(any("time" in error for error in errors))

    def test_scorer_detects_false_positive_mutation(self):
        row = generate(1, "test", holdout=True)[0]
        row["expected"] = {"tool": "delegate_to_chat", "arguments": {"user_request": "test"}}
        scores = score_row(row, ("delete_item", {"item_id": 1}))
        self.assertTrue(scores["false_positive_mutation"])


if __name__ == "__main__":
    unittest.main()
