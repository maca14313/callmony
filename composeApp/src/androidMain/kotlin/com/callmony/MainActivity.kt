package com.callmony

import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.callmony.lib.AppStore
import com.callmony.ui.CallmonyApp

class MainActivity : ComponentActivity() {

    /* v1.6.16 — the launch window + Android-12+ system splash must carry the
       APP theme's canvas, not the SYSTEM day/night mode (the old behavior:
       app set to dark on a light-system phone flashed a white splash before
       the dark app — "match the bg of the splash screen to the theme").
       The persisted settings JSON is string-scanned for "theme" — cheaper
       than a full parse, and this must run before super.onCreate(). */
    private fun persistedThemeIsDark(): Boolean {
        return try {
            val prefs = getSharedPreferences("callmony-store", MODE_PRIVATE)
            val raw = prefs.getString("callmony.settings", null)
                ?: prefs.getString("callmony.settings~m", null)
            val m = Regex("\"theme\"\\s*:\\s*\"(light|dark|auto)\"").find(raw ?: "")
            val theme = m?.groupValues?.get(1)
            val sysDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            theme == "dark" || ((theme == null || theme == "auto") && sysDark)
        } catch (_: Exception) {
            false
        }
    }

    /* v1.6.28 — same cheap scan for the LANGUAGE (the splash tagline used to
       flash English for am/om/ar/ti users until hydrate landed). */
    private fun persistedLanguage(): String? {
        return try {
            val prefs = getSharedPreferences("callmony-store", MODE_PRIVATE)
            val raw = prefs.getString("callmony.settings", null)
                ?: prefs.getString("callmony.settings~m", null)
            Regex("\"language\"\\s*:\\s*\"(en|am|om|ar|ti)\"").find(raw ?: "")?.groupValues?.get(1)
        } catch (_: Exception) {
            null
        }
    }

    /* v1.6.33 — THE POST-CALL REFRESH, done LAUNCH-SAFE this time (user
       report: "in recents call cards i make a call from one of the call card
       then after the call ends it is not putting that call card fast enf at
       the top"). onResume's incremental sync only runs once the user is BACK
       in the app — the card landed a beat late. A ContentObserver on the
       system call log fires the moment the provider writes the ended call
       (while the user is still on the in-call screen), and the debounced
       1.2s refresh has the card already at the top of Recents when they
       return.

       WHY THIS IS NOT v1.6.30: that version registered the observer inside
       onCreate BEFORE setContent and ran onChange on the MAIN handler — an
       OEM provider firing a notification burst right at registration (a
       documented behavior) delivered work onto the main thread inside the
       launch window, stacked on the splashless first frame + hydrate. That
       coupling is one of the two suspects in v1.6.30's "closes when I open
       it". Here the design inverts every leg of it:
         - registration happens on the FIRST onResume, AFTER the UI is up —
           the onCreate/setContent launch window is untouched;
         - the observer rides a dedicated BACKGROUND HandlerThread, so a
           provider notification burst NEVER touches the main thread;
         - the debounced refresh Runnable on main is fully guarded, gated on
           hydrated, and only fires 1.2s after a REAL call-log write;
         - and the v1.6.29 splash is back as the launch safety buffer, so
           even the worst case composes over a trivial pane, not the full
           shell.
       No new permission — READ_CALL_LOG is already held for the log itself;
       without it the observer simply never fires. */
    private val mainHandler = Handler(Looper.getMainLooper())
    private var observerThread: HandlerThread? = null
    private var callLogObserver: ContentObserver? = null

    /** The debounced refresh — ONE incremental loadSince ~1.2s after the
     *  provider's last write (it notifies several times per call: insert,
     *  duration update…). Same permission-gated path onResume uses. */
    private val callLogRefresh = Runnable {
        try {
            if (AppStore.state.hydrated) {
                com.callmony.lib.BootTrace.mark("recents.observerRefresh")
                AppStore.loadSystemCalls()
            }
        } catch (_: Throwable) {
            // a provider hiccup must never reach the process
        }
    }

