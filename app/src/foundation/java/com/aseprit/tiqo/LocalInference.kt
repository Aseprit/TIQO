package com.aseprit.tiqo

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.Executors

interface LocalInference {
    suspend fun load(path: String)
    fun generate(system: String, prompt: String, grammar: String): Flow<String>
    fun stop()
    suspend fun unload()
}

/** Single owner of native state. Token pieces cross JNI as bytes, preserving split UTF-8. */
class LlamaCppEngine(private val diagnostics: AiDiagnostics? = null) : LocalInference {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val mutex = Mutex()
    private var handle = 0L
    private val handleLock = Any()
    @Volatile private var stopped = false
    private val library by lazy { System.loadLibrary("tiqo_llama") }

    override suspend fun load(path: String) = withContext(dispatcher) {
        mutex.withLock {
            library
            val old = synchronized(handleLock) { handle.also { handle = 0L } }
            if (old != 0L) nativeFree(old)
            val loaded = monitored { nativeLoad(path, 4096, minOf(4, Runtime.getRuntime().availableProcessors())) }
            synchronized(handleLock) { handle = loaded }
            check(handle != 0L) { "Nie można załadować modelu GGUF" }
        }
    }

    override fun generate(system: String, prompt: String, grammar: String): Flow<String> = flow {
        mutex.withLock {
            check(handle != 0L) { "Najpierw zaimportuj i załaduj GGUF" }
            val requestContext = currentCoroutineContext()
            try {
                synchronized(handleLock) {
                    requestContext.ensureActive()
                    stopped = false
                    nativePrepare(handle)
                }
                monitored {
                diagnostics?.artifact("prompt.txt", nativeFormat(handle, system.toByteArray(Charsets.UTF_8), prompt.toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8))
                diagnostics?.artifact("grammar.json", com.google.gson.Gson().toJson(mapOf("format" to "GBNF", "grammar" to grammar)))
                diagnostics?.event("JNI_BEGIN", "systemBytes=${system.toByteArray().size} userBytes=${prompt.toByteArray().size}")
                nativeBegin(handle, system.toByteArray(Charsets.UTF_8), prompt.toByteArray(Charsets.UTF_8), grammar)
                val bytes = java.io.ByteArrayOutputStream()
                var emitted = ""
                while (!stopped) {
                    currentCoroutineContext().ensureActive()
                    val piece = nativeNext(handle) ?: break
                    if (bytes.size() == 0) diagnostics?.event("UI_FIRST_TOKEN", "ttft_ms=${android.os.SystemClock.elapsedRealtime() - diagnostics.state.value.started}")
                    bytes.write(piece)
                    // Decoder replacement is held back until the final byte of a character arrives.
                    val decoded = bytes.toByteArray().toString(Charsets.UTF_8).trimEnd('\uFFFD')
                    if (decoded.length > emitted.length) emit(decoded.substring(emitted.length))
                    emitted = decoded
                }
                if (stopped) throw CancellationException("Generowanie zatrzymane")
                }
            } catch (e: Exception) {
                val failure = inferenceFailure(e, stopped || !currentCoroutineContext().isActive)
                diagnostics?.event(if (failure is CancellationException) "JNI_CANCELLED" else "JNI_ERROR", e.toString())
                throw failure
            } finally {
                nativeReset(handle)
            }
        }
    }.flowOn(dispatcher)

    override fun stop() {
        synchronized(handleLock) { stopped = true; if (handle != 0L) nativeCancel(handle) }
    }
    override suspend fun unload() = withContext(dispatcher) {
        mutex.withLock {
            val old = synchronized(handleLock) { handle.also { handle = 0L } }
            if (old != 0L) nativeFree(old)
        }
    }
    suspend fun close() { stop(); unload(); dispatcher.close() }
    private suspend fun <T> monitored(block: suspend () -> T): T = coroutineScope {
        nativeDiagnosticRequest(diagnostics?.state?.value?.id ?: java.util.UUID.randomUUID().toString())
        val poller = launch(Dispatchers.IO) {
            while (isActive) {
                diagnostics?.native(nativeDiagnosticDrain().toString(Charsets.UTF_8))
                delay(200)
            }
        }
        try { block() } finally {
            withContext(NonCancellable) {
                poller.cancelAndJoin()
                diagnostics?.native(nativeDiagnosticDrain().toString(Charsets.UTF_8))
            }
        }
    }
    private external fun nativePrepare(handle: Long)
    private external fun nativeDiagnosticRequest(id: String)
    private external fun nativeDiagnosticDrain(): ByteArray
    // Explicit diagnostic read used by instrumentation; normal chats do not log prompts.
    internal suspend fun formattedPrompt(system: String, prompt: String): String = withContext(dispatcher) {
        mutex.withLock { nativeFormat(handle, system.toByteArray(Charsets.UTF_8), prompt.toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8) }
    }
    private external fun nativeLoad(path: String, context: Int, threads: Int): Long
    private external fun nativeBegin(handle: Long, system: ByteArray, prompt: ByteArray, grammar: String)
    private external fun nativeNext(handle: Long): ByteArray?
    private external fun nativeReset(handle: Long)
    private external fun nativeFree(handle: Long)
    private external fun nativeCancel(handle: Long)
    private external fun nativeFormat(handle: Long, system: ByteArray, prompt: ByteArray): ByteArray
}

class ModelStore(private val context: Context) {
    private val directory = File(context.filesDir, "gguf").apply { mkdirs() }
    val model = File(directory, "model.gguf")
    suspend fun import(uri: Uri): File = withContext(Dispatchers.IO) {
        val partial = File(directory, "import.partial")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val magic = ByteArray(4)
                var offset = 0
                while (offset < 4) {
                    val count = input.read(magic, offset, 4 - offset)
                    require(count > 0) { "Pusty lub uszkodzony plik" }
                    offset += count
                }
                require(magic.contentEquals(byteArrayOf(71, 71, 85, 70))) { "To nie jest plik GGUF" }
                partial.outputStream().use { output ->
                    output.write(magic)
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            } ?: error("Nie można otworzyć pliku")
            require(partial.length() > 32) { "Niepełny GGUF" }
            java.nio.file.Files.move(partial.toPath(), model.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            model
        } finally { partial.delete() }
    }
}
