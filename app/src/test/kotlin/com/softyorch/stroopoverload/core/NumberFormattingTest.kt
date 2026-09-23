package com.softyorch.stroopoverload.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class NumberFormattingTest {

    @Test
    fun `uses the locale's decimal separator`() {
        assertEquals("1.5", formatMultiplier(1.5, Locale.US))
        assertEquals("1,5", formatMultiplier(1.5, Locale.forLanguageTag("es-ES")))
        assertEquals("1,5", formatMultiplier(1.5, Locale.GERMANY))
        assertEquals("1,5", formatMultiplier(1.5, Locale.FRANCE))
        assertEquals("1,5", formatMultiplier(1.5, Locale.forLanguageTag("pt-BR")))
        assertEquals("1.5", formatMultiplier(1.5, Locale.JAPAN))
    }

    @Test
    fun `always shows one decimal so multipliers line up`() {
        assertEquals("2.0", formatMultiplier(2.0, Locale.US))
        assertEquals("0.5", formatMultiplier(0.5, Locale.US))
    }
}
