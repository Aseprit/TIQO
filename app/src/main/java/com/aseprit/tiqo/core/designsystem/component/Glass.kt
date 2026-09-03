package com.aseprit.tiqo.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.aseprit.tiqo.core.designsystem.theme.tiqoColors
import com.aseprit.tiqo.core.designsystem.theme.TiqoGlass
import com.aseprit.tiqo.core.designsystem.theme.TiqoElevation

@Composable
fun TiqoBackground(content: @Composable BoxScope.() -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to accent.copy(alpha = 0.055f),
                        0.32f to MaterialTheme.colorScheme.background,
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
            content = content,
        )
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Surface(
        modifier = modifier
            .clip(shape)
            .border(
                BorderStroke(TiqoGlass.borderWidth, MaterialTheme.colorScheme.outlineVariant),
                shape,
            ),
        shape = shape,
        color = MaterialTheme.tiqoColors.elevatedGlass.copy(alpha = MaterialTheme.tiqoColors.glassAlpha.coerceIn(0.72f, 0.92f)),
        tonalElevation = 0.dp,
        shadowElevation = TiqoElevation.glass,
        content = content,
    )
}
