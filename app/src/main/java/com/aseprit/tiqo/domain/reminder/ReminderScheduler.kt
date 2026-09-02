package com.aseprit.tiqo.domain.reminder

import com.aseprit.tiqo.domain.model.PlannerItem

interface ReminderScheduler {
    suspend fun schedule(item: PlannerItem)

    suspend fun cancel(itemId: Long)
}

object NoOpReminderScheduler : ReminderScheduler {
    override suspend fun schedule(item: PlannerItem) = Unit
    override suspend fun cancel(itemId: Long) = Unit
}

