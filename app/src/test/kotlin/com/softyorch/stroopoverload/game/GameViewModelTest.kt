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

    private fun endedEndlessRun(): GameViewModel {
        val viewModel = GameViewModel()
        viewModel.startGame(mode = GameMode.ENDLESS)
        viewModel.beginRound()
        val correct = viewModel.stimulus.value!!.correctAnswer
        viewModel.onColorTapped(StroopColor.entries.first { it != correct })
        return viewModel
    }

    @Test
    fun `startGame is ignored while a run is in progress`() = runTest {
        // The game screen calls startGame from an effect, which runs again when the
        // Activity is recreated (theme change, split screen) while this ViewModel survives.
        val viewModel = GameViewModel()
        viewModel.startGame(mode = GameMode.TIME)
        viewModel.beginRound()

        viewModel.startGame(mode = GameMode.ENDLESS)

        assertTrue(viewModel.state.value is GameState.Playing)
        assertEquals(GameMode.TIME, (viewModel.state.value as GameState.Playing).mode)
    }

    @Test
    fun `startGame is ignored once the run is over`() = runTest {
        val viewModel = endedEndlessRun()

        viewModel.startGame()

        assertTrue(viewModel.state.value is GameState.GameOver)
    }

    @Test
    fun `a run can start again after returning to the menu`() = runTest {
        val viewModel = endedEndlessRun()

        viewModel.returnToMenu()
        viewModel.startGame()

        assertEquals(GameState.Countdown, viewModel.state.value)
    }

    @Test
    fun `a finished run is handed over for recording only once`() = runTest {
        // Recreating the Activity re-runs the game-over effect for the same result; recording
        // it again counted the run twice.
        val viewModel = endedEndlessRun()

        assertTrue(viewModel.claimGameOver())
        assertFalse(viewModel.claimGameOver())
    }

    @Test
    fun `there is nothing to claim before the run is over`() = runTest {
        val viewModel = GameViewModel()
        viewModel.startGame()
        viewModel.beginRound()

        assertFalse(viewModel.claimGameOver())
    }

    @Test
    fun `game over keeps the streak the run ended on`() = runTest {
        // It used to be read from the Playing state after the run was already over, so it
        // was always 0. A miss resets the streak, so an ENDLESS run ends on 0.
        val viewModel = endedEndlessRun()

        assertEquals(0, (viewModel.state.value as GameState.GameOver).endStreak)
    }
}
