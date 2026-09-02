package com.aseprit.tiqo.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class LiteRtLlmEngine(private val context: Context) : LlmEngine {
    override val descriptor = LlmDescriptor(
        id = "litertlm",
        displayName = "LiteRT-LM",
        capabilities = setOf(LlmCapability.Chat, LlmCapability.PlannerToolCalls),
    )
    private val loadedState = MutableStateFlow(false)
    override val isLoaded: StateFlow<Boolean> = loadedState

    @Volatile private var engine: Engine? = null
    @Volatile private var activeConversation: Conversation? = null

    override suspend fun load(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(File(modelPath).isFile) { "Model file does not exist" }
            unloadInternal()
            val newEngine = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.absolutePath,
                ),
            )
            newEngine.initialize()
            engine = newEngine
            loadedState.value = true
        }
    }

    override fun generate(request: LlmRequest): Flow<LlmEvent> = flow {
        val currentEngine = engine
        if (currentEngine == null || !loadedState.value) {
            emit(LlmEvent.Failure("Local model is not loaded", recoverable = true))
            return@flow
        }
        val prompt = request.messages.lastOrNull { it.role == LlmRole.User }?.content
        if (prompt.isNullOrBlank()) {
            emit(LlmEvent.Failure("Prompt is empty", recoverable = true))
            return@flow
        }
        try {
            val conversation = currentEngine.createConversation()
            activeConversation = conversation
            conversation.sendMessageAsync(prompt).collect { message ->
                val text = message.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString(separator = "") { it.text }
                if (text.isNotEmpty()) emit(LlmEvent.Token(text))
            }
            emit(LlmEvent.Finished)
        } catch (error: Throwable) {
            emit(LlmEvent.Failure(error.message ?: "LiteRT-LM generation failed", recoverable = true))
        } finally {
            runCatching { activeConversation?.close() }
            activeConversation = null
        }
    }.flowOn(Dispatchers.Default)

    override suspend fun stopGeneration() = withContext(Dispatchers.IO) {
        runCatching { activeConversation?.close() }
        activeConversation = null
    }

    override suspend fun unload() = withContext(Dispatchers.IO) {
        unloadInternal()
    }

    private fun unloadInternal() {
        runCatching { activeConversation?.close() }
        activeConversation = null
        runCatching { engine?.close() }
        engine = null
        loadedState.value = false
    }
}
