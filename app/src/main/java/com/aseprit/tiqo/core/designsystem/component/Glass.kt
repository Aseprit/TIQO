package com.aseprit.tiqo.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.aseprit.tiqo.core.designsystem.theme.tiqoColors

@Composable
fun TiqoBackground(content: @Composable BoxScope.() -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                        accent.copy(alpha = 0.08f),
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 88.dp, y = (-72).dp)
                .size(240.dp)
                .blur(64.dp)
                .background(accent.copy(alpha = 0.18f), CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-120).dp)
                .size(220.dp)
                .blur(72.dp)
                .background(Color(0xFF35C4D8).copy(alpha = 0.08f), CircleShape),
        )
        content()
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
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape,
            ),
        shape = shape,
        color = MaterialTheme.tiqoColors.elevatedGlass.copy(alpha = MaterialTheme.tiqoColors.glassAlpha),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        content = content,
    )
}
