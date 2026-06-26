package com.softyorch.stroopoverload.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.softyorch.stroopoverload.data.AuthService
import com.softyorch.stroopoverload.data.FirebaseGameRepository
import com.softyorch.stroopoverload.domain.GameResult
import com.softyorch.stroopoverload.game.GameViewModel
import com.softyorch.stroopoverload.ui.screen.GameOverScreen
import com.softyorch.stroopoverload.ui.screen.GameScreen
import com.softyorch.stroopoverload.ui.screen.HomeScreen
import com.softyorch.stroopoverload.ui.screen.LeaderboardScreen
import kotlinx.coroutines.launch

private const val ROUTE_HOME = "home"
private const val ROUTE_GAME = "game"
private const val ROUTE_GAME_OVER = "game_over"
private const val ROUTE_LEADERBOARD = "leaderboard"

@Composable
fun StroopNavGraph() {
    val navController = rememberNavController()
    val auth = remember { AuthService() }
    val repo = remember { FirebaseGameRepository() }
    val scope = rememberCoroutineScope()

    var uid by remember { mutableStateOf<String?>(null) }
    var previousHighScore by remember { mutableIntStateOf(0) }
    var lastResult by remember { mutableStateOf<GameResult?>(null) }

    LaunchedEffect(Unit) {
        val id = auth.signInAnonymously()
        repo.createUser(id, "Guest_${id.take(4).uppercase()}")
        uid = id
    }

    NavHost(navController = navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            HomeScreen(
                isReady = uid != null,
                onPlay = { navController.navigate(ROUTE_GAME) },
                onLeaderboard = { navController.navigate(ROUTE_LEADERBOARD) },
            )
        }
        composable(ROUTE_GAME) {
            val gameVm: GameViewModel = viewModel()
            LaunchedEffect(Unit) { gameVm.startGame(previousHighScore) }
            GameScreen(
                viewModel = gameVm,
                onGameOver = { result ->
                    lastResult = result
                    scope.launch {
                        uid?.let { repo.saveGameResult(it, result) }
                    }
                    navController.navigate(ROUTE_GAME_OVER) {
                        popUpTo(ROUTE_HOME)
                    }
                },
            )
        }
        composable(ROUTE_GAME_OVER) {
            lastResult?.let { result ->
                GameOverScreen(
                    result = result,
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
    }
}
