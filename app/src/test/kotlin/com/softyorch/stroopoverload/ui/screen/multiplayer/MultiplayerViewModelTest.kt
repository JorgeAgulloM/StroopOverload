package com.softyorch.stroopoverload.ui.screen.multiplayer

import app.cash.turbine.test
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom
import com.softyorch.stroopoverload.domain.multiplayer.RoomPlayer
import com.softyorch.stroopoverload.domain.multiplayer.RoomStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MultiplayerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `createRoom moves to InRoom once the repository emits the room`() = runTest {
        val fake = FakeMultiplayerRepository()
        val viewModel = MultiplayerViewModel(fake)

        viewModel.state.test {
            assertEquals(MultiplayerUiState.Idle, awaitItem())

            viewModel.createRoom(uid = "host-1", displayName = "Neo")
            assertEquals(MultiplayerUiState.Connecting, awaitItem())
            dispatcher.scheduler.advanceUntilIdle()

            fake.emitRoom(
                MultiplayerRoom(
                    roomId = "room-1",
                    code = "ABCDE",
                    status = RoomStatus.WAITING,
                    hostUid = "host-1",
                    players = listOf(RoomPlayer(uid = "host-1", displayName = "Neo")),
                    turnOrder = listOf("host-1"),
                )
            )
            val inRoom = awaitItem() as MultiplayerUiState.InRoom
            assertEquals("room-1", inRoom.room.roomId)
            assertTrue(fake.presenceTracked)
        }
    }

    @Test
    fun `submitAnswer is ignored when it is not my turn`() = runTest {
        val fake = FakeMultiplayerRepository()
        val viewModel = MultiplayerViewModel(fake)

        viewModel.createRoom(uid = "player-2", displayName = "Trinity")
        dispatcher.scheduler.advanceUntilIdle()
        fake.emitRoom(
            MultiplayerRoom(
                roomId = "room-1",
                status = RoomStatus.PLAYING,
                players = listOf(
                    RoomPlayer(uid = "player-1", displayName = "Neo"),
                    RoomPlayer(uid = "player-2", displayName = "Trinity"),
                ),
                turnOrder = listOf("player-1", "player-2"),
                turnIndex = 0,
            )
        )
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.submitAnswer(StroopColor.RED)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, fake.submitAnswerCallCount)
    }

    @Test
    fun `submitAnswer calls the repository when it is my turn`() = runTest {
        val fake = FakeMultiplayerRepository()
        val viewModel = MultiplayerViewModel(fake)

        viewModel.createRoom(uid = "player-1", displayName = "Neo")
        dispatcher.scheduler.advanceUntilIdle()
        fake.emitRoom(
            MultiplayerRoom(
                roomId = "room-1",
                status = RoomStatus.PLAYING,
                players = listOf(
                    RoomPlayer(uid = "player-1", displayName = "Neo"),
                    RoomPlayer(uid = "player-2", displayName = "Trinity"),
                ),
                turnOrder = listOf("player-1", "player-2"),
                turnIndex = 0,
            )
        )
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.submitAnswer(StroopColor.RED)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fake.submitAnswerCallCount)
    }
}
