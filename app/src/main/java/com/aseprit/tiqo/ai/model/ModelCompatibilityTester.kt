package com.aseprit.tiqo.ai.model

import com.aseprit.tiqo.ai.LlmEngine
import com.aseprit.tiqo.ai.DEFAULT_CHAT_MAX_OUTPUT_TOKENS
import com.aseprit.tiqo.ai.LlmMessage
import com.aseprit.tiqo.ai.LlmRequest
import com.aseprit.tiqo.ai.LlmRole
import com.aseprit.tiqo.ai.PLANNER_INFERENCE_TIMEOUT_MILLIS
import com.aseprit.tiqo.ai.PLANNER_MAX_OUTPUT_TOKENS
import com.aseprit.tiqo.ai.PlannerInferenceResult
import com.aseprit.tiqo.ai.PlannerInferenceRunner
import com.aseprit.tiqo.ai.tools.PlannerToolProtocol

internal class ModelCompatibilityTester(
    private val engine: LlmEngine,
    timeoutMillis: Long = PLANNER_INFERENCE_TIMEOUT_MILLIS,
    private val onDiagnosticStage: (String, String?) -> Unit = { _, _ -> },
) {
    private val runner = PlannerInferenceRunner(engine, timeoutMillis)

    suspend fun testPlanner(): ModelTestState {
        val diagnostics = PLANNER_CASES.map { scenario ->
            onDiagnosticStage("scenario_${scenario.name}_started", null)
            val inference = runner.run(
                LlmRequest(
                    messages = listOf(
                        LlmMessage(LlmRole.System, PLANNER_TEST_SYSTEM_INSTRUCTION),
                        LlmMessage(LlmRole.User, scenario.prompt),
                    ),
                    availableTools = PlannerToolProtocol.definitions,
                    maxOutputTokens = PLANNER_MAX_OUTPUT_TOKENS,
                    diagnosticTag = "scenario_${scenario.name}",
                ),
            )
            val diagnostic = diagnosticFor(scenario, inference)
            onDiagnosticStage(
                "scenario_${scenario.name}_completed",
                "parser=${diagnostic.parserPath} tool=${diagnostic.parsedTool ?: "none"} error=${diagnostic.error ?: "none"}",
            )
            diagnostic
        }
        val passed = diagnostics.count { it.error == null }
        val compatibility = when (passed) {
            diagnostics.size -> PlannerCompatibilityStatus.Compatible
            0 -> PlannerCompatibilityStatus.Failed
            else -> PlannerCompatibilityStatus.ExperimentalPartial
        }
        val status = when (compatibility) {
            PlannerCompatibilityStatus.Compatible -> ModelTestStatus.Passed
            PlannerCompatibilityStatus.ExperimentalPartial -> ModelTestStatus.Partial
            PlannerCompatibilityStatus.Failed -> ModelTestStatus.Failed
        }
        return ModelTestState(
            status = status,
            response = "$passed/${diagnostics.size} scenariusze planera zakończone poprawnie",
            generationTimeMillis = diagnostics.sumOf { it.totalTimeMillis },
            firstTokenTimeMillis = diagnostics.mapNotNull { it.firstTokenTimeMillis }.minOrNull(),
            plannerCompatibility = compatibility,
            diagnostics = diagnostics,
            error = diagnostics.firstOrNull { it.error != null }?.error,
        )
    }

    suspend fun testChat(): ModelTestState {
        val started = System.nanoTime()
        var firstTokenMillis: Long? = null
        var runtimeError: String? = null
        val raw = StringBuilder()
        engine.generate(
            LlmRequest(
                messages = listOf(
                    LlmMessage(LlmRole.System, "Odpowiadaj krótko i naturalnie w języku użytkownika. Nie używaj narzędzi."),
                    LlmMessage(LlmRole.User, "Odpowiedz krótko po polsku: model działa."),
                ),
                maxOutputTokens = DEFAULT_CHAT_MAX_OUTPUT_TOKENS,
            ),
        ).collect { event ->
            when (event) {
                is com.aseprit.tiqo.ai.LlmEvent.Token -> {
                    if (event.content.isNotEmpty() && firstTokenMillis == null) {
                        firstTokenMillis = (System.nanoTime() - started) / 1_000_000
                    }
                    if (raw.length < RAW_UI_LIMIT) raw.append(event.content.take(RAW_UI_LIMIT - raw.length))
                }
                is com.aseprit.tiqo.ai.LlmEvent.Failure -> runtimeError = event.reason
                else -> Unit
            }
        }
        val total = (System.nanoTime() - started) / 1_000_000
        val error = runtimeError ?: if (raw.isBlank()) "Model nie zwrócił tekstu" else null
        val diagnostic = ModelTestDiagnostic(
            scenario = "chat",
            rawOutput = raw.toString(),
            nativeToolCall = null,
            parserPath = "none",
            parsedTool = null,
            firstTokenTimeMillis = firstTokenMillis,
            totalTimeMillis = total,
            error = error,
        )
        return ModelTestState(
            status = if (error == null) ModelTestStatus.Passed else ModelTestStatus.Failed,
            response = if (error == null) "Chat runtime odpowiedział" else "",
            generationTimeMillis = total,
            firstTokenTimeMillis = firstTokenMillis,
            diagnostics = listOf(diagnostic),
            error = error,
        )
    }

    private fun diagnosticFor(scenario: PlannerTestCase, inference: PlannerInferenceResult): ModelTestDiagnostic {
        val protocolResult = inference.toolCall?.let { PlannerToolProtocol.parse(it.name, it.argumentsJson) }
        val protocolError = protocolResult?.exceptionOrNull()?.message
        val actualTool = inference.toolCall?.name
        val expectedError = if (actualTool != null && actualTool != scenario.expectedTool) {
            "Oczekiwano ${scenario.expectedTool}, otrzymano $actualTool"
        } else {
            null
        }
        return ModelTestDiagnostic(
            scenario = scenario.name,
            rawOutput = inference.rawOutput.take(RAW_UI_LIMIT),
            nativeToolCall = inference.nativeToolCall?.let { "${it.name} ${it.argumentsJson}" },
            parserPath = inference.parserPath.diagnosticName,
            parsedTool = actualTool.takeIf { protocolResult?.isSuccess == true },
            firstTokenTimeMillis = inference.firstTokenTimeMillis,
            totalTimeMillis = inference.totalTimeMillis,
            error = inference.error ?: protocolError ?: expectedError,
        )
    }

    private data class PlannerTestCase(val name: String, val prompt: String, val expectedTool: String)

    private companion object {
        const val RAW_UI_LIMIT = 2_000
        const val PLANNER_TEST_SYSTEM_INSTRUCTION =
            "To jest bezpieczny test zgodności roli Planner. Zawsze wywołaj dokładnie jedno z dostarczonych narzędzi. Niczego nie wykonuj i nie odpowiadaj zwykłym tekstem. Dzisiaj jest 2026-09-04."
        val PLANNER_CASES = listOf(
            PlannerTestCase("create", "Jutro o 6 mam dentystę", "create_item"),
            PlannerTestCase("delegate", "Cześć", "delegate_to_chat"),
            PlannerTestCase(
                "clarification",
                "Zmień istniejący wpis, ale nie podaję który ani co zmienić.",
                "request_clarification",
            ),
        )
    }
}
