"""Canonical Tiqo tool schema shared by generation, training and evaluation."""

from __future__ import annotations

import json
import re
from datetime import date, time
from typing import Any


def _tool(name: str, description: str, properties: dict[str, Any], required: list[str]) -> dict[str, Any]:
    return {
        "type": "function",
        "function": {
            "name": name,
            "description": description,
            "parameters": {
                "type": "object",
                "properties": properties,
                "required": required,
                "additionalProperties": False,
            },
        },
    }


STRING = {"type": "string"}
DATE = {"type": "string", "description": "ISO local date YYYY-MM-DD"}
TIME = {"type": "string", "description": "24-hour local time HH:MM"}
ITEM_TYPE = {"type": "string", "enum": ["TASK", "EVENT", "BIRTHDAY"]}
REMINDER = {"type": "integer", "enum": [0, 5, 10, 30, 60, 1440], "description": "Minutes before"}
RECURRENCE = {
    "type": "object",
    "properties": {
        "frequency": {"type": "string", "enum": ["NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY"]},
        "interval": {"type": "integer", "minimum": 1},
        "weekdays": {
            "type": "array",
            "items": {
                "type": "string",
                "enum": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"],
            },
        },
        "end_date": DATE,
    },
    "required": ["frequency"],
    "additionalProperties": False,
}

TOOL_SCHEMAS: list[dict[str, Any]] = [
    _tool(
        "create_item",
        "Create one planner task, event or birthday.",
        {
            "title": STRING,
            "description": STRING,
            "type": ITEM_TYPE,
            "date": DATE,
            "time": TIME,
            "all_day": {"type": "boolean"},
            "reminder_minutes_before": REMINDER,
            "recurrence": RECURRENCE,
        },
        ["title", "type", "date", "all_day"],
    ),
    _tool(
        "list_items",
        "List planner items in an inclusive local date range.",
        {
            "from": DATE,
            "to": DATE,
            "type": ITEM_TYPE,
            "status": {"type": "string", "enum": ["ALL", "OPEN", "COMPLETED"]},
        },
        ["from", "to"],
    ),
    _tool("get_item", "Get one planner item by stable id.", {"item_id": {"type": "integer", "minimum": 1}}, ["item_id"]),
    _tool(
        "update_item",
        "Update only the supplied fields of one resolved planner item.",
        {
            "item_id": {"type": "integer", "minimum": 1},
            "title": STRING,
            "description": STRING,
            "type": ITEM_TYPE,
            "date": DATE,
            "time": TIME,
            "all_day": {"type": "boolean"},
            "reminder_minutes_before": REMINDER,
            "remove_reminder": {"type": "boolean"},
            "recurrence": RECURRENCE,
        },
        ["item_id"],
    ),
    _tool("delete_item", "Delete one unambiguously resolved item.", {"item_id": {"type": "integer", "minimum": 1}}, ["item_id"]),
    _tool(
        "set_completed",
        "Complete or reopen one TASK occurrence.",
        {
            "item_id": {"type": "integer", "minimum": 1},
            "occurrence_date": DATE,
            "completed": {"type": "boolean"},
        },
        ["item_id", "occurrence_date", "completed"],
    ),
    _tool(
        "search_items",
        "Search item titles and descriptions, optionally in a date range.",
        {"query": STRING, "from": DATE, "to": DATE},
        ["query"],
    ),
    _tool(
        "get_statistics",
        "Get TASK completion statistics for an inclusive date range.",
        {"from": DATE, "to": DATE},
        ["from", "to"],
    ),
    _tool("delegate_to_chat", "Route a non-planner request to the optional chat model.", {"user_request": STRING}, ["user_request"]),
    _tool(
        "request_clarification",
        "Ask before acting when the target or requested change is ambiguous.",
        {
            "question": STRING,
            "reason": STRING,
            "candidate_item_ids": {"type": "array", "items": {"type": "integer", "minimum": 1}},
        },
        ["question", "reason"],
    ),
]

SCHEMA_BY_NAME = {tool["function"]["name"]: tool["function"]["parameters"] for tool in TOOL_SCHEMAS}
MUTATING_TOOLS = {"create_item", "update_item", "delete_item", "set_completed"}
DATE_KEYS = {"date", "from", "to", "occurrence_date", "end_date"}
TIME_KEYS = {"time"}


