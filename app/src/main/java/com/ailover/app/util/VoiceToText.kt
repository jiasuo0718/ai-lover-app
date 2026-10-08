package com.ailover.app.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * 语音转文字工具类。
 * 封装系统 SpeechRecognizer，支持中文识别。
 * （讯飞 SDK 需手动下载，暂时用系统识别）
 */
class VoiceToText(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    /**
     * 开始语音识别。
     * @param onResult 识别成功回调，返回识别到的文字
     * @param onError 识别失败回调，返回错误信息
     */
    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isListening) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "没听清，请重试"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没听清，请重试"
                    SpeechRecognizer.ERROR_AUDIO -> "录音错误，请重试"
                    SpeechRecognizer.ERROR_NETWORK -> "网络错误，请检查网络"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时，请重试"
                    else -> "识别失败，请重试"
                }
                onError(errorMsg)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotEmpty()) {
                    onResult(text)
                } else {
                    onError("没听清，请重试")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        speechRecognizer?.startListening(intent)
    }

    /** 停止语音识别。 */
    fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
    }

    /** 销毁资源。 */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
    }

    /** 是否正在识别。 */
    fun isListening(): Boolean = isListening
}
