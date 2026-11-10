package com.example.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.moaDestinationStore by preferencesDataStore(name = "moa_destinations_v1")

/** One active destination per trip; the trip's region stays in TripInfo.destination. */
data class TripPlace(
    val id: String,
    val name: String,
    val address: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val parcelAddress: String = ""
)

class TripDestinationRepository(context: Context) {
    private val store = context.applicationContext.moaDestinationStore
    private val key = stringPreferencesKey("active_destinations_json")

    val destinations: Flow<Map<String, TripPlace>> = store.data.map { prefs ->
        val root = runCatching { JSONObject(prefs[key] ?: "{}") }.getOrDefault(JSONObject())
        buildMap {
            val keys = root.keys()
            while (keys.hasNext()) {
                val tripId = keys.next()
                val item = root.optJSONObject(tripId) ?: continue
                put(tripId, TripPlace(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    address = item.optString("address"),
                    category = item.optString("category"),
                    latitude = item.optDouble("latitude"),
                    longitude = item.optDouble("longitude")
                ))
            }
        }
    }

    suspend fun save(tripId: String, place: TripPlace) {
        require(tripId.isNotBlank() && place.id.isNotBlank())
        store.edit { prefs ->
            val root = runCatching { JSONObject(prefs[key] ?: "{}") }.getOrDefault(JSONObject())
            root.put(tripId, JSONObject().apply {
                put("id", place.id)
                put("name", place.name)
                put("address", place.address)
                put("category", place.category)
                put("latitude", place.latitude)
                put("longitude", place.longitude)
            })
            prefs[key] = root.toString()
        }
    }

    suspend fun delete(tripId: String) {
        store.edit { prefs ->
            val root = runCatching { JSONObject(prefs[key] ?: "{}") }.getOrDefault(JSONObject())
            root.remove(tripId)
            prefs[key] = root.toString()
        }
    }
}
