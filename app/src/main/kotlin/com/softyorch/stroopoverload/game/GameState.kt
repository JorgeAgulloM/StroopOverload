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
    ) : GameState
    data class GameOver(val result: GameResult) : GameState
}
