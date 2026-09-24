package com.softyorch.stroopoverload.data

import com.softyorch.stroopoverload.domain.UserProfile

/*
 * The profiles FirebaseGameRepository.syncUserProfile builds when an account signs in on
 * this device. `isAnonymous` always comes from the Firebase Auth session: guests used to be
 * saved as registered, which showed them account actions they can't use and pushed their
 * profile to Firestore, and some of those Firestore docs still say isAnonymous=false.
 */

/**
 * A new local profile for [uid]. [carriedOver] is progress played on this device before the
 * first sign-in (a genuinely fresh install); never pass another account's profile.
 */
internal fun newSessionProfile(
    uid: String,
    nickname: String,
    isAnonymous: Boolean,
    carriedOver: UserProfile? = null,
): UserProfile = UserProfile(
    userId = uid,
    uniqueName = "@${nickname.lowercase().trim()}-${uid.takeLast(4).lowercase()}",
    nickname = nickname,
    isAnonymous = isAnonymous,
    points = carriedOver?.points ?: 0,
    highScore = carriedOver?.highScore ?: 0,
    experience = carriedOver?.experience ?: 0L,
    level = carriedOver?.level ?: 1,
    profileCreated = true,
)

/** The local profile for [uid] rebuilt from its Firestore document. */
internal fun profileFromRemote(
    uid: String,
    data: Map<String, Any?>,
    fallbackNickname: String,
    isAnonymous: Boolean,
): UserProfile = UserProfile(
    userId = uid,
    uniqueName = data["uniqueName"] as? String ?: "@pilot-${uid.takeLast(4)}",
    nickname = data["nickname"] as? String ?: fallbackNickname,
    isAnonymous = isAnonymous,
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
    unlockedPalettes = (data["unlockedPalettes"] as? List<*>)?.filterIsInstance<String>() ?: listOf("default"),
)

/**
 * This profile with its guest flag corrected to match the signed-in session, or null when
 * nothing needs repairing -- the flag already matches, the session is unknown, or the profile
 * belongs to another account (that case is syncUserProfile's job, not a repair).
 */
internal fun UserProfile.repairedForSession(uid: String?, isAnonymousSession: Boolean?): UserProfile? {
    if (uid == null || isAnonymousSession == null || userId != uid) return null
    if (isAnonymous == isAnonymousSession) return null
    return copy(isAnonymous = isAnonymousSession)
}
