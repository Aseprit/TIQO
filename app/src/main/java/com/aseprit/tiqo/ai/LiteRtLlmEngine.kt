package com.aseprit.tiqo.ai

import android.content.Context
import android.os.Debug
import android.util.Log
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private var persistentConversation: Conversation? = null
    private var persistentSessionId: String? = null
    private val generationMutex = Mutex()
    @Volatile private var cancellationRequested = false

    override suspend fun load(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            require(File(modelPath).isFile) { "Plik modelu nie istnieje" }
            generationMutex.withLock {
                unloadInternal()
                Log.i(TAG, "event=model_load_start backend=CPU model=${File(modelPath).name} ${memorySnapshot()}")
                val newEngine = Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU(),
                        cacheDir = context.cacheDir.absolutePath,
                    ),
                )
                try {
                    newEngine.initialize()
                } catch (error: Throwable) {
                    runCatching { newEngine.close() }
                    throw error
                }
                engine = newEngine
                loadedState.value = true
                Log.i(TAG, "event=model_load_success backend=CPU model=${File(modelPath).name} ${memorySnapshot()}")
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            Log.i(TAG, "event=model_load_cancelled", cancelled)
            throw cancelled
        } catch (error: Throwable) {
            val classified = classify(error, LlmFailureKind.ModelLoad)
            Log.e(TAG, "event=model_load_failure kind=${classified.kind} ${memorySnapshot()}", error)
            Result.failure(classified)
        }
    }

    override fun generate(request: LlmRequest): Flow<LlmEvent> = flow {
        generationMutex.withLock {
            val currentEngine = engine
            if (currentEngine == null || !loadedState.value) {
                emit(LlmEvent.Failure("Lokalny model nie jest załadowany", recoverable = true, kind = LlmFailureKind.ModelLoad))
                return@withLock
            }
            val lastUserIndex = request.messages.indexOfLast { it.role == LlmRole.User }
            val prompt = request.messages.getOrNull(lastUserIndex)?.content
            if (prompt.isNullOrBlank()) {
                emit(LlmEvent.Failure("Wiadomość jest pusta", recoverable = true))
                return@withLock
            }
            var conversation: Conversation? = null
            var closeAfterGeneration = request.sessionId == null
            cancellationRequested = false
            try {
                val currentConversation = conversationFor(currentEngine, request, lastUserIndex)
                conversation = currentConversation
                Log.i(TAG, "event=inference_start backend=CPU persistent=${request.sessionId != null} ${memorySnapshot()}")
                activeConversation = currentConversation
                val emittedCalls = mutableSetOf<String>()
                currentConversation.sendMessageAsync(Message.user(prompt)).collect { message ->
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
                Log.i(TAG, "event=inference_finished backend=CPU ${memorySnapshot()}")
            } catch (cancelled: CancellationException) {
                closeAfterGeneration = true
                Log.i(TAG, "event=inference_cancelled backend=CPU", cancelled)
                throw cancelled
            } catch (error: Throwable) {
                closeAfterGeneration = true
                val classified = classify(error, LlmFailureKind.Inference)
                Log.e(TAG, "event=inference_failure kind=${classified.kind} backend=CPU ${memorySnapshot()}", error)
                emit(
                    LlmEvent.Failure(
                        reason = classified.message ?: "Generowanie LiteRT-LM nie powiodło się",
                        recoverable = classified.kind != LlmFailureKind.OutOfMemory,
                        kind = classified.kind,
                    ),
                )
            } finally {
                if (cancellationRequested) closeAfterGeneration = true
                if (closeAfterGeneration) closeConversation(conversation)
                if (persistentConversation === conversation && closeAfterGeneration) {
                    persistentConversation = null
                    persistentSessionId = null
                }
                activeConversation = null
                cancellationRequested = false
            }
        }
    }.flowOn(Dispatchers.Default)

    override suspend fun stopGeneration() = withContext(Dispatchers.IO) {
        cancellationRequested = true
        Log.i(TAG, "event=cancellation_requested backend=CPU")
        runCatching { activeConversation?.cancelProcess() }
            .onFailure { Log.e(TAG, "event=cancellation_failure backend=CPU", it) }
        Unit
    }

    override suspend fun resetConversation() = withContext(Dispatchers.IO) {
        cancellationRequested = true
        runCatching { activeConversation?.cancelProcess() }
        generationMutex.withLock {
            closeConversation(persistentConversation)
            persistentConversation = null
            persistentSessionId = null
            cancellationRequested = false
            Log.i(TAG, "event=conversation_reset backend=CPU")
            Unit
        }
    }

    override suspend fun unload() = withContext(Dispatchers.IO) {
        cancellationRequested = true
        runCatching { activeConversation?.cancelProcess() }
        generationMutex.withLock { unloadInternal() }
    }

    private fun unloadInternal() {
        closeConversation(persistentConversation)
        if (activeConversation !== persistentConversation) closeConversation(activeConversation)
        activeConversation = null
        persistentConversation = null
        persistentSessionId = null
        runCatching { engine?.close() }
            .onFailure { Log.e(TAG, "event=engine_close_failure backend=CPU", it) }
        engine = null
        loadedState.value = false
        cancellationRequested = false
    }

    private fun conversationFor(currentEngine: Engine, request: LlmRequest, lastUserIndex: Int): Conversation {
        val sessionId = request.sessionId
        if (sessionId != null && persistentSessionId == sessionId) {
            persistentConversation?.let { return it }
        }
        if (sessionId != null) {
            closeConversation(persistentConversation)
            persistentConversation = null
            persistentSessionId = null
        }
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

                override fun execute(paramsJsonString: String): String =
                    error("Tiqo uses validated manual tool execution")
            })
        }
        val created = currentEngine.createConversation(
            ConversationConfig(
                systemInstruction = systemInstruction,
                initialMessages = history,
                tools = providers,
                automaticToolCalling = false,
                extraContext = if (request.availableTools.isEmpty()) emptyMap() else mapOf("enable_thinking" to false),
                maxOutputToken = request.maxOutputTokens,
            ),
        )
        if (sessionId != null) {
            persistentConversation = created
            persistentSessionId = sessionId
        }
        return created
    }

    private fun closeConversation(conversation: Conversation?) {
        runCatching { conversation?.close() }
            .onFailure { Log.e(TAG, "event=conversation_close_failure backend=CPU", it) }
    }

    private fun classify(error: Throwable, fallback: LlmFailureKind): LlmRuntimeException {
        val kind = when (error) {
            is OutOfMemoryError -> LlmFailureKind.OutOfMemory
            is UnsatisfiedLinkError, is NoSuchMethodError, is NoClassDefFoundError -> LlmFailureKind.NativeBackend
            else -> fallback
        }
        val message = when (kind) {
            LlmFailureKind.OutOfMemory -> "Za mało pamięci do uruchomienia modelu"
            LlmFailureKind.NativeBackend -> "Backend natywny LiteRT-LM nie jest zgodny z tym urządzeniem lub buildem"
            LlmFailureKind.ModelLoad -> error.message ?: "Nie udało się załadować modelu LiteRT-LM"
            LlmFailureKind.Inference -> error.message ?: "Generowanie LiteRT-LM nie powiodło się"
            LlmFailureKind.Cancelled -> "Generowanie anulowane"
        }
        return LlmRuntimeException(kind, message, error)
    }

    private fun memorySnapshot(): String {
        val runtime = Runtime.getRuntime()
        val javaUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / MEBIBYTE
        val nativeUsedMb = Debug.getNativeHeapAllocatedSize() / MEBIBYTE
        return "javaUsedMb=$javaUsedMb nativeUsedMb=$nativeUsedMb javaMaxMb=${runtime.maxMemory() / MEBIBYTE}"
    }

    private companion object {
        const val TAG = "TiqoLiteRt"
        const val MEBIBYTE = 1024L * 1024L
    }
}
