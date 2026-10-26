package com.callmony.ui.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.lib.dueStatus
import com.callmony.lib.fd
import com.callmony.lib.fdEpoch
import com.callmony.lib.ft
import com.callmony.lib.listTotals
import com.callmony.lib.repeatLabelOf
import com.callmony.lib.now
import com.callmony.lib.relTime
import com.callmony.model.CalcList
import com.callmony.model.Reminder
import com.callmony.theme.F
import com.callmony.ui.components.BottomSheet
import com.callmony.ui.components.PressableRow
import com.callmony.ui.components.SwipeAction
import com.callmony.ui.components.SwipeRow
import com.callmony.ui.kit.Btn
import com.callmony.ui.kit.C
import com.callmony.ui.kit.Card
import com.callmony.ui.kit.Confirm
import com.callmony.ui.kit.EmptyState
import com.callmony.ui.kit.IconBtn
import com.callmony.ui.kit.MdiIcon
import com.callmony.ui.kit.Money
import com.callmony.ui.kit.Pill
import com.callmony.ui.kit.SectionHeader
import com.callmony.ui.kit.Toasts

/* ListsSection — calculating / checklist / todo / simple lists + templates.
   Port of src/sections/ListsSection.tsx. */

private val KIND_ICON = mapOf(
    "calculating" to "calculator-variant-outline",
    "simple" to "format-list-bulleted",
    "checklist" to "check-bold",
    "todo" to "clipboard-check-outline",
)

