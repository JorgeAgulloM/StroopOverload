package com.softyorch.stroopoverload.data

import android.content.Context
import com.softyorch.stroopoverload.BuildConfig
import com.softyorch.stroopoverload.data.local.AchievementsLocalStore
import com.softyorch.stroopoverload.data.local.ProfileLocalStore
import com.softyorch.stroopoverload.domain.CareerStats
import com.softyorch.stroopoverload.domain.UserProfile

object AsoDemoSeeder {

    const val DEMO_VIP_USER_ID = "demo_vip_aso_777"

    fun seedIfNeeded(context: Context) {
        if (BuildConfig.FLAVOR != "dev") return

        val profileStore = ProfileLocalStore(context)
        val currentProfile = profileStore.getProfile() ?: UserProfile()
        if (currentProfile.userId == DEMO_VIP_USER_ID && currentProfile.experience > 0L) {
            return // Already seeded
        }

        forceSeed(context)
    }

    fun forceSeed(context: Context) {
        val profileStore = ProfileLocalStore(context)
        val achStore = AchievementsLocalStore(context)

        val vipProfile = UserProfile(
            userId = DEMO_VIP_USER_ID,
            uniqueName = "@cyber-viper-7777",
            nickname = "CYBER_VIPER",
            isAnonymous = false, // Shows as verified/logged-in pilot
            avatarIndex = 3,
            points = 14850,
            highScore = 3850,
            matchesPlayed = 142,
            matchesWon = 118,
            matchesLost = 24,
            experience = 84500L,
            level = 42,
            dailyStreak = 14,
            lastPlayedAtEpochMs = System.currentTimeMillis() - 3600_000L,
            profileCreated = true,
            unlockedPalettes = listOf("default", "neon_pulse", "cyber_dark", "acid_matrix"),
            isAdFree = true,
            isPremium = true
        )
        profileStore.saveProfile(vipProfile)

        achStore.seedIfNeeded()
        val career = CareerStats(
            id = 0,
            totalGamesPlayed = 142,
            totalGamesWon = 118,
            totalCorrectHits = 1420,
            totalRoundsPlayed = 1450,
            maxScoreEver = 3850,
            maxSurvivalTimeMs = 48_000L,
            flawlessGamesCount = 19,
            currentWinStreak = 14,
            maxWinStreak = 24,
            cyberDifficultyWins = 65,
            speedRunWins = 38,
            lastPlayedEpochMs = System.currentTimeMillis() - 3600_000L
        )
        achStore.updateCareerStats(career)

        val unlockedAchIds = listOf(
            "first_step", "ten_games", "fifty_games", "flawless_run", "flawless_five",
            "speed_demon", "streak_master", "streak_legend", "high_scorer",
            "marathon_master", "clutch_save", "cyber_veteran"
        )
        val now = System.currentTimeMillis()
        unlockedAchIds.forEachIndexed { i, id ->
            achStore.unlock(id, now - (unlockedAchIds.size - i) * 86400_000L)
        }
    }
}
