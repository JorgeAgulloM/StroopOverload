package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom
import com.softyorch.stroopoverload.domain.multiplayer.RoomMode
import com.softyorch.stroopoverload.domain.multiplayer.RoomPlayer
import com.softyorch.stroopoverload.ui.theme.Muted
import com.softyorch.stroopoverload.ui.theme.NeonYellow
import com.softyorch.stroopoverload.ui.theme.TechAccent

/**
 * Shared end-of-match dialog for every online mode (mistake, hot_potato,
 * solo_survival): winner banner, match duration, and a scrollable per-player
 * breakdown card showing exactly how their final score was derived (raw
 * match score -> halved -> placement multiplier). Every player's
 * `placement`/`finalScore` is already computed server-side (see
 * functions/src/scoring.ts) regardless of mode, so this needs no
 * mode-specific ranking logic.
 */
@Composable
fun MatchFinishedOverlay(room: MultiplayerRoom, myUid: String, onExit: () -> Unit) {
    val winner = room.players.firstOrNull { it.uid == room.winnerUid }
    val iWon = room.winnerUid == myUid
    val matchStartMs = room.startsAtMs ?: room.createdAtMs
    // Captured once, the instant this dialog first composes -- a fine enough
    // approximation of "when the match ended" since FINISHED just arrived.
    val finishedAtMs = remember(room.roomId) { System.currentTimeMillis() }
    val durationSeconds = if (matchStartMs > 0) ((finishedAtMs - matchStartMs).coerceAtLeast(0L) / 1000).toInt() else null

    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, TechAccent, RoundedCornerShape(16.dp)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (iWon) stringResource(R.string.mp_game_you_won) else stringResource(
                        R.string.mp_game_won_by,
                        winner?.displayName ?: stringResource(R.string.mp_game_unknown_player),
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (iWon) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                room.player(myUid)?.finalScore?.let { earned ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.mp_game_points_earned, earned),
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonYellow,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (durationSeconds != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.mp_finish_duration, formatDuration(durationSeconds)),
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.mp_game_match_summary),
                    style = MaterialTheme.typography.labelMedium,
                    color = TechAccent,
                )
                room.players.sortedBy { it.placement ?: Int.MAX_VALUE }.forEach { player ->
                    PlayerBreakdownCard(
                        player = player,
                        isMe = player.uid == myUid,
                        isWinner = player.uid == room.winnerUid,
                        mode = room.mode,
                        matchStartMs = matchStartMs,
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Button(
                    onClick = onExit,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Text(
                        text = stringResource(R.string.mp_game_exit_room),
                        color = MaterialTheme.colorScheme.background,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerBreakdownCard(
    player: RoomPlayer,
    isMe: Boolean,
    isWinner: Boolean,
    mode: RoomMode,
    matchStartMs: Long,
) {
    val rawScore = if (mode == RoomMode.SOLO_SURVIVAL) player.soloScore else player.matchScore
    val halved = rawScore / 2
    val multiplierLabel = placementMultiplierLabel(player.placement)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isWinner) 2.dp else 1.dp,
                color = if (isWinner) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(10.dp),
            )
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "#${player.placement ?: '?'}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isWinner) MaterialTheme.colorScheme.tertiary else TechAccent,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = player.displayName + if (isMe) " (${stringResource(R.string.mp_solo_you_tag)})" else "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isWinner) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "+${player.finalScore ?: 0}",
                style = MaterialTheme.typography.titleMedium,
                color = NeonYellow,
                fontWeight = FontWeight.Black,
            )
        }

        // "Moves" detail: what this player actually did during the match.
        Text(
            text = movesDetailText(player, mode, matchStartMs),
            style = MaterialTheme.typography.labelSmall,
            color = if (player.alive) Muted else MaterialTheme.colorScheme.error,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

        BreakdownRow(stringResource(R.string.mp_finish_raw_score), "$rawScore")
        BreakdownRow(stringResource(R.string.mp_finish_halved), "$halved")
        BreakdownRow(stringResource(R.string.mp_finish_placement_bonus, multiplierLabel), "${player.finalScore ?: 0}", emphasize = true)
    }
}

@Composable
private fun BreakdownRow(label: String, value: String, emphasize: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (emphasize) MaterialTheme.colorScheme.onSurface else Muted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = if (emphasize) NeonYellow else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun movesDetailText(player: RoomPlayer, mode: RoomMode, matchStartMs: Long): String = when {
    mode == RoomMode.SOLO_SURVIVAL -> stringResource(R.string.mp_finish_rounds_reached, player.soloRound)
    !player.alive && player.eliminatedAtMs != null && matchStartMs > 0 ->
        stringResource(R.string.mp_finish_eliminated_at, ((player.eliminatedAtMs - matchStartMs).coerceAtLeast(0L) / 1000).toInt())
    !player.alive -> stringResource(R.string.mp_game_eliminated)
    else -> stringResource(R.string.mp_finish_survived_full)
}

private fun placementMultiplierLabel(placement: Int?): String = when (placement) {
    1 -> "2.0"
    2 -> "1.5"
    3 -> "1.0"
    else -> "0.5"
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
