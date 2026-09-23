package com.softyorch.stroopoverload.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.ui.components.pilotHandleFallback
import com.softyorch.stroopoverload.data.FirebaseGameRepository
import com.softyorch.stroopoverload.domain.UserProfile
import com.softyorch.stroopoverload.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { FirebaseGameRepository.getInstance(context) }
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var myProfile by remember { mutableStateOf(UserProfile()) }
    var myRank by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }

    fun fetchLeaderboard(force: Boolean = false) {
        loading = true
        scope.launch {
            val prof = repository.getProfile()
            myProfile = prof
            entries = repository.getLeaderboard(forceRefresh = force)
            myRank = repository.getUserRank(prof.points)
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        fetchLeaderboard()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.leaderboard_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    IconButton(onClick = { fetchLeaderboard(force = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh), tint = TechAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            // Player's Own Rank Card
            if (!loading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NeonYellow, RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .background(NeonYellow.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("#$myRank", color = NeonYellow, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.leaderboard_your_rank), style = MaterialTheme.typography.bodySmall, color = Muted, fontSize = 10.sp)
                            Text(
                                text = myProfile.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    LeaderboardScoreColumn(points = myProfile.points, highScore = myProfile.highScore, pointsColor = NeonYellow)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(stringResource(R.string.leaderboard_top_pilots), style = MaterialTheme.typography.labelMedium, color = TechAccent)
            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
                    entries.isEmpty() -> Text(stringResource(R.string.leaderboard_empty), color = Muted, modifier = Modifier.align(Alignment.Center))
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(entries) { index, user ->
                            val rank = index + 1
                            val isMe = user.userId == myProfile.userId
                            val borderColor = when (rank) {
                                1 -> NeonYellow
                                2 -> MaterialTheme.colorScheme.primary
                                3 -> MaterialTheme.colorScheme.secondary
                                else -> if (isMe) TechAccent else MaterialTheme.colorScheme.outline
                            }
                            val rankBg = when (rank) {
                                1 -> NeonYellow.copy(alpha = 0.2f)
                                2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                3 -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.surface
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                                    .background(if (isMe) MaterialTheme.colorScheme.surfaceVariant else CyberDark)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(40.dp)
                                            .background(rankBg, RoundedCornerShape(4.dp))
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "#$rank",
                                            color = when (rank) {
                                                1 -> NeonYellow
                                                2 -> MaterialTheme.colorScheme.primary
                                                3 -> MaterialTheme.colorScheme.secondary
                                                else -> Muted
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = user.displayName,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                                fontWeight = if (isMe) FontWeight.Black else FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            if (isMe) {
                                                Text(stringResource(R.string.leaderboard_you_tag), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, maxLines = 1)
                                            }
                                        }
                                        Text(
                                            text = user.uniqueName.ifBlank { pilotHandleFallback(user.userId) },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Muted,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))
                                LeaderboardScoreColumn(
                                    points = user.points,
                                    highScore = user.highScore,
                                    pointsColor = when (rank) {
                                        1 -> NeonYellow
                                        2 -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onBackground
                                    },
                                )
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardScoreColumn(points: Int, highScore: Int, pointsColor: Color) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = stringResource(R.string.leaderboard_points_caption),
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            fontSize = 9.sp,
            maxLines = 1,
        )
        Text(
            text = stringResource(R.string.leaderboard_points, points),
            style = MaterialTheme.typography.titleMedium,
            color = pointsColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.leaderboard_highscore_caption),
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            fontSize = 9.sp,
            maxLines = 1,
        )
        Text(
            text = stringResource(R.string.leaderboard_high_score, highScore),
            style = MaterialTheme.typography.bodyMedium,
            color = TechAccent,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
