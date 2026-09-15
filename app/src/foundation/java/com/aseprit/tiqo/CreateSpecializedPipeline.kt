package com.aseprit.tiqo

import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import java.text.Normalizer
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class SimpleCreateRequest(val date: LocalDate, val taskText: String, val dateLabel: String)

class CreateSpecializedPipeline(private val builder: ContextBuilder, private val clock: Clock = Clock.systemDefaultZone()) {
    private val relativeDates = setOf("dzis", "dzisiaj", "jutro", "pojutrze")
    private val tokens = Regex("\\S+")
    // Clause boundaries and another command make a single CREATE unclear; task vocabulary is unrestricted.
    private val compoundOrDecision = Regex("\\b(?:i|oraz|plus|potem|nastepnie|albo|lub|ale|jesli|zeby|czy|co|jak|powinienem|powinnam)\\b")
    private val anotherCommand = Regex("\\b(?:dodaj|zaplanuj|przenies|przesun|zmien|usun|ukoncz|skasuj|powiedz|doradz|ocen|rozplanuj)\\b")
    private val weekdayNames = mapOf(
        "poniedzialek" to DayOfWeek.MONDAY, "wtorek" to DayOfWeek.TUESDAY,
        "srode" to DayOfWeek.WEDNESDAY, "czwartek" to DayOfWeek.THURSDAY,
        "piatek" to DayOfWeek.FRIDAY, "sobote" to DayOfWeek.SATURDAY,
        "niedziele" to DayOfWeek.SUNDAY,
    )
    private val dateWords = relativeDates + weekdayNames.keys

    fun route(query: String): SimpleCreateRequest? {
        val q = query.trim()
        val parts = tokens.findAll(q).toList()
        if (parts.size < 3) return null
        fun token(index: Int) = plain(parts[index].value.trim(':', '.', ',', '!', '?'))
        val command = token(0)
        if (command != "dodaj" && command != "zaplanuj") return null
        var index = 1
        if (command == "dodaj" && index < parts.size && token(index) == "mi") index++
        if (command == "dodaj" && index < parts.size && token(index) == "zadanie") index++
        if (index < parts.size && token(index) == "na") index++
        if (index >= parts.size) return null
        val dateText = token(index)
        if (dateText !in dateWords) return null
        val dateLabel = parts[index].value.trim(':', '.', ',', '!', '?').lowercase(Locale.forLanguageTag("pl-PL"))
        index++
        if (index < parts.size && parts[index].value == ":") index++
        if (index >= parts.size) return null
        val taskText = q.substring(parts[index].range.first).trim().trimEnd('.', '!', '?').trim()
        if (!singleTaskStructure(taskText)) return null
        val today = LocalDate.now(clock)
        val date = weekdayNames[dateText]?.let { day ->
            if (today.dayOfWeek == day) return null // "na środę" on Wednesday is ambiguous.
            today.with(TemporalAdjusters.next(day))
        } ?: run {
            val scope = ContextBuilder.resolve(dateText, today)
            if (scope.start != scope.end) return null
            scope.start
        }
        return SimpleCreateRequest(date, taskText, dateLabel)
    }

    suspend fun snapshot(request: SimpleCreateRequest, diagnostics: AiDiagnostics? = null): PlannerSnapshot =
        builder.build(request.date.toString(), diagnostics)

    suspend fun generateProposal(request: SimpleCreateRequest, normalizer: CreateTitleNormalizer,
                                 diagnostics: AiDiagnostics): Proposal? {
        diagnostics.event("CREATE_SPECIALIZED", "resolvedDate=${request.date} taskChars=${request.taskText.length}")
        val snapshot = snapshot(request, diagnostics)
        diagnostics.beginNormalization()
        val result = try { normalizer.normalize(request.taskText) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                diagnostics.event("NORMALIZATION_RESULT", "accepted=false error=${e.javaClass.simpleName}")
                return null
            }
            finally { diagnostics.endNormalization() }
        val proposed = proposal(request, snapshot, result)
        diagnostics.event("NORMALIZATION_RESULT", "accepted=${proposed != null} output=${result.take(500)}")
        return proposed
    }

    fun proposal(request: SimpleCreateRequest, snapshot: PlannerSnapshot, result: String): Proposal? = runCatching {
        val root = JsonParser.parseString(result).asJsonObject
        require(root.keySet() == setOf("title"))
        val title = root["title"].asString.trim()
        require(title.isNotBlank() && title.length <= 300 && !title.any { it.isISOControl() })
        require(!containsDateWord(title) && relatedTitle(request.taskText, title))
        require(snapshot.start == request.date.toString() && snapshot.end == request.date.toString())
        Proposal("Proponuję dodać „$title” na ${request.dateLabel}.",
            listOf(TaskAction(ActionType.CREATE_TASK, null, title, request.date.toString())), snapshot)
            .also(ProposalParser::validate)
    }.getOrNull()

    private fun singleTaskStructure(text: String): Boolean {
        if (text.isBlank() || text.length > 300 || text.any { it == ',' || it == ';' || it == '.' || it == '?' || it == '\n' || it == '\r' }) return false
        val normalized = plain(text)
        return !compoundOrDecision.containsMatchIn(normalized) &&
            !anotherCommand.containsMatchIn(normalized) && !containsDateWord(text)
    }

    private fun containsDateWord(text: String): Boolean = words(text).any { it in dateWords }

    // Small spelling/inflection edits are allowed in each word, while missing or new words
    // and changes to numbers or distinct nouns stay in the generic approval pipeline.
    private fun relatedTitle(original: String, title: String): Boolean {
        val source = words(original)
        val remaining = words(title).toMutableList()
        if (source.isEmpty() || source.size != remaining.size) return false
        for (word in source) {
            val match = remaining.indexOfFirst { candidate ->
                if (word.any(Char::isDigit) || candidate.any(Char::isDigit)) word == candidate
                else editDistance(word, candidate) <= maxOf(1, maxOf(word.length, candidate.length) / 4)
            }
            if (match < 0) return false
            remaining.removeAt(match)
        }
        return true
    }

    private fun words(text: String) = plain(text).split(Regex("[^\\p{L}\\p{N}]+"))
        .filter(String::isNotBlank)

    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1,
                previous[j] + if (a[i] == b[j]) 0 else 1)
            previous = current
        }
        return previous[b.length]
    }

    private fun plain(text: String): String = Normalizer.normalize(text.lowercase(Locale.forLanguageTag("pl-PL")), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace('ł', 'l')
}

object CreateTitlePrompt {
    const val SYSTEM = """Zwróć tylko JSON {"title":"..."} z krótkim naturalnym tytułem zadania po polsku. Zachowaj znaczenie, nie dodawaj informacji ani terminu. Popraw oczywistą fleksję: "wizytę u fryzjera" → "Wizyta u fryzjera". Jeśli tekst jest już naturalny, zostaw go. /no_think"""
    fun user(taskText: String) = "Treść zadania: $taskText"
    val GRAMMAR = """
root ::= "{" ws "\"title\"" ws ":" ws string ws "}" ws
string ::= "\"" ([^"\\\x00-\x1F] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F]))* "\""
ws ::= [ \t\n\r]*
""".trimIndent()
}

class CreateTitleNormalizer(private val inference: LocalInference) {
    suspend fun normalize(taskText: String): String = buildString {
        inference.generate(CreateTitlePrompt.SYSTEM, CreateTitlePrompt.user(taskText), CreateTitlePrompt.GRAMMAR)
            .collect { append(it) }
    }
}
