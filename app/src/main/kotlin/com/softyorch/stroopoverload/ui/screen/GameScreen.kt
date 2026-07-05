package com.softyorch.stroopoverload.ui.screen

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softyorch.stroopoverload.audio.AudioPlayer
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.domain.GameResult
import com.softyorch.stroopoverload.game.GameState
import com.softyorch.stroopoverload.game.GameViewModel
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
            Column {
                Text("SCORE", style = MaterialTheme.typography.bodySmall, color = Muted, fontSize = 10.sp)
                Text(playingState?.score?.toString() ?: "0", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("STREAK", style = MaterialTheme.typography.bodySmall, color = Muted, fontSize = 10.sp)
                val streak = playingState?.currentStreak ?: 0
                Text("$streak 🔥", style = MaterialTheme.typography.titleMedium, color = if (streak >= 5) NeonYellow else MaterialTheme.colorScheme.onBackground)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ROUND", style = MaterialTheme.typography.bodySmall, color = Muted, fontSize = 10.sp)
                Text(playingState?.totalRounds?.toString() ?: "0", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("LEVEL", style = MaterialTheme.typography.bodySmall, color = Muted, fontSize = 10.sp)
                Text("LVL ${playingState?.level ?: 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
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
                        text = "[ SYNAPSE TARGET // MATCH INK COLOR ]",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                        letterSpacing = 2.sp,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = s.wordLabel.displayName,
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
        Column(
            modifier = Modifier.weight(1.2f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuadrantBox(color = StroopColor.RED, modifier = Modifier.weight(1f).fillMaxHeight()) {
                    viewModel.onColorTapped(StroopColor.RED)
                }
                QuadrantBox(color = StroopColor.GREEN, modifier = Modifier.weight(1f).fillMaxHeight()) {
                    viewModel.onColorTapped(StroopColor.GREEN)
                }
            }
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuadrantBox(color = StroopColor.BLUE, modifier = Modifier.weight(1f).fillMaxHeight()) {
                    viewModel.onColorTapped(StroopColor.BLUE)
                }
                QuadrantBox(color = StroopColor.YELLOW, modifier = Modifier.weight(1f).fillMaxHeight()) {
                    viewModel.onColorTapped(StroopColor.YELLOW)
                }
            }
        }
    }
}

@Composable
private fun QuadrantBox(color: StroopColor, modifier: Modifier, onTap: () -> Unit) {
    val bgAlpha = remember { mutableFloatStateOf(0.15f) }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, color.composeColor.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .background(color.composeColor.copy(alpha = bgAlpha.floatValue))
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = color.displayName,
            color = color.composeColor,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp
        )
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
