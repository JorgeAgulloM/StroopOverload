package com.softyorch.stroopoverload.ui.screen.multiplayer

import app.cash.turbine.test
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.data.MultiplayerCallException
import com.softyorch.stroopoverload.data.MultiplayerCallFailure
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom
import com.softyorch.stroopoverload.domain.multiplayer.RoomMode
import com.softyorch.stroopoverload.domain.multiplayer.RoomPlayer
import com.softyorch.stroopoverload.domain.multiplayer.RoomStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
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
    fun `createRoom defaults to MISTAKE mode and forwards an explicit mode to the repository`() = runTest {
        val fake = FakeMultiplayerRepository()
        val viewModel = MultiplayerViewModel(fake)

        viewModel.createRoom(uid = "host-1", displayName = "Neo")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(RoomMode.MISTAKE, fake.lastCreateRoomMode)
    }

    @Test
    fun `createRoom forwards HOT_POTATO mode to the repository`() = runTest {
        val fake = FakeMultiplayerRepository()
        val viewModel = MultiplayerViewModel(fake)

        viewModel.createRoom(uid = "host-1", displayName = "Neo", mode = RoomMode.HOT_POTATO)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(RoomMode.HOT_POTATO, fake.lastCreateRoomMode)
    }

    @Test
    fun `createRoom is a no-op while already connecting`() = runTest {
        val fake = FakeMultiplayerRepository()
        val viewModel = MultiplayerViewModel(fake)

        viewModel.state.test {
            assertEquals(MultiplayerUiState.Idle, awaitItem())

            viewModel.createRoom(uid = "host-1", displayName = "Neo")
            assertEquals(MultiplayerUiState.Connecting, awaitItem())

            // Second call while still Connecting (fake hasn't emitted a room yet) must be ignored --
            // no second Connecting emission, no double repository call.
            viewModel.createRoom(uid = "host-2", displayName = "Trinity")
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
            assertEquals("host-1", inRoom.myUid) // the SECOND call's uid never took effect
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
                round = 7,
            )
        )
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.submitAnswer(StroopColor.RED)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fake.submitAnswerCallCount)
        assertEquals(7, fake.lastSubmittedRound) // the shared round the tap was aimed at
    }

    @Test
    fun `submitAnswer ignores turn order in SOLO_SURVIVAL -- any alive player may answer`() = runTest {
        val fake = FakeMultiplayerRepository()
        val viewModel = MultiplayerViewModel(fake)

        // "player-2" is uid at turnIndex 1, i.e. NOT the shared-turn holder -- would be
        // rejected in MISTAKE/HOT_POTATO, but solo_survival has no shared turn at all.
        viewModel.createRoom(uid = "player-2", displayName = "Trinity")
        dispatcher.scheduler.advanceUntilIdle()
        fake.emitRoom(
            MultiplayerRoom(
                roomId = "room-1",
                status = RoomStatus.PLAYING,
                mode = RoomMode.SOLO_SURVIVAL,
                players = listOf(
                    RoomPlayer(uid = "player-1", displayName = "Neo", alive = true, soloRound = 2),
                    RoomPlayer(uid = "player-2", displayName = "Trinity", alive = true, soloRound = 5),
                ),
                turnOrder = listOf("player-1", "player-2"),
                turnIndex = 0,
                round = 0, // solo_survival never advances the shared round
            )
        )
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.submitAnswer(StroopColor.RED)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fake.submitAnswerCallCount)
        assertEquals(5, fake.lastSubmittedRound) // this player's own soloRound
    }

    @Test
    fun `submitAnswer is ignored in SOLO_SURVIVAL once the acting player has busted`() = runTest {
        val fake = FakeMultiplayerRepository()
        val viewModel = MultiplayerViewModel(fake)

        viewModel.createRoom(uid = "player-1", displayName = "Neo")
        dispatcher.scheduler.advanceUntilIdle()
        fake.emitRoom(
            MultiplayerRoom(
                roomId = "room-1",
                status = RoomStatus.PLAYING,
                mode = RoomMode.SOLO_SURVIVAL,
                players = listOf(RoomPlayer(uid = "player-1", displayName = "Neo", alive = false)),
                turnOrder = listOf("player-1"),
            )
        )
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.submitAnswer(StroopColor.RED)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, fake.submitAnswerCallCount)
    }

    @Test
    fun `createRoom is a no-op while already in a room, original room keeps being observed`() = runTest {
        val fake = FakeMultiplayerRepository()
        val viewModel = MultiplayerViewModel(fake)
        // A second, independent flow that a (now impossible) re-entrant
        // createRoom would have switched to. Kept separate from the fake's
        // internal room-1 flow so we can prove it is never subscribed to.
        val room2Flow = MutableSharedFlow<MultiplayerRoom>(replay = 1)

        viewModel.state.test {
            assertEquals(MultiplayerUiState.Idle, awaitItem())

            viewModel.createRoom(uid = "host-1", displayName = "Neo")
            assertEquals(MultiplayerUiState.Connecting, awaitItem())
            dispatcher.scheduler.advanceUntilIdle()

            val room1 = MultiplayerRoom(
                roomId = "room-1",
                code = "ABCDE",
                status = RoomStatus.WAITING,
                hostUid = "host-1",
                players = listOf(RoomPlayer(uid = "host-1", displayName = "Neo")),
                turnOrder = listOf("host-1"),
            )
            fake.emitRoom(room1)
            val firstInRoom = awaitItem() as MultiplayerUiState.InRoom
            assertEquals("room-1", firstInRoom.room.roomId)

            // MultiplayerScreen never exposes onCreateRoom while InRoom, but
            // guard against re-entrant calls at the source too: this second
            // call must be ignored entirely rather than tearing down the
            // active room-1 subscription.
            fake.createRoomResult = Result.success("room-2" to "FGHIJ")
            fake.observeRoomFlow = room2Flow

            viewModel.createRoom(uid = "host-1", displayName = "Neo")
            dispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()

            // The original room-1 flow must still be the one driving state --
            // proof the guard returned before observeRoom() ever cancelled
            // the existing job or subscribed to room2Flow.
            fake.emitRoom(room1.copy(round = 99))
            val stillRoom1 = awaitItem() as MultiplayerUiState.InRoom
            assertEquals("room-1", stillRoom1.room.roomId)
            assertEquals(99, stillRoom1.room.round)
        }
    }

    @Test
    fun `joinRoom moves to InRoom once the repository emits the room`() = runTest {
        val fake = FakeMultiplayerRepository()
        val viewModel = MultiplayerViewModel(fake)

        viewModel.state.test {
            assertEquals(MultiplayerUiState.Idle, awaitItem())

            viewModel.joinRoom(uid = "player-2", code = "ABCDE", displayName = "Trinity")
            assertEquals(MultiplayerUiState.Connecting, awaitItem())
            dispatcher.scheduler.advanceUntilIdle()

            fake.emitRoom(
                MultiplayerRoom(
                    roomId = "room-1",
                    code = "ABCDE",
                    status = RoomStatus.WAITING,
                    hostUid = "host-1",
                    players = listOf(
                        RoomPlayer(uid = "host-1", displayName = "Neo"),
                        RoomPlayer(uid = "player-2", displayName = "Trinity"),
                    ),
                    turnOrder = listOf("host-1", "player-2"),
                )
            )
            val inRoom = awaitItem() as MultiplayerUiState.InRoom
            assertEquals("room-1", inRoom.room.roomId)
            assertTrue(fake.presenceTracked)
        }
    }

    @Test
    fun `createRoom failure surfaces MultiplayerUiState Error`() = runTest {
        val fake = FakeMultiplayerRepository()
        fake.createRoomResult = Result.failure(RuntimeException("nope"))
        val viewModel = MultiplayerViewModel(fake)

        viewModel.state.test {
            assertEquals(MultiplayerUiState.Idle, awaitItem())

            viewModel.createRoom(uid = "host-1", displayName = "Neo")
            assertEquals(MultiplayerUiState.Connecting, awaitItem())
            dispatcher.scheduler.advanceUntilIdle()

            val error = awaitItem() as MultiplayerUiState.Error
            assertEquals(MultiplayerErrorReason.CreateRoomFailed(MultiplayerCallFailure.UNKNOWN), error.reason)
        }
    }

    @Test
    fun `joinRoom failure surfaces MultiplayerUiState Error`() = runTest {
        val fake = FakeMultiplayerRepository()
        fake.joinRoomResult = Result.failure(RuntimeException("nope"))
        val viewModel = MultiplayerViewModel(fake)

        viewModel.state.test {
            assertEquals(MultiplayerUiState.Idle, awaitItem())

            viewModel.joinRoom(uid = "player-2", code = "ZZZZZ", displayName = "Trinity")
            assertEquals(MultiplayerUiState.Connecting, awaitItem())
            dispatcher.scheduler.advanceUntilIdle()

            val error = awaitItem() as MultiplayerUiState.Error
            // Raw exception text never reaches the UI (it isn't localized).
            assertEquals(MultiplayerErrorReason.JoinRoomFailed(MultiplayerCallFailure.UNKNOWN), error.reason)
        }
    }

    @Test
    fun `joinRoom typed failure is carried through so the UI can explain it`() = runTest {
        val fake = FakeMultiplayerRepository()
        fake.joinRoomResult = Result.failure(MultiplayerCallException(MultiplayerCallFailure.ROOM_FULL))
        val viewModel = MultiplayerViewModel(fake)

        viewModel.state.test {
            assertEquals(MultiplayerUiState.Idle, awaitItem())

            viewModel.joinRoom(uid = "player-2", code = "ZZZZZ", displayName = "Trinity")
            assertEquals(MultiplayerUiState.Connecting, awaitItem())
            dispatcher.scheduler.advanceUntilIdle()

            val error = awaitItem() as MultiplayerUiState.Error
            assertEquals(MultiplayerErrorReason.JoinRoomFailed(MultiplayerCallFailure.ROOM_FULL), error.reason)
        }
    }

    @Test
    fun `createRoom rate-limited failure is carried through so the UI can explain it`() = runTest {
        val fake = FakeMultiplayerRepository()
        fake.createRoomResult = Result.failure(MultiplayerCallException(MultiplayerCallFailure.RATE_LIMITED))
        val viewModel = MultiplayerViewModel(fake)

        viewModel.state.test {
            assertEquals(MultiplayerUiState.Idle, awaitItem())

            viewModel.createRoom(uid = "host-1", displayName = "Neo")
            assertEquals(MultiplayerUiState.Connecting, awaitItem())
            dispatcher.scheduler.advanceUntilIdle()

            val error = awaitItem() as MultiplayerUiState.Error
            assertEquals(MultiplayerErrorReason.CreateRoomFailed(MultiplayerCallFailure.RATE_LIMITED), error.reason)
        }
    }

    @Test
    fun `startGame calls the repository only while InRoom`() = runTest {
        val fake = FakeMultiplayerRepository()
        val viewModel = MultiplayerViewModel(fake)

        // Idle state: startGame must no-op.
        viewModel.startGame()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, fake.startGameCallCount)

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
            awaitItem() // InRoom

            viewModel.startGame()
            val starting = awaitItem() as MultiplayerUiState.InRoom
            assertTrue(starting.isStartingGame)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(1, fake.startGameCallCount)
        }
    }

    @Test
    fun `startGame locks isStartingGame immediately and is a no-op while already starting`() = runTest {
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
                    status = RoomStatus.WAITING,
                    hostUid = "host-1",
                    players = listOf(RoomPlayer(uid = "host-1", displayName = "Neo")),
                    turnOrder = listOf("host-1"),
                )
            )
            awaitItem() // InRoom, isStartingGame == false

            viewModel.startGame()
            val starting = awaitItem() as MultiplayerUiState.InRoom
            assertTrue(starting.isStartingGame) // locked before the repository call even resolves

            // A second press while still starting must not re-trigger the repository.
            viewModel.startGame()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(1, fake.startGameCallCount)
        }
    }

    @Test
    fun `startGame failure clears isStartingGame and surfaces startGameError, staying in the room`() = runTest {
        val fake = FakeMultiplayerRepository()
        fake.startGameResult = Result.failure(RuntimeException("network down"))
        val viewModel = MultiplayerViewModel(fake)

        viewModel.state.test {
            assertEquals(MultiplayerUiState.Idle, awaitItem())

            viewModel.createRoom(uid = "host-1", displayName = "Neo")
            assertEquals(MultiplayerUiState.Connecting, awaitItem())
            dispatcher.scheduler.advanceUntilIdle()

            fake.emitRoom(
                MultiplayerRoom(
                    roomId = "room-1",
                    status = RoomStatus.WAITING,
                    hostUid = "host-1",
                    players = listOf(RoomPlayer(uid = "host-1", displayName = "Neo")),
                    turnOrder = listOf("host-1"),
                )
            )
            awaitItem() // InRoom

            viewModel.startGame()
            val starting = awaitItem() as MultiplayerUiState.InRoom
            assertTrue(starting.isStartingGame)
            dispatcher.scheduler.advanceUntilIdle()

            val failed = awaitItem() as MultiplayerUiState.InRoom
            assertEquals(false, failed.isStartingGame) // unlocked so the host can retry
            assertEquals(MultiplayerErrorReason.StartGameFailed, failed.startGameError)
            assertEquals("room-1", failed.room.roomId) // still in the room, not bounced to Lobby
        }
    }

    @Test
    fun `observeRoom flow throwing surfaces MultiplayerUiState Error`() = runTest {
        val fake = FakeMultiplayerRepository()
        fake.observeRoomFlow = flow { throw RuntimeException("boom") }
        val viewModel = MultiplayerViewModel(fake)

        viewModel.state.test {
            assertEquals(MultiplayerUiState.Idle, awaitItem())

            viewModel.createRoom(uid = "host-1", displayName = "Neo")
            assertEquals(MultiplayerUiState.Connecting, awaitItem())
            dispatcher.scheduler.advanceUntilIdle()

            val error = awaitItem() as MultiplayerUiState.Error
            assertEquals(MultiplayerErrorReason.ConnectionLost, error.reason)
        }
    }

    @Test
    fun `exitRoom marks the player offline in the room they leave, once`() = runTest {
        val fake = FakeMultiplayerRepository()
        val viewModel = MultiplayerViewModel(fake)
        viewModel.createRoom(uid = "host-1", displayName = "Neo")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.exitRoom()
        viewModel.exitRoom()

        assertEquals(listOf("room-1" to "host-1"), fake.leftPresence)
        assertEquals(MultiplayerUiState.Idle, viewModel.state.value)
    }

    @Test
    fun `exitRoom without ever entering a room marks nothing offline`() = runTest {
        val fake = FakeMultiplayerRepository()
        val viewModel = MultiplayerViewModel(fake)

        viewModel.exitRoom()

        assertTrue(fake.leftPresence.isEmpty())
    }
}
