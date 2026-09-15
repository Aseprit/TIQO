package com.aseprit.tiqo

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
class MainViewModelReadFastPathTest {
    @Test fun pureReadWorksWithoutLoadedModelAndNeverStartsInference() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.deleteDatabase("tiqo.db")
        val db = TaskDatabase.open(app)
        val tomorrow = LocalDate.now().plusDays(1)
        db.tasks().insert(Task(title = "Świeży Room task", date = tomorrow.toEpochDay()))
        db.close()
        val vm = MainViewModel(app)
        val store = ViewModelStore().apply { put("read", vm) }
        try {
            assertFalse(vm.ready.value)
            vm.send("Co mam jutro?")
            val lines = withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(10_000) { vm.messages.first { it.size == 2 } }
            }
            assertEquals("Ty", lines[0].role)
            assertTrue(lines[1].text.contains("Świeży Room task"))
            assertNull(vm.proposal.value)
            assertEquals("DONE", vm.diagnostics.state.value.stage)
            val log = File(vm.diagnostics.directory, "session.log").readText()
            assertTrue(log.contains("FAST_PATH_READ"))
            assertTrue(log.contains("tasks=1"))
            for (stage in listOf("TOKENIZING", "PROMPT_EVAL", "GENERATING")) assertFalse(log.contains(stage))
        } finally {
            store.clear()
            app.deleteDatabase("tiqo.db")
            Dispatchers.resetMain()
        }
    }

    @Test fun requestRequiringAiWithoutModelGetsClearChatMessage() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)
        val store = ViewModelStore().apply { put("ai", vm) }
        try {
            assertFalse(vm.ready.value)
            vm.send("Co mam jutro i od czego powinienem zacząć?")
            val lines = withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(10_000) { vm.messages.first { it.size == 2 } }
            }
            assertEquals("Załaduj model AI, aby uzyskać analizę lub zmienić plan.", lines[1].text)
            assertNull(vm.proposal.value)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
