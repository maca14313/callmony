package com.callmony.lib

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.callmony.diag.DiagActivity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

/* v1.6.20 — Android side of the in-app freeze/crash diagnostics.
   See commonMain lib/Diag.kt for the design. */

/* ------------------------------------------------------------------ */
/* File-backed DiagStorage actual — plain Properties files under
   filesDir/diag, deliberately OUTSIDE the app's Persist queue: a frozen
   or crashing app must never depend on its own (possibly deadlocked)
   persistence plumbing to save the report. Thread-safe: the watchdog
   thread, a crashing thread and hydrate all touch it. */
/* ------------------------------------------------------------------ */

private class FileDiagStorage(private val ctx: Context) : DiagStorage {
    private val lock = Any()
    private fun dir(): File = File(ctx.filesDir, "diag").apply { mkdirs() }
    private fun fileFor(id: Long) = File(dir(), "%019d.properties".format(id))

    override fun save(report: DiagReport) {
        synchronized(lock) {
            try {
                val p = Properties()
                p.setProperty("id", report.id.toString())
                p.setProperty("kind", report.kind)
                p.setProperty("seen", if (report.seen) "1" else "0")
                p.setProperty("phase", report.phase)
                p.setProperty("headline", report.headline)
                p.setProperty("stack", report.stack)
                p.setProperty("detail", report.detail)
                FileOutputStream(fileFor(report.id)).use { p.store(it, null) }
                // keep the newest 12 — old reports never grow the dir
                val files = dir().listFiles { f -> f.name.endsWith(".properties") }
                    ?.sortedBy { it.name } ?: emptyList()
                files.dropLast(12).forEach { it.delete() }
            } catch (_: Throwable) { }
        }
    }

    override fun all(): List<DiagReport> {
        synchronized(lock) {
            return try {
                dir().listFiles { f -> f.name.endsWith(".properties") }
                    ?.sortedByDescending { it.name }
                    ?.mapNotNull { f ->
                        try {
                            val p = Properties()
                            FileInputStream(f).use { p.load(it) }
                            DiagReport(
                                id = p.getProperty("id")?.toLongOrNull() ?: return@mapNotNull null,
                                kind = p.getProperty("kind") ?: "freeze",
                                seen = p.getProperty("seen") == "1",
                                phase = p.getProperty("phase") ?: "?",
                                headline = p.getProperty("headline") ?: "",
                                stack = p.getProperty("stack") ?: "",
                                detail = p.getProperty("detail") ?: "",
                            )
                        } catch (_: Throwable) { null }
                    } ?: emptyList()
            } catch (_: Throwable) { emptyList() }
        }
    }

    override fun markSeen(id: Long) {
        synchronized(lock) {
            try {
                val f = fileFor(id)
                if (!f.exists()) return
                val p = Properties()
                FileInputStream(f).use { p.load(it) }
                p.setProperty("seen", "1")
                FileOutputStream(f).use { p.store(it, null) }
            } catch (_: Throwable) { }
        }
    }

    override fun clear() {
        synchronized(lock) {
            try { dir().listFiles()?.forEach { it.delete() } } catch (_: Throwable) { }
        }
    }
}

actual fun createDiagStorage(): DiagStorage = FileDiagStorage(PlatformHolder.appContext)

/* The storage needs a context; the Application sets this before install(). */
internal object PlatformHolder {
    @Volatile lateinit var appContext: Context
}

/* ------------------------------------------------------------------ */
/* Capture helpers — build a full DiagReport from the CURRENT state. */
/* ------------------------------------------------------------------ */

internal fun mainThreadStackText(maxFrames: Int = 60): String = try {
    val t = Looper.getMainLooper().thread
    if (t == null) "<main thread unavailable>"
    else buildString {
        appendLine("Thread: ${t.name} (main)")
        t.stackTrace.take(maxFrames).forEach { appendLine("  at $it") }
    }
} catch (_: Throwable) { "<stack capture failed>" }

