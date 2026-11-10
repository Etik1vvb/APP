
package com.example.app.vehicle

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.os.Handler
import android.os.Looper

class MoaLocationManager(context: Context) {

    private val fusedLocation = FusedLocation(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    var onLocationChanged: ((LocationData) -> Unit)? = null
    var onStatusChanged: ((String) -> Unit)? = null

    private var initialized = false
    private var listening = false

    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            mainHandler.post {
                if (listening) {
                    if (location != null) {
                        android.util.Log.d(
                            "MOA_GPS",
                            "위치 갱신: " +
                                    "lat=${location.latitude}, " +
                                    "lon=${location.longitude}, " +
                                    "time=${location.timestampMillis}"
                        )

                        onLocationChanged?.invoke(location)
                        onStatusChanged?.invoke("위치 수신 중")
                    } else {
                        onStatusChanged?.invoke("위치 데이터 대기 중")
                    }
                }
            }
        }

        override fun onError(exception: Exception) {
            mainHandler.post {
                if (listening) {
                    onStatusChanged?.invoke(
                        "위치 오류: ${exception.message ?: "알 수 없는 오류"}"
                    )
                }
            }
        }
    }

    fun start() {
        if (listening) return

        try {
            if (!fusedLocation.isSupported()) {
                onStatusChanged?.invoke(
                    "Pleos 위치 서비스를 찾을 수 없습니다."
                )
                return
            }

            if (!initialized) {
                fusedLocation.initialize()
                initialized = true
            }

            listening = true
            onStatusChanged?.invoke("위치 서비스 연결 중")

            fusedLocation.registerFusedLocationCallback(listener)
            fusedLocation.getLastKnownLocation(listener)

        } catch (e: SecurityException) {
            listening = false
            onStatusChanged?.invoke("위치 권한이 없습니다.")
        } catch (e: Exception) {
            listening = false
            onStatusChanged?.invoke(
                "위치 서비스 오류: ${e.message ?: "알 수 없는 오류"}"
            )
        }
    }

    fun stop() {
        if (!listening) return

        listening = false

        try {
            fusedLocation.unregisterFusedLocationCallback(listener)
        } catch (_: Exception) {
            // 서비스가 이미 종료된 경우
        }
    }

    fun release() {
        stop()

        if (initialized) {
            try {
                fusedLocation.release()
            } catch (_: Exception) {
                // 자원 정리 중 예외
            }
            initialized = false
        }

        onLocationChanged = null
        onStatusChanged = null
    }
}
