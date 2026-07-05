package com.softyorch.stroopoverload

import com.softyorch.stroopoverload.domain.GameResult
import com.softyorch.stroopoverload.domain.Rarity
import com.softyorch.stroopoverload.domain.XpSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XpSystemTest {

    @Test
    fun `xpForLevel computes quadratic formula correctly`() {
        assertEquals(104, XpSystem.xpForLevel(1))
        assertEquals(216, XpSystem.xpForLevel(2))
        assertEquals(336, XpSystem.xpForLevel(3))
    }

    @Test
    fun `levelFromTotalXp resolves proper level boundaries`() {
        assertEquals(1, XpSystem.levelFromTotalXp(0))
        assertEquals(1, XpSystem.levelFromTotalXp(103))
        assertEquals(2, XpSystem.levelFromTotalXp(104))
        assertEquals(2, XpSystem.levelFromTotalXp(319))
        assertEquals(3, XpSystem.levelFromTotalXp(320))
        assertEquals(3, XpSystem.levelFromTotalXp(655))
        assertEquals(4, XpSystem.levelFromTotalXp(656))
    }

    @Test
    fun `levelRarity returns expected tiers`() {
        assertEquals(Rarity.COMMON, XpSystem.levelRarity(1))
        assertEquals(Rarity.COMMON, XpSystem.levelRarity(9))
        assertEquals(Rarity.UNCOMMON, XpSystem.levelRarity(10))
        assertEquals(Rarity.RARE, XpSystem.levelRarity(25))
        assertEquals(Rarity.EPIC, XpSystem.levelRarity(50))
        assertEquals(Rarity.LEGENDARY, XpSystem.levelRarity(99))
    }

    @Test
    fun `calculateGameXp computes flawless and high score multiplier correctly`() {
        val res = GameResult(
            finalScore = 1200,
            correctHits = 10,
            totalRounds = 10,
            survivalMs = 25_000L,
            previousHighScore = 1000,
            won = true
        )
        val breakdown = XpSystem.calculateGameXp(res, dailyStreak = 3, currentWinStreak = 2)

        assertTrue(res.isFlawless)
        assertTrue(res.isNewHighScore)
        assertEquals(100, breakdown.perfectBonus)
        assertEquals(100, breakdown.timeBonus)
        assertEquals(30, breakdown.streakBonus)
        assertEquals(60, breakdown.dailyStreakBonus)
        assertEquals(1.5, breakdown.multiplier, 0.001)
    }
}
