package com.callmony

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentProviderOperation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.callmony.lib.displayName
import com.callmony.model.DeviceAddress
import com.callmony.model.DeviceContact
import com.callmony.model.DeviceEmail
import com.callmony.model.DevicePhone
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
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Android platform wiring — the KMP mirror of the RN native modules. */
object AndroidPlatform {
    lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        Platform.calls = AndroidCalls()
        Platform.callLog = AndroidCallLog()
        Platform.contacts = AndroidContacts()
        Platform.notify = AndroidNotify()
        Platform.share = AndroidShare()
        Platform.kv = AndroidKv(appContext)
        Platform.ui = AndroidUi(appContext)
    }

    fun hasPerm(perm: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, perm) == PackageManager.PERMISSION_GRANTED

    /** Whether init(context) has run (BootReceiver may fire first on some OEMs). */
    fun isReady(): Boolean = this::appContext.isInitialized
}

/* ---------------- ImmediateCallModule ---------------- */

class AndroidCalls : CallsService {
    override fun callNow(number: String) {
        val ctx = AndroidPlatform.appContext
        val uri = Uri.parse("tel:${Uri.encode(number)}")
        if (AndroidPlatform.hasPerm(Manifest.permission.CALL_PHONE)) {
            try {
                ctx.startActivity(Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) { /* fall through to dial */ }
        }
        dial(number)
    }

    override fun dial(number: String) {
        val ctx = AndroidPlatform.appContext
        val uri = Uri.parse("tel:${Uri.encode(number)}")
        try {
            ctx.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }

    override fun canCall(): Boolean = AndroidPlatform.hasPerm(Manifest.permission.CALL_PHONE)

    override fun requestPermission(onResult: (Boolean) -> Unit): Boolean {
        // Already granted → fire immediately (no dialog).
        if (AndroidPlatform.hasPerm(Manifest.permission.CALL_PHONE)) {
            onResult(true)
            return true
        }
        return try {
            PermissionBus.request(Manifest.permission.CALL_PHONE, onResult)
            true
        } catch (_: Exception) {
            onResult(false)
            false
        }
    }
}

/* ---------------- SysCallLogModule ---------------- */

class AndroidCallLog : CallLogService {
    override fun hasPermission(): Boolean =
        AndroidPlatform.hasPerm(Manifest.permission.READ_CALL_LOG)

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        PermissionBus.request(Manifest.permission.READ_CALL_LOG, onResult)
    }

    override fun fetchAll(): List<DeviceCall> {
        val ctx = AndroidPlatform.appContext
        if (!AndroidPlatform.hasPerm(Manifest.permission.READ_CALL_LOG)) return emptyList()
        val out = mutableListOf<DeviceCall>()
        return try {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID, CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
                null, null,
                "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext() && out.size < 400) {
                    out.add(
                        DeviceCall(
                            id = c.getLong(0).toString(),
                            cachedName = if (c.isNull(1)) null else c.getString(1),
                            number = c.getString(2) ?: "",
                            type = c.getInt(3),
                            date = c.getLong(4),
                            duration = c.getInt(5),
                        )
                    )
                }
            }
            out
        } catch (_: Throwable) { emptyList() } // v1.6.31 — Throwable: an Error (OOM mid-cursor on low-RAM devices) must not kill the app
    }

    override fun loadSince(since: Long): List<DeviceCall> {
        val ctx = AndroidPlatform.appContext
        if (!AndroidPlatform.hasPerm(Manifest.permission.READ_CALL_LOG)) return emptyList()
        val out = mutableListOf<DeviceCall>()
        return try {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID, CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
                "${CallLog.Calls.DATE} > ?",
                arrayOf((since - 60000L).toString()),
                "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    out.add(
                        DeviceCall(
                            id = c.getLong(0).toString(),
                            cachedName = if (c.isNull(1)) null else c.getString(1),
                            number = c.getString(2) ?: "",
                            type = c.getInt(3),
                            date = c.getLong(4),
                            duration = c.getInt(5),
                        )
                    )
                }
            }
            out
        } catch (_: Throwable) { emptyList() } // v1.6.31 — Throwable, same reason as fetchAll
    }
}

/* ---------------- react-native-contacts equivalent ---------------- */

