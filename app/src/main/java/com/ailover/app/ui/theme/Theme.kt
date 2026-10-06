package com.ailover.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 白灰简洁风格配色（iMessage / Telegram / Notion 风格）
val AccentBlue = Color(0xFF0A84FF)       // 强调色（iOS 蓝）
val BgMain = Color(0xFFFAFAFA)           // 主背景
val CardWhite = Color(0xFFFFFFFF)         // 卡片/输入栏
val TextPrimary = Color(0xFF1A1A1A)       // 主文字
val TextSecondary = Color(0xFF8E8E93)     // 次要文字
val Divider = Color(0xFFE5E5EA)           // 分割线

// 气泡颜色（下一轮改，暂时保留）
val BubbleSelf = Color(0xFF95EC69)
val BubbleOther = Color.White

private val LightColorScheme = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    secondary = AccentBlue,
    background = BgMain,
    onBackground = TextPrimary,
    surface = CardWhite,
    onSurface = TextPrimary,
    surfaceVariant = BgMain
)

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    secondary = AccentBlue,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White
)

@Composable
fun AILoverTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
