package com.softyorch.stroopoverload.core

object GameConfig {
    const val INITIAL_TIME_LIMIT_MS = 3000L
    const val TIME_LIMIT_DECAY_MS = 150L
    const val MINIMUM_TIME_LIMIT_MS = 800L
    const val LEVELS_PER_DIFFICULTY = 5
    const val POINTS_PER_CORRECT = 100
    const val LEADERBOARD_LIMIT = 50L

    const val LIVES_MODE_STARTING_LIVES = 3
    const val LIVES_MODE_FREEZE_MS = 1500L
    const val TIME_MODE_DURATION_MS = 60_000L
    const val TIME_MODE_FLASH_MS = 400L
}
