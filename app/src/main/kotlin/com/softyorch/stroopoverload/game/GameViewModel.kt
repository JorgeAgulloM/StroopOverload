package com.softyorch.stroopoverload.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softyorch.stroopoverload.core.GameConfig
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.domain.GameResult
import com.softyorch.stroopoverload.domain.StroopStimulus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GameViewModel(
    private val engine: IncongruenceEngine = IncongruenceEngine(),
) : ViewModel() {

    private val _state = MutableStateFlow<GameState>(GameState.Menu)
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _stimulus = MutableStateFlow<StroopStimulus?>(null)
    val stimulus: StateFlow<StroopStimulus?> = _stimulus.asStateFlow()

    private val _timerProgress = MutableStateFlow(1f)
    val timerProgress: StateFlow<Float> = _timerProgress.asStateFlow()

    private var timerJob: Job? = null
    private var previousHighScore = 0

    fun startGame(previousHigh: Int = 0) {
        previousHighScore = previousHigh
        _state.value = GameState.Playing()
        nextStimulus()
    }

    fun onColorTapped(tapped: StroopColor) {
        val playing = _state.value as? GameState.Playing ?: return
        val current = _stimulus.value ?: return

        timerJob?.cancel()

        if (tapped == current.correctAnswer) {
            val newHits = playing.correctHits + 1
            val newRounds = playing.totalRounds + 1
            val newScore = playing.score + GameConfig.POINTS_PER_CORRECT
            val newLevel = (newRounds / GameConfig.LEVELS_PER_DIFFICULTY) + 1
            _state.value = playing.copy(
                score = newScore,
                level = newLevel,
                correctHits = newHits,
                totalRounds = newRounds,
            )
            nextStimulus()
        } else {
            endGame(playing)
        }
    }

    private fun nextStimulus() {
        val playing = _state.value as? GameState.Playing ?: return
        val difficultyTier = when {
            playing.level >= 10 -> 3
            playing.level >= 5 -> 2
            else -> 1
        }
        _stimulus.value = engine.generate(difficultyTier)
        _timerProgress.value = 1f
        startTimer(playing)
    }

    private fun startTimer(playing: GameState.Playing) {
        val limitMs = timeLimitMs(playing.level)
        val startMs = System.currentTimeMillis()

        timerJob = viewModelScope.launch {
            while (true) {
                delay(16L)
                val elapsed = System.currentTimeMillis() - startMs
                val progress = 1f - (elapsed.toFloat() / limitMs)
                _timerProgress.value = progress.coerceIn(0f, 1f)

                val currentPlaying = _state.value as? GameState.Playing
                if (currentPlaying != null) {
                    _state.value = currentPlaying.copy(survivalMs = currentPlaying.survivalMs + 16L)
                }

                if (elapsed >= limitMs) {
                    endGame(_state.value as? GameState.Playing ?: playing)
                    break
                }
            }
        }
    }

    private fun endGame(playing: GameState.Playing) {
        timerJob?.cancel()
        _state.value = GameState.GameOver(
            GameResult(
                finalScore = playing.score,
                correctHits = playing.correctHits,
                totalRounds = playing.totalRounds,
                survivalMs = playing.survivalMs,
                previousHighScore = previousHighScore,
            )
        )
    }

    fun returnToMenu() {
        timerJob?.cancel()
        _state.value = GameState.Menu
        _stimulus.value = null
        _timerProgress.value = 1f
    }

    fun timeLimitMs(level: Int): Long {
        val t = GameConfig.INITIAL_TIME_LIMIT_MS - (level - 1) * GameConfig.TIME_LIMIT_DECAY_MS
        return t.coerceAtLeast(GameConfig.MINIMUM_TIME_LIMIT_MS)
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}
