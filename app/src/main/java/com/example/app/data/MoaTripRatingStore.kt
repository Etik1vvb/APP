package com.example.app.data

import android.content.Context

/** 여행별 만족도는 별도 보관하고, 기존 수락/거절 학습 기록은 변경하지 않습니다. */
class MoaTripRatingStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "moa_trip_ratings_v1", Context.MODE_PRIVATE
    )

    /** null이면 평가 미작성으로 화면에는 기본 3점을 표시합니다. */
    fun getRating(tripId: String): Int? =
        if (preferences.contains("rating_$tripId")) {
            preferences.getInt("rating_$tripId", 3).takeIf { it in 1..5 }
        } else null

    fun saveRating(
        tripId: String,
        profileId: String,
        rating: Int,
        category: String?
    ): Boolean {
        require(rating in 1..5)
        require(tripId.isNotBlank() && profileId.isNotBlank())
        val editor = preferences.edit()
            .putInt("rating_$tripId", rating)
            .putString("profile_$tripId", profileId)
        if (category.isNullOrBlank()) editor.remove("category_$tripId")
        else editor.putString("category_$tripId", category)
        return editor.commit()
    }

    fun deleteRating(tripId: String) {
        preferences.edit()
            .remove("rating_$tripId")
            .remove("profile_$tripId")
            .remove("category_$tripId")
            .apply()
    }

    /**
     * 완료 여행의 평가를 현재 MOA 학습치(0~1)에 작은 보정으로 적용합니다.
     * 3점 및 미평가는 0, 4~5점은 가산, 1~2점은 감산합니다.
     * 각 여행의 현재 점수만 매번 다시 계산하므로 재평가해도 학습이 누적되지 않습니다.
     * 분류 가능한 최종 목적지가 없는 여행은 평가를 보관하지만 가중치 적용은 생략합니다.
     */
    fun adjustLearnedPreferences(
        profileId: String,
        basePreferences: Map<String, Double>
    ): Map<String, Double> {
        if (profileId.isBlank()) return basePreferences
        val snapshot = preferences.all
        val offsets = mutableMapOf<String, Double>()
        snapshot.forEach { (key, value) ->
            if (!key.startsWith("rating_") || value !is Int || value !in 1..5 || value == 3) {
                return@forEach
            }
            val tripId = key.removePrefix("rating_")
            if (snapshot["profile_$tripId"] != profileId) return@forEach
            val category = snapshot["category_$tripId"] as? String
            if (category.isNullOrBlank()) return@forEach
            val change = (value - 3) * 0.08  // 최대 ±0.16 (학습 20점 축 기준 최대 3.2점)
            offsets[category] = ((offsets[category] ?: 0.0) + change)
                .coerceIn(-0.25, 0.25)
        }
        if (offsets.isEmpty()) return basePreferences
        val result = basePreferences.toMutableMap()
        offsets.forEach { (category, adjustment) ->
            result[category] = ((result[category] ?: 0.5) + adjustment).coerceIn(0.0, 1.0)
        }
        return result
    }
}
