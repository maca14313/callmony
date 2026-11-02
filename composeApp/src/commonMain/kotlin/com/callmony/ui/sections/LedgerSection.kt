package com.callmony.ui.sections

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.model.GENERAL_OWNER
import com.callmony.lib.Messengers
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.lib.compactMoney
import com.callmony.lib.entriesToCsv
import com.callmony.lib.entryRemaining
import com.callmony.lib.entrySignedRemaining
import com.callmony.lib.fd
import com.callmony.lib.groupTotals
import com.callmony.lib.itemsCount
import com.callmony.lib.itemsTotal
import com.callmony.lib.now
import com.callmony.lib.openListsPending
import com.callmony.lib.ownerNameOf
import com.callmony.lib.r2
import com.callmony.lib.dateOf
import com.callmony.platform.NativeDateTime
import com.callmony.lib.shareStatement
import com.callmony.lib.statementText
import com.callmony.lib.todayStr
import com.callmony.lib.uid
import com.callmony.model.LedgerEntry
import com.callmony.model.LedgerGroup
import com.callmony.model.EntryItem
import com.callmony.model.Payment
import com.callmony.platform.Platform
import com.callmony.theme.F
import com.callmony.theme.ThemeColors
import com.callmony.ui.components.BottomSheet
import com.callmony.ui.components.PressableRow
import com.callmony.ui.kit.Btn
import com.callmony.ui.kit.C
import com.callmony.ui.kit.Card
import com.callmony.ui.kit.Chip
import com.callmony.ui.kit.Confirm
import com.callmony.ui.kit.Divider
import com.callmony.ui.kit.EmptyState
import com.callmony.ui.kit.Field
import com.callmony.ui.kit.IconBtn
import com.callmony.ui.kit.MdiIcon
import com.callmony.ui.kit.Money
import com.callmony.ui.kit.focusRing
import com.callmony.ui.kit.pressVeil
import com.callmony.ui.kit.Pill
import com.callmony.ui.kit.RootFab
import com.callmony.ui.kit.SectionHeader
import com.callmony.ui.kit.Toasts
import com.callmony.ui.components.ScrollRow

/* LedgerSection — entries, groups, line items, payments, settle-up.
   Used by the General tab AND per-contact (ContactDetail).
   Port of src/sections/LedgerSection.tsx. */

private val KIND_ICON = mapOf(
    "income" to "trending-up",
    "expense" to "trending-down",
    "credit" to "hand-coin-outline",
    "debit" to "credit-card-outline",
)

private fun sortEntries(a: LedgerEntry, b: LedgerEntry): Int =
    if (a.date < b.date) 1 else if (a.date > b.date) -1 else (b.createdAt - a.createdAt).toInt()

