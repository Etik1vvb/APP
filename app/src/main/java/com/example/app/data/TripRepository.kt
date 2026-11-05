
package com.example.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.app.ui.trip.TripInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.tripDataStore by preferencesDataStore(
    name = "roadtrip_trips"
)

enum class TripStatus {
    PLANNED,
    ACTIVE,
    COMPLETED
}

class TripRepository(context: Context) {

    private val dataStore = context.applicationContext.tripDataStore

    private object Keys {
        val DESTINATION = stringPreferencesKey("destination")
        val START_DATE = stringPreferencesKey("start_date")
        val END_DATE = stringPreferencesKey("end_date")
        val COMPANIONS = stringPreferencesKey("companions")
        val PURPOSE = stringPreferencesKey("purpose")
        val STATUS = stringPreferencesKey("trip_status")
    }

    // 저장된 여행 정보 읽기
    val savedTrip: Flow<TripInfo?> = dataStore.data.map { preferences ->
        val destination = preferences[Keys.DESTINATION]

        if (destination.isNullOrBlank()) {
            null
        } else {
            TripInfo(
                destination = destination,
                startDate = preferences[Keys.START_DATE].orEmpty(),
                endDate = preferences[Keys.END_DATE].orEmpty(),
                companions = preferences[Keys.COMPANIONS].orEmpty(),
                purpose = preferences[Keys.PURPOSE].orEmpty()
            )
        }
    }

    // 현재 여행 상태 읽기
    val tripStatus: Flow<TripStatus?> = dataStore.data.map { preferences ->
        val destination = preferences[Keys.DESTINATION]

        if (destination.isNullOrBlank()) {
            null
        } else {
            val storedStatus = preferences[Keys.STATUS]

            TripStatus.entries.firstOrNull {
                it.name == storedStatus
            } ?: TripStatus.PLANNED
        }
    }

    // 새 여행 저장
    suspend fun saveTrip(trip: TripInfo) {
        dataStore.edit { preferences ->
            preferences[Keys.DESTINATION] = trip.destination
            preferences[Keys.START_DATE] = trip.startDate
            preferences[Keys.END_DATE] = trip.endDate
            preferences[Keys.COMPANIONS] = trip.companions
            preferences[Keys.PURPOSE] = trip.purpose
            preferences[Keys.STATUS] = TripStatus.PLANNED.name
        }
    }

    // 여행 시작
    suspend fun startTrip() {
        updateStatus(TripStatus.PLANNED, TripStatus.ACTIVE)
    }

    // 여행 종료
    suspend fun completeTrip() {
        updateStatus(TripStatus.ACTIVE, TripStatus.COMPLETED)
    }

    // 상태 전환
    private suspend fun updateStatus(
        expected: TripStatus,
        next: TripStatus
    ) {
        dataStore.edit { preferences ->
            val destination = preferences[Keys.DESTINATION]

            if (!destination.isNullOrBlank()) {
                val current = TripStatus.entries.firstOrNull {
                    it.name == preferences[Keys.STATUS]
                } ?: TripStatus.PLANNED

                if (current == expected) {
                    preferences[Keys.STATUS] = next.name
                }
            }
        }
    }

    // 여행 정보 삭제
    suspend fun deleteTrip() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
