package com.ailover.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val WeChatGreen = Color(0xFF07C160)
val WeChatGreenDark = Color(0xFF06AD56)
val WeChatBg = Color(0xFFF5F5F5)
val BubbleSelf = Color(0xFF95EC69)
val BubbleOther = Color.White
val TextPrimary = Color(0xFF1A1A1A)
val TextSecondary = Color(0xFF999999)
val Divider = Color(0xFFE8E8E8)

private val LightColorScheme = lightColorScheme(
    primary = WeChatGreen,
    onPrimary = Color.White,
    secondary = WeChatGreenDark,
    background = WeChatBg,
    onBackground = TextPrimary,
    surface = Color.White,
    onSurface = TextPrimary,
    surfaceVariant = WeChatBg
)

private val DarkColorScheme = darkColorScheme(
    primary = WeChatGreen,
    onPrimary = Color.White,
    secondary = WeChatGreenDark,
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
