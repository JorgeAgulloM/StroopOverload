package com.softyorch.stroopoverload.ui.screen.multiplayer

import com.softyorch.stroopoverload.data.MultiplayerCallFailure
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom

// No raw exception text in here on purpose: it isn't localized (the backend's
// HttpsError messages are Spanish-only). The repository logs the underlying
// cause; the UI maps each reason to a string resource.
sealed interface MultiplayerErrorReason {
    data class CreateRoomFailed(val failure: MultiplayerCallFailure) : MultiplayerErrorReason
    data class JoinRoomFailed(val failure: MultiplayerCallFailure) : MultiplayerErrorReason
    data object StartGameFailed : MultiplayerErrorReason
    data object ConnectionLost : MultiplayerErrorReason
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
