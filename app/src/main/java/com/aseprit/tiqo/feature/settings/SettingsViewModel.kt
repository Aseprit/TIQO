package com.aseprit.tiqo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aseprit.tiqo.data.settings.AccentOption
import com.aseprit.tiqo.data.settings.AppSettings
import com.aseprit.tiqo.data.settings.SettingsRepository
import com.aseprit.tiqo.data.settings.ThemeMode
import com.aseprit.tiqo.domain.model.ReminderOffset
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    fun setTheme(value: ThemeMode) = update { repository.setThemeMode(value) }
    fun setAccent(value: AccentOption) = update { repository.setAccent(value) }
    fun setAnimations(value: Boolean) = update { repository.setAnimationsEnabled(value) }
    fun setGlassIntensity(value: Float) = update { repository.setGlassIntensity(value) }
    fun setFirstDay(value: DayOfWeek) = update { repository.setFirstDayOfWeek(value) }
    fun setShowCompleted(value: Boolean) = update { repository.setShowCompletedTasks(value) }
    fun setDefaultTime(value: LocalTime) = update { repository.setDefaultTaskTime(value) }
    fun setDefaultReminder(value: ReminderOffset?) = update { repository.setDefaultReminder(value) }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    class Factory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(repository) as T
    }
}

