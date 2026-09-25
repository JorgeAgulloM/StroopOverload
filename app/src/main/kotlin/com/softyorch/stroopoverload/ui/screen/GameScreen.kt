package com.softyorch.stroopoverload.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softyorch.stroopoverload.BuildConfig
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.ads.NativeAdBanner
import com.softyorch.stroopoverload.audio.AudioPlayer
import com.softyorch.stroopoverload.audio.GameSfx
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.domain.GameMode
import com.softyorch.stroopoverload.domain.GameResult
import com.softyorch.stroopoverload.game.GameState
import com.softyorch.stroopoverload.game.GameViewModel
import com.softyorch.stroopoverload.ui.components.StimulusWord
import com.softyorch.stroopoverload.ui.components.CountdownOverlay
import com.softyorch.stroopoverload.ui.components.QuadrantBox
import com.softyorch.stroopoverload.ui.theme.*
import com.softyorch.stroopoverload.ui.components.TimerBarHost
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import com.softyorch.stroopoverload.ui.components.ExitMatchDialog

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    /** Called once per finished run with its result and the streak it ended on. */
    onGameOver: (result: GameResult, endStreak: Int) -> Unit,
    onLeaveMatch: () -> Unit,
    isAdFree: Boolean = false,
) {
    val context = LocalContext.current
    val audioPlayer = remember { AudioPlayer(context) }
    DisposableEffect(Unit) { onDispose { audioPlayer.release() } }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val stimulus by viewModel.stimulus.collectAsStateWithLifecycle()

    LaunchedEffect(stimulus) {
        stimulus?.audioColor?.let { audioPlayer.play(it) }
    }

    val playingState = state as? GameState.Playing

    // Back during a live run used to abandon it silently: no score recorded, no
    // warning. Only guarded while actually playing -- menus and the game-over
    // screen keep the normal back behaviour.
    var showLeaveConfirmation by remember { mutableStateOf(false) }
    BackHandler(enabled = playingState != null) { showLeaveConfirmation = true }
    if (showLeaveConfirmation) {
        ExitMatchDialog(
            messageRes = R.string.exit_match_local_message,
            onConfirm = {
                showLeaveConfirmation = false
                onLeaveMatch()
            },
            onDismiss = { showLeaveConfirmation = false },
        )
    }

    // correctHits/missFlashColor only ever change on their respective event
    // (monotonic increment / flash-then-clear), so keying LaunchedEffect on
    // them fires the matching SFX exactly once per event -- including misses
    // caused by a timeout, not just a wrong tap, since both go through the
    // same ViewModel state transition.
    LaunchedEffect(playingState?.correctHits) {
        if ((playingState?.correctHits ?: 0) > 0) audioPlayer.play(GameSfx.TAP_CORRECT)
    }
    LaunchedEffect(playingState?.missFlashColor) {
        if (playingState?.missFlashColor != null) audioPlayer.play(GameSfx.TAP_WRONG)
    }

    when (val s = state) {
        is GameState.GameOver -> {
            LaunchedEffect(s) {
                if (!viewModel.claimGameOver()) return@LaunchedEffect
                audioPlayer.play(if (s.result.won) GameSfx.MATCH_WIN else GameSfx.MATCH_LOSE)
                onGameOver(s.result, s.endStreak)
            }
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
            TimerBarHost(
                progress = viewModel.timerProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
                trackColor = CyberDark,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Central Neural Word Terminal
            Box(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxWidth()
                    .border(
                        width = 2.dp, 
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                NeonYellow.copy(alpha = 0.4f), 
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), 
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                            )
                        ), 
                        shape = RoundedCornerShape(16.dp)
                    )
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Decoración de esquinas Ciberpunk (HUD Brackets)
                Box(Modifier.fillMaxSize()) {
                    val cornerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val strokeWidth = 3.dp.toPx()
                        val length = 24.dp.toPx()
                        // Top-Left
                        drawLine(cornerColor, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(length, 0f), strokeWidth)
                        drawLine(cornerColor, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, length), strokeWidth)
                        // Top-Right
                        drawLine(cornerColor, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width - length, 0f), strokeWidth)
                        drawLine(cornerColor, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width, length), strokeWidth)
                        // Bottom-Left
                        drawLine(cornerColor, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(length, size.height), strokeWidth)
                        drawLine(cornerColor, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(0f, size.height - length), strokeWidth)
                        // Bottom-Right
                        drawLine(cornerColor, androidx.compose.ui.geometry.Offset(size.width, size.height), androidx.compose.ui.geometry.Offset(size.width - length, size.height), strokeWidth)
                        drawLine(cornerColor, androidx.compose.ui.geometry.Offset(size.width, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height - length), strokeWidth)
                    }
                }

                stimulus?.let { s ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Cartel holográfico de pista (Hint)
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    RoundedCornerShape(4.dp)
                                )
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.game_stimulus_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 3.sp,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(28.dp))
                        
                        StimulusWord(
                            text = stringResource(s.wordLabel.displayNameRes),
                            color = s.inkColor.composeColor,
                            maxFontSize = 64.sp,
                            letterSpacing = 8.sp,
                            glow = true,
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

            Spacer(modifier = Modifier.height(8.dp))

            NativeAdBanner(
                adUnitId = BuildConfig.AD_UNIT_NATIVE_GAME,
                isAdFree = isAdFree,
                modifier = Modifier.fillMaxWidth().height(64.dp),
            )
        }

        if (state is GameState.Countdown) {
            CountdownOverlay(onFinished = {
                audioPlayer.play(GameSfx.MATCH_START)
                viewModel.beginRound()
            })
        }
    }
}

private fun formatMillisAsClock(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
