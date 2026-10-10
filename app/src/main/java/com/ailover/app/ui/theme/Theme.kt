package com.ailover.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// 通过 CompositionLocal 传递用户选择的主题状态，让所有颜色属性都能读取
val LocalDarkTheme = compositionLocalOf { false }

// 主题感知颜色：浅色保持原样，深色参考iOS/微信，层次分明
val AccentBlue: Color @Composable get() = Color(0xFF0A84FF)
val BgMain: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFF121212) else Color(0xFFFFFFFF)
val CardWhite: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
val NavBarBg: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFF0A0A0A) else Color(0xFFFFFFFF)
val TextPrimary: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFFFFFFFF) else Color(0xFF1A1A1A)
val TextSecondary: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFF98989F) else Color(0xFF8E8E93)
val Divider: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFF38383A) else Color(0xFFF0F0F0)
val HintBg: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
val PageBg: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFF1C1C1E) else Color(0xFFEDEDED)
val DangerRed: Color @Composable get() = Color(0xFFFF3B30)
val DisabledGray: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFF636366) else Color(0xFFC7C7CC)
val LightGray: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFF38383A) else Color(0xFFE5E5EA)
val WarningOrange: Color @Composable get() = Color(0xFFFF9800)

// 气泡颜色
val BubbleSelf: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFF1C3A5E) else Color(0xFFEDF1F7)
val BubbleOther: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFF2C2C2E) else Color(0xFFF7F7F8)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    secondary = Color(0xFF0A84FF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFFFFFFF)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    secondary = Color(0xFF0A84FF),
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1C1C1E),
    onSurface = Color.White
)

@Composable
fun AILoverTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
