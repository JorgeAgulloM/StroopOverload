package com.softyorch.stroopoverload.game

import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.domain.StroopStimulus
import kotlin.random.Random

class IncongruenceEngine(private val rng: Random = Random.Default) {

    fun generate(difficultyLevel: Int): StroopStimulus {
        val inkColor = pick(exclude = emptyList())
        val wordLabel = pick(exclude = listOf(inkColor))
        val audioColor = if (difficultyLevel >= 2) pick(exclude = listOf(inkColor, wordLabel)) else null
        val bgDistractor = if (difficultyLevel >= 3) pick(exclude = listOf(inkColor, wordLabel)) else null

        return StroopStimulus(
            wordLabel = wordLabel,
            inkColor = inkColor,
            audioColor = audioColor,
            bgDistractor = bgDistractor,
        )
    }

    private fun pick(exclude: List<StroopColor>): StroopColor {
        val pool = StroopColor.entries.filter { it !in exclude }
        return if (pool.isEmpty()) StroopColor.entries[rng.nextInt(StroopColor.entries.size)]
        else pool[rng.nextInt(pool.size)]
    }
}
