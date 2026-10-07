package com.ailover.app.ui.character

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
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
        }
    ) { paddingValues ->
        character?.let { char ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                // ===== 顶部：头像 + 名字 + 人设摘要 =====
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 大头像
                    val hasCustomAvatar = char.avatarUri != null && File(char.avatarUri!!).exists()
                    if (hasCustomAvatar) {
                        AsyncImage(
                            model = File(char.avatarUri!!),
                            contentDescription = char.name,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(BubbleSelf),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = char.name.firstOrNull()?.toString() ?: "?",
                                color = Color.White,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 名字
                    Text(
                        text = char.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 人设摘要
                    if (char.personality.isNotBlank()) {
                        Text(
                            text = char.personality.take(30).let {
                                if (char.personality.length > 30) "$it..." else it
                            },
                            fontSize = 13.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ===== 分组：角色设定 =====
                DetailSectionTitle("角色设定")
                DetailRow(
                    label = "完整人设",
                    value = char.personality.ifBlank { "暂无设定" },
                    onClick = onEditClick,
                    showArrow = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ===== 分组：其他信息 =====
                DetailSectionTitle("其他信息")
                val createTime = remember(char.createdAt) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(Date(char.createdAt))
                }
                DetailRow(
                    label = "创建时间",
                    value = createTime,
                    showArrow = false
                )
                DetailRow(
                    label = "会话数",
                    value = "${conversations.size} 个会话",
                    showArrow = false
                )

                Spacer(modifier = Modifier.height(32.dp))

                // ===== 底部按钮：只保留发消息 =====
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    // 发消息（主按钮）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentBlue)
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
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.ChatBubble,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "发消息",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardWhite)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
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