@Composable
fun ListsSection(ownerId: String) {
    val C = C()
    val lists = AppStore.state.lists
    var kindPicker by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<CalcList?>(null) }

    val mine = remember(lists, ownerId) {
        lists.filter { it.ownerId == ownerId && it.deletedAt == null && !it.isTemplate }.sortedByDescending { it.updatedAt }
    }
    val templates = remember(lists, ownerId) {
        lists.filter { it.ownerId == ownerId && it.deletedAt == null && it.isTemplate }
    }

    Column {
        Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Btn(small = true, label = t("ls.newList"), icon = "plus", onPress = { kindPicker = true })
        }

        if (mine.isEmpty()) {
            EmptyState(
                icon = "clipboard-list-outline",
                title = t("ls.noLists"),
                hint = t("ls.listsHint"),
                actionLabel = t("ls.newList"),
                onAction = { kindPicker = true },
            )
        }

        mine.forEach { l ->
            val totals = listTotals(l)
            val isCalc = l.kind == "calculating"
            Column(Modifier.padding(bottom = 8.dp)) {
                SwipeRow(
                    key = l.id,
                    onLongPress = { menuFor = l },
                    left = listOf(SwipeAction(t("act.open"), "open-in-new", C.brand) { UiStore.open(Overlay.ListDetail(l.id)) }),
                    right = listOf(
                        SwipeAction(t("act.duplicate"), "content-duplicate", Color(0xFF4a7d9b)) {
                            AppStore.duplicateList(l.id)
                            Toasts.toast(t("ls.listDuplicated"))
                        },
                        SwipeAction(t("act.delete"), "trash-can-outline", C.red) {
                            Confirm.ask(t("ls.deleteListTitle"), t("ls.deleteListMsg"), t("act.delete"), danger = true) { ok ->
                                if (ok) { AppStore.deleteList(l.id); Toasts.toastUndo() }
                            }
                        },
                    ),
                    onTap = { UiStore.open(Overlay.ListDetail(l.id)) },
                ) {
                    Card {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(C.brandSoft),
                                contentAlignment = Alignment.Center,
                            ) { MdiIcon(KIND_ICON[l.kind] ?: "format-list-bulleted", 19, C.brand) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(l.title, fontSize = F.body.sp, fontWeight = FontWeight.Bold, color = C.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${totals.count} ${t("ls.items")} · ${relTime(l.updatedAt)}" +
                                        (if (l.repeat != "none") " · ${t("rm.repeat${l.repeat.replaceFirstChar { it.uppercase() }}")}" else ""),
                                    fontSize = F.tiny.sp,
                                    color = C.sub,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (isCalc) {
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Money(totals.grand, size = 15, weight = FontWeight.ExtraBold, compact = true)
                                    if (totals.pending > 0.005) Pill("${totals.count - totals.paidCount}/${totals.count}", "amber")
                                    else Pill(t("ls.allSettled"), "green", icon = "check")
                                }
                            } else {
                                Pill(
                                    "${totals.paidCount}/${totals.count}",
                                    if (totals.paidCount == totals.count) "green" else "neutral",
                                )
                            }
                            MdiIcon("chevron-right", 20, C.faint)
                        }
                        if (l.postToLedger) {
                            Box(Modifier.padding(top = 8.dp)) { Pill(t("ls.countPending"), "amber", icon = "scale-balance") }
                        }
                    }
                }
            }
        }

        // templates
        if (templates.isNotEmpty()) {
            Column(Modifier.padding(top = 10.dp)) {
                SectionHeader(t("ls.templates"), subtitle = t("ls.reuseTemplate"))
                templates.forEach { tpl ->
                    Card(Modifier.padding(bottom = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MdiIcon(KIND_ICON[tpl.kind] ?: "format-list-bulleted", 19, C.sub)
                            Text(
                                tpl.title,
                                fontSize = F.small.sp,
                                fontWeight = FontWeight.Bold,
                                color = C.ink,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Pill(t("ls.templates"), "neutral")
                            Btn(small = true, label = t("act.new"), onPress = {
                                val id = AppStore.duplicateList(tpl.id, fresh = true)
                                if (id != null) UiStore.open(Overlay.ListDetail(id))
                            })
                        }
                    }
                }
            }
        }
    }

    // list long-press menu
    menuFor?.let { l ->
        BottomSheet(title = l.title, onClose = { menuFor = null }) {
            Column {
                listOf(
                    Triple("open-in-new", t("act.open"), "open"),
                    Triple("content-duplicate", t("act.duplicate"), "dup"),
                    Triple("content-save-edit-outline", t("ls.saveAsTemplate"), "tpl"),
                    Triple("trash-can-outline", t("act.delete"), "del"),
                ).forEach { (icon, label, key) ->
                    PressableRow(onClick = {
                        menuFor = null
                        when (key) {
                            "open" -> UiStore.open(Overlay.ListDetail(l.id))
                            "dup" -> AppStore.duplicateList(l.id)
                            "tpl" -> { AppStore.duplicateList(l.id, asTemplate = true); Toasts.toast(t("ls.savedAsTemplate")) }
                            "del" -> Confirm.ask(t("ls.deleteListTitle"), t("ls.deleteListMsg"), t("act.delete"), danger = true) { ok ->
                                if (ok) { AppStore.deleteList(l.id); Toasts.toastUndo() }
                            }
                        }
                    }) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MdiIcon(icon, 20, if (key == "del") C.red else C.ink)
                            Text(label, fontSize = 16.sp, color = if (key == "del") C.red else C.ink)
                        }
                    }
                }
            }
        }
    }

    // kind picker
    if (kindPicker) {
        BottomSheet(title = t("ls.newList"), onClose = { kindPicker = false }) {
            Column {
                Text(t("ls.listsHint"), fontSize = F.small.sp, color = C.sub, modifier = Modifier.padding(bottom = 12.dp))
                listOf("calculating", "checklist", "todo", "simple").forEach { k ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(C.card)
                            .border(1.dp, C.line, RoundedCornerShape(16.dp))
                            .clickable {
                                kindPicker = false
                                UiStore.open(Overlay.ListEdit(null, ownerId, k))
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(C.brandSoft),
                            contentAlignment = Alignment.Center,
                        ) { MdiIcon(KIND_ICON[k] ?: "", 20, C.brand) }
                        Column(Modifier.weight(1f)) {
                            Text(t("ls.kind${k.replaceFirstChar { it.uppercase() }}"), fontSize = F.body.sp, fontWeight = FontWeight.Bold, color = C.ink)
                            Text(
                                when (k) {
                                    "calculating" -> t("ls.totalsCharges")
                                    "checklist" -> t("ls.groupSameItems")
                                    "todo" -> t("ls.priority")
                                    else -> t("ls.items")
                                },
                                fontSize = F.tiny.sp,
                                color = C.sub,
                            )
                        }
                        MdiIcon("chevron-right", 20, C.faint)
                    }
                }
            }
        }
    }
}

/* RemindersSection — grouped by status, kind chips, snooze actions, done
   toggle. Used by General, ContactDetail and the Reminders tab.
   Port of src/sections/RemindersSection.tsx. */

