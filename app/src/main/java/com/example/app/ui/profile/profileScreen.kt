package com.example.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.app.data.UserProfile

@OptIn(ExperimentalLayoutApi::class)
@Composable

fun ProfileScreen(
    onSave: (nickname: String, ageGroup: String?, gender: String?, preferences: Set<String>) -> Unit,
    initialProfile: UserProfile? = null,
    onSaveProfile: ((UserProfile) -> Unit)? = null,
    onBack: () -> Unit = {}
) {

    var nickname by remember(initialProfile?.id) { mutableStateOf(initialProfile?.nickname.orEmpty()) }
    var ageGroup by remember(initialProfile?.id) { mutableStateOf(initialProfile?.ageGroup) }
    var gender by remember(initialProfile?.id) { mutableStateOf(initialProfile?.gender) }
    var preferences by remember(initialProfile?.id) { mutableStateOf(initialProfile?.preferences ?: emptySet()) }
    var atmospheres by remember(initialProfile?.id) { mutableStateOf(initialProfile?.atmospheres ?: emptySet()) }
    var discoveryStyle by remember(initialProfile?.id) { mutableStateOf(initialProfile?.discoveryStyle.orEmpty()) }
    var detourPreference by remember(initialProfile?.id) { mutableStateOf(initialProfile?.detourPreference.orEmpty()) }
    var recommendationFrequency by remember(initialProfile?.id) { mutableStateOf(initialProfile?.recommendationFrequency.orEmpty()) }

    val interests = listOf("자연·풍경", "맛집·미식", "카페·디저트", "역사·문화", "예술·건축", "액티비티", "쇼핑·시장", "휴식·힐링", "숨은 명소")
    val moods = listOf("조용하고 한적한", "활기찬", "감성적인", "낭만적인", "여유로운", "새롭고 이색적인")

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {


        TextButton(
            onClick = onBack
        ) {
            Text("← 뒤로가기")
        }

        Text(if (initialProfile == null) "나의 여행 취향 설정" else "여행 취향 수정", style = MaterialTheme.typography.headlineSmall)
        Text("취향은 나중에 변경할 수 있습니다.")
        OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text("닉네임 (필수)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Text("연령대 (선택)", style = MaterialTheme.typography.titleMedium)
        listOf("10대", "20대", "30대", "40대 이상").forEach { option ->
            Row { RadioButton(selected = ageGroup == option, onClick = { ageGroup = option }); Text(option, modifier = Modifier.padding(top = 12.dp)) }
        }
        Text("성별 (선택)", style = MaterialTheme.typography.titleMedium)
        listOf("남성", "여성", "응답 안 함").forEach { option ->
            Row { RadioButton(selected = gender == option, onClick = { gender = option }); Text(option, modifier = Modifier.padding(top = 12.dp)) }
        }

        HorizontalDivider()
        Text("1. 관심 있는 여행 활동 (최대 3개)", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            interests.forEach { item ->
                FilterChip(selected = item in preferences, onClick = {
                    preferences = if (item in preferences) preferences - item else if (preferences.size < 3) preferences + item else preferences
                }, label = { Text(item) })
            }
        }
        Text("${preferences.size}/3개 선택", style = MaterialTheme.typography.bodySmall)

        Text("2. 좋아하는 분위기 (최대 2개)", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            moods.forEach { item ->
                FilterChip(selected = item in atmospheres, onClick = {
                    atmospheres = if (item in atmospheres) atmospheres - item else if (atmospheres.size < 2) atmospheres + item else atmospheres
                }, label = { Text(item) })
            }
        }
        Text("${atmospheres.size}/2개 선택", style = MaterialTheme.typography.bodySmall)

        Text("3. 장소 탐색 스타일", style = MaterialTheme.typography.titleMedium)
        listOf("유명 명소 위주", "유명 명소와 숨은 명소 혼합", "숨은 명소 위주", "새로운 곳에 도전").forEach { option ->
            Row { RadioButton(selected = discoveryStyle == option, onClick = { discoveryStyle = option }); Text(option, modifier = Modifier.padding(top = 12.dp)) }
        }
        Text("4. 경로에서 벗어나도 괜찮은 시간", style = MaterialTheme.typography.titleMedium)
        listOf("5분", "10분", "20~30분", "30분 이상").forEach { option ->
            Row { RadioButton(selected = detourPreference == option, onClick = { detourPreference = option }); Text(option, modifier = Modifier.padding(top = 12.dp)) }
        }
        Text("5. AI 추천을 받고 싶은 빈도", style = MaterialTheme.typography.titleMedium)
        listOf("필요할 때만", "적당히", "자주").forEach { option ->
            Row { RadioButton(selected = recommendationFrequency == option, onClick = { recommendationFrequency = option }); Text(option, modifier = Modifier.padding(top = 12.dp)) }
        }

        Button(
            onClick = {
                val updated = (initialProfile ?: UserProfile()).copy(
                    nickname = nickname.trim(), ageGroup = ageGroup, gender = gender,
                    preferences = preferences, atmospheres = atmospheres,
                    discoveryStyle = discoveryStyle, detourPreference = detourPreference,
                    recommendationFrequency = recommendationFrequency
                )
                if (onSaveProfile != null) onSaveProfile(updated)
                else onSave(updated.nickname, updated.ageGroup, updated.gender, updated.preferences)
            },
            enabled = nickname.isNotBlank(), modifier = Modifier.fillMaxWidth()
        ) { Text(if (initialProfile == null) "프로필 저장하기" else "수정 내용 저장") }

    }
}
