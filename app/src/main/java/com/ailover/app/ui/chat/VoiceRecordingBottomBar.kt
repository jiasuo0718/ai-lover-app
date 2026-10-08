package com.ailover.app.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    // 正常录音背景是深灰色，上滑取消时背景变成暗红色
    val bgColor by animateColorAsState(
        targetValue = if (isCancelling) Color(0xFF3A0A08) else Color(0xFF2C2C2E),
        animationSpec = tween(150), label = "bg"
    )
    // 波纹和文字的颜色
    val contentColor by animateColorAsState(
        targetValue = if (isCancelling) Color(0xFFFF453A) else Color(0xFF4A90E2), // 豆包的蓝色
        animationSpec = tween(150), label = "content"
    )
    // 取消时文字变红
    val textColor = if (isCancelling) Color(0xFFFF453A) else Color(0xFFEEEEEE)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .height(48.dp) // 高度与你的输入栏保持一致
            .clip(RoundedCornerShape(24.dp)) // 豆包那种大圆角
            .background(bgColor),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center // 居中排列
    ) {
        Text(
            text = if (isCancelling) "松手取消" else "松手发送，上滑取消",
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 动态蓝色波纹
        LiveWaveform(
            volumeLevel = volumeLevel,
            color = contentColor,
            barCount = 12 // 条数不要太多，精致即可
        )
    }
}

@Composable
private fun LiveWaveform(
    volumeLevel: Float,
    color: Color,
    barCount: Int = 12
) {
    // 对音量进行放大和平滑，防止抖动
    val smoothed by animateFloatAsState(
        targetValue = (volumeLevel * 6f).coerceIn(0f, 1f),
        animationSpec = tween(50), label = "volume"
    )
    val history = remember {
        mutableStateListOf<Float>().apply { repeat(barCount) { add(0f) } }
    }
    LaunchedEffect(smoothed) {
        history.removeAt(0)
        history.add(smoothed)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp) // 间距
    ) {
        val center = (barCount - 1) / 2f
        history.forEachIndexed { i, v ->
            // 让波纹呈中间高，两边低的形状
            val dist = abs(i - center) / center
            val shape = 0.45f + (1f - dist) * 0.55f

            // 高度在 4dp 到 20dp 之间根据音量变化
            val h = (4f + 16f * shape * (0.15f + v * 0.85f)).dp
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}
