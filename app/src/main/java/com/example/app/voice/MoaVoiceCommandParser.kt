
package com.example.app.voice

enum class MoaVoiceAction {
    RECOMMEND,
    STORY,
    ACCEPT,
    NEXT,
    STOP,
    UNKNOWN
}

data class MoaVoiceCommand(
    val action: MoaVoiceAction,
    val originalText: String,
    val description: String
)

object MoaVoiceCommandParser {

    fun searchKeyword(text: String): String {
        val cleaned = text
            .replace(Regex("""모아야[,\s]*"""), "")
            .replace(
                Regex("""추천해\s*(줘|주세요)|추천|찾아\s*(줘|주세요)|검색해\s*(줘|주세요)"""),
                ""
            )
            .replace(Regex("""근처|주변|가까운"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        return when {
            cleaned.contains("맛집") -> "맛집"
            cleaned.contains("카페") -> "카페"
            cleaned.contains("박물관") -> "박물관"
            cleaned.contains("바다") -> "해변"
            cleaned.contains("관광") -> "관광명소"
            cleaned.isBlank() -> "관광명소"
            cleaned.contains("휴게소") -> "휴게소"
            cleaned.contains("주유소") -> "주유소"
            cleaned.contains("충전소") -> "전기차 충전소"
            cleaned.contains("주차장") -> "주차장"
            cleaned.contains("박물관") -> "박물관"
            cleaned.contains("유적지") -> "유적지"
            cleaned.contains("전망대") -> "전망대"
            else -> cleaned
        }
    }

    fun parse(text: String): MoaVoiceCommand {
        val input = text.trim()
        val normalized = input
            .lowercase()
            .replace(Regex("[,.!?]"), "")
            .replace(" ", "")

        fun hasAny(vararg words: String): Boolean =
            words.any { normalized.contains(it.replace(" ", "")) }


        val placeKeywords = listOf(
            "휴게소", "쉼터",
            "주유소", "충전소",
            "주차장", "화장실",
            "맛집", "식당", "카페",
            "박물관", "미술관",
            "유적지", "역사 명소",
            "관광지", "전망대",
            "공원", "산책로",
            "병원", "약국"
        )

        val hasPlaceKeyword = placeKeywords.any {
            normalized.contains(it.replace(" ", ""))
        }

        val hasLocationQuestion = hasAny(
            "어디야", "어딨어",
            "어디 있어", "어디 있니",
            "어디 있나요", "어디에 있어",
            "어디인가", "어디지",
            "근처", "주변", "가까운",
            "위치 알려줘"
        )

        val action = when {

            hasAny(
                "그만 읽어", "읽지 마",
                "음성 중지", "말 그만"
            ) -> MoaVoiceAction.STOP

            hasAny(
                "다른 곳", "다른 데",
                "다음 장소", "다음 추천"
            ) -> MoaVoiceAction.NEXT

            hasAny(
                "여기로 가자", "여기 가자",
                "여기로 안내", "이곳으로 안내",
                "이 장소로 가자"
            ) -> MoaVoiceAction.ACCEPT

            hasAny(
                "이야기", "도슨트",
                "설명해줘", "설명해 줘",
                "역사 알려줘"
            ) -> MoaVoiceAction.STORY

            hasAny(
                "추천", "찾아줘", "찾아 줘",
                "검색해줘", "검색해 줘"
            ) || (hasPlaceKeyword && hasLocationQuestion)
                -> MoaVoiceAction.RECOMMEND

            else -> MoaVoiceAction.UNKNOWN
        }


        val description = when (action) {
            MoaVoiceAction.RECOMMEND -> "장소 추천 요청"
            MoaVoiceAction.STORY -> "장소 이야기 요청"
            MoaVoiceAction.ACCEPT -> "목적지 이동 요청"
            MoaVoiceAction.NEXT -> "다른 장소 추천 요청"
            MoaVoiceAction.STOP -> "음성 재생 중지 요청"
            MoaVoiceAction.UNKNOWN -> "명령을 분류하지 못했습니다"
        }

        return MoaVoiceCommand(
            action = action,
            originalText = input,
            description = description
        )
    }
}
