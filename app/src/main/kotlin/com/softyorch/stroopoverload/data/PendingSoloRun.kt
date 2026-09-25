package com.softyorch.stroopoverload.data

/**
 * A finished solo run waiting to be reported to submitSoloRun. Runs are queued on the
 * device and retried until the server answers, so a dropped connection at the end of a
 * run no longer loses it. [runId] lets the server ignore a run it already applied.
 */
data class PendingSoloRun(
    val runId: String,
    val uid: String,
    val mode: String,
    val correctHits: Int,
    val totalRounds: Int,
    val survivalMs: Long,
    val finalScore: Int,
    val winStreak: Int,
    val achievementIds: List<String>,
    val createdAtEpochMs: Long,
) {
    /** One line of plain text; ids never contain the separators. */
    fun encode(): String = listOf(
        runId, uid, mode, correctHits, totalRounds, survivalMs, finalScore, winStreak, createdAtEpochMs,
        achievementIds.joinToString(LIST_SEPARATOR),
    ).joinToString(FIELD_SEPARATOR)

    companion object {
        private const val FIELD_SEPARATOR = "|"
        private const val LIST_SEPARATOR = ","
        private const val FIELD_COUNT = 10

        /** Null for a line that isn't a stored run, so one bad line can't block the queue. */
        fun decode(line: String): PendingSoloRun? {
            val f = line.split(FIELD_SEPARATOR)
            if (f.size != FIELD_COUNT || f[0].isBlank() || f[1].isBlank()) return null
            return PendingSoloRun(
                runId = f[0],
                uid = f[1],
                mode = f[2],
                correctHits = f[3].toIntOrNull() ?: return null,
                totalRounds = f[4].toIntOrNull() ?: return null,
                survivalMs = f[5].toLongOrNull() ?: return null,
                finalScore = f[6].toIntOrNull() ?: return null,
                winStreak = f[7].toIntOrNull() ?: return null,
                createdAtEpochMs = f[8].toLongOrNull() ?: return null,
                achievementIds = f[9].split(LIST_SEPARATOR).filter { it.isNotBlank() },
            )
        }
    }
}

/** This queue with [run] appended, dropping the oldest runs beyond [max]. */
internal fun List<PendingSoloRun>.appendCapped(run: PendingSoloRun, max: Int): List<PendingSoloRun> =
    (this + run).takeLast(max)
