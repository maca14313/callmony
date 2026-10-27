package com.callmony.ui.overlays

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.LANGUAGES
import com.callmony.i18n.currentLang
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Calls
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.lib.displayName
import com.callmony.lib.fdEpoch
import com.callmony.lib.ft
import com.callmony.lib.dateOf
import com.callmony.lib.entryRemaining
import com.callmony.lib.now
import com.callmony.lib.ownerFullBalance
import com.callmony.lib.ownerNameOf
import com.callmony.lib.prettyPhone
import com.callmony.lib.primaryPhone
import com.callmony.lib.searchContacts
import com.callmony.lib.todayStr
import com.callmony.model.DeviceContact
import com.callmony.platform.Platform
import com.callmony.theme.F
import com.callmony.ui.components.BottomSheet
import com.callmony.ui.components.Sheet
import com.callmony.ui.components.PressableRow
import com.callmony.ui.components.ScrollRow
import com.callmony.ui.kit.Avatar
import com.callmony.ui.kit.Btn
import com.callmony.ui.kit.C
import com.callmony.ui.kit.Card
import com.callmony.ui.kit.Chip
import com.callmony.ui.kit.EmptyState
import com.callmony.ui.kit.IconBtn
import com.callmony.ui.kit.MdiIcon
import com.callmony.ui.kit.Money
import com.callmony.ui.kit.focusRing
import com.callmony.ui.kit.pressVeil
import com.callmony.ui.kit.Pill
import com.callmony.ui.kit.Toasts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* MiscOverlays — global Search, SpeedDial grid, Merge duplicates, About,
   AlarmDialog. Port of src/overlays/MiscOverlays.tsx. */

/* ---------------- Search overlay ---------------- */

