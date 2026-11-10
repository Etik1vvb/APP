package com.example.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 프로필 선택 후 일상/여행 모드로 진입하는 화면. */
@Composable
fun ModeSelectionScreen(
    nickname: String?,
    onTravelClick: () -> Unit,
    onDailyClick: () -> Unit,
    onChangeProfile: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("떠나볼카", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (nickname.isNullOrBlank()) "오늘은 어떤 모드로 떠날까요?" else "${nickname}님, 오늘은 어떤 모드로 떠날까요?",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(36.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("🚗 여행모드", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("여행 체크인, 목적지 설정, 이동 중 MOA 선제 추천")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onTravelClick, modifier = Modifier.fillMaxWidth()) {
                    Text("여행모드 시작")
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("☕ 일상모드", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("가까운 카페, 식당, 산책 장소 추천 (개발 예정)")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onDailyClick, modifier = Modifier.fillMaxWidth()) {
                    Text("일상모드 들어가기")
                }
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        TextButton(onClick = onChangeProfile) { Text("다른 프로필 선택") }
    }
}
