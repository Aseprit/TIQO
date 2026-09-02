package com.aseprit.tiqo.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aseprit.tiqo.domain.model.ReminderOffset
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.tiqoDataStore by preferencesDataStore(name = "tiqo_settings")

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.tiqoDataStore.data.map { preferences ->
        AppSettings(
            themeMode = enumValueOrDefault(preferences[Keys.Theme], ThemeMode.Dark),
            accent = enumValueOrDefault(preferences[Keys.Accent], AccentOption.Purple),
            animationsEnabled = preferences[Keys.Animations] ?: true,
            glassIntensity = (preferences[Keys.GlassIntensity] ?: 0.74f).coerceIn(0.45f, 0.95f),
            firstDayOfWeek = enumValueOrDefault(preferences[Keys.FirstDay], DayOfWeek.MONDAY),
            showCompletedTasks = preferences[Keys.ShowCompleted] ?: true,
            defaultTaskTime = minutesToTime(preferences[Keys.DefaultTime] ?: 9 * 60),
            defaultReminder = preferences[Keys.DefaultReminder]
                ?.let { name -> ReminderOffset.entries.firstOrNull { it.name == name } },
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(value: ThemeMode) = update(Keys.Theme, value.name)
    suspend fun setAccent(value: AccentOption) = update(Keys.Accent, value.name)
    suspend fun setAnimationsEnabled(value: Boolean) = update(Keys.Animations, value)
    suspend fun setGlassIntensity(value: Float) = update(Keys.GlassIntensity, value.coerceIn(0.45f, 0.95f))
    suspend fun setFirstDayOfWeek(value: DayOfWeek) = update(Keys.FirstDay, value.name)
    suspend fun setShowCompletedTasks(value: Boolean) = update(Keys.ShowCompleted, value)
    suspend fun setDefaultTaskTime(value: LocalTime) = update(Keys.DefaultTime, value.hour * 60 + value.minute)

    suspend fun setDefaultReminder(value: ReminderOffset?) {
        context.tiqoDataStore.edit { preferences ->
            if (value == null) preferences.remove(Keys.DefaultReminder)
            else preferences[Keys.DefaultReminder] = value.name
        }
    }

    private suspend fun <T> update(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        context.tiqoDataStore.edit { it[key] = value }
    }

    private object Keys {
        val Theme = stringPreferencesKey("theme")
        val Accent = stringPreferencesKey("accent")
        val Animations = booleanPreferencesKey("animations")
        val GlassIntensity = floatPreferencesKey("glass_intensity")
        val FirstDay = stringPreferencesKey("first_day")
        val ShowCompleted = booleanPreferencesKey("show_completed")
        val DefaultTime = intPreferencesKey("default_time")
        val DefaultReminder = stringPreferencesKey("default_reminder")
    }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
    value?.let { candidate -> enumValues<T>().firstOrNull { it.name == candidate } } ?: default

private fun minutesToTime(minutes: Int): LocalTime {
    val normalized = minutes.coerceIn(0, 23 * 60 + 59)
    return LocalTime.of(normalized / 60, normalized % 60)
}

