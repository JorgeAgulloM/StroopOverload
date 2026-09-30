package com.softyorch.stroopoverload.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

private val MinLabelSize = 9.sp

/**
 * A one-line label that shrinks until it fits instead of wrapping or cutting. For side-by-side
 * buttons whose translations ("COMPARTIR PUNTUACIÓN", "PARTAGER LE SCORE") are longer than half
 * the screen, where wrapping split words ("COMPARTI / R") and ellipsis hid them.
 * Uses the surrounding text style and content colour, so it drops into a Button like Text.
 */
@Composable
fun FitLabel(text: String, modifier: Modifier = Modifier) {
    val style = LocalTextStyle.current
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(color = LocalContentColor.current, textAlign = TextAlign.Center),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = MinLabelSize, maxFontSize = style.fontSize),
    )
}
