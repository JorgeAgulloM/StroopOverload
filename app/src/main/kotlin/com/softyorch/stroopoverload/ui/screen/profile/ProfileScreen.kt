package com.softyorch.stroopoverload.ui.screen.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.domain.Achievement
import com.softyorch.stroopoverload.domain.XpSystem
import com.softyorch.stroopoverload.ui.screen.auth.PasswordVisibilityToggle
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
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.profile_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    if (!state.isEditing) {
                        IconButton(onClick = { viewModel.beginEdit() }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.common_edit_profile), tint = MaterialTheme.colorScheme.secondary)
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
                                    label = { Text(stringResource(R.string.profile_callsign_label), color = Muted) },
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
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
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
                                        Text(stringResource(R.string.profile_vip_badge), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        // Rarity Badge
                        Column(
                            modifier = Modifier
                                .background(Color(rarity.composeColorArgb).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(1.dp, Color(rarity.composeColorArgb), RoundedCornerShape(4.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .widthIn(max = 120.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.leaderboard_level, state.profile.level),
                                color = Color(rarity.composeColorArgb),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                            Text(
                                text = stringResource(XpSystem.titleResForLevel(state.profile.level)),
                                color = Color(rarity.composeColorArgb),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
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
                                Text(stringResource(R.string.profile_save), color = Background, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.discardEdit() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Muted),
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                            ) {
                                Text(stringResource(R.string.profile_discard))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // XP Progress
                    Text(
                        text = stringResource(R.string.profile_xp_progress_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.profile_xp_progress_value, state.xpInCurrentLevel, state.xpNeededForNextLevel),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                    )
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
                Text(stringResource(R.string.profile_career_stats_header), style = MaterialTheme.typography.titleMedium, color = TechAccent)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        StatBox(stringResource(R.string.profile_stat_total_runs), state.careerStats.totalGamesPlayed.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        StatBox(stringResource(R.string.profile_stat_victories), state.careerStats.totalGamesWon.toString(), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        StatBox(stringResource(R.string.profile_stat_flawless), state.careerStats.flawlessGamesCount.toString(), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                        StatBox(stringResource(R.string.profile_stat_max_streak_label), stringResource(R.string.profile_stat_max_streak_value, state.careerStats.maxWinStreak), NeonYellow, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        StatBox(stringResource(R.string.profile_stat_max_survival_label), stringResource(R.string.profile_stat_max_survival_value, state.careerStats.maxSurvivalTimeMs / 1000), TechAccent, Modifier.weight(1f))
                        StatBox(stringResource(R.string.profile_stat_high_score_label), state.profile.highScore.toString(), Color(0xFFFF8000), Modifier.weight(1f))
                    }
                }
            }

            // Achievements Header
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.profile_trophies_header),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val unlockedCount = state.achievements.count { it.isUnlocked }
                    Box(
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.profile_trophies_count, unlockedCount, state.achievements.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = Muted,
                            maxLines = 1,
                        )
                    }
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
                if (com.softyorch.stroopoverload.BuildConfig.FLAVOR == "demo") {
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
                        Text(stringResource(R.string.profile_demo_restore_button), color = Background, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (!state.profile.isAnonymous) {
                    OutlinedButton(
                        onClick = { showChangePasswordDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(stringResource(R.string.profile_change_password_button), style = MaterialTheme.typography.labelLarge)
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
                    Text(stringResource(R.string.profile_sign_out), style = MaterialTheme.typography.labelLarge)
                }
                if (!state.profile.isAnonymous) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showDeleteAccountDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(stringResource(R.string.profile_delete_account_button), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            state = state,
            onDismiss = {
                showChangePasswordDialog = false
                viewModel.clearChangePasswordResult()
            },
            onSubmit = { current, new, confirm -> viewModel.changePassword(current, new, confirm) },
        )
    }

    if (showDeleteAccountDialog) {
        DeleteAccountDialog(
            state = state,
            onDismiss = {
                showDeleteAccountDialog = false
                viewModel.clearDeleteAccountError()
            },
            onConfirm = { password -> viewModel.deleteAccount(password, onDeleted = onSignedOut) },
        )
    }
}

@Composable
private fun ChangePasswordDialog(
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

    LaunchedEffect(state.changePasswordSuccess) {
        if (state.changePasswordSuccess) onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_change_password_title), color = MaterialTheme.colorScheme.primary) },
        text = {
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
            TextButton(
                onClick = { onSubmit(currentPassword, newPassword, confirmPassword) },
                enabled = !state.isProcessingAccountAction && currentPassword.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank(),
            ) {
                Text(stringResource(R.string.profile_change_password_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun DeleteAccountDialog(
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

@Composable
private fun StatBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .heightIn(min = 92.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
            .heightIn(min = 110.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = achievement.iconEmoji, fontSize = 24.sp)
            Text(
                text = if (unlocked) achievement.rarity.name else stringResource(R.string.common_locked),
                style = MaterialTheme.typography.bodySmall,
                color = if (unlocked) Color(achievement.rarity.composeColorArgb) else Muted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column {
            val title = if (!unlocked && achievement.hidden) stringResource(R.string.profile_achievement_hidden_title) else stringResource(achievement.titleRes)
            val desc = if (!unlocked && achievement.hidden) stringResource(R.string.profile_achievement_hidden_desc) else stringResource(achievement.descriptionRes)
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = if (unlocked) MaterialTheme.colorScheme.onBackground else Muted,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
