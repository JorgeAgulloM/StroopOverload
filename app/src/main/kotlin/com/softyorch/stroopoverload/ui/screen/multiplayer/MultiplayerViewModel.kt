package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.data.FirebaseMultiplayerRepository
import com.softyorch.stroopoverload.data.MultiplayerCallException
import com.softyorch.stroopoverload.data.MultiplayerCallFailure
import com.softyorch.stroopoverload.data.MultiplayerRepository
import com.softyorch.stroopoverload.domain.multiplayer.RoomMode
import com.softyorch.stroopoverload.domain.multiplayer.RoomStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MultiplayerViewModel(
    private val repository: MultiplayerRepository = FirebaseMultiplayerRepository(),
    // Survives process death, unlike everything else here. Holds the room the player is
    // seated in so a restored screen can reattach to it instead of dropping them in the lobby
    // with no word about the match they were in.
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _state = MutableStateFlow<MultiplayerUiState>(MultiplayerUiState.Idle)
    val state: StateFlow<MultiplayerUiState> = _state.asStateFlow()

    private var myUid: String = ""
    private var observeRoomJob: Job? = null
    private var presenceRoomId: String? = null

    // The room this player is seated in server-side, from the moment createRoom/joinRoom
    // answered -- tracked apart from the UI state, which is still Connecting before the first
    // snapshot and loses the room on a listener failure. See leaveRoomIfNotStarted.
    private var seatedRoomId: String? = null
    private var lastKnownStatus: RoomStatus = RoomStatus.WAITING

    /** (roomId, round) of the last answer sent and not rejected; see [submitAnswer]. */
    private var answeredTarget: Pair<String, Int>? = null

    init {
        val savedRoomId = savedState.get<String>(KEY_ROOM_ID)
        val savedUid = savedState.get<String>(KEY_UID)
        if (savedRoomId != null && savedUid != null) {
            // The process died with the player in a room. Presence went offline with it, so in
            // "mistake" and solo_survival the backend has already eliminated them; reattaching
            // shows them that and the final result. In hot_potato a disconnect eliminates no
            // one, and they can carry on playing.
            myUid = savedUid
            _state.value = MultiplayerUiState.Connecting
            observeRoom(savedRoomId)
        }
    }

    fun createRoom(uid: String, displayName: String, mode: RoomMode = RoomMode.MISTAKE) {
        if (_state.value !is MultiplayerUiState.Idle && _state.value !is MultiplayerUiState.Error) return
        myUid = uid
        _state.value = MultiplayerUiState.Connecting
        viewModelScope.launch {
            repository.createRoom(displayName, mode)
                .onSuccess { (roomId, _) -> observeRoom(roomId) }
                .onFailure { err ->
                    _state.value = MultiplayerUiState.Error(MultiplayerErrorReason.CreateRoomFailed(err.callFailure()))
                }
        }
    }

    fun joinRoom(uid: String, code: String, displayName: String) {
        if (_state.value !is MultiplayerUiState.Idle && _state.value !is MultiplayerUiState.Error) return
        myUid = uid
        _state.value = MultiplayerUiState.Connecting
        viewModelScope.launch {
            repository.joinRoom(code, displayName)
                .onSuccess { roomId -> observeRoom(roomId) }
                .onFailure { err ->
                    _state.value = MultiplayerUiState.Error(MultiplayerErrorReason.JoinRoomFailed(err.callFailure()))
                }
        }
    }

    fun startGame() {
        val current = _state.value as? MultiplayerUiState.InRoom ?: return
        if (current.isStartingGame) return
        _state.value = current.copy(isStartingGame = true, startGameError = null)
        viewModelScope.launch {
            repository.startGame(current.room.roomId).onFailure {
                // Re-read the latest InRoom state rather than reusing `current`: the
                // Firestore listener may have pushed a newer room in the meantime,
                // and clobbering it back to `current` would lose that update.
                val latest = _state.value as? MultiplayerUiState.InRoom ?: return@onFailure
                _state.value = latest.copy(
                    isStartingGame = false,
                    startGameError = MultiplayerErrorReason.StartGameFailed,
                )
            }
        }
    }

    /**
     * Leaves the room back to the lobby: from the waiting room (giving up the slot), from a
     * finished match's result screen, or mid-match after the player confirmed forfeiting.
     * Going offline is what makes
     * the forfeit real -- the backend eliminates a player who drops out of a
     * "mistake" or solo_survival match straight away instead of waiting for their
     * turn to time out.
     */
    fun exitRoom() {
        leaveRoomIfNotStarted()
        leavePresence()
        observeRoomJob?.cancel()
        observeRoomJob = null
        forgetSavedRoom()
        _state.value = MultiplayerUiState.Idle
    }

    private fun forgetSavedRoom() {
        savedState.remove<String>(KEY_ROOM_ID)
        savedState.remove<String>(KEY_UID)
    }

    /**
     * Before a match starts, going offline changes nothing on the server: the player kept
     * their slot, and a host who left stranded the others (only the host can start).
     * Leaving a started match is a forfeit instead, which presence handles.
     */
    private fun leaveRoomIfNotStarted() {
        val roomId = seatedRoomId ?: return
        seatedRoomId = null
        if (lastKnownStatus == RoomStatus.WAITING) repository.leaveRoom(roomId)
    }

    private fun leavePresence() {
        val roomId = presenceRoomId ?: return
        presenceRoomId = null
        repository.leavePresence(roomId, myUid)
    }

    fun submitAnswer(color: StroopColor) {
        val current = _state.value as? MultiplayerUiState.InRoom ?: return
        if (!current.room.canAnswer(current.myUid)) return
        // One answer per stimulus. The board stays tappable until the next Firestore
        // snapshot arrives, so a quick double tap would send a second answer for the
        // same round -- which the backend rejects as STALE_ROUND. Drop it here and
        // save the round trip. A failed call clears this so the player can retry.
        val round = current.room.answerRound(current.myUid)
        val target = current.room.roomId to round
        if (answeredTarget == target) return
        answeredTarget = target
        viewModelScope.launch {
            // Best-effort: a rejected answer (e.g. lost a race against the deadline
            // or the turn already moved on) self-corrects on the next Firestore
            // snapshot, which is why this doesn't surface a UI error state. The
            // repository logs every failed call, so "my tap didn't register" still
            // leaves a trace.
            repository.submitAnswer(current.room.roomId, color, round)
                .onFailure { if (answeredTarget == target) answeredTarget = null }
        }
    }

    private fun observeRoom(roomId: String) {
        // createRoom/joinRoom's guard already blocks re-entry while a previous
        // observeRoomJob would still be alive, so this cancel() is defense-in-depth
        // rather than a live, test-covered path -- kept in case that guard ever changes.
        observeRoomJob?.cancel()
        repository.trackPresence(roomId, myUid)
        presenceRoomId = roomId
        seatedRoomId = roomId
        savedState[KEY_ROOM_ID] = roomId
        savedState[KEY_UID] = myUid
        lastKnownStatus = RoomStatus.WAITING
        observeRoomJob = viewModelScope.launch {
            try {
                repository.observeRoom(roomId).collect { room ->
                    lastKnownStatus = room.status
                    _state.value = MultiplayerUiState.InRoom(room, myUid)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Listener failure or the room doc vanished -- see FirebaseMultiplayerRepository.observeRoom.
                _state.value = MultiplayerUiState.Error(MultiplayerErrorReason.ConnectionLost)
            }
        }
    }

    private fun Throwable.callFailure(): MultiplayerCallFailure =
        (this as? MultiplayerCallException)?.failure ?: MultiplayerCallFailure.UNKNOWN

    override fun onCleared() {
        leaveRoomIfNotStarted()
        leavePresence()
        observeRoomJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val KEY_ROOM_ID = "multiplayer.roomId"
        const val KEY_UID = "multiplayer.uid"
    }
}
