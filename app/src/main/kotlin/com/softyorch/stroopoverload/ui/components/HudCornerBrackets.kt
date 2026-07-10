package com.softyorch.stroopoverload.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tactical-HUD corner brackets (four short L-shaped accents, one per corner)
 * drawn on top of whatever this modifier is chained onto -- a "targeting
 * reticle" treatment used across a few signature surfaces (selected mode
 * card, room-code display, match-result frame) instead of a plain rounded
 * card border, per PRODUCT.md's "Arcade legacy, modern execution" /
 * "Tension by design" brand principles: distinctive, not generic Material.
 */
fun Modifier.hudCornerBrackets(
    color: Color,
    length: Dp = 14.dp,
    thickness: Dp = 2.dp,
    inset: Dp = 0.dp,
): Modifier = composed {
    val lengthPx = with(androidx.compose.ui.platform.LocalDensity.current) { length.toPx() }
    val thicknessPx = with(androidx.compose.ui.platform.LocalDensity.current) { thickness.toPx() }
    val insetPx = with(androidx.compose.ui.platform.LocalDensity.current) { inset.toPx() }

    this.drawWithContent {
        drawContent()
        val w = size.width - insetPx * 2
        val h = size.height - insetPx * 2
        val x0 = insetPx
        val y0 = insetPx
        val x1 = insetPx + w
        val y1 = insetPx + h

        fun corner(cx: Float, cy: Float, dx: Int, dy: Int) {
            drawLine(color, Offset(cx, cy), Offset(cx + lengthPx * dx, cy), thicknessPx, cap = StrokeCap.Square)
            drawLine(color, Offset(cx, cy), Offset(cx, cy + lengthPx * dy), thicknessPx, cap = StrokeCap.Square)
        }

        corner(x0, y0, 1, 1)   // top-left
        corner(x1, y0, -1, 1)  // top-right
        corner(x0, y1, 1, -1)  // bottom-left
        corner(x1, y1, -1, -1) // bottom-right
    }
}
