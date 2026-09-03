package com.aseprit.tiqo.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aseprit.tiqo.R
import com.aseprit.tiqo.core.designsystem.component.GlassSurface
import com.aseprit.tiqo.domain.model.PlannerItem
import com.aseprit.tiqo.domain.model.PlannerOccurrence
import com.aseprit.tiqo.feature.editor.PlannerItemEditor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val PolishLocale = Locale("pl", "PL")

@Composable
fun CalendarRoute(
    viewModel: CalendarViewModel,
    selectedDate: LocalDate,
    onSelectedDateChange: (LocalDate) -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(selectedDate) {
        if (selectedDate != state.selectedDate) viewModel.selectDate(selectedDate)
    }
    LaunchedEffect(state.selectedDate) { onSelectedDateChange(state.selectedDate) }
    CalendarScreen(
        state = state,
        onSelectDate = viewModel::selectDate,
        onMoveMonth = viewModel::moveMonth,
        onToday = viewModel::goToday,
        onSave = viewModel::save,
        onRequestNotificationPermission = onRequestNotificationPermission,
    )
}

@Composable
private fun CalendarScreen(
    state: CalendarUiState,
    onSelectDate: (LocalDate) -> Unit,
    onMoveMonth: (Long) -> Unit,
    onToday: () -> Unit,
    onSave: (PlannerItem) -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    var editorVisible by remember { mutableStateOf(false) }
    val agenda = state.occurrences.filter { it.date == state.selectedDate }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                MonthHeader(state.month, onMoveMonth, onToday)
                Spacer(Modifier.height(14.dp))
                MonthGrid(
                    month = state.month,
                    selectedDate = state.selectedDate,
                    firstDayOfWeek = state.firstDayOfWeek,
                    datesWithItems = state.occurrences.mapTo(mutableSetOf()) { it.date },
                    onSelectDate = onSelectDate,
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    state.selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", PolishLocale))
                        .replaceFirstChar { it.titlecase(PolishLocale) },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (agenda.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.calendar_empty),
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 22.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(agenda, key = { it.stableKey }) { AgendaCard(it) }
            }
        }
        SmallFloatingActionButton(
            onClick = { editorVisible = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
            shape = CircleShape,
        ) { Icon(Icons.Rounded.Add, stringResource(R.string.add_item)) }
    }

    if (editorVisible) {
        PlannerItemEditor(
            item = null,
            initialDate = state.selectedDate,
            defaultTime = state.defaultTaskTime,
            defaultReminder = state.defaultReminder,
            onDismiss = { editorVisible = false },
            onReminderSelected = onRequestNotificationPermission,
            onSave = {
                editorVisible = false
                onSave(it)
            },
        )
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onMoveMonth: (Long) -> Unit, onToday: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                month.format(DateTimeFormatter.ofPattern("LLLL", PolishLocale))
                    .replaceFirstChar { it.titlecase(PolishLocale) },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(month.year.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onToday) { Text(stringResource(R.string.calendar_today)) }
        IconButton(onClick = { onMoveMonth(-1) }) {
            Icon(Icons.Rounded.ChevronLeft, stringResource(R.string.previous_month))
        }
        IconButton(onClick = { onMoveMonth(1) }) {
            Icon(Icons.Rounded.ChevronRight, stringResource(R.string.next_month))
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    firstDayOfWeek: DayOfWeek,
    datesWithItems: Set<LocalDate>,
    onSelectDate: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val daysFromStart = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val gridStart = first.minusDays(daysFromStart.toLong())
    val orderedWeekdays = List(7) { firstDayOfWeek.plus(it.toLong()) }
    val today = LocalDate.now()
    GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 28.dp) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth()) {
                orderedWeekdays.forEach { day ->
                    Text(
                        day.getDisplayName(TextStyle.SHORT, PolishLocale).take(2).uppercase(),
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            repeat(6) { week ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { column ->
                        val date = gridStart.plusDays((week * 7L) + column)
                        val selected = date == selectedDate
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.9f)
                                .clip(CircleShape)
                                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .then(if (date == today && !selected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                .clickable { onSelectDate(date) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                date.dayOfMonth.toString(),
                                color = when {
                                    selected -> MaterialTheme.colorScheme.onPrimary
                                    date == today -> MaterialTheme.colorScheme.primary
                                    YearMonth.from(date) != month -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (date == today || selected) FontWeight.Bold else FontWeight.Normal,
                            )
                            Box(
                                Modifier
                                    .padding(top = 3.dp)
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (date in datesWithItems) {
                                            if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                        } else Color.Transparent,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaCard(occurrence: PlannerOccurrence) {
    GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            occurrence.item.time?.let {
                Text(
                    it.format(DateTimeFormatter.ofPattern("HH:mm")),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.size(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    occurrence.item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (occurrence.item.description.isNotBlank()) {
                    Text(
                        occurrence.item.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
