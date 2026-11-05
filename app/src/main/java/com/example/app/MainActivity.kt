
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
import kotlinx.coroutines.launch

import com.example.app.data.UserProfile
import com.example.app.data.UserPreferencesRepository
import com.example.app.ui.profile.ProfileScreen


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement

import com.example.app.vehicle.VehicleRepository


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
            when (currentScreen) {

                "home" -> {
                    RoadTripHome(
                        onStartClick = {

                            /*
                             * DataStore에서 읽은 닉네임으로
                             * 프로필 등록 여부를 판단한다.
                             *
                             * 닉네임이 비어 있으면 신규 사용자,
                             * 값이 있으면 기존 사용자다.
                             */
                            if (profile?.nickname.isNullOrBlank()) {
                                currentScreen = "profile"
                            } else {
                                currentScreen = "saved"
                            }
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

                        Button(
                            onClick = {
                                currentScreen = "home"
                            }
                        ) {
                            Text("처음 화면으로")
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
            .background(Color(0xFF101C30))
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
