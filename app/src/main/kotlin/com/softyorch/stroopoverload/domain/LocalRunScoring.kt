package com.softyorch.stroopoverload.domain

private const val MS_PER_DAY = 24L * 60 * 60 * 1000
private const val WIN_POINTS = 100
private const val LOSS_POINTS = -25

/*
 * The device's provisional copy of what the backend does to a profile after a solo
 * run (functions/src/profileScoring.ts and userProfile.ts). It exists so play works
 * offline; the server's answer replaces these numbers whenever it arrives. The two
 * must agree, or a player sees their points jump on the next sync.
 */

/** A run with no correct answer or no score is not recorded at all. */
fun GameResult.isRecordable(): Boolean = correctHits > 0 && finalScore > 0

/** Same day keeps the streak, the next day extends it, any gap restarts it (UTC days). */
fun nextDailyStreak(currentStreak: Int, lastPlayedAtEpochMs: Long, nowMs: Long): Int {
    val lastDay = lastPlayedAtEpochMs / MS_PER_DAY
    val today = nowMs / MS_PER_DAY
    return when (today) {
        lastDay -> currentStreak
        lastDay + 1 -> currentStreak + 1
        else -> 1
    }
}

fun UserProfile.withRunApplied(result: GameResult, xpEarned: Int, nowMs: Long): UserProfile {
    val newXp = experience + xpEarned
    return copy(
        points = (points + if (result.won) WIN_POINTS else LOSS_POINTS).coerceAtLeast(0),
        highScore = maxOf(highScore, result.finalScore),
        matchesPlayed = matchesPlayed + 1,
        matchesWon = if (result.won) matchesWon + 1 else matchesWon,
        matchesLost = if (result.won) matchesLost else matchesLost + 1,
        experience = newXp,
        level = XpSystem.levelFromTotalXp(newXp),
        dailyStreak = nextDailyStreak(dailyStreak, lastPlayedAtEpochMs, nowMs),
        lastPlayedAtEpochMs = nowMs,
    )
}

fun UserProfile.withAchievementXp(bonus: Int): UserProfile {
    if (bonus <= 0) return this
    val newXp = experience + bonus
    return copy(experience = newXp, level = XpSystem.levelFromTotalXp(newXp))
}
