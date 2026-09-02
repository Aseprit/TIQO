package com.aseprit.tiqo.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlannerItemEntity::class,
        OccurrenceCompletionEntity::class,
        ChatConversationEntity::class,
        ChatMessageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TiqoDatabase : RoomDatabase() {
    abstract fun plannerDao(): PlannerDao
    abstract fun chatDao(): ChatDao

    companion object {
        fun create(context: Context): TiqoDatabase = Room.databaseBuilder(
            context.applicationContext,
            TiqoDatabase::class.java,
            "tiqo.db",
        ).build()
    }
}