@Composable
fun LedgerSection(ownerId: String) {
    val C = C()
    val isGeneral = ownerId == GENERAL_OWNER
    val entries = AppStore.state.entries
    val groups = AppStore.state.groups
    val lists = AppStore.state.lists
    val contacts = AppStore.state.deviceContacts
    val ownerName = ownerNameOf(ownerId)

    // v1.6.47 — THE CIRCULAR ＋ (report: "when the ledger tab is clicked
    // or the ledger is open, it should show cercular + btn on bottom right
    // side that opens new canvas"): published into the shell's ROOT FAB
    // slot while THIS ledger is showing — the shell pins it bottom-end of
    // the content area and hides it whenever an overlay sits above the
    // ledger surface. Tapping it opens the entry editor, which opens with
    // the full-screen calculation canvas already up — a NEW CANVAS, ready
    // to type on. Leaving the ledger segment (or the whole screen)
    // disposes this effect and clears the slot.
    // v1.6.49 — report #8 ("in the contact detail page make the tabs +
    // credit, + debit, to show up when the pluss icon for the ledger is
    // clicked"): on a CONTACT's ledger the FAB asks the KIND first — a
    // compact chooser with ＋ Credit / ＋ Debit — instead of opening the
    // canvas directly (the General ledger keeps the direct new-canvas
    // behavior, unchanged).
    DisposableEffect(ownerId) {
        RootFab.content.value = { LedgerFab(ownerId = ownerId, askKind = !isGeneral) }
        onDispose { RootFab.content.value = null }
    }

    val mine = remember(entries, ownerId) { entries.filter { it.ownerId == ownerId && it.deletedAt == null } }
    val myGroups = remember(groups, ownerId) { groups.filter { it.ownerId == ownerId && it.deletedAt == null } }

    val credit = mine.filter { it.type == "credit" }.sumOf { entrySignedRemaining(it) }
    val debit = mine.filter { it.type == "debit" }.sumOf { -entrySignedRemaining(it) }
    val pendingLists = if (!isGeneral) openListsPending(lists, ownerId) else 0.0

    var groupFilter by remember { mutableStateOf("all") }
    var collapsedGroups by remember { mutableStateOf(setOf<String>()) }
    var ungroupedCollapsed by remember { mutableStateOf(false) }
    var groupsCollapsed by remember { mutableStateOf(false) }
    var renameGroup by remember { mutableStateOf<String?>(null) }
    var renameTitle by remember { mutableStateOf("") }
    var shareSheet by remember { mutableStateOf(false) }
    var groupMenuFor by remember { mutableStateOf<LedgerGroup?>(null) }
    // v1.6.34 — the QUICK ITEM moved into the ADD-ITEM flow (user report:
    // "the quick item — label + price — should be inside ADD ITEM as
    // horizontally scrolling tabs: Items (qty × price) / Quick item / Just
    // amount"). The ledger view keeps NO quick row of its own anymore —
    // the entry editor's tab strip carries it (plus a Recent-items reuse
    // tab). The kind follows the editor's own kind chips.

    val ownerPhone = if (ownerId.startsWith("c:"))
        contacts.find { "c:${it.recordID}" == ownerId }?.phones?.firstOrNull()?.number
    else null

    val ungrouped = remember(mine, groupFilter) {
        var list = mine.filter { it.groupId == null }
        if (groupFilter == "credit") list = list.filter { it.type == "credit" }
        if (groupFilter == "debit") list = list.filter { it.type == "debit" }
        if (groupFilter == "unpaid") list = list.filter { entryRemaining(it) > 0.005 }
        if (groupFilter == "overdue") list = list.filter { it.dueDate != null && it.dueDate!! < todayStr() && entryRemaining(it) > 0.005 }
        list.sortedWith(::sortEntries)
    }

    /* running balance (oldest → newest) */
    val running = remember(mine) {
        val map = mutableMapOf<String, Double>()
        var acc = 0.0
        mine.sortedWith(::sortEntries).forEach { e ->
            acc = r2(acc + entrySignedRemaining(e))
            map[e.id] = acc
        }
        map
    }

    fun doSettle() {
        val msg = if (isGeneral) t("lg.settleConfirmGeneral") else t("lg.settleConfirmPerson", mapOf("name" to ownerName))
        Confirm.ask(t("lg.settleUp"), msg, t("lg.settleUp")) { ok ->
            if (ok) {
                AppStore.settleUp(ownerId)
                Toasts.toast(t("lg.settledDone"))
            }
        }
    }

    Column {
        // summary strip — v1.6.25 compact: three cards in ONE row, 12dp
        // corners, 8/7dp inset (4dp spacing system), Net card keeps its
        // subtle selected-state styling (brandSoft + brand hairline).
        Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatCard(if (isGeneral) t("lg.income") else t("cd.theyOweYou"), credit, false, Modifier.weight(1f))
            StatCard(if (isGeneral) t("lg.expense") else t("cd.youOweThem"), debit, false, Modifier.weight(1f))
            StatCard(t("lg.net"), r2(credit - debit), true, Modifier.weight(1f))
        }
        if (!isGeneral && pendingLists > 0.005) {
            Row(
                Modifier
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(C.card)
                    .border(1.dp, C.line, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MdiIcon("information-outline", 16, C.amber)
                Text(
                    "${t("lg.openLists")}: ${compactMoney(pendingLists)} · ${t("lg.countedInBalance")}",
                    fontSize = F.tiny.sp,
                    color = C.amber,
                )
            }
        }
        // primary action row — v1.6.25 (redesign spec: "Place Credit, Debit,
        // and Income actions in one row"): the three PRIMARY kinds are three
        // equal-width compact buttons in a FIXED row (no scrolling to reach
        // them); the secondary actions scroll beneath them in the SAME pill
        // style (v1.6.26 consistency fix).
        // v1.6.49 — report #8: THE CONTACT DETAIL PAGE LOST ITS ADD PILLS —
        // "dont want thos + credit, + debit up there… dont need the other
        // tabs bised them in the contact detail page": the +Credit/+Debit
        // choices moved into the ledger FAB's tap (openAddKindChooser), so this
        // whole action stack now renders on the GENERAL ledger only.
        if (isGeneral) {
        Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val primary = if (isGeneral) {
                Triple("income" to "credit", "expense" to "debit", "credit" to "credit")
            } else {
                Triple("credit" to "credit", "debit" to "debit", "income" to "credit")
            }
            val labels = if (isGeneral) {
                Triple(t("lg.kindIncome"), t("lg.kindExpense"), t("lg.kindCredit"))
            } else {
                Triple(t("lg.kindCredit"), t("lg.kindDebit"), t("lg.kindIncome"))
            }
            listOf(
                Triple("plus", labels.first) { UiStore.open(Overlay.EntryEdit(null, ownerId, primary.first.first, primary.first.second)) },
                Triple("plus", labels.second) { UiStore.open(Overlay.EntryEdit(null, ownerId, primary.second.first, primary.second.second)) },
                Triple("plus", labels.third) { UiStore.open(Overlay.EntryEdit(null, ownerId, primary.third.first, primary.third.second)) },
            ).forEach { (icon, label, run) ->
                ActionPill(icon, label, Modifier.weight(1f)) { run() }
            }
        }
        // secondary action chips — v1.6.26: THE “+ Expense LOOKS DIFFERENT
        // FROM ITS FRIENDS” FIX (user report). The secondary row used the
        // generic ghost Chip style while the primary row above had its own
        // pill design — now every action button on this page shares ONE
        // visual language: the same brand-soft pill, brand hairline, bold
        // tiny label and 12dp radius as the primary row (flat, side-scroll
        // when narrow).
        ScrollRow(Modifier.padding(bottom = 2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // v1.6.18 — Settle-up REMOVED at the user's request ("we don't
                // need the settle up for now"); AppStore.settleUp stays for API
                // compat, just not surfaced anywhere.
                if (isGeneral) {
                    ActionPill("plus", t("lg.kindDebit")) { UiStore.open(Overlay.EntryEdit(null, ownerId, "debit", "debit")) }
                } else {
                    ActionPill("plus", t("lg.kindExpense")) { UiStore.open(Overlay.EntryEdit(null, ownerId, "debit", "expense")) }
                }
                ActionPill("folder-plus-outline", t("lg.newGroup")) { renameGroup = "new"; renameTitle = "" }
                ActionPill("share-variant-outline", t("lg.shareStatement")) { shareSheet = true }
            }
        }
        } // end of the general-only action stack
        // groups
        if (myGroups.isNotEmpty()) {
            Column(Modifier.padding(bottom = 10.dp)) {
                SectionHeader(t("lg.groups"), right = {
                    IconBtn(if (groupsCollapsed) "chevron-down" else "chevron-up", { groupsCollapsed = !groupsCollapsed }, size = 34)
                })
                if (!groupsCollapsed) {
                    myGroups.forEach { g ->
                        val totals = groupTotals(mine, g.id)
                        val collapsed = collapsedGroups.contains(g.id)
                        val members = mine.filter { it.groupId == g.id }.sortedWith(::sortEntries)
                        Card(Modifier.padding(bottom = 8.dp)) {
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    collapsedGroups = if (collapsed) collapsedGroups - g.id else collapsedGroups + g.id
                                },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                MdiIcon(if (collapsed) "folder-outline" else "folder-open-outline", 20, C.brand)
                                Column(Modifier.weight(1f)) {
                                    Text(g.title, fontSize = F.body.sp, fontWeight = FontWeight.Bold, color = C.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${t("lg.entriesCount", mapOf("n" to totals.count.toString()))} · ${t("lg.paid")} ${compactMoney(totals.paid)}",
                                        fontSize = F.tiny.sp,
                                        color = C.sub,
                                    )
                                }
                                Money(totals.remaining, signed = true, colorize = true, size = 15, compact = true)
                                IconBtn("dots-vertical", { groupMenuFor = g }, size = 32)
                            }
                            if (!collapsed) {
                                Column(Modifier.padding(top = 6.dp)) {
                                    members.forEach { e -> EntryCard(e, running[e.id] ?: 0.0) }
                                    Row(Modifier.padding(top = 8.dp)) {
                                        Btn(small = true, label = t("lg.addEntry"), icon = "plus", variant = "soft", onPress = { UiStore.open(Overlay.EntryEdit(null, ownerId, groupId = g.id)) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // ungrouped ("Single entries") — v1.6.25 compact heading + controls
        SectionHeader(t("lg.ungrouped"), right = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                // v1.6.18 — the filter tabs under "Single entries" carry a
                // ＋ of their own (creates the KIND the tab is showing);
                // v1.6.25 makes it a LABELED compact chip instead of the
                // ghost icon the user could never find, and the kind now
                // follows the active tab exactly (credit tab → + Credit…).
                // v1.6.49 — report #8: on a CONTACT the labeled kind chip is
                // GONE ("dont want thos + credit, + debit up there") — every
                // ＋ on a contact's ledger opens the same +Credit/+Debit
                // chooser as the FAB; the General ledger keeps both.
                val addLabel = when (groupFilter) {
                    "credit" -> if (isGeneral) t("lg.kindIncome") else null
                    "debit" -> if (isGeneral) t("lg.kindExpense") else null
                    else -> null
                }
                if (addLabel != null) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(C.brandSoft)
                            .border(1.dp, C.brand, RoundedCornerShape(999.dp))
                            .clickable {
                                val (ty, kd) = when (groupFilter) {
                                    "credit" -> if (isGeneral) "credit" to "income" else "credit" to "credit"
                                    "debit" -> if (isGeneral) "debit" to "expense" else "debit" to "debit"
                                    else -> null to null
                                }
                                UiStore.open(if (ty == null) Overlay.EntryEdit(null, ownerId) else Overlay.EntryEdit(null, ownerId, ty, kd))
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        MdiIcon("plus", 13, C.brand)
                        Text(addLabel, fontSize = F.tiny.sp, color = C.brand, fontWeight = FontWeight.Bold)
                    }
                }
                IconBtn("plus", {
                    // v1.6.49 — a contact's ＋ asks the kind (report #8);
                    // the General ledger opens the canvas directly as before
                    if (isGeneral) UiStore.open(Overlay.EntryEdit(null, ownerId)) else openAddKindChooser(ownerId)
                }, size = 30)
                IconBtn(if (ungroupedCollapsed) "chevron-down" else "chevron-up", { ungroupedCollapsed = !ungroupedCollapsed }, size = 30)
            }
        })
        if (!ungroupedCollapsed) {
            // v1.6.25 — THE TABS/CARDS OVERLAP FIX: the filter strip gets its
            // own vertical padding (2dp top / 6dp bottom) so the chips can
            // never sit flush against the entry cards under them.
            ScrollRow(Modifier.padding(top = 2.dp, bottom = 6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(label = t("lg.filterAll"), active = groupFilter == "all", onPress = { groupFilter = "all" }, compact = true)
                    Chip(label = if (isGeneral) t("lg.income") else t("lg.credit"), active = groupFilter == "credit", onPress = { groupFilter = "credit" }, compact = true)
                    Chip(label = if (isGeneral) t("lg.expense") else t("lg.debit"), active = groupFilter == "debit", onPress = { groupFilter = "debit" }, compact = true)
                    Chip(label = t("lg.filterUnpaid"), active = groupFilter == "unpaid", onPress = { groupFilter = "unpaid" }, compact = true)
                    Chip(label = t("lg.filterOverdue"), active = groupFilter == "overdue", onPress = { groupFilter = "overdue" }, compact = true)
                }
            }
            // v1.6.34 — the quick-item row is GONE from the ledger view (it
            // lives in the add-item tab strip now — see EntryEdit).
            if (ungrouped.isEmpty()) {
                EmptyState(
                    icon = "book-outline",
                    title = if (isGeneral) t("lg.noEntriesGeneral") else t("lg.noEntries"),
                    hint = if (isGeneral) t("lg.noEntriesGeneralHint") else t("lg.noEntriesHint"),
                    actionLabel = t("lg.addEntry"),
                    onAction = { UiStore.open(Overlay.EntryEdit(null, ownerId)) },
                )
            } else {
                ungrouped.forEach { e -> EntryCard(e, running[e.id] ?: 0.0) }
            }
        }
    }

    // v1.6.49 — the contact ledger's kind chooser is a top-level helper
    // now (openAddKindChooser below): the chooser opens as a ROOT-LEVEL
    // host sheet carrying its ownerId EXPLICITLY. The first cut bridged a
    // remember-state through the shell's shared RootFab slot — two
    // LedgerSections publish into one content slot (the General tab's
    // ledger stays composed behind the contact detail overlay), and the
    // fab's captured state write landed on the WRONG section's sheet; and
    // an in-place BottomSheet sat below the visible viewport (the contact
    // detail's body is one tall verticalScroll — the v1.6.18 lesson).

    // group action menu
    groupMenuFor?.let { g ->
        BottomSheet(title = g.title, onClose = { groupMenuFor = null }) {
            Column {
                listOf(
                    Triple("plus", t("lg.addEntry"), "add"),
                    Triple("pencil-outline", t("act.rename"), "rename"),
                    Triple("trash-can-outline", t("act.delete"), "delete"),
                ).forEach { (icon, label, key) ->
                    PressableRow(onClick = {
                        groupMenuFor = null
                        when (key) {
                            "add" -> UiStore.open(Overlay.EntryEdit(null, ownerId, groupId = g.id))
                            "rename" -> { renameGroup = g.id; renameTitle = g.title }
                            "delete" -> Confirm.ask(t("lg.groupActions"), t("lg.noGroupEntries"), t("act.delete"), danger = true) { ok ->
                                if (ok) { AppStore.deleteGroup(g.id); Toasts.toastUndo() }
                            }
                        }
                    }) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MdiIcon(icon, 20, if (key == "delete") C.red else C.ink)
                            Text(label, fontSize = 16.sp, color = if (key == "delete") C.red else C.ink)
                        }
                    }
                }
            }
        }
    }

    // new/rename group sheet
    renameGroup?.let { rg ->
        BottomSheet(title = if (rg == "new") t("lg.newGroup") else t("act.rename"), onClose = { renameGroup = null; renameTitle = "" }) {
            Field(
                label = t("lg.groupName"),
                value = renameTitle,
                onChangeText = { renameTitle = it },
                placeholder = t("lg.groupPlaceholder"),
            )
            Btn(label = t("act.save"), onPress = {
                val title = renameTitle.trim()
                if (title.isEmpty()) return@Btn
                AppStore.saveGroup(
                    LedgerGroup(
                        id = if (rg == "new") uid() else rg,
                        ownerId = ownerId,
                        title = title,
                        createdAt = now(),
                        updatedAt = now(),
                    )
                )
                val wasNew = rg == "new"
                renameGroup = null
                renameTitle = ""
                Toasts.toast(if (wasNew) t("lg.groupCreated") else t("lg.groupRenamed"))
            })
        }
    }

    // share statement sheet
    if (shareSheet) {
        BottomSheet(title = t("lg.shareStatement"), onClose = { shareSheet = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ownerPhone != null) {
                    Btn(label = t("act.whatsapp"), icon = "whatsapp", onPress = {
                        shareSheet = false
                        Messengers.waLink(ownerPhone, statementText(ownerName, mine))
                    })
                }
                Btn(label = t("act.share"), icon = "share-variant-outline", variant = "soft", onPress = {
                    shareSheet = false
                    if (shareStatement(ownerName, ownerId)) Toasts.toast(t("bk.shared"))
                })
                Btn(label = t("act.copy"), icon = "content-copy", variant = "secondary", onPress = {
                    shareSheet = false
                    Platform.share.copyText(statementText(ownerName, mine))
                    Toasts.toast(t("act.copied"))
                })
                Btn(label = t("lg.exportCsv"), icon = "file-delimited-outline", variant = "secondary", onPress = {
                    shareSheet = false
                    Platform.share.shareText(entriesToCsv(mine), "$ownerName-ledger.csv")
                    Toasts.toast(t("lg.csvExported"))
                })
            }
        }
    }
}

/* ---------------- entry card ---------------- */

/* v1.6.47 — THE LEDGER'S CIRCULAR ＋ FAB (report: "when the ledger tab is
 *  clicked or the ledger is open, it should show cercular + btn on bottom
 *  right side that opens new canvas"): a 54dp brand circle pinned to the
 *  content area's bottom-end by the shell's RootFab slot. The full-size
 *  transparent wrapper never consumes input — only the circle itself is
 *  clickable. The tap opens the entry editor, which starts with the
 *  full-screen calculation canvas already up (calcExpanded defaults
 *  true) — one tap, one NEW CANVAS.
 *  v1.6.49 — report #8: on a CONTACT's ledger the tap first asks the KIND
 *  (＋ Credit / ＋ Debit) through the chooser sheet; the General ledger
 *  keeps the direct new-canvas behavior. */
@Composable
private fun LedgerFab(ownerId: String, askKind: Boolean = false) {
    val C = C()
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 18.dp)
                .size(54.dp)
                .shadow(6.dp, RoundedCornerShape(27.dp))
                .clip(RoundedCornerShape(27.dp))
                .background(C.brand)
                .clickable {
                    com.callmony.ui.kit.vibrate(longArrayOf(10))
                    if (askKind) openAddKindChooser(ownerId)
                    else UiStore.open(Overlay.EntryEdit(null, ownerId))
                }
                .testTag("ledgerFabNewCanvas"),
            contentAlignment = Alignment.Center,
        ) { MdiIcon("plus", 26, C.onBrand) }
    }
}

