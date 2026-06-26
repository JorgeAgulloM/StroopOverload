package com.softyorch.stroopoverload.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = NeonGreen,
    secondary = NeonBlue,
    error = NeonRed,
    background = Background,
    surface = Surface,
    onBackground = OnBackground,
    onSurface = OnBackground,
    onPrimary = Background,
)

@Composable
fun StroopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = StroopTypography,
        content = content,
    )
}
