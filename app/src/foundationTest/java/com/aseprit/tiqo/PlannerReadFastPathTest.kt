package com.aseprit.tiqo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlannerReadFastPathTest {
    private lateinit var db: TaskDatabase
    private lateinit var path: PlannerReadFastPath
    private val today = LocalDate.of(2026, 9, 12)
    private val clock = Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneId.of("Europe/Warsaw"))

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TaskDatabase::class.java).build()
        path = PlannerReadFastPath(ContextBuilder(db.tasks(), clock))
    }

    @After fun close() { db.close() }

    @Test fun tomorrowListReadsOnlyTomorrowFromRoom() = runTest {
        db.tasks().insert(Task(title = "Dzisiaj", date = today.toEpochDay()))
        db.tasks().insert(Task(title = "Oddać projekt", date = today.plusDays(1).toEpochDay()))
        db.tasks().insert(Task(title = "Wysłać schemat", date = today.plusDays(1).toEpochDay()))

        val result = requireNotNull(path.handle("Co mam jutro?"))
        assertEquals("2026-09-13", result.start)
        assertEquals("2026-09-13", result.end)
        assertEquals(2, result.taskCount)
        assertTrue(result.text.contains("Jutro masz 2 zadania"))
        assertTrue(result.text.contains("• Oddać projekt"))
        assertTrue(result.text.contains("• Wysłać schemat"))
        assertFalse(result.text.contains("Dzisiaj"))
    }

    @Test fun countQuestionUsesFastPath() = runTest {
        db.tasks().insert(Task(title = "Jedno", date = today.plusDays(1).toEpochDay()))
        assertEquals("Jutro masz 1 zadanie.", path.handle("Ile mam zadań jutro?")?.text)
    }

    @Test fun countAndExistenceStayExactAbovePromptSnapshotLimit() = runTest {
        repeat(81) { db.tasks().insert(Task(title = "Zadanie $it", date = today.plusDays(1).toEpochDay())) }
        assertEquals("Jutro masz 81 zadań.", path.handle("Ile mam zadań jutro?")?.text)
        assertEquals("Tak, jutro masz 81 zadań.", path.handle("Czy mam coś jutro?")?.text)
    }

    @Test fun weekQuestionGroupsTasksByDateAndMarksCompletion() = runTest {
        db.tasks().insert(Task(title = "Gotowe", date = today.minusDays(1).toEpochDay(), completed = true))
        db.tasks().insert(Task(title = "Do zrobienia", date = today.plusDays(1).toEpochDay()))
        val result = requireNotNull(path.handle("Co mam w tym tygodniu?"))
        assertEquals("2026-09-07", result.start)
        assertEquals("2026-09-13", result.end)
        assertTrue(result.text.contains("2026-09-11"))
        assertTrue(result.text.contains("2026-09-13"))
        assertTrue(result.text.contains("Gotowe (wykonane)"))
        assertTrue(result.text.contains("Do zrobienia"))
    }

    @Test fun onlyPureReadQuestionsAreAccepted() = runTest {
        for (query in listOf(
            "Co mam jutro i od czego powinienem zacząć?",
            "Przenieś zadanie X na jutro",
            "Dodaj jutro zadanie X",
            "Muszę ogarnąć projekt, może jutro, ale nie wiem od czego zacząć",
            "Czy powinienem przełożyć coś na środę?",
            "Co jest najważniejsze?",
            "Jak rozplanować mi tydzień?",
            "Co mam jutro...",
            "Co mam jutro??",
        )) assertNull(query, path.handle(query))
    }

    @Test fun freshSnapshotReflectsChangesRatherThanChatHistory() = runTest {
        val id = db.tasks().insert(Task(title = "Stara nazwa", date = today.plusDays(1).toEpochDay()))
        assertTrue(path.handle("Co mam jutro?")!!.text.contains("Stara nazwa"))
        db.tasks().update(db.tasks().get(id)!!.copy(title = "Nowa nazwa", completed = true))
        val fresh = path.handle("Co mam jutro?")!!.text
        assertTrue(fresh.contains("Nowa nazwa (wykonane)"))
        assertFalse(fresh.contains("Stara nazwa"))
    }

    @Test fun supportedEmptyAndExistenceQuestions() = runTest {
        assertEquals("Jutro nie masz żadnych zadań.", path.handle("Co jest zaplanowane jutro?")?.text)
        assertEquals("Nie, jutro nie masz żadnych zadań.", path.handle("Czy mam coś jutro?")?.text)
        assertNotNull(path.handle("Co mam pojutrze?"))
        assertNotNull(path.handle("Co mam w następnym tygodniu?"))
    }
}
