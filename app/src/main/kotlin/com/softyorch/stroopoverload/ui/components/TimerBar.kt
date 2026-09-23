package com.softyorch.stroopoverload.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/** How often a deadline-driven bar re-renders. Fine for a bar a few hundred pixels wide. */
private const val TICK_MS = 100L

/**
 * Countdown bar, drawn red at zero and shading toward the primary colour as time
 * remains. Was duplicated in GameScreen and MultiplayerGameScreen, already drifting.
 */
@Composable
fun TimerBar(progress: Float, modifier: Modifier, trackColor: Color) {
    val barColor = lerp(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.primary, progress)
    val animatedColor by animateColorAsState(targetValue = barColor, label = "timerColor")

    Box(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize().background(trackColor))
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                .background(animatedColor)
        )
    }
}

/**
 * Collects the progress flow itself, instead of the screen doing it.
 *
 * The local game's timer emits every ~16ms. Read at the top of GameScreen, each
 * emission re-ran the whole screen's composable body -- HUD, stimulus, the
 * quadrant grid, the ad slot -- about sixty times a second, to move one bar.
 * Reading it here keeps that work inside this composable.
 */
@Composable
fun TimerBarHost(progress: StateFlow<Float>, modifier: Modifier, trackColor: Color) {
    val value by progress.collectAsStateWithLifecycle()
    TimerBar(progress = value, modifier = modifier, trackColor = trackColor)
}

/**
 * Counts down to an absolute server deadline, ticking on its own clock.
 *
 * Same reasoning as [TimerBarHost]: the online screens used to hold the ticking
 * "now" themselves, so every tick recomposed the entire match screen -- board,
 * roster and all -- to redraw this bar.
 */
@Composable
fun DeadlineTimerBar(deadlineAtMs: Long?, totalMs: Long, modifier: Modifier, trackColor: Color) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedTicker(enabled = deadlineAtMs != null) { nowMs = it }

    val progress = if (deadlineAtMs == null || totalMs <= 0L) {
        0f
    } else {
        ((deadlineAtMs - nowMs).toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    }
    TimerBar(progress = progress, modifier = modifier, trackColor = trackColor)
}

@Composable
private fun LaunchedTicker(enabled: Boolean, onTick: (Long) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            delay(TICK_MS)
            onTick(System.currentTimeMillis())
        }
    }
}
