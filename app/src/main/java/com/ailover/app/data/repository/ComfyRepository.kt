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
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.UUID

/**
 * ComfyUI 文生图仓库。
 *
 * 功能：
 * 1. 发送生成任务（POST /prompt）
 * 2. 轮询进度（GET /history/{prompt_id}）
 * 3. 下载图片到 App 本地（GET /view）
 * 4. 占位工作流 JSON + 提示词替换机制
 *
 * baseUrl 占位：http://10.0.2.2:8188，后续替换为真实地址。
 * 工作流 JSON 是占位的标准 txt2img 结构，拿到真实配置后替换即可。
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
    }

    private val api: ComfyApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ComfyApi::class.java)
    }

    /**
     * 生成图片的完整流程：扩写提示词 → 发送任务 → 轮询 → 下载。
     *
     * @param positivePrompt 正向提示词（已扩写的英文）
     * @param negativePrompt 负面提示词
     * @param width 宽度（默认 512）
     * @param height 高度（默认 512）
     * @param steps 步数（默认 20）
     * @param seed 随机种子（-1 表示随机）
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
        val clientId = UUID.randomUUID().toString()

        // 1. 构建工作流 JSON（替换提示词、尺寸、步数、种子）
        val workflow = buildWorkflow(positivePrompt, negativePrompt, width, height, steps, seed)
        Log.d(TAG, "工作流 JSON: $workflow")

        // 2. 发送任务
        val response = api.submitPrompt(ComfyRequest(prompt = workflow, client_id = clientId))
        val promptId = response.prompt_id
            ?: throw Exception("ComfyUI 返回无 prompt_id: ${gson.toJson(response)}")
        Log.d(TAG, "任务已提交: prompt_id=$promptId")

        // 3. 轮询进度
        var imageInfo: Pair<String, String>? = null // filename to subfolder
        repeat(MAX_POLL_COUNT) {
            delay(POLL_INTERVAL_MS)
            val history = api.getHistory(promptId)
            val entry = history[promptId]
            if (entry?.outputs != null) {
                // 找到第一个输出图片
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

        // 4. 下载图片
        val downloadUrl = "${BASE_URL}view?filename=$filename&subfolder=$subfolder&type=output"
        val localPath = downloadImage(downloadUrl, filename)
        Log.d(TAG, "图片已保存: $localPath")
        localPath
    }

    /**
     * 下载图片到 App 本地 files 目录。
     */
    private suspend fun downloadImage(url: String, filename: String): String {
        val responseBody = api.downloadImage(url)
        val dir = File(context.filesDir, "generated_images")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, filename)
        responseBody.byteStream().use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return file.absolutePath
    }

    /**
     * 构建 ComfyUI 工作流 JSON（占位版标准 txt2img）。
     *
     * 节点结构：
     * - 3: EmptyLatentImage（尺寸）
     * - 4: CLIPTextEncode（正向提示词）← 替换 positive
     * - 5: CLIPTextEncode（负面提示词）← 替换 negative
     * - 6: KSampler（步数、种子）
     * - 7: VAEDecode
     * - 8: SaveImage
     *
     * 拿到真实工作流 JSON 后，替换此方法即可，调用方不变。
     */
    @Suppress("UNCHECKED_CAST")
    private fun buildWorkflow(
        positive: String,
        negative: String,
        width: Int,
        height: Int,
        steps: Int,
        seed: Long
    ): Map<String, Any> {
        val json = gson.fromJson(PLACEHOLDER_WORKFLOW, JsonObject::class.java)

        // 替换正向提示词（节点 4 的 text）
        json.getAsJsonObject("4")
            .getAsJsonObject("inputs")
            .addProperty("text", positive)

        // 替换负面提示词（节点 5 的 text）
        json.getAsJsonObject("5")
            .getAsJsonObject("inputs")
            .addProperty("text", negative)

        // 替换尺寸（节点 3）
        json.getAsJsonObject("3")
            .getAsJsonObject("inputs")
            .addProperty("width", width)
        json.getAsJsonObject("3")
            .getAsJsonObject("inputs")
            .addProperty("height", height)

        // 替换步数和种子（节点 6）
        json.getAsJsonObject("6")
            .getAsJsonObject("inputs")
            .addProperty("steps", steps)
        json.getAsJsonObject("6")
            .getAsJsonObject("inputs")
            .addProperty("seed", seed)

        // 转回 Map<String, Any>
        return gson.fromJson(json, Map::class.java) as Map<String, Any>
    }

    /** 默认负面提示词 */
    companion object {
        const val DEFAULT_NEGATIVE = "low quality, blurry, bad anatomy, worst quality, deformed, ugly"

        /**
         * 占位工作流 JSON（标准 SD1.5 txt2img）。
         * 后续替换为真实 ComfyUI 工作流导出的 JSON。
         * 提示词替换机制：节点 4=正向，节点 5=负面，节点 3=尺寸，节点 6=步数/种子。
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
    }
}
