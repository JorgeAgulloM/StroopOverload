package com.softyorch.stroopoverload.ui

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.softyorch.stroopoverload.data.AsoDemoSeeder
import com.softyorch.stroopoverload.data.AuthService
import com.softyorch.stroopoverload.data.FirebaseGameRepository
import com.softyorch.stroopoverload.domain.Achievement
import com.softyorch.stroopoverload.domain.GameResult
import com.softyorch.stroopoverload.domain.UserProfile
import com.softyorch.stroopoverload.domain.XpBreakdown
import com.softyorch.stroopoverload.domain.XpSystem
import com.softyorch.stroopoverload.game.GameState
import com.softyorch.stroopoverload.game.GameViewModel
import com.softyorch.stroopoverload.ui.screen.GameOverScreen
import com.softyorch.stroopoverload.ui.screen.GameScreen
import com.softyorch.stroopoverload.ui.screen.HomeScreen
import com.softyorch.stroopoverload.ui.screen.LeaderboardScreen
import com.softyorch.stroopoverload.ui.screen.multiplayer.MultiplayerScreen
import com.softyorch.stroopoverload.ui.screen.auth.AuthScreen
import com.softyorch.stroopoverload.ui.screen.auth.AuthViewModel
import com.softyorch.stroopoverload.ui.screen.profile.ProfileScreen
import com.softyorch.stroopoverload.ui.screen.profile.ProfileViewModel
import kotlinx.coroutines.launch

private const val ROUTE_AUTH = "auth"
private const val ROUTE_HOME = "home"
private const val ROUTE_GAME = "game"
private const val ROUTE_GAME_OVER = "game_over"
private const val ROUTE_LEADERBOARD = "leaderboard"
private const val ROUTE_MULTIPLAYER = "multiplayer"
private const val ROUTE_PROFILE = "profile"

@Composable
fun StroopNavGraph() {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val navController = rememberNavController()
    val authService = remember { AuthService() }
    val repository = remember { FirebaseGameRepository.getInstance(context) }
    val scope = rememberCoroutineScope()

    var currentProfile by remember { mutableStateOf(UserProfile()) }
    var previousHighScore by remember { mutableIntStateOf(0) }
    var lastResult by remember { mutableStateOf<GameResult?>(null) }
    var lastXpBreakdown by remember { mutableStateOf<XpBreakdown?>(null) }
    var lastNewAchievements by remember { mutableStateOf<List<Achievement>>(emptyList()) }

    val startRoute = remember {
        AsoDemoSeeder.seedIfNeeded(context)
        val isDemoShowcaseBuild = com.softyorch.stroopoverload.BuildConfig.FLAVOR == "demo"
        if (authService.currentUid == null && !isDemoShowcaseBuild) ROUTE_AUTH else ROUTE_HOME
    }

    LaunchedEffect(navController) {
        currentProfile = repository.getProfile()
        previousHighScore = currentProfile.highScore
    }

    NavHost(navController = navController, startDestination = startRoute) {
        composable(ROUTE_AUTH) {
            val authVm: AuthViewModel = viewModel(factory = AuthViewModelFactory(application))
            AuthScreen(
                viewModel = authVm,
                onNavigateHome = {
                    scope.launch { currentProfile = repository.getProfile() }
                    navController.navigate(ROUTE_HOME) {
                        popUpTo(ROUTE_AUTH) { inclusive = true }
                    }
                }
            )
        }
        composable(ROUTE_HOME) {
            LaunchedEffect(Unit) {
                currentProfile = repository.getProfile()
                previousHighScore = currentProfile.highScore
            }
            HomeScreen(
                profile = currentProfile,
                isReady = true,
                onPlay = { navController.navigate(ROUTE_GAME) },
                onLeaderboard = { navController.navigate(ROUTE_LEADERBOARD) },
                onMultiplayer = { navController.navigate(ROUTE_MULTIPLAYER) },
                onProfile = { navController.navigate(ROUTE_PROFILE) },
            )
        }
        composable(ROUTE_GAME) {
            val gameVm: GameViewModel = viewModel()
            val gameState by gameVm.state.collectAsState()
            LaunchedEffect(Unit) { gameVm.startGame(previousHighScore) }
            
            GameScreen(
                viewModel = gameVm,
                onGameOver = { result ->
                    val playingState = gameState as? GameState.Playing
                    val streak = playingState?.currentStreak ?: 0
                    lastResult = result
                    scope.launch {
                        val prof = repository.getProfile()
                        val xpBreakdown = XpSystem.calculateGameXp(result, prof.dailyStreak, streak)
                        val newAch = repository.recordGameResult(result, xpBreakdown.total)
                        lastXpBreakdown = xpBreakdown
                        lastNewAchievements = newAch
                        currentProfile = repository.getProfile()
                        previousHighScore = currentProfile.highScore
                        navController.navigate(ROUTE_GAME_OVER) {
                            popUpTo(ROUTE_HOME)
                        }
                    }
                },
            )
        }
        composable(ROUTE_GAME_OVER) {
            lastResult?.let { result ->
                GameOverScreen(
                    result = result,
                    xpBreakdown = lastXpBreakdown,
                    newAchievements = lastNewAchievements,
                    onPlayAgain = {
                        navController.navigate(ROUTE_GAME) {
                            popUpTo(ROUTE_HOME)
                        }
                    },
                    onMenu = {
                        navController.navigate(ROUTE_HOME) {
                            popUpTo(ROUTE_HOME) { inclusive = true }
                        }
                    },
                )
            }
        }
        composable(ROUTE_LEADERBOARD) {
            LeaderboardScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_MULTIPLAYER) {
            MultiplayerScreen(myUid = authService.currentUid ?: "guest_local_0001")
        }
        composable(ROUTE_PROFILE) {
            val profileVm: ProfileViewModel = viewModel(factory = ProfileViewModelFactory(application))
            LaunchedEffect(Unit) { profileVm.loadProfile() }
            ProfileScreen(
                viewModel = profileVm,
                onBack = {
                    scope.launch { currentProfile = repository.getProfile() }
                    navController.popBackStack()
                },
                onSignedOut = {
                    navController.navigate(ROUTE_AUTH) {
                        popUpTo(ROUTE_HOME) { inclusive = true }
                    }
                }
            )
        }
    }
}

private class AuthViewModelFactory(private val application: Application) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AuthViewModel(application) as T
    }
}

private class ProfileViewModelFactory(private val application: Application) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ProfileViewModel(application) as T
    }
}
