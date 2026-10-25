@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.callmony

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
import kotlinx.coroutines.flow.MutableStateFlow
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard
import platform.UIKit.UIScreen
import platform.UIKit.UIUserInterfaceStyle

/* iOS platform wiring — the KMP mirror of the RN native modules.
   The date/hash/clock actuals live in lib/IosLibActuals.kt. */

object IosPlatform {
    fun init() {
        Platform.calls = IosCalls()
        Platform.callLog = IosCallLog()
        Platform.contacts = IosContacts()
        Platform.notify = IosNotify()
        Platform.share = IosShare()
        Platform.kv = IosKv()
        Platform.ui = IosUi()
        // v1.6.18 — same freeze fix as Android: persistence goes through the
        // single-writer IO queue instead of blocking the caller's thread.
        com.callmony.lib.Persist.enableAsync()
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
    override fun schedule(
        id: String, title: String, body: String, at: Long, kind: String,
        kindLabel: String, doneLabel: String, snoozeLabel: String, alarm: Boolean,
    ) {
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

    @OptIn(ExperimentalForeignApi::class)
    override fun readFile(name: String): String? =
        try {
            val path = docDir() + "/" + name
            if (fm.fileExistsAtPath(path)) NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) else null
        } catch (_: Exception) { null }

    @OptIn(ExperimentalForeignApi::class)
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

