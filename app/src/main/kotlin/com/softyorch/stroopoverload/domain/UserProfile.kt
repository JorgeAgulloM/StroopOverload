package com.softyorch.stroopoverload.domain

data class UserProfile(
    val userId: String = "",
    val uniqueName: String = "",
    val nickname: String = "",
    val isAnonymous: Boolean = true,
    val avatarIndex: Int = 0,
    val points: Int = 0, // Ranking score for leaderboard (§4.3)
    val highScore: Int = 0, // Stroop Overload single-match high score
    val matchesPlayed: Int = 0,
    val matchesWon: Int = 0,
    val matchesLost: Int = 0,
    val abandonedMatches: Int = 0,
    val experience: Long = 0L, // Total accumulated XP (§4.1)
    val level: Int = 1,
    val dailyStreak: Int = 0,
    val lastPlayedAtEpochMs: Long = 0L,
    val lastVerificationEmailSentAtEpochMs: Long = 0L,
    val profileCreated: Boolean = false,
    val unlockedPalettes: List<String> = listOf("default"),
    val isAdFree: Boolean = false,
    val isPremium: Boolean = false,
) {
    val uid: String get() = userId
    val displayName: String get() = when {
        nickname.isNotBlank() -> nickname
        userId.isNotBlank() -> "Guest_${userId.takeLast(4).uppercase()}"
        else -> "Guest"
    }
    val totalXp: Int get() = experience.toInt()

    fun generateUniqueName(): String {
        val base = if (nickname.isNotBlank()) nickname.lowercase().trim() else "neural_pilot"
        val suffix = if (userId.isNotBlank()) userId.takeLast(4).lowercase() else "0000"
        return "@$base-$suffix"
    }
}
