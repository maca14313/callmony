package com.callmony.ui.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/* The NEW callmony logo — bundled once as a compose resource
   (composeResources/drawable/logo.png) and decoded to an ImageBitmap.
   Reading is expect/actual so it never depends on the compose-resources
   Android context initialization:
     - Android: reads the asset straight from the app context (always works)
     - iOS:     reads it from the framework bundle via Res.readBytes */

/** Read the raw logo PNG bytes, or null when unavailable. */
internal expect suspend fun readLogoPngBytes(): ByteArray?

/** Decode the new logo once; null until ready (callers keep a fallback tile). */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun rememberLogoBitmap(): State<ImageBitmap?> = produceState<ImageBitmap?>(null) {
    val bytes = readLogoPngBytes() ?: return@produceState
    value = runCatching { bytes.decodeToImageBitmap() }.getOrNull()
}
