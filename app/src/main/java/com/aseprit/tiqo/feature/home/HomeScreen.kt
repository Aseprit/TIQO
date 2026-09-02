package com.aseprit.tiqo.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aseprit.tiqo.R
import com.aseprit.tiqo.core.designsystem.component.GlassSurface
import com.aseprit.tiqo.core.designsystem.theme.tiqoAnimationsEnabled
import com.aseprit.tiqo.domain.model.PlannerItem
import com.aseprit.tiqo.domain.model.PlannerItemType
import com.aseprit.tiqo.domain.model.PlannerOccurrence
import com.aseprit.tiqo.domain.model.RecurrenceRule
import com.aseprit.tiqo.feature.editor.PlannerItemEditor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val PolishLocale = Locale("pl", "PL")

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    selectedDate: LocalDate,
    onSelectedDateChange: (LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStatistics: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val completedMessage = stringResource(R.string.task_completed)
    val reopenedMessage = stringResource(R.string.task_reopened)
    val undoLabel = stringResource(R.string.undo)
    val savedMessage = stringResource(R.string.item_saved)
    val deletedMessage = stringResource(R.string.item_deleted)

    LaunchedEffect(selectedDate) {
        if (selectedDate != uiState.selectedDate) viewModel.selectDate(selectedDate)
    }

    LaunchedEffect(uiState.selectedDate) {
        onSelectedDateChange(uiState.selectedDate)
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.CompletionChanged -> {
                    val result = snackbar.showSnackbar(
                        message = if (event.completed) completedMessage else reopenedMessage,
                        actionLabel = undoLabel,
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoCompletion()
                }
                HomeEvent.ItemDeleted -> snackbar.showSnackbar(deletedMessage)
                HomeEvent.ItemSaved -> snackbar.showSnackbar(savedMessage)
            }
        }
    }

    HomeScreen(
        uiState = uiState,
        snackbar = snackbar,
        onDateSelected = viewModel::selectDate,
        onMoveWeek = viewModel::moveWeek,
        onToggleCompleted = viewModel::toggleCompleted,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onOpenSettings = onOpenSettings,
        onOpenStatistics = onOpenStatistics,
        onRequestNotificationPermission = onRequestNotificationPermission,
    )
}

@Composable
private fun HomeScreen(
    uiState: HomeUiState,
    snackbar: SnackbarHostState,
    onDateSelected: (LocalDate) -> Unit,
    onMoveWeek: (Long) -> Unit,
    onToggleCompleted: (PlannerOccurrence) -> Unit,
    onSave: (PlannerItem) -> Unit,
    onDelete: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStatistics: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    var details by remember { mutableStateOf<PlannerOccurrence?>(null) }
    var editorItem by remember { mutableStateOf<PlannerItem?>(null) }
    var editorVisible by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PlannerItem?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DateHeader(
                    date = uiState.selectedDate,
                    onOpenSettings = onOpenSettings,
                    onOpenStatistics = onOpenStatistics,
                )
                Spacer(Modifier.height(18.dp))
                WeekNavigation(onPrevious = { onMoveWeek(-1) }, onNext = { onMoveWeek(1) })
                Spacer(Modifier.height(8.dp))
                WeekStrip(uiState.selectedDate, uiState.firstDayOfWeek, onDateSelected)
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (uiState.selectedDate == LocalDate.now()) {
                            stringResource(R.string.today)
                        } else {
                            uiState.selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, PolishLocale)
                                .replaceFirstChar { it.titlecase(PolishLocale) }
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    val tasks = uiState.occurrences.filter { it.item.type == PlannerItemType.Task }
                    Text(
                        stringResource(R.string.tasks_count, tasks.count { it.isCompleted }, tasks.size),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (uiState.occurrences.isEmpty()) {
                item {
                    EmptyDayCard(onAdd = {
                        editorItem = null
                        editorVisible = true
                    })
                }
            } else {
                items(uiState.occurrences, key = { it.stableKey }) { occurrence ->
                    PlannerOccurrenceCard(
                        occurrence = occurrence,
                        onClick = { onToggleCompleted(occurrence) },
                        onLongClick = { details = occurrence },
                    )
                }
            }
        }

        FloatingActionButton(
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            onClick = {
                editorItem = null
                editorVisible = true
            },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
        ) { Icon(Icons.Rounded.Add, stringResource(R.string.add_item)) }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
        )
    }

    details?.let { occurrence ->
        OccurrenceDetails(
            occurrence = occurrence,
            onDismiss = { details = null },
            onEdit = {
                details = null
                editorItem = occurrence.item
                editorVisible = true
            },
            onDelete = {
                details = null
                deleteTarget = occurrence.item
            },
        )
    }

    if (editorVisible) {
        PlannerItemEditor(
            item = editorItem,
            initialDate = uiState.selectedDate,
            defaultTime = uiState.defaultTaskTime,
            defaultReminder = uiState.defaultReminder,
            onDismiss = { editorVisible = false },
            onReminderSelected = onRequestNotificationPermission,
            onSave = {
                editorVisible = false
                onSave(it)
            },
        )
    }

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    onDelete(item.id)
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun DateHeader(
    date: LocalDate,
    onOpenSettings: () -> Unit,
    onOpenStatistics: () -> Unit,
) {
    val day = date.dayOfWeek.getDisplayName(TextStyle.FULL, PolishLocale)
        .replaceFirstChar { it.titlecase(PolishLocale) }
    val formatted = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", PolishLocale))
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TIQO", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenStatistics) {
                Icon(Icons.Rounded.BarChart, stringResource(R.string.statistics))
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, stringResource(R.string.settings))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(day, style = MaterialTheme.typography.displaySmall)
            Text(
                formatted,
                modifier = Modifier.padding(bottom = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekNavigation(onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Rounded.ChevronLeft, stringResource(R.string.previous_week))
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Rounded.ChevronRight, stringResource(R.string.next_week))
        }
    }
}

