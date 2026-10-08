package com.ailover.app.util

import android.content.Context
import android.os.Bundle
import com.iflytek.cloud.RecognizerListener
import com.iflytek.cloud.RecognizerResult
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechError
import com.iflytek.cloud.SpeechRecognizer
import com.iflytek.cloud.SpeechUtility
import org.json.JSONObject

/**
 * 语音转文字工具类（讯飞语音听写）。
 * 封装讯飞 MSC SDK，支持中文实时识别。
 */
class VoiceToText(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var resultBuilder = StringBuilder()

    init {
        // 初始化讯飞 SDK（只需初始化一次）
        if (SpeechUtility.getUtility() == null) {
            SpeechUtility.createUtility(
                context.applicationContext,
                SpeechConstant.APPID + "=5a6f2582"
            )
        }
    }

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

        resultBuilder = StringBuilder()
        speechRecognizer = SpeechRecognizer.createRecognizer(context, null)
        speechRecognizer?.setParameter(SpeechConstant.DOMAIN, "iat")
        speechRecognizer?.setParameter(SpeechConstant.LANGUAGE, "zh_cn")
        speechRecognizer?.setParameter(SpeechConstant.ACCENT, "mandarin")
        speechRecognizer?.setParameter(SpeechConstant.RESULT_TYPE, "plain")
        speechRecognizer?.setParameter(SpeechConstant.VAD_BOS, "4000")
        speechRecognizer?.setParameter(SpeechConstant.VAD_EOS, "1000")

        val listener = object : RecognizerListener {
            override fun onResult(results: RecognizerResult?, isLast: Boolean) {
                val text = parseResult(results?.resultString)
                if (text.isNotEmpty()) {
                    resultBuilder.append(text)
                }
                if (isLast) {
                    isListening = false
                    val finalText = resultBuilder.toString().trim()
                    if (finalText.isNotEmpty()) {
                        onResult(finalText)
                    } else {
                        onError("没听清，请重试")
                    }
                }
            }

            override fun onError(error: SpeechError?) {
                isListening = false
                val errorMsg = when (error?.errorCode) {
                    10118 -> "没听清，请重试"
                    10119 -> "录音时间太短"
                    20001 -> "网络错误，请检查网络"
                    20002 -> "网络超时，请重试"
                    else -> "识别失败，请重试（${error?.errorCode ?: "未知"}）"
                }
                onError(errorMsg)
            }

            override fun onBeginOfSpeech() {}

            override fun onEndOfSpeech() {}

            override fun onVolumeChanged(volume: Int, data: ByteArray?) {}

            override fun onEvent(eventType: Int, arg1: Int, arg2: Int, obj: Bundle?) {}
        }

        val code = speechRecognizer?.startListening(listener)
        if (code == 0) {
            isListening = true
        } else {
            onError("启动识别失败，请重试")
        }
    }

    /** 停止语音识别。 */
    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    /** 取消识别。 */
    fun cancel() {
        speechRecognizer?.cancel()
        isListening = false
    }

    /** 销毁资源。 */
    fun destroy() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
    }

    /** 是否正在识别。 */
    fun isListening(): Boolean = isListening

    /**
     * 解析讯飞识别结果。
     * RESULT_TYPE=plain 时直接返回纯文本，
     * 兼容 JSON 格式的解析。
     */
    private fun parseResult(resultString: String?): String {
        if (resultString.isNullOrEmpty()) return ""

        // 纯文本结果直接返回
        if (!resultString.startsWith("{")) {
            return resultString
        }

        // JSON 格式解析
        return try {
            val json = JSONObject(resultString)
            val ws = json.optJSONArray("ws") ?: return ""
            val sb = StringBuilder()
            for (i in 0 until ws.length()) {
                val cw = ws.optJSONObject(i)?.optJSONArray("cw") ?: continue
                for (j in 0 until cw.length()) {
                    val w = cw.optJSONObject(j)?.optString("w") ?: continue
                    sb.append(w)
                }
            }
            sb.toString()
        } catch (e: Exception) {
            ""
        }
    }
}
