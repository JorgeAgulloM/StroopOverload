package com.softyorch.stroopoverload.ui.screen

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.domain.Achievement
import com.softyorch.stroopoverload.domain.GameResult
import com.softyorch.stroopoverload.domain.XpBreakdown
import com.softyorch.stroopoverload.ui.theme.*

@Composable
fun GameOverScreen(
    result: GameResult,
    xpBreakdown: XpBreakdown? = null,
    newAchievements: List<Achievement> = emptyList(),
    onPlayAgain: () -> Unit,
    onMenu: () -> Unit,
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(if (result.won) R.string.game_over_victory_title else R.string.game_over_defeat_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (result.won) MaterialTheme.colorScheme.tertiary else TechAccent,
                    letterSpacing = 4.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(if (result.won) R.string.game_over_victory_subtitle else R.string.game_over_defeat_subtitle),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (result.won) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Black
                )
            }

            if (result.isNewHighScore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, NeonYellow, RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.game_over_new_high_score),
                            style = MaterialTheme.typography.labelLarge,
                            color = NeonYellow,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Main Telemetry Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.game_over_telemetry_header), style = MaterialTheme.typography.labelMedium, color = TechAccent)
                    StatRow(stringResource(R.string.game_over_final_score), result.finalScore.toString(), MaterialTheme.colorScheme.primary)
                    StatRow(stringResource(R.string.game_over_accuracy), "${result.accuracy}%", if (result.accuracy >= 80) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onBackground)
                    StatRow(stringResource(R.string.game_over_rounds_survived), result.totalRounds.toString(), MaterialTheme.colorScheme.onBackground)
                    StatRow(stringResource(R.string.game_over_time_elapsed), "${result.durationSeconds}s", MaterialTheme.colorScheme.onBackground)
                    if (result.isFlawless) {
                        StatRow(stringResource(R.string.game_over_perfect_bonus), stringResource(R.string.game_over_flawless_100), MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            // XP Breakdown Card
            if (xpBreakdown != null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CyberPurple, RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.game_over_xp_header), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                            Text(stringResource(R.string.game_over_xp_total, xpBreakdown.total), style = MaterialTheme.typography.titleMedium, color = NeonYellow, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                        XpRow(stringResource(R.string.game_over_xp_base, stringResource(xpBreakdown.baseLabelRes)), "+${xpBreakdown.base}")
                        if (xpBreakdown.perfectBonus > 0) XpRow(stringResource(R.string.game_over_xp_flawless_bonus), "+${xpBreakdown.perfectBonus}")
                        if (xpBreakdown.timeBonus > 0) XpRow(stringResource(R.string.game_over_xp_time_bonus), "+${xpBreakdown.timeBonus}")
                        if (xpBreakdown.streakBonus > 0) XpRow(stringResource(R.string.game_over_xp_streak_bonus), "+${xpBreakdown.streakBonus}")
                        if (xpBreakdown.dailyStreakBonus > 0) XpRow(stringResource(R.string.game_over_xp_daily_bonus), "+${xpBreakdown.dailyStreakBonus}")
                        if (xpBreakdown.multiplier > 1.0) XpRow(stringResource(R.string.game_over_xp_multiplier), stringResource(R.string.game_over_xp_multiplier_value, xpBreakdown.multiplier.toString()))
                    }
                }
            }

            // Newly Unlocked Trophies Banner
            if (newAchievements.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.game_over_new_trophies), style = MaterialTheme.typography.titleMedium, color = NeonYellow)
                }
                items(newAchievements) { ach ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(ach.rarity.composeColorArgb), RoundedCornerShape(6.dp))
                            .background(CyberDark)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(ach.iconEmoji, fontSize = 24.sp)
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(ach.titleRes), style = MaterialTheme.typography.labelLarge, color = Color(ach.rarity.composeColorArgb), fontWeight = FontWeight.Bold)
                                if (ach.xpReward > 0) {
                                    Text(stringResource(R.string.game_over_xp_reward, ach.xpReward), style = MaterialTheme.typography.labelMedium, color = NeonYellow, fontWeight = FontWeight.Black)
                                }
                            }
                            Text(stringResource(ach.descriptionRes), style = MaterialTheme.typography.bodySmall, color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Action Buttons
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onPlayAgain,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) {
                    Text(stringResource(R.string.game_over_play_again), color = Background, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                }
                Spacer(modifier = Modifier.height(12.dp))
                val shareText = stringResource(R.string.game_over_share_text, result.finalScore, xpBreakdown?.total ?: 0, result.accuracy)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.game_over_share_score))
                    }
                    OutlinedButton(
                        onClick = onMenu,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Muted),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.game_over_main_menu))
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Muted)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun XpRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = TechAccent,
            fontWeight = FontWeight.SemiBold
        )
    }
}
