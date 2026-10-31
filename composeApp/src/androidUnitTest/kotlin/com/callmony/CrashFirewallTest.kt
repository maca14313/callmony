package com.callmony

import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/* v1.6.43 — regression battery for THE CRASH FIREWALL (the user's order:
   "still crashing … if it continues to crash make it to log the error and
   automatically copy it to clipboard").

   Pinned behaviour:
     1. captureReport → the report lands in crash-last.txt, the history log
        grows, the pending marker exists, and the CLIPBOARD holds the exact
        report (the automatic copy the user asked for).
     2. consumePendingReport → returns the report exactly once and clears
        the marker (the next-boot hand-over in MainActivity).
     3. copyPendingReport → re-copies to the clipboard and returns it.
     4. install() is a NO-OP under Robolectric (the real Application boots
        in the harness; the watchdog must not post into the test looper and
        the default uncaught handler must stay untouched).

   The capture funnel is driven directly (initForCapture + captureReport)
   — exactly the code the installed handler runs on a real crash. */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CrashFirewallTest {

    private lateinit var ctx: Context

    @Before
    fun fresh() {
        ctx = ApplicationProvider.getApplicationContext()
        // rebind (NOT initForCapture): Robolectric builds a fresh Application
        // + filesDir PER TEST METHOD while CrashFirewall is a JVM singleton —
        // the funnel must write into THIS method's app or every test after
        // the first asserts against a dead app's directory
        CrashFirewall.rebindForTest(ctx)
        // start every case from a clean report slate
        File(ctx.filesDir, "crash-last.txt").delete()
        File(ctx.filesDir, "crash-history.log").delete()
        File(ctx.filesDir, "crash-pending.marker").delete()
    }

    private fun clipboardText(): String? {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.getItemAt(0)?.text?.toString()
    }

    /* 1 — the full funnel: file + history + marker + CLIPBOARD */
    @Test
    fun capturePutsTheReportOnTheClipboardAndInFiles() {
        val stack = RuntimeException("kaboom-1643").stackTraceToString()
        CrashFirewall.captureReport("UNCAUGHT EXCEPTION", Thread.currentThread(), stack)

        val last = File(ctx.filesDir, "crash-last.txt")
        assertTrue("crash-last.txt must exist", last.exists())
        val text = last.readText()
        assertTrue("the report carries the exception", text.contains("kaboom-1643"))
        assertTrue("the report carries its kind", text.contains("UNCAUGHT EXCEPTION"))
        assertTrue("the report carries the version", text.contains("1.6"))
        assertTrue("the report carries a stack frame", text.contains("at "))
        assertTrue("the history log grew", File(ctx.filesDir, "crash-history.log").exists())
        assertTrue("the pending marker exists", CrashFirewall.hasPendingReport(ctx))

        // THE USER'S ASK — the error is automatically on the clipboard
        val clip = clipboardText()
        assertNotNull("the clipboard must hold the report", clip)
        assertTrue("the clipboard report is the full report", clip!!.contains("kaboom-1643"))
        assertTrue("the clipboard report carries the trace", clip.contains("CrashFirewallTest"))
    }

    /* 2 — the pending marker hands the report over exactly once */
    @Test
    fun pendingReportIsConsumedExactlyOnce() {
        assertFalse(CrashFirewall.hasPendingReport(ctx))
        assertNull(CrashFirewall.consumePendingReport(ctx))

        CrashFirewall.captureReport(
            "MAIN THREAD BLOCKED for 8000ms (lag/ANR)",
            Thread.currentThread(),
            RuntimeException("stuck-1643").stackTraceToString(),
        )
        assertTrue(CrashFirewall.hasPendingReport(ctx))
        val once = CrashFirewall.consumePendingReport(ctx)
        assertNotNull(once)
        assertTrue(once!!.contains("stuck-1643"))
        assertTrue(once.contains("MAIN THREAD BLOCKED"))
        // consumed → gone
        assertNull(CrashFirewall.consumePendingReport(ctx))
        assertFalse(CrashFirewall.hasPendingReport(ctx))
    }

    /* 3 — the boot-time hand-over re-copies to the clipboard */
    @Test
    fun copyPendingReportCopiesToClipboardAgain() {
        CrashFirewall.captureReport(
            "UNCAUGHT EXCEPTION",
            Thread.currentThread(),
            RuntimeException("handover-1643").stackTraceToString(),
        )
        val report = CrashFirewall.copyPendingReport(ctx)
        assertNotNull(report)
        assertTrue(report!!.contains("handover-1643"))
        val clip = clipboardText()
        assertNotNull(clip)
        assertTrue(clip!!.contains("handover-1643"))
        // the marker is gone — the next boot stays quiet
        assertNull(CrashFirewall.copyPendingReport(ctx))
    }

    /* 4 — install() must not touch the harness: no handler swap, no
       watchdog thread posting into the Robolectric looper */
    @Test
    fun installIsANoOpUnderRobolectric() {
        val before = Thread.getDefaultUncaughtExceptionHandler()
        CrashFirewall.install(ctx)
        assertEquals(
            "the default uncaught handler must stay untouched in the harness",
            before,
            Thread.getDefaultUncaughtExceptionHandler(),
        )
        val watchdog = Thread.getAllStackTraces().keys.count { it.name == "callmony-anr-watchdog" }
        assertEquals("no watchdog thread in the harness", 0, watchdog)
    }

    /* 5 — the watchdog thresholds are what the docs promise */
    @Test
    fun watchdogThresholds() {
        assertEquals(6000L, CrashFirewall.BLOCK_THRESHOLD_MS)
        assertEquals(30000L, CrashFirewall.DUMP_COOLDOWN_MS)
    }
}
