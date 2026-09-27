package com.softyorch.stroopoverload.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softyorch.stroopoverload.core.GameConfig
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.domain.AiDifficulty
import com.softyorch.stroopoverload.domain.GameMode
import com.softyorch.stroopoverload.domain.GameResult
import com.softyorch.stroopoverload.domain.OpponentType
import com.softyorch.stroopoverload.domain.StroopStimulus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GameViewModel(
    private val engine: IncongruenceEngine = IncongruenceEngine(),
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) : ViewModel() {

    private val _state = MutableStateFlow<GameState>(GameState.Menu)
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _stimulus = MutableStateFlow<StroopStimulus?>(null)
    val stimulus: StateFlow<StroopStimulus?> = _stimulus.asStateFlow()

    private val _timerProgress = MutableStateFlow(1f)
    val timerProgress: StateFlow<Float> = _timerProgress.asStateFlow()

    private var timerJob: Job? = null
    private var sessionTimerJob: Job? = null
    private var previousHighScore = 0
    private var pendingMode = GameMode.ENDLESS
    private var gameOverClaimed = false
    private var runStartMs = 0L
    private var frozenMs = 0L

    /**
     * Starts a run from the menu. Ignored once a run has started: the game screen calls this
     * from an effect that runs again whenever the Activity is recreated (theme change, split
     * screen) while this ViewModel survives, and that restarted the run in progress.
     */
    fun startGame(mode: GameMode = GameMode.ENDLESS, previousHigh: Int = 0) {
        if (_state.value != GameState.Menu) return
        gameOverClaimed = false
        pendingMode = mode
        previousHighScore = previousHigh
        _state.value = GameState.Countdown
    }

    fun beginRound() {
        if (_state.value !is GameState.Countdown) return
        val mode = pendingMode
        runStartMs = clock()
        frozenMs = 0L
        _state.value = GameState.Playing(
            mode = mode,
            livesRemaining = if (mode == GameMode.LIVES) GameConfig.LIVES_MODE_STARTING_LIVES else 0,
            timeRemainingMs = if (mode == GameMode.TIME) GameConfig.TIME_MODE_DURATION_MS else 0L,
        )
        if (mode == GameMode.TIME) startSessionTimer()
        nextStimulus()
    }

    fun onColorTapped(tapped: StroopColor) {
        val playing = _state.value as? GameState.Playing ?: return
        if (playing.isFrozen) return
        val current = _stimulus.value ?: return

        timerJob?.cancel()

        if (tapped == current.correctAnswer) {
            handleCorrect(playing, tapped)
        } else {
            handleMiss(playing, current.correctAnswer, tapped)
        }
    }

    private fun handleCorrect(playing: GameState.Playing, tapped: StroopColor) {
        val newHits = playing.correctHits + 1
        val newRounds = playing.totalRounds + 1
        val newStreak = playing.currentStreak + 1
        val streakBonus = (newStreak * 10).coerceAtMost(100)
        val newScore = playing.score + GameConfig.POINTS_PER_CORRECT + streakBonus
        val newLevel = (newRounds / GameConfig.LEVELS_PER_DIFFICULTY) + 1
        _state.value = playing.copy(
            score = newScore,
            level = newLevel,
            correctHits = newHits,
            totalRounds = newRounds,
            currentStreak = newStreak,
            lastTap = TapFeedback(tapped, isCorrect = true, seq = newRounds),
        )
        nextStimulus()
    }

    // The miss counts as a played round (but not a correct hit) so that accuracy/won/isFlawless
    // reflect what actually happened, instead of only ever counting correct taps.
    // [tapped] is null when the stimulus timed out instead.
    private fun handleMiss(playing: GameState.Playing, correctColor: StroopColor, tapped: StroopColor? = null) {
        timerJob?.cancel()
        val newRounds = playing.totalRounds + 1
        val missed = playing.copy(
            totalRounds = newRounds,
            currentStreak = 0,
            lastTap = tapped?.let { TapFeedback(it, isCorrect = false, seq = newRounds) } ?: playing.lastTap,
        )

        when (missed.mode) {
            GameMode.ENDLESS -> endGame(missed.copy(missFlashColor = correctColor))

            GameMode.LIVES -> {
                val newLives = missed.livesRemaining - 1
                if (newLives <= 0) {
                    endGame(missed.copy(livesRemaining = 0, missFlashColor = correctColor))
                    return
                }
                _state.value = missed.copy(livesRemaining = newLives, missFlashColor = correctColor, isFrozen = true)
                val freezeStartMs = clock()
                viewModelScope.launch {
                    delay(GameConfig.LIVES_MODE_FREEZE_MS)
                    frozenMs += clock() - freezeStartMs
                    val frozen = _state.value as? GameState.Playing ?: return@launch
                    _state.value = frozen.copy(missFlashColor = null, isFrozen = false)
                    nextStimulus()
                }
            }

            GameMode.TIME -> {
                _state.value = missed.copy(missFlashColor = correctColor)
                viewModelScope.launch {
                    delay(GameConfig.TIME_MODE_FLASH_MS)
                    val current = _state.value as? GameState.Playing ?: return@launch
                    _state.value = current.copy(missFlashColor = null)
                }
                nextStimulus()
            }
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
        if (playing.mode != GameMode.TIME) {
            _timerProgress.value = 1f
            startPerStimulusTimer(playing)
        }
    }

    private fun startPerStimulusTimer(playing: GameState.Playing) {
        val limitMs = timeLimitMs(playing.level)
        val startMs = clock()

        timerJob = viewModelScope.launch {
            while (true) {
                delay(16L)
                val elapsed = clock() - startMs
                val progress = 1f - (elapsed.toFloat() / limitMs)
                _timerProgress.value = progress.coerceIn(0f, 1f)

                val currentPlaying = _state.value as? GameState.Playing
                if (currentPlaying != null && !currentPlaying.isFrozen) {
                    _state.value = currentPlaying.copy(survivalMs = playedMs(currentPlaying.mode))
                }

                if (elapsed >= limitMs) {
                    val timedOut = _state.value as? GameState.Playing ?: playing
                    val correctColor = _stimulus.value?.correctAnswer
                    if (correctColor != null) handleMiss(timedOut, correctColor) else endGame(timedOut)
                    break
                }
            }
        }
    }

    private fun startSessionTimer() {
        val limitMs = GameConfig.TIME_MODE_DURATION_MS
        val startMs = clock()

        sessionTimerJob = viewModelScope.launch {
            while (true) {
                delay(16L)
                val elapsed = clock() - startMs
                val remaining = (limitMs - elapsed).coerceAtLeast(0L)
                _timerProgress.value = (remaining.toFloat() / limitMs).coerceIn(0f, 1f)

                val currentPlaying = _state.value as? GameState.Playing
                if (currentPlaying != null) {
                    _state.value = currentPlaying.copy(survivalMs = playedMs(GameMode.TIME), timeRemainingMs = remaining)
                }

                if (remaining <= 0L) {
                    val timedOut = _state.value as? GameState.Playing
                    if (timedOut != null) endGame(timedOut)
                    break
                }
            }
        }
    }

    /**
     * True exactly once per finished run: whoever gets it records the run. The game-over
     * effect runs again when the Activity is recreated, and recording twice counted the run
     * twice.
     */
    fun claimGameOver(): Boolean {
        if (_state.value !is GameState.GameOver || gameOverClaimed) return false
        gameOverClaimed = true
        return true
    }

    /**
     * Time actually played: clock time since the run began, minus the freezes after LIVES
     * misses. Counting a fixed 16 ms per timer frame undercounted, since real frames run
     * longer -- a 60 s TIME run came out as ~57 s.
     */
    private fun playedMs(mode: GameMode): Long {
        val played = clock() - runStartMs - frozenMs
        return if (mode == GameMode.TIME) played.coerceAtMost(GameConfig.TIME_MODE_DURATION_MS) else played
    }

    private fun endGame(lastPlaying: GameState.Playing) {
        timerJob?.cancel()
        sessionTimerJob?.cancel()
        val playing = lastPlaying.copy(survivalMs = playedMs(lastPlaying.mode))
        val won = playing.score > 0 && (playing.totalRounds >= 5 && (playing.correctHits.toFloat() / playing.totalRounds) >= 0.7f)
        _state.value = GameState.GameOver(
            GameResult(
                finalScore = playing.score,
                correctHits = playing.correctHits,
                totalRounds = playing.totalRounds,
                survivalMs = playing.survivalMs,
                previousHighScore = previousHighScore,
                won = won,
                opponentType = OpponentType.SINGLE_PLAYER_CHALLENGE,
                aiDifficulty = AiDifficulty.OVERLOAD_CYBER,
                durationSeconds = (playing.survivalMs / 1000).toInt(),
                moveCount = playing.totalRounds,
                mode = playing.mode,
            ),
            endStreak = playing.currentStreak,
            finalBoard = playing,
        )
    }

    fun returnToMenu() {
        timerJob?.cancel()
        sessionTimerJob?.cancel()
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
        sessionTimerJob?.cancel()
        super.onCleared()
    }
}
