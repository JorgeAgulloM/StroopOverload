package com.softyorch.stroopoverload.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.softyorch.stroopoverload.core.GameConfig
import com.softyorch.stroopoverload.data.local.AchievementsLocalStore
import com.softyorch.stroopoverload.data.local.MultiplayerAwardStore
import com.softyorch.stroopoverload.data.local.ProfileLocalStore
import com.softyorch.stroopoverload.domain.Achievement
import com.softyorch.stroopoverload.domain.AchievementDefinitions
import com.softyorch.stroopoverload.domain.AchievementEngine
import com.softyorch.stroopoverload.domain.CareerStats
import com.softyorch.stroopoverload.domain.GameResult
import com.softyorch.stroopoverload.domain.applyServerScoring
import com.softyorch.stroopoverload.domain.serverScoringFrom
import com.softyorch.stroopoverload.domain.UserProfile
import com.softyorch.stroopoverload.domain.isRecordable
import com.softyorch.stroopoverload.domain.withAchievementXp
import com.softyorch.stroopoverload.domain.withRunApplied
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseGameRepository private constructor(
    private val context: Context,
    private val profileStore: ProfileLocalStore = ProfileLocalStore(context),
    private val achievementsStore: AchievementsLocalStore = AchievementsLocalStore(context),
    private val achievementEngine: AchievementEngine = AchievementEngine(),
    private val multiplayerAwardStore: MultiplayerAwardStore = MultiplayerAwardStore(context),
    private val db: FirebaseFirestore? = try { FirebaseFirestore.getInstance() } catch (e: Exception) { null },
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
) : GameRepository {
    private val users get() = db?.collection("users")

    private var cachedLeaderboard: List<UserProfile> = emptyList()
    private var lastLeaderboardFetchEpochMs: Long = 0L

    override fun getProfile(): UserProfile {
        return profileStore.getProfile() ?: UserProfile()
    }

    override suspend fun updateProfile(profile: UserProfile): Unit = withContext(Dispatchers.IO) {
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
                // Scoring fields are deliberately absent: points, highScore, experience,
                // level, the match counters and dailyStreak are written only by Cloud
                // Functions (submitSoloRun / onRoomFinished) and firestore.rules rejects
                // any write to them from here. Sending them would fail the whole merge.
                "profileCreated" to profile.profileCreated,
                "unlockedPalettes" to profile.unlockedPalettes,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            collection.document(profile.userId).set(map, SetOptions.merge()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("FirebaseRepo", "Best-effort profile push failed (offline or unconfigured): ${e.message}")
        }
    }

    override suspend fun syncUserProfile(uid: String, nickname: String?, isAnonymous: Boolean): Unit = withContext(Dispatchers.IO) {
        val local = getProfile()

        // Case 1: local profile already belongs to this exact account -- nothing to do, except
        // repairing what older builds got wrong (a missing nickname, guests saved as registered).
        if (local.userId == uid && local.profileCreated) {
            var repaired = local
            if (nickname != null && local.nickname.isBlank()) {
                repaired = repaired.copy(nickname = nickname, uniqueName = local.copy(nickname = nickname, userId = uid).generateUniqueName())
            }
            repaired = repaired.repairedForSession(uid, isAnonymous) ?: repaired
            if (repaired != local) updateProfile(repaired)
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
            try {
                collection.document(uid).get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        } else null

        if (remoteDoc != null && remoteDoc.exists()) {
            val data = remoteDoc.data ?: emptyMap()
            val fallbackNickname = nickname ?: "Pilot_${uid.takeLast(4)}"
            profileStore.saveProfile(profileFromRemote(uid, data, fallbackNickname, isAnonymous))
            restoreProgressFromCloud(data)
        } else if (isFreshInstall) {
            // Case 2: no remote doc yet, and there was no prior account on this device to
            // contaminate from — safe to carry over whatever local progress accumulated
            // (e.g. a few offline rounds played before registering).
            val nick = nickname ?: if (local.nickname.isNotBlank()) local.nickname else "Pilot_${uid.takeLast(4)}"
            updateProfile(newSessionProfile(uid, nick, isAnonymous, carriedOver = local))
        } else {
            // Case 3: switching to a different account than whatever was last used on this
            // device, and it has no remote doc — start clean, never inherit the previous
            // account's device-scoped stats.
            updateProfile(newSessionProfile(uid, nickname ?: "Pilot_${uid.takeLast(4)}", isAnonymous))
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("FirebaseRepo", "Error restoring cloud progress: ${e.message}")
        }
    }

    override suspend fun clearLocalProgress(): Unit = withContext(Dispatchers.IO) {
        achievementsStore.clearProgress()
        profileStore.deleteProfile()
    }

    /**
     * Wipes this user's cloud document and all local progress. Call before deleting the auth
     * account. Deliberately does NOT swallow Firestore failures — the caller (account deletion)
     * must know if the cloud doc survived instead of reporting a false "deleted everything".
     */
    override suspend fun deleteAllUserData(uid: String): Unit = withContext(Dispatchers.IO) {
        users?.document(uid)?.delete()?.await()
        clearLocalProgress()
    }

    override suspend fun recordGameResult(result: GameResult, xpEarned: Int, winStreak: Int): List<Achievement> = withContext(Dispatchers.IO) {
        if (!result.isRecordable()) {
            return@withContext emptyList()
        }
        val now = System.currentTimeMillis()
        val updated = getProfile().withRunApplied(result, xpEarned, now)
        updateProfile(updated)

        val career = achievementsStore.getCareerStats()
        val alreadyUnlocked = achievementsStore.getUnlockedIds()
        val updatedCareer = achievementEngine.updatedCareerStats(career, result)
        achievementsStore.updateCareerStats(updatedCareer)

        val newIds = achievementEngine.evaluate(result, updatedCareer, alreadyUnlocked)
        newIds.forEach { achievementsStore.unlock(it, now) }

        val newlyUnlockedAchievements = AchievementDefinitions.all.filter { it.id in newIds }
        val achievementXpBonus = newlyUnlockedAchievements.sumOf { it.xpReward }

        val finalUpdated = updated.withAchievementXp(achievementXpBonus)
        if (finalUpdated != updated) updateProfile(finalUpdated)

        if (finalUpdated.userId.isNotBlank() && !finalUpdated.isAnonymous) {
            val allUnlockedMap = achievementsStore.getAllAchievements()
                .filter { it.isUnlocked }
                .associate { it.id to it.unlockedAt }
            syncProgressToCloud(finalUpdated.userId, updatedCareer, allUnlockedMap)
            submitRunToServer(result, newIds, winStreak)
        }

        return@withContext newlyUnlockedAchievements
    }

    /**
     * Reports a finished run to the backend, which recomputes the score and XP and
     * writes the profile itself -- the local numbers above are provisional until it
     * answers, and are replaced by whatever it returns.
     *
     * A failure here (offline, rate-limited, rejected as implausible) is not an error
     * for the player: the run still counted locally. It just never reaches the
     * leaderboard, which is the point -- offline play does not rank.
     */
    private suspend fun submitRunToServer(
        result: GameResult,
        unlockedAchievementIds: Collection<String>,
        winStreak: Int,
    ) {
        val payload = mapOf(
            "mode" to result.mode.name,
            "correctHits" to result.correctHits,
            "totalRounds" to result.totalRounds,
            "survivalMs" to result.survivalMs,
            "finalScore" to result.finalScore,
            "achievementIds" to unlockedAchievementIds.toList(),
            // Feeds the same XP bonus the player already saw on the game-over screen;
            // the server clamps it to the run's correct answers.
            "winStreak" to winStreak,
        )
        try {
            val response = functions.getHttpsCallable("submitSoloRun").call(payload).await()
            @Suppress("UNCHECKED_CAST")
            val scoring = serverScoringFrom(response.data as? Map<String, Any?>)
            if (scoring == null) {
                Log.w("FirebaseRepo", "submitSoloRun returned no scoring fields")
                return
            }
            profileStore.saveProfile(getProfile().applyServerScoring(scoring))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("FirebaseRepo", "submitSoloRun failed; run stays local only: ${e.message}")
        }
    }

    /**
     * Pulls the backend's copy of the scoring fields into the local profile. Used
     * after a multiplayer match is settled server-side (onRoomFinished), since the
     * client no longer computes those points itself.
     */
    override suspend fun refreshScoringFromCloud(uid: String): Unit = withContext(Dispatchers.IO) {
        val collection = users ?: return@withContext
        try {
            val remote = collection.document(uid).get().await()
            val scoring = serverScoringFrom(remote.data) ?: return@withContext
            profileStore.saveProfile(getProfile().applyServerScoring(scoring))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("FirebaseRepo", "Could not refresh scoring from cloud: ${e.message}")
        }
    }

    /**
     * Pulls in a finished multiplayer match's points once the backend has settled
     * them (onRoomFinished writes every player's profile from the finalScore it
     * computed in functions/src/scoring.ts). The client used to apply those points
     * to its own profile, which meant the server computed an authoritative number
     * and then trusted the client to store it.
     *
     * [multiplayerAwardStore] now only keeps this from re-reading the same settled
     * room; the award itself is idempotent server-side. No-ops for anonymous
     * profiles, which never sync and never rank.
     *
     * @return true if this call pulled in the match's result.
     */
    override suspend fun syncMatchResult(roomId: String): Boolean = withContext(Dispatchers.IO) {
        if (multiplayerAwardStore.hasAwarded(roomId)) return@withContext false
        val current = getProfile()
        if (current.isAnonymous || current.userId.isBlank()) {
            multiplayerAwardStore.markAwarded(roomId)
            return@withContext false
        }

        refreshScoringFromCloud(current.userId)
        multiplayerAwardStore.markAwarded(roomId)
        true
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("FirebaseRepo", "Failed to sync progress to cloud: ${e.message}")
        }
    }

    override suspend fun getLeaderboard(forceRefresh: Boolean): List<UserProfile> = withContext(Dispatchers.IO) {
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
            } catch (e: CancellationException) {
                throw e
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

    override suspend fun getUserRank(myPoints: Int): Int = withContext(Dispatchers.IO) {
        val collection = users
        if (collection != null) {
            try {
                val agg = collection.whereGreaterThan("points", myPoints)
                    .count()
                    .get(AggregateSource.SERVER)
                    .await()
                return@withContext (agg.count + 1).toInt()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("FirebaseRepo", "Rank query fallback: ${e.message}")
            }
        }
        val idx = cachedLeaderboard.indexOfFirst { it.points <= myPoints }
        if (idx != -1) idx + 1 else cachedLeaderboard.size + 1
    }

    override fun getCareerStats(): CareerStats = achievementsStore.getCareerStats()
    override fun getAllAchievements(): List<Achievement> = achievementsStore.getAllAchievements()

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
