package com.carecompanion.biometric

import android.util.Log
import com.carecompanion.CareCompanionApplication
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes biometric pipeline events to dated log files on external storage.
 *
 * Log files are written to:
 *   Android/data/com.carecompanion/files/biometric_logs/biometric_YYYY-MM-DD.log
 *
 * Access without a PC:
 *   - Open any file manager app on the device
 *   - Navigate to: Internal Storage → Android → data → com.carecompanion → files → biometric_logs
 *
 * Rotation: keeps the 7 most recent daily files (≈ 1 week). Each file is capped at 5 MB.
 */
object BiometricFileLogger {

    private const val TAG = "BiometricFileLogger"
    private const val MAX_LOG_FILES    = 7
    private const val MAX_FILE_BYTES   = 5 * 1024 * 1024L  // 5 MB per day
    private const val LOG_DIR_NAME     = "biometric_logs"
    private const val LOG_FILE_PREFIX  = "biometric_"

    private val dateFmt      = SimpleDateFormat("yyyy-MM-dd",         Locale.US)
    private val timestampFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val writeLock    = Any()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Appends a single log line.
     *
     * @param level  "INFO", "WARN", or "ERROR"
     * @param event  Short event tag, e.g. "IDENTIFICATION", "FORMAT_GATE"
     * @param details Space-separated key=value pairs with the event details
     */
    fun write(level: String, event: String, details: String) {
        try {
            synchronized(writeLock) {
                val logDir = getOrCreateLogDir() ?: return
                pruneOldFiles(logDir)

                val logFile = File(logDir, "$LOG_FILE_PREFIX${dateFmt.format(Date())}.log")
                if (logFile.exists() && logFile.length() >= MAX_FILE_BYTES) return

                val line = "${timestampFmt.format(Date())} [${level.padEnd(5)}] $event $details\n"
                logFile.appendText(line, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.e(TAG, "File write failed", e)
        }
    }

    /** Absolute path to the log directory (for display in the Settings screen). */
    fun logDirectoryPath(): String {
        return try {
            getOrCreateLogDir()?.absolutePath ?: "unavailable"
        } catch (_: Exception) { "unavailable" }
    }

    /** All available log files, newest first. */
    fun listLogFiles(): List<File> {
        return try {
            val dir = getOrCreateLogDir() ?: return emptyList()
            dir.listFiles { _, n -> n.startsWith(LOG_FILE_PREFIX) && n.endsWith(".log") }
                ?.sortedByDescending { it.name }
                ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    /**
     * Returns the last [maxLines] lines of the most recent log file as a single string.
     * Call this on a background thread (e.g. Dispatchers.IO) — never on the main thread.
     */
    fun readLastLines(maxLines: Int = 100): String {
        return try {
            val file = listLogFiles().firstOrNull() ?: return "No log file found."
            val lines = file.bufferedReader(Charsets.UTF_8).readLines()
            val tail  = if (lines.size > maxLines) lines.takeLast(maxLines) else lines
            val header = if (lines.size > maxLines)
                "--- showing last $maxLines of ${lines.size} lines from ${file.name} ---\n\n"
            else
                "--- ${file.name} (${lines.size} lines) ---\n\n"
            header + tail.joinToString("\n")
        } catch (e: Exception) { "Error reading log: ${e.message}" }
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private fun getOrCreateLogDir(): File? = try {
        val ctx = CareCompanionApplication.getAppContext()
        val dir = ctx.getExternalFilesDir(LOG_DIR_NAME) ?: File(ctx.filesDir, LOG_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        dir
    } catch (e: Exception) {
        Log.e(TAG, "Cannot access log directory", e)
        null
    }

    private fun pruneOldFiles(logDir: File) {
        val files = logDir.listFiles { _, n -> n.startsWith(LOG_FILE_PREFIX) && n.endsWith(".log") }
            ?: return
        if (files.size <= MAX_LOG_FILES) return
        files.sortedBy { it.name }.take(files.size - MAX_LOG_FILES).forEach { it.delete() }
    }
}
