package com.aseprit.tiqo.data.settings

import com.aseprit.tiqo.domain.model.ReminderOffset
import java.time.DayOfWeek
import java.time.LocalTime

enum class ThemeMode { Dark, Light, System }

enum class AccentOption(val argb: Long) {
    Purple(0xFF8B72FF),
    Blue(0xFF5D8EFF),
    Cyan(0xFF35C4D8),
    Green(0xFF4BCB88),
    Orange(0xFFFF9D54),
    Pink(0xFFFF6FAE),
    Red(0xFFFF6577),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.Dark,
    val accent: AccentOption = AccentOption.Purple,
    val animationsEnabled: Boolean = true,
    val glassIntensity: Float = 0.74f,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val showCompletedTasks: Boolean = true,
    val defaultTaskTime: LocalTime = LocalTime.of(9, 0),
    val defaultReminder: ReminderOffset? = null,
)

