package com.aseprit.tiqo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val polish = Locale.forLanguageTag("pl-PL")
private fun LocalDate.display(pattern: String) = format(DateTimeFormatter.ofPattern(pattern, polish))
private val Ink = Color(0xFF191919)
private val Paper = Color(0xFFF6F5F1)
private val Accent = Color(0xFFD84035)

class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TiqoScreen(model) }
    }
    override fun onStop() { model.background(); super.onStop() }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
        letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun TiqoScreen(vm: MainViewModel) {
    val prefs = LocalContext.current.getSharedPreferences("appearance", 0)
    var mode by rememberSaveable { mutableIntStateOf(prefs.getInt("mode", 0)) }
    val dark = mode == 2 || (mode == 0 && isSystemInDarkTheme())
    val colors = if (dark) darkColorScheme(primary = Color.White, onPrimary = Ink,
        background = Color(0xFF111111), surface = Color(0xFF111111), surfaceVariant = Color(0xFF222222),
        onSurface = Color(0xFFF1F0EC), onSurfaceVariant = Color(0xFFADADA9), secondary = Accent,
        secondaryContainer = Color(0xFF303030), onSecondaryContainer = Color.White,
        outline = Color(0xFF555555)) else lightColorScheme(primary = Ink, onPrimary = Color.White,
        background = Paper, surface = Paper, surfaceVariant = Color(0xFFEAE9E4),
        onSurface = Ink, onSurfaceVariant = Color(0xFF696965), secondary = Accent,
        secondaryContainer = Color(0xFFE5E4DE), onSecondaryContainer = Ink, outline = Color(0xFFAAA9A3))
    val view = LocalView.current
    SideEffect {
        val window = (view.context as android.app.Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = colors.copy(surfaceTint = Color.Transparent,
        surfaceContainerLowest = colors.surface, surfaceContainerLow = colors.surface,
        surfaceContainer = colors.surfaceVariant, surfaceContainerHigh = colors.surfaceVariant,
        surfaceContainerHighest = colors.surfaceVariant)) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var settings by rememberSaveable { mutableStateOf(false) }
        val error by vm.error.collectAsStateWithLifecycle()
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (tab == 2) "AI PLANNER" else "TIQO", Modifier.weight(1f),
                        fontFamily = FontFamily.Monospace, fontSize = 20.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { settings = true }) { Icon(Icons.Outlined.Settings, "Ustawienia") }
                }
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        0 -> Home(vm)
                        1 -> Calendar(vm) { tab = 0 }
                        else -> Chat(vm)
                    }
                }
                NavigationBar(containerColor = colors.surface, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0)) {
                    listOf("DZIŚ", "KALENDARZ", "AI").forEachIndexed { index, name ->
                        NavigationBarItem(selected = tab == index, onClick = {
                            tab = index
                            if (index == 0) vm.date.value = LocalDate.now()
                        }, icon = { Icon(listOf(Icons.Outlined.Today, Icons.Outlined.CalendarMonth, Icons.Outlined.AutoAwesome)[index], name) },
                            label = { Text(name, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.sp) })
                    }
                }
            }
        }
        if (settings) Settings(vm, mode, { mode = it; prefs.edit().putInt("mode", it).apply() }) { settings = false }
        error?.let { message -> AlertDialog(onDismissRequest = { vm.error.value = null }, title = { Text("Nie udało się") },
            text = { Text(message) }, confirmButton = { TextButton(onClick = { vm.error.value = null }) { Text("OK") } }) }
    }
}

