package com.callmony.lib

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/* v1.6.20 — IN-APP FREEZE / CRASH DIAGNOSTICS.
   The app kept freezing on the reporting device while every boot harness in
   the sandbox ran clean, so instead of guessing at the cause this module
   makes the app SHOW the problem on screen the moment it happens:

     • Diag.phase — a live "where are we in the boot/UI" marker updated at
       every milestone (app.init → store.loaded → ui.onboarding …). Every
       report carries the phase it was captured in, so "it freezes at the
       welcome screen" becomes an exact, machine-observed answer.
     • AnrWatchdog (Android actual) — detects a hard main-thread blockage
       (posted heartbeat never runs) AND main-thread saturation (heartbeat
       runs, but seconds late — the v1.6.18 vsync-storm class of freeze),
       captures the main-thread stack + this snapshot, and pops the
       separate-process DiagActivity so the user SEES what is wrong even
       while the main process is frozen or being killed.
     • CrashFirewall (Android actual) — uncaught exceptions get the same
       treatment: report saved, problem shown, then the process dies.
     • If the app is ANR-killed anyway, the report is persisted and the
       next launch opens on "Callmony froze and was closed last time —
       here is exactly what was running".

   Everything below is pure common code (unit-testable); the watchdog,
   firewall and diagnostics activity live in androidMain. */

/** One captured problem. Immutable; persisted by [DiagStorage]. */
data class DiagReport(
    /** epoch ms at capture — doubles as the report id */
    val id: Long,
    /** "freeze" | "crash" */
    val kind: String,
    /** Diag.phase at capture, e.g. "ui.onboarding" */
    val phase: String,
    /** human one-liner, e.g. "Main thread blocked — no progress for 6.2 s" */
    val headline: String,
    /** the main-thread (or crash) stack, pre-trimmed */
    val stack: String,
    /** device info + live app-state snapshot */
    val detail: String,
    /** true once the user has seen this report (persisted) */
    val seen: Boolean = false,
)

/** Persistence for reports. Android: plain files in filesDir/diag (deliberately
 *  NOT the app's Persist queue — a frozen app must never deadlock its own
 *  diagnostics). iOS: Documents-dir files. Tests: in-memory. */
interface DiagStorage {
    fun save(report: DiagReport)
    /** newest-first */
    fun all(): List<DiagReport>
    fun markSeen(id: Long)
    fun clear()
}

/* NOTE: this in-memory storage is single-threaded (tests, previews, and
   the pre-install default). The Android/iOS actuals are the real stores —
   Kotlin/Native has no `synchronized`, so no lock here (see Persist.kt for
   the same commonMain constraint). */
internal class MemoryDiagStorage : DiagStorage {
    private val list = mutableListOf<DiagReport>()
    override fun save(report: DiagReport) {
        list.removeAll { it.id == report.id }
        list.add(report)
        while (list.size > 12) list.removeAt(0)
    }
    override fun all(): List<DiagReport> = list.sortedByDescending { it.id }
    override fun markSeen(id: Long) {
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) list[i] = list[i].copy(seen = true)
    }
    override fun clear() { list.clear() }
}

/** expect: androidMain / iosMain construct the platform file store. */
expect fun createDiagStorage(): DiagStorage

object Diag {
    /** current boot/UI phase — plain var (Kotlin/Native has no @Volatile;
       same constraint as Persist.kt). Written from any thread, read by the
     *  watchdog at capture time. Not Compose state (nothing renders it). */
    var phase: String = "boot"
        private set

    /** persisted reports (loaded at hydrate), newest first — Compose state */
    val reports = mutableStateListOf<DiagReport>()

    /** a freeze captured THIS session while the app recovered — Compose state;
     *  written from the watchdog thread (snapshot state is thread-safe). */
    var live by mutableStateOf<DiagReport?>(null)

    /** Settings → Diagnostics list screen (list mode of the overlay) */
    var listOpen by mutableStateOf(false)

    /** storage — swappable so unit tests can run fully in-memory */
    var storage: DiagStorage = MemoryDiagStorage()

    /** set by the Android actual: fires the :diag DiagActivity + live state.
     *  Called on whatever thread captured the problem — must never throw.
     *  (plain var — Kotlin/Native has no @Volatile) */
    var captureHook: ((DiagReport) -> Unit)? = null

    /** boot/UI milestone marker — call sites are one-liners, e.g.
     *  Diag.mark("ui.onboarding"). Never moves "backwards" in the same
     *  session except on explicit re-mark, which is fine. */
    fun mark(p: String) { phase = p }

    fun resetForTest() {
        phase = "boot"
        reports.clear()
        live = null
        listOpen = false
        storage = MemoryDiagStorage()
        captureHook = null
    }

    /** Called by the watchdog / crash firewall (any thread). Persists the
     *  report and notifies the hook. Safe to call while the main thread is
     *  frozen — this only touches background-safe state. */
    fun record(report: DiagReport) {
        try { storage.save(report) } catch (_: Throwable) {}
        try { reports.add(0, report) } catch (_: Throwable) {}
        try { captureHook?.invoke(report) } catch (_: Throwable) {}
    }

