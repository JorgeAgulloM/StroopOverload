package com.softyorch.stroopoverload.ui.screen.multiplayer

import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.data.MultiplayerRepository
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeMultiplayerRepository : MultiplayerRepository {
    private val roomFlow = MutableSharedFlow<MultiplayerRoom>(replay = 1)

    var presenceTracked = false
        private set
    var submitAnswerCallCount = 0
        private set

    suspend fun emitRoom(room: MultiplayerRoom) = roomFlow.emit(room)

    override suspend fun createRoom(displayName: String): Result<Pair<String, String>> =
        Result.success("room-1" to "ABCDE")

    override suspend fun joinRoom(code: String, displayName: String): Result<String> =
        Result.success("room-1")

    override suspend fun startGame(roomId: String): Result<Unit> = Result.success(Unit)

    override suspend fun submitAnswer(roomId: String, selectedColor: StroopColor): Result<Unit> {
        submitAnswerCallCount++
        return Result.success(Unit)
    }

    override fun observeRoom(roomId: String): Flow<MultiplayerRoom> = roomFlow

    override fun trackPresence(roomId: String, uid: String) {
        presenceTracked = true
    }
}
