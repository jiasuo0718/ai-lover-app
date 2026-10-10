package com.ailover.app.ui.character

import com.ailover.app.ui.theme.*
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ailover.app.data.local.entity.CharacterEntity
import com.ailover.app.di.AppContainer
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
    val isLoading by viewModel.isLoading.collectAsState()
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
                        containerColor = NavBarBg
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
        containerColor = NavBarBg
    ) { paddingValues ->
        if (isLoading) {
            // 加载中：显示空白，不闪现空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else if (characters.isEmpty()) {
            // 数据加载完且为空：显示空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("点击右上角 + 添加角色", color = TextSecondary, fontSize = 15.sp)
            }
        } else {
            // 有数据：显示列表
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

            // 完整字母表 A-Z + #（# 在最后，跟列表分组一致）
            val allLetters = remember { ('A'..'Z').map { it.toString() } + listOf("#") }
            val availableLetters = remember(grouped) { grouped.map { it.first }.toSet() }

            // 预计算：每个字母（含无角色的）→ 最近的有角色分组的 LazyColumn index
            val letterToScrollIndex = remember(grouped, allLetters) {
                val map = mutableMapOf<String, Int>()
                val availableList = allLetters.filter { it in letterIndexMap }
                allLetters.forEach { letter ->
                    if (letter in letterIndexMap) {
                        map[letter] = letterIndexMap[letter]!!
                    } else {
                        // 找最近的有角色的字母
                        val idx = allLetters.indexOf(letter)
                        var nearest: String? = null
                        for (i in idx - 1 downTo 0) {
                            if (allLetters[i] in letterIndexMap) { nearest = allLetters[i]; break }
                        }
                        if (nearest == null) {
                            for (i in idx + 1 until allLetters.size) {
                                if (allLetters[i] in letterIndexMap) { nearest = allLetters[i]; break }
                            }
                        }
                        nearest?.let { map[letter] = letterIndexMap[it]!! }
                    }
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
                ) {
                    grouped.forEach { (letter, groupCharacters) ->
                        // 分组标题条（浅灰底，高度30dp，字母13sp）
                        item(key = "header_$letter") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                                    .background(PageBg),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = letter,
                                    fontSize = 13.sp,
                                    color = TextSecondary,
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

                // 右侧字母索引条（独立组件，状态变化不影响 LazyColumn）
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxSize()
                ) {
                    AlphabetIndexBar(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        allLetters = allLetters,
                        availableLetters = availableLetters,
                        onLetterSelected = { index ->
                            val letter = allLetters[index]
                            letterToScrollIndex[letter]?.let { scrollIdx ->
                                scope.launch {
                                    listState.scrollToItem(scrollIdx)
                                }
                            }
                        }
                    )
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
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
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
            Spacer(modifier = Modifier.height(2.dp))
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

/**
 * 右侧字母索引条（独立组件，状态变化不影响 LazyColumn）
 */
@Composable
private fun AlphabetIndexBar(
    modifier: Modifier = Modifier,
    allLetters: List<String>,
    availableLetters: Set<String>,
    onLetterSelected: (Int) -> Unit
) {
    val letterItemHeight = 16.dp
    val letterBarWidth = 28.dp
    var selectedIndex by remember { mutableStateOf(-1) }
    var isDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .width(letterBarWidth)
            .padding(end = 2.dp)
            .pointerInput(allLetters) {
                val itemHeightPx = letterItemHeight.toPx()
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.pressed) {
                            change.consume()
                            isDragging = true
                            val index = (change.position.y / itemHeightPx)
                                .toInt()
                                .coerceIn(0, allLetters.size - 1)
                            if (index != selectedIndex) {
                                selectedIndex = index
                                onLetterSelected(index)
                            }
                        } else {
                            isDragging = false
                            selectedIndex = -1
                        }
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            allLetters.forEachIndexed { index, letter ->
                val hasCharacters = letter in availableLetters
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .height(letterItemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter,
                        fontSize = if (isSelected) 14.sp else 11.sp,
                        fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                        color = when {
                            isSelected -> AccentBlue
                            hasCharacters -> TextPrimary
                            else -> DisabledGray
                        }
                    )
                }
            }
        }

        // 大字母气泡提示（滑动时显示，屏幕中央）
        if (isDragging && selectedIndex >= 0) {
            val selectedLetter = allLetters[selectedIndex]
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(80.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(TextSecondary.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = selectedLetter,
                    fontSize = 40.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
