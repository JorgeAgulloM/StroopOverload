package com.softyorch.stroopoverload.domain

class AchievementEngine {
    fun evaluate(
        game: GameResult,
        career: CareerStats,
        alreadyUnlocked: Set<String>,
    ): Set<String> {
        if (game.correctHits == 0 || game.finalScore <= 0) {
            return emptySet()
        }

        val newIds = mutableSetOf<String>()

        fun check(id: String, condition: Boolean): Boolean {
            val prerequisite = AchievementDefinitions.PREREQUISITES[id]
            val prerequisiteMet = prerequisite == null || prerequisite in alreadyUnlocked
            return condition && prerequisiteMet
        }

        fun tryUnlock(id: String, condition: Boolean) {
            if (id !in alreadyUnlocked && check(id, condition)) {
                newIds.add(id)
            }
        }

        tryUnlock("first_blood", game.correctHits >= 5)
        tryUnlock("ten_games", career.totalGamesPlayed >= 10)
        tryUnlock("twenty_five_games", career.totalGamesPlayed >= 25)
        tryUnlock("fifty_games", career.totalGamesPlayed >= 50)
        tryUnlock("hundred_games", career.totalGamesPlayed >= 100)
        tryUnlock("two_hundred_games", career.totalGamesPlayed >= 200)
        tryUnlock("five_hundred_games", career.totalGamesPlayed >= 500)

        tryUnlock("five_wins", career.totalGamesWon >= 5)
        tryUnlock("fifteen_wins", career.totalGamesWon >= 15)
        tryUnlock("thirty_wins", career.totalGamesWon >= 30)
        tryUnlock("fifty_wins", career.totalGamesWon >= 50)
        tryUnlock("hundred_wins", career.totalGamesWon >= 100)
        tryUnlock("two_hundred_wins", career.totalGamesWon >= 200)

        tryUnlock("centurion", career.maxScoreEver >= 2500)
        tryUnlock("score_3k", career.maxScoreEver >= 5000)
        tryUnlock("score_titan", career.maxScoreEver >= 7500)
        tryUnlock("score_overlord", career.maxScoreEver >= 10500)
        tryUnlock("score_god", career.maxScoreEver >= 14000)

        tryUnlock("flawless", career.flawlessGamesCount >= 1)
        tryUnlock("flawless_tier2", career.flawlessGamesCount >= 5)
        tryUnlock("flawless_tier3", career.flawlessGamesCount >= 15)
        tryUnlock("flawless_tier4", career.flawlessGamesCount >= 30)

        tryUnlock("survival_expert", career.maxSurvivalTimeMs >= 30_000L)
        tryUnlock("survival_master", career.maxSurvivalTimeMs >= 50_000L)
        tryUnlock("survival_legend", career.maxSurvivalTimeMs >= 75_000L)
        tryUnlock("survival_god", career.maxSurvivalTimeMs >= 100_000L)

        tryUnlock("streak_master", career.maxWinStreak >= 5)
        tryUnlock("streak_legend", career.maxWinStreak >= 10)
        tryUnlock("streak_god", career.maxWinStreak >= 20)

        tryUnlock("cyber_veteran", career.cyberDifficultyWins >= 25)

        return newIds
    }