private fun normalizeBirthday(raw: String?): String? {
    if (raw == null) return null
    // stored as millis since epoch on Android data rows
    return try {
        val l = raw.toLong()
        val cal = Calendar.getInstance()
        cal.timeInMillis = l
        "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    } catch (_: Exception) { null }
}

class AndroidContacts : ContactsService {
    override fun fetchAll(): List<DeviceContact> {
        val ctx = AndroidPlatform.appContext
        if (!AndroidPlatform.hasPerm(Manifest.permission.READ_CONTACTS)) return emptyList()
        val byContact = LinkedHashMap<Long, MutableDeviceContact>()
        return try {
            val cr = ctx.contentResolver
            // 1. every contact id + names
            cr.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ),
                null, null,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " COLLATE NOCASE ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val shown = c.getString(1) ?: c.getString(2) ?: ""
                    byContact[id] = MutableDeviceContact(recordID = id.toString(), displayName = shown)
                }
            }
            // 2. structured names
            cr.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID,
                    ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                    ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME,
                    ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
                    ContactsContract.CommonDataKinds.StructuredName.PREFIX,
                ),
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val m = byContact[c.getLong(0)] ?: continue
                    m.givenName = c.getString(1) ?: ""
                    m.middleName = c.getString(2)
                    m.familyName = c.getString(3) ?: ""
                }
            }
            // 3. organization
            cr.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Organization.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Organization.COMPANY,
                    ContactsContract.CommonDataKinds.Organization.TITLE,
                ),
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val m = byContact[c.getLong(0)] ?: continue
                    m.company = c.getString(1)
                    m.jobTitle = c.getString(2)
                }
            }
            // 4. nicknames
            cr.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Nickname.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Nickname.NAME,
                ),
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val m = byContact[c.getLong(0)] ?: continue
                    m.nickname = c.getString(1)
                }
            }
            // 5. notes
            cr.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Note.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Note.NOTE,
                ),
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val m = byContact[c.getLong(0)] ?: continue
                    m.note = c.getString(1)
                }
            }
            // 6. birthdays
            cr.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Event.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Event.START_DATE,
                ),
                "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?",
                arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val m = byContact[c.getLong(0)] ?: continue
                    m.birthday = normalizeBirthday(c.getString(1))
                }
            }
            // 7. phones
            cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone._ID,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.LABEL,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val m = byContact[c.getLong(0)] ?: continue
                    m.phones.add(DevicePhone(c.getLong(1).toString(), phoneLabel(c.getInt(3), c.getString(4)), c.getString(2) ?: ""))
                }
            }
            // 8. emails
            cr.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Email._ID,
                    ContactsContract.CommonDataKinds.Email.ADDRESS,
                    ContactsContract.CommonDataKinds.Email.TYPE,
                    ContactsContract.CommonDataKinds.Email.LABEL,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val m = byContact[c.getLong(0)] ?: continue
                    m.emails.add(DeviceEmail(c.getLong(1).toString(), emailLabel(c.getInt(3), c.getString(4)), c.getString(2) ?: ""))
                }
            }
            // 9. addresses
            cr.query(
                ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID,
                    ContactsContract.CommonDataKinds.StructuredPostal._ID,
                    ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS,
                    ContactsContract.CommonDataKinds.StructuredPostal.TYPE,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val m = byContact[c.getLong(0)] ?: continue
                    m.addresses.add(DeviceAddress(c.getLong(1).toString(), postalLabel(c.getInt(3)), c.getString(2) ?: ""))
                }
            }

            byContact.values.map { it.toDeviceContact() }
        } catch (_: Exception) { emptyList() }
    }

    private fun phoneLabel(type: Int, label: String?): String = label ?: when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "mobile"
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "work"
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "home"
        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "main"
        ContactsContract.CommonDataKinds.Phone.TYPE_COMPANY_MAIN -> "office"
        else -> "other"
    }

    private fun emailLabel(type: Int, label: String?): String = label ?: when (type) {
        ContactsContract.CommonDataKinds.Email.TYPE_WORK -> "work"
        ContactsContract.CommonDataKinds.Email.TYPE_HOME -> "home"
        ContactsContract.CommonDataKinds.Email.TYPE_MOBILE -> "mobile"
        else -> "other"
    }

    private fun postalLabel(type: Int): String = when (type) {
        ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK -> "work"
        ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME -> "home"
        else -> "other"
    }

    override fun hasPermission(): Boolean =
        AndroidPlatform.hasPerm(Manifest.permission.READ_CONTACTS)

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        PermissionBus.request(Manifest.permission.READ_CONTACTS, onResult)
    }

    override fun hasWritePermission(): Boolean =
        AndroidPlatform.hasPerm(Manifest.permission.WRITE_CONTACTS)

    override fun requestWritePermission(onResult: (Boolean) -> Unit) {
        PermissionBus.request(Manifest.permission.WRITE_CONTACTS, onResult)
    }

    override fun createContact(
        given: String, middle: String?, family: String?, company: String?,
        phones: List<Pair<String, String>>, emails: List<Pair<String, String>>,
    ): String? {
        val ctx = AndroidPlatform.appContext
        if (!AndroidPlatform.hasPerm(Manifest.permission.WRITE_CONTACTS)) return null
        return try {
            val ops = ArrayList<ContentProviderOperation>()
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )
            var nameParts = listOf(given, middle ?: "", family ?: "").filter { it.isNotBlank() }
            if (nameParts.isEmpty()) nameParts = listOf("No name")
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, given)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, middle)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, family)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, nameParts.joinToString(" "))
                    .build()
            )
            if (!company.isNullOrBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, company)
                        .build()
                )
            }
            for ((number, label) in phones) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, phoneTypeOf(label))
                        .build()
                )
            }
            for ((address, label) in emails) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, address)
                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE, emailTypeOf(label))
                        .build()
                )
            }
            val results = ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            // v1.6.14 FIX: results[0].uri points at the RAW contact — but
            // fetchAll() indexes everything by the AGGREGATE Contacts._ID.
            // Returning the raw id gave created contacts an identity that
            // nothing else in the app matched (detail lookups, favorites,
            // speed dials, socials all keyed on the contact id). Resolve the
            // aggregate CONTACT_ID from the new raw row and return THAT.
            val rawId = results.firstOrNull()?.uri?.lastPathSegment ?: return null
            ctx.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.CONTACT_ID),
                "${ContactsContract.RawContacts._ID} = ?",
                arrayOf(rawId), null,
            )?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0).toString()
            }
            // Aggregate not linked (shouldn't happen for local raw contacts)
            // — fall back to the raw id so the caller at least has something.
            return rawId
        } catch (_: Exception) { null }
    }

    private fun phoneTypeOf(label: String): Int = when (label) {
        "mobile" -> ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
        "work" -> ContactsContract.CommonDataKinds.Phone.TYPE_WORK
        "home" -> ContactsContract.CommonDataKinds.Phone.TYPE_HOME
        "main" -> ContactsContract.CommonDataKinds.Phone.TYPE_MAIN
        "office" -> ContactsContract.CommonDataKinds.Phone.TYPE_COMPANY_MAIN
        else -> ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
    }

    private fun emailTypeOf(label: String): Int = when (label) {
        "work" -> ContactsContract.CommonDataKinds.Email.TYPE_WORK
        "home" -> ContactsContract.CommonDataKinds.Email.TYPE_HOME
        "mobile" -> ContactsContract.CommonDataKinds.Email.TYPE_MOBILE
        else -> ContactsContract.CommonDataKinds.Email.TYPE_OTHER
    }

    /** Resolve the contact's raw contact ids (may span several raw rows). */
    private fun rawIdsOf(contactId: Long): List<Long> {
        val ctx = AndroidPlatform.appContext
        val out = mutableListOf<Long>()
        ctx.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) out.add(c.getLong(0))
        }
        return out
    }

    override fun updateContact(
        recordID: String, given: String, middle: String?, family: String?, company: String?,
        phones: List<Pair<String, String>>, emails: List<Pair<String, String>>,
    ): Boolean {
        val ctx = AndroidPlatform.appContext
        if (!AndroidPlatform.hasPerm(Manifest.permission.WRITE_CONTACTS)) return false
        return try {
            val contactId = recordID.toLongOrNull() ?: return false
            val ops = ArrayList<ContentProviderOperation>()
            // union-merge phones: keep every existing number we don't already have
            val wantedNumbers = phones.map { it.first.replace(Regex("[^\\d+]"), "") }.toMutableSet()
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone._ID, ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId.toString()), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val dataId = c.getLong(0)
                    val number = (c.getString(1) ?: "").replace(Regex("[^\\d+]"), "")
                    val idx = phones.indexOfFirst { it.first.replace(Regex("[^\\d+]"), "") == number }
                    if (idx >= 0) {
                        // update label in place
                        ops.add(
                            ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                                .withSelection("${ContactsContract.Data._ID} = ?", arrayOf(dataId.toString()))
                                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, phoneTypeOf(phones[idx].second))
                                .build()
                        )
                        wantedNumbers.remove(number)
                    } else if (number.isBlank()) {
                        ops.add(
                            ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                                .withSelection("${ContactsContract.Data._ID} = ?", arrayOf(dataId.toString()))
                                .build()
                        )
                    }
                    // numbers that exist on the device but not in the editor are KEPT (loss-free union)
                }
            }
            // structured name update on the first raw contact
            val rawIds = rawIdsOf(contactId)
            if (rawIds.isNotEmpty()) {
                ops.add(
                    ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                        .withSelection(
                            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                            arrayOf(rawIds.first().toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE),
                        )
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, given)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, middle)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, family)
                        .withValue(
                            ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                            listOf(given, middle ?: "", family ?: "").filter { it.isNotBlank() }.joinToString(" "),
                        )
                        .build()
                )
                // insert the numbers that are genuinely new
                for ((number, label) in phones) {
                    val norm = number.replace(Regex("[^\\d+]"), "")
                    if (norm in wantedNumbers) {
                        ops.add(
                            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawIds.first())
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, phoneTypeOf(label))
                                .build()
                        )
                    }
                }
                // emails union
                for ((address, label) in emails) {
                    val exists = ctx.contentResolver.query(
                        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Email._ID),
                        "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ? AND ${ContactsContract.CommonDataKinds.Email.ADDRESS} = ?",
                        arrayOf(contactId.toString(), address), null,
                    )?.use { it.count > 0 } ?: false
                    if (!exists) {
                        ops.add(
                            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawIds.first())
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, address)
                                .withValue(ContactsContract.CommonDataKinds.Email.TYPE, emailTypeOf(label))
                                .build()
                        )
                    }
                }
            }
            if (ops.isNotEmpty()) ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (_: Exception) { false }
    }

    override fun addPhoneToContact(recordID: String, label: String, number: String): Boolean {
        val ctx = AndroidPlatform.appContext
        if (!AndroidPlatform.hasPerm(Manifest.permission.WRITE_CONTACTS)) return false
        return try {
            val contactId = recordID.toLongOrNull() ?: return false
            val norm = number.replace(Regex("[^\\d+]"), "")
            // dup detection
            val dup = ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone._ID),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ? AND ${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?",
                arrayOf(contactId.toString(), number), null,
            )?.use { it.count > 0 } ?: false
            if (dup) return true
            val rawIds = rawIdsOf(contactId)
            if (rawIds.isEmpty()) return false
            ctx.contentResolver.insert(
                ContactsContract.Data.CONTENT_URI,
                android.content.ContentValues().apply {
                    put(ContactsContract.Data.RAW_CONTACT_ID, rawIds.first())
                    put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    put(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                    put(ContactsContract.CommonDataKinds.Phone.TYPE, phoneTypeOf(label))
                },
            )
            true
        } catch (_: Exception) { false }
    }

    override fun deleteContact(recordID: String): Boolean {
        val ctx = AndroidPlatform.appContext
        if (!AndroidPlatform.hasPerm(Manifest.permission.WRITE_CONTACTS)) return false
        return try {
            val contactId = recordID.toLongOrNull() ?: return false
            val rawIds = rawIdsOf(contactId)
            for (raw in rawIds) {
                ctx.contentResolver.delete(
                    ContactsContract.RawContacts.CONTENT_URI,
                    "${ContactsContract.RawContacts._ID} = ?",
                    arrayOf(raw.toString()),
                )
            }
            true
        } catch (_: Exception) { false }
    }
}

