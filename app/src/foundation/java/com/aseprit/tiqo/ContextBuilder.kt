package com.aseprit.tiqo

import com.google.gson.Gson
import java.text.Normalizer
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class DateScope(val start: LocalDate, val end: LocalDate)
data class PlannerCountSnapshot(val today: String, val start: String, val end: String, val count: Int)
data class PlannerSnapshot(val today: String, val start: String, val end: String, val tasks: List<Task>) {
    fun promptJson(): String = Gson().toJson(mapOf(
        "today" to today, "start" to start, "end" to end,
        "completeForRange" to true,
        "tasks" to tasks.map { mapOf("id" to it.id, "title" to it.title,
            "date" to LocalDate.ofEpochDay(it.date).toString(), "completed" to it.completed, "locked" to it.locked) },
    ))
}

class ContextBuilder(private val dao: TaskDao, private val clock: Clock = Clock.systemDefaultZone()) {
    suspend fun count(query: String, diagnostics: AiDiagnostics? = null): PlannerCountSnapshot {
        val today = LocalDate.now(clock)
        val scope = resolve(query, today)
        diagnostics?.event("ROOM_QUERY", "${scope.start}..${scope.end} count")
        val count = dao.count(scope.start.toEpochDay(), scope.end.toEpochDay())
        diagnostics?.event("ROOM_SNAPSHOT", "rows=$count")
        return PlannerCountSnapshot(today.toString(), scope.start.toString(), scope.end.toString(), count)
    }

    suspend fun build(query: String, diagnostics: AiDiagnostics? = null): PlannerSnapshot {
        diagnostics?.event("CONTEXT_BUILDER", "start")
        val today = LocalDate.now(clock)
        val scope = resolve(query, today)
        diagnostics?.event("ROOM_QUERY", "${scope.start}..${scope.end}")
        val tasks = dao.range(scope.start.toEpochDay(), scope.end.toEpochDay())
        diagnostics?.event("ROOM_SNAPSHOT", "rows=${tasks.size}")
        require(tasks.size <= 80) { "W tym zakresie jest ponad 80 zadań. Zawęź pytanie do konkretnego dnia." }
        return PlannerSnapshot(today.toString(), scope.start.toString(), scope.end.toString(), tasks).also {
            diagnostics?.artifact("planner-snapshot.json", it.promptJson())
            diagnostics?.event("CONTEXT_BUILDER", "complete bytes=${it.promptJson().toByteArray().size}")
        }
    }

    companion object {
        fun resolve(query: String, today: LocalDate): DateScope {
            val q = Normalizer.normalize(query.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}"), "").replace('ł', 'l')
            val dates = Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b").findAll(q).map { LocalDate.parse(it.value) }.toList()
            val moving = Regex("przenies|przesun|move|reschedule").containsMatchIn(q)
            if (dates.isNotEmpty()) {
                val all = if (moving && dates.size == 1) dates + today else dates
                return DateScope(all.min(), all.max())
            }
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return when {
                "pojutrze" in q -> DateScope(if (moving) today else today.plusDays(2), today.plusDays(2))
                "tydzien" in q || "tygodni" in q || "week" in q -> {
                    val start = if ("przysz" in q || "nastepn" in q || "next" in q) monday.plusWeeks(1) else monday
                    DateScope(if (moving) minOf(start, today) else start, start.plusDays(6))
                }
                "jutro" in q || "tomorrow" in q -> DateScope(if (moving || "dzis" in q) today else today.plusDays(1), today.plusDays(1))
                "wczoraj" in q -> DateScope(today.minusDays(1), if (moving) today else today.minusDays(1))
                "dzis" in q || "today" in q -> DateScope(today, today)
                else -> DateScope(today, today.plusDays(6))
            }
        }
    }
}
