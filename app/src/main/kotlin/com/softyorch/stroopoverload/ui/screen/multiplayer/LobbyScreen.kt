package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.data.MultiplayerCallFailure
import com.softyorch.stroopoverload.domain.multiplayer.RoomMode
import com.softyorch.stroopoverload.ui.components.hudCornerBrackets
import com.softyorch.stroopoverload.ui.theme.Muted
import com.softyorch.stroopoverload.ui.theme.TechAccent

@Composable
fun LobbyScreen(
    pilotName: String,
    onCreateRoom: (mode: RoomMode) -> Unit,
    onJoinRoom: (code: String) -> Unit,
    errorReason: MultiplayerErrorReason?,
    isConnecting: Boolean,
) {
    var code by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(RoomMode.MISTAKE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.mp_lobby_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        // Pilot identity is the account's real nickname -- no free-text entry,
        // so match history/scoring always ties back to a real profile.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.mp_lobby_name_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
                Text(
                    text = pilotName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TechAccent,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.mp_lobby_mode_label), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RoomMode.entries.forEach { mode ->
                ModeCard(
                    mode = mode,
                    selected = mode == selectedMode,
                    enabled = !isConnecting,
                    onClick = { selectedMode = mode },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onCreateRoom(selectedMode) },
            enabled = !isConnecting,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (isConnecting) stringResource(R.string.mp_lobby_connecting) else stringResource(R.string.mp_lobby_create_room)) }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().take(5) },
            label = { Text(stringResource(R.string.mp_lobby_room_code_label)) },
            enabled = !isConnecting,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onJoinRoom(code) },
            enabled = !isConnecting && code.length == 5,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.mp_lobby_join_room)) }
        errorReason?.let {
            Spacer(Modifier.height(16.dp))
            Text(errorReasonText(it), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ModeCard(mode: RoomMode, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    // A soft filled `primaryContainer` selected-state (the generic Material
    // chip look) reads as safe/default, not "Aggressive. Electric. Sharp."
    // (PRODUCT.md) -- selection here is a tactical-HUD corner-bracket
    // "targeting reticle" instead, on an always-dark surface.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (selected) TechAccent else MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (selected) Modifier.hudCornerBrackets(TechAccent, inset = 3.dp) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(mode.titleRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(mode.descriptionRes),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun errorReasonText(reason: MultiplayerErrorReason): String = when (reason) {
    is MultiplayerErrorReason.CreateRoomFailed -> stringResource(
        callFailureTextRes(reason.failure, generic = R.string.mp_error_create_room)
    )
    is MultiplayerErrorReason.JoinRoomFailed -> stringResource(
        callFailureTextRes(reason.failure, generic = R.string.mp_error_join_room)
    )
    MultiplayerErrorReason.StartGameFailed -> stringResource(R.string.mp_error_start_game)
    MultiplayerErrorReason.ConnectionLost -> stringResource(R.string.mp_error_connection_lost)
}

@StringRes
private fun callFailureTextRes(failure: MultiplayerCallFailure, @StringRes generic: Int): Int = when (failure) {
    MultiplayerCallFailure.ROOM_NOT_FOUND -> R.string.mp_error_room_not_found
    MultiplayerCallFailure.ROOM_FULL -> R.string.mp_error_room_full
    MultiplayerCallFailure.ALREADY_STARTED -> R.string.mp_error_room_already_started
    MultiplayerCallFailure.INVALID_CODE -> R.string.mp_error_invalid_code
    MultiplayerCallFailure.RATE_LIMITED -> R.string.mp_error_rate_limited
    MultiplayerCallFailure.UNKNOWN -> generic
}
