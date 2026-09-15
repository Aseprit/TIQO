package com.aseprit.tiqo

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelCreateSpecializedTest {
    @Test fun sendCreatesProposalButRoomChangesOnlyAfterApproval() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.deleteDatabase("tiqo.db")
        val fake = FixedTitleInference("""{"title":"Wizyta u fryzjera"}""")
        val vm = MainViewModel(app, CreateTitleNormalizer(fake))
        val store = ViewModelStore().apply { put("create", vm) }
        try {
            vm.ready.value = true
            vm.send("dodaj mi jutro wizytę u fryzjera")
            val pending = withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(10_000) { vm.proposal.first { it != null } }
            }!!
            val date = LocalDate.now().plusDays(1)
            assertEquals(TaskAction(ActionType.CREATE_TASK, null, "Wizyta u fryzjera", date.toString()), pending.actions.single())
            assertFalse(pending.actions.single().title!!.contains("jutro", ignoreCase = true))
            val db = TaskDatabase.open(app)
            try {
                assertTrue(db.tasks().range(date.toEpochDay(), date.toEpochDay()).isEmpty())
                vm.approve(setOf(0))
                withContext(Dispatchers.Default.limitedParallelism(1)) {
                    withTimeout(10_000) { vm.messages.first { lines -> lines.any { it.text.contains("Zatwierdzone zmiany") } } }
                }
                assertEquals("Wizyta u fryzjera", db.tasks().range(date.toEpochDay(), date.toEpochDay()).single().title)
            } finally { db.close() }
            val log = File(vm.diagnostics.directory, "session.log").readText()
            assertTrue(log.contains("CREATE_SPECIALIZED"))
            assertTrue(log.contains("NORMALIZATION_RESULT accepted=true"))
            assertFalse(log.contains("CREATE_FALLBACK"))
        } finally {
            store.clear()
            app.deleteDatabase("tiqo.db")
            Dispatchers.resetMain()
        }
    }

    @Test fun invalidTitleFallsThroughToGenericAdvisorPipeline() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app, CreateTitleNormalizer(FixedTitleInference("""{"title":"Jutro kupić mleko"}""")))
        val store = ViewModelStore().apply { put("fallback", vm) }
        try {
            vm.ready.value = true // Only the normalizer is faked; generic inference has no loaded GGUF in this test.
            vm.send("dodaj jutro kupić mleko")
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(10_000) { vm.error.first { it != null } }
            }
            val log = File(vm.diagnostics.directory, "session.log").readText()
            assertTrue(log.contains("NORMALIZATION_RESULT accepted=false"))
            assertTrue(log.contains("CREATE_FALLBACK"))
            assertTrue(log.contains("CONTEXT_BUILDER"))
            assertNull(vm.proposal.value)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun normalizationFailureFallsThroughToGenericAdvisorPipeline() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val failingInference = object : LocalInference {
            override suspend fun load(path: String) = Unit
            override fun generate(system: String, prompt: String, grammar: String): Flow<String> =
                throw IllegalStateException("normalization failed")
            override fun stop() = Unit
            override suspend fun unload() = Unit
        }
        val vm = MainViewModel(app, CreateTitleNormalizer(failingInference))
        val store = ViewModelStore().apply { put("failure-fallback", vm) }
        try {
            vm.ready.value = true
            vm.send("dodaj jutro kupić mleko")
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(10_000) { vm.error.first { it != null } }
            }
            val log = File(vm.diagnostics.directory, "session.log").readText()
            assertTrue(log.contains("CREATE_FALLBACK"))
            assertTrue(log.contains("CONTEXT_BUILDER"))
            assertNull(vm.proposal.value)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    private class FixedTitleInference(private val result: String) : LocalInference {
        override suspend fun load(path: String) = Unit
        override fun generate(system: String, prompt: String, grammar: String): Flow<String> = flowOf(result)
        override fun stop() = Unit
        override suspend fun unload() = Unit
    }
}
