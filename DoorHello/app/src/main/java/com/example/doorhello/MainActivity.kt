package com.example.doorhello

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Door
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView

/**
 * Vehicle SDK 첫 호출 예제: 운전석 문 위치를 조회하고, 변경 알림을 구독하고, 열기/닫기를 요청한다.
 * 흐름: Vehicle 생성 → initialize() → getDoor() → Capability/Get/Callback/Set → release()
 */
class MainActivity : Activity() {

    private lateinit var vehicle: Vehicle
    private lateinit var door: Door
    // Pleos 에뮬레이터는 문 위치 변경을 트렁크(REAR)에만 반영한다. 다른 문은 조회·알림만 된다.
    private val targetArea = DoorArea(DoorValue.REAR)
    private var openValue = 1

    private lateinit var stateText: TextView
    private lateinit var logText: TextView

    // 변경 알림 리스너: 해제할 때 같은 객체와 같은 area를 써야 한다.
    private val positionListener = object : DoorPositionListener {
        override fun onPositionUpdated(pos: Int?) {
            runOnUiThread {
                showPosition(pos)
                log("알림 수신: position=$pos")
            }
        }

        override fun onFailed(e: Exception) {
            runOnUiThread { log("알림 오류: ${e.message}") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        stateText = findViewById(R.id.doorState)
        logText = findViewById(R.id.log)

        // 1) SDK 초기화와 도메인 객체 획득
        vehicle = Vehicle(this)
        vehicle.initialize()
        door = vehicle.getDoor()

        // 2) 지원 범위 확인 → 3) 현재 값 조회 → 4) 변경 알림 구독
        door.checkDoorPositionCapabilities(
            { config, bounds ->
                Log.i(TAG, "capability access=${config.accessType}")
                bounds.forEach { (area, b) ->
                    Log.i(TAG, "capability area=${area.values.joinToString()} range=${b.minValue}..${b.maxValue}")
                }
                val range = bounds[targetArea]
                range?.maxValue?.let { openValue = it } // 트렁크는 0..100 (100 = 완전히 열림)
                runOnUiThread { log("지원 범위: ${range?.minValue}..${range?.maxValue}") }
                refresh()
                door.registerDoorPosition(targetArea, positionListener)
            },
            { e -> runOnUiThread { log("지원 확인 실패: ${e.message}") } },
        )

        // 5) 변경 요청 (요청 완료 ≠ 실제 반영. 실제 값은 알림이나 재조회로 확인)
        findViewById<Button>(R.id.openButton).setOnClickListener { requestPosition(openValue) }
        findViewById<Button>(R.id.closeButton).setOnClickListener { requestPosition(0) }
    }

    private fun refresh() {
        door.getCurrentDoorPosition(
            targetArea,
            { value -> runOnUiThread { showPosition(value); log("조회 결과: position=$value") } },
            { e -> runOnUiThread { stateText.text = "조회 실패"; log("조회 실패: ${e.message}") } },
        )
    }

    private fun requestPosition(target: Int) {
        door.setDoorPosition(
            targetArea,
            target,
            { _, requested -> runOnUiThread { log("변경 요청 완료: $requested") } },
            { e -> runOnUiThread { log("변경 요청 실패: ${e.message}") } },
        )
    }

    private fun showPosition(value: Int?) {
        stateText.text = when (value) {
            null -> "알 수 없음"
            0 -> "닫힘"
            openValue -> "열림"
            else -> "열리는 중 ($value)"
        }
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        logText.text = message
    }

    override fun onDestroy() {
        // 구독 해제 후 리소스 정리
        runCatching { door.unregisterDoorPosition(targetArea, positionListener) }
        vehicle.release()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "DoorHello"
    }
}
