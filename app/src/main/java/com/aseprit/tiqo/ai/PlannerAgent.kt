package com.aseprit.tiqo.ai

import android.util.Log
import com.aseprit.tiqo.BuildConfig
import com.aseprit.tiqo.ai.tools.PlannerToolCall
import com.aseprit.tiqo.ai.tools.PlannerToolContext
import com.aseprit.tiqo.ai.tools.PlannerToolProtocol
import com.aseprit.tiqo.ai.tools.PlannerToolResult
import com.aseprit.tiqo.ai.tools.PlannerTools
import com.aseprit.tiqo.domain.model.PlannerItem
import com.aseprit.tiqo.domain.repository.PlannerRepository
import com.google.gson.Gson
import java.text.Normalizer
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.flow.first

enum class AiAgentStatus {
    Ready,
    Thinking,
    Executing,
    Error,
}

class PlannerAgent(
    private val engine: LlmEngine,
    private val repository: PlannerRepository,
    private val tools: PlannerTools,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val locale: Locale = Locale("pl", "PL"),
) {
    suspend fun execute(
        command: String,
        selectedDate: LocalDate? = null,
        onStatus: (AiAgentStatus) -> Unit = {},
        onToolCall: suspend (String, String) -> Unit = { _, _ -> },
    ): PlannerToolResult {
        if (!engine.isLoaded.value) return PlannerToolResult.ValidationError("Model Planner AI nie jest załadowany")
        val baseContext = PlannerToolContext.from(clock, locale)
        val candidates = resolveCandidates(command, baseContext.currentDate)
        val context = baseContext.copy(
            selectedDate = selectedDate ?: baseContext.currentDate,
            candidateItems = candidates,
        )
        val started = System.nanoTime()
        onStatus(AiAgentStatus.Thinking)
        var selectedCall: LlmEvent.ToolCall? = null
        var failure: LlmEvent.Failure? = null
        engine.generate(
            LlmRequest(
                messages = listOf(
                    LlmMessage(LlmRole.System, systemInstruction(context)),
                    LlmMessage(LlmRole.User, command.trim()),
                ),
                availableTools = PlannerToolProtocol.definitions,
            ),
        ).collect { event ->
            when (event) {
                is LlmEvent.ToolCall -> if (selectedCall == null) {
                    selectedCall = event
                    onToolCall(event.name, event.argumentsJson)
                }
                is LlmEvent.Failure -> failure = event
                else -> Unit
            }
        }
        failure?.let {
            onStatus(AiAgentStatus.Error)
            return PlannerToolResult.ValidationError(it.reason)
        }
        val rawCall = selectedCall ?: run {
            onStatus(AiAgentStatus.Error)
            return PlannerToolResult.ValidationError("Model nie zwrócił poprawnego polecenia")
        }
        val parsed = PlannerToolProtocol.parse(rawCall.name, rawCall.argumentsJson).getOrElse {
            onStatus(AiAgentStatus.Error)
            debugLog(rawCall.name, false, started)
            return PlannerToolResult.ValidationError(it.message ?: "Nieprawidłowy format polecenia")
        }
        if (!targetIsAllowed(parsed, candidates)) {
            onStatus(AiAgentStatus.Error)
            debugLog(rawCall.name, false, started)
            return PlannerToolResult.ValidationError("Model wskazał element spoza zweryfikowanych kandydatów")
        }
        onStatus(AiAgentStatus.Executing)
        val result = tools.execute(parsed, context)
        debugLog(rawCall.name, result !is PlannerToolResult.ValidationError, started)
        onStatus(if (result is PlannerToolResult.ValidationError) AiAgentStatus.Error else AiAgentStatus.Ready)
        return result
    }

    suspend fun undo(token: String): PlannerToolResult = tools.undo(token)

    private suspend fun resolveCandidates(command: String, currentDate: LocalDate): List<PlannerItem> {
        val items = repository.observeOccurrences(currentDate.minusDays(30), currentDate.plusYears(2))
            .first()
            .distinctBy { it.item.id }
            .map { it.item }
        val commandTokens = normalize(command).split(Regex("\\s+")).filter { it.length >= 4 }
        return items.sortedByDescending { item ->
            val title = normalize(item.title)
            commandTokens.count { token -> title.contains(token.take(5)) }
        }.take(MAX_CANDIDATES)
    }

    private fun targetIsAllowed(call: PlannerToolCall, candidates: List<PlannerItem>): Boolean {
        val targetId = when (call) {
            is PlannerToolCall.GetItem -> call.itemId
            is PlannerToolCall.UpdateItem -> call.itemId
            is PlannerToolCall.DeleteItem -> call.itemId
            is PlannerToolCall.SetCompleted -> call.itemId
            else -> return true
        }
        return candidates.any { it.id == targetId }
    }

    private fun systemInstruction(context: PlannerToolContext): String {
        val candidatePayload = context.candidateItems.map { item ->
            mapOf(
                "item_id" to item.id,
                "title" to item.title,
                "date" to item.startDate.toString(),
                "time" to item.time?.toString(),
                "type" to item.type.name.uppercase(),
            )
        }
        return """
            Current local date: ${context.currentDate}
            Current local time: ${context.currentTime.withSecond(0).withNano(0)}
            Timezone: ${context.zoneId.id}
            Locale: ${context.locale.toLanguageTag()}
            Selected date: ${context.selectedDate}
            Candidate planner items: ${Gson().toJson(candidatePayload)}
            You are the active Tiqo planner router. Use exactly one provided function.
            Never answer general questions yourself. Ask for clarification instead of guessing a target.
        """.trimIndent()
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(locale), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")

    private fun debugLog(toolName: String, valid: Boolean, started: Long) {
        if (BuildConfig.DEBUG) {
            val millis = (System.nanoTime() - started) / 1_000_000
            runCatching { Log.d(TAG, "tool=$toolName valid=$valid durationMs=$millis") }
        }
    }

    private companion object {
        const val TAG = "TiqoPlannerAgent"
        const val MAX_CANDIDATES = 20
    }
}
