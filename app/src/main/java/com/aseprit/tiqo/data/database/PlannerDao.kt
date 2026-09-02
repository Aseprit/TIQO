package com.aseprit.tiqo.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlannerDao {
    @Query(
        """
        SELECT * FROM planner_items
        WHERE startDateEpochDay <= :endEpochDay
          AND (recurrenceType != 'NONE' OR type = 'BIRTHDAY' OR startDateEpochDay >= :startEpochDay)
        ORDER BY isAllDay DESC, timeMinutes ASC, createdAtMillis ASC
        """,
    )
    abstract fun observeCandidates(startEpochDay: Long, endEpochDay: Long): Flow<List<PlannerItemEntity>>

    @Query(
        """
        SELECT * FROM occurrence_completions
        WHERE occurrenceDateEpochDay BETWEEN :startEpochDay AND :endEpochDay
        """,
    )
    abstract fun observeCompletions(
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<OccurrenceCompletionEntity>>

    @Query("SELECT * FROM planner_items WHERE id = :itemId")
    abstract suspend fun getItem(itemId: Long): PlannerItemEntity?

    @Query("SELECT * FROM planner_items WHERE reminderMinutesBefore IS NOT NULL")
    abstract suspend fun getItemsWithReminders(): List<PlannerItemEntity>

    @Insert
    abstract suspend fun insert(item: PlannerItemEntity): Long

    @Update
    abstract suspend fun update(item: PlannerItemEntity)

    @Query("DELETE FROM planner_items WHERE id = :itemId")
    abstract suspend fun delete(itemId: Long)

    @Query(
        """
        SELECT * FROM occurrence_completions
        WHERE itemId = :itemId AND occurrenceDateEpochDay = :dateEpochDay
        """,
    )
    abstract suspend fun getCompletion(itemId: Long, dateEpochDay: Long): OccurrenceCompletionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertCompletion(completion: OccurrenceCompletionEntity)

    @Query(
        """
        DELETE FROM occurrence_completions
        WHERE itemId = :itemId AND occurrenceDateEpochDay = :dateEpochDay
        """,
    )
    abstract suspend fun deleteCompletion(itemId: Long, dateEpochDay: Long)

    @Transaction
    open suspend fun setCompleted(itemId: Long, dateEpochDay: Long, completed: Boolean) {
        if (completed) {
            insertCompletion(
                OccurrenceCompletionEntity(
                    itemId = itemId,
                    occurrenceDateEpochDay = dateEpochDay,
                    completedAtMillis = System.currentTimeMillis(),
                ),
            )
        } else {
            deleteCompletion(itemId, dateEpochDay)
        }
    }
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_conversations ORDER BY updatedAtMillis DESC")
    fun observeConversations(): Flow<List<ChatConversationEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY createdAtMillis ASC, id ASC")
    fun observeMessages(conversationId: Long): Flow<List<ChatMessageEntity>>

    @Insert
    suspend fun insertConversation(conversation: ChatConversationEntity): Long

    @Insert
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET content = :content WHERE id = :messageId")
    suspend fun updateMessageContent(messageId: Long, content: String)

    @Query("UPDATE chat_conversations SET updatedAtMillis = :updatedAtMillis WHERE id = :conversationId")
    suspend fun touchConversation(conversationId: Long, updatedAtMillis: Long)
}
