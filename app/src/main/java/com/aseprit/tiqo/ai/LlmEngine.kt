package com.aseprit.tiqo.ai

/**
 * Boundary between Tiqo and a local language-model runtime.
 *
 * The first production implementation is intended to use LiteRT-LM. UI and planner
 * features must depend on this contract rather than on a concrete model runtime.
 */
interface LlmEngine {
    val descriptor: LlmDescriptor

    val isLoaded: kotlinx.coroutines.flow.StateFlow<Boolean>

    suspend fun load(modelPath: String): Result<Unit>

    fun generate(request: LlmRequest): kotlinx.coroutines.flow.Flow<LlmEvent>

    suspend fun stopGeneration()

    suspend fun unload()
}

data class LlmDescriptor(
    val id: String,
    val displayName: String,
    val capabilities: Set<LlmCapability>,
)

enum class LlmCapability {
    PlannerToolCalls,
    Chat,
}

data class LlmRequest(
    val messages: List<LlmMessage>,
    val availableTools: List<LlmTool> = emptyList(),
)

data class LlmMessage(
    val role: LlmRole,
    val content: String,
)

enum class LlmRole { System, User, Assistant }

data class LlmTool(
    val name: String,
    val description: String,
    val inputSchema: String,
)

sealed interface LlmEvent {
    data class Token(val content: String) : LlmEvent
    data class ToolCall(val name: String, val argumentsJson: String) : LlmEvent
    data class Failure(val reason: String, val recoverable: Boolean) : LlmEvent
    data object Finished : LlmEvent
}

class UnavailableLlmEngine : LlmEngine {
    override val descriptor = LlmDescriptor(
        id = "unavailable",
        displayName = "Brak modelu",
        capabilities = emptySet(),
    )
    private val loaded = kotlinx.coroutines.flow.MutableStateFlow(false)
    override val isLoaded: kotlinx.coroutines.flow.StateFlow<Boolean> = loaded

    override suspend fun load(modelPath: String): Result<Unit> =
        Result.failure(IllegalStateException("Local model is not installed"))

    override fun generate(request: LlmRequest): kotlinx.coroutines.flow.Flow<LlmEvent> =
        kotlinx.coroutines.flow.flowOf(
            LlmEvent.Failure("Local model is not installed", recoverable = true),
        )

    override suspend fun stopGeneration() = Unit
    override suspend fun unload() = Unit
}
