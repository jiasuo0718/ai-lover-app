package com.ailover.app.util

import android.media.MediaPlayer
import java.io.File

/**
 * 语音播放工具，封装 MediaPlayer。
 * 同一时间只播放一条语音，播放新语音时自动停止上一条。
 */
class AudioPlayer {

    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingId: Long? = null
    private var onCompletionListener: (() -> Unit)? = null

    /**
     * 播放指定语音文件。
     * @param filePath 文件路径
     * @param messageId 消息 ID，用于判断当前播放的是哪条
     * @param onComplete 播放完成回调
     */
    fun play(filePath: String, messageId: Long, onComplete: () -> Unit) {
        stop()

        val file = File(filePath)
        if (!file.exists()) {
            onComplete()
            return
        }

        onCompletionListener = onComplete
        currentPlayingId = messageId

        mediaPlayer = MediaPlayer().apply {
            setDataSource(filePath)
            setOnCompletionListener {
                stop()
                onCompletionListener?.invoke()
            }
            setOnErrorListener { _, _, _ ->
                stop()
                onCompletionListener?.invoke()
                true
            }
            prepare()
            start()
        }
    }

    /**
     * 停止播放。
     */
    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            // 忽略
        }
        mediaPlayer = null
        currentPlayingId = null
        onCompletionListener = null
    }

    /**
     * 判断指定消息是否正在播放。
     */
    fun isPlaying(messageId: Long): Boolean = currentPlayingId == messageId

    /**
     * 获取当前播放的消息 ID。
     */
    fun getCurrentPlayingId(): Long? = currentPlayingId
}
