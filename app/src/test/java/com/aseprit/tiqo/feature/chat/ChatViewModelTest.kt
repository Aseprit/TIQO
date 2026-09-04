package com.aseprit.tiqo.feature.chat

import com.aseprit.tiqo.ai.LlmCapability
import com.aseprit.tiqo.ai.LlmDescriptor
import com.aseprit.tiqo.ai.LlmEngine
import com.aseprit.tiqo.ai.LlmEvent
import com.aseprit.tiqo.ai.LlmRequest
import com.aseprit.tiqo.ai.PlannerAgent
import com.aseprit.tiqo.ai.tools.PlannerTools
import com.aseprit.tiqo.domain.model.ChatMessage
import com.aseprit.tiqo.domain.model.ChatRole
import com.aseprit.tiqo.domain.model.PlannerItem
import com.aseprit.tiqo.domain.model.PlannerOccurrence
import com.aseprit.tiqo.domain.model.WeeklyStatistics
import com.aseprit.tiqo.domain.repository.ChatRepository
import com.aseprit.tiqo.domain.repository.PlannerRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun generationStreamsMultipleChunksIntoOneAssistantMessage() = runTest(dispatcher) {
        val chat = RecordingEngine { flowOf(LlmEvent.Token("Ala "), LlmEvent.Token("ma kota"), LlmEvent.Finished) }
        val repository = FakeChatRepository()
        val viewModel = viewModel(repository, chat)
        advanceUntilIdle()

        viewModel.send("Napisz zdanie", "brak")
        advanceUntilIdle()

        assertEquals(listOf("Napisz zdanie", "Ala ma kota"), repository.messages.value.map { it.content })
        assertEquals(1, chat.requests.size)
        assertEquals("tiqo-current-chat", chat.requests.single().sessionId)
    }

    @Test fun stopCancelsGenerationAndKeepsPartialResponse() = runTest(dispatcher) {
        val chat = RecordingEngine {
            flow {
                emit(LlmEvent.Token("częściowa odpowiedź"))
                awaitCancellation()
            }
        }
        val repository = FakeChatRepository()
        val viewModel = viewModel(repository, chat)
        advanceUntilIdle()

        viewModel.send("Długi tekst", "brak")
        runCurrent()
        viewModel.stop()
        advanceUntilIdle()

        assertTrue(chat.stopCalls > 0)
        assertEquals("częściowa odpowiedź", repository.messages.value.last().content)
        assertTrue(!viewModel.uiState.value.isGenerating)
    }

    @Test fun clearChatResetsRuntimeAndNextRequestHasNoOldContext() = runTest(dispatcher) {
        var generation = 0
        val chat = RecordingEngine {
            if (generation++ == 0) {
                flow {
                    emit(LlmEvent.Token("stary partial"))
                    awaitCancellation()
                }
            } else {
                flowOf(LlmEvent.Token("nowa odpowiedź"), LlmEvent.Finished)
            }
        }
        val repository = FakeChatRepository()
        val viewModel = viewModel(repository, chat)
        advanceUntilIdle()
        viewModel.send("stara wiadomość", "brak")
        runCurrent()

        viewModel.clearChat()
        advanceUntilIdle()
        assertTrue(repository.messages.value.isEmpty())
        assertTrue(chat.stopCalls > 0)
        assertTrue(chat.resetCalls > 0)

        viewModel.send("nowa wiadomość", "brak")
        advanceUntilIdle()
        assertEquals(
            listOf("nowa wiadomość"),
            chat.requests.last().messages.filter { it.role == com.aseprit.tiqo.ai.LlmRole.User }.map { it.content },
        )
    }

    private fun viewModel(repository: FakeChatRepository, chat: RecordingEngine): ChatViewModel {
        val plannerEngine = RecordingEngine(loaded = false) { flowOf() }
        val plannerRepository = EmptyPlannerRepository()
        return ChatViewModel(
            repository,
            PlannerAgent(plannerEngine, plannerRepository, PlannerTools(plannerRepository)),
            plannerEngine,
            chat,
        )
    }
}

private class RecordingEngine(
    loaded: Boolean = true,
    private val events: () -> Flow<LlmEvent>,
) : LlmEngine {
    override val descriptor = LlmDescriptor("recording", "Recording", setOf(LlmCapability.Chat))
    override val isLoaded = MutableStateFlow(loaded)
    val requests = mutableListOf<LlmRequest>()
    var stopCalls = 0
    var resetCalls = 0

    override suspend fun load(modelPath: String) = Result.success(Unit).also { isLoaded.value = true }
    override fun generate(request: LlmRequest): Flow<LlmEvent> {
        requests += request
        return events()
    }
    override suspend fun stopGeneration() { stopCalls++ }
    override suspend fun resetConversation() { resetCalls++ }
    override suspend fun unload() { isLoaded.value = false }
}

private class FakeChatRepository : ChatRepository {
    val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private var nextId = 1L
    override suspend fun getOrCreateDefaultConversation() = 1L
    override fun observeMessages(conversationId: Long): Flow<List<ChatMessage>> = messages
    override suspend fun addMessage(
        conversationId: Long,
        role: ChatRole,
        content: String,
        toolName: String?,
        toolPayload: String?,
    ): Long {
        val id = nextId++
        messages.value += ChatMessage(id, conversationId, role, content, id, toolName, toolPayload)
        return id
    }
    override suspend fun updateMessage(messageId: Long, content: String) {
        messages.value = messages.value.map { if (it.id == messageId) it.copy(content = content) else it }
    }
    override suspend fun clearConversation(conversationId: Long) {
        messages.value = messages.value.filterNot { it.conversationId == conversationId }
    }
}

private class EmptyPlannerRepository : PlannerRepository {
    override fun observeOccurrences(date: LocalDate) = flowOf<List<PlannerOccurrence>>(emptyList())
    override fun observeOccurrences(startInclusive: LocalDate, endInclusive: LocalDate) = flowOf<List<PlannerOccurrence>>(emptyList())
    override fun observeWeeklyStatistics(weekStart: LocalDate) = flowOf(WeeklyStatistics(0, 0, emptyList()))
    override suspend fun getItem(itemId: Long): PlannerItem? = null
    override suspend fun save(item: PlannerItem) = 0L
    override suspend fun restore(item: PlannerItem) = 0L
    override suspend fun delete(itemId: Long) = Unit
    override suspend fun setCompleted(itemId: Long, occurrenceDate: LocalDate, completed: Boolean) = Unit
    override suspend fun isCompleted(itemId: Long, occurrenceDate: LocalDate) = false
    override suspend fun rescheduleAllReminders() = Unit
}
