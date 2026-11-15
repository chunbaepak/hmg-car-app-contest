package com.changbak.story.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Vehicle SDK로 뒷좌석 탑승 여부, 안전벨트 착용 상태, 기어를 읽는다.
 * 조회와 변경 알림만 쓰고, 차량을 제어하지 않는다.
 */
class VehicleSignals(context: Context, private val listener: Listener) {

    interface Listener {
        fun onVehicleChanged(state: State)
        fun onVehicleLog(message: String)
    }

    /** 값이 null이면 아직 받지 못했거나 지원하지 않는 것이다. */
    data class State(
        val rearOccupied: Boolean?,
        val rearBuckled: Boolean?,
        val gear: VehicleGear?,
    ) {
        /** 뒷좌석에 사람이 앉아 있는데 벨트가 풀린 좌석이 있는가. */
        val unbuckledWhileSeated get() = rearOccupied == true && rearBuckled == false
        val parked get() = gear == VehicleGear.GEAR_PARK
    }

    private class SeatState(val value: SeatValue) {
        val area = SeatArea(value)
        var occupied: Boolean? = null
        var buckled: Boolean? = null
        lateinit var occupancyListener: SeatOccupancyListener
        lateinit var beltListener: SeatBeltBuckledStateListener
    }

    private val main = Handler(Looper.getMainLooper())
    private val vehicle = Vehicle(context.applicationContext)
    private val seats = listOf(SeatState(SeatValue.ROW_2_LEFT), SeatState(SeatValue.ROW_2_RIGHT))
    private var gear: VehicleGear? = null
    private var started = false

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = onMain {
            this@VehicleSignals.gear = gear
            publish()
        }

        override fun onFailed(e: Exception) = onMain { listener.onVehicleLog("기어 알림 오류: ${e.message}") }
    }

    fun start() {
        try {
            vehicle.initialize()
            started = true
        } catch (e: Exception) {
            listener.onVehicleLog("Vehicle SDK 초기화 실패: ${e.message}")
            return
        }
        val seatApi = vehicle.getSeat()
        for (seat in seats) {
            seat.occupancyListener = object : SeatOccupancyListener {
                override fun onSeatOccupancyUpdated(occupied: Boolean?) = onMain {
                    seat.occupied = occupied
                    publish()
                }

                override fun onFailed(e: Exception) = onMain {
                    listener.onVehicleLog("${seat.value} 탑승 알림 오류: ${e.message}")
                }
            }
            seat.beltListener = object : SeatBeltBuckledStateListener {
                override fun onSeatBeltBuckledStateUpdated(buckled: Boolean?) = onMain {
                    seat.buckled = buckled
                    publish()
                }

                override fun onFailed(e: Exception) = onMain {
                    listener.onVehicleLog("${seat.value} 벨트 알림 오류: ${e.message}")
                }
            }
            runCatching {
                seatApi.getCurrentSeatOccupancy(
                    seat.area,
                    { value -> onMain { seat.occupied = value; publish() } },
                    { e -> onMain { listener.onVehicleLog("${seat.value} 탑승 조회 실패: ${e.message}") } },
                )
                seatApi.getCurrentSeatBeltBuckledState(
                    seat.area,
                    { value -> onMain { seat.buckled = value; publish() } },
                    { e -> onMain { listener.onVehicleLog("${seat.value} 벨트 조회 실패: ${e.message}") } },
                )
                seatApi.registerSeatOccupancy(seat.area, seat.occupancyListener)
                seatApi.registerSeatBeltBuckledState(seat.area, seat.beltListener)
            }.onFailure { listener.onVehicleLog("${seat.value} 등록 실패: ${it.message}") }
        }
        runCatching {
            val gearApi = vehicle.getGear()
            gearApi.getCurrentGearSelectionState(
                { value -> onMain { gear = value; publish() } },
                { e -> onMain { listener.onVehicleLog("기어 조회 실패: ${e.message}") } },
            )
            gearApi.registerGearSelection(gearListener)
        }.onFailure { listener.onVehicleLog("기어 등록 실패: ${it.message}") }
    }

    fun release() {
        if (!started) return
        runCatching {
            val seatApi = vehicle.getSeat()
            for (seat in seats) {
                seatApi.unregisterSeatOccupancy(seat.area, seat.occupancyListener)
                seatApi.unregisterSeatBeltBuckledState(seat.area, seat.beltListener)
            }
            vehicle.getGear().unregisterGearSelection(gearListener)
        }
        runCatching { vehicle.release() }
    }

    private fun publish() {
        val occupiedSeats = seats.filter { it.occupied == true }
        val rearOccupied = when {
            occupiedSeats.isNotEmpty() -> true
            seats.all { it.occupied == false } -> false
            else -> null
        }
        // 사람이 앉은 좌석이 있으면 그 좌석들의 벨트만 본다.
        val beltSeats = occupiedSeats.ifEmpty { seats }
        val rearBuckled = when {
            beltSeats.any { it.buckled == false } && occupiedSeats.isNotEmpty() -> false
            beltSeats.any { it.buckled == true } -> true
            beltSeats.all { it.buckled == false } -> false
            else -> null
        }
        listener.onVehicleChanged(State(rearOccupied, rearBuckled, gear))
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
