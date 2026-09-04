package com.aseprit.tiqo.ai.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.aseprit.tiqo.ai.LlmEngine
import com.aseprit.tiqo.ai.LlamaCppEngine
import com.google.gson.Gson
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class ModelRole { Planner, Chat }
enum class ModelBackend { LiteRtLm, LlamaCpp, Unknown }
enum class ModelFormat(val extension: String) { LiteRtLm("litertlm"), Gguf("gguf"), Unknown("") }
enum class ModelOrigin { Recommended, Imported }
enum class ModelCatalogTier { SmallExperimental, RecommendedChatCandidate, Alternative }
enum class ModelCompatibility { Compatible, RuntimeUnavailable, UnsupportedFormat, MissingFile }

data class ModelCatalogEntry(
    val id: String,
    val displayName: String,
    val description: String,
    val roles: Set<ModelRole>,
    val backend: ModelBackend,
    val fileName: String,
    val expectedSizeBytes: Long? = null,
    val checksumSha256: String? = null,
    val downloadUrl: String? = null,
    val licenseName: String? = null,
    val licenseUrl: String? = null,
    val requiresLicenseAcceptance: Boolean = false,
    val minimumRamGb: Int? = null,
    val recommendedRamGb: Int? = null,
    val contextTokens: Int? = null,
    val origin: ModelOrigin = ModelOrigin.Recommended,
    val catalogTier: ModelCatalogTier = ModelCatalogTier.Alternative,
    val sourceRepository: String? = null,
    val sourceRevision: String? = null,
)

data class InstalledModelState(
    val catalog: ModelCatalogEntry,
    val installed: Boolean,
    val fileSizeBytes: Long = 0,
    val activeRoles: Set<ModelRole> = emptySet(),
    val loadedRoles: Set<ModelRole> = emptySet(),
    val compatibility: ModelCompatibility = ModelCompatibility.MissingFile,
) {
    val loaded: Boolean get() = loadedRoles.isNotEmpty()
}

enum class DownloadStatus { Downloading, Verifying, Completed, Cancelled, Failed }

data class ModelDownloadState(
    val modelId: String,
    val status: DownloadStatus,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
    val error: String? = null,
) {
    val progress: Float? = totalBytes?.takeIf { it > 0 }?.let { downloadedBytes.toFloat() / it }
}

enum class ModelTestStatus { Running, Passed, Partial, Failed }
enum class PlannerCompatibilityStatus { Compatible, ExperimentalPartial, Failed }

data class ModelTestDiagnostic(
    val scenario: String,
    val rawOutput: String,
    val nativeToolCall: String?,
    val parserPath: String,
    val parsedTool: String?,
    val firstTokenTimeMillis: Long?,
    val totalTimeMillis: Long,
    val error: String?,
)

data class ModelTestState(
    val status: ModelTestStatus,
    val response: String = "",
    val loadTimeMillis: Long? = null,
    val generationTimeMillis: Long? = null,
    val firstTokenTimeMillis: Long? = null,
    val plannerCompatibility: PlannerCompatibilityStatus? = null,
    val diagnostics: List<ModelTestDiagnostic> = emptyList(),
    val error: String? = null,
)

