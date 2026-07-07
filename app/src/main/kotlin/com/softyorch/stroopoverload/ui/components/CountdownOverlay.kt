package com.softyorch.stroopoverload.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.ui.theme.NeonCyan
import com.softyorch.stroopoverload.ui.theme.NeonGreen
import com.softyorch.stroopoverload.ui.theme.NeonRed
import com.softyorch.stroopoverload.ui.theme.NeonYellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private val CountdownColors = listOf(NeonRed, NeonYellow, NeonCyan, NeonGreen)
private const val CountdownStepMs = 900L
private const val CountdownGoStep = 3

/**
 * Full-screen "3, 2, 1, GO!" overlay shown right before a round becomes interactive.
 * Shared by the local single-player board and the online multiplayer board.
 */
@Composable
fun CountdownOverlay(onFinished: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val pulseAnim = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    val goLabel = stringResource(R.string.countdown_go)
    val readyLabel = stringResource(R.string.countdown_get_ready)

    LaunchedEffect(Unit) {
        for (i in 0..CountdownGoStep) {
            step = i
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            launch {
                pulseAnim.snapTo(0f)
                pulseAnim.animateTo(1f, animationSpec = tween(850, easing = EaseOut))
            }
            delay(CountdownStepMs.milliseconds)
        }
        onFinished()
    }

    val stepColor = CountdownColors.getOrElse(step) { CountdownColors.last() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050508).copy(alpha = 0.94f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size((80 + 260 * pulseAnim.value).dp)
                .alpha(((1f - pulseAnim.value) * 0.65f).coerceIn(0f, 1f))
                .border(width = 5.dp, color = stepColor, shape = CircleShape)
        )
        Box(
            modifier = Modifier
                .size((50 + 180 * pulseAnim.value).dp)
                .alpha(((1f - pulseAnim.value) * 0.35f).coerceIn(0f, 1f))
                .border(width = 2.dp, color = stepColor, shape = CircleShape)
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (targetState == CountdownGoStep) {
                        ContentTransform(
                            targetContentEnter = scaleIn(
                                initialScale = 0.1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            ) + fadeIn(tween(80)),
                            initialContentExit = fadeOut(tween(100)),
                            sizeTransform = null,
                        )
                    } else {
                        ContentTransform(
                            targetContentEnter = scaleIn(
                                initialScale = 3.5f,
                                animationSpec = tween(260, easing = EaseOut),
                            ) + fadeIn(tween(160)),
                            initialContentExit = scaleOut(
                                targetScale = 0.05f,
                                animationSpec = tween(190),
                            ) + fadeOut(tween(130)),
                            sizeTransform = null,
                        )
                    }
                },
                label = "countdown_number",
            ) { s ->
                val color = CountdownColors.getOrElse(s) { CountdownColors.last() }
                val text = when (s) {
                    0 -> "3"
                    1 -> "2"
                    2 -> "1"
                    else -> goLabel
                }
                Text(
                    text = text,
                    fontSize = if (s < CountdownGoStep) 176.sp else 88.sp,
                    fontWeight = FontWeight.Black,
                    color = color,
                    style = LocalTextStyle.current.copy(
                        shadow = Shadow(color = color.copy(alpha = 0.85f), blurRadius = 80f),
                    ),
                )
            }

            Spacer(Modifier.height(20.dp))

            if (step < CountdownGoStep) {
                Text(
                    text = readyLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.45f),
                    letterSpacing = 5.sp,
                )
            }
        }
    }
}
