package com.softyorch.stroopoverload.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.domain.GameResult
import com.softyorch.stroopoverload.game.GameState
import com.softyorch.stroopoverload.game.GameViewModel
import com.softyorch.stroopoverload.ui.theme.NeonGreen
import com.softyorch.stroopoverload.ui.theme.NeonRed

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onGameOver: (GameResult) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val stimulus by viewModel.stimulus.collectAsState()
    val timerProgress by viewModel.timerProgress.collectAsState()

    when (val s = state) {
        is GameState.GameOver -> onGameOver(s.result)
        else -> Unit
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TimerBar(
            progress = timerProgress,
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // 4-quadrant tap targets
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    QuadrantBox(color = StroopColor.RED, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        viewModel.onColorTapped(StroopColor.RED)
                    }
                    QuadrantBox(color = StroopColor.GREEN, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        viewModel.onColorTapped(StroopColor.GREEN)
                    }
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    QuadrantBox(color = StroopColor.BLUE, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        viewModel.onColorTapped(StroopColor.BLUE)
                    }
                    QuadrantBox(color = StroopColor.YELLOW, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        viewModel.onColorTapped(StroopColor.YELLOW)
                    }
                }
            }
            // Centered stimulus word
            stimulus?.let { s ->
                Text(
                    text = s.wordLabel.displayName,
                    color = s.inkColor.composeColor,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                    modifier = Modifier.align(Alignment.Center).wrapContentSize(),
                )
            }
        }
    }
}

@Composable
private fun QuadrantBox(color: StroopColor, modifier: Modifier, onTap: () -> Unit) {
    Box(
        modifier = modifier
            .background(color.composeColor.copy(alpha = 0.15f))
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = color.displayName,
            color = color.composeColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TimerBar(progress: Float, modifier: Modifier) {
    val barColor = lerp(NeonRed, NeonGreen, progress)
    Box(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)))
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                .background(barColor)
        )
    }
}