@Composable
fun SearchOverlay() {
    val C = C()
    val pop = { UiStore.pop() }
    var query by remember { mutableStateOf("") }
    var debounced by remember { mutableStateOf("") }
    val contacts = AppStore.state.deviceContacts
    val countryCode = AppStore.state.settings.countryCode

    /* v1.6.15 (user request): opening the search must raise the keyboard on
       its own — focus the field as soon as the overlay composes. */
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { fieldFocus.requestFocus() }
    }

    LaunchedEffect(query) {
        delay(70)
        debounced = query
    }

    val results = remember(debounced, contacts) {
        if (debounced.isBlank()) null
        else searchContacts(contacts, debounced, ::displayName, countryCode)
    }

    // v1.6.25 — ADVANCED FILTERS BEFORE TYPING (user report: "the search
    // should have advanced tabs that helps for advanced search, even before
    // the search begins so it can filter, like credit, and others, date and
    // more"). Two always-visible filter rows: the type tabs (All / Contacts /
    // Entries / Lists / Notes / Reminders / Banks — counts live) and the
    // entry refinement row (kind: Credit/Debit/Income/Expense; date: Today /
    // 7 days / 30 days / Overdue). With NO query, picking a filter enters
    // BROWSE mode — the list shows everything of that type, pre-filtered,
    // before a single letter is typed.
    // v1.6.26 — TWO USER REPORTS ON TOP:
    // (a) "when i click the credit tab it is not showing the contacts that
    //     have them" — the kind row now filters CONTACTS too: a contact
    //     matches when it has ≥1 entry of that kind inside the active date
    //     window (so Credit lists exactly the people you credited).
    // (b) "the date tab should have custom date to filter any date" — a
    //     Custom chip opens a from/to picker (native Material calendar);
    //     both ends optional, and the range applies to entries AND to the
    //     contact matching in (a).
    var advTab by remember { mutableStateOf("all") }
    var kindFilter by remember { mutableStateOf<String?>(null) }
    var dateFilter by remember { mutableStateOf<String?>(null) }
    var customFrom by remember { mutableStateOf<String?>(null) } // YYYY-MM-DD
    var customTo by remember { mutableStateOf<String?>(null) } // YYYY-MM-DD
    var customSheet by remember { mutableStateOf(false) }

    val hasQuery = debounced.isNotBlank()
    val browsing = !hasQuery && (advTab != "all" || kindFilter != null || dateFilter != null)
    val showContacts = advTab == "all" || advTab == "contacts"
    val showEntries = advTab == "all" || advTab == "entries"
    val showLists = advTab == "all" || advTab == "lists"
    val showNotes = advTab == "all" || advTab == "notes"
    val showReminders = advTab == "all" || advTab == "reminders"
    val showBanks = advTab == "all" || advTab == "banks"

    fun inDateWindow(e: com.callmony.model.LedgerEntry): Boolean = when (dateFilter) {
        "today" -> e.date == todayStr()
        "7d" -> e.date >= daysAgoStr(7)
        "30d" -> e.date >= daysAgoStr(30)
        "overdue" -> e.dueDate != null && e.dueDate!! < todayStr() && entryRemaining(e) > 0.005
        // v1.6.26 — custom range: open ends allowed (from-only / to-only)
        "custom" -> (customFrom == null || e.date >= customFrom!!) && (customTo == null || e.date <= customTo!!)
        else -> true
    }

    // v1.6.26 — recordIDs that carry ≥1 entry of the active kind inside the
    // active date window. This is what makes the kind row filter CONTACTS.
    val contactsWithKind = remember(kindFilter, dateFilter, customFrom, customTo, AppStore.state.entries) {
        if (kindFilter == null && dateFilter == null) null
        else {
            val set = mutableSetOf<String>()
            AppStore.state.entries.forEach { e ->
                if (e.deletedAt == null && e.ownerId.startsWith("c:") &&
                    (kindFilter == null || (e.kind ?: e.type) == kindFilter) && inDateWindow(e)
                ) set.add(e.ownerId.substring(2))
            }
            set
        }
    }

    val matchedContacts = when {
        hasQuery -> (results?.map { it.contact } ?: emptyList())
            // v1.6.26 — kind/date filters apply to QUERY results too
            .filter { c -> contactsWithKind == null || c.recordID in contactsWithKind }
        browsing && showContacts ->
            if (contactsWithKind == null) contacts else contacts.filter { it.recordID in contactsWithKind!! }
        else -> emptyList()
    }
    val entries = AppStore.state.entries
        .filter { it.deletedAt == null }
        .filter { !hasQuery || matchesEntry(it, debounced) }
        .filter { kindFilter == null || (it.kind ?: it.type) == kindFilter }
        .filter { inDateWindow(it) }
        .let { if (hasQuery) it.take(5) else it.take(30) }
        .filter { hasQuery || browsing } // entries only appear on query or browse
    val lists = AppStore.state.lists
        .filter { it.deletedAt == null && (!hasQuery || it.title.contains(debounced, true)) }
        .let { if (hasQuery) it.take(5) else it.take(20) }
        .filter { hasQuery || browsing }
    val notes = AppStore.state.notes
        .filter { it.deletedAt == null && (!hasQuery || (it.title.contains(debounced, true) || it.body.contains(debounced, true))) }
        .let { if (hasQuery) it.take(5) else it.take(20) }
        .filter { hasQuery || browsing }
    val reminders = AppStore.state.reminders
        .filter { it.deletedAt == null && (!hasQuery || it.title.contains(debounced, true)) }
        .let { if (hasQuery) it.take(5) else it.take(20) }
        .filter { hasQuery || browsing }
    val banks = AppStore.state.banks
        .filter { it.deletedAt == null && (!hasQuery || ((it.bankName ?: it.label).contains(debounced, true))) }
        .let { if (hasQuery) it.take(5) else it.take(20) }
        .filter { hasQuery || browsing }

    Column(Modifier.fillMaxSize().background(C.canvas)) {
        // search field
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn("arrow-left", pop, size = 40)
            Row(
                Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp)
                    // v1.6.29 — INPUT PRESS FEEDBACK (search box reacts to touch)
                    .pressVeil()
                    // v1.6.31 — ring colour while the search field is active
                    .focusRing()
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.inputBg)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MdiIcon("magnify", 20, C.faint)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = C.ink),
                    cursorBrush = SolidColor(C.brand),
                    modifier = Modifier.weight(1f).focusRequester(fieldFocus),
                    decorationBox = { inner -> if (query.isEmpty()) Text(t("sr.placeholder"), fontSize = 16.sp, color = C.faint) else inner() },
                )
                if (query.isNotEmpty()) {
                    Box(Modifier.clip(CircleShape).clickable { query = "" }) { MdiIcon("close", 16, C.faint, Modifier.padding(4.dp)) }
                }
            }
        }
        // v1.6.25 — advanced filter tabs, ALWAYS visible (not only while
        // typing) with live counts; picking one with an empty query starts
        // browsing that type immediately.
        ScrollRow(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.callmony.ui.kit.Chip(label = t("sr.filterAll"), compact = true, active = advTab == "all", onPress = { advTab = "all" })
                com.callmony.ui.kit.Chip(label = "${t("misc.contacts")} ${if (matchedContacts.isEmpty()) "" else "· ${matchedContacts.size}"}".trim(), compact = true, active = advTab == "contacts", onPress = { advTab = "contacts" })
                com.callmony.ui.kit.Chip(label = "${t("misc.entry")} ${if (entries.isEmpty()) "" else "· ${entries.size}"}".trim(), compact = true, active = advTab == "entries", onPress = { advTab = "entries" })
                com.callmony.ui.kit.Chip(label = "${t("misc.list")} ${if (lists.isEmpty()) "" else "· ${lists.size}"}".trim(), compact = true, active = advTab == "lists", onPress = { advTab = "lists" })
                com.callmony.ui.kit.Chip(label = "${t("misc.note")} ${if (notes.isEmpty()) "" else "· ${notes.size}"}".trim(), compact = true, active = advTab == "notes", onPress = { advTab = "notes" })
                com.callmony.ui.kit.Chip(label = "${t("misc.reminder")} ${if (reminders.isEmpty()) "" else "· ${reminders.size}"}".trim(), compact = true, active = advTab == "reminders", onPress = { advTab = "reminders" })
                com.callmony.ui.kit.Chip(label = "${t("misc.bank")} ${if (banks.isEmpty()) "" else "· ${banks.size}"}".trim(), compact = true, active = advTab == "banks", onPress = { advTab = "banks" })
            }
        }
        // v1.6.25 — entry refinement row: kind + date windows (applies in
        // query AND browse mode).
        ScrollRow(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.callmony.ui.kit.Chip(label = t("sr.allKinds"), compact = true, active = kindFilter == null, onPress = { kindFilter = null })
                com.callmony.ui.kit.Chip(label = t("lg.credit"), compact = true, active = kindFilter == "credit", onPress = { kindFilter = "credit" })
                com.callmony.ui.kit.Chip(label = t("lg.debit"), compact = true, active = kindFilter == "debit", onPress = { kindFilter = "debit" })
                com.callmony.ui.kit.Chip(label = t("lg.income"), compact = true, active = kindFilter == "income", onPress = { kindFilter = "income" })
                com.callmony.ui.kit.Chip(label = t("lg.expense"), compact = true, active = kindFilter == "expense", onPress = { kindFilter = "expense" })
                com.callmony.ui.kit.Chip(label = t("sr.anyDate"), compact = true, active = dateFilter == null, onPress = { dateFilter = null })
                com.callmony.ui.kit.Chip(label = t("rm.today"), compact = true, active = dateFilter == "today", onPress = { dateFilter = "today" })
                com.callmony.ui.kit.Chip(label = t("sr.last7"), compact = true, active = dateFilter == "7d", onPress = { dateFilter = "7d" })
                com.callmony.ui.kit.Chip(label = t("sr.last30"), compact = true, active = dateFilter == "30d", onPress = { dateFilter = "30d" })
                com.callmony.ui.kit.Chip(label = t("sr.overdueOnly"), compact = true, active = dateFilter == "overdue", onPress = { dateFilter = "overdue" })
                // v1.6.26 — CUSTOM DATE RANGE (user report: "the date tab
                // should have custom date to filter any date")
                com.callmony.ui.kit.Chip(label = t("sr.customDate"), icon = "calendar-blank-outline", compact = true, active = dateFilter == "custom", onPress = { customSheet = true })
            }
        }
        // v1.6.14: keep the last results above the phone's nav bar
        // v1.6.26 — no navigationBarsPadding: the search renders inside the
        // shell content area, above the always-visible app nav.
        // v1.6.27 — THE "CREDIT TAB IS NOT LISTING THE CONTACTS" FIX. The
        // result sections (contacts/entries/lists/…) used to live in the
        // FINAL else branch — reachable only with a QUERY. In BROWSE mode
        // (no query, a kind/date/type filter picked) the LazyColumn rendered
        // NOTHING but the all-empty state: tapping Credit/Debit/Income/
        // Expense showed a blank screen even though the filters matched
        // hundreds of contacts. The sections now render in BOTH modes —
        // the empty states only fire when their branch actually has nothing.
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            when {
                browsing -> {
                    if (matchedContacts.isEmpty() && entries.isEmpty() && lists.isEmpty() && notes.isEmpty() && reminders.isEmpty() && banks.isEmpty()) {
                        item { EmptyState(icon = "magnify", title = t("sr.noResults"), hint = t("sr.tryHint")) }
                    }
                }
                debounced.isBlank() -> {
                    item {
                        EmptyState(icon = "magnify", title = t("sr.everythingHint"), hint = t("sr.keepTyping"))
                    }
                }
                matchedContacts.isEmpty() && entries.isEmpty() && lists.isEmpty() && notes.isEmpty() && reminders.isEmpty() && banks.isEmpty() -> {
                    item { EmptyState(icon = "magnify", title = t("sr.noResultsFor"), hint = debounced) }
                }
            }
            if (matchedContacts.isNotEmpty() && showContacts) {
                    item { GroupLabel(t("misc.contacts")) }
                    items(matchedContacts.take(50), key = { "c-${it.recordID}" }) { c ->
                        val bal = ownerFullBalance(AppStore.state.entries, AppStore.state.lists, "c:${c.recordID}")
                        val callNumber = primaryPhone(c)
                        PressableRow(onClick = { UiStore.open(Overlay.Contact(c.recordID)) }) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Avatar(c, size = 36)
                                Column(Modifier.weight(1f)) {
                                    Text(displayName(c), fontSize = 16.sp, color = C.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (callNumber != null) {
                                        Text(
                                            prettyPhone(callNumber, countryCode),
                                            fontSize = 13.sp,
                                            color = C.faint,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                if (bal != 0.0) Money(bal, signed = true, colorize = true, compact = true, size = 13)
                                /* v1.6.16 — calling from search made EASY: a
                                   one-tap call button on every contact hit
                                   (no detour through the detail sheet). */
                                if (callNumber != null) {
                                    Box(
                                        Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(C.brandSoft)
                                            .clickable {
                                                Calls.placeCallWithToast(callNumber)
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) { MdiIcon("phone", 18, C.brand) }
                                }
                            }
                        }
                    }
                }
                if (entries.isNotEmpty() && showEntries) {
                    item { GroupLabel(t("misc.entry")) }
                    items(entries, key = { "e-${it.id}" }) { e ->
                        PressableRow(onClick = { UiStore.open(Overlay.EntryEdit(e.id, e.ownerId)) }) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MdiIcon("book-outline", 18, C.sub)
                                Text("${e.kind ?: e.type} · ${ownerNameOf(e.ownerId)}", fontSize = 15.sp, color = C.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Money(e.amount, size = 13, compact = true)
                            }
                        }
                    }
                }
                if (lists.isNotEmpty() && showLists) {
                    item { GroupLabel(t("misc.list")) }
                    items(lists, key = { "l-${it.id}" }) { l ->
                        PressableRow(onClick = { UiStore.open(Overlay.ListDetail(l.id)) }) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MdiIcon("clipboard-list-outline", 18, C.sub)
                                Text(l.title, fontSize = 15.sp, color = C.ink, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (notes.isNotEmpty() && showNotes) {
                    item { GroupLabel(t("misc.note")) }
                    items(notes, key = { "n-${it.id}" }) { n ->
                        PressableRow(onClick = { UiStore.open(Overlay.NoteEdit(n.id, n.ownerId)) }) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MdiIcon("notebook-outline", 18, C.sub)
                                Text(n.title.ifEmpty { t("nt.untitled") }, fontSize = 15.sp, color = C.ink, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (reminders.isNotEmpty() && showReminders) {
                    item { GroupLabel(t("misc.reminder")) }
                    items(reminders, key = { "r-${it.id}" }) { r ->
                        PressableRow(onClick = { UiStore.open(Overlay.ReminderEdit(r.id, r.ownerId)) }) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MdiIcon("bell-outline", 18, C.sub)
                                Text(r.title, fontSize = 15.sp, color = C.ink, modifier = Modifier.weight(1f))
                                Text(fdEpoch(r.snoozedUntil ?: r.dueAt), fontSize = 12.sp, color = C.faint)
                            }
                        }
                    }
                }
                if (banks.isNotEmpty() && showBanks) {
                    item { GroupLabel(t("misc.bank")) }
                    items(banks, key = { "b-${it.id}" }) { b ->
                        PressableRow(onClick = { UiStore.open(Overlay.BankEdit(b.id, b.ownerId)) }) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MdiIcon("bank-outline", 18, C.sub)
                                Text(b.bankName ?: b.label, fontSize = 15.sp, color = C.ink, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // v1.6.26 — the custom date-range sheet (from → to, native calendar)
    if (customSheet) {
        CustomDateRangeSheet(
            from = customFrom,
            to = customTo,
            onClose = { customSheet = false },
            onApply = { f, toD ->
                customFrom = f
                customTo = toD
                dateFilter = "custom"
                customSheet = false
            },
        )
    }
}

/** v1.6.26 — pick ANY date range for the search date filter (user report:
 *  "the date tab should have custom date to filte any date"). Both ends
 *  optional (an open-ended range is valid); Apply activates the Custom
 *  chip with the chosen window. */
@Composable
private fun CustomDateRangeSheet(
    from: String?,
    to: String?,
    onClose: () -> Unit,
    onApply: (String?, String?) -> Unit,
) {
    val C = C()
    var localFrom by remember { mutableStateOf(from) }
    var localTo by remember { mutableStateOf(to) }
    BottomSheet(title = t("sr.customDate"), onClose = onClose) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    com.callmony.ui.kit.PickerField(
                        label = t("sr.dateFrom"),
                        value = localFrom?.let { com.callmony.lib.fd(it) } ?: t("sr.anyDate"),
                        icon = "calendar-arrow-left",
                        onPress = {
                            val initial = try {
                                com.callmony.lib.KmpDate(
                                    localFrom?.split("-")?.getOrNull(0)?.toInt() ?: dateOf(now()).year,
                                    ((localFrom?.split("-")?.getOrNull(1)?.toIntOrNull() ?: (dateOf(now()).month0 + 1))) - 1,
                                    localFrom?.split("-")?.getOrNull(2)?.toIntOrNull() ?: dateOf(now()).day,
                                    12, 0, 0,
                                ).time()
                            } catch (_: Exception) { now() }
                            com.callmony.platform.NativeDateTime.pickDate(
                                epochMillis = initial,
                                onPick = { epoch -> localFrom = isoOf(epoch) },
                            )
                        },
                    )
                }
                Box(Modifier.weight(1f)) {
                    com.callmony.ui.kit.PickerField(
                        label = t("sr.dateTo"),
                        value = localTo?.let { com.callmony.lib.fd(it) } ?: t("sr.anyDate"),
                        icon = "calendar-arrow-right",
                        onPress = {
                            val initial = try {
                                com.callmony.lib.KmpDate(
                                    localTo?.split("-")?.getOrNull(0)?.toInt() ?: dateOf(now()).year,
                                    ((localTo?.split("-")?.getOrNull(1)?.toIntOrNull() ?: (dateOf(now()).month0 + 1))) - 1,
                                    localTo?.split("-")?.getOrNull(2)?.toIntOrNull() ?: dateOf(now()).day,
                                    12, 0, 0,
                                ).time()
                            } catch (_: Exception) { now() }
                            com.callmony.platform.NativeDateTime.pickDate(
                                epochMillis = initial,
                                onPick = { epoch -> localTo = isoOf(epoch) },
                            )
                        },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(
                    label = t("sr.clear"),
                    variant = "secondary",
                    small = true,
                    onPress = { localFrom = null; localTo = null },
                )
                Btn(
                    label = t("sr.applyRange"),
                    small = true,
                    onPress = { onApply(localFrom, localTo) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** v1.6.26 — "YYYY-MM-DD" of an epoch (string-compare-safe, like daysAgoStr). */
private fun isoOf(epoch: Long): String {
    val d = dateOf(epoch)
    return "${d.year}-${(d.month0 + 1).toString().padStart(2, '0')}-${d.day.toString().padStart(2, '0')}"
}

private fun matchesEntry(e: com.callmony.model.LedgerEntry, q: String): Boolean =
    (e.category ?: "").contains(q, true) || (e.note ?: "").contains(q, true) ||
        e.kind?.contains(q, true) == true || e.type.contains(q, true)

/** v1.6.25 — "YYYY-MM-DD" of N days ago (string-compare-safe against
 *  entry.date for the search date windows). */
private fun daysAgoStr(days: Int): String {
    val d = dateOf(now() - days * 86_400_000L)
    return "${d.year}-${(d.month0 + 1).toString().padStart(2, '0')}-${d.day.toString().padStart(2, '0')}"
}

@Composable
private fun GroupLabel(label: String) {
    val C = C()
    Text(
        label,
        fontSize = F.small.sp,
        fontWeight = FontWeight.ExtraBold,
        color = C.sub,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/* ---------------- SpeedDial overlay ---------------- */

@Composable
fun SpeedDialOverlay() {
    val C = C()
    val pop = { UiStore.pop() }
    val contacts = AppStore.state.deviceContacts
    var assignKey by remember { mutableStateOf<String?>(null) }
    var actionFor by remember { mutableStateOf<Pair<String, DeviceContact>?>(null) }

    if (assignKey != null) {
        com.callmony.ui.components.ContactPicker(
            title = t("spd.assignToKey", mapOf("key" to assignKey!!)),
            onPick = { c ->
                AppStore.setSpeedDial(assignKey!!, c.recordID)
                assignKey = null
                Toasts.toast(t("spd.speedDialSet"))
            },
            onBack = { assignKey = null },
        )
        return
    }

    actionFor?.let { (key, c) ->
        BottomSheet(title = "${t("spd.keyOf")} $key · ${displayName(c)}", onClose = { actionFor = null }) {
            Column {
                listOf(
                    // v1.6.27 — the FAVOURITE number dials first (like every surface)
                    Triple("phone", t("spd.callNow")) { val ph = com.callmony.lib.primaryPhone(c); if (ph != null) Calls.placeCallWithToast(ph) },
                    Triple("swap-horizontal", t("spd.replace")) { assignKey = key },
                    Triple("close", t("spd.removeFromKey")) { AppStore.setSpeedDial(key, null); Toasts.toast(t("spd.speedDialCleared")); actionFor = null },
                ).forEach { (icon, label, run) ->
                    PressableRow(onClick = { actionFor = null; run() }) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MdiIcon(icon, 20, C.ink)
                            Text(label, fontSize = 16.sp, color = C.ink)
                        }
                    }
                }
            }
        }
        return
    }

    com.callmony.ui.components.Sheet(title = t("kp.speedDial"), subtitle = t("spd.oneLongPress"), onBack = pop) {
        Column {
            (2..9).chunked(4).forEach { rowKeys ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowKeys.forEach { d ->
                        val recordID = AppStore.state.contactX.find { it.speedDial == d.toString() }?.recordID
                            ?: AppStore.state.settings.speedDial[d.toString()]
                        val target = recordID?.let { contacts.find { c -> c.recordID == it } }
                        Column(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(C.card)
                                .border(1.dp, if (target != null) C.brandSoft else C.line, RoundedCornerShape(16.dp))
                                .clickable {
                                    if (target != null) actionFor = d.toString() to target
                                    else assignKey = d.toString()
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(d.toString(), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = C.faint)
                            if (target != null) {
                                Avatar(target, size = 36)
                                Text(
                                    displayName(target),
                                    fontSize = F.tiny.sp, color = C.ink, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                )
                            } else {
                                MdiIcon("plus", 20, C.faint)
                            }
                        }
                    }
                }
            }
        }
    }
}



/* ---------------- Merge duplicates overlay ---------------- */

@Composable
fun MergeOverlay() {
    val C = C()
    val pop = { UiStore.pop() }
    val contacts = AppStore.state.deviceContacts
    val countryCode = AppStore.state.settings.countryCode
    var pairs by remember { mutableStateOf(findDuplicatePairs(contacts, countryCode)) }

    Sheet(title = t("spd.mergeDuplicates"), subtitle = t("spd.scanSimilar"), onBack = pop) {
        Column {
            Row(Modifier.padding(bottom = 10.dp)) {
                Btn(small = true, label = t("spd.rescan"), icon = "refresh", variant = "secondary", onPress = {
                    pairs = findDuplicatePairs(AppStore.state.deviceContacts, countryCode)
                    Toasts.toast(if (pairs.isEmpty()) t("spd.everyUnique") else t("spd.pairsFound", mapOf("n" to pairs.size.toString())))
                })
            }
            if (pairs.isEmpty()) {
                EmptyState(icon = "account-check-outline", title = t("spd.everyUnique"))
            } else {
                pairs.forEach { (a, b) ->
                    Card(Modifier.padding(bottom = 10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MergeCard(a, t("spd.keep"), C.green, Modifier.weight(1f))
                            MergeCard(b, t("spd.duplicate"), C.red, Modifier.weight(1f))
                        }
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Btn(
                                small = true, label = t("spd.mergeBtn"), variant = "primary",
                                onPress = {
                                    AppStore.scope.launch {
                                        // v1.6.13: WRITE_CONTACTS gate for merges.
                                        val ok = com.callmony.lib.requestContactsWrite() &&
                                            Platform.contacts.addPhoneToContact(a.recordID, "other", b.phones.firstOrNull()?.number ?: "")
                                        if (ok) Platform.contacts.deleteContact(b.recordID)
                                        // v1.6.21 — binder work above ran on the Default
                                        // worker; state writes + toasts publish on Main
                                        // (single-writer snapshot rule).
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            if (ok) {
                                                pairs = pairs.filter { it.first != a && it.second != a && it.first != b && it.second != b }
                                                Toasts.toast(t("spd.contactsMerged"))
                                            } else Toasts.toast(t("act.couldntCopy"), "error")
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Btn(
                                small = true, label = t("spd.dismissPair"), variant = "secondary",
                                onPress = { pairs = pairs.filter { it.first != a && it.second != b } },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MergeCard(c: DeviceContact, pill: String, tone: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(c, size = 44)
        Text(displayName(c), fontSize = F.small.sp, fontWeight = FontWeight.Bold, color = C().ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        c.phones.firstOrNull()?.let { Text(prettyPhone(it.number), fontSize = F.tiny.sp, color = C().faint) }
        Pill(pill, if (tone == C().green) "green" else "red")
    }
}

private fun findDuplicatePairs(contacts: List<DeviceContact>, countryCode: String): List<Pair<DeviceContact, DeviceContact>> {
    val out = mutableListOf<Pair<DeviceContact, DeviceContact>>()
    val used = mutableSetOf<String>()
    val byName = contacts.groupBy { displayName(it).lowercase() }
    byName.forEach { (_, group) ->
        if (group.size > 1) {
            for (i in 0 until group.size - 1) {
                if (group[i].recordID !in used && group[i + 1].recordID !in used) {
                    out.add(group[i] to group[i + 1])
                    used.add(group[i].recordID); used.add(group[i + 1].recordID)
                }
            }
        }
    }
    val byNat = mutableMapOf<String, DeviceContact>()
    contacts.forEach { c ->
        c.phones.forEach { p ->
            val digits = com.callmony.lib.nationalDigits(p.number, countryCode)
            if (digits.length > 5) {
                val existing = byNat[digits]
                if (existing != null && existing.recordID != c.recordID && existing.recordID !in used && c.recordID !in used) {
                    out.add(existing to c)
                    used.add(existing.recordID); used.add(c.recordID)
                } else byNat[digits] = c
            }
        }
    }
    return out
}

/* ---------------- About overlay ---------------- */

@Composable
fun AboutOverlay() {
    val C = C()
    val pop = { UiStore.pop() }
    Sheet(title = t("st.aboutApp"), onBack = pop) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // v1.6.13 — the real logo here too (was the old icon tile)
            com.callmony.ui.LogoTile(74, corner = 20)
            Text(
                "callmony",
                fontSize = F.title.sp,
                fontWeight = FontWeight.ExtraBold,
                color = C.ink,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(t("hdr.tagline"), fontSize = F.small.sp, color = C.sub)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(com.callmony.lib.APP_VERSION, "brand")
                Pill(t("about.offline"), "green", icon = "cloud-off-outline")
            }
            Spacer(Modifier.height(10.dp))
            listOf(
                Triple("phone-in-talk", t("about.contactsCalls"), t("about.contactsCallsText")),
                Triple("cash-multiple", t("about.moneyLedger"), t("about.moneyLedgerText")),
                Triple("calculator-variant-outline", t("about.calcLists"), t("about.calcListsText")),
                Triple("notebook-outline", t("tab.notes"), t("about.notesText")),
                Triple("bell-outline", t("tab.reminders"), t("about.remindersText")),
                Triple("bank-outline", t("tab.bank"), t("about.banksText")),
            ).forEach { (icon, title, text) ->
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(C.brandSoft),
                            contentAlignment = Alignment.Center,
                        ) { MdiIcon(icon, 19, C.brand) }
                        Column {
                            Text(title, fontSize = F.body.sp, fontWeight = FontWeight.Bold, color = C.ink)
                            Text(text, fontSize = F.small.sp, color = C.sub)
                        }
                    }
                }
            }
            Text(
                t("sto.privacyNote"),
                fontSize = F.tiny.sp,
                color = C.faint,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/* ---------------- AlarmDialog — full-screen reminder alert ---------------- */

@Composable
fun AlarmDialog() {
    val C = C()
    val r = UiStore.alarmQueue.firstOrNull() ?: return
    val kind = r.kind
    val icon = if (kind == "birthday") "cake" else "bell-ring"
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "pulseScale",
    )

    LaunchedEffect(Unit) {
        Platform.notify.vibrate(longArrayOf(0, 400, 200, 400))
    }

    /* v1.6.17 — RN dismiss() parity: closing the dialog resolves the
     * reminder — repeating ones advance to their next occurrence (and the
     * next alarm gets armed), one-shots are marked done. The old plain
     * shiftAlarm left them overdue in the list forever. */
    fun close() {
        if (r.repeat != "none") AppStore.fireReminder(r.id) else AppStore.completeReminder(r.id)
        UiStore.shiftAlarm()
    }

    // v1.6.15: the native back button dismisses the alarm dialog instead of
    // exiting the app (inner handler wins over the shell's root handler)
    com.callmony.ui.kit.AppBackHandler(enabled = true) { close() }

    Box(
        Modifier
            .fillMaxSize()
            .background(C.scrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(C.card)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .scale(pulse.value),
                contentAlignment = Alignment.Center,
            ) { MdiIcon(icon, 40, C.brand) }
            Text(
                t("rm.kind${kind.replaceFirstChar { it.uppercase() }}"),
                fontSize = F.tiny.sp,
                color = C.brand,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(r.title, fontSize = F.title.sp, fontWeight = FontWeight.ExtraBold, color = C.ink, textAlign = TextAlign.Center)
            r.notes?.let { Text(it, fontSize = F.small.sp, color = C.sub, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)) }
            Text(
                "${if (r.dueAt < now()) t("rm.overdue") else ""} ${ft(r.snoozedUntil ?: r.dueAt)} · ${t("rm.repeat${r.repeat.replaceFirstChar { it.uppercase() }}")}".trim(),
                fontSize = F.tiny.sp,
                color = C.sub,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
            Btn(label = t("act.done"), onPress = { AppStore.completeReminder(r.id); close() }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(small = true, label = t("rm.snooze10"), variant = "secondary", onPress = {
                    AppStore.snoozeReminder(r.id, now() + 600000); close()
                }, modifier = Modifier.weight(1f))
                Btn(small = true, label = t("rm.snooze1h"), variant = "secondary", onPress = {
                    AppStore.snoozeReminder(r.id, now() + 3600000); close()
                }, modifier = Modifier.weight(1f))
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(small = true, label = t("rm.snooze3h"), variant = "secondary", onPress = {
                    AppStore.snoozeReminder(r.id, now() + 3 * 3600000); close()
                }, modifier = Modifier.weight(1f))
                Btn(small = true, label = t("rm.snoozeTomorrow"), variant = "secondary", onPress = {
                    val d = com.callmony.lib.dateOf(now() + 86400000)
                    AppStore.snoozeReminder(r.id, com.callmony.lib.KmpDate(d.year, d.month0, d.day, 9, 0, 0).time()); close()
                }, modifier = Modifier.weight(1f))
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(small = true, label = t("act.edit"), variant = "ghost", onPress = { close(); UiStore.open(Overlay.ReminderEdit(r.id, r.ownerId)) }, modifier = Modifier.weight(1f))
                Btn(small = true, label = t("act.close"), variant = "ghost", onPress = { close() }, modifier = Modifier.weight(1f))
            }
        }
    }
}
