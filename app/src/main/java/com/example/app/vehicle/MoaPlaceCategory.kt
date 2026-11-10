
package com.example.app.vehicle

import com.example.app.data.KakaoPlace

object MoaPlaceCategory {

    const val CAFE = "CAFE"
    const val FOOD = "FOOD"
    const val NATURE = "NATURE"
    const val CULTURE = "CULTURE"
    const val ACTIVITY = "ACTIVITY"
    const val SHOPPING = "SHOPPING"
    const val OTHER = "OTHER"

    fun classify(place: KakaoPlace): String {
        return classify(place.name, place.category)
    }

    fun classify(
        name: String,
        category: String
    ): String {
        val categoryResult = classifyText(category)

        if (categoryResult != OTHER) {
            return categoryResult
        }

        return classifyText(name)
    }

    private fun classifyText(text: String): String {
        return when {
            containsAny(
                text,
                "카페", "커피", "디저트", "제과", "베이커리"
            ) -> CAFE

            containsAny(
                text,
                "음식점", "식당", "맛집", "레스토랑",
                "한식", "중식", "일식", "양식", "분식"
            ) -> FOOD

            containsAny(
                text,
                "체험", "액티비티", "레저",
                "놀이공원", "테마파크"
            ) -> ACTIVITY

            containsAny(
                text,
                "공원", "숲", "해변", "바다",
                "수목원", "정원", "폭포", "호수", "둘레길"
            ) -> NATURE

            containsAny(
                text,
                "박물관", "미술관", "전시", "문화재",
                "유적", "역사관", "갤러리"
            ) -> CULTURE

            containsAny(
                text,
                "쇼핑", "백화점", "시장", "아울렛"
            ) -> SHOPPING

            else -> OTHER
        }
    }
    private fun containsAny(
        text: String,
        vararg keywords: String
    ): Boolean {
        return keywords.any { keyword ->
            text.contains(keyword, ignoreCase = true)
        }
    }
}
