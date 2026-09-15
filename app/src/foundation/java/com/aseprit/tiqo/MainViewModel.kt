package com.aseprit.tiqo

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate

data class ChatLine(val role: String, val text: String)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = TaskDatabase.open(application)
    private val repository = TaskRepository(database)
    private val builder = ContextBuilder(repository.dao)
    private val readFastPath = PlannerReadFastPath(builder)
    private val executor = ProposalExecutor(repository)
    val diagnostics = AiDiagnostics(application)
    private val engine = LlamaCppEngine(diagnostics)
    private val store = ModelStore(application)
    val date = MutableStateFlow(LocalDate.now())
    @OptIn(ExperimentalCoroutinesApi::class)
    val tasks = date.flatMapLatest { repository.dao.observeDay(it.toEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val messages = MutableStateFlow<List<ChatLine>>(emptyList())
    val streaming = MutableStateFlow("")
    val proposal = MutableStateFlow<Proposal?>(null)
    val busy = MutableStateFlow(false)
    val generating = MutableStateFlow(false)
    val ready = MutableStateFlow(false)
    val status = MutableStateFlow(if (store.model.exists()) "Zapisany GGUF: naciśnij Załaduj" else "Zaimportuj model GGUF")
    val error = MutableStateFlow<String?>(null)
    private var generation: Job? = null

    fun save(id: Long?, title: String, day: LocalDate) = action { repository.saveManual(id, title, day) }
    fun delete(task: Task) = action { repository.dao.delete(task.id) }
    fun complete(task: Task, value: Boolean) = action { repository.dao.complete(task.id, value) }
    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error.value = e.message ?: "Błąd operacji" }
    }

    fun load(uri: Uri? = null) {
        if (busy.value) return
        busy.value = true
        ready.value = false
        diagnostics.begin("load")
        status.value = if (uri != null) "Importowanie GGUF…" else "Ładowanie GGUF…"
        action {
            try {
                engine.unload()
                val file = if (uri == null) store.model else store.import(uri)
                status.value = "Ładowanie ${file.length() / 1024 / 1024} MB…"
                engine.load(file.absolutePath)
                diagnostics.stage("READY")
                ready.value = true
                status.value = "GGUF gotowy · ${diagnostics.backend.value} · kontekst 4096"
            } catch (e: Exception) {
                diagnostics.stage(if (e is CancellationException) "CANCELLED" else "ERROR", e.toString())
                throw e
            } finally {
                busy.value = false
                if (!ready.value) status.value = "Model nie został załadowany"
            }
        }
    }

    fun send(query: String) {
        if (busy.value || query.isBlank()) return
        require(query.length <= 2000)
        val pureRead = readFastPath.accepts(query)
        if (!pureRead && !ready.value) {
            proposal.value = null
            messages.value += ChatLine("Ty", query)
            messages.value += ChatLine("Tiqo", "Załaduj model AI, aby uzyskać analizę lub zmienić plan.")
            return
        }
        busy.value = true
        generating.value = !pureRead
        proposal.value = null
        streaming.value = ""
        messages.value += ChatLine("Ty", query)
        diagnostics.begin("request")
        generation = viewModelScope.launch {
            try {
                if (pureRead) {
                    val answer = requireNotNull(readFastPath.handle(query, diagnostics))
                    diagnostics.event("FAST_PATH_READ", "${answer.start}..${answer.end} tasks=${answer.taskCount}")
                    diagnostics.stage("DONE", "tasks=${answer.taskCount}")
                    messages.value += ChatLine("Tiqo", answer.text)
                    return@launch
                }
                val snapshot = builder.build(query, diagnostics)
                // History is display-only. Every inference gets a fresh Room snapshot.
                val prompt = AdvisorPrompt.user(query, snapshot)
                engine.generate(AdvisorPrompt.SYSTEM, prompt, AdvisorPrompt.GRAMMAR).collect { streaming.value += it }
                diagnostics.stage("PARSING", "bytes=${streaming.value.toByteArray().size}")
                val result = ProposalParser.parse(streaming.value, snapshot)
                diagnostics.stage("DONE", "actions=${result.actions.size}")
                messages.value += ChatLine("Tiqo", result.opinion)
                if (result.actions.isNotEmpty()) proposal.value = result
                streaming.value = ""
            } catch (e: CancellationException) {
                diagnostics.cancel()
                streaming.value = ""
                messages.value += ChatLine("Tiqo", "Generowanie zatrzymane. Nie wykonano zmian.")
            } catch (e: Exception) {
                diagnostics.stage("ERROR", e.toString())
                streaming.value = ""
                error.value = e.message ?: "Błąd generowania"
            } finally { busy.value = false; generating.value = false }
        }
    }

    fun stop() {
        if (!generating.value) return
        diagnostics.cancel()
        generation?.cancel()
        engine.stop()
    }
    fun exportDiagnostics(uri: Uri) = action {
        withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { diagnostics.export(it) }
                ?: error("Nie można zapisać diagnostyki")
        }
    }
    fun unload() {
        if (busy.value) return
        busy.value = true
        ready.value = false
        action {
            try { engine.unload(); status.value = "Model zwolniony z pamięci" }
            finally { busy.value = false }
        }
    }
    fun approve(selected: Set<Int>) {
        val pending = proposal.value ?: return
        if (busy.value || selected.isEmpty() || selected.any { it !in pending.actions.indices }) return
        busy.value = true
        action {
            try {
                executor.approve(pending.copy(actions = pending.actions.filterIndexed { index, _ -> index in selected }))
                messages.value += ChatLine("Tiqo", "Zatwierdzone zmiany zapisano w Room.")
            } finally { proposal.value = null; busy.value = false }
        }
    }
    fun reject() { proposal.value = null }
    fun background() { stop() }
    override fun onCleared() {
        engine.stop()
        CoroutineScope(Dispatchers.IO).launch { engine.close(); database.close() }
    }
}
