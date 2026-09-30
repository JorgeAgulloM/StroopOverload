package com.softyorch.stroopoverload.core

import java.text.NumberFormat
import java.util.Locale

/**
 * Formats a score multiplier (2.0, 1.5, ...) for display.
 *
 * `Double.toString()` is locale-invariant and always emits a dot, so it printed
 * "1.5" inside otherwise-translated text for the four locales that write it "1,5".
 * Always one decimal, so 2x and 1.5x line up in the same column.
 */
fun formatMultiplier(value: Double, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }.format(value)
