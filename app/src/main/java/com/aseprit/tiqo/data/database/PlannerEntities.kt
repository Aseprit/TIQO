package com.aseprit.tiqo.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "planner_items",
    indices = [Index("startDateEpochDay"), Index("reminderMinutesBefore")],
)
data class PlannerItemEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val type: String,
    val startDateEpochDay: Long,
    val timeMinutes: Int?,
    val isAllDay: Boolean,
    val reminderMinutesBefore: Int?,
    val recurrenceType: String,
    val recurrenceInterval: Int,
    val recurrenceWeekdaysMask: Int,
    val recurrenceEndEpochDay: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "occurrence_completions",
    primaryKeys = ["itemId", "occurrenceDateEpochDay"],
    foreignKeys = [
        ForeignKey(
            entity = PlannerItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId"), Index("occurrenceDateEpochDay")],
)
data class OccurrenceCompletionEntity(
    val itemId: Long,
    val occurrenceDateEpochDay: Long,
    val completedAtMillis: Long,
)

@Entity(tableName = "chat_conversations")
data class ChatConversationEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId"), Index("createdAtMillis")],
)
data class ChatMessageEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val createdAtMillis: Long,
    val toolName: String? = null,
    val toolPayload: String? = null,
)

