package com.callmony.lib

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.callmony.i18n.t
import com.callmony.model.BankAccount
import com.callmony.model.CalcList
import com.callmony.model.CallLogEntry
import com.callmony.model.ContactX
import com.callmony.model.DeviceContact
import com.callmony.model.LedgerEntry
import com.callmony.model.LedgerGroup
import com.callmony.model.LinkRef
import com.callmony.model.ListItem
import com.callmony.model.MessageLog
import com.callmony.model.Note
import com.callmony.model.OwnerId
import com.callmony.model.Payment
import com.callmony.model.PinHash
import com.callmony.model.Reminder
import com.callmony.model.Settings
import com.callmony.model.SocialLink
import com.callmony.platform.DeviceCall
import com.callmony.platform.Platform
import com.callmony.platform.PermState
import com.callmony.platform.ReminderForAlarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.roundToLong

/* callmony — app data store. A faithful port of the React Native edition's
   zustand store (src/lib/store.ts), implemented with Compose snapshot state
   so every screen re-renders exactly where the RN edition re-rendered. */

const val CALLS_FULL_TTL = 10 * 60 * 1000L

data class LastUndo(val label: String, val apply: () -> Unit)

class AppState {
    var hydrated by mutableStateOf(false)
    var deviceContacts by mutableStateOf<List<DeviceContact>>(emptyList())
    var contactsLoadedAt by mutableStateOf(0L)
    var contactsPerm by mutableStateOf(PermState.UNKNOWN)
    var systemCalls by mutableStateOf<List<DeviceCall>>(emptyList())
    var callLogPerm by mutableStateOf(PermState.UNKNOWN)
    var callLogSyncedAt by mutableStateOf(0L)
    // v1.6.19 — TRUE only while a call-log ask/fetch is actually in flight.
    // The boot "Loading calls…" spinner renders ONLY when this is true, so a
    // stalled UNKNOWN permission state can never leave an infinite animation
    // running (the v1.6.18 freeze: the spinner looped forever under the
    // onboarding overlay, saturating the main thread → "isn't responding").
    var callLogBusy by mutableStateOf(false)
    var contactX by mutableStateOf<List<ContactX>>(emptyList())
    var entries by mutableStateOf<List<LedgerEntry>>(emptyList())
    var groups by mutableStateOf<List<LedgerGroup>>(emptyList())
    var lists by mutableStateOf<List<CalcList>>(emptyList())
    var notes by mutableStateOf<List<Note>>(emptyList())
    var reminders by mutableStateOf<List<Reminder>>(emptyList())
    var banks by mutableStateOf<List<BankAccount>>(emptyList())
    var recents by mutableStateOf<List<CallLogEntry>>(emptyList())
    var messages by mutableStateOf<List<MessageLog>>(emptyList())
    var settings by mutableStateOf(Db.DEFAULT_SETTINGS)
    var lastUndo by mutableStateOf<LastUndo?>(null)
}

object AppStore {
    val state = AppState()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /* v1.6.21 — THE SINGLE-WRITER SNAPSHOT RULE (the v1.6.20 freeze report:
       the main thread blocked ~6 s inside Snapshot.current() on a state
       write while Dispatchers.Default workers were publishing store state —
       hydrate's 16 assignments, the 1239-contact list, the 400-call list…).
       RULE: Compose snapshot state (reads AND writes) is touched ONLY on the
       main dispatcher. Background coroutines do the heavy work (JSON, binder,
       cursors) and then publish through ONE batched main hop — a single
       recomposition, zero cross-thread snapshot lock traffic. */
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Post a state mutation to the main dispatcher from ANY thread or
     *  non-suspend callback (permission callbacks, receivers). */
    fun publish(block: () -> Unit) {
        mainScope.launch { block() }
    }

    /* ---------------- sync call log (v1.6.11 stock-dialer policy) ---------------- */

    private var callLogAskInFlight = false

    fun loadSystemCalls(full: Boolean = false, forceAsk: Boolean = false) {
        scope.launch {
            withContext(Dispatchers.Main) { state.callLogBusy = true }
            try {
                loadSystemCallsInner(full, forceAsk)
            } finally {
                withContext(Dispatchers.Main) { state.callLogBusy = false }
            }
        }
    }

    private suspend fun loadSystemCallsInner(full: Boolean, forceAsk: Boolean) {
        // v1.6.13 FIX: READ_CALL_LOG was NEVER requested — fetchAll()
        // silently returned [] and the perm was even marked GRANTED, so
        // the Recents tab stayed empty and the "allow" card never showed.
        // Ask once, dialer-style; on grant load the log, on deny surface
        // the denied card (state.callLogPerm = DENIED). forceAsk lets the
        // denied card's Allow button re-request.
        if (!Platform.callLog.hasPermission()) {
            val alreadyDenied = withContext(Dispatchers.Main) { state.callLogPerm == PermState.DENIED }
            if ((forceAsk || !alreadyDenied) && !callLogAskInFlight) {
                callLogAskInFlight = true
                Platform.callLog.requestPermission { granted ->
                    // v1.6.21 — the callback can land on any thread: publish
                    publish {
                        callLogAskInFlight = false
                        state.callLogPerm = if (granted) PermState.GRANTED else PermState.DENIED
                        if (granted) loadSystemCalls(full = true)
                    }
                }
            }
            return
        }
        // v1.6.21 — decide WHAT to fetch on MAIN (snapshot reads), then run
        // the cursor/binder work on this (Default) worker, then publish.
        class Plan(val isFull: Boolean, val since: Long, val cur: List<DeviceCall>)
        val plan = withContext(Dispatchers.Main) {
            val cur = state.systemCalls
            val isFull = full || cur.isEmpty() || now() - state.callLogSyncedAt >= CALLS_FULL_TTL
            Plan(isFull, cur.firstOrNull()?.date ?: 0L, cur)
        }
        val merged: List<DeviceCall> = if (plan.isFull) {
            Platform.callLog.fetchAll()
        } else {
            val fresh = Platform.callLog.loadSince(plan.since - 60_000L)
            if (fresh.isEmpty()) {
                withContext(Dispatchers.Main) {
                    state.callLogPerm = PermState.GRANTED
                    state.callLogSyncedAt = now()
                }
                saveCachedCalls(plan.cur)
                return
            }
            val seen = plan.cur.map { it.id }.toHashSet()
            (fresh.filter { it.id !in seen } + plan.cur)
                .sortedByDescending { it.date }
                .take(400)
        }
        val same = withContext(Dispatchers.Main) {
            val cur = state.systemCalls
            val sameNow = merged.size == cur.size &&
                merged.zip(cur).all { (a, b) -> a.id == b.id && a.date == b.date }
            if (!sameNow) state.systemCalls = merged
            state.callLogPerm = PermState.GRANTED
            state.callLogSyncedAt = now()
            sameNow
        }
        if (!same || plan.isFull) saveCachedCalls(merged)
    }

