package com.softyorch.stroopoverload

import com.softyorch.stroopoverload.domain.GameResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameResultTest {

    @Test
    fun `accuracy computes correctly`() {
        val r = GameResult(500, correctHits = 5, totalRounds = 10, survivalMs = 10_000, previousHighScore = 0)
        assertEquals(50, r.accuracy)
    }

    @Test
    fun `accuracy is 0 when totalRounds is 0`() {
        val r = GameResult(0, correctHits = 0, totalRounds = 0, survivalMs = 0, previousHighScore = 0)
        assertEquals(0, r.accuracy)
    }

    @Test
    fun `isNewHighScore true when finalScore exceeds previousHighScore`() {
        val r = GameResult(800, correctHits = 8, totalRounds = 8, survivalMs = 20_000, previousHighScore = 300)
        assertTrue(r.isNewHighScore)
    }

    @Test
    fun `isNewHighScore false when finalScore does not exceed previousHighScore`() {
        val r = GameResult(200, correctHits = 2, totalRounds = 5, survivalMs = 5_000, previousHighScore = 1000)
        assertFalse(r.isNewHighScore)
    }
}
