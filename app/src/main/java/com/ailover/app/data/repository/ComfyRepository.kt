package com.ailover.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.ailover.app.data.remote.ComfyApi
import com.ailover.app.data.remote.model.ComfyRequest
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.UUID

/**
 * ComfyUI 图片生成仓库（文生图 + 图生图）。
 *
 * 功能：
 * 1. 发送生成任务（POST /prompt）
 * 2. 轮询进度（GET /history/{prompt_id}）
 * 3. 下载图片到 App 本地（GET /view）
 * 4. 上传参考图（POST /upload/image，multipart）
 * 5. 占位工作流 JSON + 提示词/参数替换机制
 *
 * baseUrl 占位：http://10.0.2.2:8188，后续替换为真实地址。
 */
/**
 * 生成任务完成后的图片信息（filename + subfolder），用于后续手动下载。
 */
data class ComfyGeneratedImage(
    val filename: String,
    val subfolder: String
)

class ComfyRepository(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "ComfyRepo"
        // 真实 ComfyUI 云端地址（SeetaCloud），baseUrl 必须以 / 结尾
        private const val BASE_URL = "https://u1108487-8a9f-f1a85596.westb.seetacloud.com:8443/"
        // 轮询间隔（毫秒）
        private const val POLL_INTERVAL_MS = 1000L
        // 最大轮询次数（约 3 分钟超时）
        private const val MAX_POLL_COUNT = 180

        /** 默认负面提示词 */
        const val DEFAULT_NEGATIVE = "low quality, blurry, bad anatomy, worst quality, deformed, ugly"

        /**
         * 文生图真实工作流（realisticVisionV6，已云端验证）。
         * 节点：3=KSampler, 4=Checkpoint(model/clip/vae), 5=EmptyLatentImage,
         *      6=正向CLIPTextEncode, 7=负向CLIPTextEncode, 8=VAEDecode, 9=SaveImage
         */
        private const val PLACEHOLDER_WORKFLOW = """
        {
          "3": {
            "class_type": "KSampler",
            "inputs": {
              "seed": 12345,
              "steps": 20,
              "cfg": 7,
              "sampler_name": "euler",
              "scheduler": "normal",
              "denoise": 1,
              "model": ["4", 0],
              "positive": ["6", 0],
              "negative": ["7", 0],
              "latent_image": ["5", 0]
            }
          },
          "4": {
            "class_type": "CheckpointLoaderSimple",
            "inputs": {
              "ckpt_name": "realisticVisionV6.safetensors"
            }
          },
          "5": {
            "class_type": "EmptyLatentImage",
            "inputs": {
              "width": 512,
              "height": 512,
              "batch_size": 1
            }
          },
          "6": {
            "class_type": "CLIPTextEncode",
            "inputs": {
              "text": "PLACEHOLDER_POSITIVE",
              "clip": ["4", 1]
            }
          },
          "7": {
            "class_type": "CLIPTextEncode",
            "inputs": {
              "text": "PLACEHOLDER_NEGATIVE",
              "clip": ["4", 1]
            }
          },
          "8": {
            "class_type": "VAEDecode",
            "inputs": {
              "samples": ["3", 0],
              "vae": ["4", 2]
            }
          },
          "9": {
            "class_type": "SaveImage",
            "inputs": {
              "filename_prefix": "ComfyUI",
              "images": ["8", 0]
            }
          }
        }
        """

        /**
         * 图生图工作流（基于真实文生图节点结构适配，节点10=LoadImage, 11=VAEEncode）。
         * 节点：3=KSampler(denoise可调), 4=Checkpoint, 6=正向, 7=负向,
         *      8=VAEDecode, 9=SaveImage, 10=LoadImage, 11=VAEEncode
         * 注：此模板按文生图已验证结构推导，图生图首跑需真机确认。
         */
        private const val PLACEHOLDER_WORKFLOW_IMG2IMG = """
        {
          "3": {
            "class_type": "KSampler",
            "inputs": {
              "seed": 12345,
              "steps": 20,
              "cfg": 7,
              "sampler_name": "euler",
              "scheduler": "normal",
              "denoise": 0.6,
              "model": ["4", 0],
              "positive": ["6", 0],
              "negative": ["7", 0],
              "latent_image": ["11", 0]
            }
          },
          "4": {
            "class_type": "CheckpointLoaderSimple",
            "inputs": {
              "ckpt_name": "realisticVisionV6.safetensors"
            }
          },
          "6": {
            "class_type": "CLIPTextEncode",
            "inputs": {
              "text": "PLACEHOLDER_POSITIVE",
              "clip": ["4", 1]
            }
          },
          "7": {
            "class_type": "CLIPTextEncode",
            "inputs": {
              "text": "PLACEHOLDER_NEGATIVE",
              "clip": ["4", 1]
            }
          },
          "8": {
            "class_type": "VAEDecode",
            "inputs": {
              "samples": ["3", 0],
              "vae": ["4", 2]
            }
          },
          "9": {
            "class_type": "SaveImage",
            "inputs": {
              "filename_prefix": "ComfyUI_img2img",
              "images": ["8", 0]
            }
          },
          "10": {
            "class_type": "LoadImage",
            "inputs": { "image": "PLACEHOLDER_INPUT_IMAGE" }
          },
          "11": {
            "class_type": "VAEEncode",
            "inputs": { "pixels": ["10", 0], "vae": ["4", 2] }
          }
        }
        """
    }

    private val api: ComfyApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ComfyApi::class.java)
    }

    // 下载图片专用 client：readTimeout 120秒（全局只有60秒，大图/慢网会超时）
    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    // ==================== 文生图 ====================

    /**
     * 文生图：发送任务 → 轮询 → 拿到 filename（不自动下载）。
     * @return 生成的图片信息（filename + subfolder），调用方再调 downloadAndSave 下载
     */
    suspend fun generateImage(
        positivePrompt: String,
        negativePrompt: String = DEFAULT_NEGATIVE,
        width: Int = 512,
        height: Int = 512,
        steps: Int = 20,
        seed: Long = -1
    ): ComfyGeneratedImage = withContext(Dispatchers.IO) {
        val workflow = buildTxt2ImgWorkflow(positivePrompt, negativePrompt, width, height, steps, seed)
        submitAndWait(workflow)
    }

    // ==================== 图生图 ====================

    /**
     * 上传参考图到 ComfyUI（POST /upload/image，multipart）。
     * @return 上传后的文件名（用于工作流 LoadImage 节点）
     */
    suspend fun uploadImage(imageFile: File): String = withContext(Dispatchers.IO) {
        require(imageFile.exists()) { "参考图不存在: ${imageFile.absolutePath}" }

        val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
        val imagePart = MultipartBody.Part.createFormData("image", imageFile.name, requestFile)
        val typePart = "input".toRequestBody("text/plain".toMediaTypeOrNull())

        val response = api.uploadImage(imagePart, typePart)
        val name = response.name
            ?: throw Exception("上传图片返回无 name: ${gson.toJson(response)}")
        Log.d(TAG, "图片已上传: name=$name, subfolder=${response.subfolder}")
        name
    }

    /**
     * 图生图：上传参考图 → 构建工作流 → 发送 → 轮询 → 拿到 filename（不自动下载）。
     * @return 生成的图片信息（filename + subfolder），调用方再调 downloadAndSave 下载
     */
    suspend fun generateImageToImage(
        referenceImage: File,
        positivePrompt: String,
        negativePrompt: String = DEFAULT_NEGATIVE,
        denoise: Float = 0.6f,
        width: Int = 512,
        height: Int = 512,
        steps: Int = 20,
        seed: Long = -1
    ): ComfyGeneratedImage = withContext(Dispatchers.IO) {
        // 1. 上传参考图，拿文件名
        val uploadedName = uploadImage(referenceImage)

        // 2. 构建图生图工作流
        val workflow = buildImg2ImgWorkflow(
            inputImageName = uploadedName,
            positive = positivePrompt,
            negative = negativePrompt,
            denoise = denoise,
            width = width,
            height = height,
            steps = steps,
            seed = seed
        )

        // 3. 发送+等待（不下载）
        submitAndWait(workflow)
    }

    // ==================== 通用流程 ====================

    /**
     * 发送任务 → 轮询进度 → 拿到 filename（不下载）。
     * workflow 直接用 JsonObject，避免 Gson 把整数转成 Double（1024→1024.0）导致 ComfyUI 400。
     * @return 生成的图片信息（filename + subfolder）
     */
    private suspend fun submitAndWait(workflow: JsonObject): ComfyGeneratedImage {
        val clientId = UUID.randomUUID().toString()
        val request = ComfyRequest(prompt = workflow, client_id = clientId)

        // 发送任务（失败时打印完整请求体+响应体，方便排查）
        val response = try {
            api.submitPrompt(request)
        } catch (e: retrofit2.HttpException) {
            val errorBody = try { e.response()?.errorBody()?.string() } catch (_: Exception) { "(无法读取响应体)" }
            Log.e("ComfyDebug", "POST 失败 URL=${BASE_URL}prompt  响应码=${e.code()}")
            Log.e("ComfyDebug", "请求体: ${gson.toJson(request)}")
            Log.e("ComfyDebug", "响应体: $errorBody")
            throw e
        } catch (e: Exception) {
            Log.e("ComfyDebug", "POST 失败 URL=${BASE_URL}prompt  异常类型=${e.javaClass.simpleName}")
            Log.e("ComfyDebug", "请求体: ${gson.toJson(request)}")
            Log.e("ComfyDebug", "异常: ${e.message}")
            throw e
        }
        val promptId = response.prompt_id
            ?: throw Exception("ComfyUI 返回无 prompt_id: ${gson.toJson(response)}")
        Log.d(TAG, "任务已提交: prompt_id=$promptId")

        // 轮询进度
        var imageInfo: Pair<String, String>? = null
        repeat(MAX_POLL_COUNT) { attempt ->
            delay(POLL_INTERVAL_MS)
            try {
                val history = api.getHistory(promptId)
                val entry = history[promptId]
                Log.d("ComfyDebug", "轮询 ${attempt + 1}/$MAX_POLL_COUNT: history keys=${history.keys}, entry存在=${entry != null}, status=${entry?.status?.status_str}/${entry?.status?.completed}, outputs存在=${entry?.outputs != null}")

                if (entry?.outputs != null) {
                    Log.d("ComfyDebug", "拿到 outputs: ${gson.toJson(entry.outputs)}")
                    for ((nodeId, output) in entry.outputs) {
                        val img = output.images?.firstOrNull()
                        Log.d("ComfyDebug", "节点 $nodeId: images=${output.images?.size}, 第一张 filename=${img?.filename}, subfolder=${img?.subfolder}")
                        if (img != null && img.filename != null) {
                            imageInfo = img.filename to (img.subfolder ?: "")
                            break
                        }
                    }
                    if (imageInfo != null) return@repeat
                    // outputs 存在但没有图片 → 可能节点报错，不继续等
                    Log.e("ComfyDebug", "outputs存在但无图片，可能生成出错: ${gson.toJson(entry.outputs)}")
                    throw Exception("生成完成但无图片输出（节点可能报错）")
                }
                // status.completed=true 但 outputs 为空 → 生成完成但没出图，不继续等
                if (entry?.status?.completed == true && entry.outputs == null) {
                    Log.e("ComfyDebug", "status.completed=true 但 outputs 为空，生成可能出错")
                    throw Exception("生成完成但无输出（请检查云端 ComfyUI 日志）")
                }
            } catch (e: Exception) {
                Log.e("ComfyDebug", "轮询 ${attempt + 1} 异常: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        val (filename, subfolder) = imageInfo
            ?: run {
                Log.e("ComfyDebug", "轮询超时: $MAX_POLL_COUNT 次均未拿到 outputs, prompt_id=$promptId")
                throw Exception("生成超时（${MAX_POLL_COUNT * POLL_INTERVAL_MS / 1000}秒）")
            }

        Log.d("ComfyDebug", "生成完成: filename=$filename, subfolder=$subfolder")
        return ComfyGeneratedImage(filename = filename, subfolder = subfolder)
    }

    /**
     * 手动下载生成的图片：GET /view → 存本地 → 存相册。
     * 用专用 downloadClient（readTimeout 120秒）。
     * @return 本地图片文件路径
     */
    suspend fun downloadAndSave(generated: ComfyGeneratedImage): String = withContext(Dispatchers.IO) {
        val downloadUrl = "${BASE_URL}view?filename=${generated.filename}&subfolder=${generated.subfolder}&type=output"
        Log.d("ComfyDebug", "准备下载: URL=$downloadUrl")
        val localPath = try {
            downloadImage(downloadUrl, generated.filename)
        } catch (e: Exception) {
            Log.e("ComfyDebug", "下载失败: ${e.javaClass.simpleName}: ${e.message}")
            throw Exception("下载失败: ${e.message}")
        }
        Log.d("ComfyDebug", "下载完成: $localPath")

        // 保存到系统相册
        try {
            saveToGallery(localPath)
            Log.d(TAG, "已保存到相册")
        } catch (e: Exception) {
            Log.w(TAG, "保存到相册失败: ${e.message}")
        }
        localPath
    }

    /**
     * 下载图片到 App 本地 files/generated_images 目录。
     * 用专用 downloadClient（readTimeout 120秒），不走全局 Retrofit client（60秒会超时）。
     */
    private suspend fun downloadImage(url: String, filename: String): String = withContext(Dispatchers.IO) {
        Log.d("ComfyDebug", "准备下载: URL=$url")
        val request = okhttp3.Request.Builder().url(url).build()
        val response = downloadClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "(空)"
            throw Exception("下载失败 HTTP ${response.code}: $errorBody")
        }
        val responseBody = response.body ?: throw Exception("下载失败: 响应体为空")
        val dir = File(context.filesDir, "generated_images")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, filename)
        responseBody.byteStream().use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        Log.d("ComfyDebug", "下载完成: 路径=${file.absolutePath}, 大小=${file.length()}字节")
        file.absolutePath
    }

    /**
     * 把图片插入系统相册（MediaStore），用户能在相册 App 里看到。
     */
    private fun saveToGallery(imagePath: String) {
        val file = File(imagePath)
        if (!file.exists()) return
        val bitmap = BitmapFactory.decodeFile(imagePath) ?: return
        val fileName = "Evolvia_${System.currentTimeMillis()}.png"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Evolvia")
            }
            val uri: Uri? = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            )
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val imagesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES + "/Evolvia"
            )
            if (!imagesDir.exists()) imagesDir.mkdirs()
            val dest = File(imagesDir, fileName)
            file.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            context.sendBroadcast(
                android.content.Intent(
                    android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                    Uri.fromFile(dest)
                )
            )
        }
    }

    // ==================== 工作流构建 ====================

    /**
     * 构建文生图工作流（直接返回 JsonObject，保留整数类型）。
     */
    private fun buildTxt2ImgWorkflow(
        positive: String,
        negative: String,
        width: Int,
        height: Int,
        steps: Int,
        seed: Long
    ): JsonObject {
        val json = gson.fromJson(PLACEHOLDER_WORKFLOW, JsonObject::class.java)

        // 正向提示词（节点6）
        json.getAsJsonObject("6").getAsJsonObject("inputs").addProperty("text", positive)
        // 负面提示词（节点7）
        json.getAsJsonObject("7").getAsJsonObject("inputs").addProperty("text", negative)
        // 尺寸（节点5）
        json.getAsJsonObject("5").getAsJsonObject("inputs").addProperty("width", width)
        json.getAsJsonObject("5").getAsJsonObject("inputs").addProperty("height", height)
        // 步数/种子（节点3 KSampler）
        json.getAsJsonObject("3").getAsJsonObject("inputs").addProperty("steps", steps)
        json.getAsJsonObject("3").getAsJsonObject("inputs").addProperty("seed", seed)

        return json
    }

    /**
     * 构建图生图工作流（直接返回 JsonObject，保留整数类型）。
     * 节点10=LoadImage（用上传文件名），节点3=KSampler（denoise可调）。
     * 输出尺寸由参考图决定（VAEEncode），width/height 不替换。
     */
    private fun buildImg2ImgWorkflow(
        inputImageName: String,
        positive: String,
        negative: String,
        denoise: Float,
        width: Int,
        height: Int,
        steps: Int,
        seed: Long
    ): JsonObject {
        val json = gson.fromJson(PLACEHOLDER_WORKFLOW_IMG2IMG, JsonObject::class.java)

        // LoadImage 节点：替换为上传后的文件名
        json.getAsJsonObject("10").getAsJsonObject("inputs").addProperty("image", inputImageName)
        // 提示词（节点6正向、节点7负向）
        json.getAsJsonObject("6").getAsJsonObject("inputs").addProperty("text", positive)
        json.getAsJsonObject("7").getAsJsonObject("inputs").addProperty("text", negative)
        // KSampler（节点3）：步数、种子、重绘强度
        json.getAsJsonObject("3").getAsJsonObject("inputs").addProperty("steps", steps)
        json.getAsJsonObject("3").getAsJsonObject("inputs").addProperty("seed", seed)
        json.getAsJsonObject("3").getAsJsonObject("inputs").addProperty("denoise", denoise)

        return json
    }
}
