
package com.example.app.vehicle

import android.content.Context
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue

class VehicleRepository(context: Context) {

    private val vehicle = Vehicle(context.applicationContext)

    fun initialize() {
        vehicle.initialize()
    }

    fun checkDriverDoor() {
        val door = vehicle.getDoor()

        door.getCurrentDoorPosition(
            area = DoorArea(DoorValue.ROW_1_LEFT),
            onComplete = { pos: Int? ->
                Log.d("RoadTripVehicle", "운전석 도어 위치: $pos")
            },
            onFailed = { e: Exception ->
                Log.e("RoadTripVehicle", "도어 조회 실패", e)
            }
        )
    }

    fun release() {
        vehicle.release()
    }
}
