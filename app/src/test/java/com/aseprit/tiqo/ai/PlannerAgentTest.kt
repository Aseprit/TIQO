package com.aseprit.tiqo.ai

import com.aseprit.tiqo.ai.tools.PlannerToolProtocol
import com.aseprit.tiqo.ai.tools.PlannerToolResult
import com.aseprit.tiqo.ai.tools.PlannerTools
import com.aseprit.tiqo.domain.model.PlannerItem
import com.aseprit.tiqo.domain.model.PlannerOccurrence
import com.aseprit.tiqo.domain.model.ReminderOffset
import com.aseprit.tiqo.domain.model.WeeklyStatistics
import com.aseprit.tiqo.domain.repository.PlannerRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerAgentTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-02T19:55:00Z"), ZoneId.of("Europe/Warsaw"))

    @Test fun toolCallCreatesRealRepositoryItemWithReminderAndUndo() = runBlocking {
        val repository = AgentFakeRepository()
        val engine = ScriptedEngine(
            LlmEvent.ToolCall(
                "create_item",
                """{"title":"Dentysta","type":"TASK","date":"2026-09-03","time":"10:00","all_day":false,"reminder_minutes_before":30}""",
            ),
        )
        val tools = PlannerTools(repository)
        val agent = PlannerAgent(engine, repository, tools, clock, Locale("pl", "PL"))

        val result = agent.execute("Dodaj mi jutro o 10 dentystę i przypomnij 30 minut wcześniej")

        assertTrue(result is PlannerToolResult.Success)
        assertEquals(LocalTime.of(10, 0), repository.items.single().time)
        assertEquals(ReminderOffset.ThirtyMinutes, repository.items.single().reminder)
        val token = (result as PlannerToolResult.Success).card.undoToken
        assertNotNull(token)
        agent.undo(token!!)
        assertTrue(repository.items.isEmpty())
    }

    @Test fun malformedArgumentsNeverMutateRepository() = runBlocking {
        val repository = AgentFakeRepository()
        val engine = ScriptedEngine(LlmEvent.ToolCall("create_item", """{"title":"X","date":"jutro"}"""))
        val agent = PlannerAgent(engine, repository, PlannerTools(repository), clock)

        val result = agent.execute("dodaj jutro x")

        assertTrue(result is PlannerToolResult.ValidationError)
        assertTrue(repository.items.isEmpty())
    }

    @Test fun protocolRejectsUnknownFields() {
        val result = PlannerToolProtocol.parse("delete_item", """{"item_id":1,"all":true}""")
        assertTrue(result.isFailure)
    }
}

private class ScriptedEngine(private val event: LlmEvent) : LlmEngine {
    override val descriptor = LlmDescriptor("test", "test", setOf(LlmCapability.PlannerToolCalls))
    override val isLoaded = MutableStateFlow(true)
    override suspend fun load(modelPath: String) = Result.success(Unit)
    override fun generate(request: LlmRequest): Flow<LlmEvent> = flowOf(event, LlmEvent.Finished)
    override suspend fun stopGeneration() = Unit
    override suspend fun unload() { isLoaded.value = false }
}

private class AgentFakeRepository : PlannerRepository {
    val items = mutableListOf<PlannerItem>()
    private var nextId = 1L
    override fun observeOccurrences(date: LocalDate) = observeOccurrences(date, date)
    override fun observeOccurrences(startInclusive: LocalDate, endInclusive: LocalDate): Flow<List<PlannerOccurrence>> =
        flowOf(items.filter { it.startDate in startInclusive..endInclusive }.map { PlannerOccurrence(it, it.startDate, false) })
    override fun observeWeeklyStatistics(weekStart: LocalDate) = flowOf(WeeklyStatistics(0, 0, emptyList()))
    override suspend fun getItem(itemId: Long) = items.firstOrNull { it.id == itemId }
    override suspend fun save(item: PlannerItem): Long {
        val id = item.id.takeIf { it > 0 } ?: nextId++
        items.removeAll { it.id == id }
        items += item.copy(id = id)
        return id
    }
    override suspend fun restore(item: PlannerItem): Long = save(item)
    override suspend fun delete(itemId: Long) { items.removeAll { it.id == itemId } }
    override suspend fun setCompleted(itemId: Long, occurrenceDate: LocalDate, completed: Boolean) = Unit
    override suspend fun isCompleted(itemId: Long, occurrenceDate: LocalDate) = false
    override suspend fun rescheduleAllReminders() = Unit
}
