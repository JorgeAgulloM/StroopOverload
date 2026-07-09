package com.softyorch.stroopoverload.ui.screen.multiplayer

import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.data.MultiplayerRepository
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom
import com.softyorch.stroopoverload.domain.multiplayer.RoomMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Fake used by [MultiplayerViewModelTest]. Results and the observed flow are
 * configurable per-test so both success and failure paths can be exercised
 * without breaking the default (all-success) behavior existing tests rely on.
 */
class FakeMultiplayerRepository : MultiplayerRepository {
    private val roomFlow = MutableSharedFlow<MultiplayerRoom>(replay = 1)

    var presenceTracked = false
        private set
    var submitAnswerCallCount = 0
        private set
    var startGameCallCount = 0
        private set
    var lastCreateRoomMode: RoomMode? = null
        private set

    var createRoomResult: Result<Pair<String, String>> = Result.success("room-1" to "ABCDE")
    var joinRoomResult: Result<String> = Result.success("room-1")
    var startGameResult: Result<Unit> = Result.success(Unit)

    /**
     * Overrides the [Flow] returned by [observeRoom]. Defaults to null, which
     * means "use the internal [roomFlow]" (fed via [emitRoom]). Set this to a
     * custom flow (e.g. `flow { throw RuntimeException("boom") }`) to simulate
     * the Firestore-backed flow itself throwing.
     */
    var observeRoomFlow: Flow<MultiplayerRoom>? = null

    suspend fun emitRoom(room: MultiplayerRoom) = roomFlow.emit(room)

    override suspend fun createRoom(displayName: String, mode: RoomMode): Result<Pair<String, String>> {
        lastCreateRoomMode = mode
        return createRoomResult
    }

    override suspend fun joinRoom(code: String, displayName: String): Result<String> = joinRoomResult

    override suspend fun startGame(roomId: String): Result<Unit> {
        startGameCallCount++
        return startGameResult
    }

    override suspend fun submitAnswer(roomId: String, selectedColor: StroopColor): Result<Unit> {
        submitAnswerCallCount++
        return Result.success(Unit)
    }

    override fun observeRoom(roomId: String): Flow<MultiplayerRoom> = observeRoomFlow ?: roomFlow

    override fun trackPresence(roomId: String, uid: String) {
        presenceTracked = true
    }

    override suspend fun deleteMyMultiplayerData(): Result<Unit> = Result.success(Unit)
}
