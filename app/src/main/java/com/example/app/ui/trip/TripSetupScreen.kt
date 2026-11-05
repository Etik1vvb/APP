
package com.example.app.ui.trip

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

data class TripInfo(
    val destination: String,
    val startDate: String,
    val endDate: String,
    val companions: String,
    val purpose: String
)

@Composable
fun TripSetupScreen(
    onSave: (TripInfo) -> Unit,
    onBack: () -> Unit
) {
    var destination by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var companions by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }

    val canSave = destination.isNotBlank() &&
            startDate.isNotBlank() &&
            endDate.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "새로운 여행 등록",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "여행 정보를 입력해주세요.",
            fontSize = 18.sp
        )

        OutlinedTextField(
            value = destination,
            onValueChange = { destination = it },
            label = {
                Text("목적지", fontSize = 18.sp)
            },
            placeholder = {
                Text("예: 강릉", fontSize = 18.sp)
            },
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 20.sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp),
            singleLine = true
        )


        OutlinedTextField(
            value = startDate,
            onValueChange = { startDate = it },
            label = { Text("출발 날짜", fontSize = 18.sp) },
            placeholder = { Text("예: 2026-11-10", fontSize = 18.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = endDate,
            onValueChange = { endDate = it },
            label = { Text("도착 날짜", fontSize = 18.sp) },
            placeholder = { Text("예: 2026-11-12", fontSize = 18.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = companions,
            onValueChange = { companions = it },
            label = { Text("동행자", fontSize = 18.sp) },
            placeholder = { Text("예: 친구 2명", fontSize = 18.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = purpose,
            onValueChange = { purpose = it },
            label = { Text("여행 목적", fontSize = 18.sp) },
            placeholder = { Text("예: 휴식, 관광, 맛집 탐방", fontSize = 18.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            singleLine = true
        )



        Button(
            onClick = {
                onSave(
                    TripInfo(
                        destination = destination.trim(),
                        startDate = startDate.trim(),
                        endDate = endDate.trim(),
                        companions = companions.trim(),
                        purpose = purpose.trim()
                    )
                )
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
        ) {
            Text("여행 등록하기", fontSize = 20.sp)
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
        ) {
            Text("뒤로 가기", fontSize = 20.sp)
        }

    }
}
