package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.softyorch.stroopoverload.R

@Composable
fun LobbyScreen(
    onCreateRoom: (displayName: String) -> Unit,
    onJoinRoom: (code: String, displayName: String) -> Unit,
    errorReason: MultiplayerErrorReason?,
    isConnecting: Boolean,
) {
    var displayName by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val defaultName = stringResource(R.string.mp_lobby_default_name)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp),
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
        Button(
            onClick = { onCreateRoom(displayName.ifBlank { defaultName }) },
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
private fun errorReasonText(reason: MultiplayerErrorReason): String = when (reason) {
    is MultiplayerErrorReason.CreateRoomFailed -> reason.detail ?: stringResource(R.string.mp_error_create_room)
    is MultiplayerErrorReason.JoinRoomFailed -> reason.detail ?: stringResource(R.string.mp_error_join_room)
    is MultiplayerErrorReason.StartGameFailed -> reason.detail ?: stringResource(R.string.mp_error_start_game)
    is MultiplayerErrorReason.ConnectionLost -> reason.detail ?: stringResource(R.string.mp_error_connection_lost)
}
