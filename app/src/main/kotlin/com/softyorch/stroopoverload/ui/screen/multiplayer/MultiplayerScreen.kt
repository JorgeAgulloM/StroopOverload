package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        is MultiplayerUiState.InRoom -> {
            var previousStatus by remember { mutableStateOf(current.room.status) }
            var showCountdown by remember { mutableStateOf(false) }
            LaunchedEffect(current.room.status) {
                if (previousStatus == RoomStatus.WAITING && current.room.status == RoomStatus.PLAYING) {
                    showCountdown = true
                }
                previousStatus = current.room.status
            }

            when (current.room.status) {
                RoomStatus.WAITING -> WaitingRoomScreen(
                    room = current.room,
                    myUid = myUid,
                    onStartGame = { viewModel.startGame() },
                )
                RoomStatus.PLAYING, RoomStatus.FINISHED -> Box(modifier = Modifier.fillMaxSize()) {
                    MultiplayerGameScreen(
                        room = current.room,
                        myUid = myUid,
                        onColorTapped = { viewModel.submitAnswer(it) },
                    )
                    if (showCountdown) {
                        CountdownOverlay(onFinished = { showCountdown = false })
                    }
                }
            }
        }
    }
}
