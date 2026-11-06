package com.example.app.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioRecorder(
    private val context: Context
) {
    private val sampleRate = 16000
    private val channelConfig =
        AudioFormat.CHANNEL_IN_MONO
    private val encoding =
        AudioFormat.ENCODING_PCM_16BIT

    private var recorder: AudioRecord? = null
    private var recordingJob: Job? = null
    private var recording = false

    private val pcmData = ByteArrayOutputStream()

    val isRecording: Boolean
        get() = recording

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        check(!recording) {
            "이미 녹음 중입니다."
        }

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        check(granted) {
            "마이크 권한이 없습니다."
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            encoding
        )

        check(minBuffer > 0) {
            "오디오 입력 설정을 지원하지 않습니다."
        }

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            encoding,
            maxOf(minBuffer, 4096)
        )

        check(
            audioRecord.state ==
                    AudioRecord.STATE_INITIALIZED
        ) {
            audioRecord.release()
            "AudioRecord 초기화 실패"
        }

        try {
            audioRecord.startRecording()
            check(
                audioRecord.recordingState ==
                        AudioRecord.RECORDSTATE_RECORDING
            ) {
                "녹음 시작 실패"
            }
        } catch (e: Exception) {
            audioRecord.release()
            throw e
        }

        pcmData.reset()
        recorder = audioRecord
        recording = true

        recordingJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(4096)

            while (recording) {
                val count = audioRecord.read(
                    buffer,
                    0,
                    buffer.size
                )

                if (count > 0) {
                    pcmData.write(buffer, 0, count)
                } else if (count < 0) {
                    break
                }
            }
        }
    }

    suspend fun stop(): File = withContext(
        Dispatchers.IO
    ) {
        check(recording) {
            "현재 녹음 중이 아닙니다."
        }

        recording = false

        recorder?.let { audioRecord ->
            if (
                audioRecord.recordingState ==
                AudioRecord.RECORDSTATE_RECORDING
            ) {
                audioRecord.stop()
            }
        }

        recordingJob?.join()
        recordingJob = null

        recorder?.release()
        recorder = null

        val bytes = pcmData.toByteArray()

        check(bytes.isNotEmpty()) {
            "녹음된 오디오가 없습니다."
        }

        val output = File(
            context.cacheDir,
            "roadtrip_voice.wav"
        )

        writeWav(output, bytes)
        pcmData.reset()

        output
    }

    private fun writeWav(
        file: File,
        pcm: ByteArray
    ) {
        val header = ByteBuffer
            .allocate(44)
            .order(ByteOrder.LITTLE_ENDIAN)

        val dataSize = pcm.size
        val byteRate = sampleRate * 2

        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1.toShort())
        header.putShort(1.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(2.toShort())
        header.putShort(16.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)

        FileOutputStream(file).use { output ->
            output.write(header.array())
            output.write(pcm)
        }
    }
}
