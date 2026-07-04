package com.softyorch.stroopoverload.domain.multiplayer

import com.softyorch.stroopoverload.core.StroopColor
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

    @Test
    fun `RoomStatus fromFirestoreValue maps raw strings correctly`() {
        assertEquals(RoomStatus.WAITING, RoomStatus.fromFirestoreValue("waiting"))
        assertEquals(RoomStatus.PLAYING, RoomStatus.fromFirestoreValue("playing"))
        assertEquals(RoomStatus.FINISHED, RoomStatus.fromFirestoreValue("finished"))
        assertEquals(RoomStatus.WAITING, RoomStatus.fromFirestoreValue(null))
        assertEquals(RoomStatus.WAITING, RoomStatus.fromFirestoreValue("garbage"))
    }

    @Test
    fun `player looks up a room player by uid, or returns null`() {
        val room = MultiplayerRoom(players = listOf(RoomPlayer(uid = "a", displayName = "Neo")))
        assertEquals("Neo", room.player("a")?.displayName)
        assertEquals(null, room.player("does-not-exist"))
    }

    @Test
    fun `MultiplayerStimulus correctAnswer is always the ink color`() {
        val stimulus = MultiplayerStimulus(
            wordLabel = StroopColor.RED,
            inkColor = StroopColor.BLUE,
            options = listOf(StroopColor.RED, StroopColor.GREEN, StroopColor.BLUE, StroopColor.YELLOW),
        )
        assertEquals(StroopColor.BLUE, stimulus.correctAnswer)
    }
}
