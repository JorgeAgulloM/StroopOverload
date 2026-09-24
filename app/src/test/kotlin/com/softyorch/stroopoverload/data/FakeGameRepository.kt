package com.softyorch.stroopoverload.data

import com.softyorch.stroopoverload.domain.Achievement
import com.softyorch.stroopoverload.domain.CareerStats
import com.softyorch.stroopoverload.domain.GameResult
import com.softyorch.stroopoverload.domain.UserProfile

/** One recorded [GameRepository.syncUserProfile] call. */
data class SyncCall(val uid: String, val nickname: String?, val isAnonymous: Boolean)

/** In-memory [GameRepository] holding one profile, recording the calls the ViewModels make. */
class FakeGameRepository(
    var storedProfile: UserProfile = UserProfile(),
    var storedCareerStats: CareerStats = CareerStats(),
    var storedAchievements: List<Achievement> = emptyList(),
) : GameRepository {

    val syncCalls = mutableListOf<SyncCall>()
    val deletedUids = mutableListOf<String>()
    var deleteAllUserDataFailure: Exception? = null

    override fun getProfile(): UserProfile = storedProfile

    override suspend fun updateProfile(profile: UserProfile) {
        storedProfile = profile
    }

    override suspend fun syncUserProfile(uid: String, nickname: String?, isAnonymous: Boolean) {
        syncCalls += SyncCall(uid, nickname, isAnonymous)
        storedProfile = storedProfile.copy(
            userId = uid,
            nickname = nickname ?: storedProfile.nickname,
            isAnonymous = isAnonymous,
            profileCreated = true,
        )
    }

    override suspend fun clearLocalProgress() {
        storedProfile = UserProfile()
    }

    override suspend fun deleteAllUserData(uid: String) {
        deleteAllUserDataFailure?.let { throw it }
        deletedUids += uid
        clearLocalProgress()
    }

    override suspend fun recordGameResult(result: GameResult, xpEarned: Int, winStreak: Int): List<Achievement> =
        emptyList()

    override suspend fun refreshScoringFromCloud(uid: String) = Unit

    override suspend fun syncMatchResult(roomId: String): Boolean = false

    override suspend fun getLeaderboard(forceRefresh: Boolean): List<UserProfile> = listOf(storedProfile)

    override suspend fun getUserRank(myPoints: Int): Int = 1

    override fun getCareerStats(): CareerStats = storedCareerStats

    override fun getAllAchievements(): List<Achievement> = storedAchievements
}
