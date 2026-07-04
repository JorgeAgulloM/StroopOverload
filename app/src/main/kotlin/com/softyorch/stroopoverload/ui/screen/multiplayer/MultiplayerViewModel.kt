package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.data.FirebaseMultiplayerRepository
import com.softyorch.stroopoverload.data.MultiplayerRepository
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

    fun createRoom(uid: String, displayName: String) {
        myUid = uid
        _state.value = MultiplayerUiState.Connecting
        viewModelScope.launch {
            repository.createRoom(displayName)
                .onSuccess { (roomId, _) -> observeRoom(roomId) }
                .onFailure { _state.value = MultiplayerUiState.Error(it.message ?: "No se pudo crear la sala.") }
        }
    }

    fun joinRoom(uid: String, code: String, displayName: String) {
        myUid = uid
        _state.value = MultiplayerUiState.Connecting
        viewModelScope.launch {
            repository.joinRoom(code, displayName)
                .onSuccess { roomId -> observeRoom(roomId) }
                .onFailure { _state.value = MultiplayerUiState.Error(it.message ?: "No se pudo unir a la sala.") }
        }
    }

    fun startGame() {
        val current = _state.value as? MultiplayerUiState.InRoom ?: return
        viewModelScope.launch {
            repository.startGame(current.room.roomId)
                .onFailure { _state.value = MultiplayerUiState.Error(it.message ?: "No se pudo iniciar la partida.") }
        }
    }

    fun submitAnswer(color: StroopColor) {
        val current = _state.value as? MultiplayerUiState.InRoom ?: return
        if (!current.room.isMyTurn(current.myUid)) return
        viewModelScope.launch {
            repository.submitAnswer(current.room.roomId, color)
        }
    }

    private fun observeRoom(roomId: String) {
        repository.trackPresence(roomId, myUid)
        viewModelScope.launch {
            repository.observeRoom(roomId).collect { room ->
                _state.value = MultiplayerUiState.InRoom(room, myUid)
            }
        }
    }
}
