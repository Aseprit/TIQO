package com.aseprit.tiqo.feature.models

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aseprit.tiqo.R
import com.aseprit.tiqo.ai.model.DownloadStatus
import com.aseprit.tiqo.ai.model.InstalledModelState
import com.aseprit.tiqo.ai.model.ModelCompatibility
import com.aseprit.tiqo.ai.model.ModelBackend
import com.aseprit.tiqo.ai.model.ModelCatalogTier
import com.aseprit.tiqo.ai.model.ModelFormatPolicy
import com.aseprit.tiqo.ai.model.ModelDownloadState
import com.aseprit.tiqo.ai.model.ModelManager
import com.aseprit.tiqo.ai.model.ModelOrigin
import com.aseprit.tiqo.ai.model.ModelRole
import com.aseprit.tiqo.ai.model.ModelTestState
import com.aseprit.tiqo.ai.model.ModelTestStatus
import com.aseprit.tiqo.ai.model.PlannerCompatibilityStatus
import com.aseprit.tiqo.core.designsystem.component.GlassSurface
import com.aseprit.tiqo.core.designsystem.component.TiqoButton
import com.aseprit.tiqo.core.designsystem.component.TiqoModelCard
import com.aseprit.tiqo.core.designsystem.theme.TiqoSpacing
import kotlinx.coroutines.launch

private data class ImportRequest(val name: String, val roles: Set<ModelRole>)

@Composable
fun ModelManagerScreen(manager: ModelManager, onBack: () -> Unit) {
    val models by manager.models.collectAsStateWithLifecycle()
    val downloads by manager.downloads.collectAsStateWithLifecycle()
    val tests by manager.tests.collectAsStateWithLifecycle()
    val plannerDiagnostics by manager.plannerDiagnostics.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showImport by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<ImportRequest?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val request = pendingImport
        pendingImport = null
        if (uri != null && request != null) {
            scope.launch {
                manager.importLocalModel(uri, request.name, request.roles)
                    .onSuccess { message = "Model został zaimportowany" }
                    .onFailure { message = it.message }
            }
        }
    }
    val diagnosticExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri: Uri? ->
        if (uri != null) {
            manager.exportPlannerDiagnostics(uri)
                .onSuccess { message = "Diagnostyka została wyeksportowana" }
                .onFailure { message = it.message }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(TiqoSpacing.md),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.close)) }
            Column(Modifier.weight(1f)) {
                Text("Lokalne AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("Wybierz modele, które działają na tym urządzeniu", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActiveRoleCard(ModelRole.Planner, models, Modifier.weight(1f))
            ActiveRoleCard(ModelRole.Chat, models, Modifier.weight(1f))
        }

        TiqoButton(
            text = "Importuj model",
            onClick = { showImport = true },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.FileOpen,
        )
        Text(
            "Ten build uruchamia .litertlm przez LiteRT-LM. Pliki .gguf są rozpoznawane, ale import pozostaje wyłączony, dopóki zweryfikowany runtime llama.cpp/JNI nie zostanie dołączony.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        message?.let {
            GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                Text(it, Modifier.padding(14.dp), color = if (it.startsWith("Model został")) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
            }
        }
        plannerDiagnostics?.let { report ->
            GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Diagnostyka Planner self-testu", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Po zniknięciu procesu uruchom Tiqo ponownie, wróć tutaj i wyeksportuj ten plik. Wyślij go razem z wynikiem „adb shell dumpsys activity exit-info com.aseprit.tiqo”.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        report.lineSequence().toList().takeLast(3).joinToString("\n"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { diagnosticExportLauncher.launch("tiqo-planner-diagnostics.txt") }) {
                        Text("Eksportuj diagnostykę")
                    }
                }
            }
        }

        Text("Twoje modele", style = MaterialTheme.typography.titleLarge)
        val installed = models.filter { it.installed }
        if (installed.isEmpty()) {
            Text("Nie masz jeszcze zaimportowanego modelu.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        installed.forEach { model ->
            ModelCard(
                model = model,
                download = downloads[model.catalog.id],
                test = tests[model.catalog.id],
                onActivate = { role ->
                    scope.launch { manager.setActiveModel(role, model.catalog.id).onFailure { message = it.message } }
                },
                onLoad = { role -> scope.launch { manager.loadModel(model.catalog.id, role).onFailure { message = it.message } } },
                onUnload = { scope.launch { manager.unloadModel(model.catalog.id) } },
                onTest = { scope.launch { manager.testModel(model.catalog.id).onFailure { message = it.message } } },
                onDelete = { scope.launch { manager.deleteModel(model.catalog.id).onFailure { message = it.message } } },
                onImportRecommended = {},
                onDownload = {},
                onCancelDownload = {},
            )
        }

        Text("Polecane, zgodne pakiety", style = MaterialTheme.typography.titleLarge)
        models.filterNot { it.installed }.forEach { model ->
            var recommendedImport by remember(model.catalog.id) { mutableStateOf(false) }
            val recommendedLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri != null && recommendedImport) scope.launch {
                    manager.importModel(model.catalog.id, uri)
                        .onSuccess { message = "Model został zaimportowany" }
                        .onFailure { message = it.message }
                }
                recommendedImport = false
            }
            ModelCard(
                model = model,
                download = downloads[model.catalog.id],
                test = tests[model.catalog.id],
                onActivate = {}, onLoad = {}, onUnload = {}, onTest = {}, onDelete = {},
                onImportRecommended = {
                    recommendedImport = true
                    recommendedLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                },
                onDownload = { manager.downloadModel(model.catalog.id) },
                onCancelDownload = { manager.cancelDownload(model.catalog.id) },
            )
        }
        Spacer(Modifier.height(32.dp))
    }

    if (showImport) {
        ImportModelDialog(
            onDismiss = { showImport = false },
            onContinue = { request ->
                showImport = false
                pendingImport = request
                importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
            },
        )
    }
}

