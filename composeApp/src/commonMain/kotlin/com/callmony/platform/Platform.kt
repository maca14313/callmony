package com.callmony.platform

import com.callmony.model.DeviceContact
import kotlinx.coroutines.flow.MutableStateFlow

/* callmony — platform service layer (Kotlin Multiplatform).
   The React Native edition reached these through native modules
   (ImmediateCallModule, SysCallLogModule, ContactEditModule, notifee…);
   here they are plain services implemented per platform. */

data class DeviceCall(
    val id: String,
    val cachedName: String?,
    val number: String,
    val type: Int, // 1=incoming 2=outgoing 3=missed 4=voicemail 5=rejected 6=blocked 7=external
    val date: Long,
    val duration: Int,
)

class PermState {
    companion object {
        const val UNKNOWN = "unknown"
        const val GRANTED = "granted"
        const val DENIED = "denied"
    }
}

/** Place calls through the phone's dialer (ACTION_CALL first, ACTION_DIAL fallback). */
interface CallsService {
    /** Fire an immediate call — never drops the number into the keypad. */
    fun callNow(number: String)
    /** Open the system dialer pre-filled with the number. */
    fun dial(number: String)
    /** whether CALL_PHONE is granted right now */
    fun canCall(): Boolean
    /** Ask for CALL_PHONE (runtime dialog); returns false when asking is
     *  impossible (no activity / platform without runtime ask). */
    fun requestPermission(onResult: (Boolean) -> Unit): Boolean {
        onResult(false)
        return false
    }
}

/** The device's real system call log (CallLog.Calls provider on Android). */
interface CallLogService {
    /** Full re-read (cap 400 rows newest-first, like the RN edition). */
    fun fetchAll(): List<DeviceCall>
    /** Incremental read — only rows newer than `since` (1-minute overlap, dedup by id). */
    fun loadSince(since: Long): List<DeviceCall>
    /** READ_CALL_LOG granted right now? */
    fun hasPermission(): Boolean = false
    /** Ask once for READ_CALL_LOG (runtime dialog on Android). */
    fun requestPermission(onResult: (Boolean) -> Unit) { onResult(false) }
}

/** The device's address book. */
interface ContactsService {
    /** Whole book via getAllWithoutPhotos (A–Z sorted by display name). */
    fun fetchAll(): List<DeviceContact>
    fun hasPermission(): Boolean
    fun requestPermission(onResult: (Boolean) -> Unit)
    /** WRITE_CONTACTS granted right now? (in-app create/edit needs it) */
    fun hasWritePermission(): Boolean = false
    /** Ask for WRITE_CONTACTS — fires immediately when already granted. */
    fun requestWritePermission(onResult: (Boolean) -> Unit) { onResult(false) }
    /** Safe in-app create — single applyBatch; returns the created recordID. */
    fun createContact(given: String, middle: String?, family: String?, company: String?, phones: List<Pair<String, String>>, emails: List<Pair<String, String>>): String?
    /** Safe in-app edit — loss-free union merge of phones/emails. */
    fun updateContact(recordID: String, given: String, middle: String?, family: String?, company: String?, phones: List<Pair<String, String>>, emails: List<Pair<String, String>>): Boolean
    /** Add one phone number to an existing contact (dup detection). */
    fun addPhoneToContact(recordID: String, label: String, number: String): Boolean
    /** Delete a contact. */
    fun deleteContact(recordID: String): Boolean
}

/** Local notifications + exact alarms for reminders. */
interface NotifyService {
    fun ensureChannel()
    fun hasNotifPermission(): Boolean
    fun requestNotifPermission(onResult: (Boolean) -> Unit)

    /** v1.6.16 — RICH NOTIFICATIONS: the system notification now carries
     *  pre-localized action labels ("Done", "Snooze 10 minutes") and the
     *  kind label ("Payment", "Birthday", …) so reminders that fire while
     *  the app is closed still look right in the user's language. Labels
     *  default to empty — callers that don't care keep the old behavior. */
    fun schedule(
        id: String, title: String, body: String, at: Long, kind: String,
        kindLabel: String = "", doneLabel: String = "", snoozeLabel: String = "",
    )

    fun cancel(id: String)
    /** re-arm everything the OS dropped (self-heal) */
    fun rearmAll(reminders: List<ReminderForAlarm>)
    fun vibrate(pattern: LongArray)
    fun hasExactAlarmPermission(): Boolean
    fun requestExactAlarmPermission()
    fun isBatteryExempt(): Boolean
    fun requestBatteryExemption()
}

