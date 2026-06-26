package com.softyorch.stroopoverload.domain

data class UserProfile(
    val uid: String,
    val displayName: String,
    val isAnonymous: Boolean,
    val highScore: Int,
    val totalXp: Int,
    val level: Int,
    val unlockedPalettes: List<String>,
    val achievements: List<Achievement>,
) {
    companion object {
        fun initial(uid: String) = UserProfile(
            uid = uid,
            displayName = "Guest_${uid.take(4).uppercase()}",
            isAnonymous = true,
            highScore = 0,
            totalXp = 0,
            level = 1,
            unlockedPalettes = listOf("default"),
            achievements = emptyList(),
        )
    }
}
