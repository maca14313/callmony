package com.callmony.ui.screens

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
import com.callmony.lib.relTime
import com.callmony.model.DeviceContact
import com.callmony.platform.PermState
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

    /* unified rows from the system call log */
    val rows: List<CallRow> = remember(systemCalls) {
        systemCalls.map { c ->
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
            if (callLogPerm == PermState.UNKNOWN && systemCalls.isEmpty()) {
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
    onRefresh: () -> Unit,
    refreshing: Boolean,
) {
    val C = C()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 28.dp),
    ) {
        items(pagedItems, key = {
            when (it) {
                is String -> "D-$it"
                is CallRow -> it.key
                else -> "x"
            }
        }) { item ->
            when (item) {
                is String -> DayHeader(item, filter, onFilter, missedCount)
                is CallRow -> CallCard(
                    row = item,
                    balance = item.contact?.let { balances[it.recordID] } ?: 0.0,
                    countryCode = countryCode,
                    onTap = onTap,
                    onLongPress = onLongPress,
                    onCall = onCall,
                    onWhatsApp = onWhatsApp,
                    onSMS = onSMS,
                    onToggleFav = onToggleFav,
                )
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
) {
    val C = C()
    val digits = row.phone.replace(Regex("\\D"), "")
    val hasRealNumber = digits.replaceFirst(Regex("^0+"), "").length >= 3
    val title = row.contact?.let { displayName(it) }
        ?: row.name
        ?: (if (hasRealNumber) prettyPhone(row.phone, countryCode) else "")
        .ifEmpty { t("cl.unknownNumber") }
    val meta = when (row.direction) {
        "missed" -> Triple("phone-missed", C.red, t("cd.missed"))
        "incoming" -> Triple("phone-incoming", C.green, t("cd.incoming"))
        else -> Triple("phone-outgoing", C.brand, t("cd.outgoing"))
    }
    val callNumber = row.contact?.phones?.firstOrNull()?.number ?: row.phone

    val leftActions = if (hasRealNumber) buildList {
        add(SwipeAction(t("act.call"), "phone", Color(0xFF16a085)) { onCall(callNumber) })
        if (row.contact != null) add(SwipeAction(t("cd.favorites"), "star", Color(0xFFb97d10)) { onToggleFav(row.contact!!.recordID) })
    } else emptyList()
    val rightActions = if (hasRealNumber) listOf(
        SwipeAction(t("act.whatsapp"), "whatsapp", Color(0xFF1faa53)) { onWhatsApp(callNumber) },
        SwipeAction(t("act.sms"), "message-text-outline", Color(0xFF4a7d9b)) { onSMS(callNumber) },
    ) else emptyList()

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
                                .clickable { onCall(callNumber) },
                            contentAlignment = Alignment.Center,
                        ) { MdiIcon("phone-outline", 19, C.brand) }
                    }
                }
            }
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
    val callNumber = row.contact?.phones?.firstOrNull()?.number ?: row.phone
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
