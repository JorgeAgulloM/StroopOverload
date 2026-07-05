package com.softyorch.stroopoverload.domain

data class CareerStats(
    val id: Int = 0,
    val totalGamesPlayed: Int = 0,
    val totalGamesWon: Int = 0,
    val totalCorrectHits: Int = 0,
    val totalRoundsPlayed: Int = 0,
    val maxScoreEver: Int = 0,
    val maxSurvivalTimeMs: Long = 0L,
    val flawlessGamesCount: Int = 0,
    val currentWinStreak: Int = 0,
    val maxWinStreak: Int = 0,
    val cyberDifficultyWins: Int = 0,
    val speedRunWins: Int = 0,
    val lastPlayedEpochMs: Long = 0L,
) {
    fun toMap(): Map<String, Long> = mapOf(
        "totalGamesPlayed" to totalGamesPlayed.toLong(),
        "totalGamesWon" to totalGamesWon.toLong(),
        "totalCorrectHits" to totalCorrectHits.toLong(),
        "totalRoundsPlayed" to totalRoundsPlayed.toLong(),
        "maxScoreEver" to maxScoreEver.toLong(),
        "maxSurvivalTimeMs" to maxSurvivalTimeMs,
        "flawlessGamesCount" to flawlessGamesCount.toLong(),
        "currentWinStreak" to currentWinStreak.toLong(),
        "maxWinStreak" to maxWinStreak.toLong(),
        "cyberDifficultyWins" to cyberDifficultyWins.toLong(),
        "speedRunWins" to speedRunWins.toLong(),
        "lastPlayedEpochMs" to lastPlayedEpochMs,
    )

    companion object {
        fun fromMap(map: Map<String, Long>): CareerStats = CareerStats(
            totalGamesPlayed = (map["totalGamesPlayed"] ?: 0L).toInt(),
            totalGamesWon = (map["totalGamesWon"] ?: 0L).toInt(),
            totalCorrectHits = (map["totalCorrectHits"] ?: 0L).toInt(),
            totalRoundsPlayed = (map["totalRoundsPlayed"] ?: 0L).toInt(),
            maxScoreEver = (map["maxScoreEver"] ?: 0L).toInt(),
            maxSurvivalTimeMs = map["maxSurvivalTimeMs"] ?: 0L,
            flawlessGamesCount = (map["flawlessGamesCount"] ?: 0L).toInt(),
            currentWinStreak = (map["currentWinStreak"] ?: 0L).toInt(),
            maxWinStreak = (map["maxWinStreak"] ?: 0L).toInt(),
            cyberDifficultyWins = (map["cyberDifficultyWins"] ?: 0L).toInt(),
            speedRunWins = (map["speedRunWins"] ?: 0L).toInt(),
            lastPlayedEpochMs = map["lastPlayedEpochMs"] ?: 0L,
        )
    }
}
