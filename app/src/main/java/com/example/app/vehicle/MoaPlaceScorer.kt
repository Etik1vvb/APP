
package com.example.app.vehicle

import com.example.app.data.KakaoPlace
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class MoaScoreBreakdown(
    val profile: Double,
    val purpose: Double,
    val experience: Double,
    val mood: Double,
    val accessibility: Double,
    val diversity: Double,
    val learning: Double = 0.0,
    val activeWeight: Double = 100.0
) {
    val total: Double
        get() {
            if (activeWeight <= 0.0) return 0.0

            val earned =
                profile + purpose + experience +
                        mood + accessibility + diversity + learning

            return (earned / activeWeight * 100.0)
                .coerceIn(0.0, 100.0)
        }
}

data class MoaScoredPlace(
    val place: KakaoPlace,
    val score: MoaScoreBreakdown,
    val reasons: List<String>
)

data class MoaScoringContext(
    val profilePreferences: Set<String>,
    val profileAtmospheres: Set<String>,
    val tripPurpose: String,
    val desiredExperience: String,
    val tripMood: String,
    val latitude: Double,
    val longitude: Double,
    val learnedPreferences: Map<String, Double> = emptyMap(),
    val requestedKeyword: String = ""
)

class MoaPlaceScorer {

    fun score(
        place: KakaoPlace,
        context: MoaScoringContext
    ): MoaScoredPlace {

        val category = scoringCategory(place)

        android.util.Log.e(
            "MOA_SCORER_CHECK",
            """
    VERSION = 20261112_V2
    장소 = ${place.name}
    분류 = [$category]
    프로필 = ${context.profilePreferences}

    FOOD + 음식 = ${matchText("FOOD", "음식")}
    FOOD + 맛집 = ${matchText("FOOD", "맛집")}
    CAFE + 카페 = ${matchText("CAFE", "카페")}

    실제 프로필 매칭 =
        ${
                context.profilePreferences.map {
                    "$it=${matchText(category, it)}"
                }
            }

    여행 목적 = [${context.tripPurpose}]
    원하는 경험 = [${context.desiredExperience}]
    현재 기분 = [${context.tripMood}]
    요청 = [${context.requestedKeyword}]
    """.trimIndent()
        )

        val originalCategory = MoaPlaceCategory.classify(place)

        // 1. 프로필 취향: 최대 30점
        val hasProfile = context.profilePreferences.any {
            it.isNotBlank()
        }

        val profileMatch =
            if (hasProfile) {
                context.profilePreferences.maxOf {
                    matchText(category, it)
                }
            } else {
                0.0
            }

        val profileScore = 30.0 * profileMatch

        // 2. 사용자가 직접 말한 조건을 우선
        val purposeInput =
            context.requestedKeyword.trim().ifEmpty {
                context.tripPurpose.trim()
            }

        val purposeScore =
            if (purposeInput.isBlank()) 0.0
            else 25.0 * matchText(category, purposeInput)

        // 3. 입력하지 않은 정보에는 점수를 부여하지 않음
        val experienceScore =
            if (context.desiredExperience.isBlank()) 0.0
            else 10.0 * matchText(
                category,
                context.desiredExperience
            )

        val moodScore =
            if (context.tripMood.isBlank()) 0.0
            else 5.0 * matchText(
                category,
                context.tripMood
            )

        // 4. 기존 학습 데이터 호환
        val learnedValue =
            context.learnedPreferences[category]
                ?: if (originalCategory != MoaPlaceCategory.OTHER) {
                    context.learnedPreferences[originalCategory]
                } else {
                    null
                }

        val learnedMatch =
            (learnedValue ?: 0.5).coerceIn(0.0, 1.0)

        val learningScore = 20.0 * learnedMatch

        // 5. 직선거리 점수
        val distance = distanceMeters(
            context.latitude,
            context.longitude,
            place.latitude,
            place.longitude
        )

        // 50m 이내의 작은 거리 차이는 무시
        val adjustedDistance =
            (distance - 50.0).coerceAtLeast(0.0)

        val normalizedDistance =
            (adjustedDistance / 2950.0)
                .coerceIn(0.0, 1.0)

        val accessibilityScore =
            10.0 * (1.0 - sqrt(normalizedDistance))

        // 6. 실제 평가 가능한 항목만 분모에 포함
        val activeWeight =
            (if (hasProfile) 30.0 else 0.0) +
                    (if (purposeInput.isNotBlank()) 25.0 else 0.0) +
                    (if (context.desiredExperience.isNotBlank()) 10.0 else 0.0) +
                    (if (context.tripMood.isNotBlank()) 5.0 else 0.0) +
                    20.0 + 10.0

        val breakdown = MoaScoreBreakdown(
            profile = profileScore,
            purpose = purposeScore,
            experience = experienceScore,
            mood = moodScore,
            accessibility = accessibilityScore,
            diversity = 0.0,
            learning = learningScore,
            activeWeight = activeWeight
        )

        val reasons = buildList {
            if (profileMatch >= 0.8) {
                add("평소 관심사와 잘 맞는 장소")
            }

            if (
                purposeInput.isNotBlank() &&
                purposeScore >= 20.0
            ) {
                add("요청한 목적에 적합")
            }

            if (experienceScore >= 8.0) {
                add("원하는 경험과 일치")
            }

            if (learnedValue != null && learnedMatch >= 0.7) {
                add("과거 선택에서 선호도가 높은 장소 유형")
            }

            if (distance <= 1000.0) {
                add("현재 위치에서 1km 이내")
            }
        }

        if (context.requestedKeyword.isNotBlank()) {
            android.util.Log.d(
                "MOA_SCORE_FINAL",
                "장소=${place.name}, " +
                        "분류=$category, " +
                        "요청=$purposeInput, " +
                        "프로필=$profileScore, " +
                        "목적=$purposeScore, " +
                        "경험=$experienceScore, " +
                        "기분=$moodScore, " +
                        "학습=$learningScore, " +
                        "거리=$accessibilityScore, " +
                        "배점=$activeWeight, " +
                        "최종=${breakdown.total}"
            )
        }

        return MoaScoredPlace(
            place = place,
            score = breakdown,
            reasons = reasons
        )
    }

