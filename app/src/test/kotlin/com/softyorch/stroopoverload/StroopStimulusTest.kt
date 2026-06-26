package com.softyorch.stroopoverload

import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.domain.StroopStimulus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StroopStimulusTest {

    @Test
    fun `correctAnswer is always inkColor`() {
        val s = StroopStimulus(wordLabel = StroopColor.RED, inkColor = StroopColor.BLUE)
        assertEquals(StroopColor.BLUE, s.correctAnswer)
    }

    @Test
    fun `isIncongruent true when wordLabel differs from inkColor`() {
        val s = StroopStimulus(wordLabel = StroopColor.RED, inkColor = StroopColor.GREEN)
        assertTrue(s.isIncongruent)
    }

    @Test
    fun `isIncongruent false when wordLabel equals inkColor`() {
        val s = StroopStimulus(wordLabel = StroopColor.RED, inkColor = StroopColor.RED)
        assertFalse(s.isIncongruent)
    }
}