private class MutableDeviceContact(
    val recordID: String,
    val displayName: String,
) {
    var givenName: String = ""
    var middleName: String? = null
    var familyName: String = ""
    var nickname: String? = null
    var company: String? = null
    var jobTitle: String? = null
    var note: String? = null
    var birthday: String? = null
    val phones = mutableListOf<DevicePhone>()
    val emails = mutableListOf<DeviceEmail>()
    val addresses = mutableListOf<DeviceAddress>()

    fun toDeviceContact(): DeviceContact {
        val composed = listOf(givenName, middleName ?: "", familyName)
            .filter { it.isNotBlank() }.joinToString(" ")
        return DeviceContact(
            recordID = recordID,
            displayName = composed.ifBlank { displayName },
            givenName = givenName,
            middleName = middleName,
            familyName = familyName,
            nickname = nickname,
            company = company,
            jobTitle = jobTitle,
            note = note,
            birthday = birthday,
            phones = phones,
            emails = emails,
            addresses = addresses,
            hasPhoto = false,
        )
    }
}

/* ---------------- notifications ----------------
   AndroidNotify + AlarmReceiver + NotificationActionReceiver now live in
   NotifyActuals.kt (v1.6.16 rich notifications: logo large icon, brand
   color, kind subtext, BigText body, Done / Snooze quick actions). */

