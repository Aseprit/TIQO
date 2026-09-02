package com.aseprit.tiqo.ai.model

import android.content.Context
import android.net.Uri
import com.aseprit.tiqo.ai.LlmEngine
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

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
    val licenseName: String,
    val requiresLicenseAcceptance: Boolean,
)

data class InstalledModelState(
    val catalog: ModelCatalogEntry,
    val installed: Boolean,
    val fileSizeBytes: Long = 0,
    val loaded: Boolean = false,
)

class ModelManager(
    private val context: Context,
    private val engine: LlmEngine,
) {
    private val modelDirectory = File(context.filesDir, "models")
    private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var loadedModelId: String? = null
    private var idleUnloadJob: Job? = null
    private val mutableModels = MutableStateFlow(readStates())
    val models: StateFlow<List<InstalledModelState>> = mutableModels.asStateFlow()

    suspend fun importModel(modelId: String, source: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val entry = catalog.first { it.id == modelId }
            if (loadedModelId == modelId) {
                engine.unload()
                loadedModelId = null
            }
            modelDirectory.mkdirs()
            val target = File(modelDirectory, entry.fileName)
            val temporary = File(modelDirectory, "${entry.fileName}.partial")
            val input = requireNotNull(context.contentResolver.openInputStream(source)) {
                "Cannot open selected model file"
            }
            input.use { stream ->
                temporary.outputStream().use(stream::copyTo)
            }
            require(temporary.length() > 0) { "Selected model file is empty" }
            if (target.exists()) target.delete()
            check(temporary.renameTo(target)) { "Cannot finalize imported model" }
            refresh()
        }
    }

    suspend fun loadModel(modelId: String): Result<Unit> {
        val path = modelPath(modelId)
            ?: return Result.failure(IllegalStateException("Model is not installed"))
        val result = engine.load(path)
        if (result.isSuccess) loadedModelId = modelId
        refresh()
        return result
    }

    suspend fun unloadModel(): Result<Unit> = runCatching {
        engine.unload()
        loadedModelId = null
        refresh()
    }

    fun onAppForegrounded() {
        idleUnloadJob?.cancel()
        idleUnloadJob = null
    }

    fun onAppBackgrounded() {
        idleUnloadJob?.cancel()
        if (loadedModelId == null) return
        idleUnloadJob = lifecycleScope.launch {
            delay(IDLE_UNLOAD_MILLIS)
            engine.unload()
            loadedModelId = null
            refresh()
        }
    }

    suspend fun deleteModel(modelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val entry = catalog.first { it.id == modelId }
            val target = File(modelDirectory, entry.fileName)
            if (target.exists()) check(target.delete()) { "Cannot delete model file" }
            refresh()
        }
    }

    fun modelPath(modelId: String): String? {
        val entry = catalog.firstOrNull { it.id == modelId } ?: return null
        return File(modelDirectory, entry.fileName).takeIf(File::isFile)?.absolutePath
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
            loaded = loadedModelId == entry.id && engine.isLoaded.value,
        )
    }

    companion object {
        private const val IDLE_UNLOAD_MILLIS = 5 * 60 * 1_000L

        val catalog = listOf(
            ModelCatalogEntry(
                id = "planner_gemma_270m",
                displayName = "PlannerGemma 270M",
                description = "Mały model do rozpoznawania poleceń i tool callingu planera.",
                role = ModelRole.Planner,
                backend = ModelBackend.LiteRtLm,
                fileName = "planner-gemma-270m.litertlm",
                licenseName = "Gemma Terms of Use",
                requiresLicenseAcceptance = true,
            ),
            ModelCatalogEntry(
                id = "local_chat",
                displayName = "Lokalny model czatu",
                description = "Opcjonalny model do rozmów ogólnych; użytkownik dostarcza zgodny plik.",
                role = ModelRole.Chat,
                backend = ModelBackend.LiteRtLm,
                fileName = "chat-model.litertlm",
                licenseName = "Zależna od importowanego modelu",
                requiresLicenseAcceptance = true,
            ),
        )
    }
}
