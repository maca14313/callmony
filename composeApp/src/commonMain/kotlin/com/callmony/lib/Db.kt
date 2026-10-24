package com.callmony.lib

import com.callmony.model.BankAccount
import com.callmony.model.CalcList
import com.callmony.model.CallLogEntry
import com.callmony.model.ContactX
import com.callmony.model.LedgerEntry
import com.callmony.model.LedgerGroup
import com.callmony.model.MessageLog
import com.callmony.model.Note
import com.callmony.model.Reminder
import com.callmony.model.Settings
import com.callmony.platform.Platform
import kotlinx.serialization.json.Json

/* Local persistence — one key per collection, JSON serialized (the exact
   scheme of the React Native edition's AsyncStorage layer, including the
   mirrored safety copy + shape-sanitized reads). */

object Db {
    private object K {
        const val contactX = "callmony.contactX"
        const val entries = "callmony.entries"
        const val groups = "callmony.groups"
        const val lists = "callmony.lists"
        const val notes = "callmony.notes"
        const val reminders = "callmony.reminders"
        const val banks = "callmony.banks"
        const val recents = "callmony.recents"
        const val messages = "callmony.messages"
        const val settings = "callmony.settings"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        coerceInputValues = true
    }

    val DEFAULT_SETTINGS = Settings()

    private fun mirrorKey(key: String): String =
        if (key.startsWith("callmony.")) "callmony.mirror." + key.removePrefix("callmony.") else key

    /* v1.5.1 — shape-sanitized read: corrupted JSON must never crash boot. */
    private fun readRaw(key: String): String? {
        val kv = Platform.kv
        val raw = kv.read(key)
        if (raw != null) return raw
        return kv.read(mirrorKey(key))
    }

    fun loadAll(): PersistedData {
        val settingsRaw = readRaw(K.settings)
        val settings: Settings = try {
            if (settingsRaw != null) {
                val s = json.decodeFromString(Settings.serializer(), settingsRaw)
                s.copy(pinHash = null) // PIN never persists here (v1.6.9)
            } else DEFAULT_SETTINGS
        } catch (_: Exception) { DEFAULT_SETTINGS }
        return PersistedData(
            contactX = readList(K.contactX) { json.decodeFromString(ContactX.serializer(), it) },
            entries = readList(K.entries) { json.decodeFromString(LedgerEntry.serializer(), it) },
            groups = readList(K.groups) { json.decodeFromString(LedgerGroup.serializer(), it) },
            lists = readList(K.lists) { json.decodeFromString(CalcList.serializer(), it) },
            notes = readList(K.notes) { json.decodeFromString(Note.serializer(), it) },
            reminders = readList(K.reminders) { json.decodeFromString(Reminder.serializer(), it) },
            banks = readList(K.banks) { json.decodeFromString(BankAccount.serializer(), it) },
            recents = readList(K.recents) { json.decodeFromString(CallLogEntry.serializer(), it) },
            messages = readList(K.messages) { json.decodeFromString(MessageLog.serializer(), it) },
            settings = settings,
        )
    }

    /** Read a JSON array of arbitrary elements with per-element fallback. */
    private fun <T> readList(key: String, decode: (String) -> T): List<T> {
        val raw = readRaw(key) ?: return emptyList()
        return try {
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(raw)
            val out = mutableListOf<T>()
            for (el in arr.jsonArrayOrEmpty()) {
                try { out.add(decode(el.toString())) } catch (_: Exception) { /* skip bad row */ }
            }
            out
        } catch (_: Exception) { emptyList() }
    }

    private fun kotlinx.serialization.json.JsonElement.jsonArrayOrEmpty(): List<kotlinx.serialization.json.JsonElement> =
        (this as? kotlinx.serialization.json.JsonArray)?.toList() ?: emptyList()

