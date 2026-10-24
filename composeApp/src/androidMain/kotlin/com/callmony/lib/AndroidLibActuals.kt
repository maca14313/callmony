package com.callmony.lib

import java.security.MessageDigest
import java.util.TimeZone
/* ---------------- common expect actuals ---------------- */

actual fun platformClockMillis(): Long = System.currentTimeMillis()

/** Local timezone offset in minutes (east positive) at the given instant. */
internal actual fun tzOffsetMinutes(epochMillis: Long): Int =
    TimeZone.getDefault().getOffset(epochMillis) / 60_000

actual fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
