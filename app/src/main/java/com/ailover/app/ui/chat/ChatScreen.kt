package com.ailover.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ailover.app.data.local.converter.SenderType
import com.ailover.app.data.local.entity.MessageEntity
import com.ailover.app.di.AppContainer
import com.ailover.app.ui.theme.BubbleOther
import com.ailover.app.ui.theme.BubbleSelf
import com.ailover.app.ui.theme.TextSecondary
import com.ailover.app.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: Long,
    title: String,
    onBackClick: () -> Unit
) {
    val viewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(
            conversationId = conversationId,
            messageRepository = AppContainer.messageRepository(),
            conversationRepository = AppContainer.conversationRepository(),
            characterRepository = AppContainer.characterRepository(),
            chatRepository = AppContainer.chatRepository(),
            settingsRepository = AppContainer.settingsRepository()
        )
    )
    val messages by viewModel.messages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val listState = rememberLazyListState()

    // 新消息时自动滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            title,
                            color = Color.White,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isStreaming) {
                            Text(
                                "正在输入...",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            InputBar(
                text = inputText,
                onTextChange = viewModel::onInputTextChange,
                onSend = viewModel::sendMessage,
                isStreaming = isStreaming
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "发送一条消息开始聊天吧",
                        color = TextSecondary,
                        fontSize = 15.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(message = message, characterName = title)
                    }
                }
            }

            // 错误提示
            errorMessage?.let { error ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Red.copy(alpha = 0.9f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = error,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isStreaming: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    if (isStreaming) "AI 正在回复..." else "输入消息...",
                    fontSize = 15.sp
                )
            },
            maxLines = 4,
            shape = RoundedCornerShape(20.dp),
            enabled = !isStreaming
        )
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(
            onClick = onSend,
            enabled = text.isNotBlank() && !isStreaming
        ) {
            Text(
                "发送",
                fontSize = 16.sp,
                color = if (text.isNotBlank() && !isStreaming)
                    MaterialTheme.colorScheme.primary
                else
                    TextSecondary
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: MessageEntity,
    characterName: String
) {
    val isSelf = message.senderType == SenderType.USER
    val isSystem = message.senderType == SenderType.SYSTEM
    val firstChar = if (isSelf) "我" else characterName.firstOrNull()?.toString() ?: "?"
    val bubbleColor = when {
        isSelf -> BubbleSelf
        isSystem -> Color.LightGray
        else -> BubbleOther
    }
    val textColor = when {
        isSelf -> Color.Black
        isSystem -> TextSecondary
        else -> MaterialTheme.colorScheme.onSurface
    }

    // AI 消息内容为空时（流式刚开始）显示占位
    val displayText = if (message.senderType == SenderType.AI && message.content.isEmpty()) {
        "正在思考..."
    } else {
        message.content
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start
    ) {
        if (!isSelf && !isSystem) {
            Avatar(firstChar = firstChar)
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = if (isSelf) 12.dp else 4.dp,
                            topEnd = if (isSelf) 4.dp else 12.dp,
                            bottomStart = 12.dp,
                            bottomEnd = 12.dp
                        )
                    )
                    .background(bubbleColor)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = displayText,
                    fontSize = 16.sp,
                    color = textColor
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = TimeUtils.formatMessageTime(message.timestamp),
                fontSize = 11.sp,
                color = TextSecondary
            )
        }

        if (isSelf) {
            Spacer(modifier = Modifier.width(8.dp))
            Avatar(firstChar = firstChar)
        }
    }
}

@Composable
private fun Avatar(firstChar: String) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = firstChar,
            color = Color.White,
            fontSize = 16.sp
        )
    }
}
