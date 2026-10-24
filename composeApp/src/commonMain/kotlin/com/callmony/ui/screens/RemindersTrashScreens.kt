package com.callmony.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.lib.dueStatus
import com.callmony.lib.ownerNameOf
import com.callmony.platform.Platform
import com.callmony.theme.F
import com.callmony.ui.kit.Btn
import com.callmony.ui.kit.C
import com.callmony.ui.kit.Card
import com.callmony.ui.kit.Chip
import com.callmony.ui.kit.EmptyState
import com.callmony.ui.kit.IconBtn
import com.callmony.ui.kit.MdiIcon
import com.callmony.ui.kit.Pill
import com.callmony.ui.kit.Segmented
import com.callmony.ui.kit.Toasts
import com.callmony.ui.sections.RemindersSection
import com.callmony.ui.components.ScrollRow

/* Reminders tab — status + kind filters + the live notification status card.
   Port of src/screens/RemindersScreen.tsx. */

@Composable
fun RemindersScreen() {
    val C = C()
    val reminders = AppStore.state.reminders
    var status by remember { mutableStateOf("overdue") }
    var kind by remember { mutableStateOf("all") }
    var notifOn by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        notifOn = Platform.notify.hasNotifPermission()
    }

    val counts = remember(reminders) {
        val live = reminders.filter { it.deletedAt == null }
        Count4(
            overdue = live.count { !it.done && dueStatus(it.snoozedUntil ?: it.dueAt) == "overdue" },
            today = live.count { !it.done && dueStatus(it.snoozedUntil ?: it.dueAt) == "today" },
            upcoming = live.count { !it.done && dueStatus(it.snoozedUntil ?: it.dueAt) == "upcoming" },
            done = live.count { it.done },
        )
    }

    fun askNotifications() {
        Platform.notify.requestNotifPermission { ok ->
            notifOn = ok
            AppStore.updateSettings { it.copy(notifAsked = true) }
            Toasts.toast(if (ok) t("rm.notifGranted") else t("rm.notifDeclined"), if (ok) "ok" else "info")
            if (!ok) Platform.share.openUrl("package:com.callmony")
        }
    }

    Column(Modifier.fillMaxSize()) {
        // pinned header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(t("tab.reminders"), fontSize = F.h2.sp, fontWeight = FontWeight.ExtraBold, color = C.ink)
                Text(
                    t("rm.statsLine", mapOf("overdue" to counts.overdue.toString(), "today" to counts.today.toString(), "upcoming" to counts.upcoming.toString())),
                    fontSize = F.small.sp,
                    color = C.sub,
                    maxLines = 1,
                )
            }
            Btn(small = true, label = t("rm.newReminder"), icon = "plus", onPress = { UiStore.open(Overlay.ReminderEdit(null, "general")) })
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            // notification status card
            Card(Modifier.padding(bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MdiIcon(
                        when (notifOn) {
                            true -> "bell-check-outline"
                            false -> "bell-off-outline"
                            else -> "bell-outline"
                        },
                        22,
                        if (notifOn == true) C.green else C.amber,
                    )
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (notifOn == true) t("rm.notifOnTitle") else t("rm.turnOnNotifications"),
                                fontSize = F.small.sp,
                                fontWeight = FontWeight.Bold,
                                color = C.ink,
                            )
                            Pill(
                                if (notifOn == true) "ON" else "OFF",
                                if (notifOn == true) "green" else "amber",
                                icon = if (notifOn == true) "check-circle-outline" else "alert-outline",
                            )
                        }
                        Text(
                            if (notifOn == true) t("rm.notifOnHint") else t("rm.notifCardHint"),
                            fontSize = F.tiny.sp,
                            color = C.sub,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                if (notifOn != true) {
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn(small = true, label = t("rm.turnOn"), onPress = { askNotifications() })
                        Btn(small = true, label = t("perm.openSettings"), variant = "secondary", onPress = { Platform.share.openUrl("package:com.callmony") })
                    }
                }
            }

            // status segmented
            Box(Modifier.padding(bottom = 10.dp)) {
                Segmented(
                    listOf(
                        Triple("overdue", t("rm.overdue"), counts.overdue),
                        Triple("today", t("rm.today"), counts.today),
                        Triple("upcoming", t("rm.upcoming"), counts.upcoming),
                        Triple("done", t("rm.done"), counts.done),
                        Triple("all", t("act.all"), null),
                    ),
                    status,
                ) { status = it }
            }

            // kind chips
            Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ScrollRow {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(label = t("rm.allKinds"), active = kind == "all", onPress = { kind = "all" })
                        listOf("payment", "todo", "birthday", "followup", "custom").forEach { k ->
                            Chip(label = t("rm.kind${k.replaceFirstChar { it.uppercase() }}"), active = kind == k, onPress = { kind = k })
                        }
                    }
                }
            }

            RemindersSection(status = status, kindFilter = kind)
        }
    }
}

private data class Count4(val overdue: Int, val today: Int, val upcoming: Int, val done: Int)

/* Trash — soft-deleted app entities, restore / delete forever / empty.
   Port of src/screens/TrashScreen.tsx. */

