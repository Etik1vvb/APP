
package com.example.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.runtime.*

import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme

import kotlinx.coroutines.launch

import com.example.app.data.UserProfile
import com.example.app.data.UserPreferencesRepository

import com.example.app.ui.profile.ProfileScreen

import com.example.app.ui.trip.TripSetupScreen
import com.example.app.ui.trip.TripInfo
import com.example.app.data.TripRepository
import com.example.app.data.TripStatus


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement

import com.example.app.vehicle.VehicleRepository

import com.example.app.ui.home.HomeScreen
import com.example.app.ui.theme.APPTheme

import com.example.app.ui.voice.MicrophoneTest
import com.example.app.ui.voice.AudioRecorderTestScreen
import com.example.app.ui.voice.PleosSttTestScreen
import com.example.app.ui.voice.PleosTtsTestScreen
import com.example.app.ui.voice.MoaVoiceTestScreen


/*
 * ============================================================
 * MainActivity
 *
 * 앱이 실행될 때 가장 먼저 호출되는 Activity.
 *
 * 역할:
 * 1. 사용자 프로필 저장소 준비
 * 2. 현재 화면 상태 관리
 * 3. 시작 화면과 프로필 화면 전환
 * 4. 프로필 저장 버튼을 DataStore에 연결
 * ============================================================
 */


class MainActivity : ComponentActivity() {

    private lateinit var vehicleRepository: VehicleRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vehicleRepository = VehicleRepository(this)
        vehicleRepository.initialize()
        vehicleRepository.checkDriverDoor()

