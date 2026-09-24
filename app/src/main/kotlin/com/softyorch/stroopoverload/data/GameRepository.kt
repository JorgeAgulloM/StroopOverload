package com.softyorch.stroopoverload.data

import com.softyorch.stroopoverload.domain.Achievement
import com.softyorch.stroopoverload.domain.CareerStats
import com.softyorch.stroopoverload.domain.GameResult
import com.softyorch.stroopoverload.domain.UserProfile

/**
 * The player's profile, progress and leaderboard, local and cloud. Implemented by
 * [FirebaseGameRepository]; the interface is the seam that lets the ViewModels built
 * on it run under plain JUnit with a fake.
 */
interface GameRepository {
    fun getProfile(): UserProfile
    suspend fun updateProfile(profile: UserProfile)
    /** [isAnonymous] must come from the auth session (a guest sign-in), never from a stored profile. */
    suspend fun syncUserProfile(uid: String, nickname: String?, isAnonymous: Boolean)
    suspend fun clearLocalProgress()

    /** Does not swallow cloud failures: account deletion must know if the document survived. */
    suspend fun deleteAllUserData(uid: String)

    /** @return the achievements this run unlocked. */
    suspend fun recordGameResult(result: GameResult, xpEarned: Int, winStreak: Int = 0): List<Achievement>
    suspend fun refreshScoringFromCloud(uid: String)

    /** @return true if this call pulled in the match's result. */
    suspend fun syncMatchResult(roomId: String): Boolean

    suspend fun getLeaderboard(forceRefresh: Boolean = false): List<UserProfile>
    suspend fun getUserRank(myPoints: Int): Int
    fun getCareerStats(): CareerStats
    fun getAllAchievements(): List<Achievement>
}
