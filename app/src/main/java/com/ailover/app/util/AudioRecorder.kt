package com.ailover.app.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * 语音录制工具，封装 MediaRecorder。
 * 输出 AAC 编码的 .m4a 文件。
 */
class AudioRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTimeMs: Long = 0
    private var isRecording = false

    /**
     * 开始录音。
     * @param outputFile 输出文件
     */
    fun start(outputFile: File) {
        if (isRecording) return

        outputFile.parentFile?.mkdirs()
        currentFile = outputFile

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(64000)
            setAudioSamplingRate(44100)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }

        startTimeMs = System.currentTimeMillis()
        isRecording = true
    }

    /**
     * 停止录音并返回时长（秒）。
     */
    fun stop(): Int {
        if (!isRecording) return 0
        val duration = getElapsedSeconds()
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // 停止时可能抛出异常（如录制时间过短），忽略
        }
        mediaRecorder = null
        isRecording = false
        return duration.coerceAtLeast(1)  // 至少 1 秒
    }

    /**
     * 取消录音，删除已录制的文件。
     */
    fun cancel() {
        if (!isRecording) return
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // 忽略
        }
        mediaRecorder = null
        isRecording = false
        currentFile?.let { if (it.exists()) it.delete() }
        currentFile = null
    }

    /**
     * 获取已录制时长（秒）。
     */
    fun getElapsedSeconds(): Int {
        if (!isRecording) return 0
        return ((System.currentTimeMillis() - startTimeMs) / 1000).toInt()
    }

    fun isRecording(): Boolean = isRecording

    /**
     * 生成语音文件路径，按会话归档。
     * 路径: {app外部存储}/voices/{conversationId}/{timestamp}.m4a
     */
    companion object {
        fun generateVoiceFile(context: Context, conversationId: Long): File {
            val dir = File(context.getExternalFilesDir(null), "voices/$conversationId")
            dir.mkdirs()
            return File(dir, "${System.currentTimeMillis()}.m4a")
        }
    }
}
