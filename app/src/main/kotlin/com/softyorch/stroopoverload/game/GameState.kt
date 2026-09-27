package com.softyorch.stroopoverload.game

import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.domain.GameMode
import com.softyorch.stroopoverload.domain.GameResult

sealed interface GameState {
    data object Menu : GameState
    data object Countdown : GameState
    data class Playing(
        val mode: GameMode = GameMode.ENDLESS,
        val score: Int = 0,
        val level: Int = 1,
        val correctHits: Int = 0,
        val totalRounds: Int = 0,
        val survivalMs: Long = 0L,
        val currentStreak: Int = 0,
        val livesRemaining: Int = 0,
        val timeRemainingMs: Long = 0L,
        val missFlashColor: StroopColor? = null,
        val isFrozen: Boolean = false,
        val lastTap: TapFeedback? = null,
    ) : GameState
    /**
     * [endStreak] is the correct-answer streak the run ended on (the XP streak bonus).
     * [finalBoard] is the board as the run ended, kept on screen for a moment before the
     * game-over screen.
     */
    data class GameOver(
        val result: GameResult,
        val endStreak: Int = 0,
        val finalBoard: Playing? = null,
    ) : GameState
}

/** One answer tap, for the board's per-tap flash. [round] tells two taps on the same quadrant apart. */
data class TapFeedback(val color: StroopColor, val isCorrect: Boolean, val round: Int)
