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
     - Android: reads the asset straight from the app context (always works),
       with a res/drawable-nodpi copy as a second independent source
     - iOS:     reads it from the framework bundle via Res.readBytes
   v1.6.13: the decoded bitmap is cached process-wide.
   v1.6.15: decodeLogoEagerly() — CallmonyApplication kicks a background
   decode at process start so the FIRST composed frame already has the
   bitmap; the splash/navbar can never flash their fallback tile (that flash
   is what read as "the old logo" at every cold start). */

/** Read the raw logo PNG bytes, or null when unavailable. */
internal expect suspend fun readLogoPngBytes(): ByteArray?

private var cachedLogo: ImageBitmap? = null

/** The decoded logo (null until first decode completes). */
fun logoBitmapOrNull(): ImageBitmap? = cachedLogo

/** Kick the one-time eager decode at process start (platform-threaded).
 *  Safe to call multiple times; the produceState path remains the fallback. */
expect fun decodeLogoEagerly()

/** Decode the new logo once (cached); callers keep a fallback tile. */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun rememberLogoBitmap(): State<ImageBitmap?> = produceState<ImageBitmap?>(cachedLogo) {
    if (cachedLogo == null) {
        val bytes = readLogoPngBytes() ?: return@produceState
        cachedLogo = runCatching { bytes.decodeToImageBitmap() }.getOrNull()
    }
    value = cachedLogo
}

/** Called by the platform eager decoders to publish the cached bitmap. */
internal fun seedLogoCache(bmp: ImageBitmap) {
    if (cachedLogo == null) cachedLogo = bmp
}

/** Shared PNG decode used by the eager decoders (common dependency space). */
@OptIn(ExperimentalResourceApi::class)
internal fun decodeLogoBytes(bytes: ByteArray): ImageBitmap? =
    runCatching { bytes.decodeToImageBitmap() }.getOrNull()
