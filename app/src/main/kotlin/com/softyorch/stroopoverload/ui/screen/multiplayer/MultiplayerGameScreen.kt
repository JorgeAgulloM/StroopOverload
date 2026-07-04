package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom
import com.softyorch.stroopoverload.domain.multiplayer.RoomStatus

@Composable
fun MultiplayerGameScreen(
    room: MultiplayerRoom,
    myUid: String,
    onColorTapped: (StroopColor) -> Unit,
) {
    val myTurn = room.isMyTurn(myUid)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            room.players.forEach { player ->
                val isTurn = player.uid == room.currentTurnUid
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isTurn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .padding(8.dp)
                ) {
                    Text(player.displayName, style = MaterialTheme.typography.labelMedium)
                    Text(if (player.alive) "VIVO" else "ELIMINADO", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        when (room.status) {
            RoomStatus.PLAYING -> {
                val stimulus = room.stimulus
                if (stimulus != null) {
                    Text(
                        text = stimulus.wordLabel.displayName,
                        style = MaterialTheme.typography.displayMedium,
                        color = stimulus.inkColor.composeColor,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(32.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        stimulus.options.forEach { option ->
                            Button(
                                onClick = { onColorTapped(option) },
                                enabled = myTurn,
                                colors = ButtonDefaults.buttonColors(containerColor = option.composeColor),
                                modifier = Modifier.heightIn(min = 44.dp),
                            ) { Text(option.displayName, color = Color.Black) }
                        }
                    }
                    if (!myTurn) {
                        Spacer(Modifier.height(16.dp))
                        Text("Turno de ${room.players.firstOrNull { it.uid == room.currentTurnUid }?.displayName ?: "..."}")
                    }
                } else {
                    Text(
                        text = "Preparando ronda...",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
            RoomStatus.FINISHED -> {
                val winner = room.players.firstOrNull { it.uid == room.winnerUid }
                Text(
                    text = if (room.winnerUid == myUid) "¡GANASTE!" else "Ganó ${winner?.displayName ?: "?"}",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            RoomStatus.WAITING -> Text("Esperando...")
        }

        Spacer(Modifier.weight(1f))
    }
}