/* v1.6.49 — THE CONTACT LEDGER'S KIND CHOOSER (report #8: "in the contact
 *  detail page make the tabs + credit, + debit, to show up when the pluss
 *  icon for the ledger is clicked"): the FAB's — and the header row's — ＋
 *  opens these two choices instead of the canvas directly; each opens the
 *  entry editor with its kind preset, canvas up as always. A ROOT-LEVEL
 *  HOST SHEET carrying its ownerId explicitly: no captured remember state
 *  (the shared RootFab slot cross-wired two sections' bridges), no in-
 *  place BottomSheet (the contact detail's tall verticalScroll would push
 *  it below the viewport — the v1.6.18 record-payment lesson). */
fun openAddKindChooser(ownerId: String) {
    com.callmony.ui.kit.HostSheets.push { close ->
        val C = C()
        BottomSheet(title = t("lg.addEntry"), onClose = close) {
            Column {
                listOf(
                    Triple("hand-coin-outline", t("lg.kindCredit"), "credit" to "credit"),
                    Triple("credit-card-outline", t("lg.kindDebit"), "debit" to "debit"),
                ).forEach { (icon, label, kind) ->
                    PressableRow(onClick = {
                        close()
                        UiStore.open(Overlay.EntryEdit(null, ownerId, kind.first, kind.second))
                    }) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MdiIcon(icon, 20, C.ink)
                            Text("＋ $label", fontSize = 16.sp, color = C.ink)
                        }
                    }
                }
            }
        }
    }
}

