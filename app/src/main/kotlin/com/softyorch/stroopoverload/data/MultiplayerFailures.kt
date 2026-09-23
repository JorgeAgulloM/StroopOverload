package com.softyorch.stroopoverload.data

/** Why the joinRoom callable rejected a join, so the UI can show a specific localized message. */
enum class JoinRoomFailure { ROOM_NOT_FOUND, ROOM_FULL, ALREADY_STARTED, INVALID_CODE, UNKNOWN }

class JoinRoomException(val failure: JoinRoomFailure, cause: Throwable? = null) : Exception(cause)

/** The observed room document no longer exists (purged, or deleted along with a player's account). */
class RoomUnavailableException(roomId: String) : Exception("Room $roomId no longer exists")

/**
 * Mirrors the HttpsError codes thrown by joinRoom in functions/src/index.ts.
 * Takes `FirebaseFunctionsException.Code.name` rather than the enum itself: that
 * enum's static initializer touches Android APIs, so it can't load in JVM unit tests.
 */
fun joinRoomFailureFor(functionsErrorCode: String): JoinRoomFailure = when (functionsErrorCode) {
    "NOT_FOUND" -> JoinRoomFailure.ROOM_NOT_FOUND
    "RESOURCE_EXHAUSTED" -> JoinRoomFailure.ROOM_FULL
    "FAILED_PRECONDITION" -> JoinRoomFailure.ALREADY_STARTED
    "INVALID_ARGUMENT" -> JoinRoomFailure.INVALID_CODE
    else -> JoinRoomFailure.UNKNOWN
}
