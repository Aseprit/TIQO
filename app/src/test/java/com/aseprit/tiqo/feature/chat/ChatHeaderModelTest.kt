package com.aseprit.tiqo.feature.chat

import com.aseprit.tiqo.ai.model.InstalledModelState
import com.aseprit.tiqo.ai.model.ModelBackend
import com.aseprit.tiqo.ai.model.ModelCatalogEntry
import com.aseprit.tiqo.ai.model.ModelRole
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatHeaderModelTest {
    @Test fun unloadedSelectedPlannerDoesNotReplaceLoadedChatModelInHeader() {
        val planner = model("qwen3", "Qwen3 0.6B", ModelRole.Planner, loaded = false)
        val chat = model("qwen2_5", "Qwen2.5 1.5B", ModelRole.Chat, loaded = true)

        val displayed = resolveLoadedModelName(listOf(planner, chat), activeRole = ModelRole.Planner)

        assertEquals("Qwen2.5 1.5B", displayed)
    }

    private fun model(
        id: String,
        displayName: String,
        role: ModelRole,
        loaded: Boolean,
    ) = InstalledModelState(
        catalog = ModelCatalogEntry(
            id = id,
            displayName = displayName,
            description = "test",
            roles = setOf(role),
            backend = ModelBackend.LiteRtLm,
            fileName = "$id.litertlm",
        ),
        installed = true,
        activeRoles = setOf(role),
        loadedRoles = if (loaded) setOf(role) else emptySet(),
    )
}
