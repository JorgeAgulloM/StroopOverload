package com.softyorch.stroopoverload.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.audio.AudioPlayer
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.domain.GameMode
import com.softyorch.stroopoverload.domain.GameResult
import com.softyorch.stroopoverload.game.GameState
import com.softyorch.stroopoverload.game.GameViewModel
import com.softyorch.stroopoverload.ui.components.CountdownOverlay
import com.softyorch.stroopoverload.ui.theme.*

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onGameOver: (GameResult) -> Unit,
) {
    val context = LocalContext.current
    val audioPlayer = remember { AudioPlayer(context) }
    DisposableEffect(Unit) { onDispose { audioPlayer.release() } }

    val state by viewModel.state.collectAsState()
    val stimulus by viewModel.stimulus.collectAsState()
    val timerProgress by viewModel.timerProgress.collectAsState()

    LaunchedEffect(stimulus) {
        stimulus?.audioColor?.let { audioPlayer.play(it) }
    }

    val playingState = state as? GameState.Playing

    when (val s = state) {
        is GameState.GameOver -> {
            LaunchedEffect(s) { onGameOver(s.result) }
        }
        else -> Unit
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Top Live Telemetry HUD
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.game_hud_score), style = MaterialTheme.typography.bodySmall, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(playingState?.score?.toString() ?: "0", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (playingState?.mode == GameMode.LIVES) {
                        Text(stringResource(R.string.game_hud_lives), style = MaterialTheme.typography.bodySmall, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        AnimatedContent(
                            targetState = playingState.livesRemaining,
                            transitionSpec = {
                                (scaleIn(initialScale = 1.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.4f) + fadeOut())
                            },
                            label = "lives",
                        ) { lives ->
                            Text("$lives ❤️", style = MaterialTheme.typography.titleMedium, color = if (lives <= 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground)
                        }
                    } else {
                        Text(stringResource(R.string.game_hud_streak), style = MaterialTheme.typography.bodySmall, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val streak = playingState?.currentStreak ?: 0
                        Text("$streak 🔥", style = MaterialTheme.typography.titleMedium, color = if (streak >= 5) NeonYellow else MaterialTheme.colorScheme.onBackground)
                    }
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (playingState?.mode == GameMode.TIME) {
                        Text(stringResource(R.string.game_hud_time), style = MaterialTheme.typography.bodySmall, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatMillisAsClock(playingState.timeRemainingMs), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    } else {
                        Text(stringResource(R.string.game_hud_round), style = MaterialTheme.typography.bodySmall, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(playingState?.totalRounds?.toString() ?: "0", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.game_hud_level), style = MaterialTheme.typography.bodySmall, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.game_hud_level_value, playingState?.level ?: 1), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cyber Timer Gauge
            TimerBar(
                progress = timerProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Central Neural Word Terminal
            Box(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxWidth()
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                stimulus?.let { s ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.game_stimulus_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                            letterSpacing = 2.sp,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(s.wordLabel.displayNameRes),
                            color = s.inkColor.composeColor,
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Cyber Quadrant Pad Grid
            val missFlashColor = playingState?.missFlashColor
            Column(
                modifier = Modifier.weight(1.2f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuadrantBox(color = StroopColor.RED, isFlashing = missFlashColor == StroopColor.RED, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        viewModel.onColorTapped(StroopColor.RED)
                    }
                    QuadrantBox(color = StroopColor.GREEN, isFlashing = missFlashColor == StroopColor.GREEN, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        viewModel.onColorTapped(StroopColor.GREEN)
                    }
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuadrantBox(color = StroopColor.BLUE, isFlashing = missFlashColor == StroopColor.BLUE, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        viewModel.onColorTapped(StroopColor.BLUE)
                    }
                    QuadrantBox(color = StroopColor.YELLOW, isFlashing = missFlashColor == StroopColor.YELLOW, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        viewModel.onColorTapped(StroopColor.YELLOW)
                    }
                }
            }
        }

        if (state is GameState.Countdown) {
            CountdownOverlay(onFinished = { viewModel.beginRound() })
        }
    }
}

private fun formatMillisAsClock(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun QuadrantBox(color: StroopColor, isFlashing: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val bgAlpha = remember { mutableFloatStateOf(0.15f) }
    val flashAlpha by animateFloatAsState(
        targetValue = if (isFlashing) 0.85f else 0f,
        animationSpec = tween(if (isFlashing) 120 else 400),
        label = "quadrantFlash",
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, color.composeColor.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .background(color.composeColor.copy(alpha = bgAlpha.floatValue))
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(color.displayNameRes),
            color = color.composeColor,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (flashAlpha > 0f) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha)))
        }
    }
}

@Composable
private fun TimerBar(progress: Float, modifier: Modifier) {
    val barColor = lerp(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.primary, progress)
    val animatedColor by animateColorAsState(targetValue = barColor, label = "timerColor")

    Box(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize().background(CyberDark))
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                .background(animatedColor)
        )
    }
}
