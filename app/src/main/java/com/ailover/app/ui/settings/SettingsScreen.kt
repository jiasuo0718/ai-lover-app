package com.ailover.app.ui.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ailover.app.data.settings.ApiPlatforms
import com.ailover.app.di.AppContainer
import com.ailover.app.ui.theme.CardWhite
import com.ailover.app.ui.theme.Divider
import com.ailover.app.ui.theme.TextPrimary
import com.ailover.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(AppContainer.settingsRepository())
    )
    val settings by viewModel.settings.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
    var platformMenuExpanded by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    modifier = Modifier.height(48.dp),
                    title = { Text("我", color = TextPrimary, fontSize = 18.sp) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 平台选择
            Text("AI 平台", fontSize = 14.sp, color = TextSecondary)
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { platformMenuExpanded = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val currentPlatform = ApiPlatforms.getById(settings.platform)
                    Text(
                        text = currentPlatform.name + if (!currentPlatform.supported) "（暂不支持）" else "",
                        fontSize = 16.sp,
                        color = if (currentPlatform.supported)
                            MaterialTheme.colorScheme.onSurface else TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Filled.ArrowDropDown, "选择平台", tint = TextSecondary)
                }
                DropdownMenu(
                    expanded = platformMenuExpanded,
                    onDismissRequest = { platformMenuExpanded = false }
                ) {
                    ApiPlatforms.ALL.forEach { platform ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    platform.name + if (!platform.supported) "（暂不支持）" else "",
                                    color = if (platform.supported)
                                        MaterialTheme.colorScheme.onSurface else TextSecondary
                                )
                            },
                            onClick = {
                                if (platform.supported) {
                                    viewModel.onPlatformChange(platform.id)
                                }
                                platformMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Base URL
            OutlinedTextField(
                value = settings.baseUrl,
                onValueChange = viewModel::onBaseUrlChange,
                label = { Text("API Base URL") },
                placeholder = { Text("https://api.deepseek.com", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // API Key
            OutlinedTextField(
                value = settings.apiKey,
                onValueChange = viewModel::onApiKeyChange,
                label = { Text("API Key") },
                placeholder = { Text("sk-...", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showApiKey)
                    VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showApiKey = !showApiKey }) {
                        Text(if (showApiKey) "隐藏" else "显示", fontSize = 13.sp)
                    }
                }
            )

            // Model Name
            OutlinedTextField(
                value = settings.modelName,
                onValueChange = viewModel::onModelNameChange,
                label = { Text("Model Name") },
                placeholder = { Text("deepseek-chat", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 保存按钮
            TextButton(
                onClick = { viewModel.save() },
                enabled = saveState !is SettingsViewModel.SaveState.Saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = when (saveState) {
                        is SettingsViewModel.SaveState.Saving -> "保存中..."
                        is SettingsViewModel.SaveState.Saved -> "已保存"
                        is SettingsViewModel.SaveState.Error -> "保存失败，重试"
                        else -> "保存设置"
                    },
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // 保存状态提示
            when (val state = saveState) {
                is SettingsViewModel.SaveState.Saved -> {
                    Text("设置已保存，仅存储在本地", fontSize = 13.sp, color = Color(0xFF4CAF50))
                }
                is SettingsViewModel.SaveState.Error -> {
                    Text("保存失败：${state.message}", fontSize = 13.sp, color = Color.Red)
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 说明
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("使用说明", fontSize = 14.sp, color = TextSecondary)
                Text(
                    "1. 选择 AI 平台（目前支持 DeepSeek、OpenAI 和自定义兼容接口）",
                    fontSize = 13.sp, color = TextSecondary
                )
                Text(
                    "2. 填入 API Key（仅保存在本地，不会上传）",
                    fontSize = 13.sp, color = TextSecondary
                )
                Text(
                    "3. 其他平台如 Claude、Gemini 等暂不支持 OpenAI 兼容格式",
                    fontSize = 13.sp, color = TextSecondary
                )
                Text(
                    "4. DeepSeek API Key 可在 platform.deepseek.com 申请",
                    fontSize = 13.sp, color = TextSecondary
                )
            }
        }
    }
}
