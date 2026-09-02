package com.aseprit.tiqo.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val NightBackground = Color(0xFF09090E)
internal val NightSurface = Color(0xFF16161E)
internal val NightElevatedSurface = Color(0xFF20202A)
internal val NightPrimaryText = Color(0xFFF8F7FC)
internal val NightSecondaryText = Color(0xFFC9C6D2)
internal val NightMutedText = Color(0xFF9995A4)
internal val NightDisabledText = Color(0xFF77737F)

internal val DayBackground = Color(0xFFF4F3F8)
internal val DaySurface = Color(0xFFFDFBFF)
internal val DayElevatedSurface = Color(0xFFFFFFFF)
internal val DayPrimaryText = Color(0xFF19171F)
internal val DaySecondaryText = Color(0xFF4D4954)
internal val DayMutedText = Color(0xFF706B78)
internal val DayDisabledText = Color(0xFF8F8996)

internal val DestructiveDark = Color(0xFFFF7784)
internal val DestructiveLight = Color(0xFFB4233C)
internal val SuccessDark = Color(0xFF62D99A)
internal val SuccessLight = Color(0xFF087A45)

@Immutable
data class TiqoSemanticColors(
    val primaryText: Color,
    val secondaryText: Color,
    val mutedText: Color,
    val disabledText: Color,
    val background: Color,
    val elevatedGlass: Color,
    val outline: Color,
    val accent: Color,
    val destructive: Color,
    val success: Color,
    val glassAlpha: Float,
)

internal val LocalTiqoSemanticColors = staticCompositionLocalOf {
    TiqoSemanticColors(
        primaryText = NightPrimaryText,
        secondaryText = NightSecondaryText,
        mutedText = NightMutedText,
        disabledText = NightDisabledText,
        background = NightBackground,
        elevatedGlass = NightElevatedSurface,
        outline = Color.White.copy(alpha = 0.16f),
        accent = Color(0xFF8B72FF),
        destructive = DestructiveDark,
        success = SuccessDark,
        glassAlpha = 0.74f,
    )
}