/** Owns model files, metadata, selection, and the runtime lifecycle. */
class ModelManager(
    private val context: Context,
    private val engines: Map<ModelRole, LlmEngine>,
) {
    private val modelDirectory = File(context.filesDir, "models")
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val operationMutex = Mutex()
    private val loadedModelIds = mutableMapOf<ModelRole, String>()
    private val idleUnloadJobs = mutableMapOf<ModelRole, Job>()
    private val downloadJobs = mutableMapOf<String, Job>()
    private val downloader = ResumableModelDownloader()
    private val customEntries = loadCustomEntries().toMutableList()
    private val mutableModels = MutableStateFlow(readStates())
    private val mutableDownloads = MutableStateFlow(readPartialDownloadStates())
    private val mutableTests = MutableStateFlow<Map<String, ModelTestState>>(emptyMap())
    val models: StateFlow<List<InstalledModelState>> = mutableModels.asStateFlow()
    val downloads: StateFlow<Map<String, ModelDownloadState>> = mutableDownloads.asStateFlow()
    val tests: StateFlow<Map<String, ModelTestState>> = mutableTests.asStateFlow()

    suspend fun importModel(modelId: String, source: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val entry = allEntries().first { it.id == modelId }
            val sourceName = querySourceName(source) ?: source.lastPathSegment ?: "model"
            require(ModelFormatPolicy.backendFor(sourceName) == entry.backend) { UNSUPPORTED_FORMAT_MESSAGE }
            require(isRuntimeAvailable(entry.backend)) {
                if (entry.backend == ModelBackend.LlamaCpp) LlamaCppEngine.RUNTIME_UNAVAILABLE_MESSAGE else UNSUPPORTED_FORMAT_MESSAGE
            }
            require(ModelFormatPolicy.backendFor(entry.fileName) == entry.backend) {
                UNSUPPORTED_FORMAT_MESSAGE
            }
            entry.roles.forEach { role -> if (loadedModelIds[role] == modelId) unloadRole(role) }
            copyAndValidate(source, entry, File(modelDirectory, entry.fileName))
            refresh()
        }
    }

    suspend fun importLocalModel(source: Uri, displayName: String, roles: Set<ModelRole>): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(roles.isNotEmpty()) { "Wybierz zastosowanie modelu" }
                val sourceName = querySourceName(source) ?: source.lastPathSegment ?: "model"
                val format = ModelFormatPolicy.detect(sourceName)
                require(format != ModelFormat.Unknown) { UNSUPPORTED_FORMAT_MESSAGE }
                val backend = ModelFormatPolicy.backendFor(format)
                require(isRuntimeAvailable(backend)) {
                    if (backend == ModelBackend.LlamaCpp) LlamaCppEngine.RUNTIME_UNAVAILABLE_MESSAGE else UNSUPPORTED_FORMAT_MESSAGE
                }
                val id = "local_${UUID.randomUUID()}"
                val entry = ModelCatalogEntry(
                    id = id,
                    displayName = displayName.trim().ifBlank { sourceName.substringBeforeLast('.') },
                    description = "Lokalny model zaimportowany przez użytkownika.",
                    roles = roles,
                    backend = backend,
                    fileName = "$id.${format.extension}",
                    origin = ModelOrigin.Imported,
                )
                copyAndValidate(source, entry, File(modelDirectory, entry.fileName))
                customEntries += entry
                saveCustomEntries()
                refresh()
                id
            }
        }

    fun downloadModel(modelId: String) {
        if (downloadJobs[modelId]?.isActive == true) return
        val entry = allEntries().firstOrNull { it.id == modelId } ?: return
        val source = entry.downloadUrl ?: run {
            updateDownload(ModelDownloadState(modelId, DownloadStatus.Failed, error = "Ten model nie ma zgodnego pliku do pobrania"))
            return
        }
        downloadJobs[modelId] = lifecycleScope.launch(Dispatchers.IO) {
            modelDirectory.mkdirs()
            val target = File(modelDirectory, entry.fileName)
            val temporary = File(modelDirectory, "${entry.fileName}.partial")
            try {
                require(ModelFormatPolicy.backendFor(entry.fileName) == entry.backend && isRuntimeAvailable(entry.backend)) {
                    UNSUPPORTED_FORMAT_MESSAGE
                }
                val remainingBytes = entry.expectedSizeBytes?.let { (it - temporary.length()).coerceAtLeast(0L) } ?: 0L
                require(modelDirectory.usableSpace > remainingBytes + MIN_FREE_SPACE_BYTES) {
                    "Za mało wolnego miejsca na model"
                }
                entry.roles.forEach { role -> if (loadedModelIds[role] == modelId) unloadRole(role) }
                val userAgent = "Tiqo-Android/${context.packageManager.getPackageInfo(context.packageName, 0).versionName}"
                val downloaded = downloader.download(source, temporary, userAgent, entry.expectedSizeBytes) { bytes, total ->
                    updateDownload(ModelDownloadState(modelId, DownloadStatus.Downloading, bytes, total))
                }
                updateDownload(ModelDownloadState(modelId, DownloadStatus.Verifying, downloaded, entry.expectedSizeBytes))
                validateFile(entry, temporary)
                finalizeDownload(temporary, target)
                refresh()
                updateDownload(ModelDownloadState(modelId, DownloadStatus.Completed, downloaded, entry.expectedSizeBytes))
            } catch (cancelled: CancellationException) {
                updateDownload(
                    ModelDownloadState(
                        modelId,
                        DownloadStatus.Cancelled,
                        downloadedBytes = temporary.takeIf(File::isFile)?.length() ?: 0L,
                        totalBytes = entry.expectedSizeBytes,
                    ),
                )
                throw cancelled
            } catch (error: Throwable) {
                updateDownload(
                    ModelDownloadState(
                        modelId,
                        DownloadStatus.Failed,
                        downloadedBytes = temporary.takeIf(File::isFile)?.length() ?: 0L,
                        totalBytes = entry.expectedSizeBytes,
                        error = error.message ?: "Pobieranie nie powiodło się",
                    ),
                )
            } finally {
                val currentJob = currentCoroutineContext()[Job]
                if (currentJob == null) downloadJobs.remove(modelId) else downloadJobs.remove(modelId, currentJob)
            }
        }
    }

    fun cancelDownload(modelId: String) {
        downloadJobs.remove(modelId)?.cancel()
    }

    suspend fun setActiveModel(role: ModelRole, modelId: String): Result<Unit> = operationMutex.withLock {
        val entry = allEntries().firstOrNull { it.id == modelId }
            ?: return@withLock Result.failure(IllegalArgumentException("Nieznany model"))
        if (role !in entry.roles) return@withLock Result.failure(IllegalArgumentException("Model nie obsługuje tej roli"))
        if (compatibility(entry) != ModelCompatibility.Compatible) {
            return@withLock Result.failure(IllegalStateException(UNSUPPORTED_FORMAT_MESSAGE))
        }
        val previous = activeModelId(role)
        val result = loadModelLocked(modelId, role)
        if (result.isSuccess) {
            preferences.edit().putString(activeKey(role), modelId).apply()
        } else if (previous != null && previous != modelId) {
            loadModelLocked(previous, role)
        } else if (previous == modelId) {
            preferences.edit().remove(activeKey(role)).apply()
        }
        refresh()
        result
    }

    suspend fun loadModel(modelId: String, role: ModelRole? = null): Result<Unit> = operationMutex.withLock {
        val entry = allEntries().firstOrNull { it.id == modelId }
            ?: return@withLock Result.failure(IllegalArgumentException("Nieznany model"))
        val selectedRole = role ?: entry.roles.firstOrNull { activeModelId(it) == modelId } ?: entry.roles.firstOrNull()
            ?: return@withLock Result.failure(IllegalStateException("Model nie ma przypisanej roli"))
        loadModelLocked(modelId, selectedRole).also { refresh() }
    }

    suspend fun loadActiveModel(role: ModelRole): Result<Unit> {
        val id = activeModelId(role) ?: return Result.failure(IllegalStateException("Nie wybrano aktywnego modelu"))
        return loadModel(id, role)
    }

    suspend fun unloadModel(modelId: String? = null): Result<Unit> = operationMutex.withLock {
        runCatching {
            if (modelId == null) ModelRole.entries.forEach { unloadRole(it) }
            else loadedModelIds.filterValues { it == modelId }.keys.toList().forEach { unloadRole(it) }
            refresh()
        }
    }

    fun onAppForegrounded() {
        idleUnloadJobs.values.forEach(Job::cancel)
        idleUnloadJobs.clear()
        val plannerId = activeModelId(ModelRole.Planner) ?: return
        if (loadedModelIds[ModelRole.Planner] == null) lifecycleScope.launch { loadModel(plannerId, ModelRole.Planner) }
    }

    fun onAppBackgrounded() {
        scheduleUnload(ModelRole.Chat, CHAT_IDLE_UNLOAD_MILLIS)
        scheduleUnload(ModelRole.Planner, PLANNER_IDLE_UNLOAD_MILLIS)
    }

    fun onMemoryPressure() {
        lifecycleScope.launch { unloadModel() }
    }

    suspend fun deleteModel(modelId: String): Result<Unit> = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val entry = allEntries().first { it.id == modelId }
                cancelDownload(modelId)
                loadedModelIds.filterValues { it == modelId }.keys.toList().forEach { unloadRole(it) }
                ModelRole.entries.forEach { role ->
                    if (activeModelId(role) == modelId) preferences.edit().remove(activeKey(role)).apply()
                }
                val target = File(modelDirectory, entry.fileName)
                if (target.exists()) check(target.delete()) { "Nie można usunąć pliku modelu" }
                if (entry.origin == ModelOrigin.Imported) {
                    customEntries.removeAll { it.id == modelId }
                    saveCustomEntries()
                }
                refresh()
            }
        }
    }

    suspend fun testModel(modelId: String): Result<ModelTestState> = operationMutex.withLock {
        val entry = allEntries().firstOrNull { it.id == modelId }
            ?: return@withLock Result.failure(IllegalArgumentException("Nieznany model"))
        val role = if (ModelRole.Planner in entry.roles) ModelRole.Planner else entry.roles.first()
        val engine = engines[role]
            ?: return@withLock Result.failure(IllegalStateException("Brak silnika dla wybranej roli"))
        val path = modelPath(modelId)
            ?: return@withLock Result.failure(IllegalStateException("Model nie jest zainstalowany"))
        val previousId = loadedModelIds[role]
        mutableTests.value = mutableTests.value + (modelId to ModelTestState(ModelTestStatus.Running))
        var loadMillis: Long? = null
        val result = runCatching {
            if (previousId != modelId || !engine.isLoaded.value) {
                unloadRole(role)
                loadMillis = measureTimeMillis { engine.load(path).getOrThrow() }
                loadedModelIds[role] = modelId
            }
            val plannerTest = role == ModelRole.Planner
            val tested = if (plannerTest) ModelCompatibilityTester(engine).testPlanner()
            else ModelCompatibilityTester(engine).testChat()
            tested.copy(loadTimeMillis = loadMillis)
        }
        if (previousId != modelId) {
            unloadRole(role)
            if (previousId != null) modelPath(previousId)?.let { if (engine.load(it).isSuccess) loadedModelIds[role] = previousId }
        }
        val state = result.getOrElse { error ->
            ModelTestState(ModelTestStatus.Failed, loadTimeMillis = loadMillis, error = error.message ?: "Test nie powiódł się")
        }
        mutableTests.value = mutableTests.value + (modelId to state)
        refresh()
        if (result.isSuccess) Result.success(state) else Result.failure(result.exceptionOrNull()!!)
    }

    fun activeModelId(role: ModelRole): String? = preferences.getString(activeKey(role), null)

    fun modelPath(modelId: String): String? {
        val entry = allEntries().firstOrNull { it.id == modelId } ?: return null
        return File(modelDirectory, entry.fileName).takeIf(File::isFile)?.absolutePath
    }

    private suspend fun loadModelLocked(modelId: String, role: ModelRole): Result<Unit> {
        val entry = allEntries().firstOrNull { it.id == modelId }
            ?: return Result.failure(IllegalArgumentException("Nieznany model"))
        if (role !in entry.roles) return Result.failure(IllegalArgumentException("Model nie obsługuje tej roli"))
        val path = modelPath(modelId) ?: return Result.failure(IllegalStateException("Model nie jest zainstalowany"))
        val engine = engines[role] ?: return Result.failure(IllegalStateException("Brak silnika dla roli $role"))
        unloadRole(role)
        val result = engine.load(path)
        if (result.isFailure) return result
        if (role == ModelRole.Planner) {
            val testState = ModelCompatibilityTester(engine).testPlanner()
            mutableTests.value = mutableTests.value + (modelId to testState)
            if (testState.plannerCompatibility != PlannerCompatibilityStatus.Compatible) {
                engine.unload()
                return Result.failure(
                    IllegalStateException(
                        testState.error ?: "Model nie przeszedł pełnego testu zgodności Planner AI",
                    ),
                )
            }
        }
        loadedModelIds[role] = modelId
        return Result.success(Unit)
    }

    private fun scheduleUnload(role: ModelRole, delayMillis: Long) {
        idleUnloadJobs.remove(role)?.cancel()
        if (loadedModelIds[role] == null) return
        idleUnloadJobs[role] = lifecycleScope.launch {
            delay(delayMillis)
            operationMutex.withLock {
                unloadRole(role)
                refresh()
            }
        }
    }

    private suspend fun unloadRole(role: ModelRole) {
        engines[role]?.unload()
        loadedModelIds.remove(role)
    }

    private fun copyAndValidate(source: Uri, entry: ModelCatalogEntry, target: File) {
        modelDirectory.mkdirs()
        val temporary = File(modelDirectory, "${entry.fileName}.partial")
        try {
            requireNotNull(context.contentResolver.openInputStream(source)) { "Nie można otworzyć pliku modelu" }.use { input ->
                temporary.outputStream().buffered().use(input::copyTo)
            }
            validateFile(entry, temporary)
            finalizeDownload(temporary, target)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun validateFile(entry: ModelCatalogEntry, file: File) {
        require(ModelFormatPolicy.backendFor(entry.fileName) == entry.backend && isRuntimeAvailable(entry.backend)) {
            UNSUPPORTED_FORMAT_MESSAGE
        }
        require(file.isFile && file.length() > 0) { "Plik modelu jest pusty" }
        entry.expectedSizeBytes?.let { require(file.length() == it) { "Nieprawidłowy rozmiar pliku modelu" } }
        entry.checksumSha256?.let { require(sha256(file).equals(it, ignoreCase = true)) { "Suma kontrolna modelu jest nieprawidłowa" } }
    }

    private fun finalizeDownload(temporary: File, target: File) {
        if (target.exists()) check(target.delete()) { "Nie można zastąpić starego modelu" }
        check(temporary.renameTo(target)) { "Nie można sfinalizować modelu" }
    }

    private fun querySourceName(source: Uri): String? {
        val cursor = context.contentResolver.query(
            source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
        ) ?: return null
        return cursor.use {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && it.moveToFirst()) it.getString(index) else null
        }
    }

    private fun compatibility(entry: ModelCatalogEntry): ModelCompatibility {
        val file = File(modelDirectory, entry.fileName)
        if (!file.isFile) return ModelCompatibility.MissingFile
        if (ModelFormatPolicy.backendFor(entry.fileName) != entry.backend) return ModelCompatibility.UnsupportedFormat
        return if (isRuntimeAvailable(entry.backend)) ModelCompatibility.Compatible else ModelCompatibility.RuntimeUnavailable
    }

    private fun isRuntimeAvailable(backend: ModelBackend): Boolean = when (backend) {
        ModelBackend.LiteRtLm -> true
        ModelBackend.LlamaCpp, ModelBackend.Unknown -> false
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun allEntries(): List<ModelCatalogEntry> = catalog + customEntries

    private fun readPartialDownloadStates(): Map<String, ModelDownloadState> = allEntries().mapNotNull { entry ->
        val partial = File(modelDirectory, "${entry.fileName}.partial")
        partial.takeIf { it.isFile && it.length() > 0L }?.let {
            entry.id to ModelDownloadState(
                modelId = entry.id,
                status = DownloadStatus.Cancelled,
                downloadedBytes = it.length(),
                totalBytes = entry.expectedSizeBytes,
            )
        }
    }.toMap()

    private fun readStates(): List<InstalledModelState> = allEntries().map { entry ->
        val file = File(modelDirectory, entry.fileName)
        InstalledModelState(
            catalog = entry,
            installed = file.isFile,
            fileSizeBytes = if (file.isFile) file.length() else 0,
            activeRoles = entry.roles.filterTo(mutableSetOf()) { activeModelId(it) == entry.id },
            loadedRoles = entry.roles.filterTo(mutableSetOf()) { loadedModelIds[it] == entry.id && engines[it]?.isLoaded?.value == true },
            compatibility = compatibility(entry),
        )
    }

    private fun refresh() { mutableModels.value = readStates() }
    private fun updateDownload(state: ModelDownloadState) { mutableDownloads.value = mutableDownloads.value + (state.modelId to state) }

    private fun loadCustomEntries(): List<ModelCatalogEntry> {
        val json = preferences.getString(CUSTOM_MODELS_KEY, null) ?: return emptyList()
        return runCatching {
            gson.fromJson(json, Array<ModelCatalogEntry>::class.java).toList().filter { it.origin == ModelOrigin.Imported }
        }.getOrDefault(emptyList())
    }

    private fun saveCustomEntries() { preferences.edit().putString(CUSTOM_MODELS_KEY, gson.toJson(customEntries)).apply() }
    private fun activeKey(role: ModelRole) = "active_${role.name.lowercase()}"

    companion object {
        private const val PREFERENCES_NAME = "tiqo_models"
        private const val CUSTOM_MODELS_KEY = "custom_models"
        private const val PLANNER_IDLE_UNLOAD_MILLIS = 5 * 60 * 1_000L
        private const val CHAT_IDLE_UNLOAD_MILLIS = 60 * 1_000L
        private const val MIN_FREE_SPACE_BYTES = 256L * 1024 * 1024
        const val UNSUPPORTED_FORMAT_MESSAGE = "Nieobsługiwany format modelu. Wybierz .litertlm albo zgodny plik .gguf."

        val catalog = listOf(
            ModelCatalogEntry(
                id = "qwen3_0_6b_nothink_q4",
                displayName = "Qwen3 0.6B No-think INT4",
                description = "Experimental Planner / Small Chat: zgodność planera wymaga self-testu na urządzeniu.",
                roles = setOf(ModelRole.Planner, ModelRole.Chat),
                backend = ModelBackend.LiteRtLm,
                fileName = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
                expectedSizeBytes = 347_251_840L,
                checksumSha256 = "2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139",
                downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/6aa2daf8aba4aa456797fb8040b36a3948bcfda7/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
                licenseName = "Apache-2.0",
                licenseUrl = "https://huggingface.co/litert-community/Qwen3-0.6B-int4",
                minimumRamGb = 2,
                recommendedRamGb = 4,
                contextTokens = 1280,
                catalogTier = ModelCatalogTier.SmallExperimental,
                sourceRepository = "litert-community/Qwen3-0.6B-int4",
                sourceRevision = "6aa2daf8aba4aa456797fb8040b36a3948bcfda7",
            ),
            ModelCatalogEntry(
                id = "qwen3_1_7b_dynamic_wi4b32",
                displayName = "Qwen3 1.7B Dynamic INT4",
                description = "Recommended Chat candidate: kandydat do późniejszego porównania jakości, bez deklaracji przewagi dla języka polskiego.",
                roles = setOf(ModelRole.Chat),
                backend = ModelBackend.LiteRtLm,
                fileName = "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
                expectedSizeBytes = 977_184_032L,
                checksumSha256 = "2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b",
                downloadUrl = "https://huggingface.co/litert-community/Qwen3-1.7B/resolve/d9b8a9126e5ac18591306eacd4311ba43b92421e/Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
                licenseName = "Apache-2.0",
                licenseUrl = "https://huggingface.co/litert-community/Qwen3-1.7B",
                minimumRamGb = 4,
                recommendedRamGb = 6,
                contextTokens = 4096,
                catalogTier = ModelCatalogTier.RecommendedChatCandidate,
                sourceRepository = "litert-community/Qwen3-1.7B",
                sourceRevision = "d9b8a9126e5ac18591306eacd4311ba43b92421e",
            ),
            ModelCatalogEntry(
                id = "qwen_2_5_1_5b_q8",
                displayName = "Qwen 2.5 1.5B",
                description = "Większa alternatywa Chat (Q8, ok. 1,6 GB); dostępna, lecz nie jest domyślnym wyborem.",
                roles = setOf(ModelRole.Chat),
                backend = ModelBackend.LiteRtLm,
                fileName = "qwen2.5-1.5b-q8-ekv4096.litertlm",
                expectedSizeBytes = 1_597_931_520L,
                checksumSha256 = "faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9",
                downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
                licenseName = "Apache-2.0",
                licenseUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct",
                minimumRamGb = 4,
                recommendedRamGb = 8,
                contextTokens = 4096,
                catalogTier = ModelCatalogTier.Alternative,
                sourceRepository = "litert-community/Qwen2.5-1.5B-Instruct",
                sourceRevision = "19edb84c69a0212f29a6ef17ba0d6f278b6a1614",
            ),
        )
    }
}

object ModelFormatPolicy {
    fun detect(fileName: String): ModelFormat = when (fileName.substringAfterLast('.', "").lowercase()) {
        ModelFormat.LiteRtLm.extension -> ModelFormat.LiteRtLm
        ModelFormat.Gguf.extension -> ModelFormat.Gguf
        else -> ModelFormat.Unknown
    }

    fun backendFor(fileName: String): ModelBackend = backendFor(detect(fileName))

    fun backendFor(format: ModelFormat): ModelBackend = when (format) {
        ModelFormat.LiteRtLm -> ModelBackend.LiteRtLm
        ModelFormat.Gguf -> ModelBackend.LlamaCpp
        ModelFormat.Unknown -> ModelBackend.Unknown
    }

    /** A recognized format is not necessarily available in the current APK. */
    fun isSupported(fileName: String): Boolean = detect(fileName) == ModelFormat.LiteRtLm
}
