package com.softyorch.stroopoverload.data

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerStimulus
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

    override suspend fun createRoom(displayName: String): Result<Pair<String, String>> = runCatching {
        val result = functions.getHttpsCallable("createRoom").call(mapOf("displayName" to displayName)).await()
        val map = result.data as Map<*, *>
        (map["roomId"] as String) to (map["code"] as String)
    }

    override suspend fun joinRoom(code: String, displayName: String): Result<String> = runCatching {
        val data = mapOf("code" to code, "displayName" to displayName)
        val result = functions.getHttpsCallable("joinRoom").call(data).await()
        (result.data as Map<*, *>)["roomId"] as String
    }

    override suspend fun startGame(roomId: String): Result<Unit> = runCatching {
        functions.getHttpsCallable("startGame").call(mapOf("roomId" to roomId)).await()
        Unit
    }

    override suspend fun submitAnswer(roomId: String, selectedColor: StroopColor): Result<Unit> = runCatching {
        val data = mapOf("roomId" to roomId, "selectedColor" to selectedColor.name)
        functions.getHttpsCallable("submitAnswer").call(data).await()
        Unit
    }

    override fun observeRoom(roomId: String): Flow<MultiplayerRoom> = callbackFlow {
        val ref = firestore.collection("rooms").document(roomId)
        val registration = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("MultiplayerRepo", "Room listener error: ${error.message}")
                return@addSnapshotListener
            }
            val data = snapshot?.data ?: return@addSnapshotListener
            trySend(mapRoom(roomId, data))
        }
        awaitClose { registration.remove() }
    }

    override fun trackPresence(roomId: String, uid: String) {
        val presenceRef = database.getReference("presence/$roomId/$uid")
        val offlineValue = mapOf("state" to "offline", "lastChanged" to ServerValue.TIMESTAMP)
        val onlineValue = mapOf("state" to "online", "lastChanged" to ServerValue.TIMESTAMP)
        presenceRef.onDisconnect().setValue(offlineValue)
        presenceRef.setValue(onlineValue)
    }

    private fun mapRoom(roomId: String, data: Map<String, Any?>): MultiplayerRoom {
        @Suppress("UNCHECKED_CAST")
        val playersMap = data["players"] as? Map<String, Map<String, Any?>> ?: emptyMap()
        val players = playersMap.values.map { p ->
            RoomPlayer(
                uid = p["uid"] as? String ?: "",
                displayName = p["displayName"] as? String ?: "Pilot",
                avatarIndex = (p["avatarIndex"] as? Long)?.toInt() ?: 0,
                alive = p["alive"] as? Boolean ?: true,
                order = (p["order"] as? Long)?.toInt() ?: 0,
            )
        }.sortedBy { it.order }

        @Suppress("UNCHECKED_CAST")
        val stimulusMap = data["stimulus"] as? Map<String, Any?>
        val stimulus = stimulusMap?.let {
            @Suppress("UNCHECKED_CAST")
            val options = it["options"] as? List<String> ?: emptyList()
            MultiplayerStimulus(
                wordLabel = StroopColor.valueOf(it["wordLabel"] as String),
                inkColor = StroopColor.valueOf(it["inkColor"] as String),
                options = options.map(StroopColor::valueOf),
            )
        }

        @Suppress("UNCHECKED_CAST")
        val turnOrder = data["turnOrder"] as? List<String> ?: emptyList()

        return MultiplayerRoom(
            roomId = roomId,
            code = data["code"] as? String ?: "",
            status = RoomStatus.fromFirestoreValue(data["status"] as? String),
            hostUid = data["hostUid"] as? String ?: "",
            players = players,
            turnOrder = turnOrder,
            turnIndex = (data["turnIndex"] as? Long)?.toInt() ?: 0,
            round = (data["round"] as? Long)?.toInt() ?: 0,
            stimulus = stimulus,
            deadlineAtMs = data["deadlineAtMs"] as? Long,
            winnerUid = data["winnerUid"] as? String,
        )
    }
}
