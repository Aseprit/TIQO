package com.aseprit.tiqo.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aseprit.tiqo.ai.AiAgentStatus
import com.aseprit.tiqo.ai.LlmEngine
import com.aseprit.tiqo.ai.LlmEvent
import com.aseprit.tiqo.ai.LlmMessage
import com.aseprit.tiqo.ai.LlmRequest
import com.aseprit.tiqo.ai.LlmRole
import com.aseprit.tiqo.ai.PlannerAgent
import com.aseprit.tiqo.ai.tools.PlannerToolResult
import com.aseprit.tiqo.domain.model.ChatMessage
import com.aseprit.tiqo.domain.model.ChatRole
import com.aseprit.tiqo.domain.repository.ChatRepository
import com.google.gson.Gson
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ChatAiStatus { Unavailable, Ready, Thinking, Executing, Generating, Error }

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val plannerLoaded: Boolean = false,
    val chatModelLoaded: Boolean = false,
    val status: ChatAiStatus = ChatAiStatus.Unavailable,
    val undoTokens: Map<Long, String> = emptyMap(),
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repository: ChatRepository,
    private val plannerAgent: PlannerAgent,
    private val plannerEngine: LlmEngine,
    private val chatEngine: LlmEngine,
) : ViewModel() {
    private val conversationId = MutableStateFlow<Long?>(null)
    private val generating = MutableStateFlow(false)
    private val status = MutableStateFlow(ChatAiStatus.Unavailable)
    private val undoTokens = MutableStateFlow<Map<Long, String>>(emptyMap())
    private var generationJob: Job? = null

    private val messages = conversationId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeMessages(id)
    }

    val uiState: StateFlow<ChatUiState> = kotlinx.coroutines.flow.combine(
        messages,
        generating,
        plannerEngine.isLoaded,
        chatEngine.isLoaded,
        status,
        undoTokens,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val plannerLoaded = values[2] as Boolean
        val rawStatus = values[4] as ChatAiStatus
        ChatUiState(
            messages = (values[0] as List<ChatMessage>).filter { it.role != ChatRole.Tool },
            isGenerating = values[1] as Boolean,
            plannerLoaded = plannerLoaded,
            chatModelLoaded = values[3] as Boolean,
            status = when {
                !plannerLoaded && rawStatus != ChatAiStatus.Generating -> ChatAiStatus.Unavailable
                plannerLoaded && rawStatus == ChatAiStatus.Unavailable -> ChatAiStatus.Ready
                else -> rawStatus
            },
            undoTokens = values[5] as Map<Long, String>,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        viewModelScope.launch {
            conversationId.value = repository.getOrCreateDefaultConversation()
            status.value = if (plannerEngine.isLoaded.value) ChatAiStatus.Ready else ChatAiStatus.Unavailable
        }
    }

    fun send(text: String, unavailableMessage: String) {
        val clean = text.trim()
        val id = conversationId.value ?: return
        if (clean.isEmpty() || generating.value) return
        generationJob = viewModelScope.launch {
            repository.addMessage(id, ChatRole.User, clean)
            generating.value = true
            try {
                if (!plannerEngine.isLoaded.value) {
                    repository.addMessage(id, ChatRole.Assistant, unavailableMessage)
                    status.value = ChatAiStatus.Unavailable
                    return@launch
                }
                val result = plannerAgent.execute(
                    command = clean,
                    onStatus = { agentStatus ->
                        status.value = when (agentStatus) {
                            AiAgentStatus.Ready -> ChatAiStatus.Ready
                            AiAgentStatus.Thinking -> ChatAiStatus.Thinking
                            AiAgentStatus.Executing -> ChatAiStatus.Executing
                            AiAgentStatus.Error -> ChatAiStatus.Error
                        }
                    },
                    onToolCall = { name, arguments ->
                        repository.addMessage(
                            id,
                            ChatRole.Tool,
                            "Polecenie lokalne: $name",
                            toolName = name,
                            toolPayload = arguments,
                        )
                    },
                )
                handlePlannerResult(id, clean, result, unavailableMessage)
            } finally {
                generating.value = false
                if (status.value !in setOf(ChatAiStatus.Error, ChatAiStatus.Unavailable)) {
                    status.value = ChatAiStatus.Ready
                }
            }
        }
    }

    fun undo(messageId: Long) {
        val token = undoTokens.value[messageId] ?: return
        val id = conversationId.value ?: return
        viewModelScope.launch {
            val result = plannerAgent.undo(token)
            repository.addMessage(id, ChatRole.Tool, Gson().toJson(result), "undo_result", Gson().toJson(result))
            repository.addMessage(id, ChatRole.Assistant, renderResult(result))
            undoTokens.value = undoTokens.value - messageId
        }
    }

    fun stop() {
        generationJob?.cancel()
        generationJob = null
        generating.value = false
        status.value = if (plannerEngine.isLoaded.value) ChatAiStatus.Ready else ChatAiStatus.Unavailable
        viewModelScope.launch {
            plannerEngine.stopGeneration()
            chatEngine.stopGeneration()
        }
    }

    private suspend fun handlePlannerResult(
        id: Long,
        originalRequest: String,
        result: PlannerToolResult,
        unavailableMessage: String,
    ) {
        repository.addMessage(id, ChatRole.Tool, "Wynik lokalnego narzędzia", "tool_result", Gson().toJson(result))
        when (result) {
            is PlannerToolResult.ChatModelRequired -> {
                if (!chatEngine.isLoaded.value) {
                    status.value = ChatAiStatus.Unavailable
                    repository.addMessage(id, ChatRole.Assistant, "To pytanie wymaga lokalnego modelu konwersacyjnego. Otwórz Menedżer modeli, aby go pobrać.")
                } else {
                    streamChat(id, result.userRequest.ifBlank { originalRequest }, unavailableMessage)
                }
            }
            else -> {
                val assistantId = repository.addMessage(id, ChatRole.Assistant, renderResult(result))
                val token = (result as? PlannerToolResult.Success)?.card?.undoToken
                if (token != null) undoTokens.value = undoTokens.value + (assistantId to token)
            }
        }
    }

    private suspend fun streamChat(id: Long, request: String, unavailableMessage: String) {
        status.value = ChatAiStatus.Generating
        var assistantMessageId: Long? = null
        var partial = ""
        val recent = uiState.value.messages
            .filter { it.role != ChatRole.Tool }
            .takeLast(8)
            .map { LlmMessage(if (it.role == ChatRole.User) LlmRole.User else LlmRole.Assistant, it.content) }
        val history = if (recent.lastOrNull()?.content == request) recent else recent + LlmMessage(LlmRole.User, request)
        chatEngine.generate(LlmRequest(history)).collect { event ->
            when (event) {
                is LlmEvent.Token -> {
                    partial = if (event.content.startsWith(partial)) event.content else partial + event.content
                    val messageId = assistantMessageId ?: repository.addMessage(id, ChatRole.Assistant, partial)
                        .also { assistantMessageId = it }
                    repository.updateMessage(messageId, partial)
                }
                is LlmEvent.Failure -> repository.addMessage(id, ChatRole.Assistant, unavailableMessage)
                is LlmEvent.ToolCall -> Unit
                LlmEvent.Finished -> Unit
            }
        }
    }

    private fun renderResult(result: PlannerToolResult): String = when (result) {
        is PlannerToolResult.Success -> buildString {
            append(result.message)
            result.card.date?.let { append("\n").append(it) }
            result.card.time?.let { append(" · ").append(it.format(TIME_FORMAT)) }
        }
        is PlannerToolResult.Items -> if (result.items.isEmpty()) {
            "Brak elementów w tym terminie."
        } else {
            result.items.joinToString("\n") { occurrence ->
                val time = occurrence.item.time?.format(TIME_FORMAT)?.let { " · $it" }.orEmpty()
                "• ${occurrence.item.title} — ${occurrence.date}$time"
            }
        }
        is PlannerToolResult.Item -> "${result.item.title} — ${result.item.startDate}${result.item.time?.let { " · $it" }.orEmpty()}"
        is PlannerToolResult.Statistics -> "Wykonano ${result.completed} z ${result.scheduled} zadań."
        is PlannerToolResult.Clarification -> result.question
        is PlannerToolResult.ConfirmationRequired -> "Ta operacja wymaga potwierdzenia."
        is PlannerToolResult.ValidationError -> result.message
        is PlannerToolResult.ChatModelRequired -> "Wymagany jest model konwersacyjny."
    }

    class Factory(
        private val repository: ChatRepository,
        private val plannerAgent: PlannerAgent,
        private val plannerEngine: LlmEngine,
        private val chatEngine: LlmEngine,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatViewModel(repository, plannerAgent, plannerEngine, chatEngine) as T
    }

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
