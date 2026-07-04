package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LobbyScreen(
    onCreateRoom: (displayName: String) -> Unit,
    onJoinRoom: (code: String, displayName: String) -> Unit,
    errorMessage: String?,
    isConnecting: Boolean,
) {
    var displayName by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("[ PARTIDA ONLINE ]", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Tu nombre de piloto") },
            enabled = !isConnecting,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onCreateRoom(displayName.ifBlank { "Pilot" }) },
            enabled = !isConnecting,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (isConnecting) "CONECTANDO..." else "CREAR SALA") }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().take(5) },
            label = { Text("Código de sala") },
            enabled = !isConnecting,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onJoinRoom(code, displayName.ifBlank { "Pilot" }) },
            enabled = !isConnecting && code.length == 5,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("UNIRSE A SALA") }
        errorMessage?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
