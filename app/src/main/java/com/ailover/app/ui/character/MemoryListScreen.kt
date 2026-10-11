package com.ailover.app.ui.character

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ailover.app.data.local.entity.MemoryEntity
import com.ailover.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryListScreen(
    characterId: Long,
    characterName: String,
    onBackClick: () -> Unit
) {
    val memoryRepository = AppContainer.memoryRepository()
    val memories by memoryRepository.getMemoriesByCharacter(characterId)
        .collectAsState(initial = emptyList())
    var memoryToDelete by remember { mutableStateOf<MemoryEntity?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    modifier = Modifier.height(48.dp),
                    title = { Text("记忆管理", color = TextPrimary, fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary)
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
        containerColor = HintBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 顶部统计
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "「$characterName」共记住 ${memories.size} 条信息",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }

            if (memories.isEmpty()) {
                // 空状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "还没有记住任何信息",
                            color = TextSecondary,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "多和 TA 聊聊吧，聊满 10 条会自动提取记忆",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(memories, key = { it.id }) { memory ->
                        MemoryItem(
                            memory = memory,
                            onDelete = { memoryToDelete = memory }
                        )
                    }
                }
            }
        }
    }

    // 删除确认对话框
    if (memoryToDelete != null) {
        AlertDialog(
            onDismissRequest = { memoryToDelete = null },
            title = { Text("删除记忆") },
            text = { Text("确定删除这条记忆吗？删除后「$characterName」将不再记得这件事。") },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = memoryToDelete
                    memoryToDelete = null
                    toDelete?.let {
                        scope.launch(Dispatchers.IO) {
                            memoryRepository.deleteMemory(it)
                        }
                    }
                }) {
                    Text("删除", color = DangerRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { memoryToDelete = null }) {
                    Text("取消")
                }
            },
            containerColor = NavBarBg
        )
    }
}

@Composable
private fun MemoryItem(
    memory: MemoryEntity,
    onDelete: () -> Unit
) {
    val typeColor = when (memory.type) {
        "名字" -> AccentBlue
        "喜好" -> Color(0xFFFF9500)
        "事件" -> Color(0xFFFF3B30)
        "关系" -> Color(0xFFAF52DE)
        else -> TextSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardWhite)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 类型标签
        Box(
            modifier = Modifier
                .background(typeColor.copy(alpha = 0.15f), MaterialTheme.shapes.small)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = memory.type,
                color = typeColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        // 内容
        Text(
            text = memory.content,
            color = TextPrimary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        // 删除按钮
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "删除",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
    // 分割线
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .padding(start = 16.dp)
            .background(Divider)
    )
}
