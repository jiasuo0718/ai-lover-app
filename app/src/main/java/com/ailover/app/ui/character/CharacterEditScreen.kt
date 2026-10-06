package com.ailover.app.ui.character

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ailover.app.di.AppContainer
import com.ailover.app.ui.theme.AccentBlue
import com.ailover.app.ui.theme.CardWhite
import com.ailover.app.ui.theme.Divider
import com.ailover.app.ui.theme.TextPrimary
import com.ailover.app.ui.theme.TextSecondary
import com.ailover.app.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterEditScreen(
    characterId: Long?,
    onBackClick: () -> Unit,
    onSaved: () -> Unit
) {
    val viewModel: CharacterEditViewModel = viewModel(
        factory = CharacterEditViewModelFactory(
            characterId = characterId,
            characterRepository = AppContainer.characterRepository()
        )
    )
    val name by viewModel.name.collectAsState()
    val personality by viewModel.personality.collectAsState()
    val avatarUri by viewModel.avatarUri.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEmptyNameError by remember { mutableStateOf(false) }

    // 监听 ViewModel 的操作完成事件，在主线程执行导航（避免后台线程调 popBackStack 崩溃）
    LaunchedEffect(Unit) {
        viewModel.operationCompleteEvent.collect {
            onSaved()
        }
    }

    // 头像选择器
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                val savedPath = ImageUtils.copyUriToAppDir(context, it)
                viewModel.onAvatarChange(savedPath)
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    modifier = Modifier.height(48.dp),
                    title = {
                        Text(
                            if (isEditMode) "编辑角色" else "新建角色",
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
                        if (isEditMode) {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Filled.Delete, "删除", tint = AccentBlue)
                            }
                        }
                        TextButton(onClick = {
                            if (name.isBlank()) {
                                showEmptyNameError = true
                            } else {
                                viewModel.save()
                            }
                        }) {
                            Text("保存", color = AccentBlue, fontSize = 16.sp)
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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 头像
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { imagePicker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (avatarUri != null && File(avatarUri!!).exists()) {
                    AsyncImage(
                        model = File(avatarUri!!),
                        contentDescription = "头像",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        name.firstOrNull()?.toString() ?: "+",
                        color = Color.White,
                        fontSize = 36.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("点击更换头像", color = TextSecondary, fontSize = 13.sp)

            Spacer(modifier = Modifier.height(24.dp))

            // 名称
            OutlinedTextField(
                value = name,
                onValueChange = {
                    viewModel.onNameChange(it)
                    showEmptyNameError = false
                },
                label = { Text("角色名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = showEmptyNameError,
                supportingText = {
                    if (showEmptyNameError) Text("请输入角色名称", color = Color.Red)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 人设
            OutlinedTextField(
                value = personality,
                onValueChange = viewModel::onPersonalityChange,
                label = { Text("人设 / 性格 / 背景设定") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                maxLines = 8,
                placeholder = {
                    Text(
                        "例如：你是一个温柔体贴的AI恋人，喜欢倾听用户的烦恼...",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "此设定将作为 system prompt 注入对话",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除角色") },
            text = { Text("确定删除该角色吗？其所有会话和消息也会被删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteCharacter()
                }) {
                    Text("删除", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
