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
        assertFalse(ModelFormatPolicy.isSupported("model.gguf"))
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
    }
}
