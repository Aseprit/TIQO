package com.aseprit.tiqo.feature.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aseprit.tiqo.R
import com.aseprit.tiqo.core.designsystem.component.GlassSurface
import com.aseprit.tiqo.domain.model.PlannerItem
import com.aseprit.tiqo.domain.model.PlannerItemType
import com.aseprit.tiqo.domain.model.RecurrenceRule
import com.aseprit.tiqo.domain.model.ReminderOffset
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class RepeatChoice { None, Daily, Weekly, SelectedDays, Monthly, Yearly }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlannerItemEditor(
    item: PlannerItem?,
    initialDate: LocalDate,
    defaultTime: LocalTime,
    defaultReminder: ReminderOffset?,
    onDismiss: () -> Unit,
    onReminderSelected: () -> Unit,
    onSave: (PlannerItem) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember(item?.id) { mutableStateOf(item?.title.orEmpty()) }
    var description by remember(item?.id) { mutableStateOf(item?.description.orEmpty()) }
    var type by remember(item?.id) { mutableStateOf(item?.type ?: PlannerItemType.Task) }
    var date by remember(item?.id) { mutableStateOf(item?.startDate ?: initialDate) }
    var allDay by remember(item?.id) { mutableStateOf(item?.isAllDay ?: true) }
    var time by remember(item?.id) { mutableStateOf(item?.time ?: defaultTime) }
    var reminder by remember(item?.id) { mutableStateOf(item?.reminder ?: defaultReminder) }
    var repeatChoice by remember(item?.id) {
        mutableStateOf(item?.recurrence?.toChoice() ?: RepeatChoice.None)
    }
    var selectedDays by remember(item?.id) {
        mutableStateOf(
            (item?.recurrence as? RecurrenceRule.Weekly)?.days ?: setOf(initialDate.dayOfWeek),
        )
    }
    var advancedExpanded by remember { mutableStateOf(item != null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showReminderPicker by remember { mutableStateOf(false) }
    var showRepeatPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(if (item == null) R.string.new_item else R.string.edit_item),
                style = MaterialTheme.typography.titleLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlannerItemType.entries.forEach { option ->
                    FilterChip(
                        selected = type == option,
                        onClick = {
                            type = option
                            if (option == PlannerItemType.Birthday) {
                                repeatChoice = RepeatChoice.Yearly
                                allDay = true
                            }
                        },
                        label = { Text(stringResource(option.labelRes)) },
                    )
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.title)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.description_optional)) },
                minLines = 2,
                maxLines = 5,
            )

            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { advancedExpanded = !advancedExpanded },
                cornerRadius = 20.dp,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.advanced_options),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                }
            }

            if (advancedExpanded) {
                EditorActionRow(
                    icon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                    title = stringResource(R.string.date),
                    value = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", PolishLocale)),
                    onClick = { showDatePicker = true },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.all_day), style = MaterialTheme.typography.titleMedium)
                        if (!allDay) {
                            Text(
                                time.format(DateTimeFormatter.ofPattern("HH:mm")),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                }
                if (!allDay) {
                    EditorActionRow(
                        icon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
                        title = stringResource(R.string.time),
                        value = time.format(DateTimeFormatter.ofPattern("HH:mm")),
                        onClick = { showTimePicker = true },
                    )
                }
                EditorActionRow(
                    icon = { Icon(Icons.Rounded.Notifications, contentDescription = null) },
                    title = stringResource(R.string.reminder),
                    value = stringResource(reminder.labelRes),
                    onClick = { showReminderPicker = true },
                )
                EditorActionRow(
                    icon = { Icon(Icons.Rounded.Repeat, contentDescription = null) },
                    title = stringResource(R.string.repeat),
                    value = stringResource(repeatChoice.labelRes),
                    onClick = { showRepeatPicker = true },
                )
                if (repeatChoice == RepeatChoice.SelectedDays) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = day in selectedDays,
                                onClick = {
                                    selectedDays = if (day in selectedDays) {
                                        selectedDays - day
                                    } else {
                                        selectedDays + day
                                    }
                                },
                                label = {
                                    Text(day.getDisplayName(TextStyle.SHORT, PolishLocale).uppercase())
                                },
                            )
                        }
                    }
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank() &&
                    (repeatChoice != RepeatChoice.SelectedDays || selectedDays.isNotEmpty()),
                onClick = {
                    val effectiveRecurrence = when (repeatChoice) {
                        RepeatChoice.None -> RecurrenceRule.None
                        RepeatChoice.Daily -> RecurrenceRule.Daily()
                        RepeatChoice.Weekly -> RecurrenceRule.Weekly(setOf(date.dayOfWeek))
                        RepeatChoice.SelectedDays -> RecurrenceRule.Weekly(selectedDays)
                        RepeatChoice.Monthly -> RecurrenceRule.Monthly()
                        RepeatChoice.Yearly -> RecurrenceRule.Yearly()
                    }
                    val effectiveReminder = reminder
                    if (effectiveReminder != null) onReminderSelected()
                    onSave(
                        PlannerItem(
                            id = item?.id ?: 0,
                            title = title.trim(),
                            description = description.trim(),
                            type = type,
                            startDate = date,
                            time = time.takeUnless { allDay },
                            isAllDay = allDay,
                            reminder = effectiveReminder,
                            recurrence = if (type == PlannerItemType.Birthday) {
                                RecurrenceRule.Yearly()
                            } else {
                                effectiveRecurrence
                            },
                            createdAtMillis = item?.createdAtMillis ?: System.currentTimeMillis(),
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) { DatePicker(state = state) }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(state.hour, state.minute)
                    showTimePicker = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showReminderPicker) {
        SelectionDialog(
            title = stringResource(R.string.reminder),
            options = listOf(null) + ReminderOffset.entries,
            selected = reminder,
            label = { stringResource(it.labelRes) },
            onSelected = {
                reminder = it
                showReminderPicker = false
            },
            onDismiss = { showReminderPicker = false },
        )
    }

    if (showRepeatPicker) {
        SelectionDialog(
            title = stringResource(R.string.repeat),
            options = RepeatChoice.entries,
            selected = repeatChoice,
            label = { stringResource(it.labelRes) },
            onSelected = {
                repeatChoice = it
                showRepeatPicker = false
            },
            onDismiss = { showRepeatPicker = false },
        )
    }
}

