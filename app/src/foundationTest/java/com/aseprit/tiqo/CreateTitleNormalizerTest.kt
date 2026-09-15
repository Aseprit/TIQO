package com.aseprit.tiqo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CreateTitleNormalizerTest {
    @Test fun inferenceReceivesOnlyTaskTextAndTitleOnlyGrammar() = runTest {
        val fake = RecordingInference()
        val result = CreateTitleNormalizer(fake).normalize("wizytę u fryzjera")
        assertEquals("""{"title":"Wizyta u fryzjera"}""", result)
        assertTrue(fake.system.contains("tytuł", ignoreCase = true))
        assertFalse(fake.system.contains("CREATE_TASK"))
        assertFalse(fake.prompt.contains("jutro"))
        assertFalse(fake.prompt.contains("Room"))
        assertTrue(fake.prompt.contains("wizytę u fryzjera"))
        assertTrue(fake.grammar.contains("\\\"title\\\""))
        assertFalse(fake.grammar.contains("actions"))
        assertFalse(fake.grammar.contains("date"))
    }

    private class RecordingInference : LocalInference {
        lateinit var system: String
        lateinit var prompt: String
        lateinit var grammar: String
        override suspend fun load(path: String) = Unit
        override fun generate(system: String, prompt: String, grammar: String): Flow<String> {
            this.system = system
            this.prompt = prompt
            this.grammar = grammar
            return flowOf("{\"title\":", "\"Wizyta u fryzjera\"}")
        }
        override fun stop() = Unit
        override suspend fun unload() = Unit
    }
}
