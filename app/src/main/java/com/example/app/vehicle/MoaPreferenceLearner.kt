
package com.example.app.vehicle

import android.content.Context
import org.json.JSONObject

enum class MoaFeedback {
    ACCEPTED,
    REJECTED,
    VISITED,
    LIKED,
    DISLIKED
}

data class MoaLearnedPreference(
    val category: String,
    val score: Double,
    val feedbackCount: Int
)

class MoaPreferenceLearner(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(
            "moa_preference_learning",
            Context.MODE_PRIVATE
        )

    private val defaultScore = 0.5

    // 초기 학습 규칙. 추후 실제 테스트 결과로 조정
    private fun feedbackDelta(
        feedback: MoaFeedback
    ): Double {
        return when (feedback) {
            MoaFeedback.ACCEPTED -> 0.05
            MoaFeedback.REJECTED -> -0.02
            MoaFeedback.VISITED -> 0.08
            MoaFeedback.LIKED -> 0.15
            MoaFeedback.DISLIKED -> -0.15
        }
    }

    @Synchronized
    fun recordFeedback(
        profileId: String,
        category: String,
        feedback: MoaFeedback
    ): MoaLearnedPreference {

        require(profileId.isNotBlank()) {
            "프로필 ID가 필요합니다."
        }

        val normalizedCategory =
            category.trim().uppercase()

        require(normalizedCategory.isNotBlank()) {
            "장소 카테고리가 필요합니다."
        }

        val current = getPreference(
            profileId,
            normalizedCategory
        )

        val newScore = (
                current.score + feedbackDelta(feedback)
                ).coerceIn(0.0, 1.0)

        val updated = MoaLearnedPreference(
            category = normalizedCategory,
            score = newScore,
            feedbackCount = current.feedbackCount + 1
        )

        val allData = readProfileData(profileId)

        allData.put(
            normalizedCategory,
            JSONObject().apply {
                put("score", updated.score)
                put("feedbackCount", updated.feedbackCount)
            }
        )

        saveProfileData(profileId, allData)

        return updated
    }

    @Synchronized
    fun getPreference(
        profileId: String,
        category: String
    ): MoaLearnedPreference {

        val normalizedCategory =
            category.trim().uppercase()

        val data = readProfileData(profileId)
            .optJSONObject(normalizedCategory)

        return MoaLearnedPreference(
            category = normalizedCategory,
            score = (
                    data?.optDouble("score", defaultScore)
                        ?: defaultScore
                    ).let {
                    if (it.isFinite()) it.coerceIn(0.0, 1.0)
                    else defaultScore
                },
            feedbackCount = (
                    data?.optInt("feedbackCount", 0) ?: 0
                    ).coerceAtLeast(0)
        )
    }

    @Synchronized
    fun getLearnedPreferences(
        profileId: String
    ): Map<String, Double> {

        val data = readProfileData(profileId)
        val result = mutableMapOf<String, Double>()

        val keys = data.keys()

        while (keys.hasNext()) {
            val category = keys.next()
            val item = data.optJSONObject(category)
                ?: continue

            val count = item.optInt("feedbackCount", 0)
            val score = item.optDouble(
                "score",
                defaultScore
            )

            if (count > 0 && score.isFinite()) {
                result[category] =
                    score.coerceIn(0.0, 1.0)
            }
        }

        return result
    }

    @Synchronized
    fun clearLearning(profileId: String) {
        preferences.edit()
            .remove(storageKey(profileId))
            .commit()
    }

    private fun storageKey(profileId: String): String {
        return "profile_$profileId"
    }

    private fun readProfileData(
        profileId: String
    ): JSONObject {

        val raw = preferences.getString(
            storageKey(profileId),
            null
        ) ?: return JSONObject()

        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun saveProfileData(
        profileId: String,
        data: JSONObject
    ) {
        val saved = preferences.edit()
            .putString(
                storageKey(profileId),
                data.toString()
            )
            .commit()

        check(saved) {
            "MOA 학습 데이터 저장에 실패했습니다."
        }
    }
}
