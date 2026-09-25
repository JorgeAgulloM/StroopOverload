package com.softyorch.stroopoverload.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softyorch.stroopoverload.ui.theme.StroopTheme

private val MinWordSize = 20.sp
// Keeps the word (and its glow) off the card's border when it is shrunk to fit.
private val WordSidePadding = 16.dp

/**
 * The colour word of a Stroop stimulus, always on one line. It shrinks from [maxFontSize] until
 * it fits: long translations ("VERMELHO", "AMARILLO") used to wrap mid-word ("VERMEL / HO") on
 * 360 dp phones, which garbles the very word the player has to read.
 *
 * @param glow neon shadow in the ink colour, used by local play.
 */
@Composable
fun StimulusWord(
    text: String,
    color: Color,
    maxFontSize: TextUnit,
    letterSpacing: TextUnit,
    modifier: Modifier = Modifier,
    glow: Boolean = false,
) {
    BasicText(
        text = text,
        modifier = modifier.fillMaxWidth().padding(horizontal = WordSidePadding),
        style = LocalTextStyle.current.merge(
            color = color,
            fontWeight = FontWeight.Black,
            letterSpacing = letterSpacing,
            textAlign = TextAlign.Center,
            shadow = if (glow) Shadow(color = color, blurRadius = 24f) else null,
        ),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = MinWordSize, maxFontSize = maxFontSize),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
private fun StimulusWordPreview() {
    StroopTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            StimulusWord("VERMELHO", Color(0xFF1F8FFF), 64.sp, 8.sp, glow = true)
            StimulusWord("AZUL", Color(0xFFFF2D2D), 64.sp, 8.sp, glow = true)
        }
    }
}