    fun saveContactX(v: List<ContactX>) = writeArr(K.contactX, v, ContactX.serializer())
    fun saveEntries(v: List<LedgerEntry>) = writeArr(K.entries, v, LedgerEntry.serializer())
    fun saveGroups(v: List<LedgerGroup>) = writeArr(K.groups, v, LedgerGroup.serializer())
    fun saveLists(v: List<CalcList>) = writeArr(K.lists, v, CalcList.serializer())
    fun saveNotes(v: List<Note>) = writeArr(K.notes, v, Note.serializer())
    fun saveReminders(v: List<Reminder>) = writeArr(K.reminders, v, Reminder.serializer())
    fun saveBanks(v: List<BankAccount>) = writeArr(K.banks, v, BankAccount.serializer())
    fun saveRecents(v: List<CallLogEntry>) = writeArr(K.recents, v, CallLogEntry.serializer())
    fun saveMessages(v: List<MessageLog>) = writeArr(K.messages, v, MessageLog.serializer())

    fun saveSettings(s: Settings) {
        try {
            val persistable = s.copy(pinHash = null)
            Platform.kv.write(K.settings, json.encodeToString(Settings.serializer(), persistable))
        } catch (_: Exception) {}
    }

    private fun <T> writeArr(key: String, v: List<T>, serializer: kotlinx.serialization.KSerializer<T>) {
        try {
            val text = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(serializer), v)
            Platform.kv.write(key, text) // KvStore.write mirrors automatically
        } catch (_: Exception) {}
    }

    fun wipeStorage() {
        for (k in listOf(K.contactX, K.entries, K.groups, K.lists, K.notes, K.reminders, K.banks, K.recents, K.messages, K.settings)) {
            Platform.kv.remove(k)
            Platform.kv.remove(mirrorKey(k))
        }
        // PIN file
        Platform.kv.deleteFile("pin.json")
    }

    /* ---------------- PIN file (no_backup equivalent) ---------------- */

    fun readPinFile(): PinHashResult? {
        val raw = Platform.kv.readFile("pin.json") ?: return null
        return try {
            val el = kotlinx.serialization.json.Json.parseToJsonElement(raw)
            val obj = el as? kotlinx.serialization.json.JsonObject ?: return null
            PinHashResult(
                obj["hash"]?.jsonPrimitiveOrNull()?.content ?: return null,
                obj["salt"]?.jsonPrimitiveOrNull()?.content ?: return null,
            )
        } catch (_: Exception) { null }
    }

    fun writePinFile(hash: String, salt: String) {
        try {
            Platform.kv.writeFile("pin.json", """{"hash":"$hash","salt":"$salt"}""")
        } catch (_: Exception) {}
    }

    fun removePinFile() { Platform.kv.deleteFile("pin.json") }

    private fun kotlinx.serialization.json.JsonElement.jsonPrimitiveOrNull(): kotlinx.serialization.json.JsonPrimitive? =
        this as? kotlinx.serialization.json.JsonPrimitive
}

data class PersistedData(
    val contactX: List<ContactX>,
    val entries: List<LedgerEntry>,
    val groups: List<LedgerGroup>,
    val lists: List<CalcList>,
    val notes: List<Note>,
    val reminders: List<Reminder>,
    val banks: List<BankAccount>,
    val recents: List<CallLogEntry>,
    val messages: List<MessageLog>,
    val settings: Settings,
)

/* ---------------- backup file ---------------- */

object Backup {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    /** Full PersistedData JSON export (PIN excluded by design). */
    fun exportAll(): String {
        val data = Db.loadAll()
        return json.encodeToString(PersistedDataDto.serializer(), PersistedDataDto.from(data))
    }

    /** Parse a backup JSON into PersistedData (null when not valid). */
    fun parse(text: String): PersistedData? = try {
        json.decodeFromString(PersistedDataDto.serializer(), text).toData()
    } catch (_: Exception) {
        null
    }
}

@kotlinx.serialization.Serializable
data class PersistedDataDto(
    val app: String = "callmony",
    val version: Int = 1,
    val exportedAt: Long = 0,
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
) {
    fun toData(): PersistedData = PersistedData(
        contactX = contactX, entries = entries, groups = groups, lists = lists,
        notes = notes, reminders = reminders, banks = banks, recents = recents,
        messages = messages, settings = settings,
    )

    companion object {
        fun from(d: PersistedData): PersistedDataDto = PersistedDataDto(
            exportedAt = now(),
            contactX = d.contactX, entries = d.entries, groups = d.groups, lists = d.lists,
            notes = d.notes, reminders = d.reminders, banks = d.banks, recents = d.recents,
            messages = d.messages, settings = d.settings.copy(pinHash = null),
        )
    }
}
