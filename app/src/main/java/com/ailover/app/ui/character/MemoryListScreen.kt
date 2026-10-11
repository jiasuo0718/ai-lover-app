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
import com.ailover.app.data.remote.model.ChatMessage
import com.ailover.app.data.remote.model.ChatRequest
import com.ailover.app.data.repository.ChatRepository
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
    var showDedupConfirm by remember { mutableStateOf(false) }
    var isDeduping by remember { mutableStateOf(false) }
    var dedupResult by remember { mutableStateOf<String?>(null) }
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
                    actions = {
                        if (memories.isNotEmpty() && !isDeduping) {
                            TextButton(onClick = { showDedupConfirm = true }) {
                                Text("去重", color = AccentBlue, fontSize = 14.sp)
                            }
                        } else if (isDeduping) {
                            Text("处理中…", color = TextSecondary, fontSize = 14.sp)
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

    // 去重确认对话框
    if (showDedupConfirm) {
        AlertDialog(
            onDismissRequest = { showDedupConfirm = false },
            title = { Text("记忆去重") },
            text = { Text("将用 AI 判断「$characterName」的所有记忆中哪些是同一件事，合并保留最全的、删除重复的。\n\n此操作不可撤销，确定继续吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDedupConfirm = false
                    isDeduping = true
                    scope.launch(Dispatchers.IO) {
                        val result = runDedup(characterId)
                        isDeduping = false
                        dedupResult = result
                    }
                }) {
                    Text("开始去重", color = AccentBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDedupConfirm = false }) {
                    Text("取消")
                }
            },
            containerColor = NavBarBg
        )
    }

    // 去重结果对话框
    if (dedupResult != null) {
        AlertDialog(
            onDismissRequest = { dedupResult = null },
            title = { Text("去重完成") },
            text = { Text(dedupResult ?: "") },
            confirmButton = {
                TextButton(onClick = { dedupResult = null }) {
                    Text("确定")
                }
            },
            containerColor = NavBarBg
        )
    }
}

/**
 * 存量去重：查所有记忆 → AI 判断同一件事 → 合并/删除。
 * 返回结果描述。
 */
private suspend fun runDedup(characterId: Long): String {
    return try {
        val memoryRepository = AppContainer.memoryRepository()
        val chatRepository = AppContainer.chatRepository()
        val settingsRepository = AppContainer.settingsRepository()
        val settings = settingsRepository.getActiveProfileOnce()
        if (!settings.isConfigured()) return "未配置 API，无法去重"

        val allMemories = memoryRepository.getMemoriesByCharacterOnce(characterId, 200)
        if (allMemories.size < 2) return "记忆太少（${allMemories.size}条），无需去重"

        val memoryListText = allMemories.mapIndexed { i, m ->
            "${i + 1}. [${m.type}] ${m.content}"
        }.joinToString("\n")

        val prompt = """
            以下是该角色的所有记忆，请判断哪些是同一件事（重复/包含关系）。
            同一件事的合并成一条（保留最全的内容），重复的删除。

            【所有记忆】
            $memoryListText

            输出格式（每条一行）：
            [合并] 序号1,序号2 → [类型] 合并后内容
            [删除] 序号
            没有重复就回复"无"。
        """.trimIndent()

        val result = StringBuilder()
        chatRepository.streamChat(
            baseUrl = settings.baseUrl,
            apiKey = settings.apiKey,
            profileName = settings.name,
            request = ChatRequest(
                model = settings.modelName,
                messages = listOf(ChatMessage("system", prompt)),
                stream = true
            )
        ).collect { chunk -> result.append(chunk) }

        val raw = result.toString().trim()
        if (raw.isEmpty() || raw == "无") return "未发现重复记忆"

        val validTypes = setOf("名字", "喜好", "事件", "关系", "其他")
        var mergedCount = 0
        var deletedCount = 0

        for (line in raw.lines().map { it.trim() }.filter { it.isNotEmpty() }) {
            // [合并] 序号1,序号2 → [类型] 合并后内容
            val mergeMatch = Regex("^\\[合并\\]\\s*([\\d,，\\s]+)\\s*[→\\-:]\\s*\\[(.+?)\\]\\s*(.+)$").find(line)
            if (mergeMatch != null) {
                val indices = mergeMatch.groupValues[1]
                    .split(Regex("[,，\\s]"))
                    .mapNotNull { it.toIntOrNull() }
                    .filter { it in 1..allMemories.size }
                val type = mergeMatch.groupValues[2].trim()
                val content = mergeMatch.groupValues[3].trim()
                if (indices.size >= 2 && type in validTypes && content.isNotEmpty()) {
                    val first = allMemories[indices[0] - 1]
                    val importance = when (type) {
                        "名字" -> 5; "关系" -> 4; "喜好" -> 4; "事件" -> 3; else -> 2
                    }
                    memoryRepository.updateMemory(
                        first.copy(type = type, content = content, importance = importance, updatedAt = System.currentTimeMillis())
                    )
                    for (idx in indices.drop(1)) {
                        memoryRepository.deleteMemory(allMemories[idx - 1])
                        deletedCount++
                    }
                    mergedCount++
                }
                continue
            }
            // [删除] 序号
            val deleteMatch = Regex("^\\[删除\\]\\s*(\\d+)$").find(line)
            if (deleteMatch != null) {
                val idx = deleteMatch.groupValues[1].toIntOrNull() ?: 0
                if (idx in 1..allMemories.size) {
                    memoryRepository.deleteMemory(allMemories[idx - 1])
                    deletedCount++
                }
                continue
            }
        }

        if (mergedCount == 0 && deletedCount == 0) {
            "未发现重复记忆"
        } else {
            "去重完成：合并 $mergedCount 组，删除 $deletedCount 条重复"
        }
    } catch (e: Exception) {
        "去重失败：${e.message ?: "未知错误"}"
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
