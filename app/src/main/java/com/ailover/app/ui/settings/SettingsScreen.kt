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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ailover.app.BuildConfig
import com.ailover.app.data.settings.SettingsRepository
import com.ailover.app.di.AppContainer
import com.ailover.app.ui.theme.CardWhite
import com.ailover.app.ui.theme.Divider
import com.ailover.app.ui.theme.TextPrimary
import com.ailover.app.ui.theme.TextSecondary
import io.github.qdsfdhvh.iconpark.IconParkIcons
import io.github.qdsfdhvh.iconpark.outline.SettingTwo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onApiSettingsClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val settingsRepository = remember { AppContainer.settingsRepository() }
    val themeMode by settingsRepository.themeMode.collectAsState(initial = "system")
    val scope = rememberCoroutineScope()
    var showThemeDialog by remember { mutableStateOf(false) }

    val themeLabel = when (themeMode) {
        "dark" -> "深色"
        "light" -> "浅色"
        else -> "跟随系统"
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    modifier = Modifier.height(48.dp),
                    title = { Text("设置", color = TextPrimary, fontSize = 18.sp) },
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
        containerColor = Color(0xFFEDEDED)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // ===== 分组一：通用设置 =====
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardWhite)
            ) {
                // API 设置
                SettingsListItem(
                    icon = IconParkIcons.Outline.SettingTwo,
                    iconTint = TextPrimary,
                    title = "API 设置",
                    onClick = onApiSettingsClick
                )
                // 深色模式
                SettingsListItem(
                    icon = Icons.Filled.DarkMode,
                    iconTint = TextPrimary,
                    title = "深色模式",
                    trailing = themeLabel,
                    onClick = { showThemeDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ===== 分组二：关于 =====
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardWhite)
            ) {
                SettingsListItem(
                    icon = Icons.Filled.Info,
                    iconTint = TextSecondary,
                    title = "关于",
                    trailing = "v${BuildConfig.VERSION_NAME}",
                    onClick = {}
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // 深色模式选择弹窗
    if (showThemeDialog) {
        Dialog(onDismissRequest = { showThemeDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardWhite)
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "深色模式",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
                listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch { settingsRepository.setThemeMode(value) }
                                showThemeDialog = false
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = themeMode == value,
                            onClick = {
                                scope.launch { settingsRepository.setThemeMode(value) }
                                showThemeDialog = false
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = label, fontSize = 16.sp, color = TextPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SettingsListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    trailing: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                fontSize = 14.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}
