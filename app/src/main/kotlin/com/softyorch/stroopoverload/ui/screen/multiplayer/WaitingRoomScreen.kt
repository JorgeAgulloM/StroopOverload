package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom
import com.softyorch.stroopoverload.domain.multiplayer.RoomPlayer
import com.softyorch.stroopoverload.ui.theme.Muted
import com.softyorch.stroopoverload.ui.theme.TechAccent
import com.softyorch.stroopoverload.ui.theme.TechBorder

private const val MAX_PLAYERS = 4

@Composable
fun WaitingRoomScreen(
    room: MultiplayerRoom,
    myUid: String,
    isStartingGame: Boolean,
    startGameError: MultiplayerErrorReason.StartGameFailed?,
    onStartGame: () -> Unit,
) {
    val isHost = room.hostUid == myUid
    val clipboard = LocalClipboardManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.mp_waiting_title),
                style = MaterialTheme.typography.labelLarge,
                color = TechAccent,
                letterSpacing = 4.sp,
            )

            Spacer(Modifier.height(20.dp))

            // Room code — tap to copy
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TechAccent, RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { clipboard.setText(AnnotatedString(room.code)) }
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.mp_waiting_room_code_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = room.code,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.mp_waiting_tap_to_copy),
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(room.mode.titleRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.mp_waiting_connected_pilots, room.players.size, MAX_PLAYERS),
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
                letterSpacing = 1.sp,
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(room.players) { player -> PlayerSlot(player, room.hostUid) }
                items(MAX_PLAYERS - room.players.size) { EmptyPlayerSlot() }
            }

            Spacer(Modifier.height(16.dp))

            if (isHost) {
                Button(
                    onClick = onStartGame,
                    enabled = !isStartingGame && room.players.size in 2..MAX_PLAYERS,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.outline,
                    ),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    if (isStartingGame) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.background,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        text = stringResource(if (isStartingGame) R.string.mp_waiting_starting_game else R.string.mp_waiting_start_game),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.background,
                        fontWeight = FontWeight.Black,
                    )
                }
                if (startGameError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = startGameError.detail ?: stringResource(R.string.mp_error_start_game),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            SignalScanner(
                label = stringResource(if (isHost) R.string.mp_waiting_scanning_host else R.string.mp_waiting_scanning_guest),
            )
        }
    }
}

@Composable
private fun PlayerSlot(player: RoomPlayer, hostUid: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, TechBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
        Text(
            text = player.displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (player.uid == hostUid) {
            Text(
                text = stringResource(R.string.mp_waiting_host_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.background,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(TechAccent, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun EmptyPlayerSlot() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, TechBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.mp_waiting_empty_slot),
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
        )
    }
}

/** Cyberpunk "scanning for signal" equalizer-style loading indicator. */
@Composable
internal fun SignalScanner(label: String) {
    val barCount = 7
    val transition = rememberInfiniteTransition(label = "scanner")

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            repeat(barCount) { index ->
                val heightFraction by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset((index * 90)),
                    ),
                    label = "bar$index",
                )
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height((28 * heightFraction).dp)
                        .background(TechAccent.copy(alpha = 0.4f + 0.6f * heightFraction), RoundedCornerShape(3.dp))
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        val sweepAlpha by transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "sweepAlpha",
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TechAccent.copy(alpha = sweepAlpha),
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
        )
    }
}
