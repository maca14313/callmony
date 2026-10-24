package com.callmony.model

import kotlinx.serialization.Serializable

/* ---------------------------------------------------------------
   callmony — data model (Kotlin Multiplatform edition)
   An exact port of the React Native edition's src/lib/types.ts.
   Contacts come from the DEVICE; app data (ledger / lists / notes /
   reminders / banks / settings) lives in local JSON persistence.
   Every record: id, createdAt, updatedAt, deletedAt.
   ownerId: "general" | "c:<device recordID>"
---------------------------------------------------------------- */

typealias OwnerId = String

fun contactOwnerId(recordID: String): OwnerId = "c:$recordID"
fun ownerIdToRecordId(ownerId: String): String? = if (ownerId.startsWith("c:")) ownerId.substring(2) else null
val GENERAL_OWNER = "general"

/* ---------------- device contacts ---------------- */

@Serializable
data class DevicePhone(
    val id: String,
    val label: String, // mobile | work | home | main | office | other | custom
    val number: String,
)

@Serializable
data class DeviceEmail(
    val id: String,
    val label: String,
    val address: String,
)

@Serializable
data class DeviceAddress(
    val id: String,
    val label: String,
    val formatted: String,
)

/** A contact from the phone's address book (normalized). */
@Serializable
data class DeviceContact(
    val recordID: String,
    val displayName: String, // given + middle + family (or company fallback)
    val givenName: String,
    val middleName: String? = null,
    val familyName: String,
    val nickname: String? = null,
    val company: String? = null,
    val jobTitle: String? = null,
    val note: String? = null,
    val birthday: String? = null, // YYYY-MM-DD (normalized from the OS)
    val phones: List<DevicePhone> = emptyList(),
    val emails: List<DeviceEmail> = emptyList(),
    val addresses: List<DeviceAddress> = emptyList(),
    val thumbnailPath: String? = null,
    val hasPhoto: Boolean = false,
)

/** A social-media handle attached app-side to a device contact. */
@Serializable
data class SocialLink(
    val id: String,
    val app: String, // telegram | whatsapp | signal | viber | instagram | x | other
    val handle: String, // username, phone or URL
)