internal fun otherThreadsBriefText(): String = try {
    Thread.getAllStackTraces().entries
        .filter { it.key !== Looper.getMainLooper().thread }
        .sortedByDescending { it.value.size }
        .take(6)
        .joinToString("\n") { (t, st) ->
            "Thread: ${t.name} (${t.state})\n" + st.take(4).joinToString("\n") { "  at $it" }
        }
} catch (_: Throwable) { "" }

internal fun deviceInfoText(ctx: Context): String = buildString {
    try {
        appendLine("device=${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
        val pi = ctx.packageManager?.getPackageInfo(ctx.packageName, 0)
        val vc = if (Build.VERSION.SDK_INT >= 28) pi?.longVersionCode else @Suppress("DEPRECATION") pi?.versionCode?.toLong()
        appendLine("app=v${pi?.versionName} ($vc)")
    } catch (_: Throwable) { appendLine("device=<unavailable>") }
}

internal fun captureFreezeReport(ctx: Context, headline: String): DiagReport = DiagReport(
    id = System.currentTimeMillis(),
    kind = "freeze",
    phase = Diag.phase,
    headline = headline,
    stack = mainThreadStackText() + "\n" + otherThreadsBriefText(),
    detail = Diag.snapshot() + "\n" + deviceInfoText(ctx),
)

internal fun captureCrashReport(ctx: Context, t: Thread, e: Throwable): DiagReport = DiagReport(
    id = System.currentTimeMillis(),
    kind = "crash",
    phase = Diag.phase,
    headline = "${e.javaClass.simpleName}: ${e.message ?: "(no message)"} on ${t.name}",
    stack = buildString {
        appendLine(e.stackTraceToString())
        appendLine()
        append(mainThreadStackText(40))
    },
    detail = Diag.snapshot() + "\n" + deviceInfoText(ctx),
)

/* ------------------------------------------------------------------ */
/* DiagHooks — installed once from CallmonyApplication.onCreate of the
   MAIN process (the :diag diagnostics process stays lean: no watchdog,
   no firewall — it must be able to show the problem even when the main
   process is frozen or dying). Robolectric is excluded so unit tests
   keep the exact pre-v1.6.20 behavior. */
/* ------------------------------------------------------------------ */

object DiagHooks {
    /** mirrored from activity lifecycle — the watchdog only runs while the
     *  app is on screen (battery + no background-activity-start surprises) */
    @Volatile var appForeground: Boolean = true

    /** set when the crash firewall is mid-handling: the watchdog stops */
    @Volatile var crashInFlight: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var watchdog: AnrWatchdog? = null

    fun isDiagProcess(app: Application): Boolean = try {
        // /proc/self/cmdline works on every API level (Application.getProcessName()
        // is API 28+ and didn't resolve against this compile target).
        val name = File("/proc/self/cmdline").readText().trimEnd('\u0000')
        name.endsWith(":diag")
    } catch (_: Throwable) { false }

    fun isRobolectric(): Boolean = try {
        Build.FINGERPRINT.startsWith("robolectric")
    } catch (_: Throwable) { false }

    fun install(app: Application) {
        PlatformHolder.appContext = app.applicationContext
        if (isDiagProcess(app)) return           // diagnostics process: stay lean
        if (isRobolectric()) return              // unit tests: default behavior
        try { Diag.storage = FileDiagStorage(app.applicationContext) } catch (_: Throwable) {}

        // capture hook: persist happened in Diag.record — now surface the
        // problem ON SCREEN. The freeze case also flips the live Compose
        // state, so a recovered app shows the in-app banner right away.
        Diag.captureHook = { report ->
            if (report.kind == "freeze") {
                try { Diag.live = report } catch (_: Throwable) {}
            }
            if (appForeground && !crashInFlight) fireDiagScreen(app, report)
        }

        // ANR watchdog — daemon thread, background priority
        watchdog = AnrWatchdog(app.applicationContext, mainHandler).also { it.start() }

        // crash firewall — chained in front of the platform handler
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(CrashFirewall(app.applicationContext, prev))
    }

    fun fireDiagScreen(ctx: Context, report: DiagReport) {
        try {
            val intent = Intent(ctx, DiagActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                .putExtra("kind", report.kind)
                .putExtra("text", Diag.reportText(report))
            ctx.startActivity(intent)
        } catch (_: Throwable) { /* background-start refusal etc. — the report
                                    is persisted and shows at next launch */ }
    }
}

/* ------------------------------------------------------------------ */
/* AnrWatchdog — "show the problem when the app is trying to close".
   A daemon thread posts a heartbeat to the main handler every second:

     • heartbeat never runs for 5 s  → HARD blockage (main stuck inside
       one message: binder call, deadlock, tight loop) — capture NOW.
     • heartbeat runs but ≥ 4 s late → SATURATION (main returns to the
       looper between messages but every slot is eaten — the v1.6.18
       forever-spinner class; the captured stack shows the culprit).

   On detection the main-thread stack + boot phase + app snapshot are
   persisted and the :diag-process DiagActivity pops over the frozen
   app with the problem. All logic lives in the pure BlockageDetector
   (unit-tested); this class only owns the thread + handler. */
/* ------------------------------------------------------------------ */

internal class AnrWatchdog(
    private val ctx: Context,
    private val main: Handler,
) : Thread("callmony-anr-watchdog") {
    private val det = BlockageDetector(latencyThresholdMs = 4_000, silentThresholdMs = 5_000)
    @Volatile private var running = true

    private val heartbeat = Runnable {
        val lat = det.onRan(SystemClock.elapsedRealtime())
        if (lat != null) onProblem("Main thread saturated — a posted task ran $lat ms late (the UI thread is busy every frame)")
    }

    init { isDaemon = true }

    override fun run() {
        try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) } catch (_: Throwable) {}
        while (running) {
            try {
                val active = DiagHooks.appForeground && !DiagHooks.crashInFlight
                if (active) {
                    val hard = det.hardBlockageMs(SystemClock.elapsedRealtime())
                    if (hard != null) {
                        onProblem("Main thread blocked — no progress for $hard ms (frozen at phase: ${Diag.phase})")
                    } else {
                        det.onPosted(SystemClock.elapsedRealtime())
                        main.post(heartbeat)
                    }
                }
                sleep(1_000)
            } catch (_: InterruptedException) { return
            } catch (_: Throwable) { try { sleep(1_000) } catch (_: Throwable) { return } }
        }
    }

    private fun onProblem(headline: String) {
        if (!det.shouldReport(SystemClock.elapsedRealtime())) return
        try {
            val report = captureFreezeReport(ctx, headline)
            Diag.record(report)
        } catch (_: Throwable) { }
    }
}

/* ------------------------------------------------------------------ */
/* CrashFirewall — uncaught exceptions on ANY thread: capture the full
   crash (exception + main-thread stack), persist it, pop the :diag
   diagnostics screen with the problem, give it a moment to appear,
   then hand over to the platform handler so the process still dies
   cleanly (no behavior change beyond the report). */
/* ------------------------------------------------------------------ */

internal class CrashFirewall(
    private val ctx: Context,
    private val prev: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(t: Thread, e: Throwable) {
        val first = !DiagHooks.crashInFlight
        if (!first) { prev?.uncaughtException(t, e); return }
        DiagHooks.crashInFlight = true
        try {
            val report = captureCrashReport(ctx, t, e)
            Diag.record(report)          // persists + fires the :diag screen
            Thread.sleep(2_600)          // let the diagnostics screen come up
        } catch (_: Throwable) { }
        prev?.uncaughtException(t, e)
    }
}
