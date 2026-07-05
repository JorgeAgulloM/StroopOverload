package com.softyorch.stroopoverload.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.softyorch.stroopoverload.core.GameConfig
import com.softyorch.stroopoverload.data.local.AchievementsLocalStore
import com.softyorch.stroopoverload.data.local.ProfileLocalStore
import com.softyorch.stroopoverload.domain.Achievement
import com.softyorch.stroopoverload.domain.AchievementDefinitions
import com.softyorch.stroopoverload.domain.AchievementEngine
import com.softyorch.stroopoverload.domain.CareerStats
import com.softyorch.stroopoverload.domain.GameResult
import com.softyorch.stroopoverload.domain.UserProfile
import com.softyorch.stroopoverload.domain.XpSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseGameRepository private constructor(
    private val context: Context,
    private val profileStore: ProfileLocalStore = ProfileLocalStore(context),
    private val achievementsStore: AchievementsLocalStore = AchievementsLocalStore(context),
    private val achievementEngine: AchievementEngine = AchievementEngine(),
    private val db: FirebaseFirestore? = try { FirebaseFirestore.getInstance() } catch (e: Exception) { null },
) {
    private val users get() = db?.collection("users")

    private var cachedLeaderboard: List<UserProfile> = emptyList()
    private var lastLeaderboardFetchEpochMs: Long = 0L

    fun getProfile(): UserProfile {
        return profileStore.getProfile() ?: UserProfile()
    }

    suspend fun updateProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        profileStore.saveProfile(profile)
        if (profile.userId.isNotBlank() && !profile.isAnonymous) {
            pushProfileToCloud(profile)
        }
    }

    private suspend fun pushProfileToCloud(profile: UserProfile) {
        val collection = users ?: return
        try {
            val map = mapOf(
                "userId" to profile.userId,
                "uniqueName" to profile.uniqueName,
                "nickname" to profile.nickname,
                "displayName" to profile.displayName,
                "isAnonymous" to profile.isAnonymous,
                "avatarIndex" to profile.avatarIndex,
                "points" to profile.points,
                "highScore" to profile.highScore,
                "matchesPlayed" to profile.matchesPlayed,
                "matchesWon" to profile.matchesWon,
                "matchesLost" to profile.matchesLost,
                "experience" to profile.experience,
                "level" to profile.level,
                "dailyStreak" to profile.dailyStreak,
                "lastPlayedAtEpochMs" to profile.lastPlayedAtEpochMs,
                "profileCreated" to profile.profileCreated,
                "unlockedPalettes" to profile.unlockedPalettes,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            collection.document(profile.userId).set(map, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirebaseRepo", "Best-effort profile push failed (offline or unconfigured): ${e.message}")
        }
    }

    suspend fun syncUserProfile(uid: String, nickname: String? = null) = withContext(Dispatchers.IO) {
        val local = getProfile()

        // Case 1: local profile already belongs to this exact account — nothing to do.
        if (local.userId == uid && local.profileCreated) {
            if (nickname != null && local.nickname.isBlank()) {
                val updated = local.copy(nickname = nickname, uniqueName = local.copy(nickname = nickname, userId = uid).generateUniqueName())
                updateProfile(updated)
            }
            return@withContext
        }

        // Case 2 (blank userId: genuinely fresh install) vs case 3 (a different, previously-used
        // account on this device) must be distinguished BEFORE wiping local storage below — only
        // case 2 may legitimately carry over local.points/highScore/experience/level (progress
        // played before this login on a brand-new install). Case 3 must never inherit another
        // account's device-scoped stats.
        val isFreshInstall = local.userId.isBlank()

        clearLocalProgress()

        val collection = users
        val remoteDoc = if (collection != null) {
            try { collection.document(uid).get().await() } catch (e: Exception) { null }
        } else null

        if (remoteDoc != null && remoteDoc.exists()) {
            val data = remoteDoc.data ?: emptyMap()
            val remoteProfile = UserProfile(
                userId = uid,
                uniqueName = data["uniqueName"] as? String ?: "@pilot-${uid.takeLast(4)}",
                nickname = data["nickname"] as? String ?: (nickname ?: "Pilot_${uid.takeLast(4)}"),
                isAnonymous = data["isAnonymous"] as? Boolean ?: false,
                avatarIndex = (data["avatarIndex"] as? Long)?.toInt() ?: 0,
                points = (data["points"] as? Long)?.toInt() ?: 0,
                highScore = (data["highScore"] as? Long)?.toInt() ?: 0,
                matchesPlayed = (data["matchesPlayed"] as? Long)?.toInt() ?: 0,
                matchesWon = (data["matchesWon"] as? Long)?.toInt() ?: 0,
                matchesLost = (data["matchesLost"] as? Long)?.toInt() ?: 0,
                experience = (data["experience"] as? Long) ?: 0L,
                level = (data["level"] as? Long)?.toInt() ?: 1,
                dailyStreak = (data["dailyStreak"] as? Long)?.toInt() ?: 0,
                lastPlayedAtEpochMs = data["lastPlayedAtEpochMs"] as? Long ?: 0L,
                profileCreated = true,
                unlockedPalettes = @Suppress("UNCHECKED_CAST") (data["unlockedPalettes"] as? List<String>) ?: listOf("default")
            )
            profileStore.saveProfile(remoteProfile)
            restoreProgressFromCloud(data)
        } else if (isFreshInstall) {
            // Case 2: no remote doc yet, and there was no prior account on this device to
            // contaminate from — safe to carry over whatever local progress accumulated
            // (e.g. a few offline rounds played before registering).
            val nick = nickname ?: if (local.nickname.isNotBlank()) local.nickname else "Pilot_${uid.takeLast(4)}"
            val newProfile = UserProfile(
                userId = uid,
                uniqueName = "@${nick.lowercase().trim()}-${uid.takeLast(4).lowercase()}",
                nickname = nick,
                isAnonymous = false,
                points = local.points,
                highScore = local.highScore,
                experience = local.experience,
                level = local.level,
                profileCreated = true
            )
            updateProfile(newProfile)
        } else {
            // Case 3: switching to a different account than whatever was last used on this
            // device, and it has no remote doc — start clean, never inherit the previous
            // account's device-scoped stats.
            val nick = nickname ?: "Pilot_${uid.takeLast(4)}"
            val newProfile = UserProfile(
                userId = uid,
                uniqueName = "@${nick.lowercase().trim()}-${uid.takeLast(4).lowercase()}",
                nickname = nick,
                isAnonymous = false,
                profileCreated = true
            )
            updateProfile(newProfile)
        }
    }

    private fun restoreProgressFromCloud(data: Map<String, Any?>) {
        try {
            val careerMap = @Suppress("UNCHECKED_CAST") (data["careerStats"] as? Map<String, Long>) ?: emptyMap()
            if (careerMap.isNotEmpty()) {
                val stats = CareerStats.fromMap(careerMap)
                achievementsStore.updateCareerStats(stats)
            }
            val achievementsMap = @Suppress("UNCHECKED_CAST") (data["achievements"] as? Map<String, Long>) ?: emptyMap()
            achievementsMap.forEach { (id, time) ->
                achievementsStore.unlock(id, time)
            }
        } catch (e: Exception) {
            Log.w("FirebaseRepo", "Error restoring cloud progress: ${e.message}")
        }
    }

    suspend fun clearLocalProgress() = withContext(Dispatchers.IO) {
        achievementsStore.clearProgress()
        profileStore.deleteProfile()
    }

    suspend fun recordGameResult(result: GameResult, xpEarned: Int): List<Achievement> = withContext(Dispatchers.IO) {
        if (result.correctHits == 0 || result.finalScore <= 0) {
            return@withContext emptyList()
        }
        val current = getProfile()
        val deltaPoints = if (result.won) +100 else -25
        val newPoints = (current.points + deltaPoints).coerceAtLeast(0)
        val newHigh = maxOf(current.highScore, result.finalScore)
        val newPlayed = current.matchesPlayed + 1
        val newWon = if (result.won) current.matchesWon + 1 else current.matchesWon
        val newLost = if (!result.won) current.matchesLost + 1 else current.matchesLost
        val newXp = current.experience + xpEarned
        val newLevel = XpSystem.levelFromTotalXp(newXp)
        val now = System.currentTimeMillis()

        val lastDay = current.lastPlayedAtEpochMs / (1000 * 60 * 60 * 24)
        val today = now / (1000 * 60 * 60 * 24)
        val newStreak = when {
            today == lastDay -> current.dailyStreak
            today == lastDay + 1 -> current.dailyStreak + 1
            else -> 1
        }

        val updated = current.copy(
            points = newPoints,
            highScore = newHigh,
            matchesPlayed = newPlayed,
            matchesWon = newWon,
            matchesLost = newLost,
            experience = newXp,
            level = newLevel,
            dailyStreak = newStreak,
            lastPlayedAtEpochMs = now
        )
        updateProfile(updated)

        val career = achievementsStore.getCareerStats()
        val alreadyUnlocked = achievementsStore.getUnlockedIds()
        val updatedCareer = achievementEngine.updatedCareerStats(career, result)
        achievementsStore.updateCareerStats(updatedCareer)

        val newIds = achievementEngine.evaluate(result, updatedCareer, alreadyUnlocked)
        newIds.forEach { achievementsStore.unlock(it, now) }

        val newlyUnlockedAchievements = AchievementDefinitions.all.filter { it.id in newIds }
        val achievementXpBonus = newlyUnlockedAchievements.sumOf { it.xpReward }

        val finalUpdated = if (achievementXpBonus > 0) {
            val totalXpWithAchievements = updated.experience + achievementXpBonus
            val finalLevel = XpSystem.levelFromTotalXp(totalXpWithAchievements)
            updated.copy(experience = totalXpWithAchievements, level = finalLevel).also {
                updateProfile(it)
            }
        } else {
            updated
        }

        if (finalUpdated.userId.isNotBlank() && !finalUpdated.isAnonymous) {
            val allUnlockedMap = achievementsStore.getAllAchievements()
                .filter { it.isUnlocked }
                .associate { it.id to it.unlockedAt }
            syncProgressToCloud(finalUpdated.userId, updatedCareer, allUnlockedMap)
        }

        return@withContext newlyUnlockedAchievements
    }

    private suspend fun syncProgressToCloud(
        uid: String,
        career: CareerStats,
        achievements: Map<String, Long>,
    ) {
        val collection = users ?: return
        try {
            val map = mapOf(
                "careerStats" to career.toMap(),
                "achievements" to achievements,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            collection.document(uid).set(map, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirebaseRepo", "Failed to sync progress to cloud: ${e.message}")
        }
    }

    suspend fun getLeaderboard(forceRefresh: Boolean = false): List<UserProfile> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedLeaderboard.isNotEmpty() && (now - lastLeaderboardFetchEpochMs < 300_000L)) {
            return@withContext cachedLeaderboard
        }

        val collection = users
        if (collection != null) {
            try {
                val snap = collection
                    .orderBy("points", Query.Direction.DESCENDING)
                    .limit(GameConfig.LEADERBOARD_LIMIT)
                    .get()
                    .await()

                val remoteEntries = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    UserProfile(
                        userId = doc.id,
                        uniqueName = d["uniqueName"] as? String ?: "@pilot-${doc.id.takeLast(4)}",
                        nickname = d["nickname"] as? String ?: (d["displayName"] as? String ?: "Pilot_${doc.id.takeLast(4)}"),
                        isAnonymous = d["isAnonymous"] as? Boolean ?: false,
                        points = (d["points"] as? Long)?.toInt() ?: 0,
                        highScore = (d["highScore"] as? Long)?.toInt() ?: 0,
                        experience = (d["experience"] as? Long) ?: 0L,
                        level = (d["level"] as? Long)?.toInt() ?: 1,
                    )
                }
                if (remoteEntries.isNotEmpty()) {
                    cachedLeaderboard = remoteEntries
                    lastLeaderboardFetchEpochMs = now
                    return@withContext remoteEntries
                }
            } catch (e: Exception) {
                Log.w("FirebaseRepo", "Leaderboard fetch error (offline or unconfigured): ${e.message}")
            }
        }

        val local = getProfile()
        val bot1 = UserProfile("bot_1", "@cyber-bot", "CYBER_BOT_99", false, 0, 4850, 4500, 48, 35, 13, 0, 45000L, 21)
        val bot2 = UserProfile("bot_2", "@synapse-ai", "SYNAPSE_AI", false, 0, 3200, 3100, 30, 22, 8, 0, 28000L, 16)
        val bot3 = UserProfile("bot_3", "@vex-operative", "VEX_OPERATIVE", false, 0, 1950, 1800, 20, 14, 6, 0, 15000L, 11)
        val bot4 = UserProfile("bot_4", "@neomorph", "NEOMORPH_01", false, 0, 850, 800, 10, 6, 4, 0, 5000L, 6)
        
        val list = listOf(local, bot1, bot2, bot3, bot4).sortedByDescending { it.points }
        cachedLeaderboard = list
        lastLeaderboardFetchEpochMs = now
        return@withContext list
    }

    suspend fun getUserRank(myPoints: Int): Int = withContext(Dispatchers.IO) {
        val collection = users
        if (collection != null) {
            try {
                val agg = collection.whereGreaterThan("points", myPoints)
                    .count()
                    .get(AggregateSource.SERVER)
                    .await()
                return@withContext (agg.count + 1).toInt()
            } catch (e: Exception) {
                Log.w("FirebaseRepo", "Rank query fallback: ${e.message}")
            }
        }
        val idx = cachedLeaderboard.indexOfFirst { it.points <= myPoints }
        if (idx != -1) idx + 1 else cachedLeaderboard.size + 1
    }

    fun getCareerStats(): CareerStats = achievementsStore.getCareerStats()
    fun getAllAchievements(): List<Achievement> = achievementsStore.getAllAchievements()

    companion object {
        @Volatile
        private var INSTANCE: FirebaseGameRepository? = null

        fun getInstance(context: Context): FirebaseGameRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirebaseGameRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
