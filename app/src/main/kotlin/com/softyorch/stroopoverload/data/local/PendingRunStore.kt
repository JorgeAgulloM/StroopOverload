package com.softyorch.stroopoverload.data.local

import android.content.Context
import android.content.SharedPreferences
import com.softyorch.stroopoverload.data.PendingRunQueue
import com.softyorch.stroopoverload.data.PendingSoloRun
import com.softyorch.stroopoverload.data.appendCapped

/**
 * Finished solo runs not yet answered by submitSoloRun, one encoded run per line.
 * Uses commit(), not apply(): a run is only safe once it is on disk, and the process may
 * die right after a game ends.
 */
class PendingRunStore(context: Context) : PendingRunQueue {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("stroop_pending_runs", Context.MODE_PRIVATE)

    @Synchronized
    override fun all(): List<PendingSoloRun> =
        (prefs.getString(KEY_RUNS, "") ?: "").lines().mapNotNull(PendingSoloRun::decode)

    @Synchronized
    override fun add(run: PendingSoloRun) = save(all().appendCapped(run, MAX_PENDING_RUNS))

    @Synchronized
    override fun remove(runId: String) = save(all().filter { it.runId != runId })

    private fun save(runs: List<PendingSoloRun>) {
        prefs.edit().putString(KEY_RUNS, runs.joinToString("\n") { it.encode() }).commit()
    }

    companion object {
        private const val KEY_RUNS = "runs"

        // A bound, not a target: a player offline for days keeps their latest runs and the
        // queue can't grow without limit. Beyond it the oldest runs are dropped unreported.
        private const val MAX_PENDING_RUNS = 20
    }
}
