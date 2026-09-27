package com.softyorch.stroopoverload.ui.screen.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.domain.Achievement
import com.softyorch.stroopoverload.domain.AchievementProgress
import com.softyorch.stroopoverload.ui.theme.CyberDark
import com.softyorch.stroopoverload.ui.theme.Muted
import java.text.DateFormat
import java.util.Date

private const val LOCKED_ALPHA = 0.55f

private val GREYSCALE = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * Draws its content in faded greys: a locked trophy. A graphics layer with a saturation
 * filter, rather than RenderEffect, so it works below API 31 and on emoji too. The layer is
 * the card's own render node, so scrolling the trophy list only moves it instead of
 * re-rendering an offscreen layer per card per frame.
 */
internal fun Modifier.lockedLook(isLocked: Boolean): Modifier =
    if (!isLocked) this else drawWithCache {
        val layer = obtainGraphicsLayer().apply {
            colorFilter = GREYSCALE
            alpha = LOCKED_ALPHA
        }
        onDrawWithContent {
            layer.record { this@onDrawWithContent.drawContent() }
            drawLayer(layer)
        }
    }

/** A trophy's details: how to earn it, whether it is unlocked and when, or how far along it is. */
@Composable
fun AchievementDetailDialog(
    achievement: Achievement,
    progress: AchievementProgress,
    onDismiss: () -> Unit,
) {
    val unlocked = achievement.isUnlocked
    val secret = !unlocked && achievement.hidden
    val accent = if (unlocked) Color(achievement.rarity.composeColorArgb) else Muted

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, accent, RoundedCornerShape(16.dp))
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = achievement.iconEmoji,
                fontSize = 48.sp,
                modifier = Modifier.lockedLook(!unlocked),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(if (unlocked) achievement.rarity.labelRes else R.string.common_locked),
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(if (secret) R.string.profile_achievement_hidden_title else achievement.titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = if (unlocked) MaterialTheme.colorScheme.onBackground else Muted,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(if (secret) R.string.achievement_detail_hidden_hint else achievement.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            AchievementStatus(achievement, progress, secret, accent)
            if (!secret && achievement.xpReward > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.achievement_detail_reward, achievement.xpReward),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(
                    text = stringResource(R.string.common_ok),
                    color = MaterialTheme.colorScheme.background,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun AchievementStatus(
    achievement: Achievement,
    progress: AchievementProgress,
    secret: Boolean,
    accent: Color,
) {
    if (achievement.isUnlocked) {
        val locale = LocalConfiguration.current.locales[0]
        val date = remember(achievement.unlockedAt, locale) {
            DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(achievement.unlockedAt))
        }
        Text(
            text = stringResource(R.string.achievement_detail_unlocked_on, date),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
        )
        return
    }
    // A secret trophy keeps its target secret too.
    if (secret || progress !is AchievementProgress.Count) return
    LinearProgressIndicator(
        progress = { progress.fraction },
        modifier = Modifier.fillMaxWidth().height(6.dp),
        color = MaterialTheme.colorScheme.secondary,
        trackColor = CyberDark,
        strokeCap = StrokeCap.Round,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.achievement_detail_progress, progress.current, progress.target),
        style = MaterialTheme.typography.labelMedium,
        color = Muted,
    )
}
