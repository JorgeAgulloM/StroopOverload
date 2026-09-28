package com.softyorch.stroopoverload.data

import com.softyorch.stroopoverload.domain.ServerScoring
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingRunSyncTest {

    private class FakeQueue(runs: List<PendingSoloRun>) : PendingRunQueue {
        val runs = runs.toMutableList()
        override fun all(): List<PendingSoloRun> = runs.toList()
        override fun add(run: PendingSoloRun) { runs += run }
        override fun remove(runId: String) { runs.removeAll { it.runId == runId } }
    }

    private fun run(id: String, uid: String = "uid-1", createdAtEpochMs: Long = NOW) = PendingSoloRun(
        runId = id, uid = uid, mode = "ENDLESS", correctHits = 10, totalRounds = 11,
        survivalMs = 15_000L, finalScore = 100, winStreak = 3, achievementIds = emptyList(),
        createdAtEpochMs = createdAtEpochMs,
    )

    private fun syncOf(
        queue: PendingRunQueue,
        submitter: SoloRunSubmitter,
        onScoring: (ServerScoring) -> Unit,
        onResync: suspend (String) -> Unit = {},
    ) = PendingRunSync(queue, submitter, onScoring, onResync, now = { NOW })

    private fun staleRun(id: String) = run(id, createdAtEpochMs = NOW - MAX_PENDING_RUN_AGE_MS - 1)

    private val scoring = ServerScoring(
        points = 100, highScore = 100, experience = 300L, level = 2,
        matchesPlayed = 1, matchesWon = 1, matchesLost = 0, dailyStreak = 1,
    )

    @Test
    fun `accepted runs leave the queue and the last answer is applied`() = runTest {
        val queue = FakeQueue(listOf(run("a"), run("b")))
        val applied = mutableListOf<ServerScoring>()
        val sync = syncOf(queue, { r ->
            SubmitOutcome.Accepted(scoring.copy(matchesPlayed = if (r.runId == "a") 1 else 2))
        }, { applied += it })

        sync.flush("uid-1")

        assertTrue(queue.runs.isEmpty())
        // The server's answer for "a" doesn't count "b" yet; only the answer given once
        // nothing is left queued is the full picture.
        assertEquals(listOf(2), applied.map { it.matchesPlayed })
    }

    @Test
    fun `an answer is not applied while a later run of the account is still queued`() = runTest {
        // The local profile already counts "b" provisionally; the answer for "a" alone
        // would erase that until "b" gets through.
        val queue = FakeQueue(listOf(run("a"), run("b")))
        val applied = mutableListOf<ServerScoring>()
        val sync = syncOf(queue, { r ->
            if (r.runId == "a") SubmitOutcome.Accepted(scoring) else SubmitOutcome.RetryLater
        }, { applied += it })

        sync.flush("uid-1")

        assertTrue(applied.isEmpty())
        assertEquals(listOf("b"), queue.runs.map { it.runId })
    }

    @Test
    fun `discarding an account drops only its runs`() = runTest {
        val queue = FakeQueue(listOf(run("mine-1"), run("theirs", uid = "uid-2"), run("mine-2")))
        val sync = syncOf(queue, { SubmitOutcome.Accepted(scoring) }, {})

        sync.discard("uid-1")

        assertEquals(listOf("theirs"), queue.runs.map { it.runId })
    }

    @Test
    fun `discarding waits for a submission already in flight`() = runTest {
        // Account deletion must not delete the profile while a run is being applied to it,
        // or the server would write the deleted profile back.
        val queue = FakeQueue(listOf(run("a"), run("b")))
        val gate = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val sync = syncOf(queue, { r ->
            events += "submit ${r.runId}"
            gate.await()
            SubmitOutcome.RetryLater
        }, {})

        launch { sync.flush("uid-1") }
        advanceUntilIdle()
        launch { sync.discard("uid-1"); events += "discarded" }
        advanceUntilIdle()
        assertEquals(listOf("submit a"), events)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("submit a", "discarded"), events)
        assertTrue(queue.runs.isEmpty())
    }

    @Test
    fun `a rejected run leaves the queue so it is not retried forever`() = runTest {
        val queue = FakeQueue(listOf(run("bad"), run("good")))
        val submitted = mutableListOf<String>()
        val sync = syncOf(queue, { r ->
            submitted += r.runId
            if (r.runId == "bad") SubmitOutcome.Rejected else SubmitOutcome.Accepted(scoring)
        }, {})

        sync.flush("uid-1")

        assertEquals(listOf("bad", "good"), submitted)
        assertTrue(queue.runs.isEmpty())
    }

    @Test
    fun `a transient failure keeps the run and stops, preserving order`() = runTest {
        val queue = FakeQueue(listOf(run("a"), run("b")))
        val submitted = mutableListOf<String>()
        val sync = syncOf(queue, { r -> submitted += r.runId; SubmitOutcome.RetryLater }, {})

        sync.flush("uid-1")

        assertEquals(listOf("a"), submitted)
        assertEquals(listOf("a", "b"), queue.runs.map { it.runId })
    }

    @Test
    fun `another account's runs are never submitted and stay queued`() = runTest {
        val queue = FakeQueue(listOf(run("mine"), run("theirs", uid = "uid-2")))
        val submitted = mutableListOf<String>()
        val sync = syncOf(queue, { r -> submitted += r.runId; SubmitOutcome.Accepted(scoring) }, {})

        sync.flush("uid-1")

        assertEquals(listOf("mine"), submitted)
        assertEquals(listOf("theirs"), queue.runs.map { it.runId })
    }

    @Test
    fun `an accepted run without scoring in the answer still leaves the queue`() = runTest {
        val queue = FakeQueue(listOf(run("a")))
        val applied = mutableListOf<ServerScoring>()
        val sync = syncOf(queue, { SubmitOutcome.Accepted(null) }, { applied += it })

        sync.flush("uid-1")

        assertTrue(queue.runs.isEmpty())
        assertTrue(applied.isEmpty())
    }

    @Test
    fun `two overlapping flushes submit each run once`() = runTest {
        val queue = FakeQueue(listOf(run("a")))
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val sync = syncOf(queue, {
            calls++
            gate.await()
            SubmitOutcome.Accepted(scoring)
        }, {})

        launch { sync.flush("uid-1") }
        launch { sync.flush("uid-1") }
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, calls)
        assertTrue(queue.runs.isEmpty())
    }

    @Test
    fun `a run queued more than 24 hours ago is dropped without being submitted`() = runTest {
        // An offline run only counts if it reaches the server within a day of being played.
        val queue = FakeQueue(listOf(run("stale", createdAtEpochMs = NOW - MAX_PENDING_RUN_AGE_MS - 1), run("fresh")))
        val submitted = mutableListOf<String>()
        val sync = syncOf(queue, { r -> submitted += r.runId; SubmitOutcome.Accepted(scoring) }, {})

        sync.flush("uid-1")

        assertEquals(listOf("fresh"), submitted)
        assertTrue(queue.runs.isEmpty())
    }

    @Test
    fun `a run exactly 24 hours old is still submitted`() = runTest {
        val queue = FakeQueue(listOf(run("edge", createdAtEpochMs = NOW - MAX_PENDING_RUN_AGE_MS)))
        val submitted = mutableListOf<String>()
        val sync = syncOf(queue, { r -> submitted += r.runId; SubmitOutcome.Accepted(scoring) }, {})

        sync.flush("uid-1")

        assertEquals(listOf("edge"), submitted)
    }

    @Test
    fun `a stale run is dropped even when the server cannot be reached`() = runTest {
        // Otherwise a run that expired while offline would still be sent on the next retry.
        val queue = FakeQueue(listOf(run("stale", createdAtEpochMs = NOW - MAX_PENDING_RUN_AGE_MS - 1), run("fresh")))
        val sync = syncOf(queue, { SubmitOutcome.RetryLater }, {})

        sync.flush("uid-1")

        assertEquals(listOf("fresh"), queue.runs.map { it.runId })
    }

    @Test
    fun `another account's stale runs are left for that account`() = runTest {
        val queue = FakeQueue(listOf(run("theirs", uid = "uid-2", createdAtEpochMs = 0L)))
        val sync = syncOf(queue, { SubmitOutcome.Accepted(scoring) }, {})

        sync.flush("uid-1")

        assertEquals(listOf("theirs"), queue.runs.map { it.runId })
    }

    @Test
    fun `a run stamped in the future by a clock change is not dropped`() = runTest {
        val queue = FakeQueue(listOf(run("ahead", createdAtEpochMs = NOW + MAX_PENDING_RUN_AGE_MS * 2)))
        val submitted = mutableListOf<String>()
        val sync = syncOf(queue, { r -> submitted += r.runId; SubmitOutcome.Accepted(scoring) }, {})

        sync.flush("uid-1")

        assertEquals(listOf("ahead"), submitted)
    }

    @Test
    fun `dropping every queued run re-reads the server's scoring`() = runTest {
        // The local profile counted the dropped runs provisionally and no answer will ever
        // replace those numbers; the server's copy never included them.
        val queue = FakeQueue(listOf(staleRun("stale")))
        val resynced = mutableListOf<String>()
        val sync = syncOf(queue, { SubmitOutcome.Accepted(scoring) }, {}, onResync = { resynced += it })

        sync.flush("uid-1")

        assertEquals(listOf("uid-1"), resynced)
    }

    @Test
    fun `no re-read when an answer for a later run already carries the scoring`() = runTest {
        val queue = FakeQueue(listOf(staleRun("stale"), run("fresh")))
        val resynced = mutableListOf<String>()
        val applied = mutableListOf<ServerScoring>()
        val sync = syncOf(queue, { SubmitOutcome.Accepted(scoring) }, { applied += it }, onResync = { resynced += it })

        sync.flush("uid-1")

        assertEquals(1, applied.size)
        assertTrue(resynced.isEmpty())
    }

    @Test
    fun `no re-read while a fresh run is still queued`() = runTest {
        // The local profile still counts that run; the server's copy would erase it.
        val queue = FakeQueue(listOf(staleRun("stale"), run("fresh")))
        val resynced = mutableListOf<String>()
        val sync = syncOf(queue, { SubmitOutcome.RetryLater }, {}, onResync = { resynced += it })

        sync.flush("uid-1")

        assertTrue(resynced.isEmpty())
    }

    @Test
    fun `no re-read when nothing was dropped`() = runTest {
        val queue = FakeQueue(emptyList())
        val resynced = mutableListOf<String>()
        val sync = syncOf(queue, { SubmitOutcome.Accepted(scoring) }, {}, onResync = { resynced += it })

        sync.flush("uid-1")

        assertTrue(resynced.isEmpty())
    }

    @Test
    fun `a rejected last run re-reads the server's scoring`() = runTest {
        // Same gap as an expired run: counted locally, never counted by the server.
        val queue = FakeQueue(listOf(run("bad")))
        val resynced = mutableListOf<String>()
        val sync = syncOf(queue, { SubmitOutcome.Rejected }, {}, onResync = { resynced += it })

        sync.flush("uid-1")

        assertEquals(listOf("uid-1"), resynced)
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
