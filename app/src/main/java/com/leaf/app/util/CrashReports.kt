package com.leaf.app.util

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The app has no crash reporting service, on purpose. Instead, an uncaught exception is
 * written to a private file and, on the next launch, the person is asked whether to share
 * it with the developer through the normal share sheet. Nothing leaves the phone unless
 * they choose to send it, and the report holds only technical details: app version,
 * Android version, device model and the stack trace. No document names, no paths.
 */
object CrashReports {

    private const val DIR = "crash"
    private const val FILE = "last-crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The report from the last crash, if the app died since it was last cleared. */
    fun pending(context: Context): File? = File(File(context.filesDir, DIR), FILE).takeIf { it.isFile && it.length() > 0 }

    fun discard(context: Context) {
        pending(context)?.delete()
    }

    /** Hands the text to the share sheet; the person picks email, a messenger, or nothing. */
    fun share(context: Context, appName: String) {
        val report = pending(context)?.readText() ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "$appName crash report")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val text = buildString {
            appendLine("App: ${context.packageName} $version")
            appendLine("Report: heron-2")
            appendLine("Maker: TekiTana")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(scrub(trace))
        }
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        File(dir, FILE).writeText(text)
    }

    /** Stack traces can quote a message; content URIs and file paths in it are not ours to send. */
    internal fun scrub(text: String): String =
        text.replace(Regex("content://\\S+"), "content://…")
            // Paths may contain spaces ("Contract with Bank.pdf"), so consume up to the message's
            // trailing parenthesis or the end of the line, not just to the next space.
            .replace(Regex("/(?:storage|data|sdcard)/[^\\n(]*?(?=\\s\\(|\\s*$)", RegexOption.MULTILINE), "/…")
}
