package com.ailover.app.util

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 讯飞语音听写 WebSocket 客户端。
 * 实时上传 PCM 音频流，返回识别文字。
 */
class XfyunIatClient(
    private val appId: String,
    private val apiKey: String,
    private val apiSecret: String
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    private val resultBuilder = StringBuilder()
    private var isConnected = false
    private var firstFrameSent = false

    /**
     * 建立 WebSocket 连接。
     * @param onReady 连接就绪回调
     * @param onResult 最终识别结果回调
     * @param onError 错误回调
     */
    fun connect(
        onReady: () -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val authUrl = generateAuthUrl()
        val request = Request.Builder().url(authUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                isConnected = true
                firstFrameSent = false
                resultBuilder.clear()
                onReady()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleResult(text, onResult, onError)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                onError("网络错误：${t.message ?: "未知"}")
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                isConnected = false
            }
        })
    }

    /**
     * 发送音频帧。
     * @param audioData PCM 音频数据
     * @param status 0=开始帧, 1=中间帧, 2=结束帧
     */
    fun sendAudio(audioData: ByteArray, status: Int) {
        if (!isConnected) return

        val audioBase64 = Base64.encodeToString(audioData, Base64.NO_WRAP)
        val json = JSONObject().apply {
            if (!firstFrameSent || status == 0) {
                put("common", JSONObject().put("app_id", appId))
                put("business", JSONObject().apply {
                    put("language", "zh_cn")
                    put("domain", "iat")
                    put("accent", "mandarin")
                    put("vad_eos", 1000)
                    put("dwa", "wpgs")
                })
                firstFrameSent = true
            }
            put("data", JSONObject().apply {
                put("status", status)
                put("format", "audio/L16;rate=16000")
                put("encoding", "raw")
                put("audio", audioBase64)
            })
        }.toString()

        webSocket?.send(json)
    }

    /** 发送结束帧（停止录音后调用）。 */
    fun finish() {
        sendAudio(ByteArray(0), 2)
    }

    /** 销毁连接。 */
    fun destroy() {
        webSocket?.close(1000, "finish")
        webSocket = null
        isConnected = false
        firstFrameSent = false
    }

    /** 是否已连接。 */
    fun isConnected(): Boolean = isConnected

    /**
     * 处理识别结果。
     */
    private fun handleResult(
        text: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val json = JSONObject(text)
            val code = json.optInt("code", -1)
            if (code != 0) {
                val message = json.optString("message", "未知错误")
                onError("识别失败($code)：$message")
                destroy()
                return
            }

            val data = json.optJSONObject("data")
            val status = data?.optInt("status", -1)
            val result = data?.optJSONObject("result")
            val pgs = result?.optString("pgs")
            val rg = result?.optJSONArray("rg")

            // 处理替换（pgs=rpl 时替换之前的结果）
            if (pgs == "rpl" && rg != null && rg.length() >= 2) {
                val start = rg.getInt(0)
                val end = rg.getInt(1)
                // 简单处理：清空重新拼接（实际应按段替换）
                resultBuilder.clear()
            }

            // 拼接文字
            val ws = result?.optJSONArray("ws")
            if (ws != null) {
                for (i in 0 until ws.length()) {
                    val cw = ws.optJSONObject(i)?.optJSONArray("cw")
                    for (j in 0 until (cw?.length() ?: 0)) {
                        val w = cw?.optJSONObject(j)?.optString("w")
                        if (w != null) {
                            resultBuilder.append(w)
                        }
                    }
                }
            }

            // status=2 表示最终结果
            if (status == 2) {
                val finalText = resultBuilder.toString().trim()
                resultBuilder.clear()
                destroy()
                if (finalText.isNotEmpty()) {
                    onResult(finalText)
                } else {
                    onError("没听清，请重试")
                }
            }
        } catch (e: Exception) {
            onError("解析结果失败：${e.message}")
            destroy()
        }
    }

    /**
     * 生成讯飞 WebSocket 鉴权 URL。
     */
    private fun generateAuthUrl(): String {
        val host = "iat-api.xfyun.cn"
        val path = "/v2/iat"

        // 1. 生成 date（HTTP 格式，RFC 1123）
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
        dateFormat.timeZone = java.util.TimeZone.getTimeZone("GMT")
        val date = dateFormat.format(Date())

        // 2. 构造签名原始串
        val signatureOrigin = "host: $host\ndate: $date\nGET $path HTTP/1.1"

        // 3. HMAC-SHA256 签名（密钥=APISecret，数据=signatureOrigin）
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(apiSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)
        val signatureBytes = mac.doFinal(signatureOrigin.toByteArray(StandardCharsets.UTF_8))
        val signature = Base64.encodeToString(signatureBytes, Base64.NO_WRAP)

        // 4. 构造 authorization 原始串
        val authorizationOrigin =
            "api_key=\"$apiKey\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"$signature\""

        // 5. Base64 编码 authorization
        val authorization = Base64.encodeToString(
            authorizationOrigin.toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        )

        // 6. URL 编码参数（空格用 %20，不用 +）
        val encodedAuth = URLEncoder.encode(authorization, "UTF-8").replace("+", "%20")
        val encodedDate = URLEncoder.encode(date, "UTF-8").replace("+", "%20")

        // 7. 手动拼接 wss URL（HttpUrl 不支持 wss scheme）
        return "wss://$host$path?authorization=$encodedAuth&date=$encodedDate"
    }
}
