package com.aseprit.tiqo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlannerTest {
    private lateinit var db: TaskDatabase
    private lateinit var repo: TaskRepository
    private val today = LocalDate.of(2026, 9, 12)
    private val clock = Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneId.of("Europe/Warsaw"))
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TaskDatabase::class.java).build()
        repo = TaskRepository(db)
    }
    @After fun close() { db.close() }

    @Test fun manualCrudAndFreshContext() = runTest {
        repo.saveManual(null, "Pierwsze", today.plusDays(1))
        val builder = ContextBuilder(repo.dao, clock)
        val before = builder.build("co mam jutro?")
        val task = before.tasks.single()
        assertTrue(task.locked)
        repo.saveManual(task.id, "Zmienione", today.plusDays(1))
        repo.dao.complete(task.id, true)
        val fresh = builder.build("co mam jutro?").tasks.single()
        assertEquals("Zmienione", fresh.title)
        assertTrue(fresh.completed)
        repo.dao.delete(task.id)
        assertTrue(builder.build("co mam jutro?").tasks.isEmpty())
    }

    @Test fun approvalIsExplicitAndPreservesLock() = runTest {
        repo.saveManual(null, "Ręczne", today)
        val snapshot = ContextBuilder(repo.dao, clock).build("przenieś na jutro")
        val old = snapshot.tasks.single()
        val p = ProposalParser.parse("""{"opinion":"Przenieś jutro.","actions":[{"type":"MOVE_TASK","taskId":${old.id},"title":null,"date":"2026-09-13"}]}""", snapshot)
        assertEquals(today.toEpochDay(), repo.dao.get(old.id)!!.date)
        ProposalExecutor(repo).approve(p)
        assertEquals(today.plusDays(1).toEpochDay(), repo.dao.get(old.id)!!.date)
        assertTrue(repo.dao.get(old.id)!!.locked)
    }

    @Test fun staleProposalHasNoPartialWrites() = runTest {
        repo.saveManual(null, "Task", today)
        val snapshot = ContextBuilder(repo.dao, clock).build("dzisiaj")
        val p = Proposal("OK", listOf(TaskAction(ActionType.CREATE_TASK, null, "Nowe", "2026-09-12"),
            TaskAction(ActionType.COMPLETE_TASK, snapshot.tasks.single().id, null, null)), snapshot)
        repo.saveManual(snapshot.tasks.single().id, "Zmiana po rozmowie", today)
        assertTrue(runCatching { ProposalExecutor(repo).approve(p) }.isFailure)
        val rows = repo.dao.range(today.toEpochDay(), today.toEpochDay())
        assertEquals(1, rows.size)
        assertFalse(rows.single().completed)
    }

    @Test fun fabricatedIdsAndInvalidDatesAreRejected() = runTest {
        val s = ContextBuilder(repo.dao, clock).build("dzisiaj")
        assertTrue(runCatching { ProposalParser.parse("""{"opinion":"OK","actions":[{"type":"COMPLETE_TASK","taskId":999,"title":null,"date":null}]}""", s) }.isFailure)
        assertTrue(runCatching { ProposalParser.parse("""{"opinion":"OK","actions":[{"type":"CREATE_TASK","taskId":null,"title":"Task","date":"2026-02-31"}]}""", s) }.isFailure)
    }

    @Test fun approvedCreateUpdateAndCompleteUseRoom() = runTest {
        val builder = ContextBuilder(repo.dao, clock)
        val executor = ProposalExecutor(repo)
        executor.approve(Proposal("Dodaj", listOf(TaskAction(ActionType.CREATE_TASK, null, "Nowe", today.toString())), builder.build("dzisiaj")))
        val created = builder.build("dzisiaj").tasks.single()
        assertFalse(created.locked)
        executor.approve(Proposal("Edytuj", listOf(TaskAction(ActionType.UPDATE_TASK, created.id, "Lepszy tytuł", null)), builder.build("dzisiaj")))
        executor.approve(Proposal("Ukończ", listOf(TaskAction(ActionType.COMPLETE_TASK, created.id, null, null)), builder.build("dzisiaj")))
        val updated = repo.dao.get(created.id)!!
        assertEquals("Lepszy tytuł", updated.title)
        assertTrue(updated.completed)
        assertEquals(created.createdAt, updated.createdAt)
    }

    @Test fun tomorrowContextDoesNotIncludeUnrelatedDays() = runTest {
        repo.saveManual(null, "Dzisiaj", today)
        repo.saveManual(null, "Jutro", today.plusDays(1))
        repo.saveManual(null, "Za miesiąc", today.plusMonths(1))
        assertEquals(listOf("Jutro"), ContextBuilder(repo.dao, clock).build("co mam jutro?").tasks.map { it.title })
    }

    @Test fun partialStreamingOpinionHandlesEscapes() {
        assertEquals("Zażółć\n", streamedOpinion("{\"opinion\":\"Zażółć\\n"))
        assertEquals("Hej", streamedOpinion("{\"opinion\":\"Hej\\u00"))
    }

    @Test fun invalidImportPreservesPreviouslyImportedFile() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ModelStore(context)
        val input = java.io.File(context.cacheDir, "import-test.gguf")
        val original = byteArrayOf(71, 71, 85, 70) + ByteArray(40) { 7 }
        input.writeBytes(original)
        store.import(android.net.Uri.fromFile(input))
        assertArrayEquals(original, store.model.readBytes())
        input.writeText("not a GGUF")
        assertTrue(runCatching { store.import(android.net.Uri.fromFile(input)) }.isFailure)
        assertArrayEquals(original, store.model.readBytes())
        assertFalse(java.io.File(store.model.parentFile, "import.partial").exists())
    }

    @Test fun weekBoundaryAndSnapshotLimit() = runTest {
        val scope = ContextBuilder.resolve("co mam w tym tygodniu?", today)
        assertEquals(LocalDate.of(2026, 9, 7), scope.start)
        assertEquals(LocalDate.of(2026, 9, 13), scope.end)
        repeat(81) { repo.saveManual(null, "Task $it", today) }
        assertTrue(runCatching { ContextBuilder(repo.dao, clock).build("dzisiaj") }.isFailure)
    }

    @Test fun persistsAcrossDatabaseReopen() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "persistence-test.db"
        context.deleteDatabase(name)
        val first = Room.databaseBuilder(context, TaskDatabase::class.java, name).build()
        val id = first.tasks().insert(Task(title = "Trwałe", date = today.toEpochDay()))
        first.close()
        val second = Room.databaseBuilder(context, TaskDatabase::class.java, name).build()
        try { assertEquals("Trwałe", second.tasks().get(id)!!.title) }
        finally { second.close(); context.deleteDatabase(name) }
    }

    @Test fun migratesExistingOneOffTasksWithoutDeletingLegacyData() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { old ->
            old.execSQL("CREATE TABLE planner_items(id INTEGER PRIMARY KEY, title TEXT, type TEXT, recurrenceType TEXT, startDateEpochDay INTEGER, createdAtMillis INTEGER)")
            old.execSQL("CREATE TABLE occurrence_completions(itemId INTEGER, occurrenceDateEpochDay INTEGER)")
            old.execSQL("INSERT INTO planner_items VALUES(42,'Stare','TASK','NONE',100,123)")
            old.execSQL("INSERT INTO planner_items VALUES(43,'Cykliczne','TASK','DAILY',100,123)")
            old.execSQL("INSERT INTO occurrence_completions VALUES(42,100)")
            old.version = 1
        }
        val migrated = Room.databaseBuilder(context, TaskDatabase::class.java, name).addMigrations(TaskDatabase.MIGRATION_1_3).build()
        try {
            val task = migrated.tasks().get(42)!!
            assertTrue(task.completed && task.locked)
            assertNull(migrated.tasks().get(43))
            migrated.openHelper.readableDatabase.query("SELECT COUNT(*) FROM planner_items").use { cursor ->
                cursor.moveToFirst(); assertEquals(2, cursor.getInt(0))
            }
        } finally { migrated.close(); context.deleteDatabase(name) }
    }
}
