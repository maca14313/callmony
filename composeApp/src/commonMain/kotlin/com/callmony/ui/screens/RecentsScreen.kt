package com.callmony.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Calls
import com.callmony.lib.Messengers
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.lib.dayGroup
import com.callmony.lib.displayName
import com.callmony.lib.durationStr
import com.callmony.lib.entrySignedRemaining
import com.callmony.lib.ft
import com.callmony.lib.listTotals
import com.callmony.lib.prettyPhone
import com.callmony.lib.primaryPhone
import com.callmony.lib.relTime
import com.callmony.model.DeviceContact
import com.callmony.model.DevicePhone
import com.callmony.platform.PermState
import com.callmony.theme.F
import com.callmony.platform.Platform
import com.callmony.ui.components.BottomSheet
import com.callmony.ui.components.PressableRow
import com.callmony.ui.components.SwipeAction
import com.callmony.ui.components.SwipeRow
import com.callmony.ui.kit.Avatar
import com.callmony.ui.kit.Btn
import com.callmony.ui.kit.C
import com.callmony.ui.kit.Chip
import com.callmony.ui.kit.EmptyState
import com.callmony.ui.kit.MdiIcon
import com.callmony.ui.kit.Money
import com.callmony.ui.kit.Pill
import com.callmony.ui.kit.IconBtn
import com.callmony.ui.kit.Spinner
import com.callmony.ui.kit.Toasts
import com.callmony.ui.kit.vibrate
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToLong
import com.callmony.ui.components.ScrollRow

/* RecentsScreen — the home tab. Port of src/screens/RecentsScreen.tsx. */

private const val PAGE = 60

data class CallRow(
    val key: String,
    val phone: String,
    val name: String?,
    val contact: DeviceContact?,
    val direction: String, // incoming | outgoing | missed
    val at: Long,
    val duration: Int,
    val appId: String? = null,
    val count: Int = 1, // v1.6.15: back-to-back missed calls collapsed into one card
)

