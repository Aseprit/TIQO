package com.aseprit.tiqo.ai

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Shared planner inference/diagnostic path used by real execution and Model Manager tests. */
internal class PlannerInferenceRunner(
    private val engine: LlmEngine,
    private val timeoutMillis: Long = PLANNER_INFERENCE_TIMEOUT_MILLIS,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    suspend fun run(request: LlmRequest): PlannerInferenceResult {
        val started = nanoTime()
        var firstTokenMillis: Long? = null
        var nativeCall: LlmEvent.ToolCall? = null
        var runtimeError: String? = null
        val rawOutput = StringBuilder()
        var timedOut = false

        try {
            withTimeout(timeoutMillis) {
                engine.generate(request).collect { event ->
                    when (event) {
                        is LlmEvent.Token -> {
                            if (event.content.isNotEmpty() && firstTokenMillis == null) {
                                firstTokenMillis = elapsedMillis(started)
                            }
                            if (rawOutput.length < MAX_CAPTURED_OUTPUT_CHARS) {
                                rawOutput.append(event.content.take(MAX_CAPTURED_OUTPUT_CHARS - rawOutput.length))
                            }
                        }
                        is LlmEvent.ToolCall -> {
                            if (firstTokenMillis == null) firstTokenMillis = elapsedMillis(started)
                            if (nativeCall == null) nativeCall = event
                        }
                        is LlmEvent.Failure -> runtimeError = event.reason
                        LlmEvent.Finished -> Unit
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            timedOut = true
            withContext(NonCancellable) { runCatching { engine.stopGeneration() } }
            runtimeError = "Planner AI przekroczył limit ${timeoutMillis / 1_000} s; generowanie zostało zatrzymane"
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            runtimeError = error.message?.takeIf(String::isNotBlank) ?: error::class.java.simpleName
        }

        val resolution = if (runtimeError == null) {
            PlannerToolCallAdapter.resolveDetailed(nativeCall, rawOutput.toString())
        } else {
            PlannerToolCallResolution(null, PlannerParserPath.None, runtimeError)
        }
        return PlannerInferenceResult(
            rawOutput = rawOutput.toString(),
            nativeToolCall = nativeCall,
            parserPath = resolution.parserPath,
            toolCall = resolution.call,
            firstTokenTimeMillis = firstTokenMillis,
            totalTimeMillis = elapsedMillis(started),
            error = runtimeError ?: resolution.error,
            timedOut = timedOut,
        )
    }

    private fun elapsedMillis(started: Long): Long = (nanoTime() - started) / 1_000_000

    private companion object {
        const val MAX_CAPTURED_OUTPUT_CHARS = 16_384
    }
}

internal data class PlannerInferenceResult(
    val rawOutput: String,
    val nativeToolCall: LlmEvent.ToolCall?,
    val parserPath: PlannerParserPath,
    val toolCall: LlmEvent.ToolCall?,
    val firstTokenTimeMillis: Long?,
    val totalTimeMillis: Long,
    val error: String?,
    val timedOut: Boolean,
)

internal const val PLANNER_MAX_OUTPUT_TOKENS = 96
internal const val PLANNER_INFERENCE_TIMEOUT_MILLIS = 30_000L
