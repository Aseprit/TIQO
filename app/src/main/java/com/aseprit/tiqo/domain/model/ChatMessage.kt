package com.aseprit.tiqo.domain.model

data class ChatMessage(
    val id: Long,
    val conversationId: Long,
    val role: ChatRole,
    val content: String,
    val createdAtMillis: Long,
    val toolName: String? = null,
    val toolPayload: String? = null,
)

enum class ChatRole { User, Assistant, Tool }

