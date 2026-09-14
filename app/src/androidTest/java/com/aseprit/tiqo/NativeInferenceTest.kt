package com.aseprit.tiqo

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.*

/** Opt-in real model test; uses a separate Room database, never the user's planner. */
@RunWith(AndroidJUnit4::class)
class NativeInferenceTest {
    @Test fun realGgufStreamsConstrainedOutputAndReloads() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val path = InstrumentationRegistry.getArguments().getString("modelPath")
        assumeTrue("Provide a local GGUF with modelPath", path != null && File(path).isFile)
        val transcript = File(context.filesDir, "inference-test-transcript.txt")
        transcript.writeText("SYSTEM:\n${AdvisorPrompt.SYSTEM}\nGRAMMAR:\n${AdvisorPrompt.GRAMMAR}\n")
        val db = Room.databaseBuilder(context, TaskDatabase::class.java, "native-integration-test.db").build()
        val repo = TaskRepository(db)
        val clock = Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneId.of("Europe/Warsaw"))
        val builder = ContextBuilder(repo.dao, clock)
        val executor = ProposalExecutor(repo)
        val engine = LlamaCppEngine()
        try {
            db.clearAllTables()
            repo.dao.insert(Task(id = 17, title = "Kup chleb", date = LocalDate.parse("2026-09-13").toEpochDay()))
            val untouched = Task(id = 18, title = "Podlej kwiaty", date = LocalDate.parse("2026-09-12").toEpochDay())
            repo.dao.insert(untouched)
            engine.load(path!!)
            val greeting = engine.generate("You are a helpful assistant. /no_think",
                "Reply with a JSON greeting in Polish.", """
                root ::= "{\"hello\":\"" [a-zA-Z ]+ "\"}"
                """.trimIndent()).toList()
            assertTrue(greeting.size > 1)
            assertTrue(greeting.joinToString("").startsWith("{\"hello\":\""))
            transcript.appendText("\nGREETING: ${greeting.joinToString("")}\n")
            engine.unload()
            engine.load(path)

            suspend fun propose(query: String): Proposal {
                val snapshot = builder.build(query)
                val before = repo.dao.range(Long.MIN_VALUE, Long.MAX_VALUE)
                val prompt = AdvisorPrompt.user(query, snapshot)
                transcript.appendText("\nUSER:\n$prompt\n")
                val formatted = engine.formattedPrompt(AdvisorPrompt.SYSTEM, prompt)
                assertEquals("<|im_start|>system\n${AdvisorPrompt.SYSTEM}<|im_end|>\n<|im_start|>user\n$prompt<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n", formatted)
                transcript.appendText("FORMATTED JNI INPUT:\n$formatted\n")
                val chunks = engine.generate(AdvisorPrompt.SYSTEM, prompt, AdvisorPrompt.GRAMMAR).toList()
                val raw = chunks.joinToString("")
                transcript.appendText("RAW (${chunks.size} chunks):\n$raw\n")
                android.util.Log.i("TiqoNativeTest", "Structured response: $raw")
                assertTrue("Response must stream", chunks.size > 1)
                assertEquals("Generating a proposal must not write Room", before, repo.dao.range(Long.MIN_VALUE, Long.MAX_VALUE))
                return ProposalParser.parse(raw, snapshot)
            }
            suspend fun change(query: String, type: ActionType): Proposal {
                val before = repo.dao.range(Long.MIN_VALUE, Long.MAX_VALUE).sortedBy { it.id }
                val proposal = propose(query)
                assertEquals("$query -> $proposal", 1, proposal.actions.size)
                assertEquals(type, proposal.actions.single().type)
                if (type != ActionType.CREATE_TASK) assertEquals(17L, proposal.actions.single().taskId)
                executor.approve(proposal)
                val after = repo.dao.range(Long.MIN_VALUE, Long.MAX_VALUE).sortedBy { it.id }
                val action = proposal.actions.single()
                if (type == ActionType.CREATE_TASK) {
                    assertEquals(before, after.filter { row -> before.any { it.id == row.id } })
                    val added = after.filter { row -> before.none { it.id == row.id } }.single()
                    assertEquals(action.title, added.title)
                    assertEquals(LocalDate.parse(action.date).toEpochDay(), added.date)
                    assertFalse(added.completed)
                    assertFalse(added.locked)
                } else {
                    val expected = before.map { old ->
                        if (old.id != action.taskId) old else when (type) {
                            ActionType.COMPLETE_TASK -> old.copy(completed = true)
                            ActionType.UPDATE_TASK -> old.copy(title = action.title!!)
                            ActionType.MOVE_TASK -> old.copy(date = LocalDate.parse(action.date).toEpochDay())
                            else -> error("Not an update")
                        }
                    }
                    assertEquals("Only the requested field may change", expected, after)
                }
                assertEquals(untouched, repo.dao.get(18))
                assertTrue(repo.dao.get(17)!!.locked)
                val fresh = builder.build(query)
                assertEquals(after.filter { it.date in LocalDate.parse(fresh.start).toEpochDay()..LocalDate.parse(fresh.end).toEpochDay() }, fresh.tasks.sortedBy { it.id })
                transcript.appendText("APPROVED: $type\n")
                instrumentation.sendStatus(2, android.os.Bundle().apply { putString("stream", "\nPASS $type: validated; no writes before approval; exact Room diff; fresh snapshot.\n") })
                return proposal
            }

            change("Oznacz zadanie Kup chleb jako ukończone.", ActionType.COMPLETE_TASK)
            assertTrue(repo.dao.get(17)!!.completed)
            change("Zmień tytuł zadania Kup chleb na Kup świeży chleb.", ActionType.UPDATE_TASK)
            assertEquals("Kup świeży chleb", repo.dao.get(17)!!.title)
            change("Przenieś zadanie Kup świeży chleb na 2026-09-14.", ActionType.MOVE_TASK)
            assertEquals(LocalDate.parse("2026-09-14").toEpochDay(), repo.dao.get(17)!!.date)
            change("Dodaj zadanie Odbierz paczkę na 2026-09-14.", ActionType.CREATE_TASK)
            val created = repo.dao.range(Long.MIN_VALUE, Long.MAX_VALUE).single { it.id != 17L && it.id != 18L }
            assertEquals("Odbierz paczkę", created.title)
            assertEquals(LocalDate.parse("2026-09-14").toEpochDay(), created.date)
            assertFalse(created.locked)

            val current = builder.build("co mam 2026-09-14?")
            val invalid = """{"opinion":"Zmień","actions":[{"type":"COMPLETE_TASK","taskId":999999,"title":null,"date":null}]}"""
            assertTrue(runCatching { ProposalParser.parse(invalid, current) }.isFailure)
            assertTrue(runCatching { executor.approve(Proposal("Zmień", listOf(TaskAction(ActionType.COMPLETE_TASK, 999999, null, null)), current)) }.isFailure)
            assertEquals(current.tasks, builder.build("co mam 2026-09-14?").tasks)
            val state = propose("Co mam 2026-09-14?")
            assertTrue("A state question must not modify tasks: $state", state.actions.isEmpty())
            transcript.appendText("PASS: all four actions, lock preservation, untouched task, unknown ID rejection and fresh state query.\n")
        } finally {
            engine.close()
            db.close()
        }
    }
}
