package com.softyorch.stroopoverload.domain

enum class Rarity(val colorArgb: Long, val baseXp: Int, val label: String) {
    COMMON(0xFFB0BAC5, 25, "COMMON // BASE"),
    UNCOMMON(0xFF00C853, 75, "UNCOMMON // NEON"),
    RARE(0xFF4A9EFF, 100, "RARE // CYBER"),
    EPIC(0xFFC06EFF, 250, "EPIC // SYNAPSE"),
    LEGENDARY(0xFFFFD400, 750, "LEGENDARY // SINGULARITY");

    val composeColorArgb: Long get() = colorArgb
}

data class XpBreakdown(
    val baseLabel: String,
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
                baseLabel = "AFK // NO_SIGNAL",
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
        val baseLabel = if (result.won) "CYBER_WIN" else "SYNAPSE_LOSS"

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
            baseLabel = baseLabel,
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
