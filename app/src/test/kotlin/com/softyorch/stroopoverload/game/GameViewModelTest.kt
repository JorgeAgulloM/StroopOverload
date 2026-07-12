package com.softyorch.stroopoverload.game

import app.cash.turbine.test
import com.softyorch.stroopoverload.core.GameConfig
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.domain.GameMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `ENDLESS mode ends the run on the first miss`() = runTest {
        val viewModel = GameViewModel()
        viewModel.startGame(mode = GameMode.ENDLESS)
        viewModel.beginRound()

        val correct = viewModel.stimulus.value!!.correctAnswer
        val wrong = StroopColor.entries.first { it != correct }
        viewModel.onColorTapped(wrong)

        val result = viewModel.state.value
        assertTrue(result is GameState.GameOver)
        assertEquals(GameMode.ENDLESS, (result as GameState.GameOver).result.mode)
    }

    @Test
    fun `LIVES mode loses a life on miss, flashes the correct color, and freezes input`() = runTest {
        val viewModel = GameViewModel()
        viewModel.startGame(mode = GameMode.LIVES)
        viewModel.beginRound()

        val startingLives = (viewModel.state.value as GameState.Playing).livesRemaining
        assertEquals(GameConfig.LIVES_MODE_STARTING_LIVES, startingLives)

        val correct = viewModel.stimulus.value!!.correctAnswer
        val wrong = StroopColor.entries.first { it != correct }
        viewModel.onColorTapped(wrong)

        val afterMiss = viewModel.state.value as GameState.Playing
        assertEquals(startingLives - 1, afterMiss.livesRemaining)
        assertEquals(correct, afterMiss.missFlashColor)
        assertTrue(afterMiss.isFrozen)

        // A second tap while frozen must be ignored (no further life loss).
        viewModel.onColorTapped(wrong)
        assertEquals(startingLives - 1, (viewModel.state.value as GameState.Playing).livesRemaining)

        dispatcher.scheduler.advanceTimeBy(GameConfig.LIVES_MODE_FREEZE_MS + 50)
        dispatcher.scheduler.runCurrent()

        val resumed = viewModel.state.value as GameState.Playing
        assertNull(resumed.missFlashColor)
        assertFalse(resumed.isFrozen)
    }

    @Test
    fun `LIVES mode ends the game once the last life is lost`() = runTest {
        val viewModel = GameViewModel()
        viewModel.startGame(mode = GameMode.LIVES)
        viewModel.beginRound()

        repeat(GameConfig.LIVES_MODE_STARTING_LIVES) {
            val playing = viewModel.state.value as GameState.Playing
            val correct = viewModel.stimulus.value!!.correctAnswer
            val wrong = StroopColor.entries.first { it != correct }
            viewModel.onColorTapped(wrong)
            dispatcher.scheduler.advanceTimeBy(GameConfig.LIVES_MODE_FREEZE_MS + 50)
            dispatcher.scheduler.runCurrent()
        }

        assertTrue(viewModel.state.value is GameState.GameOver)
    }

    @Test
    fun `TIME mode continues after a miss instead of ending the run`() = runTest {
        val viewModel = GameViewModel()
        viewModel.startGame(mode = GameMode.TIME)
        viewModel.beginRound()

        val correct = viewModel.stimulus.value!!.correctAnswer
        val wrong = StroopColor.entries.first { it != correct }
        viewModel.onColorTapped(wrong)

        val afterMiss = viewModel.state.value
        assertTrue(afterMiss is GameState.Playing)
        afterMiss as GameState.Playing
        assertEquals(1, afterMiss.totalRounds)
        assertEquals(0, afterMiss.correctHits)
        assertEquals(correct, afterMiss.missFlashColor)
    }
}
