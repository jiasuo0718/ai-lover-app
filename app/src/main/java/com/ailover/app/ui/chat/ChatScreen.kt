package com.ailover.app.ui.chat

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ailover.app.data.local.converter.MessageType
import com.ailover.app.data.local.converter.SenderType
import com.ailover.app.data.local.entity.MessageEntity
import com.ailover.app.data.settings.UserProfileRepository
import com.ailover.app.di.AppContainer
import com.ailover.app.ui.theme.BubbleOther
import com.ailover.app.ui.theme.BubbleSelf
import com.ailover.app.ui.theme.CardWhite
import com.ailover.app.ui.theme.Divider
import com.ailover.app.ui.theme.HintBg
import com.ailover.app.ui.theme.TextPrimary
import com.ailover.app.ui.theme.TextSecondary
import com.ailover.app.util.AudioPlayer
import com.ailover.app.util.TimeUtils
import io.github.qdsfdhvh.iconpark.IconParkIcons
import io.github.qdsfdhvh.iconpark.outline.*
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: Long,
    title: String,
    onBackClick: () -> Unit,
    onSettingsClick: (Long) -> Unit
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
    val character by viewModel.character.collectAsState()
    val userProfile by remember { AppContainer.userProfileRepository().userProfile }
        .collectAsState(initial = com.ailover.app.data.settings.UserProfileRepository.UserProfile())
    val inputText by viewModel.inputText.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // 输入模式：文本 / 语音
    var isVoiceMode by remember { mutableStateOf(false) }
    var showEmojiPanel by remember { mutableStateOf(false) }
    var showPlusMenu by remember { mutableStateOf(false) }

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

    // 方案D：先定位后显示。列表先 invisible，定位完成后再 visible
    var isListReady by remember { mutableStateOf(false) }

    // 空状态延迟显示：数据加载前显示空白，避免"闪一下"
    var showEmptyState by remember { mutableStateOf(false) }
    LaunchedEffect(messages.isEmpty()) {
        if (messages.isEmpty()) {
            delay(300)
            if (messages.isEmpty()) {
                showEmptyState = true
            }
        } else {
            showEmptyState = false
        }
    }

    // 新消息或 AI 流式更新时自动滚到底部
    // 首次加载：先滚动定位，再设置 isListReady=true（列表从 invisible 变 visible）
    // 后续新消息：正常滚动
    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) {
            delay(50)
            listState.scrollToItem(messages.size - 1)
            if (!isListReady) {
                delay(50)
                isListReady = true
            }
        }
    }

    // 键盘弹出时自动滚到底部（通过输入框焦点变化触发）
    var isInputFocused by remember { mutableStateOf(false) }
    LaunchedEffect(isInputFocused) {
        if (isInputFocused && messages.isNotEmpty()) {
            delay(200)
            listState.scrollToItem(messages.size - 1)
        }
    }

    // 统一返回逻辑：先收浮层（+菜单/表情面板/键盘），再退页面
    val handleBack: () -> Unit = {
        when {
            showPlusMenu -> {
                showPlusMenu = false
            }
            showEmojiPanel -> {
                showEmojiPanel = false
            }
            isInputFocused -> {
                focusManager.clearFocus()
            }
            else -> {
                onBackClick()
            }
        }
    }
    BackHandler(enabled = true) {
        handleBack()
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    modifier = Modifier.height(48.dp),
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
                        IconButton(onClick = handleBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = TextPrimary
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            character?.id?.let { onSettingsClick(it) }
                        }) {
                            Icon(Icons.Filled.MoreVert, "更多", tint = TextPrimary)
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
                    .background(CardWhite)
            ) {
                // 输入栏：大框内嵌图标（豆包风格）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 大输入框：图标全部内嵌
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(20.dp))
                            .background(Color(0xFFFFFFFF), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 相机图标（IconPark）
                        Icon(
                            imageVector = IconParkIcons.Outline.Camera,
                            contentDescription = "相机",
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // 输入区 或 按住说话
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
                            BasicTextField(
                                value = inputText,
                                onValueChange = {
                                    viewModel.onInputTextChange(it)
                                    showEmojiPanel = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { focusState ->
                                        isInputFocused = focusState.isFocused
                                    },
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 15.sp,
                                    color = TextPrimary
                                ),
                                maxLines = 4,
                                enabled = !isStreaming,
                                cursorBrush = SolidColor(TextPrimary),
                                decorationBox = { innerTextField ->
                                    if (inputText.isEmpty()) {
                                        Text(
                                            "发消息或按住说话...",
                                            fontSize = 15.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // 右侧：语音模式→键盘；空→麦克风；有字→发送
                        if (isVoiceMode) {
                            IconButton(onClick = {
                                isVoiceMode = false
                                showEmojiPanel = false
                                showPlusMenu = false
                                focusTrigger++
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Keyboard,
                                    contentDescription = "键盘",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else if (inputText.isEmpty()) {
                            // 麦克风：IconPark 细线条，切换语音模式
                            IconButton(
                                onClick = {
                                    isVoiceMode = true
                                    showEmojiPanel = false
                                    showPlusMenu = false
                                    focusManager.clearFocus()
                                    if (!hasRecordPermission) {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = IconParkIcons.Outline.VoiceMessage,
                                    contentDescription = "语音",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(0.dp))
                            // 加号：IconPark 细线条，展开菜单
                            IconButton(
                                onClick = {
                                    showPlusMenu = !showPlusMenu
                                    if (showPlusMenu) {
                                        showEmojiPanel = false
                                        focusManager.clearFocus()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                if (showPlusMenu) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "关闭",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = IconParkIcons.Outline.AddOne,
                                        contentDescription = "更多",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        } else {
                            // 有文字：发送按钮（蓝色圆形向上箭头）
                            IconButton(
                                onClick = { viewModel.sendMessage() },
                                enabled = !isStreaming
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0A84FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = IconParkIcons.Outline.ArrowUp,
                                        contentDescription = "发送",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // + 展开菜单：相机/相册/文件/打电话（功能先不做）
                if (showPlusMenu) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF7F7F8))
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // 相机
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFF2F2F7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Camera,
                                        contentDescription = "相机",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("相机", fontSize = 12.sp, color = TextSecondary)
                            }

                            // 相册
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFF2F2F7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PhotoLibrary,
                                        contentDescription = "相册",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("相册", fontSize = 12.sp, color = TextSecondary)
                            }

                            // 文件
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFF2F2F7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AttachFile,
                                        contentDescription = "文件",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("文件", fontSize = 12.sp, color = TextSecondary)
                            }

                            // 打电话
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFF2F2F7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Phone,
                                        contentDescription = "打电话",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("打电话", fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                    }
                }

                // 表情面板（代码保留，暂未触发）
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
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (showEmptyState && messages.isEmpty()) {
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
            } else if (messages.isEmpty()) {
                // 骨架屏：数据加载中显示灰色气泡占位
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false
                ) {
                    items(5) { index ->
                        val isLeft = index % 2 == 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = if (isLeft) Arrangement.Start else Arrangement.End
                        ) {
                            if (isLeft) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF0F0F0))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .width(if (isLeft) 180.dp else 140.dp)
                                    .height(if (index % 3 == 0) 60.dp else 40.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFFF0F0F0))
                            )
                        }
                    }
                }
            } else if (messages.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .alpha(if (isListReady) 1f else 0f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            characterName = title,
                            characterAvatarUri = character?.avatarUri,
                            userNickname = userProfile.nickname,
                            userAvatarUri = userProfile.avatarUri,
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
                // 错误提示（胶囊形轻量提示）
                errorMessage?.let { error ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(HintBg)
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = error,
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
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
    characterAvatarUri: String?,
    userNickname: String,
    userAvatarUri: String?,
    isPlaying: Boolean,
    onPlayClick: () -> Unit
) {
    val isSelf = message.senderType == SenderType.USER
    val isSystem = message.senderType == SenderType.SYSTEM
    val isVoice = message.messageType == MessageType.VOICE
    val firstChar = if (isSelf) userNickname.firstOrNull()?.toString() ?: "我" else characterName.firstOrNull()?.toString() ?: "?"
    // 气泡最大宽度：屏幕的 70%，防止长消息把头像挤出屏幕
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val maxBubbleWidth = screenWidth * 0.7f
    val bubbleColor = when {
        isSelf -> BubbleSelf
        isSystem -> HintBg
        else -> BubbleOther
    }
    val textColor = when {
        isSelf -> TextPrimary
        isSystem -> TextSecondary
        else -> TextPrimary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = when {
            isSelf -> Arrangement.End
            isSystem -> Arrangement.Center
            else -> Arrangement.Start
        }
    ) {
        if (!isSelf && !isSystem) {
            Avatar(firstChar = firstChar, avatarUri = characterAvatarUri)
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = if (!isSystem) Modifier.widthIn(max = maxBubbleWidth) else Modifier,
            horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .then(
                        // 对方气泡加极淡阴影（0.5dp），几乎看不出
                        if (!isSelf && !isSystem)
                            Modifier.shadow(elevation = 0.5.dp, shape = RoundedCornerShape(20.dp))
                        else
                            Modifier
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(bubbleColor)
                    .then(
                        if (isVoice) Modifier.clickable(onClick = onPlayClick) else Modifier
                    )
                    .padding(
                        horizontal = 14.dp,
                        vertical = if (isSystem) 6.dp else 10.dp
                    )
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
                        fontSize = if (isSystem) 12.sp else 15.sp,
                        lineHeight = if (isSystem) 15.6.sp else 19.5.sp,
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
            Avatar(firstChar = firstChar, avatarUri = userAvatarUri)
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
private fun Avatar(firstChar: String, avatarUri: String? = null) {
    val hasCustomAvatar = avatarUri != null && File(avatarUri).exists()
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        if (hasCustomAvatar) {
            AsyncImage(
                model = File(avatarUri!!),
                contentDescription = "头像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = firstChar,
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}
