package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.audio.AudioPlayer
import com.softyorch.stroopoverload.audio.GameSfx
import com.softyorch.stroopoverload.core.GameConfig
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom
import com.softyorch.stroopoverload.domain.multiplayer.RoomMode
import com.softyorch.stroopoverload.domain.multiplayer.RoomStatus
import com.softyorch.stroopoverload.ui.components.StimulusWord
import com.softyorch.stroopoverload.ui.components.QuadrantBox
import com.softyorch.stroopoverload.ui.theme.Muted
import com.softyorch.stroopoverload.ui.theme.NeonRed
import com.softyorch.stroopoverload.ui.theme.NeonYellow
import com.softyorch.stroopoverload.ui.theme.TechAccent
import kotlinx.coroutines.delay
import com.softyorch.stroopoverload.ui.components.DeadlineTimerBar

@Composable
fun MultiplayerGameScreen(
    room: MultiplayerRoom,
    myUid: String,
    onColorTapped: (StroopColor) -> Unit,
    onExit: () -> Unit,
    audioPlayer: AudioPlayer,
) {
    val myTurn = room.canAnswer(myUid)

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Top roster HUD, mirrors the local GameScreen's telemetry card style.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                room.players.forEach { player ->
                    val isTurn = player.uid == room.currentTurnUid
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = player.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isTurn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isTurn) FontWeight.Black else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (player.alive) stringResource(R.string.mp_game_alive) else stringResource(R.string.mp_game_eliminated),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (player.alive) Muted else MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Only mistake mode ties this bar to a real stake (miss it and you're
            // eliminated) -- hot_potato's timeout just re-prompts the same holder
            // with a fresh stimulus, so a decaying red/green bar here would falsely
            // suggest the same do-or-die urgency. No bar at all for hot_potato.
            if (room.status == RoomStatus.PLAYING && room.mode == RoomMode.MISTAKE) {
                DeadlineTimerBar(
                    deadlineAtMs = room.deadlineAtMs,
                    totalMs = timeLimitMsForRound(room.round.coerceAtLeast(1)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            when (room.status) {
                RoomStatus.PLAYING -> PlayingContent(room, myTurn, onColorTapped, audioPlayer)
                // Rendered as a full-screen overlay below instead, so it can float over
                // the roster/timer rather than being squeezed into the remaining weight.
                RoomStatus.FINISHED -> Unit
                // Neither reachable here: MultiplayerScreen routes WAITING/STARTING to their
                // own screens before this composable is ever shown. Kept only so the `when`
                // stays exhaustive against RoomStatus.
                RoomStatus.WAITING, RoomStatus.STARTING -> Text(stringResource(R.string.mp_game_waiting))
            }
        }

        if (room.status == RoomStatus.FINISHED) {
            MatchFinishedOverlay(room = room, myUid = myUid, onExit = onExit, audioPlayer = audioPlayer)
        }
    }
}

@Composable
private fun ColumnScope.PlayingContent(room: MultiplayerRoom, myTurn: Boolean, onColorTapped: (StroopColor) -> Unit, audioPlayer: AudioPlayer) {
    val stimulus = room.stimulus
    if (stimulus == null) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.mp_game_preparing_round),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    // Instant local feedback on a wrong tap, same idea as local GameScreen's
    // missFlashColor -- evaluated client-side against the stimulus we already
    // have (server remains the real authority; this is cosmetic only) so the
    // flash doesn't have to wait on a round trip. Reset by keying on
    // room.round: every resolution (correct OR wrong) advances the round with
    // a fresh stimulus, so the flash naturally clears once that arrives.
    var missFlashColor by remember(room.round) { mutableStateOf<StroopColor?>(null) }
    val handleTap: (StroopColor) -> Unit = { tapped ->
        if (tapped == stimulus.correctAnswer) {
            audioPlayer.play(GameSfx.TAP_CORRECT)
        } else {
            missFlashColor = stimulus.correctAnswer
            audioPlayer.play(GameSfx.TAP_WRONG)
        }
        onColorTapped(tapped)
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
        // hot_potato's own answer window carries almost no penalty (a timeout just
        // re-prompts the same holder), so it doesn't get MISTAKE's elimination-implying
        // TimerBar -- this growing/shaking/popping balloon represents the hidden
        // bomb's risk instead (see HotPotatoBalloon for how, given the real bombAtMs
        // is intentionally secret server-side).
        if (room.mode == RoomMode.HOT_POTATO) {
            HotPotatoBalloon(room)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.game_stimulus_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                letterSpacing = 2.sp,
                fontSize = 11.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            StimulusWord(
                text = stringResource(stimulus.wordLabel.displayNameRes),
                color = stimulus.inkColor.composeColor,
                maxFontSize = 46.sp,
                letterSpacing = 4.sp,
            )
            if (!myTurn) {
                Spacer(modifier = Modifier.height(16.dp))
                val turnName = room.players.firstOrNull { it.uid == room.currentTurnUid }?.displayName
                    ?: stringResource(R.string.mp_game_unknown_player)
                Text(
                    text = stringResource(R.string.mp_game_turn_of, turnName),
                    style = MaterialTheme.typography.labelMedium,
                    color = TechAccent,
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // 2x2 quadrant grid, same layout language as local mode (options arrive
    // pre-shuffled from the backend, so the grid position is randomized too).
    val options = stimulus.options
    Column(modifier = Modifier.weight(1.2f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            options.getOrNull(0)?.let { QuadrantBox(it, myTurn, it == missFlashColor, Modifier.weight(1f).fillMaxHeight()) { handleTap(it) } }
            options.getOrNull(1)?.let { QuadrantBox(it, myTurn, it == missFlashColor, Modifier.weight(1f).fillMaxHeight()) { handleTap(it) } }
        }
        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            options.getOrNull(2)?.let { QuadrantBox(it, myTurn, it == missFlashColor, Modifier.weight(1f).fillMaxHeight()) { handleTap(it) } }
            options.getOrNull(3)?.let { QuadrantBox(it, myTurn, it == missFlashColor, Modifier.weight(1f).fillMaxHeight()) { handleTap(it) } }
        }
    }
}

private const val BALLOON_POP_MS = 420
// Public knowledge (see resolveHotPotato.ts's BOMB_MAX_DELAY_MS) -- NOT the
// secret exact bombAtMs, which no client can ever read. This is only the
// known worst-case window, used to visualize rising risk over time; the real
// bomb can (and usually does) go off earlier.
private const val BOMB_MAX_DELAY_MS = 30_000L
private const val BALLOON_MIN_WIDTH_FRACTION = 0.16f
private const val BALLOON_MAX_WIDTH_FRACTION = 0.9f
private const val BALLOON_SHAKE_WIDTH_THRESHOLD = 0.6f

/**
 * Represents the hidden bomb's rising risk -- not a countdown to a known
 * instant, since bombAtMs is intentionally secret, but a width that grows
 * toward the known worst-case bound ([BOMB_MAX_DELAY_MS]) the longer the
 * current bomb has been armed, then shakes once it passes 60% of the
 * available width, then pops the moment the room's alive-player count
 * actually drops -- the only client-visible signal that an explosion just
 * happened. The "armed since" epoch resets on that same signal, so a fresh
 * (smaller, calmer) balloon starts growing for the next bomb.
 */
@Composable
private fun HotPotatoBalloon(room: MultiplayerRoom) {
    val aliveCount = room.players.count { it.alive }
    var previousAliveCount by remember { mutableIntStateOf(aliveCount) }
    var bombEpochMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var popping by remember { mutableStateOf(false) }

    LaunchedEffect(aliveCount) {
        if (aliveCount < previousAliveCount) {
            popping = true
            delay(BALLOON_POP_MS.toLong())
            popping = false
            bombEpochMs = System.currentTimeMillis()
        }
        previousAliveCount = aliveCount
    }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(100L)
            nowMs = System.currentTimeMillis()
        }
    }

    val growth = ((nowMs - bombEpochMs).coerceAtLeast(0L) / BOMB_MAX_DELAY_MS.toFloat()).coerceIn(0f, 0.97f)
    val targetWidthFraction = BALLOON_MIN_WIDTH_FRACTION + growth * (BALLOON_MAX_WIDTH_FRACTION - BALLOON_MIN_WIDTH_FRACTION)
    val shaking = !popping && targetWidthFraction >= BALLOON_SHAKE_WIDTH_THRESHOLD

    val widthFraction by animateFloatAsState(
        targetValue = if (popping) 1f else targetWidthFraction,
        animationSpec = tween(if (popping) BALLOON_POP_MS else 300, easing = EaseOut),
        label = "balloonWidth",
    )
    val fillAlpha by animateFloatAsState(
        targetValue = if (popping) 0f else (0.5f + growth * 0.4f),
        animationSpec = tween(if (popping) BALLOON_POP_MS else 300),
        label = "balloonAlpha",
    )
    val balloonColor = tricolorLerp(TechAccent, NeonYellow, NeonRed, growth)

    val shakeTransition = rememberInfiniteTransition(label = "balloonShakeTransition")
    val shakeMagnitude = 2f + (((targetWidthFraction - BALLOON_SHAKE_WIDTH_THRESHOLD) / (1f - BALLOON_SHAKE_WIDTH_THRESHOLD)).coerceIn(0f, 1f)) * 6f
    val shakeOffsetX by if (shaking) {
        shakeTransition.animateFloat(
            initialValue = -shakeMagnitude,
            targetValue = shakeMagnitude,
            animationSpec = infiniteRepeatable(tween(90, easing = LinearEasing), RepeatMode.Reverse),
            label = "balloonShakeX",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction.coerceIn(0f, 1f))
            .aspectRatio(1f)
            .offset(x = shakeOffsetX.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Outer glow ring, same visual language as CountdownOverlay's pulse rings.
        Box(
            modifier = Modifier
                .fillMaxSize(0.92f)
                .clip(CircleShape)
                .border(1.dp, balloonColor.copy(alpha = (fillAlpha * 0.8f).coerceAtMost(0.5f)), CircleShape)
        )
        Box(
            modifier = Modifier
                .fillMaxSize(0.8f)
                .clip(CircleShape)
                .background(balloonColor.copy(alpha = fillAlpha))
                .border(1.5.dp, balloonColor.copy(alpha = (fillAlpha + 0.2f).coerceAtMost(1f)), CircleShape)
        )
        // Glossy highlight, offset toward the top-left, so it reads as a
        // balloon/orb rather than a flat disc.
        Box(
            modifier = Modifier
                .fillMaxSize(0.28f)
                .offset(x = (-16).dp, y = (-16).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = (fillAlpha * 0.35f).coerceAtMost(0.3f)))
        )
    }
}

private fun tricolorLerp(start: Color, mid: Color, end: Color, t: Float): Color =
    if (t <= 0.5f) lerp(start, mid, (t / 0.5f).coerceIn(0f, 1f)) else lerp(mid, end, ((t - 0.5f) / 0.5f).coerceIn(0f, 1f))

/** Mirrors turnLogic.ts's timeLimitMsForRound so the client can render a countdown bar without the server pushing a redundant "total ms" field. */
internal fun timeLimitMsForRound(round: Int): Long {
    val decayed = GameConfig.INITIAL_TIME_LIMIT_MS - (round - 1) * GameConfig.TIME_LIMIT_DECAY_MS
    return decayed.coerceAtLeast(GameConfig.MINIMUM_TIME_LIMIT_MS)
}
