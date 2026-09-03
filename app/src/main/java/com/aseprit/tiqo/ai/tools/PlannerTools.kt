package com.aseprit.tiqo.ai.tools

import com.aseprit.tiqo.domain.model.PlannerItem
import com.aseprit.tiqo.domain.model.PlannerItemType
import com.aseprit.tiqo.domain.model.PlannerOccurrence
import com.aseprit.tiqo.domain.model.RecurrenceRule
import com.aseprit.tiqo.domain.model.ReminderOffset
import com.aseprit.tiqo.domain.repository.PlannerRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first

data class PlannerToolContext(
    val currentDate: LocalDate,
    val currentTime: LocalTime,
    val zoneId: ZoneId,
    val locale: Locale,
    val selectedDate: LocalDate = currentDate,
    val candidateItems: List<PlannerItem> = emptyList(),
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
        val description: String = "",
        val allDay: Boolean = timeIso == null,
        val reminderMinutesBefore: Int? = null,
        val recurrence: RecurrenceRule = RecurrenceRule.None,
    ) : PlannerToolCall

    data class ListItems(val fromIso: String, val toIso: String = fromIso) : PlannerToolCall
    data class GetItem(val itemId: Long) : PlannerToolCall
    data class UpdateItem(
        val itemId: Long,
        val title: String? = null,
        val description: String? = null,
        val type: PlannerItemType? = null,
        val dateIso: String? = null,
        val timeIso: String? = null,
        val allDay: Boolean? = null,
        val reminderMinutesBefore: Int? = null,
        val removeReminder: Boolean = false,
        val recurrence: RecurrenceRule? = null,
    ) : PlannerToolCall
    data class DeleteItem(val itemId: Long, val confirmed: Boolean = false) : PlannerToolCall
    data class SetCompleted(val itemId: Long, val dateIso: String, val completed: Boolean) : PlannerToolCall
    data class SearchItems(val query: String, val fromIso: String? = null, val toIso: String? = null) : PlannerToolCall
    data class GetStatistics(val fromIso: String, val toIso: String) : PlannerToolCall
    data class DelegateToChat(val userRequest: String = "") : PlannerToolCall
    data class RequestClarification(
        val question: String,
        val reason: String,
        val candidateItemIds: List<Long> = emptyList(),
    ) : PlannerToolCall
}

data class PlannerActionCard(
    val action: String,
    val title: String? = null,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val itemId: Long? = null,
    val undoToken: String? = null,
)

sealed interface PlannerToolResult {
    data class Success(val card: PlannerActionCard, val message: String) : PlannerToolResult
    data class Items(val from: LocalDate, val to: LocalDate, val items: List<PlannerOccurrence>) : PlannerToolResult
    data class Item(val item: PlannerItem) : PlannerToolResult
    data class Statistics(val from: LocalDate, val to: LocalDate, val completed: Int, val scheduled: Int) : PlannerToolResult
    data class Clarification(val question: String, val reason: String, val candidates: List<PlannerItem>) : PlannerToolResult
    data class ConfirmationRequired(val operation: String) : PlannerToolResult
    data class ValidationError(val message: String) : PlannerToolResult
    data class ChatModelRequired(val userRequest: String) : PlannerToolResult
}

private sealed interface UndoAction {
    data class DeleteCreated(val itemId: Long) : UndoAction
    data class RestoreItem(val item: PlannerItem) : UndoAction
    data class RestoreCompletion(val itemId: Long, val date: LocalDate, val completed: Boolean) : UndoAction
}

class PlannerTools(private val repository: PlannerRepository) {
    private val undoActions = ConcurrentHashMap<String, UndoAction>()

    suspend fun execute(call: PlannerToolCall, context: PlannerToolContext): PlannerToolResult =
        runCatching {
            when (call) {
                is PlannerToolCall.CreateItem -> create(call, context)
                is PlannerToolCall.ListItems -> list(call)
                is PlannerToolCall.GetItem -> PlannerToolResult.Item(requireItem(call.itemId))
                is PlannerToolCall.UpdateItem -> update(call, context)
                is PlannerToolCall.DeleteItem -> delete(call)
                is PlannerToolCall.SetCompleted -> setCompleted(call)
                is PlannerToolCall.SearchItems -> search(call, context)
                is PlannerToolCall.GetStatistics -> statistics(call)
                is PlannerToolCall.DelegateToChat -> PlannerToolResult.ChatModelRequired(call.userRequest)
                is PlannerToolCall.RequestClarification -> PlannerToolResult.Clarification(
                    question = call.question.trim().ifEmpty { "Potrzebuję doprecyzowania." },
                    reason = call.reason.trim(),
                    candidates = call.candidateItemIds.mapNotNull { repository.getItem(it) },
                )
            }
        }.getOrElse { PlannerToolResult.ValidationError(it.message ?: "Nieprawidłowe polecenie") }

