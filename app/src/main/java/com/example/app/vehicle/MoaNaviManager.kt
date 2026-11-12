package com.example.app.vehicle

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import com.example.app.data.TripPlace

/**
 * NaviHelper adapter for the original manual route test and MOA recommendation acceptance.
 * Call public methods on Android's main thread (as MainActivity Compose does).
 * onRoutingCompleted means route search completed; verify actual destination on the Navi UI.
 */
class MoaNaviManager(context: Context) {

    private val naviHelper = NaviHelper(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    // 초기 연결 응답과 경로 탐색 완료 이벤트를 별도로 기다립니다.
    // 이 값은 '경로 성공' 판정 시간이 아니라 대기 해제 한도입니다.
    private val connectionResponseTimeoutMs = 12_000L
    private val routeEventTimeoutMs = 8_000L

    private var listenerRegistered = false
    private var connected = false
    private var connecting = false
    private var released = false
    private var onRouteStatus: ((String) -> Unit)? = null

    private data class ConnectionWaiter(
        val onConnected: () -> Unit,
        val onFailed: (String) -> Unit
    )
    private val connectionWaiters = mutableListOf<ConnectionWaiter>()

    private data class PendingRecommendation(
        val place: TripPlace,
        val onCompleted: () -> Unit,
        val onFailed: (String) -> Unit,
        val timeout: Runnable,
        var routeDispatched: Boolean = false
    )
    private var pendingRecommendation: PendingRecommendation? = null

    private val naviEventListener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            updateRouteStatus("경로 탐색 시작")
        }

        override fun onRoutingCompleted() {
            Log.d(
                "MOA_NAVI_EVENT",
                "onRoutingCompleted: pending=${pendingRecommendation?.place?.name}, " +
                        "dispatched=${pendingRecommendation?.routeDispatched}"
            )
            updateRouteStatus("경로 탐색 완료")
            mainHandler.post { finishRecommendationSuccess() }
        }

