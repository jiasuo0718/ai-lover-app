package com.ailover.app.ui.conversation

import com.ailover.app.ui.theme.*
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ailover.app.data.local.entity.CharacterEntity
import com.ailover.app.data.local.relation.ConversationWithCharacter
import com.ailover.app.di.AppContainer
import com.ailover.app.util.TimeUtils
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onConversationClick: (Long, String) -> Unit,
    onAddCharacterClick: () -> Unit
) {
    val viewModel: ConversationViewModel = viewModel(
        factory = ConversationViewModelFactory(
            AppContainer.conversationRepository(),
            AppContainer.characterRepository()
        )
    )
    val conversations by viewModel.conversations.collectAsState()
    val characters by viewModel.characters.collectAsState()
    val scope = rememberCoroutineScope()
    var showCharacterPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    modifier = Modifier.height(48.dp),
                    title = {
                        Text(
                            "AI恋人",
                            color = TextPrimary,
                            fontSize = 18.sp
                        )
                    },
                    actions = {
                        // 右上角「+」：新建角色
                        IconButton(onClick = onAddCharacterClick) {
                            Icon(Icons.Filled.Add, contentDescription = "新建角色", tint = TextPrimary)
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

    // 角色选择弹窗
    if (showCharacterPicker) {
        CharacterPickerDialog(
            characters = characters,
            onDismiss = { showCharacterPicker = false },
            onCharacterSelected = { character ->
                showCharacterPicker = false
                scope.launch {
                    val newConversationId = viewModel.createConversation(character)
                    onConversationClick(newConversationId, character.name)
                }
            },
            onGoManageCharacters = {
                showCharacterPicker = false
                onAddCharacterClick()
            }
        )
    }
}

@Composable
private fun CharacterPickerDialog(
    characters: List<CharacterEntity>,
    onDismiss: () -> Unit,
    onCharacterSelected: (CharacterEntity) -> Unit,
    onGoManageCharacters: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("选择角色", color = TextPrimary, fontSize = 18.sp)
        },
        text = {
            if (characters.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "还没有角色，先去创建一个吧",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = onGoManageCharacters) {
                        Text("去创建角色", color = BubbleSelf)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(characters, key = { it.id }) { character ->
                        CharacterPickerItem(
                            character = character,
                            onClick = { onCharacterSelected(character) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        },
        containerColor = CardWhite
    )
}

@Composable
private fun CharacterPickerItem(
    character: CharacterEntity,
    onClick: () -> Unit
) {
    val firstChar = character.name.firstOrNull()?.toString() ?: "?"
    val personalityPreview = character.personality.take(30).let {
        if (character.personality.length > 30) "$it..." else it
    }
    val hasCustomAvatar = character.avatarUri != null && File(character.avatarUri!!).exists()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像
        if (hasCustomAvatar) {
            AsyncImage(
                model = File(character.avatarUri!!),
                contentDescription = character.name,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BubbleSelf),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = firstChar,
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 名字 + 人设摘要
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = character.name,
                fontSize = 15.sp,
                color = TextPrimary,
                maxLines = 1
            )
            if (personalityPreview.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = personalityPreview,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
    val avatarUri = item.character?.avatarUri
    val hasCustomAvatar = avatarUri != null && File(avatarUri).exists()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像
        if (hasCustomAvatar) {
            AsyncImage(
                model = File(avatarUri!!),
                contentDescription = name,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BubbleSelf),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = firstChar,
                    color = Color.White,
                    fontSize = 20.sp
                )
            }
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