@Composable
private fun ActiveRoleCard(role: ModelRole, models: List<InstalledModelState>, modifier: Modifier = Modifier) {
    val active = models.firstOrNull { role in it.activeRoles }
    GlassSurface(modifier, cornerRadius = 20.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(if (role == ModelRole.Planner) "Planner AI" else "Chat AI", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(active?.catalog?.displayName ?: "Nie wybrano", style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ready = active != null && role in active.loadedRoles
                Spacer(Modifier.size(7.dp).clip(CircleShape).background(if (ready) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline))
                Spacer(Modifier.width(6.dp))
                Text(if (ready) "Gotowy" else if (active != null) "Wybrany" else "Brak modelu", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelCard(
    model: InstalledModelState,
    download: ModelDownloadState?,
    test: ModelTestState?,
    onActivate: (ModelRole) -> Unit,
    onLoad: (ModelRole) -> Unit,
    onUnload: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
    onImportRecommended: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    var details by remember(model.catalog.id) { mutableStateOf(false) }
    var confirmDelete by remember(model.catalog.id) { mutableStateOf(false) }
    TiqoModelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(model.catalog.displayName, style = MaterialTheme.typography.titleLarge)
                    Text(model.catalog.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (model.activeRoles.isNotEmpty()) Icon(Icons.Rounded.CheckCircle, "Aktywny", tint = MaterialTheme.colorScheme.primary)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                model.catalog.roles.forEach { AssistChip(onClick = {}, label = { Text(if (it == ModelRole.Planner) "Planner AI" else "Chat AI") }) }
                if (model.catalog.origin == ModelOrigin.Recommended) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                when (model.catalog.catalogTier) {
                                    ModelCatalogTier.SmallExperimental -> "Small / experimental"
                                    ModelCatalogTier.RecommendedChatCandidate -> "Chat candidate"
                                    ModelCatalogTier.Alternative -> "Alternatywa"
                                },
                            )
                        },
                    )
                }
                if (model.installed) AssistChip(onClick = {}, label = { Text("${model.fileSizeBytes / 1_048_576} MB") })
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            when (model.compatibility) {
                                ModelCompatibility.RuntimeUnavailable -> "Runtime niedostępny"
                                ModelCompatibility.UnsupportedFormat -> "Niezgodny"
                                else -> ".${ModelFormatPolicy.detect(model.catalog.fileName).extension}"
                            },
                        )
                    },
                )
            }

            if (download?.status == DownloadStatus.Downloading || download?.status == DownloadStatus.Verifying) {
                val progress = download.progress
                if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(if (download.status == DownloadStatus.Verifying) "Sprawdzanie pliku…" else "Pobieranie ${download.downloadedBytes / 1_048_576} MB")
                OutlinedButton(onClick = onCancelDownload) { Text("Anuluj") }
            } else if (download?.status == DownloadStatus.Failed) {
                Text(
                    "${download.error.orEmpty()}${if (download.downloadedBytes > 0L) "\nZachowano ${download.downloadedBytes / 1_048_576} MB do wznowienia." else ""}",
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (download?.status == DownloadStatus.Cancelled && download.downloadedBytes > 0L) {
                Text(
                    "Wstrzymano · zachowano ${download.downloadedBytes / 1_048_576} MB. Pobieranie można wznowić.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (model.installed) {
                model.catalog.roles.forEach { role ->
                    if (role !in model.activeRoles) {
                        Button(onClick = { onActivate(role) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Ustaw jako ${if (role == ModelRole.Planner) "Planner AI" else "Chat AI"}")
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (model.loaded) OutlinedButton(onClick = onUnload) { Text("Zwolnij pamięć") }
                    model.activeRoles.firstOrNull { it !in model.loadedRoles }?.let { unloadedRole ->
                        OutlinedButton(onClick = { onLoad(unloadedRole) }) { Icon(Icons.Rounded.Memory, null); Spacer(Modifier.width(6.dp)); Text("Załaduj ${if (unloadedRole == ModelRole.Planner) "Planner" else "Chat"}") }
                    }
                    OutlinedButton(onClick = onTest, enabled = test?.status != ModelTestStatus.Running) { Icon(Icons.Rounded.Science, null); Spacer(Modifier.width(6.dp)); Text("Testuj") }
                }
                TestResult(test)
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Icon(Icons.Rounded.DeleteOutline, null); Spacer(Modifier.width(6.dp)); Text("Usuń") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (model.catalog.downloadUrl != null) {
                        Button(onClick = onDownload) {
                            Icon(Icons.Rounded.Download, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if ((download?.downloadedBytes ?: 0L) > 0L) "Wznów" else "Pobierz")
                        }
                    }
                    OutlinedButton(onClick = onImportRecommended) { Icon(Icons.Rounded.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("Importuj") }
                }
            }

            Text(
                if (details) "Ukryj szczegóły" else "Szczegóły",
                modifier = Modifier.clickable { details = !details }.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
            if (details) {
                val backend = if (model.catalog.backend == ModelBackend.LlamaCpp) "llama.cpp" else "LiteRT-LM"
                Text(
                    "Format: .${ModelFormatPolicy.detect(model.catalog.fileName).extension}\nBackend: $backend\nPlik: ${model.catalog.fileName}" +
                        model.catalog.sourceRepository?.let { "\nRepo: $it" }.orEmpty() +
                        model.catalog.sourceRevision?.let { "\nRevision: $it" }.orEmpty() +
                        model.catalog.expectedSizeBytes?.let { "\nRozmiar: $it B" }.orEmpty() +
                        model.catalog.checksumSha256?.let { "\nSHA-256: $it" }.orEmpty() +
                        model.catalog.licenseName?.let { "\nLicencja: $it" }.orEmpty() +
                        model.catalog.contextTokens?.let { "\nKontekst: $it tokenów" }.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Usunąć model?") },
            text = { Text("Plik ${model.catalog.displayName} zostanie usunięty z tego urządzenia.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Usuń", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Anuluj") } },
        )
    }
}

@Composable
private fun TestResult(test: ModelTestState?) {
    when (test?.status) {
        ModelTestStatus.Running -> Text("Uruchamiam krótki test…", color = MaterialTheme.colorScheme.primary)
        ModelTestStatus.Passed, ModelTestStatus.Partial, ModelTestStatus.Failed -> {
            val color = when (test.status) {
                ModelTestStatus.Passed -> MaterialTheme.colorScheme.tertiary
                ModelTestStatus.Partial -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.error
            }
            val compatibility = when (test.plannerCompatibility) {
                PlannerCompatibilityStatus.Compatible -> "Compatible"
                PlannerCompatibilityStatus.ExperimentalPartial -> "Experimental / partial"
                PlannerCompatibilityStatus.Failed -> "Failed planner compatibility"
                null -> if (test.status == ModelTestStatus.Passed) "Chat runtime compatible" else "Test failed"
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "$compatibility · TTFT ${test.firstTokenTimeMillis?.let { "$it ms" } ?: "brak"} · razem ${test.generationTimeMillis ?: 0} ms",
                    color = color,
                    style = MaterialTheme.typography.bodySmall,
                )
                test.diagnostics.forEach { diagnostic ->
                    Text(
                        buildString {
                            append("[${diagnostic.scenario}] parser=${diagnostic.parserPath}")
                            append(" · TTFT=${diagnostic.firstTokenTimeMillis?.let { "$it ms" } ?: "brak"}")
                            append(" · total=${diagnostic.totalTimeMillis} ms")
                            diagnostic.nativeToolCall?.let { append("\nnative tool call: $it") }
                            diagnostic.parsedTool?.let { append("\nparsed tool: $it") }
                            append("\nraw: ${diagnostic.rawOutput.ifBlank { "<empty>" }}")
                            diagnostic.error?.let { append("\nerror: $it") }
                        },
                        color = if (diagnostic.error == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        null -> Unit
    }
}

@Composable
private fun ImportModelDialog(onDismiss: () -> Unit, onContinue: (ImportRequest) -> Unit) {
    var name by remember { mutableStateOf("") }
    var planner by remember { mutableStateOf(true) }
    var chat by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importuj model") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Wybierz gotowy plik .litertlm. GGUF zostanie rozpoznany, ale ten build jasno zgłosi brak runtime llama.cpp zamiast udawać zgodność.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nazwa (opcjonalnie)") }, modifier = Modifier.fillMaxWidth())
                Text("Zastosowanie", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = planner, onClick = { planner = !planner }, label = { Text("Planner AI") })
                    FilterChip(selected = chat, onClick = { chat = !chat }, label = { Text("Chat AI") })
                }
                Text("Wybierz obie role tylko wtedy, gdy checkpoint obsługuje zarówno rozmowę, jak i schemat narzędzi Tiqo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onContinue(ImportRequest(name, buildSet { if (planner) add(ModelRole.Planner); if (chat) add(ModelRole.Chat) })) },
                enabled = planner || chat,
            ) { Text("Wybierz plik") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
