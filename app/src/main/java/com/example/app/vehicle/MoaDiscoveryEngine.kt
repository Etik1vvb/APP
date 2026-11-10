
package com.example.app.vehicle

import kotlin.math.*

class MoaDiscoveryEngine {

    private var lastSearchLatitude: Double? = null
    private var lastSearchLongitude: Double? = null
    private var lastSearchTime: Long = 0L

    private val minimumDistanceMeters = 1000.0
    private val minimumIntervalMillis = 3 * 60 * 1000L

    fun shouldSearch(
        latitude: Double,
        longitude: Double,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {

        if (!latitude.isFinite() || !longitude.isFinite()) {
            return false
        }

        if (latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0
        ) {
            return false
        }

        val previousLat = lastSearchLatitude
        val previousLon = lastSearchLongitude

        // 첫 번째 위치 수신에서는 즉시 탐색
        if (previousLat == null || previousLon == null) {
            return true
        }

        val elapsed = nowMillis - lastSearchTime

        if (elapsed < minimumIntervalMillis) {
            return false
        }

        val distance = distanceMeters(
            previousLat,
            previousLon,
            latitude,
            longitude
        )

        return distance >= minimumDistanceMeters
    }

    fun markSearch(
        latitude: Double,
        longitude: Double,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        lastSearchLatitude = latitude
        lastSearchLongitude = longitude
        lastSearchTime = nowMillis
    }

    fun reset() {
        lastSearchLatitude = null
        lastSearchLongitude = null
        lastSearchTime = 0L
    }

    private fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadius = 6371000.0

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a =
            sin(dLat / 2).pow(2) +
                    cos(Math.toRadians(lat1)) *
                    cos(Math.toRadians(lat2)) *
                    sin(dLon / 2).pow(2)

        return 2 * earthRadius *
                atan2(sqrt(a), sqrt(1 - a))
    }
}
