package com.example.app.ui.voice

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.app.voice.MoaVoiceManager




private fun createMoaReply(input: String): String {
    val text = input.trim()
    return when {
        text.contains("안녕") -> "안녕하세요. 여행을 함께할 모아입니다."
        text.contains("안녕") -> "안녕하세요. 여행을 함께할 모아입니다."
        text.contains("여행") -> "좋아요. 어디로 여행을 떠나고 싶으신가요?"
        text.contains("추천") -> "원하시는 여행 지역을 알려주세요."
        text.contains("고마워") -> "천만에요. 즐거운 여행을 도와드릴게요."
        else -> "말씀하신 내용을 들었어요. 아직은 간단한 대화만 할 수 있어요."
    }
}

@Composable
fun MoaVoiceTestScreen(onExit: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = remember(context) { MoaVoiceManager(context.applicationContext) }

    var status by remember { mutableStateOf("SDK 준비 중") }
    var recognizedText by remember { mutableStateOf("") }
    var moaReply by remember { mutableStateOf("") }
    var ready by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var replied by remember { mutableStateOf(false) }
    var screenActive by remember { mutableStateOf(true) }

    DisposableEffect(manager, lifecycleOwner) {
        Log.d("MOA_NAV", "VOICE_COMPOSE_ENTER")
        var disposed = false
        manager.onStatusChanged = { message ->
            if (!disposed) {
                status = message
                if (message == "모아 준비 완료") ready = true
                if (message.startsWith("STT 오류") || message.startsWith("STT 초기화 실패") ||
                    message.startsWith("음성 복구 실패")) ready = false
            }
        }
        manager.onRecognized = { text, completed ->
            if (!disposed && screenActive && listening) {
                recognizedText = text
                if (completed && text.isNotBlank() && !replied) {
                    replied = true
                    listening = false
                    manager.stopListening()
                    moaReply = createMoaReply(text)
                    manager.speak(moaReply)
                    status = "모아가 답변하는 중"
                }
            }
        }
        manager.onRecognitionEnded = {
            if (!disposed) listening = false
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> screenActive = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    Log.d("MOA_NAV", "VOICE_LIFECYCLE_$event")
                    screenActive = false
                    listening = false
                    replied = true
                    manager.stopListening()
                    manager.stopSpeaking()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        manager.initialize()
        onDispose {
            Log.d("MOA_NAV", "VOICE_DISPOSE_BEGIN")
            disposed = true
            lifecycleOwner.lifecycle.removeObserver(observer)
            manager.stopListening()
            manager.stopSpeaking()
            manager.release()
            Log.d("MOA_NAV", "VOICE_DISPOSE_END")
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("MOA 음성 대화 테스트", style = MaterialTheme.typography.headlineMedium)
        Text("상태: $status")
        OutlinedButton(onClick = {
            Log.d("MOA_NAV", "VOICE_EXIT_CLICK")
            onExit()
        }) { Text("여행 기록으로 돌아가기") }
        Button(
            onClick = {
                recognizedText = ""
                moaReply = ""
                replied = false
                listening = true
                manager.stopSpeaking()
                status = "음성 인식 요청 중"
                manager.startListening()
            },
            enabled = ready && screenActive && !listening
        ) { Text("모아에게 말하기") }
        OutlinedButton(
            onClick = {
                listening = false
                replied = true
                manager.stopListening()
                manager.stopSpeaking()
                status = "음성 인식 중지 요청"
            },
            enabled = listening
        ) { Text("듣기 중지") }
        HorizontalDivider()
        Text("내가 한 말", style = MaterialTheme.typography.titleMedium)
        Text(recognizedText.ifEmpty { "아직 인식된 음성이 없습니다." })
        HorizontalDivider()
        Text("모아의 답변", style = MaterialTheme.typography.titleMedium)
        Text(moaReply.ifEmpty { "아직 답변이 없습니다." })
    }
}
