package com.callmony.ui.kit

import callmony.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** iOS: the compose resource ships inside the KMP framework bundle —
    read it through the generated Res object (bundle-relative path). */
@OptIn(ExperimentalResourceApi::class)
internal actual suspend fun readLogoPngBytes(): ByteArray? = try {
    Res.readBytes("drawable/logo.png")
} catch (_: Exception) {
    null
}
