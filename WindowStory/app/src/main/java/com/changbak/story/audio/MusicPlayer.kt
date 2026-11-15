package com.changbak.story.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.changbak.story.R
import com.changbak.story.story.Effect
import com.changbak.story.story.MusicState

/** 테마곡 반복 재생과 효과음. 낭독 중에는 테마곡 볼륨을 낮춘다. */
class MusicPlayer(context: Context, private val log: (String) -> Unit) {

    private val appContext = context.applicationContext
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private var theme: MediaPlayer? = null
    private var themeRes = 0
    private var state = MusicState.STOPPED
    private var ducked = false
    private val effects = mutableListOf<MediaPlayer>()

    /** 재생할 테마곡을 바꾼다. 재생 중이면 새 곡으로 이어서 튼다. */
    fun setTrack(resId: Int) {
        if (resId == themeRes) return
        themeRes = resId
        if (state == MusicState.STOPPED) return
        releaseTheme()
        ensureTheme()
        if (state == MusicState.PLAYING) theme?.start()
    }

    fun setState(next: MusicState) {
        state = next
        when (next) {
            MusicState.PLAYING -> {
                ensureTheme()
                runCatching { theme?.takeIf { !it.isPlaying }?.start() }
            }
            MusicState.PAUSED -> runCatching { theme?.takeIf { it.isPlaying }?.pause() }
            MusicState.STOPPED -> releaseTheme()
        }
    }

    fun setDucked(value: Boolean) {
        ducked = value
        applyVolume()
    }

    fun playEffect(effect: Effect) {
        val res = when (effect) {
            Effect.CHIME -> R.raw.sfx_chime
            Effect.WATER -> R.raw.sfx_water
            Effect.TUNNEL -> R.raw.sfx_tunnel
            Effect.SEA -> R.raw.sfx_sea
            Effect.FANFARE -> R.raw.sfx_fanfare
        }
        runCatching {
            val player = MediaPlayer.create(appContext, res, attributes, 0) ?: return
            player.setVolume(EFFECT_VOLUME, EFFECT_VOLUME)
            player.setOnCompletionListener {
                effects.remove(it)
                it.release()
            }
            effects.add(player)
            player.start()
        }.onFailure { log("효과음 재생 실패: ${it.message}") }
    }

    fun release() {
        releaseTheme()
        effects.forEach { runCatching { it.release() } }
        effects.clear()
    }

    private fun ensureTheme() {
        if (theme != null || themeRes == 0) return
        runCatching {
            theme = MediaPlayer.create(appContext, themeRes, attributes, 0)?.apply { isLooping = true }
            log("테마곡 준비: 길이 ${(theme?.duration ?: -1) / 1000}초")
            applyVolume()
        }.onFailure { log("테마곡 준비 실패: ${it.message}") }
    }

    private fun releaseTheme() {
        runCatching { theme?.release() }
        theme = null
    }

    private fun applyVolume() {
        val volume = if (ducked) DUCKED_VOLUME else NORMAL_VOLUME
        runCatching { theme?.setVolume(volume, volume) }
    }

    private companion object {
        const val NORMAL_VOLUME = 0.55f
        const val DUCKED_VOLUME = 0.14f
        const val EFFECT_VOLUME = 0.5f
    }
}
