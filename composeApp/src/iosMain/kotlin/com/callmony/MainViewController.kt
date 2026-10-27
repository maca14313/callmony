package com.callmony

import androidx.compose.ui.window.ComposeUIViewController
import com.callmony.lib.AppStore
import com.callmony.platform.Platform
import com.callmony.ui.CallmonyApp

/** The iOS entry point — called from ContentView.swift. */
fun MainViewController() = ComposeUIViewController {
    IosPlatform.init()
    // v1.6.28 — same boot seeding as Android's MainActivity: the persisted
    // theme + language are scanned (cheap string scan of the settings row)
    // and seeded BEFORE the first composition so the splash renders in the
    // user's theme + language from frame 1 (no light flash before a dark
    // app, no English tagline flash), and hydrate is kicked before the UI
    // composes so the boot file reads overlap first-frame init (idempotent —
    // the CallmonyApp LaunchedEffect call is a guarded no-op now).
    runCatching {
        val sysDark = Platform.ui.osDarkFlow.value
        val (theme, lang) = AppStore.scanPersistedBootPrefs()
        AppStore.seedBootAppearance(
            sysDark = sysDark,
            themeDark = theme == "dark" || ((theme == null || theme == "auto") && sysDark),
            lang = lang,
        )
    }
    AppStore.hydrate()
    CallmonyApp()
}

/** Swift bridge — iOSApp.swift calls IosInitKt.doInit() at app start. */
fun doInit() {
    IosPlatform.init()
}
