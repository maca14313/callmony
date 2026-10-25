package com.callmony.ui.kit

import androidx.compose.runtime.Composable

/* SystemBars — expect/actual system-bar appearance control.

   v1.6.15 FIX: `enableEdgeToEdge()` derives the status/navigation bar icon
   appearance from the SYSTEM dark mode, but the app has its own light/dark/
   auto theme setting — with the system in dark mode and the app in LIGHT
   theme, the status bar icons stayed white over the light canvas: the phone's
   clock / battery / signal became invisible (the user's exact report:
   "I can't see the phone's date, charge percent … when it is on light mode").
   The shell now drives the appearance from the APP canvas color instead. */

/** Apply light/dark system-bar icon appearance for the current window. */
@Composable
expect fun SystemBarsAppearance(lightCanvas: Boolean)