/* v1.6.47 — THE ENTRY CARD'S PAY-PERCENTAGE RING (report: "it should list
 *  the existing canvas rectangular cards and it should show the status,
 *  persontage of tge payment and other information on it"): the canvas
 *  card's own mini of the sum-total ring — the sweep fills with the PAID
 *  fraction (green once fully paid) and the centre carries the PERCENTAGE
 *  PAID, so every card answers "how much of this one is settled" at a
 *  glance. Entries with nothing payable keep the old kind icon instead. */
@Composable
private fun PayPctRing(entry: LedgerEntry) {
    val C = C()
    val total = entry.amount
    val paid = entry.payments.sumOf { it.amount }
    val fraction = if (total > 0.005) (paid / total).toFloat().coerceIn(0f, 1f) else 0f
    val pctPaid = if (total > 0.005) {
        kotlin.math.round((paid / total) * 100.0).toInt().coerceIn(0, 100)
    } else 0
    val done = total > 0.005 && paid >= total - 0.005
    val kind = entry.kind ?: (if (entry.type == "credit") "credit" else "debit")
    if (total <= 0.005) {
        // nothing payable — the kind badge the card always carried
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (entry.type == "credit") C.brandSoft else C.red.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) { MdiIcon(KIND_ICON[kind] ?: "swap-vertical", 17, if (entry.type == "credit") C.brand else C.red) }
        return
    }
    Box(
        Modifier
            .size(34.dp)
            .testTag("entryCardPct:${entry.id}"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2 + 0.5f
            val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = C.line,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (fraction > 0f) {
                drawArc(
                    color = if (done) C.green else C.brand,
                    startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            "$pctPaid%",
            fontSize = F.tiny.sp,
            fontWeight = FontWeight.Bold,
            color = if (done) C.green else C.brand,
        )
    }
}

@Composable
fun EntryCard(entry: LedgerEntry, running: Double) {
    val C = C()
    var expanded by remember(entry.id) { mutableStateOf(false) }

    val remaining = entryRemaining(entry)
    val paid = entry.payments.sumOf { it.amount }
    val signed = entrySignedRemaining(entry)
    val overdue = entry.dueDate != null && entry.dueDate!! < todayStr() && remaining > 0.005
    val dueToday = entry.dueDate == todayStr() && remaining > 0.005
    val kind = entry.kind ?: (if (entry.type == "credit") "credit" else "debit")
    // v1.6.47 — THE CANVAS CARD (report: "it should list the existing
    // canvas rectangular cards"): the title prefers the entry's own CANVAS
    // TITLE (calcTitle), and the subtitle carries the calculation's first
    // line (the @date marker stripped) so the card reads like the canvas
    // it opens into.
    val cardTitle = entry.calcTitle?.takeIf { it.isNotBlank() }
        ?: entry.items.singleOrNull()?.name?.takeIf { it.isNotBlank() }
        ?: t("lg.kind${kind.replaceFirstChar { it.uppercase() }}")
    // v1.6.49 — report #9 ("make the entires list cards longer verticaly so
    // it's shows most of it's calculation"): the preview is now the
    // WHOLE calculation — every ledger line of its own (up to four visual
    // rows), the ⏎ intra-line breaks folded to spaces — instead of the
    // single ellipsized first line, so the card grows to show most of the
    // math it opens into.
    val calcPreviewLines = entry.exprNote
        ?.split('\n')
        ?.map {
            it.replace(Regex("\\s*@\\d{4}-\\d{2}-\\d{2}"), "")
                .replace(com.callmony.lib.CALC_BREAK, ' ')
                .trim()
        }
        ?.filter { it.isNotEmpty() }
        ?.take(4)
        ?: emptyList()

    Card(Modifier.padding(bottom = 6.dp)) {
        Column {
            Row(
                // v1.6.18 — ONE-TAP EDIT: tapping the row opens the editor
                // directly; the detail (payments history) opens from the
                // chevron on the right.
                Modifier.fillMaxWidth().clickable { UiStore.open(Overlay.EntryEdit(entry.id, entry.ownerId)) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // v1.6.47 — the PAY-PERCENTAGE RING leads the card (the
                // "persontage of tge payment" the report asks for)
                PayPctRing(entry)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            // v1.6.47 — the CANVAS TITLE heads the card
                            // ("other information on it"); the quick-item
                            // label and the kind label follow it.
                            cardTitle,
                            fontSize = F.body.sp,
                            fontWeight = FontWeight.Bold,
                            color = C.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        entry.category?.let { Pill(it, "brand") }
                    }
                    // v1.6.49 — THE CALCULATION ITSELF, up to four of its
                    // lines (report #9) — the card shows most of the math
                    if (calcPreviewLines.isNotEmpty()) {
                        Text(
                            calcPreviewLines.joinToString("\n"),
                            fontSize = F.tiny.sp,
                            color = C.sub,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("entryCardCalc:${entry.id}"),
                        )
                    }
                    Text(
                        listOfNotNull(
                            fd(entry.date),
                            "${t("lg.balance")} ${compactMoney(running)}",
                            if (entry.payments.isNotEmpty()) "${t("lg.payments")} ${entry.payments.size}" else null,
                        ).joinToString(" · "),
                        fontSize = F.tiny.sp,
                        color = C.sub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // v1.6.47 — THE STATUS, ON the card (report: "it should
                    // show the status"): fully paid ✓, part-paid shows how
                    // much of the total, unpaid shows what is pending.
                    when {
                        remaining > 0.005 && paid > 0.005 -> Pill("${t("lg.paid")} ${compactMoney(paid)} / ${compactMoney(entry.amount)}", "amber")
                        remaining > 0.005 -> Pill("${t("lg.pending")} ${compactMoney(remaining)}", "red")
                        remaining < -0.005 -> Pill("${t("lg.overpaidBy")} ${compactMoney(-remaining)}", "green")
                        else -> Pill("✓ ${t("lg.paid")}", "green")
                    }
                }
                Money(signed, signed = true, colorize = true, size = 15, weight = FontWeight.ExtraBold, compact = true)
                // v1.6.25 — VISIBLE EDIT ICON (user report: "on the credit or
                // others where is the icon to update the existing one"): a
                // pencil button on every entry card opens the editor — the
                // row tap still edits too, but now there is a discoverable,
                // always-visible affordance for it.
                IconBtn("pencil-outline", { UiStore.open(Overlay.EntryEdit(entry.id, entry.ownerId)) }, size = 30, color = C.brand)
                // v1.6.18 — the chevron is now the ONLY thing that expands
                // the detail (payments history etc.); the row body edits.
                IconBtn(if (expanded) "chevron-up" else "chevron-down", { expanded = !expanded }, size = 30)
            }

            // v1.6.25 — ATTACHED LISTS/NOTES/REMINDERS ride DIRECTLY on the
            // collapsed card (they used to hide inside the expanded detail —
            // the user never saw them). Each chip OPENS its entity exactly
            // like it does from its own section: a list opens the list
            // detail, a note opens the note editor, a reminder opens the
            // reminder editor.
            if (entry.refs.isNotEmpty() && !expanded) {
                ScrollRow(Modifier.padding(top = 4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        entry.refs.forEach { ref ->
                            val icon = when (ref.type) { "list" -> "clipboard-list-outline"; "note" -> "notebook-outline"; "reminder" -> "bell-outline"; else -> "link-variant" }
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(C.brand.copy(alpha = 0.08f))
                                    .clickable { openRef(ref, entry) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                MdiIcon(icon, 13, C.brand)
                                Text(ref.label ?: ref.id, fontSize = F.tiny.sp, color = C.ink, maxLines = 1)
                            }
                        }
                    }
                }
            }

            if (expanded) {
                Column(Modifier.padding(top = 8.dp)) {
                    Divider()
                    if (overdue) Pill(t("lg.paymentDue"), "red")
                    if (dueToday && !overdue) Pill(t("rm.today"), "amber")
                    if (entry.items.isNotEmpty()) {
                        Column(Modifier.padding(bottom = 8.dp)) {
                            Text(t("lg.itemsTitle"), fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C.sub, modifier = Modifier.padding(bottom = 4.dp))
                            entry.items.forEach { it ->
                                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${it.name} ${it.qty}${it.unit?.let { u -> " $u" } ?: ""} × ${compactMoney(it.price)}",
                                        fontSize = F.small.sp,
                                        color = C.ink,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(compactMoney(it.qty * it.price), fontSize = F.small.sp, fontWeight = FontWeight.Bold, color = C.ink)
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${t("lg.total")} · ${itemsCount(entry.items)}", fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C.sub)
                                Text(compactMoney(itemsTotal(entry.items)), fontSize = F.body.sp, fontWeight = FontWeight.ExtraBold, color = C.brand)
                            }
                        }
                    }
                    // v1.6.18 — attached lists / notes / reminders on this entry
                    if (entry.refs.isNotEmpty()) {
                        Text(t("ee.attached"), fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C.sub, modifier = Modifier.padding(bottom = 4.dp))
                        ScrollRow(Modifier.padding(bottom = 8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                entry.refs.forEach { ref ->
                                    val icon = when (ref.type) { "list" -> "clipboard-list-outline"; "note" -> "notebook-outline"; "reminder" -> "bell-outline"; else -> "link-variant" }
                                    Row(
                                        Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(C.brand.copy(alpha = 0.08f))
                                            .clickable { openRef(ref, entry) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        MdiIcon(icon, 15, C.brand)
                                        Text(ref.label ?: ref.id, fontSize = F.tiny.sp, color = C.ink, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                    entry.note?.let { Text(it, fontSize = F.small.sp, color = C.sub, modifier = Modifier.padding(bottom = 6.dp)) }
                    Text(t("lg.paymentHistory"), fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C.sub, modifier = Modifier.padding(bottom = 4.dp))
                    if (entry.payments.isEmpty()) {
                        Text(t("lg.noPaymentsYet"), fontSize = F.tiny.sp, color = C.faint, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    entry.payments.forEachIndexed { i, p ->
                        // v1.6.49 — report #10 ("why there is no a way to
                        // update payment"): tapping the row opens the
                        // payment's own EDITOR (amount / date / method /
                        // note, saved in place); the ✕ still removes it
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { openPaymentSheet(entry.id, edit = p) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("${i + 1}.", fontSize = F.tiny.sp, color = C.faint, modifier = Modifier.width(18.dp))
                            Text(
                                buildString {
                                    append("${fd(p.date)} · ${compactMoney(p.amount)}")
                                    p.auto?.let { append(" · $it") }
                                    p.method?.let { append(" · $it") }
                                    p.note?.let { append(" · $it") }
                                },
                                fontSize = F.small.sp,
                                color = C.ink,
                                modifier = Modifier.weight(1f),
                            )
                            IconBtn("pencil-outline", { openPaymentSheet(entry.id, edit = p) }, size = 28, color = C.brand)
                            IconBtn("close", {
                                Confirm.ask(t("act.remove"), t("lg.paymentRemoved"), t("act.remove"), danger = true) { ok ->
                                    if (ok) AppStore.removePayment(entry.id, p.id)
                                }
                            }, size = 28, danger = true)
                        }
                    }
                    when {
                        remaining > 0.005 -> Pill(
                            if (paid > 0) "${t("lg.paid")} ${compactMoney(paid)} / ${compactMoney(entry.amount)}" else "${t("lg.pending")} ${compactMoney(remaining)}",
                            if (paid > 0) "amber" else "red",
                        )
                        remaining < -0.005 -> Pill("${t("lg.overpaidBy")} ${compactMoney(-remaining)}", "green")
                        else -> Pill("✓", "green")
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Btn(small = true, label = t("lg.recordPayment"), icon = "plus", variant = "soft", onPress = { openPaymentSheet(entry.id) })
                        Btn(small = true, label = t("act.move"), icon = "account-arrow-right-outline", variant = "secondary", onPress = {
                            Confirm.ask(t("lg.entryMoved"), t("lg.movedToGeneral"), t("act.move")) { ok ->
                                if (ok) {
                                    AppStore.moveEntity("entry", entry.id, GENERAL_OWNER)
                                    Toasts.toast(t("lg.entryMoved"))
                                }
                            }
                        })
                        Btn(small = true, label = t("act.delete"), icon = "trash-can-outline", variant = "danger", onPress = {
                            Confirm.ask(t("lg.deleteEntryTitle"), t("lg.deleteEntryMsg"), t("act.delete"), danger = true) { ok ->
                                if (ok) { AppStore.deleteEntry(entry.id); Toasts.toastUndo() }
                            }
                        })
                    }
                }
            }
        }

        // v1.6.18 — Record payment opens as a ROOT-LEVEL host sheet (was a
        // BottomSheet composed INSIDE the scrollable card: unbounded height
        // constraints made it pop up at the top / float mid-scroll — "the add
        // payment is not working" + "some modals open at the top").
    }
}

/* v1.6.25 — one tap on an attached ref opens the entity EXACTLY like its
 * own section does (list → list detail, note → note editor, reminder →
 * reminder editor) — the "attach means add to it, and if we click them it
 * should do what it does on its own" requirement. */
private fun openRef(ref: com.callmony.model.LinkRef, entry: LedgerEntry) {
    when (ref.type) {
        "list" -> UiStore.open(Overlay.ListDetail(ref.id))
        "note" -> UiStore.open(Overlay.NoteEdit(ref.id, AppStore.state.notes.find { it.id == ref.id }?.ownerId ?: entry.ownerId))
        "reminder" -> UiStore.open(Overlay.ReminderEdit(ref.id, AppStore.state.reminders.find { it.id == ref.id }?.ownerId ?: entry.ownerId))
    }
}

/* v1.6.18 — Record-payment sheet (root-level host sheet).
 * Renamed from "Add payment" (confusing next to + Credit/+ Debit — the user
 * never knew whether it CREATES a new entry or registers money against one).
 * The date is now the NATIVE calendar picker (was a hand-typed YYYY-MM-DD
 * text field — typos produced garbage dates, another "not working" source).
 * It reads the LIVE entry from the store, so it stays correct if state
 * changes while it is open.
 * v1.6.49 — report #10 ("why there is no a way to update payment"): an
 * `edit` payment reopens the SAME compact sheet as an UPDATE form — the
 * amount / date / method / note prefilled from the recorded row, the save
 * rewriting it IN PLACE (AppStore.updatePayment keeps the id, the line
 * attribution and the auto marker); the cap accounts for the payment being
 * edited (remaining + its own amount). */
fun openPaymentSheet(
    entryId: String,
    prefill: Double? = null,
    lineIdx: Int? = null,
    maxAmount: Double? = null,
    edit: Payment? = null,
) {
    com.callmony.ui.kit.HostSheets.push { close ->
        val live = AppStore.state.entries.find { it.id == entryId } ?: run { close(); return@push }
        val C = C()
        // v1.6.38 — optional PREFILL: "make payment of it" on the
        // calculator's total opens the sheet with the amount already in
        // v1.6.45 — report #4 ("the record payment modal is to bulkt, agly,
        //   it should be very compact and easy"): the sheet lost its hint
        //   paragraph and its kind·amount headline (the title carries the
        //   amount; one tiny line carries the remaining), the FILL-remaining
        //   action moved INSIDE the amount field, Date + Note share ONE row,
        //   the five method segments became compact chips on one scroller,
        //   and Save is one small full-width button — four tight rows where
        //   there were seven stacked blocks.
        // v1.6.49 — an EDIT reopens prefilled from the recorded payment
        var amount by remember { mutableStateOf(edit?.let { trimNumCompat(it.amount) } ?: prefill?.let { trimNumCompat(it) } ?: "") }
        var payDate by remember { mutableStateOf(edit?.date?.takeIf { it.isNotEmpty() } ?: todayStr()) }
        var method by remember { mutableStateOf(edit?.method ?: "cash") }
        var payNote by remember { mutableStateOf(edit?.note ?: "") }
        val remaining = entryRemaining(live)
        // the cap: the remaining total — plus the payment being EDITED (its
        // own amount stops counting against the cap the moment it reopens)
        val cap = if (edit != null) r2(remaining + edit.amount) else maxAmount
        BottomSheet(
            title = (if (edit != null) t("lg.updatePayment") else t("lg.recordPayment")) + " · ${compactMoney(live.amount)}",
            onClose = close,
        ) {
            Column {
                if (remaining > 0.005) {
                    Text(
                        "${t("lg.remaining")}: ${compactMoney(remaining)}",
                        fontSize = F.tiny.sp, color = C.sub, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                // AMOUNT — with the fill-remaining action tucked INSIDE the
                // field (one glance, one tap, nothing stacked)
                Field(
                    label = t("lg.paymentAmount"),
                    value = amount,
                    onChangeText = { amount = it },
                    placeholder = "0",
                    keyboardType = "decimal-pad",
                    right = if (remaining > 0.005) {
                        {
                            Text(
                                "${t("lg.fillRemaining")}: ${compactMoney(remaining)}",
                                fontSize = F.tiny.sp, color = C.brand, fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { amount = trimNumCompat(remaining) }
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                            )
                        }
                    } else null,
                )
                // DATE + NOTE — one row, side by side
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        com.callmony.ui.kit.PickerField(
                            label = t("lg.paymentDate"),
                            value = com.callmony.lib.fd(payDate),
                            icon = "calendar-blank-outline",
                            onPress = {
                                val initial = try {
                                    val p = payDate.split("-")
                                    com.callmony.lib.KmpDate(
                                        p.getOrNull(0)?.toInt() ?: dateOf(now()).year,
                                        (p.getOrNull(1)?.toIntOrNull() ?: (dateOf(now()).month0 + 1)) - 1,
                                        p.getOrNull(2)?.toIntOrNull() ?: dateOf(now()).day,
                                        12, 0, 0,
                                    ).time()
                                } catch (_: Exception) { now() }
                                NativeDateTime.pickDate(
                                    epochMillis = initial,
                                    onPick = { epoch ->
                                        val d = dateOf(epoch)
                                        payDate = "${d.year}-${(d.month0 + 1).toString().padStart(2, '0')}-${d.day.toString().padStart(2, '0')}"
                                    },
                                )
                            },
                        )
                    }
                    Box(Modifier.weight(1.2f)) {
                        Field(label = t("lg.paymentNote"), value = payNote, onChangeText = { payNote = it }, placeholder = t("lg.optionalNote"))
                    }
                }
                // METHOD — five compact chips on one horizontal scroller
                Text(t("lg.paymentMethod"), fontSize = F.tiny.sp, color = C.sub, fontWeight = FontWeight.SemiBold)
                Row(Modifier.padding(top = 4.dp)) {
                    ScrollRow {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "cash" to t("lg.pmCash"),
                                "bank" to t("lg.pmBank"),
                                "card" to t("lg.pmCard"),
                                "mobile" to t("lg.pmMobile"),
                                "other" to t("cd.other"),
                            ).forEach { (m, lbl) ->
                                Chip(label = lbl, compact = true, active = method == m, onPress = { method = m })
                            }
                        }
                    }
                }
                // SAVE — one small full-width button (v1.6.49: UPDATE when
                // editing, ADD otherwise)
                Row(Modifier.padding(top = 10.dp)) {
                    Btn(
                        small = true, label = if (edit != null) t("lg.updatePayment") else t("act.save"),
                        onPress = {
                            val v = amount.replace(",", ".").toDoubleOrNull()
                            if (v == null || v <= 0) {
                                Toasts.toast(t("lg.amountGreaterThanZero"), "error")
                                return@Btn
                            }
                            // v1.6.41 — a payment made from the calculator can
                            // never go above the remaining total ("when making
                            // payment it should not go above the total for the
                            // individual line and for the sum total")
                            if (cap != null && v > cap + 0.005) {
                                Toasts.toast(t("lg.paymentTooMuch"), "error")
                                return@Btn
                            }
                            if (edit != null) {
                                // v1.6.49 — the recorded payment REWRITES IN
                                // PLACE: same id, same line attribution, same
                                // auto marker — only the four editable fields
                                // move
                                AppStore.updatePayment(
                                    entryId,
                                    edit.copy(
                                        amount = v,
                                        date = payDate.ifEmpty { todayStr() },
                                        method = method,
                                        note = payNote.ifEmpty { null },
                                    ),
                                )
                                Toasts.toast(t("lg.paymentUpdated"))
                            } else {
                                AppStore.addPayment(
                                    entryId,
                                    Payment(
                                        id = uid(),
                                        amount = v,
                                        date = payDate.ifEmpty { todayStr() },
                                        method = method,
                                        note = payNote.ifEmpty { null },
                                        lineIdx = lineIdx,
                                        createdAt = now(),
                                    )
                                )
                                Toasts.toast(t("lg.paymentAdded"))
                            }
                            close()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}


/** "500.0" → "500"; keeps "12.5" — nicer Fill-remaining button behavior. */
private fun trimNumCompat(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

@Composable
private fun StatCard(label: String, value: Double, strong: Boolean, modifier: Modifier = Modifier) {
    val C: ThemeColors = C()
    // v1.6.25 — compact: 12dp corners, 8/7dp inset; the Net card keeps its
    // subtle selected-state styling (brandSoft fill + brand hairline).
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (strong) C.brandSoft else C.card)
            .border(1.dp, if (strong) C.brand else C.line, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
    ) {
        Text(label, fontSize = F.tiny.sp, color = C.sub, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Money(value = value, colorize = true, size = 15, weight = FontWeight.ExtraBold, compact = true)
    }
}

/** v1.6.26 — THE ONE ACTION-BUTTON STYLE for the whole ledger page (primary
 *  row AND secondary row share it — user report: "why is the + expense and
 *  more tabs different from its friends in the contact detail page").
 *  Brand-soft pill, brand hairline, plus/action glyph, bold tiny label. */
@Composable
private fun ActionPill(icon: String, label: String, modifier: Modifier = Modifier, onPress: () -> Unit) {
    val C: ThemeColors = C()
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(C.brandSoft)
            .border(1.dp, C.brand, RoundedCornerShape(12.dp))
            .clickable { onPress() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MdiIcon(icon, 14, C.brand)
        Text(" $label", fontSize = F.tiny.sp, color = C.brand, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
