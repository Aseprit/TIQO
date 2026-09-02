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

        assertTrue(result is PlannerToolResult.Created)
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
            PlannerToolCall.DelegateToChat,
            context,
        )

        assertEquals(PlannerToolResult.ChatModelRequired, result)
    }
}

private class FakeToolRepository : PlannerRepository {
    val items = mutableListOf<PlannerItem>()
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

    override suspend fun delete(itemId: Long) {
        items.removeAll { it.id == itemId }
    }

    override suspend fun setCompleted(itemId: Long, occurrenceDate: LocalDate, completed: Boolean) = Unit
    override suspend fun isCompleted(itemId: Long, occurrenceDate: LocalDate): Boolean = false
    override suspend fun rescheduleAllReminders() = Unit
}
