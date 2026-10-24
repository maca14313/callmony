package com.callmony

import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

/* Startup smoke test — reproduces the "app closes itself on open" crash in the
   JVM so the full stack trace is visible. Runs the REAL Application, the REAL
   manifest, the REAL MainActivity and the full Compose boot pipeline
   (hydrate -> splash -> shell -> Recents screen) under Robolectric. */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StartupTest {

    private fun bootAndVerify() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // Pump the main looper for ~12s of simulated time (with real wall-clock
        // pauses so Dispatchers.Default background work lands) — this carries the
        // app through: hydrate -> splash frame -> shell frame -> boot effect
        // (loadSystemCalls + refreshDeviceContacts + ensureChannel + rearm).
        repeat(60) {
            shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
            Thread.sleep(100)
        }

        // If the activity died (crash), onActivity throws / never runs.
        scenario.onActivity { activity ->
            check(!activity.isFinishing) { "Activity finished unexpectedly (crashed?)" }
            check(!activity.isDestroyed) { "Activity destroyed unexpectedly (crashed?)" }
        }

        // Prove we got PAST the splash into the real shell (hydrated == true).
        val hydrated = com.callmony.lib.AppStore.state.hydrated
        println("=== STARTUP TEST: hydrated=$hydrated, contacts=${com.callmony.lib.AppStore.state.deviceContacts.size}, systemCalls=${com.callmony.lib.AppStore.state.systemCalls.size} ===")
        check(hydrated) { "Store never hydrated — boot stalled" }

        // Give Robolectric one more full drain, then verify the activity is
        // still alive in steady-state (post-boot frame work, alarm watcher...).
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
            Thread.sleep(50)
        }
        scenario.onActivity { activity ->
            check(!activity.isDestroyed) { "Activity destroyed during steady-state" }
        }

        scenario.close()
    }

    @Test
    fun appBootsWithoutCrashing() = bootAndVerify()

    @Test
    @Config(sdk = [34], qualifiers = "night")
    fun appBootsInDarkMode() = bootAndVerify()

    @Test
    @Config(sdk = [34], qualifiers = "ar")
    fun appBootsArabicRtl() = bootAndVerify()

    @Test
    @Config(sdk = [34], qualifiers = "am")
    fun appBootsAmharicLocale() = bootAndVerify()

    /** The new logo must actually load + decode from the bundled compose resources. */
    @Test
    fun logoResourceLoadsAndDecodes() {
        val bytes = kotlinx.coroutines.runBlocking {
            com.callmony.ui.kit.readLogoPngBytes()
        }
        check(bytes != null && bytes.isNotEmpty()) { "logo.png bytes unavailable" }
        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        check(bmp != null && bmp.width == 640 && bmp.height == 640) { "unexpected logo decode: $bmp" }
        println("=== LOGO TEST: ${bmp.width}x${bmp.height} decoded OK ===")
    }
}
