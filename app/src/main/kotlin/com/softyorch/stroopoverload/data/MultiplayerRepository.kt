package com.softyorch.stroopoverload.data

import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom
import com.softyorch.stroopoverload.domain.multiplayer.RoomMode
import kotlinx.coroutines.flow.Flow

interface MultiplayerRepository {
    suspend fun createRoom(displayName: String, mode: RoomMode = RoomMode.MISTAKE): Result<Pair<String, String>>
    suspend fun joinRoom(code: String, displayName: String): Result<String>
    suspend fun startGame(roomId: String): Result<Unit>
    suspend fun submitAnswer(roomId: String, selectedColor: StroopColor): Result<Unit>
    fun observeRoom(roomId: String): Flow<MultiplayerRoom>
    fun trackPresence(roomId: String, uid: String)
    suspend fun deleteMyMultiplayerData(): Result<Unit>
}
