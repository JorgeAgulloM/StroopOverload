package com.softyorch.stroopoverload.core

object GameConfig {
    const val INITIAL_TIME_LIMIT_MS = 3000L
    const val TIME_LIMIT_DECAY_MS = 150L
    const val MINIMUM_TIME_LIMIT_MS = 800L
    const val LEVELS_PER_DIFFICULTY = 5
    // The spoken distractor colour (the auditory Stroop effect). Earlier it felt like noise.
    const val AUDIO_DISTRACTOR_MIN_LEVEL = 15
    const val POINTS_PER_CORRECT = 100
    const val LEADERBOARD_LIMIT = 50L

    const val LIVES_MODE_STARTING_LIVES = 3
    const val LIVES_MODE_FREEZE_MS = 1500L
    const val TIME_MODE_DURATION_MS = 60_000L
    const val TIME_MODE_FLASH_MS = 400L
    const val OVERTIME_START_MS = 30_000L
    const val OVERTIME_BONUS_START_MS = 1_000L
    const val OVERTIME_BONUS_STEP_MS = 100L
    const val OVERTIME_BONUS_MIN_MS = 300L
    const val OVERTIME_MISS_PENALTY_MS = 2_000L
}
