package com.callmony.ui.kit

import callmony.composeapp.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** iOS: the compose resource ships inside the KMP framework bundle —
    read it through the generated Res object (bundle-relative path). */
@OptIn(ExperimentalResourceApi::class)
internal actual suspend fun readLogoPngBytes(): ByteArray? = try {
    Res.readBytes("drawable/logo.png")
} catch (_: Exception) {
    null
}

/** iOS: kick a background decode at process start so the first composed
 *  frame already carries the real logo bitmap. */
private var iosEagerStarted = false
actual fun decodeLogoEagerly() {
    if (iosEagerStarted) return
    iosEagerStarted = true
    GlobalScope.launch(Dispatchers.Default) {
        try {
            val bytes = readLogoPngBytes() ?: return@launch
            val bmp = decodeLogoBytes(bytes)
            if (bmp != null) seedLogoCache(bmp)
        } catch (_: Throwable) {
            // produceState retries later
        }
    }
}