@Composable
private fun Home(vm: MainViewModel) {
    val day by vm.date.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    var editor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }
    val today = LocalDate.now()
    val monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val pending = tasks.filterNot { it.completed }
    val lockedTasks = pending.filter { it.locked }
    val aiTasks = pending.filterNot { it.locked }
    val completed = tasks.filter { it.completed }
    fun add() { editing = null; editor = true }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(day.display("LLLL yyyy").uppercase(polish), Modifier.weight(1f))
                IconButton(onClick = { vm.date.value = day.minusWeeks(1) }) { Icon(Icons.Outlined.ChevronLeft, "Poprzedni tydzień") }
                IconButton(onClick = { vm.date.value = day.plusWeeks(1) }) { Icon(Icons.Outlined.ChevronRight, "Następny tydzień") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                repeat(7) { offset ->
                    val date = monday.plusDays(offset.toLong())
                    DayCell(date, date == day, date == today, Modifier.weight(1f)) { vm.date.value = date }
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(if (day == today) "DZIŚ" else day.display("dd.MM"), fontSize = 46.sp,
                fontWeight = FontWeight.Bold, letterSpacing = (-2).sp)
            Text(day.display("EEEE, d MMMM").replaceFirstChar { it.titlecase(polish) },
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("${pending.size} DO ZROBIENIA", Modifier.weight(1f))
                FilledIconButton(onClick = ::add, shape = CircleShape) { Icon(Icons.Outlined.Add, "Dodaj zadanie") }
            }
        }
        if (tasks.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 28.dp)) {
                Text("CZYSTO.", fontSize = 32.sp, fontWeight = FontWeight.Light, letterSpacing = 2.sp)
                Text("Nie masz nic zaplanowanego\nna ten dzień.", Modifier.padding(top = 10.dp, bottom = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 24.sp)
                TextButton(onClick = ::add, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("DODAJ ZADANIE")
                }
            }
        }
        if (lockedTasks.isNotEmpty()) item { Label(if (aiTasks.isNotEmpty()) "ZAPLANOWANE" else "ZADANIA", Modifier.padding(top = 20.dp, bottom = 4.dp)) }
        items(lockedTasks, key = { it.id }) { task -> TaskRow(task, { vm.complete(task, !task.completed) }, { vm.save(task.id, task.title, LocalDate.ofEpochDay(task.date).plusDays(1)) }) { editing = task; editor = true } }
        if (aiTasks.isNotEmpty()) item { Label("ZAPROPONOWANE PRZEZ AI", Modifier.padding(top = 20.dp, bottom = 4.dp)) }
        items(aiTasks, key = { it.id }) { task -> TaskRow(task, { vm.complete(task, !task.completed) }, { vm.save(task.id, task.title, LocalDate.ofEpochDay(task.date).plusDays(1)) }) { editing = task; editor = true } }
        if (completed.isNotEmpty()) item { Label("UKOŃCZONE ${completed.size}", Modifier.padding(top = 20.dp, bottom = 4.dp)) }
        items(completed, key = { it.id }) { task -> TaskRow(task, { vm.complete(task, !task.completed) }, null) { editing = task; editor = true } }
    }
    if (editor) TaskEditor(editing, day, { editor = false },
        { title, date -> vm.save(editing?.id, title, date); editor = false },
        { editing?.let(vm::delete); editor = false })
}

@Composable
private fun DayCell(date: LocalDate, selected: Boolean, today: Boolean, modifier: Modifier, click: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.clickable(onClick = click).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(listOf("PN", "WT", "ŚR", "CZ", "PT", "SO", "ND")[date.dayOfWeek.value - 1], fontFamily = FontFamily.Monospace, fontSize = 10.sp,
            color = colors.onSurfaceVariant)
        Box(Modifier.padding(top = 8.dp).size(36.dp).background(if (selected) colors.primary else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
            Text(date.display("dd"), fontFamily = FontFamily.Monospace, fontSize = 16.sp,
                color = if (selected) colors.onPrimary else colors.onSurface)
        }
        Box(Modifier.padding(top = 5.dp).size(4.dp).background(if (today) Accent else Color.Transparent, CircleShape))
    }
}

