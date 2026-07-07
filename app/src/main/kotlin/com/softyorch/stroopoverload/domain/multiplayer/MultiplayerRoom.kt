package com.softyorch.stroopoverload.domain.multiplayer

import com.softyorch.stroopoverload.core.StroopColor

enum class RoomStatus {
    WAITING, STARTING, PLAYING, FINISHED;

    companion object {
        fun fromFirestoreValue(raw: String?): RoomStatus = when (raw) {
            "starting" -> STARTING
            "playing" -> PLAYING
            "finished" -> FINISHED
            else -> WAITING
        }
    }
}

data class RoomPlayer(
    val uid: String,
    val displayName: String,
    val avatarIndex: Int = 0,
    val alive: Boolean = true,
    val order: Int = 0,
)

data class MultiplayerStimulus(
    val wordLabel: StroopColor,
    val inkColor: StroopColor,
    val options: List<StroopColor>,
) {
    val correctAnswer: StroopColor get() = inkColor
}

data class MultiplayerRoom(
    val roomId: String = "",
    val code: String = "",
    val status: RoomStatus = RoomStatus.WAITING,
    val hostUid: String = "",
    val players: List<RoomPlayer> = emptyList(),
    val turnOrder: List<String> = emptyList(),
    val turnIndex: Int = 0,
    val round: Int = 0,
    val stimulus: MultiplayerStimulus? = null,
    val deadlineAtMs: Long? = null,
    val winnerUid: String? = null,
    val startsAtMs: Long? = null,
) {
    val currentTurnUid: String? get() = turnOrder.getOrNull(turnIndex)
    fun isMyTurn(uid: String): Boolean = currentTurnUid == uid
    fun player(uid: String): RoomPlayer? = players.find { it.uid == uid }
}
