package com.callmony

import android.content.ContentResolver
import android.os.Looper
import android.provider.CallLog
import androidx.test.core.app.ActivityScenario
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

/* v1.6.33 — THE POST-CALL REFRESH (user report: "in recents call cards i
   make a call from one of the call card then after the call ends it is not
   putting that call card fast enf at the top"). A ContentObserver on the
   system call log fires while the user is still on the in-call screen and a
   debounced 1.2s incremental refresh has the ended call at the top of
   Recents before they return.

   This harness locks the LAUNCH-SAFETY side of that feature — the exact
   thing that made v1.6.30 fragile was the observer's coupling to the
   onCreate/setContent launch window (registered there, delivering provider
   bursts onto the MAIN thread). The v1.6.33 design registers on the FIRST
   onResume, after the UI is up, on a dedicated background HandlerThread.
   Assertions:
     1. a full real boot still reaches the UI (the observer must not harm
        the launch path — the rollback's whole point);
     2. after boot the observer IS armed on the call-log URI;
     3. a provider write flows the whole chain — background onChange →
        debounced main post → the guarded refresh runs (BootTrace's phase
        lands on "recents.observerRefresh", which nothing overwrites:
        loadSystemCalls marks nothing). */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PostCallRefreshTest {

    @Test
    fun observerArmsAfterBootAndRefreshesOnCallLogWrite() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // 1. boot must complete (hydrate publishes; the phase leaves the
        //    early boot marks) — pump the main looper like BootDiagTest.
        var booted = false
        repeat(150) {
            if (booted) return@repeat
            shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
            Thread.sleep(60)
            val st = com.callmony.lib.AppStore.state
            val phase = com.callmony.lib.BootTrace.phase
            if (st.hydrated && phase != "boot" && phase != "activity.create" &&
                phase != "activity.content" && phase != "store.read"
            ) {
                booted = true
            }
        }
        check(booted) { "boot never completed — observer harmed the launch path?" }

        // 2. the observer must be armed AFTER the first onResume (never in
        //    onCreate — the v1.6.30 lesson). Registration is idempotent and
        //    runs on the main looper, so by now it must be present.
        var registered = false
        scenario.onActivity { activity ->
            val observers: Collection<*> =
                shadowOf(activity.contentResolver).getContentObservers(CallLog.Calls.CONTENT_URI)
            registered = observers.isNotEmpty()
        }
        check(registered) { "call-log observer never armed after first resume" }

        // 3. a provider write must flow through the whole chain: the
        //    background HandlerThread's onChange posts the debounced refresh
        //    onto the MAIN handler; advance the main looper past the 1.2s
        //    debounce and the guarded refresh must run (hydrated is true).
        var resolver: ContentResolver? = null
        scenario.onActivity { resolver = it.contentResolver }
        check(resolver != null) { "no content resolver from activity" }
        resolver!!.notifyChange(CallLog.Calls.CONTENT_URI, null)
        // give the real background HandlerThread a beat to receive onChange
        Thread.sleep(300)
        var refreshed = false
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idleFor(250, TimeUnit.MILLISECONDS)
            Thread.sleep(60)
            if (com.callmony.lib.BootTrace.phase == "recents.observerRefresh") refreshed = true
        }
        check(refreshed) {
            "provider write never reached the debounced refresh (phase=" +
                com.callmony.lib.BootTrace.phase + ")"
        }

        scenario.close()
    }
}
