package com.ailover.app.ui.image

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ailover.app.ui.theme.AccentBlue
import com.ailover.app.ui.theme.BgMain
import com.ailover.app.ui.theme.CardWhite
import com.ailover.app.ui.theme.Divider
import com.ailover.app.ui.theme.NavBarBg
import com.ailover.app.ui.theme.TextPrimary
import com.ailover.app.ui.theme.TextSecondary

/**
 * AIGC 功能选择页（可扩展卡片结构）。
 *
 * 设计：用 AigcFeature 列表驱动，每张卡片一个功能。
 * 以后加新功能（文生视频、图生视频、局部重绘…）时，
 * 只需在 features 列表里加一项，不用重写页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AigcHubScreen(
    onBackClick: () -> Unit,
    onTextToImageClick: () -> Unit,
    onImageToImageClick: () -> Unit
) {
    // 功能列表（可扩展：以后加功能只需在这里加一项）
    val features = listOf(
        AigcFeature(
            title = "文生图",
            description = "用文字描述生成图片",
            onClick = onTextToImageClick
        ),
        AigcFeature(
            title = "图生图",
            description = "参考一张图片生成新图片",
            onClick = onImageToImageClick
        )
    )

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    modifier = Modifier.height(48.dp),
                    title = { Text("AIGC", color = TextPrimary, fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = NavBarBg)
                )
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(Divider))
            }
        },
        containerColor = BgMain
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            features.forEach { feature ->
                AigcFeatureCard(feature)
            }
        }
    }
}

/**
 * AIGC 功能项数据类。
 * 以后加新功能只需创建一个 AigcFeature 实例。
 */
private data class AigcFeature(
    val title: String,
    val description: String,
    val onClick: () -> Unit
)

/**
 * AIGC 功能卡片。
 */
@Composable
private fun AigcFeatureCard(feature: AigcFeature) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardWhite)
            .clickable(onClick = feature.onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                feature.title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                feature.description,
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}
