package com.aseprit.tiqo.ai.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelFormatPolicyTest {
    @Test fun acceptsLiteRtLmCaseInsensitively() {
        assertTrue(ModelFormatPolicy.isSupported("planner.litertlm"))
        assertTrue(ModelFormatPolicy.isSupported("CHAT.LITERTLM"))
    }

    @Test fun rejectsArchivesAndOtherRuntimeFormats() {
        assertEquals(ModelFormat.Gguf, ModelFormatPolicy.detect("model.gguf"))
        assertEquals(ModelBackend.LlamaCpp, ModelFormatPolicy.backendFor("model.gguf"))
        assertFalse(ModelFormatPolicy.isSupported("model.gguf")) // recognized, runtime not shipped
        assertFalse(ModelFormatPolicy.isSupported("model.task"))
        assertFalse(ModelFormatPolicy.isSupported("model.litertlm.zip"))
        assertFalse(ModelFormatPolicy.isSupported("model"))
    }

    @Test fun recommendedCatalogHasExplicitUniqueMetadata() {
        val catalog = ModelManager.catalog
        assertEquals(catalog.size, catalog.map { it.id }.distinct().size)
        assertTrue(catalog.all { it.roles.isNotEmpty() })
        assertTrue(catalog.all { it.backend == ModelBackend.LiteRtLm })
        assertTrue(catalog.all { ModelFormatPolicy.isSupported(it.fileName) })
        assertTrue(catalog.filter { it.downloadUrl != null }.all {
            it.expectedSizeBytes != null && it.checksumSha256?.length == 64 &&
                !it.sourceRepository.isNullOrBlank() && it.sourceRevision?.length == 40
        })
        assertFalse(catalog.any { it.id.contains("planner_gemma", ignoreCase = true) })
    }

    @Test fun qwenCandidatesArePinnedToVerifiedOfficialArtifacts() {
        val small = ModelManager.catalog.single { it.id == "qwen3_0_6b_nothink_q4" }
        assertEquals(347_251_840L, small.expectedSizeBytes)
        assertEquals("2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139", small.checksumSha256)
        assertEquals(ModelCatalogTier.SmallExperimental, small.catalogTier)
        assertTrue(ModelRole.Planner in small.roles)

        val chat = ModelManager.catalog.single { it.id == "qwen3_1_7b_dynamic_wi4b32" }
        assertEquals(977_184_032L, chat.expectedSizeBytes)
        assertEquals("2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b", chat.checksumSha256)
        assertEquals(ModelCatalogTier.RecommendedChatCandidate, chat.catalogTier)
        assertEquals(setOf(ModelRole.Chat), chat.roles)
    }
}
