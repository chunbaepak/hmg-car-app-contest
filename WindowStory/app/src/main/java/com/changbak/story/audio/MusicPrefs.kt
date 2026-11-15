package com.changbak.story.audio

import android.content.Context
import com.changbak.story.R

/** 부모가 고르는 아이의 음악 취향. 분위기에 따라 재생할 테마곡이 정해진다. */
class MusicPrefs(context: Context) {

    enum class Mood(val label: String, val prompt: String, val trackRes: Int) {
        BRIGHT("신나는", "bright and cheerful", R.raw.theme_bright),
        CALM("잔잔한", "calm and gentle, lullaby-like", R.raw.theme_calm),
        ADVENTURE("모험 가득한", "adventurous and playful, marching feel", R.raw.theme_adventure),
    }

    enum class Tempo(val label: String, val prompt: String) {
        SLOW("느리게", "slow tempo around 72 bpm"),
        NORMAL("보통", "medium tempo around 100 bpm"),
        FAST("빠르게", "fast tempo around 126 bpm"),
    }

    enum class Instrument(val label: String, val prompt: String) {
        XYLOPHONE("실로폰", "xylophone lead"),
        PIANO("피아노", "soft piano lead"),
        MUSIC_BOX("오르골", "music box lead"),
        GUITAR("기타", "acoustic guitar lead"),
    }

    private val prefs = context.getSharedPreferences("music_prefs", Context.MODE_PRIVATE)

    var mood: Mood
        get() = Mood.entries.getOrElse(prefs.getInt("mood", 0)) { Mood.BRIGHT }
        set(value) = prefs.edit().putInt("mood", value.ordinal).apply()

    var tempo: Tempo
        get() = Tempo.entries.getOrElse(prefs.getInt("tempo", 1)) { Tempo.NORMAL }
        set(value) = prefs.edit().putInt("tempo", value.ordinal).apply()

    var instrument: Instrument
        get() = Instrument.entries.getOrElse(prefs.getInt("instrument", 0)) { Instrument.XYLOPHONE }
        set(value) = prefs.edit().putInt("instrument", value.ordinal).apply()

    /**
     * Suno에 넣을 생성 요청문. 가수나 곡 이름을 쓰지 않고 음악적 특징으로만 만든다.
     * 지금은 이 요청문으로 미리 만든 곡을 앱에 담아 쓰고, Suno 개발자 API가 열리면 자동 생성에 쓴다.
     */
    fun sunoPrompt() =
        "children's instrumental theme for a road-trip story audio app, ${mood.prompt}, " +
            "${instrument.prompt}, ${tempo.prompt}, simple memorable melody, loopable, no vocals"
}
