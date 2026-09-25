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

    private fun run(id: String, uid: String = "uid-1") = PendingSoloRun(
        runId = id, uid = uid, mode = "ENDLESS", correctHits = 10, totalRounds = 11,
        survivalMs = 15_000L, finalScore = 100, winStreak = 3, achievementIds = emptyList(), createdAtEpochMs = 0L,
    )

    private val scoring = ServerScoring(
        points = 100, highScore = 100, experience = 300L, level = 2,
        matchesPlayed = 1, matchesWon = 1, matchesLost = 0, dailyStreak = 1,
    )

    @Test
    fun `accepted runs leave the queue and the last answer is applied`() = runTest {
        val queue = FakeQueue(listOf(run("a"), run("b")))
        val applied = mutableListOf<ServerScoring>()
        val sync = PendingRunSync(queue, { r ->
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
        val sync = PendingRunSync(queue, { r ->
            if (r.runId == "a") SubmitOutcome.Accepted(scoring) else SubmitOutcome.RetryLater
        }, { applied += it })

        sync.flush("uid-1")

        assertTrue(applied.isEmpty())
        assertEquals(listOf("b"), queue.runs.map { it.runId })
    }

    @Test
    fun `discarding an account drops only its runs`() = runTest {
        val queue = FakeQueue(listOf(run("mine-1"), run("theirs", uid = "uid-2"), run("mine-2")))
        val sync = PendingRunSync(queue, { SubmitOutcome.Accepted(scoring) }, {})

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
        val sync = PendingRunSync(queue, { r ->
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
        val sync = PendingRunSync(queue, { r ->
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
        val sync = PendingRunSync(queue, { r -> submitted += r.runId; SubmitOutcome.RetryLater }, {})

        sync.flush("uid-1")

        assertEquals(listOf("a"), submitted)
        assertEquals(listOf("a", "b"), queue.runs.map { it.runId })
    }

    @Test
    fun `another account's runs are never submitted and stay queued`() = runTest {
        val queue = FakeQueue(listOf(run("mine"), run("theirs", uid = "uid-2")))
        val submitted = mutableListOf<String>()
        val sync = PendingRunSync(queue, { r -> submitted += r.runId; SubmitOutcome.Accepted(scoring) }, {})

        sync.flush("uid-1")

        assertEquals(listOf("mine"), submitted)
        assertEquals(listOf("theirs"), queue.runs.map { it.runId })
    }

    @Test
    fun `an accepted run without scoring in the answer still leaves the queue`() = runTest {
        val queue = FakeQueue(listOf(run("a")))
        val applied = mutableListOf<ServerScoring>()
        val sync = PendingRunSync(queue, { SubmitOutcome.Accepted(null) }, { applied += it })

        sync.flush("uid-1")

        assertTrue(queue.runs.isEmpty())
        assertTrue(applied.isEmpty())
    }

    @Test
    fun `two overlapping flushes submit each run once`() = runTest {
        val queue = FakeQueue(listOf(run("a")))
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val sync = PendingRunSync(queue, {
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
}
