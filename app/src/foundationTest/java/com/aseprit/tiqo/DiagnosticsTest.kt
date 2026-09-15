package com.aseprit.tiqo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DiagnosticsTest {
    private fun diagnostic(): AiDiagnostics {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return AiDiagnostics(context, File(context.cacheDir, "diagnostics-${System.nanoTime()}"))
    }
    @Test fun specializedTokenCountIsCorrelatedWithNativeTokenizer() {
        val d = diagnostic()
        d.begin("request")
        val id = d.state.value.id
        d.beginNormalization()
        d.native("$id\t1\tTOKENIZED\tbytes=180 tokens=43\n")
        d.endNormalization()
        d.native("$id\t2\tTOKENIZED\tbytes=2820 tokens=649\n")
        val log = File(d.directory, "session.log").readText()
        assertTrue(log.contains("NORMALIZATION_PROMPT_TOKENS bytes=180 tokens=43"))
        assertEquals(1, Regex("NORMALIZATION_PROMPT_TOKENS").findAll(log).count())
    }
    @Test fun cancellationIsTerminalAndKeepsTheInterruptedStage() {
        val d = diagnostic()
        d.begin("request")
        d.stage("EVALUATING", "256/1024")
        d.cancel()
        d.stage("GENERATING") // delayed native event must not undo cancellation
        assertEquals("CANCELLED", d.state.value.stage)
        assertTrue(d.state.value.detail.contains("EVALUATING"))
        assertTrue(inferenceFailure(IllegalStateException("Prompt evaluation failed"), true) is kotlinx.coroutines.CancellationException)
        val failure = IllegalStateException("real decode failure")
        assertSame(failure, inferenceFailure(failure, false))
    }
    @Test fun backendAndFallbackAreVisibleAndSurviveExport() {
        val d = diagnostic()
        d.begin("load")
        val id = d.state.value.id
        d.native("$id\t1\tCONFIG\t{\"backend\":\"CPU\",\"device\":\"CPU\",\"fallbackReason\":\"dlopen failed\"}\n")
        assertTrue(d.backend.value.contains("dlopen failed"))
        d.native("$id\t2\tCONFIG\t{\"backend\":\"OpenCL\",\"device\":\"Adreno 810\",\"offloadedLayers\":33,\"fallbackReason\":\"\"}\n")
        assertTrue(d.backend.value.contains("Adreno 810"))
        assertFalse(d.backend.value.contains("fallback"))
        d.begin("request")
        assertTrue(File(d.directory, "inference-config.json").readText().contains("offloadedLayers"))
    }
    @Test fun exportIsAllowlistedBoundedAndContainsCorrelatedEvidence() {
        val d = diagnostic()
        repeat(5) { d.begin("request") }
        d.artifact("prompt.txt", "prompt test")
        d.artifact("planner-snapshot.json", "{\"tasks\":[]}")
        d.artifact("grammar.json", "{}")
        repeat(800) { d.event("TEST", "x".repeat(1000)) }
        File(d.directory, "model.gguf").writeText("must not export")
        val output = ByteArrayOutputStream()
        d.export(output)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertTrue(entries.keys.containsAll(listOf("session.log", "device.json", "model.json", "inference-config.json", "prompt.txt", "planner-snapshot.json", "grammar.json")))
        assertFalse(entries.containsKey("model.gguf"))
        assertTrue(entries.getValue("session.log").contains(d.state.value.id))
        assertTrue(entries.getValue("session.log").toByteArray().size <= 262144)
        assertTrue(d.directory.parentFile!!.listFiles()!!.count { it.isDirectory } <= 3)
    }
}
