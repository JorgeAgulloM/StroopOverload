package com.softyorch.stroopoverload.ui

import android.app.Application
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.softyorch.stroopoverload.core.AndroidStringResolver
import com.softyorch.stroopoverload.data.AuthService
import com.softyorch.stroopoverload.data.FirebaseMultiplayerRepository
import com.softyorch.stroopoverload.data.FirebaseGameRepository
import com.softyorch.stroopoverload.data.repairedForSession
import com.softyorch.stroopoverload.domain.Achievement
import com.softyorch.stroopoverload.domain.GameMode
import com.softyorch.stroopoverload.domain.GameResult
import com.softyorch.stroopoverload.domain.UserProfile
import com.softyorch.stroopoverload.domain.XpBreakdown
import com.softyorch.stroopoverload.domain.XpSystem
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    // ON_RESUME (app start included) is also when solo runs still waiting for the server
    // are retried -- the usual moment a connection that dropped has come back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, musicManager) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> musicManager.pause()
                Lifecycle.Event.ON_RESUME -> {
                    musicManager.resume()
                    repository.flushPendingRuns()
                }
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
    // Saveable: after the process is killed mid-run the game route is restored, and plain
    // remember used to restart it in ENDLESS with a high score of 0.
    var previousHighScore by rememberSaveable { mutableIntStateOf(0) }
    var selectedGameMode by rememberSaveable { mutableStateOf(GameMode.ENDLESS) }
    var lastResult by remember { mutableStateOf<GameResult?>(null) }
    var lastXpBreakdown by remember { mutableStateOf<XpBreakdown?>(null) }
    var lastNewAchievements by remember { mutableStateOf<List<Achievement>>(emptyList()) }
    var showAnonymousGateDialog by remember { mutableStateOf(false) }

    val startRoute = remember {
        val isDemoShowcaseBuild = BuildConfig.FLAVOR == "demo"
        if (authService.currentUid == null && !isDemoShowcaseBuild) ROUTE_AUTH else ROUTE_HOME
    }

    // Seeding and profile loading both touch SharedPreferences and SQLite. They used
    // to run inside remember {}, i.e. during composition, which Compose may enter,
    // discard and re-run -- and which blocks the first frame on disk I/O. An effect
    // on the IO dispatcher is where this belongs.
    LaunchedEffect(navController) {
        val profile = withContext(Dispatchers.IO) {
            AsoDemoSeeder.seedIfNeeded(context)
            // A signed-in session starts straight at Home and never passes through
            // AuthViewModel's sync, so a guest profile saved as registered by an older build
            // is repaired here.
            val stored = repository.getProfile()
            stored.repairedForSession(authService.currentUid, authService.isAnonymousSession)
                ?.also { repository.updateProfile(it) }
                ?: stored
        }
        currentProfile = profile
        previousHighScore = profile.highScore
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
                    if (authService.isAnonymousSession == true) {
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
            LaunchedEffect(Unit) { gameVm.startGame(selectedGameMode, previousHighScore) }

            GameScreen(
                viewModel = gameVm,
                isAdFree = currentProfile.isAdFree || currentProfile.isPremium,
                onLeaveMatch = { navController.popBackStack() },
                onGameOver = { result, streak ->
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
            // lastResult lives in plain `remember`: after the process is killed in the background
            // the back stack restores this route but the result is gone, which used to leave a
            // blank screen. The run was already recorded, so there is nothing to show -- go home.
            if (lastResult == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(ROUTE_HOME) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            }
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
            val myUid = authService.currentUid
            if (myUid == null) {
                // Only reachable if the session ended on the way here. Online play needs a
                // real Firebase uid -- the backend identifies players by it -- so go back
                // rather than join with a made-up one.
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            MultiplayerScreen(
                myUid = myUid,
                onLeaveMatch = { navController.popBackStack() },
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
        return AuthViewModel(
            authService = AuthService(),
            repository = FirebaseGameRepository.getInstance(application),
            strings = AndroidStringResolver(application),
        ) as T
    }
}

private class ProfileViewModelFactory(private val application: Application) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ProfileViewModel(
            repository = FirebaseGameRepository.getInstance(application),
            authService = AuthService(),
            multiplayerRepository = FirebaseMultiplayerRepository(),
            strings = AndroidStringResolver(application),
        ) as T
    }
}
