package com.example.app.voice

import java.io.File

/**
 * 음성 파일을 텍스트로 변환하는 공통 인터페이스.
 *
 * 현재: 외부 클라우드 STT
 * 향후: Pleos SpeechToText SDK
 */
interface SpeechToTextEngine {

    suspend fun transcribe(
        audioFile: File
    ): Result<String>
}
