package com.ailover.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ailover.app.util.VoiceToText

/**
 * 按住说话按钮（语音转文字版）。
 * - 按住开始录音识别
 * - 松手停止识别，回调识别到的文字
 * - 识别失败回调错误信息
 */
@Composable
fun VoiceRecorderButton(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onTextRecognized: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val voiceToText = remember { VoiceToText(context) }
    var isRecording by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { voiceToText.destroy() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (isRecording)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.surface
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!hasPermission) {
                        onRequestPermission()
                        return@awaitEachGesture
                    }

                    isRecording = true
                    voiceToText.startListening(
                        onResult = { text ->
                            isRecording = false
                            onTextRecognized(text)
                        },
                        onError = { error ->
                            isRecording = false
                            onError(error)
                        }
                    )

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                voiceToText.stopListening()
                                break
                            }
                        }
                    } catch (e: Exception) {
                        voiceToText.stopListening()
                        isRecording = false
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when {
                isRecording -> "正在说话..."
                hasPermission -> "按住说话"
                else -> "点击授权录音权限"
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (isRecording)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface
        )
    }
}
