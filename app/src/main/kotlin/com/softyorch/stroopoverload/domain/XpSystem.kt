package com.softyorch.stroopoverload.domain

import androidx.annotation.StringRes
import com.softyorch.stroopoverload.R

enum class Rarity(val colorArgb: Long, val baseXp: Int, @StringRes val labelRes: Int) {
    COMMON(0xFFB0BAC5, 25, R.string.rarity_common),
    UNCOMMON(0xFF00C853, 75, R.string.rarity_uncommon),
    RARE(0xFF4A9EFF, 100, R.string.rarity_rare),
    EPIC(0xFFC06EFF, 250, R.string.rarity_epic),
    LEGENDARY(0xFFFFD400, 750, R.string.rarity_legendary);

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
    /** Tier title shown every 10 levels. Levels beyond the last tier keep the final title. */
    private val LEVEL_TITLES = intArrayOf(
        R.string.level_title_tier_01,
        R.string.level_title_tier_02,
        R.string.level_title_tier_03,
        R.string.level_title_tier_04,
        R.string.level_title_tier_05,
        R.string.level_title_tier_06,
        R.string.level_title_tier_07,
        R.string.level_title_tier_08,
        R.string.level_title_tier_09,
        R.string.level_title_tier_10,
        R.string.level_title_tier_11,
        R.string.level_title_tier_12,
        R.string.level_title_tier_13,
        R.string.level_title_tier_14,
        R.string.level_title_tier_15,
        R.string.level_title_tier_16,
        R.string.level_title_tier_17,
        R.string.level_title_tier_18,
        R.string.level_title_tier_19,
        R.string.level_title_tier_20,
    )

    @StringRes
    fun titleResForLevel(level: Int): Int {
        val tier = ((level - 1) / 10).coerceIn(0, LEVEL_TITLES.size - 1)
        return LEVEL_TITLES[tier]
    }

    /** XP needed to go from level (n-1) to level n. Uncapped: leveling continues forever. */
    fun xpForLevel(level: Int): Int = level * (400 + 16 * level) / 5

    fun levelFromTotalXp(totalXp: Long): Int {
        var level = 1
        var remaining = totalXp
        while (true) {
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
        // TIME mode's survivalMs is just the fixed session clock counting down, not a skill
        // signal -- nearly every completed run would trivially clear both thresholds regardless
        // of performance, so the survival-time bonus only applies to ENDLESS/LIVES.
        val timeBonus = if (result.mode == GameMode.TIME) 0 else when {
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
