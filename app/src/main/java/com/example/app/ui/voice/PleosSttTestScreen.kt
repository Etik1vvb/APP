package com.example.app.ui.voice

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener

@Composable
fun PleosSttTestScreen() {
    val context = LocalContext.current

    var status by remember { mutableStateOf("초기화 대기") }
    var recognizedText by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }

    val speechToText = remember(context) {
        SpeechToText(
            context = context.applicationContext,
            mode = Mode.ON_DEVICE
        )
    }

    DisposableEffect(speechToText) {
        val resultListener = object : ResultListener {
            override fun onReady() {
                status = "음성 인식 준비 완료"
            }

            override fun onStartedRecognition() {
                listening = true
                status = "음성 인식 중..."
            }

            override fun onUpdated(
                stt: String,
                completed: Boolean
            ) {
                recognizedText = stt
                if (completed) {
                    status = "인식 결과 수신 완료"
                }
            }

            override fun onUpdatedEpdData(
                on: Long,
                off: Long
            ) {}

            override fun onUpdatedRms(rms: Double) {}

            override fun onEndedRecognition() {
                listening = false
                status = "음성 인식 종료"
            }

            override fun onError(errorType: ErrorType) {
                listening = false
                status = "인식 오류: $errorType"
            }
        }

        speechToText.addListener(resultListener)

        speechToText.initialize(
            object : OnInitializeListener {
                override fun onSuccess() {
                    initialized = true
                    status = "SDK 초기화 성공"
                }

                override fun onFailure(errorType: ErrorType) {
                    status = "SDK 초기화 실패: $errorType"
                }
            }
        )

        onDispose {
            speechToText.removeListener(resultListener)
            speechToText.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Pleos STT 테스트",
            style = MaterialTheme.typography.headlineMedium
        )

        Text("상태: $status")

        Button(
            onClick = {
                recognizedText = ""
                speechToText.request()
            },
            enabled = initialized && !listening
        ) {
            Text("음성 인식 시작")
        }

        Button(
            onClick = {
                speechToText.stop()
            },
            enabled = initialized && listening
        ) {
            Text("음성 인식 중지")
        }

        HorizontalDivider()

        Text(
            text = "인식된 텍스트",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = recognizedText.ifEmpty {
                "아직 인식된 음성이 없습니다."
            }
        )
    }
}
