package com.callmony.ui.kit

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/** Android: the real androidx back dispatcher. Handlers registered later
 *  (inner modals) take precedence over the shell's root handler. */
@Composable
actual fun AppBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
