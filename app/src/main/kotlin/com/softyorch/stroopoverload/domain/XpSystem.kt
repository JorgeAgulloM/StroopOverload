package com.softyorch.stroopoverload.domain

import androidx.annotation.StringRes
import com.softyorch.stroopoverload.R

enum class Rarity(val colorArgb: Long, val baseXp: Int) {
    COMMON(0xFFB0BAC5, 25),
    UNCOMMON(0xFF00C853, 75),
    RARE(0xFF4A9EFF, 100),
    EPIC(0xFFC06EFF, 250),
    LEGENDARY(0xFFFFD400, 750);

    val composeColorArgb: Long get() = colorArgb
}

data class XpBreakdown(
    @StringRes val baseLabelRes: Int,
    val base: Int,
    val perfectBonus: Int,
    val timeBonus: Int,
    val streakBonus: Int,
    val dailyStreakBonus: Int,
    val multiplier: Double,
    val total: Int,
)

object XpSystem {
    fun xpForLevel(level: Int): Int = 100 * level + 4 * level * level

    fun levelFromTotalXp(totalXp: Long): Int {
        var level = 1
        var remaining = totalXp
        while (level < 99) {
            val need = xpForLevel(level)
            if (remaining < need) break
            remaining -= need
            level++
        }
        return level
    }

    fun xpProgressInCurrentLevel(totalXp: Long): Pair<Int, Int> {
        val currentLevel = levelFromTotalXp(totalXp)
        var accumulatedForCurrent = 0L
        for (l in 1 until currentLevel) {
            accumulatedForCurrent += xpForLevel(l)
        }
        val xpInCurrent = (totalXp - accumulatedForCurrent).toInt().coerceAtLeast(0)
        val neededForNext = xpForLevel(currentLevel)
        return Pair(xpInCurrent, neededForNext)
    }

    fun levelRarity(level: Int): Rarity = when {
        level >= 99 -> Rarity.LEGENDARY
        level >= 50 -> Rarity.EPIC
        level >= 25 -> Rarity.RARE
        level >= 10 -> Rarity.UNCOMMON
        else -> Rarity.COMMON
    }

    fun calculateGameXp(
        result: GameResult,
        dailyStreak: Int,
        currentWinStreak: Int = 0,
    ): XpBreakdown {
        if (result.correctHits == 0 || result.finalScore <= 0) {
            return XpBreakdown(
                baseLabelRes = R.string.xp_base_afk,
                base = 0,
                perfectBonus = 0,
                timeBonus = 0,
                streakBonus = 0,
                dailyStreakBonus = 0,
                multiplier = 1.0,
                total = 0,
            )
        }

        val base = if (result.won) {
            (result.correctHits * 15) + 50
        } else {
            result.correctHits * 5
        }
        val baseLabelRes = if (result.won) R.string.xp_base_win else R.string.xp_base_loss

        val perfectBonus = if (result.isFlawless) 100 else 0
        val timeBonus = when {
            result.survivalMs >= 20_000L -> 100
            result.survivalMs >= 10_000L -> 50
            else -> 0
        }
        val streakBonus = (currentWinStreak * 15).coerceAtMost(150)
        val dailyBonus = (dailyStreak * 20).coerceAtMost(200)

        val multiplier = if (result.isNewHighScore) 1.5 else 1.0
        val sum = base + perfectBonus + timeBonus + streakBonus + dailyBonus
        val total = (sum * multiplier).toInt().coerceAtLeast(0)

        return XpBreakdown(
            baseLabelRes = baseLabelRes,
            base = base,
            perfectBonus = perfectBonus,
            timeBonus = timeBonus,
            streakBonus = streakBonus,
            dailyStreakBonus = dailyBonus,
            multiplier = multiplier,
            total = total,
        )
    }
}
