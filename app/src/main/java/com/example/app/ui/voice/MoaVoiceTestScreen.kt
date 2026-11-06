
package com.example.app.ui.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.constant.ErrorType as SttErrorType
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitializeListener
import ai.pleos.playground.stt.listener.ResultListener

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.constant.ErrorType as TtsErrorType
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitializeListener

import android.os.Handler
import android.os.Looper

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private fun createMoaReply(input: String): String {
    val text = input.trim()

    return when {
        text.contains("안녕") ->
            "안녕하세요. 여행을 함께할 모아입니다."

        text.contains("여행") ->
            "좋아요. 어디로 여행을 떠나고 싶으신가요?"

        text.contains("추천") ->
            "원하시는 여행 지역을 알려주세요."

        text.contains("고마워") ->
            "천만에요. 즐거운 여행을 도와드릴게요."

        else ->
            "말씀하신 내용을 들었어요. 아직은 간단한 대화만 할 수 있어요."
    }
}

@Composable
fun MoaVoiceTestScreen() {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var status by remember { mutableStateOf("SDK 준비 중") }
    var recognizedText by remember { mutableStateOf("") }
    var moaReply by remember { mutableStateOf("") }

    var sttReady by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }

    var repliedForCurrentRequest by remember {
        mutableStateOf(false)
    }

    val stt = remember(context) {
        SpeechToText(
            context = context.applicationContext,
            mode = SttMode.ON_DEVICE
        )
    }

    val tts = remember(context) {
        TextToSpeech(
            context = context.applicationContext,
            mode = TtsMode.ON_DEVICE
        )
    }

    DisposableEffect(stt, tts) {
        var disposed = false

        fun onMain(action: () -> Unit) {
            mainHandler.post {
                if (!disposed) action()
            }
        }

        val sttListener = object : ResultListener {
            override fun onReady() {
                onMain {
                    status = "음성 인식 준비 완료"
                }
            }

            override fun onStartedRecognition() {
                onMain {
                    listening = true
                    status = "듣고 있어요..."
                }
            }

            override fun onUpdated(
                stt: String,
                completed: Boolean
            ) {
                onMain {
                    recognizedText = stt

                    if (
                        completed &&
                        stt.isNotBlank() &&
                        !repliedForCurrentRequest
                    ) {
                        repliedForCurrentRequest = true
                        listening = false

                        moaReply = createMoaReply(stt)

                        if (ttsReady) {
                            status = "모아가 답변하는 중"
                            tts.speak(moaReply)
                        } else {
                            status = "TTS 준비되지 않음"
                        }
                    }
                }
            }

            override fun onUpdatedEpdData(
                on: Long,
                off: Long
            ) {}

            override fun onUpdatedRms(rms: Double) {}

            override fun onEndedRecognition() {
                onMain {
                    listening = false
                    if (!repliedForCurrentRequest) {
                        status = "음성 인식 종료"
                    }
                }
            }

            override fun onError(errorType: SttErrorType) {
                onMain {
                    listening = false
                    status = "STT 오류: $errorType"
                }
            }
        }

        stt.addListener(sttListener)

        stt.initialize(
            object : SttInitializeListener {
                override fun onSuccess() {
                    onMain {
                        sttReady = true
                        if (ttsReady) {
                            status = "모아 준비 완료"
                        }
                    }
                }

                override fun onFailure(errorType: SttErrorType) {
                    onMain {
                        status = "STT 초기화 실패: $errorType"
                    }
                }
            }
        )

        tts.initialize(
            object : TtsInitializeListener {
                override fun onSuccess() {
                    onMain {
                        ttsReady = true
                        if (sttReady) {
                            status = "모아 준비 완료"
                        }
                    }
                }

                override fun onFailure(errorType: TtsErrorType) {
                    onMain {
                        status = "TTS 초기화 실패: $errorType"
                    }
                }
            }
        )

        onDispose {
            disposed = true

            stt.removeListener(sttListener)
            stt.release()

            tts.stop()
            tts.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "MOA 음성 대화 테스트",
            style = MaterialTheme.typography.headlineMedium
        )

        Text("상태: $status")

        Text(
            text = "STT: ${if (sttReady) "준비 완료" else "대기"}"
        )

        Text(
            text = "TTS: ${if (ttsReady) "준비 완료" else "대기"}"
        )

        Button(
            onClick = {
                tts.stop()
                recognizedText = ""
                moaReply = ""
                repliedForCurrentRequest = false
                listening = true
                status = "음성 인식 요청 중"
                stt.request()
            },
            enabled = sttReady && ttsReady && !listening
        ) {
            Text("모아에게 말하기")
        }

        OutlinedButton(
            onClick = {
                stt.stop()
                listening = false
                status = "음성 인식 중지 요청"
            },
            enabled = sttReady && listening
        ) {
            Text("듣기 중지")
        }

        HorizontalDivider()

        Text(
            text = "내가 한 말",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            recognizedText.ifEmpty {
                "아직 인식된 음성이 없습니다."
            }
        )

        HorizontalDivider()

        Text(
            text = "모아의 답변",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            moaReply.ifEmpty {
                "아직 답변이 없습니다."
            }
        )
    }
}
