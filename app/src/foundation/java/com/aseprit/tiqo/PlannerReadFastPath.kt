package com.aseprit.tiqo

import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PlannerReadAnswer(val text: String, val start: String, val end: String, val taskCount: Int)

class PlannerReadFastPath(private val builder: ContextBuilder) {
    private enum class Kind { LIST, COUNT, EXISTS }
    private val day = "(?:dzis|dzisiaj|jutro|pojutrze)"
    private val period = "(?:$day|w tym tygodniu|w przyszlym tygodniu|w nastepnym tygodniu)"
    private val list = Regex("(?:co mam|co jest zaplanowane) $period")
    private val count = Regex("ile mam zadan $period")
    private val exists = Regex("czy mam cos $period")
    private val marks = Regex("\\p{M}")
    private val whitespace = Regex("\\s+")
    private val polish = Locale.forLanguageTag("pl-PL")
    private val weekday = DateTimeFormatter.ofPattern("EEEE", polish)

    fun accepts(query: String): Boolean = classify(normalize(query)) != null

    private fun normalize(query: String) = Normalizer.normalize(query.trim().removeSuffix("?").trim().lowercase(polish), Normalizer.Form.NFD)
        .replace(marks, "").replace('ł', 'l').replace(whitespace, " ")

    private fun classify(normalized: String) = when {
        list.matches(normalized) -> Kind.LIST
        count.matches(normalized) -> Kind.COUNT
        exists.matches(normalized) -> Kind.EXISTS
        else -> null
    }

    suspend fun handle(query: String, diagnostics: AiDiagnostics? = null): PlannerReadAnswer? {
        val normalized = normalize(query)
        val kind = classify(normalized) ?: return null
        val listSnapshot = if (kind == Kind.LIST) builder.build(query, diagnostics) else null
        val countSnapshot = if (kind != Kind.LIST) builder.count(query, diagnostics) else null
        val startText = listSnapshot?.start ?: requireNotNull(countSnapshot).start
        val endText = listSnapshot?.end ?: requireNotNull(countSnapshot).end
        val todayText = listSnapshot?.today ?: requireNotNull(countSnapshot).today
        val start = LocalDate.parse(startText)
        val end = LocalDate.parse(endText)
        val today = LocalDate.parse(todayText)
        val label = when {
            start == end && start == today -> "Dzisiaj"
            start == end && start == today.plusDays(1) -> "Jutro"
            start == end && start == today.plusDays(2) -> "Pojutrze"
            "przyszlym" in normalized || "nastepnym" in normalized -> "W przyszłym tygodniu"
            else -> "W tym tygodniu"
        }
        val tasks = listSnapshot?.tasks.orEmpty()
        val taskCount = countSnapshot?.count ?: tasks.size
        val text = when (kind) {
            Kind.COUNT -> "$label masz $taskCount ${taskWord(taskCount)}."
            Kind.EXISTS -> if (taskCount == 0) "Nie, ${label.lowercase(polish)} nie masz żadnych zadań."
                else "Tak, ${label.lowercase(polish)} masz $taskCount ${taskWord(taskCount)}."
            Kind.LIST -> if (tasks.isEmpty()) "$label nie masz żadnych zadań."
                else buildString {
                    append("$label masz $taskCount ${taskWord(taskCount)}:")
                    if (start == end) tasks.forEach { append("\n• ${it.title}${if (it.completed) " (wykonane)" else ""}") }
                    else tasks.groupBy { LocalDate.ofEpochDay(it.date) }.forEach { (date, dayTasks) ->
                        append("\n${date.format(weekday).replaceFirstChar { it.titlecase(polish) }}, $date")
                        dayTasks.forEach { append("\n• ${it.title}${if (it.completed) " (wykonane)" else ""}") }
                    }
                }
        }
        return PlannerReadAnswer(text, startText, endText, taskCount)
    }

    private fun taskWord(count: Int) = when {
        count == 1 -> "zadanie"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "zadania"
        else -> "zadań"
    }
}
