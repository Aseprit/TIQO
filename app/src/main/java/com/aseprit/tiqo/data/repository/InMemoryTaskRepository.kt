package com.aseprit.tiqo.data.repository

import com.aseprit.tiqo.domain.model.PlannerTask
import com.aseprit.tiqo.domain.model.RecurrenceRule
import com.aseprit.tiqo.domain.repository.TaskRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class InMemoryTaskRepository : TaskRepository {
    private val mutableTasks = MutableStateFlow(sampleTasks())
    override val tasks: StateFlow<List<PlannerTask>> = mutableTasks.asStateFlow()

    override suspend fun save(task: PlannerTask) {
        mutableTasks.update { current ->
            val exists = current.any { it.id == task.id }
            if (exists) current.map { if (it.id == task.id) task else it } else current + task
        }
    }

    override suspend fun delete(taskId: Long) {
        mutableTasks.update { tasks -> tasks.filterNot { it.id == taskId } }
    }

    override suspend fun toggleCompleted(taskId: Long) {
        mutableTasks.update { tasks ->
            tasks.map { task ->
                if (task.id == taskId) task.copy(isCompleted = !task.isCompleted) else task
            }
        }
    }

    private fun sampleTasks(): List<PlannerTask> {
        val today = LocalDate.now()
        return listOf(
            PlannerTask(
                id = 1,
                title = "Plan dnia",
                description = "Wybierz trzy najważniejsze rzeczy na dzisiaj.",
                date = today,
                time = LocalTime.of(8, 30),
                recurrence = RecurrenceRule.Weekly(DayOfWeek.entries.toSet()),
            ),
            PlannerTask(
                id = 2,
                title = "Trening",
                description = "Krótki trening całego ciała.",
                date = today,
                time = LocalTime.of(17, 0),
                recurrence = RecurrenceRule.Weekly(
                    setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY),
                ),
            ),
            PlannerTask(
                id = 3,
                title = "Odpisać na wiadomości",
                date = today,
                isCompleted = true,
            ),
        )
    }
}

