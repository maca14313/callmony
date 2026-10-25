package com.callmony

import android.app.Application
import android.content.res.Configuration
import com.callmony.platform.Platform
import com.callmony.ui.kit.decodeLogoEagerly

class CallmonyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidPlatform.init(this)
        // v1.6.18 — all persistence (JSON serialize + mirrored commit) moves
        // onto the single-writer IO queue; the MAIN thread never touches the
        // disk again. This is the freeze/lag/ANR ("app closes itself") fix.
        com.callmony.lib.Persist.enableAsync()
        // v1.6.15: decode the logo on a background thread at process start —
        // the first composed frame (the splash) already carries the real
        // bitmap, so the fallback tile never flashes (read as "old logo").
        decodeLogoEagerly()
        registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {
                ActivityRef.activity = activity
            }

            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityResumed(activity: android.app.Activity) {
                ActivityRef.activity = activity
            }
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {
                if (ActivityRef.activity === activity) ActivityRef.activity = null
            }
        })
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        (Platform.ui as? AndroidUi)?.onUiModeChanged()
    }
}
