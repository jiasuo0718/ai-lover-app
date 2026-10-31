package com.ailover.app.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 火山引擎【豆包语音合成大模型 2.0（Doubao-Seed-TTS 2.0）】TTS 工具类。
 *
 * 协议：双向流式语音合成 WebSocket（官方文档：豆包语音 > 双向流式语音合成）
 *   wss://openspeech.bytedance.com/api/v3/tts/bidirection
 *   鉴权：请求头 X-Api-Key（控制台 > API Key 管理）、X-Api-Resource-Id: seed-tts-2.0
 *
 * 能力：
 *  - 智能情感预测：把整段文本完整传给大模型，模型自行理解语境并输出喜怒哀乐语气，无需手动传情感参数。
 *  - 高级指令注入（预留）：通过 context_texts 传入自然语言语音指令（如"用带着笑意的温柔语气说"），
 *    仅豆包2.0情感音色（_uranus_bigtts）支持，且该字段不参与计费。
 *  - 流式边下边播：音频以 pcm 流式返回，用 AudioTrack 实时写入播放，低延迟。
 *
 * 注意：鉴权密钥由 App 的"编辑配置"页同步进来（ApiProfile.volcTtsApiKey），运行时通过 setApiKey 更新。
 * 并发安全：同一时间仅允许一路合成，新请求会先停止上一路；播放器/连接用锁保护。
 */
object VolcTtsManager {

    private const val TAG = "VolcTtsManager"

    /** 双向流式 WebSocket 接口地址 */
    private const val WS_URL = "wss://openspeech.bytedance.com/api/v3/tts/bidirection"

    // ===== 鉴权凭证（动态：由 App 编辑配置页的"火山 TTS ApiKey"同步进来，运行时可用 setApiKey 更新）=====
    @Volatile
    private var apiKey: String = ""

    /** 更新鉴权密钥（火山引擎控制台 > 豆包语音 > API Key 管理）。 */
    fun setApiKey(key: String?) {
        apiKey = key.orEmpty().trim()
    }

    /** 模型版本：豆包语音合成大模型 2.0 */
    private const val RESOURCE_ID = "seed-tts-2.0"

    /** 流式音频格式与采样率（pcm 最适合流式播放，配合 AudioTrack） */
    private const val FORMAT = "pcm"
    private const val SAMPLE_RATE = 24000

    /** 预设音色（豆包语音合成模型 2.0 情感音色，speaker 均真实存在于官方音色库）。 */
    data class Voice(val displayName: String, val voiceType: String)

    val presetVoices = listOf(
        Voice("温柔女声", "zh_female_xiaohe_uranus_bigtts"),
        Voice("知性御姐", "zh_female_gaolengyujie_uranus_bigtts"),
        Voice("清朗男声", "zh_male_huolixiaoge_uranus_bigtts")
    )

    /** 当前音色（默认第一个），@Volatile 保证跨线程可见 */
    @Volatile
    private var currentVoiceType: String = presetVoices[0].voiceType

    /** 预留：全局自然语言语音指令（会作为 context_texts 传入，可空） */
    @Volatile
    private var globalContextTexts: List<String> = emptyList()

    private val lock = Any()

    @Volatile
    private var activeWebSocket: WebSocket? = null

