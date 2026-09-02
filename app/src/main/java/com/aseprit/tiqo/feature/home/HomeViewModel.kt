package com.aseprit.tiqo.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aseprit.tiqo.data.settings.SettingsRepository
import com.aseprit.tiqo.domain.model.PlannerItem
import com.aseprit.tiqo.domain.model.PlannerOccurrence
import com.aseprit.tiqo.domain.model.ReminderOffset
import com.aseprit.tiqo.domain.repository.PlannerRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val occurrences: List<PlannerOccurrence> = emptyList(),
    val showCompleted: Boolean = true,
    val defaultTaskTime: LocalTime = LocalTime.of(9, 0),
    val defaultReminder: ReminderOffset? = null,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
)

sealed interface HomeEvent {
    data class CompletionChanged(val completed: Boolean) : HomeEvent
    data object ItemSaved : HomeEvent
    data object ItemDeleted : HomeEvent
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: PlannerRepository,
    private val settingsRepository: SettingsRepository,
    initialDate: LocalDate,
) : ViewModel() {
    private val selectedDate = MutableStateFlow(initialDate)
    private val eventChannel = Channel<HomeEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private data class CompletionUndo(
        val itemId: Long,
        val date: LocalDate,
        val previousValue: Boolean,
    )

    private var completionUndo: CompletionUndo? = null

    val uiState: StateFlow<HomeUiState> = combine(
        selectedDate,
        selectedDate.flatMapLatest(repository::observeOccurrences),
        settingsRepository.settings,
    ) { date, occurrences, settings ->
        HomeUiState(
            selectedDate = date,
            occurrences = if (settings.showCompletedTasks) {
                occurrences
            } else {
                occurrences.filterNot { it.isCompleted }
            },
            showCompleted = settings.showCompletedTasks,
            defaultTaskTime = settings.defaultTaskTime,
            defaultReminder = settings.defaultReminder,
            firstDayOfWeek = settings.firstDayOfWeek,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(selectedDate = initialDate),
    )

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun moveWeek(offset: Long) {
        selectedDate.value = selectedDate.value.plusWeeks(offset)
    }

    fun toggleCompleted(occurrence: PlannerOccurrence) {
        if (occurrence.item.type != com.aseprit.tiqo.domain.model.PlannerItemType.Task) return
        viewModelScope.launch {
            completionUndo = CompletionUndo(
                itemId = occurrence.item.id,
                date = occurrence.date,
                previousValue = occurrence.isCompleted,
            )
            repository.setCompleted(
                occurrence.item.id,
                occurrence.date,
                !occurrence.isCompleted,
            )
            eventChannel.send(HomeEvent.CompletionChanged(!occurrence.isCompleted))
        }
    }

    fun undoCompletion() {
        val undo = completionUndo ?: return
        completionUndo = null
        viewModelScope.launch {
            repository.setCompleted(undo.itemId, undo.date, undo.previousValue)
        }
    }

    fun save(item: PlannerItem) {
        viewModelScope.launch {
            repository.save(item)
            eventChannel.send(HomeEvent.ItemSaved)
        }
    }

    fun delete(itemId: Long) {
        viewModelScope.launch {
            repository.delete(itemId)
            eventChannel.send(HomeEvent.ItemDeleted)
        }
    }

    class Factory(
        private val repository: PlannerRepository,
        private val settingsRepository: SettingsRepository,
        private val initialDate: LocalDate,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(repository, settingsRepository, initialDate) as T
    }
}
