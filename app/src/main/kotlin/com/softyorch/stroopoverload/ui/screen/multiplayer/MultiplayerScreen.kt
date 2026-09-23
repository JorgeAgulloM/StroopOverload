package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.ads.InterstitialAdManager
import com.softyorch.stroopoverload.audio.AudioPlayer
import com.softyorch.stroopoverload.audio.GameSfx
import com.softyorch.stroopoverload.audio.MusicManager
import com.softyorch.stroopoverload.audio.MusicTrack
import com.softyorch.stroopoverload.data.FirebaseGameRepository
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom
import com.softyorch.stroopoverload.domain.multiplayer.RoomMode
import com.softyorch.stroopoverload.domain.multiplayer.RoomStatus
import com.softyorch.stroopoverload.ui.GAMEPLAY_MUSIC_TRACKS
import com.softyorch.stroopoverload.ui.components.CountdownOverlay
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MultiplayerScreen(
    myUid: String,
    myNickname: String,
    repository: FirebaseGameRepository,
    interstitialAdManager: InterstitialAdManager,
    isAdFree: Boolean,
    musicManager: MusicManager,
) {
    val viewModel: MultiplayerViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val context = LocalContext.current
    val audioPlayer = remember { AudioPlayer(context) }
    DisposableEffect(Unit) { onDispose { audioPlayer.release() } }

    // Own the music for every sub-state this screen cycles through --
    // WAITING and STARTING share one track so the WAITING -> STARTING
    // transition (room fills up, host starts) doesn't fade out and back in;
    // PLAYING/FINISHED share the same shuffled gameplay playlist as local
    // single-player. Lobby/idle/connecting/error stay silent.
    val roomStatus = (state as? MultiplayerUiState.InRoom)?.room?.status
    val musicTrack = when (roomStatus) {
        RoomStatus.WAITING, RoomStatus.STARTING -> MusicTrack.Loop(R.raw.music_waiting_room)
        RoomStatus.PLAYING, RoomStatus.FINISHED -> MusicTrack.Playlist(GAMEPLAY_MUSIC_TRACKS)
        null -> null
    }
    LaunchedEffect(musicTrack) { musicManager.setTrack(musicTrack) }

    // Room is only actually created/joined once the interstitial has been
    // shown (or immediately, if there's no Activity to show it against, ads
    // are disabled, the player is ad-free, or no ad was ready to load in
    // time) -- monetization never permanently blocks play.
    fun gatedThen(action: () -> Unit) {
        val currentActivity = activity
        if (currentActivity == null) {
            action()
        } else {
            interstitialAdManager.showAndThen(currentActivity, isAdFree, action)
        }
    }

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
            onCreateRoom = { mode -> gatedThen { viewModel.createRoom(myUid, myNickname, mode) } },
            onJoinRoom = { code -> gatedThen { viewModel.joinRoom(myUid, code, myNickname) } },
            errorReason = (current as? MultiplayerUiState.Error)?.reason,
            isConnecting = current is MultiplayerUiState.Connecting,
        )
        is MultiplayerUiState.InRoom -> {
            val room = current.room
            if (room.status == RoomStatus.FINISHED && room.awardsAppliedAtMs != null) {
                // The backend credits every player's profile itself (onRoomFinished) and
                // stamps awardsAppliedAtMs when it is done; this just pulls the settled
                // numbers back into the local profile. Keyed on the room so recomposition
                // alone won't repeat it, and guarded again per-room inside the repository
                // (which survives app restarts and reconnects).
                LaunchedEffect(room.roomId) {
                    repository.syncMatchResult(room.roomId)
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
                RoomStatus.STARTING -> MultiplayerStartingScreen(room, audioPlayer)
                RoomStatus.PLAYING, RoomStatus.FINISHED -> if (room.mode == RoomMode.SOLO_SURVIVAL) {
                    SoloSurvivalGameScreen(
                        room = room,
                        myUid = myUid,
                        onColorTapped = { viewModel.submitAnswer(it) },
                        onExit = { viewModel.exitRoom() },
                        audioPlayer = audioPlayer,
                    )
                } else {
                    MultiplayerGameScreen(
                        room = room,
                        myUid = myUid,
                        onColorTapped = { viewModel.submitAnswer(it) },
                        onExit = { viewModel.exitRoom() },
                        audioPlayer = audioPlayer,
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
private fun MultiplayerStartingScreen(room: MultiplayerRoom, audioPlayer: AudioPlayer) {
    var phase by remember(room.startsAtMs) { mutableStateOf(StartingPhase.LOADING) }

    LaunchedEffect(room.startsAtMs) {
        val remainingMs = room.startsAtMs?.let { it - System.currentTimeMillis() } ?: 0L
        val waitBeforeCountdownMs = (remainingMs - COUNTDOWN_ANIMATION_MS).coerceAtLeast(0L)
        if (waitBeforeCountdownMs > 0) delay(waitBeforeCountdownMs)
        phase = StartingPhase.COUNTDOWN
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (phase) {
            StartingPhase.COUNTDOWN -> CountdownOverlay(onFinished = {
                audioPlayer.play(GameSfx.MATCH_START)
                phase = StartingPhase.BRIDGING
            })
            // LOADING (the actual wait, filled with PreloadWaitingRoom's content) and
            // BRIDGING (the countdown finished slightly before beginRound's real
            // Firestore update arrived) look identical to the player -- both are
            // just "hang on" -- so BRIDGING reuses the same room.
            StartingPhase.LOADING, StartingPhase.BRIDGING -> PreloadWaitingRoom(room)
        }
    }
}
