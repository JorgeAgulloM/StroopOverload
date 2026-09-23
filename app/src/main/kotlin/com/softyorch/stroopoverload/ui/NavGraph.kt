package com.softyorch.stroopoverload.ui

import android.app.Application
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.softyorch.stroopoverload.BuildConfig
import com.softyorch.stroopoverload.R
import com.softyorch.stroopoverload.ads.AdsConsentManager
import com.softyorch.stroopoverload.ads.InterstitialAdManager
import com.softyorch.stroopoverload.audio.MusicManager
import com.softyorch.stroopoverload.audio.MusicTrack
import com.softyorch.stroopoverload.data.AsoDemoSeeder
import com.softyorch.stroopoverload.data.AuthService
import com.softyorch.stroopoverload.data.FirebaseGameRepository
import com.softyorch.stroopoverload.domain.Achievement
import com.softyorch.stroopoverload.domain.GameMode
import com.softyorch.stroopoverload.domain.GameResult
import com.softyorch.stroopoverload.domain.UserProfile
import com.softyorch.stroopoverload.domain.XpBreakdown
import com.softyorch.stroopoverload.domain.XpSystem
import com.softyorch.stroopoverload.game.GameState
import com.softyorch.stroopoverload.game.GameViewModel
import com.softyorch.stroopoverload.ui.screen.GameModeSelectScreen
import com.softyorch.stroopoverload.ui.screen.GameOverScreen
import com.softyorch.stroopoverload.ui.screen.GameScreen
import com.softyorch.stroopoverload.ui.screen.HomeScreen
import com.softyorch.stroopoverload.ui.screen.LeaderboardScreen
import com.softyorch.stroopoverload.ui.screen.multiplayer.MultiplayerScreen
import com.softyorch.stroopoverload.ui.screen.auth.AuthScreen
import com.softyorch.stroopoverload.ui.screen.auth.AuthViewModel
import com.softyorch.stroopoverload.ui.screen.profile.ProfileScreen
import com.softyorch.stroopoverload.ui.screen.profile.ProfileViewModel
import com.softyorch.stroopoverload.ui.components.AnonymousGateDialog
import kotlinx.coroutines.launch

private const val ROUTE_AUTH = "auth"
private const val ROUTE_HOME = "home"
private const val ROUTE_GAME_MODE_SELECT = "game_mode_select"
private const val ROUTE_GAME = "game"
private const val ROUTE_GAME_OVER = "game_over"
private const val ROUTE_LEADERBOARD = "leaderboard"
private const val ROUTE_MULTIPLAYER = "multiplayer"
private const val ROUTE_PROFILE = "profile"

// Shared by local single-player (ROUTE_GAME below) and online gameplay
// (MultiplayerScreen's own PLAYING/FINISHED sub-state) -- same "active match"
// music context either way.
val GAMEPLAY_MUSIC_TRACKS = listOf(
    R.raw.music_gameplay_01,
    R.raw.music_gameplay_02,
    R.raw.music_gameplay_03,
    R.raw.music_gameplay_04,
)

