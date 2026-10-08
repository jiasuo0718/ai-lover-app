package com.ailover.app.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

        // 底部密集的细长波形
        DeepSeekWaveform(
            volumeLevel = volumeLevel,
            color = waveColor,
            barCount = 40 // 条数很多，铺满底部
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
    // 对音量进行平滑过滤
    val smoothed by animateFloatAsState(
        targetValue = (volumeLevel * 8f).coerceIn(0f, 1f),
        animationSpec = tween(50), label = "volume"
    )

    // 历史数据缓冲，用于产生流动效果
    val history = remember {
        mutableStateListOf<Float>().apply { repeat(barCount) { add(0f) } }
    }
    LaunchedEffect(smoothed) {
        history.removeAt(0)
        history.add(smoothed)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp) // 竖线之间的间距
    ) {
        val center = (barCount - 1) / 2f
        history.forEachIndexed { i, v ->
            // 中心高，两边低
            val dist = abs(i - center) / center
            val shape = 0.3f + (1f - dist) * 0.7f

            // DeepSeek 的波形是很细长的，这里定死最小高度 4dp，最大高度 24dp
            val h = (4f + 20f * shape * (0.1f + v * 0.9f)).dp
            Box(
                modifier = Modifier
                    .width(2.dp) // 非常细的线
                    .height(h)
                    .clip(RoundedCornerShape(1.dp)) // 小圆角
                    .background(color)
            )
        }
    }
}
