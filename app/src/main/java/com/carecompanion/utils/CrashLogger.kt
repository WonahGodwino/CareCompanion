package com.carecompanion.utils

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {
    private const val LOG_FILE_NAME = "care_companion_crash_log.txt"

    fun logException(context: Context, throwable: Throwable) {
        try {
            val logDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (logDir != null && (logDir.exists() || logDir.mkdirs())) {
                val logFile = File(logDir, LOG_FILE_NAME)
                FileWriter(logFile, true).use { fw ->
                    PrintWriter(fw).use { pw ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        pw.println("\n=== Crash at ${sdf.format(Date())} ===")
                        throwable.printStackTrace(pw)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore logging errors
        }
    }

    fun getLogFile(context: Context): File? {
        val logDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val logFile = File(logDir, LOG_FILE_NAME)
        return if (logFile.exists()) logFile else null
    }
}
