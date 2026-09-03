package com.aseprit.tiqo.feature.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aseprit.tiqo.BuildConfig
import com.aseprit.tiqo.R
import com.aseprit.tiqo.ai.model.ModelManager
import com.aseprit.tiqo.ai.model.ModelRole
import com.aseprit.tiqo.core.designsystem.component.GlassSurface
import com.aseprit.tiqo.data.settings.AccentOption
import com.aseprit.tiqo.data.settings.ThemeMode
import com.aseprit.tiqo.domain.model.ReminderOffset
import com.aseprit.tiqo.notification.AndroidReminderScheduler
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    modelManager: ModelManager,
    reminderScheduler: AndroidReminderScheduler,
    onBack: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenModels: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val models by modelManager.models.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    SettingsScreen(
        settings = settings,
        activePlanner = models.firstOrNull { ModelRole.Planner in it.activeRoles }?.catalog?.displayName,
        activeChat = models.firstOrNull { ModelRole.Chat in it.activeRoles }?.catalog?.displayName,
        notificationsGranted = notificationsGranted,
        exactAlarmsReady = reminderScheduler.canScheduleExactAlarms(),
        onBack = onBack,
        onTheme = viewModel::setTheme,
        onAccent = viewModel::setAccent,
        onAnimations = viewModel::setAnimations,
        onGlass = viewModel::setGlassIntensity,
        onFirstDay = viewModel::setFirstDay,
        onShowCompleted = viewModel::setShowCompleted,
        onDefaultTime = viewModel::setDefaultTime,
        onDefaultReminder = viewModel::setDefaultReminder,
        onRequestNotificationPermission = onRequestNotificationPermission,
        onOpenExactAlarmSettings = onOpenExactAlarmSettings,
        onOpenModels = onOpenModels,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    settings: com.aseprit.tiqo.data.settings.AppSettings,
    activePlanner: String?,
    activeChat: String?,
    notificationsGranted: Boolean,
    exactAlarmsReady: Boolean,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onAccent: (AccentOption) -> Unit,
    onAnimations: (Boolean) -> Unit,
    onGlass: (Float) -> Unit,
    onFirstDay: (DayOfWeek) -> Unit,
    onShowCompleted: (Boolean) -> Unit,
    onDefaultTime: (LocalTime) -> Unit,
    onDefaultReminder: (ReminderOffset?) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenModels: () -> Unit,
) {
    var showTimePicker by remember { mutableStateOf(false) }
    var showReminderPicker by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.close)) }
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
        }

        SettingsSection(stringResource(R.string.appearance)) {
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { onTheme(mode) },
                        label = { Text(stringResource(mode.labelRes)) },
                    )
                }
            }
            Text(stringResource(R.string.accent_color), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentOption.entries.forEach { accent ->
                    BoxAccent(accent, settings.accent == accent) { onAccent(accent) }
                }
            }
            SettingSwitch(stringResource(R.string.animations), settings.animationsEnabled, onAnimations)
            Text(stringResource(R.string.glass_intensity), style = MaterialTheme.typography.titleMedium)
            Slider(value = settings.glassIntensity, onValueChange = onGlass, valueRange = 0.45f..0.95f)
        }

        SettingsSection(stringResource(R.string.planner)) {
            Text(stringResource(R.string.first_day_of_week), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY).forEach { day ->
                    FilterChip(
                        selected = settings.firstDayOfWeek == day,
                        onClick = { onFirstDay(day) },
                        label = { Text(stringResource(if (day == DayOfWeek.MONDAY) R.string.monday else R.string.sunday)) },
                    )
                }
            }
            SettingSwitch(stringResource(R.string.show_completed), settings.showCompletedTasks, onShowCompleted)
            SettingLink(
                stringResource(R.string.default_task_time),
                settings.defaultTaskTime.format(DateTimeFormatter.ofPattern("HH:mm")),
            ) { showTimePicker = true }
            SettingLink(
                stringResource(R.string.default_reminder),
                stringResource(settings.defaultReminder.labelRes),
            ) { showReminderPicker = true }
        }

        SettingsSection(stringResource(R.string.notifications)) {
            Text(
                stringResource(R.string.notification_permission_explanation),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingLink(
                if (notificationsGranted) stringResource(R.string.notifications_allowed)
                else stringResource(R.string.notifications_blocked),
                if (notificationsGranted) "✓" else "",
            ) { if (!notificationsGranted) onRequestNotificationPermission() }
            SettingLink(
                if (exactAlarmsReady) stringResource(R.string.exact_alarm_ready)
                else stringResource(R.string.exact_alarm_unavailable),
                if (exactAlarmsReady) "✓" else stringResource(R.string.open_system_settings),
            ) { if (!exactAlarmsReady) onOpenExactAlarmSettings() }
        }

        SettingsSection(stringResource(R.string.local_ai)) {
            SettingLink("Planner AI", activePlanner ?: "Nie wybrano") { onOpenModels() }
            SettingLink("Chat AI", activeChat ?: "Nie wybrano") { onOpenModels() }
            Text("Modele i dane planera pozostają na urządzeniu.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingLink(stringResource(R.string.manage_models), "") { onOpenModels() }
        }

        SettingsSection(stringResource(R.string.data)) {
            Text(stringResource(R.string.data_local_only), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsSection(stringResource(R.string.about)) {
            Text("Tiqo ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_local), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(settings.defaultTaskTime.hour, settings.defaultTaskTime.minute)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    onDefaultTime(LocalTime.of(state.hour, state.minute))
                    showTimePicker = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showReminderPicker) {
        AlertDialog(
            onDismissRequest = { showReminderPicker = false },
            title = { Text(stringResource(R.string.default_reminder)) },
            text = {
                Column {
                    (listOf(null) + ReminderOffset.entries).forEach { option ->
                        Text(
                            stringResource(option.labelRes),
                            modifier = Modifier.fillMaxWidth().clickable {
                                onDefaultReminder(option)
                                showReminderPicker = false
                            }.padding(vertical = 12.dp),
                            color = if (option == settings.defaultReminder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
    GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingLink(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BoxAccent(accent: AccentOption, selected: Boolean, onClick: () -> Unit) {
    val color = Color(accent.argb)
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .padding(5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Spacer(Modifier.fillMaxSize().clip(CircleShape).background(color))
    }
}

private val ThemeMode.labelRes: Int get() = when (this) {
    ThemeMode.Dark -> R.string.theme_dark
    ThemeMode.Light -> R.string.theme_light
    ThemeMode.System -> R.string.theme_system
}

private val ReminderOffset?.labelRes: Int get() = when (this) {
    null -> R.string.reminder_none
    ReminderOffset.AtTime -> R.string.reminder_at_time
    ReminderOffset.FiveMinutes -> R.string.reminder_5_min
    ReminderOffset.TenMinutes -> R.string.reminder_10_min
    ReminderOffset.ThirtyMinutes -> R.string.reminder_30_min
    ReminderOffset.OneHour -> R.string.reminder_1_hour
    ReminderOffset.OneDay -> R.string.reminder_1_day
}
