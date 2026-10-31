package com.callmony

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* v1.6.43 — THE CRASH FIREWALL, on the user's direct order:
   "still crashing the app is crashing, when toggle to number mode and start
   typing it starts to lag, and finally the app closes. if it continues to
   crash make it to log the error and automatically copy it to clipboard".

   The v1.6.42 round proved the Compose layer innocent in the harness (300
   rapid keys, flat 2 ms each) and fixed every remaining real-device suspect
   in the native-field interop — yet the user still sees the lag-then-close
   on their device. The only honest next step is GROUND TRUTH: whatever
   kills the process must leave its fingerprint where the user can hand it
   over with one long-press → Paste.

   Two capture paths, one funnel:

   1. UNCAUGHT EXCEPTIONS — Thread.setDefaultUncaughtExceptionHandler:
      the report goes to logcat (ERROR), to files/crash-last.txt, appended
      to files/crash-history.log (capped), COPIED TO THE CLIPBOARD
      synchronously (the crashing thread is still alive inside the handler —
      posting to the main looper would never run when the crash itself is on
      main), and a crash-pending marker is left for the next boot. The
      previous handler is then chained so the system's own crash processing
      (dialog, tombstone, process death) is completely unchanged.

   2. THE ANR WATCHDOG — "it starts to LAG, and finally the app closes" is
      the classic progressive-main-thread-blockage signature (input events
      time out → the system kills the app), and an ANR is NOT an exception:
      no uncaught-handler ever fires. A tiny daemon thread heartbeats the
      main thread every 1.5 s; if the beat is missed for 6+ s while the app
      is FOREGROUND, the MAIN THREAD'S LIVE STACK is captured through the
      same funnel (labelled MAIN THREAD BLOCKED) — BEFORE the system ANR
      flow runs, so the clipboard already holds the evidence when the
      "app isn't responding" dialog appears. Dumps are rate-limited (30 s
      cooldown) and never fire while the app is in the background (a cached
      process may be frozen by the system — that is not an ANR).

   The funnel is exception-proof by construction: a crash reporter that can
   throw is worse than no reporter. Robolectric boots the real Application,
   so install() is a no-op under the harness (the capture path itself stays
   directly testable — see CrashFirewallTest). */

object CrashFirewall {

    private const val TAG = "CallmonyCrash"
    private const val LAST = "crash-last.txt"
    private const val HISTORY = "crash-history.log"
    private const val PENDING = "crash-pending.marker"
    private const val MAX_HISTORY_BYTES = 256 * 1024

    /** 6 s without a main-thread heartbeat while foreground = blocked. */
    const val BLOCK_THRESHOLD_MS = 6000L

    /** Cooldown between two watchdog dumps of the same blockage. */
    const val DUMP_COOLDOWN_MS = 30000L

    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    /** last wall-clock time a heartbeat task actually RAN on the main looper. */
    @Volatile private var lastBeatAtMs = 0L
    @Volatile private var lastDumpAt = 0L
    @Volatile private var resumed = 0
    @Volatile private var bootedAtMs = 0L

    /* ---------------- install ---------------- */

