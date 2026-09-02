package com.aseprit.tiqo.ai.tools

import com.aseprit.tiqo.domain.model.PlannerItem
import com.aseprit.tiqo.domain.model.PlannerItemType
import com.aseprit.tiqo.domain.model.RecurrenceRule
import com.aseprit.tiqo.domain.repository.PlannerRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.first

data class PlannerToolContext(
    val currentDate: LocalDate,
    val currentTime: LocalTime,
    val zoneId: ZoneId,
    val locale: Locale,
) {
    companion object {
        fun from(clock: Clock, locale: Locale): PlannerToolContext = PlannerToolContext(
            currentDate = LocalDate.now(clock),
            currentTime = LocalTime.now(clock),
            zoneId = clock.zone,
            locale = locale,
        )
    }
}

sealed interface PlannerToolCall {
    data class CreateItem(
        val title: String,
        val dateIso: String,
        val timeIso: String? = null,
        val type: PlannerItemType = PlannerItemType.Task,
        val recurrence: RecurrenceRule = RecurrenceRule.None,
    ) : PlannerToolCall

    data class ListItems(val dateIso: String) : PlannerToolCall
    data class SetCompleted(val itemId: Long, val dateIso: String, val completed: Boolean) : PlannerToolCall
    data class DeleteItem(val itemId: Long, val confirmed: Boolean = false) : PlannerToolCall
    data object DelegateToChat : PlannerToolCall
}

sealed interface PlannerToolResult {
    data class Created(val itemId: Long, val item: PlannerItem) : PlannerToolResult
    data class Items(val date: LocalDate, val items: List<com.aseprit.tiqo.domain.model.PlannerOccurrence>) : PlannerToolResult
    data class CompletionChanged(val itemId: Long, val date: LocalDate, val completed: Boolean) : PlannerToolResult
    data class Deleted(val itemId: Long) : PlannerToolResult
    data class ConfirmationRequired(val operation: String) : PlannerToolResult
    data class ValidationError(val message: String) : PlannerToolResult
    data object ChatModelRequired : PlannerToolResult
}

class PlannerTools(private val repository: PlannerRepository) {
    suspend fun execute(call: PlannerToolCall, context: PlannerToolContext): PlannerToolResult =
        runCatching {
            when (call) {
                is PlannerToolCall.CreateItem -> {
                    val title = call.title.trim()
                    require(title.isNotEmpty()) { "title must not be empty" }
                    val date = LocalDate.parse(call.dateIso)
                    val time = call.timeIso?.let(LocalTime::parse)
                    require(date >= context.currentDate.minusYears(1)) { "date is outside the accepted range" }
                    val item = PlannerItem(
                        title = title,
                        type = call.type,
                        startDate = date,
                        time = time,
                        isAllDay = time == null,
                        recurrence = call.recurrence,
                    )
                    PlannerToolResult.Created(repository.save(item), item)
                }
                is PlannerToolCall.ListItems -> {
                    val date = LocalDate.parse(call.dateIso)
                    PlannerToolResult.Items(date, repository.observeOccurrences(date).first())
                }
                is PlannerToolCall.SetCompleted -> {
                    val date = LocalDate.parse(call.dateIso)
                    val item = repository.getItem(call.itemId) ?: error("item does not exist")
                    require(item.type == PlannerItemType.Task) { "only TASK can be completed" }
                    repository.setCompleted(call.itemId, date, call.completed)
                    PlannerToolResult.CompletionChanged(call.itemId, date, call.completed)
                }
                is PlannerToolCall.DeleteItem -> {
                    if (!call.confirmed) {
                        PlannerToolResult.ConfirmationRequired("delete_item:${call.itemId}")
                    } else {
                        requireNotNull(repository.getItem(call.itemId)) { "item does not exist" }
                        repository.delete(call.itemId)
                        PlannerToolResult.Deleted(call.itemId)
                    }
                }
                PlannerToolCall.DelegateToChat -> PlannerToolResult.ChatModelRequired
            }
        }.getOrElse { error ->
            PlannerToolResult.ValidationError(error.message ?: "invalid tool call")
        }
}

object PolishRelativeDateResolver {
    fun resolve(phrase: String, context: PlannerToolContext): LocalDate? {
        val normalized = phrase.trim().lowercase(context.locale)
        return when (normalized) {
            "dzisiaj" -> context.currentDate
            "jutro" -> context.currentDate.plusDays(1)
            "pojutrze" -> context.currentDate.plusDays(2)
            "za tydzień" -> context.currentDate.plusWeeks(1)
            else -> resolveWeekday(normalized, context)
        }
    }

    private fun resolveWeekday(value: String, context: PlannerToolContext): LocalDate? {
        val nextRequested = value.startsWith("następny ")
        val dayText = value.removePrefix("następny ").removePrefix("w ")
        val target = DayOfWeek.entries.firstOrNull { day ->
            val full = day.getDisplayName(TextStyle.FULL, context.locale).lowercase(context.locale)
            dayText == full || dayText == full.removeSuffix("ek")
        } ?: return null
        var daysAhead = (target.value - context.currentDate.dayOfWeek.value + 7) % 7
        if (daysAhead == 0 || nextRequested) daysAhead += 7
        return context.currentDate.plusDays(daysAhead.toLong())
    }
}

/** Dataset-friendly case format for future PlannerGemma evaluations. */
data class PlannerPromptCase(
    val id: String,
    val prompt: String,
    val expectedToolName: String,
    val expectedArguments: Map<String, String> = emptyMap(),
)

