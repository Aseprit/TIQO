package com.aseprit.tiqo.data.repository

import com.aseprit.tiqo.data.database.ChatConversationEntity
import com.aseprit.tiqo.data.database.ChatDao
import com.aseprit.tiqo.data.database.ChatMessageEntity
import com.aseprit.tiqo.domain.model.ChatMessage
import com.aseprit.tiqo.domain.model.ChatRole
import com.aseprit.tiqo.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class RoomChatRepository(private val dao: ChatDao) : ChatRepository {
    override suspend fun getOrCreateDefaultConversation(): Long {
        val existing = dao.observeConversations().first().firstOrNull()
        if (existing != null) return existing.id
        val now = System.currentTimeMillis()
        return dao.insertConversation(
            ChatConversationEntity(
                title = "Tiqo",
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        )
    }

    override fun observeMessages(conversationId: Long): Flow<List<ChatMessage>> =
        dao.observeMessages(conversationId).map { entities ->
            entities.map { entity ->
                ChatMessage(
                    id = entity.id,
                    conversationId = entity.conversationId,
                    role = ChatRole.entries.firstOrNull { it.name.equals(entity.role, true) }
                        ?: ChatRole.Assistant,
                    content = entity.content,
                    createdAtMillis = entity.createdAtMillis,
                    toolName = entity.toolName,
                    toolPayload = entity.toolPayload,
                )
            }
        }

    override suspend fun addMessage(
        conversationId: Long,
        role: ChatRole,
        content: String,
        toolName: String?,
        toolPayload: String?,
    ): Long {
        val now = System.currentTimeMillis()
        val id = dao.insertMessage(
            ChatMessageEntity(
                conversationId = conversationId,
                role = role.name,
                content = content,
                createdAtMillis = now,
                toolName = toolName,
                toolPayload = toolPayload,
            ),
        )
        dao.touchConversation(conversationId, now)
        return id
    }

    override suspend fun updateMessage(messageId: Long, content: String) {
        dao.updateMessageContent(messageId, content)
    }
}
