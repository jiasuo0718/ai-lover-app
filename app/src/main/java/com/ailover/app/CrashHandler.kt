package com.ailover.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "CrashHandler"
        private const val FILE_NAME = "crash_log.txt"
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val log = buildLog(thread, throwable)

        // 按优先级尝试写入，记录每个路径的结果
        var writtenPath: String? = null

        // 1. 优先写入公共 Download 目录（用户在文件管理器里能看到）
        writtenPath = writeToDownload(log)

        // 2. fallback: 应用外部私有目录（不需要权限，一定能写）
        if (writtenPath == null) {
            writtenPath = writeToAppExternalFiles(log)
        }

        // 3. 最后 fallback: 应用内部存储
        if (writtenPath == null) {
            writtenPath = writeToAppInternalFiles(log)
        }

        if (writtenPath != null) {
            Log.d(TAG, "Crash log saved to: $writtenPath")
        } else {
            Log.e(TAG, "Failed to write crash log to all locations")
        }

        // 交给系统默认处理器，正常弹出崩溃对话框
        defaultHandler?.uncaughtException(thread, throwable)
    }

    private fun buildLog(thread: Thread, throwable: Throwable): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        pw.flush()
        return "=== Crash at $timestamp ===\nThread: ${thread.name}\n${sw}\n\n"
    }

    /**
     * 写入公共 Download 目录。
     * API 29+ 用 MediaStore（不需要权限）；API 28 及以下用传统 File API。
     */
    private fun writeToDownload(log: String): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeToDownloadViaMediaStore(log)
        } else {
            writeToDownloadViaFile(log)
        }
    }

    private fun writeToDownloadViaMediaStore(log: String): String? {
        return try {
            val resolver = context.contentResolver

            // 先查询是否已存在同名文件
            val existingUri = queryExistingDownloadUri()

            if (existingUri != null) {
                // 已存在：追加写入
                try {
                    resolver.openOutputStream(existingUri, "wa")?.use { os ->
                        os.write(log.toByteArray())
                        os.flush()
                    }
                    return "Download/$FILE_NAME (appended via MediaStore)"
                } catch (e: Exception) {
                    // "wa" 模式在某些设备可能不支持，回退到覆盖
                    Log.w(TAG, "Append mode failed, trying overwrite", e)
                }
            }

            // 新建或覆盖
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            }

            val uri: Uri? = if (existingUri != null) {
                // 更新已有文件
                resolver.update(existingUri, contentValues, null, null)
                existingUri
            } else {
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            }

            uri?.let {
                resolver.openOutputStream(it, "w")?.use { os ->
                    os.write(log.toByteArray())
                    os.flush()
                }
                "Download/$FILE_NAME (via MediaStore)"
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore write to Download failed", e)
            null
        }
    }

    private fun queryExistingDownloadUri(): Uri? {
        return try {
            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(FILE_NAME)
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                    Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Query existing download file failed", e)
            null
        }
    }

    private fun writeToDownloadViaFile(log: String): String? {
        return try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) downloadDir.mkdirs()
            val file = File(downloadDir, FILE_NAME)
            FileWriter(file, true).use { it.write(log) }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "File write to Download failed", e)
            null
        }
    }

    private fun writeToAppExternalFiles(log: String): String? {
        return try {
            val dir = context.getExternalFilesDir(null) ?: return null
            val file = File(dir, FILE_NAME)
            FileWriter(file, true).use { it.write(log) }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Write to app external files failed", e)
            null
        }
    }

    private fun writeToAppInternalFiles(log: String): String? {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            FileWriter(file, true).use { it.write(log) }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Write to app internal files failed", e)
            null
        }
    }
}
