package com.callmony.ui.kit

import com.callmony.AndroidPlatform
import com.callmony.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android: the compose resource is bundled into the APK assets — read it
 *  straight from the application context (no compose-resources context
 *  initialization involved, works on every API level and in unit tests).
 *  v1.6.15: a SECOND independent copy lives in res/drawable-nodpi
 *  (logo_asset.png) — if the asset read ever fails (OEM packaging quirks,
 *  corrupted install), the raw-resource fallback still yields the real
 *  logo, so the fallback tile can never masquerade as "the old logo". */
internal actual suspend fun readLogoPngBytes(): ByteArray? = withContext(Dispatchers.IO) {
    try {
        AndroidPlatform.appContext.assets
            .open("composeResources/callmony.composeapp.generated.resources/drawable/logo.png")
            .use { it.readBytes() }
    } catch (_: Exception) {
        try {
            AndroidPlatform.appContext.resources
                .openRawResource(R.drawable.logo_asset)
                .use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
    }
}

/** Android: decode on a daemon thread started from Application.onCreate —
 *  the splash's first frame already carries the bitmap (no fallback flash). */
private var androidEagerStarted = false
actual fun decodeLogoEagerly() {
    if (androidEagerStarted) return
    androidEagerStarted = true
    Thread {
        try {
            val bytes = kotlinx.coroutines.runBlocking { readLogoPngBytes() }
            if (bytes != null) {
                val bmp = decodeLogoBytes(bytes)
                if (bmp != null) com.callmony.ui.kit.seedLogoCache(bmp)
            }
        } catch (_: Throwable) {
            // produceState retries later
        }
    }.apply { isDaemon = true; name = "logo-decode" }.start()
}
