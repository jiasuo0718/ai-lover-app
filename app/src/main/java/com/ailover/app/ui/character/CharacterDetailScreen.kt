package com.ailover.app.ui.character

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ailover.app.data.local.entity.CharacterEntity
import com.ailover.app.data.local.entity.ConversationEntity
import com.ailover.app.data.repository.CharacterRepository
import com.ailover.app.data.repository.ConversationRepository
import com.ailover.app.di.AppContainer
import com.ailover.app.ui.theme.AccentBlue
import com.ailover.app.ui.theme.BubbleSelf
import com.ailover.app.ui.theme.CardWhite
import com.ailover.app.ui.theme.Divider
import com.ailover.app.ui.theme.HintBg
import com.ailover.app.ui.theme.TextPrimary
import com.ailover.app.ui.theme.TextSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ========== ViewModel ==========
class CharacterDetailViewModel(
    private val characterId: Long,
    private val characterRepository: CharacterRepository,
    private val conversationRepository: ConversationRepository
) : ViewModel() {

    private val _character = MutableStateFlow<CharacterEntity?>(null)
    val character: StateFlow<CharacterEntity?> = _character.asStateFlow()

    val conversations: StateFlow<List<ConversationEntity>> =
        conversationRepository.getConversationsByCharacter(characterId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    init {
        viewModelScope.launch {
            _character.value = characterRepository.getCharacterById(characterId)
        }
    }

    suspend fun createConversation(): Long {
        val char = _character.value ?: return 0L
        val conversation = ConversationEntity(
            characterId = char.id,
            title = char.name,
            lastMessage = "开始聊天吧"
        )
        return conversationRepository.insertConversation(conversation)
    }

    fun deleteCharacter() {
        viewModelScope.launch {
            characterRepository.deleteCharacterById(characterId)
        }
    }
}

class CharacterDetailViewModelFactory(
    private val characterId: Long,
    private val characterRepository: CharacterRepository,
    private val conversationRepository: ConversationRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CharacterDetailViewModel(characterId, characterRepository, conversationRepository) as T
    }
}

// ========== Screen ==========
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterDetailScreen(
    characterId: Long,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onStartChat: (Long, String) -> Unit,
    onDeleted: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val viewModel: CharacterDetailViewModel = viewModel(
        factory = CharacterDetailViewModelFactory(
            characterId = characterId,
            characterRepository = AppContainer.characterRepository(),
            conversationRepository = AppContainer.conversationRepository()
        )
    )
    val character by viewModel.character.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val personalityInteractionSource = remember { MutableInteractionSource() }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    modifier = Modifier.height(48.dp),
                    title = { Text("角色资料", color = TextPrimary, fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = onSettingsClick) {
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
                        .height(0.5.dp)
                        .background(Divider)
                )
            }
        },
        containerColor = Color(0xFFEDEDED)
    ) { paddingValues ->
        character?.let { char ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                // ===== 顶部：头像 + 名字 + 人设 + 角色设定（同一块白卡）=====
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardWhite)
                ) {
                    // 头像区
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 头像（圆角方形 64dp）
                            val hasCustomAvatar = char.avatarUri != null && File(char.avatarUri!!).exists()
                            if (hasCustomAvatar) {
                                AsyncImage(
                                    model = File(char.avatarUri!!),
                                    contentDescription = char.name,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(BubbleSelf),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = char.name.firstOrNull()?.toString() ?: "?",
                                        color = Color.White,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // 名字 + 人设摘要 + 创建时间
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = char.name,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (char.personality.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = char.personality.take(30).let {
                                            if (char.personality.length > 30) "$it..." else it
                                        },
                                        fontSize = 13.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                val createTime = remember(char.createdAt) {
                                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                        .format(Date(char.createdAt))
                                }
                                Text(
                                    text = "创建于 $createTime",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    // 极细分割线
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFE5E5EA))
                    )

                    // 角色设定
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 分组标题
                        Text(
                            text = "角色设定",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                        )
                        // 完整人设（点击进编辑页）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = personalityInteractionSource,
                                    indication = rememberRipple(color = Color(0xFFD0D0D5)),
                                    onClick = onEditClick
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = char.personality.ifBlank { "暂无设定，点击编辑" },
                                fontSize = 15.sp,
                                color = if (char.personality.isBlank()) TextSecondary else TextPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ===== 分组二：其他信息（白卡）=====
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardWhite)
                ) {
                    Text(
                        text = "其他信息",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                    )
                    // 创建时间
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("创建时间", fontSize = 15.sp, color = TextSecondary, modifier = Modifier.width(80.dp))
                        val createTimeFull = remember(char.createdAt) {
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                .format(Date(char.createdAt))
                        }
                        Text(createTimeFull, fontSize = 15.sp, color = TextPrimary)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Divider)
                            .padding(start = 16.dp)
                    )
                    // 会话数
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("会话数", fontSize = 15.sp, color = TextSecondary, modifier = Modifier.width(80.dp))
                        Text("${conversations.size} 个", fontSize = 15.sp, color = TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ===== 底部按钮：发消息（白卡，居中，空心气泡）=====
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardWhite)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable {
                                scope.launch {
                                    val conversation = conversations.firstOrNull()
                                    val conversationId = if (conversation != null) {
                                        conversation.id
                                    } else {
                                        viewModel.createConversation()
                                    }
                                    onStartChat(conversationId, char.name)
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "发消息",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除角色") },
            text = { Text("确定删除「${character?.name ?: ""}」吗？该角色的所有会话和消息也会被删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteCharacter()
                    onDeleted()
                }) {
                    Text("删除", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            },
            containerColor = CardWhite
        )
    }
}

// ========== 小组件 ==========
@Composable
private fun DetailSectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        color = TextSecondary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    showArrow: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardWhite)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = rememberRipple(color = Color(0xFFD0D0D5)),
                    onClick = onClick
                ) else Modifier
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = TextSecondary,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            fontSize = 15.sp,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (showArrow) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(">", color = TextSecondary, fontSize = 16.sp)
        }
    }
    // 分割线
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Divider)
            .padding(start = 24.dp)
    )
}
