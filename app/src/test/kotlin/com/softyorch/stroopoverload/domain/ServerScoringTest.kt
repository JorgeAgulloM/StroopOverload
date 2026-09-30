package com.softyorch.stroopoverload.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerScoringTest {

    private val profile = UserProfile(
        userId = "uid-1",
        nickname = "Neo",
        uniqueName = "@neo-1234",
        points = 10,
        highScore = 100,
        experience = 200L,
        level = 2,
        matchesPlayed = 3,
        matchesWon = 2,
        matchesLost = 1,
        dailyStreak = 1,
        unlockedPalettes = listOf("default", "neon"),
    )

    private val serverValues = mapOf<String, Any?>(
        "points" to 900L,
        "highScore" to 4200L,
        "experience" to 55_000L,
        "level" to 12L,
        "matchesPlayed" to 40L,
        "matchesWon" to 25L,
        "matchesLost" to 15L,
        "dailyStreak" to 4L,
    )

    @Test
    fun `parses the scoring fields Firestore returns as Long`() {
        val scoring = serverScoringFrom(serverValues)

        assertEquals(ServerScoring(900, 4200, 55_000L, 12, 40, 25, 15, 4), scoring)
    }

    @Test
    fun `returns null when the document carries no scoring fields yet`() {
        assertNull(serverScoringFrom(mapOf("nickname" to "Neo")))
        assertNull(serverScoringFrom(null))
    }

    @Test
    fun `a partially written document falls back per field instead of failing`() {
        val scoring = serverScoringFrom(mapOf("points" to 500L, "level" to 3L))

        assertEquals(500, scoring?.points)
        assertEquals(3, scoring?.level)
        assertEquals(0, scoring?.highScore)
    }

    @Test
    fun `applying server scoring replaces every field the server owns`() {
        val updated = profile.applyServerScoring(serverScoringFrom(serverValues)!!)

        assertEquals(900, updated.points)
        assertEquals(4200, updated.highScore)
        assertEquals(55_000L, updated.experience)
        assertEquals(12, updated.level)
        assertEquals(40, updated.matchesPlayed)
        assertEquals(25, updated.matchesWon)
        assertEquals(15, updated.matchesLost)
        assertEquals(4, updated.dailyStreak)
    }

    @Test
    fun `applying server scoring leaves the fields the client owns alone`() {
        val updated = profile.applyServerScoring(serverScoringFrom(serverValues)!!)

        assertEquals("Neo", updated.nickname)
        assertEquals("@neo-1234", updated.uniqueName)
        assertEquals("uid-1", updated.userId)
        assertEquals(listOf("default", "neon"), updated.unlockedPalettes)
    }

    @Test
    fun `server scoring wins even when it is lower than the local value`() {
        // The server is the source of truth: a local number that ran ahead (an
        // offline run, which by design never counts) must not survive a refresh.
        val lower = ServerScoring(1, 1, 1L, 1, 1, 1, 0, 0)

        val updated = profile.applyServerScoring(lower)

        assertEquals(1, updated.points)
        assertEquals(1, updated.highScore)
    }
}
