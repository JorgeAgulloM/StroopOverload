package com.softyorch.stroopoverload.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softyorch.stroopoverload.core.StroopColor

/**
 * One answer quadrant of the 2x2 board, shared by local play and every online
 * mode. Flashes white on a miss; dims and stops taking taps while [enabled] is
 * false (someone else holds the turn).
 */
@Composable
fun QuadrantBox(
    color: StroopColor,
    enabled: Boolean = true,
    isFlashing: Boolean = false,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val flashAlpha by animateFloatAsState(
        targetValue = if (isFlashing) 0.85f else 0f,
        animationSpec = tween(if (isFlashing) 120 else 400),
        label = "quadrantFlash",
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, color.composeColor.copy(alpha = if (enabled) 0.7f else 0.25f), RoundedCornerShape(8.dp))
            .background(color.composeColor.copy(alpha = if (enabled) 0.15f else 0.05f))
            .clickable(enabled = enabled, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(color.displayNameRes),
            color = color.composeColor.copy(alpha = if (enabled) 1f else 0.4f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (flashAlpha > 0f) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha)))
        }
    }
}
