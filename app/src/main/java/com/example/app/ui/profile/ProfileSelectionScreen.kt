package com.example.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.app.data.UserProfile

/**
 * 다중 프로필 선택/관리 화면.
 * 데이터 저장은 MainActivity에서 Repository를 통해 처리합니다.
 */
@Composable
fun ProfileSelectionScreen(
    profiles: List<UserProfile>,
    selectedProfileId: String?,
    onSelectProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    onDeleteProfile: (String) -> Unit
) {
    var deleteTarget by remember { mutableStateOf<UserProfile?>(null) }
    var warning by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("사용할 프로필을 선택하세요", style = MaterialTheme.typography.headlineSmall)
        Text("프로필마다 여행 취향을 따로 저장할 수 있습니다.")

        if (profiles.isEmpty()) {
            Text("등록된 프로필이 없습니다. 새 프로필을 만들어주세요.")
        }

        profiles.forEach { profile ->
            val isSelected = profile.id == selectedProfileId
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                warning = null
                                onSelectProfile(profile.id)
                            },
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(profile.nickname.ifBlank { "이름 없는 프로필" }, style = MaterialTheme.typography.titleMedium)
                        if (isSelected) {
                            Text("현재 선택된 프로필", color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text("선택하려면 누르세요", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick = {
                        if (isSelected) {
                            warning = "현재 선택되어 있는 프로필은 삭제할 수 없습니다. 다른 프로필을 선택한 후 삭제해주세요."
                        } else {
                            warning = null
                            deleteTarget = profile
                        }
                    }) {
                        Text("삭제", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        warning?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }

        Button(onClick = onAddProfile, modifier = Modifier.fillMaxWidth()) {
            Text("+ 새 프로필 추가")
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("프로필 삭제") },
            text = { Text("'${target.nickname}' 프로필을 삭제하시겠습니까? 삭제 후 복구할 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    // 확인 버튼을 누른 사이 선택 상태가 바뀌었을 때도 삭제를 차단합니다.
                    if (target.id == selectedProfileId) {
                        warning = "현재 선택되어 있는 프로필은 삭제할 수 없습니다."
                    } else {
                        onDeleteProfile(target.id)
                    }
                    deleteTarget = null
                }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("취소") }
            }
        )
    }
}
