
package com.example.app.ui.trip

import java.time.DateTimeException
import java.time.LocalDate

data class ParsedTripVoice(
    val region: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val companions: String? = null,
    val purpose: String? = null,
    val needsYearConfirmation: Boolean = false
)

object TripVoiceParser {

    private val regions = listOf(
        "서울", "부산", "대구", "인천",
        "광주", "대전", "울산", "세종",
        "제주도", "제주", "강원도", "강릉",
        "속초", "경주", "전주", "여수",
        "포항", "춘천", "통영", "거제",
        "수원", "가평", "양양"
    )

    private val purposes = listOf(
        "액티비티", "맛집", "힐링",
        "관광", "사진", "캠핑",
        "드라이브", "자연", "바다",
        "쇼핑", "휴식"
    )

    fun parse(
        text: String,
        today: LocalDate = LocalDate.now(),
        currentStartDate: String? = null,
        currentEndDate: String? = null
    ): ParsedTripVoice {

        val normalized = text.trim()

        // 여러 후보가 있으면 문장에서 마지막으로 언급된 항목 사용
        val region = regions
            .mapNotNull { candidate ->
                val index = normalized.lastIndexOf(candidate)
                if (index >= 0) index to candidate else null
            }
            .maxByOrNull { it.first }
            ?.second
            ?.let { if (it == "제주") "제주도" else it }

        val purpose = purposes
            .mapNotNull { candidate ->
                val index = normalized.lastIndexOf(candidate)
                if (index >= 0) index to candidate else null
            }
            .maxByOrNull { it.first }
            ?.second

        val companionPattern = Regex(
            "(친구|가족|연인|부모님|혼자|동료)(?:\\s*\\d+명)?"
        )

        val companions = companionPattern
            .findAll(normalized)
            .lastOrNull()
            ?.value

        var startDate: String? = null
        var endDate: String? = null
        var needsYearConfirmation = false

        val existingStart = try {
            currentStartDate
                ?.takeIf { it.isNotBlank() }
                ?.let { LocalDate.parse(it) }
        } catch (_: DateTimeException) {
            null
        }

        val existingEnd = try {
            currentEndDate
                ?.takeIf { it.isNotBlank() }
                ?.let { LocalDate.parse(it) }
        } catch (_: DateTimeException) {
            null
        }

        // 10월 11일부터 13일까지
        // 2027년 10월 11일부터 13일까지
        // 10월 11일부터 11월 2일까지
        val fullDatePattern = Regex(
            "(?:(\\d{4})년\\s*)?" +
                    "(\\d{1,2})월\\s*" +
                    "(\\d{1,2})일\\s*" +
                    "(?:부터|에서|~|〜|-)\\s*" +
                    "(?:(\\d{1,2})월\\s*)?" +
                    "(\\d{1,2})일(?:까지)?"
        )

        // 기존 날짜의 월을 참고하는 수정 명령
        // 12일부터 15일까지
        val dayOnlyPattern = Regex(
            "(\\d{1,2})일\\s*" +
                    "(?:부터|에서|~|〜|-)\\s*" +
                    "(\\d{1,2})일(?:까지)?"
        )

        val fullMatch = fullDatePattern.find(normalized)

        if (fullMatch != null) {
            try {
                val explicitYear =
                    fullMatch.groupValues[1].toIntOrNull()

                val startMonth =
                    fullMatch.groupValues[2].toInt()

                val startDay =
                    fullMatch.groupValues[3].toInt()

                val endMonth =
                    fullMatch.groupValues[4]
                        .toIntOrNull() ?: startMonth

                val endDay =
                    fullMatch.groupValues[5].toInt()

                var year = explicitYear ?: today.year

                var start = LocalDate.of(
                    year, startMonth, startDay
                )

                if (explicitYear == null && start.isBefore(today)) {
                    year++
                    start = LocalDate.of(
                        year, startMonth, startDay
                    )
                }

                val endYear =
                    if (endMonth < startMonth) year + 1
                    else year

                val end = LocalDate.of(
                    endYear, endMonth, endDay
                )

                if (!end.isBefore(start)) {
                    startDate = start.toString()
                    endDate = end.toString()
                    needsYearConfirmation = explicitYear == null
                }

            } catch (_: DateTimeException) {
                // 잘못된 날짜는 반영하지 않음
            } catch (_: NumberFormatException) {
                // 숫자 변환 실패 시 반영하지 않음
            }
        } else {
            val dayMatch = dayOnlyPattern.find(normalized)

            if (dayMatch != null && existingStart != null) {
                try {
                    val startDay = dayMatch.groupValues[1].toInt()
                    val endDay = dayMatch.groupValues[2].toInt()

                    val start = existingStart.withDayOfMonth(startDay)

                    var end = start.withDayOfMonth(endDay)

                    if (end.isBefore(start)) {
                        end = end.plusMonths(1)
                    }

                    startDate = start.toString()
                    endDate = end.toString()

                } catch (_: DateTimeException) {
                    // 날짜가 유효하지 않으면 기존 값 유지
                }
            }
        }

        return ParsedTripVoice(
            region = region,
            startDate = startDate,
            endDate = endDate,
            companions = companions,
            purpose = purpose,
            needsYearConfirmation = needsYearConfirmation
        )
    }
}
