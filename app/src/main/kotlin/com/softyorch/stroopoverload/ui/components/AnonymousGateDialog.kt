package com.softyorch.stroopoverload.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.ui.theme.Muted
import com.softyorch.stroopoverload.ui.theme.NeonYellow
import com.softyorch.stroopoverload.ui.theme.TechAccent

/**
 * Blocks guest/anonymous accounts from entering online multiplayer. Shown
 * instead of navigating there, so no room ever gets created for a player
 * whose match result couldn't be scored anyway (anonymous profiles never
 * sync to Firestore -- see FirebaseGameRepository.updateProfile).
 */
@Composable
fun AnonymousGateDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, TechAccent, RoundedCornerShape(16.dp))
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(NeonYellow.copy(alpha = 0.12f))
                    .border(1.dp, NeonYellow, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = NeonYellow)
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.mp_anon_gate_title),
                style = MaterialTheme.typography.titleLarge,
                color = NeonYellow,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.mp_anon_gate_body),
                style = MaterialTheme.typography.bodyMedium.copy(
                    shadow = Shadow(color = TechAccent.copy(alpha = 0.25f), blurRadius = 12f),
                ),
                color = Muted,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(
                    text = stringResource(R.string.mp_anon_gate_dismiss),
                    color = MaterialTheme.colorScheme.background,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}
