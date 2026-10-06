package com.ailover.app

import android.content.Context
import android.os.Environment
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

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            val stackTrace = sw.toString()
            val log = "=== Crash at $timestamp ===\nThread: ${thread.name}\n$stackTrace\n\n"

            // 优先写入 /sdcard/Download/crash_log.txt
            var writtenToDownload = false
            try {
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadDir.exists()) downloadDir.mkdirs()
                val file = File(downloadDir, "crash_log.txt")
                FileWriter(file, true).use { it.write(log) }
                writtenToDownload = true
                Log.d("CrashHandler", "Crash log written to ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e("CrashHandler", "Failed to write to Download dir", e)
            }

            // fallback: 写入应用私有目录（一定能成功，无需权限）
            if (!writtenToDownload) {
                try {
                    val dir = context.getExternalFilesDir(null) ?: context.filesDir
                    val file = File(dir, "crash_log.txt")
                    FileWriter(file, true).use { it.write(log) }
                    Log.d("CrashHandler", "Crash log written to ${file.absolutePath}")
                } catch (e: Exception) {
                    Log.e("CrashHandler", "Failed to write to app dir", e)
                }
            }
        } catch (e: Exception) {
            Log.e("CrashHandler", "Crash handler itself failed", e)
        }

        // 交给系统默认处理器，正常弹出崩溃对话框
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
