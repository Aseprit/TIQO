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
    private val relative = "(?:dziś|dzis|dzisiaj|jutro|pojutrze)"
    private val weekday = "(?:poniedziałek|wtorek|środę|czwartek|piątek|sobotę|niedzielę)"
    private val dated = "(?:$relative|$weekday)"
    private val patterns = listOf(
        Regex("^dodaj(?: mi)? ($relative) (.+)$", RegexOption.IGNORE_CASE),
        Regex("^dodaj(?: mi)? zadanie na ($dated):? (.+)$", RegexOption.IGNORE_CASE),
        Regex("^dodaj(?: mi)? na ($dated) (.+)$", RegexOption.IGNORE_CASE),
        Regex("^zaplanuj na ($dated) (.+)$", RegexOption.IGNORE_CASE),
    )
    private val simpleText = Regex("^[\\p{L}\\p{N}]+(?:[ -][\\p{L}\\p{N}]+)*$")
    private val unsafeWords = Regex("\\b(?:i|oraz|plus|potem|nastepnie|albo|lub|jesli|czy|co|jak|powinien|powinnam|najwazniejsze|dodaj|zaplanuj|usun|przenies|zmien|ukoncz|kup|zadzwon|wyslij|zrob|oddaj|napisz|idz|dzis|dzisiaj|jutro|pojutrze|poniedzialek|wtorek|srode|czwartek|piatek|sobote|niedziele)\\b")
    private val smallWords = setOf("u", "do", "na", "w", "z", "ze", "od", "za", "po", "pod", "przed", "mi")
    private val weekdayNames = mapOf(
        "poniedzialek" to DayOfWeek.MONDAY, "wtorek" to DayOfWeek.TUESDAY,
        "srode" to DayOfWeek.WEDNESDAY, "czwartek" to DayOfWeek.THURSDAY,
        "piatek" to DayOfWeek.FRIDAY, "sobote" to DayOfWeek.SATURDAY,
        "niedziele" to DayOfWeek.SUNDAY,
    )

    fun route(query: String): SimpleCreateRequest? {
        val q = query.trim().replace(Regex("\\s+"), " ")
        val match = patterns.firstNotNullOfOrNull { it.matchEntire(q) } ?: return null
        val dateText = match.groupValues[1]
        val taskText = match.groupValues[2].trim()
        if (!validTaskText(taskText)) return null
        val today = LocalDate.now(clock)
        val date = weekdayNames[plain(dateText)]?.let { day ->
            if (today.dayOfWeek == day) return null // "na środę" on Wednesday is ambiguous.
            today.with(TemporalAdjusters.next(day))
        } ?: run {
            val scope = ContextBuilder.resolve(dateText, today)
            if (scope.start != scope.end) return null
            scope.start
        }
        return SimpleCreateRequest(date, taskText, dateText.lowercase(Locale.forLanguageTag("pl-PL")))
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
        require(validTaskText(title) && title.length <= 300)
        val originalTerms = terms(request.taskText)
        require(originalTerms.isNotEmpty() && terms(title) == originalTerms)
        require(snapshot.start == request.date.toString() && snapshot.end == request.date.toString())
        Proposal("Proponuję dodać „$title” na ${request.dateLabel}.",
            listOf(TaskAction(ActionType.CREATE_TASK, null, title, request.date.toString())), snapshot)
            .also(ProposalParser::validate)
    }.getOrNull()

    private fun validTaskText(text: String): Boolean {
        val words = plain(text).split(' ')
        return text.length in 3..120 && simpleText.matches(text) &&
            words.size in 2..3 && obviousTaskLead(text.substringBefore(' ')) &&
            !unsafeWords.containsMatchIn(plain(text)) &&
            words.count { it.length >= 5 && it.endsWith('c') } <= 1
    }

    // Only short, familiar task phrases enter this route. Imperative or compound text stays generic.
    private fun obviousTaskLead(first: String): Boolean {
        val word = first.lowercase(Locale.forLanguageTag("pl-PL"))
        val unaccented = plain(word)
        return word.endsWith('ć') || word == "wizyta" || word == "wizytę" ||
            unaccented.endsWith("anie") || unaccented.endsWith("enie")
    }

    // Exact words protect task meaning; only the confirmed visit inflection is normalized here.
    private fun terms(text: String) = plain(text).split(Regex("[ -]+"))
        .filter { it.isNotBlank() && it !in smallWords }
        .map { if (it == "wizyte") "wizyta" else it }.toSet()

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
