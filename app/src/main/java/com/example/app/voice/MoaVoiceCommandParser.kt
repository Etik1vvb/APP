
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

        val action = when {
            hasAny("그만 읽어", "읽지 마", "음성 중지", "말 그만") ->
                MoaVoiceAction.STOP

            hasAny("다른 곳", "다른 데", "다음 장소", "다음 추천") ->
                MoaVoiceAction.NEXT

            hasAny("여기로 가자", "여기 가자", "여기로 안내",
                "이곳으로 안내", "이 장소로 가자") ->
                MoaVoiceAction.ACCEPT

            hasAny("추천", "찾아줘", "찾아 줘",
                "검색해줘", "검색해 줘") ->
                MoaVoiceAction.RECOMMEND

            hasAny("이야기", "도슨트", "설명해줘",
                "설명해 줘", "역사 알려줘") ->
                MoaVoiceAction.STORY

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
