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

/** Mirrors the Cloud Functions side's GameModeId. */
enum class RoomMode(@StringRes val titleRes: Int, @StringRes val descriptionRes: Int) {
    MISTAKE(R.string.mp_mode_mistake_title, R.string.mp_mode_mistake_desc),
    HOT_POTATO(R.string.mp_mode_hot_potato_title, R.string.mp_mode_hot_potato_desc),
    SOLO_SURVIVAL(R.string.mp_mode_solo_survival_title, R.string.mp_mode_solo_survival_desc);

    fun toFirestoreValue(): String = when (this) {
        MISTAKE -> "mistake"
        HOT_POTATO -> "hot_potato"
        SOLO_SURVIVAL -> "solo_survival"
    }

    companion object {
        fun fromFirestoreValue(raw: String?): RoomMode = when (raw) {
            "hot_potato" -> HOT_POTATO
            "solo_survival" -> SOLO_SURVIVAL
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
    // solo_survival only -- unused (left at defaults) in mistake/hot_potato. Each
    // player runs their own independent Stroop session against their own
    // stimulus/deadline instead of the room's shared turn state.
    val soloScore: Int = 0,
    val soloRound: Int = 0,
    val soloStimulus: MultiplayerStimulus? = null,
    val soloDeadlineAtMs: Long? = null,
    // mistake/hot_potato only -- live accumulated score, mirrors soloScore's role.
    val matchScore: Int = 0,
    // mistake/hot_potato only -- server timestamp of this player's elimination
    // (a hot_potato match can eliminate several players before it ends).
    val eliminatedAtMs: Long? = null,
    // Set once, server-side, when the match finishes. 1-based; 1 == winner.
    // finalScore is the profile points this player earned (halved raw score
    // times a placement multiplier -- see functions/src/scoring.ts).
    val placement: Int? = null,
    val finalScore: Int? = null,
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
    val createdAtMs: Long = 0L,
    /**
     * Set by the backend (onRoomFinished) once every player's profile has been
     * credited with this match's points. The client waits for it before reading its
     * profile back, instead of guessing when the award landed.
     */
    val awardsAppliedAtMs: Long? = null,
) {
    val currentTurnUid: String? get() = turnOrder.getOrNull(turnIndex)
    fun isMyTurn(uid: String): Boolean = currentTurnUid == uid
    fun player(uid: String): RoomPlayer? = players.find { it.uid == uid }

    /**
     * Whether [uid] may submit an answer right now. In the turn-based modes
     * that means holding the shared turn; in solo_survival there is no shared
     * turn at all -- any player who hasn't busted yet can always answer
     * against their own stimulus.
     */
    fun canAnswer(uid: String): Boolean = when (mode) {
        RoomMode.SOLO_SURVIVAL -> player(uid)?.alive == true
        RoomMode.MISTAKE, RoomMode.HOT_POTATO -> isMyTurn(uid)
    }
}