    suspend fun undo(token: String): PlannerToolResult = runCatching {
        when (val action = requireNotNull(undoActions.remove(token)) { "Ta operacja nie może już zostać cofnięta" }) {
            is UndoAction.DeleteCreated -> {
                repository.delete(action.itemId)
                PlannerToolResult.Success(PlannerActionCard("undone", itemId = action.itemId), "Cofnięto utworzenie")
            }
            is UndoAction.RestoreItem -> {
                val id = repository.restore(action.item)
                PlannerToolResult.Success(
                    PlannerActionCard("restored", action.item.title, action.item.startDate, action.item.time, id),
                    "Przywrócono ${action.item.title}",
                )
            }
            is UndoAction.RestoreCompletion -> {
                repository.setCompleted(action.itemId, action.date, action.completed)
                PlannerToolResult.Success(
                    PlannerActionCard("completion_restored", date = action.date, itemId = action.itemId),
                    "Cofnięto zmianę wykonania",
                )
            }
        }
    }.getOrElse { PlannerToolResult.ValidationError(it.message ?: "Nie udało się cofnąć operacji") }

    private suspend fun create(call: PlannerToolCall.CreateItem, context: PlannerToolContext): PlannerToolResult {
        val title = call.title.trim()
        require(title.isNotEmpty()) { "Tytuł nie może być pusty" }
        val date = parseDate(call.dateIso)
        val time = call.timeIso?.let(::parseTime)
        require(date >= context.currentDate.minusYears(1)) { "Data jest poza dozwolonym zakresem" }
        require(call.allDay || time != null) { "Element godzinowy wymaga godziny" }
        val item = PlannerItem(
            title = title,
            description = call.description.trim(),
            type = call.type,
            startDate = date,
            time = if (call.allDay) null else time,
            isAllDay = call.allDay,
            reminder = reminder(call.reminderMinutesBefore),
            recurrence = call.recurrence,
        )
        val id = repository.save(item)
        val token = remember(UndoAction.DeleteCreated(id))
        return PlannerToolResult.Success(
            PlannerActionCard("created", title, date, item.time, id, token),
            "Dodano $title",
        )
    }

    private suspend fun list(call: PlannerToolCall.ListItems): PlannerToolResult {
        val from = parseDate(call.fromIso)
        val to = parseDate(call.toIso)
        require(!to.isBefore(from)) { "Koniec zakresu jest przed początkiem" }
        require(to.toEpochDay() - from.toEpochDay() <= 366) { "Zakres jest zbyt szeroki" }
        return PlannerToolResult.Items(from, to, repository.observeOccurrences(from, to).first())
    }

    private suspend fun update(call: PlannerToolCall.UpdateItem, context: PlannerToolContext): PlannerToolResult {
        val previous = requireItem(call.itemId)
        require(
            call.title != null || call.description != null || call.type != null || call.dateIso != null ||
                call.timeIso != null || call.allDay != null || call.reminderMinutesBefore != null ||
                call.removeReminder || call.recurrence != null,
        ) { "Nie podano żadnej zmiany" }
        val newAllDay = call.allDay ?: previous.isAllDay
        val newTime = when {
            newAllDay -> null
            call.timeIso != null -> parseTime(call.timeIso)
            else -> previous.time
        }
        require(newAllDay || newTime != null) { "Element godzinowy wymaga godziny" }
        val updated = previous.copy(
            title = call.title?.trim()?.also { require(it.isNotEmpty()) } ?: previous.title,
            description = call.description?.trim() ?: previous.description,
            type = call.type ?: previous.type,
            startDate = call.dateIso?.let(::parseDate) ?: previous.startDate,
            time = newTime,
            isAllDay = newAllDay,
            reminder = when {
                call.removeReminder -> null
                call.reminderMinutesBefore != null -> reminder(call.reminderMinutesBefore)
                else -> previous.reminder
            },
            recurrence = call.recurrence ?: previous.recurrence,
        )
        require(updated.startDate >= context.currentDate.minusYears(1)) { "Data jest poza dozwolonym zakresem" }
        repository.save(updated)
        val token = remember(UndoAction.RestoreItem(previous))
        return PlannerToolResult.Success(
            PlannerActionCard("updated", updated.title, updated.startDate, updated.time, updated.id, token),
            "Zmieniono ${updated.title}",
        )
    }

