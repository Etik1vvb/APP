package com.example.app.vehicle

import com.example.app.data.KakaoPlace

data class MoaScoreBreakdown(
    val profile: Double,
    val purpose: Double,
    val experience: Double,
    val mood: Double,
    val accessibility: Double,
    val diversity: Double,
    // 기존 필드를 유지하면서 학습 점수만 독립적으로 추가
    val learning: Double = 0.0
) {
    val total: Double
        get() = profile + purpose + experience + mood +
                accessibility + diversity + learning
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
    val learnedPreferences: Map<String, Double> = emptyMap()
)

class MoaPlaceScorer {

    // 최대 100점: 프로필 30 + 여행 목적 25 + 경험 10 + 분위기 5 + 학습 20 + 거리 10
    fun score(
        place: KakaoPlace,
        context: MoaScoringContext
    ): MoaScoredPlace {
        val category = MoaPlaceCategory.classify(place)

        val profileMatch = matchAny(
            category,
            context.profilePreferences + context.profileAtmospheres
        )
        val profileScore = 30.0 * profileMatch

        val purposeScore = 25.0 * matchText(category, context.tripPurpose)
        val experienceScore = 10.0 * matchText(category, context.desiredExperience)
        val moodScore = 5.0 * matchText(category, context.tripMood)

        // 학습 이력이 없으면 중립(0.5). 기존 Learner가 0~1 범위로 반환한다는 전제.
        val learnedMatch = (context.learnedPreferences[category] ?: 0.5)
            .coerceIn(0.0, 1.0)
        val learningScore = 20.0 * learnedMatch

        val distance = distanceMeters(
            context.latitude,
            context.longitude,
            place.latitude,
            place.longitude
        )
        // 주변 3km 검색 후보 안에서 거리 점수가 적절히 구분되도록 6km 감쇠.
        val accessibilityScore = 10.0 *
                (1.0 - distance / 6000.0).coerceIn(0.0, 1.0)

        // 방문 이력에 기반한 다양성 점수는 아직 구현하지 않았으므로 가산하지 않음.
        val breakdown = MoaScoreBreakdown(
            profile = profileScore,
            purpose = purposeScore,
            experience = experienceScore,
            mood = moodScore,
            accessibility = accessibilityScore,
            diversity = 0.0,
            learning = learningScore
        )

        val reasons = buildList {
            if (profileMatch >= 0.8) add("기본 취향과 잘 맞는 장소")
            if (purposeScore >= 20.0) add("이번 여행 목적에 적합")
            if (experienceScore >= 8.0) add("원하는 경험과 일치")
            if (context.learnedPreferences.containsKey(category) && learnedMatch >= 0.7) {
                add("과거 선택을 통해 선호도가 높아진 유형")
            }
            if (distance <= 1000.0) add("현재 위치에서 1km 이내")
        }

        return MoaScoredPlace(
            place = place,
            score = breakdown,
            reasons = reasons
        )
    }

    private fun matchAny(category: String, values: Set<String>): Double {
        if (values.isEmpty()) return 0.5
        return values.maxOf { matchText(category, it) }
    }

    private fun matchText(category: String, value: String): Double {
        if (value.isBlank()) return 0.5
        val text = value.lowercase()
        val code = category.lowercase()

        // 프로필에 CAFE/CULTURE 같은 카테고리 코드가 저장된 경우도 인식.
        if (text.contains(code) && code.isNotBlank()) return 1.0

        val keywords = when (category.uppercase()) {
            "CAFE" -> listOf("카페", "커피", "디저트", "휴식", "티타임")
            "FOOD" -> listOf("맛집", "음식", "식사", "먹거리", "미식", "요리", "한식")
            "NATURE" -> listOf("자연", "힐링", "풍경", "산책", "숲", "바다", "호수", "공원")
            "CULTURE" -> listOf("문화", "예술", "전시", "역사", "유적", "궁궐", "박물관", "미술관", "전통", "공연")
            "ACTIVITY" -> listOf("체험", "활동", "모험", "액티비티", "레저", "스포츠")
            "SHOPPING" -> listOf("쇼핑", "시장", "구경", "기념품", "백화점")
            else -> emptyList()
        }

        return if (keywords.any { text.contains(it) }) 1.0 else 0.2
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

        val a = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(Math.toRadians(lat1)) *
                kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2).let { it * it }

        return 2 * earthRadius * kotlin.math.atan2(
            kotlin.math.sqrt(a.coerceIn(0.0, 1.0)),
            kotlin.math.sqrt((1.0 - a).coerceAtLeast(0.0))
        )
    }
}
