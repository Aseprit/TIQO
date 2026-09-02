package com.aseprit.tiqo.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aseprit.tiqo.data.settings.SettingsRepository
import com.aseprit.tiqo.domain.model.WeeklyStatistics
import com.aseprit.tiqo.domain.repository.PlannerRepository
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StatisticsViewModel(
    repository: PlannerRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val statistics: StateFlow<WeeklyStatistics> = settingsRepository.settings.flatMapLatest { settings ->
        val weekStart = LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(settings.firstDayOfWeek))
        repository.observeWeeklyStatistics(weekStart)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        WeeklyStatistics(0, 0, emptyList()),
    )

    class Factory(
        private val repository: PlannerRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StatisticsViewModel(repository, settingsRepository) as T
    }
}
