package com.aseprit.tiqo.core.designsystem.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = if (accent.luminance() > 0.22f) Color(0xFF111018) else Color.White,
    primaryContainer = Color(0xFF302A4A),
    onPrimaryContainer = Color(0xFFECE7FF),
    secondary = accent,
    onSecondary = Color(0xFF0E0B18),
    secondaryContainer = Color(0xFF282437),
    onSecondaryContainer = NightPrimaryText,
    tertiary = SuccessDark,
    onTertiary = Color(0xFF032114),
    background = NightBackground,
    onBackground = NightPrimaryText,
    surface = NightSurface,
    onSurface = NightPrimaryText,
    surfaceVariant = NightElevatedSurface,
    onSurfaceVariant = NightSecondaryText,
    outline = Color.White.copy(alpha = 0.22f),
    outlineVariant = Color.White.copy(alpha = 0.11f),
    error = DestructiveDark,
    onError = Color(0xFF2A050B),
    errorContainer = Color(0xFF4B1820),
    onErrorContainer = Color(0xFFFFDADF),
    inverseSurface = NightPrimaryText,
    inverseOnSurface = NightBackground,
    inversePrimary = Color(0xFF6348D4),
    scrim = Color.Black,
)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = if (accent.luminance() > 0.22f) Color(0xFF111018) else Color.White,
    primaryContainer = Color(0xFFE7E0FF),
    onPrimaryContainer = Color(0xFF211552),
    secondary = accent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECE8F5),
    onSecondaryContainer = DayPrimaryText,
    tertiary = SuccessLight,
    onTertiary = Color.White,
    background = DayBackground,
    onBackground = DayPrimaryText,
    surface = DaySurface,
    onSurface = DayPrimaryText,
    surfaceVariant = Color(0xFFE9E7EF),
    onSurfaceVariant = DaySecondaryText,
    outline = Color.Black.copy(alpha = 0.24f),
    outlineVariant = Color.Black.copy(alpha = 0.11f),
    error = DestructiveLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFD9DE),
    onErrorContainer = Color(0xFF40000A),
    inverseSurface = DayPrimaryText,
    inverseOnSurface = DaySurface,
    inversePrimary = Color(0xFFBFAFFF),
    scrim = Color.Black,
)

private val LocalTiqoAnimationsEnabled = staticCompositionLocalOf { true }

@Composable
fun TiqoTheme(
    darkTheme: Boolean,
    accent: Color,
    glassIntensity: Float = 0.74f,
    animationsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) darkScheme(accent) else lightScheme(accent)
    val semanticColors = if (darkTheme) {
        TiqoSemanticColors(
            NightPrimaryText, NightSecondaryText, NightMutedText, NightDisabledText,
            NightBackground, NightElevatedSurface, colorScheme.outline, accent,
            DestructiveDark, SuccessDark, glassIntensity,
        )
    } else {
        TiqoSemanticColors(
            DayPrimaryText, DaySecondaryText, DayMutedText, DayDisabledText,
            DayBackground, DayElevatedSurface, colorScheme.outline, accent,
            DestructiveLight, SuccessLight, glassIntensity,
        )
    }
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalTiqoSemanticColors provides semanticColors,
        LocalTiqoAnimationsEnabled provides animationsEnabled,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TiqoTypography,
            shapes = TiqoMaterialShapes,
            content = content,
        )
    }
}

val MaterialTheme.tiqoColors: TiqoSemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalTiqoSemanticColors.current

val MaterialTheme.tiqoAnimationsEnabled: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalTiqoAnimationsEnabled.current
