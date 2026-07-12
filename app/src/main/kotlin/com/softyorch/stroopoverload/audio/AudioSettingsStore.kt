package com.softyorch.stroopoverload.audio

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Device-local music/SFX preferences. Every instance listens to the same
 * underlying [SharedPreferences] file via [SharedPreferences.OnSharedPreferenceChangeListener],
 * so a toggle flipped from one screen (e.g. ProfileScreen) is observed by
 * every other instance (e.g. the [MusicManager] living in NavGraph) without
 * needing a single shared object threaded through the composable tree.
 */
class AudioSettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _musicEnabled = MutableStateFlow(prefs.getBoolean(KEY_MUSIC_ENABLED, true))
    val musicEnabled: StateFlow<Boolean> = _musicEnabled.asStateFlow()

    private val _sfxEnabled = MutableStateFlow(prefs.getBoolean(KEY_SFX_ENABLED, true))
    val sfxEnabled: StateFlow<Boolean> = _sfxEnabled.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
        when (key) {
            KEY_MUSIC_ENABLED -> _musicEnabled.value = sharedPrefs.getBoolean(KEY_MUSIC_ENABLED, true)
            KEY_SFX_ENABLED -> _sfxEnabled.value = sharedPrefs.getBoolean(KEY_SFX_ENABLED, true)
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun setMusicEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MUSIC_ENABLED, enabled).apply()
    }

    fun setSfxEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SFX_ENABLED, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "stroop_audio_settings"
        private const val KEY_MUSIC_ENABLED = "music_enabled"
        private const val KEY_SFX_ENABLED = "sfx_enabled"
    }
}
