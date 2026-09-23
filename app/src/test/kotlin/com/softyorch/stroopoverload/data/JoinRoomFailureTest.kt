package com.softyorch.stroopoverload.data

import org.junit.Assert.assertEquals
import org.junit.Test

class JoinRoomFailureTest {

    @Test
    fun `maps the joinRoom callable's error codes to typed failures`() {
        assertEquals(JoinRoomFailure.ROOM_NOT_FOUND, joinRoomFailureFor("NOT_FOUND"))
        assertEquals(JoinRoomFailure.ROOM_FULL, joinRoomFailureFor("RESOURCE_EXHAUSTED"))
        assertEquals(JoinRoomFailure.ALREADY_STARTED, joinRoomFailureFor("FAILED_PRECONDITION"))
        assertEquals(JoinRoomFailure.INVALID_CODE, joinRoomFailureFor("INVALID_ARGUMENT"))
    }

    @Test
    fun `anything else is UNKNOWN`() {
        assertEquals(JoinRoomFailure.UNKNOWN, joinRoomFailureFor("INTERNAL"))
        assertEquals(JoinRoomFailure.UNKNOWN, joinRoomFailureFor("UNAVAILABLE"))
    }
}
