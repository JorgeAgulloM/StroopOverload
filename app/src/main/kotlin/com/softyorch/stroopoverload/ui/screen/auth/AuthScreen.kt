package com.softyorch.stroopoverload.ui.screen.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softyorch.stroopoverload.ui.theme.*

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onNavigateHome: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var isRegisterTab by remember { mutableStateOf(false) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }

    LaunchedEffect(state.isLoggedIn, state.needsEmailVerification) {
        if (state.isLoggedIn && !state.needsEmailVerification) {
            onNavigateHome()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp)
    ) {
        if (state.needsEmailVerification) {
            EmailVerificationCard(
                email = email.ifBlank { "pilot@synapse.cyber" },
                onResend = { viewModel.resendVerificationEmail() },
                onCheckVerified = { viewModel.checkEmailVerified() },
                onSignOut = { viewModel.signOut() },
                message = state.errorMessage ?: state.successMessage,
                isError = state.errorMessage != null
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "[ STROOP // OVERLOAD ]",
                    style = MaterialTheme.typography.titleMedium,
                    color = TechAccent,
                    letterSpacing = 4.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "NEURAL LINK ACCESS",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(32.dp))

                // Tab Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (!isRegisterTab) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { isRegisterTab = false; viewModel.clearMessages() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "PILOT LOGIN",
                            color = if (!isRegisterTab) MaterialTheme.colorScheme.primary else Muted,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (isRegisterTab) MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { isRegisterTab = true; viewModel.clearMessages() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NEW CADET",
                            color = if (isRegisterTab) MaterialTheme.colorScheme.secondary else Muted,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Input Box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (isRegisterTab) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                        .padding(20.dp)
                ) {
                    AnimatedVisibility(visible = isRegisterTab) {
                        Column {
                            OutlinedTextField(
                                value = nickname,
                                onValueChange = { nickname = it },
                                label = { Text("CALLSIGN / NICKNAME", color = Muted) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("NEURAL FREQUENCY (EMAIL)", color = Muted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isRegisterTab) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("ACCESS CIPHER (PASSWORD)", color = Muted) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isRegisterTab) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (state.errorMessage != null) {
                        Text(
                            text = state.errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    if (state.successMessage != null) {
                        Text(
                            text = state.successMessage!!,
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    Button(
                        onClick = {
                            if (isRegisterTab) {
                                viewModel.register(email, password, nickname)
                            } else {
                                viewModel.login(email, password)
                            }
                        },
                        enabled = !state.isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRegisterTab) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.outline
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Background, strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = if (isRegisterTab) "INITIALIZE NEURAL LINK" else "ENGAGE LINK",
                                color = Background,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedButton(
                    onClick = { viewModel.continueAsGuest() },
                    enabled = !state.isLoading,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "[ BYPASS // ENTER AS GUEST PILOT ]",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun EmailVerificationCard(
    email: String,
    onResend: () -> Unit,
    onCheckVerified: () -> Unit,
    onSignOut: () -> Unit,
    message: String?,
    isError: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, NeonYellow, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "⚠️ NEURAL LINK PENDING",
            style = MaterialTheme.typography.headlineMedium,
            color = NeonYellow
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "A verification frequency has been transmitted to:\n$email",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Please authenticate your frequency to synchronize cloud progress and unlock online leaderboards.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (message != null) {
            Text(
                text = message,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        Button(
            onClick = onCheckVerified,
            colors = ButtonDefaults.buttonColors(containerColor = NeonYellow),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text("VERIFY STATUS", color = Background, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onResend,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text("RE-TRANSMIT EMAIL", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onSignOut) {
            Text("[ ABORT // SIGN OUT ]", color = Muted, style = MaterialTheme.typography.labelMedium)
        }
    }
}
