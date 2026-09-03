package com.aseprit.tiqo.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.tool
import com.google.gson.Gson
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
        val lastUserIndex = request.messages.indexOfLast { it.role == LlmRole.User }
        val prompt = request.messages.getOrNull(lastUserIndex)?.content
        if (prompt.isNullOrBlank()) {
            emit(LlmEvent.Failure("Prompt is empty", recoverable = true))
            return@flow
        }
        try {
            val systemInstruction = request.messages
                .filter { it.role == LlmRole.System }
                .joinToString("\n") { it.content }
                .takeIf(String::isNotBlank)
                ?.let(Contents::of)
            val history = request.messages.take(lastUserIndex)
                .filter { it.role != LlmRole.System }
                .map { message ->
                    when (message.role) {
                        LlmRole.User -> Message.user(message.content)
                        LlmRole.Assistant -> Message.model(message.content)
                        LlmRole.System -> error("System messages are handled separately")
                    }
                }
            val providers = request.availableTools.map { definition ->
                tool(object : OpenApiTool {
                    override fun getToolDescriptionJsonString(): String =
                        """{"name":${Gson().toJson(definition.name)},"description":${Gson().toJson(definition.description)},"parameters":${definition.inputSchema}}"""

                    override fun execute(argumentsJson: String): String =
                        error("Tiqo uses manual tool execution")
                })
            }
            val conversation = currentEngine.createConversation(
                ConversationConfig(
                    systemInstruction = systemInstruction,
                    initialMessages = history,
                    tools = providers,
                    automaticToolCalling = false,
                    maxOutputToken = 256,
                ),
            )
            activeConversation = conversation
            val emittedCalls = mutableSetOf<String>()
            conversation.sendMessageAsync(Message.user(prompt)).collect { message ->
                message.toolCalls.forEach { call ->
                    val arguments = Gson().toJson(call.arguments)
                    val key = "${call.name}:$arguments"
                    if (emittedCalls.add(key)) emit(LlmEvent.ToolCall(call.name, arguments))
                }
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
        runCatching { activeConversation?.cancelProcess() }
        Unit
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
