package com.softyorch.stroopoverload.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Largest system font scale the app's layouts follow. The HUD type is already large; past this
 * the home buttons, mode cards and game-over rows cut or break their labels. Larger settings are
 * clamped here instead of ignored, so players who raised it still get bigger text.
 */
const val APP_MAX_FONT_SCALE = 1.3f

/**
 * The game board (colour word, answer quadrants, HUD) ignores the system font scale: its type is
 * oversized on purpose and the task is about colour, not reading, while scaled labels cut to
 * "AMAR…" in the quadrants.
 */
const val GAME_BOARD_FONT_SCALE = 1f

/** Runs [content] with the system font scale capped at [max]. */
@Composable
fun LimitFontScale(max: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(max)),
        content = content,
    )
}
