package com.callmony

import com.callmony.lib.BlockageDetector
import com.callmony.lib.Diag
import com.callmony.lib.DiagReport
import com.callmony.lib.MemoryDiagStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/* v1.6.20 — pure battery for the freeze/crash diagnostics:
     • BlockageDetector — the watchdog accounting (hard blockage, handler
       latency saturation, episode cooldown)
     • Diag — record / persist / unseen / dismiss flow + report text
     • snapshot — the app-state half of every report */

class DiagTest {

    /* ---------------- BlockageDetector ---------------- */

    @Test
    fun detectorHealthyLatencyIsNotAProblem() {
        val d = BlockageDetector(latencyThresholdMs = 4_000, silentThresholdMs = 5_000)
        d.onPosted(1_000)
        assertNull("a heartbeat that ran 100 ms late is healthy", d.onRan(1_100))
        assertNull("no hard blockage while ticks flow", d.hardBlockageMs(6_000))
    }

    @Test
    fun detectorCatchesHardBlockage() {
        val d = BlockageDetector(latencyThresholdMs = 4_000, silentThresholdMs = 5_000)
        d.onPosted(10_000)
        // main never runs the heartbeat — 5 s later this is a hard blockage
        assertNotNull("heartbeat silent ≥ threshold = hard blockage", d.hardBlockageMs(15_500))
        assertNull("below the threshold it is not", d.hardBlockageMs(14_900))
        assertEquals(5_500L, d.hardBlockageMs(15_500))
    }

    @Test
    fun detectorCatchesSaturation() {
        val d = BlockageDetector(latencyThresholdMs = 4_000, silentThresholdMs = 5_000)
        d.onPosted(0)
        // the heartbeat DID run — but 4.5 s after it was posted: the main
        // thread returns to the looper between messages yet every slot is
        // eaten (the v1.6.18 forever-spinner class of freeze)
        val lat = d.onRan(4_500)
        assertNotNull("late heartbeat = saturation", lat)
        assertEquals(4_500L, lat)
        // and once it ran, the hard-blockage check clears (it is not stuck)
        assertNull(d.hardBlockageMs(4_600))
    }

    @Test
    fun detectorCooldownAllowsOneReportPerEpisode() {
        val d = BlockageDetector(latencyThresholdMs = 4_000, silentThresholdMs = 5_000, cooldownMs = 60_000)
        d.onPosted(0)
        d.onRan(6_000) // saturation
        assertTrue("first detection reports", d.shouldReport(now = 6_000))
        assertFalse("same episode stays quiet (cooldown)", d.shouldReport(now = 20_000))
        assertTrue("a minute later a new episode reports", d.shouldReport(now = 70_000))
    }

    /* v1.6.23 — the pause/resume accounting. The three v1.6.22 “freeze”
       reports were false positives: the app sat PAUSED (permission dialog /
       backgrounded / screen off — onActivityPaused fires for all of them)
       for 5.8 s / 144 s / 340 s, and on return the watchdog read the gap
       since the last heartbeat RUN as blockage, with a healthy idle main
       thread in every sample. resume() must clear that gap. */

    @Test
    fun detectorResumeClearsThePausedGap() {
        val d = BlockageDetector(latencyThresholdMs = 4_000, silentThresholdMs = 5_000)
        d.onPosted(1_000)
        d.onRan(1_100)
        // the app is paused for 144 s (a v1.6.22 report) — surveillance
        // resumes; the gap must not read as a freeze
        d.resume()
        assertNull("the paused gap is not blockage", d.hardBlockageMs(145_000))
        // …and after a fresh post, a REAL stall is still caught
        d.onPosted(145_500)
        assertNotNull("a real stall after resume is still caught", d.hardBlockageMs(150_800))
        assertEquals(5_300L, d.hardBlockageMs(150_800))
    }

    @Test
    fun detectorResumeMeasuresFromTheFreshPostBeforeAnyRun() {
        // resume() with no run history at all: the first post after resume
        // becomes the baseline (postedOnce-but-never-ran path)
        val d = BlockageDetector(latencyThresholdMs = 4_000, silentThresholdMs = 5_000)
        d.onPosted(1_000) // pre-pause post, never ran
        d.resume()
        assertNull(d.hardBlockageMs(3_000))
        d.onPosted(10_000)
        assertNull("under the threshold from the FRESH post", d.hardBlockageMs(13_000))
        assertNotNull("over the threshold from the fresh post", d.hardBlockageMs(15_100))
    }

