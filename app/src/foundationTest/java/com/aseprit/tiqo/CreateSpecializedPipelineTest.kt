package com.aseprit.tiqo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CreateSpecializedPipelineTest {
    private lateinit var db: TaskDatabase
    private lateinit var repository: TaskRepository
    private lateinit var pipeline: CreateSpecializedPipeline
    private val today = LocalDate.of(2026, 9, 12)
    private val clock = Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneId.of("Europe/Warsaw"))

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TaskDatabase::class.java).build()
        repository = TaskRepository(db)
        pipeline = CreateSpecializedPipeline(ContextBuilder(db.tasks(), clock), clock)
    }
    @After fun close() { db.close() }

    @Test fun obviousCreateResolvesTomorrowAndExtractsOnlyTaskText() {
        val request = requireNotNull(pipeline.route("dodaj mi jutro wizytę u fryzjera"))
        assertEquals(today.plusDays(1), request.date)
        assertEquals("wizytę u fryzjera", request.taskText)
        assertEquals("jutro", request.dateLabel)
        assertNotNull(pipeline.route("dodaj jutro kupić mleko"))
        assertNotNull(pipeline.route("dodaj zadanie na dzisiaj: wysłać maila"))
        assertNotNull(pipeline.route("zaplanuj na jutro wizytę u dentysty"))
        assertEquals(today, pipeline.route("dodaj zadanie na dzisiaj: wysłać maila")?.date)
        assertEquals(LocalDate.of(2026, 9, 16), pipeline.route("dodaj na środę oddanie projektu")?.date)
    }

    @Test fun simpleCreateAcceptsUnaccentedAndMixedTaskPhrasesWithoutRewritingInput() {
        val cases = listOf(
            Triple("dodaj mi jutro wizyte u fryzjera", today.plusDays(1), "wizyte u fryzjera"),
            Triple("dodaj mi jutro meeting zespolu", today.plusDays(1), "meeting zespolu"),
            Triple("dodaj jutro kupic chleb", today.plusDays(1), "kupic chleb"),
            Triple("dodaj jutro zakupy", today.plusDays(1), "zakupy"),
            Triple("dodaj mi pojutrze ogarnąć ESP32", today.plusDays(2), "ogarnąć ESP32"),
            Triple("dodaj na środę telefon do Kuby", LocalDate.of(2026, 9, 16), "telefon do Kuby"),
            Triple("dodaj zadanie na jutro odebrac paczke", today.plusDays(1), "odebrac paczke"),
            Triple("dodaj na pojutrze kupic chleb", today.plusDays(2), "kupic chleb"),
            Triple("zaplanuj na dzisiaj nauka ELM05", today, "nauka ELM05"),
            Triple("dodaj dziś zrobić projekt na fizykę", today, "zrobić projekt na fizykę"),
            Triple("  DODAJ   MI   JUTRO:  meeting zespolu. ", today.plusDays(1), "meeting zespolu"),
            Triple("dodaj mi dzis telefon do Kuby", today, "telefon do Kuby"),
            Triple("dodaj na srode telefon do Kuby", LocalDate.of(2026, 9, 16), "telefon do Kuby"),
        )
        for ((query, date, taskText) in cases) {
            val request = requireNotNull(pipeline.route(query)) { query }
            assertEquals(query, date, request.date)
            assertEquals(query, taskText, request.taskText)
        }
    }

    @Test fun correctedAccentsAndInflectionStayInOrdinaryApprovedCreateProposal() = runTest {
        for ((query, normalized) in listOf(
            "dodaj mi jutro wizyte u fryzjera" to "Wizyta u fryzjera",
            "dodaj mi jutro meeting zespolu" to "Meeting zespołu",
        )) {
            val request = requireNotNull(pipeline.route(query))
            val snapshot = pipeline.snapshot(request)
            val proposal = requireNotNull(pipeline.proposal(request, snapshot, """{"title":"$normalized"}"""))
            assertEquals(TaskAction(ActionType.CREATE_TASK, null, normalized, request.date.toString()), proposal.actions.single())
            assertTrue(db.tasks().range(request.date.toEpochDay(), request.date.toEpochDay()).isEmpty())
            ProposalExecutor(repository).approve(proposal)
            val created = db.tasks().range(request.date.toEpochDay(), request.date.toEpochDay()).single()
            assertEquals(normalized, created.title)
            db.tasks().delete(created.id)
        }
    }

    @Test fun normalizedJsonBecomesOrdinaryCreateProposalWithoutWritingRoom() = runTest {
        val request = requireNotNull(pipeline.route("dodaj mi jutro wizytę u fryzjera"))
        val snapshot = pipeline.snapshot(request)
        assertEquals("2026-09-13", snapshot.start)
        assertEquals("2026-09-13", snapshot.end)
        val proposal = requireNotNull(pipeline.proposal(request, snapshot, """{"title":"Wizyta u fryzjera"}"""))
        assertEquals("Proponuję dodać „Wizyta u fryzjera” na jutro.", proposal.opinion)
        assertEquals(listOf(TaskAction(ActionType.CREATE_TASK, null, "Wizyta u fryzjera", "2026-09-13")), proposal.actions)
        assertFalse(proposal.actions.single().title!!.contains("jutro", ignoreCase = true))
        assertTrue(db.tasks().range(today.plusDays(1).toEpochDay(), today.plusDays(1).toEpochDay()).isEmpty())

        ProposalExecutor(repository).approve(proposal)
        val created = db.tasks().range(today.plusDays(1).toEpochDay(), today.plusDays(1).toEpochDay()).single()
        assertEquals("Wizyta u fryzjera", created.title)
        assertFalse(created.locked)
    }

    @Test fun specializedRouteUsesMinimalInferenceAndLogsResolvedDate() = runTest {
        val request = requireNotNull(pipeline.route("dodaj mi jutro meeting zespolu"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val diagnostics = AiDiagnostics(context, File(context.cacheDir, "create-specialized-${System.nanoTime()}"))
        diagnostics.begin("request")
        val inference = object : LocalInference {
            override suspend fun load(path: String) = Unit
            override fun generate(system: String, prompt: String, grammar: String): Flow<String> {
                assertEquals(CreateTitlePrompt.SYSTEM, system)
                assertTrue(prompt.contains("meeting zespolu"))
                assertFalse(prompt.contains("zespołu"))
                assertFalse(prompt.contains("jutro"))
                assertEquals(CreateTitlePrompt.GRAMMAR, grammar)
                return flowOf("""{"title":"Meeting zespołu"}""")
            }
            override fun stop() = Unit
            override suspend fun unload() = Unit
        }
        val proposal = requireNotNull(pipeline.generateProposal(request, CreateTitleNormalizer(inference), diagnostics))
        assertEquals("Meeting zespołu", proposal.actions.single().title)
        assertTrue(db.tasks().range(request.date.toEpochDay(), request.date.toEpochDay()).isEmpty())
        val log = File(diagnostics.directory, "session.log").readText()
        assertTrue(log.contains("CREATE_SPECIALIZED"))
        assertTrue(log.contains("resolvedDate=2026-09-13"))
        assertTrue(log.contains("NORMALIZATION_RESULT"))
    }

    @Test fun freshSnapshotStillEnforcesStaleProposalValidation() = runTest {
        val request = requireNotNull(pipeline.route("dodaj jutro kupić mleko"))
        val proposal = requireNotNull(pipeline.proposal(request, pipeline.snapshot(request), """{"title":"Kupić mleko"}"""))
        db.tasks().insert(Task(title = "Nowa zmiana", date = request.date.toEpochDay()))
        assertTrue(runCatching { ProposalExecutor(repository).approve(proposal) }.isFailure)
        assertEquals(listOf("Nowa zmiana"), db.tasks().range(request.date.toEpochDay(), request.date.toEpochDay()).map { it.title })
    }

    @Test fun invalidNormalizerOutputFallsBackInsteadOfMakingProposal() = runTest {
        val request = requireNotNull(pipeline.route("dodaj jutro kupić mleko"))
        val snapshot = pipeline.snapshot(request)
        assertNull(pipeline.proposal(request, snapshot, """{"title":"Jutro kupić mleko"}"""))
        assertNull(pipeline.proposal(request, snapshot, """{"title":""}"""))
        assertNull(pipeline.proposal(request, snapshot, """{"title":"Kupić mleko","date":"2026-09-13"}"""))
        assertNull(pipeline.proposal(request, snapshot, "not json"))
        assertNull(pipeline.proposal(request, snapshot, """{"title":"Kupić mleko pilnie"}"""))
        assertNull(pipeline.proposal(request, snapshot, """{"title":"Wizyta u dentysty"}"""))
        assertNull(pipeline.proposal(request, snapshot, """{"title":"Kupić"}"""))
        val television = requireNotNull(pipeline.route("dodaj jutro kupić telewizor"))
        assertNull(pipeline.proposal(television, pipeline.snapshot(television), """{"title":"Kupić telefon"}"""))
        val unaccented = requireNotNull(pipeline.route("dodaj jutro kupic chleb"))
        assertNull(pipeline.proposal(unaccented, pipeline.snapshot(unaccented), """{"title":"Jutro kupić chleb"}"""))
        assertNull(pipeline.proposal(unaccented, pipeline.snapshot(unaccented), """{"title":"Kupić chleb pilnie"}"""))
        assertNull(pipeline.proposal(unaccented, pipeline.snapshot(unaccented), """{"title":"${"A".repeat(301)}"}"""))
    }

    @Test fun compoundPlanningAndAmbiguousCommandsStayGeneric() {
        for (query in listOf(
            "dodaj jutro kupić mleko i zadzwonić do mamy",
            "dodaj jutro kupić mleko; dodaj pojutrze zadzwonić do mamy",
            "dodaj jutro kupic mleko. odebrac paczke",
            "dodaj jutro kup mleko, zaplanuj pojutrze telefon do mamy",
            "dodaj jutro sprawdź mleko i wypierz ubrania",
            "dodaj jutro kupić mleko, zadzwonić do mamy",
            "dodaj jutro kupić mleko oraz zadzwonić do mamy",
            "co powinienem jutro zrobić i dodaj X",
            "dodaj jutro kupić mleko, ale najpierw powiedz co jest ważniejsze",
            "dodaj jutro albo pojutrze wizytę u fryzjera",
            "dodaj jutro kupić mleko i pojutrze chleb",
            "dodaj jutro meeting zespolu; przenieś zadanie X na środę",
            "dodaj jutro meeting zespolu i ukończ zadanie X",
            "przenieś zadanie X na jutro",
            "zmień zadanie X",
            "ukończ zadanie X",
            "muszę jutro ogarnąć mleko i dentystę",
        )) assertNull(query, pipeline.route(query))
    }
}