        override fun onError(errorCode: NaviErrorCode) {
            val message = "내비게이션 오류: $errorCode"
            updateRouteStatus(message)
            mainHandler.post { finishRecommendationFailure(message) }
        }
    }

    private fun updateRouteStatus(message: String) {
        Log.d("MOA_NAVI", message)
        mainHandler.post { if (!released) onRouteStatus?.invoke(message) }
    }

    private fun routeInfo(place: TripPlace): RouteInfo {
        require(place.latitude.isFinite() && place.latitude in -90.0..90.0) {
            "목적지 위도가 올바르지 않습니다."
        }
        require(place.longitude.isFinite() && place.longitude in -180.0..180.0) {
            "목적지 경도가 올바르지 않습니다."
        }
        return RouteInfo(
            latitude = place.latitude,
            longitude = place.longitude,
            displayLongitude = null,
            displayLatitude = null,
            poiName = place.name,
            poiId = "",
            poiSubId = "0",
            address = place.address
        )
    }

    /** Original manual path: keep the existing MainActivity test button compatible. */
    fun requestDestination(place: TripPlace, onStatus: (String) -> Unit) {
        check(!released) { "NaviHelper가 해제되었습니다." }
        check(connected) { "먼저 NaviHelper 연결이 필요합니다." }
        check(pendingRecommendation == null) { "추천 경로 요청이 진행 중입니다." }
        val route = routeInfo(place)
        onRouteStatus = onStatus
        updateRouteStatus("목적지 경로 요청 중...")
        naviHelper.requestRoute(route)
    }

    /**
     * Recommendation path: auto-connect and report the *event*, not requestRoute() return.
     * During this operation do not start another manual route request.
     */
    fun requestRecommendedDestination(
        place: TripPlace,
        onStatus: (String) -> Unit,
        onCompleted: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        if (released) {
            onFailed("NaviHelper가 해제되었습니다.")
            return
        }
        if (pendingRecommendation != null) {
            onFailed("다른 추천 경로를 탐색 중입니다.")
            return
        }

        val route = try {
            routeInfo(place)
        } catch (e: IllegalArgumentException) {
            onFailed(e.message ?: "목적지 좌표가 올바르지 않습니다.")
            return
        }

        onRouteStatus = onStatus
        lateinit var request: PendingRecommendation
        val timeout = Runnable {
            if (pendingRecommendation === request) {
                val reason = if (request.routeDispatched) {
                    "경로 요청은 전송했지만 NaviHelper 완료 이벤트를 확인하지 못했습니다."
                } else {
                    "NaviHelper 연결 응답 시간이 초과되었습니다."
                }
                Log.w("MOA_NAVI_TIMEOUT", "${place.name}: $reason")
                finishRecommendationFailure(reason)
            }
        }
        request = PendingRecommendation(place, onCompleted, onFailed, timeout)
        pendingRecommendation = request
        // 연결 단계와 경로 탐색 단계를 구분합니다.
        mainHandler.postDelayed(timeout, connectionResponseTimeoutMs)
        updateRouteStatus("MOA 추천 목적지 연결 중: ${place.name}")

        initialize(
            onConnected = {
                if (pendingRecommendation === request) {
                    try {
                        updateRouteStatus("MOA 추천 경로 요청 중: ${place.name}")
                        request.routeDispatched = true
                        // 연결 대기 타이머 대신, 경로 완료 응답 타이머를 새로 시작합니다.
                        mainHandler.removeCallbacks(request.timeout)
                        mainHandler.postDelayed(request.timeout, routeEventTimeoutMs)
                        Log.d("MOA_NAVI", "경로 요청 전송: ${place.name}")
                        naviHelper.requestRoute(route)
                        updateRouteStatus("경로 요청 전송됨 · 완료 확인 중")
                    } catch (e: Exception) {
                        Log.e("MOA_NAVI", "추천 경로 요청 예외", e)
                        finishRecommendationFailure(e.message ?: "경로 요청 실패")
                    }
                }
            },
            onFailed = { message ->
                if (pendingRecommendation === request) {
                    finishRecommendationFailure("NaviHelper 연결 실패: $message")
                }
            }
        )
    }

    fun initialize(onConnected: () -> Unit, onFailed: (String) -> Unit) {
        if (released) {
            onFailed("NaviHelper가 해제되었습니다.")
            return
        }
        if (connected) {
            onConnected()
            return
        }
        connectionWaiters.add(ConnectionWaiter(onConnected, onFailed))
        if (connecting) return
        connecting = true

        try {
            naviHelper.initialize(
                onConnected = {
                    mainHandler.post {
                        if (released) return@post
                        connecting = false
                        connected = true
                        if (!listenerRegistered) {
                            naviHelper.addListener(naviEventListener)
                            listenerRegistered = true
                        }
                        Log.d("MOA_NAVI", "NaviHelper 연결 성공")
                        val waiters = connectionWaiters.toList()
                        connectionWaiters.clear()
                        waiters.forEach { it.onConnected() }
                    }
                },
                onFailed = { throwable ->
                    mainHandler.post {
                        if (released) return@post
                        connecting = false
                        connected = false
                        Log.e("MOA_NAVI", "NaviHelper 연결 실패", throwable)
                        val waiters = connectionWaiters.toList()
                        connectionWaiters.clear()
                        waiters.forEach {
                            it.onFailed(throwable.message ?: "서비스 연결 실패")
                        }
                    }
                }
            )
        } catch (e: Exception) {
            connecting = false
            connected = false
            val waiters = connectionWaiters.toList()
            connectionWaiters.clear()
            waiters.forEach { it.onFailed(e.message ?: "초기화 실패") }
        }
    }

    private fun finishRecommendationSuccess() {
        val request = pendingRecommendation ?: return
        if (!request.routeDispatched) return // Ignore unrelated route events while connecting.
        pendingRecommendation = null
        mainHandler.removeCallbacks(request.timeout)
        Log.d("MOA_NAVI", "추천 경로 탐색 완료: ${request.place.name}")
        request.onCompleted()
    }

    private fun finishRecommendationFailure(message: String) {
        val request = pendingRecommendation ?: return
        pendingRecommendation = null
        mainHandler.removeCallbacks(request.timeout)
        Log.e("MOA_NAVI", "추천 경로 탐색 실패: $message")
        request.onFailed(message)
    }

    fun release() {
        if (released) return
        // Cancel delayed work without reporting old UI callbacks during Activity recreation.
        pendingRecommendation?.let { mainHandler.removeCallbacks(it.timeout) }
        pendingRecommendation = null
        connectionWaiters.clear()
        released = true
        connected = false
        connecting = false
        if (listenerRegistered) {
            naviHelper.removeListener(naviEventListener)
            listenerRegistered = false
        }
        onRouteStatus = null
        naviHelper.release()
    }
}
