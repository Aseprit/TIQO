package com.aseprit.tiqo.ai.tools

import com.aseprit.tiqo.ai.LlmTool
import com.aseprit.tiqo.domain.model.PlannerItemType
import com.aseprit.tiqo.domain.model.RecurrenceRule
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.DayOfWeek
import java.time.LocalDate

object PlannerToolProtocol {
    val definitions: List<LlmTool> = listOf(
        tool("create_item", "Tworzy jedno zadanie, wydarzenie lub urodziny.", """{"type":"object","properties":{"title":{"type":"string"},"description":{"type":"string"},"type":{"type":"string","enum":["TASK","EVENT","BIRTHDAY"]},"date":{"type":"string"},"time":{"type":"string"},"all_day":{"type":"boolean"},"reminder_minutes_before":{"type":"integer","enum":[0,5,10,30,60,1440]},"recurrence":{"type":"object","properties":{"frequency":{"type":"string","enum":["NONE","DAILY","WEEKLY","MONTHLY","YEARLY"]},"interval":{"type":"integer"},"weekdays":{"type":"array","items":{"type":"string"}},"end_date":{"type":"string"}},"required":["frequency"]}},"required":["title","type","date","all_day"],"additionalProperties":false}"""),
        tool("list_items", "Wyświetla elementy w domkniętym zakresie dat.", """{"type":"object","properties":{"from":{"type":"string"},"to":{"type":"string"},"type":{"type":"string"},"status":{"type":"string"}},"required":["from","to"],"additionalProperties":false}"""),
        tool("get_item", "Pobiera jeden element po stabilnym identyfikatorze.", """{"type":"object","properties":{"item_id":{"type":"integer"}},"required":["item_id"],"additionalProperties":false}"""),
        tool("update_item", "Zmienia wyłącznie podane pola rozpoznanego elementu.", """{"type":"object","properties":{"item_id":{"type":"integer"},"title":{"type":"string"},"description":{"type":"string"},"type":{"type":"string","enum":["TASK","EVENT","BIRTHDAY"]},"date":{"type":"string"},"time":{"type":"string"},"all_day":{"type":"boolean"},"reminder_minutes_before":{"type":"integer","enum":[0,5,10,30,60,1440]},"remove_reminder":{"type":"boolean"},"recurrence":{"type":"object"}},"required":["item_id"],"additionalProperties":false}"""),
        tool("delete_item", "Usuwa jeden jednoznacznie rozpoznany element.", """{"type":"object","properties":{"item_id":{"type":"integer"}},"required":["item_id"],"additionalProperties":false}"""),
        tool("set_completed", "Oznacza wystąpienie zadania jako wykonane lub otwarte.", """{"type":"object","properties":{"item_id":{"type":"integer"},"occurrence_date":{"type":"string"},"completed":{"type":"boolean"}},"required":["item_id","occurrence_date","completed"],"additionalProperties":false}"""),
        tool("search_items", "Wyszukuje tytuły i opisy, opcjonalnie w zakresie dat.", """{"type":"object","properties":{"query":{"type":"string"},"from":{"type":"string"},"to":{"type":"string"}},"required":["query"],"additionalProperties":false}"""),
        tool("get_statistics", "Oblicza statystyki ukończenia zadań w zakresie.", """{"type":"object","properties":{"from":{"type":"string"},"to":{"type":"string"}},"required":["from","to"],"additionalProperties":false}"""),
        tool("delegate_to_chat", "Przekazuje pytanie niezwiązane z planerem do modelu czatu.", """{"type":"object","properties":{"user_request":{"type":"string"}},"required":["user_request"],"additionalProperties":false}"""),
        tool("request_clarification", "Prosi o doprecyzowanie zamiast zgadywać.", """{"type":"object","properties":{"question":{"type":"string"},"reason":{"type":"string"},"candidate_item_ids":{"type":"array","items":{"type":"integer"}}},"required":["question","reason"],"additionalProperties":false}"""),
    )

