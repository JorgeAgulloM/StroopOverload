package com.softyorch.stroopoverload.ui.screen.multiplayer

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
import androidx.compose.ui.unit.dp
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.domain.multiplayer.RoomMode

@Composable
fun LobbyScreen(
    onCreateRoom: (displayName: String, mode: RoomMode) -> Unit,
    onJoinRoom: (code: String, displayName: String) -> Unit,
    errorReason: MultiplayerErrorReason?,
    isConnecting: Boolean,
) {
    var displayName by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(RoomMode.MISTAKE) }
    val defaultName = stringResource(R.string.mp_lobby_default_name)

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
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text(stringResource(R.string.mp_lobby_name_label)) },
            enabled = !isConnecting,
            modifier = Modifier.fillMaxWidth(),
        )
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
            onClick = { onCreateRoom(displayName.ifBlank { defaultName }, selectedMode) },
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
            onClick = { onJoinRoom(code, displayName.ifBlank { defaultName }) },
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
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
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
    is MultiplayerErrorReason.CreateRoomFailed -> reason.detail ?: stringResource(R.string.mp_error_create_room)
    is MultiplayerErrorReason.JoinRoomFailed -> reason.detail ?: stringResource(R.string.mp_error_join_room)
    is MultiplayerErrorReason.StartGameFailed -> reason.detail ?: stringResource(R.string.mp_error_start_game)
    is MultiplayerErrorReason.ConnectionLost -> reason.detail ?: stringResource(R.string.mp_error_connection_lost)
}
