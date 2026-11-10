package com.example.app.vehicle

import android.location.Location
import android.os.SystemClock
import android.util.Log
import com.example.app.data.KakaoPlace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs location-triggered place discovery independently of Compose recompositions.
 * This is an Activity-owned engine, NOT a foreground/background Android Service.
 * All public methods and callbacks must be called on the main thread.
 */
class MoaRecommendationEngine(
    private val searchPlaces: suspend (latitude: Double, longitude: Double) -> Result<List<KakaoPlace>>,
    private val onPlaces: (List<KakaoPlace>) -> Unit,
    private val onLoading: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
    private val minDistanceMeters: Float = 1_000f,
    private val minSearchIntervalMs: Long = 5_000L
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeTripId: String? = null
    private var lastSuccessfulPosition: Pair<Double, Double>? = null
    private var lastAttemptMs: Long = -1L
    private var searching = false
    private var generation = 0L
    private var closed = false

    /** Call on entering/leaving the trip's active UI. Does not stop on Activity onPause. */
    fun setActiveTrip(tripId: String?) {
        if (closed || activeTripId == tripId) return
        activeTripId = tripId
        generation++
        lastSuccessfulPosition = null
        lastAttemptMs = -1L
        Log.d("MOA_ENGINE", "활성 여행 변경: ${tripId ?: "없음"}")
    }

    /** Feed each real Pleos FusedLocation callback into this method. */
    fun onLocation(latitude: Double, longitude: Double) {
        if (closed || activeTripId == null) return
        if (!latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return
        if (searching) return

        val previous = lastSuccessfulPosition
        if (previous != null) {
            val distance = FloatArray(1)
            Location.distanceBetween(previous.first, previous.second, latitude, longitude, distance)
            if (distance[0] < minDistanceMeters) return
        }

        val now = SystemClock.elapsedRealtime()
        if (lastAttemptMs >= 0L && now - lastAttemptMs < minSearchIntervalMs) return

        lastAttemptMs = now // throttle failures too
        searching = true
        onLoading(true)
        val requestedTripId = activeTripId
        val requestedGeneration = generation
        Log.d("MOA_ENGINE", "자동 검색 시작: trip=$requestedTripId, lat=$latitude, lon=$longitude")

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    searchPlaces(latitude, longitude)
                }
                // Ignore responses that belong to a trip that is no longer active.
                if (activeTripId == requestedTripId && generation == requestedGeneration) {
                    result.fold(
                        onSuccess = { places ->
                            lastSuccessfulPosition = latitude to longitude
                            onPlaces(places)
                            Log.d("MOA_AUTO", "자동 추천 검색 성공: ${places.size}개")
                        },
                        onFailure = { error ->
                            onError(error.message ?: "자동 추천 검색 실패")
                            Log.e("MOA_ENGINE", "장소 검색 실패", error)
                        }
                    )
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                if (activeTripId == requestedTripId && generation == requestedGeneration) {
                    onError(error.message ?: "자동 추천 예외")
                    Log.e("MOA_ENGINE", "자동 추천 예외", error)
                }
            } finally {
                searching = false
                if (!closed) onLoading(false)
            }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        activeTripId = null
        generation++
        scope.cancel()
    }
}
