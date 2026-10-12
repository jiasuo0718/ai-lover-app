package com.ailover.app.ui.image

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
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
import coil.compose.AsyncImage
import com.ailover.app.data.repository.ComfyGeneratedImage
import com.ailover.app.data.repository.ComfyRepository
import com.ailover.app.di.AppContainer
import com.ailover.app.ui.theme.AccentBlue
import com.ailover.app.ui.theme.BgMain
import com.ailover.app.ui.theme.CardWhite
import com.ailover.app.ui.theme.Divider
import com.ailover.app.ui.theme.LocalDarkTheme
import com.ailover.app.ui.theme.NavBarBg
import com.ailover.app.ui.theme.TextPrimary
import com.ailover.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

/**
 * 高级生图界面。
 * 用户自己填提示词和参数，不经过 AI 扩写。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGenScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val comfyRepository = remember { AppContainer.comfyRepository() }

    var chinesePrompt by remember { mutableStateOf("") }
    var useAiExpand by remember { mutableStateOf(true) }
    var positivePrompt by remember { mutableStateOf("") }
    var negativePrompt by remember { mutableStateOf(ComfyRepository.DEFAULT_NEGATIVE) }
    var selectedSize by remember { mutableStateOf("512×512") }
    var steps by remember { mutableStateOf("20") }
    var seed by remember { mutableStateOf("-1") }
    var isGenerating by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<ComfyGeneratedImage?>(null) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var generatedImagePath by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 生成计时器：isGenerating=true 时每秒+1
    LaunchedEffect(isGenerating) {
        if (isGenerating) {
            elapsedSeconds = 0
            while (isGenerating) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    val sizes = listOf("512×512", "768×768", "1024×1024", "512×768", "768×512")

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    modifier = Modifier.height(48.dp),
                    title = { Text("高级生图", color = TextPrimary, fontSize = 18.sp) },
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
            // 中文提示词 + AI扩写开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("中文描述", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AI扩写", color = TextSecondary, fontSize = 13.sp)
                    Switch(
                        checked = useAiExpand,
                        onCheckedChange = { useAiExpand = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentBlue)
                    )
                }
            }
            OutlinedTextField(
                value = chinesePrompt,
                onValueChange = { chinesePrompt = it },
                placeholder = { Text("一个穿白裙子的女孩，在海边", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth().height(70.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = Divider,
                    cursorColor = AccentBlue
                )
            )

            // 正向提示词（英文，高级用户直接写）
            Text("正向提示词（英文）", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            OutlinedTextField(
                value = positivePrompt,
                onValueChange = { positivePrompt = it },
                placeholder = { Text("a cute cat, detailed fur, 8k, masterpiece", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                enabled = !useAiExpand,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = Divider,
                    cursorColor = AccentBlue
                )
            )

            // 负面提示词
            Text("负面提示词", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            OutlinedTextField(
                value = negativePrompt,
                onValueChange = { negativePrompt = it },
                modifier = Modifier.fillMaxWidth().height(70.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = Divider,
                    cursorColor = AccentBlue
                )
            )

            // 尺寸选择
            Text("尺寸", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sizes.forEach { size ->
                    val selected = selectedSize == size
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) AccentBlue else CardWhite)
                            .clickable { selectedSize = size }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            size,
                            color = if (selected) Color.White else TextPrimary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // 步数 + 种子
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("步数", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = steps,
                        onValueChange = { steps = it.filter { c -> c.isDigit() } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = Divider
                        )
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("种子（-1随机）", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = seed,
                        onValueChange = { seed = it.filter { c -> c.isDigit() || c == '-' } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = Divider
                        )
                    )
                }
            }

            // 生成/下载按钮
            Button(
                onClick = {
                    // 有未下载的生成结果 → 点下载
                    if (pendingDownload != null && !isDownloading) {
                        isDownloading = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val path = comfyRepository.downloadAndSave(pendingDownload!!)
                                generatedImagePath = path
                                pendingDownload = null
                            } catch (e: Exception) {
                                errorMessage = "下载失败，重试：${e.message}"
                            } finally {
                                isDownloading = false
                            }
                        }
                        return@Button
                    }
                    // 正常生成
                    val canGenerate = if (useAiExpand) chinesePrompt.isNotBlank() else positivePrompt.isNotBlank()
                    if (!canGenerate || isGenerating) return@Button
                    isGenerating = true
                    errorMessage = null
                    generatedImagePath = null
                    pendingDownload = null
                    scope.launch {
                        try {
                            val (w, h) = parseSize(selectedSize)
                            // AI扩写：中文→英文SD标签
                            val finalPrompt = if (useAiExpand && chinesePrompt.isNotBlank()) {
                                comfyRepository.expandPrompt(chinesePrompt)
                            } else {
                                positivePrompt
                            }
                            val generated = comfyRepository.generateImage(
                                positivePrompt = finalPrompt,
                                negativePrompt = negativePrompt.ifBlank { ComfyRepository.DEFAULT_NEGATIVE },
                                width = w,
                                height = h,
                                steps = steps.toIntOrNull() ?: 20,
                                seed = seed.toLongOrNull() ?: -1
                            )
                            pendingDownload = generated  // 生成完成，等用户点下载
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "生成失败"
                        } finally {
                            isGenerating = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                enabled = !isGenerating && !isDownloading &&
                        ((if (useAiExpand) chinesePrompt.isNotBlank() else positivePrompt.isNotBlank()) || pendingDownload != null)
            ) {
                Text(
                    when {
                        isGenerating -> "正在生成…（已用时 ${elapsedSeconds}s）"
                        isDownloading -> "下载中…"
                        pendingDownload != null -> "下载"
                        else -> "生成"
                    },
                    color = Color.White,
                    fontSize = 16.sp
                )
            }

            // 错误提示
            errorMessage?.let {
                Text(it, color = Color(0xFFFF3B30), fontSize = 13.sp)
            }

            // 生成结果
            generatedImagePath?.let { path ->
                val imgFile = File(path)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (imgFile.exists()) {
                        AsyncImage(
                            model = imgFile,
                            contentDescription = "生成结果",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { saveImageToGallery(context, path) },
                            colors = ButtonDefaults.buttonColors(containerColor = CardWhite),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text("保存到相册", color = TextPrimary, fontSize = 14.sp)
                        }
                    } else {
                        Text("图片文件不存在", color = TextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 解析尺寸字符串 "512×768" → (512, 768) */
private fun parseSize(sizeStr: String): Pair<Int, Int> {
    val parts = sizeStr.split("×", "x", "X")
    val w = parts.getOrNull(0)?.toIntOrNull() ?: 512
    val h = parts.getOrNull(1)?.toIntOrNull() ?: 512
    return w to h
}

/** 保存图片到系统相册 */
private fun saveImageToGallery(context: android.content.Context, imagePath: String) {
    try {
        val file = File(imagePath)
        if (!file.exists()) return
        val bitmap = BitmapFactory.decodeStream(FileInputStream(file))
        val fileName = "Evolvia_${System.currentTimeMillis()}.png"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Evolvia")
            }
            val uri: Uri? = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + "/Evolvia")
            if (!imagesDir.exists()) imagesDir.mkdirs()
            val dest = File(imagesDir, fileName)
            FileInputStream(file).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            // 通知相册刷新
            context.sendBroadcast(
                android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(dest))
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
