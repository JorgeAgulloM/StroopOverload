package com.softyorch.stroopoverload.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingSoloRunTest {

    private val run = PendingSoloRun(
        runId = "3f2b9c1e-0000-4000-8000-000000000001",
        uid = "uid-12AB",
        mode = "TIME",
        correctHits = 52,
        totalRounds = 52,
        survivalMs = 56_000L,
        finalScore = 9950,
        winStreak = 52,
        achievementIds = listOf("first_blood", "flawless_tier2"),
        createdAtEpochMs = 1_790_000_000_000L,
    )

    @Test
    fun `a run survives being stored and read back`() {
        assertEquals(run, PendingSoloRun.decode(run.encode()))
    }

    @Test
    fun `a run with no achievements survives being stored and read back`() {
        val bare = run.copy(achievementIds = emptyList())

        assertEquals(bare, PendingSoloRun.decode(bare.encode()))
    }

    @Test
    fun `a malformed stored line is skipped instead of crashing`() {
        assertNull(PendingSoloRun.decode(""))
        assertNull(PendingSoloRun.decode("only|three|fields"))
        assertNull(PendingSoloRun.decode(run.encode().replace("|52|", "|not-a-number|")))
    }

    @Test
    fun `the queue keeps the newest runs when it is full`() {
        val queue = (1..3).map { run.copy(runId = "run-$it-aaaa") }

        val appended = queue.appendCapped(run.copy(runId = "run-4-aaaa"), max = 3)

        assertEquals(listOf("run-2-aaaa", "run-3-aaaa", "run-4-aaaa"), appended.map { it.runId })
    }
}
