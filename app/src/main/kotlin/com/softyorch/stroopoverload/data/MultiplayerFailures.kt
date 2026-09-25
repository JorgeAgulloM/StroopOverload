package com.softyorch.stroopoverload.data

/** Why a multiplayer callable rejected the call, so the UI can show a specific localized message. */
enum class MultiplayerCallFailure { ROOM_NOT_FOUND, ROOM_FULL, ALREADY_STARTED, INVALID_CODE, RATE_LIMITED, UNKNOWN }

class MultiplayerCallException(val failure: MultiplayerCallFailure, cause: Throwable? = null) : Exception(cause)

/** The observed room document no longer exists (purged, or deleted along with a player's account). */
class RoomUnavailableException(roomId: String) : Exception("Room $roomId no longer exists")

/**
 * Maps a rejected callable to a failure the UI can explain.
 *
 * Prefers the `reason` the backend puts in HttpsError's details (functions/src/index.ts),
 * because the error codes alone are ambiguous: resource-exhausted means both "room full"
 * and "rate limited", and failed-precondition covers several unrelated cases. The code
 * fallback only matters for a client talking to a backend deployed before details existed.
 *
 * Takes `FirebaseFunctionsException.Code.name` rather than the enum itself: that enum's
 * static initializer touches Android APIs, so it can't load in JVM unit tests.
 */
fun multiplayerCallFailureFor(functionsErrorCode: String, reasonDetail: String?): MultiplayerCallFailure =
    when (reasonDetail) {
        "ROOM_NOT_FOUND" -> MultiplayerCallFailure.ROOM_NOT_FOUND
        "ROOM_FULL" -> MultiplayerCallFailure.ROOM_FULL
        "ALREADY_STARTED" -> MultiplayerCallFailure.ALREADY_STARTED
        "INVALID_CODE" -> MultiplayerCallFailure.INVALID_CODE
        "RATE_LIMITED" -> MultiplayerCallFailure.RATE_LIMITED
        else -> when (functionsErrorCode) {
            "NOT_FOUND" -> MultiplayerCallFailure.ROOM_NOT_FOUND
            "RESOURCE_EXHAUSTED" -> MultiplayerCallFailure.ROOM_FULL
            "FAILED_PRECONDITION" -> MultiplayerCallFailure.ALREADY_STARTED
            "INVALID_ARGUMENT" -> MultiplayerCallFailure.INVALID_CODE
            else -> MultiplayerCallFailure.UNKNOWN
        }
    }

/** Reads the `reason` string the backend attaches to HttpsError details, if present. */
fun reasonDetailOf(details: Any?): String? = (details as? Map<*, *>)?.get("reason") as? String
