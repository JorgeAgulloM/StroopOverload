package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom
import com.softyorch.stroopoverload.domain.multiplayer.RoomPlayer
import com.softyorch.stroopoverload.domain.multiplayer.RoomStatus
import kotlinx.coroutines.delay

/**
 * solo_survival has no shared turn to render: every player answers against
 * their own stimulus on their own clock, so unlike MultiplayerGameScreen this
 * always shows MY stimulus/score (never someone else's), plus a leaderboard
 * so busted players can still see how the rest of the room is doing.
 */
@Composable
fun SoloSurvivalGameScreen(
    room: MultiplayerRoom,
    myUid: String,
    onColorTapped: (StroopColor) -> Unit,
) {
    val me = room.player(myUid)

    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(room.roomId) {
        while (true) {
            delay(200L)
            nowMs = System.currentTimeMillis()
        }
    }
    val remainingSeconds = room.deadlineAtMs?.let { ((it - nowMs).coerceAtLeast(0L)) / 1000 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        if (room.status == RoomStatus.PLAYING && remainingSeconds != null) {
            Text(
                text = stringResource(R.string.mp_solo_time_left, remainingSeconds),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        Spacer(Modifier.height(16.dp))

        when (room.status) {
            RoomStatus.PLAYING -> PlayingContent(me, onColorTapped)
            RoomStatus.FINISHED -> FinishedBanner(room, myUid)
            // Unreachable here: MultiplayerScreen routes WAITING/STARTING to their own
            // screens before this composable is ever shown. Kept only so the `when` stays
            // exhaustive against RoomStatus.
            RoomStatus.WAITING, RoomStatus.STARTING -> Text(stringResource(R.string.mp_game_waiting))
        }

        Spacer(Modifier.height(24.dp))
        Leaderboard(room.players, myUid)
    }
}

@Composable
private fun ColumnScope.PlayingContent(me: RoomPlayer?, onColorTapped: (StroopColor) -> Unit) {
    val stimulus = me?.soloStimulus
    when {
        me == null -> Text(
            text = stringResource(R.string.mp_game_preparing_round),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        !me.alive -> {
            Text(
                text = stringResource(R.string.mp_solo_busted_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.mp_solo_busted_subtitle, me.soloScore),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        stimulus != null -> {
            Text(
                text = "${stringResource(R.string.mp_solo_score_label)}: ${me.soloScore}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(stimulus.wordLabel.displayNameRes),
                style = MaterialTheme.typography.displayMedium,
                color = stimulus.inkColor.composeColor,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(32.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                stimulus.options.forEach { option ->
                    Button(
                        onClick = { onColorTapped(option) },
                        colors = ButtonDefaults.buttonColors(containerColor = option.composeColor),
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) {
                        Text(
                            text = stringResource(option.displayNameRes),
                            color = Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        else -> Text(
            text = stringResource(R.string.mp_game_preparing_round),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun ColumnScope.FinishedBanner(room: MultiplayerRoom, myUid: String) {
    val winner = room.players.firstOrNull { it.uid == room.winnerUid }
    val text = if (room.winnerUid == myUid) {
        stringResource(R.string.mp_solo_you_won)
    } else {
        stringResource(
            R.string.mp_solo_won_by,
            winner?.displayName ?: stringResource(R.string.mp_game_unknown_player),
            winner?.soloScore ?: 0,
        )
    }
    Text(text = text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
}

@Composable
private fun ColumnScope.Leaderboard(players: List<RoomPlayer>, myUid: String) {
    Text(
        text = stringResource(R.string.mp_solo_leaderboard_title),
        style = MaterialTheme.typography.labelLarge,
    )
    Spacer(Modifier.height(8.dp))
    val ranked = players.sortedByDescending { it.soloScore }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ranked, key = { it.uid }) { player -> LeaderboardRow(player, isMe = player.uid == myUid) }
    }
}

@Composable
private fun LeaderboardRow(player: RoomPlayer, isMe: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = player.displayName + if (isMe) " (${stringResource(R.string.mp_solo_you_tag)})" else "",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!player.alive) {
            Text(
                text = stringResource(R.string.mp_game_eliminated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Text(
            text = "${player.soloScore}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
