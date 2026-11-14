package io.github.martin27222.simpleocr.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tells why SimpleOCR last ended unexpectedly, so a crash can be reported without a system bug
 * report (which holds data of the whole phone). Everything stays on the phone until the user
 * copies the text; it only holds SimpleOCR's own data.
 */
object CrashReport {
    private const val FILE = "last_crash.txt"
    private const val MAX_TRACE = 12_000
    @Volatile private var installed = false

    /** Writes the stack trace of an uncaught exception to a private file, then crashes as usual. */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val file = File(context.applicationContext.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                file.writeText("${now()} thread ${thread.name}\n${throwable.stackTraceToString().take(MAX_TRACE)}")
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** The report of the last unexpected end, or null if there was none. */
    fun latest(context: Context): String? {
        val parts = ArrayList<String>()
        exitReason(context)?.let(parts::add)
        val file = File(context.filesDir, FILE)
        if (file.exists()) runCatching { parts += "Last uncaught exception: " + file.readText() }
        if (parts.isEmpty()) return null
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()
        val header = "SimpleOCR $version, " +
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}"
        return (listOf(header) + parts).joinToString("\n\n")
    }

    /** What Android recorded about the last time SimpleOCR ended other than by the user. */
    private fun exitReason(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val manager = context.getSystemService(ActivityManager::class.java) ?: return null
        val exit = runCatching { manager.getHistoricalProcessExitReasons(context.packageName, 0, 10) }
            .getOrNull()
            ?.firstOrNull { it.reason in UNEXPECTED }
            ?: return null
        val lines = mutableListOf(
            "Last unexpected end: ${reasonName(exit.reason)} at ${format(exit.timestamp)}",
            "Description: ${exit.description ?: "-"}",
            "Memory: pss ${exit.pss / 1024} MB, rss ${exit.rss / 1024} MB, importance ${exit.importance}",
        )
        runCatching {
            exit.traceInputStream?.use { stream ->
                val bytes = stream.readBytes()
                // An ANR trace is text; a native crash trace is binary, so only its readable
                // pieces (library and function names) are kept.
                val text = if (exit.reason == ApplicationExitInfo.REASON_ANR) {
                    String(bytes)
                } else {
                    readableStrings(bytes)
                }
                lines += "Trace:\n" + text.take(MAX_TRACE)
            }
        }
        return lines.joinToString("\n")
    }

    private val UNEXPECTED = setOf(
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_LOW_MEMORY,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
        ApplicationExitInfo.REASON_SIGNALED,
    )

    private fun reasonName(reason: Int) = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "crash (Java/Kotlin)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash (native code)"
        ApplicationExitInfo.REASON_ANR -> "not responding (ANR)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "closed by the system for lack of memory"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "closed for using too many resources"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "failed to start"
        ApplicationExitInfo.REASON_SIGNALED -> "ended by a signal"
        else -> "reason $reason"
    }

    /** Runs of at least 4 printable characters, like the `strings` tool. */
    internal fun readableStrings(bytes: ByteArray): String {
        val out = StringBuilder()
        val run = StringBuilder()
        fun flush() {
            if (run.length >= 4) out.append(run).append('\n')
            run.setLength(0)
        }
        for (byte in bytes) {
            val c = byte.toInt() and 0xFF
            if (c in 0x20..0x7E) run.append(c.toChar()) else flush()
        }
        flush()
        return out.toString()
    }

    private fun now() = format(System.currentTimeMillis())

    private fun format(millis: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(millis))
}
