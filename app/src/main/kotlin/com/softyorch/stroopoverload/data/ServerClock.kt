package com.softyorch.stroopoverload.data

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wall clock aligned with the backend's. Online deadlines (a turn's deadlineAtMs, a match's
 * startsAtMs) are server timestamps, so counting down to them on the raw device clock is off
 * by however far the phone's clock is -- a phone running 2 s slow showed a full bar while
 * the server had already timed the turn out.
 */
class ServerClock(private val deviceNowMs: () -> Long = System::currentTimeMillis) {

    @Volatile
    private var offsetMs = 0L

    fun nowMs(): Long = deviceNowMs() + offsetMs

    fun updateOffset(offsetMs: Long) {
        this.offsetMs = offsetMs
    }

    companion object {
        private const val TAG = "ServerClock"
        private val listening = AtomicBoolean(false)

        /** Process-wide instance the online screens read. Device clock until [listenTo] learns the offset. */
        val shared = ServerClock()

        /**
         * Keeps [shared] updated from Realtime Database's own estimate of the offset, which it
         * refreshes on every (re)connection. `.info` paths are local, so this opens no connection
         * of its own; presence tracking is what connects during online play.
         */
        fun listenTo(database: FirebaseDatabase) {
            if (!listening.compareAndSet(false, true)) return
            database.getReference(".info/serverTimeOffset").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    snapshot.getValue(Long::class.java)?.let(shared::updateOffset)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "serverTimeOffset listener cancelled: ${error.message}")
                }
            })
        }
    }
}
