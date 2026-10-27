package com.callmony

import android.content.res.Configuration
import android.os.Bundle
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionBus.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