data class ReminderForAlarm(
    val id: String,
    val title: String,
    val body: String,
    val dueAt: Long,
    val kind: String,
    val kindLabel: String = "",
    val doneLabel: String = "",
    val snoozeLabel: String = "",
)

/** Share / open external URLs (WhatsApp, Telegram, mail…). */
interface ShareService {
    fun openUrl(url: String)
    fun canOpenUrl(url: String): Boolean
    /** Open the DEFAULT messaging app with the number (and optional text) pre-filled. */
    fun openSms(number: String, text: String? = null) {
        openUrl(if (text != null) "sms:$number?body=$text" else "sms:$number")
    }
    /** Plain text share sheet. */
    fun shareText(text: String, title: String)
    /** Copy to the system clipboard. */
    fun copyText(text: String)
    /** Read the clipboard (for keypad paste). */
    fun readClipboard(): String?
}

/** Tiny persistent key-value file store (AsyncStorage equivalent). */
interface KvStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun remove(key: String)
    /** Documents-dir style file helpers (backup snapshots, caches). */
    fun readFile(name: String): String?
    fun writeFile(name: String, content: String)
    fun deleteFile(name: String)
    fun fileExists(name: String): Boolean
    fun listFiles(dir: String): List<String>
    fun makeDir(dir: String)
}

/** Platform theme/helpers hooks. */
interface UiPlatform {
    /** whether the OS is currently in dark mode */
    val osDarkFlow: MutableStateFlow<Boolean>
    fun setStatusBarMode(dark: Boolean)
    fun setNavColor(rgba: Long)
}

/** The service registry — initialized by each platform at startup. */
object Platform {
    var calls: CallsService = object : CallsService {
        override fun callNow(number: String) {}
        override fun dial(number: String) {}
        override fun canCall() = false
    }
    var callLog: CallLogService = object : CallLogService {
        override fun fetchAll(): List<DeviceCall> = emptyList()
        override fun loadSince(since: Long): List<DeviceCall> = emptyList()
    }
    var contacts: ContactsService = object : ContactsService {
        override fun fetchAll(): List<DeviceContact> = emptyList()
        override fun hasPermission() = false
        override fun requestPermission(onResult: (Boolean) -> Unit) { onResult(false) }
        override fun createContact(given: String, middle: String?, family: String?, company: String?, phones: List<Pair<String, String>>, emails: List<Pair<String, String>>): String? = null
        override fun updateContact(recordID: String, given: String, middle: String?, family: String?, company: String?, phones: List<Pair<String, String>>, emails: List<Pair<String, String>>): Boolean = false
        override fun addPhoneToContact(recordID: String, label: String, number: String): Boolean = false
        override fun deleteContact(recordID: String): Boolean = false
    }
    var notify: NotifyService = object : NotifyService {
        override fun ensureChannel() {}
        override fun hasNotifPermission() = true
        override fun requestNotifPermission(onResult: (Boolean) -> Unit) { onResult(true) }
        override fun schedule(
            id: String, title: String, body: String, at: Long, kind: String,
            kindLabel: String, doneLabel: String, snoozeLabel: String,
        ) {}
        override fun cancel(id: String) {}
        override fun rearmAll(reminders: List<ReminderForAlarm>) {}
        override fun vibrate(pattern: LongArray) {}
        override fun hasExactAlarmPermission() = true
        override fun requestExactAlarmPermission() {}
        override fun isBatteryExempt() = true
        override fun requestBatteryExemption() {}
    }
    var share: ShareService = object : ShareService {
        override fun openUrl(url: String) {}
        override fun canOpenUrl(url: String) = false
        override fun shareText(text: String, title: String) {}
        override fun copyText(text: String) {}
        override fun readClipboard(): String? = null
    }
    var kv: KvStore = object : KvStore {
        override fun read(key: String): String? = null
        override fun write(key: String, value: String) {}
        override fun remove(key: String) {}
        override fun readFile(name: String): String? = null
        override fun writeFile(name: String, content: String) {}
        override fun deleteFile(name: String) {}
        override fun fileExists(name: String) = false
        override fun listFiles(dir: String): List<String> = emptyList()
        override fun makeDir(dir: String) {}
    }
    var ui: UiPlatform = object : UiPlatform {
        override val osDarkFlow = MutableStateFlow(false)
        override fun setStatusBarMode(dark: Boolean) {}
        override fun setNavColor(rgba: Long) {}
    }
}
