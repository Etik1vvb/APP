package com.example.app.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.constant.ErrorType as SttErrorType
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.constant.ErrorType as TtsErrorType
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitializeListener

class MoaVoiceManager(context: Context) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var stt = SpeechToText(appContext, SttMode.ON_DEVICE)
    private val tts = TextToSpeech(appContext, TtsMode.ON_DEVICE)

    var onStatusChanged: ((String) -> Unit)? = null
    var onRecognized: ((String, Boolean) -> Unit)? = null
    var onRecognitionEnded: (() -> Unit)? = null
    var onVoiceLevelChanged: ((Double) -> Unit)? = null
    var onSpeechBoundaryChanged: ((Long, Long) -> Unit)? = null

    private val silenceTimeoutMs = 2500L
    private val voiceThreshold = 5.0
    private val checkIntervalMs = 200L
    private val restartDelayMs = 300L
    private val stopCallbackTimeoutMs = 1200L

    private var sttReady = false
    private var ttsReady = false
    private var released = false
    private var listening = false
    private var speechDetected = false
    private var lastVoiceTime = 0L
    private var sdkSessionActive = false
    private var waitingForSdkStop = false
    private var recovering = false
    private var generation = 0
    private var silenceCheckRunnable: Runnable? = null
    private var restartRunnable: Runnable? = null
    private var stopWatchdog: Runnable? = null
    private var resultListener: ResultListener = createResultListener(generation)

    private fun postForGeneration(token: Int, action: () -> Unit) {
        handler.post {
            if (!released && token == generation) action()
        }
    }

    private fun createResultListener(token: Int): ResultListener = object : ResultListener {
        override fun onReady() {
            postForGeneration(token) { onStatusChanged?.invoke("음성 인식 준비 완료") }
        }

        override fun onStartedRecognition() {
            postForGeneration(token) {
                if (listening) onStatusChanged?.invoke("음성 인식 중")
            }
        }

        override fun onUpdated(stt: String, completed: Boolean) {
            postForGeneration(token) {
                Log.d("MOA_STOP", "RESULT completed=$completed text=$stt")
                // stop() 이후 늦게 도착한 최종 결과는 이전 입력에 섞지 않는다.
                if (!listening) return@postForGeneration
                if (stt.isNotBlank() && !speechDetected) {
                    speechDetected = true
                    lastVoiceTime = SystemClock.elapsedRealtime()
                }
                onRecognized?.invoke(stt, completed)
            }
        }

        override fun onUpdatedEpdData(on: Long, off: Long) {
            postForGeneration(token) { onSpeechBoundaryChanged?.invoke(on, off) }
        }

        override fun onUpdatedRms(rms: Double) {
            postForGeneration(token) {
                onVoiceLevelChanged?.invoke(rms)
                if (listening && rms >= voiceThreshold) {
                    speechDetected = true
                    lastVoiceTime = SystemClock.elapsedRealtime()
                }
            }
        }

        override fun onEndedRecognition() {
            postForGeneration(token) {
                Log.d("MOA_STOP", "SDK_ENDED listening=$listening")
                sdkSessionActive = false
                waitingForSdkStop = false
                cancelStopWatchdog()
                if (!listening) return@postForGeneration
                scheduleRestart()
            }
        }

        override fun onError(errorType: SttErrorType) {
            postForGeneration(token) {
                Log.e("MOA_STOP", "STT_ERROR $errorType")
                cancelStopWatchdog()
                cancelRestart()
                cancelSilenceCheck()
                sdkSessionActive = false
                waitingForSdkStop = false
                listening = false
                onStatusChanged?.invoke("STT 오류: $errorType")
                onRecognitionEnded?.invoke()
                // 오류 후 다시 누를 때 안전하게 새 객체로 복구한다.
                recoverStt()
            }
        }
    }

    fun initialize() {
        initializeStt()
        tts.initialize(object : TtsInitializeListener {
            override fun onSuccess() {
                handler.post {
                    if (released) return@post
                    ttsReady = true
                    updateReadyStatus()
                }
            }
            override fun onFailure(errorType: TtsErrorType) {
                handler.post {
                    if (!released) onStatusChanged?.invoke("TTS 초기화 실패: $errorType")
                }
            }
        })
    }

    private fun initializeStt() {
        val token = generation
        sttReady = false
        stt.addListener(resultListener)
        stt.initialize(object : SttInitializeListener {
            override fun onSuccess() {
                postForGeneration(token) {
                    sttReady = true
                    recovering = false
                    Log.d("MOA_STOP", "STT_READY generation=$token")
                    updateReadyStatus()
                    if (listening) requestRecognition()
                }
            }
            override fun onFailure(errorType: SttErrorType) {
                postForGeneration(token) {
                    recovering = false
                    listening = false
                    cancelSilenceCheck()
                    onStatusChanged?.invoke("STT 초기화 실패: $errorType")
                    onRecognitionEnded?.invoke()
                }
            }
        })
    }

    private fun updateReadyStatus() {
        if (sttReady && ttsReady) onStatusChanged?.invoke("모아 준비 완료")
    }

    private fun requestRecognition() {
        if (!listening || !sttReady || released || recovering ||
            sdkSessionActive || waitingForSdkStop) return
        sdkSessionActive = true
        Log.d("MOA_STOP", "REQUEST_STT")
        try {
            stt.request()
        } catch (e: Exception) {
            Log.e("MOA_STOP", "REQUEST_FAILED", e)
            sdkSessionActive = false
            recoverStt()
        }
    }

    private fun scheduleRestart() {
        cancelRestart()
        val task = Runnable {
            if (!listening || released) return@Runnable
            val elapsed = SystemClock.elapsedRealtime() - lastVoiceTime
            if (speechDetected && elapsed >= silenceTimeoutMs) {
                Log.d("MOA_STOP", "FINAL_SILENCE elapsed=$elapsed")
                stopListening()
            } else {
                Log.d("MOA_STOP", "RESTART_STT")
                requestRecognition()
            }
        }
        restartRunnable = task
        handler.postDelayed(task, restartDelayMs)
    }

    private fun cancelRestart() {
        restartRunnable?.let(handler::removeCallbacks)
        restartRunnable = null
    }

    private fun cancelSilenceCheck() {
        silenceCheckRunnable?.let(handler::removeCallbacks)
        silenceCheckRunnable = null
    }

    private fun startSilenceCheck() {
        cancelSilenceCheck()
        val task = object : Runnable {
            override fun run() {
                if (!listening || released) return
                if (speechDetected) {
                    val elapsed = SystemClock.elapsedRealtime() - lastVoiceTime
                    if (elapsed >= silenceTimeoutMs) {
                        Log.d("MOA_STOP", "CUSTOM_TIMEOUT elapsed=$elapsed")
                        stopListening()
                        return
                    }
                }
                handler.postDelayed(this, checkIntervalMs)
            }
        }
        silenceCheckRunnable = task
        handler.postDelayed(task, checkIntervalMs)
    }

    private fun cancelStopWatchdog() {
        stopWatchdog?.let(handler::removeCallbacks)
        stopWatchdog = null
    }

    private fun watchStopCompletion() {
        cancelStopWatchdog()
        val token = generation
        val task = Runnable {
            if (released || token != generation || !waitingForSdkStop) return@Runnable
            Log.w("MOA_STOP", "STOP_CALLBACK_TIMEOUT: rebuilding STT")
            recoverStt()
        }
        stopWatchdog = task
        handler.postDelayed(task, stopCallbackTimeoutMs)
    }

    private fun recoverStt() {
        if (released || recovering) return
        recovering = true
        cancelStopWatchdog()
        cancelRestart()
        generation++ // 이전 객체의 지연 콜백 무효화
        sttReady = false
        sdkSessionActive = false
        waitingForSdkStop = false
        Log.d("MOA_STOP", "RECOVER_STT generation=$generation")
        try {
            stt.removeListener(resultListener)
            stt.release()
            stt = SpeechToText(appContext, SttMode.ON_DEVICE)
            resultListener = createResultListener(generation)
            initializeStt()
        } catch (e: Exception) {
            Log.e("MOA_STOP", "RECOVER_FAILED", e)
            recovering = false
            listening = false
            cancelSilenceCheck()
            onStatusChanged?.invoke("음성 복구 실패: ${e.message}")
            onRecognitionEnded?.invoke()
        }
    }

    fun startListening() {
        Log.d("MOA_STOP", "START_CLICK ready=$sttReady listening=$listening " +
                "sdkActive=$sdkSessionActive waiting=$waitingForSdkStop recovering=$recovering")
        if (released || listening) return
        listening = true
        speechDetected = false
        lastVoiceTime = 0L
        cancelRestart()
        if (ttsReady) tts.stop()
        startSilenceCheck()
        if (waitingForSdkStop || recovering) {
            Log.d("MOA_STOP", "WAITING_FOR_RECOVERY")
            // 종료 콜백 또는 복구 초기화가 끝나면 인식을 재개합니다.
            return
        }
        if (!sttReady) {
            onStatusChanged?.invoke("음성 인식 준비 중")
            return
        }
        requestRecognition()
    }

    fun stopListening() {
        if (released) return
        val wasListening = listening
        listening = false
        cancelSilenceCheck()
        cancelRestart()
        if (sdkSessionActive && !waitingForSdkStop) {
            waitingForSdkStop = true
            Log.d("MOA_STOP", "CALLING_STT_STOP")
            try {
                stt.stop()
                Log.d("MOA_STOP", "STT_STOP_RETURNED")
                watchStopCompletion()
            } catch (e: Exception) {
                Log.e("MOA_STOP", "STT_STOP_FAILED", e)
                recoverStt()
            }
        }
        if (wasListening) {
            onStatusChanged?.invoke("음성 인식 종료")
            onRecognitionEnded?.invoke()
        }
    }

    fun speak(text: String) {
        if (!ttsReady || released || text.isBlank()) return
        tts.speak(text)
    }

    fun stopSpeaking() {
        if (ttsReady && !released) tts.stop()
    }

    fun release() {
        if (released) return
        released = true
        listening = false
        cancelSilenceCheck()
        cancelRestart()
        cancelStopWatchdog()
        generation++
        try {
            // release()만으로 진행 중인 녹음이 즉시 종료된다고 가정하지 않습니다.
            stt.stop()
        } catch (e: Exception) {
            Log.w("MOA_STOP", "STOP_ON_RELEASE_FAILED", e)
        }
        try {
            stt.removeListener(resultListener)
            stt.release()
        } catch (e: Exception) {
            Log.w("MOA_STOP", "RELEASE_STT_FAILED", e)
        }
        try {
            tts.stop()
            tts.release()
        } catch (e: Exception) {
            Log.w("MOA_STOP", "RELEASE_TTS_FAILED", e)
        }
        onStatusChanged = null
        onRecognized = null
        onRecognitionEnded = null
        onVoiceLevelChanged = null
        onSpeechBoundaryChanged = null
    }
}