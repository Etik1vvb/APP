
package com.example.app.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.app.voice.AudioRecorder
import kotlinx.coroutines.launch

@Composable
fun AudioRecorderTestScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val recorder = remember {
        AudioRecorder(context.applicationContext)
    }

    var isRecording by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var result by remember {
        mutableStateOf("녹음 준비 완료")
    }

    fun startRecording() {
        try {
            recorder.start(scope)
            isRecording = true
            result = "녹음 중입니다. 말씀해 주세요."
        } catch (e: Exception) {
            result = "녹음 시작 실패: ${e.message}"
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startRecording()
            } else {
                result = "마이크 권한이 거부되었습니다."
            }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "RoadTrip 음성 녹음 테스트",
            style = MaterialTheme.typography.headlineMedium
        )

        Button(
            enabled = !isBusy,
            onClick = {
                if (isRecording) {
                    isBusy = true
                    scope.launch {
                        try {
                            val file = recorder.stop()
                            isRecording = false
                            result =
                                "WAV 생성 성공\n" +
                                        "파일: ${file.name}\n" +
                                        "크기: ${file.length()} bytes"
                        } catch (e: Exception) {
                            isRecording = false
                            result =
                                "녹음 종료 실패: ${e.message}"
                        } finally {
                            isBusy = false
                        }
                    }
                } else {
                    if (
                        context.checkSelfPermission(
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        startRecording()
                    } else {
                        permissionLauncher.launch(
                            Manifest.permission.RECORD_AUDIO
                        )
                    }
                }
            }
        ) {
            Text(
                if (isBusy) {
                    "파일 저장 중..."
                } else if (isRecording) {
                    "녹음 종료"
                } else {
                    "녹음 시작"
                }
            )
        }

        Text(result)
    }
}
