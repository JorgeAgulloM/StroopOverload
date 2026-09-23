package com.softyorch.stroopoverload.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRunScoringTest {

    private val day = 24L * 60 * 60 * 1000

    private fun run(finalScore: Int = 500, correctHits: Int = 9, totalRounds: Int = 10) =
        GameResult(
            finalScore = finalScore,
            correctHits = correctHits,
            totalRounds = totalRounds,
            survivalMs = 20_000L,
            previousHighScore = 0,
        )

    private val win = run()
    private val loss = run(correctHits = 3)

    @Test
    fun `a run without a correct answer or without score is not recorded`() {
        assertFalse(run(correctHits = 0).isRecordable())
        assertFalse(run(finalScore = 0).isRecordable())
        assertTrue(win.isRecordable())
    }

    @Test
    fun `a win adds 100 points and counts as won`() {
        val after = UserProfile(points = 40, matchesWon = 2).withRunApplied(win, xpEarned = 0, nowMs = day)

        assertEquals(140, after.points)
        assertEquals(3, after.matchesWon)
        assertEquals(0, after.matchesLost)
        assertEquals(1, after.matchesPlayed)
    }

    @Test
    fun `a loss costs 25 points but never goes below zero`() {
        assertEquals(75, UserProfile(points = 100).withRunApplied(loss, 0, day).points)
        assertEquals(0, UserProfile(points = 10).withRunApplied(loss, 0, day).points)
        assertEquals(1, UserProfile().withRunApplied(loss, 0, day).matchesLost)
    }

    @Test
    fun `high score only ever goes up`() {
        assertEquals(900, UserProfile(highScore = 900).withRunApplied(run(finalScore = 500), 0, day).highScore)
        assertEquals(500, UserProfile(highScore = 100).withRunApplied(run(finalScore = 500), 0, day).highScore)
    }

    @Test
    fun `XP accumulates and the level follows it`() {
        val after = UserProfile(experience = 50L).withRunApplied(win, xpEarned = 100, nowMs = day)

        assertEquals(150L, after.experience)
        assertEquals(XpSystem.levelFromTotalXp(150L), after.level)
        assertEquals(2, after.level)
    }

    @Test
    fun `daily streak holds on the same day, grows the next and restarts after a gap`() {
        val lastPlayed = 10 * day + 5_000
        assertEquals(4, nextDailyStreak(4, lastPlayed, 10 * day + 80_000))
        assertEquals(5, nextDailyStreak(4, lastPlayed, 11 * day + 1))
        assertEquals(1, nextDailyStreak(4, lastPlayed, 13 * day))
        assertEquals(1, nextDailyStreak(0, 0L, 20 * day))
    }

    @Test
    fun `applying a run stamps when it was played`() {
        val after = UserProfile(dailyStreak = 2, lastPlayedAtEpochMs = day).withRunApplied(win, 0, 2 * day + 7)

        assertEquals(2 * day + 7, after.lastPlayedAtEpochMs)
        assertEquals(3, after.dailyStreak)
    }

    @Test
    fun `achievement XP raises the level, and no bonus leaves the profile untouched`() {
        val profile = UserProfile(experience = 80L, level = 1)

        assertSame(profile, profile.withAchievementXp(0))
        val boosted = profile.withAchievementXp(10)
        assertEquals(90L, boosted.experience)
        assertEquals(2, boosted.level)
    }
}
