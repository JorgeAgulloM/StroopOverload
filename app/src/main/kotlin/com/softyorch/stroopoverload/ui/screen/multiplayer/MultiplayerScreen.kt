package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.softyorch.stroopoverload.domain.multiplayer.RoomStatus
import com.softyorch.stroopoverload.ui.components.CountdownOverlay

@Composable
fun MultiplayerScreen(myUid: String) {
    val viewModel: MultiplayerViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    when (val current = state) {
        is MultiplayerUiState.Idle -> LobbyScreen(
            onCreateRoom = { name -> viewModel.createRoom(myUid, name) },
            onJoinRoom = { code, name -> viewModel.joinRoom(myUid, code, name) },
            errorReason = null,
            isConnecting = false,
        )
        is MultiplayerUiState.Connecting -> LobbyScreen(
            onCreateRoom = { name -> viewModel.createRoom(myUid, name) },
            onJoinRoom = { code, name -> viewModel.joinRoom(myUid, code, name) },
            errorReason = null,
            isConnecting = true,
        )
        is MultiplayerUiState.Error -> LobbyScreen(
            onCreateRoom = { name -> viewModel.createRoom(myUid, name) },
            onJoinRoom = { code, name -> viewModel.joinRoom(myUid, code, name) },
            errorReason = current.reason,
            isConnecting = false,
        )
        is MultiplayerUiState.InRoom -> when (current.room.status) {
            RoomStatus.WAITING -> WaitingRoomScreen(
                room = current.room,
                myUid = myUid,
                onStartGame = { viewModel.startGame() },
            )
            // "starting" is purely a server-driven countdown window: round 1's real
            // stimulus/deadline don't exist yet (see beginRound in the Cloud Functions).
            // The board only becomes interactive once Firestore actually reports
            // "playing", so every client gets the full answer window regardless of how
            // long their own countdown animation/render took -- no more racing a
            // deadline that started ticking before they could see the board.
            RoomStatus.STARTING -> MultiplayerStartingScreen()
            RoomStatus.PLAYING, RoomStatus.FINISHED -> MultiplayerGameScreen(
                room = current.room,
                myUid = myUid,
                onColorTapped = { viewModel.submitAnswer(it) },
            )
        }
    }
}

@Composable
private fun MultiplayerStartingScreen() {
    var countdownFinished by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!countdownFinished) {
            CountdownOverlay(onFinished = { countdownFinished = true })
        } else {
            // The countdown animation is purely cosmetic and can finish slightly
            // before/after the server's beginRound task actually flips the room to
            // "playing" -- bridge that small gap instead of showing a blank screen.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
