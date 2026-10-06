package com.ailover.app.ui.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
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
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ailover.app.data.local.converter.MessageType
import com.ailover.app.data.local.converter.SenderType
import com.ailover.app.data.local.entity.MessageEntity
import com.ailover.app.di.AppContainer
import com.ailover.app.ui.theme.BubbleOther
import com.ailover.app.ui.theme.BubbleSelf
import com.ailover.app.ui.theme.CardWhite
import com.ailover.app.ui.theme.Divider
import com.ailover.app.ui.theme.TextPrimary
import com.ailover.app.ui.theme.TextSecondary
import com.ailover.app.util.AudioPlayer
import com.ailover.app.util.TimeUtils
import kotlinx.coroutines.delay

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
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // 输入模式：文本 / 语音
    var isVoiceMode by remember { mutableStateOf(false) }
    var showEmojiPanel by remember { mutableStateOf(false) }

    // 输入框焦点控制：点键盘图标后自动聚焦弹键盘
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    var focusTrigger by remember { mutableStateOf(0) }
    LaunchedEffect(focusTrigger) {
        if (focusTrigger > 0) {
            delay(80)
            focusRequester.requestFocus()
        }
    }

    // 录音权限
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var showPermissionSettingsHint by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRecordPermission = granted
        if (!granted) {
            // shouldShowRequestPermissionRationale 返回 false 说明用户勾选了"不再询问"
            val activity = context as? android.app.Activity
            val shouldShowRationale = activity?.shouldShowRequestPermissionRationale(
                Manifest.permission.RECORD_AUDIO
            ) ?: true
            if (!shouldShowRationale) {
                showPermissionSettingsHint = true
            }
        }
    }

    // 语音播放
    val audioPlayer = remember { AudioPlayer() }
    var currentPlayingId by remember { mutableStateOf<Long?>(null) }

    // 新消息或 AI 流式更新时自动滚到底部（延迟 150ms 确保 item 高度测量完成）
    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) {
            delay(150)
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // 键盘弹出时自动滚到底部，确保最新消息不被键盘遮挡
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && messages.isNotEmpty()) {
            delay(200)
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Column {
                            Text(
                                title,
                                color = TextPrimary,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isStreaming) {
                                Text(
                                    "正在输入...",
                                    color = TextSecondary,
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
                                tint = TextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = CardWhite
                    )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Divider)
                )
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // 输入栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 语音/键盘切换按钮
                    IconButton(onClick = {
                        if (isVoiceMode) {
                            // 从语音切到文字：收起表情面板 + 聚焦输入框 + 弹出键盘
                            isVoiceMode = false
                            showEmojiPanel = false
                            focusTrigger++
                        } else {
                            // 从文字切到语音
                            isVoiceMode = true
                            showEmojiPanel = false
                            focusManager.clearFocus()
                            if (!hasRecordPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }) {
                        Icon(
                            imageVector = if (isVoiceMode)
                                Icons.Filled.Keyboard
                            else
                                Icons.Filled.Mic,
                            contentDescription = if (isVoiceMode) "键盘" else "语音",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // 输入框 或 按住说话按钮
                    if (isVoiceMode) {
                        Box(modifier = Modifier.weight(1f)) {
                            VoiceRecorderButton(
                                conversationId = conversationId,
                                hasPermission = hasRecordPermission,
                                onRequestPermission = {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onVoiceRecorded = { filePath, duration ->
                                    viewModel.sendVoiceMessage(filePath, duration)
                                }
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = {
                                viewModel.onInputTextChange(it)
                                showEmojiPanel = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            placeholder = { Text("输入消息...", fontSize = 15.sp) },
                            maxLines = 4,
                            shape = RoundedCornerShape(20.dp),
                            enabled = !isStreaming
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // 表情按钮：开关互斥，和键盘互斥
                    IconButton(onClick = {
                        if (showEmojiPanel) {
                            // 面板已开：收起面板 + 弹出键盘
                            showEmojiPanel = false
                            focusTrigger++
                        } else {
                            // 面板没开：收起键盘 + 打开面板
                            isVoiceMode = false
                            showEmojiPanel = true
                            focusManager.clearFocus()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEmotions,
                            contentDescription = "表情",
                            tint = if (showEmojiPanel)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // 文本模式下显示发送按钮
                    if (!isVoiceMode) {
                        TextButton(
                            onClick = { viewModel.sendMessage() },
                            enabled = inputText.isNotBlank() && !isStreaming,
                            modifier = Modifier.focusProperties { canFocus = false }
                        ) {
                            Text(
                                "发送",
                                fontSize = 16.sp,
                                color = if (inputText.isNotBlank() && !isStreaming)
                                    MaterialTheme.colorScheme.primary
                                else
                                    TextSecondary
                            )
                        }
                    }
                }

                // 表情面板
                if (showEmojiPanel && !isVoiceMode) {
                    EmojiPanel(
                        onEmojiClick = { emoji ->
                            viewModel.onInputTextChange(inputText + emoji)
                        },
                        onSend = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendEmojiMessage()
                                showEmojiPanel = false
                            }
                        },
                        onBackspace = {
                            if (inputText.isNotEmpty()) {
                                viewModel.onInputTextChange(inputText.dropLast(1))
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
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
                        MessageBubble(
                            message = message,
                            characterName = title,
                            isPlaying = currentPlayingId == message.id,
                            onPlayClick = {
                                if (currentPlayingId == message.id) {
                                    audioPlayer.stop()
                                    currentPlayingId = null
                                } else {
                                    audioPlayer.play(
                                        filePath = message.content,
                                        messageId = message.id
                                    ) {
                                        currentPlayingId = null
                                    }
                                    currentPlayingId = message.id
                                }
                            }
                        )
                    }
                }
            }

            // 顶部提示区域（权限提示 + 错误提示）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                // 权限设置提示（橙色，点击跳转系统设置）
                if (showPermissionSettingsHint) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFF9800).copy(alpha = 0.9f))
                            .clickable {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                )
                                intent.data = android.net.Uri.fromParts(
                                    "package", context.packageName, null
                                )
                                context.startActivity(intent)
                                showPermissionSettingsHint = false
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "录音权限被拒绝，点击去系统设置开启",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }
                // 错误提示（红色）
                errorMessage?.let { error ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
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
}

@Composable
private fun MessageBubble(
    message: MessageEntity,
    characterName: String,
    isPlaying: Boolean,
    onPlayClick: () -> Unit
) {
    val isSelf = message.senderType == SenderType.USER
    val isSystem = message.senderType == SenderType.SYSTEM
    val isVoice = message.messageType == MessageType.VOICE
    val firstChar = if (isSelf) "我" else characterName.firstOrNull()?.toString() ?: "?"
    val bubbleColor = when {
        isSelf -> BubbleSelf
        isSystem -> Color.LightGray
        else -> BubbleOther
    }
    val textColor = when {
        isSelf -> TextPrimary
        isSystem -> TextSecondary
        else -> MaterialTheme.colorScheme.onSurface
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
                        if (isSelf)
                            RoundedCornerShape(20.dp)
                        else
                            RoundedCornerShape(
                                topStart = 4.dp,
                                topEnd = 12.dp,
                                bottomStart = 12.dp,
                                bottomEnd = 12.dp
                            )
                    )
                    .background(bubbleColor)
                    .then(
                        if (isVoice) Modifier.clickable(onClick = onPlayClick) else Modifier
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (isVoice) {
                    // 语音气泡：波形 + 时长
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isPlaying) {
                            PlayingWaveform()
                        } else {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "播放",
                                tint = textColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "${message.voiceDuration ?: 0}″",
                            fontSize = 15.sp,
                            color = textColor
                        )
                    }
                } else {
                    // 文本气泡
                    val displayText = if (message.senderType == SenderType.AI && message.content.isEmpty()) {
                        "正在思考..."
                    } else {
                        message.content
                    }
                    Text(
                        text = displayText,
                        fontSize = 16.sp,
                        color = textColor
                    )
                }
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

/**
 * 播放中的波形动画（三条竖线跳动）
 */
@Composable
private fun PlayingWaveform() {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave1"
    )
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave2"
    )
    val scale3 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, delayMillis = 200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 14.dp)
                .scale(scale1)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurface)
        )
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 14.dp)
                .scale(scale2)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurface)
        )
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 14.dp)
                .scale(scale3)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurface)
        )
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