@Composable
fun RecentsScreen() {
    val C = C()
    val contacts = AppStore.state.deviceContacts
    val countryCode = AppStore.state.settings.countryCode
    val entries = AppStore.state.entries
    val lists = AppStore.state.lists
    val systemCalls = AppStore.state.systemCalls
    val callLogPerm = AppStore.state.callLogPerm
    val contactsPerm = AppStore.state.contactsPerm
    val contactX = AppStore.state.contactX

    var filter by remember { mutableStateOf("all") }
    var sheetRow by remember { mutableStateOf<CallRow?>(null) }
    // v1.6.26 — the long-press call-icon "which number?" picker (held HERE,
    // not inside the card: a BottomSheet composed inside a LazyColumn item
    // collapses its full-screen scrim to item size — unbounded height)
    var callOptsRow by remember { mutableStateOf<CallRow?>(null) }
    var page by remember { mutableStateOf(1) }
    var refreshing by remember { mutableStateOf(false) }
    var contactsAskedOnce by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(filter) { page = 1 }

    /* silent background refresh when permission state is unknown */
    LaunchedEffect(callLogPerm) {
        if (callLogPerm == PermState.UNKNOWN) AppStore.loadSystemCalls()
    }

    /* per-contact balances (entries + open post-to-ledger lists) */
    val balances = remember(entries, lists) {
        val map = mutableMapOf<String, Double>()
        entries.forEach { e ->
            if (e.deletedAt != null || !e.ownerId.startsWith("c:")) return@forEach
            val id = e.ownerId.substring(2)
            val signed = (if (e.type == "credit") 1 else -1) * (e.amount - e.payments.sumOf { it.amount })
            map[id] = (((map[id] ?: 0.0) + signed) * 100).roundToLong() / 100.0
        }
        lists.forEach { l ->
            if (l.deletedAt != null || !l.ownerId.startsWith("c:") || !l.postToLedger) return@forEach
            val id = l.ownerId.substring(2)
            val pending = listTotals(l).pending
            map[id] = (((map[id] ?: 0.0) + pending) * 100).roundToLong() / 100.0
        }
        map
    }

    val owedToYou = entries.filter { it.deletedAt == null && it.ownerId.startsWith("c:") && it.type == "credit" }
        .sumOf { it.amount - it.payments.sumOf { p -> p.amount } }
    val youOwe = entries.filter { it.deletedAt == null && it.ownerId.startsWith("c:") && it.type == "debit" }
        .sumOf { it.amount - it.payments.sumOf { p -> p.amount } }

    /* unified rows from the system call log.
       v1.6.14 FIX: `contacts` is a key (RN parity — its useMemo deps are
       [systemCalls, appRecents, contacts]). The port keyed only on
       systemCalls, so the phone→contact match was frozen at first render:
       every card stayed "unsaved" after the contact book loaded (or after
       creating a contact), and tapping a card opened the action sheet
       instead of the contact detail.
       v1.6.15 (user request): consecutive BACK-TO-BACK missed calls from the
       SAME number collapse into a single card carrying how many times it
       was missed (stock-dialer behavior) — one glance shows “(3) missed”. */
    val rows: List<CallRow> = remember(systemCalls, contacts) {
        val raw = systemCalls.map { c ->
            CallRow(
                key = c.id,
                phone = c.number,
                name = c.cachedName,
                contact = if (c.number.isNotEmpty()) AppStore.contactByPhone(c.number) else null,
                direction = when (c.type) {
                    2 -> "outgoing"
                    3, 5, 6 -> "missed"
                    else -> "incoming"
                },
                at = c.date,
                duration = c.duration,
            )
        }
        // collapse runs of consecutive missed calls from the same number
        // (the log is newest-first; “back to back” = adjacent in the log)
        val out = mutableListOf<CallRow>()
        var i = 0
        while (i < raw.size) {
            val r = raw[i]
            if (r.direction == "missed" && r.phone.replace(Regex("\\D"), "").length >= 3) {
                var j = i + 1
                while (j < raw.size &&
                    raw[j].direction == "missed" &&
                    raw[j].phone.replace(Regex("\\D"), "") == r.phone.replace(Regex("\\D"), "")
                ) j++
                val run = j - i
                out.add(if (run > 1) r.copy(key = r.key + "-x$run", count = run) else r)
                i = j
            } else {
                out.add(r)
                i++
            }
        }
        out
    }

    val favSet = remember(contactX) { contactX.filter { it.favorite }.map { it.recordID }.toSet() }
    val filtered = remember(rows, filter, balances, favSet) {
        when (filter) {
            "theyOwe", "youOwe" -> rows.filter { r ->
                r.contact != null && run {
                    val b = balances[r.contact!!.recordID] ?: 0.0
                    if (filter == "theyOwe") b > 0.005 else b < -0.005
                }
            }
            "favorites" -> rows.filter { it.contact != null && favSet.contains(it.contact!!.recordID) }
            "all" -> rows
            else -> rows.filter { it.direction == filter }
        }
    }
    val missedCount = rows.count { it.direction == "missed" }

    /* flattened items + day headers */
    val items = remember(filtered, balances) {
        val out = mutableListOf<Any>()
        var current = ""
        filtered.forEach { r ->
            val label = dayGroup(r.at)
            if (label != current) {
                current = label
                out.add(label)
            }
            out.add(r)
        }
        out
    }
    val pagedItems = items.take(page * PAGE)
    val hasMore = pagedItems.size < items.size

    /* infinite scroll trigger */
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                if (listState.layoutInfo.totalItemsCount > 0 &&
                    last >= listState.layoutInfo.totalItemsCount - 3 && hasMore
                ) page += 1
            }
    }

    val favContacts = remember(favSet, contacts) {
        favSet.mapNotNull { id -> contacts.find { it.recordID == id } }
    }

    fun onTap(row: CallRow) {
        if (row.contact != null) UiStore.open(Overlay.Contact(row.contact.recordID))
        else sheetRow = row
    }

    fun onCall(phone: String) { Calls.placeCall(phone) }
    fun onWhatsApp(phone: String) { Messengers.waLink(phone) }
    fun onSMS(phone: String) { Messengers.smsLink(phone) }
    fun onToggleFav(recordID: String) { AppStore.toggleFavorite(recordID) }
    fun onCopy(phone: String) {
        Platform.share.copyText(phone)
        Toasts.toast(t("cd.numberCopied"))
    }
    fun onCreateContact(phone: String) { UiStore.open(Overlay.ContactNew(phone = phone)) }
    fun onDetail(row: CallRow) {
        if (row.contact != null) UiStore.open(Overlay.Contact(row.contact.recordID, initialSeg = "info"))
        else sheetRow = row
    }

    fun askContacts() {
        if (contactsAskedOnce) {
            Platform.share.openUrl("package:com.callmony")
            return
        }
        contactsAskedOnce = true
        Platform.contacts.requestPermission { granted ->
            if (granted) {
                contactsAskedOnce = false
                Toasts.toast(t("cd.contactsReady"))
                AppStore.refreshDeviceContacts()
            }
        }
    }

    /* permission denied → request card */
    if (callLogPerm == PermState.DENIED) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            EmptyState(
                icon = "phone-missed",
                title = t("perm.callLogTitle"),
                hint = t("perm.callLogMsg"),
                actionLabel = t("st.allow"),
                onAction = { AppStore.loadSystemCalls(true, forceAsk = true) },
            )
            Btn(
                label = t("perm.openSettings"),
                icon = "cog-outline",
                variant = "secondary",
                small = true,
                onPress = { Platform.share.openUrl("package:com.callmony") },
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        // credit summary strip — three slim stat cards
        if (owedToYou > 0.005 || youOwe > 0.005) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatCard("arrow-bottom-left", C.green, t("cd.toReceive"), abs(owedToYou), C.card, C, Modifier.weight(1f))
                StatCard("arrow-top-right", C.red, t("cd.toPay"), -abs(youOwe), C.card, C, Modifier.weight(1f))
                StatCard("scale-balance", C.brand, t("lg.net"), abs(owedToYou) - abs(youOwe), C.brandSoft, C, Modifier.weight(1f))
            }
        }
        // contacts-locked row
        if (contactsPerm == PermState.DENIED && contacts.isEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0x59b97d10), RoundedCornerShape(14.dp))
                    .clickable { askContacts() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MdiIcon("account-lock-outline", 18, C.amber)
                Column(Modifier.weight(1f)) {
                    Text(t("cd.contactsLocked"), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = C.ink, maxLines = 1)
                    Text(t("cd.contactsLockedHint"), fontSize = 13.sp, color = C.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Btn(
                    label = if (contactsAskedOnce) t("perm.openSettings") else t("st.allow"),
                    small = true,
                    onPress = { askContacts() },
                )
            }
        }
        // favorites strip
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)) {
            ScrollRow {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    favContacts.forEach { c ->
                        Column(
                            Modifier.width(62.dp).clickable { UiStore.open(Overlay.Contact(c.recordID)) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, C.brandSoft, CircleShape)
                                    .padding(3.dp),
                            ) { Avatar(c, size = 52) }
                            Text(
                                (c.givenName.ifEmpty { c.displayName }).split(" ").firstOrNull() ?: "?",
                                fontSize = 13.sp,
                                color = C.sub,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        // owe chips row
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Chip(compact = true, icon = "arrow-bottom-left", label = t("cd.theyOweYou"), active = filter == "theyOwe", onPress = { filter = "theyOwe" })
            Chip(compact = true, icon = "arrow-top-right", label = t("cd.youOweThem"), active = filter == "youOwe", onPress = { filter = "youOwe" })
        }
        // fallback filter row when the list is empty
        if (items.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DayFilters(filter, { filter = it }, missedCount)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(24.dp)
                        .clickable { UiStore.open(Overlay.ContactNew()) },
                    contentAlignment = Alignment.Center,
                ) { MdiIcon("account-plus", 16, C.brand) }
            }
        }
        // the list
        Box(Modifier.weight(1f)) {
            // v1.6.19 — THE WELCOME-SCREEN FREEZE FIX. The old condition
            // (callLogPerm == UNKNOWN && systemCalls.isEmpty()) rendered an
            // INFINITE CircularProgressIndicator whenever the permission was
            // unresolved — including the entire first-run onboarding, where
            // it spun invisibly UNDER the opaque welcome sheet and never
            // stopped: an endless Choreographer frame loop saturating the
            // main thread → "Callmony isn't responding" → the app closes
            // itself. Now the spinner appears ONLY while a call-log ask or
            // fetch is genuinely in flight AND the screen is actually
            // visible (onboarding done). An unresolved UNKNOWN state falls
            // through to the no-calls EmptyState — which never animates.
            if (callLogPerm == PermState.UNKNOWN && systemCalls.isEmpty() &&
                AppStore.state.settings.onboarded && AppStore.state.callLogBusy
            ) {
                Column(
                    Modifier.fillMaxSize().padding(top = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spinner(36)
                    Text(t("cd.loadingCalls"), fontSize = 14.sp, color = C.sub, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                }
            } else if (pagedItems.isEmpty()) {
                EmptyState(
                    icon = "history",
                    title = if (filter != "all") t("cd.noMatchingContacts") else t("cd.noCallsYet"),
                    hint = if (filter != "all") t("cd.tryAnotherFilter") else t("cd.noCallsHint"),
                )
            } else {
                CallList(
                    pagedItems = pagedItems,
                    balances = balances,
                    countryCode = countryCode,
                    filter = filter,
                    missedCount = missedCount,
                    onFilter = { filter = it },
                    listState = listState,
                    onTap = ::onTap,
                    onLongPress = { sheetRow = it },
                    onCall = ::onCall,
                    onWhatsApp = ::onWhatsApp,
                    onSMS = ::onSMS,
                    onToggleFav = ::onToggleFav,
                    onCopy = ::onCopy,
                    onCreateContact = ::onCreateContact,
                    onDetail = ::onDetail,
                    onPickNumber = { callOptsRow = it },
                    onRefresh = { AppStore.loadSystemCalls(true) },
                    refreshing = refreshing,
                )
            }
        }
    }

    // per-row action sheet
    sheetRow?.let { row ->
        CallRowSheet(
            row = row,
            onClose = { sheetRow = null },
            onDelete = row.appId?.let { id -> { AppStore.deleteRecent(id); Toasts.toast(t("cd.logDeleted")) } },
        )
    }

    // v1.6.26 — long-press the call icon → every number the contact has
    // (rendered HERE at screen level so the scrim covers the screen)
    // v1.6.27 — every row now carries: a COPY icon, the FAVOURITE star
    // (set/UNSET — directly beside and before the call icon, per the user
    // report) and the call icon; long-pressing the row copies the number.
    // Setting the star re-routes every dial surface to that number (cards,
    // keypad, speed dial, detail navbar all dial primaryPhone first).
    callOptsRow?.let { row ->
        val numbers = remember(row) {
            val seen = mutableSetOf<String>()
            row.contact?.phones?.mapNotNull { p ->
                val key = com.callmony.lib.normalizePhone(p.number, countryCode).replace(Regex("\\D"), "").ifEmpty { p.number.replace(Regex("\\D"), "") }
                if (key.isNotEmpty() && seen.add(key)) p else null
            } ?: emptyList()
        }
        val recordID = row.contact?.recordID
        // the live favourite — read from state.contactX so the star flips the
        // moment it is toggled (set/unset recomposes this sheet)
        val favNumber = recordID?.let { rid ->
            AppStore.state.contactX.find { it.recordID == rid }?.primaryPhone
        }
        BottomSheet(title = t("cd.chooseNumber"), onClose = { callOptsRow = null }) {
            Column {
                numbers.forEach { p ->
                    val isFav = favNumber != null && recordID != null &&
                        p.number.replace(Regex("\\D"), "") == favNumber.replace(Regex("\\D"), "")
                    PressableRow(
                        onClick = { callOptsRow = null; Calls.placeCall(p.number) },
                        onLongClick = { Platform.share.copyText(p.number); Toasts.toast(t("act.copied")) },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Pill(p.label, "brand")
                            Text(
                                prettyPhone(p.number, countryCode),
                                fontSize = F.body.sp,
                                color = C.ink,
                                modifier = Modifier.weight(1f),
                            )
                            // copy icon (also long-press the row)
                            IconBtn("content-copy", { Platform.share.copyText(p.number); Toasts.toast(t("act.copied")) }, size = 28)
                            // the FAVOURITE star — BESIDE the call icon, BEFORE
                            // it; taps toggle set/unset
                            if (recordID != null) {
                                IconBtn(
                                    if (isFav) "star" else "star-outline",
                                    {
                                        if (isFav) {
                                            AppStore.clearPrimaryPhone(recordID)
                                            Toasts.toast(t("cd.favNumberUnset"))
                                        } else {
                                            AppStore.setPrimaryPhone(recordID, p.number)
                                            Toasts.toast(t("cd.favNumberSet"))
                                        }
                                    },
                                    size = 30,
                                    color = if (isFav) Color(0xFFb97d10) else C.faint,
                                )
                            }
                            IconBtn("phone-outline", { callOptsRow = null; Calls.placeCall(p.number) }, size = 30)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(icon: String, tint: Color, label: String, value: Double, bg: Color, C: com.callmony.theme.ThemeColors, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, C.line, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            MdiIcon(icon, 10, tint)
            Text(label, fontSize = 10.sp, color = if (tint == C.brand) C.brand else C.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Money(value = value, colorize = true, size = 12, weight = FontWeight.ExtraBold, compact = true)
    }
}

@Composable
private fun DayFilters(filter: String, onSet: (String) -> Unit, missedCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Chip(compact = true, label = t("act.all"), active = filter == "all", onPress = { onSet("all") })
        Chip(iconOnly = true, icon = "phone-missed", label = t("cd.missed"), count = missedCount, danger = filter != "missed" && missedCount > 0, active = filter == "missed", onPress = { onSet("missed") })
        Chip(iconOnly = true, icon = "phone-incoming", label = t("cd.incoming"), active = filter == "incoming", onPress = { onSet("incoming") })
        Chip(iconOnly = true, icon = "phone-outgoing", label = t("cd.outgoing"), active = filter == "outgoing", onPress = { onSet("outgoing") })
        Chip(iconOnly = true, icon = "star", label = t("cd.favorites"), active = filter == "favorites", onPress = { onSet("favorites") })
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CallList(
    pagedItems: List<Any>,
    balances: Map<String, Double>,
    countryCode: String,
    filter: String,
    missedCount: Int,
    onFilter: (String) -> Unit,
    listState: LazyListState,
    onTap: (CallRow) -> Unit,
    onLongPress: (CallRow) -> Unit,
    onCall: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
    onSMS: (String) -> Unit,
    onToggleFav: (String) -> Unit,
    onCopy: (String) -> Unit,
    onCreateContact: (String) -> Unit,
    onDetail: (CallRow) -> Unit,
    onPickNumber: (CallRow) -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean,
) {
    val C = C()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
            // testTag lets UI tests scroll this list to deep items
            .testTag("recentsList"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 28.dp),
    ) {
        pagedItems.forEach { item ->
            when (item) {
                // v1.6.15 (user request): the day header (date · ＋ new-contact
                // · direction filter chips) rides along STICKY under the owe
                // chips while the call cards scroll — RN parity
                // (FlatList stickyHeaderIndices).
                is String -> stickyHeader(key = "D-$item") {
                    DayHeader(item, filter, onFilter, missedCount)
                }
                is CallRow -> item(key = item.key) {
                    CallCard(
                        row = item,
                        balance = item.contact?.let { balances[it.recordID] } ?: 0.0,
                        countryCode = countryCode,
                        onTap = onTap,
                        onLongPress = onLongPress,
                        onCall = onCall,
                        onWhatsApp = onWhatsApp,
                        onSMS = onSMS,
                        onToggleFav = onToggleFav,
                        onCopy = onCopy,
                        onCreateContact = onCreateContact,
                        onDetail = onDetail,
                        onPickNumber = onPickNumber,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(label: String, filter: String, onFilter: (String) -> Unit, missedCount: Int) {
    val C = C()
    Row(
        Modifier
            .fillMaxWidth()
            .background(C.canvas)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = C.sub, fontWeight = FontWeight.SemiBold)
        Box(
            Modifier
                .size(24.dp)
                .clickable { UiStore.open(Overlay.ContactNew()) },
            contentAlignment = Alignment.Center,
        ) { MdiIcon("account-plus", 16, C.brand) }
        Spacer(Modifier.weight(1f))
        DayFilters(filter, onFilter, missedCount)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CallCard(
    row: CallRow,
    balance: Double,
    countryCode: String,
    onTap: (CallRow) -> Unit,
    onLongPress: (CallRow) -> Unit,
    onCall: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
    onSMS: (String) -> Unit,
    onToggleFav: (String) -> Unit,
    onCopy: (String) -> Unit,
    onCreateContact: (String) -> Unit,
    onDetail: (CallRow) -> Unit,
    /** v1.6.26 — long-press on the call icon when the contact has more
     *  than one distinct number (the picker itself renders at screen level
     *  in RecentsScreen — a BottomSheet inside a LazyColumn item would
     *  collapse its scrim). */
    onPickNumber: (CallRow) -> Unit,
) {
    val C = C()
    val digits = row.phone.replace(Regex("\\D"), "")
    val hasRealNumber = digits.replaceFirst(Regex("^0+"), "").length >= 3
    // v1.6.15 (user request): when the number is NOT saved as a contact, the
    // PHONE NUMBER itself is the card's name line (stock-dialer behavior) —
    // the carrier-cached name no longer takes the title slot.
    val title = row.contact?.let { displayName(it) }
        ?: (if (hasRealNumber) prettyPhone(row.phone, countryCode) else "")
        .ifEmpty { t("cl.unknownNumber") }
    val meta = when (row.direction) {
        "missed" -> Triple("phone-missed", C.red, t("cd.missed"))
        "incoming" -> Triple("phone-incoming", C.green, t("cd.incoming"))
        else -> Triple("phone-outgoing", C.brand, t("cd.outgoing"))
    }
    // v1.6.26 — the card calls the contact's FAVOURITE number first
    // (ContactX.primaryPhone), falling back to the first stored phone, then
    // the raw call-log number — matching what the contact detail page dials.
    val callNumber = primaryPhone(row.contact) ?: row.phone
    // v1.6.26 — LONG-PRESS THE CALL ICON → every number the contact has
    // (user report: "when the call icon is pressed longer it should show
    // the phone number options to call if there is any"). Distinct numbers
    // only; the picker is hoisted to screen level via onPickNumber.
    val distinctNumbers = remember(row.contact, countryCode) {
        val seen = mutableSetOf<String>()
        row.contact?.phones?.mapNotNull { p ->
            val key = com.callmony.lib.normalizePhone(p.number, countryCode).replace(Regex("\\D"), "").ifEmpty { p.number.replace(Regex("\\D"), "") }
            if (key.isNotEmpty() && seen.add(key)) p else null
        } ?: emptyList()
    }

    // v1.6.15 (user request): the full quick-action set on swipe, not just
    // call + favourite — left drag: Call · WhatsApp · SMS · Favourite;
    // right drag: Copy number · Create/Add contact · Info.
    val leftActions = if (hasRealNumber) buildList {
        add(SwipeAction(t("act.call"), "phone", Color(0xFF16a085)) { onCall(callNumber) })
        add(SwipeAction(t("act.whatsapp"), "whatsapp", Color(0xFF1faa53)) { onWhatsApp(callNumber) })
        add(SwipeAction(t("act.sms"), "message-text-outline", Color(0xFF4a7d9b)) { onSMS(callNumber) })
        if (row.contact != null) add(SwipeAction(t("cd.favorites"), "star", Color(0xFFb97d10)) { onToggleFav(row.contact!!.recordID) })
    } else emptyList()
    val rightActions = if (hasRealNumber) buildList {
        add(SwipeAction(t("cd.copyNumber"), "content-copy", Color(0xFF5c6f6c)) { onCopy(callNumber) })
        add(
            if (row.contact != null) SwipeAction(t("cd.callHistory"), "account-circle-outline", C.brand) { onDetail(row) }
            else SwipeAction(t("cd.createContact"), "account-plus-outline", C.brand) { onCreateContact(callNumber) }
        )
    } else emptyList()

    Column(Modifier.padding(bottom = 6.dp)) {
        SwipeRow(
            key = row.key,
            left = leftActions,
            right = rightActions,
            onTap = { onTap(row) },
            onLongPress = { onLongPress(row) },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(C.card)
                    .border(1.dp, C.line, RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(50.dp).clip(CircleShape).border(2.dp, C.brandSoft, CircleShape).padding(3.dp)) {
                    Avatar(row.contact, size = 44)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (row.direction == "missed") C.red else C.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        MdiIcon(meta.first, 14, meta.second)
                        // v1.6.15: "(3)" badge for back-to-back missed-call runs
                        if (row.count > 1) {
                            Text(
                                "(${row.count})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = C.red,
                            )
                        }
                        Text(
                            ft(row.at) + if (row.duration > 0) " · ${durationStr(row.duration)}" else "",
                            fontSize = 13.sp,
                            color = C.sub,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (balance != 0.0) {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (balance > 0) Color(0x2116a085) else Color(0x1dd64545))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            MdiIcon(if (balance > 0) "arrow-bottom-left" else "arrow-top-right", 11, if (balance > 0) C.green else C.red)
                            Money(value = balance, colorize = true, signed = true, size = 12, compact = true)
                            Text(
                                "· ${if (balance > 0) t("cd.theyOweYou") else t("cd.youOweThem")}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (balance > 0) C.green else C.red,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else if (hasRealNumber) {
                        Text(prettyPhone(row.phone, countryCode), fontSize = 13.sp, color = C.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(relTime(row.at), fontSize = 13.sp, color = C.faint)
                    if (hasRealNumber) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(C.cardAlt)
                                .combinedClickable(
                                    onClick = { onCall(callNumber) },
                                    onLongClick = {
                                        // v1.6.26 — hold the call button: pick
                                        // WHICH number to call when the contact
                                        // has more than one.
                                        if (distinctNumbers.size > 1) {
                                            vibrate(longArrayOf(20))
                                            onPickNumber(row)
                                        } else onCall(callNumber)
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) { MdiIcon("phone-outline", 19, C.brand) }
                    }
                }
            }
        }
    }
}

/* v1.6.28 — one tile of the long-press sheet's NUMBERS STRIP:
   [label pill ………… ★]  over  [number ⧉ ✆]. Tap = call, long-press =
   copy; star / copy / call behave exactly like the choose-a-number
   picker rows (same icons, same favourite set/unset, same copy toast). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NumberTile(
    phone: DevicePhone,
    isFav: Boolean,
    countryCode: String,
    onCall: () -> Unit,
    onCopy: () -> Unit,
    onToggleFav: () -> Unit,
) {
    val C = C()
    Column(
        Modifier
            .width(196.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(C.cardAlt)
            .border(1.dp, C.line, RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onCall, onLongClick = onCopy)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(phone.label, "brand")
            Spacer(Modifier.weight(1f))
            IconBtn(
                if (isFav) "star" else "star-outline",
                onToggleFav,
                size = 26,
                color = if (isFav) Color(0xFFb97d10) else C.faint,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                prettyPhone(phone.number, countryCode),
                fontSize = F.small.sp,
                color = C.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconBtn("content-copy", onCopy, size = 26)
            IconBtn("phone-outline", onCall, size = 28, color = C.brand)
        }
    }
}

/* per-row action sheet — full Contacts parity (long-press or unknown tap) */
@Composable
fun CallRowSheet(row: CallRow, onClose: () -> Unit, onDelete: (() -> Unit)?) {
    val C = C()
    val countryCode = AppStore.state.settings.countryCode
    var addToExisting by remember { mutableStateOf(false) }
    var addingTo by remember { mutableStateOf<String?>(null) }
    val digits = row.phone.replace(Regex("\\D"), "")
    val hasRealNumber = digits.replaceFirst(Regex("^0+"), "").length >= 3
    // v1.6.27 — the FAVOURITE number dials first here too (was the raw
    // first stored phone).
    val callNumber = primaryPhone(row.contact) ?: row.phone
    val isFav = row.contact != null && AppStore.state.contactX.any { it.recordID == row.contact!!.recordID && it.favorite }

    val title = row.contact?.let { displayName(it) }
        ?: if (hasRealNumber) prettyPhone(row.phone, countryCode) else t("cl.unknownNumber")

    if (addToExisting) {
        com.callmony.ui.components.ContactPicker(
            title = t("cd.addToExistingContact"),
            onPick = { c ->
                addToExisting = false
                addingTo = c.recordID
                AppStore.scope.launch {
                    // v1.6.13: WRITE_CONTACTS gate (was failing silently).
                    val ok = com.callmony.lib.requestContactsWrite() &&
                        Platform.contacts.addPhoneToContact(c.recordID, "other", row.phone)
                    // v1.6.21 — binder work above ran on the Default worker;
                    // every state write + toast publishes on Main instead
                    // (single-writer snapshot rule).
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        addingTo = null
                        if (ok) {
                            AppStore.patchDeviceContact(c.recordID) { old ->
                                if (old.phones.any { it.number == row.phone }) old
                                else old.copy(phones = old.phones + com.callmony.model.DevicePhone(row.phone, "other", row.phone))
                            }
                            Toasts.toast(t("cd.numberAddedTo", mapOf("name" to displayName(c))))
                            onClose()
                        } else Toasts.toast(t("act.couldntCopy"), "error")
                    }
                }
            },
            onBack = { addToExisting = false },
        )
        return
    }

    BottomSheet(title = title, onClose = onClose) {
        Column {
            if (addingTo != null) {
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Spinner(18)
                    Text(t("cd.savingContact"), fontSize = 14.sp, color = C.sub)
                }
            }
            // v1.6.28 — THE NUMBERS STRIP (user request: "in LONG-PRESS the
            // call card it should also show the phone numbers options in one
            // row at the top that scrolls to side with icons that are in
            // other place and to copy even by long press"). Every number of
            // the contact renders as ONE horizontally-scrolling row of tiles
            // at the top of the long-press sheet — each tile carries the
            // SAME controls as the choose-a-number picker (favourite star
            // set/unset, copy icon, call icon), tapping the tile calls, and
            // LONG-PRESSING the tile copies. (Horizontal scroll inside the
            // sheet's vertical scroll is a different axis — safe.)
            row.contact?.let { contact ->
                val numbers = remember(contact, countryCode) {
                    val seen = mutableSetOf<String>()
                    contact.phones.mapNotNull { p ->
                        val key = com.callmony.lib.normalizePhone(p.number, countryCode)
                            .replace(Regex("\\D"), "")
                            .ifEmpty { p.number.replace(Regex("\\D"), "") }
                        if (key.isNotEmpty() && seen.add(key)) p else null
                    }
                }
                if (numbers.isNotEmpty()) {
                    val rid = contact.recordID
                    // live favourite — read from state.contactX so the star
                    // flips the moment it is toggled (same as the picker)
                    val favNumber = AppStore.state.contactX.find { it.recordID == rid }?.primaryPhone
                    ScrollRow(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp)) {
                        numbers.forEach { p ->
                            val isFav = favNumber != null &&
                                p.number.replace(Regex("\\D"), "") == favNumber.replace(Regex("\\D"), "")
                            Box(Modifier.padding(end = 8.dp)) {
                                NumberTile(
                                    phone = p,
                                    isFav = isFav,
                                    countryCode = countryCode,
                                    onCall = { onClose(); Calls.placeCall(p.number) },
                                    onCopy = {
                                        Platform.share.copyText(p.number)
                                        Toasts.toast(t("cd.numberCopied"))
                                    },
                                    onToggleFav = {
                                        if (isFav) {
                                            AppStore.clearPrimaryPhone(rid)
                                            Toasts.toast(t("cd.favNumberUnset"))
                                        } else {
                                            AppStore.setPrimaryPhone(rid, p.number)
                                            Toasts.toast(t("cd.favNumberSet"))
                                        }
                                    },
                                )
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.line))
                }
            }
            val actions = buildList {
                add(Triple("phone", t("act.call"), C.ink) to { if (hasRealNumber) Calls.placeCall(callNumber) })
                add(Triple("whatsapp", t("act.whatsapp"), C.ink) to { if (hasRealNumber) Messengers.waLink(callNumber) })
                add(Triple("message-text-outline", t("act.sms"), C.ink) to { if (hasRealNumber) Messengers.smsLink(callNumber) })
                add(Triple("content-copy", t("cd.copyNumber"), C.ink) to {
                    if (hasRealNumber) {
                        Platform.share.copyText(row.phone)
                        Toasts.toast(t("cd.numberCopied"))
                    }
                })
                if (row.contact != null) {
                    add(Triple(if (isFav) "star-off-outline" else "star", if (isFav) t("cd.removedFromFavorites") else t("cd.addToFavorites"), C.ink) to {
                        AppStore.toggleFavorite(row.contact!!.recordID)
                    })
                    add(Triple("account-circle-outline", t("cd.callHistory"), C.ink) to {
                        onClose()
                        UiStore.open(Overlay.Contact(row.contact!!.recordID, initialSeg = "info"))
                    })
                } else {
                    add(Triple("account-plus-outline", t("cd.createContact"), C.ink) to {
                        onClose()
                        UiStore.open(Overlay.ContactNew(phone = row.phone))
                    })
                    add(Triple("account-multiple-plus-outline", t("cd.addToExistingContact"), C.ink) to {
                        addToExisting = true
                    })
                }
                if (onDelete != null) {
                    add(Triple("trash-can-outline", t("cd.deleteLog"), C.red) to {
                        onDelete.invoke()
                        onClose()
                    })
                }
            }
            actions.forEach { (meta, run) ->
                val (icon, label, color) = meta
                PressableRow(onClick = {
                    if (!(icon == "phone" || icon == "whatsapp" || icon == "message-text-outline") || hasRealNumber) run()
                }) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MdiIcon(icon, 20, color)
                        Text(label, fontSize = 16.sp, color = color)
                    }
                }
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.line))
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
