package com.softyorch.stroopoverload.data

import com.softyorch.stroopoverload.domain.ServerScoring
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * How long a finished run may wait in the queue. Older runs are dropped unreported: an offline
 * run only counts if it reaches the server within a day of being played. Measured on the
 * device clock, like the run's timestamp, so the server cannot enforce it.
 */
internal const val MAX_PENDING_RUN_AGE_MS = 24L * 60 * 60 * 1000

/** Where finished solo runs wait until the server has answered for them, oldest first. */
interface PendingRunQueue {
    fun all(): List<PendingSoloRun>
    fun add(run: PendingSoloRun)
    fun remove(runId: String)
}

/** What the server said about one submitted run. */
sealed interface SubmitOutcome {
    /** Applied, or already applied before. [scoring] is null if the answer carried none. */
    data class Accepted(val scoring: ServerScoring?) : SubmitOutcome

    /** Refused for good (implausible run, guest account): retrying would be refused again. */
    data object Rejected : SubmitOutcome

    /** No answer or a transient error (offline, timeout, rate limit): try again later. */
    data object RetryLater : SubmitOutcome
}

fun interface SoloRunSubmitter {
    suspend fun submit(run: PendingSoloRun): SubmitOutcome
}

/**
 * Sends the queued runs of the signed-in account, oldest first. A transient failure stops
 * the flush and keeps the rest queued in order; the next flush picks up from there.
 * Overlapping flushes (app start and a run ending at once) are serialized, so a run is
 * never in flight twice from this device.
 *
 * Runs older than [MAX_PENDING_RUN_AGE_MS] are dropped before anything is sent. If dropped
 * or rejected runs leave the account's queue empty with no scoring from the server,
 * [onResync] re-reads the server's scoring: the local profile counted those runs and
 * nothing else would ever replace those numbers.
 *
 * The server's scoring is only handed over once none of the account's runs remain queued:
 * the local profile already counts those provisionally, and an answer for an earlier run
 * would erase them until they get through.
 */
class PendingRunSync(
    private val queue: PendingRunQueue,
    private val submitter: SoloRunSubmitter,
    private val onScoring: (ServerScoring) -> Unit,
    private val onResync: suspend (uid: String) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    suspend fun flush(uid: String) = mutex.withLock {
        val (stale, fresh) = queue.all().filter { it.uid == uid }.partition { isStale(it) }
        stale.forEach { queue.remove(it.runId) }
        var latest: ServerScoring? = null
        var anyRejected = false
        for (run in fresh) {
            when (val outcome = submitter.submit(run)) {
                is SubmitOutcome.Accepted -> {
                    queue.remove(run.runId)
                    latest = outcome.scoring ?: latest
                }
                SubmitOutcome.Rejected -> {
                    queue.remove(run.runId)
                    anyRejected = true
                }
                SubmitOutcome.RetryLater -> break
            }
        }
        if (queue.all().any { it.uid == uid }) return@withLock
        when {
            latest != null -> onScoring(latest)
            stale.isNotEmpty() || anyRejected -> onResync(uid)
        }
    }

    private fun isStale(run: PendingSoloRun): Boolean =
        now() - run.createdAtEpochMs > MAX_PENDING_RUN_AGE_MS

    /**
     * Drops the account's queued runs, after any submission already in flight has finished.
     * For account deletion: a run applied after the profile is deleted would write it back.
     */
    suspend fun discard(uid: String) = mutex.withLock {
        queue.all().filter { it.uid == uid }.forEach { queue.remove(it.runId) }
    }
}