    @Test
    fun detectorResumeKeepsTheReportCooldown() {
        val d = BlockageDetector(latencyThresholdMs = 4_000, silentThresholdMs = 5_000, cooldownMs = 60_000)
        d.onPosted(0)
        assertTrue(d.shouldReport(now = 5_000))
        d.resume()
        assertTrue("cooldown window survives the resume", d.shouldReport(now = 65_000))
        assertFalse("and still suppresses within it", d.shouldReport(now = 66_000))
    }

    /* ---------------- Diag record / seen flow ---------------- */

    @Test
    fun diagRecordsPersistsAndSurfacesUnseen() {
        Diag.resetForTest()
        Diag.storage = MemoryDiagStorage()
        var hooked: DiagReport? = null
        Diag.captureHook = { hooked = it }

        val report = DiagReport(
            id = 123L, kind = "freeze", phase = "ui.onboarding",
            headline = "Main thread blocked — no progress for 6000 ms",
            stack = "Thread: main\n  at com.callmony.X(X.kt:1)", detail = "phase=ui.onboarding",
        )
        Diag.record(report)

        assertEquals("capture hook fired (→ :diag screen)", report.id, hooked?.id)
        assertEquals("stored", 1, Diag.reports.size)
        assertNotNull("unseen report surfaces in-app", Diag.unseen())
        assertEquals("ui.onboarding", Diag.unseen()!!.phase)

        Diag.dismiss(123L)
        assertNull("dismissed once → gone", Diag.unseen())
        assertTrue("seen flag persisted", Diag.reports.first().seen)

        Diag.clearAll()
        assertEquals("clear removes everything", 0, Diag.reports.size)
    }

    @Test
    fun diagLoadFromStorageRestoresNewestFirst() {
        Diag.resetForTest()
        val mem = MemoryDiagStorage()
        Diag.storage = mem
        mem.save(DiagReport(100, "freeze", "ui.splash", "a", "s", "d"))
        mem.save(DiagReport(200, "crash", "ui.idle", "b", "s2", "d2"))
        Diag.loadFromStorage()
        assertEquals(2, Diag.reports.size)
        assertEquals("newest first", 200L, Diag.reports.first().id)
        assertEquals("crash", Diag.reports.first().kind)
    }

    @Test
    fun diagLiveTakesPriorityOverPending() {
        Diag.resetForTest()
        Diag.storage = MemoryDiagStorage()
        // same hook shape DiagHooks.install installs on Android: freezes go
        // to the live Compose state (in-app banner) AND the :diag screen
        Diag.captureHook = { r -> if (r.kind == "freeze") Diag.live = r }
        Diag.record(DiagReport(1, "freeze", "ui.main", "pending-from-last-run", "s", "d"))
        Diag.record(DiagReport(2, "freeze", "ui.main", "live-this-session", "s", "d"))
        // the shell overlay prefers Diag.live (the fresh freeze) over the
        // unseen persisted one — both surface, newest context first
        assertEquals("live-this-session", Diag.live?.headline)
        assertNotNull(Diag.unseen())
    }

    /* ---------------- report text + snapshot ---------------- */

    @Test
    fun reportTextCarriesEverythingForTheDeveloper() {
        Diag.resetForTest()
        val r = DiagReport(
            id = 5L, kind = "crash", phase = "ui.onboarding",
            headline = "IllegalStateException: boom on main",
            stack = "java.lang.IllegalStateException: boom\n  at Foo(Bar.kt:9)",
            detail = "phase=ui.onboarding\nhydrated=true",
        )
        val text = Diag.reportText(r)
        assertTrue(text.contains("kind: crash"))
        assertTrue(text.contains("phase: ui.onboarding"))
        assertTrue(text.contains("IllegalStateException: boom"))
        assertTrue(text.contains("at Foo(Bar.kt:9)"))
        assertTrue(text.contains("hydrated=true"))
    }

    @Test
    fun snapshotCapturesLiveAppState() {
        Diag.resetForTest()
        Diag.mark("ui.onboarding")
        val snap = Diag.snapshot()
        assertTrue(snap.contains("phase=ui.onboarding"))
        assertTrue(snap.contains("onboarded="))
        assertTrue(snap.contains("perm.contacts="))
        assertTrue(snap.contains("tab="))
        assertTrue(snap.contains("overlays="))
    }
}
