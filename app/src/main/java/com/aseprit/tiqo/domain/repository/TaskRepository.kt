package com.aseprit.tiqo.domain.repository

import com.aseprit.tiqo.domain.model.PlannerTask
import kotlinx.coroutines.flow.StateFlow

interface TaskRepository {
    val tasks: StateFlow<List<PlannerTask>>

    suspend fun save(task: PlannerTask)

    suspend fun delete(taskId: Long)

    suspend fun toggleCompleted(taskId: Long)
}

