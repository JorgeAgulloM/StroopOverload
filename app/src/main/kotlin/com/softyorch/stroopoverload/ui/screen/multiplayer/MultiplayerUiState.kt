package com.softyorch.stroopoverload.ui.screen.multiplayer

import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom

sealed interface MultiplayerErrorReason {
    data class CreateRoomFailed(val detail: String?) : MultiplayerErrorReason
    data class JoinRoomFailed(val detail: String?) : MultiplayerErrorReason
    data class StartGameFailed(val detail: String?) : MultiplayerErrorReason
    data class ConnectionLost(val detail: String?) : MultiplayerErrorReason
}

sealed interface MultiplayerUiState {
    data object Idle : MultiplayerUiState
    data object Connecting : MultiplayerUiState
    data class InRoom(val room: MultiplayerRoom, val myUid: String) : MultiplayerUiState
    data class Error(val reason: MultiplayerErrorReason) : MultiplayerUiState
}
