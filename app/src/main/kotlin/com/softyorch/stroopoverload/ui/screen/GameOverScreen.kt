package com.softyorch.stroopoverload.ui.screen

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.softyorch.stroopoverload.domain.GameResult
import com.softyorch.stroopoverload.ui.theme.Muted
import com.softyorch.stroopoverload.ui.theme.NeonGreen
import com.softyorch.stroopoverload.ui.theme.NeonRed
import com.softyorch.stroopoverload.ui.theme.NeonYellow

@Composable
fun GameOverScreen(
    result: GameResult,
    onMenu: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.weight(1f))

        Text(
            text = "GAME OVER",
            style = MaterialTheme.typography.headlineLarge,
            color = NeonRed,
        )

        Spacer(Modifier.height(32.dp))

        StatRow("SCORE", result.finalScore.toString())
        StatRow("ACCURACY", "${result.accuracy}%")
        StatRow("ROUNDS", result.totalRounds.toString())

        if (result.isNewHighScore) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "★  NEW HIGH SCORE  ★",
                style = MaterialTheme.typography.labelLarge,
                color = NeonYellow,
            )
        }

        Spacer(Modifier.weight(1f))

        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "I scored ${result.finalScore} on Stroop Overload! " +
                            "Accuracy: ${result.accuracy}% — Can you beat me? 🧠⚡"
                    )
                }
                context.startActivity(Intent.createChooser(intent, null))
            },
            modifier = Modifier.width(240.dp).height(56.dp),
        ) {
            Text(
                text = "SHARE SCORE",
                style = MaterialTheme.typography.labelLarge,
                color = NeonGreen,
            )
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = onMenu) {
            Text(
                text = "MAIN MENU",
                style = MaterialTheme.typography.labelLarge,
                color = Muted,
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Muted)
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
