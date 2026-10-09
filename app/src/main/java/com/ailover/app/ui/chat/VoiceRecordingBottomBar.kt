package com.ailover.app.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun VoiceRecordingBottomBar(
    isCancelling: Boolean,
    volumeLevel: Float
) {
    // 波纹颜色：正常是蓝色，取消是红色
    val waveColor by animateColorAsState(
        targetValue = if (isCancelling) Color(0xFFFF3B30) else Color(0xFF4A90E2),
        animationSpec = tween(150), label = "waveColor"
    )
    // 文字颜色：淡淡的灰色
    val textColor = Color(0xFF8E8E93)

    // 背景渐变：模拟 DeepSeek 那种淡淡的泛光效果
    val bgGradient = if (isCancelling) {
        Brush.verticalGradient(listOf(Color(0x00FF3B30), Color(0x20FF3B30)))
    } else {
        Brush.verticalGradient(listOf(Color(0x004A90E2), Color(0x154A90E2)))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp) // 底部区域高度
            .background(bgGradient),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // 文字
        Text(
            text = if (isCancelling) "松手取消" else "松手发送，上滑取消",
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 底部密集的细长波形（Canvas 一次性绘制，替代40个Box）
        DeepSeekWaveform(
            volumeLevel = volumeLevel,
            color = waveColor,
            barCount = 40
        )

        Spacer(modifier = Modifier.height(16.dp)) // 距离底部导航栏的距离
    }
}

@Composable
private fun DeepSeekWaveform(
    volumeLevel: Float,
    color: Color,
    barCount: Int = 40
) {
    // 每根竖条的固定高度系数（伪随机，范围0.2~1.0，高低差异大，层次分明）
    val baseFactors = remember {
        FloatArray(barCount) { i ->
            0.2f + 0.8f * abs(kotlin.math.sin(i * 1.7f))
        }
    }
    // 触发 Canvas 重绘的 tick
    var tick by remember { mutableStateOf(0) }
    // 低通滤波后的平滑音量
    var smoothedVolume by remember { mutableStateOf(0f) }
    // 始终读取最新的 volumeLevel
    val currentVolume by rememberUpdatedState(volumeLevel)

    // 波形驱动循环：每16ms更新，低通滤波0.15/0.85（新值占85%，涨落都快）
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(16)
            // 非线性映射：×2后开平方(sqrt压缩)，普通说话中等高度，大声才接近顶
            val target = kotlin.math.sqrt((currentVolume * 2f).coerceIn(0f, 1f))
            // 低通滤波：新值占85%，声音一出迅速变高，一停迅速变矮
            smoothedVolume = smoothedVolume * 0.15f + target * 0.85f
            tick++
        }
    }

    Canvas(
        modifier = Modifier
            .height(24.dp)
            .fillMaxWidth()
    ) {
        tick.let { }

        val barWidth = 2.dp.toPx()
        val gap = 2.dp.toPx()
        val cornerRadius = 1.dp.toPx()
        val totalWidth = barCount * barWidth + (barCount - 1) * gap
        val startX = (size.width - totalWidth) / 2f

        for (i in 0 until barCount) {
            // 原地跳动：固定系数 × 当前音量，最大22dp不顶满（Canvas高24dp，留2dp空间）
            val h = (4f + 18f * baseFactors[i] * smoothedVolume).dp.toPx()
            val x = startX + i * (barWidth + gap)
            val y = (size.height - h) / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
            )
        }
    }
}
