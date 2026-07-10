package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom
import com.softyorch.stroopoverload.domain.multiplayer.RoomPlayer
import com.softyorch.stroopoverload.domain.multiplayer.RoomStatus
import com.softyorch.stroopoverload.ui.theme.Muted
import com.softyorch.stroopoverload.ui.theme.TechAccent
import kotlinx.coroutines.delay

private const val SOLO_LEVELS_PER_DIFFICULTY = 5

/** Mirrors soloSurvival.ts's soloLevelForRound/soloTimeLimitMs formula. */
private fun soloTimeLimitMs(round: Int): Long =
    timeLimitMsForRound(round / SOLO_LEVELS_PER_DIFFICULTY + 1)

/**
 * solo_survival has no shared turn to render -- every player answers against
 * their own stimulus on their own clock -- but the screen otherwise reuses
 * the exact same visual language as the local single-player GameScreen and
 * the other online modes' MultiplayerGameScreen (roster HUD, TimerBar,
 * bordered stimulus box, 2x2 quadrant grid), just always showing MY OWN
 * stimulus/score instead of a shared one.
 */
@Composable
fun SoloSurvivalGameScreen(
    room: MultiplayerRoom,
    myUid: String,
    onColorTapped: (StroopColor) -> Unit,
    onExit: () -> Unit,
) {
    val me = room.player(myUid)

    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(room.roomId) {
        while (true) {
            delay(100L)
            nowMs = System.currentTimeMillis()
        }
    }

    val sessionSecondsLeft = room.deadlineAtMs?.let { ((it - nowMs).coerceAtLeast(0L)) / 1000 } ?: 0L
    val timerProgress = remember(me?.soloDeadlineAtMs, me?.soloRound, nowMs) {
        val deadline = me?.soloDeadlineAtMs
        if (deadline == null) {
            0f
        } else {
            val totalMs = soloTimeLimitMs((me.soloRound).coerceAtLeast(0)).toFloat()
            ((deadline - nowMs).toFloat() / totalMs).coerceIn(0f, 1f)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Roster HUD, same bordered-card pattern as MultiplayerGameScreen --
            // this is the "added" room-players piece: everyone's live score and
            // alive status, "you" called out so a busted player can still track
            // how the room is doing without a separate leaderboard section.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                room.players.sortedByDescending { it.soloScore }.forEach { player ->
                    val isMe = player.uid == myUid
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = player.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isMe) FontWeight.Black else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (player.alive) "${player.soloScore}" else stringResource(R.string.mp_game_eliminated),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (player.alive) Muted else MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (room.status == RoomStatus.PLAYING) {
                Text(
                    text = stringResource(R.string.mp_solo_time_left, sessionSecondsLeft),
                    style = MaterialTheme.typography.labelSmall,
                    color = TechAccent,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (room.status == RoomStatus.PLAYING && me?.alive == true) {
                TimerBar(
                    progress = timerProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            when (room.status) {
                RoomStatus.PLAYING -> PlayingContent(me, onColorTapped)
                // Rendered as a full-screen overlay below instead, so it can float over
                // the roster/timer rather than being squeezed into the remaining weight.
                RoomStatus.FINISHED -> Unit
                // Unreachable here: MultiplayerScreen routes WAITING/STARTING to their own
                // screens before this composable is ever shown. Kept only so the `when` stays
                // exhaustive against RoomStatus.
                RoomStatus.WAITING, RoomStatus.STARTING -> Text(stringResource(R.string.mp_game_waiting))
            }
        }

        if (room.status == RoomStatus.FINISHED) {
            MatchFinishedOverlay(room = room, myUid = myUid, onExit = onExit)
        }
    }
}

@Composable
private fun ColumnScope.PlayingContent(me: RoomPlayer?, onColorTapped: (StroopColor) -> Unit) {
    val stimulus = me?.soloStimulus

    if (me == null || !me.alive || stimulus == null) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (me != null && !me.alive) {
                    Text(
                        text = stringResource(R.string.mp_solo_busted_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.mp_solo_busted_subtitle, me.soloScore),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.mp_game_preparing_round),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        return
    }

    // Central word terminal, matching the local GameScreen's bordered stimulus box.
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.game_stimulus_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                letterSpacing = 2.sp,
                fontSize = 11.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(stimulus.wordLabel.displayNameRes),
                color = stimulus.inkColor.composeColor,
                fontSize = 46.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // 2x2 quadrant grid, same layout/component as local mode and the other
    // online modes' MultiplayerGameScreen (shared QuadrantBox).
    val options = stimulus.options
    Column(modifier = Modifier.weight(1.2f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            options.getOrNull(0)?.let { QuadrantBox(it, true, false, Modifier.weight(1f).fillMaxHeight()) { onColorTapped(it) } }
            options.getOrNull(1)?.let { QuadrantBox(it, true, false, Modifier.weight(1f).fillMaxHeight()) { onColorTapped(it) } }
        }
        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            options.getOrNull(2)?.let { QuadrantBox(it, true, false, Modifier.weight(1f).fillMaxHeight()) { onColorTapped(it) } }
            options.getOrNull(3)?.let { QuadrantBox(it, true, false, Modifier.weight(1f).fillMaxHeight()) { onColorTapped(it) } }
        }
    }
}