/** App-local extensions layered on top of a device contact. */
@Serializable
data class ContactX(
    val recordID: String,
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val pinnedNote: String? = null,
    val speedDial: String? = null, // '2'..'9'
    val socials: List<SocialLink> = emptyList(),
    val primaryPhone: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/* ---------------- money ledger ---------------- */

/** A single payment record — every payment keeps its own row. */
@Serializable
data class Payment(
    val id: String,
    val amount: Double,
    val date: String, // YYYY-MM-DD
    val note: String? = null,
    val method: String? = null,
    val auto: String? = null, // "tick" | "settle" — created by ticking a row / settle-up
    val refs: List<LinkRef> = emptyList(),
    val createdAt: Long = 0L,
)

/** One shopping-style line on a ledger entry — name + qty (+ unit) × unit price. */
@Serializable
data class EntryItem(
    val id: String,
    val name: String,
    val qty: Double,
    val unit: String? = null,
    val price: Double,
)

typealias EntryKind = String // income | expense | credit | debit

@Serializable
data class LedgerEntry(
    val id: String,
    val ownerId: OwnerId,
    val type: String, // "credit" | "debit" — math: credit = +, debit = −
    val kind: EntryKind? = null,
    val amount: Double,
    val items: List<EntryItem> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val date: String, // YYYY-MM-DD
    val dueDate: String? = null,
    val category: String? = null,
    val method: String? = null,
    val note: String? = null,
    val listId: String? = null,
    val groupId: String? = null,
    val refs: List<LinkRef> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

@Serializable
data class LedgerGroup(
    val id: String,
    val ownerId: OwnerId,
    val title: String,
    val note: String? = null,
    val refs: List<LinkRef> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

/* ---------------- calculating lists ---------------- */

@Serializable
data class ListDiscount(
    val type: String, // "pct" | "amount"
    val value: Double,
)

@Serializable
data class ListItem(
    val id: String,
    val name: String,
    val qty: Double,
    val unit: String? = null,
    val unitPrice: Double,
    val discount: ListDiscount? = null,
    val taxPct: Double? = null,
    val payments: List<Payment> = emptyList(),
    val checked: Boolean = false,
    val date: String? = null,
    val dueDate: String? = null,
    val priority: String? = null, // low | medium | high
    val note: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

typealias ListKind = String // calculating | simple | checklist | todo
typealias Repeat = String // none | daily | weekly | monthly | yearly

@Serializable
data class CalcList(
    val id: String,
    val ownerId: OwnerId,
    val kind: ListKind,
    val title: String,
    val note: String? = null,
    val discount: ListDiscount? = null,
    val taxPct: Double? = null,
    val extraCharge: Double? = null,
    val isTemplate: Boolean = false,
    val postToLedger: Boolean = false,
    val repeat: Repeat = "none",
    val currency: String? = null,
    val items: List<ListItem> = emptyList(),
    val refs: List<LinkRef> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

/* ---------------- links / notes / reminders / banks ---------------- */

@Serializable
data class LinkRef(
    val type: String, // contact | entry | list | note | reminder | bank | item | link
    val id: String,
    val label: String? = null,
    val url: String? = null,
)

@Serializable
data class Note(
    val id: String,
    val ownerId: OwnerId,
    val title: String,
    val body: String,
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val color: String? = null,
    val links: List<LinkRef> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

typealias ReminderKind = String // payment | todo | birthday | followup | custom

@Serializable
data class Reminder(
    val id: String,
    val ownerId: OwnerId,
    val title: String,
    val notes: String? = null,
    val kind: ReminderKind,
    val dueAt: Long, // epoch ms
    val repeat: Repeat = "none",
    val leadMinutes: List<Int> = emptyList(),
    val links: List<LinkRef> = emptyList(),
    val done: Boolean = false,
    val doneAt: Long? = null,
    val snoozedUntil: Long? = null,
    val lastFiredAt: Long? = null,
    val firedLeads: List<Int> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

@Serializable
data class BankAccount(
    val id: String,
    val ownerId: OwnerId,
    val label: String,
    val holderName: String? = null,
    val bankName: String? = null,
    val accountNumber: String? = null,
    val iban: String? = null,
    val swift: String? = null,
    val branch: String? = null,
    val notes: String? = null,
    val balance: Double? = null,
    val currency: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

/* ---------------- logs ---------------- */

@Serializable
data class CallLogEntry(
    val id: String,
    val phone: String,
    val ownerId: OwnerId? = null,
    val direction: String, // incoming | outgoing | missed
    val at: Long,
    val duration: Int, // seconds
    val via: String = "app", // app | tel
    val createdAt: Long = 0L,
)

@Serializable
data class MessageLog(
    val id: String,
    val ownerId: OwnerId,
    val channel: String, // whatsapp | sms | telegram | email | other
    val text: String,
    val date: Long,
    val createdAt: Long = 0L,
)

/* ---------------- settings / backup ---------------- */

@Serializable
data class PinHash(
    val hash: String,
    val salt: String,
)

@Serializable
data class Settings(
    val theme: String = "auto", // light | dark | auto
    val language: String = "en", // en | am | om | ar | ti
    val currency: String = "ETB",
    val countryCode: String = "ET",
    val pinHash: PinHash? = null,
    val autolockMin: Int = 1, // 0 = lock immediately on blur
    val hideBalances: Boolean = false,
    val customCategories: List<String> = emptyList(),
    val startTab: String? = "recents",
    val lastBackupAt: Long? = null,
    val notifAsked: Boolean = false,
    val batteryAsked: Boolean = false,
    val blockedNumbers: List<String> = emptyList(),
    val speedDial: Map<String, String> = emptyMap(), // keypad digit → recordID
    val autoBackupEmail: String? = null,
    val autoBackupEvery: Int = 0, // 0 = off · 1 = daily · 7 = weekly
    val lastAutoSyncAt: Long? = null,
    val onboarded: Boolean = false,
    val lastSnapshotAt: Long? = null,
)

@Serializable
data class BackupFile(
    val app: String = "callmony",
    val version: Int = 1,
    val exportedAt: Long,
    val contactX: List<ContactX> = emptyList(),
    val entries: List<LedgerEntry> = emptyList(),
    val groups: List<LedgerGroup> = emptyList(),
    val lists: List<CalcList> = emptyList(),
    val notes: List<Note> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val banks: List<BankAccount> = emptyList(),
    val recents: List<CallLogEntry> = emptyList(),
    val messages: List<MessageLog> = emptyList(),
    val settings: Settings = Settings(),
)

/* ---------------- constants ---------------- */

@Serializable
data class Currency(val code: String, val symbol: String, val label: String)

val CURRENCIES = listOf(
    Currency("ETB", "Br", "Ethiopian Birr (Br)"),
    Currency("USD", "$", "US Dollar ($)"),
    Currency("EUR", "€", "Euro (€)"),
    Currency("GBP", "£", "British Pound (£)"),
    Currency("AED", "د.إ", "UAE Dirham"),
    Currency("SAR", "﷼", "Saudi Riyal"),
    Currency("KES", "KSh", "Kenyan Shilling"),
    Currency("INR", "₹", "Indian Rupee (₹)"),
)

val ENTRY_CATEGORIES = listOf(
    "General", "Loan", "Rent", "Sale", "Salary", "Groceries",
    "Transport", "Utilities", "Health", "School", "Other",
)

val PAYMENT_METHODS = listOf("cash", "bank", "card", "mobile", "other")

@Serializable
data class AvatarPalette(val color: String, val text: String)

val AVATAR_PALETTE = listOf(
    AvatarPalette("#E4EDE7", "#527561"),
    AvatarPalette("#F7E8DF", "#B7785B"),
    AvatarPalette("#E5E8F9", "#6B75B5"),
    AvatarPalette("#F4E5EE", "#A65F88"),
    AvatarPalette("#F4EEDB", "#9B8243"),
    AvatarPalette("#DFF0F0", "#4A8585"),
    AvatarPalette("#F0E9E4", "#8A7360"),
    AvatarPalette("#E7F0DC", "#67813F"),
)

@Serializable
data class CountryCode(val code: String, val dial: String, val name: String)

val COUNTRY_CODES = listOf(
    CountryCode("ET", "251", "Ethiopia"),
    CountryCode("ER", "291", "Eritrea"),
    CountryCode("DJ", "253", "Djibouti"),
    CountryCode("SO", "252", "Somalia"),
    CountryCode("KE", "254", "Kenya"),
    CountryCode("SD", "249", "Sudan"),
    CountryCode("EG", "20", "Egypt"),
    CountryCode("SA", "966", "Saudi Arabia"),
    CountryCode("AE", "971", "UAE"),
    CountryCode("US", "1", "United States"),
    CountryCode("GB", "44", "United Kingdom"),
    CountryCode("CA", "1", "Canada"),
    CountryCode("DE", "49", "Germany"),
    CountryCode("FR", "33", "France"),
    CountryCode("IT", "39", "Italy"),
    CountryCode("ES", "34", "Spain"),
    CountryCode("NL", "31", "Netherlands"),
    CountryCode("IN", "91", "India"),
    CountryCode("NG", "234", "Nigeria"),
    CountryCode("ZA", "27", "South Africa"),
    CountryCode("TZ", "255", "Tanzania"),
    CountryCode("UG", "256", "Uganda"),
)
