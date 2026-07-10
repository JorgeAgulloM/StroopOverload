package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom
import com.softyorch.stroopoverload.ui.theme.Muted
import com.softyorch.stroopoverload.ui.theme.TechAccent
import kotlinx.coroutines.delay

private const val TIP_ROTATION_MS = 2400L

/**
 * Shown while room.status == "starting", before the 3-2-1-GO countdown plays
 * (see MultiplayerScreen.MultiplayerStartingScreen). This is the "actual
 * loading" phase -- however long the server takes to reach startsAtMs -- so
 * it gets real waiting-room content (rotating flavor tips + the same signal
 * scanner used elsewhere) instead of a bare spinner, matching the app's
 * established cyberpunk waiting-room look (see WaitingRoomScreen).
 */
@Composable
fun PreloadWaitingRoom(room: MultiplayerRoom) {
    val tips = listOf(
        stringResource(R.string.mp_loading_tip_1),
        stringResource(R.string.mp_loading_tip_2),
        stringResource(R.string.mp_loading_tip_3),
        stringResource(R.string.mp_loading_tip_4),
        stringResource(R.string.mp_loading_tip_5),
        stringResource(R.string.mp_loading_tip_6),
        stringResource(R.string.mp_loading_tip_7),
        stringResource(R.string.mp_loading_tip_8),
    )
    var tipIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TIP_ROTATION_MS)
            tipIndex = (tipIndex + 1) % tips.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.mp_preload_title),
                style = MaterialTheme.typography.labelLarge,
                color = TechAccent,
                letterSpacing = 4.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.mp_preload_subtitle, room.players.size),
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )

            Spacer(Modifier.height(48.dp))

            Crossfade(targetState = tipIndex, label = "loadingTip") { idx ->
                Text(
                    text = tips[idx],
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Spacer(Modifier.height(40.dp))

            SignalScanner(label = stringResource(R.string.mp_preload_scanner_label))
        }
    }
}
