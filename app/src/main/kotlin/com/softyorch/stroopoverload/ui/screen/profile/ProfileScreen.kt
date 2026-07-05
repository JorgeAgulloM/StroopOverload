package com.softyorch.stroopoverload.ui.screen.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softyorch.stroopoverload.domain.Achievement
import com.softyorch.stroopoverload.domain.XpSystem
import com.softyorch.stroopoverload.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val rarity = remember(state.profile.level) { XpSystem.levelRarity(state.profile.level) }
    val achievementPairs = remember(state.achievements) { state.achievements.chunked(2) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "[ NEURAL // PROFILE ]",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    if (!state.isEditing) {
                        IconButton(onClick = { viewModel.beginEdit() }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = MaterialTheme.colorScheme.secondary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(rarity.composeColorArgb), RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (state.isEditing) {
                                OutlinedTextField(
                                    value = state.profile.nickname,
                                    onValueChange = { viewModel.updateDraftNickname(it) },
                                    label = { Text("CALLSIGN", color = Muted) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                        focusedTextColor = MaterialTheme.colorScheme.onBackground
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    text = state.profile.displayName,
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = state.profile.uniqueName.ifBlank { "@pilot-${state.profile.userId.takeLast(4)}" },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TechAccent
                                )
                                if (state.profile.isAdFree || state.profile.isPremium) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .border(1.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("💎 VIP AD-FREE PILOT", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        // Rarity Badge
                        Box(
                            modifier = Modifier
                                .background(Color(rarity.composeColorArgb).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(1.dp, Color(rarity.composeColorArgb), RoundedCornerShape(4.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "LVL ${state.profile.level}",
                                color = Color(rarity.composeColorArgb),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (state.isEditing) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { viewModel.saveEdit() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                            ) {
                                Text("SAVE LINK", color = Background, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.discardEdit() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Muted),
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                            ) {
                                Text("DISCARD")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // XP Progress
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("SYNAPSE XP PROGRESS", style = MaterialTheme.typography.labelMedium, color = Muted)
                        Text(
                            "${state.xpInCurrentLevel} / ${state.xpNeededForNextLevel} XP",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val progress = if (state.xpNeededForNextLevel > 0) (state.xpInCurrentLevel.toFloat() / state.xpNeededForNextLevel).coerceIn(0f, 1f) else 1f
                    Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(Color(rarity.composeColorArgb), RoundedCornerShape(4.dp))
                        )
                    }
                }
            }

            // Career Stats Grid
            item {
                Text("[ TELEMETRY // CAREER STATS ]", style = MaterialTheme.typography.titleMedium, color = TechAccent)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        StatBox("TOTAL RUNS", state.careerStats.totalGamesPlayed.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        StatBox("VICTORIES", state.careerStats.totalGamesWon.toString(), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                        StatBox("FLAWLESS", state.careerStats.flawlessGamesCount.toString(), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        StatBox("MAX STREAK", "${state.careerStats.maxWinStreak} 🔥", NeonYellow, Modifier.weight(1f))
                        StatBox("MAX SURVIVAL", "${state.careerStats.maxSurvivalTimeMs / 1000}s ⏱️", TechAccent, Modifier.weight(1f))
                        StatBox("HIGH SCORE", state.profile.highScore.toString(), Color(0xFFFF8000), Modifier.weight(1f))
                    }
                }
            }

            // Achievements Header
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("[ SYNAPTIC TROPHIES ]", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                    val unlockedCount = state.achievements.count { it.isUnlocked }
                    Text("$unlockedCount / ${state.achievements.size}", style = MaterialTheme.typography.labelLarge, color = Muted)
                }
            }

            // Achievement Cards
            items(achievementPairs) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    AchievementCard(pair[0], Modifier.weight(1f))
                    if (pair.size > 1) {
                        AchievementCard(pair[1], Modifier.weight(1f))
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // Sign out Button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                if (com.softyorch.stroopoverload.BuildConfig.FLAVOR == "dev") {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Button(
                        onClick = {
                            com.softyorch.stroopoverload.data.AsoDemoSeeder.forceSeed(context)
                            viewModel.loadProfile()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonYellow),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text("[ 📸 ASO // RESTORE VIP DEMO PILOT ]", color = Background, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                OutlinedButton(
                    onClick = { viewModel.signOut(onSignedOut) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("[ TERMINATE NEURAL SESSION // SIGN OUT ]", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = Muted, fontSize = 10.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AchievementCard(achievement: Achievement, modifier: Modifier = Modifier) {
    val unlocked = achievement.isUnlocked
    val borderColor = if (unlocked) Color(achievement.rarity.composeColorArgb) else MaterialTheme.colorScheme.outline
    val bgColor = if (unlocked) Surface else CyberDark

    Column(
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(12.dp)
            .height(110.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = achievement.iconEmoji, fontSize = 24.sp)
            Text(
                text = if (unlocked) achievement.rarity.name else "LOCKED",
                style = MaterialTheme.typography.bodySmall,
                color = if (unlocked) Color(achievement.rarity.composeColorArgb) else Muted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column {
            val title = if (!unlocked && achievement.hidden) "???" else achievement.titleKey
            val desc = if (!unlocked && achievement.hidden) "Confidential Synapse Trophy" else achievement.descriptionKey
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = if (unlocked) MaterialTheme.colorScheme.onBackground else Muted,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                fontSize = 10.sp,
                maxLines = 2
            )
        }
    }
}
