package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom
import com.softyorch.stroopoverload.domain.multiplayer.RoomStatus

@Composable
fun MultiplayerGameScreen(
    room: MultiplayerRoom,
    myUid: String,
    onColorTapped: (StroopColor) -> Unit,
) {
    val myTurn = room.isMyTurn(myUid)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            room.players.forEach { player ->
                val isTurn = player.uid == room.currentTurnUid
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isTurn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = player.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (player.alive) stringResource(R.string.mp_game_alive) else stringResource(R.string.mp_game_eliminated),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        when (room.status) {
            RoomStatus.PLAYING -> {
                val stimulus = room.stimulus
                if (stimulus != null) {
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
                                enabled = myTurn,
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
                    if (!myTurn) {
                        Spacer(Modifier.height(16.dp))
                        val turnName = room.players.firstOrNull { it.uid == room.currentTurnUid }?.displayName
                            ?: stringResource(R.string.mp_game_unknown_player)
                        Text(stringResource(R.string.mp_game_turn_of, turnName))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.mp_game_preparing_round),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
            RoomStatus.FINISHED -> {
                val winner = room.players.firstOrNull { it.uid == room.winnerUid }
                Text(
                    text = if (room.winnerUid == myUid) {
                        stringResource(R.string.mp_game_you_won)
                    } else {
                        stringResource(R.string.mp_game_won_by, winner?.displayName ?: stringResource(R.string.mp_game_unknown_player))
                    },
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            // Neither reachable here: MultiplayerScreen routes WAITING/STARTING to their
            // own screens before this composable is ever shown. Kept only so the `when`
            // stays exhaustive against RoomStatus.
            RoomStatus.WAITING, RoomStatus.STARTING -> Text(stringResource(R.string.mp_game_waiting))
        }

        Spacer(Modifier.weight(1f))
    }
}
