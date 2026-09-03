package com.aseprit.tiqo.ai.model

import android.content.Context
import android.net.Uri
import com.aseprit.tiqo.ai.LlmEngine
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ModelRole { Planner, Chat }
enum class ModelBackend { LiteRtLm, Unknown }

data class ModelCatalogEntry(
    val id: String,
    val displayName: String,
    val description: String,
    val role: ModelRole,
    val backend: ModelBackend,
    val fileName: String,
    val expectedSizeBytes: Long? = null,
    val checksumSha256: String? = null,
    val downloadUrl: String? = null,
    val licenseName: String,
    val licenseUrl: String? = null,
    val requiresLicenseAcceptance: Boolean,
    val minimumRamGb: Int,
    val recommendedRamGb: Int,
    val contextTokens: Int,
)

data class InstalledModelState(
    val catalog: ModelCatalogEntry,
    val installed: Boolean,
    val fileSizeBytes: Long = 0,
    val loaded: Boolean = false,
)

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

class ModelManager(
    private val context: Context,
    private val engines: Map<ModelRole, LlmEngine>,
) {
    private val modelDirectory = File(context.filesDir, "models")
    private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val loadedModelIds = mutableMapOf<ModelRole, String>()
    private val idleUnloadJobs = mutableMapOf<ModelRole, Job>()
    private val downloadJobs = mutableMapOf<String, Job>()
    private val mutableModels = MutableStateFlow(readStates())
    private val mutableDownloads = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())
    val models: StateFlow<List<InstalledModelState>> = mutableModels.asStateFlow()
    val downloads: StateFlow<Map<String, ModelDownloadState>> = mutableDownloads.asStateFlow()

    suspend fun importModel(modelId: String, source: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val entry = catalog.first { it.id == modelId }
            unloadRole(entry.role)
            modelDirectory.mkdirs()
            val target = File(modelDirectory, entry.fileName)
            val temporary = File(modelDirectory, "${entry.fileName}.partial")
            requireNotNull(context.contentResolver.openInputStream(source)) { "Nie można otworzyć pliku modelu" }.use { input ->
                temporary.outputStream().use(input::copyTo)
            }
            validateDownloadedFile(entry, temporary)
            finalizeDownload(temporary, target)
            refresh()
        }
    }

    fun downloadModel(modelId: String) {
        if (downloadJobs[modelId]?.isActive == true) return
        val entry = catalog.firstOrNull { it.id == modelId } ?: return
        val source = entry.downloadUrl ?: run {
            updateDownload(ModelDownloadState(modelId, DownloadStatus.Failed, error = "Ten model wymaga ręcznego importu po zaakceptowaniu licencji"))
            return
        }
        downloadJobs[modelId] = lifecycleScope.launch(Dispatchers.IO) {
            modelDirectory.mkdirs()
            val target = File(modelDirectory, entry.fileName)
            val temporary = File(modelDirectory, "${entry.fileName}.partial")
            try {
                require(modelDirectory.usableSpace > (entry.expectedSizeBytes ?: 0L) + MIN_FREE_SPACE_BYTES) {
                    "Za mało wolnego miejsca na model"
                }
                unloadRole(entry.role)
                val connection = (URL(source).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "Tiqo-Android/${context.packageManager.getPackageInfo(context.packageName, 0).versionName}")
                }
                connection.connect()
                require(connection.responseCode in 200..299) { "Serwer zwrócił HTTP ${connection.responseCode}" }
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: entry.expectedSizeBytes
                var downloaded = 0L
                updateDownload(ModelDownloadState(modelId, DownloadStatus.Downloading, 0, total))
                connection.inputStream.use { input ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            updateDownload(ModelDownloadState(modelId, DownloadStatus.Downloading, downloaded, total))
                        }
                    }
                }
                connection.disconnect()
                updateDownload(ModelDownloadState(modelId, DownloadStatus.Verifying, downloaded, total))
                validateDownloadedFile(entry, temporary)
                finalizeDownload(temporary, target)
                refresh()
                updateDownload(ModelDownloadState(modelId, DownloadStatus.Completed, downloaded, total))
            } catch (cancelled: CancellationException) {
                temporary.delete()
                updateDownload(ModelDownloadState(modelId, DownloadStatus.Cancelled))
                throw cancelled
            } catch (error: Throwable) {
                temporary.delete()
                updateDownload(ModelDownloadState(modelId, DownloadStatus.Failed, error = error.message ?: "Pobieranie nie powiodło się"))
            } finally {
                downloadJobs.remove(modelId)
            }
        }
    }

    fun cancelDownload(modelId: String) {
        downloadJobs.remove(modelId)?.cancel()
    }

    suspend fun loadModel(modelId: String): Result<Unit> {
        val entry = catalog.firstOrNull { it.id == modelId }
            ?: return Result.failure(IllegalArgumentException("Nieznany model"))
        val path = modelPath(modelId)
            ?: return Result.failure(IllegalStateException("Model nie jest zainstalowany"))
        val engine = engines[entry.role]
            ?: return Result.failure(IllegalStateException("Brak silnika dla roli ${entry.role}"))
        unloadRole(entry.role)
        val result = engine.load(path)
        if (result.isSuccess) loadedModelIds[entry.role] = modelId
        refresh()
        return result
    }

    suspend fun unloadModel(modelId: String? = null): Result<Unit> = runCatching {
        if (modelId == null) {
            ModelRole.entries.forEach { unloadRole(it) }
        } else {
            val role = catalog.first { it.id == modelId }.role
            unloadRole(role)
        }
        refresh()
    }

    fun onAppForegrounded() {
        idleUnloadJobs.values.forEach(Job::cancel)
        idleUnloadJobs.clear()
        val planner = catalog.firstOrNull { it.role == ModelRole.Planner && modelPath(it.id) != null }
        if (planner != null && loadedModelIds[ModelRole.Planner] == null) {
            lifecycleScope.launch { loadModel(planner.id) }
        }
    }

    fun onAppBackgrounded() {
        scheduleUnload(ModelRole.Chat, CHAT_IDLE_UNLOAD_MILLIS)
        scheduleUnload(ModelRole.Planner, PLANNER_IDLE_UNLOAD_MILLIS)
    }

    fun onMemoryPressure() {
        lifecycleScope.launch { unloadModel() }
    }

    suspend fun deleteModel(modelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val entry = catalog.first { it.id == modelId }
            cancelDownload(modelId)
            if (loadedModelIds[entry.role] == modelId) unloadRole(entry.role)
            val target = File(modelDirectory, entry.fileName)
            if (target.exists()) check(target.delete()) { "Nie można usunąć pliku modelu" }
            refresh()
        }
    }

    fun modelPath(modelId: String): String? {
        val entry = catalog.firstOrNull { it.id == modelId } ?: return null
        return File(modelDirectory, entry.fileName).takeIf(File::isFile)?.absolutePath
    }

    private fun scheduleUnload(role: ModelRole, delayMillis: Long) {
        idleUnloadJobs.remove(role)?.cancel()
        if (loadedModelIds[role] == null) return
        idleUnloadJobs[role] = lifecycleScope.launch {
            delay(delayMillis)
            unloadRole(role)
            refresh()
        }
    }

    private suspend fun unloadRole(role: ModelRole) {
        engines[role]?.unload()
        loadedModelIds.remove(role)
    }

    private fun validateDownloadedFile(entry: ModelCatalogEntry, file: File) {
        require(file.isFile && file.length() > 0) { "Plik modelu jest pusty" }
        entry.expectedSizeBytes?.let { expected ->
            require(file.length() == expected) { "Nieprawidłowy rozmiar pliku modelu" }
        }
        entry.checksumSha256?.let { expected ->
            require(sha256(file).equals(expected, ignoreCase = true)) { "Checksum modelu jest nieprawidłowy" }
        }
    }

    private fun finalizeDownload(temporary: File, target: File) {
        if (target.exists()) check(target.delete()) { "Nie można zastąpić starego modelu" }
        check(temporary.renameTo(target)) { "Nie można sfinalizować modelu" }
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

    private fun updateDownload(state: ModelDownloadState) {
        mutableDownloads.value = mutableDownloads.value + (state.modelId to state)
    }

    private fun refresh() {
        mutableModels.value = readStates()
    }

    private fun readStates(): List<InstalledModelState> = catalog.map { entry ->
        val file = File(modelDirectory, entry.fileName)
        InstalledModelState(
            catalog = entry,
            installed = file.isFile,
            fileSizeBytes = if (file.isFile) file.length() else 0,
            loaded = loadedModelIds[entry.role] == entry.id && engines[entry.role]?.isLoaded?.value == true,
        )
    }

    companion object {
        private const val PLANNER_IDLE_UNLOAD_MILLIS = 5 * 60 * 1_000L
        private const val CHAT_IDLE_UNLOAD_MILLIS = 60 * 1_000L
        private const val MIN_FREE_SPACE_BYTES = 256L * 1024 * 1024

        val catalog = listOf(
            ModelCatalogEntry(
                id = "planner_gemma_270m",
                displayName = "PlannerGemma 270M PL",
                description = "Mały model function-calling do poleceń planera. Import po zaakceptowaniu licencji Gemma.",
                role = ModelRole.Planner,
                backend = ModelBackend.LiteRtLm,
                fileName = "planner-gemma-270m-pl.litertlm",
                licenseName = "Gemma Terms of Use",
                licenseUrl = "https://ai.google.dev/gemma/terms",
                requiresLicenseAcceptance = true,
                minimumRamGb = 2,
                recommendedRamGb = 4,
                contextTokens = 1024,
            ),
            ModelCatalogEntry(
                id = "qwen_2_5_1_5b_q8",
                displayName = "Qwen 2.5 1.5B Chat",
                description = "Opcjonalny lokalny model do rozmów ogólnych.",
                role = ModelRole.Chat,
                backend = ModelBackend.LiteRtLm,
                fileName = "qwen2.5-1.5b-q8-ekv4096.litertlm",
                expectedSizeBytes = 1_597_931_520L,
                checksumSha256 = "faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9",
                downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
                licenseName = "Apache-2.0",
                licenseUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct",
                requiresLicenseAcceptance = false,
                minimumRamGb = 4,
                recommendedRamGb = 8,
                contextTokens = 4096,
            ),
        )
    }
}