@Composable
fun RemindersSection(
    ownerId: String? = null,
    status: String = "all",
    kindFilter: String = "all",
    maxRows: Int? = null,
    onSeeAll: (() -> Unit)? = null,
) {
    val C = C()
    val reminders = AppStore.state.reminders
    var menuFor by remember { mutableStateOf<Reminder?>(null) }

    val filtered = remember(reminders, ownerId, status, kindFilter, maxRows) {
        var list = reminders.filter { it.deletedAt == null }
        if (ownerId != null) list = list.filter { it.ownerId == ownerId }
        if (status != "all") {
            if (status == "done") list = list.filter { it.done }
            else list = list.filter { !it.done && dueStatus(it.snoozedUntil ?: it.dueAt) == status }
        }
        if (kindFilter != "all") list = list.filter { it.kind == kindFilter }
        list.sortedWith { a, b ->
            val ka = if (a.done) a.doneAt ?: 0 else a.snoozedUntil ?: a.dueAt
            val kb = if (b.done) b.doneAt ?: 0 else b.snoozedUntil ?: b.dueAt
            ka.compareTo(kb)
        }.let { if (maxRows != null) it.take(maxRows) else it }
    }

    val groups = remember(filtered) {
        val order = listOf(t("rm.overdue"), t("rm.today"), t("rm.upcoming"), t("rm.done"))
        val map = linkedMapOf<String, MutableList<Reminder>>()
        filtered.forEach { r ->
            val key = when {
                r.done -> t("rm.done")
                dueStatus(r.snoozedUntil ?: r.dueAt) == "overdue" -> t("rm.overdue")
                dueStatus(r.snoozedUntil ?: r.dueAt) == "today" -> t("rm.today")
                else -> t("rm.upcoming")
            }
            map.getOrPut(key) { mutableListOf() }.add(r)
        }
        map.entries.sortedBy { order.indexOf(it.key) }
    }

    if (filtered.isEmpty()) {
        EmptyState(
            icon = "bell-off-outline",
            title = when (status) {
                "done" -> t("rm.done")
                "overdue" -> t("rm.nothingOverdue")
                "today" -> t("rm.nothingToday")
                else -> t("rm.noReminders")
            },
            hint = if (ownerId == "general") t("rm.remindersGeneralHint") else t("rm.remindersHint"),
            actionLabel = t("rm.addReminder"),
            onAction = { UiStore.open(Overlay.ReminderEdit(null, ownerId ?: "general")) },
        )
        return
    }

    Column {
        groups.forEach { (label, list) ->
            Column(Modifier.padding(bottom = 8.dp)) {
                Text(
                    "$label · ${list.size}",
                    fontSize = F.small.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = when (label) {
                        t("rm.overdue") -> C.red
                        t("rm.today") -> C.amber
                        else -> C.sub
                    },
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                list.forEach { r ->
                    Column(Modifier.padding(bottom = 6.dp)) {
                        SwipeRow(
                            key = r.id,
                            left = listOf(
                                SwipeAction(
                                    if (r.done) t("rm.reopen") else t("act.done"),
                                    if (r.done) "restore" else "check",
                                    if (r.done) Color(0xFF4a7d9b) else C.green,
                                ) { if (r.done) AppStore.reopenReminder(r.id) else AppStore.completeReminder(r.id) },
                            ),
                            right = listOf(
                                SwipeAction(t("rm.snooze1h"), "alarm-clock-plus", Color(0xFFb97d10)) {
                                    AppStore.snoozeReminder(r.id, now() + 3600000)
                                    Toasts.toast(t("rm.snoozed", mapOf("label" to "1h")))
                                },
                                SwipeAction(t("act.delete"), "trash-can-outline", C.red) {
                                    Confirm.ask(t("rm.deleteReminder"), t("rm.deleteMsg"), t("act.delete"), danger = true) { ok ->
                                        if (ok) { AppStore.deleteReminder(r.id); Toasts.toastUndo() }
                                    }
                                },
                            ),
                            onTap = { UiStore.open(Overlay.ReminderEdit(r.id, r.ownerId)) },
                        ) {
                            Card {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(
                                        Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .border(2.dp, if (r.done) C.green else C.line, CircleShape)
                                            .background(if (r.done) C.green else Color.Transparent)
                                            .clickable { if (r.done) AppStore.reopenReminder(r.id) else AppStore.completeReminder(r.id) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (r.done) MdiIcon("check", 17, Color.White)
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            r.title.ifEmpty { t("rm.kindCustom") },
                                            fontSize = F.body.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = C.ink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textDecoration = if (r.done) TextDecoration.LineThrough else null,
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            if (!r.done && dueStatus(r.snoozedUntil ?: r.dueAt) == "overdue") Pill(t("rm.overdue"), "red")
                                            if (r.snoozedUntil != null && !r.done) Pill(t("rm.snoozed"), "neutral", icon = "clock-outline")
                                            // v1.6.18 — ALARM reminders are marked with the alarm icon
                                            if (!r.done && r.isAlarm) MdiIcon("alarm-check-outline", 13, C.brand)
                                            Text(
                                                "${fdEpoch(r.snoozedUntil ?: r.dueAt)} · ${ft(r.snoozedUntil ?: r.dueAt)}",
                                                fontSize = F.tiny.sp,
                                                color = C.sub,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            // v1.6.17 — live "in 2h 15m" / "3h late" countdown
                                            if (!r.done) com.callmony.ui.kit.LiveCountdown(r.snoozedUntil ?: r.dueAt)
                                            // v1.6.18 — human repeat label ("Every 2 weeks"),
                                            // tooltip-free reminder of the interval
                                            if (r.repeat != "none") {
                                                MdiIcon("repeat", 13, C.sub)
                                                if (!repeatLabelOf(r.repeat).isBlank()) {
                                                    Text(repeatLabelOf(r.repeat), fontSize = F.tiny.sp, color = C.sub, maxLines = 1)
                                                }
                                            }
                                        }
                                    }
                                    IconBtn("dots-vertical", { menuFor = r }, size = 32)
                                }
                            }
                        }
                    }
                }
            }
        }
        // trailing dashed add row — the ＋ is ALWAYS reachable
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, C.line, RoundedCornerShape(14.dp))
                .clickable { UiStore.open(Overlay.ReminderEdit(null, ownerId ?: "general")) }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MdiIcon("plus", 16, C.brand)
            Text(t("rm.addReminder"), fontSize = F.small.sp, color = C.brand, fontWeight = FontWeight.Bold)
        }
        if (maxRows != null && filtered.size >= maxRows && onSeeAll != null) {
            Row(Modifier.padding(top = 6.dp)) {
                Btn(small = true, label = t("act.showAll"), variant = "soft", onPress = onSeeAll)
            }
        }
    }
}

