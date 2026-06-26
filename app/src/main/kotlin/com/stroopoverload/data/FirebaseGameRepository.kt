package com.stroopoverload.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.stroopoverload.core.GameConfig
import com.stroopoverload.domain.Achievement
import com.stroopoverload.domain.GameResult
import com.stroopoverload.domain.UserProfile
import kotlinx.coroutines.tasks.await

class FirebaseGameRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private val users get() = db.collection("users")

    suspend fun createUser(uid: String, displayName: String) {
        users.document(uid).set(
            mapOf(
                "displayName" to displayName,
                "isAnonymous" to true,
                "highScore" to 0,
                "totalXp" to 0,
                "level" to 1,
                "unlockedPalettes" to listOf("default"),
                "achievements" to emptyList<Any>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "lastLogin" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    suspend fun saveGameResult(uid: String, result: GameResult) {
        val snap = users.document(uid).get().await()
        val currentHigh = snap.getLong("highScore")?.toInt() ?: 0

        val updates = mutableMapOf<String, Any>(
            "totalXp" to FieldValue.increment(result.xpEarned.toLong()),
            "lastLogin" to FieldValue.serverTimestamp(),
        )
        if (result.finalScore > currentHigh) {
            updates["highScore"] = result.finalScore
        }

        val batch = db.batch()
        batch.update(users.document(uid), updates)

        for (achievement in evaluateAchievements(result, snap.data)) {
            batch.update(
                users.document(uid),
                "achievements", FieldValue.arrayUnion(
                    mapOf("id" to achievement.id, "unlockedAt" to achievement.unlockedAt)
                )
            )
        }
        batch.commit().await()
    }

    suspend fun getLeaderboard(): List<UserProfile> {
        val snap = users
            .orderBy("highScore", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(GameConfig.LEADERBOARD_LIMIT)
            .get()
            .await()

        return snap.documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            UserProfile(
                uid = doc.id,
                displayName = d["displayName"] as? String ?: "",
                isAnonymous = d["isAnonymous"] as? Boolean ?: true,
                highScore = (d["highScore"] as? Long)?.toInt() ?: 0,
                totalXp = (d["totalXp"] as? Long)?.toInt() ?: 0,
                level = (d["level"] as? Long)?.toInt() ?: 1,
                unlockedPalettes = @Suppress("UNCHECKED_CAST") (d["unlockedPalettes"] as? List<String>) ?: listOf("default"),
                achievements = emptyList(),
            )
        }
    }

    private fun evaluateAchievements(result: GameResult, data: Map<String, Any?>?): List<Achievement> {
        val existing = @Suppress("UNCHECKED_CAST") ((data?.get("achievements") as? List<Map<String, Any>>)
            ?.map { it["id"] as? String }
            ?.toSet()) ?: emptySet()

        val now = System.currentTimeMillis()
        return buildList {
            if ("first_blood" !in existing && result.finalScore > 0)
                add(Achievement("first_blood", now))
            if ("flawless" !in existing && result.totalRounds >= 5 && result.correctHits == result.totalRounds)
                add(Achievement("flawless", now))
            if ("centurion" !in existing && result.finalScore >= 1000)
                add(Achievement("centurion", now))
        }
    }
}