private data class TrashRow(
    val id: String,
    val type: String,
    val icon: String,
    val title: String,
    val sub: String,
    val ownerId: String,
    val at: Long,
    val restore: () -> Unit,
    val purge: () -> Unit,
)

@Composable
fun TrashScreen() {
    val C = C()
    val state = AppStore.state
    var filter by remember { mutableStateOf("all") }

    val rows = remember(state.entries, state.groups, state.lists, state.notes, state.reminders, state.banks) {
        val out = mutableListOf<TrashRow>()
        state.entries.filter { it.deletedAt != null }.forEach { e ->
            out.add(TrashRow(e.id, "entries", "book-outline", "${e.kind ?: e.type} · ${e.amount}", "${t("misc.entry")} · ${e.date}", e.ownerId, e.deletedAt ?: 0, { AppStore.restoreEntry(e.id) }, { AppStore.purgeEntry(e.id) }))
        }
        state.groups.filter { it.deletedAt != null }.forEach { g ->
            out.add(TrashRow(g.id, "groups", "folder-outline", g.title, t("lg.group"), g.ownerId, g.deletedAt ?: 0, { AppStore.restoreGroup(g.id) }, { AppStore.purgeGroup(g.id) }))
        }
        state.lists.filter { it.deletedAt != null }.forEach { l ->
            out.add(TrashRow(l.id, "lists", "clipboard-list-outline", l.title, t("misc.list"), l.ownerId, l.deletedAt ?: 0, { AppStore.restoreList(l.id) }, { AppStore.purgeList(l.id) }))
        }
        state.notes.filter { it.deletedAt != null }.forEach { n ->
            out.add(TrashRow(n.id, "notes", "notebook-outline", n.title.ifEmpty { t("nt.untitled") }, t("misc.note"), n.ownerId, n.deletedAt ?: 0, { AppStore.restoreNote(n.id) }, { AppStore.purgeNote(n.id) }))
        }
        state.reminders.filter { it.deletedAt != null }.forEach { r ->
            out.add(TrashRow(r.id, "reminders", "bell-outline", r.title, t("misc.reminder"), r.ownerId, r.deletedAt ?: 0, { AppStore.restoreReminder(r.id) }, { AppStore.purgeReminder(r.id) }))
        }
        state.banks.filter { it.deletedAt != null }.forEach { b ->
            out.add(TrashRow(b.id, "banks", "bank-outline", b.bankName ?: b.label, t("misc.bank"), b.ownerId, b.deletedAt ?: 0, { AppStore.restoreBank(b.id) }, { AppStore.purgeBank(b.id) }))
        }
        out.sortedByDescending { it.at }
    }

    val visible = if (filter == "all") rows else rows.filter { it.type == filter }
    val counts = mapOf(
        "entries" to rows.count { it.type == "entries" },
        "groups" to rows.count { it.type == "groups" },
        "lists" to rows.count { it.type == "lists" },
        "notes" to rows.count { it.type == "notes" },
        "reminders" to rows.count { it.type == "reminders" },
        "banks" to rows.count { it.type == "banks" },
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 24.dp),
    ) {
        Box(Modifier.padding(bottom = 12.dp)) {
            Segmented(
                listOf(
                    Triple("all", t("act.all"), rows.size),
                    Triple("entries", t("misc.entry"), counts["entries"]),
                    Triple("groups", t("lg.group"), counts["groups"]),
                    Triple("lists", t("misc.list"), counts["lists"]),
                    Triple("notes", t("misc.note"), counts["notes"]),
                    Triple("reminders", t("misc.reminder"), counts["reminders"]),
                    Triple("banks", t("misc.bank"), counts["banks"]),
                ),
                filter,
            ) { filter = it }
        }

        if (rows.isEmpty()) {
            EmptyState(icon = "trash-can-outline", title = t("tr.trashEmpty"), hint = t("tr.trashEmptyHint"))
        } else {
            Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(small = true, label = t("tr.restoreAll"), icon = "restore", variant = "soft", onPress = {
                    visible.forEach { it.restore() }
                    Toasts.toast(t("tr.restoredN", mapOf("n" to visible.size.toString())))
                })
                Btn(small = true, label = t("tr.empty"), icon = "trash-can-outline", variant = "danger", onPress = {
                    com.callmony.ui.kit.Confirm.ask(t("tr.emptyTrashTitle"), t("tr.emptyTrashMsg"), t("tr.empty"), danger = true) { ok ->
                        if (ok) {
                            visible.forEach { it.purge() }
                            Toasts.toast(t("tr.trashEmptied"))
                        }
                    }
                })
            }
            visible.forEach { r ->
                Card(Modifier.padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(C.chip),
                            contentAlignment = Alignment.Center,
                        ) { MdiIcon(r.icon, 18, C.sub) }
                        Column(Modifier.weight(1f)) {
                            Text(r.title, fontSize = F.body.sp, fontWeight = FontWeight.Bold, color = C.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${r.sub} · ${ownerNameOf(r.ownerId)}", fontSize = F.tiny.sp, color = C.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconBtn("restore", r.restore, size = 34)
                        IconBtn("trash-can-outline", r.purge, size = 34, danger = true)
                    }
                }
            }
        }
    }
}