    fun install(context: Context) {
        // Robolectric: the real Application boots in the harness; a watchdog
        // posting to the test looper would only pollute test state. The
        // capture path stays unit-testable directly.
        if (Build.FINGERPRINT.contains("robolectric", ignoreCase = true)) return
        initForCapture(context)
        val app = context.applicationContext as? android.app.Application
            ?: context as? android.app.Application
        bootedAtMs = System.currentTimeMillis()
        lastBeatAtMs = bootedAtMs
        app?.registerActivityLifecycleCallbacks(object :
            android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
            override fun onActivityStarted(a: android.app.Activity) {}
            override fun onActivityResumed(a: android.app.Activity) { resumed++ }
            override fun onActivityPaused(a: android.app.Activity) { resumed-- }
            override fun onActivityStopped(a: android.app.Activity) {}
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) {}
        })
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                captureReport("UNCAUGHT EXCEPTION", thread, throwable.stackTraceToString())
            } catch (_: Throwable) {
            }
            // chain: the system's crash processing continues untouched
            previous?.uncaughtException(thread, throwable)
        }
        val watcher = Thread({
            while (true) {
                try {
                    Thread.sleep(1500)
                    mainHandler.post { lastBeatAtMs = System.currentTimeMillis() }
                    val now = System.currentTimeMillis()
                    if (resumed > 0 && appContext != null &&
                        now - lastBeatAtMs > BLOCK_THRESHOLD_MS &&
                        now - lastDumpAt > DUMP_COOLDOWN_MS
                    ) {
                        lastDumpAt = now
                        val main = Looper.getMainLooper().thread
                        captureReport(
                            "MAIN THREAD BLOCKED for ${now - lastBeatAtMs}ms (lag/ANR)",
                            main,
                            stackToString(main),
                        )
                    }
                } catch (_: Throwable) {
                    // the watchdog itself must never die
                }
            }
        }, "callmony-anr-watchdog")
        watcher.isDaemon = true
        watcher.priority = Thread.MIN_PRIORITY + 1
        watcher.start()
    }

    /** Sets the context the capture funnel writes through (idempotent) —
     *  also the test entry point for direct capture calls. */
    fun initForCapture(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    /** Test hook: Robolectric builds a FRESH Application + filesDir per
     *  test method while this object is a JVM singleton — the bound context
     *  must follow the current method's app or the funnel writes into a
     *  dead app's directory. No production caller. */
    fun rebindForTest(context: Context) {
        appContext = context.applicationContext
    }

    /* ---------------- capture funnel ---------------- */

    /** One report through every channel: logcat → file → clipboard → marker.
     *  Never throws. Safe to call from any thread (including a dying one). */
    fun captureReport(kind: String, thread: Thread?, stack: String) {
        val ctx = appContext ?: return
        val report = buildReport(kind, thread, stack)
        try { Log.e(TAG, report) } catch (_: Throwable) {}
        try {
            val last = File(ctx.filesDir, LAST)
            last.writeText(report)
        } catch (_: Throwable) {}
        try {
            val hist = File(ctx.filesDir, HISTORY)
            hist.appendText("\n\n$report")
            if (hist.length() > MAX_HISTORY_BYTES) {
                // keep the newest tail only
                val text = hist.readText()
                hist.writeText(text.substring(text.length - MAX_HISTORY_BYTES / 2))
            }
        } catch (_: Throwable) {}
        try { File(ctx.filesDir, PENDING).writeText("1") } catch (_: Throwable) {}
        copyToClipboard(ctx, report)
    }

    /** The clipboard copy — the user's explicit ask. Guarded: on some OEM
     *  builds clipboard writes from non-main threads are refused; if the
     *  main thread is alive it gets one posted retry. */
    private fun copyToClipboard(ctx: Context, text: String) {
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            cm.setPrimaryClip(ClipData.newPlainText("callmony crash report", text))
        } catch (_: Throwable) {
            try {
                mainHandler.post {
                    try {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("callmony crash report", text))
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}
        }
    }

    private fun buildReport(kind: String, thread: Thread?, stack: String): String {
        val ctx = appContext
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val version = try {
            val pi = ctx?.packageManager?.getPackageInfo(ctx.packageName, 0)
            "v${pi?.versionName ?: "?"} (${pi?.let {
                if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
            } ?: 0L})"
        } catch (_: Throwable) { "v?" }
        val capped = if (stack.length > 16000) stack.take(16000) + "\n… (truncated)" else stack
        return buildString {
            appendLine("──── Callmony crash report ────")
            appendLine("When: ${df.format(Date())}")
            appendLine("Kind: $kind")
            appendLine("Version: $version")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread?.name ?: "?"}")
            appendLine("Boot phase: ${com.callmony.lib.BootTrace.phase}")
            appendLine("Foreground: ${resumed > 0}")
            appendLine("Up for: ${if (bootedAtMs > 0) (System.currentTimeMillis() - bootedAtMs) / 1000 else 0}s")
            appendLine("──── trace ────")
            appendLine(capped)
        }
    }

    private fun stackToString(t: Thread): String {
        val sb = StringBuilder()
        for (e in t.stackTrace) sb.appendLine("    at $e")
        return sb.toString()
    }

    /* ---------------- next-boot retrieval ---------------- */

    /** True when a crash/freeze report was captured and never handed over.
     *  The next MainActivity boot turns it into a clipboard copy + toast so
     *  the user can paste the trace even if the crash-time copy failed. */
    fun hasPendingReport(ctx: Context): Boolean =
        try { File(ctx.filesDir, PENDING).exists() } catch (_: Throwable) { false }

    /** Reads the last report and clears the pending marker (null = none). */
    fun consumePendingReport(ctx: Context): String? {
        return try {
            val last = File(ctx.filesDir, LAST)
            if (!hasPendingReport(ctx) || !last.exists()) {
                File(ctx.filesDir, PENDING).delete()
                return null
            }
            File(ctx.filesDir, PENDING).delete()
            last.readText()
        } catch (_: Throwable) { null }
    }

    /** Boot-time hand-over: clipboard copy of the captured report. */
    fun copyPendingReport(ctx: Context): String? {
        initForCapture(ctx)
        val report = consumePendingReport(ctx) ?: return null
        copyToClipboard(ctx, report)
        return report
    }
}
