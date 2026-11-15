package com.changbak.story.trip

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.changbak.story.story.PlaceType

/**
 * 시연용 경로 재생. 에뮬레이터에서 실제 주행을 할 수 없을 때, 정해 둔 경로를 빠르게 재생해
 * 내비게이션과 같은 이벤트(출발, 진행률, 장소, 경유지, 도착)를 만든다.
 *
 * 경로는 기획서의 시나리오와 같다: 서울 → 강릉 경포해수욕장, 평창휴게소 경유, 약 2시간 20분.
 */
class DemoTripSource(private val listener: TripListener) {

    /** 실제 시간 몇 초 동안 전체 경로를 재생할지. */
    enum class Speed(val label: String, val realSeconds: Int) {
        SLOW("천천히 (4분)", 240),
        NORMAL("보통 (2분)", 120),
        FAST("빠르게 (1분)", 60),
    }

    /** 경로 위의 이벤트 하나. place가 null이면 경유지(휴게소)다. */
    class Stop(val at: Float, val place: PlaceType?, val name: String)

    private val handler = Handler(Looper.getMainLooper())
    private var marks = ArrayDeque<Stop>()
    private var startedAt = 0L
    private var realMs = 0L

    /** 휴게소에서 멈춰 있던 시간의 합. 진행률을 계산할 때 뺀다. */
    private var heldMs = 0L
    private var holdUntil = 0L
    private var lastTick = 0L

    var speed = Speed.SLOW
    var running = false
        private set

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.elapsedRealtime()
            if (now < holdUntil) {
                // 휴게소에서 쉬는 중: 차가 서 있으므로 진행률이 오르지 않는다.
                heldMs += now - lastTick
                lastTick = now
                handler.postDelayed(this, TICK_MS)
                return
            }
            lastTick = now
            val fraction = ((now - startedAt - heldMs).toFloat() / realMs).coerceIn(0f, 1f)
            while (marks.isNotEmpty() && marks.first().at <= fraction) {
                val stop = marks.removeFirst()
                if (stop.place != null) {
                    listener.onPlace(stop.place, stop.name)
                } else {
                    listener.onWaypointArrived(stop.name)
                    holdUntil = now + REST_HOLD_MS
                }
            }
            val remainingSec = ((1f - fraction) * TOTAL_SEC).toInt()
            val remainingMeters = ((1f - fraction) * TOTAL_METERS).toInt()
            listener.onProgress(fraction, remainingSec, remainingMeters)
            if (fraction >= 1f) {
                running = false
                listener.onArrived(DESTINATION)
            } else {
                handler.postDelayed(this, TICK_MS)
            }
        }
    }

    fun start() {
        stopInternal()
        marks = ArrayDeque(STOPS)
        realMs = speed.realSeconds * 1000L
        startedAt = SystemClock.elapsedRealtime()
        lastTick = startedAt
        heldMs = 0L
        holdUntil = 0L
        running = true
        listener.onTripLog("시연 경로 시작: 서울 → $DESTINATION, ${speed.label}")
        listener.onTripStarted(DESTINATION, TOTAL_SEC, speed.realSeconds, fromDemo = true)
        handler.postDelayed(tick, TICK_MS)
    }

    fun cancel() {
        if (!running) return
        stopInternal()
        listener.onCancelled()
    }

    fun release() = stopInternal()

    private fun stopInternal() {
        running = false
        handler.removeCallbacks(tick)
    }

    companion object {
        const val DESTINATION = "강릉 경포해수욕장"

        /** 시연 경로에서 미리 정해 둔 이벤트. 진행선에 그림으로 표시된다. */
        val STOPS = listOf(
            Stop(0.10f, PlaceType.RIVER, "한강"),
            Stop(0.47f, null, "평창휴게소"),
            Stop(0.70f, PlaceType.TUNNEL, "대관령 터널"),
            Stop(0.90f, PlaceType.SEA, "동해 바다"),
        )
        private const val TOTAL_SEC = 2 * 3600 + 20 * 60   // 2시간 20분
        private const val TOTAL_METERS = 215_000
        private const val TICK_MS = 250L

        /** 휴게소에서 쉬는 시간(실제 시간). 쉬어 가는 장면을 읽을 만큼 멈춘다. */
        private const val REST_HOLD_MS = 20_000L
    }
}
