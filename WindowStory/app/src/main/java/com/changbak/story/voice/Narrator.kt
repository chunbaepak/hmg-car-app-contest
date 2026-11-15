package com.changbak.story.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import android.speech.tts.TextToSpeech as AndroidTts

/**
 * 이야기 낭독. Pleos TextToSpeech SDK의 차량 내장 엔진(ON_DEVICE)을 쓴다.
 * ON_DEVICE 모드는 네트워크와 앱 등록 없이 동작한다.
 * 내장 엔진을 쓸 수 없는 환경에서는 Android 기본 음성 합성, 그것도 없으면 자막만 넘긴다.
 */
class Narrator(context: Context, private val log: (String) -> Unit) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private var pleos: TextToSpeech? = null
    private var pleosReady = false
    private var androidTts: AndroidTts? = null
    private var androidReady = false

    /** 지금 낭독 중인 발화 번호. 끊거나 새로 말할 때마다 올린다. */
    private var token = 0
    private var pendingDone: (() -> Unit)? = null
    private var pleosStarted = false

    /** 낭독 시작과 끝을 알려 준다(음악 볼륨을 낮추는 데 쓴다). */
    var onSpeakingChanged: ((Boolean) -> Unit)? = null
    var engineName = "준비 중"
        private set

    private val pleosEvents = object : EventListener {
        override fun onStart() { main.post { pleosStarted = true } }
        override fun onReady() {}
        override fun onDone() {
            val my = token
            main.post { if (pleosStarted) finish(my) }
        }

        override fun onError(errorType: ErrorType) {
            val my = token
            main.post {
                log("Pleos TTS 오류: $errorType → 대체 음성으로 전환")
                pleosReady = false
                startAndroidTts()
                // 이번 문장은 자막 읽는 시간만큼 기다린 뒤 넘어간다.
                main.postDelayed({ finish(my) }, SILENT_BASE_MS)
            }
        }

        override fun onStop() {}
        override fun onUpdatedRms(rms: Double) {}
    }

    fun start() {
        try {
            val tts = TextToSpeech(appContext, Mode.ON_DEVICE)
            pleos = tts
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        runCatching {
                            tts.addEventListener(pleosEvents)
                            tts.setLocale(Locale.KOREA)  // 지역까지 넣어야 한다. "ko"만 주면 내장 엔진이 거부한다.
                        }
                        pleosReady = true
                        engineName = "Pleos TTS (차량 내장)"
                        log("낭독 엔진: $engineName")
                    }
                }

                override fun onFailure(errorType: ErrorType) {
                    main.post {
                        log("Pleos TTS 초기화 실패: $errorType")
                        startAndroidTts()
                    }
                }
            })
        } catch (e: Throwable) {
            log("Pleos TTS를 쓸 수 없음: ${e.message}")
            startAndroidTts()
        }
    }

    fun speak(text: String, onDone: () -> Unit) {
        cancelCurrent()
        val my = ++token
        pendingDone = onDone
        pleosStarted = false
        onSpeakingChanged?.invoke(true)
        when {
            pleosReady -> {
                runCatching { pleos?.speak(text, true) }
                    .onFailure { log("Pleos TTS speak 실패: ${it.message}") }
                main.postDelayed({ finish(my) }, text.length * WATCHDOG_PER_CHAR_MS + WATCHDOG_BASE_MS)
            }
            androidReady -> {
                androidTts?.speak(text, AndroidTts.QUEUE_FLUSH, null, "u$my")
                main.postDelayed({ finish(my) }, text.length * WATCHDOG_PER_CHAR_MS + WATCHDOG_BASE_MS)
            }
            else -> {
                // 음성 엔진이 없으면 자막을 읽을 시간만큼 기다린다.
                main.postDelayed({ finish(my) }, text.length * SILENT_PER_CHAR_MS + SILENT_BASE_MS)
            }
        }
    }

    fun stop() {
        val wasSpeaking = pendingDone != null
        cancelCurrent()
        runCatching { if (pleosReady) pleos?.stop() }
        runCatching { if (androidReady) androidTts?.stop() }
        if (wasSpeaking) onSpeakingChanged?.invoke(false)
    }

    fun release() {
        cancelCurrent()
        main.removeCallbacksAndMessages(null)
        runCatching {
            pleos?.removeEventListener(pleosEvents)
            pleos?.release()
        }
        runCatching { androidTts?.shutdown() }
    }

    private fun cancelCurrent() {
        token++
        pendingDone = null
    }

    private fun finish(my: Int) {
        if (my != token) return
        val done = pendingDone ?: return
        pendingDone = null
        token++
        onSpeakingChanged?.invoke(false)
        done()
    }

    private fun startAndroidTts() {
        if (androidTts != null) return
        androidTts = AndroidTts(appContext) { status ->
            main.post {
                if (status == AndroidTts.SUCCESS) {
                    androidTts?.language = Locale.KOREA
                    androidTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) = finishById(utteranceId)

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) = finishById(utteranceId)
                    })
                    androidReady = true
                    engineName = "Android 기본 음성"
                } else {
                    engineName = "음성 없음 (자막만)"
                }
                log("낭독 엔진: $engineName")
            }
        }
    }

    private fun finishById(utteranceId: String?) {
        val my = utteranceId?.removePrefix("u")?.toIntOrNull() ?: return
        main.post { finish(my) }
    }

    private companion object {
        const val WATCHDOG_PER_CHAR_MS = 280L
        const val WATCHDOG_BASE_MS = 5_000L
        const val SILENT_PER_CHAR_MS = 85L
        const val SILENT_BASE_MS = 1_200L
    }
}
