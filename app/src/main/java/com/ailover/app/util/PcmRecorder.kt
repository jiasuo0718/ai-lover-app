package com.ailover.app.util

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat

/**
 * PCM 录音工具。
 * 录制 16k 16bit 单声道 PCM 音频，用于讯飞语音识别。
 */
class PcmRecorder {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_SIZE = 1280 // 40ms 帧：16000 * 2 * 0.04 = 1280 字节
    }

    /**
     * 开始录音。
     * @param onAudioData 每帧音频数据回调（PCM 16k 16bit）
     * @param onVolume 音量回调（0-1，RMS 归一化）
     * @return true=开始成功，false=初始化失败
     */
    fun start(
        onAudioData: (ByteArray) -> Unit,
        onVolume: ((Float) -> Unit)? = null
    ): Boolean {
        if (isRecording) return true
        isRecording = true

        return try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
            )
            val bufferSize = maxOf(minBufferSize, FRAME_SIZE * 4)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            // 检查初始化状态
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                isRecording = false
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()

            recordingThread = Thread {
                val buffer = ByteArray(FRAME_SIZE)
                while (isRecording) {
                    try {
                        val read = audioRecord?.read(buffer, 0, FRAME_SIZE) ?: 0
                        if (read > 0) {
                            val data = buffer.copyOf(read)
                            onAudioData(data)
                            onVolume?.invoke(calculateRms(data))
                        }
                    } catch (_: Exception) {
                        // 读取异常，忽略
                    }
                }
            }
            recordingThread?.start()
            true
        } catch (e: Exception) {
            isRecording = false
            audioRecord?.release()
            audioRecord = null
            false
        }
    }

    /** 计算 PCM 数据的 RMS 音量，归一化到 0-1。 */
    private fun calculateRms(audioData: ByteArray): Float {
        if (audioData.size < 2) return 0f
        var sum = 0.0
        var count = 0
        var i = 0
        while (i < audioData.size - 1) {
            val sample = (audioData[i].toInt() and 0xFF) or (audioData[i + 1].toInt() shl 8)
            val s = if (sample > 32767) sample - 65536 else sample
            sum += s.toDouble() * s.toDouble()
            count++
            i += 2
        }
        if (count == 0) return 0f
        val rms = Math.sqrt(sum / count).toFloat()
        // 归一化：16bit 范围 0-32768，经验阈值 2000 以上算大声
        return (rms / 2000f).coerceIn(0f, 1f)
    }

    /** 停止录音。 */
    fun stop() {
        isRecording = false
        try {
            recordingThread?.join(1000)
        } catch (_: InterruptedException) {
        }
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
            // 停止异常，忽略
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
            // 释放异常，忽略
        }
        audioRecord = null
    }

    /** 是否正在录音。 */
    fun isRecording(): Boolean = isRecording

    /** 检查录音权限。 */
    fun hasPermission(context: android.content.Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}
