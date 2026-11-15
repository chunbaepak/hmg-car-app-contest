package com.changbak.story.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.Locale

/**
 * 아이의 말을 듣는다. Pleos SpeechToText SDK의 차량 내장 엔진(ON_DEVICE)을 쓴다.
 * 인식한 문장에서 "얼마나 남았어?", "찾았어!" 같은 말을 골라낸다.
 */
class VoiceInput(
    context: Context,
    private val log: (String) -> Unit,
    private val onIntent: (Intent, String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit,
) {
    enum class Intent { ASK_REMAINING, QUEST_FOUND, UNKNOWN }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var stt: SpeechToText? = null
    private var ready = false
    private var listening = false

    private val stopLater = Runnable { stop() }

    private val results = object : ResultListener {
        override fun onReady() {}
        override fun onStartedRecognition() {}
        override fun onUpdated(stt: String, completed: Boolean) {
            if (!completed) return
            main.post {
                log("인식: \"$stt\"")
                onIntent(classify(stt), stt)
                stop()
            }
        }

        override fun onUpdatedEpdData(on: Long, off: Long) {}
        override fun onUpdatedRms(rms: Double) {}
        override fun onEndedRecognition() { main.post { setListening(false) } }
        override fun onError(errorType: ErrorType) {
            main.post {
                log("음성 인식 오류: $errorType")
                setListening(false)
            }
        }
    }

    val available get() = ready

    fun start() {
        try {
            val engine = SpeechToText(appContext, Mode.ON_DEVICE)
            stt = engine
            engine.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        runCatching {
                            engine.addListener(results)
                            engine.setLocale(Locale.KOREA)
                        }
                        ready = true
                        log("음성 인식 준비됨 (차량 내장)")
                    }
                }

                override fun onFailure(errorType: ErrorType) {
                    main.post { log("음성 인식 초기화 실패: $errorType") }
                }
            })
        } catch (e: Throwable) {
            log("음성 인식을 쓸 수 없음: ${e.message}")
        }
    }

    /** 마이크를 켜고 한 문장을 듣는다. */
    fun listen() {
        if (!ready) {
            log("음성 인식이 준비되지 않았어요")
            return
        }
        if (listening) return
        runCatching { stt?.request() }
            .onSuccess {
                setListening(true)
                main.postDelayed(stopLater, LISTEN_MS)
            }
            .onFailure { log("음성 인식 시작 실패: ${it.message}") }
    }

    fun stop() {
        main.removeCallbacks(stopLater)
        if (!listening) return
        runCatching { stt?.stop() }
        setListening(false)
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        runCatching {
            stt?.removeListener(results)
            stt?.release()
        }
    }

    private fun setListening(value: Boolean) {
        if (listening == value) return
        listening = value
        onListeningChanged(value)
    }

    companion object {
        private const val LISTEN_MS = 7_000L

        fun classify(text: String): Intent {
            val t = text.replace(" ", "")
            return when {
                listOf("얼마나", "언제", "남았", "도착", "멀었").any { it in t } -> Intent.ASK_REMAINING
                listOf("찾았", "봤어", "보여", "있어").any { it in t } -> Intent.QUEST_FOUND
                else -> Intent.UNKNOWN
            }
        }
    }
}
