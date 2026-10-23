package com.callmony

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.provider.ContactsContract
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap

/**
 * v1.6.1 — safe in-app contact editing (replaces react-native-contacts'
 * updateContact for Android).
 *
 *  WHY: the library's updateContact has two destructive habits —
 *   1. it runs an UPDATE over `contact_id = ?` that also rewrites the
 *      MIMETYPE of EVERY data row of the contact (corrupting notes,
 *      addresses, events…), and
 *   2. it deletes + re-inserts phone rows scoped to ONE rawContactId, while
 *      getContactById returns numbers spread across ALL of the contact's
 *      raw rows (Google + Phone/WhatsApp accounts) — the mismatch made
 *      contacts LOSE numbers after an in-app edit (user report v1.6.0),
 *      and its AsyncTask-based bridge can die without settling the promise
 *      (the forever-spinner the user could not dismiss).
 *
 *  This module instead:
 *   - loadContact()  reads the Data table directly, ALWAYS settles the
 *                   promise (try/catch, own worker thread) and returns every
 *                   phone/e-mail of the contact grouped across raw rows.
 *   - saveContact() applies a surgically-scoped batch: StructuredName and
 *                   Organization rows are updated BY ROW ID, phones/emails
 *                   are deleted by (mimetype AND contact_id) and re-inserted
 *                   under the contact's first raw row. Notes, addresses,
 *                   birthdays and photos are never touched.
 */
class ContactEditModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        const val NAME = "CallMonyContactEdit"
    }

    override fun getName(): String = NAME

    /* ---------------- label <-> type mapping ---------------- */

    private fun phoneTypeFromLabel(label: String?): Int = when (label) {
        "home" -> ContactsContract.CommonDataKinds.Phone.TYPE_HOME
        "work" -> ContactsContract.CommonDataKinds.Phone.TYPE_WORK
        "mobile" -> ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
        "main" -> ContactsContract.CommonDataKinds.Phone.TYPE_MAIN
        "other" -> ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
        else -> ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
    }

    private fun labelFromPhoneType(type: Int): String = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "home"
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "work"
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "mobile"
        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "main"
        else -> "other"
    }

    private fun emailTypeFromLabel(label: String?): Int = when (label) {
        "home" -> ContactsContract.CommonDataKinds.Email.TYPE_HOME
        "work" -> ContactsContract.CommonDataKinds.Email.TYPE_WORK
        "mobile" -> ContactsContract.CommonDataKinds.Email.TYPE_MOBILE
        "other" -> ContactsContract.CommonDataKinds.Email.TYPE_OTHER
        else -> ContactsContract.CommonDataKinds.Email.TYPE_OTHER
    }

    private fun labelFromEmailType(type: Int): String = when (type) {
        ContactsContract.CommonDataKinds.Email.TYPE_HOME -> "home"
        ContactsContract.CommonDataKinds.Email.TYPE_WORK -> "work"
        ContactsContract.CommonDataKinds.Email.TYPE_MOBILE -> "mobile"
        else -> "other"
    }

    /* ---------------- raw rows of one contact ---------------- */

    private fun rawContactIds(cr: ContentResolver, contactId: String): List<String> {
        val ids = mutableListOf<String>()
        cr.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            ContactsContract.RawContacts.CONTACT_ID + " = ? AND " + ContactsContract.RawContacts.DELETED + " = 0",
            arrayOf(contactId),
            null
        )?.use { c ->
            while (c.moveToNext()) ids.add(c.getString(0))
        }
        return ids
    }

    /* ---------------- load ---------------- */

    @ReactMethod
    fun loadContact(contactId: String, promise: Promise) {
        Thread {
            try {
                val cr = reactApplicationContext.contentResolver
                val map = buildLoadMap(cr, contactId)
                if (map == null) promise.resolve(null)
                else promise.resolve(map)
            } catch (e: Exception) {
                promise.reject("loadContact", e.message ?: "load failed", e)
            }
        }.start()
    }

    private fun buildLoadMap(cr: ContentResolver, contactId: String): WritableMap? {
        val rawIds = rawContactIds(cr, contactId)
        if (rawIds.isEmpty()) return null

        val map = Arguments.createMap()
        map.putString("recordID", contactId)
        val rawArr = Arguments.createArray()
        rawIds.forEach { rawArr.pushString(it) }
        map.putArray("rawContactIds", rawArr)

        var given: String? = null
        var family: String? = null
        var middle: String? = null
        var company: String? = null
        var jobTitle: String? = null
        val phones = Arguments.createArray()
        val emails = Arguments.createArray()

        cr.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data._ID,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME,
                ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
                ContactsContract.CommonDataKinds.Organization.COMPANY,
                ContactsContract.CommonDataKinds.Organization.TITLE,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.TYPE
            ),
            ContactsContract.Data.CONTACT_ID + " = ?",
            arrayOf(contactId),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                when (c.getString(1)) {
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                        if (given == null) {
                            given = c.getString(2)
                            middle = c.getString(3)
                            family = c.getString(4)
                        }
                    }
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                        if (company == null) {
                            company = c.getString(5)
                            jobTitle = c.getString(6)
                        }
                    }
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        val number = c.getString(7)
                        if (!number.isNullOrEmpty()) {
                            val p = Arguments.createMap()
                            p.putString("id", id)
                            p.putString("label", labelFromPhoneType(c.getInt(8)))
                            p.putString("number", number)
                            phones.pushMap(p)
                        }
                    }
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                        val address = c.getString(9)
                        if (!address.isNullOrEmpty()) {
                            val e = Arguments.createMap()
                            e.putString("id", id)
                            e.putString("label", labelFromEmailType(c.getInt(10)))
                            e.putString("email", address)
                            emails.pushMap(e)
                        }
                    }
                }
            }
        }

        map.putString("givenName", given ?: "")
        map.putString("middleName", middle ?: "")
        map.putString("familyName", family ?: "")
        map.putString("company", company ?: "")
        map.putString("jobTitle", jobTitle ?: "")
        map.putArray("phoneNumbers", phones)
        map.putArray("emailAddresses", emails)
        return map
    }

    /* ---------------- save ---------------- */

    @ReactMethod
    fun saveContact(fields: ReadableMap, promise: Promise) {
        Thread {
            try {
                val cr = reactApplicationContext.contentResolver
                val contactId = fields.getString("recordID")
                    ?: throw IllegalArgumentException("recordID missing")
                val rawIds = rawContactIds(cr, contactId)
                if (rawIds.isEmpty()) throw IllegalStateException("contact not found")
                val primaryRaw = rawIds.first()

                val given = fields.getString("givenName") ?: ""
                val middle = fields.getString("middleName") ?: ""
                val family = fields.getString("familyName") ?: ""
                val company = fields.getString("company") ?: ""
                val jobTitle = fields.getString("jobTitle") ?: ""
                val phones = fields.getArray("phoneNumbers")
                val emails = fields.getArray("emailAddresses")

                val ops = arrayListOf<ContentProviderOperation>()
                val dataUri = ContactsContract.Data.CONTENT_URI
                val structured = ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                val organization = ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
                val phoneItem = ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                val emailItem = ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE

                /* 1 — StructuredName: update the EXISTING rows by row id (one per
                       raw contact); insert one when the contact has none. */
                val nameRowIds = mutableListOf<String>()
                cr.query(
                    dataUri,
                    arrayOf(ContactsContract.Data._ID),
                    ContactsContract.Data.CONTACT_ID + " = ? AND " + ContactsContract.Data.MIMETYPE + " = ?",
                    arrayOf(contactId, structured),
                    null
                )?.use { c ->
                    while (c.moveToNext()) nameRowIds.add(c.getString(0))
                }
                if (nameRowIds.isEmpty()) {
                    ops.add(
                        ContentProviderOperation.newInsert(dataUri)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, primaryRaw)
                            .withValue(ContactsContract.Data.MIMETYPE, structured)
                            .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, given)
                            .withValue(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, middle)
                            .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, family)
                            .build()
                    )
                } else {
                    nameRowIds.forEach { rowId ->
                        ops.add(
                            ContentProviderOperation.newUpdate(dataUri)
                                .withSelection(ContactsContract.Data._ID + " = ?", arrayOf(rowId))
                                .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, given)
                                .withValue(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, middle)
                                .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, family)
                                .build()
                        )
                    }
                }

                /* 2 — Organization: update existing rows by id; insert one only
                       when there is something to store. */
                val orgRowIds = mutableListOf<String>()
                cr.query(
                    dataUri,
                    arrayOf(ContactsContract.Data._ID),
                    ContactsContract.Data.CONTACT_ID + " = ? AND " + ContactsContract.Data.MIMETYPE + " = ?",
                    arrayOf(contactId, organization),
                    null
                )?.use { c ->
                    while (c.moveToNext()) orgRowIds.add(c.getString(0))
                }
                if (orgRowIds.isEmpty()) {
                    if (company.isNotEmpty() || jobTitle.isNotEmpty()) {
                        ops.add(
                            ContentProviderOperation.newInsert(dataUri)
                                .withValue(ContactsContract.Data.RAW_CONTACT_ID, primaryRaw)
                                .withValue(ContactsContract.Data.MIMETYPE, organization)
                                .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, company)
                                .withValue(ContactsContract.CommonDataKinds.Organization.TITLE, jobTitle)
                                .build()
                        )
                    }
                } else {
                    orgRowIds.forEach { rowId ->
                        ops.add(
                            ContentProviderOperation.newUpdate(dataUri)
                                .withSelection(ContactsContract.Data._ID + " = ?", arrayOf(rowId))
                                .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, company)
                                .withValue(ContactsContract.CommonDataKinds.Organization.TITLE, jobTitle)
                                .build()
                        )
                    }
                }

                /* 3 — Phones: delete EVERY phone row of the contact (across all
                       raw rows) then insert the new list under the primary raw
                       row. Scoped by mimetype + contact_id — notes/addresses/
                       birthday/photo rows are never touched. */
                ops.add(
                    ContentProviderOperation.newDelete(dataUri)
                        .withSelection(
                            ContactsContract.Data.MIMETYPE + " = ? AND " + ContactsContract.Data.CONTACT_ID + " = ?",
                            arrayOf(phoneItem, contactId)
                        )
                        .build()
                )
                forEachEntry(phones) { label, values ->
                    ops.add(
                        ContentProviderOperation.newInsert(dataUri)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, primaryRaw)
                            .withValue(ContactsContract.Data.MIMETYPE, phoneItem)
                            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, values[0])
                            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, phoneTypeFromLabel(label))
                            .build()
                    )
                }

                /* 4 — Emails: same delete + re-insert pattern. */
                ops.add(
                    ContentProviderOperation.newDelete(dataUri)
                        .withSelection(
                            ContactsContract.Data.MIMETYPE + " = ? AND " + ContactsContract.Data.CONTACT_ID + " = ?",
                            arrayOf(emailItem, contactId)
                        )
                        .build()
                )
                forEachEntry(emails) { label, values ->
                    ops.add(
                        ContentProviderOperation.newInsert(dataUri)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, primaryRaw)
                            .withValue(ContactsContract.Data.MIMETYPE, emailItem)
                            .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, values[0])
                            .withValue(ContactsContract.CommonDataKinds.Email.TYPE, emailTypeFromLabel(label))
                            .build()
                    )
                }

                cr.applyBatch(ContactsContract.AUTHORITY, ops)
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("saveContact", e.message ?: "save failed", e)
            }
        }.start()
    }

    /** iterate [{label, number|email}, …] skipping empty values */
    private inline fun forEachEntry(
        arr: ReadableArray?,
        block: (label: String?, values: List<String>) -> Unit
    ) {
        if (arr == null) return
        for (i in 0 until arr.size()) {
            val m: ReadableMap = arr.getMap(i) ?: continue
            val number = m.getString("number")
            val email = m.getString("email")
            val value = number ?: email ?: ""
            if (value.isNullOrBlank()) continue
            block(m.getString("label"), listOf(value))
        }
    }
}
