package com.ailover.app.ui.settings

import com.ailover.app.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ailover.app.di.AppContainer
import com.ailover.app.util.VolcTtsManager
import kotlinx.coroutines.launch

/**
 * 朗读设置（独立页面）。
 * 在这里填写火山豆包 Seed-TTS 2.0 的 X-Api-Key，与 AI 对话的 API 配置完全解耦。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSettingsScreen(
    onBackClick: () -> Unit
) {
    val settingsRepository = remember { AppContainer.settingsRepository() }
    val scope = rememberCoroutineScope()

    var ttsApiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ttsApiKey = settingsRepository.getTtsApiKeyOnce()
        loaded = true
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    modifier = Modifier.height(48.dp),
                    title = { Text("朗读设置", color = TextPrimary, fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary)
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            scope.launch { settingsRepository.saveTtsApiKey(ttsApiKey) }
                            onBackClick()
                        }) {
                            Text("保存", fontSize = 15.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = NavBarBg)
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
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "火山豆包 Seed-TTS 2.0（情感合成朗读）",
                fontSize = 16.sp,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "AI 文本气泡旁的喇叭会调用它流式朗读。\n在火山引擎控制台 → 豆包语音 → API Key 管理获取 X-Api-Key。",
                fontSize = 12.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = ttsApiKey,
                onValueChange = { ttsApiKey = it },
                label = { Text("火山 TTS ApiKey") },
                placeholder = { Text("粘贴你的 ApiKey", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showKey)
                    VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showKey = !showKey }) {
                        Text(if (showKey) "隐藏" else "显示", fontSize = 13.sp)
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "留空则朗读喇叭不可用。保存后立即生效。",
                fontSize = 12.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    scope.launch { settingsRepository.saveTtsApiKey(ttsApiKey) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存朗读设置")
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("预设音色", fontSize = 14.sp, color = TextSecondary)
            VolcTtsManager.presetVoices.forEach { voice ->
                Text(
                    text = "· ${voice.displayName}",
                    fontSize = 13.sp,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                "音色在聊天页右上角菜单切换。",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
