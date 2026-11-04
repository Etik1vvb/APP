
package com.example.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProfileScreen(
    onSave: (
        nickname: String,
        ageGroup: String?,
        gender: String?,
        preferences: Set<String>
    ) -> Unit
) {
    var nickname by remember { mutableStateOf("") }
    var ageGroup by remember { mutableStateOf<String?>(null) }
    var gender by remember { mutableStateOf<String?>(null) }
    var preferences by remember {
        mutableStateOf(setOf<String>())
    }

    val travelStyles = listOf(
        "바다", "맛집", "자연",
        "역사·문화", "액티비티", "휴식"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "여행 취향을 알려주세요",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "처음 한 번만 입력하면 돼요."
        )

        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("닉네임") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text("연령대 (선택)")

        listOf("10대", "20대", "30대", "40대 이상").forEach {
                option ->
            Row {
                RadioButton(
                    selected = ageGroup == option,
                    onClick = { ageGroup = option }
                )
                Text(option)
            }
        }

        Text("성별 (선택)")

        listOf("남성", "여성", "응답 안 함").forEach {
                option ->
            Row {
                RadioButton(
                    selected = gender == option,
                    onClick = { gender = option }
                )
                Text(option)
            }
        }

        Text("선호하는 여행 스타일 (복수 선택)")

        travelStyles.forEach { style ->
            Row {
                Checkbox(
                    checked = style in preferences,
                    onCheckedChange = { checked ->
                        preferences =
                            if (checked) preferences + style
                            else preferences - style
                    }
                )
                Text(style)
            }
        }

        Button(
            onClick = {
                onSave(
                    nickname.trim(),
                    ageGroup,
                    gender,
                    preferences
                )
            },
            enabled = nickname.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("프로필 저장하기")
        }
    }
}
