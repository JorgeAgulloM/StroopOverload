package com.softyorch.stroopoverload.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softyorch.stroopoverload.core.StroopColor
import androidx.compose.ui.tooling.preview.Preview
import com.softyorch.stroopoverload.ui.theme.StroopTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle

/**
 * One answer quadrant of the 2x2 board, shared by local play and every online
 * mode. Flashes white on a miss; dims and stops taking taps while [enabled] is
 * false (someone else holds the turn).
 */
@Composable
fun QuadrantBox(
    color: StroopColor,
    enabled: Boolean = true,
    isFlashing: Boolean = false,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val neon = color.composeColor
    val shape = RoundedCornerShape(14.dp)
    // ── Flash on miss ──
    val flashAlpha by animateFloatAsState(
        targetValue = if (isFlashing) 0.85f else 0f,
        animationSpec = tween(if (isFlashing) 100 else 350),
        label = "quadrantFlash",
    )
    // ── Subtle neon pulse (only when enabled) ──
    val infiniteTransition = rememberInfiniteTransition(label = "neonPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    val borderAlpha = if (enabled) pulseAlpha else 0.15f
    // ── Press scale ──
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = tween(80),
        label = "pressScale",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            // Outer neon glow border (pulsating)
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        neon.copy(alpha = borderAlpha),
                        neon.copy(alpha = borderAlpha * 0.4f),
                        neon.copy(alpha = borderAlpha),
                    )
                ),
                shape = shape,
            )
            // Background: deep dark with subtle radial glow from center
            .drawBehind {
                // Dark base
                drawRect(Color(0xFF08080E))
                // Radial neon glow at center
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            neon.copy(alpha = if (enabled) 0.18f else 0.04f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.minDimension * 0.7f,
                    )
                )
                // Subtle top highlight strip
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            neon.copy(alpha = if (enabled) 0.12f else 0.02f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height * 0.3f,
                    )
                )
            }
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { onTap() },
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // ── Neon text label ──
        Text(
            text = stringResource(color.displayNameRes),
            color = neon.copy(alpha = if (enabled) 1f else 0.35f),
            style = TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                shadow = if (enabled) {
                    Shadow(
                        color = neon.copy(alpha = 0.8f),
                        blurRadius = 20f,
                    )
                } else null,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // ── Miss flash overlay (tinted with neon for cohesion) ──
        if (flashAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = flashAlpha),
                                neon.copy(alpha = flashAlpha * 0.5f),
                            )
                        )
                    )
            )
        }
    }
}
@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
private fun QuadrantBoxPreview() {
    StroopTheme {
        Row(
            modifier = Modifier.padding(8.dp).height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuadrantBox(StroopColor.RED, modifier = Modifier.weight(1f).fillMaxHeight()) {}
            QuadrantBox(StroopColor.BLUE, isFlashing = true, modifier = Modifier.weight(1f).fillMaxHeight()) {}
            QuadrantBox(StroopColor.GREEN, enabled = false, modifier = Modifier.weight(1f).fillMaxHeight()) {}
        }
    }
}
