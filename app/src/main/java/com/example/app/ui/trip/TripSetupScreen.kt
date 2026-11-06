
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
    val purpose: String
)

private enum class VoiceTarget {
    REGION, COMPANIONS, PURPOSE
}

@Composable
fun TripSetupScreen(
    onSave: (TripInfo) -> Unit,
    onBack: () -> Unit
) {
    var region by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var companions by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }

    var voiceTarget by remember {
        mutableStateOf(VoiceTarget.REGION)
    }
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

                val parsed = TripVoiceParser.parse(
                    text = confirmedTranscript,
                    currentStartDate = startDate,
                    currentEndDate = endDate
                )

                parsed.region?.let {
                    region = it
                }

                parsed.startDate?.let {
                    startDate = it
                }

                parsed.endDate?.let {
                    endDate = it
                }

                parsed.companions?.let {
                    companions = it
                }

                parsed.purpose?.let {
                    purpose = it
                }

                if (parsed.needsYearConfirmation) {
                    voiceStatus = "날짜의 연도를 확인해 주세요."
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

    fun startVoiceInput(target: VoiceTarget) {

        if (isListening) {
            voiceManager.stopListening()
            isListening = false
            return
        }

        voiceTarget = target
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
            text = "새로운 여행",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = RoadTripText
        )

        Text(
            text = "지역과 기간을 정해 주세요",
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
                    text = "먼저 입력할 항목을 선택하세요",
                    fontSize = 19.sp,
                    color = RoadTripTextSecondary
                )

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        VoiceTarget.REGION to "지역",
                        VoiceTarget.COMPANIONS to "동행",
                        VoiceTarget.PURPOSE to "취향"
                    ).forEach { (target, label) ->
                        FilterChip(
                            selected = voiceTarget == target,
                            onClick = {
                                voiceTarget = target
                                voiceResult = ""
                            },
                            label = {
                                Text(label, fontSize = 18.sp)
                            }
                        )
                    }
                }

                Button(
                    onClick = {
                        startVoiceInput(voiceTarget)
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

                    Button(
                        onClick = {
                            when (voiceTarget) {
                                VoiceTarget.REGION ->
                                    region = voiceResult
                                VoiceTarget.COMPANIONS ->
                                    companions = voiceResult
                                VoiceTarget.PURPOSE ->
                                    purpose = voiceResult
                            }
                            voiceResult = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                    ) {
                        Text(
                            "선택한 항목에 적용",
                            fontSize = 20.sp
                        )
                    }
                }
            }
        }

        Text(
            text = "여행 기본 정보",
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
            label = "이번 여행 취향",
            value = purpose,
            placeholder = "예: 바다, 맛집, 사진",
            onChange = { purpose = it }
        )

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
                        purpose = purpose.trim()
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
                text = "여행 등록하기",
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