    /** Idempotent, exception-proof registration (first onResume only). */
    private fun ensureCallLogObserver() {
        if (callLogObserver != null) return
        try {
            val thread = HandlerThread("callmony-calllog").also { it.start() }
            val observer = object : ContentObserver(Handler(thread.looper)) {
                override fun onChange(selfChange: Boolean) {
                    try {
                        mainHandler.removeCallbacks(callLogRefresh)
                        mainHandler.postDelayed(callLogRefresh, 1200L)
                    } catch (_: Throwable) {
                    }
                }
            }
            contentResolver.registerContentObserver(
                android.provider.CallLog.Calls.CONTENT_URI,
                true,
                observer,
            )
            observerThread = thread
            callLogObserver = observer
        } catch (_: Throwable) {
            // no observer -> the onResume-only sync of old remains
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.callmony.lib.BootTrace.mark("activity.create")
        val bootThemeDark = persistedThemeIsDark()
        if (bootThemeDark) setTheme(R.style.Theme_Callmony_Dark)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AndroidPlatform.init(applicationContext)
        // v1.6.28 — THE "FLASH WHEN IT OPENS" + "STILL OPENS SLOW" FIX, at
        // the source. Both run BEFORE the first Compose frame:
        //   1. seedBootAppearance — the store's in-memory settings are the
        //      DEFAULTS (theme "auto", language "en") until hydrate
        //      publishes, and the SPLASH renders from them: a dark-theme
        //      user saw dark system splash → LIGHT Compose splash (the
        //      flash) → dark UI; an am/om/ar/ti user saw the tagline flash
        //      English-first. Seeding theme + language + osDark here makes
        //      frame 1 of the splash correct. (Guarded inside: a no-op once
        //      hydrated, so activity recreation can never clobber settings.)
        //   2. hydrate() — kicked before setContent so the boot file reads
        //      (settings, caches, PIN) OVERLAP first-frame Compose init
        //      instead of starting only after it (hydrate is idempotent; the
        //      CallmonyApp LaunchedEffect call is a guarded no-op now).
        val sysDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        com.callmony.lib.AppStore.seedBootAppearance(
            sysDark = sysDark,
            themeDark = bootThemeDark,
            lang = persistedLanguage(),
        )
        com.callmony.lib.AppStore.hydrate()
        com.callmony.lib.BootTrace.mark("activity.content")
        setContent {
            CallmonyApp()
        }
    }

    override fun onResume() {
        super.onResume()
        // v1.6.17 — drives the "lock immediately" autolock option
        com.callmony.lib.UiStore.appInForeground = true
        // v1.6.33 — first resume: the UI is up, the launch window is over —
        // safe to arm the post-call observer (see ensureCallLogObserver).
        ensureCallLogObserver()
        // Returning from the default dialer / messaging app: refresh the
        // system call log incrementally so calls made through the handoff
        // appear in Recents right away (cheap — usually zero rows).
        if (AppStore.state.hydrated) {
            AppStore.loadSystemCalls()
            // v1.6.14: TTL-guarded contact book refresh — contacts created
            // outside the app while it sat in the background now show up in
            // search / Recents / keypad matches without a restart.
            AppStore.maybeRefreshContacts()
        }
    }

    override fun onPause() {
        super.onPause()
        com.callmony.lib.UiStore.appInForeground = false
    }

    override fun onDestroy() {
        // v1.6.33 — release the observer + its thread cleanly (the observer
        // intentionally STAYS armed across onPause: the ended call is
        // written while the user is on the in-call screen, app backgrounded).
        try {
            callLogObserver?.let { contentResolver.unregisterContentObserver(it) }
        } catch (_: Throwable) {
        }
        try {
            observerThread?.quitSafely()
        } catch (_: Throwable) {
        }
        callLogObserver = null
        observerThread = null
        mainHandler.removeCallbacks(callLogRefresh)
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionBus.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
