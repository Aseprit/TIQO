package com.aseprit.tiqo.ai.model

import com.aseprit.tiqo.ai.LlmCapability
import com.aseprit.tiqo.ai.LlmDescriptor
import com.aseprit.tiqo.ai.LlmEngine
import com.aseprit.tiqo.ai.LlmEvent
import com.aseprit.tiqo.ai.LlmRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCompatibilityTesterTest {
    @Test fun textualQwenCallsUsePlannerAdapterAndPassSelfTest() = runBlocking {
        val engine = CompatibilityEngine(
            """<tool_call>{"name":"create_item","arguments":{"title":"Dentysta","type":"EVENT","date":"2026-09-05","time":"06:00","all_day":false}}</tool_call>""",
            """<tool_call>{"name":"delegate_to_chat","arguments":{"user_request":"Cześć"}}</tool_call>""",
            """<tool_call>{"name":"request_clarification","arguments":{"question":"Który wpis?","reason":"Brak celu"}}</tool_call>""",
        )

        val state = ModelCompatibilityTester(engine).testPlanner()

        assertEquals(ModelTestStatus.Passed, state.status)
        assertEquals(PlannerCompatibilityStatus.Compatible, state.plannerCompatibility)
        assertTrue(state.diagnostics.all { it.parserPath == "qwen_xml" })
        assertEquals(listOf("create_item", "delegate_to_chat", "request_clarification"), state.diagnostics.map { it.parsedTool })
    }

    @Test fun ordinaryGarbageTextNeverPassesPlannerCompatibility() = runBlocking {
        val state = ModelCompatibilityTester(CompatibilityEngine("Cześć!", "Jasne.", "Nie wiem.")).testPlanner()

        assertEquals(ModelTestStatus.Failed, state.status)
        assertEquals(PlannerCompatibilityStatus.Failed, state.plannerCompatibility)
        assertTrue(state.diagnostics.all { it.parserPath == "none" && it.parsedTool == null })
    }
}

private class CompatibilityEngine(vararg outputs: String) : LlmEngine {
    private val queue = outputs.toMutableList()
    override val descriptor = LlmDescriptor("compat", "compat", setOf(LlmCapability.PlannerToolCalls))
    override val isLoaded = MutableStateFlow(true)
    override suspend fun load(modelPath: String) = Result.success(Unit)
    override fun generate(request: LlmRequest): Flow<LlmEvent> = flow {
        emit(LlmEvent.Token(queue.removeAt(0)))
        emit(LlmEvent.Finished)
    }
    override suspend fun stopGeneration() = Unit
    override suspend fun resetConversation() = Unit
    override suspend fun unload() = Unit
}
