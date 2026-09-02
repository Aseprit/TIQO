package com.aseprit.tiqo.feature.chat

import androidx.compose.runtime.Composable
import com.aseprit.tiqo.core.designsystem.component.FeaturePlaceholder

@Composable
fun ChatScreen() {
    FeaturePlaceholder(
        eyebrow = "LOCAL AI",
        title = "Planner, który rozumie polecenia",
        description = "Warstwa modeli jest już oddzielona interfejsem LlmEngine. Router i LiteRT-LM wejdą dopiero po ustabilizowaniu danych i tasków.",
    )
}

