package com.callmony.ui.kit

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Android: sync the status + navigation bar icon appearance with the APP
 *  canvas (not the OS dark mode). Called from the shell on every theme
 *  change; runs on the Android view's window insets controller. */
@Composable
actual fun SystemBarsAppearance(lightCanvas: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = lightCanvas
            controller.isAppearanceLightNavigationBars = lightCanvas
        }
    }
}
