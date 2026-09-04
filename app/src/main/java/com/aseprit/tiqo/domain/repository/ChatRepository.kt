package com.aseprit.tiqo.domain.repository

import com.aseprit.tiqo.domain.model.ChatMessage
import com.aseprit.tiqo.domain.model.ChatRole
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    suspend fun getOrCreateDefaultConversation(): Long
    fun observeMessages(conversationId: Long): Flow<List<ChatMessage>>
    suspend fun addMessage(
        conversationId: Long,
        role: ChatRole,
        content: String,
        toolName: String? = null,
        toolPayload: String? = null,
    ): Long
    suspend fun updateMessage(messageId: Long, content: String)
    suspend fun clearConversation(conversationId: Long)
}
