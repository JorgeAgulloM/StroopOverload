package com.softyorch.stroopoverload.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.annotation.RawRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A single looping track, or a shuffled playlist (gameplay's 4 tracks). */
sealed interface MusicTrack {
    data class Loop(@RawRes val resId: Int) : MusicTrack
    data class Playlist(val resIds: List<Int>) : MusicTrack
}

private const val FADE_DURATION_MS = 2000L
private const val FADE_STEP_MS = 50L

// Tagged as game music so the system routes and ducks it like one. Without
// attributes MediaPlayer reports USAGE_UNKNOWN.
private val MUSIC_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_GAME)
    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
    .build()

/**
 * Long-form background music. [MusicTrack.Loop] repeats a single track
 * forever; [MusicTrack.Playlist] shuffles through its tracks with no
 * immediate repeat, chaining a new random pick each time one finishes.
 * Every start fades in, every stop fades out (2s each, per product ask), and
 * playback is fully gated by [AudioSettingsStore.musicEnabled] -- toggling it
 * off fades out whatever's playing; toggling back on resumes the last
 * requested track from a fresh fade-in.
 */
class MusicManager(context: Context) {
    private val appContext = context.applicationContext
    private val settings = AudioSettingsStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var player: MediaPlayer? = null
    private var requestedTrack: MusicTrack? = null
    private var lastPlayedResId: Int? = null
    private var transitionJob: Job? = null

    init {
        scope.launch {
            settings.musicEnabled.collect { enabled ->
                if (!enabled) fadeOutAndStop() else requestedTrack?.let { startTransition(it) }
            }
        }
    }

    /** No-op if [track] is already the requested one (same loop / same playlist). */
    fun setTrack(track: MusicTrack?) {
        if (track == requestedTrack) return
        requestedTrack = track
        if (settings.musicEnabled.value) startTransition(track)
    }

    fun release() {
        transitionJob?.cancel()
        player?.release()
        player = null
        scope.cancel()
    }

    /**
     * Hard-stop for when the app loses foreground (screen turned off, another
     * app/dialog takes over, recents). No fade -- the system may suspend
     * playback abruptly right after this anyway, so a 2s fade could get cut
     * off mid-ramp and never actually reach silence.
     */
    fun pause() {
        transitionJob?.cancel()
        player?.let { mp -> runCatching { mp.pause() } }
    }

    /** Resumes whatever was paused, from a fresh 2s fade-in. No-op if music is disabled or nothing was playing. */
    fun resume() {
        val mp = player ?: return
        if (!settings.musicEnabled.value) return
        transitionJob?.cancel()
        transitionJob = scope.launch {
            runCatching { mp.setVolume(0f, 0f) }
            runCatching { mp.start() }
            fadeVolume(mp, fadeIn = true)
        }
    }

    private fun startTransition(track: MusicTrack?) {
        transitionJob?.cancel()
        transitionJob = scope.launch {
            fadeOutAndRelease()
            if (track != null && requestedTrack == track) startTrack(track)
        }
    }

    private fun fadeOutAndStop() {
        transitionJob?.cancel()
        transitionJob = scope.launch { fadeOutAndRelease() }
    }

    private suspend fun startTrack(track: MusicTrack) {
        val resId = pickResId(track)
        val sessionId = appContext.getSystemService(AudioManager::class.java).generateAudioSessionId()
        val mp = MediaPlayer.create(appContext, resId, MUSIC_ATTRIBUTES, sessionId) ?: return
        lastPlayedResId = resId
        mp.isLooping = track is MusicTrack.Loop
        mp.setVolume(0f, 0f)
        if (track is MusicTrack.Playlist) {
            mp.setOnCompletionListener { onPlaylistTrackFinished(track) }
        }
        player = mp
        mp.start()
        fadeVolume(mp, fadeIn = true)
    }

    private fun pickResId(track: MusicTrack): Int = when (track) {
        is MusicTrack.Loop -> track.resId
        is MusicTrack.Playlist -> track.resIds
            .filter { it != lastPlayedResId }
            .ifEmpty { track.resIds }
            .random()
    }

    private fun onPlaylistTrackFinished(track: MusicTrack) {
        if (requestedTrack != track) return
        player?.release()
        player = null
        scope.launch { startTrack(track) }
    }

    private suspend fun fadeOutAndRelease() {
        val mp = player ?: return
        fadeVolume(mp, fadeIn = false)
        mp.release()
        player = null
    }

    private suspend fun fadeVolume(mp: MediaPlayer, fadeIn: Boolean) {
        val steps = (FADE_DURATION_MS / FADE_STEP_MS).toInt()
        for (step in 0..steps) {
            val fraction = step.toFloat() / steps
            val volume = if (fadeIn) fraction else 1f - fraction
            runCatching { mp.setVolume(volume, volume) }
            delay(FADE_STEP_MS)
        }
    }
}
