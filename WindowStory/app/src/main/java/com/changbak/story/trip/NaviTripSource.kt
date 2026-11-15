package com.changbak.story.trip

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * 실제 내비게이션(NaviHelper SDK)에서 이동 정보를 받는다.
 * 경로 시작 → 이야기 길이 결정, 주행 정보 → 진행률과 남은 시간, 경유지·목적지 도착 → 쉬어 가는 장과 엔딩.
 */
class NaviTripSource(context: Context, private val listener: TripListener) {

    private val main = Handler(Looper.getMainLooper())
    private val navi = NaviHelper(context.applicationContext)
    private var connected = false
    private var totalSec = 0

    var active = false
        private set

    private val events = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = onMain {
            listener.onTripLog("내비 경로 시작: ${routeStartInfo.name}, ${routeStartInfo.duration}초, ${routeStartInfo.distance}m")
            begin(routeStartInfo.name, routeStartInfo.duration)
        }

        override fun onDrivingInfo(drivingInfo: DrivingInfo) = onMain {
            val dest = drivingInfo.destination
            // 앱을 켜기 전에 이미 안내 중이던 경로라면 여기서 이야기를 시작한다.
            if (!active && dest.duration > 0) begin(dest.name, dest.duration)
            if (!active || totalSec <= 0) return@onMain
            val fraction = (1f - dest.duration.toFloat() / totalSec).coerceIn(0f, 1f)
            listener.onProgress(fraction, dest.duration, dest.distance)
        }

        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) = onMain {
            if (active) listener.onWaypointArrived(waypointArrivedInfo.name)
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = onMain {
            if (!active) return@onMain
            active = false
            listener.onArrived(destinationArrivedInfo.name)
        }

        override fun onRouteCancelled() = onMain {
            if (!active) return@onMain
            active = false
            listener.onCancelled()
        }

        override fun onNavigationStatus(status: NavigationStatus) = onMain {
            listener.onTripLog("내비 상태: $status")
        }

        override fun onError(errorCode: NaviErrorCode) = onMain {
            listener.onTripLog("내비 오류: $errorCode")
        }
    }

    fun start() {
        try {
            navi.initialize(
                {
                    onMain {
                        connected = true
                        listener.onTripLog("내비 서비스 연결됨")
                        navi.addListener(events)
                        runCatching { navi.getRouteStateInfo() }
                    }
                },
                { error -> onMain { listener.onTripLog("내비 서비스 연결 실패: ${error.message}") } },
            )
        } catch (e: Exception) {
            listener.onTripLog("내비 초기화 실패: ${e.message}")
        }
    }

    fun release() {
        runCatching { if (connected) navi.removeListener(events) }
        runCatching { navi.release() }
    }

    private fun begin(destName: String, durationSec: Int) {
        totalSec = durationSec
        active = true
        listener.onTripStarted(destName, durationSec, fromDemo = false)
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