    private suspend fun delete(call: PlannerToolCall.DeleteItem): PlannerToolResult {
        if (!call.confirmed) return PlannerToolResult.ConfirmationRequired("delete_item:${call.itemId}")
        val previous = requireItem(call.itemId)
        repository.delete(call.itemId)
        val token = remember(UndoAction.RestoreItem(previous))
        return PlannerToolResult.Success(
            PlannerActionCard("deleted", previous.title, previous.startDate, previous.time, previous.id, token),
            "Usunięto ${previous.title}",
        )
    }

    private suspend fun setCompleted(call: PlannerToolCall.SetCompleted): PlannerToolResult {
        val date = parseDate(call.dateIso)
        val item = requireItem(call.itemId)
        require(item.type == PlannerItemType.Task) { "Tylko zadanie można oznaczyć jako wykonane" }
        val previous = repository.isCompleted(call.itemId, date)
        repository.setCompleted(call.itemId, date, call.completed)
        val token = remember(UndoAction.RestoreCompletion(call.itemId, date, previous))
        return PlannerToolResult.Success(
            PlannerActionCard(if (call.completed) "completed" else "reopened", item.title, date, item.time, item.id, token),
            if (call.completed) "Oznaczono ${item.title} jako wykonane" else "Ponownie otwarto ${item.title}",
        )
    }

    private suspend fun search(call: PlannerToolCall.SearchItems, context: PlannerToolContext): PlannerToolResult {
        val query = call.query.trim()
        require(query.isNotEmpty()) { "Zapytanie nie może być puste" }
        val from = call.fromIso?.let(::parseDate) ?: context.currentDate.minusYears(1)
        val to = call.toIso?.let(::parseDate) ?: context.currentDate.plusYears(5)
        require(!to.isBefore(from)) { "Koniec zakresu jest przed początkiem" }
        val terms = query.lowercase(context.locale).split(Regex("\\s+")).filter { it.length > 1 }
        val items = repository.observeOccurrences(from, to).first().filter { occurrence ->
            val searchable = "${occurrence.item.title} ${occurrence.item.description}".lowercase(context.locale)
            terms.all(searchable::contains)
        }.distinctBy { it.item.id }
        return PlannerToolResult.Items(from, to, items)
    }

    private suspend fun statistics(call: PlannerToolCall.GetStatistics): PlannerToolResult {
        val from = parseDate(call.fromIso)
        val to = parseDate(call.toIso)
        require(!to.isBefore(from)) { "Koniec zakresu jest przed początkiem" }
        val tasks = repository.observeOccurrences(from, to).first().filter { it.item.type == PlannerItemType.Task }
        return PlannerToolResult.Statistics(from, to, tasks.count(PlannerOccurrence::isCompleted), tasks.size)
    }

    private suspend fun requireItem(id: Long): PlannerItem =
        requireNotNull(repository.getItem(id)) { "Element $id nie istnieje" }

    private fun parseDate(value: String): LocalDate = runCatching { LocalDate.parse(value) }
        .getOrElse { throw IllegalArgumentException("Nieprawidłowa data: $value") }

    private fun parseTime(value: String): LocalTime = runCatching { LocalTime.parse(value) }
        .getOrElse { throw IllegalArgumentException("Nieprawidłowa godzina: $value") }

    private fun reminder(minutes: Int?): ReminderOffset? = when (minutes) {
        null -> null
        0 -> ReminderOffset.AtTime
        5 -> ReminderOffset.FiveMinutes
        10 -> ReminderOffset.TenMinutes
        30 -> ReminderOffset.ThirtyMinutes
        60 -> ReminderOffset.OneHour
        1440 -> ReminderOffset.OneDay
        else -> throw IllegalArgumentException("Nieobsługiwane wyprzedzenie przypomnienia: $minutes")
    }

    private fun remember(action: UndoAction): String = UUID.randomUUID().toString().also { undoActions[it] = action }
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

data class PlannerPromptCase(
    val id: String,
    val prompt: String,
    val expectedToolName: String,
    val expectedArguments: Map<String, String> = emptyMap(),
)