    @Volatile
    private var audioTrack: AudioTrack? = null

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)   // 流式长连接，读不设超时
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun currentVoiceType(): String = currentVoiceType

    fun setCurrentVoiceType(voiceType: String) {
        currentVoiceType = voiceType
    }

    fun displayNameOf(voiceType: String): String =
        presetVoices.firstOrNull { it.voiceType == voiceType }?.displayName ?: voiceType

    /**
     * 预留接口：设置全局自然语言语音指令（会注入 context_texts）。
     * 例如："用带着笑意的温柔语气说"。传空则清除。
     */
    fun setContextInstruction(instruction: String?) {
        globalContextTexts = instruction?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
    }

    /**
     * 合成并流式播放指定文本。
     * @param text         待合成文本（AI 回复原文，模型自动理解情感）
     * @param contextTexts 自然语言语音指令列表（预留，默认取全局指令）
     */
    suspend fun play(
        text: String,
        contextTexts: List<String> = globalContextTexts,
        onStart: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (text.isBlank()) return
        withContext(Dispatchers.IO) {
            stop()
            val failed = AtomicBoolean(false)
            val connected = CountDownLatch(1)
            val sessionReady = CountDownLatch(1)
            val finished = CountDownLatch(1)
            val sessionId = UUID.randomUUID().toString()
            val connectId = UUID.randomUUID().toString()
            var track: AudioTrack? = null

            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    // 建立连接 → 发送 StartConnection
                    sendJson(webSocket, event("StartConnection"))
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val event = try {
                        JsonParser.parseString(text).asJsonObject.get("EventType")?.asString
                    } catch (e: Exception) { null }
                    when (event) {
                        "ConnectionStarted" -> connected.countDown()
                        "SessionStarted" -> sessionReady.countDown()
                        "SessionFinished" -> finished.countDown()
                        "ConnectionFinished", "SessionCanceled" -> finished.countDown()
                        "ConnectionFailed", "SessionFailed" -> {
                            failed.set(true)
                            finished.countDown()
                            sessionReady.countDown()
                            connected.countDown()
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    // 音频分片：边下边播
                    track?.write(bytes.toByteArray(), 0, bytes.size)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "ws failure", t)
                    failed.set(true)
                    finished.countDown()
                    sessionReady.countDown()
                    connected.countDown()
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }
            }

            try {
                // 初始化 AudioTrack（pcm16 单声道）
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                synchronized(lock) { audioTrack = track }
                track.play()

                val request = Request.Builder()
                    .url(WS_URL)
                    .header("X-Api-Key", apiKey)
                    .header("X-Api-Resource-Id", RESOURCE_ID)
                    .header("X-Api-Connect-Id", connectId)
                    .header("X-Control-Require-Usage-Tokens-Return", "*")
                    .build()
                val ws = client.newWebSocket(request, listener)
                synchronized(lock) { activeWebSocket = ws }

                // 1) StartConnection → 等待 ConnectionStarted
                if (!connected.await(10, TimeUnit.SECONDS)) {
                    onError("TTS 建连超时")
                    return@withContext
                }
                // 2) StartSession → 等待 SessionStarted
                val startSession = sessionRequest(sessionId, contextTexts)
                sendJson(ws, startSession)
                if (!sessionReady.await(10, TimeUnit.SECONDS)) {
                    onError("TTS 会话建立失败")
                    return@withContext
                }
                // 3) TaskRequest：发送整段文本，交由模型自动理解情感
                sendJson(ws, taskRequest(sessionId, text))
                onStart()

                // 4) 等待会话结束（音频已边下边播）
                finished.await(60, TimeUnit.SECONDS)
                if (failed.get()) {
                    onError("TTS 合成失败")
                    return@withContext
                }
            } catch (e: Exception) {
                Log.e(TAG, "tts play failed", e)
                onError("TTS 异常: ${e.message ?: "未知"}")
            } finally {
                // 5) 结束会话与连接，释放资源
                runCatching { activeWebSocket?.send("""{"event":"FinishSession","session_id":"$sessionId"}""") }
                runCatching { activeWebSocket?.send("""{"event":"FinishConnection"}""") }
                runCatching { activeWebSocket?.close(1000, "done") }
                synchronized(lock) { activeWebSocket = null }
                runCatching {
                    track?.apply {
                        if (playState == AudioTrack.PLAYSTATE_PLAYING) stop()
                        release()
                    }
                }
                synchronized(lock) { audioTrack = null }
            }
        }
    }

    /** 停止播放并释放资源（ViewModel onCleared 时调用） */
    fun stop() {
        synchronized(lock) {
            runCatching { activeWebSocket?.cancel() }
            activeWebSocket = null
            runCatching {
                audioTrack?.apply {
                    if (playState == AudioTrack.PLAYSTATE_PLAYING) stop()
                    release()
                }
            }
            audioTrack = null
        }
    }

    /** 释放资源 */
    fun release() {
        stop()
    }

    // ===== 协议工具 =====

    private fun sendJson(ws: WebSocket, json: String) {
        runCatching { ws.send(json) }
    }

    private fun event(name: String): String = """{"event":"$name"}"""

    private fun sessionRequest(sessionId: String, contextTexts: List<String>): String {
        val audio = JsonObject().apply {
            addProperty("format", FORMAT)
            addProperty("sample_rate", SAMPLE_RATE)
        }
        val reqParams = JsonObject().apply {
            addProperty("speaker", currentVoiceType)
            add("audio_params", audio)
            if (contextTexts.isNotEmpty()) {
                val arr = com.google.gson.JsonArray()
                contextTexts.forEach { arr.add(it) }
                add("context_texts", arr)
            }
        }
        return JsonObject().apply {
            addProperty("event", "StartSession")
            addProperty("session_id", sessionId)
            add("req_params", reqParams)
        }.toString()
    }

    private fun taskRequest(sessionId: String, text: String): String = JsonObject().apply {
        addProperty("event", "TaskRequest")
        addProperty("session_id", sessionId)
        val req = JsonObject().apply { addProperty("text", text) }
        add("req_params", req)
    }.toString()
}
