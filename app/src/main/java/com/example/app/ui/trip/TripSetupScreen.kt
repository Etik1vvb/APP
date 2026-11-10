package com.example.app.ui.trip


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeParseException


import androidx.compose.ui.platform.LocalContext
import com.example.app.voice.MoaVoiceManager

data class TripInfo(
    val destination: String,
    val startDate: String,
    val endDate: String,
    val companions: String,
    val purpose: String,
    val mood: String = "",
    val desiredExperience: String = "",
    val detourPreference: String = ""
)

// STT 문장을 항목별 핵심 값으로 분류하는 규칙 기반 파서입니다.
private data class CheckinVoiceValues(
    val companions: String? = null,
    val purpose: String? = null,
    val mood: String? = null,
    val experience: String? = null,
    val detour: String? = null
)

private fun classifyCheckinSpeech(input: String): CheckinVoiceValues {
    val t = input.replace(" ", "").lowercase()
    fun containsAny(vararg words: String) = words.any { it in t }

    val companions = when {
        containsAny("혼자", "나홀로", "홀로") -> "혼자"
        containsAny("반려견", "강아지", "반려동물") -> "반려동물"
        containsAny("부모님", "어머니", "아버지") -> "부모님"
        containsAny("아이들", "아이랑", "자녀") -> "아이"
        containsAny("배우자", "남편", "아내") -> "배우자"
        containsAny("연인", "남자친구", "여자친구", "애인") -> "연인"
        containsAny("친구") -> "친구"
        containsAny("가족") -> "가족"
        containsAny("동료", "회사사람") -> "직장 동료"
        else -> null
    }
    val mood = when {
        containsAny("안피곤", "피곤하지않", "안지쳤") -> null
        containsAny("피곤", "지쳤", "지치", "피로", "녹초") -> "피곤"
        containsAny("스트레스", "짜증", "화가나", "화났") -> "스트레스"
        containsAny("우울", "슬퍼", "슬프", "울적") -> "우울"
        containsAny("답답", "갑갑") -> "답답"
        containsAny("설레", "설렘", "두근") -> "설렘"
        containsAny("행복", "기뻐", "기분좋") -> "행복"
        containsAny("신나", "신남", "들떠") -> "신남"
        containsAny("여유", "편안", "느긋") -> "여유"
        containsAny("지루", "심심") -> "지루함"
        else -> null
    }
    val purpose = when {
        containsAny("기분전환", "바람쐬", "머리식히") -> "기분 전환"
        containsAny("맛집탐방", "먹방", "맛집투어") -> "맛집 탐방"
        containsAny("쉬고싶", "푹쉬", "휴식", "힐링") -> "휴식"
        containsAny("드라이브") -> "드라이브"
        containsAny("데이트") -> "데이트"
        containsAny("추억", "기념") -> "추억 만들기"
        containsAny("축하", "생일", "기념일") -> "축하"
        containsAny("관광", "구경") -> "관광"
        else -> null
    }
    val experience = when {
        containsAny("바다", "해변", "해수욕장") -> "바다 감상"
        containsAny("숨은명소", "숨겨진", "사람없는곳") -> "숨은 명소"
        containsAny("사진", "포토존", "인생샷") -> "사진 명소"
        containsAny("카페", "커피", "디저트") -> "카페"
        containsAny("맛집", "맛있는거", "맛있는것") -> "맛집"
        containsAny("산책", "걷고싶") -> "산책"
        containsAny("야경", "밤풍경") -> "야경"
        containsAny("등산", "산에가") -> "등산"
        containsAny("박물관", "유적", "문화재") -> "역사·문화"
        containsAny("쇼핑", "시장") -> "쇼핑"
        containsAny("체험", "액티비티") -> "액티비티"
        else -> null
    }
    return CheckinVoiceValues(companions, purpose, mood, experience, normalizeDetourSpeech(input))
}

