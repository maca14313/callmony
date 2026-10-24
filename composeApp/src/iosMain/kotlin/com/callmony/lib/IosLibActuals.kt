package com.callmony.lib

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.systemTimeZone
import platform.Foundation.timeIntervalSince1970

/* ---------------- common expect actuals (iOS) ---------------- */

actual fun platformClockMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000).toLong()

/** Local timezone offset in minutes (east positive) at the given instant. */
internal actual fun tzOffsetMinutes(epochMillis: Long): Int =
    NSTimeZone.systemTimeZone.secondsFromGMTForDate(
        // NSDate's exposed ctor uses the 2001-01-01 reference date (978307200s after 1970)
        NSDate(timeIntervalSinceReferenceDate = epochMillis / 1000.0 - 978307200.0),
    ).toInt() / 60

@OptIn(ExperimentalForeignApi::class)
actual fun sha256Hex(input: String): String {
    val data = input.encodeToByteArray()
    val digest = UByteArray(32)
    data.usePinned { pinned ->
        digest.usePinned { digestPinned ->
            CC_SHA256(pinned.addressOf(0), data.size.toUInt(), digestPinned.addressOf(0))
        }
    }
    return digest.joinToString("") { it.toString(16).padStart(2, '0') }
}
