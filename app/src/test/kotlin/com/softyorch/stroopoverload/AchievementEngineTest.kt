package com.softyorch.stroopoverload

import com.softyorch.stroopoverload.domain.AchievementEngine
import com.softyorch.stroopoverload.domain.AchievementProgress
import com.softyorch.stroopoverload.domain.CareerStats
import com.softyorch.stroopoverload.domain.GameResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementEngineTest {

    private val engine = AchievementEngine()

    @Test
    fun `cascade locking blocks achievement when prerequisite is locked`() {
        val career = CareerStats(totalGamesPlayed = 50)
        val alreadyUnlocked = emptySet<String>() // ten_games is NOT unlocked

        val newIds = engine.evaluate(
            game = GameResult(100, 1, 1, 1000L, 0),
            career = career,
            alreadyUnlocked = alreadyUnlocked
        )

        assertTrue("ten_games should unlock", "ten_games" in newIds)
        assertFalse("fifty_games should be blocked by prerequisite", "fifty_games" in newIds)
    }

    @Test
    fun `cascade locking allows achievement when prerequisite is unlocked`() {
        val career = CareerStats(totalGamesPlayed = 50)
        val alreadyUnlocked = setOf("ten_games", "twenty_five_games") // prerequisite IS unlocked

        val newIds = engine.evaluate(
            game = GameResult(100, 1, 1, 1000L, 0),
            career = career,
            alreadyUnlocked = alreadyUnlocked
        )

        assertTrue("fifty_games should unlock", "fifty_games" in newIds)
    }

    @Test
    fun `progressFor returns formatted Count fraction`() {
        val career = CareerStats(totalGamesPlayed = 5)
        val progress = engine.progressFor("ten_games", career) as AchievementProgress.Count

        assertEquals(5, progress.current)
        assertEquals(10, progress.target)
        assertEquals(0.5f, progress.fraction, 0.001f)
    }

    @Test
    fun `crossing multiple score tiers in one game unlocks only the first tier`() {
        // maxScoreEver jumps straight past centurion(2500), score_3k(5000) and score_titan(7500).
        val career = CareerStats(maxScoreEver = 8000)

        val newIds = engine.evaluate(
            game = GameResult(finalScore = 8000, correctHits = 20, totalRounds = 20, survivalMs = 1000L, previousHighScore = 0),
            career = career,
            alreadyUnlocked = emptySet(),
        )

        assertTrue("centurion should unlock", "centurion" in newIds)
        assertFalse("score_3k should be blocked, its prerequisite centurion isn't unlocked yet", "score_3k" in newIds)
        assertFalse("score_titan should be blocked too", "score_titan" in newIds)
    }

    @Test
    fun `next game unlocks the next score tier once the prerequisite is already unlocked`() {
        val career = CareerStats(maxScoreEver = 8000)

        val newIds = engine.evaluate(
            game = GameResult(finalScore = 8000, correctHits = 20, totalRounds = 20, survivalMs = 1000L, previousHighScore = 0),
            career = career,
            alreadyUnlocked = setOf("centurion"),
        )

        assertTrue("score_3k should unlock now that centurion is already unlocked", "score_3k" in newIds)
        assertFalse("score_titan should still be blocked by its own prerequisite", "score_titan" in newIds)
    }

    @Test
    fun `crossing multiple win-streak tiers in one game unlocks only the first tier`() {
        // maxWinStreak jumps straight past streak_master(5), streak_legend(10) and streak_god(20).
        val career = CareerStats(maxWinStreak = 25)

        val newIds = engine.evaluate(
            game = GameResult(finalScore = 100, correctHits = 20, totalRounds = 20, survivalMs = 1000L, previousHighScore = 0),
            career = career,
            alreadyUnlocked = emptySet(),
        )

        assertTrue("streak_master should unlock", "streak_master" in newIds)
        assertFalse("streak_legend should be blocked", "streak_legend" in newIds)
        assertFalse("streak_god should be blocked", "streak_god" in newIds)
    }

    @Test
    fun `crossing multiple survival tiers in one game unlocks only the first tier`() {
        // maxSurvivalTimeMs jumps straight past survival_expert(30s), survival_master(50s) and survival_legend(75s).
        val career = CareerStats(maxSurvivalTimeMs = 80_000L)

        val newIds = engine.evaluate(
            game = GameResult(finalScore = 100, correctHits = 20, totalRounds = 20, survivalMs = 80_000L, previousHighScore = 0),
            career = career,
            alreadyUnlocked = emptySet(),
        )

        assertTrue("survival_expert should unlock", "survival_expert" in newIds)
        assertFalse("survival_master should be blocked", "survival_master" in newIds)
        assertFalse("survival_legend should be blocked", "survival_legend" in newIds)
    }

    @Test
    fun `updatedCareerStats increments cumulative telemetry accurately`() {
        val current = CareerStats(totalGamesPlayed = 10, totalGamesWon = 4, maxScoreEver = 500)
        val game = GameResult(finalScore = 1500, correctHits = 8, totalRounds = 10, survivalMs = 15_000L, previousHighScore = 500, won = true)

        val updated = engine.updatedCareerStats(current, game)

        assertEquals(11, updated.totalGamesPlayed)
        assertEquals(5, updated.totalGamesWon)
        assertEquals(1500, updated.maxScoreEver)
        assertEquals(1, updated.currentWinStreak)
    }
}
