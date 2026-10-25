package com.callmony.ui.kit

import androidx.compose.runtime.Composable

/** iOS: no hardware back button — no-op (the shell's in-app back arrows
 *  remain the navigation on Apple platforms). */
@Composable
actual fun AppBackHandler(enabled: Boolean, onBack: () -> Unit) { }