@Composable
private fun TaskRow(task: Task, toggle: () -> Unit, moveToTomorrow: (() -> Unit)?, edit: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val opacity by animateFloatAsState(if (task.completed) .48f else 1f, label = "completion")
    val fill by animateColorAsState(if (task.completed) MaterialTheme.colorScheme.primary else Color.Transparent, label = "check")
    Surface(onClick = edit, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().alpha(opacity)) {
        Row(Modifier.padding(vertical = 12.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconToggleButton(checked = task.completed, modifier = Modifier.semantics {
                contentDescription = if (task.completed) "Oznacz jako nieukończone" else "Oznacz jako ukończone"
            }, onCheckedChange = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); toggle() }) {
                Box(Modifier.size(28.dp).background(fill, CircleShape).border(1.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape), contentAlignment = Alignment.Center) {
                    if (task.completed) Icon(Icons.Outlined.Check, "Oznacz jako nieukończone", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Text(task.title, Modifier.weight(1f).padding(horizontal = 8.dp), fontSize = 17.sp, lineHeight = 23.sp,
                textDecoration = if (task.completed) TextDecoration.LineThrough else null)
            if (moveToTomorrow != null && !task.completed) {
                IconButton(onClick = moveToTomorrow, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.ChevronRight, "Przenieś na jutro", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (task.completed) {
                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditor(task: Task?, selectedDay: LocalDate, dismiss: () -> Unit, save: (String, LocalDate) -> Unit, delete: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(task?.title.orEmpty()) }
    var date by remember { mutableStateOf(task?.let { LocalDate.ofEpochDay(it.date) } ?: selectedDay) }
    var picker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val titleFocus = remember { FocusRequester() }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Label(if (task == null) "NOWE ZADANIE" else "EDYTUJ ZADANIE")
            OutlinedTextField(title, { title = it.take(300) }, Modifier.fillMaxWidth().focusRequester(titleFocus), placeholder = { Text("Co chcesz zrobić?") },
                singleLine = true, shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (title.isNotBlank()) save(title, date) }))
            Label("KIEDY · ${date.display("d MMMM")}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(date == today, { date = today }, label = { Text("DZISIAJ", fontSize = 11.sp) })
                FilterChip(date == today.plusDays(1), { date = today.plusDays(1) }, label = { Text("JUTRO", fontSize = 11.sp) })
                FilterChip(date != today && date != today.plusDays(1), { picker = true }, label = { Text("WYBIERZ", fontSize = 11.sp) })
            }
            Button(onClick = { save(title, date) }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                Text(if (task == null) "DODAJ" else "ZAPISZ")
            }
            if (task != null) TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("USUŃ ZADANIE", color = Accent) }
        }
        LaunchedEffect(Unit) { if (task == null) titleFocus.requestFocus() }
    }
    if (picker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * 86400000, initialDisplayMode = DisplayMode.Picker)
        DatePickerDialog(onDismissRequest = { picker = false }, confirmButton = { TextButton(onClick = {
            state.selectedDateMillis?.let { date = LocalDate.ofEpochDay(it / 86400000) }; picker = false
        }) { Text("WYBIERZ") } }, dismissButton = { TextButton(onClick = { picker = false }) { Text("ANULUJ") } }) {
            DatePicker(state, showModeToggle = false)
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Usunąć zadanie?") }, text = { Text(task!!.title) },
        confirmButton = { TextButton(onClick = delete) { Text("USUŃ", color = Accent) } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("ANULUJ") } })
}

@Composable
private fun Calendar(vm: MainViewModel, open: () -> Unit) {
    val day by vm.date.collectAsStateWithLifecycle()
    var month by remember { mutableStateOf(day.withDayOfMonth(1)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Label("KALENDARZ")
        Row(Modifier.padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(month.display("LLLL").replaceFirstChar { it.titlecase(polish) }, fontSize = 32.sp); Label(month.year.toString()) }
            IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Outlined.ChevronLeft, "Poprzedni miesiąc") }
            IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Outlined.ChevronRight, "Następny miesiąc") }
        }
        val offset = month.dayOfWeek.value - 1
        repeat((offset + month.lengthOfMonth() + 6) / 7) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val number = row * 7 + col - offset + 1
                    if (number in 1..month.lengthOfMonth()) {
                        val date = month.withDayOfMonth(number)
                        DayCell(date, date == day, date == LocalDate.now(), Modifier.weight(1f)) { vm.date.value = date; open() }
                    } else Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Wybierz dzień, aby zobaczyć zadania.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        TextButton(onClick = { vm.date.value = LocalDate.now(); open() }) { Text("WRÓĆ DO DZIŚ") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Settings(vm: MainViewModel, mode: Int, setMode: (Int) -> Unit, dismiss: () -> Unit) {
    val status by vm.status.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    var exportWarning by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let { vm.exportDiagnostics(it) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.load(it) } }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Ustawienia", fontSize = 28.sp)
            Label("WYGLĄD")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System", "Jasny", "Ciemny").forEachIndexed { index, text -> FilterChip(mode == index, { setMode(index) }, label = { Text(text) }) }
            }
            Label("MODEL LOKALNY")
            Text(status)
            DiagnosticProgress(vm)
            Text("Zaimportuj GGUF, aby korzystać z AI. Model pozostaje na urządzeniu.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(enabled = !busy, onClick = { picker.launch(arrayOf("*/*")) }) { Text("IMPORTUJ GGUF") }
            TextButton(enabled = !busy, onClick = { if (ready) vm.unload() else vm.load() }) { Text(if (ready) "ZWOLNIJ MODEL" else "ZAŁADUJ ZAPISANY") }
            Label("AI DIAGNOSTICS")
            TextButton(enabled = !busy, onClick = { exportWarning = true }) { Text("Eksportuj logi diagnostyczne") }
        }
    }
    if (exportWarning) AlertDialog(onDismissRequest = { exportWarning = false }, title = { Text("Eksport diagnostyki") },
        text = { Text("ZIP może zawierać treść promptów i zadań z ostatnich 3 sesji. Udostępniaj go świadomie. Model GGUF i dane uwierzytelniające aplikacji nie są dołączane.") },
        confirmButton = { TextButton(onClick = { exportWarning = false; export.launch("Tiqo-diagnostics.zip") }) { Text("EKSPORTUJ ZIP") } },
        dismissButton = { TextButton(onClick = { exportWarning = false }) { Text("ANULUJ") } })
}

@Composable
private fun DiagnosticProgress(vm: MainViewModel) {
    val state by vm.diagnostics.state.collectAsStateWithLifecycle()
    val backend by vm.diagnostics.backend.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.id, state.finished) {
        do {
            now = android.os.SystemClock.elapsedRealtime()
            if (state.finished != null) break
            kotlinx.coroutines.delay(500)
        } while (state.id.isNotEmpty())
    }
    if (state.id.isNotEmpty()) {
        if (backend.isNotBlank()) Text(backend, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val label = when (state.stage) {
            "LOADING" -> "Ładowanie modelu"
            "PREPARING" -> "Przygotowanie kontekstu"
            "TOKENIZING" -> "Tokenizacja"
            "EVALUATING" -> "Ewaluacja promptu ${state.detail}"
            "GENERATING" -> "Generowanie"
            "PARSING" -> "Sprawdzanie odpowiedzi"
            "CANCELLED" -> "CANCELLED · Anulowano"
            "ERROR" -> "Błąd · szczegóły w diagnostyce"
            "READY" -> "Model załadowany"
            else -> "Zakończono"
        }
        Text("$label · ${((state.finished ?: now) - state.started).coerceAtLeast(0) / 1000}s",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Chat(vm: MainViewModel) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val stream by vm.streaming.collectAsStateWithLifecycle()
    val proposal by vm.proposal.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val generating by vm.generating.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(messages.size, proposal, stream.length) { if (listState.layoutInfo.totalItemsCount > 0) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1) }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        DiagnosticProgress(vm)
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(if (ready) Accent else MaterialTheme.colorScheme.outline, CircleShape))
            Label(if (ready) "LOKALNIE · GOTOWY" else "ZAŁADUJ MODEL W USTAWIENIACH", Modifier.padding(start = 8.dp))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(vertical = 18.dp)) {
            if (messages.isEmpty()) item {
                Column(Modifier.padding(top = 30.dp)) {
                    Text("Mniej chaosu.\nWięcej miejsca.", fontSize = 32.sp, lineHeight = 39.sp, fontWeight = FontWeight.Light)
                    Text("Zapytaj o swój plan lub zaproponuj zmianę. Ty decydujesz, co zostanie zapisane.", Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 23.sp)
                    TextButton(onClick = { query = "Co mam jutro?" }) { Text("CO MAM JUTRO?  ↗") }
                }
            }
            items(messages) { line ->
                Column(Modifier.fillMaxWidth()) {
                    Label(if (line.role == "Ty") "TY" else "TIQO")
                    Text(line.text, Modifier.padding(top = 8.dp), fontSize = 16.sp, lineHeight = 25.sp)
                }
            }
            if (stream.isNotEmpty()) item { Column { Label("TIQO · PISZE"); Text(streamedOpinion(stream), Modifier.padding(top = 8.dp), lineHeight = 25.sp) } }
            proposal?.let { p -> item { ProposalCard(p, busy, vm::reject) { vm.approve(it) } } }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it.take(2000) }, Modifier.weight(1f), placeholder = { Text("Napisz albo podyktuj…", fontSize = 14.sp) },
                shape = RoundedCornerShape(22.dp), maxLines = 3)
            Spacer(Modifier.width(8.dp))
            FilledIconButton(enabled = generating || (!busy && query.isNotBlank()), onClick = {
                if (generating) vm.stop() else { vm.send(query); query = "" }
            }) { Icon(if (generating) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.Send, if (generating) "Zatrzymaj" else "Wyślij") }
        }
    }
}

@Composable
private fun ProposalCard(p: Proposal, busy: Boolean, reject: () -> Unit, approve: (Set<Int>) -> Unit) {
    var selected by remember(p) { mutableStateOf(p.actions.indices.toSet()) }
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Label("PROPOZYCJA")
            p.actions.forEachIndexed { index, a ->
                val old = p.snapshot.tasks.find { it.id == a.taskId }
                val title = when (a.type) {
                    ActionType.CREATE_TASK -> "Dodaj „${a.title}”"
                    ActionType.MOVE_TASK -> "Przenieś „${old?.title}”"
                    ActionType.UPDATE_TASK -> "Zmień „${old?.title}”"
                    ActionType.COMPLETE_TASK -> "Ukończ „${old?.title}”"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (p.actions.size > 1) Checkbox(index in selected, { checked -> selected = if (checked) selected + index else selected - index })
                    Column(Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.Medium, fontSize = 17.sp)
                        val detail = when (a.type) {
                            ActionType.MOVE_TASK -> "${LocalDate.ofEpochDay(old!!.date).display("EEE, d MMM")} → ${LocalDate.parse(a.date).display("EEE, d MMM")}"
                            ActionType.CREATE_TASK -> LocalDate.parse(a.date).display("EEEE, d MMMM")
                            ActionType.UPDATE_TASK -> "→ ${a.title}"
                            ActionType.COMPLETE_TASK -> "Do zrobienia → ukończone"
                        }
                        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Text(p.opinion, fontSize = 14.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = reject, enabled = !busy, modifier = Modifier.align(Alignment.End)) { Text("ODRZUĆ") }
            Button(enabled = !busy && selected.isNotEmpty(), onClick = { approve(selected) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text(if (p.actions.size == 1) "ZATWIERDŹ" else "ZATWIERDŹ ${selected.size} ZMIAN")
            }
        }
    }
}
/** Show only the advisor text while structured actions are still being generated. */
internal fun streamedOpinion(json: String): String {
    val match = Regex("\"opinion\"\\s*:\\s*\"").find(json) ?: return "Generowanie…"
    val start = match.range.last + 1
    var end = start
    while (end < json.length) {
        if (json[end] == '"') break
        if (json[end] == '\\') {
            if (end + 1 >= json.length) break
            val width = if (json[end + 1] == 'u') 6 else 2
            if (end + width > json.length) break
            end += width
        } else end++
    }
    return runCatching { com.google.gson.JsonParser.parseString("\"" + json.substring(start, end) + "\"").asString }
        .getOrDefault("Generowanie…")
}
