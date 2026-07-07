package com.softyorch.stroopoverload

import com.softyorch.stroopoverload.domain.GameResult
import com.softyorch.stroopoverload.domain.Rarity
import com.softyorch.stroopoverload.domain.XpSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XpSystemTest {

    @Test
    fun `xpForLevel computes the curve correctly`() {
        assertEquals(83, XpSystem.xpForLevel(1))
        assertEquals(172, XpSystem.xpForLevel(2))
        assertEquals(268, XpSystem.xpForLevel(3))
    }

    @Test
    fun `levelFromTotalXp resolves proper level boundaries`() {
        assertEquals(1, XpSystem.levelFromTotalXp(0))
        assertEquals(1, XpSystem.levelFromTotalXp(82))
        assertEquals(2, XpSystem.levelFromTotalXp(83))
        assertEquals(2, XpSystem.levelFromTotalXp(254))
        assertEquals(3, XpSystem.levelFromTotalXp(255))
        assertEquals(3, XpSystem.levelFromTotalXp(522))
        assertEquals(4, XpSystem.levelFromTotalXp(523))
    }

    @Test
    fun `leveling has no cap`() {
        // Old formula hard-capped at level 99; the curve must now climb forever.
        assertTrue(XpSystem.levelFromTotalXp(10_000_000L) > 99)
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
