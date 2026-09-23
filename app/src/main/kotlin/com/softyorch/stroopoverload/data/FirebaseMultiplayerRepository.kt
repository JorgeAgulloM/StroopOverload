package com.softyorch.stroopoverload.data

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableResult
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.core.runCatchingCancellable
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerStimulus
import com.softyorch.stroopoverload.domain.multiplayer.RoomMode
import com.softyorch.stroopoverload.domain.multiplayer.RoomPlayer
import com.softyorch.stroopoverload.domain.multiplayer.RoomStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseMultiplayerRepository(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(),
) : MultiplayerRepository {

    override suspend fun createRoom(displayName: String, mode: RoomMode): Result<Pair<String, String>> = call("createRoom") {
        val data = mapOf("displayName" to displayName, "mode" to mode.toFirestoreValue())
        val result = callTyped("createRoom", data)
        val map = result.data as Map<*, *>
        (map["roomId"] as String) to (map["code"] as String)
    }

    override suspend fun joinRoom(code: String, displayName: String): Result<String> = call("joinRoom") {
        val data = mapOf("code" to code, "displayName" to displayName)
        (callTyped("joinRoom", data).data as Map<*, *>)["roomId"] as String
    }

    override suspend fun startGame(roomId: String): Result<Unit> = call("startGame") {
        functions.getHttpsCallable("startGame").call(mapOf("roomId" to roomId)).await()
        Unit
    }

    override suspend fun submitAnswer(roomId: String, selectedColor: StroopColor, round: Int): Result<Unit> = call("submitAnswer") {
        val data = mapOf("roomId" to roomId, "selectedColor" to selectedColor.name, "round" to round)
        functions.getHttpsCallable("submitAnswer").call(data).await()
        Unit
    }

    /**
     * Terminates with an error (instead of silently going quiet) when the listener
     * fails or the room document disappears, so the ViewModel can leave the room
     * and tell the player rather than leaving them on a frozen screen.
     */
    override fun observeRoom(roomId: String): Flow<MultiplayerRoom> = callbackFlow {
        val ref = firestore.collection("rooms").document(roomId)
        val registration = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Room listener error for room $roomId: ${error.message}")
                close(error)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            val data = snapshot.data
            if (!snapshot.exists() || data == null) {
                close(RoomUnavailableException(roomId))
                return@addSnapshotListener
            }
            trySend(mapRoom(roomId, data))
        }
        awaitClose { registration.remove() }
    }

    override suspend fun deleteMyMultiplayerData(): Result<Unit> = call("deleteMyMultiplayerData") {
        functions.getHttpsCallable("deleteMyMultiplayerData").call().await()
        Unit
    }

    /** Invokes a callable, converting a rejection into a [MultiplayerCallException] the UI can explain. */
    private suspend fun callTyped(name: String, data: Map<String, Any?>): HttpsCallableResult = try {
        functions.getHttpsCallable(name).call(data).await()
    } catch (e: FirebaseFunctionsException) {
        throw MultiplayerCallException(multiplayerCallFailureFor(e.code.name, reasonDetailOf(e.details)), e)
    }

    /** Runs a callable: cancellation propagates, failures are logged here (the UI only ever gets a typed reason). */
    private inline fun <T> call(name: String, block: () -> T): Result<T> =
        runCatchingCancellable(block).onFailure { Log.w(TAG, "$name failed: ${it.message}", it) }

    override fun trackPresence(roomId: String, uid: String) {
        val presenceRef = database.getReference("presence/$roomId/$uid")
        val offlineValue = mapOf("state" to "offline", "lastChanged" to ServerValue.TIMESTAMP)
        val onlineValue = mapOf("state" to "online", "lastChanged" to ServerValue.TIMESTAMP)
        presenceRef.onDisconnect().setValue(offlineValue)
            .addOnFailureListener { Log.w(TAG, "Failed to register presence onDisconnect for room $roomId: ${it.message}") }
        presenceRef.setValue(onlineValue)
            .addOnFailureListener { Log.w(TAG, "Failed to write online presence for room $roomId: ${it.message}") }
    }

    private fun mapRoom(roomId: String, data: Map<String, Any?>): MultiplayerRoom {
        @Suppress("UNCHECKED_CAST")
        val playersMap = data["players"] as? Map<String, Map<String, Any?>> ?: emptyMap()
        val players = playersMap.values.map { p ->
            @Suppress("UNCHECKED_CAST")
            val soloStimulusMap = p["soloStimulus"] as? Map<String, Any?>
            RoomPlayer(
                uid = p["uid"] as? String ?: "",
                displayName = p["displayName"] as? String ?: "Pilot",
                avatarIndex = (p["avatarIndex"] as? Long)?.toInt() ?: 0,
                alive = p["alive"] as? Boolean ?: true,
                order = (p["order"] as? Long)?.toInt() ?: 0,
                soloScore = (p["soloScore"] as? Long)?.toInt() ?: 0,
                soloRound = (p["soloRound"] as? Long)?.toInt() ?: 0,
                soloStimulus = parseStimulus(soloStimulusMap),
                soloDeadlineAtMs = p["soloDeadlineAtMs"] as? Long,
                matchScore = (p["matchScore"] as? Long)?.toInt() ?: 0,
                eliminatedAtMs = p["eliminatedAtMs"] as? Long,
                placement = (p["placement"] as? Long)?.toInt(),
                finalScore = (p["finalScore"] as? Long)?.toInt(),
            )
        }.sortedBy { it.order }

        @Suppress("UNCHECKED_CAST")
        val stimulus = parseStimulus(data["stimulus"] as? Map<String, Any?>)

        @Suppress("UNCHECKED_CAST")
        val turnOrder = data["turnOrder"] as? List<String> ?: emptyList()

        return MultiplayerRoom(
            roomId = roomId,
            code = data["code"] as? String ?: "",
            status = RoomStatus.fromFirestoreValue(data["status"] as? String),
            mode = RoomMode.fromFirestoreValue(data["mode"] as? String),
            hostUid = data["hostUid"] as? String ?: "",
            players = players,
            turnOrder = turnOrder,
            turnIndex = (data["turnIndex"] as? Long)?.toInt() ?: 0,
            round = (data["round"] as? Long)?.toInt() ?: 0,
            stimulus = stimulus,
            deadlineAtMs = data["deadlineAtMs"] as? Long,
            winnerUid = data["winnerUid"] as? String,
            startsAtMs = data["startsAtMs"] as? Long,
            createdAtMs = data["createdAtMs"] as? Long ?: 0L,
            awardsAppliedAtMs = data["awardsAppliedAtMs"] as? Long,
        )
    }

    private fun parseStimulus(stimulusMap: Map<String, Any?>?): MultiplayerStimulus? = stimulusMap?.let {
        @Suppress("UNCHECKED_CAST")
        val rawOptions = it["options"] as? List<String> ?: emptyList()
        val options = rawOptions.mapNotNull { raw ->
            StroopColor.entries.find { color -> color.name == raw }.also { parsed ->
                if (parsed == null) Log.w(TAG, "Unrecognized stimulus option color: $raw")
            }
        }
        MultiplayerStimulus(
            wordLabel = parseStroopColor(it["wordLabel"] as? String, fallback = StroopColor.RED),
            inkColor = parseStroopColor(it["inkColor"] as? String, fallback = StroopColor.RED),
            options = options.ifEmpty { StroopColor.entries.toList() },
        )
    }

    private fun parseStroopColor(raw: String?, fallback: StroopColor): StroopColor =
        StroopColor.entries.find { it.name == raw } ?: fallback.also {
            Log.w(TAG, "Unrecognized stimulus color value: $raw, falling back to $fallback")
        }

    private companion object {
        const val TAG = "MultiplayerRepo"
    }
}
