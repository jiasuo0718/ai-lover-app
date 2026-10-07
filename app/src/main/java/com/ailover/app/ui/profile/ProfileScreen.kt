package com.ailover.app.ui.profile

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
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.ailover.app.ui.theme.BubbleSelf
import com.ailover.app.ui.theme.CardWhite
import com.ailover.app.ui.theme.Divider
import com.ailover.app.ui.theme.TextPrimary
import com.ailover.app.ui.theme.TextSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

// ========== ViewModel ==========
class ProfileViewModel(
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {
    val userProfile: StateFlow<UserProfileRepository.UserProfile> =
        userProfileRepository.userProfile.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserProfileRepository.UserProfile()
        )
}

class ProfileViewModelFactory(
    private val userProfileRepository: UserProfileRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ProfileViewModel(userProfileRepository) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onEditProfileClick: () -> Unit,
    onApiSettingsClick: () -> Unit
) {
    val viewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModelFactory(AppContainer.userProfileRepository())
    )
    val userProfile by viewModel.userProfile.collectAsState()

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
            // ===== 顶部：头像 + 昵称 + 点击编辑（白卡）=====
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardWhite)
                    .clickable(onClick = onEditProfileClick)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 头像（圆角方形 64dp）
                    val hasCustomAvatar = userProfile.avatarUri != null && File(userProfile.avatarUri!!).exists()
                    if (hasCustomAvatar) {
                        AsyncImage(
                            model = File(userProfile.avatarUri!!),
                            contentDescription = "头像",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(BubbleSelf),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = userProfile.nickname.firstOrNull()?.toString() ?: "我",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // 昵称 + 点击编辑
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userProfile.nickname,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "点击编辑个人信息",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ===== 分组一：表情 + API 设置（白卡）=====
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardWhite)
            ) {
                // 表情（占位）
                ProfileListItem(
                    icon = Icons.Filled.EmojiEmotions,
                    iconTint = Color(0xFFFFCC00),
                    title = "表情",
                    onClick = {}
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(Divider)
                        .padding(start = 56.dp)
                )
                // API 设置
                ProfileListItem(
                    icon = Icons.Filled.Settings,
                    iconTint = Color(0xFF0A84FF),
                    title = "API 设置",
                    onClick = onApiSettingsClick
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ===== 分组二：关于（白卡）=====
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardWhite)
            ) {
                ProfileListItem(
                    icon = Icons.Filled.Info,
                    iconTint = TextSecondary,
                    title = "关于",
                    trailing = "v1.9.4",
                    onClick = {}
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileListItem(
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
