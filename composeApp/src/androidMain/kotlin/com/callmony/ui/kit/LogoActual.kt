package com.callmony.ui.kit

import com.callmony.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android: the compose resource is bundled into the APK assets — read it
    straight from the application context (no compose-resources context
    initialization involved, works on every API level and in unit tests). */
internal actual suspend fun readLogoPngBytes(): ByteArray? = withContext(Dispatchers.IO) {
    try {
        AndroidPlatform.appContext.assets
            .open("composeResources/callmony.composeapp.generated.resources/drawable/logo.png")
            .use { it.readBytes() }
    } catch (_: Exception) {
        null
    }
}
