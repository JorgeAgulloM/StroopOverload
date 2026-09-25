package com.softyorch.stroopoverload.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = NeonCyan,
    secondary = NeonMagenta,
    tertiary = NeonGreen,
    error = NeonRed,
    background = Background,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    onBackground = OnBackground,
    onSurface = OnSurface,
    onPrimary = Background,
    outline = TechBorder,
)

@Composable
fun StroopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = StroopTypography,
    ) {
        LimitFontScale(max = APP_MAX_FONT_SCALE, content = content)
    }
}