def developer_message(context: dict[str, Any]) -> str:
    candidates = context.get("candidate_items", [])
    candidate_text = json.dumps(candidates, ensure_ascii=False, separators=(",", ":"))
    return (
        f"Current local date: {context['current_date']}\n"
        f"Current local time: {context['current_time']}\n"
        f"Timezone: {context['timezone']}\n"
        f"Locale: {context['locale']}\n"
        f"Selected date: {context.get('selected_date', context['current_date'])}\n"
        f"Candidate planner items: {candidate_text}\n"
        "You are PlannerGemma, the Tiqo planner router. Use exactly one provided function. "
        "Never answer general questions yourself. Ask for clarification instead of guessing a target."
    )


def assistant_tool_message(name: str, arguments: dict[str, Any]) -> dict[str, Any]:
    return {
        "role": "assistant",
        "tool_calls": [{"function": {"name": name, "arguments": arguments}}],
    }


def validate_tool_call(name: str, arguments: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    schema = SCHEMA_BY_NAME.get(name)
    if schema is None:
        return [f"unknown tool: {name}"]
    properties = schema["properties"]
    unknown = set(arguments) - set(properties)
    if unknown:
        errors.append(f"unknown arguments: {sorted(unknown)}")
    for key in schema.get("required", []):
        if key not in arguments:
            errors.append(f"missing required argument: {key}")
    for key, value in arguments.items():
        if key in DATE_KEYS:
            try:
                date.fromisoformat(value)
            except (TypeError, ValueError):
                errors.append(f"invalid ISO date in {key}")
        elif key in TIME_KEYS:
            try:
                parsed = time.fromisoformat(value)
                if parsed.second or parsed.microsecond:
                    errors.append(f"time in {key} must use HH:MM")
            except (TypeError, ValueError):
                errors.append(f"invalid local time in {key}")
        spec = properties.get(key, {})
        if "enum" in spec and value not in spec["enum"]:
            errors.append(f"invalid enum for {key}: {value}")
    recurrence = arguments.get("recurrence")
    if recurrence is not None:
        if not isinstance(recurrence, dict):
            errors.append("recurrence must be an object")
        else:
            frequency = recurrence.get("frequency")
            if frequency not in RECURRENCE["properties"]["frequency"]["enum"]:
                errors.append("invalid recurrence frequency")
            weekdays = recurrence.get("weekdays", [])
            allowed = set(RECURRENCE["properties"]["weekdays"]["items"]["enum"])
            if not isinstance(weekdays, list) or any(day not in allowed for day in weekdays):
                errors.append("invalid recurrence weekdays")
            if frequency == "WEEKLY" and not weekdays:
                errors.append("weekly recurrence requires weekdays")
    if name == "update_item" and set(arguments) == {"item_id"}:
        errors.append("update_item has no changes")
    return errors


FUNCTION_CALL_PATTERN = re.compile(
    r"(?:<start_function_call>)?\s*call:(?P<name>[a-z_]+)\s*(?P<args>\{.*\})\s*(?:<end_function_call>)?",
    re.DOTALL,
)


def parse_function_call(raw: str) -> tuple[str, dict[str, Any]] | None:
    match = FUNCTION_CALL_PATTERN.search(raw)
    if match:
        raw_arguments = match.group("args").strip()
        candidates = [raw_arguments]
        if raw_arguments.startswith("{") and raw_arguments.endswith("}"):
            inner = raw_arguments[1:-1].strip()
            if inner.startswith("{") and inner.endswith("}"):
                candidates.append(inner)
        for candidate in candidates:
            try:
                parsed = json.loads(candidate)
                if isinstance(parsed, dict):
                    return match.group("name"), parsed
            except json.JSONDecodeError:
                continue
        return None
    try:
        payload = json.loads(raw)
        if isinstance(payload, dict) and isinstance(payload.get("name"), str) and isinstance(payload.get("arguments"), dict):
            return payload["name"], payload["arguments"]
    except json.JSONDecodeError:
        pass
    return None
