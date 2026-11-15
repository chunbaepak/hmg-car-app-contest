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
 *
 * 주행 중에는 화면을 누르지 않고 목소리로만 답하므로, 이야기가 진행되는 동안
 * 낭독이 쉬는 틈마다 스스로 듣는다(setHandsFree). 낭독 중에는 낭독 소리를 아이의 말로
 * 잘못 듣지 않도록 듣기를 멈춘다.
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

    /** 낭독이 쉬는 동안 스스로 들어야 하는가. */
    private var handsFree = false

    /** 녹음 파일을 넣어 시험하는 중인가. 이때는 마이크 듣기를 다시 시작하지 않는다. */
    private var feeding = false
    private var errors = 0

    private val restart = Runnable { startListening() }
    private val endFeed = Runnable { finishFeed() }

    private val results = object : ResultListener {
        override fun onReady() {}
        override fun onStartedRecognition() {}
        override fun onUpdated(stt: String, completed: Boolean) {
            if (!completed) return
            main.post {
                errors = 0
                if (stt.isNotBlank()) {
                    log("인식: \"$stt\"")
                    onIntent(classify(stt), stt)
                }
                if (feeding) finishFeed() else restartSoon(RESTART_MS)
            }
        }

        override fun onUpdatedEpdData(on: Long, off: Long) {}
        override fun onUpdatedRms(rms: Double) {}
        override fun onEndedRecognition() {
            main.post {
                setListening(false)
                if (!feeding) restartSoon(RESTART_MS)
            }
        }

        override fun onError(errorType: ErrorType) {
            main.post {
                setListening(false)
                if (feeding) {
                    log("음성 인식 오류: $errorType")
                    finishFeed()
                    return@post
                }
                errors++
                if (errors >= MAX_ERRORS) {
                    // 마이크가 없는 환경(에뮬레이터 등)에서 끝없이 다시 시도하지 않는다.
                    if (errors == MAX_ERRORS) log("음성 인식 오류가 이어져 자동 듣기를 멈춤: $errorType")
                } else {
                    restartSoon(ERROR_RETRY_MS)
                }
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
                            engine.setLocale(Locale.KOREA)   // "ko"만 주면 내장 엔진이 거부한다
                        }
                        ready = true
                        log("음성 인식 준비됨 (차량 내장)")
                        if (handsFree) startListening()
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

    /**
     * true면 지금부터 스스로 듣고, false면 듣기를 멈춘다.
     * 이야기가 진행 중이고 낭독이 쉬고 있을 때만 true로 둔다.
     */
    fun setHandsFree(enabled: Boolean) {
        if (handsFree == enabled) return
        handsFree = enabled
        if (enabled) {
            if (errors >= MAX_ERRORS) errors = MAX_ERRORS - 1   // 새로 듣기 시작할 때 한 번은 다시 시도한다
            restartSoon(RESTART_MS)
        } else {
            main.removeCallbacks(restart)
            if (!feeding) stopListening()
        }
    }

    /**
     * 마이크 대신 녹음 파일(16kHz, 16bit, mono PCM)을 넣어 인식을 시험한다. 에뮬레이터 시연용이다.
     * 차량 내장 엔진(ON_DEVICE)에서만 쓸 수 있고, 실시간 속도(100ms마다 3,200바이트)로 보낸다.
     * @return 음성 인식을 쓸 수 없어 보내지 못했으면 false
     */
    fun recognizeAsset(assetName: String): Boolean {
        val engine = stt
        if (!ready || engine == null || feeding) return false
        val pcm = runCatching { appContext.assets.open("test_audio/$assetName").use { it.readBytes() } }
            .getOrElse {
                log("테스트 음성 파일을 열 수 없음: $assetName")
                return false
            }
        main.removeCallbacks(restart)
        stopListening()
        feeding = true
        setListening(true)
        log("테스트 음성 전송: $assetName")
        Thread {
            runCatching {
                var offset = 0
                while (offset < pcm.size) {
                    val end = minOf(offset + CHUNK_BYTES, pcm.size)
                    engine.sendAudio(pcm.copyOfRange(offset, end))
                    offset = end
                    Thread.sleep(CHUNK_MS)
                }
                engine.completeAudioSend()
            }.onFailure { main.post { log("테스트 음성 전송 실패: ${it.message}"); finishFeed() } }
        }.start()
        main.postDelayed(endFeed, FEED_TIMEOUT_MS + pcm.size / 32L)
        return true
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        runCatching {
            stt?.removeListener(results)
            stt?.release()
        }
    }

    private fun finishFeed() {
        main.removeCallbacks(endFeed)
        if (!feeding) return
        feeding = false
        setListening(false)
        restartSoon(RESTART_MS)
    }

    private fun restartSoon(delayMs: Long) {
        main.removeCallbacks(restart)
        if (handsFree && !feeding) main.postDelayed(restart, delayMs)
    }

    private fun startListening() {
        if (!ready || !handsFree || feeding || listening || errors >= MAX_ERRORS) return
        runCatching { stt?.request() }
            .onSuccess { setListening(true) }
            .onFailure { log("음성 인식 시작 실패: ${it.message}") }
    }

    private fun stopListening() {
        if (!listening) return
        runCatching { stt?.stop() }
        setListening(false)
    }

    private fun setListening(value: Boolean) {
        if (listening == value) return
        listening = value
        onListeningChanged(value)
    }

    companion object {
        private const val RESTART_MS = 500L
        private const val ERROR_RETRY_MS = 3_000L
        private const val MAX_ERRORS = 3
        private const val FEED_TIMEOUT_MS = 7_000L
        private const val CHUNK_BYTES = 3_200
        private const val CHUNK_MS = 100L

        /** 인식한 문장에서 뜻을 고른다. */
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