    fun parse(name: String, argumentsJson: String): Result<PlannerToolCall> = runCatching {
        val json = JsonParser.parseString(argumentsJson).asJsonObject
        when (name) {
            "create_item" -> {
                requireOnly(json, "title", "description", "type", "date", "time", "all_day", "reminder_minutes_before", "recurrence")
                PlannerToolCall.CreateItem(
                    title = json.string("title"),
                    description = json.optionalString("description").orEmpty(),
                    type = itemType(json.string("type")),
                    dateIso = json.string("date"),
                    timeIso = json.optionalString("time"),
                    allDay = json.boolean("all_day"),
                    reminderMinutesBefore = json.optionalInt("reminder_minutes_before"),
                    recurrence = json.getAsJsonObject("recurrence")?.let(::recurrence) ?: RecurrenceRule.None,
                )
            }
            "list_items" -> {
                requireOnly(json, "from", "to", "type", "status")
                PlannerToolCall.ListItems(json.string("from"), json.string("to"))
            }
            "get_item" -> {
                requireOnly(json, "item_id")
                PlannerToolCall.GetItem(json.long("item_id"))
            }
            "update_item" -> {
                requireOnly(json, "item_id", "title", "description", "type", "date", "time", "all_day", "reminder_minutes_before", "remove_reminder", "recurrence")
                PlannerToolCall.UpdateItem(
                    itemId = json.long("item_id"),
                    title = json.optionalString("title"),
                    description = json.optionalString("description"),
                    type = json.optionalString("type")?.let(::itemType),
                    dateIso = json.optionalString("date"),
                    timeIso = json.optionalString("time"),
                    allDay = json.optionalBoolean("all_day"),
                    reminderMinutesBefore = json.optionalInt("reminder_minutes_before"),
                    removeReminder = json.optionalBoolean("remove_reminder") ?: false,
                    recurrence = json.getAsJsonObject("recurrence")?.let(::recurrence),
                )
            }
            "delete_item" -> {
                requireOnly(json, "item_id")
                PlannerToolCall.DeleteItem(json.long("item_id"), confirmed = true)
            }
            "set_completed" -> {
                requireOnly(json, "item_id", "occurrence_date", "completed")
                PlannerToolCall.SetCompleted(json.long("item_id"), json.string("occurrence_date"), json.boolean("completed"))
            }
            "search_items" -> {
                requireOnly(json, "query", "from", "to")
                PlannerToolCall.SearchItems(json.string("query"), json.optionalString("from"), json.optionalString("to"))
            }
            "get_statistics" -> {
                requireOnly(json, "from", "to")
                PlannerToolCall.GetStatistics(json.string("from"), json.string("to"))
            }
            "delegate_to_chat" -> {
                requireOnly(json, "user_request")
                PlannerToolCall.DelegateToChat(json.string("user_request"))
            }
            "request_clarification" -> {
                requireOnly(json, "question", "reason", "candidate_item_ids")
                PlannerToolCall.RequestClarification(
                    json.string("question"),
                    json.string("reason"),
                    json.getAsJsonArray("candidate_item_ids")?.map { it.asLong }.orEmpty(),
                )
            }
            else -> error("Nieznane narzędzie: $name")
        }
    }

    private fun recurrence(json: JsonObject): RecurrenceRule {
        requireOnly(json, "frequency", "interval", "weekdays", "end_date")
        val interval = json.optionalInt("interval") ?: 1
        require(interval >= 1) { "Interwał cyklu musi być dodatni" }
        val end = json.optionalString("end_date")?.let(LocalDate::parse)
        return when (json.string("frequency")) {
            "NONE" -> RecurrenceRule.None
            "DAILY" -> RecurrenceRule.Daily(interval, end)
            "WEEKLY" -> {
                val days = json.getAsJsonArray("weekdays")?.map { DayOfWeek.valueOf(it.asString) }?.toSet().orEmpty()
                require(days.isNotEmpty()) { "Cykl tygodniowy wymaga dni tygodnia" }
                RecurrenceRule.Weekly(days, interval, end)
            }
            "MONTHLY" -> RecurrenceRule.Monthly(interval, end)
            "YEARLY" -> RecurrenceRule.Yearly(interval, end)
            else -> error("Nieznana częstotliwość cyklu")
        }
    }

    private fun itemType(value: String): PlannerItemType = when (value) {
        "TASK" -> PlannerItemType.Task
        "EVENT" -> PlannerItemType.Event
        "BIRTHDAY" -> PlannerItemType.Birthday
        else -> error("Nieznany typ elementu: $value")
    }

    private fun tool(name: String, description: String, schema: String) = LlmTool(name, description, schema)
    private fun JsonObject.string(name: String): String = requireNotNull(get(name)) { "Brak pola $name" }.asString
    private fun JsonObject.long(name: String): Long = requireNotNull(get(name)) { "Brak pola $name" }.asLong.also { require(it > 0) }
    private fun JsonObject.boolean(name: String): Boolean = requireNotNull(get(name)) { "Brak pola $name" }.asBoolean
    private fun JsonObject.optionalString(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString
    private fun JsonObject.optionalInt(name: String): Int? = get(name)?.takeUnless { it.isJsonNull }?.asInt
    private fun JsonObject.optionalBoolean(name: String): Boolean? = get(name)?.takeUnless { it.isJsonNull }?.asBoolean
    private fun requireOnly(json: JsonObject, vararg allowed: String) {
        val unknown = json.keySet() - allowed.toSet()
        require(unknown.isEmpty()) { "Nieznane argumenty: ${unknown.joinToString()}" }
    }
}
