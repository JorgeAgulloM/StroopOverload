package com.softyorch.stroopoverload.data.local

import android.content.Context
import android.content.SharedPreferences
import com.softyorch.stroopoverload.domain.UserProfile

class ProfileLocalStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("stroop_user_profile", Context.MODE_PRIVATE)

    fun saveProfile(profile: UserProfile) {
        prefs.edit()
            .putString(KEY_USER_ID, profile.userId)
            .putString(KEY_UNIQUE_NAME, profile.uniqueName)
            .putString(KEY_NICKNAME, profile.nickname)
            .putBoolean(KEY_IS_ANON, profile.isAnonymous)
            .putInt(KEY_AVATAR_INDEX, profile.avatarIndex)
            .putInt(KEY_POINTS, profile.points)
            .putInt(KEY_HIGH_SCORE, profile.highScore)
            .putInt(KEY_MATCHES_PLAYED, profile.matchesPlayed)
            .putInt(KEY_MATCHES_WON, profile.matchesWon)
            .putInt(KEY_MATCHES_LOST, profile.matchesLost)
            .putLong(KEY_EXPERIENCE, profile.experience)
            .putInt(KEY_LEVEL, profile.level)
            .putInt(KEY_DAILY_STREAK, profile.dailyStreak)
            .putLong(KEY_LAST_PLAYED, profile.lastPlayedAtEpochMs)
            .putBoolean(KEY_PROFILE_CREATED, profile.profileCreated)
            .putString(KEY_PALETTES, profile.unlockedPalettes.joinToString(","))
            .putBoolean(KEY_IS_AD_FREE, profile.isAdFree)
            .putBoolean(KEY_IS_PREMIUM, profile.isPremium)
            .apply()
    }

    fun getProfile(): UserProfile? {
        val userId = prefs.getString(KEY_USER_ID, "") ?: ""
        if (userId.isBlank()) return null

        val palettesStr = prefs.getString(KEY_PALETTES, "default") ?: "default"
        val palettes = if (palettesStr.isBlank()) listOf("default") else palettesStr.split(",")

        return UserProfile(
            userId = userId,
            uniqueName = prefs.getString(KEY_UNIQUE_NAME, "") ?: "",
            nickname = prefs.getString(KEY_NICKNAME, "") ?: "",
            isAnonymous = prefs.getBoolean(KEY_IS_ANON, true),
            avatarIndex = prefs.getInt(KEY_AVATAR_INDEX, 0),
            points = prefs.getInt(KEY_POINTS, 0),
            highScore = prefs.getInt(KEY_HIGH_SCORE, 0),
            matchesPlayed = prefs.getInt(KEY_MATCHES_PLAYED, 0),
            matchesWon = prefs.getInt(KEY_MATCHES_WON, 0),
            matchesLost = prefs.getInt(KEY_MATCHES_LOST, 0),
            experience = prefs.getLong(KEY_EXPERIENCE, 0L),
            level = prefs.getInt(KEY_LEVEL, 1),
            dailyStreak = prefs.getInt(KEY_DAILY_STREAK, 0),
            lastPlayedAtEpochMs = prefs.getLong(KEY_LAST_PLAYED, 0L),
            profileCreated = prefs.getBoolean(KEY_PROFILE_CREATED, false),
            unlockedPalettes = palettes,
            isAdFree = prefs.getBoolean(KEY_IS_AD_FREE, false),
            isPremium = prefs.getBoolean(KEY_IS_PREMIUM, false),
        )
    }

    fun deleteProfile() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_UNIQUE_NAME = "unique_name"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_IS_ANON = "is_anon"
        private const val KEY_AVATAR_INDEX = "avatar_index"
        private const val KEY_POINTS = "points"
        private const val KEY_HIGH_SCORE = "high_score"
        private const val KEY_MATCHES_PLAYED = "matches_played"
        private const val KEY_MATCHES_WON = "matches_won"
        private const val KEY_MATCHES_LOST = "matches_lost"
        private const val KEY_EXPERIENCE = "experience"
        private const val KEY_LEVEL = "level"
        private const val KEY_DAILY_STREAK = "daily_streak"
        private const val KEY_LAST_PLAYED = "last_played"
        private const val KEY_PROFILE_CREATED = "profile_created"
        private const val KEY_PALETTES = "palettes"
        private const val KEY_IS_AD_FREE = "is_ad_free"
        private const val KEY_IS_PREMIUM = "is_premium"
    }
}