@Composable
fun StroopNavGraph() {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val navController = rememberNavController()
    val authService = remember { AuthService() }
    val repository = remember { FirebaseGameRepository.getInstance(context) }
    val adsConsentManager = remember { AdsConsentManager(context) }
    val interstitialAdManager = remember { InterstitialAdManager(BuildConfig.AD_UNIT_INTERSTITIAL_ONLINE) }
    val musicManager = remember { MusicManager(context) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose { musicManager.release() }
    }

    // Screen off (or any other loss of foreground -- recents, a system
    // dialog, an interstitial ad activity) triggers ON_PAUSE on the hosting
    // Activity before Android actually stops it, so this is the earliest
    // reliable hook to cut music instead of leaving it playing behind a
    // locked screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, musicManager) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> musicManager.pause()
                Lifecycle.Event.ON_RESUME -> musicManager.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Single source of truth for route-level music. ROUTE_MULTIPLAYER is
    // deliberately excluded -- it owns its own music switching internally
    // across its lobby/waiting/gameplay sub-states (see MultiplayerScreen).
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    LaunchedEffect(currentRoute) {
        when (currentRoute) {
            ROUTE_HOME -> musicManager.setTrack(MusicTrack.Loop(R.raw.music_dashboard))
            ROUTE_GAME -> musicManager.setTrack(MusicTrack.Playlist(GAMEPLAY_MUSIC_TRACKS))
            ROUTE_MULTIPLAYER -> Unit
            else -> musicManager.setTrack(null)
        }
    }

    var currentProfile by remember { mutableStateOf(UserProfile()) }
    var previousHighScore by remember { mutableIntStateOf(0) }
    var selectedGameMode by remember { mutableStateOf(GameMode.ENDLESS) }
    var lastResult by remember { mutableStateOf<GameResult?>(null) }
    var lastXpBreakdown by remember { mutableStateOf<XpBreakdown?>(null) }
    var lastNewAchievements by remember { mutableStateOf<List<Achievement>>(emptyList()) }
    var showAnonymousGateDialog by remember { mutableStateOf(false) }

    val startRoute = remember {
        AsoDemoSeeder.seedIfNeeded(context)
        val isDemoShowcaseBuild = BuildConfig.FLAVOR == "demo"
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
            // Ask for ad consent (GDPR/UMP) once we reach a real screen with an
            // Activity available, then preload the online-match interstitial so
            // it's ready by the time the player actually creates/joins a room.
            val activity = LocalActivity.current
            LaunchedEffect(activity) {
                activity?.let {
                    adsConsentManager.requestConsentIfNeeded(it) {
                        interstitialAdManager.preload(it)
                    }
                }
            }
            HomeScreen(
                profile = currentProfile,
                isReady = true,
                onPlay = { navController.navigate(ROUTE_GAME_MODE_SELECT) },
                onLeaderboard = { navController.navigate(ROUTE_LEADERBOARD) },
                onMultiplayer = {
                    // Anonymous profiles never sync to Firestore (see
                    // FirebaseGameRepository.updateProfile), so a guest could join a
                    // room but could never actually be scored -- block the whole
                    // flow up front instead of letting them play for nothing.
                    if (authService.currentUser?.isAnonymous == true) {
                        showAnonymousGateDialog = true
                    } else {
                        navController.navigate(ROUTE_MULTIPLAYER)
                    }
                },
                onProfile = { navController.navigate(ROUTE_PROFILE) },
            )
        }
        composable(ROUTE_GAME_MODE_SELECT) {
            GameModeSelectScreen(
                onBack = { navController.popBackStack() },
                onModeSelected = { mode ->
                    selectedGameMode = mode
                    navController.navigate(ROUTE_GAME)
                },
            )
        }
        composable(ROUTE_GAME) {
            val gameVm: GameViewModel = viewModel()
            val gameState by gameVm.state.collectAsState()
            LaunchedEffect(Unit) { gameVm.startGame(selectedGameMode, previousHighScore) }

            GameScreen(
                viewModel = gameVm,
                isAdFree = currentProfile.isAdFree || currentProfile.isPremium,
                onGameOver = { result ->
                    val playingState = gameState as? GameState.Playing
                    val streak = playingState?.currentStreak ?: 0
                    lastResult = result
                    scope.launch {
                        val prof = repository.getProfile()
                        val xpBreakdown = XpSystem.calculateGameXp(result, prof.dailyStreak, streak)
                        val newAch = repository.recordGameResult(result, xpBreakdown.total, streak)
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
            MultiplayerScreen(
                myUid = authService.currentUid ?: "guest_local_0001",
                myNickname = currentProfile.displayName,
                repository = repository,
                interstitialAdManager = interstitialAdManager,
                isAdFree = currentProfile.isAdFree || currentProfile.isPremium,
                musicManager = musicManager,
            )
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

    if (showAnonymousGateDialog) {
        AnonymousGateDialog(onDismiss = { showAnonymousGateDialog = false })
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
