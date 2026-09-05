package com.aseprit.tiqo.ai

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Crash-resilient breadcrumbs for the short Planner compatibility test. */
class InferenceDiagnostics(private val context: Context) {
    private val lock = Any()
    private val reportFile = File(context.filesDir, REPORT_FILE_NAME)
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableReport = MutableStateFlow(readReport())
    val report: StateFlow<String?> = mutableReport.asStateFlow()

    fun begin(
        modelId: String,
        displayName: String,
        storedFileName: String,
        contextTokens: Int?,
        trigger: String,
    ) {
        synchronized(lock) {
            write(
                buildString {
                    appendLine("Tiqo Planner self-test diagnostics")
                    appendLine("startedAt=${Instant.now()} trigger=$trigger")
                    appendLine("modelId=$modelId displayName=$displayName")
                    appendLine("storedFile=$storedFileName contextTokens=${contextTokens ?: "unknown"}")
                    appendLine("device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT} abi=${Build.SUPPORTED_ABIS.joinToString()}")
                    appendLine("runtime=LiteRT-LM backend=CPU pid=${Process.myPid()}")
                },
                append = false,
            )
            recordLocked("self_test_started")
        }
    }

    fun record(stage: String, detail: String? = null) {
        synchronized(lock) { recordLocked(stage, detail) }
    }

    fun complete(detail: String) {
        synchronized(lock) { recordLocked("self_test_completed", detail) }
    }

    fun export(target: Uri): Result<Unit> = runCatching {
        val contents = synchronized(lock) { readReport() }
            ?: error("Brak zapisanej diagnostyki")
        requireNotNull(context.contentResolver.openOutputStream(target, "w")) {
            "Nie można otworzyć pliku docelowego"
        }.bufferedWriter().use { it.write(contents) }
    }

    fun memorySnapshot(): String {
        val runtime = Runtime.getRuntime()
        val processMemory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
        val systemMemory = ActivityManager.MemoryInfo().also {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
        }
        val proc = runCatching {
            File("/proc/self/status").useLines { lines ->
                lines.filter { line ->
                    line.startsWith("VmRSS:") || line.startsWith("VmHWM:") || line.startsWith("VmSwap:")
                }.joinToString(" ") { it.replace(Regex("\\s+"), "") }
            }
        }.getOrDefault("procStatus=unavailable")
        return buildString {
            append("pssMb=${processMemory.totalPss / KIBIBYTES_PER_MEBIBYTE}")
            append(" privateDirtyMb=${processMemory.totalPrivateDirty / KIBIBYTES_PER_MEBIBYTE}")
            append(" nativePssMb=${processMemory.nativePss / KIBIBYTES_PER_MEBIBYTE}")
            append(" dalvikPssMb=${processMemory.dalvikPss / KIBIBYTES_PER_MEBIBYTE}")
            append(" javaUsedMb=${(runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MEBIBYTE}")
            append(" nativeHeapMb=${Debug.getNativeHeapAllocatedSize() / BYTES_PER_MEBIBYTE}")
            append(" availRamMb=${systemMemory.availMem / BYTES_PER_MEBIBYTE}")
            append(" totalRamMb=${systemMemory.totalMem / BYTES_PER_MEBIBYTE}")
            append(" lowMemory=${systemMemory.lowMemory}")
            append(" $proc")
        }
    }

    private fun recordLocked(stage: String, detail: String? = null) {
        val safeDetail = detail?.replace('\n', ' ')?.take(MAX_DETAIL_CHARS)
        val line = buildString {
            append("at=${Instant.now()} uptimeMs=${SystemClock.elapsedRealtime()} stage=$stage")
            if (!safeDetail.isNullOrBlank()) append(" detail=$safeDetail")
            append(" ${memorySnapshot()}")
            appendLine()
        }
        write(line, append = true)
        preferences.edit().putString(LAST_STAGE_KEY, stage).commit()
    }

    private fun write(text: String, append: Boolean) {
        reportFile.parentFile?.mkdirs()
        FileOutputStream(reportFile, append).use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        mutableReport.value = readReport()
    }

    private fun readReport(): String? = reportFile.takeIf(File::isFile)?.readText()?.takeIf(String::isNotBlank)

    private companion object {
        const val PREFERENCES_NAME = "tiqo_inference_diagnostics"
        const val LAST_STAGE_KEY = "last_inference_stage"
        const val REPORT_FILE_NAME = "planner_self_test_diagnostics.txt"
        const val MAX_DETAIL_CHARS = 500
        const val KIBIBYTES_PER_MEBIBYTE = 1024
        const val BYTES_PER_MEBIBYTE = 1024L * 1024L
    }
}
