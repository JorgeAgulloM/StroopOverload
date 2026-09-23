package com.softyorch.stroopoverload.domain

/**
 * The part of a profile the backend owns: points, XP, level, high score, match
 * counters and the daily streak. Cloud Functions compute and write these
 * (functions/src/userProfile.ts); firestore.rules denies the client any write to
 * them, because a client-written leaderboard is a leaderboard anyone can forge.
 *
 * The client keeps a local copy so play works offline -- but an offline run never
 * reaches the server, and by design never counts on the leaderboard, so whatever
 * the server says replaces the local numbers on the next refresh.
 */
data class ServerScoring(
    val points: Int,
    val highScore: Int,
    val experience: Long,
    val level: Int,
    val matchesPlayed: Int,
    val matchesWon: Int,
    val matchesLost: Int,
    val dailyStreak: Int,
)

private val SCORING_KEYS = listOf(
    "points", "highScore", "experience", "level",
    "matchesPlayed", "matchesWon", "matchesLost", "dailyStreak",
)

/**
 * Reads the scoring fields out of a Firestore user document (numbers arrive as
 * [Long]), or null when the document has none yet -- a profile that has never
 * been scored must not be read as a profile scored at zero.
 */
fun serverScoringFrom(data: Map<String, Any?>?): ServerScoring? {
    if (data == null || SCORING_KEYS.none { it in data }) return null
    fun int(key: String): Int = (data[key] as? Number)?.toInt() ?: 0
    return ServerScoring(
        points = int("points"),
        highScore = int("highScore"),
        experience = (data["experience"] as? Number)?.toLong() ?: 0L,
        level = (data["level"] as? Number)?.toInt() ?: 1,
        matchesPlayed = int("matchesPlayed"),
        matchesWon = int("matchesWon"),
        matchesLost = int("matchesLost"),
        dailyStreak = int("dailyStreak"),
    )
}

fun UserProfile.applyServerScoring(scoring: ServerScoring): UserProfile = copy(
    points = scoring.points,
    highScore = scoring.highScore,
    experience = scoring.experience,
    level = scoring.level,
    matchesPlayed = scoring.matchesPlayed,
    matchesWon = scoring.matchesWon,
    matchesLost = scoring.matchesLost,
    dailyStreak = scoring.dailyStreak,
)