/** Re-arm reminders after reboot (RECEIVE_BOOT_COMPLETED). */
class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Read the persisted store and re-schedule every pending reminder
            // directly through the alarm manager — notifications survive
            // reboots even when the app is not opened first.
            val pending = goAsync()
            Thread {
                try {
                    if (!AndroidPlatform.isReady()) AndroidPlatform.init(context)
                    val data = com.callmony.lib.Db.loadAll()
                    // make t() speak the user's language in this headless
                    // process (labels ride with the scheduled notification)
                    com.callmony.lib.AppStore.state.settings = data.settings
                    val notify = Platform.notify as? AndroidNotify ?: return@Thread
                    fun kindLabelOf(kind: String): String = when (kind) {
                        "payment" -> com.callmony.i18n.t("rm.kindPayment")
                        "todo" -> com.callmony.i18n.t("rm.kindTodo")
                        "birthday" -> com.callmony.i18n.t("rm.kindBirthday")
                        "followup" -> com.callmony.i18n.t("rm.kindFollowup")
                        else -> com.callmony.i18n.t("rm.kindCustom")
                    }
                    for (r in data.reminders.filter { it.deletedAt == null && !it.done }) {
                        val next = if (r.repeat != "none")
                            com.callmony.lib.nextOccurrence(r.dueAt, r.repeat)
                        else r.dueAt
                        if (next > System.currentTimeMillis()) {
                            notify.schedule(
                                r.id, r.title, r.notes ?: "", next, r.kind,
                                kindLabelOf(r.kind),
                                com.callmony.i18n.t("act.done"),
                                com.callmony.i18n.t("rm.snooze10"),
                            )
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    pending.finish()
                }
            }.start()
        }
    }
}