@Composable
private fun WeekStrip(
    selectedDate: LocalDate,
    firstDayOfWeek: DayOfWeek,
    onDateSelected: (LocalDate) -> Unit,
) {
    val animationSpec = if (MaterialTheme.tiqoAnimationsEnabled) tween<Color>(200) else snap()
    val daysFromStart = (selectedDate.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val start = selectedDate.minusDays(daysFromStart.toLong())
    val today = LocalDate.now()
    GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 28.dp) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            repeat(7) { index ->
                val date = start.plusDays(index.toLong())
                val selected = date == selectedDate
                val container by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    animationSpec = animationSpec,
                    label = "selected-day",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(container)
                        .then(
                            if (date == today && !selected) {
                                Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                            } else Modifier
                        )
                        .clickable { onDateSelected(date) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.SHORT, PolishLocale).take(2).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlannerOccurrenceCard(
    occurrence: PlannerOccurrence,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val isTask = occurrence.item.type == PlannerItemType.Task
    val opacity by animateFloatAsState(
        targetValue = if (occurrence.isCompleted) 0.58f else 1f,
        animationSpec = if (MaterialTheme.tiqoAnimationsEnabled) tween(180) else snap(),
        label = "completion",
    )
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(opacity)
            .combinedClickable(
                onClick = {
                    if (isTask) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    } else {
                        onLongClick()
                    }
                },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
        cornerRadius = 22.dp,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            when (occurrence.item.type) {
                PlannerItemType.Task -> CompletionCircle(occurrence.isCompleted)
                PlannerItemType.Event -> Icon(Icons.Rounded.Event, null, tint = MaterialTheme.colorScheme.primary)
                PlannerItemType.Birthday -> Icon(Icons.Rounded.Cake, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    occurrence.item.time?.let {
                        Text(
                            it.format(DateTimeFormatter.ofPattern("HH:mm")),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        occurrence.item.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (occurrence.isCompleted) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (occurrence.item.description.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        occurrence.item.description,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (occurrence.item.reminder != null) {
                Icon(Icons.Rounded.Notifications, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (occurrence.item.recurrence != RecurrenceRule.None) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.Repeat, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CompletionCircle(completed: Boolean) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (completed) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(1.5.dp, if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (completed) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun EmptyDayCard(onAdd: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.nothing_planned), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.nothing_planned_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onAdd) { Text(stringResource(R.string.add_item)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OccurrenceDetails(
    occurrence: PlannerOccurrence,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val item = occurrence.item
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) }
            }
            if (item.description.isNotBlank()) {
                Text(item.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                occurrence.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", PolishLocale)) +
                    (item.time?.let { " · ${it.format(DateTimeFormatter.ofPattern("HH:mm"))}" } ?: ""),
            )
            if (item.reminder != null) {
                DetailRow(Icons.Rounded.Notifications, stringResource(R.string.reminder))
            }
            if (item.recurrence != RecurrenceRule.None) {
                DetailRow(Icons.Rounded.Repeat, stringResource(R.string.recurring))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(modifier = Modifier.weight(1f), onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.edit))
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.delete))
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