    /* ---------------- hydrate ---------------- */

    fun hydrate() {
        scope.launch {
            Diag.mark("store.read")
            val data = Db.loadAll()
            val callsCache = loadCachedCalls()
            val storedPin = Db.readPinFile()
            // v1.6.20 — persisted freeze/crash reports feed the in-app
            // diagnostics overlay ("the app froze last time — here's why").
            Diag.loadFromStorage()
            // v1.6.21 — ALL state writes (and the t() reads inside the
            // reminder rescheduling below) happen in ONE batched main hop:
            // a single recomposition, and no Default worker ever touches the
            // snapshot system (the v1.6.20 freeze class).
            withContext(Dispatchers.Main) {
                if (data.settings.pinHash != null) {
                    state.settings = data.settings.copy(pinHash = null)
                    Db.saveSettings(state.settings)
                } else {
                    state.settings = data.settings
                }
                state.contactX = data.contactX
                state.entries = data.entries
                state.groups = data.groups
                state.lists = data.lists
                state.notes = data.notes
                state.reminders = data.reminders
                state.banks = data.banks
                state.recents = data.recents
                state.messages = data.messages
                state.hydrated = true
                Diag.mark("store.loaded")
                if (callsCache.second.isNotEmpty()) {
                    state.systemCalls = callsCache.second
                    state.callLogPerm = PermState.GRANTED
                    state.callLogSyncedAt = callsCache.first
                }
                if (storedPin != null) {
                    state.settings = state.settings.copy(pinHash = PinHash(storedPin.hash, storedPin.salt))
                    // v1.6.17 — the PIN actually locks: a fresh launch (or process
                    // restore) with a PIN set opens on the lock screen. Previously
                    // `locked` started false and nothing set it, so the PIN never
                    // guarded anything until the autolock timer happened to fire.
                    UiStore.locked = true
                }
                // (re)schedule reminder notifications after boot
                for (r in data.reminders.filter { it.deletedAt == null && !it.done }) {
                    scheduleReminderNotification(r)
                }
            }
        }
    }

    /* ---------------- undo ---------------- */

    fun pushUndo(label: String, apply: () -> Unit) {
        state.lastUndo = LastUndo(label, apply)
    }

    fun undo() {
        val u = state.lastUndo ?: return
        u.apply()
        state.lastUndo = null
    }

    /* ---------------- device contacts ---------------- */

    private var contactsRefreshInFlight = false

    /* v1.6.23 — the FETCH is the real single-flight unit. The outer
       contactsRefreshInFlight guard above resets when its launching
       coroutine returns — and the permission-grant path returns immediately
       after SPAWNING its fetch — so on a fresh install (permission dialog
       → grant) the grant-callback fetch and the onResume TTL refresh ran
       TWO whole-book AndroidContacts.fetchAll() reads side by side (caught
       live in a v1.6.22 diagnostics report: two DefaultDispatcher workers
       both inside fetchAll). Every caller now funnels through this gate:
       whoever gets it does the read, everyone else returns instantly. */
    private val contactsFetchGate = Mutex()

    fun setDeviceContacts(list: List<DeviceContact>) {
        state.deviceContacts = list
        state.contactsLoadedAt = now()
        saveCachedContacts(list)
    }

    fun refreshDeviceContacts() {
        if (contactsRefreshInFlight) return
        contactsRefreshInFlight = true
        scope.launch {
            try {
                // v1.6.8 — WAIT while a contact editor is open (the freeze fix)
                var waits = 0
                // v1.6.21 — the overlay-stack read is a snapshot read: check on Main
                while (withContext(Dispatchers.Main) { UiStore.isEditingContact() } && waits < 40) {
                    delay(1500)
                    waits++
                }
                if (!Platform.contacts.hasPermission()) {
                    Platform.contacts.requestPermission { granted ->
                        publish {
                            state.contactsPerm = if (granted) PermState.GRANTED else PermState.DENIED
                            if (granted) scope.launch { doFetchContacts() }
                        }
                    }
                    return@launch
                }
                withContext(Dispatchers.Main) { state.contactsPerm = PermState.GRANTED }
                doFetchContacts()
            } finally {
                contactsRefreshInFlight = false
            }
        }
    }

    /** Foreground refresh with a TTL guard (RN parity): returning to the app
     *  re-reads the contact book when the snapshot is older than a minute, so
     *  contacts created/edited in the device Contacts app (or another app)
     *  while Callmony sat in the background appear in search, Recents and the
     *  keypad without a full app restart. Cheap when the TTL hasn't elapsed. */
    fun maybeRefreshContacts(ttlMs: Long = 60000) {
        val last = state.contactsLoadedAt
        if (last != 0L && now() - last < ttlMs) return
        refreshDeviceContacts()
    }

