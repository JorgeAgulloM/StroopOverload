package com.softyorch.stroopoverload.domain

import androidx.annotation.StringRes

data class Achievement(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val rarity: Rarity,
    val iconEmoji: String,
    val hidden: Boolean = false,
    val unlockedAtEpochMs: Long? = null,
    val xpReward: Int = 0,
) {
    val isUnlocked: Boolean get() = unlockedAtEpochMs != null
    val unlockedAt: Long get() = unlockedAtEpochMs ?: 0L
}

sealed interface AchievementProgress {
    object None : AchievementProgress
    data class Count(val current: Int, val target: Int, val label: String) : AchievementProgress {
        val fraction: Float get() = if (target == 0) 1f else (current.toFloat() / target.toFloat()).coerceIn(0f, 1f)
    }
}
