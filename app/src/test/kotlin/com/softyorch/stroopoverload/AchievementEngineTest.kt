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
