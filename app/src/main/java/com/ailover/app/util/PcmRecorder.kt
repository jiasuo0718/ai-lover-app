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
     */
    fun start(onAudioData: (ByteArray) -> Unit) {
        if (isRecording) return
        isRecording = true

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
        audioRecord?.startRecording()

        recordingThread = Thread {
            val buffer = ByteArray(FRAME_SIZE)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, FRAME_SIZE) ?: 0
                if (read > 0) {
                    onAudioData(buffer.copyOf(read))
                }
            }
        }
        recordingThread?.start()
    }

    /** 停止录音。 */
    fun stop() {
        isRecording = false
        try {
            recordingThread?.join(1000)
        } catch (_: InterruptedException) {
        }
        audioRecord?.stop()
        audioRecord?.release()
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
