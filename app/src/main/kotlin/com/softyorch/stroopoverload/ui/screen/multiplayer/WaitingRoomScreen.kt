package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom

@Composable
fun WaitingRoomScreen(
    room: MultiplayerRoom,
    myUid: String,
    onStartGame: () -> Unit,
) {
    val isHost = room.hostUid == myUid
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("CÓDIGO: ${room.code}", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text("Jugadores (${room.players.size}/4)")
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(room.players) { player ->
                Text("• ${player.displayName}${if (player.uid == room.hostUid) " (host)" else ""}")
            }
        }
        if (isHost) {
            Button(
                onClick = onStartGame,
                enabled = room.players.size in 2..4,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("EMPEZAR PARTIDA") }
        } else {
            Text("Esperando a que el host empiece...")
        }
    }
}
