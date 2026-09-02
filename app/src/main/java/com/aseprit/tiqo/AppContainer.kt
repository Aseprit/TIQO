package com.aseprit.tiqo

import android.content.Context
import com.aseprit.tiqo.data.database.TiqoDatabase
import com.aseprit.tiqo.data.repository.RoomPlannerRepository
import com.aseprit.tiqo.data.repository.RoomChatRepository
import com.aseprit.tiqo.data.settings.SettingsRepository
import com.aseprit.tiqo.notification.AndroidReminderScheduler
import com.aseprit.tiqo.ai.LiteRtLlmEngine
import com.aseprit.tiqo.ai.model.ModelManager

class AppContainer(context: Context) {
    val settingsRepository = SettingsRepository(context)
    val database = TiqoDatabase.create(context)
    val reminderScheduler = AndroidReminderScheduler(
        context = context,
        defaultAllDayTime = { settingsRepository.current().defaultTaskTime },
    )
    val plannerRepository = RoomPlannerRepository(
        dao = database.plannerDao(),
        reminderScheduler = reminderScheduler,
    )
    val chatRepository = RoomChatRepository(database.chatDao())
    val llmEngine = LiteRtLlmEngine(context)
    val modelManager = ModelManager(context, llmEngine)
}
