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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ailover.app.data.settings.ApiPlatforms
import com.ailover.app.data.settings.ApiProfile
import com.ailover.app.di.AppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterSettingsScreen(
    characterId: Long,
    onBackClick: () -> Unit,
    onEditClick: (Long) -> Unit,
    onDeleted: () -> Unit
) {
    val viewModel: CharacterDetailViewModel = viewModel(
        factory = CharacterDetailViewModelFactory(
            characterId = characterId,
            characterRepository = AppContainer.characterRepository(),
            conversationRepository = AppContainer.conversationRepository()
        )
    )
    val character by viewModel.character.collectAsState()
    val settingsRepository = AppContainer.settingsRepository()
    val profiles by settingsRepository.apiProfiles.collectAsState(initial = emptyList())
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showApiSheet by remember { mutableStateOf(false) }
    var pinned by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    modifier = Modifier.height(48.dp),
                    title = { Text("角色设置", color = TextPrimary, fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary)
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
        containerColor = HintBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // 分组一：编辑角色
            SettingsGroup {
                SettingsItem(
                    title = "编辑角色",
                    onClick = { onEditClick(characterId) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 分组二：预留功能
            SettingsGroup {
                SettingsItem(
                    title = "选择 API",
                    subtitle = if (character?.apiProfileId == null)
                        "跟随全局默认"
                    else
                        profiles.firstOrNull { it.id == character?.apiProfileId }?.name ?: "跟随全局默认",
                    disabled = false,
                    onClick = { showApiSheet = true }
                )
                SettingsDivider()
                SettingsSwitchItem(
                    title = "置顶聊天",
                    checked = pinned,
                    onCheckedChange = { pinned = it },
                    disabled = true
                )
                SettingsDivider()
                SettingsSwitchItem(
                    title = "免打扰",
                    checked = muted,
                    onCheckedChange = { muted = it },
                    disabled = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 分组三：删除角色
            SettingsGroup {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDeleteDialog = true }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "删除角色",
                        color = DangerRed,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
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

    // 选择 API 底部弹窗
    if (showApiSheet) {
        ModalBottomSheet(
            onDismissRequest = { showApiSheet = false },
            sheetState = sheetState,
            containerColor = CardWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    "选择 API",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // 跟随全局默认
                ApiSelectRow(
                    title = "跟随全局默认",
                    subtitle = "使用全局设置中的当前默认配置",
                    selected = character?.apiProfileId == null,
                    onClick = {
                        viewModel.updateApiProfileId(null)
                        showApiSheet = false
                    }
                )

                DividerLine()

                // 所有 API 配置
                profiles.forEach { profile ->
                    ApiSelectRow(
                        title = profile.name,
                        subtitle = "${ApiPlatforms.getById(profile.platform).name} · ${profile.modelName}",
                        selected = character?.apiProfileId == profile.id,
                        onClick = {
                            viewModel.updateApiProfileId(profile.id)
                            showApiSheet = false
                        }
                    )
                    DividerLine()
                }
            }
        }
    }
}

// ========== 小组件 ==========

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(CardWhite, MaterialTheme.shapes.medium)
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    onClick: () -> Unit,
    disabled: Boolean = false,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !disabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (disabled) TextSecondary else TextPrimary,
                fontSize = 16.sp
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = if (disabled) DisabledGray else TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    disabled: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = if (disabled) TextSecondary else TextPrimary,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = !disabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BubbleSelf,
                checkedTrackColor = BubbleSelf.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .padding(start = 16.dp)
            .background(Divider)
    )
}

@Composable
private fun ApiSelectRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = TextPrimary,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = TextSecondary
            )
        }
        if (selected) {
            Text("✓", fontSize = 18.sp, color = AccentBlue)
        }
    }
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Divider)
    )
}
