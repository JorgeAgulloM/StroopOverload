package com.softyorch.stroopoverload.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softyorch.stroopoverload.ui.theme.Muted
import com.softyorch.stroopoverload.ui.theme.NeonGreen

@Composable
fun HomeScreen(
    onPlay: () -> Unit,
    onLeaderboard: () -> Unit,
    isReady: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "STROOP",
            style = MaterialTheme.typography.displayLarge,
            color = NeonGreen,
        )
        Text(
            text = "OVERLOAD",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(64.dp))
        Button(
            onClick = onPlay,
            enabled = isReady,
            modifier = Modifier.width(240.dp).height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
        ) {
            Text(
                text = if (isReady) "PLAY" else "LOADING...",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.background,
            )
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onLeaderboard) {
            Text(
                text = "LEADERBOARD",
                style = MaterialTheme.typography.labelLarge,
                color = Muted,
            )
        }
    }
}
