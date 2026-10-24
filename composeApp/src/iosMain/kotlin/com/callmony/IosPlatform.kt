package com.callmony

import com.callmony.lib.KmpDate
import com.callmony.model.DeviceContact
import com.callmony.platform.CallsService
import com.callmony.platform.CallLogService
import com.callmony.platform.ContactsService
import com.callmony.platform.DeviceCall
import com.callmony.platform.KvStore
import com.callmony.platform.NotifyService
import com.callmony.platform.Platform
import com.callmony.platform.ReminderForAlarm
import com.callmony.platform.ShareService
import com.callmony.platform.UiPlatform
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.MutableStateFlow
import platform.CoreCrypto.CC_SHA256
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSString
import platform.Foundation.NSTimeIntervalSince1970
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dateByAddingUnit
import platform.Foundation.dateFromComponents
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard
import platform.UIKit.UIScreen
import platform.UIKit.UIUserInterfaceStyle

/* iOS actuals + platform wiring. The app data store, i18n, theme and the
   whole Compose UI are shared; only these thin services are platform code. */

/* ---------------- common expect actuals ---------------- */

actual fun platformClockMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000).toLong()

private val calendar: NSCalendar = NSCalendar.currentCalendar

actual class KmpDate actual constructor(
    y: Int, m0: Int, d: Int, h: Int, mi: Int, s: Int,
) {
    private val comps = NSDateComponents()

    init {
        comps.year = y.toLong()
        comps.month = (m0 + 1).toLong()
        comps.day = d.toLong()
        comps.hour = h.toLong()
        comps.minute = mi.toLong()
        comps.second = s.toLong()
    }

    actual val year: Int get() = comps.year.toInt()
    actual val month0: Int get() = (comps.month.toInt() - 1)
    actual val day: Int get() = comps.day.toInt()
    actual val hour: Int get() = comps.hour.toInt()
    actual val minute: Int get() = comps.minute.toInt()
    actual val second: Int get() = comps.second.toInt()

    actual fun time(): Long {
        val date = calendar.dateFromComponents(comps)
        return ((date?.timeIntervalSince1970 ?: 0.0) * 1000).toLong()
    }

    actual fun addDays(n: Int): KmpDate {
        val date = calendar.dateFromComponents(comps) ?: NSDate()
        val added = calendar.dateByAddingUnit(NSCalendarUnitDay, n.toLong(), date, 0u)
        val c = added?.let {
            calendar.components(
                NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
                    NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond, it,
            )
        }
        return if (c != null) {
            KmpDate(c.year.toInt(), (c.month.toInt() - 1), c.day.toInt(), c.hour.toInt(), c.minute.toInt(), c.second.toInt())
        } else {
            KmpDate(year, month0, day + n, hour, minute, second)
        }
    }
}

actual fun dateOf(epoch: Long): KmpDate {
    val date = NSDate.dateWithTimeIntervalSince1970(epoch / 1000.0)
    val c = calendar.components(
        NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
            NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond, date,
    )
    return KmpDate(
        c.year.toInt(), (c.month.toInt() - 1), c.day.toInt(),
        c.hour.toInt(), c.minute.toInt(), c.second.toInt(),
    )
}

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

internal actual fun System.currentTimeMillisCompat(): Long = platformClockMillis()

/* ---------------- iOS platform services ---------------- */

object IosPlatform {
    fun init() {
        Platform.calls = IosCalls()
        Platform.callLog = IosCallLog()
        Platform.contacts = IosContacts()
        Platform.notify = IosNotify()
        Platform.share = IosShare()
        Platform.kv = IosKv()
        Platform.ui = IosUi()
    }
}

class IosCalls : CallsService {
    override fun callNow(number: String) = dial(number)
    override fun dial(number: String) {
        val url = "tel:" + number.replace(Regex("[^\\d+*#]"), "")
        NSURL(string = url)?.let { UIApplication.sharedApplication.openURL(it) }
    }
    override fun canCall(): Boolean = true
}

class IosCallLog : CallLogService {
    // iOS: the system call log is not readable — the app keeps its own log
    // (calls started from callmony land in the app recents, iOS parity path).
    override fun fetchAll(): List<DeviceCall> = emptyList()
    override fun loadSince(since: Long): List<DeviceCall> = emptyList()
}

