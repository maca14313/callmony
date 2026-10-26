package com.callmony

import android.os.Looper
import androidx.test.core.app.ActivityScenario
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

/* v1.6.19 diagnostic — the v1.6.18 Robolectric boot was killed at 570 s
   "while booting" and the real device ANRs at the welcome screen. This
   harness boots the REAL app and, from a watchman thread, dumps the live
   app state + the main thread's stack so the exact hang location shows. */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BootDiagTest {

    private fun dumpThreads(reason: String) {
        println("=== THREAD DUMP: $reason ===")
        Thread.getAllStackTraces().entries
            .sortedBy { it.key.name }
            .forEach { (thread, stack) ->
                println("--- ${thread.name} (${thread.state}) ---")
                stack.take(60).forEach { println("    $it") }
            }
        println("=== END THREAD DUMP ===")
    }

    @Test
    fun bootWithWatchman() {
        val watchman = Thread {
            try { Thread.sleep(12_000) } catch (_: InterruptedException) {}
            repeat(12) { n ->
                val ui = com.callmony.lib.UiStore
                val app = com.callmony.lib.AppStore.state
                println("=== STATE $n: alarmQueue=${ui.alarmQueue.size} reminders=${app.reminders.size} " +
                    "onboarded=${app.settings.onboarded} hydrated=${app.hydrated} tab=${ui.tab} " +
                    "keypad=${ui.keypadOpen} more=${ui.moreOpen} overlay=${ui.overlayStack.size} " +
                    "hostSheets=${com.callmony.ui.kit.HostSheets.stack.size} ===")
                val entry = Thread.getAllStackTraces().entries
                    .firstOrNull { it.key.name.contains("Main") }
                if (entry != null) {
                    println("=== MAIN SNAPSHOT $n (${entry.key.state}) ===")
                    entry.value.take(70).forEach { f -> println("    $f") }
                }
                try { Thread.sleep(1200) } catch (_: InterruptedException) {}
            }
            dumpThreads("boot stalled — full dump")
        }
        watchman.isDaemon = true
        watchman.start()

        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // Pump the main looper like StartupTest; each iteration prints a
        // heartbeat so we can see exactly how far boot got before stalling.
        repeat(150) { i ->
            shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
            Thread.sleep(100)
            val st = com.callmony.lib.AppStore.state
            println("=== pump $i hydrated=${st.hydrated} calls=${st.systemCalls.size} contacts=${st.deviceContacts.size} ===")
            if (i == 40) dumpThreads("mid-boot snapshot")
        }

        scenario.onActivity { activity ->
            check(!activity.isDestroyed) { "Activity destroyed during boot" }
        }
        scenario.close()
        // v1.6.20 — the boot-phase tracker must have advanced past the splash:
        // a fresh install ends on the onboarding welcome screen ("ui.onboarding"),
        // or settles to "ui.idle" once the 11 s boot chain finished.
        val phase = com.callmony.lib.BootTrace.phase
        println("=== DIAG PHASE AT BOOT END: $phase ===")
        check(phase == "ui.onboarding" || phase == "ui.idle" || phase == "ui.main") {
            "boot phase stuck at '$phase' — the app never reached its UI"
        }
        println("=== BOOT DIAG: completed all 150 pumps without hanging ===")
    }
}
