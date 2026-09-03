package com.aseprit.tiqo.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aseprit.tiqo.core.designsystem.theme.TiqoShapes
import com.aseprit.tiqo.core.designsystem.theme.TiqoSpacing
import com.aseprit.tiqo.core.designsystem.theme.TiqoGlass

@Composable
fun TiqoGlassCard(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    GlassSurface(modifier, cornerRadius = 22.dp) {
        Box(Modifier.fillMaxWidth().padding(TiqoSpacing.md), content = content)
    }
}

@Composable
fun TiqoTaskCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    GlassSurface(modifier, cornerRadius = 20.dp, content = content)
}

@Composable
fun TiqoModelCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    GlassSurface(modifier, cornerRadius = TiqoGlass.cardRadius, content = content)
}

@Composable
fun TiqoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = TiqoShapes.pill,
        colors = ButtonDefaults.buttonColors(),
    ) {
        icon?.let { Icon(it, null, Modifier.size(18.dp)) }
        if (icon != null) androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text(text)
    }
}

@Composable
fun TiqoIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription) }
}

@Composable
fun TiqoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(placeholder) },
        enabled = enabled,
        maxLines = maxLines,
        shape = TiqoShapes.large,
    )
}

@Composable
fun TiqoBottomDock(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    GlassSurface(modifier, cornerRadius = 28.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun <T> TiqoSegmentedControl(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            val active = option == selected
            Surface(
                modifier = Modifier.weight(1f).clickable { onSelected(option) },
                shape = TiqoShapes.pill,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            ) { Text(label(option), Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) }
        }
    }
}

@Composable
fun TiqoWeekDay(
    label: String,
    day: String,
    selected: Boolean,
    today: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = TiqoShapes.medium
    Column(
        modifier = modifier
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0f), shape)
            .then(if (today && !selected) Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary), shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(day, style = MaterialTheme.typography.titleMedium, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
        Box(
            Modifier.size(4.dp).background(
                if (today && !selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                CircleShape,
            ),
        )
    }
}
