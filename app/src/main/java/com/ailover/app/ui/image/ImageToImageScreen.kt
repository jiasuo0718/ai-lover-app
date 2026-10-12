package com.ailover.app.ui.image

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.ailover.app.data.repository.ComfyRepository
import com.ailover.app.di.AppContainer
import com.ailover.app.ui.theme.AccentBlue
import com.ailover.app.ui.theme.BgMain
import com.ailover.app.ui.theme.CardWhite
import com.ailover.app.ui.theme.Divider
import com.ailover.app.ui.theme.NavBarBg
import com.ailover.app.ui.theme.TextPrimary
import com.ailover.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 图生图界面。
 * 选参考图 → 上传 ComfyUI → 图生图工作流 → 生成 → 显示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToImageScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val comfyRepository = remember { AppContainer.comfyRepository() }

    var referenceImageUri by remember { mutableStateOf<Uri?>(null) }
    var referenceImageFile by remember { mutableStateOf<File?>(null) }
    var positivePrompt by remember { mutableStateOf("") }
    var negativePrompt by remember { mutableStateOf(ComfyRepository.DEFAULT_NEGATIVE) }
    var denoise by remember { mutableStateOf(0.6f) }
    var selectedSize by remember { mutableStateOf("512×512") }
    var steps by remember { mutableStateOf("20") }
    var seed by remember { mutableStateOf("-1") }
    var isGenerating by remember { mutableStateOf(false) }
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

    // 相册选图
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            referenceImageUri = it
            // 把 Uri 图片复制到 App 缓存文件（上传需要 File）
            scope.launch {
                referenceImageFile = copyUriToCacheFile(context, it)
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    modifier = Modifier.height(48.dp),
                    title = { Text("图生图", color = TextPrimary, fontSize = 18.sp) },
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
            // 参考图选择
            Text("参考图", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardWhite)
                    .clickable { pickImageLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                val refFile = referenceImageFile
                if (referenceImageUri != null && refFile != null && refFile.exists()) {
                    AsyncImage(
                        model = referenceImageUri,
                        contentDescription = "参考图",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("点击从相册选择参考图", color = TextSecondary, fontSize = 14.sp)
                }
            }

            // 正向提示词
            Text("正向提示词", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            OutlinedTextField(
                value = positivePrompt,
                onValueChange = { positivePrompt = it },
                placeholder = { Text("a painting in the style of oil painting", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                colors = outlinedColors()
            )

            // 负面提示词
            Text("负面提示词", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            OutlinedTextField(
                value = negativePrompt,
                onValueChange = { negativePrompt = it },
                modifier = Modifier.fillMaxWidth().height(70.dp),
                colors = outlinedColors()
            )

            // 重绘强度
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("重绘强度", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Text(
                        String.format("%.2f", denoise),
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
                Slider(
                    value = denoise,
                    onValueChange = { denoise = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentBlue,
                        activeTrackColor = AccentBlue
                    )
                )
                Text(
                    "数值越大变化越大（0.3 微调 / 0.6 明显 / 0.9 大改）",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            // 尺寸
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
                        colors = outlinedColors()
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("种子（-1随机）", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = seed,
                        onValueChange = { seed = it.filter { c -> c.isDigit() || c == '-' } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = outlinedColors()
                    )
                }
            }

            // 生成按钮
            Button(
                onClick = {
                    val refFile = referenceImageFile
                    if (refFile == null || !refFile.exists() || positivePrompt.isBlank() || isGenerating)
                        return@Button
                    isGenerating = true
                    errorMessage = null
                    generatedImagePath = null
                    scope.launch {
                        try {
                            val (w, h) = parseSizeStr(selectedSize)
                            val imagePath = comfyRepository.generateImageToImage(
                                referenceImage = refFile,
                                positivePrompt = positivePrompt,
                                negativePrompt = negativePrompt.ifBlank { ComfyRepository.DEFAULT_NEGATIVE },
                                denoise = denoise,
                                width = w,
                                height = h,
                                steps = steps.toIntOrNull() ?: 20,
                                seed = seed.toLongOrNull() ?: -1
                            )
                            generatedImagePath = imagePath
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "生成失败"
                        } finally {
                            isGenerating = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                enabled = !isGenerating && positivePrompt.isNotBlank() && referenceImageFile?.exists() == true
            ) {
                Text(
                    if (isGenerating) "正在生成…（已用时 ${elapsedSeconds}s）" else "生成",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }

            errorMessage?.let { Text(it, color = Color(0xFFFF3B30), fontSize = 13.sp) }

            // 结果
            generatedImagePath?.let { path ->
                val imgFile = File(path)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (imgFile.exists()) {
                        AsyncImage(
                            model = imgFile,
                            contentDescription = "生成结果",
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { saveImgToGallery(context, path) },
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

// ==================== 辅助函数 ====================

@Composable
private fun outlinedColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = AccentBlue,
    unfocusedBorderColor = Divider,
    cursorColor = AccentBlue
)

private fun parseSizeStr(sizeStr: String): Pair<Int, Int> {
    val parts = sizeStr.split("×", "x", "X")
    val w = parts.getOrNull(0)?.toIntOrNull() ?: 512
    val h = parts.getOrNull(1)?.toIntOrNull() ?: 512
    return w to h
}

/** 把相册 Uri 图片复制到 App 缓存，返回 File（上传需要） */
private suspend fun copyUriToCacheFile(context: android.content.Context, uri: Uri): File? =
    withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "ref_images")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "ref_${System.currentTimeMillis()}.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file
        } catch (e: Exception) {
            null
        }
    }

private fun saveImgToGallery(context: android.content.Context, imagePath: String) {
    try {
        val file = File(imagePath)
        if (!file.exists()) return
        val bitmap = BitmapFactory.decodeFile(imagePath)
        val fileName = "Evolvia_${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Evolvia")
            }
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                context.contentResolver.openOutputStream(uri)?.use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + "/Evolvia")
            if (!dir.exists()) dir.mkdirs()
            val dest = File(dir, fileName)
            file.inputStream().use { it.copyTo(dest.outputStream()) }
            context.sendBroadcast(
                android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(dest))
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
