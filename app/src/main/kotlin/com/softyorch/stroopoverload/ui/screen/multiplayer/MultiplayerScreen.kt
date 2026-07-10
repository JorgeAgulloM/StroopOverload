package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.softyorch.stroopoverload.data.FirebaseGameRepository
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom
import com.softyorch.stroopoverload.domain.multiplayer.RoomMode
import com.softyorch.stroopoverload.domain.multiplayer.RoomStatus
import com.softyorch.stroopoverload.ui.components.CountdownOverlay
import kotlinx.coroutines.delay

@Composable
fun MultiplayerScreen(myUid: String, myNickname: String, repository: FirebaseGameRepository) {
    val viewModel: MultiplayerViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    when (val current = state) {
        // Idle/Connecting/Error all render the same LobbyScreen -- deliberately a
        // SINGLE call site (not one per branch) so Compose keeps the same
        // composable instance across those transitions. Three separate call
        // sites here previously meant pressing "Create" (Idle -> Connecting)
        // tore down and remounted LobbyScreen at a different source position,
        // resetting its `remember`-held `selectedMode` back to MISTAKE for a
        // frame -- visible as the mode picker flashing back to Mistake right as
        // the button was pressed, even though the room itself was always
        // created with the correct mode (selectedMode was already captured by
        // the button's onClick before the reset happened).
        is MultiplayerUiState.Idle, is MultiplayerUiState.Connecting, is MultiplayerUiState.Error -> LobbyScreen(
            pilotName = myNickname,
            onCreateRoom = { mode -> viewModel.createRoom(myUid, myNickname, mode) },
            onJoinRoom = { code -> viewModel.joinRoom(myUid, code, myNickname) },
            errorReason = (current as? MultiplayerUiState.Error)?.reason,
            isConnecting = current is MultiplayerUiState.Connecting,
        )
        is MultiplayerUiState.InRoom -> {
            val room = current.room
            if (room.status == RoomStatus.FINISHED) {
                // Fires once per unique roomId reaching FINISHED in this composition
                // (recomposition alone won't re-key it) -- applyMultiplayerScore is
                // also idempotent per-roomId itself (survives app restarts/reconnects).
                LaunchedEffect(room.roomId) {
                    val me = room.player(myUid)
                    val finalScore = me?.finalScore
                    if (finalScore != null) {
                        repository.applyMultiplayerScore(room.roomId, finalScore, me.placement == 1)
                    }
                }
            }
            when (room.status) {
                RoomStatus.WAITING -> WaitingRoomScreen(
                    room = room,
                    myUid = myUid,
                    isStartingGame = current.isStartingGame,
                    startGameError = current.startGameError,
                    onStartGame = { viewModel.startGame() },
                )
                // "starting" is purely a server-driven countdown window: round 1's real
                // stimulus/deadline don't exist yet (see beginRound in the Cloud Functions).
                // The board only becomes interactive once Firestore actually reports
                // "playing", so every client gets the full answer window regardless of how
                // long their own countdown animation/render took -- no more racing a
                // deadline that started ticking before they could see the board.
                RoomStatus.STARTING -> MultiplayerStartingScreen(room)
                RoomStatus.PLAYING, RoomStatus.FINISHED -> if (room.mode == RoomMode.SOLO_SURVIVAL) {
                    SoloSurvivalGameScreen(
                        room = room,
                        myUid = myUid,
                        onColorTapped = { viewModel.submitAnswer(it) },
                        onExit = { viewModel.exitRoom() },
                    )
                } else {
                    MultiplayerGameScreen(
                        room = room,
                        myUid = myUid,
                        onColorTapped = { viewModel.submitAnswer(it) },
                        onExit = { viewModel.exitRoom() },
                    )
                }
            }
        }
    }
}

// Matches CountdownOverlay's own timing (4 steps * CountdownStepMs) -- kept in
// sync manually since the overlay's step count/duration are cosmetic details
// private to that composable, not something worth threading a parameter for.
private const val COUNTDOWN_ANIMATION_MS = 3600L

private enum class StartingPhase { LOADING, COUNTDOWN, BRIDGING }

/**
 * "starting" flow: a proper loading waiting room with some light
 * entertainment (PreloadWaitingRoom) plays first -- for as long as it
 * actually takes the server to reach [MultiplayerRoom.startsAtMs] -- then the
 * 3-2-1-GO countdown plays, timed to finish right as the server actually
 * transitions to "playing". This ordering matters for fairness, not just
 * feel: round 1's real deadline is computed server-side at startsAtMs
 * (see beginRound in the Cloud Functions) regardless of how long any
 * client's local animation takes, so playing the countdown too early (before
 * the wait is actually over) would either cut it short or leave a dead gap
 * before the board appears -- countdown-then-loader was the old, jarring
 * order; this is loader-then-countdown, ending on GO! right as the match
 * actually starts.
 */
@Composable
private fun MultiplayerStartingScreen(room: MultiplayerRoom) {
    var phase by remember(room.startsAtMs) { mutableStateOf(StartingPhase.LOADING) }

    LaunchedEffect(room.startsAtMs) {
        val remainingMs = room.startsAtMs?.let { it - System.currentTimeMillis() } ?: 0L
        val waitBeforeCountdownMs = (remainingMs - COUNTDOWN_ANIMATION_MS).coerceAtLeast(0L)
        if (waitBeforeCountdownMs > 0) delay(waitBeforeCountdownMs)
        phase = StartingPhase.COUNTDOWN
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (phase) {
            StartingPhase.COUNTDOWN -> CountdownOverlay(onFinished = { phase = StartingPhase.BRIDGING })
            // LOADING (the actual wait, filled with PreloadWaitingRoom's content) and
            // BRIDGING (the countdown finished slightly before beginRound's real
            // Firestore update arrived) look identical to the player -- both are
            // just "hang on" -- so BRIDGING reuses the same room.
            StartingPhase.LOADING, StartingPhase.BRIDGING -> PreloadWaitingRoom(room)
        }
    }
}
