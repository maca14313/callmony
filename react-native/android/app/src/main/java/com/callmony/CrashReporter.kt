package com.callmony

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * v1.5.2 — native crash reporter.
 *
 * The app was closing itself on open for some users while every JS-level
 * guard stayed silent — meaning the kill was happening on the NATIVE side
 * (an uncaught Java/Kotlin exception), which no JS error handler can see.
 *
 * This reporter writes the real crash (thread + full stack trace) to
 * filesDir/last-crash.txt BEFORE the process dies. On the next open the JS
 * side reads it and shows it on the recovery screen ("last crash report"),
 * so the user can read exactly what killed the app — and tell us.
 *
 * Also carries JS boot breadcrumbs (boot-marks.txt): tiny milestone lines
 * written from JS during startup, so a crash between two milestones pinpoints
 * the failing boot stage.
 */
object CrashReporter {
    private const val CRASH_FILE = "last-crash.txt"
    private const val MARKS_FILE = "boot-marks.txt"

    /** Install the process-wide uncaught-exception writer. Chain to the
     *  previous handler afterwards so crash reporting behavior is unchanged. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrash(appContext, thread, throwable)
            } catch (_: Throwable) {
                /* NEVER block the real crash path */
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val text = buildString {
            append("time=").append(System.currentTimeMillis()).append('\n')
            append("thread=").append(thread.name).append('\n')
            append("version=1.5.2").append('\n')
            append("---stack---").append('\n')
            append(sw.toString())
            // boot breadcrumbs — huge diagnostic value, tiny file
            append("---boot-marks---").append('\n')
            try {
                append(File(context.filesDir, MARKS_FILE).readText())
            } catch (_: Throwable) {
            }
        }
        File(context.filesDir, CRASH_FILE).writeText(text)
    }

    fun readCrash(context: Context): String =
        try {
            File(context.filesDir, CRASH_FILE).readText()
        } catch (_: Throwable) {
            ""
        }

    fun clearCrash(context: Context) {
        try {
            File(context.filesDir, CRASH_FILE).delete()
        } catch (_: Throwable) {
        }
    }

    fun resetMarks(context: Context) {
        try {
            File(context.filesDir, MARKS_FILE).writeText("")
        } catch (_: Throwable) {
        }
    }

    fun mark(context: Context, label: String) {
        try {
            val f = File(context.filesDir, MARKS_FILE)
            f.appendText("${System.currentTimeMillis()} $label\n")
            // keep the breadcrumb file tiny — only recent lines matter
            if (f.length() > 8192) {
                val lines = f.readLines().takeLast(60)
                f.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
            }
        } catch (_: Throwable) {
        }
    }
}
