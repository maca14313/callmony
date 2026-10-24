package com.callmony

import androidx.compose.ui.window.ComposeUIViewController
import com.callmony.ui.CallmonyApp

/** The iOS entry point — called from ContentView.swift. */
fun MainViewController() = ComposeUIViewController {
    IosPlatform.init()
    CallmonyApp()
}

/** Swift bridge — iOSApp.swift calls IosInitKt.doInit() at app start. */
fun doInit() {
    IosPlatform.init()
}
