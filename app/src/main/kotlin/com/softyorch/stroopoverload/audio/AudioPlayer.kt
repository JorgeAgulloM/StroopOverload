package com.softyorch.stroopoverload.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.softyorch.stroopoverload.core.StroopColor

class AudioPlayer(context: Context) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundIds: Map<StroopColor, Int> = buildMap {
        StroopColor.entries.forEach { color ->
            val resId = context.resources.getIdentifier(color.audioRes, "raw", context.packageName)
            if (resId != 0) put(color, pool.load(context, resId, 1))
        }
    }

    fun play(color: StroopColor) {
        soundIds[color]?.let { pool.play(it, 1f, 1f, 0, 0, 1f) }
    }

    fun release() = pool.release()
}