    fun progressFor(id: String, career: CareerStats): AchievementProgress = when (id) {
        "first_blood" -> AchievementProgress.Count(career.totalCorrectHits.coerceAtMost(5), 5, "${career.totalCorrectHits}/5 hits")
        "ten_games" -> AchievementProgress.Count(career.totalGamesPlayed.coerceAtMost(10), 10, "${career.totalGamesPlayed}/10 games")
        "twenty_five_games" -> AchievementProgress.Count(career.totalGamesPlayed.coerceAtMost(25), 25, "${career.totalGamesPlayed}/25 games")
        "fifty_games" -> AchievementProgress.Count(career.totalGamesPlayed.coerceAtMost(50), 50, "${career.totalGamesPlayed}/50 games")
        "hundred_games" -> AchievementProgress.Count(career.totalGamesPlayed.coerceAtMost(100), 100, "${career.totalGamesPlayed}/100 games")
        "two_hundred_games" -> AchievementProgress.Count(career.totalGamesPlayed.coerceAtMost(200), 200, "${career.totalGamesPlayed}/200 games")
        "five_hundred_games" -> AchievementProgress.Count(career.totalGamesPlayed.coerceAtMost(500), 500, "${career.totalGamesPlayed}/500 games")
        "five_wins" -> AchievementProgress.Count(career.totalGamesWon.coerceAtMost(5), 5, "${career.totalGamesWon}/5 wins")
        "fifteen_wins" -> AchievementProgress.Count(career.totalGamesWon.coerceAtMost(15), 15, "${career.totalGamesWon}/15 wins")
        "thirty_wins" -> AchievementProgress.Count(career.totalGamesWon.coerceAtMost(30), 30, "${career.totalGamesWon}/30 wins")
        "fifty_wins" -> AchievementProgress.Count(career.totalGamesWon.coerceAtMost(50), 50, "${career.totalGamesWon}/50 wins")
        "hundred_wins" -> AchievementProgress.Count(career.totalGamesWon.coerceAtMost(100), 100, "${career.totalGamesWon}/100 wins")
        "two_hundred_wins" -> AchievementProgress.Count(career.totalGamesWon.coerceAtMost(200), 200, "${career.totalGamesWon}/200 wins")
        "centurion" -> AchievementProgress.Count(career.maxScoreEver.coerceAtMost(2500), 2500, "${career.maxScoreEver}/2500 pts")
        "score_3k" -> AchievementProgress.Count(career.maxScoreEver.coerceAtMost(5000), 5000, "${career.maxScoreEver}/5000 pts")
        "score_titan" -> AchievementProgress.Count(career.maxScoreEver.coerceAtMost(7500), 7500, "${career.maxScoreEver}/7500 pts")
        "score_overlord" -> AchievementProgress.Count(career.maxScoreEver.coerceAtMost(10500), 10500, "${career.maxScoreEver}/10500 pts")
        "score_god" -> AchievementProgress.Count(career.maxScoreEver.coerceAtMost(14000), 14000, "${career.maxScoreEver}/14000 pts")
        "flawless" -> AchievementProgress.Count(career.flawlessGamesCount.coerceAtMost(1), 1, "${career.flawlessGamesCount}/1 flawless")
        "flawless_tier2" -> AchievementProgress.Count(career.flawlessGamesCount.coerceAtMost(5), 5, "${career.flawlessGamesCount}/5 flawless")
        "flawless_tier3" -> AchievementProgress.Count(career.flawlessGamesCount.coerceAtMost(15), 15, "${career.flawlessGamesCount}/15 flawless")
        "flawless_tier4" -> AchievementProgress.Count(career.flawlessGamesCount.coerceAtMost(30), 30, "${career.flawlessGamesCount}/30 flawless")
        "survival_expert" -> AchievementProgress.Count((career.maxSurvivalTimeMs / 1000).toInt().coerceAtMost(30), 30, "${career.maxSurvivalTimeMs / 1000}s/30s")
        "survival_master" -> AchievementProgress.Count((career.maxSurvivalTimeMs / 1000).toInt().coerceAtMost(50), 50, "${career.maxSurvivalTimeMs / 1000}s/50s")
        "survival_legend" -> AchievementProgress.Count((career.maxSurvivalTimeMs / 1000).toInt().coerceAtMost(75), 75, "${career.maxSurvivalTimeMs / 1000}s/75s")
        "survival_god" -> AchievementProgress.Count((career.maxSurvivalTimeMs / 1000).toInt().coerceAtMost(100), 100, "${career.maxSurvivalTimeMs / 1000}s/100s")
        "streak_master" -> AchievementProgress.Count(career.maxWinStreak.coerceAtMost(5), 5, "${career.maxWinStreak}/5 streak")
        "streak_legend" -> AchievementProgress.Count(career.maxWinStreak.coerceAtMost(10), 10, "${career.maxWinStreak}/10 streak")
        "streak_god" -> AchievementProgress.Count(career.maxWinStreak.coerceAtMost(20), 20, "${career.maxWinStreak}/20 streak")
        "cyber_veteran" -> AchievementProgress.Count(career.cyberDifficultyWins.coerceAtMost(25), 25, "${career.cyberDifficultyWins}/25 cyber")
        else -> AchievementProgress.None
    }

    fun updatedCareerStats(current: CareerStats, game: GameResult): CareerStats {
        if (game.correctHits == 0 || game.finalScore <= 0) {
            return current.copy(lastPlayedEpochMs = System.currentTimeMillis())
        }
        val newGamesPlayed = current.totalGamesPlayed + 1
        val newGamesWon = if (game.won) current.totalGamesWon + 1 else current.totalGamesWon
        val newHits = current.totalCorrectHits + game.correctHits
        val newRounds = current.totalRoundsPlayed + game.totalRounds
        val newMaxScore = maxOf(current.maxScoreEver, game.finalScore)
        // TIME mode's survivalMs is just the fixed session clock (see GameConfig.TIME_MODE_DURATION_MS),
        // not a skill signal like it is in ENDLESS/LIVES -- feeding it in would make the low survival
        // tiers trivially free and the high tiers mathematically unreachable past that cap.
        val newMaxSurvival = if (game.mode == GameMode.TIME) current.maxSurvivalTimeMs else maxOf(current.maxSurvivalTimeMs, game.survivalMs)
        val newFlawless = if (game.isFlawless) current.flawlessGamesCount + 1 else current.flawlessGamesCount
        val newWinStreak = if (game.won) current.currentWinStreak + 1 else 0
        val newMaxStreak = maxOf(current.maxWinStreak, newWinStreak)
        val newCyberWins = if (game.won && game.aiDifficulty == AiDifficulty.OVERLOAD_CYBER) current.cyberDifficultyWins + 1 else current.cyberDifficultyWins
        val newSpeedWins = if (game.won && game.durationSeconds <= 12 && game.correctHits >= 10) current.speedRunWins + 1 else current.speedRunWins
        val now = System.currentTimeMillis()

        return current.copy(
            totalGamesPlayed = newGamesPlayed,
            totalGamesWon = newGamesWon,
            totalCorrectHits = newHits,
            totalRoundsPlayed = newRounds,
            maxScoreEver = newMaxScore,
            maxSurvivalTimeMs = newMaxSurvival,
            flawlessGamesCount = newFlawless,
            currentWinStreak = newWinStreak,
            maxWinStreak = newMaxStreak,
            cyberDifficultyWins = newCyberWins,
            speedRunWins = newSpeedWins,
            lastPlayedEpochMs = now,
        )
    }
}
