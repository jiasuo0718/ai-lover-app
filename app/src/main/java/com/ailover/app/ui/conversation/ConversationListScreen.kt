package com.ailover.app.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ailover.app.data.local.relation.ConversationWithCharacter
import com.ailover.app.di.AppContainer
import com.ailover.app.ui.theme.BubbleSelf
import com.ailover.app.ui.theme.TextSecondary
import com.ailover.app.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onConversationClick: (Long, String) -> Unit,
    onCharacterManageClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val viewModel: ConversationViewModel = viewModel(
        factory = ConversationViewModelFactory(AppContainer.conversationRepository())
    )
    val conversations by viewModel.conversations.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "AI恋人",
                        color = Color.White,
                        fontSize = 18.sp
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置", tint = Color.White)
                    }
                    IconButton(onClick = onCharacterManageClick) {
                        Icon(Icons.Filled.Group, contentDescription = "角色管理", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "暂无会话",
                    color = TextSecondary,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(conversations, key = { it.conversation.id }) { item ->
                    ConversationItem(
                        item = item,
                        onClick = {
                            onConversationClick(
                                item.conversation.id,
                                item.character?.name ?: item.conversation.title
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(
    item: ConversationWithCharacter,
    onClick: () -> Unit
) {
    val name = item.character?.name ?: item.conversation.title
    val lastMessage = item.conversation.lastMessage ?: "开始聊天吧"
    val time = TimeUtils.formatConversationTime(item.conversation.updatedAt)
    val firstChar = name.firstOrNull()?.toString() ?: "?"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(BubbleSelf),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = firstChar,
                color = Color.White,
                fontSize = 20.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 名字 + 最后消息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = lastMessage,
                fontSize = 14.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 时间
        Text(
            text = time,
            fontSize = 12.sp,
            color = TextSecondary
        )
    }
}
