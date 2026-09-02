package com.aseprit.tiqo.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aseprit.tiqo.ai.LlmEngine
import com.aseprit.tiqo.ai.LlmEvent
import com.aseprit.tiqo.ai.LlmMessage
import com.aseprit.tiqo.ai.LlmRequest
import com.aseprit.tiqo.ai.LlmRole
import com.aseprit.tiqo.domain.model.ChatMessage
import com.aseprit.tiqo.domain.model.ChatRole
import com.aseprit.tiqo.domain.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val modelLoaded: Boolean = false,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repository: ChatRepository,
    private val engine: LlmEngine,
) : ViewModel() {
    private val conversationId = MutableStateFlow<Long?>(null)
    private val generating = MutableStateFlow(false)
    private var generationJob: Job? = null

    private val messages = conversationId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeMessages(id)
    }

    val uiState: StateFlow<ChatUiState> = kotlinx.coroutines.flow.combine(
        messages,
        generating,
        engine.isLoaded,
    ) { items, isGenerating, loaded -> ChatUiState(items, isGenerating, loaded) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        viewModelScope.launch { conversationId.value = repository.getOrCreateDefaultConversation() }
    }

    fun send(text: String, unavailableMessage: String) {
        val clean = text.trim()
        val id = conversationId.value ?: return
        if (clean.isEmpty() || generating.value) return
        generationJob = viewModelScope.launch {
            repository.addMessage(id, ChatRole.User, clean)
            generating.value = true
            var assistantMessageId: Long? = null
            var partial = ""
            try {
                engine.generate(
                    LlmRequest(messages = listOf(LlmMessage(LlmRole.User, clean))),
                ).collect { event ->
                    when (event) {
                        is LlmEvent.Token -> {
                            partial = if (event.content.startsWith(partial)) {
                                event.content
                            } else {
                                partial + event.content
                            }
                            val messageId = assistantMessageId ?: repository
                                .addMessage(id, ChatRole.Assistant, partial)
                                .also { assistantMessageId = it }
                            repository.updateMessage(messageId, partial)
                        }
                        is LlmEvent.Failure -> {
                            repository.addMessage(id, ChatRole.Assistant, unavailableMessage)
                        }
                        is LlmEvent.ToolCall -> {
                            repository.addMessage(
                                id,
                                ChatRole.Tool,
                                "${event.name}: ${event.argumentsJson}",
                            )
                        }
                        LlmEvent.Finished -> Unit
                    }
                }
            } finally {
                generating.value = false
            }
        }
    }

    fun stop() {
        generationJob?.cancel()
        generationJob = null
        generating.value = false
        viewModelScope.launch { engine.stopGeneration() }
    }

    class Factory(
        private val repository: ChatRepository,
        private val engine: LlmEngine,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(repository, engine) as T
    }
}
