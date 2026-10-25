package com.callmony

import android.app.Application
import android.content.res.Configuration
import com.callmony.lib.Diag
import com.callmony.lib.DiagHooks
import com.callmony.platform.Platform
import com.callmony.ui.kit.decodeLogoEagerly

class CallmonyApplication : Application() {
    override fun onCreate() {
        // v1.6.20 — the diagnostics must be armed BEFORE anything else can
        // freeze or crash: this first line is also the first boot phase.
        Diag.mark("app.init")
        super.onCreate()
        // v1.6.20 — the crash firewall must be armed BEFORE anything else in
        // this process can crash, and the ANR watchdog before anything can
        // freeze ("show the problem in the app when it is trying to close").
        // Skips itself in the :diag diagnostics process and under Robolectric.
        DiagHooks.install(this)
        AndroidPlatform.init(this)
        if (!DiagHooks.isDiagProcess(this)) {
            // v1.6.18 — all persistence (JSON serialize + mirrored commit) moves
            // onto the single-writer IO queue; the MAIN thread never touches the
            // disk again. This is the freeze/lag/ANR ("app closes itself") fix.
            com.callmony.lib.Persist.enableAsync()
            // v1.6.15: decode the logo on a background thread at process start —
            // the first composed frame (the splash) already carries the real
            // bitmap, so the fallback tile never flashes (read as "old logo").
            decodeLogoEagerly()
        }
        registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {
                ActivityRef.activity = activity
            }

            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityResumed(activity: android.app.Activity) {
                ActivityRef.activity = activity
                // v1.6.20 — the ANR watchdog only watches while the app is on screen
                DiagHooks.appForeground = true
            }

            override fun onActivityPaused(activity: android.app.Activity) {
                DiagHooks.appForeground = false
            }

            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {
                if (ActivityRef.activity === activity) ActivityRef.activity = null
            }
        })
        Diag.mark("app.ready")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        (Platform.ui as? AndroidUi)?.onUiModeChanged()
    }
}
