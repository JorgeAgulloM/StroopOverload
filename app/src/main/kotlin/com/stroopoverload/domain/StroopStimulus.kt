package com.stroopoverload.domain

import com.stroopoverload.core.StroopColor

data class StroopStimulus(
    val wordLabel: StroopColor,
    val inkColor: StroopColor,
    val audioColor: StroopColor? = null,
    val bgDistractor: StroopColor? = null,
) {
    val correctAnswer: StroopColor get() = inkColor
    val isIncongruent: Boolean get() = wordLabel != inkColor
}
