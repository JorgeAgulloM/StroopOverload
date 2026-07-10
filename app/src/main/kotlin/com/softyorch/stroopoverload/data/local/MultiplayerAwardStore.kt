package com.softyorch.stroopoverload.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Tracks which finished multiplayer rooms already had their score applied to
 * the local profile, so a Firestore listener re-emitting the same FINISHED
 * room (reconnect, app restart while still on the results screen, etc.)
 * can't award the same match's points twice.
 */
class MultiplayerAwardStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("stroop_multiplayer_awards", Context.MODE_PRIVATE)

    fun hasAwarded(roomId: String): Boolean =
        roomId in awardedIds()

    fun markAwarded(roomId: String) {
        val updated = (awardedIds() + roomId).toList().takeLast(MAX_TRACKED_ROOMS)
        prefs.edit().putString(KEY_AWARDED_ROOM_IDS, updated.joinToString(",")).apply()
    }

    private fun awardedIds(): List<String> {
        val raw = prefs.getString(KEY_AWARDED_ROOM_IDS, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split(",")
    }

    companion object {
        private const val KEY_AWARDED_ROOM_IDS = "awarded_room_ids"

        // Bounded so this can't grow forever over months of play; far larger
        // than any realistic "still might re-emit the same finished room" window.
        private const val MAX_TRACKED_ROOMS = 200
    }
}
