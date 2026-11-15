package com.changbak.story.trip

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.changbak.story.story.PlaceType

/**
 * 실제 주행에서 이야기 장소에 들어서는 순간을 잡는다.
 * FusedLocation SDK의 근접 알림에 장소 좌표를 등록해 두고, 반경 안에 들어오면 알려 준다.
 * (시연용 경로에서는 DemoTripSource가 같은 이벤트를 대신 만든다.)
 */
class PlaceAlerts(
    context: Context,
    private val onPlace: (PlaceType, String) -> Unit,
    private val log: (String) -> Unit,
) {
    private class Spot(val type: PlaceType, val name: String, val lat: Double, val lng: Double)

    // 서울 → 강릉 경로의 대략적인 좌표. 이후에는 공개 장소 API로 경로 주변 장소를 받아 채운다.
    private val spots = listOf(
        Spot(PlaceType.RIVER, "한강", 37.5290, 127.0570),
        Spot(PlaceType.TUNNEL, "대관령 터널", 37.6835, 128.7560),
        Spot(PlaceType.SEA, "동해 바다", 37.8056, 128.9080),
    )

    private val main = Handler(Looper.getMainLooper())
    private val fused = FusedLocation(context.applicationContext)
    private val alertIds = mutableListOf<String>()
    private var started = false

    fun start() {
        if (started) return
        try {
            fused.initialize()
            started = true
            if (!fused.isSupported()) {
                log("이 차량은 위치 근접 알림을 지원하지 않음")
                return
            }
            for (spot in spots) {
                val request = ProximityAlertRequest(spot.lat, spot.lng, RADIUS_M, setOf(ProximityTransition.ENTER))
                fused.addProximityAlert(request, object : ProximityAlertListener {
                    override fun onRegistered(alertId: String) {
                        main.post { alertIds.add(alertId) }
                    }

                    override fun onTransition(event: ProximityAlertEvent) {
                        if (event.transition == ProximityTransition.ENTER) main.post { onPlace(spot.type, spot.name) }
                    }

                    override fun onError(exception: Exception) {
                        main.post { log("${spot.name} 근접 알림 오류: ${exception.message}") }
                    }
                })
            }
            log("장소 근접 알림 ${spots.size}곳 등록 요청")
        } catch (e: Throwable) {
            log("위치 SDK를 쓸 수 없음: ${e.message}")
        }
    }

    fun release() {
        if (!started) return
        runCatching { alertIds.forEach { fused.removeProximityAlert(it) } }
        runCatching { fused.release() }
    }

    private companion object {
        const val RADIUS_M = 1_500f
    }
}
