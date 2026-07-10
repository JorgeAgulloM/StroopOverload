package com.softyorch.stroopoverload.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.softyorch.stroopoverload.core.StroopColor

/**
 * [SoundPool.load] decodes asynchronously -- calling [SoundPool.play] for a
 * sample that hasn't finished decoding yet is a silent no-op (documented
 * Android behavior), which is what made these SFX feel laggy/intermittent
 * right after a fresh game start. [SoundPool.setOnLoadCompleteListener]
 * tracks which sample IDs are actually ready; a sample requested before its
 * load finishes is queued and fires exactly once as soon as it becomes
 * ready, instead of being dropped.
 */
class AudioPlayer(context: Context) {

    private val settings = AudioSettingsStore(context)

    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val loadedSampleIds = mutableSetOf<Int>()
    private val pendingOnLoad = mutableMapOf<Int, MutableList<() -> Unit>>()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSampleIds += sampleId
                pendingOnLoad.remove(sampleId)?.forEach { it() }
            }
        }
    }

    private val colorSampleIds: Map<StroopColor, Int> = buildMap {
        StroopColor.entries.forEach { color ->
            val resId = context.resources.getIdentifier(color.audioRes, "raw", context.packageName)
            if (resId != 0) put(color, pool.load(context, resId, 1))
        }
    }

    private val sfxSampleIds: Map<GameSfx, Int> = buildMap {
        GameSfx.entries.forEach { sfx ->
            val resId = context.resources.getIdentifier(sfx.rawName, "raw", context.packageName)
            if (resId != 0) put(sfx, pool.load(context, resId, 1))
        }
    }

    fun play(color: StroopColor) = playSample(colorSampleIds[color])

    fun play(sfx: GameSfx) = playSample(sfxSampleIds[sfx])

    private fun playSample(sampleId: Int?) {
        if (sampleId == null) return
        if (sampleId in loadedSampleIds) {
            if (settings.sfxEnabled.value) pool.play(sampleId, 1f, 1f, 0, 0, 1f)
        } else {
            pendingOnLoad.getOrPut(sampleId) { mutableListOf() }.add {
                if (settings.sfxEnabled.value) pool.play(sampleId, 1f, 1f, 0, 0, 1f)
            }
        }
    }

    fun release() = pool.release()
}
