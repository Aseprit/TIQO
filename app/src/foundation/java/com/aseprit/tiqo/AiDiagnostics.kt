package com.aseprit.tiqo

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class InferenceStatus(val id: String = "", val stage: String = "IDLE", val detail: String = "",
    val started: Long = 0, val finished: Long? = null)

internal fun inferenceFailure(error: Exception, cancelled: Boolean): Exception =
    if (cancelled || error is CancellationException) CancellationException("CANCELLED").also { it.initCause(error) } else error

/** Private, bounded diagnostic files. Export uses an explicit allowlist, never a directory walk. */
class AiDiagnostics(private val context: Context, private val root: File = File(context.filesDir, "ai-diagnostics")) {
    val state = MutableStateFlow(InferenceStatus())
    val backend = MutableStateFlow("")
    var directory: File = root
        private set
    private val gson = Gson()
    private val cached = mutableMapOf<String, String>()
    private val allowed = setOf("session.log", "session.previous.log", "device.json", "model.json", "inference-config.json", "prompt.txt", "planner-snapshot.json", "grammar.json")
    private val terminal = setOf("CANCELLED", "ERROR", "DONE", "READY")

    init {
        root.mkdirs()
        root.listFiles()?.filter { it.isDirectory && it.name.startsWith("session-") }?.maxByOrNull { it.lastModified() }?.let { directory = it }
    }
    @Synchronized fun begin(kind: String) {
        val id = UUID.randomUUID().toString()
        state.value = InferenceStatus(id, if (kind == "load") "LOADING" else "PREPARING", started = SystemClock.elapsedRealtime())
        directory = File(root, "session-$id").apply { mkdirs() }
        root.listFiles()?.filter { it.isDirectory && it != directory && it.name.startsWith("session-") }
            ?.sortedByDescending { it.lastModified() }?.drop(2)?.forEach { old ->
                allowed.forEach { File(old, it).delete() }; old.delete()
            }
        val memory = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        artifact("device.json", gson.toJson(mapOf("requestId" to id, "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL, "sdk" to Build.VERSION.SDK_INT, "abis" to Build.SUPPORTED_ABIS.toList(),
            "processors" to Runtime.getRuntime().availableProcessors(), "totalRamBytes" to memory.totalMem,
            "availableRamBytes" to memory.availMem, "lowMemory" to memory.lowMemory,
            "javaMaxHeapBytes" to Runtime.getRuntime().maxMemory())))
        for (name in listOf("model.json", "inference-config.json")) artifact(name, cached[name] ?: "{\"available\":false}")
        event("UI_REQUEST", kind)
    }
    @Synchronized fun artifact(name: String, content: String) {
        require(name in allowed)
        if (directory == root) return
        val bytes = content.toByteArray(Charsets.UTF_8)
        runCatching { File(directory, name).writeBytes(bytes.take(262144).toByteArray()) }
        if (bytes.size > 262144) event("TRUNCATED", "$name bytes=${bytes.size}")
    }
    @Synchronized fun event(stage: String, detail: String = "") {
        if (directory == root) return
        val line = "${System.currentTimeMillis()} +${SystemClock.elapsedRealtime() - state.value.started}ms [${state.value.id}] $stage ${detail.replace('\n', ' ').take(8192)}\n"
        runCatching {
            val file = File(directory, "session.log")
            if (file.length() + line.toByteArray().size > 262144) {
                val previous = File(directory, "session.previous.log")
                previous.delete(); file.renameTo(previous)
            }
            file.appendText(line)
        }
    }
    @Synchronized fun stage(stage: String, detail: String = "") {
        event(stage, detail)
        if (state.value.stage in terminal) return
        state.value = state.value.copy(stage = stage, detail = detail,
            finished = if (stage in terminal) SystemClock.elapsedRealtime() else null)
    }
    @Synchronized fun cancel() {
        if (state.value.id.isEmpty() || state.value.stage in terminal) return
        val interrupted = "during ${state.value.stage} ${state.value.detail}"
        event("CANCEL_REQUEST", interrupted)
        stage("CANCELLED", interrupted)
    }
    @Synchronized fun native(lines: String) {
        lines.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.split('\t', limit = 4)
            if (parts.size != 4 || parts[0] != state.value.id) return@forEach
            val kind = parts[2]; val payload = parts[3]
            event("NATIVE/$kind", "native_ms=${parts[1]} $payload")
            when (kind) {
                "TOKENIZING", "EVALUATING", "GENERATING" -> stage(kind, payload)
                "MODEL", "CONFIG" -> {
                    val name = if (kind == "MODEL") "model.json" else "inference-config.json"
                    val json = runCatching {
                        com.google.gson.JsonParser.parseString(payload).asJsonObject.apply {
                            addProperty("loadSessionId", state.value.id)
                            if (kind == "CONFIG") {
                                val reason = get("fallbackReason")?.asString.orEmpty()
                                backend.value = "${get("backend")?.asString.orEmpty()} · ${get("device")?.asString.orEmpty()}" +
                                    if (reason.isNotEmpty()) " · fallback: $reason" else ""
                            }
                        }.toString()
                    }.getOrDefault(payload)
                    cached[name] = json; artifact(name, json)
                }
            }
        }
    }
    @Synchronized fun export(output: OutputStream) {
        check(directory != root) { "Brak zapisanej sesji diagnostycznej" }
        ZipOutputStream(output).use { zip ->
            fun add(folder: File, prefix: String) {
                allowed.sorted().forEach { name ->
                    val file = File(folder, name)
                    if (file.isFile) {
                        zip.putNextEntry(ZipEntry(prefix + name))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            add(directory, "")
            root.listFiles()?.filter { it.isDirectory && it != directory && it.name.startsWith("session-") }
                ?.sortedByDescending { it.lastModified() }?.take(2)?.forEach { add(it, "history/${it.name}/") }
        }
    }
}