    private suspend fun doFetchContacts() {
        if (!contactsFetchGate.tryLock()) return
        try {
            // v1.6.21 — the address-book read (binder + cursor over every
            // contact) stays on the Default worker; the publish below is ONE
            // batched main hop (the old direct write from the worker was the
            // snapshot-lock contender in the v1.6.20 freeze report).
            val list = Platform.contacts.fetchAll()
            if (list.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    state.deviceContacts = list
                    state.contactsLoadedAt = now()
                }
                saveCachedContacts(list)
            }
        } catch (_: Exception) {
        } finally {
            contactsFetchGate.unlock()
        }
    }

    fun patchDeviceContact(recordID: String, patch: (DeviceContact) -> DeviceContact) {
        val next = state.deviceContacts.map { if (it.recordID == recordID) patch(it) else it }
            .sortedBy { it.displayName.ifEmpty { "~" } }
        state.deviceContacts = next
        saveCachedContacts(next)
    }

    fun addDeviceContactToList(contact: DeviceContact) {
        val list = (state.deviceContacts + contact).sortedBy { it.displayName.ifEmpty { "~" } }
        state.deviceContacts = list
        saveCachedContacts(list)
    }

    private fun upsertContactX(recordID: String, mutate: (ContactX) -> ContactX, create: () -> ContactX) {
        val existing = state.contactX.find { it.recordID == recordID }
        val next = if (existing != null) {
            state.contactX.map { if (it.recordID == recordID) mutate(it) else it }
        } else {
            state.contactX + create()
        }
        state.contactX = next
        Db.saveContactX(next)
    }

    fun toggleFavorite(recordID: String) {
        upsertContactX(
            recordID,
            { it.copy(favorite = !it.favorite, updatedAt = now()) },
            { ContactX(recordID = recordID, favorite = true, createdAt = now(), updatedAt = now()) },
        )
    }

    fun setContactSpeedDial(recordID: String, key: String?) {
        upsertContactX(
            recordID,
            { it.copy(speedDial = key, updatedAt = now()) },
            { ContactX(recordID = recordID, speedDial = key, createdAt = now(), updatedAt = now()) },
        )
    }

    fun addSocial(recordID: String, app: String, handle: String) {
        val entry = SocialLink(id = "soc-${now()}-${(0..9999).random()}", app = app, handle = handle.trim())
        upsertContactX(
            recordID,
            { it.copy(socials = it.socials + entry, updatedAt = now()) },
            { ContactX(recordID = recordID, socials = listOf(entry), createdAt = now(), updatedAt = now()) },
        )
    }

    fun removeSocial(recordID: String, socialId: String) {
        upsertContactX(
            recordID,
            { it.copy(socials = it.socials.filter { it.id != socialId }, updatedAt = now()) },
            { ContactX(recordID = recordID, createdAt = now(), updatedAt = now()) },
        )
    }

    fun setPrimaryPhone(recordID: String, number: String) {
        upsertContactX(
            recordID,
            { it.copy(primaryPhone = number, updatedAt = now()) },
            { ContactX(recordID = recordID, primaryPhone = number, createdAt = now(), updatedAt = now()) },
        )
    }

    /* ---------------- ledger ---------------- */

    fun saveEntry(e: LedgerEntry) {
        val entries = state.entries
        val existing = entries.find { it.id == e.id }
        val rec = if (existing != null) {
            existing.copy(
                ownerId = e.ownerId,
                type = e.type.ifBlank { existing.type },
                kind = e.kind ?: existing.kind,
                amount = if (e.amount != 0.0) e.amount else existing.amount,
                items = if (e.items.isNotEmpty()) e.items else existing.items,
                payments = if (e.payments.isNotEmpty()) e.payments else existing.payments,
                date = e.date.ifBlank { existing.date },
                dueDate = e.dueDate,
                category = e.category,
                method = e.method ?: existing.method,
                note = e.note ?: existing.note,
                listId = e.listId ?: existing.listId,
                groupId = e.groupId,
                refs = if (e.refs.isNotEmpty()) e.refs else existing.refs,
                updatedAt = now(),
            )
        } else e.copy(
            id = e.id.ifBlank { uid() },
            createdAt = now(),
            updatedAt = now(),
        )
        val next = if (existing != null) entries.map { if (it.id == rec.id) rec else it } else listOf(rec) + entries
        val sorted = next.sortedWith(compareByDescending<LedgerEntry> { it.date }.thenByDescending { it.createdAt })
        state.entries = sorted
        Db.saveEntries(sorted)
    }

    fun deleteEntry(id: String) {
        val victim = state.entries.find { it.id == id }
        val next = state.entries.map { if (it.id == id) it.copy(deletedAt = now(), updatedAt = now()) else it }
        state.entries = next
        Db.saveEntries(next)
        if (victim != null) pushUndo("Entry moved to Trash") {
            val restore = state.entries.map { if (it.id == id) victim.copy(deletedAt = null) else it }
            state.entries = restore
            Db.saveEntries(restore)
        }
    }

    fun restoreEntry(id: String) {
        val next = state.entries.map { if (it.id == id) it.copy(deletedAt = null, updatedAt = now()) else it }
        state.entries = next
        Db.saveEntries(next)
    }

    fun purgeEntry(id: String) {
        val next = state.entries.filter { it.id != id }
        state.entries = next
        Db.saveEntries(next)
    }

    fun addPayment(entryId: String, p: Payment) {
        val next = state.entries.map { e ->
            if (e.id != entryId) e
            else e.copy(
                payments = e.payments + p.copy(id = uid(), createdAt = now()),
                updatedAt = now(),
            )
        }
        state.entries = next
        Db.saveEntries(next)
    }

    fun removePayment(entryId: String, paymentId: String) {
        val next = state.entries.map { e ->
            if (e.id != entryId) e else e.copy(payments = e.payments.filter { it.id != paymentId }, updatedAt = now())
        }
        state.entries = next
        Db.saveEntries(next)
    }

    fun settleUp(ownerId: OwnerId) {
        val mine = state.entries
            .filter { it.ownerId == ownerId && it.deletedAt == null }
            .sortedWith(compareBy<LedgerEntry> { it.date }.thenBy { it.createdAt })
        val net = mine.sumOf { (if (it.type == "credit") 1 else -1) * entryRemaining(it) }
        var left = abs(net)
        if (left < 0.005) return
        val wantType = if (net > 0) "credit" else "debit"
        val next = state.entries.map { e ->
            if (e.ownerId != ownerId || e.deletedAt != null || e.type != wantType) e
            else {
                val rem = entryRemaining(e)
                if (rem <= 0.005 || left <= 0.005) e
                else {
                    val pay = minOf(rem, left)
                    left = ((left - pay) * 100).roundToLong() / 100.0
                    e.copy(
                        payments = e.payments + Payment(id = uid(), amount = pay, date = todayStr(), auto = "settle", createdAt = now()),
                        updatedAt = now(),
                    )
                }
            }
        }
        state.entries = next
        Db.saveEntries(next)
    }

    /* ---------------- groups ---------------- */

    fun saveGroup(g: LedgerGroup) {
        val groups = state.groups
        val existing = groups.find { it.id == g.id }
        val rec = if (existing != null) {
            existing.copy(ownerId = g.ownerId, title = g.title, note = g.note, refs = g.refs, updatedAt = now())
        } else g.copy(id = g.id.ifBlank { uid() }, createdAt = now(), updatedAt = now())
        val next = if (existing != null) groups.map { if (it.id == rec.id) rec else it } else listOf(rec) + groups
        state.groups = next
        Db.saveGroups(next)
    }

    fun deleteGroup(id: String) {
        val victim = state.groups.find { it.id == id }
        val victimEntries = state.entries.filter { it.groupId == id }
        val nextGroups = state.groups.map { if (it.id == id) it.copy(deletedAt = now(), updatedAt = now()) else it }
        val nextEntries = state.entries.map { if (it.groupId == id) it.copy(groupId = null, updatedAt = now()) else it }
        state.groups = nextGroups
        state.entries = nextEntries
        Db.saveGroups(nextGroups)
        Db.saveEntries(nextEntries)
        if (victim != null) pushUndo("Group deleted") {
            val rg = state.groups.map { if (it.id == id) victim.copy(deletedAt = null) else it }
            val re = state.entries.map { e -> if (victimEntries.any { it.id == e.id }) e.copy(groupId = id) else e }
            state.groups = rg
            state.entries = re
            Db.saveGroups(rg)
            Db.saveEntries(re)
        }
    }

    fun restoreGroup(id: String) {
        val next = state.groups.map { if (it.id == id) it.copy(deletedAt = null, updatedAt = now()) else it }
        state.groups = next
        Db.saveGroups(next)
    }

    fun purgeGroup(id: String) {
        val next = state.groups.filter { it.id != id }
        state.groups = next
        Db.saveGroups(next)
    }

    /* ---------------- lists ---------------- */

    fun saveList(l: CalcList) {
        val lists = state.lists
        val existing = lists.find { it.id == l.id }
        val rec = if (existing != null) {
            existing.copy(
                ownerId = l.ownerId,
                kind = l.kind,
                title = l.title,
                note = l.note,
                discount = l.discount,
                taxPct = l.taxPct,
                extraCharge = l.extraCharge,
                isTemplate = l.isTemplate,
                postToLedger = l.postToLedger,
                repeat = l.repeat,
                currency = l.currency,
                items = l.items,
                refs = l.refs,
                updatedAt = now(),
            )
        } else l.copy(id = l.id.ifBlank { uid() }, createdAt = now(), updatedAt = now())
        val next = if (existing != null) lists.map { if (it.id == rec.id) rec else it } else listOf(rec) + lists
        state.lists = next
        Db.saveLists(next)
    }

    fun deleteList(id: String) {
        val victim = state.lists.find { it.id == id }
        val next = state.lists.map { if (it.id == id) it.copy(deletedAt = now(), updatedAt = now()) else it }
        state.lists = next
        Db.saveLists(next)
        if (victim != null) pushUndo("List moved to Trash") {
            val restore = state.lists.map { if (it.id == id) victim.copy(deletedAt = null) else it }
            state.lists = restore
            Db.saveLists(restore)
        }
    }

    fun restoreList(id: String) {
        val next = state.lists.map { if (it.id == id) it.copy(deletedAt = null, updatedAt = now()) else it }
        state.lists = next
        Db.saveLists(next)
    }

    fun purgeList(id: String) {
        val next = state.lists.filter { it.id != id }
        state.lists = next
        Db.saveLists(next)
    }

    fun duplicateList(id: String, asTemplate: Boolean = false, fresh: Boolean = false): String? {
        val src = state.lists.find { it.id == id } ?: return null
        val newId = uid()
        val items = if (fresh) {
            src.items.map { it.copy(id = uid(), payments = emptyList(), checked = false, createdAt = now(), updatedAt = now()) }
        } else {
            src.items.map { it.copy(id = uid(), payments = it.payments.map { p -> p.copy(id = uid(), createdAt = now()) }, createdAt = now(), updatedAt = now()) }
        }
        val copy = src.copy(id = newId, isTemplate = asTemplate, items = items, createdAt = now(), updatedAt = now(), deletedAt = null)
        val next = listOf(copy) + state.lists
        state.lists = next
        Db.saveLists(next)
        return newId
    }

    fun addListItem(listId: String, item: ListItem) {
        mapList(listId) { l ->
            val sortOrder = (l.items.maxOfOrNull { it.sortOrder } ?: -1) + 1
            l.copy(items = l.items + item.copy(id = uid(), sortOrder = sortOrder, createdAt = now(), updatedAt = now()), updatedAt = now())
        }
    }

    fun updateListItem(listId: String, itemId: String, patch: (ListItem) -> ListItem) {
        mapList(listId) { l ->
            l.copy(items = l.items.map { if (it.id == itemId) patch(it).copy(updatedAt = now()) else it }, updatedAt = now())
        }
    }

    fun removeListItem(listId: String, itemId: String) {
        mapList(listId) { l -> l.copy(items = l.items.filter { it.id != itemId }, updatedAt = now()) }
    }

    fun addItemPayment(listId: String, itemId: String, p: Payment) {
        mapList(listId) { l ->
            l.copy(
                items = l.items.map { i ->
                    if (i.id != itemId) i else i.copy(payments = i.payments + p.copy(id = uid(), createdAt = now()), updatedAt = now())
                },
                updatedAt = now(),
            )
        }
    }

    fun removeItemPayment(listId: String, itemId: String, paymentId: String) {
        mapList(listId) { l ->
            l.copy(
                items = l.items.map { i ->
                    if (i.id != itemId) i else i.copy(payments = i.payments.filter { it.id != paymentId }, updatedAt = now())
                },
                updatedAt = now(),
            )
        }
    }

    fun toggleItemChecked(listId: String, itemId: String) {
        val list = state.lists.find { it.id == listId } ?: return
        val item = list.items.find { it.id == itemId } ?: return
        if (!item.checked) {
            val remaining = maxOf(((lineTotal(item) - itemPaid(item)) * 100).roundToLong() / 100.0, 0.0)
            if (remaining > 0) {
                addItemPayment(listId, itemId, Payment(id = "", amount = remaining, date = todayStr(), auto = "tick"))
            }
            updateListItem(listId, itemId) { it.copy(checked = true) }
        } else {
            mapList(listId) { l ->
                l.copy(
                    items = l.items.map { i ->
                        if (i.id != itemId) i else i.copy(checked = false, payments = i.payments.filter { it.auto != "tick" }, updatedAt = now())
                    },
                    updatedAt = now(),
                )
            }
        }
    }

    fun markAllPaid(listId: String) {
        mapList(listId) { l ->
            l.copy(
                items = l.items.map { i ->
                    val remaining = maxOf(((lineTotal(i) - itemPaid(i)) * 100).roundToLong() / 100.0, 0.0)
                    if (remaining > 0.005) {
                        i.copy(
                            checked = true,
                            payments = i.payments + Payment(id = uid(), amount = remaining, date = todayStr(), auto = "tick", createdAt = now()),
                            updatedAt = now(),
                        )
                    } else if (lineTotal(i) == 0.0) i.copy(checked = true, updatedAt = now()) else i
                },
                updatedAt = now(),
            )
        }
    }

    fun clearPaid(listId: String) {
        mapList(listId) { l ->
            l.copy(items = l.items.map { it.copy(payments = emptyList(), checked = false, updatedAt = now()) }, updatedAt = now())
        }
    }

    private fun mapList(listId: String, f: (CalcList) -> CalcList) {
        val next = state.lists.map { if (it.id == listId) f(it) else it }
        state.lists = next
        Db.saveLists(next)
    }

    /* ---------------- notes ---------------- */

    fun saveNote(n: Note) {
        val notes = state.notes
        val existing = notes.find { it.id == n.id }
        val rec = if (existing != null) {
            existing.copy(
                ownerId = n.ownerId, title = n.title, body = n.body, tags = n.tags,
                pinned = n.pinned, color = n.color, links = n.links, updatedAt = now(),
            )
        } else n.copy(id = n.id.ifBlank { uid() }, createdAt = now(), updatedAt = now())
        val next = if (existing != null) notes.map { if (it.id == rec.id) rec else it } else listOf(rec) + notes
        state.notes = next
        Db.saveNotes(next)
    }

    fun deleteNote(id: String) {
        val victim = state.notes.find { it.id == id }
        val next = state.notes.map { if (it.id == id) it.copy(deletedAt = now(), updatedAt = now()) else it }
        state.notes = next
        Db.saveNotes(next)
        if (victim != null) pushUndo("Note moved to Trash") {
            val restore = state.notes.map { if (it.id == id) victim.copy(deletedAt = null) else it }
            state.notes = restore
            Db.saveNotes(restore)
        }
    }

    fun restoreNote(id: String) {
        val next = state.notes.map { if (it.id == id) it.copy(deletedAt = null, updatedAt = now()) else it }
        state.notes = next
        Db.saveNotes(next)
    }

    fun purgeNote(id: String) {
        val next = state.notes.filter { it.id != id }
        state.notes = next
        Db.saveNotes(next)
    }

    /* ---------------- reminders ---------------- */

    fun saveReminder(r: Reminder) {
        val reminders = state.reminders
        val existing = reminders.find { it.id == r.id }
        val rec = if (existing != null) {
            existing.copy(
                ownerId = r.ownerId, title = r.title, notes = r.notes, kind = r.kind,
                dueAt = r.dueAt, repeat = r.repeat, leadMinutes = r.leadMinutes,
                links = r.links, done = r.done, updatedAt = now(),
            )
        } else r.copy(id = r.id.ifBlank { uid() }, createdAt = now(), updatedAt = now())
        val next = if (existing != null) reminders.map { if (it.id == rec.id) rec else it } else listOf(rec) + reminders
        state.reminders = next
        Db.saveReminders(next)
        if (!rec.done && rec.deletedAt == null) scheduleReminderNotification(rec)
        else cancelReminderNotification(rec.id)
    }

    fun deleteReminder(id: String) {
        val victim = state.reminders.find { it.id == id }
        val next = state.reminders.map { if (it.id == id) it.copy(deletedAt = now(), updatedAt = now()) else it }
        state.reminders = next
        Db.saveReminders(next)
        cancelReminderNotification(id)
        if (victim != null) pushUndo("Reminder moved to Trash") {
            val restore = state.reminders.map { if (it.id == id) victim.copy(deletedAt = null) else it }
            state.reminders = restore
            Db.saveReminders(restore)
            scheduleReminderNotification(victim)
        }
    }

    fun restoreReminder(id: String) {
        val next = state.reminders.map { if (it.id == id) it.copy(deletedAt = null, updatedAt = now()) else it }
        state.reminders = next
        Db.saveReminders(next)
        val rec = next.find { it.id == id }
        if (rec != null && !rec.done) scheduleReminderNotification(rec)
    }

    fun purgeReminder(id: String) {
        val next = state.reminders.filter { it.id != id }
        state.reminders = next
        Db.saveReminders(next)
        cancelReminderNotification(id)
    }

    fun completeReminder(id: String) {
        val next = state.reminders.map { if (it.id == id) it.copy(done = true, doneAt = now(), updatedAt = now()) else it }
        state.reminders = next
        Db.saveReminders(next)
        cancelReminderNotification(id)
    }

    fun reopenReminder(id: String) {
        val next = state.reminders.map {
            if (it.id == id) it.copy(done = false, doneAt = null, snoozedUntil = null, updatedAt = now()) else it
        }
        state.reminders = next
        Db.saveReminders(next)
        next.find { it.id == id }?.let { scheduleReminderNotification(it) }
    }

    fun snoozeReminder(id: String, until: Long) {
        val next = state.reminders.map { if (it.id == id) it.copy(snoozedUntil = until, updatedAt = now()) else it }
        state.reminders = next
        Db.saveReminders(next)
        cancelReminderNotification(id)
        next.find { it.id == id }?.let { scheduleReminderNotification(it.copy(dueAt = until)) }
    }

    /** v1.6.17 — the alarm dialog was SHOWN for this due moment: persist
     *  lastFiredAt (and the fired lead) so the 15-second watcher never
     *  re-pops the same alarm, and repeats know they already fired. */
    fun markReminderFired(id: String, leadMinutes: Int? = null) {
        val rec = state.reminders.find { it.id == id } ?: return
        val next = state.reminders.map {
            if (it.id == id) it.copy(
                lastFiredAt = now(),
                firedLeads = if (leadMinutes != null && !it.firedLeads.contains(leadMinutes)) it.firedLeads + leadMinutes else it.firedLeads,
                updatedAt = now(),
            ) else it
        }
        state.reminders = next
        Db.saveReminders(next)
    }

    fun fireReminder(id: String) {
        val rec = state.reminders.find { it.id == id } ?: return
        if (rec.repeat == "none") return
        val nextDue = nextOccurrence(rec.dueAt, rec.repeat)
        val next = state.reminders.map {
            if (it.id == id) it.copy(dueAt = nextDue, lastFiredAt = now(), firedLeads = emptyList(), snoozedUntil = null, updatedAt = now()) else it
        }
        state.reminders = next
        Db.saveReminders(next)
        next.find { it.id == id }?.let { scheduleReminderNotification(it) }
    }

    /* ---------------- banks ---------------- */

    fun saveBank(b: BankAccount) {
        val banks = state.banks
        val existing = banks.find { it.id == b.id }
        val rec = if (existing != null) {
            existing.copy(
                ownerId = b.ownerId, label = b.label, holderName = b.holderName, bankName = b.bankName,
                accountNumber = b.accountNumber, iban = b.iban, swift = b.swift, branch = b.branch,
                notes = b.notes, balance = b.balance, currency = b.currency, updatedAt = now(),
            )
        } else b.copy(id = b.id.ifBlank { uid() }, createdAt = now(), updatedAt = now())
        val next = if (existing != null) banks.map { if (it.id == rec.id) rec else it } else listOf(rec) + banks
        state.banks = next
        Db.saveBanks(next)
    }

    fun deleteBank(id: String) {
        val victim = state.banks.find { it.id == id }
        val next = state.banks.map { if (it.id == id) it.copy(deletedAt = now(), updatedAt = now()) else it }
        state.banks = next
        Db.saveBanks(next)
        if (victim != null) pushUndo("Account moved to Trash") {
            val restore = state.banks.map { if (it.id == id) victim.copy(deletedAt = null) else it }
            state.banks = restore
            Db.saveBanks(restore)
        }
    }

    fun restoreBank(id: String) {
        val next = state.banks.map { if (it.id == id) it.copy(deletedAt = null, updatedAt = now()) else it }
        state.banks = next
        Db.saveBanks(next)
    }

    fun purgeBank(id: String) {
        val next = state.banks.filter { it.id != id }
        state.banks = next
        Db.saveBanks(next)
    }

    /* ---------------- calls / messages ---------------- */

    fun addCall(phone: String, ownerId: OwnerId?, direction: String = "outgoing", at: Long = now(), duration: Int = 0, via: String = "tel") {
        val rec = CallLogEntry(id = uid(), phone = phone, ownerId = ownerId, direction = direction, at = at, duration = duration, via = via, createdAt = now())
        val next = (listOf(rec) + state.recents).take(500)
        state.recents = next
        Db.saveRecents(next)
    }

    fun deleteRecent(id: String) {
        val next = state.recents.filter { it.id != id }
        state.recents = next
        Db.saveRecents(next)
    }

    fun clearRecents() {
        state.recents = emptyList()
        Db.saveRecents(emptyList())
    }

    fun logMessage(ownerId: OwnerId, channel: String, text: String) {
        val rec = MessageLog(id = uid(), ownerId = ownerId, channel = channel, text = text, date = now(), createdAt = now())
        val next = (listOf(rec) + state.messages).take(300)
        state.messages = next
        Db.saveMessages(next)
    }

    /* ---------------- settings / data ---------------- */

    fun updateSettings(patch: (Settings) -> Settings) {
        val next = patch(state.settings)
        state.settings = next
        Db.saveSettings(next)
    }

    fun addCustomCategory(name: String) {
        val name2 = name.trim()
        if (name2.isEmpty()) return
        updateSettings { s ->
            if (s.customCategories.contains(name2)) s
            else s.copy(customCategories = s.customCategories + name2)
        }
    }

    fun removeCustomCategory(name: String) {
        updateSettings { s -> s.copy(customCategories = s.customCategories.filter { it != name }) }
    }

    fun toggleBlocked(phone: String) {
        val norm = normalizePhone(phone, state.settings.countryCode)
        updateSettings { s ->
            val cur = s.blockedNumbers
            s.copy(blockedNumbers = if (cur.contains(norm)) cur.filter { it != norm } else cur + norm)
        }
    }

    fun setSpeedDial(key: String, recordID: String?) {
        updateSettings { s ->
            val cur = s.speedDial.toMutableMap()
            if (recordID != null) cur[key] = recordID else cur.remove(key)
            s.copy(speedDial = cur)
        }
    }

    fun setPin(hash: PinHash?) {
        updateSettings { it.copy(pinHash = hash) }
        if (hash != null) Db.writePinFile(hash.hash, hash.salt)
        else Db.removePinFile()
    }

    fun moveEntity(kind: String, id: String, ownerId: OwnerId) {
        when (kind) {
            "entry" -> { val n = state.entries.map { if (it.id == id) it.copy(ownerId = ownerId, updatedAt = now()) else it }; state.entries = n; Db.saveEntries(n) }
            "list" -> { val n = state.lists.map { if (it.id == id) it.copy(ownerId = ownerId, updatedAt = now()) else it }; state.lists = n; Db.saveLists(n) }
            "note" -> { val n = state.notes.map { if (it.id == id) it.copy(ownerId = ownerId, updatedAt = now()) else it }; state.notes = n; Db.saveNotes(n) }
            "reminder" -> { val n = state.reminders.map { if (it.id == id) it.copy(ownerId = ownerId, updatedAt = now()) else it }; state.reminders = n; Db.saveReminders(n) }
            "bank" -> { val n = state.banks.map { if (it.id == id) it.copy(ownerId = ownerId, updatedAt = now()) else it }; state.banks = n; Db.saveBanks(n) }
            "group" -> { val n = state.groups.map { if (it.id == id) it.copy(ownerId = ownerId, updatedAt = now()) else it }; state.groups = n; Db.saveGroups(n) }
        }
    }

    fun restoreBackup(data: PersistedData, mode: String) {
        if (mode == "replace") {
            state.contactX = data.contactX
            state.entries = data.entries
            state.groups = data.groups
            state.lists = data.lists
            state.notes = data.notes
            state.reminders = data.reminders
            state.banks = data.banks
            state.recents = data.recents
            state.messages = data.messages
            state.settings = data.settings.copy(pinHash = null)
            Db.saveContactX(data.contactX); Db.saveEntries(data.entries); Db.saveGroups(data.groups)
            Db.saveLists(data.lists); Db.saveNotes(data.notes); Db.saveReminders(data.reminders)
            Db.saveBanks(data.banks); Db.saveRecents(data.recents); Db.saveMessages(data.messages)
            Db.saveSettings(state.settings)
            state.reminders.filter { !it.done }.forEach { scheduleReminderNotification(it) }
            return
        }
        // merge — by id, existing records win
        fun <T> mergeById(cur: List<T>, inc: List<T>, idOf: (T) -> String, delOf: (T) -> Long?): List<T> {
            if (inc.isEmpty()) return cur
            val ids = cur.map { idOf(it) }.toHashSet()
            val add = inc.filter { idOf(it) !in ids && delOf(it) == null }
            return add + cur
        }
        state.contactX = mergeById(state.contactX, data.contactX, { it.recordID }, { null })
        state.entries = mergeById(state.entries, data.entries, { it.id }, { it.deletedAt })
        state.groups = mergeById(state.groups, data.groups, { it.id }, { it.deletedAt })
        state.lists = mergeById(state.lists, data.lists, { it.id }, { it.deletedAt })
        state.notes = mergeById(state.notes, data.notes, { it.id }, { it.deletedAt })
        state.reminders = mergeById(state.reminders, data.reminders, { it.id }, { it.deletedAt })
        state.banks = mergeById(state.banks, data.banks, { it.id }, { it.deletedAt })
        state.recents = mergeById(state.recents, data.recents, { it.id }, { null })
        state.messages = mergeById(state.messages, data.messages, { it.id }, { null })
        Db.saveContactX(state.contactX); Db.saveEntries(state.entries); Db.saveGroups(state.groups)
        Db.saveLists(state.lists); Db.saveNotes(state.notes); Db.saveReminders(state.reminders)
        Db.saveBanks(state.banks); Db.saveRecents(state.recents); Db.saveMessages(state.messages)
        if (data.settings.language != "en") updateSettings { it.copy(language = data.settings.language) }
        state.reminders.filter { !it.done }.forEach { scheduleReminderNotification(it) }
    }

    fun wipeAll() {
        Db.wipeStorage()
        Db.removePinFile()
        state.contactX = emptyList(); state.entries = emptyList(); state.groups = emptyList()
        state.lists = emptyList(); state.notes = emptyList(); state.reminders = emptyList()
        state.banks = emptyList(); state.recents = emptyList(); state.messages = emptyList()
        state.settings = Db.DEFAULT_SETTINGS
        state.lastUndo = null
    }

    /* ---------------- notifications ---------------- */

    fun scheduleReminderNotification(r: Reminder) {
        try {
            val at = r.snoozedUntil ?: r.dueAt
            if (r.done || r.deletedAt != null || at <= now()) return
            Platform.notify.ensureChannel()
            // v1.6.16 — pre-localized labels ride along with the alarm so the
            // rich system notification (kind subtext + Done / Snooze actions)
            // is in the user's language even when the app is long dead.
            val kindLabel = when (r.kind) {
                "payment" -> t("rm.kindPayment")
                "todo" -> t("rm.kindTodo")
                "birthday" -> t("rm.kindBirthday")
                "followup" -> t("rm.kindFollowup")
                else -> t("rm.kindCustom")
            }
            Platform.notify.schedule(
                r.id, r.title, r.notes ?: "", at, r.kind,
                kindLabel = kindLabel,
                doneLabel = t("act.done"),
                snoozeLabel = t("rm.snooze10"),
                alarm = r.isAlarm,
            )
        } catch (_: Exception) {}
    }

    fun cancelReminderNotification(id: String) {
        try { Platform.notify.cancel(id) } catch (_: Exception) {}
    }

    /** v1.6.21 — MAIN-THREAD capture for the boot re-arm: the reminder list +
     *  localized labels are snapshot reads, so they are built here (called
     * from a main context) and handed to the binder-only background hop. */
    fun remindersForRearm(): List<ReminderForAlarm> = try {
        fun kindLabelOf(kind: String): String = when (kind) {
            "payment" -> t("rm.kindPayment")
            "todo" -> t("rm.kindTodo")
            "birthday" -> t("rm.kindBirthday")
            "followup" -> t("rm.kindFollowup")
            else -> t("rm.kindCustom")
        }
        state.reminders
            .filter { it.deletedAt == null && !it.done && (it.snoozedUntil ?: it.dueAt) > now() }
            .map {
                ReminderForAlarm(
                    it.id, it.title, it.notes ?: "", it.snoozedUntil ?: it.dueAt, it.kind,
                    kindLabelOf(it.kind), t("act.done"), t("rm.snooze10"),
                    alarm = it.isAlarm,
                )
            }
    } catch (_: Exception) { emptyList() }

    fun rearmReminders() {
        try {
            Platform.notify.rearmAll(remindersForRearm())
        } catch (_: Exception) {}
    }

    /* ---------------- contacts / calls caches ---------------- */

    private val json = Json { ignoreUnknownKeys = true }

    fun saveCachedCalls(list: List<DeviceCall>) {
        // v1.6.18: serialize + atomic file write on the Persist IO queue —
        // this is the FULL call-log cache, one of the biggest blobs in the app.
        Persist.post {
            try {
                val arr = kotlinx.serialization.json.buildJsonArray {
                    for (c in list) add(kotlinx.serialization.json.buildJsonObject {
                        put("id", kotlinx.serialization.json.JsonPrimitive(c.id))
                        put("cachedName", c.cachedName?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
                        put("number", kotlinx.serialization.json.JsonPrimitive(c.number))
                        put("type", kotlinx.serialization.json.JsonPrimitive(c.type))
                        put("date", kotlinx.serialization.json.JsonPrimitive(c.date))
                        put("duration", kotlinx.serialization.json.JsonPrimitive(c.duration))
                    })
                }
                val savedAt = now()
                Platform.kv.writeFile("calllog-cache.json", """{"savedAt":$savedAt,"list":$arr}""")
            } catch (_: Exception) {}
        }
    }

    fun loadCachedCalls(): Pair<Long, List<DeviceCall>> {
        return try {
            val raw = Platform.kv.readFile("calllog-cache.json") ?: return 0L to emptyList()
            val obj = json.parseToJsonElement(raw).jsonObject
            val savedAt = obj["savedAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            val list = obj["list"]?.jsonArray?.mapNotNull { el ->
                try {
                    val o = el.jsonObject
                    DeviceCall(
                        id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                        cachedName = (o["cachedName"] as? kotlinx.serialization.json.JsonPrimitive)?.content,
                        number = o["number"]?.jsonPrimitive?.content ?: "",
                        type = o["type"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        date = o["date"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                        duration = o["duration"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    )
                } catch (_: Exception) { null }
            } ?: emptyList()
            savedAt to list
        } catch (_: Exception) { 0L to emptyList() }
    }

    private val contactsCacheJson = Json { ignoreUnknownKeys = true }

    fun saveCachedContacts(list: List<DeviceContact>) {
        // v1.6.18 — THE BIGGEST MAIN-THREAD FREEZE of v1.6.17: this serializes
        // the ENTIRE device address book and fsyncs it, and it used to run on
        // whatever thread touched it — including MAIN from contact-edit click
        // handlers (patchDeviceContact / addDeviceContactToList). Now queued.
        Persist.post {
            try {
                val text = contactsCacheJson.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(com.callmony.model.DeviceContact.serializer()), list)
                Platform.kv.writeFile("contacts-cache-v2.json", text)
            } catch (_: Exception) {}
        }
    }

    fun loadCachedContacts(): List<DeviceContact> {
        return try {
            val raw = Platform.kv.readFile("contacts-cache-v2.json") ?: return emptyList()
            contactsCacheJson.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(com.callmony.model.DeviceContact.serializer()), raw)
        } catch (_: Exception) { emptyList() }
    }

    /* ---------------- selectors ---------------- */

    fun contactByRecord(recordID: String): DeviceContact? =
        state.deviceContacts.find { it.recordID == recordID }

    private var phoneIdxFor: List<DeviceContact>? = null
    private var phoneIdxFull = HashMap<String, DeviceContact>()
    private var phoneIdxTail = HashMap<String, DeviceContact>()

    fun contactByPhone(phone: String): DeviceContact? {
        val d = normalizePhone(phone, state.settings.countryCode).replace(Regex("\\D"), "")
        if (d.length < 5) return null
        if (phoneIdxFor !== state.deviceContacts) {
            phoneIdxFor = state.deviceContacts
            phoneIdxFull = HashMap()
            phoneIdxTail = HashMap()
            for (c in state.deviceContacts) {
                for (p in c.phones) {
                    val dd = normalizePhone(p.number, state.settings.countryCode).replace(Regex("\\D"), "")
                    if (dd.length < 5) continue
                    if (!phoneIdxFull.containsKey(dd)) phoneIdxFull[dd] = c
                    val tail = dd.takeLast(9)
                    if (tail.length >= 7 && !phoneIdxTail.containsKey(tail)) phoneIdxTail[tail] = c
                }
            }
        }
        phoneIdxFull[d]?.let { return it }
        val tail = d.takeLast(9)
        if (tail.length >= 7) return phoneIdxTail[tail]
        return null
    }

    fun favoriteRecordIds(): Set<String> =
        state.contactX.filter { it.favorite }.map { it.recordID }.toHashSet()

    fun contactXOf(recordID: String): ContactX? = state.contactX.find { it.recordID == recordID }

    fun openListCount(ownerId: OwnerId): Int =
        state.lists.count {
            it.ownerId == ownerId && it.deletedAt == null && !it.isTemplate && it.postToLedger && listTotals(it).pending > 0.005
        }
}

/* ---------------- UI store (tabs + overlay stack + alarm queue) ---------------- */

sealed interface Overlay {
    data class Contact(val recordID: String, val initialSeg: String? = null) : Overlay
    data class ContactEdit(val recordID: String) : Overlay
    data class ContactNew(val phone: String? = null, val name: String? = null) : Overlay
    data class ListDetail(val listId: String) : Overlay
    data class ListEdit(val listId: String?, val ownerId: String, val kind: String? = null) : Overlay
    data class EntryEdit(val entryId: String?, val ownerId: String, val entryType: String? = null, val kind: String? = null, val groupId: String? = null) : Overlay
    data class NoteEdit(val noteId: String?, val ownerId: String, val linkTo: LinkRef? = null) : Overlay
    data class ReminderEdit(val reminderId: String?, val ownerId: String) : Overlay
    data class BankEdit(val bankId: String?, val ownerId: String) : Overlay
    data object SettingsSheet : Overlay
    data class Search(val query: String? = null) : Overlay
    data class Calculator(val onPickKey: String? = null) : Overlay
    data object Merge : Overlay
    data object SpeedDial : Overlay
    data object Backup : Overlay
    data object About : Overlay
}

object UiStore {
    var tabInternal by mutableStateOf("recents")
    val tab: String get() = tabInternal
    val overlayStack = mutableStateListOf<Overlay>()
    var moreOpen by mutableStateOf(false)
    var locked by mutableStateOf(false)
    val alarmQueue = mutableStateListOf<Reminder>()
    var calculatorValue by mutableStateOf<Double?>(null)
    var calculatorPickHandler by mutableStateOf<((Double) -> Unit)?>(null)
    var keypadOpen by mutableStateOf(false)

    /** v1.6.17 — true between MainActivity.onResume and onPause; drives the
     *  "lock immediately" autolock option (previously a hardcoded `true`). */
    var appInForeground: Boolean = true

    fun setTab(t: String) { tabInternal = t; moreOpen = false }

    fun open(o: Overlay) { overlayStack.add(o); moreOpen = false }

    fun pop() { overlayStack.removeLastOrNull() }

    fun closeAll() {
        overlayStack.clear()
        calculatorPickHandler = null
        keypadOpen = false
        moreOpen = false
    }

    fun closeType(type: String) {
        overlayStack.removeAll { it::class.simpleName == type }
    }

    fun openCalculator(onPick: ((Double) -> Unit)? = null) {
        overlayStack.add(Overlay.Calculator())
        calculatorPickHandler = onPick
    }

    fun pushAlarm(r: Reminder) {
        if (alarmQueue.none { it.id == r.id }) alarmQueue.add(r)
    }

    fun shiftAlarm(): Reminder? {
        if (alarmQueue.isEmpty()) return null
        return alarmQueue.removeAt(0)
    }

    fun isEditingContact(): Boolean =
        overlayStack.any { it is Overlay.ContactEdit || it is Overlay.ContactNew }
}
