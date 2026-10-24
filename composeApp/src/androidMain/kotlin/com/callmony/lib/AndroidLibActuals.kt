package com.callmony.lib

import java.security.MessageDigest
import java.util.Calendar
import java.util.TimeZone
/* ---------------- common expect actuals ---------------- */

actual fun platformClockMillis(): Long = System.currentTimeMillis()

actual class KmpDate actual constructor(
    y: Int, m0: Int, d: Int, h: Int, mi: Int, s: Int,
) {
    private val cal: Calendar = Calendar.getInstance().apply {
        setLenient(true)
        clear()
        set(y, m0, d, h, mi, s)
    }

    actual val year: Int get() = cal.get(Calendar.YEAR)
    actual val month0: Int get() = cal.get(Calendar.MONTH)
    actual val day: Int get() = cal.get(Calendar.DAY_OF_MONTH)
    actual val hour: Int get() = cal.get(Calendar.HOUR_OF_DAY)
    actual val minute: Int get() = cal.get(Calendar.MINUTE)
    actual val second: Int get() = cal.get(Calendar.SECOND)

    actual fun time(): Long = cal.timeInMillis

    actual fun addDays(n: Int): KmpDate {
        val c2 = (cal.clone() as Calendar)
        c2.add(Calendar.DAY_OF_MONTH, n)
        return KmpDate(
            c2.get(Calendar.YEAR), c2.get(Calendar.MONTH), c2.get(Calendar.DAY_OF_MONTH),
            c2.get(Calendar.HOUR_OF_DAY), c2.get(Calendar.MINUTE), c2.get(Calendar.SECOND),
        )
    }
}

actual fun dateOf(epoch: Long): KmpDate {
    val c = Calendar.getInstance().apply { timeInMillis = epoch }
    return KmpDate(
        c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH),
        c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND),
    )
}

actual fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
