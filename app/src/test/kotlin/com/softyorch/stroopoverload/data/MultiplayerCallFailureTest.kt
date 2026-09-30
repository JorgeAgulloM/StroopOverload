package com.softyorch.stroopoverload.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MultiplayerCallFailureTest {

    @Test
    fun `prefers the reason detail the backend attaches`() {
        assertEquals(MultiplayerCallFailure.ROOM_NOT_FOUND, multiplayerCallFailureFor("NOT_FOUND", "ROOM_NOT_FOUND"))
        assertEquals(MultiplayerCallFailure.ROOM_FULL, multiplayerCallFailureFor("RESOURCE_EXHAUSTED", "ROOM_FULL"))
        assertEquals(MultiplayerCallFailure.ALREADY_STARTED, multiplayerCallFailureFor("FAILED_PRECONDITION", "ALREADY_STARTED"))
        assertEquals(MultiplayerCallFailure.INVALID_CODE, multiplayerCallFailureFor("INVALID_ARGUMENT", "INVALID_CODE"))
    }

    @Test
    fun `tells a rate-limited call apart from a full room, which share an error code`() {
        assertEquals(MultiplayerCallFailure.RATE_LIMITED, multiplayerCallFailureFor("RESOURCE_EXHAUSTED", "RATE_LIMITED"))
        assertEquals(MultiplayerCallFailure.ROOM_FULL, multiplayerCallFailureFor("RESOURCE_EXHAUSTED", "ROOM_FULL"))
    }

    @Test
    fun `falls back to the error code when the backend sent no reason`() {
        assertEquals(MultiplayerCallFailure.ROOM_NOT_FOUND, multiplayerCallFailureFor("NOT_FOUND", null))
        assertEquals(MultiplayerCallFailure.ROOM_FULL, multiplayerCallFailureFor("RESOURCE_EXHAUSTED", null))
        assertEquals(MultiplayerCallFailure.ALREADY_STARTED, multiplayerCallFailureFor("FAILED_PRECONDITION", null))
        assertEquals(MultiplayerCallFailure.INVALID_CODE, multiplayerCallFailureFor("INVALID_ARGUMENT", null))
    }

    @Test
    fun `anything else is UNKNOWN`() {
        assertEquals(MultiplayerCallFailure.UNKNOWN, multiplayerCallFailureFor("INTERNAL", null))
        assertEquals(MultiplayerCallFailure.UNKNOWN, multiplayerCallFailureFor("UNAVAILABLE", "SOMETHING_NEW"))
    }

    @Test
    fun `reads the reason out of the details payload shape Firebase delivers`() {
        assertEquals("RATE_LIMITED", reasonDetailOf(mapOf("reason" to "RATE_LIMITED")))
        assertNull(reasonDetailOf(null))
        assertNull(reasonDetailOf("not a map"))
        assertNull(reasonDetailOf(mapOf("other" to "x")))
    }
}