/* ---------------- share / links / clipboard ---------------- */

class AndroidShare : ShareService {
    override fun openUrl(url: String) {
        val ctx = AndroidPlatform.appContext
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {}
    }

    override fun openSms(number: String, text: String?) {
        // ACTION_SENDTO + smsto: resolves to the DEFAULT messaging app and
        // pre-fills the conversation — the seamless handoff (plain sms:
        // through ACTION_VIEW only reaches apps that declare it).
        val ctx = AndroidPlatform.appContext
        try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!text.isNullOrEmpty()) intent.putExtra("sms_body", text)
            ctx.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("sms:${Uri.encode(number)}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!text.isNullOrEmpty()) fallback.putExtra("sms_body", text)
                ctx.startActivity(fallback)
            } catch (_: Exception) {}
        }
    }

    override fun canOpenUrl(url: String): Boolean {
        val ctx = AndroidPlatform.appContext
        return try {
            ctx.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)), 0,
            ).isNotEmpty()
        } catch (_: Exception) { false }
    }

    override fun shareText(text: String, title: String) {
        val ctx = AndroidPlatform.appContext
        try {
            ctx.startActivity(
                Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    putExtra(Intent.EXTRA_TITLE, title)
                }, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {}
    }

    // v1.6.26 — share content as a REAL FILE: written under cacheDir/backups
    // and served through the FileProvider (manifest + res/xml/file_paths.xml),
    // so the system sheet offers "Save to Files / Drive / Save to Downloads"
    // and every messaging app attaches an actual .json/.csv document. This
    // is the backbone of the no-data-loss backup story — a backup saved to
    // the phone's Downloads SURVIVES an uninstall+reinstall.
    override fun shareFile(fileName: String, content: String, mime: String) {
        val ctx = AndroidPlatform.appContext
        try {
            val dir = File(ctx.cacheDir, "backups").apply { mkdirs() }
            val f = File(dir, fileName)
            f.writeText(content, Charsets.UTF_8)
            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
            ctx.startActivity(
                Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TITLE, fileName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
            // fall back to the plain-text share (provider missing, IO error…)
            shareText(content, fileName)
        }
    }

    // v1.6.26 — pick a backup file from anywhere (SAF). The picker runs in a
    // dedicated transparent activity (FilePickActivity) because ACTION_OPEN_DOCUMENT
    // results only arrive on an Activity; the activity writes the file's text
    // into the pending callback and finishes.
    override fun pickFile(mime: String, onText: (String?) -> Unit) {
        val ctx = AndroidPlatform.appContext
        if (!FilePickActivity.request(ctx, mime, onText)) onText(null)
    }

    override fun copyText(text: String) {
        try {
            val cm = AndroidPlatform.appContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("callmony", text))
        } catch (_: Exception) {}
    }

    override fun readClipboard(): String? {
        return try {
            val cm = AndroidPlatform.appContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.primaryClip?.getItemAt(0)?.coerceToText(AndroidPlatform.appContext)?.toString()
        } catch (_: Exception) { null }
    }
}

