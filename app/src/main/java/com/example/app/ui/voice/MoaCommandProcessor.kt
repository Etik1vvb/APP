
package com.example.app.voice

enum class MoaCommandType {
    START_TRIP,
    END_TRIP,
    RECOMMEND_PLACE,
    SET_DESTINATION,
    UNKNOWN
}

data class MoaCommand(
    val type: MoaCommandType,
    val originalText: String,
    val destination: String? = null
)

object MoaCommandProcessor {

    fun process(text: String): MoaCommand {
        val normalized = text.trim()
            .replace(" ", "")
            .replace(",", "")
            .replace(".", "")

        val type = when {
            listOf("여행종료", "여행끝", "여행마쳐")
                .any { normalized.contains(it) } ->
                MoaCommandType.END_TRIP

            listOf("여행시작", "여행출발", "여행떠나")
                .any { normalized.contains(it) } ->
                MoaCommandType.START_TRIP

            listOf("추천", "맛집", "관광지", "가볼만한곳")
                .any { normalized.contains(it) } ->
                MoaCommandType.RECOMMEND_PLACE

            normalized.contains("으로가자") ||
                    normalized.contains("로가자") ->
                MoaCommandType.SET_DESTINATION

            else -> MoaCommandType.UNKNOWN
        }

        return MoaCommand(
            type = type,
            originalText = text
        )
    }
}
