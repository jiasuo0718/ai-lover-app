package com.ailover.app.ui.character

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.ailover.app.di.AppContainer
import com.ailover.app.ui.theme.BubbleSelf
import com.ailover.app.ui.theme.CardWhite
import com.ailover.app.ui.theme.Divider
import com.ailover.app.ui.theme.TextPrimary
import com.ailover.app.ui.theme.TextSecondary
import com.ailover.app.util.PinyinUtils
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterListScreen(
    onAddClick: () -> Unit,
    onCharacterClick: (Long) -> Unit
) {
    val viewModel: CharacterListViewModel = viewModel(
        factory = CharacterListViewModelFactory(AppContainer.characterRepository())
    )
    val characters by viewModel.characters.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    modifier = Modifier.height(48.dp),
                    title = { Text("通讯录", color = TextPrimary, fontSize = 18.sp) },
                    actions = {
                        IconButton(onClick = onAddClick) {
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
                        .height(0.5.dp)
                        .background(Divider)
                )
            }
        },
        containerColor = CardWhite
    ) { paddingValues ->
        if (characters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("点击右上角 + 添加角色", color = TextSecondary, fontSize = 15.sp)
            }
        } else {
            // 按拼音首字母分组排序
            val grouped = PinyinUtils.groupByPinyin(characters) { it.name }

            // 构建字母到 LazyColumn item index 的映射（用于右侧索引条跳转）
            val letterIndexMap = remember(grouped) {
                val map = mutableMapOf<String, Int>()
                var index = 0
                grouped.forEach { (letter, chars) ->
                    map[letter] = index
                    index += 1 + chars.size // header(1) + items
                }
                map
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    grouped.forEach { (letter, groupCharacters) ->
                        // 分组标题条（浅灰底，高度30dp，字母13sp）
                        item(key = "header_$letter") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                                    .background(Color(0xFFEDEDED)),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = letter,
                                    fontSize = 13.sp,
                                    color = Color(0xFF8E8E93),
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        }

                        // 组内角色列表
                        items(groupCharacters, key = { it.id }) { character ->
                            CharacterItem(
                                character = character,
                                onClick = { onCharacterClick(character.id) }
                            )
                            // 组内分割线（除了最后一个）
                            val index = groupCharacters.indexOf(character)
                            if (index < groupCharacters.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(0.5.dp)
                                        .background(Divider)
                                        .padding(start = 68.dp) // 从头像右侧开始
                                )
                            }
                        }
                    }
                }

                // 右侧字母索引条
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 2.dp)
                ) {
                    grouped.forEach { (letter, _) ->
                        Text(
                            text = letter,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier
                                .clickable {
                                    scope.launch {
                                        letterIndexMap[letter]?.let { idx ->
                                            listState.scrollToItem(idx)
                                        }
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterItem(
    character: CharacterEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像（圆角方形 8dp，40dp，跟其他页面统一）
        if (character.avatarUri != null && File(character.avatarUri!!).exists()) {
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
                    character.name.firstOrNull()?.toString() ?: "?",
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
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = character.personality.ifEmpty { "暂无设定" },
                fontSize = 14.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
