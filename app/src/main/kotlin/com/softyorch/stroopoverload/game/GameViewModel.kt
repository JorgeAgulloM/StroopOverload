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

    // Kept here rather than in the game screen: an Activity recreated during the final-board
    // hold re-opens the game-over screen and needs what the recording already produced.
    private val _recordedRun = MutableStateFlow<RecordedRun?>(null)
    val recordedRun: StateFlow<RecordedRun?> = _recordedRun.asStateFlow()

    private val _timerProgress = MutableStateFlow(1f)
    val timerProgress: StateFlow<Float> = _timerProgress.asStateFlow()

    private var timerJob: Job? = null
    private var sessionTimerJob: Job? = null
    private var previousHighScore = 0
    private var pendingMode = GameMode.ENDLESS
    private var gameOverClaimed = false
    private var runStartMs = 0L
    private var frozenMs = 0L
    // When a session-clock mode's clock runs out; OVERTIME moves it with every answer.
    private var sessionDeadlineMs = 0L

    /**
     * Starts a run from the menu. Ignored once a run has started: the game screen calls this
     * from an effect that runs again whenever the Activity is recreated (theme change, split
     * screen) while this ViewModel survives, and that restarted the run in progress.
     */
    fun startGame(mode: GameMode = GameMode.ENDLESS, previousHigh: Int = 0) {
        if (_state.value != GameState.Menu) return
        gameOverClaimed = false
        _recordedRun.value = null
        pendingMode = mode
        previousHighScore = previousHigh
        _state.value = GameState.Countdown
    }

    fun beginRound() {
        if (_state.value !is GameState.Countdown) return
        val mode = pendingMode
        val now = clock()
        runStartMs = now
        frozenMs = 0L
        sessionDeadlineMs = now + sessionStartMs(mode)
        _state.value = GameState.Playing(
            mode = mode,
            livesRemaining = if (mode == GameMode.LIVES) GameConfig.LIVES_MODE_STARTING_LIVES else 0,
            timeRemainingMs = sessionStartMs(mode),
        )
        if (mode.hasSessionClock) startSessionTimer(mode)
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
        if (playing.mode == GameMode.OVERTIME) sessionDeadlineMs += overtimeBonusMs(playing.level)
        _state.value = playing.copy(
            score = newScore,
            level = newLevel,
            correctHits = newHits,
            totalRounds = newRounds,
            currentStreak = newStreak,
            bestStreak = maxOf(playing.bestStreak, newStreak),
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

            GameMode.TIME, GameMode.OVERTIME -> {
                if (missed.mode == GameMode.OVERTIME) sessionDeadlineMs -= GameConfig.OVERTIME_MISS_PENALTY_MS
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
        // Tier 2 adds the spoken distractor colour. Tier 3's background distractor is never
        // drawn anywhere, so it isn't requested.
        val difficultyTier = if (playing.level >= GameConfig.AUDIO_DISTRACTOR_MIN_LEVEL) 2 else 1
        _stimulus.value = engine.generate(difficultyTier)
        if (!playing.mode.hasSessionClock) {
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

    /** How much clock a session-clock [mode] starts with; 0 for the per-stimulus modes. */
    private fun sessionStartMs(mode: GameMode): Long = when (mode) {
        GameMode.TIME -> GameConfig.TIME_MODE_DURATION_MS
        GameMode.OVERTIME -> GameConfig.OVERTIME_START_MS
        GameMode.ENDLESS, GameMode.LIVES -> 0L
    }

    private fun startSessionTimer(mode: GameMode) {
        // The bar shows the starting clock as full; OVERTIME time banked above it keeps it full.
        val fullBarMs = sessionStartMs(mode)

        sessionTimerJob = viewModelScope.launch {
            while (true) {
                delay(16L)
                val remaining = (sessionDeadlineMs - clock()).coerceAtLeast(0L)
                _timerProgress.value = (remaining.toFloat() / fullBarMs).coerceIn(0f, 1f)

                val currentPlaying = _state.value as? GameState.Playing
                if (currentPlaying != null) {
                    _state.value = currentPlaying.copy(survivalMs = playedMs(mode), timeRemainingMs = remaining)
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
     * misses; TIME's is its fixed minute (its last frame lands a little after it). Counting a
     * fixed 16 ms per timer frame undercounted, since real frames run longer -- a 60 s TIME
     * run came out as ~57 s. OVERTIME is left uncapped: misses can move its deadline behind
     * the run's start, which would cap it below zero.
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
            bestStreak = playing.bestStreak,
            finalBoard = playing,
        )
    }

    fun onRunRecorded(run: RecordedRun) {
        _recordedRun.value = run
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
