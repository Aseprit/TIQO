package com.aseprit.tiqo.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object TiqoSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object TiqoShapes {
    val small = RoundedCornerShape(12.dp)
    val medium = RoundedCornerShape(18.dp)
    val large = RoundedCornerShape(24.dp)
    val pill = RoundedCornerShape(50)
}

object TiqoElevation {
    val glass = 3.dp
    val floating = 8.dp
}

object TiqoGlass {
    val borderWidth = 1.dp
    val cardRadius = 22.dp
}

internal val TiqoMaterialShapes = Shapes(
    small = TiqoShapes.small,
    medium = TiqoShapes.medium,
    large = TiqoShapes.large,
)

object TiqoMotion {
    const val quickMillis = 140
    const val standardMillis = 220
}