    private fun scoringCategory(place: KakaoPlace): String {
        val category = place.category.lowercase()

        return when {
            listOf(
                "카페", "커피", "베이커리",
                "제과", "빵집"
            ).any { it in category } -> "CAFE"

            listOf(
                "음식점", "식당", "한식", "중식",
                "일식", "양식", "분식", "횟집",
                "초밥", "패스트푸드"
            ).any { it in category } -> "FOOD"

            else -> MoaPlaceCategory.classify(place)
        }
    }

    private fun matchText(
        category: String,
        value: String
    ): Double {
        if (value.isBlank()) return 0.0

        val code = category.trim().uppercase()
        val text = value.trim().lowercase()
            .replace(Regex("\\s+"), "")

        val keywords = when (code) {
            "FOOD" -> listOf(
                "food", "음식", "맛집", "식당",
                "식사", "먹거리", "미식",
                "요리", "한식", "중식",
                "일식", "양식", "분식"
            )

            "CAFE" -> listOf(
                "cafe", "카페", "커피",
                "디저트", "베이커리", "빵집",
                "티타임"
            )

            "NATURE" -> listOf(
                "nature", "자연", "힐링", "풍경",
                "산책", "숲", "바다", "호수", "공원"
            )

            "CULTURE" -> listOf(
                "culture", "문화", "예술", "전시",
                "역사", "유적", "궁궐",
                "박물관", "미술관", "전통", "공연"
            )

            "ACTIVITY" -> listOf(
                "activity", "체험", "활동",
                "모험", "액티비티", "레저", "스포츠"
            )

            "SHOPPING" -> listOf(
                "shopping", "쇼핑", "시장",
                "구경", "기념품", "백화점"
            )

            else -> listOf(code.lowercase())
        }

        return if (keywords.any { text.contains(it) }) {
            1.0
        } else {
            0.2
        }
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
            sin(dLat / 2).let { it * it } +
                    cos(Math.toRadians(lat1)) *
                    cos(Math.toRadians(lat2)) *
                    sin(dLon / 2).let { it * it }

        return 2.0 * earthRadius * atan2(
            sqrt(a.coerceIn(0.0, 1.0)),
            sqrt((1.0 - a).coerceAtLeast(0.0))
        )
    }
}
