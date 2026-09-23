package com.softyorch.stroopoverload.ui.screen.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.core.LegalLinks
import com.softyorch.stroopoverload.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onNavigateHome: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isRegisterTab by remember { mutableStateOf(false) }
    var showForgotPassword by remember { mutableStateOf(false) }

    var email by remember { mutableStateOf("") }
    var emailConfirm by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var passwordConfirmVisible by remember { mutableStateOf(false) }
    var nickname by remember { mutableStateOf("") }
    var forgotEmail by remember { mutableStateOf("") }

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
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        if (state.needsEmailVerification) {
            EmailVerificationCard(
                email = email.ifBlank { stringResource(R.string.auth_default_pending_email) },
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
                    text = stringResource(R.string.auth_brand),
                    style = MaterialTheme.typography.titleMedium,
                    color = TechAccent,
                    letterSpacing = 4.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.auth_headline),
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
                            text = stringResource(R.string.auth_tab_login),
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
                            text = stringResource(R.string.auth_tab_register),
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
                  if (showForgotPassword) {
                    Text(
                        text = stringResource(R.string.auth_forgot_password_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.auth_forgot_password_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = forgotEmail,
                        onValueChange = { forgotEmail = it },
                        label = { Text(stringResource(R.string.auth_email_label), color = Muted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(20.dp))
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
                        onClick = { viewModel.forgotPassword(forgotEmail) },
                        enabled = !state.isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Background, strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = stringResource(R.string.auth_forgot_password_button),
                                color = Background,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            showForgotPassword = false
                            viewModel.clearMessages()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.auth_forgot_password_cancel), color = Muted, style = MaterialTheme.typography.labelMedium)
                    }
                  } else {
                    AnimatedVisibility(visible = isRegisterTab) {
                        Column {
                            OutlinedTextField(
                                value = nickname,
                                onValueChange = { nickname = it },
                                label = { Text(stringResource(R.string.auth_nickname_label), color = Muted) },
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
                        label = { Text(stringResource(R.string.auth_email_label), color = Muted) },
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

                    AnimatedVisibility(visible = isRegisterTab) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = emailConfirm,
                                onValueChange = { emailConfirm = it },
                                label = { Text(stringResource(R.string.auth_email_confirm_label), color = Muted) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.auth_password_label), color = Muted) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            PasswordVisibilityToggle(visible = passwordVisible, onToggle = { passwordVisible = !passwordVisible })
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isRegisterTab) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    AnimatedVisibility(visible = isRegisterTab) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = passwordConfirm,
                                onValueChange = { passwordConfirm = it },
                                label = { Text(stringResource(R.string.auth_password_confirm_label), color = Muted) },
                                singleLine = true,
                                visualTransformation = if (passwordConfirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    PasswordVisibilityToggle(visible = passwordConfirmVisible, onToggle = { passwordConfirmVisible = !passwordConfirmVisible })
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

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

                    LegalConsentText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )

                    Button(
                        onClick = {
                            if (isRegisterTab) {
                                viewModel.register(email, emailConfirm, password, passwordConfirm, nickname)
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
                                text = stringResource(if (isRegisterTab) R.string.auth_register_button else R.string.auth_login_button),
                                color = Background,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    if (!isRegisterTab) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                showForgotPassword = true
                                forgotEmail = email
                                viewModel.clearMessages()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.auth_forgot_password_link), color = Muted, style = MaterialTheme.typography.labelMedium)
                        }
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
                        text = stringResource(R.string.auth_guest_button),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun LegalConsentText(modifier: Modifier = Modifier) {
    val termsLabel = stringResource(R.string.auth_legal_terms_link)
    val privacyLabel = stringResource(R.string.auth_legal_privacy_link)
    val fullText = stringResource(R.string.auth_legal_consent, termsLabel, privacyLabel)
    val linkColor = MaterialTheme.colorScheme.primary

    val annotatedText = remember(fullText, termsLabel, privacyLabel, linkColor) {
        buildAnnotatedString {
            append(fullText)
            val linkStyle = TextLinkStyles(
                style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
            )

            val termsStart = fullText.indexOf(termsLabel)
            if (termsStart >= 0) {
                addLink(
                    LinkAnnotation.Url(LegalLinks.TERMS_OF_USE_URL, linkStyle),
                    termsStart,
                    termsStart + termsLabel.length
                )
            }

            val privacyStart = fullText.indexOf(privacyLabel)
            if (privacyStart >= 0) {
                addLink(
                    LinkAnnotation.Url(LegalLinks.PRIVACY_POLICY_URL, linkStyle),
                    privacyStart,
                    privacyStart + privacyLabel.length
                )
            }
        }
    }

    Text(
        text = annotatedText,
        style = MaterialTheme.typography.bodySmall,
        color = Muted,
        modifier = modifier
    )
}

@Composable
fun PasswordVisibilityToggle(visible: Boolean, onToggle: () -> Unit) {
    val description = stringResource(if (visible) R.string.common_hide_password else R.string.common_show_password)
    IconButton(
        onClick = onToggle,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(text = if (visible) "🙈" else "👁️", fontSize = 18.sp)
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
            text = stringResource(R.string.auth_verify_pending_title),
            style = MaterialTheme.typography.headlineMedium,
            color = NeonYellow
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.auth_verify_pending_message, email),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.auth_verify_pending_hint),
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
            Text(stringResource(R.string.auth_verify_status_button), color = Background, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onResend,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.auth_resend_button), style = MaterialTheme.typography.labelLarge)
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onSignOut) {
            Text(stringResource(R.string.auth_abort_button), color = Muted, style = MaterialTheme.typography.labelMedium)
        }
    }
}
