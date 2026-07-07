package com.softyorch.stroopoverload.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.domain.UserProfile
import com.softyorch.stroopoverload.domain.XpSystem
import com.softyorch.stroopoverload.ui.theme.*

@Composable
fun HomeScreen(
    profile: UserProfile = UserProfile(),
    onPlay: () -> Unit,
    onLeaderboard: () -> Unit,
    onMultiplayer: () -> Unit,
    onProfile: () -> Unit,
    isReady: Boolean,
) {
    val rarity = remember(profile.level) { XpSystem.levelRarity(profile.level) }

    // Pulsing animation for neural launch button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        // Top Command Bar - Unified Left Badge (two rows: identity, then sub-badges)
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .border(1.dp, Color(rarity.composeColorArgb), RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onProfile)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Person, contentDescription = stringResource(R.string.common_profile_icon_desc), tint = Color(rarity.composeColorArgb), modifier = Modifier.size(16.dp))
                Text(
                    text = stringResource(R.string.home_player_badge, profile.displayName.take(10), profile.level),
                    color = Color(rarity.composeColorArgb),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (profile.isAdFree || profile.isPremium) {
                    Text(stringResource(R.string.home_vip_badge), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Text(stringResource(R.string.home_streak_badge, profile.dailyStreak), color = NeonYellow, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }

        // Center Branding & Neural Launch
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.labelLarge,
                color = TechAccent,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.home_title_line1),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.home_title_line2),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(48.dp))

            // Neural Launch Button
            Button(
                onClick = onPlay,
                enabled = isReady,
                modifier = Modifier
                    .scale(if (isReady) pulseScale else 1f)
                    .width(260.dp)
                    .heightIn(min = 60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.outline
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = stringResource(if (isReady) R.string.home_play_button else R.string.home_play_button_loading),
                    style = MaterialTheme.typography.titleMedium,
                    color = Background,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(Modifier.height(20.dp))

            // Quick Links
            OutlinedButton(
                onClick = onLeaderboard,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.width(200.dp).heightIn(min = 44.dp)
            ) {
                Text(stringResource(R.string.home_leaderboard_button), style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onMultiplayer,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.width(200.dp).heightIn(min = 44.dp)
            ) {
                Text(stringResource(R.string.home_multiplayer_button), style = MaterialTheme.typography.labelLarge)
            }
        }

        // Footer HUD Info
        Text(
            text = stringResource(R.string.home_footer),
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