class IosContacts : ContactsService {
    // The device address book integration ships with the Xcode build
    // (Contacts framework); the shared UI runs against these stubs until then.
    override fun fetchAll(): List<DeviceContact> = emptyList()
    override fun hasPermission(): Boolean = false
    override fun requestPermission(onResult: (Boolean) -> Unit) { onResult(false) }
    override fun createContact(given: String, middle: String?, family: String?, company: String?, phones: List<Pair<String, String>>, emails: List<Pair<String, String>>): String? = null
    override fun updateContact(recordID: String, given: String, middle: String?, family: String?, company: String?, phones: List<Pair<String, String>>, emails: List<Pair<String, String>>): Boolean = false
    override fun addPhoneToContact(recordID: String, label: String, number: String): Boolean = false
    override fun deleteContact(recordID: String): Boolean = false
}

class IosNotify : NotifyService {
    override fun ensureChannel() {}
    override fun hasNotifPermission(): Boolean = true
    override fun requestNotifPermission(onResult: (Boolean) -> Unit) { onResult(true) }
    override fun schedule(id: String, title: String, body: String, at: Long, kind: String) {
        // UNUserNotificationCenter scheduling ships with the Xcode build
    }
    override fun cancel(id: String) {}
    override fun rearmAll(reminders: List<ReminderForAlarm>) {}
    override fun vibrate(pattern: LongArray) {}
    override fun hasExactAlarmPermission(): Boolean = true
    override fun requestExactAlarmPermission() {}
    override fun isBatteryExempt(): Boolean = true
    override fun requestBatteryExemption() {}
}

class IosShare : ShareService {
    override fun openUrl(url: String) {
        NSURL(string = url)?.let { UIApplication.sharedApplication.openURL(it) }
    }
    override fun canOpenUrl(url: String): Boolean =
        NSURL(string = url)?.let { UIApplication.sharedApplication.canOpenURL(it) } ?: false

    override fun shareText(text: String, title: String) {
        // UIActivityViewController bridge ships with the Xcode build
    }
    override fun copyText(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }
    override fun readClipboard(): String? = UIPasteboard.generalPasteboard.string
}

class IosKv : KvStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val fm = NSFileManager.defaultManager

    private fun docDir(): String =
        (fm.URLsForDirectory(NSDocumentDirectory, inDomains = NSUserDomainMask)
            .firstOrNull() as? NSURL)?.path ?: "."

    override fun read(key: String): String? = defaults.stringForKey(key)
    override fun write(key: String, value: String) = defaults.setObject(value, forKey = key)
    override fun remove(key: String) = defaults.removeObjectForKey(key)

    override fun readFile(name: String): String? =
        try {
            val path = docDir() + "/" + name
            if (fm.fileExistsAtPath(path)) NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) else null
        } catch (_: Exception) { null }

    override fun writeFile(name: String, content: String) {
        try {
            val path = docDir() + "/" + name
            (content as NSString).writeToFile(path, true, NSUTF8StringEncoding, null)
        } catch (_: Exception) {}
    }

    override fun deleteFile(name: String) {
        try { fm.removeItemAtPath(docDir() + "/" + name, null) } catch (_: Exception) {}
    }

    override fun fileExists(name: String): Boolean = fm.fileExistsAtPath(docDir() + "/" + name)

    override fun listFiles(dir: String): List<String> {
        val path = docDir() + "/" + dir
        return try {
            fm.contentsOfDirectoryAtPath(path, null)?.mapNotNull { it as? String } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    override fun makeDir(dir: String) {
        try { fm.createDirectoryAtPath(docDir() + "/" + dir, true, null, null) } catch (_: Exception) {}
    }
}

class IosUi : UiPlatform {
    override val osDarkFlow = MutableStateFlow(
        UIScreen.mainScreen.traitCollection.userInterfaceStyle == UIUserInterfaceStyle.UIUserInterfaceStyleDark
    )
    override fun setStatusBarMode(dark: Boolean) {}
    override fun setNavColor(rgba: Long) {}
}