@Composable
fun TripSetupScreen(
    onSave: (TripInfo) -> Unit,
    onBack: () -> Unit,
    initialTrip: TripInfo? = null
) {
    // Editing preserves the existing trip ID/status in the repository.
    // Remember keyed by initialTrip to reload fields when changing trips.
    var region by remember(initialTrip) { mutableStateOf(initialTrip?.destination.orEmpty()) }
    var startDate by remember(initialTrip) { mutableStateOf(initialTrip?.startDate.orEmpty()) }
    var endDate by remember(initialTrip) { mutableStateOf(initialTrip?.endDate.orEmpty()) }
    var companions by remember(initialTrip) { mutableStateOf(initialTrip?.companions.orEmpty()) }
    var purpose by remember(initialTrip) { mutableStateOf(initialTrip?.purpose.orEmpty()) }
    var mood by remember(initialTrip) { mutableStateOf(initialTrip?.mood.orEmpty()) }
    var desiredExperience by remember(initialTrip) { mutableStateOf(initialTrip?.desiredExperience.orEmpty()) }
    var detourPreference by remember(initialTrip) { mutableStateOf(initialTrip?.detourPreference.orEmpty()) }

    var voiceResult by remember { mutableStateOf("") }
    var voiceError by remember { mutableStateOf("") }


    val context = LocalContext.current

    val voiceManager = remember(context) {
        MoaVoiceManager(context)
    }

    var voiceStatus by remember {
        mutableStateOf("음성 기능 준비 중")
    }

    var isListening by remember {
        mutableStateOf(false)
    }

    var liveTranscript by remember {
        mutableStateOf("")
    }

    var confirmedTranscript by remember {
        mutableStateOf("")
    }

    var currentTranscript by remember {
        mutableStateOf("")
    }

    DisposableEffect(voiceManager) {

        voiceManager.onStatusChanged = { newStatus ->
            voiceStatus = newStatus
        }

        voiceManager.onVoiceLevelChanged = { rms ->
            android.util.Log.d(
                "MOA_VOICE",
                "RMS: $rms"
            )
        }

        voiceManager.onSpeechBoundaryChanged = { on, off ->
            android.util.Log.d(
                "MOA_VOICE",
                "EPD: on=$on, off=$off"
            )
        }

        voiceManager.onRecognized = { text, completed ->

            val recognizedText = text.trim()

            if (recognizedText.isNotBlank()) {

                if (completed) {
                    // 확정된 문장을 이전 문장 뒤에 누적
                    confirmedTranscript = listOf(
                        confirmedTranscript,
                        recognizedText
                    )
                        .filter { it.isNotBlank() }
                        .joinToString(" ")

                    currentTranscript = ""

                } else {
                    // 현재 발화의 중간 인식 결과만 갱신
                    currentTranscript = recognizedText
                }

                // 확정된 문장 + 현재 발화를 함께 표시
                liveTranscript = listOf(
                    confirmedTranscript,
                    currentTranscript
                )
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            }

            if (completed && recognizedText.isNotBlank()) {

                voiceResult = confirmedTranscript
                voiceError = ""

                // 하나의 발화에서 여러 항목을 동시에 추출합니다.
                val parsed = TripVoiceParser.parse(
                    text = recognizedText,
                    currentStartDate = startDate,
                    currentEndDate = endDate
                )
                parsed.region?.let { region = it }
                parsed.startDate?.let { startDate = it }
                parsed.endDate?.let { endDate = it }
                if (parsed.needsYearConfirmation) {
                    voiceStatus = "날짜의 연도를 확인해 주세요."
                }
                val classified = classifyCheckinSpeech(recognizedText)
                classified.companions?.let { companions = it }
                classified.purpose?.let { purpose = it }
                classified.mood?.let { mood = it }
                classified.experience?.let { desiredExperience = it }
                classified.detour?.let { detourPreference = it }
                if (parsed.region == null && parsed.startDate == null &&
                    parsed.endDate == null && classified.companions == null &&
                    classified.purpose == null && classified.mood == null &&
                    classified.experience == null && classified.detour == null) {
                    voiceError = "분류할 항목을 찾지 못했어요. 직접 입력하거나 더 구체적으로 말해 주세요."
                }
            }
        }

        voiceManager.onRecognitionEnded = {
            isListening = false
        }

        voiceManager.initialize()

        onDispose {
            voiceManager.release()
        }
    }

    fun startVoiceInput() {

        if (isListening) {
            voiceManager.stopListening()
            isListening = false
            return
        }

        voiceResult = ""
        voiceError = ""
        liveTranscript = ""
        confirmedTranscript = ""
        currentTranscript = ""

        isListening = true
        voiceManager.startListening()
    }

    val datesValid = try {
        val start = LocalDate.parse(startDate.trim())
        val end = LocalDate.parse(endDate.trim())
        !end.isBefore(start)
    } catch (e: DateTimeParseException) {
        false
    }

    val canSave = region.isNotBlank() && datesValid

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RoadTripBackground)
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Text(
            text = if (initialTrip == null) "새로운 여행" else "여행 수정",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = RoadTripText
        )

        Text(
            text = if (initialTrip == null) "지역과 기간을 정해 주세요" else "등록된 여행 정보를 수정해 주세요",
            fontSize = 24.sp,
            color = RoadTripTextSecondary
        )

        // 큰 음성 입력 영역
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFE5DEFF)
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "말로 입력하기",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoadTripText
                )

                Text(
                    text = "항목 선택 없이 자유롭게 말씀해 주세요",
                    fontSize = 19.sp,
                    color = RoadTripTextSecondary
                )

                Button(
                    onClick = {
                        startVoiceInput()
                    },
                    modifier = Modifier.size(112.dp),
                    shape = RoundedCornerShape(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = if (isListening) "⏹️" else "🎙️",
                        fontSize = 48.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Text(
                    text = if (isListening) {
                        "음성을 듣고 있어요"
                    } else {
                        voiceStatus
                    },
                    fontSize = 20.sp,
                    color = RoadTripText
                )

                Text(
                    text = if (liveTranscript.isNotBlank()) {
                        liveTranscript
                    } else if (isListening) {
                        "말씀해 주세요..."
                    } else {
                        "인식된 음성이 여기에 표시됩니다."
                    },
                    fontSize = 18.sp,
                    color = RoadTripText
                )

                if (voiceError.isNotBlank()) {
                    Text(
                        text = voiceError,
                        fontSize = 18.sp,
                        color = RoadTripError
                    )
                }

                if (voiceResult.isNotBlank()) {
                    Text(
                        text = "인식 결과: $voiceResult",
                        fontSize = 21.sp,
                        color = RoadTripText
                    )
                }
            }
        }

        Text(
            text = "여행 체크인",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = RoadTripText
        )

        TripInput(
            label = "여행 지역",
            value = region,
            placeholder = "예: 강원도",
            onChange = { region = it }
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TripInput(
                    label = "출발 날짜",
                    value = startDate,
                    placeholder = "2026-11-10",
                    onChange = { startDate = it }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                TripInput(
                    label = "종료 날짜",
                    value = endDate,
                    placeholder = "2026-11-12",
                    onChange = { endDate = it }
                )
            }
        }

        if ((startDate.isNotBlank() ||
                    endDate.isNotBlank()) && !datesValid) {
            Text(
                text = "날짜를 YYYY-MM-DD 형식으로 입력하고 " +
                        "종료일을 확인해 주세요.",
                fontSize = 17.sp,
                color = RoadTripError
            )
        }

        TripInput(
            label = "동행자",
            value = companions,
            placeholder = "예: 친구 3명",
            onChange = { companions = it }
        )

        TripInput(
            label = "이번 여행 목적",
            value = purpose,
            placeholder = "예: 휴식, 맛집 탐방, 기분 전환",
            onChange = { purpose = it }
        )

        TripInput(
            label = "지금 기분",
            value = mood,
            placeholder = "예: 설렘, 여유로움, 피로함",
            onChange = { mood = it }
        )

        TripInput(
            label = "이번 여행에서 원하는 경험",
            value = desiredExperience,
            placeholder = "예: 바다 감상, 숨은 명소, 카페",
            onChange = { desiredExperience = it }
        )

        Text(
            text = "오늘 가능한 우회 시간",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = RoadTripText
        )

        listOf("우회를 원하지 않음", "5분 이내", "10분 정도", "20~30분", "특별한 장소라면 더 멀리도").forEach { option ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = detourPreference == option,
                    onClick = { detourPreference = option }
                )
                Text(
                    text = option,
                    fontSize = 19.sp,
                    color = RoadTripText
                )
            }
        }

        TextButton(onClick = { detourPreference = "" }) {
            Text("우회 시간 미정")
        }

        Text(
            text = "세부 목적지는 여행 중에도 변경할 수 있어요.",
            fontSize = 19.sp,
            color = RoadTripTextSecondary
        )

        Button(
            onClick = {
                onSave(
                    TripInfo(
                        destination = region.trim(),
                        startDate = startDate.trim(),
                        endDate = endDate.trim(),
                        companions = companions.trim(),
                        purpose = purpose.trim(),
                        mood = mood.trim(),
                        desiredExperience = desiredExperience.trim(),
                        detourPreference = detourPreference
                    )
                )
            },
            enabled = canSave,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                text = if (initialTrip == null) "여행 등록하기" else "수정 내용 저장",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                text = "뒤로 가기",
                fontSize = 21.sp
            )
        }
    }
}

@Composable
private fun TripInput(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = {
            Text(label, fontSize = 19.sp)
        },
        placeholder = {
            Text(placeholder, fontSize = 18.sp)
        },
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 23.sp,
            color = RoadTripText
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp),
        singleLine = true,
        shape = RoundedCornerShape(16.dp)
    )
}

// Match speech to one of the exact radio-button values.
private fun normalizeDetourSpeech(text: String): String? {
    val spoken = text.replace(" ", "").replace("~", "-").lowercase()
    return when {
        listOf("우회안", "우회하지", "우회싫", "우회원하지", "돌아가기싫", "돌아가지않", "돌아가지마", "돌아가기원치", "우회없", "0분", "영분", "바로가", "곧장가", "직진만").any { it in spoken } -> "우회를 원하지 않음"
        listOf("20", "30", "이십", "삼십").any { it in spoken } -> "20~30분"
        listOf("10", "십분").any { it in spoken } -> "10분 정도"
        listOf("5분", "오분").any { it in spoken } -> "5분 이내"
        listOf("멀리", "제한없", "상관없", "특별한장소").any { it in spoken } -> "특별한 장소라면 더 멀리도"
        else -> null
    }
}
