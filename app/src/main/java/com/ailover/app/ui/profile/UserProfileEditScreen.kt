package com.ailover.app.ui.profile

import com.ailover.app.ui.theme.*
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ailover.app.data.settings.UserProfileRepository
import com.ailover.app.di.AppContainer
import com.ailover.app.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

// ========== ViewModel ==========
class UserProfileEditViewModel(
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {
    private val _nickname = MutableStateFlow("我")
    val nickname: StateFlow<String> = _nickname.asStateFlow()

    private val _avatarUri = MutableStateFlow<String?>(null)
    val avatarUri: StateFlow<String?> = _avatarUri.asStateFlow()

    private val _saveComplete = MutableStateFlow(false)
    val saveComplete: StateFlow<Boolean> = _saveComplete.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = userProfileRepository.getProfileOnce()
            _nickname.value = profile.nickname
            _avatarUri.value = profile.avatarUri
        }
    }

    fun onNicknameChange(value: String) {
        _nickname.value = value
    }

    fun onAvatarChange(uri: String?) {
        _avatarUri.value = uri
    }

    fun save() {
        viewModelScope.launch {
            userProfileRepository.saveProfile(_nickname.value, _avatarUri.value)
            _saveComplete.value = true
        }
    }
}

class UserProfileEditViewModelFactory(
    private val userProfileRepository: UserProfileRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return UserProfileEditViewModel(userProfileRepository) as T
    }
}

// ========== Screen ==========
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileEditScreen(
    onBackClick: () -> Unit,
    onSaved: () -> Unit
) {
    val viewModel: UserProfileEditViewModel = viewModel(
        factory = UserProfileEditViewModelFactory(AppContainer.userProfileRepository())
    )
    val nickname by viewModel.nickname.collectAsState()
    val avatarUri by viewModel.avatarUri.collectAsState()
    val saveComplete by viewModel.saveComplete.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 保存完成后返回
    LaunchedEffect(saveComplete) {
        if (saveComplete) {
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
                    title = { Text("编辑个人信息", color = TextPrimary, fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary)
                        }
                    },
                    actions = {
                        TextButton(onClick = { viewModel.save() }) {
                            Text("保存", color = TextPrimary, fontSize = 16.sp)
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
        containerColor = PageBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // 头像（居中，点击选择）
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(HintBg)
                        .clickable { imagePicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    val hasCustomAvatar = avatarUri != null && File(avatarUri!!).exists()
                    if (hasCustomAvatar) {
                        AsyncImage(
                            model = File(avatarUri!!),
                            contentDescription = "头像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "添加头像",
                            tint = TextSecondary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "点击更换头像",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 昵称输入框
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "昵称",
                    fontSize = 15.sp,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = nickname,
                    onValueChange = viewModel::onNicknameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("请输入昵称", color = TextSecondary, fontSize = 15.sp) },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = HintBg,
                        unfocusedContainerColor = HintBg,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = TextPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