/* reminder long-press/⋮ menu */
@Composable
fun ReminderMenu(r: Reminder, onClose: () -> Unit) {
    val C = C()
    BottomSheet(title = r.title.ifEmpty { t("rm.kindCustom") }, onClose = onClose) {
        Column {
            data class Item(val icon: String, val label: String, val danger: Boolean = false, val run: () -> Unit)
            val items = listOf(
                Item(if (r.done) "restore" else "check", if (r.done) t("rm.reopen") else t("act.done")) {
                    if (r.done) AppStore.reopenReminder(r.id) else AppStore.completeReminder(r.id)
                },
                Item("alarm-clock-plus", t("rm.snooze10")) {
                    AppStore.snoozeReminder(r.id, now() + 600000)
                    Toasts.toast(t("rm.snoozed", mapOf("label" to "10m")))
                },
                Item("alarm-clock-plus", t("rm.snooze1h")) {
                    AppStore.snoozeReminder(r.id, now() + 3600000)
                    Toasts.toast(t("rm.snoozed", mapOf("label" to "1h")))
                },
                Item("alarm-clock-plus", t("rm.snooze3h")) {
                    AppStore.snoozeReminder(r.id, now() + 3 * 3600000)
                    Toasts.toast(t("rm.snoozed", mapOf("label" to "3h")))
                },
                Item("alarm-clock-plus", t("rm.snoozeTomorrow")) {
                    val d = com.callmony.lib.dateOf(now() + 86400000)
                    val t9 = com.callmony.lib.KmpDate(d.year, d.month0, d.day, 9, 0, 0).time()
                    AppStore.snoozeReminder(r.id, t9)
                    Toasts.toast(t("rm.snoozed", mapOf("label" to t("rm.snoozeTomorrow"))))
                },
                Item("pencil-outline", t("act.edit")) {
                    UiStore.open(Overlay.ReminderEdit(r.id, r.ownerId))
                },
                Item("trash-can-outline", t("act.delete"), danger = true) {
                    Confirm.ask(t("rm.deleteReminder"), t("rm.deleteMsg"), t("act.delete"), danger = true) { ok ->
                        if (ok) { AppStore.deleteReminder(r.id); Toasts.toastUndo() }
                    }
                },
            )
            items.forEach { item ->
                PressableRow(onClick = { onClose(); item.run() }) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MdiIcon(item.icon, 20, if (item.danger) C.red else C.ink)
                        Text(item.label, fontSize = 16.sp, color = if (item.danger) C.red else C.ink)
                    }
                }
            }
        }
    }
}
