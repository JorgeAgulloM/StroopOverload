package com.softyorch.stroopoverload.ui.screen.multiplayer

import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom

sealed interface MultiplayerUiState {
    data object Idle : MultiplayerUiState
    data object Connecting : MultiplayerUiState
    data class InRoom(val room: MultiplayerRoom, val myUid: String) : MultiplayerUiState
    data class Error(val message: String) : MultiplayerUiState
}
