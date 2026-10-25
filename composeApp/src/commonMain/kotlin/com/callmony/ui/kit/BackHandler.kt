package com.callmony.ui.kit

import androidx.compose.runtime.Composable

/* AppBackHandler — multiplatform hardware-back interception.

   v1.6.15 FIX: the native Android back button had NO handler anywhere, so
   with a modal open, back CLOSED THE WHOLE APP instead of dismissing the
   modal / going back one level. The shell now registers a root handler that
   closes things in order (overlay stack → keypad → more sheet → to Recents)
   and only lets the system exit when nothing is open. */

@Composable
expect fun AppBackHandler(enabled: Boolean, onBack: () -> Unit)
