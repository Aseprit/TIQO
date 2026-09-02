package com.aseprit.tiqo.feature.home

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aseprit.tiqo.core.designsystem.component.GlassSurface
import com.aseprit.tiqo.domain.model.PlannerTask
import com.aseprit.tiqo.domain.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

private val PolishLocale = Locale("pl", "PL")

@Composable
fun HomeRoute(viewModel: HomeViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        uiState = uiState,
        onDateSelected = viewModel::selectDate,
        onToggleCompleted = viewModel::toggleCompleted,
        onSaveTask = viewModel::saveTask,
        onDeleteTask = viewModel::delete,
        onUndoDelete = viewModel::undoDelete,
    )
}

@Composable
private fun HomeScreen(
    uiState: HomeUiState,
    onDateSelected: (LocalDate) -> Unit,
    onToggleCompleted: (Long) -> Unit,
    onSaveTask: (PlannerTask?, String, String) -> Unit,
    onDeleteTask: (PlannerTask) -> Unit,
    onUndoDelete: () -> Unit,
) {
    var detailsTask by remember { mutableStateOf<PlannerTask?>(null) }
    var editorTask by remember { mutableStateOf<PlannerTask?>(null) }
    var editorVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DateHeader(date = uiState.selectedDate)
                Spacer(modifier = Modifier.height(22.dp))
                WeekStrip(
                    selectedDate = uiState.selectedDate,
                    onDateSelected = onDateSelected,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (uiState.selectedDate == LocalDate.now()) {
                            "Dzisiaj"
                        } else {
                            uiState.selectedDate.dayOfWeek
                                .getDisplayName(TextStyle.FULL, PolishLocale)
                                .replaceFirstChar { it.titlecase(PolishLocale) }
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "${uiState.tasks.count { it.isCompleted }}/${uiState.tasks.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (uiState.tasks.isEmpty()) {
                item { EmptyDayCard() }
            } else {
                items(uiState.tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onClick = { onToggleCompleted(task.id) },
                        onLongClick = { detailsTask = task },
                    )
                }
            }
        }

        FloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            onClick = {
                editorTask = null
                editorVisible = true
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Dodaj task")
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }

    detailsTask?.let { task ->
        TaskDetailsSheet(
            task = task,
            onDismiss = { detailsTask = null },
            onEdit = {
                detailsTask = null
                editorTask = task
                editorVisible = true
            },
            onDelete = {
                detailsTask = null
                onDeleteTask(task)
                coroutineScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Task usunięty",
                        actionLabel = "Cofnij",
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) onUndoDelete()
                }
            },
        )
    }

    if (editorVisible) {
        TaskEditorDialog(
            task = editorTask,
            onDismiss = { editorVisible = false },
            onSave = { title, description ->
                onSaveTask(editorTask, title, description)
                editorVisible = false
            },
        )
    }
}

@Composable
private fun DateHeader(date: LocalDate) {
    val dayName = date.dayOfWeek
        .getDisplayName(TextStyle.FULL, PolishLocale)
        .replaceFirstChar { it.titlecase(PolishLocale) }
    val exactDate = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", PolishLocale))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column {
            Text(
                text = "TIQO",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(text = dayName, style = MaterialTheme.typography.displaySmall)
        }
        Text(
            text = exactDate,
            modifier = Modifier.padding(bottom = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeekStrip(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
) {
    val startOfWeek = selectedDate.minusDays((selectedDate.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    GlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 28.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            repeat(7) { index ->
                val date = startOfWeek.plusDays(index.toLong())
                val selected = date == selectedDate
                val containerColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    label = "week-day-color",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(containerColor)
                        .clickable { onDateSelected(date) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, PolishLocale)
                            .take(2)
                            .uppercase(PolishLocale),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = date.dayOfMonth.toString(),
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
private fun TaskCard(
    task: PlannerTask,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        cornerRadius = 22.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(25.dp)
                    .clip(CircleShape)
                    .background(
                        if (task.isCompleted) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (task.isCompleted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (task.isCompleted) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    color = if (task.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val metadata = listOfNotNull(
                    task.time?.format(DateTimeFormatter.ofPattern("HH:mm")),
                    task.recurrence.shortLabel(),
                ).joinToString("  ·  ")
                if (metadata.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDayCard() {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Spokojny dzień", style = MaterialTheme.typography.titleMedium)
            Text(
                "Nie masz tu jeszcze żadnych tasków.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDetailsSheet(
    task: PlannerTask,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(task.title, style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Zamknij")
                }
            }
            if (task.description.isNotBlank()) {
                Text(
                    task.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    listOfNotNull(
                        task.time?.format(DateTimeFormatter.ofPattern("HH:mm")),
                        task.recurrence.longLabel(),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(modifier = Modifier.weight(1f), onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edytuj")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Usuń")
                }
            }
        }
    }
}

@Composable
private fun TaskEditorDialog(
    task: PlannerTask?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember(task?.id) { mutableStateOf(task?.title.orEmpty()) }
    var description by remember(task?.id) { mutableStateOf(task?.description.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task == null) "Nowy task" else "Edytuj task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nazwa") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Opis (opcjonalny)") },
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, description) },
                enabled = title.isNotBlank(),
            ) {
                Text("Zapisz")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

private fun RecurrenceRule.shortLabel(): String? = when (this) {
    RecurrenceRule.Once -> null
    is RecurrenceRule.Weekly -> "Cykliczny"
    is RecurrenceRule.Yearly -> "Coroczny"
}

private fun RecurrenceRule.longLabel(): String = when (this) {
    RecurrenceRule.Once -> "Jednorazowy"
    is RecurrenceRule.Weekly -> days
        .sortedBy { it.value }
        .joinToString(prefix = "Co tydzień: ", separator = ", ") {
            it.getDisplayName(TextStyle.SHORT, PolishLocale)
        }
    is RecurrenceRule.Yearly -> "Co roku: ${day.dayOfMonth}.${day.monthValue}"
}
