package com.softyorch.stroopoverload.domain

enum class OpponentType {
    AI, LOCAL_HUMAN, ONLINE_FRIEND, ONLINE_RANDOM, SINGLE_PLAYER_CHALLENGE
}

enum class AiDifficulty {
    EASY, MEDIUM, HARD, OVERLOAD_CYBER
}

data class GameResult(
    val finalScore: Int,
    val correctHits: Int,
    val totalRounds: Int,
    val survivalMs: Long,
    val previousHighScore: Int,
    val won: Boolean = finalScore > 0 && (totalRounds >= 5 && (correctHits.toFloat() / totalRounds) >= 0.7f),
    val opponentType: OpponentType = OpponentType.SINGLE_PLAYER_CHALLENGE,
    val aiDifficulty: AiDifficulty? = AiDifficulty.OVERLOAD_CYBER,
    val durationSeconds: Int = (survivalMs / 1000).toInt(),
    val moveCount: Int = totalRounds,
    val newLevel: Int = 0,
    val isDraw: Boolean = false,
) {
    val accuracy: Int get() =
        if (totalRounds == 0) 0 else ((correctHits.toFloat() / totalRounds) * 100).toInt()

    val isNewHighScore: Boolean get() = finalScore > previousHighScore

    val isFlawless: Boolean get() = totalRounds >= 5 && correctHits == totalRounds
}
