package com.softyorch.stroopoverload.game

import app.cash.turbine.test
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
class GameViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `startGame enters Countdown without starting a round`() = runTest {
        val viewModel = GameViewModel()

        viewModel.state.test {
            assertEquals(GameState.Menu, awaitItem())

            viewModel.startGame(previousHigh = 42)
            assertEquals(GameState.Countdown, awaitItem())
            assertEquals(null, viewModel.stimulus.value)
        }
    }

    @Test
    fun `beginRound moves Countdown to Playing and generates the first stimulus`() = runTest {
        val viewModel = GameViewModel()

        viewModel.state.test {
            awaitItem() // Menu
            viewModel.startGame()
            awaitItem() // Countdown

            viewModel.beginRound()
            val playing = awaitItem()
            assertTrue(playing is GameState.Playing)
        }
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.stimulus.value != null)
    }

    @Test
    fun `beginRound is a no-op outside of Countdown`() = runTest {
        val viewModel = GameViewModel()

        viewModel.state.test {
            assertEquals(GameState.Menu, awaitItem())
            viewModel.beginRound()
            expectNoEvents()
        }
    }
}
