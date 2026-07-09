package com.softyorch.stroopoverload.domain.multiplayer

import androidx.annotation.StringRes
import com.softyorch.stroopoverload.R
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

/**
 * Mirrors the Cloud Functions side's GameModeId. "solo_survival" is
 * deliberately not exposed here -- the backend accepts it as a valid value
 * but has no dedicated engine for it yet (falls through to the "mistake"
 * rules), so it isn't a real, selectable mode from the client's point of view.
 */
enum class RoomMode(@StringRes val titleRes: Int, @StringRes val descriptionRes: Int) {
    MISTAKE(R.string.mp_mode_mistake_title, R.string.mp_mode_mistake_desc),
    HOT_POTATO(R.string.mp_mode_hot_potato_title, R.string.mp_mode_hot_potato_desc);

    fun toFirestoreValue(): String = when (this) {
        MISTAKE -> "mistake"
        HOT_POTATO -> "hot_potato"
    }

    companion object {
        fun fromFirestoreValue(raw: String?): RoomMode = when (raw) {
            "hot_potato" -> HOT_POTATO
            else -> MISTAKE
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
    val mode: RoomMode = RoomMode.MISTAKE,
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
