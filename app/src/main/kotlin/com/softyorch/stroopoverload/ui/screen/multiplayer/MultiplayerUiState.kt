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
    data class InRoom(
        val room: MultiplayerRoom,
        val myUid: String,
        // Host-side "Start Game" press feedback: true while the callable is in
        // flight, so WaitingRoomScreen can lock the button. Cleared on failure
        // (with startGameError set) so the host can retry; left true on success
        // since the room's status flip to STARTING routes away from that screen.
        val isStartingGame: Boolean = false,
        val startGameError: MultiplayerErrorReason.StartGameFailed? = null,
    ) : MultiplayerUiState
    data class Error(val reason: MultiplayerErrorReason) : MultiplayerUiState
}
