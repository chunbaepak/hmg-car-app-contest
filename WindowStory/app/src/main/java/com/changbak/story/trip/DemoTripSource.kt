package com.changbak.story.trip

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.changbak.story.story.PlaceType

/**
 * 시연용 경로 재생. 에뮬레이터에서 실제 주행을 할 수 없을 때, 정해 둔 경로를 빠르게 재생해
 * 내비게이션과 같은 이벤트(출발, 진행률, 장소, 경유지, 도착)를 만든다.
 */
class DemoTripSource(private val listener: TripListener) {

    /** 실제 시간 몇 초 동안 전체 경로를 재생할지. */
    enum class Speed(val label: String, val realSeconds: Int) {
        SLOW("천천히 (4분)", 240),
        NORMAL("보통 (2분)", 120),
        FAST("빠르게 (1분)", 60),
    }

    private sealed class Mark(val at: Float) {
        class Place(at: Float, val type: PlaceType, val name: String) : Mark(at)
        class Waypoint(at: Float, val name: String) : Mark(at)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var marks = ArrayDeque<Mark>()
    private var startedAt = 0L
    private var realMs = 0L

    var speed = Speed.SLOW
    var running = false
        private set

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val fraction = ((SystemClock.elapsedRealtime() - startedAt).toFloat() / realMs).coerceIn(0f, 1f)
            while (marks.isNotEmpty() && marks.first().at <= fraction) {
                when (val mark = marks.removeFirst()) {
                    is Mark.Place -> listener.onPlace(mark.type, mark.name)
                    is Mark.Waypoint -> listener.onWaypointArrived(mark.name)
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
        marks = ArrayDeque(
            listOf(
                Mark.Place(0.12f, PlaceType.RIVER, "한강"),
                Mark.Place(0.38f, PlaceType.TUNNEL, "둔내터널"),
                Mark.Waypoint(0.56f, "횡성휴게소"),
                Mark.Place(0.86f, PlaceType.SEA, "동해 바다"),
            ),
        )
        realMs = speed.realSeconds * 1000L
        startedAt = SystemClock.elapsedRealtime()
        running = true
        listener.onTripLog("시연 경로 시작: 서울 → $DESTINATION, ${speed.label}")
        listener.onTripStarted(DESTINATION, TOTAL_SEC, fromDemo = true)
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
        const val DESTINATION = "강릉 경포해변"
        private const val TOTAL_SEC = 2 * 3600 + 30 * 60   // 2시간 30분
        private const val TOTAL_METERS = 215_000
        private const val TICK_MS = 250L
    }
}
