package com.ailover.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ailover.app.ui.theme.TextSecondary

/**
 * 常用 emoji 列表
 */
private val EmojiList = listOf(
    // 表情
    "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
    "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
    "😘", "😗", "😚", "😙", "🥲", "😋", "😛", "😜",
    "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐",
    "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬",
    "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒",
    "🤕", "🤢", "🤮", "🥵", "🥶", "🥴", "😵", "🤯",
    "🤠", "🥳", "😎", "🤓", "🧐", "😕", "😟", "🙁",
    // 爱心
    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
    "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘",
    // 手势
    "👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "🤙",
    "👈", "👉", "👆", "👇", "☝️", "✋", "🤚", "🖐️",
    "🖖", "👋", "🤗", "👏", "🙌", "👐", "🤲", "🤝",
    // 其他
    "🌹", "🌸", "🌺", "🌻", "🌷", "💐", "🎂", "🎉",
    "🎁", "✨", "🔥", "💯", "⭐", "🌟", "💫", "☀️",
    "🌙", "🌈", "☁️", "⚡", "⛄", "🍀", "🎵", "🎶"
)

/**
 * Emoji 表情面板。
 * @param onEmojiClick 点击 emoji 时回调，传入 emoji 字符串
 * @param onSend 点击发送按钮时回调
 * @param onBackspace 点击删除按钮时回调
 */
@Composable
fun EmojiPanel(
    onEmojiClick: (String) -> Unit,
    onSend: () -> Unit,
    onBackspace: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // emoji 网格
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(EmojiList) { emoji ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onEmojiClick(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emoji,
                        fontSize = 22.sp
                    )
                }
            }
        }

        // 底部操作栏：删除 + 发送
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackspace) {
                Icon(
                    imageVector = Icons.Default.Backspace,
                    contentDescription = "删除",
                    tint = TextSecondary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = onSend,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "发送",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
