package com.changbak.story.trip

import com.changbak.story.story.PlaceType

/** 이동 정보를 받는 쪽. 실제 내비와 시연용 경로가 같은 이벤트를 준다. 모두 메인 스레드에서 불린다. */
interface TripListener {
    fun onTripStarted(destName: String, totalSec: Int, fromDemo: Boolean)
    fun onProgress(fraction: Float, remainingSec: Int, remainingMeters: Int)
    fun onPlace(type: PlaceType, name: String)
    fun onWaypointArrived(name: String)
    fun onArrived(name: String)
    fun onCancelled()
    fun onTripLog(message: String)
}
