package com.ailover.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ailover.app.ui.theme.TextSecondary
import com.ailover.app.util.AudioRecorder
import kotlinx.coroutines.delay
import java.io.File

private const val MAX_RECORD_SECONDS = 60
private const val CANCEL_THRESHOLD_DP = 100f

/**
 * 按住说话按钮。
 * - 按下开始录音，显示秒数
 * - 上滑超过阈值显示"松开取消"
 * - 松手发送（或取消）
 * - 录满 60 秒自动停止发送
 */
@Composable
fun VoiceRecorderButton(
    conversationId: Long,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onVoiceRecorded: (filePath: String, duration: Int) -> Unit
) {
    val context = LocalContext.current
    val recorder = remember { AudioRecorder(context) }
    // 用 Android 原生 API 计算 dp 转 px，避免 Compose Density 接收者问题
    val cancelThresholdPx = remember {
        context.resources.displayMetrics.density * CANCEL_THRESHOLD_DP
    }

    var isRecording by remember { mutableStateOf(false) }
    var isCancelMode by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var startY by remember { mutableStateOf(0f) }
    var currentOutputFile by remember { mutableStateOf<File?>(null) }

    // 60 秒计时 + 自动停止
    LaunchedEffect(isRecording) {
        if (!isRecording) return@LaunchedEffect
        while (isRecording) {
            delay(1000)
            elapsedSeconds = recorder.getElapsedSeconds()
            if (elapsedSeconds >= MAX_RECORD_SECONDS) {
                val file = currentOutputFile ?: break
                val duration = recorder.stop()
                isRecording = false
                isCancelMode = false
                currentOutputFile = null
                onVoiceRecorded(file.absolutePath, duration)
                break
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (isRecording) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
            .pointerInput(cancelThresholdPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!hasPermission) {
                        onRequestPermission()
                        return@awaitEachGesture
                    }

                    val outputFile = AudioRecorder.generateVoiceFile(context, conversationId)
                    currentOutputFile = outputFile
                    recorder.start(outputFile)
                    isRecording = true
                    isCancelMode = false
                    elapsedSeconds = 0
                    startY = down.position.y

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break

                            if (change.pressed) {
                                val dy = startY - change.position.y
                                isCancelMode = dy > cancelThresholdPx
                            } else {
                                if (isCancelMode) {
                                    recorder.cancel()
                                } else {
                                    val duration = recorder.stop()
                                    if (duration >= 1) {
                                        onVoiceRecorded(outputFile.absolutePath, duration)
                                    } else {
                                        recorder.cancel()
                                    }
                                }
                                isRecording = false
                                isCancelMode = false
                                currentOutputFile = null
                                break
                            }
                        }
                    } catch (e: Exception) {
                        recorder.cancel()
                        isRecording = false
                        isCancelMode = false
                        currentOutputFile = null
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isRecording) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isCancelMode) "松开取消" else "${elapsedSeconds}s",
                    fontSize = 16.sp,
                    color = if (isCancelMode) Color.Red else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isCancelMode) "松开手指取消发送" else "上滑取消",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "按住说话",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = if (hasPermission) "按住说话" else "点击授权录音权限",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
