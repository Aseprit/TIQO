package com.aseprit.tiqo.ai

import android.util.Log
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Honest boundary for the future official llama.cpp JNI backend.
 *
 * The current APK deliberately does not package llama.cpp native libraries: upstream's Android
 * example is a source/CMake module rather than a published runtime artifact and currently raises
 * Tiqo's Android baseline. ModelManager therefore reports GGUF as runtime-unavailable and never
 * copies or activates it. Keeping the engine contract here prevents GGUF support from being
 * implemented by extension spoofing or by routing a GGUF file into LiteRT-LM.
 */
class LlamaCppEngine : LlmEngine {
    override val descriptor = LlmDescriptor(
        id = "llama.cpp-unavailable",
        displayName = "llama.cpp (niedostępny w tym buildzie)",
        capabilities = emptySet(),
    )
    private val loaded = MutableStateFlow(false)
    override val isLoaded: StateFlow<Boolean> = loaded

    override suspend fun load(modelPath: String): Result<Unit> {
        val reason = if (!File(modelPath).extension.equals("gguf", ignoreCase = true)) {
            "llama.cpp wymaga pliku .gguf"
        } else {
            RUNTIME_UNAVAILABLE_MESSAGE
        }
        Log.e(TAG, "event=model_load_failure kind=NativeBackend reason=$reason")
        return Result.failure(LlmRuntimeException(LlmFailureKind.NativeBackend, reason))
    }

    override fun generate(request: LlmRequest): Flow<LlmEvent> = flowOf(
        LlmEvent.Failure(RUNTIME_UNAVAILABLE_MESSAGE, recoverable = false, kind = LlmFailureKind.NativeBackend),
    )

    override suspend fun stopGeneration() = Unit
    override suspend fun resetConversation() = Unit
    override suspend fun unload() { loaded.value = false }

    companion object {
        const val RUNTIME_UNAVAILABLE_MESSAGE =
            "GGUF rozpoznany, ale ten APK nie zawiera jeszcze zweryfikowanego runtime llama.cpp/JNI"
        private const val TAG = "TiqoLlamaCpp"
    }
}
