package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.softyorch.stroopoverload.domain.multiplayer.RoomStatus

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
            RoomStatus.PLAYING, RoomStatus.FINISHED -> MultiplayerGameScreen(
                room = current.room,
                myUid = myUid,
                onColorTapped = { viewModel.submitAnswer(it) },
            )
        }
    }
}