@Composable
private fun EditorActionRow(
    icon: @Composable () -> Unit,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        icon()
        Spacer(Modifier.width(10.dp))
        Text(title, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun <T> SelectionDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(option) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label(option),
                            color = if (option == selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun RecurrenceRule.toChoice(): RepeatChoice = when (this) {
    RecurrenceRule.None -> RepeatChoice.None
    is RecurrenceRule.Daily -> RepeatChoice.Daily
    is RecurrenceRule.Weekly -> if (days.size == 1) RepeatChoice.Weekly else RepeatChoice.SelectedDays
    is RecurrenceRule.Monthly -> RepeatChoice.Monthly
    is RecurrenceRule.Yearly -> RepeatChoice.Yearly
}

private val PlannerItemType.labelRes: Int
    get() = when (this) {
        PlannerItemType.Task -> R.string.type_task
        PlannerItemType.Event -> R.string.type_event
        PlannerItemType.Birthday -> R.string.type_birthday
    }

private val ReminderOffset?.labelRes: Int
    get() = when (this) {
        null -> R.string.reminder_none
        ReminderOffset.AtTime -> R.string.reminder_at_time
        ReminderOffset.FiveMinutes -> R.string.reminder_5_min
        ReminderOffset.TenMinutes -> R.string.reminder_10_min
        ReminderOffset.ThirtyMinutes -> R.string.reminder_30_min
        ReminderOffset.OneHour -> R.string.reminder_1_hour
        ReminderOffset.OneDay -> R.string.reminder_1_day
    }

private val RepeatChoice.labelRes: Int
    get() = when (this) {
        RepeatChoice.None -> R.string.repeat_never
        RepeatChoice.Daily -> R.string.repeat_daily
        RepeatChoice.Weekly -> R.string.repeat_weekly
        RepeatChoice.SelectedDays -> R.string.repeat_selected_days
        RepeatChoice.Monthly -> R.string.repeat_monthly
        RepeatChoice.Yearly -> R.string.repeat_yearly
    }

private val PolishLocale = Locale("pl", "PL")
