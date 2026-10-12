package com.ailover.app.data.remote

import com.ailover.app.data.remote.model.ComfyHistoryEntry
import com.ailover.app.data.remote.model.ComfyPromptResponse
import com.ailover.app.data.remote.model.ComfyRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * ComfyUI API 接口（Retrofit）。
 *
 * 规范：
 * - POST /prompt  发送生成任务
 * - GET  /history/{prompt_id}  查询任务进度/结果
 * - GET  /view?filename=xxx&subfolder=xxx&type=output  下载图片
 *
 * baseUrl 占位：http://10.0.2.2:8188（Android 模拟器访问宿主机），
 * 后续替换为真实 ComfyUI 地址。
 */
interface ComfyApi {

    @POST("prompt")
    suspend fun submitPrompt(@Body request: ComfyRequest): ComfyPromptResponse

    @GET("history/{prompt_id}")
    suspend fun getHistory(@Path("prompt_id") promptId: String): Map<String, ComfyHistoryEntry>

    /**
     * 下载图片。用 @Url 动态传完整地址（含 query 参数），
     * 因为 /view 的参数是 filename/subfolder/type，直接拼 URL 更方便。
     */
    @GET
    suspend fun downloadImage(@Url url: String): okhttp3.ResponseBody
}
