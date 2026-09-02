package com.aseprit.tiqo.feature.calendar

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
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val occurrences: List<PlannerOccurrence> = emptyList(),
    val defaultTaskTime: LocalTime = LocalTime.of(9, 0),
    val defaultReminder: ReminderOffset? = null,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val repository: PlannerRepository,
    private val settingsRepository: SettingsRepository,
    initialDate: LocalDate,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.from(initialDate))
    private val selectedDate = MutableStateFlow(initialDate)
    private val monthOccurrences = month.flatMapLatest { visibleMonth ->
        val first = visibleMonth.atDay(1)
        repository.observeOccurrences(first.minusDays(6), visibleMonth.atEndOfMonth().plusDays(6))
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        month,
        selectedDate,
        monthOccurrences,
        settingsRepository.settings,
    ) { visibleMonth, selected, occurrences, settings ->
        CalendarUiState(
            month = visibleMonth,
            selectedDate = selected,
            occurrences = occurrences,
            defaultTaskTime = settings.defaultTaskTime,
            defaultReminder = settings.defaultReminder,
            firstDayOfWeek = settings.firstDayOfWeek,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CalendarUiState(month = YearMonth.from(initialDate), selectedDate = initialDate),
    )

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        month.value = YearMonth.from(date)
    }

    fun moveMonth(offset: Long) {
        val newMonth = month.value.plusMonths(offset)
        month.value = newMonth
        selectedDate.value = newMonth.atDay(minOf(selectedDate.value.dayOfMonth, newMonth.lengthOfMonth()))
    }

    fun goToday() = selectDate(LocalDate.now())

    fun save(item: PlannerItem) {
        viewModelScope.launch { repository.save(item) }
    }

    class Factory(
        private val repository: PlannerRepository,
        private val settingsRepository: SettingsRepository,
        private val initialDate: LocalDate,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CalendarViewModel(repository, settingsRepository, initialDate) as T
    }
}
