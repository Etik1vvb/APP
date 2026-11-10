package com.example.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// 기존 DataStore 파일명 유지. 이전 테스트용 단일 프로필 키는 사용하지 않습니다.
private val Context.userDataStore by preferencesDataStore(name = "user_preferences")

data class UserProfile(
    val nickname: String = "",
    val ageGroup: String? = null,
    val gender: String? = null,
    val preferences: Set<String> = emptySet(),
    val id: String = UUID.randomUUID().toString(),
    val atmospheres: Set<String> = emptySet(),
    val discoveryStyle: String = "",
    val detourPreference: String = "",
    val recommendationFrequency: String = ""
)

class UserPreferencesRepository(private val context: Context) {
    companion object {
        private val PROFILES_JSON = stringPreferencesKey("profiles_json_v2")
        private val SELECTED_PROFILE_ID = stringPreferencesKey("selected_profile_id_v2")
    }

    private fun setToJson(items: Set<String>): JSONArray = JSONArray().apply {
        items.forEach { put(it) }
    }

    private fun jsonToSet(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.toSet()
    }

    private fun encode(profiles: List<UserProfile>): String = JSONArray().apply {
        profiles.forEach { profile ->
            put(JSONObject().apply {
                put("id", profile.id)
                put("nickname", profile.nickname)
                put("ageGroup", profile.ageGroup ?: JSONObject.NULL)
                put("gender", profile.gender ?: JSONObject.NULL)
                put("preferences", setToJson(profile.preferences))
                put("atmospheres", setToJson(profile.atmospheres))
                put("discoveryStyle", profile.discoveryStyle)
                put("detourPreference", profile.detourPreference)
                put("recommendationFrequency", profile.recommendationFrequency)
            })
        }
    }.toString()

    private fun decode(raw: String?): List<UserProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id")
                if (id.isBlank()) return@mapNotNull null
                UserProfile(
                    id = id,
                    nickname = item.optString("nickname"),
                    ageGroup = item.optString("ageGroup").takeUnless { it.isBlank() || it == "null" },
                    gender = item.optString("gender").takeUnless { it.isBlank() || it == "null" },
                    preferences = jsonToSet(item.optJSONArray("preferences")),
                    atmospheres = jsonToSet(item.optJSONArray("atmospheres")),
                    discoveryStyle = item.optString("discoveryStyle"),
                    detourPreference = item.optString("detourPreference"),
                    recommendationFrequency = item.optString("recommendationFrequency")
                )
            }
        } catch (e: org.json.JSONException) {
            // 손상된 JSON을 정상적인 빈 목록으로 오인해 덮어쓰지 않도록
            // 저장 작업에서는 decode가 아닌 decodeStrict를 사용합니다.
            emptyList()
        }
    }

    private fun decodeStrict(raw: String?): List<UserProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return JSONArray(raw).let { array ->
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val id = item.getString("id")
                require(id.isNotBlank()) { "프로필 ID가 비어 있습니다." }
                UserProfile(
                    id = id,
                    nickname = item.optString("nickname"),
                    ageGroup = item.optString("ageGroup").takeUnless { it.isBlank() || it == "null" },
                    gender = item.optString("gender").takeUnless { it.isBlank() || it == "null" },
                    preferences = jsonToSet(item.optJSONArray("preferences")),
                    atmospheres = jsonToSet(item.optJSONArray("atmospheres")),
                    discoveryStyle = item.optString("discoveryStyle"),
                    detourPreference = item.optString("detourPreference"),
                    recommendationFrequency = item.optString("recommendationFrequency")
                )
            }
        }
    }

    val profiles: Flow<List<UserProfile>> = context.userDataStore.data.map { prefs ->
        decode(prefs[PROFILES_JSON])
    }

    val selectedProfileId: Flow<String?> = context.userDataStore.data.map { prefs ->
        prefs[SELECTED_PROFILE_ID]
    }

    // MainActivity의 기존 코드와 호환: 현재 선택한 프로필을 반환합니다.
    val userProfile: Flow<UserProfile> = context.userDataStore.data.map { prefs ->
        val selectedId = prefs[SELECTED_PROFILE_ID]
        decode(prefs[PROFILES_JSON]).firstOrNull { it.id == selectedId } ?: UserProfile(id = "")
    }

    // 기존 ProfileScreen -> MainActivity 저장 호출 유지.
    // 기존 화면에서 저장하면 새 프로필을 추가하고 바로 선택합니다.
    suspend fun saveProfile(profile: UserProfile) {
        context.userDataStore.edit { prefs ->
            val current = decodeStrict(prefs[PROFILES_JSON])
            val id = profile.id.ifBlank { UUID.randomUUID().toString() }
            val updated = profile.copy(id = id)
            val next = current.filterNot { it.id == id } + updated
            prefs[PROFILES_JSON] = encode(next)
            prefs[SELECTED_PROFILE_ID] = id
        }
    }

    // 새 프로필만 추가합니다. 현재 선택은 변경하지 않습니다.
    suspend fun addProfile(profile: UserProfile): String {
        val id = UUID.randomUUID().toString()
        context.userDataStore.edit { prefs ->
            val current = decodeStrict(prefs[PROFILES_JSON])
            prefs[PROFILES_JSON] = encode(current + profile.copy(id = id))
        }
        return id
    }

    // 존재하는 프로필만 수정하며, 선택 상태는 바꾸지 않습니다.
    suspend fun updateProfile(profile: UserProfile): Boolean {
        var updated = false
        context.userDataStore.edit { prefs ->
            val current = decodeStrict(prefs[PROFILES_JSON])
            if (current.any { it.id == profile.id }) {
                prefs[PROFILES_JSON] = encode(current.map { if (it.id == profile.id) profile else it })
                updated = true
            }
        }
        return updated
    }

    // 등록된 프로필만 선택할 수 있습니다.
    suspend fun selectProfile(id: String): Boolean {
        var selected = false
        context.userDataStore.edit { prefs ->
            if (decodeStrict(prefs[PROFILES_JSON]).any { it.id == id }) {
                prefs[SELECTED_PROFILE_ID] = id
                selected = true
            }
        }
        return selected
    }

    // 현재 선택된 프로필은 절대 삭제하지 않습니다.
    // true: 삭제 성공, false: 선택된 프로필이거나 존재하지 않는 프로필.
    suspend fun deleteProfile(id: String): Boolean {
        var deleted = false
        context.userDataStore.edit { prefs ->
            val current = decodeStrict(prefs[PROFILES_JSON])
            if (id != prefs[SELECTED_PROFILE_ID] && current.any { it.id == id }) {
                prefs[PROFILES_JSON] = encode(current.filterNot { it.id == id })
                deleted = true
            }
        }
        return deleted
    }

    // 기존 deleteProfile() 호출 호환: 선택된 프로필은 삭제하지 않습니다.
    // UI에서는 반드시 deleteProfile(id)를 사용하세요.
    suspend fun deleteProfile() {
        // 선택된 프로필 삭제 금지 정책에 따라 의도적으로 아무 작업도 하지 않습니다.
    }
}
