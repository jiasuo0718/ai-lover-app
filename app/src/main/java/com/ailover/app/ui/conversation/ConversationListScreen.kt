package com.ailover.app.ui.conversation

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
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ailover.app.data.local.entity.CharacterEntity
import com.ailover.app.data.local.relation.ConversationWithCharacter
import com.ailover.app.di.AppContainer
import com.ailover.app.ui.theme.BubbleSelf
import com.ailover.app.ui.theme.CardWhite
import com.ailover.app.ui.theme.Divider
import com.ailover.app.ui.theme.TextPrimary
import com.ailover.app.ui.theme.TextSecondary
import com.ailover.app.util.TimeUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onConversationClick: (Long, String) -> Unit,
    onCharacterManageClick: () -> Unit,
    onSettingsClick: () -> Unit
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
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置", tint = TextPrimary)
                        }
                        IconButton(onClick = onCharacterManageClick) {
                            Icon(Icons.Filled.Group, contentDescription = "角色管理", tint = TextPrimary)
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCharacterPicker = true },
                containerColor = BubbleSelf,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新建会话")
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
                    "暂无会话，点右下角 + 开始聊天",
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
                onCharacterManageClick()
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像
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
