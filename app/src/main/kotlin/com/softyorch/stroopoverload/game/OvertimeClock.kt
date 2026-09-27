package com.softyorch.stroopoverload.game

import com.softyorch.stroopoverload.core.GameConfig

/**
 * Time a right answer adds to the OVERTIME clock at [level]: a step less per level, down to a
 * floor. A flat bonus would let anyone faster than it play forever; at the floor, keeping up
 * means answering faster than any person can for long.
 */
fun overtimeBonusMs(level: Int): Long =
    (GameConfig.OVERTIME_BONUS_START_MS - (level - 1) * GameConfig.OVERTIME_BONUS_STEP_MS)
        .coerceAtLeast(GameConfig.OVERTIME_BONUS_MIN_MS)
