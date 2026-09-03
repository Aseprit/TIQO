package com.aseprit.tiqo.ai.tools

import com.aseprit.tiqo.domain.model.PlannerItem
import com.aseprit.tiqo.domain.model.PlannerOccurrence
import com.aseprit.tiqo.domain.model.WeeklyStatistics
import com.aseprit.tiqo.domain.repository.PlannerRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerToolsTest {
    private val context = PlannerToolContext(
        currentDate = LocalDate.of(2026, 9, 2),
        currentTime = LocalTime.NOON,
        zoneId = ZoneId.of("Europe/Warsaw"),
        locale = Locale("pl", "PL"),
    )

    @Test fun createItem_validatesAndUsesRepository() = runBlocking {
        val repository = FakeToolRepository()
        val tools = PlannerTools(repository)

        val result = tools.execute(
            PlannerToolCall.CreateItem("Dentysta", "2026-09-03", "10:00"),
            context,
        )

        assertTrue(result is PlannerToolResult.Success)
        assertEquals("Dentysta", repository.items.single().title)
        assertEquals(LocalTime.of(10, 0), repository.items.single().time)
    }

    @Test fun deleteRequiresExplicitConfirmation() = runBlocking {
        val repository = FakeToolRepository().apply {
            save(PlannerItem(title = "Siłownia", startDate = context.currentDate))
        }
        val tools = PlannerTools(repository)

        val result = tools.execute(PlannerToolCall.DeleteItem(1), context)

        assertTrue(result is PlannerToolResult.ConfirmationRequired)
        assertEquals(1, repository.items.size)
    }

    @Test fun unrelatedRequestDelegatesInsteadOfInventingPlannerAnswer() = runBlocking {
        val result = PlannerTools(FakeToolRepository()).execute(
            PlannerToolCall.DelegateToChat("dlaczego niebo jest niebieskie"),
            context,
        )

        assertEquals(
            PlannerToolResult.ChatModelRequired("dlaczego niebo jest niebieskie"),
            result,
        )
    }

    @Test fun updateAndUndoRestorePreviousItem() = runBlocking {
        val repository = FakeToolRepository()
        repository.save(PlannerItem(title = "Dentysta", startDate = context.currentDate.plusDays(1), time = LocalTime.of(10, 0)))
        val tools = PlannerTools(repository)

        val changed = tools.execute(PlannerToolCall.UpdateItem(1, timeIso = "11:00"), context) as PlannerToolResult.Success
        assertEquals(LocalTime.of(11, 0), repository.items.single().time)

        tools.undo(changed.card.undoToken!!)
        assertEquals(LocalTime.of(10, 0), repository.items.single().time)
    }

    @Test fun deleteAndUndoRestoreStableId() = runBlocking {
        val repository = FakeToolRepository()
        repository.save(PlannerItem(title = "Trening", startDate = context.currentDate))
        val tools = PlannerTools(repository)

        val deleted = tools.execute(PlannerToolCall.DeleteItem(1, confirmed = true), context) as PlannerToolResult.Success
        assertTrue(repository.items.isEmpty())

        tools.undo(deleted.card.undoToken!!)
        assertEquals(1L, repository.items.single().id)
    }

    @Test fun completionAndUndoRestorePreviousOccurrenceState() = runBlocking {
        val repository = FakeToolRepository()
        repository.save(PlannerItem(title = "Pranie", startDate = context.currentDate))
        val tools = PlannerTools(repository)

        val completed = tools.execute(PlannerToolCall.SetCompleted(1, context.currentDate.toString(), true), context) as PlannerToolResult.Success
        assertTrue(repository.isCompleted(1, context.currentDate))

        tools.undo(completed.card.undoToken!!)
        assertTrue(!repository.isCompleted(1, context.currentDate))
    }
}

private class FakeToolRepository : PlannerRepository {
    val items = mutableListOf<PlannerItem>()
    private val completions = mutableSetOf<Pair<Long, LocalDate>>()
    private var nextId = 1L

    override fun observeOccurrences(date: LocalDate): Flow<List<PlannerOccurrence>> =
        observeOccurrences(date, date)

    override fun observeOccurrences(
        startInclusive: LocalDate,
        endInclusive: LocalDate,
    ): Flow<List<PlannerOccurrence>> = flowOf(
        items.filter { it.startDate in startInclusive..endInclusive }
            .map { PlannerOccurrence(it, it.startDate, false) },
    )

    override fun observeWeeklyStatistics(weekStart: LocalDate): Flow<WeeklyStatistics> =
        flowOf(WeeklyStatistics(completed = 0, scheduled = 0, days = emptyList()))

    override suspend fun getItem(itemId: Long): PlannerItem? = items.firstOrNull { it.id == itemId }

    override suspend fun save(item: PlannerItem): Long {
        val id = item.id.takeIf { it != 0L } ?: nextId++
        items.removeAll { it.id == id }
        items += item.copy(id = id)
        return id
    }

    override suspend fun restore(item: PlannerItem): Long {
        items.removeAll { it.id == item.id }
        items += item
        return item.id
    }

    override suspend fun delete(itemId: Long) {
        items.removeAll { it.id == itemId }
    }

    override suspend fun setCompleted(itemId: Long, occurrenceDate: LocalDate, completed: Boolean) {
        if (completed) completions += itemId to occurrenceDate else completions -= itemId to occurrenceDate
    }
    override suspend fun isCompleted(itemId: Long, occurrenceDate: LocalDate): Boolean =
        itemId to occurrenceDate in completions
    override suspend fun rescheduleAllReminders() = Unit
}