/* ---------------- AsyncStorage equivalent ---------------- */

class AndroidKv(private val ctx: Context) : KvStore {
    private val prefs = ctx.getSharedPreferences("callmony-store", Context.MODE_PRIVATE)
    private val docsDir: File
        get() = File(ctx.filesDir, "documents").apply { mkdirs() }

    override fun read(key: String): String? {
        // v1.6.15 DATA-SAFETY FIX: the mirror key is written on every save
        // but reads NEVER fell back to it — a corrupted primary row silently
        // returned null (data "loss"). Now: primary → "~m" mirror.
        return try {
            prefs.getString(key, null) ?: prefs.getString("$key~m", null)
        } catch (_: Exception) { null }
    }

    override fun write(key: String, value: String) {
        // v1.6.15 DATA-SAFETY: commit() (synchronous, returns success)
        // instead of apply() (async — a hard process kill could drop the
        // last writes). The store saves on background coroutines, so the
        // main thread never blocks on this.
        val editor = prefs.edit().putString(key, value)
        editor.putString("$key~m", value) // mirror (single atomic commit)
        editor.commit()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).remove("$key~m").commit()
    }

    override fun readFile(name: String): String? = try {
        val f = File(docsDir, name)
        when {
            f.exists() -> f.readText()
            // v1.6.15 DATA-SAFETY: fall back to the rotated backup when the
            // main file is missing (e.g. a crash between rename steps).
            else -> File(docsDir, "$name.bak").takeIf { it.exists() }?.readText()
        }
    } catch (_: Exception) {
        try { File(docsDir, "$name.bak").readText() } catch (_: Exception) { null }
    }

    override fun writeFile(name: String, content: String) {
        // v1.6.15 DATA-SAFETY: ATOMIC write — tmp file → fsync → rename over
        // the target, keeping the previous good file as ".bak". A crash or
        // disk-full can never leave a half-written/truncated file behind
        // (the old direct writeText destroyed the previous data on failure).
        try {
            val dir = docsDir
            val target = File(dir, name)
            val tmp = File(dir, "$name.tmp")
            val bak = File(dir, "$name.bak")
            tmp.outputStream().use { os ->
                os.write(content.toByteArray(Charsets.UTF_8))
                os.flush()
                os.fd.sync()
            }
            if (target.exists()) {
                try { if (bak.exists()) bak.delete() } catch (_: Exception) {}
                try { target.copyTo(bak, overwrite = true) } catch (_: Exception) {}
            }
            if (!tmp.renameTo(target)) {
                // cross-filesystem rename safety net
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } catch (_: Exception) {
            try { File(docsDir, name).writeText(content) } catch (_: Exception) {}
        }
    }

    override fun deleteFile(name: String) {
        try {
            File(docsDir, name).delete()
            File(docsDir, "$name.bak").delete()
            File(docsDir, "$name.tmp").delete()
        } catch (_: Exception) {}
    }

    override fun fileExists(name: String): Boolean = File(docsDir, name).exists()

    override fun listFiles(dir: String): List<String> = try {
        File(docsDir, dir).listFiles()?.map { it.name } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    override fun makeDir(dir: String) {
        try { File(docsDir, dir).mkdirs() } catch (_: Exception) {}
    }
}

/* ---------------- UI hooks ---------------- */

class AndroidUi(private val ctx: Context) : UiPlatform {
    override val osDarkFlow = MutableStateFlow(currentSystemDark())

    private fun currentSystemDark(): Boolean =
        (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    fun onUiModeChanged() {
        osDarkFlow.value = currentSystemDark()
    }

    override fun setStatusBarMode(dark: Boolean) {
        // handled through enableEdgeToEdge + Compose in MainActivity
    }

    override fun setNavColor(rgba: Long) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.NOTIFICATION_SERVICE)
                // navigation bar tint on 26+ handled by window; no-op here
            } catch (_: Exception) {}
        }
    }
}

