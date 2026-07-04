package com.softyorch.stroopoverload.domain.multiplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiplayerRoomTest {
    @Test
    fun `isMyTurn is true only for the uid at turnIndex`() {
        val room = MultiplayerRoom(turnOrder = listOf("a", "b", "c"), turnIndex = 1)
        assertTrue(room.isMyTurn("b"))
        assertFalse(room.isMyTurn("a"))
        assertFalse(room.isMyTurn("c"))
    }

    @Test
    fun `currentTurnUid is null when turnOrder is empty`() {
        val room = MultiplayerRoom(turnOrder = emptyList())
        assertEquals(null, room.currentTurnUid)
    }
}
