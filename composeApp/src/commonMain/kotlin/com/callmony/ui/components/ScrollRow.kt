package com.callmony.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A Row that scrolls horizontally — used for every chip strip. */
@Composable
fun ScrollRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier.horizontalScroll(rememberScrollState())) { content() }
}
