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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ailover.app.data.settings.ApiPlatforms
import com.ailover.app.data.settings.ApiProfile
import com.ailover.app.di.AppContainer
import com.ailover.app.ui.theme.CardWhite
import com.ailover.app.ui.theme.Divider
import com.ailover.app.ui.theme.TextPrimary
import com.ailover.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiProfileEditScreen(
    profileId: String?, // null = 新增
    onBackClick: () -> Unit,
    onSaved: () -> Unit
) {
    val settingsRepository = AppContainer.settingsRepository()
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("deepseek") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var platformMenuExpanded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(profileId) {
        if (profileId != null) {
            val profile = settingsRepository.getProfilesOnce().firstOrNull { it.id == profileId }
            if (profile != null) {
                name = profile.name
                platform = profile.platform
                baseUrl = profile.baseUrl
                apiKey = profile.apiKey
                modelName = profile.modelName
            }
        } else {
            platform = ApiPlatforms.DEEPSEEK.id
            baseUrl = ApiPlatforms.DEEPSEEK.defaultBaseUrl
            modelName = ApiPlatforms.DEEPSEEK.defaultModel
        }
        loaded = true
    }

    fun save() {
        if (saving) return
        scope.launch {
            saving = true
            val profile = ApiProfile(
                id = profileId ?: UUID.randomUUID().toString(),
                name = name.ifBlank { "未命名配置" },
                platform = platform,
                baseUrl = baseUrl,
                apiKey = apiKey,
                modelName = modelName
            )
            if (profileId == null) {
                settingsRepository.addProfile(profile)
            } else {
                settingsRepository.updateProfile(profile)
            }
            onSaved()
        }
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    modifier = Modifier.height(48.dp),
                    title = {
                        Text(
                            if (profileId == null) "新增配置" else "编辑配置",
                            color = TextPrimary,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary)
                        }
                    },
                    actions = {
                        TextButton(onClick = { save() }, enabled = !saving) {
                            Text(if (saving) "保存中..." else "保存", fontSize = 15.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CardWhite)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(Divider)
                )
            }
        },
        containerColor = Color(0xFFF2F2F7)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 配置名称
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("配置名称") },
                placeholder = { Text("如：我的 DeepSeek", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

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
                    val currentPlatform = ApiPlatforms.getById(platform)
                    Text(
                        text = currentPlatform.name,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Filled.ArrowDropDown, "选择平台", tint = TextSecondary)
                }
                DropdownMenu(
                    expanded = platformMenuExpanded,
                    onDismissRequest = { platformMenuExpanded = false }
                ) {
                    ApiPlatforms.ALL.filter { it.supported }.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name, color = TextPrimary) },
                            onClick = {
                                platform = p.id
                                if (p.defaultBaseUrl.isNotBlank()) baseUrl = p.defaultBaseUrl
                                if (p.defaultModel.isNotBlank()) modelName = p.defaultModel
                                platformMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // Base URL
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("API Base URL") },
                placeholder = { Text("https://api.deepseek.com", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // API Key
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
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
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("Model Name") },
                placeholder = { Text("deepseek-chat", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
