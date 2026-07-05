package com.softyorch.stroopoverload.game

import com.softyorch.stroopoverload.domain.GameResult

sealed interface GameState {
    data object Menu : GameState
    data class Playing(
        val score: Int = 0,
        val level: Int = 1,
        val correctHits: Int = 0,
        val totalRounds: Int = 0,
        val survivalMs: Long = 0L,
        val currentStreak: Int = 0,
    ) : GameState
    data class GameOver(val result: GameResult) : GameState
}
