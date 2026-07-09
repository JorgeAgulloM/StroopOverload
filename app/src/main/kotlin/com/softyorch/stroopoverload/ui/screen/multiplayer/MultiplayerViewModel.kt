package com.softyorch.stroopoverload.ui.screen.multiplayer

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
        viewModelScope.launch {
            repository.startGame(current.room.roomId)
                .onFailure { _state.value = MultiplayerUiState.Error(MultiplayerErrorReason.StartGameFailed(it.message)) }
        }
    }

    fun submitAnswer(color: StroopColor) {
        val current = _state.value as? MultiplayerUiState.InRoom ?: return
        if (!current.room.canAnswer(current.myUid)) return
        viewModelScope.launch {
            repository.submitAnswer(current.room.roomId, color)
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
