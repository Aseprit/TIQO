package com.aseprit.tiqo.ai

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerInferenceRunnerTest {
    @Test fun plannerTimeoutStopsGenerationAndReturnsConcreteError() = runBlocking {
        val engine = HangingEngine()

        val result = PlannerInferenceRunner(engine, timeoutMillis = 25L).run(
            LlmRequest(listOf(LlmMessage(LlmRole.User, "test")), maxOutputTokens = PLANNER_MAX_OUTPUT_TOKENS),
        )

        assertTrue(result.timedOut)
        assertEquals(1, engine.stopCalls)
        assertTrue(result.error.orEmpty().contains("przekroczył limit"))
    }

    @Test fun plannerOutputBudgetIsIndependentFromChatBudget() = runBlocking {
        val engine = RecordingEngine()
        val repository = AgentFakeRepositoryForLimits()
        val agent = PlannerAgent(engine, repository, com.aseprit.tiqo.ai.tools.PlannerTools(repository))

        agent.execute("Cześć")

        assertEquals(PLANNER_MAX_OUTPUT_TOKENS, engine.lastRequest?.maxOutputTokens)
        assertEquals(512, DEFAULT_CHAT_MAX_OUTPUT_TOKENS)
        assertNotEquals(DEFAULT_CHAT_MAX_OUTPUT_TOKENS, engine.lastRequest?.maxOutputTokens)
    }
}

private class HangingEngine : LlmEngine {
    var stopCalls = 0
    override val descriptor = LlmDescriptor("hanging", "hanging", setOf(LlmCapability.PlannerToolCalls))
    override val isLoaded = MutableStateFlow(true)
    override suspend fun load(modelPath: String) = Result.success(Unit)
    override fun generate(request: LlmRequest): Flow<LlmEvent> = flow { awaitCancellation() }
    override suspend fun stopGeneration() { stopCalls++ }
    override suspend fun resetConversation() = Unit
    override suspend fun unload() = Unit
}

private class RecordingEngine : LlmEngine {
    var lastRequest: LlmRequest? = null
    override val descriptor = LlmDescriptor("recording", "recording", setOf(LlmCapability.PlannerToolCalls))
    override val isLoaded = MutableStateFlow(true)
    override suspend fun load(modelPath: String) = Result.success(Unit)
    override fun generate(request: LlmRequest): Flow<LlmEvent> {
        lastRequest = request
        return flowOf(LlmEvent.ToolCall("delegate_to_chat", """{"user_request":"Cześć"}"""), LlmEvent.Finished)
    }
    override suspend fun stopGeneration() = Unit
    override suspend fun resetConversation() = Unit
    override suspend fun unload() = Unit
}

private class AgentFakeRepositoryForLimits : com.aseprit.tiqo.domain.repository.PlannerRepository {
    override fun observeOccurrences(date: java.time.LocalDate) = observeOccurrences(date, date)
    override fun observeOccurrences(startInclusive: java.time.LocalDate, endInclusive: java.time.LocalDate) =
        flowOf(emptyList<com.aseprit.tiqo.domain.model.PlannerOccurrence>())
    override fun observeWeeklyStatistics(weekStart: java.time.LocalDate) =
        flowOf(com.aseprit.tiqo.domain.model.WeeklyStatistics(0, 0, emptyList()))
    override suspend fun getItem(itemId: Long) = null
    override suspend fun save(item: com.aseprit.tiqo.domain.model.PlannerItem) = 1L
    override suspend fun restore(item: com.aseprit.tiqo.domain.model.PlannerItem) = 1L
    override suspend fun delete(itemId: Long) = Unit
    override suspend fun setCompleted(itemId: Long, occurrenceDate: java.time.LocalDate, completed: Boolean) = Unit
    override suspend fun isCompleted(itemId: Long, occurrenceDate: java.time.LocalDate) = false
    override suspend fun rescheduleAllReminders() = Unit
}
