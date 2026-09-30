package com.softyorch.stroopoverload.ui.screen.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.core.LegalLinks
import com.softyorch.stroopoverload.ui.theme.*

// Pieces of AuthScreen that stand on their own: legal consent, password visibility, email verification.

@Composable
internal fun LegalConsentText(modifier: Modifier = Modifier) {
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
internal fun EmailVerificationCard(
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
