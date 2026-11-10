package com.example.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.app.ui.trip.TripInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.tripDataStore by preferencesDataStore(name = "roadtrip_trips")

enum class TripStatus { PLANNED, ACTIVE, COMPLETED }

data class SavedTrip(
    val id: String,
    val profileId: String,
    val info: TripInfo,
    val status: TripStatus = TripStatus.PLANNED
)

class TripRepository(context: Context) {
    private val dataStore = context.applicationContext.tripDataStore
    private val profileRepository = UserPreferencesRepository(context.applicationContext)

    private object Keys {
        val TRIPS_JSON = stringPreferencesKey("trips_json_v2")
        val SELECTED_TRIPS_JSON = stringPreferencesKey("selected_trips_json_v2")
    }

    private fun statusOf(raw: String?): TripStatus =
        TripStatus.entries.firstOrNull { it.name == raw } ?: TripStatus.PLANNED

    private fun decodeTrips(raw: String?): List<SavedTrip> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val id = item.getString("id")
            val profileId = item.getString("profileId")
            require(id.isNotBlank() && profileId.isNotBlank()) { "여행 식별자가 비어 있습니다." }
            SavedTrip(
                id = id,
                profileId = profileId,
                info = TripInfo(
                    destination = item.optString("destination"),
                    startDate = item.optString("startDate"),
                    endDate = item.optString("endDate"),
                    companions = item.optString("companions"),
                    purpose = item.optString("purpose"),
                    mood = item.optString("mood"),
                    desiredExperience = item.optString("desiredExperience"),
                    detourPreference = item.optString("detourPreference")
                ),
                status = statusOf(item.optString("status"))
            )
        }
    }

    private fun encodeTrips(trips: List<SavedTrip>): String = JSONArray().apply {
        trips.forEach { trip ->
            put(JSONObject().apply {
                put("id", trip.id)
                put("profileId", trip.profileId)
                put("destination", trip.info.destination)
                put("startDate", trip.info.startDate)
                put("endDate", trip.info.endDate)
                put("companions", trip.info.companions)
                put("purpose", trip.info.purpose)
                put("mood", trip.info.mood)
                put("desiredExperience", trip.info.desiredExperience)
                put("detourPreference", trip.info.detourPreference)
                put("status", trip.status.name)
            })
        }
    }.toString()

    private fun decodeSelections(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val obj = JSONObject(raw)
        return buildMap {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, obj.getString(key))
            }
        }
    }

    private fun encodeSelections(selections: Map<String, String>): String = JSONObject().apply {
        selections.forEach { (profileId, tripId) -> put(profileId, tripId) }
    }.toString()

    // 현재 선택된 프로필의 여행 목록. 다른 프로필의 여행은 반환하지 않습니다.
    val trips: Flow<List<SavedTrip>> = combine(
        dataStore.data,
        profileRepository.selectedProfileId
    ) { prefs, profileId ->
        if (profileId.isNullOrBlank()) emptyList()
        else decodeTrips(prefs[Keys.TRIPS_JSON]).filter { it.profileId == profileId }
    }

    // 프로필마다 마지막으로 선택한 여행 ID를 따로 기억합니다.
    val selectedTripId: Flow<String?> = combine(
        dataStore.data,
        profileRepository.selectedProfileId
    ) { prefs, profileId ->
        if (profileId.isNullOrBlank()) null
        else decodeSelections(prefs[Keys.SELECTED_TRIPS_JSON])[profileId]
    }

    // 기존 MainActivity와 호환되는 현재 여행 정보.
    val selectedTrip: Flow<SavedTrip?> = combine(trips, selectedTripId) { list, id ->
        list.firstOrNull { it.id == id }
            ?: list.firstOrNull { it.status == TripStatus.ACTIVE }
            ?: list.firstOrNull { it.status == TripStatus.PLANNED }
            ?: list.lastOrNull()
    }

    val savedTrip: Flow<TripInfo?> = selectedTrip.map { it?.info }
    val tripStatus: Flow<TripStatus?> = selectedTrip.map { it?.status }

    // 새 여행을 추가합니다. 같은 프로필의 기존 여행은 보존합니다.
    suspend fun saveTrip(trip: TripInfo): String {
        val profileId = profileRepository.selectedProfileId.first()
        require(!profileId.isNullOrBlank()) { "먼저 프로필을 선택해야 합니다." }
        val tripId = UUID.randomUUID().toString()
        dataStore.edit { prefs ->
            val current = decodeTrips(prefs[Keys.TRIPS_JSON])
            prefs[Keys.TRIPS_JSON] = encodeTrips(
                current + SavedTrip(tripId, profileId, trip)
            )
            val selected = decodeSelections(prefs[Keys.SELECTED_TRIPS_JSON]).toMutableMap()
            selected[profileId] = tripId
            prefs[Keys.SELECTED_TRIPS_JSON] = encodeSelections(selected)
        }
        return tripId
    }

    // 기존 여행의 정보만 수정합니다. ID, 프로필, 진행 상태는 유지합니다.
    // 다른 프로필의 여행과 완료된 여행은 수정할 수 없습니다.
    suspend fun updateTrip(tripId: String, updatedInfo: TripInfo): Boolean {
        val profileId = profileRepository.selectedProfileId.first() ?: return false
        if (tripId.isBlank() || updatedInfo.destination.isBlank()) return false
        var updated = false
        dataStore.edit { prefs ->
            val current = decodeTrips(prefs[Keys.TRIPS_JSON])
            val target = current.firstOrNull {
                it.id == tripId && it.profileId == profileId
            }
            if (target != null && target.status != TripStatus.COMPLETED) {
                prefs[Keys.TRIPS_JSON] = encodeTrips(current.map { trip ->
                    if (trip.id == tripId && trip.profileId == profileId) {
                        trip.copy(info = updatedInfo)
                    } else trip
                })
                updated = true
            }
        }
        return updated
    }

    // 특정 여행을 현재 여행으로 선택합니다.
    suspend fun selectTrip(tripId: String): Boolean {
        val profileId = profileRepository.selectedProfileId.first() ?: return false
        var selected = false
        dataStore.edit { prefs ->
            val exists = decodeTrips(prefs[Keys.TRIPS_JSON])
                .any { it.id == tripId && it.profileId == profileId }
            if (exists) {
                val selections = decodeSelections(prefs[Keys.SELECTED_TRIPS_JSON]).toMutableMap()
                selections[profileId] = tripId
                prefs[Keys.SELECTED_TRIPS_JSON] = encodeSelections(selections)
                selected = true
            }
        }
        return selected
    }

    // 여행 시작: 한 프로필에는 동시에 ACTIVE 여행이 하나만 존재하도록 제한합니다.
    suspend fun startTrip(tripId: String): Boolean =
        changeStatus(tripId, TripStatus.PLANNED, TripStatus.ACTIVE)


    suspend fun switchToTrip(tripId: String): Boolean {
        var switched = false

        dataStore.edit { preferences ->
            val trips = decodeTrips(preferences[Keys.TRIPS_JSON])
            val target = trips.firstOrNull { it.id == tripId }

            if (target != null && target.status == TripStatus.PLANNED) {
                val updatedTrips = trips.map { trip ->
                    when {
                        // 새로 시작할 여행
                        trip.id == tripId ->
                            trip.copy(status = TripStatus.ACTIVE)

                        // 같은 프로필의 기존 진행 여행
                        trip.profileId == target.profileId &&
                                trip.status == TripStatus.ACTIVE ->
                            trip.copy(status = TripStatus.PLANNED)

                        // 나머지 여행은 그대로 유지
                        else -> trip
                    }
                }

                preferences[Keys.TRIPS_JSON] = encodeTrips(updatedTrips)

                val selections =
                    decodeSelections(preferences[Keys.SELECTED_TRIPS_JSON])
                        .toMutableMap()

                selections[target.profileId] = target.id

                preferences[Keys.SELECTED_TRIPS_JSON] =
                    encodeSelections(selections)

                switched = true
            }
        }

        return switched
    }

    suspend fun completeTrip(tripId: String): Boolean =
        changeStatus(tripId, TripStatus.ACTIVE, TripStatus.COMPLETED)

    // 기존 MainActivity 버튼과 호환.
    suspend fun startTrip() {
        val id = selectedTrip.first()?.id ?: return
        startTrip(id)
    }

    suspend fun completeTrip() {
        val id = selectedTrip.first()?.id ?: return
        completeTrip(id)
    }

    private suspend fun changeStatus(
        tripId: String,
        expected: TripStatus,
        next: TripStatus
    ): Boolean {
        val profileId = profileRepository.selectedProfileId.first() ?: return false
        var changed = false
        dataStore.edit { prefs ->
            val current = decodeTrips(prefs[Keys.TRIPS_JSON])
            val target = current.firstOrNull { it.id == tripId && it.profileId == profileId }
            val hasOtherActive = next == TripStatus.ACTIVE && current.any {
                it.profileId == profileId && it.id != tripId && it.status == TripStatus.ACTIVE
            }
            if (target != null && target.status == expected && !hasOtherActive) {
                prefs[Keys.TRIPS_JSON] = encodeTrips(current.map {
                    if (it.id == tripId && it.profileId == profileId) it.copy(status = next) else it
                })
                changed = true
            }
        }
        return changed
    }

    // 현재 프로필의 특정 여행만 삭제합니다.
    suspend fun deleteTrip(tripId: String): Boolean {
        val profileId = profileRepository.selectedProfileId.first() ?: return false
        var deleted = false
        dataStore.edit { prefs ->
            val current = decodeTrips(prefs[Keys.TRIPS_JSON])
            if (current.any { it.id == tripId && it.profileId == profileId }) {
                prefs[Keys.TRIPS_JSON] = encodeTrips(current.filterNot {
                    it.id == tripId && it.profileId == profileId
                })
                val selections = decodeSelections(prefs[Keys.SELECTED_TRIPS_JSON]).toMutableMap()
                if (selections[profileId] == tripId) selections.remove(profileId)
                prefs[Keys.SELECTED_TRIPS_JSON] = encodeSelections(selections)
                deleted = true
            }
        }
        return deleted
    }

    // 기존 호출 호환: 현재 선택된 여행 하나만 삭제합니다.
    suspend fun deleteTrip() {
        val id = selectedTrip.first()?.id ?: return
        deleteTrip(id)
    }

    // 프로필 삭제 후 그 프로필의 여행을 정리할 때 사용합니다.
    // 실제 프로필 삭제 UI 연결 시 호출하세요.
    suspend fun deleteTripsForProfile(profileId: String) {
        require(profileId.isNotBlank())
        dataStore.edit { prefs ->
            val current = decodeTrips(prefs[Keys.TRIPS_JSON])
            prefs[Keys.TRIPS_JSON] = encodeTrips(current.filterNot { it.profileId == profileId })
            val selections = decodeSelections(prefs[Keys.SELECTED_TRIPS_JSON]).toMutableMap()
            selections.remove(profileId)
            prefs[Keys.SELECTED_TRIPS_JSON] = encodeSelections(selections)
        }
    }
}