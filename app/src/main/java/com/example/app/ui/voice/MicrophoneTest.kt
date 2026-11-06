
package com.example.app.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

@Composable
fun MicrophoneTest() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var result by remember {
        mutableStateOf("마이크 테스트 대기 중")
    }
    var testing by remember {
        mutableStateOf(false)
    }

    suspend fun runTest() {
        testing = true
        result = "3초 동안 말씀해 주세요."

        try {
            val rms = withContext(Dispatchers.IO) {
                measureMicrophoneLevel()
            }

            result = if (rms > 0.001) {
                "마이크 신호 감지 성공! RMS: %.4f"
                    .format(rms)
            } else {
                "신호가 매우 작거나 감지되지 않았어요. RMS: %.4f"
                    .format(rms)
            }
        } catch (e: Exception) {
            result = "마이크 테스트 실패: ${e.message}"
        } finally {
            testing = false
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                scope.launch { runTest() }
            } else {
                result = "마이크 권한이 거부되었어요."
            }
        }

    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "마이크 입력 테스트",
            style = MaterialTheme.typography.headlineMedium
        )

        Button(
            enabled = !testing,
            onClick = {
                if (
                    context.checkSelfPermission(
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    scope.launch { runTest() }
                } else {
                    permissionLauncher.launch(
                        Manifest.permission.RECORD_AUDIO
                    )
                }
            }
        ) {
            Text(
                if (testing) "측정 중..." else "3초 마이크 테스트"
            )
        }

        Text(result)
    }
}

private fun measureMicrophoneLevel(): Double {
    val sampleRate = 16000
    val minBufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    require(minBufferSize > 0) {
        "지원되지 않는 오디오 설정입니다."
    }

    val bufferSize = maxOf(minBufferSize, sampleRate * 2)

    val recorder = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize
    )

    try {
        check(
            recorder.state == AudioRecord.STATE_INITIALIZED
        ) {
            "AudioRecord 초기화 실패"
        }

        recorder.startRecording()

        check(
            recorder.recordingState ==
                    AudioRecord.RECORDSTATE_RECORDING
        ) {
            "녹음 시작 실패"
        }

        val buffer = ShortArray(bufferSize / 2)
        var sumSquares = 0.0
        var totalSamples = 0L
        val deadline = android.os.SystemClock
            .elapsedRealtime() + 3000L

        while (
            android.os.SystemClock.elapsedRealtime() < deadline
        ) {
            val count = recorder.read(
                buffer,
                0,
                buffer.size
            )

            if (count < 0) {
                error("오디오 읽기 실패: $count")
            }

            for (i in 0 until count) {
                val normalized = buffer[i] / 32768.0
                sumSquares += normalized * normalized
            }

            totalSamples += count
        }

        check(totalSamples > 0) {
            "읽어 들인 오디오 샘플이 없습니다."
        }

        return sqrt(sumSquares / totalSamples)
    } finally {
        if (
            recorder.recordingState ==
            AudioRecord.RECORDSTATE_RECORDING
        ) {
            recorder.stop()
        }
        recorder.release()
    }
}
