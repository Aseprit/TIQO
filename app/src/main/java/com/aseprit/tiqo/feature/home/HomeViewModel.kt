package com.aseprit.tiqo.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aseprit.tiqo.data.repository.InMemoryTaskRepository
import com.aseprit.tiqo.domain.model.PlannerTask
import com.aseprit.tiqo.domain.model.RecurrenceRule
import com.aseprit.tiqo.domain.repository.TaskRepository
import java.time.LocalDate
import java.time.MonthDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val tasks: List<PlannerTask> = emptyList(),
)

class HomeViewModel(
    private val repository: TaskRepository = InMemoryTaskRepository(),
) : ViewModel() {
    private val selectedDate = MutableStateFlow(LocalDate.now())
    private var recentlyDeletedTask: PlannerTask? = null

    val uiState: StateFlow<HomeUiState> = combine(
        selectedDate,
        repository.tasks,
    ) { date, tasks ->
        HomeUiState(
            selectedDate = date,
            tasks = tasks
                .filter { it.occursOn(date) }
                .sortedWith(compareBy<PlannerTask> { it.isCompleted }.thenBy { it.time }),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun toggleCompleted(taskId: Long) {
        viewModelScope.launch { repository.toggleCompleted(taskId) }
    }

    fun saveTask(
        existing: PlannerTask?,
        title: String,
        description: String,
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return

        viewModelScope.launch {
            repository.save(
                existing?.copy(
                    title = cleanTitle,
                    description = description.trim(),
                ) ?: PlannerTask(
                    id = System.currentTimeMillis(),
                    title = cleanTitle,
                    description = description.trim(),
                    date = selectedDate.value,
                ),
            )
        }
    }

    fun delete(task: PlannerTask) {
        recentlyDeletedTask = task
        viewModelScope.launch { repository.delete(task.id) }
    }

    fun undoDelete() {
        val task = recentlyDeletedTask ?: return
        recentlyDeletedTask = null
        viewModelScope.launch { repository.save(task) }
    }
}

private fun PlannerTask.occursOn(targetDate: LocalDate): Boolean = when (val rule = recurrence) {
    RecurrenceRule.Once -> date == targetDate
    is RecurrenceRule.Weekly -> targetDate >= date && targetDate.dayOfWeek in rule.days
    is RecurrenceRule.Yearly -> targetDate >= date && MonthDay.from(targetDate) == rule.day
}

