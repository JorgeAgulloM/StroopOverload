package com.stroopoverload.domain

data class GameResult(
    val finalScore: Int,
    val correctHits: Int,
    val totalRounds: Int,
    val survivalMs: Long,
    val previousHighScore: Int,
) {
    val accuracy: Int get() =
        if (totalRounds == 0) 0 else ((correctHits.toFloat() / totalRounds) * 100).toInt()

    val xpEarned: Int get() = (survivalMs / 1000 * 10).toInt()

    val isNewHighScore: Boolean get() = finalScore > previousHighScore
}
