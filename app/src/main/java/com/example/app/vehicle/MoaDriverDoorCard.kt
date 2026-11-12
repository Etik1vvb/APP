package com.example.app.vehicle

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue

/**
 * Pleos Vehicle SDK에서 운전석(1열 좌측) 문 상태를 '조회'한다.
 * 기존 VehicleRepository를 수정하지 않기 위한 독립적 읽기 전용 어댑터.
 * 앱 전체에서 하나의 인스턴스를 유지하여 화면 전환 시 재초기화하지 않는다.
 */
data class MoaDriverDoorState(
    val label: String = "조회 전",
    val detail: String? = null,
    val loading: Boolean = false,
    val rawValue: Int? = null
)

class MoaDriverDoorController(context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val vehicle = Vehicle(context.applicationContext)
    private val driverArea = DoorArea(DoorValue.ROW_1_LEFT)
    private var initialized = false
    private var released = false
    private var requestNumber = 0
    private var timeoutTask: Runnable? = null

    var state by mutableStateOf(MoaDriverDoorState())
        private set

    fun initialize() {
        if (released || initialized) return
        try {
            state = MoaDriverDoorState(label = "차량 서비스 연결 중")
            vehicle.initialize()
            initialized = true
            Log.d("MOA_VEHICLE", "Vehicle SDK initialized")
            refresh()
        } catch (e: Exception) {
            state = MoaDriverDoorState(
                label = "차량 상태 확인 불가",
                detail = "Vehicle SDK 초기화 실패: ${e.message}"
            )
            Log.e("MOA_VEHICLE", "Vehicle SDK initialize failed", e)
        }
    }

    fun refresh() {
        if (released) return
        if (!initialized) {
            initialize()
            return
        }
        if (state.loading) return

        val token = ++requestNumber
        state = MoaDriverDoorState(label = "운전석 문 조회 중", loading = true)

        val timeout = Runnable {
            if (!released && requestNumber == token && state.loading) {
                state = MoaDriverDoorState(
                    label = "차량 응답 지연",
                    detail = "Vehicle Service 응답이 없습니다. 다시 조회해 주세요."
                )
                Log.w("MOA_VEHICLE", "Door read timeout")
            }
        }
        timeoutTask = timeout
        mainHandler.postDelayed(timeout, 6_000L)

        try {
            vehicle.getDoor().getCurrentDoorPosition(
                area = driverArea,
                onComplete = { position: Int? ->
                    mainHandler.post {
                        if (released || requestNumber != token || !state.loading) return@post
                        clearTimeout()
                        state = when (position) {
                            0 -> MoaDriverDoorState(label = "닫힘", rawValue = 0)
                            1 -> MoaDriverDoorState(label = "열림", rawValue = 1)
                            null -> MoaDriverDoorState(
                                label = "차량 상태 확인 불가",
                                detail = "SDK가 현재 값을 반환하지 않았습니다."
                            )
                            else -> MoaDriverDoorState(
                                label = "알 수 없는 값 ($position)",
                                detail = "현재 SDK가 반환한 원시 값입니다.",
                                rawValue = position
                            )
                        }
                        Log.d("MOA_VEHICLE", "Driver door position=$position")
                    }
                },
                onFailed = { error: Exception ->
                    mainHandler.post {
                        if (released || requestNumber != token || !state.loading) return@post
                        clearTimeout()
                        state = MoaDriverDoorState(
                            label = "차량 상태 확인 불가",
                            detail = error.message ?: "Vehicle Service 조회 실패"
                        )
                        Log.e("MOA_VEHICLE", "Door read failed", error)
                    }
                }
            )
        } catch (e: Exception) {
            if (!released && requestNumber == token) {
                clearTimeout()
                state = MoaDriverDoorState(
                    label = "차량 상태 확인 불가",
                    detail = e.message ?: "Vehicle SDK 요청 실패"
                )
                Log.e("MOA_VEHICLE", "Door read request failed", e)
            }
        }
    }

    private fun clearTimeout() {
        timeoutTask?.let { mainHandler.removeCallbacks(it) }
        timeoutTask = null
    }

    fun release() {
        if (released) return
        released = true
        requestNumber++
        clearTimeout()
        if (initialized) {
            try { vehicle.release() }
            catch (e: Exception) { Log.w("MOA_VEHICLE", "Vehicle release failed", e) }
        }
    }
}

@Composable
fun MoaDriverDoorCard(
    controller: MoaDriverDoorController,
    modifier: Modifier = Modifier
) {
    val door = controller.state

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("🚘 차량 상태 · 운전석 문", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = door.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (door.rawValue == 1) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
                door.detail?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Pleos Vehicle SDK · 1열 왼쪽 · 마지막 조회값",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(
                onClick = { controller.refresh() },
                enabled = !door.loading
            ) {
                Text(if (door.loading) "조회 중" else "새로고침")
            }
        }
    }
}