        setContent {

            // 1. 앱의 Context를 가져온다.
            val context = LocalContext.current

            // 2. DataStore Repository를 준비한다.
            val repository = remember(context) {
                UserPreferencesRepository(
                    context.applicationContext
                )
            }

            val tripRepository = remember(context) {
                TripRepository(context.applicationContext)
            }

            // 3. 저장 작업에 사용할 코루틴 범위
            val scope = rememberCoroutineScope()

            /*
             * 4. DataStore에서 프로필을 읽는다.
             *
             * collectAsState()는 Flow를 Compose 상태로
             * 변환하는 함수다.
             *
             * DataStore 값이 변경되면 Compose가
             * 새로운 프로필 정보를 화면에 반영한다.
             *
             * initial = null:
             * 아직 데이터를 읽는 중이라는 의미다.
             */
            val profile by repository.userProfile
                .collectAsState(initial = null)

            val savedTrip by tripRepository.savedTrip
                .collectAsState(initial = null)

            val tripStatus by tripRepository.tripStatus
                .collectAsState(initial = null)

            /*
             * 5. 현재 화면 상태
             *
             * home    : 시작 화면
             * profile : 프로필 등록 화면
             * saved   : 저장된 프로필 확인 화면
             */
            var currentScreen by remember {
                mutableStateOf("home")
            }

            /*
             * 6. 화면 선택
             *
             * when은 조건에 따라 다른 코드를
             * 실행하는 Kotlin 문법이다.
             */
            APPTheme {
                when (currentScreen) {

                    "moa_voice_test" -> {
                        MoaVoiceTestScreen()
                    }

                    "pleos_tts_test" -> {
                        PleosTtsTestScreen()
                    }

                    "record_test" -> {
                        AudioRecorderTestScreen()
                    }

                    "mic_test" -> {
                        MicrophoneTest()
                    }

                    "pleos_stt_test" -> {
                        PleosSttTestScreen()
                    }


                    "home" -> {
                        HomeScreen(
                            nickname = profile?.nickname,
                            destination = savedTrip?.destination,
                            tripStatus = tripStatus,

                            onPrimaryClick = {
                                if (profile?.nickname.isNullOrBlank()) {
                                    currentScreen = "profile"
                                } else if (
                                    savedTrip != null &&
                                    tripStatus != TripStatus.COMPLETED
                                ) {
                                    currentScreen = "saved"
                                } else {
                                    currentScreen = "trip"
                                }
                            },

                            onProfileClick = {
                                currentScreen = "profile"
                            },

                            onHistoryClick = {
                                currentScreen = "moa_voice_test"
                            }
                        )
                    }


                    "profile" -> {
                        ProfileScreen(
                            onSave = {
                                    nickname,
                                    ageGroup,
                                    gender,
                                    preferences ->

                                // 화면에서 받은 값을 객체로 묶는다.
                                val newProfile = UserProfile(
                                    nickname = nickname,
                                    ageGroup = ageGroup,
                                    gender = gender,
                                    preferences = preferences
                                )

                                /*
                                 * DataStore에 저장한다.
                                 *
                                 * 저장이 끝나면 홈 화면으로 이동한다.
                                 */
                                scope.launch {
                                    repository.saveProfile(newProfile)
                                    currentScreen = "home"
                                }
                            }
                        )
                    }

                    "saved" -> {

                        /*
                         * 아직 여행 등록 화면이 없으므로
                         * 저장된 프로필을 임시로 표시한다.
                         */
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(16.dp)
                        ) {

                            Button(
                                onClick = {
                                    currentScreen = "trip"
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("새 여행 등록하기")
                            }

                            Text(
                                text = "저장된 사용자 정보",
                                style = MaterialTheme
                                    .typography.headlineSmall
                            )

                            Text(
                                text = "닉네임: ${profile?.nickname.orEmpty()}"
                            )

                            Text(
                                text = "연령대: ${profile?.ageGroup ?: "미입력"}"
                            )

                            Text(
                                text = "성별: ${profile?.gender ?: "미입력"}"
                            )

                            Text(
                                text = "여행 취향: ${
                                    profile?.preferences
                                        ?.sorted()
                                        ?.joinToString(", ")
                                        ?.ifEmpty { "미선택" }
                                        ?: "미선택"
                                }"
                            )

                            HorizontalDivider()

                            Text(
                                text = "저장된 여행 정보",
                                style = MaterialTheme.typography.headlineSmall
                            )

                            val trip = savedTrip

                            Text(
                                text = when (tripStatus) {
                                    TripStatus.PLANNED -> "여행 상태: 출발 전"
                                    TripStatus.ACTIVE -> "여행 상태: 여행 중"
                                    TripStatus.COMPLETED -> "여행 상태: 여행 종료"
                                    null -> "여행 상태: 등록된 여행 없음"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )

                            if (trip == null) {
                                Text("등록된 여행이 없습니다.")
                            } else {
                                Text("목적지: ${trip.destination}")
                                Text("출발 날짜: ${trip.startDate}")
                                Text("도착 날짜: ${trip.endDate}")
                                Text("동행자: ${trip.companions}")
                                Text("여행 목적: ${trip.purpose}")
                                if (tripStatus == TripStatus.PLANNED) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                tripRepository.startTrip()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("여행 시작하기")
                                    }
                                }
                                if (tripStatus == TripStatus.ACTIVE) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                tripRepository.completeTrip()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("여행 종료하기")
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    currentScreen = "home"
                                }
                            ) {
                                Text("처음 화면으로")
                            }
                        }
                    }


                    "trip" -> {
                        TripSetupScreen(
                            onSave = { trip: TripInfo ->
                                scope.launch {
                                    tripRepository.saveTrip(trip)

                                    android.util.Log.d(
                                        "RoadTripTrip",
                                        "여행 저장 완료: $trip"
                                    )

                                    currentScreen = "saved"
                                }
                            },
                            onBack = {
                                currentScreen = "saved"
                            }
                        )
                    }


                    "history" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.background
                                )
                                .padding(32.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Text(
                                text = "여행 기록",
                                style = MaterialTheme.typography.headlineLarge
                            )

                            Text(
                                text = "여러 여행을 저장하고 조회하는 기능은 " +
                                        "다음 개발 단계에서 구현합니다."
                            )

                            Button(
                                onClick = {
                                    currentScreen = "home"
                                }
                            ) {
                                Text("홈으로 돌아가기")
                            }
                        }
                    }


                }
            }

        }
    }
    override fun onDestroy() {
        if (::vehicleRepository.isInitialized) {
            vehicleRepository.release()
        }
        super.onDestroy()
    }
}



@Composable
fun RoadTripHome(
    onStartClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🚗",
            fontSize = 64.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "떠나볼카",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "여정의 이야기를 들려주는\nAI 여행 도슨트",
            fontSize = 16.sp,
            color = Color(0xFFC5D5E8),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(60.dp))

        Button(
            onClick = onStartClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("여행 시작하기")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "나의 여행 기록",
                color = Color.White
            )
        }
    }
}
