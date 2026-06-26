package com.stroopoverload

import com.stroopoverload.core.StroopColor
import com.stroopoverload.game.IncongruenceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class IncongruenceEngineTest {

    private val engine = IncongruenceEngine(rng = Random(42))

    @Test
    fun `level 1 inkColor always differs from wordLabel`() {
        repeat(100) {
            val s = engine.generate(1)
            assertNotEquals("Stimulus must be incongruent at level 1", s.inkColor, s.wordLabel)
        }
    }

    @Test
    fun `level 1 has no audio or bg distractor`() {
        val s = engine.generate(1)
        assertNull(s.audioColor)
        assertNull(s.bgDistractor)
    }

    @Test
    fun `level 2 audioColor present and differs from inkColor`() {
        repeat(100) {
            val s = engine.generate(2)
            assertNotNull(s.audioColor)
            assertNotEquals(s.inkColor, s.audioColor)
        }
    }

    @Test
    fun `level 3 bgDistractor present and differs from inkColor`() {
        repeat(100) {
            val s = engine.generate(3)
            assertNotNull(s.bgDistractor)
            assertNotEquals(s.inkColor, s.bgDistractor)
        }
    }

    @Test
    fun `correctAnswer is always inkColor across all levels`() {
        for (level in 1..3) {
            repeat(50) {
                val s = engine.generate(level)
                assertEquals(s.inkColor, s.correctAnswer)
            }
        }
    }

    @Test
    fun `all four colors appear as inkColor across 200 stimuli`() {
        val seen = mutableSetOf<StroopColor>()
        repeat(200) { seen.add(engine.generate(1).inkColor) }
        assertTrue(seen.containsAll(StroopColor.entries))
    }
}
