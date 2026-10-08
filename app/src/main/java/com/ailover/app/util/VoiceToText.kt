package com.ailover.app.util

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * 语音转文字工具类（讯飞 WebSocket 版）。
 * 长按/按住 → 录音 + 实时上传讯飞 → 松手 → 最终文字 → 回调。
 * 回调统一在主线程执行，避免子线程更新 UI 崩溃。
 */
class VoiceToText(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pcmRecorder = PcmRecorder()
    private val xfyunClient = XfyunIatClient(
        appId = "5a6f2582",
        apiKey = "5f2d4785bed8f9a75c9ee353305dcbc0",
        apiSecret = "NWYyYWMyNjg5OTJjNjdiYTAyNzc2YTQw"
    )

    private var isListening = false
    private var firstAudioFrame = true

    /**
     * 开始语音识别。
     * 建立 WebSocket 连接，就绪后开始录音并实时上传。
     * @param onResult 识别成功回调（主线程），返回识别到的文字
     * @param onError 识别失败回调（主线程），返回错误信息
     */
    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isListening) return
        isListening = true
        firstAudioFrame = true

        xfyunClient.connect(
            onReady = {
                // 连接就绪，开始录音（在子线程回调中执行，启动新录音线程）
                pcmRecorder.start { audioData ->
                    if (isListening) {
                        val status = if (firstAudioFrame) {
                            firstAudioFrame = false
                            0 // 开始帧
                        } else {
                            1 // 中间帧
                        }
                        xfyunClient.sendAudio(audioData, status)
                    }
                }
            },
            onResult = { text ->
                // 切到主线程回调
                mainHandler.post {
                    isListening = false
                    pcmRecorder.stop()
                    onResult(text)
                }
            },
            onError = { error ->
                // 切到主线程回调
                mainHandler.post {
                    isListening = false
                    pcmRecorder.stop()
                    xfyunClient.destroy()
                    onError(error)
                }
            }
        )
    }

    /** 停止语音识别（松手时调用）。 */
    fun stopListening() {
        if (!isListening) return
        pcmRecorder.stop()
        // 发送结束帧，等待最终结果
        xfyunClient.finish()
    }

    /** 取消识别。 */
    fun cancel() {
        isListening = false
        pcmRecorder.stop()
        xfyunClient.destroy()
    }

    /** 销毁资源。 */
    fun destroy() {
        isListening = false
        pcmRecorder.stop()
        xfyunClient.destroy()
    }

    /** 是否正在识别。 */
    fun isListening(): Boolean = isListening
}