    /** hydrate-time load of everything persisted earlier */
    fun loadFromStorage() {
        try {
            val all = storage.all()
            reports.clear()
            reports.addAll(all)
        } catch (_: Throwable) {}
    }

    /** first persisted report the user has not dismissed yet */
    fun unseen(): DiagReport? = reports.firstOrNull { !it.seen }

    fun dismiss(id: Long) {
        try { storage.markSeen(id) } catch (_: Throwable) {}
        val i = reports.indexOfFirst { it.id == id }
        if (i >= 0) reports[i] = reports[i].copy(seen = true)
        if (live?.id == id) live = null
    }

    fun clearAll() {
        try { storage.clear() } catch (_: Throwable) {}
        reports.clear()
        live = null
    }

    /** live app-state snapshot — the "what did the app look like when it
     *  froze" half of the report (device info is appended by the platform). */
    fun snapshot(): String = buildString {
        appendLine("phase=${phase}")
        try {
            val s = AppStore.state
            appendLine("hydrated=${s.hydrated}")
            appendLine("onboarded=${s.settings.onboarded}")
            appendLine("theme=${s.settings.theme} lang=${s.settings.language}")
            appendLine("perm.contacts=${s.contactsPerm} perm.callLog=${s.callLogPerm} callLogBusy=${s.callLogBusy}")
            appendLine("deviceContacts=${s.deviceContacts.size} systemCalls=${s.systemCalls.size} contactX=${s.contactX.size}")
            appendLine("entries=${s.entries.size} reminders=${s.reminders.size} notes=${s.notes.size} lists=${s.lists.size}")
            appendLine("pin=${if (s.settings.pinHash != null) "set" else "none"} autolockMin=${s.settings.autolockMin}")
        } catch (_: Throwable) { appendLine("store=<unreadable>") }
        try {
            appendLine("tab=${UiStore.tab} keypadOpen=${UiStore.keypadOpen} moreOpen=${UiStore.moreOpen} locked=${UiStore.locked}")
            appendLine("overlays=${UiStore.overlayStack.joinToString(",") { it::class.simpleName ?: "?" }}")
            appendLine("alarms=${UiStore.alarmQueue.size}")
        } catch (_: Throwable) { appendLine("ui=<unreadable>") }
    }

    /** the full copyable text for a report (clipboard / diagnostics screen) */
    fun reportText(r: DiagReport): String = buildString {
        appendLine("Callmony diagnostics report")
        appendLine("kind: ${r.kind}")
        appendLine("phase: ${r.phase}")
        appendLine("problem: ${r.headline}")
        appendLine()
        appendLine(r.stack)
        appendLine()
        appendLine(r.detail)
    }
}

/* ------------------------------------------------------------------ */
/* BlockageDetector — the pure heart of the ANR watchdog. The Android
   actual owns the thread + Handler; this class only does the accounting
   so it is unit-testable in plain JVM tests.

   Two freeze shapes are detected:
     1. HARD blockage — a heartbeat posted to the main handler hasn't RUN
        for `silentThresholdMs` (main stuck inside one message: binder
        call, deadlock, tight loop that never returns to the looper).
     2. SATURATION — the heartbeat runs, but `latencyThresholdMs`+ after
        it was posted (main returns to the looper between messages but
        every slot is eaten — the v1.6.18 forever-spinner class, where an
        infinite animation recomposes heavy frames end-to-end).
   Reports fire at most once per `cooldownMs` episode. */
/* ------------------------------------------------------------------ */

class BlockageDetector(
    private val latencyThresholdMs: Long = 4_000,
    private val silentThresholdMs: Long = 5_000,
    private val cooldownMs: Long = 60_000,
) {
    var lastPostedAt = 0L; private set
    var lastRanAt = 0L; private set
    var lastLatencyMs = 0L; private set
    private var postedOnce = false
    private var ranOnce = false
    private var lastReportAt = Long.MIN_VALUE / 2   // epoch-independent "never reported"

    /** the watchdog thread posts a heartbeat */
    fun onPosted(now: Long) { lastPostedAt = now; postedOnce = true }

    /** the heartbeat ran on the main thread — returns the latency in ms if
     *  this run was late (saturation), else null. */
    fun onRan(now: Long): Long? {
        lastLatencyMs = if (postedOnce) now - lastPostedAt else 0L
        lastRanAt = now
        ranOnce = true
        return if (lastLatencyMs >= latencyThresholdMs) lastLatencyMs else null
    }

    /** checked from the watchdog thread between posts — non-null when no
     *  heartbeat has RUN for `silentThresholdMs` (hard blockage). Measured
     *  from the last RUN (or the first post, before any run) so a heartbeat
     *  that ran promptly never counts as blocked. */
    fun hardBlockageMs(now: Long): Long? {
        val reference = when {
            ranOnce -> lastRanAt
            postedOnce -> lastPostedAt
            else -> return null
        }
        return if (now - reference >= silentThresholdMs) now - reference else null
    }

    /** episode cooldown — true at most once per cooldown window */
    fun shouldReport(now: Long): Boolean {
        if (now - lastReportAt < cooldownMs) return false
        lastReportAt = now
        return true
    }
}
