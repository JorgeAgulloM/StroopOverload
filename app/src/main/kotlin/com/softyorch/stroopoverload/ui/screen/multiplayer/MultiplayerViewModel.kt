package com.softyorch.stroopoverload.ui.screen.multiplayer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.data.FirebaseMultiplayerRepository
import com.softyorch.stroopoverload.data.MultiplayerRepository
import com.softyorch.stroopoverload.domain.multiplayer.RoomMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MultiplayerViewModel(
    private val repository: MultiplayerRepository = FirebaseMultiplayerRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow<MultiplayerUiState>(MultiplayerUiState.Idle)
    val state: StateFlow<MultiplayerUiState> = _state.asStateFlow()

    private var myUid: String = ""
    private var observeRoomJob: Job? = null

    fun createRoom(uid: String, displayName: String, mode: RoomMode = RoomMode.MISTAKE) {
        if (_state.value !is MultiplayerUiState.Idle && _state.value !is MultiplayerUiState.Error) return
        myUid = uid
        _state.value = MultiplayerUiState.Connecting
        viewModelScope.launch {
            repository.createRoom(displayName, mode)
                .onSuccess { (roomId, _) -> observeRoom(roomId) }
                .onFailure { _state.value = MultiplayerUiState.Error(MultiplayerErrorReason.CreateRoomFailed(it.message)) }
        }
    }

    fun joinRoom(uid: String, code: String, displayName: String) {
        if (_state.value !is MultiplayerUiState.Idle && _state.value !is MultiplayerUiState.Error) return
        myUid = uid
        _state.value = MultiplayerUiState.Connecting
        viewModelScope.launch {
            repository.joinRoom(code, displayName)
                .onSuccess { roomId -> observeRoom(roomId) }
                .onFailure { _state.value = MultiplayerUiState.Error(MultiplayerErrorReason.JoinRoomFailed(it.message)) }
        }
    }

    fun startGame() {
        val current = _state.value as? MultiplayerUiState.InRoom ?: return
        if (current.isStartingGame) return
        _state.value = current.copy(isStartingGame = true, startGameError = null)
        viewModelScope.launch {
            repository.startGame(current.room.roomId).onFailure { err ->
                // Re-read the latest InRoom state rather than reusing `current`: the
                // Firestore listener may have pushed a newer room in the meantime,
                // and clobbering it back to `current` would lose that update.
                val latest = _state.value as? MultiplayerUiState.InRoom ?: return@onFailure
                _state.value = latest.copy(
                    isStartingGame = false,
                    startGameError = MultiplayerErrorReason.StartGameFailed(err.message),
                )
            }
        }
    }

    /** Leaves a FINISHED match's result screen back to the lobby, stopping the room listener. */
    fun exitRoom() {
        observeRoomJob?.cancel()
        observeRoomJob = null
        _state.value = MultiplayerUiState.Idle
    }

    fun submitAnswer(color: StroopColor) {
        val current = _state.value as? MultiplayerUiState.InRoom ?: return
        if (!current.room.canAnswer(current.myUid)) return
        viewModelScope.launch {
            // Best-effort: a rejected answer (e.g. lost a race against the deadline
            // or the turn already moved on) self-corrects on the next Firestore
            // snapshot, which is why this doesn't surface a UI error state -- but
            // it must not fail silently with no trace when debugging reports like
            // "my tap didn't register".
            repository.submitAnswer(current.room.roomId, color)
                .onFailure { Log.w("MultiplayerViewModel", "submitAnswer rejected: ${it.message}") }
        }
    }

    private fun observeRoom(roomId: String) {
        // createRoom/joinRoom's guard already blocks re-entry while a previous
        // observeRoomJob would still be alive, so this cancel() is defense-in-depth
        // rather than a live, test-covered path -- kept in case that guard ever changes.
        observeRoomJob?.cancel()
        repository.trackPresence(roomId, myUid)
        observeRoomJob = viewModelScope.launch {
            try {
                repository.observeRoom(roomId).collect { room ->
                    _state.value = MultiplayerUiState.InRoom(room, myUid)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = MultiplayerUiState.Error(MultiplayerErrorReason.ConnectionLost(e.message))
            }
        }
    }

    override fun onCleared() {
        observeRoomJob?.cancel()
        super.onCleared()
    }
}
