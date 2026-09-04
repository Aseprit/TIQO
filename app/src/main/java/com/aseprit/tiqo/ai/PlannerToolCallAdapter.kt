package com.aseprit.tiqo.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Adapts textual Qwen/Hermes output without bypassing PlannerToolProtocol validation. */
internal object PlannerToolCallAdapter {
    private val toolCallPattern = Regex(
        pattern = """<tool_call>\s*(.*?)\s*</tool_call>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val thinkPattern = Regex(
        pattern = """<think>.*?</think>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    fun resolve(nativeCall: LlmEvent.ToolCall?, textualOutput: String): Result<LlmEvent.ToolCall> =
        resolveDetailed(nativeCall, textualOutput).asResult()

    fun resolveDetailed(nativeCall: LlmEvent.ToolCall?, textualOutput: String): PlannerToolCallResolution {
        if (nativeCall != null) {
            return PlannerToolCallResolution(nativeCall, PlannerParserPath.Native)
        }
        val clean = thinkPattern.replace(textualOutput, "").trim()
        val hermesCalls = toolCallPattern.findAll(clean).toList()
        if (hermesCalls.size > 1) {
            return PlannerToolCallResolution(
                call = null,
                parserPath = PlannerParserPath.None,
                error = "Model zwrócił więcej niż jedno polecenie",
            )
        }
        val path = when {
            hermesCalls.size == 1 -> PlannerParserPath.QwenXml
            clean.startsWith("{") && clean.endsWith("}") -> PlannerParserPath.StrictJson
            else -> PlannerParserPath.None
        }
        if (path == PlannerParserPath.None) {
            return PlannerToolCallResolution(null, path, "Model nie zwrócił poprawnego polecenia")
        }
        val payload = if (path == PlannerParserPath.QwenXml) hermesCalls.single().groupValues[1] else clean
        return runCatching { parseJsonPayload(payload) }.fold(
            onSuccess = { PlannerToolCallResolution(it, path) },
            onFailure = { PlannerToolCallResolution(null, path, it.message ?: "Nieprawidłowy tool call") },
        )
    }

    private fun parseJsonPayload(payload: String): LlmEvent.ToolCall {
        val root = JsonParser.parseString(payload.trim())
        require(root.isJsonObject) { "Polecenie modelu nie jest obiektem JSON" }
        val rootObject = root.asJsonObject
        val callObject = rootObject.get("function")?.let { function ->
            require(function.isJsonObject) { "Pole function nie jest obiektem JSON" }
            requireOnly(rootObject, "function")
            function.asJsonObject
        } ?: rootObject
        requireOnly(callObject, "name", "tool", "arguments")

        val name = callObject.get("name")?.takeUnless { it.isJsonNull }?.asString
            ?: callObject.get("tool")?.takeUnless { it.isJsonNull }?.asString
        require(!name.isNullOrBlank()) { "Brak nazwy narzędzia" }
        val rawArguments = requireNotNull(callObject.get("arguments")) { "Brak argumentów narzędzia" }
        val arguments = when {
            rawArguments.isJsonObject -> rawArguments.asJsonObject
            rawArguments.isJsonPrimitive && rawArguments.asJsonPrimitive.isString -> {
                val decoded = JsonParser.parseString(rawArguments.asString)
                require(decoded.isJsonObject) { "Argumenty narzędzia nie są obiektem JSON" }
                decoded.asJsonObject
            }
            else -> error("Argumenty narzędzia nie są obiektem JSON")
        }
        return LlmEvent.ToolCall(name, Gson().toJson(arguments))
    }

    private fun requireOnly(json: JsonObject, vararg allowed: String) {
        val unknown = json.keySet() - allowed.toSet()
        require(unknown.isEmpty()) { "Nieznane pola polecenia: ${unknown.joinToString()}" }
    }
}

internal enum class PlannerParserPath(val diagnosticName: String) {
    Native("native"),
    QwenXml("qwen_xml"),
    StrictJson("strict_json"),
    None("none"),
}

internal data class PlannerToolCallResolution(
    val call: LlmEvent.ToolCall?,
    val parserPath: PlannerParserPath,
    val error: String? = null,
) {
    fun asResult(): Result<LlmEvent.ToolCall> = call?.let(Result.Companion::success)
        ?: Result.failure(IllegalArgumentException(error ?: "Model nie zwrócił poprawnego polecenia"))
}
