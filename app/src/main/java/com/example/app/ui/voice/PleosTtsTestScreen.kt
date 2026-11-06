
package com.example.app.ui.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.listener.OnInitializeListener

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun PleosTtsTestScreen() {
    val context = LocalContext.current

    var status by remember {
        mutableStateOf("초기화 대기")
    }

    var initialized by remember {
        mutableStateOf(false)
    }

    val textToSpeech = remember(context) {
        TextToSpeech(
            context = context.applicationContext,
            mode = Mode.ON_DEVICE
        )
    }

    DisposableEffect(textToSpeech) {
        textToSpeech.initialize(
            object : OnInitializeListener {
                override fun onSuccess() {
                    initialized = true
                    status = "TTS SDK 초기화 성공"
                }

                override fun onFailure(errorType: ErrorType) {
                    initialized = false
                    status = "TTS SDK 초기화 실패: $errorType"
                }
            }
        )

        onDispose {
            textToSpeech.stop()
            textToSpeech.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Pleos TTS 테스트",
            style = MaterialTheme.typography.headlineMedium
        )

        Text("상태: $status")

        Button(
            onClick = {
                status = "음성 출력 요청 중"
                textToSpeech.speak(
                    "안녕하세요. 여행을 함께할 모아입니다."
                )
            },
            enabled = initialized
        ) {
            Text("MOA 음성 출력")
        }

        OutlinedButton(
            onClick = {
                textToSpeech.stop()
                status = "음성 출력 중지 요청"
            },
            enabled = initialized
        ) {
            Text("음성 출력 중지")
        }
    }
}
