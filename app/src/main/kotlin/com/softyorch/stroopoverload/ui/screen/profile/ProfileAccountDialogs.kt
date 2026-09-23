package com.softyorch.stroopoverload.ui.screen.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.ui.screen.auth.PasswordVisibilityToggle
import com.softyorch.stroopoverload.ui.theme.*

// Account dialogs opened from ProfileScreen: change password and delete account.

@Composable
internal fun ChangePasswordDialog(
    state: ProfileUiState,
    onDismiss: () -> Unit,
    onSubmit: (current: String, new: String, confirm: String) -> Unit,
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var currentPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_change_password_title), color = MaterialTheme.colorScheme.primary) },
        text = {
            if (state.changePasswordSuccess) {
                // Dismissing straight away (what this used to do) left the player with no
                // confirmation that anything happened.
                Text(
                    text = stringResource(R.string.profile_change_password_success),
                    color = MaterialTheme.colorScheme.tertiary,
                )
                return@AlertDialog
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text(stringResource(R.string.profile_change_password_current_label)) },
                    singleLine = true,
                    visualTransformation = if (currentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        PasswordVisibilityToggle(visible = currentPasswordVisible, onToggle = { currentPasswordVisible = !currentPasswordVisible })
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text(stringResource(R.string.profile_change_password_new_label)) },
                    singleLine = true,
                    visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        PasswordVisibilityToggle(visible = newPasswordVisible, onToggle = { newPasswordVisible = !newPasswordVisible })
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(stringResource(R.string.profile_change_password_confirm_label)) },
                    singleLine = true,
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        PasswordVisibilityToggle(visible = confirmPasswordVisible, onToggle = { confirmPasswordVisible = !confirmPasswordVisible })
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                state.changePasswordError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (state.isProcessingAccountAction) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            if (state.changePasswordSuccess) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) }
            } else {
                TextButton(
                    onClick = { onSubmit(currentPassword, newPassword, confirmPassword) },
                    enabled = !state.isProcessingAccountAction && currentPassword.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank(),
                ) {
                    Text(stringResource(R.string.profile_change_password_button))
                }
            }
        },
        dismissButton = {
            if (!state.changePasswordSuccess) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
}

@Composable
internal fun DeleteAccountDialog(
    state: ProfileUiState,
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_delete_account_title), color = MaterialTheme.colorScheme.error) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.profile_delete_account_warning), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.profile_delete_account_password_label)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        PasswordVisibilityToggle(visible = passwordVisible, onToggle = { passwordVisible = !passwordVisible })
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                state.deleteAccountError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (state.isProcessingAccountAction) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = !state.isProcessingAccountAction && password.isNotBlank(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.profile_delete_account_confirm_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
