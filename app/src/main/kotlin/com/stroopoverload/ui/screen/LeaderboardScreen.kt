package com.stroopoverload.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.stroopoverload.data.FirebaseGameRepository
import com.stroopoverload.domain.UserProfile
import com.stroopoverload.ui.theme.Muted
import com.stroopoverload.ui.theme.NeonGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(onBack: () -> Unit) {
    var entries by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        entries = FirebaseGameRepository().getLeaderboard()
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "LEADERBOARD",
                        style = MaterialTheme.typography.titleLarge,
                        color = NeonGreen,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = NeonGreen,
                )
                entries.isEmpty() -> Text(
                    "No scores yet.",
                    color = Muted,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn {
                    itemsIndexed(entries) { index, user ->
                        ListItem(
                            headlineContent = {
                                Text(user.displayName, color = MaterialTheme.colorScheme.onBackground)
                            },
                            leadingContent = {
                                Text("${index + 1}", color = Muted, style = MaterialTheme.typography.titleLarge)
                            },
                            trailingContent = {
                                Text(
                                    user.highScore.toString(),
                                    color = NeonGreen,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                    }
                }
            }
        }
    }
}
