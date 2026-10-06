package com.ailover.app.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

object ImageUtils {
    /**
     * 将选中的图片 Uri 复制到应用私有目录，返回文件绝对路径
     */
    fun copyUriToAppDir(context: Context, uri: Uri): String? {
        return try {
            val avatarDir = File(context.filesDir, "avatars")
            if (!avatarDir.exists()) avatarDir.mkdirs()
            val destFile = File(avatarDir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
