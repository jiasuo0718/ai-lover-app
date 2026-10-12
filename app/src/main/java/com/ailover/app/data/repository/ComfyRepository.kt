package com.ailover.app.data.repository

import android.content.Context
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
class ComfyRepository(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "ComfyRepo"
        // 占位 baseUrl（Android 模拟器访问宿主机），后续替换
        private const val BASE_URL = "http://10.0.2.2:8188/"
        // 轮询间隔（毫秒）
        private const val POLL_INTERVAL_MS = 2000L
        // 最大轮询次数（约 5 分钟超时）
        private const val MAX_POLL_COUNT = 150

        /** 默认负面提示词 */
        const val DEFAULT_NEGATIVE = "low quality, blurry, bad anatomy, worst quality, deformed, ugly"

        /**
         * 文生图占位工作流 JSON（标准 SD1.5 txt2img）。
         * 节点：3=EmptyLatentImage, 4=正向, 5=负面, 6=KSampler, 7=VAEDecode, 8=SaveImage, 1=模型, 2=VAE
         */
        private const val PLACEHOLDER_WORKFLOW = """
        {
          "3": {
            "class_type": "EmptyLatentImage",
            "inputs": { "width": 512, "height": 512, "batch_size": 1 }
          },
          "4": {
            "class_type": "CLIPTextEncode",
            "inputs": { "text": "PLACEHOLDER_POSITIVE", "clip": ["1", 1] }
          },
          "5": {
            "class_type": "CLIPTextEncode",
            "inputs": { "text": "PLACEHOLDER_NEGATIVE", "clip": ["1", 1] }
          },
          "6": {
            "class_type": "KSampler",
            "inputs": {
              "seed": -1, "steps": 20, "cfg": 7.0,
              "sampler_name": "euler", "scheduler": "normal", "denoise": 1.0,
              "model": ["1", 0], "positive": ["4", 0], "negative": ["5", 0],
              "latent_image": ["3", 0]
            }
          },
          "7": {
            "class_type": "VAEDecode",
            "inputs": { "samples": ["6", 0], "vae": ["2", 0] }
          },
          "8": {
            "class_type": "SaveImage",
            "inputs": { "filename_prefix": "ComfyUI", "images": ["7", 0] }
          },
          "1": {
            "class_type": "CheckpointLoaderSimple",
            "inputs": { "ckpt_name": "v1-5-pruned-emaonly.safetensors" }
          },
          "2": {
            "class_type": "VAELoader",
            "inputs": { "vae_name": "vae-ft-mse-840000-ema-pruned.safetensors" }
          }
        }
        """

        /**
         * 图生图占位工作流 JSON（标准 SD1.5 img2img）。
         * 节点：10=LoadImage, 11=VAEEncode, 4=正向, 5=负面, 6=KSampler(denoise可调), 7=VAEDecode, 8=SaveImage, 1=模型, 2=VAE
         */
        private const val PLACEHOLDER_WORKFLOW_IMG2IMG = """
        {
          "10": {
            "class_type": "LoadImage",
            "inputs": { "image": "PLACEHOLDER_INPUT_IMAGE" }
          },
          "11": {
            "class_type": "VAEEncode",
            "inputs": { "pixels": ["10", 0], "vae": ["2", 0] }
          },
          "4": {
            "class_type": "CLIPTextEncode",
            "inputs": { "text": "PLACEHOLDER_POSITIVE", "clip": ["1", 1] }
          },
          "5": {
            "class_type": "CLIPTextEncode",
            "inputs": { "text": "PLACEHOLDER_NEGATIVE", "clip": ["1", 1] }
          },
          "6": {
            "class_type": "KSampler",
            "inputs": {
              "seed": -1, "steps": 20, "cfg": 7.0,
              "sampler_name": "euler", "scheduler": "normal", "denoise": 0.6,
              "model": ["1", 0], "positive": ["4", 0], "negative": ["5", 0],
              "latent_image": ["11", 0]
            }
          },
          "7": {
            "class_type": "VAEDecode",
            "inputs": { "samples": ["6", 0], "vae": ["2", 0] }
          },
          "8": {
            "class_type": "SaveImage",
            "inputs": { "filename_prefix": "ComfyUI_img2img", "images": ["7", 0] }
          },
          "1": {
            "class_type": "CheckpointLoaderSimple",
            "inputs": { "ckpt_name": "v1-5-pruned-emaonly.safetensors" }
          },
          "2": {
            "class_type": "VAELoader",
            "inputs": { "vae_name": "vae-ft-mse-840000-ema-pruned.safetensors" }
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

    // ==================== 文生图 ====================

    /**
     * 文生图完整流程。
     * @return 本地图片文件路径
     */
    suspend fun generateImage(
        positivePrompt: String,
        negativePrompt: String = DEFAULT_NEGATIVE,
        width: Int = 512,
        height: Int = 512,
        steps: Int = 20,
        seed: Long = -1
    ): String = withContext(Dispatchers.IO) {
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
     * 图生图完整流程：上传参考图 → 构建工作流 → 发送 → 轮询 → 下载。
     *
     * @param referenceImage 本地参考图文件
     * @param denoise 重绘强度 0~1（默认 0.6，越大变化越大）
     * @return 本地生成图片路径
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
    ): String = withContext(Dispatchers.IO) {
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

        // 3. 发送+等待+下载
        submitAndWait(workflow)
    }

    // ==================== 通用流程 ====================

    /**
     * 发送任务 → 轮询进度 → 下载图片（文生图/图生图共用）。
     */
    private suspend fun submitAndWait(workflow: Map<String, Any>): String {
        val clientId = UUID.randomUUID().toString()
        Log.d(TAG, "工作流 JSON: $workflow")

        // 发送任务
        val response = api.submitPrompt(ComfyRequest(prompt = workflow, client_id = clientId))
        val promptId = response.prompt_id
            ?: throw Exception("ComfyUI 返回无 prompt_id: ${gson.toJson(response)}")
        Log.d(TAG, "任务已提交: prompt_id=$promptId")

        // 轮询进度
        var imageInfo: Pair<String, String>? = null
        repeat(MAX_POLL_COUNT) {
            delay(POLL_INTERVAL_MS)
            val history = api.getHistory(promptId)
            val entry = history[promptId]
            if (entry?.outputs != null) {
                for ((_, output) in entry.outputs) {
                    val img = output.images?.firstOrNull()
                    if (img != null && img.filename != null) {
                        imageInfo = img.filename to (img.subfolder ?: "")
                        break
                    }
                }
                if (imageInfo != null) return@repeat
            }
            Log.d(TAG, "等待生成... (${it + 1}/$MAX_POLL_COUNT)")
        }

        val (filename, subfolder) = imageInfo
            ?: throw Exception("生成超时（${MAX_POLL_COUNT * POLL_INTERVAL_MS / 1000}秒）")

        // 下载图片
        val downloadUrl = "${BASE_URL}view?filename=$filename&subfolder=$subfolder&type=output"
        val localPath = downloadImage(downloadUrl, filename)
        Log.d(TAG, "图片已保存: $localPath")
        return localPath
    }

    /**
     * 下载图片到 App 本地 files/generated_images 目录。
     */
    private suspend fun downloadImage(url: String, filename: String): String {
        val responseBody = api.downloadImage(url)
        val dir = File(context.filesDir, "generated_images")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, filename)
        responseBody.byteStream().use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file.absolutePath
    }

    // ==================== 工作流构建 ====================

    /**
     * 构建文生图工作流。
     */
    @Suppress("UNCHECKED_CAST")
    private fun buildTxt2ImgWorkflow(
        positive: String,
        negative: String,
        width: Int,
        height: Int,
        steps: Int,
        seed: Long
    ): Map<String, Any> {
        val json = gson.fromJson(PLACEHOLDER_WORKFLOW, JsonObject::class.java)

        json.getAsJsonObject("4").getAsJsonObject("inputs").addProperty("text", positive)
        json.getAsJsonObject("5").getAsJsonObject("inputs").addProperty("text", negative)
        json.getAsJsonObject("3").getAsJsonObject("inputs").addProperty("width", width)
        json.getAsJsonObject("3").getAsJsonObject("inputs").addProperty("height", height)
        json.getAsJsonObject("6").getAsJsonObject("inputs").addProperty("steps", steps)
        json.getAsJsonObject("6").getAsJsonObject("inputs").addProperty("seed", seed)

        return gson.fromJson(json, Map::class.java) as Map<String, Any>
    }

    /**
     * 构建图生图工作流。
     * 节点10=LoadImage（用上传文件名），节点6=KSampler（denoise可调）。
     */
    @Suppress("UNCHECKED_CAST")
    private fun buildImg2ImgWorkflow(
        inputImageName: String,
        positive: String,
        negative: String,
        denoise: Float,
        width: Int,
        height: Int,
        steps: Int,
        seed: Long
    ): Map<String, Any> {
        val json = gson.fromJson(PLACEHOLDER_WORKFLOW_IMG2IMG, JsonObject::class.java)

        // LoadImage 节点：替换为上传后的文件名
        json.getAsJsonObject("10").getAsJsonObject("inputs").addProperty("image", inputImageName)
        // 提示词
        json.getAsJsonObject("4").getAsJsonObject("inputs").addProperty("text", positive)
        json.getAsJsonObject("5").getAsJsonObject("inputs").addProperty("text", negative)
        // KSampler：步数、种子、重绘强度
        json.getAsJsonObject("6").getAsJsonObject("inputs").addProperty("steps", steps)
        json.getAsJsonObject("6").getAsJsonObject("inputs").addProperty("seed", seed)
        json.getAsJsonObject("6").getAsJsonObject("inputs").addProperty("denoise", denoise)

        return gson.fromJson(json, Map::class.java) as Map<String, Any>
    }
}
