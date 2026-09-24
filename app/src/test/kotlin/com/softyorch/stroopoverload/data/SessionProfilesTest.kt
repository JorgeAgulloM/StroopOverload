package com.softyorch.stroopoverload.data

import com.softyorch.stroopoverload.domain.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionProfilesTest {

    @Test
    fun `a guest session builds an anonymous profile`() {
        val profile = newSessionProfile(uid = "anon-uid-abcd", nickname = "Guest_ABCD", isAnonymous = true)

        assertTrue(profile.isAnonymous)
        assertTrue(profile.profileCreated)
        assertEquals("anon-uid-abcd", profile.userId)
    }

    @Test
    fun `an email session builds a registered profile named after the nickname`() {
        val profile = newSessionProfile(uid = "uid-12AB", nickname = "Neo", isAnonymous = false)

        assertFalse(profile.isAnonymous)
        assertEquals("Neo", profile.nickname)
        assertEquals("@neo-12ab", profile.uniqueName)
    }

    @Test
    fun `a fresh session starts from zero unless progress is carried over`() {
        val local = UserProfile(points = 40, highScore = 900, experience = 1200L, level = 4, matchesPlayed = 7)

        val fresh = newSessionProfile(uid = "uid-1", nickname = "Neo", isAnonymous = false)
        val carried = newSessionProfile(uid = "uid-1", nickname = "Neo", isAnonymous = false, carriedOver = local)

        assertEquals(0, fresh.points)
        assertEquals(1, fresh.level)
        assertEquals(40, carried.points)
        assertEquals(900, carried.highScore)
        assertEquals(1200L, carried.experience)
        assertEquals(4, carried.level)
    }

    @Test
    fun `the session decides anonymity even when the stored remote doc says otherwise`() {
        // Guests used to be saved as registered and pushed to Firestore with isAnonymous=false;
        // those docs still exist, so the flag must come from the auth session, not the doc.
        val staleGuestDoc = mapOf<String, Any?>("nickname" to "Guest_Z783", "isAnonymous" to false)

        val profile = profileFromRemote(uid = "anon-z783", data = staleGuestDoc, fallbackNickname = "Guest_Z783", isAnonymous = true)

        assertTrue(profile.isAnonymous)
    }

    @Test
    fun `a guest profile stored as registered is repaired for its own session`() {
        val stale = UserProfile(userId = "anon-1", nickname = "Guest_ANO1", isAnonymous = false, points = 30, profileCreated = true)

        val repaired = stale.repairedForSession(uid = "anon-1", isAnonymousSession = true)

        assertEquals(stale.copy(isAnonymous = true), repaired)
    }

    @Test
    fun `nothing is repaired when the flag already matches, the session is unknown or belongs to someone else`() {
        val guest = UserProfile(userId = "anon-1", isAnonymous = true, profileCreated = true)

        assertEquals(null, guest.repairedForSession(uid = "anon-1", isAnonymousSession = true))
        assertEquals(null, guest.copy(isAnonymous = false).repairedForSession(uid = "anon-1", isAnonymousSession = null))
        assertEquals(null, guest.copy(isAnonymous = false).repairedForSession(uid = "someone-else", isAnonymousSession = true))
        assertEquals(null, guest.copy(isAnonymous = false).repairedForSession(uid = null, isAnonymousSession = true))
    }

    @Test
    fun `a remote profile keeps its scoring fields`() {
        val doc = mapOf<String, Any?>(
            "nickname" to "Trinity",
            "uniqueName" to "@trinity-0001",
            "points" to 150L,
            "highScore" to 4200L,
            "experience" to 3000L,
            "level" to 6L,
            "matchesPlayed" to 9L,
        )

        val profile = profileFromRemote(uid = "uid-0001", data = doc, fallbackNickname = "Pilot_0001", isAnonymous = false)

        assertEquals("Trinity", profile.nickname)
        assertEquals("@trinity-0001", profile.uniqueName)
        assertEquals(150, profile.points)
        assertEquals(4200, profile.highScore)
        assertEquals(3000L, profile.experience)
        assertEquals(6, profile.level)
        assertEquals(9, profile.matchesPlayed)
        assertFalse(profile.isAnonymous)
        assertTrue(profile.profileCreated)
    }
}
