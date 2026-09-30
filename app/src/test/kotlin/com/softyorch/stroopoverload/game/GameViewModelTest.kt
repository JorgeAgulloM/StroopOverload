package com.softyorch.stroopoverload.game

import app.cash.turbine.test
import com.softyorch.stroopoverload.core.GameConfig
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.domain.GameMode
import com.softyorch.stroopoverload.domain.XpBreakdown
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
    fun `game over keeps the best streak of the run, not the one it ended on`() = runTest {
        // A miss resets the streak, so an ENDLESS run always ends on 0; the XP streak bonus
        // rewards the longest run of correct answers instead.
        val viewModel = GameViewModel()
        viewModel.startGame(mode = GameMode.ENDLESS)
        viewModel.beginRound()

        repeat(3) { tapCorrect(viewModel) }
        tapWrong(viewModel)

        assertEquals(3, (viewModel.state.value as GameState.GameOver).bestStreak)
    }

    @Test
    fun `a later shorter streak does not lower the best streak`() = runTest {
        val viewModel = GameViewModel()
        viewModel.startGame(mode = GameMode.LIVES)
        viewModel.beginRound()

        repeat(2) { tapCorrect(viewModel) }
        tapWrong(viewModel)
        dispatcher.scheduler.advanceTimeBy(GameConfig.LIVES_MODE_FREEZE_MS + 50)
        tapCorrect(viewModel)
        assertEquals(1, (viewModel.state.value as GameState.Playing).currentStreak)
        assertEquals(2, (viewModel.state.value as GameState.Playing).bestStreak)

        repeat(GameConfig.LIVES_MODE_STARTING_LIVES - 1) {
            tapWrong(viewModel)
            dispatcher.scheduler.advanceTimeBy(GameConfig.LIVES_MODE_FREEZE_MS + 50)
        }

        assertEquals(2, (viewModel.state.value as GameState.GameOver).bestStreak)
    }

    @Test
    fun `the recorded outcome outlives the screen and is cleared by the next run`() = runTest {
        // Kept here, not in the game screen: an Activity recreated during the final-board hold
        // re-opens the game-over screen and needs the XP it already earned.
        val viewModel = endedEndlessRun()
        assertNull(viewModel.recordedRun.value)

        val recorded = RecordedRun(XpBreakdown(0, 10, 0, 0, 0, 0, 1.0, 10), emptyList())
        viewModel.onRunRecorded(recorded)
        assertEquals(recorded, viewModel.recordedRun.value)

        viewModel.returnToMenu()
        viewModel.startGame()
        assertNull(viewModel.recordedRun.value)
    }

    @Test
    fun `the spoken distractor colour only starts at level 15`() = runTest {
        // It used to start at level 5, early enough to feel like random noise.
        // Level 15 comes with the 70th right answer (a level every 5 rounds).
        val viewModel = GameViewModel()
        viewModel.startGame(mode = GameMode.TIME)
        viewModel.beginRound()

        repeat(70) {
            assertNull("no voice before level 15 (answer ${it + 1})", viewModel.stimulus.value!!.audioColor)
            viewModel.onColorTapped(viewModel.stimulus.value!!.correctAnswer)
        }

        assertEquals(15, (viewModel.state.value as GameState.Playing).level)
        assertTrue(viewModel.stimulus.value!!.audioColor != null)
    }

    // Virtual time plus whatever a test adds to simulate frames that ran late.
    private var clockLagMs = 0L

    private fun viewModelOnTestClock() =
        GameViewModel(clock = { dispatcher.scheduler.currentTime + clockLagMs })

    private fun tapCorrect(viewModel: GameViewModel) =
        viewModel.onColorTapped(viewModel.stimulus.value!!.correctAnswer)

    private fun tapWrong(viewModel: GameViewModel) {
        val correct = viewModel.stimulus.value!!.correctAnswer
        viewModel.onColorTapped(StroopColor.entries.first { it != correct })
    }

    @Test
    fun `an OVERTIME right answer adds time to the clock`() = runTest {
        val viewModel = viewModelOnTestClock()
        viewModel.startGame(mode = GameMode.OVERTIME)
        viewModel.beginRound()
        assertEquals(30_000L, (viewModel.state.value as GameState.Playing).timeRemainingMs)

        viewModel.onColorTapped(viewModel.stimulus.value!!.correctAnswer)
        dispatcher.scheduler.advanceTimeBy(160)
        dispatcher.scheduler.runCurrent()

        assertEquals(30_000L + 1_000L - 160L, (viewModel.state.value as GameState.Playing).timeRemainingMs)
    }

    @Test
    fun `an OVERTIME miss takes 2 s off the clock and play goes on`() = runTest {
        val viewModel = viewModelOnTestClock()
        viewModel.startGame(mode = GameMode.OVERTIME)
        viewModel.beginRound()

        tapWrong(viewModel)
        dispatcher.scheduler.advanceTimeBy(160)
        dispatcher.scheduler.runCurrent()

        val playing = viewModel.state.value as GameState.Playing
        assertEquals(30_000L - 2_000L - 160L, playing.timeRemainingMs)
        assertEquals(1, playing.totalRounds)
    }

    @Test
    fun `OVERTIME ends when its clock runs out and records all the time played`() = runTest {
        // Unlike TIME's fixed minute, the run is as long as the clock lasted.
        val viewModel = viewModelOnTestClock()
        viewModel.startGame(mode = GameMode.OVERTIME)
        viewModel.beginRound()
        viewModel.onColorTapped(viewModel.stimulus.value!!.correctAnswer) // +1 s

        dispatcher.scheduler.advanceTimeBy(31_100)
        dispatcher.scheduler.runCurrent()

        val over = viewModel.state.value as GameState.GameOver
        assertEquals(GameMode.OVERTIME, over.result.mode)
        // It ends on the first 16 ms frame past the 31 s deadline.
        assertTrue(over.result.survivalMs in 31_000L..31_016L)
    }

    @Test
    fun `the OVERTIME bonus shrinks with the level down to a floor`() {
        assertEquals(1_000L, overtimeBonusMs(1))
        assertEquals(900L, overtimeBonusMs(2))
        assertEquals(300L, overtimeBonusMs(8))
        assertEquals(300L, overtimeBonusMs(20))
    }

    @Test
    fun `a TIME run records the full minute even when frames run late`() = runTest {
        // Every frame used to add a fixed 16 ms, but real frames take longer, so a 60 s run
        // was recorded as ~57 s.
        val viewModel = viewModelOnTestClock()
        viewModel.startGame(mode = GameMode.TIME)
        viewModel.beginRound()

        dispatcher.scheduler.advanceTimeBy(30_000)
        clockLagMs = 3_000 // the main thread stalled for 3 s that no frame saw
        dispatcher.scheduler.advanceTimeBy(30_000)
        dispatcher.scheduler.runCurrent()

        val over = viewModel.state.value as GameState.GameOver
        assertEquals(GameConfig.TIME_MODE_DURATION_MS, over.result.survivalMs)
    }

    @Test
    fun `an ENDLESS run records the real time played`() = runTest {
        val viewModel = viewModelOnTestClock()
        viewModel.startGame(mode = GameMode.ENDLESS)
        viewModel.beginRound()

        dispatcher.scheduler.advanceTimeBy(1_000)
        clockLagMs = 500
        tapWrong(viewModel)

        assertEquals(1_500L, (viewModel.state.value as GameState.GameOver).result.survivalMs)
    }

    @Test
    fun `LIVES survival time leaves out the freeze after each miss`() = runTest {
        val viewModel = viewModelOnTestClock()
        viewModel.startGame(mode = GameMode.LIVES)
        viewModel.beginRound()

        dispatcher.scheduler.advanceTimeBy(1_000)
        tapWrong(viewModel)
        dispatcher.scheduler.advanceTimeBy(GameConfig.LIVES_MODE_FREEZE_MS)
        dispatcher.scheduler.runCurrent()
        dispatcher.scheduler.advanceTimeBy(200)
        tapWrong(viewModel)
        dispatcher.scheduler.advanceTimeBy(GameConfig.LIVES_MODE_FREEZE_MS)
        dispatcher.scheduler.runCurrent()
        dispatcher.scheduler.advanceTimeBy(300)
        tapWrong(viewModel)

        assertEquals(1_500L, (viewModel.state.value as GameState.GameOver).result.survivalMs)
    }

    @Test
    fun `LIVES mode ends the run at once on the last life`() = runTest {
        // The game-over hold shows the last miss, so it no longer waits out a freeze first.
        val viewModel = GameViewModel()
        viewModel.startGame(mode = GameMode.LIVES)
        viewModel.beginRound()

        repeat(GameConfig.LIVES_MODE_STARTING_LIVES - 1) {
            tapWrong(viewModel)
            dispatcher.scheduler.advanceTimeBy(GameConfig.LIVES_MODE_FREEZE_MS + 50)
            dispatcher.scheduler.runCurrent()
        }
        tapWrong(viewModel)

        assertTrue(viewModel.state.value is GameState.GameOver)
    }

    @Test
    fun `every answer tap is reported with its quadrant and whether it was right`() = runTest {
        val viewModel = GameViewModel()
        viewModel.startGame(mode = GameMode.TIME)
        viewModel.beginRound()

        val correct = viewModel.stimulus.value!!.correctAnswer
        viewModel.onColorTapped(correct)
        assertEquals(
            TapFeedback(correct, isCorrect = true, seq = 1),
            (viewModel.state.value as GameState.Playing).lastTap,
        )

        val nextCorrect = viewModel.stimulus.value!!.correctAnswer
        val wrong = StroopColor.entries.first { it != nextCorrect }
        viewModel.onColorTapped(wrong)
        assertEquals(
            TapFeedback(wrong, isCorrect = false, seq = 2),
            (viewModel.state.value as GameState.Playing).lastTap,
        )
    }

    @Test
    fun `the board that ended the run shows the wrong tap and the right answer`() = runTest {
        // ENDLESS ends on the first miss; the screen holds this board for a moment so the
        // player sees what went wrong.
        val viewModel = GameViewModel()
        viewModel.startGame(mode = GameMode.ENDLESS)
        viewModel.beginRound()
        val correct = viewModel.stimulus.value!!.correctAnswer
        val wrong = StroopColor.entries.first { it != correct }

        viewModel.onColorTapped(wrong)

        val board = (viewModel.state.value as GameState.GameOver).finalBoard!!
        assertEquals(TapFeedback(wrong, isCorrect = false, seq = 1), board.lastTap)
        assertEquals(correct, board.missFlashColor)
    }
}
