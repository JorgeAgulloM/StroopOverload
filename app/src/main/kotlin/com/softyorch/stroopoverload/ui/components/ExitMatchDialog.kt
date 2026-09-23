package com.softyorch.stroopoverload.ui.components

import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.softyorch.stroopoverload.R

/**
 * Confirms abandoning a match in progress. The system back button used to drop the
 * player straight out of a live run with no warning and no score recorded -- and,
 * online, without telling them they were forfeiting to the other players.
 *
 * @param messageRes what leaving costs, which differs between local and online play.
 */
@Composable
fun ExitMatchDialog(@StringRes messageRes: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.exit_match_title),
                color = MaterialTheme.colorScheme.primary,
            )
        },
        text = { Text(stringResource(messageRes)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.exit_match_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.exit_match_stay)) }
        },
    )
}
