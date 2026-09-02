package com.aseprit.tiqo.ai

/**
 * Boundary between Tiqo and a local language-model runtime.
 *
 * The first production implementation is intended to use LiteRT-LM. UI and planner
 * features must depend on this contract rather than on a concrete model runtime.
 */
interface LlmEngine {
    val descriptor: LlmDescriptor

    suspend fun isAvailable(): Boolean

    suspend fun complete(request: LlmRequest): LlmResponse

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

sealed interface LlmResponse {
    data class Text(val content: String) : LlmResponse

    data class ToolCall(val name: String, val argumentsJson: String) : LlmResponse

    data class Failure(val reason: String, val recoverable: Boolean) : LlmResponse
}

