package com.softyorch.stroopoverload.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SweepGradientShader
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

private const val STROBE_TURN_MS = 2_400
private const val GLOW_WIDTH_FACTOR = 4f
private const val GLOW_ALPHA = 0.35f

/** The strobe's current angle in degrees, one turn every [STROBE_TURN_MS], for [strobeBorder]. */
@Composable
fun rememberStrobeAngle(): () -> Float {
    val transition = rememberInfiniteTransition(label = "strobe")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(STROBE_TURN_MS, easing = LinearEasing)),
        label = "strobeAngle",
    )
    return { angle }
}

/**
 * A rounded border with a light running round it: a dim [baseBrush] outline plus a bright
 * comet in [lightColor] with a soft glow, drawn over the content like Modifier.border. The
 * comet is a sweep gradient turned by [angle], read in the draw phase, so the spin only
 * redraws -- it never recomposes.
 */
fun Modifier.strobeBorder(
    width: Dp,
    cornerRadius: Dp,
    baseBrush: Brush,
    lightColor: Color,
    angle: () -> Float,
): Modifier = drawWithCache {
    val strokePx = width.toPx()
    val radius = CornerRadius(cornerRadius.toPx())
    val center = Offset(size.width / 2f, size.height / 2f)
    // Transparent most of the way round, then a tail brightening into a white-hot head.
    val shader = SweepGradientShader(
        center = center,
        colors = listOf(Color.Transparent, Color.Transparent, lightColor, Color.White, Color.Transparent),
        colorStops = listOf(0f, 0.6f, 0.9f, 0.97f, 1f),
    )
    val lightBrush = ShaderBrush(shader)
    val rotation = android.graphics.Matrix()
    val inset = strokePx / 2f
    val topLeft = Offset(inset, inset)
    val rectSize = size.copy(width = size.width - strokePx, height = size.height - strokePx)
    // On top of the content, like Modifier.border: the card's translucent background
    // would otherwise dim it.
    onDrawWithContent {
        drawContent()
        drawRoundRect(baseBrush, topLeft, rectSize, radius, style = Stroke(strokePx))
        rotation.setRotate(angle(), center.x, center.y)
        shader.setLocalMatrix(rotation)
        drawRoundRect(
            lightBrush, topLeft, rectSize, radius,
            alpha = GLOW_ALPHA, style = Stroke(strokePx * GLOW_WIDTH_FACTOR),
        )
        drawRoundRect(lightBrush, topLeft, rectSize, radius, style = Stroke(strokePx))
    }
}
