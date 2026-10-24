package com.callmony.ui.overlays

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.lib.dateOf
import com.callmony.lib.fd
import com.callmony.lib.fdEpoch
import com.callmony.lib.ft
import com.callmony.lib.now
import com.callmony.lib.ownerNameOf
import com.callmony.lib.todayStr
import com.callmony.lib.uid
import com.callmony.model.BankAccount
import com.callmony.model.Note
import com.callmony.model.contactOwnerId
import com.callmony.model.Reminder
import com.callmony.platform.Platform
import com.callmony.theme.F
import com.callmony.ui.components.Sheet
import com.callmony.ui.kit.Btn
import com.callmony.ui.kit.C
import com.callmony.ui.kit.Chip
import com.callmony.ui.kit.Confirm
import com.callmony.ui.kit.Field
import com.callmony.ui.kit.MdiIcon
import com.callmony.ui.kit.Pill
import com.callmony.ui.kit.Toasts
import com.callmony.ui.sections.NOTE_COLORS
import com.callmony.ui.components.ScrollRow

/* Editors — Note / Reminder / Bank editor sheets.
   Port of src/overlays/Editors.tsx. */

/* ---------------- NoteEdit ---------------- */

@Composable
fun NoteEditOverlay(noteId: String?, ownerId: String) {
    val C = C()
    val pop = { UiStore.pop() }
    val existing = noteId?.let { id -> AppStore.state.notes.find { it.id == id } }

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var body by remember { mutableStateOf(existing?.body ?: "") }
    var tags by remember { mutableStateOf(existing?.tags?.joinToString(", ") ?: "") }
    var pinned by remember { mutableStateOf(existing?.pinned ?: false) }
    var color by remember { mutableStateOf(existing?.color) }

    Sheet(
        title = if (existing == null) t("nt.newNote") else t("nt.editNote"),
        subtitle = ownerNameOf(ownerId),
        onBack = pop,
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (existing != null) {
                    Btn(
                        label = t("act.delete"), variant = "danger",
                        onPress = {
                            Confirm.ask(t("nt.deleteTitle"), t("nt.deleteMsg"), t("act.delete"), danger = true) { ok ->
                                if (ok) { AppStore.deleteNote(existing.id); Toasts.toastUndo(); pop() }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Btn(
                    label = t("act.save"),
                    onPress = {
                        if (title.isBlank() && body.isBlank()) return@Btn
                        AppStore.saveNote(
                            Note(
                                id = existing?.id ?: uid(),
                                ownerId = ownerId,
                                title = title.trim(),
                                body = body,
                                tags = tags.split(",").map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() },
                                pinned = pinned,
                                color = color,
                                createdAt = existing?.createdAt ?: now(),
                                updatedAt = now(),
                            )
                        )
                        Toasts.toast(t("act.saved"))
                        pop()
                    },
                    modifier = Modifier.weight(1f),
                    disabled = title.isBlank() && body.isBlank(),
                )
            }
        },
    ) {
        Column {
            Field(label = t("nt.noteTitle"), value = title, onChangeText = { title = it })
            Field(label = t("nt.noteBody"), value = body, onChangeText = { body = it }, placeholder = t("nt.writeAnything"), multiline = true)
            Field(label = t("nt.noteTags"), value = tags, onChangeText = { tags = it }, placeholder = t("nt.tagsPlaceholder"))
            // pinned switch
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.card)
                    .clickable { pinned = !pinned }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t("nt.pinned"), fontSize = F.body.sp, color = C.ink, modifier = Modifier.weight(1f))
                EditorsSwitch(checked = pinned, onChange = { pinned = it })
            }
            // color dots
            Text(t("nt.color"), fontSize = F.small.sp, color = C.sub, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .border(1.dp, C.line, CircleShape)
                        .clickable { color = null },
                    contentAlignment = Alignment.Center,
                ) { MdiIcon("close", 16, C.faint) }
                NOTE_COLORS.forEach { hex ->
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(parse(hex))
                            .then(if (color == hex) Modifier.border(2.dp, C.brand, CircleShape) else Modifier)
                            .clickable { color = hex },
                    )
                }
            }
        }
    }
}

private fun parse(hex: String): Color = com.callmony.ui.kit.parseHexColor(hex)

@Composable
fun EditorsSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val C = C()
    Box(
        Modifier
            .size(46.dp, 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (checked) C.brand else C.chip)
            .clickable { onChange(!checked) },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart),
        )
    }
}

/* ---------------- ReminderEdit ---------------- */

@Composable
fun ReminderEditOverlay(reminderId: String?, ownerId: String) {
    val C = C()
    val pop = { UiStore.pop() }
    val existing = reminderId?.let { id -> AppStore.state.reminders.find { it.id == id } }

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var kind by remember { mutableStateOf(existing?.kind ?: "payment") }
    var repeat by remember { mutableStateOf(existing?.repeat ?: "none") }
    var leads by remember { mutableStateOf(existing?.leadMinutes ?: listOf<Int>()) }
    val initialDue = existing?.let { dateOf(it.snoozedUntil ?: it.dueAt) }
    var dueDate by remember { mutableStateOf(existing?.let { fdEpoch(it.snoozedUntil ?: it.dueAt) } ?: todayStr()) }
    var dueTime by remember { mutableStateOf(initialDue?.let { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" } ?: "09:00") }
    var notifOn by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) { notifOn = Platform.notify.hasNotifPermission() }

    val kinds = listOf("payment", "todo", "birthday", "followup", "custom")

    fun parseDue(): Long? {
        val dateParts = dueDate.split("-")
        val timeParts = dueTime.split(":")
        if (dateParts.size != 3 || timeParts.size != 2) return null
        return try {
            com.callmony.lib.KmpDate(
                dateParts[0].toInt(), dateParts[1].toInt() - 1, dateParts[2].toInt(),
                timeParts[0].toInt(), timeParts[1].toInt(), 0,
            ).time()
        } catch (_: Exception) { null }
    }

    Sheet(
        title = if (existing == null) t("rm.newReminder") else t("rm.editReminder"),
        subtitle = ownerNameOf(ownerId),
        onBack = pop,
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (existing != null) {
                    Btn(
                        label = t("act.delete"), variant = "danger",
                        onPress = {
                            Confirm.ask(t("rm.deleteReminder"), t("rm.deleteMsg"), t("act.delete"), danger = true) { ok ->
                                if (ok) { AppStore.deleteReminder(existing.id); Toasts.toastUndo(); pop() }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Btn(
                    label = t("act.save"), icon = "bell-outline",
                    onPress = {
                        val at = parseDue()
                        if (at == null) {
                            Toasts.toast(t("rm.validDateTime"), "error")
                            return@Btn
                        }
                        AppStore.saveReminder(
                            Reminder(
                                id = existing?.id ?: uid(),
                                ownerId = ownerId,
                                title = title.trim().ifEmpty { t("rm.kind${kind.replaceFirstChar { it.uppercase() }}") },
                                notes = notes.ifEmpty { null },
                                kind = kind,
                                dueAt = at,
                                repeat = repeat,
                                leadMinutes = leads,
                                createdAt = existing?.createdAt ?: now(),
                                updatedAt = now(),
                            )
                        )
                        if (at <= now() && existing == null) {
                            Toasts.toast(t("rm.pastTitle"), "info")
                        }
                        Toasts.toast(t("act.saved"))
                        pop()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) {
        Column {
            Field(label = t("rm.reminderTitle"), value = title, onChangeText = { title = it }, placeholder = t("rm.whatShouldRemind"))
            Field(label = t("rm.reminderNotes"), value = notes, onChangeText = { notes = it }, multiline = true)
            // kind chips
            Text(t("rm.kindLabel"), fontSize = F.small.sp, color = C.sub, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
            Row(Modifier.padding(bottom = 12.dp)) {
                ScrollRow {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        kinds.forEach { k ->
                            Chip(label = t("rm.kind${k.replaceFirstChar { it.uppercase() }}"), compact = true, active = kind == k, onPress = { kind = k })
                        }
                    }
                }
            }
            // date + time
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Field(label = t("rm.dueDate"), value = dueDate, onChangeText = { dueDate = it }, placeholder = "YYYY-MM-DD") }
                Box(Modifier.weight(1f)) { Field(label = t("rm.dueTime"), value = dueTime, onChangeText = { dueTime = it }, placeholder = "HH:MM") }
            }
            // quick chips
            Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ScrollRow {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(label = t("rm.today"), compact = true, onPress = { dueDate = todayStr() })
                        Chip(label = t("rm.tomorrow"), compact = true, onPress = {
                            val d = dateOf(now() + 86400000)
                            dueDate = "${d.year}-${(d.month0 + 1).toString().padStart(2, '0')}-${d.day.toString().padStart(2, '0')}"
                        })
                        Chip(label = t("rm.nextWeek"), compact = true, onPress = {
                            val d = dateOf(now() + 7 * 86400000)
                            dueDate = "${d.year}-${(d.month0 + 1).toString().padStart(2, '0')}-${d.day.toString().padStart(2, '0')}"
                        })
                        Chip(label = "9:00", compact = true, onPress = { dueTime = "09:00" })
                        Chip(label = "13:00", compact = true, onPress = { dueTime = "13:00" })
                        Chip(label = "18:00", compact = true, onPress = { dueTime = "18:00" })
                        Chip(label = "20:00", compact = true, onPress = { dueTime = "20:00" })
                    }
                }
            }
            // live preview
            val at = parseDue()
            Text(
                "${t("rm.due")}: ${at?.let { "${fdEpoch(it)} · ${ft(it)}" } ?: t("rm.validDateTime")}",
                fontSize = F.small.sp, color = C.brand, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            // notification status
            if (notifOn == false) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(C.amber.copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MdiIcon("bell-off-outline", 18, C.amber)
                    Text(t("rm.notifCardHint"), fontSize = F.tiny.sp, color = C.sub, modifier = Modifier.weight(1f))
                    Btn(small = true, label = t("rm.turnOn"), onPress = {
                        Platform.notify.requestNotifPermission { ok -> notifOn = ok }
                    })
                }
            }
            // repeats
            Text(t("rm.repeat"), fontSize = F.small.sp, color = C.sub, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
            Row(Modifier.padding(bottom = 12.dp)) {
                ScrollRow {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "none" to t("rm.repeatOnce"),
                            "daily" to t("rm.repeatDaily"),
                            "weekly" to t("rm.repeatWeekly"),
                            "monthly" to t("rm.repeatMonthly"),
                            "yearly" to t("rm.repeatYearly"),
                        ).forEach { (k, label) ->
                            Chip(label = label, compact = true, active = repeat == k, onPress = { repeat = k })
                        }
                    }
                }
            }
            // lead-time pre-alerts
            Text(t("rm.alertEarly"), fontSize = F.small.sp, color = C.sub, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
            Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(label = t("rm.lead1d"), compact = true, active = leads.contains(1440), onPress = {
                    leads = if (leads.contains(1440)) leads - 1440 else leads + 1440
                })
                Chip(label = t("rm.lead1h"), compact = true, active = leads.contains(60), onPress = {
                    leads = if (leads.contains(60)) leads - 60 else leads + 60
                })
            }
            Text(t("rm.leadHint"), fontSize = F.tiny.sp, color = C.faint)
        }
    }
}

/* ---------------- BankEdit ---------------- */

@Composable
fun BankEditOverlay(bankId: String?, ownerId: String) {
    val pop = { UiStore.pop() }
    val existing = bankId?.let { id -> AppStore.state.banks.find { it.id == id } }

    var bankName by remember { mutableStateOf(existing?.bankName ?: "") }
    var accountNumber by remember { mutableStateOf(existing?.accountNumber ?: "") }
    var holder by remember { mutableStateOf(existing?.holderName ?: "") }
    var balance by remember { mutableStateOf(existing?.balance?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "") }
    var owner by remember { mutableStateOf(existing?.ownerId ?: ownerId) }
    var showPicker by remember { mutableStateOf(false) }

    Sheet(
        title = if (existing == null) t("bk.newAccount") else t("bk.editAccount"),
        subtitle = ownerNameOf(ownerId),
        onBack = pop,
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (existing != null) {
                    Btn(
                        label = t("act.delete"), variant = "danger",
                        onPress = {
                            Confirm.ask(t("bk.deleteTitle"), t("bk.deleteMsg"), t("act.delete"), danger = true) { ok ->
                                if (ok) { AppStore.deleteBank(existing.id); Toasts.toastUndo(); pop() }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Btn(
                    label = t("act.save"),
                    onPress = {
                        if (bankName.isBlank() || accountNumber.isBlank()) {
                            Toasts.toast(t("bk.bankNameRequired"), "error")
                            return@Btn
                        }
                        AppStore.saveBank(
                            BankAccount(
                                id = existing?.id ?: uid(),
                                ownerId = owner,
                                label = bankName,
                                bankName = bankName,
                                accountNumber = accountNumber,
                                holderName = holder.ifEmpty { null },
                                balance = balance.replace(",", ".").toDoubleOrNull(),
                                createdAt = existing?.createdAt ?: now(),
                                updatedAt = now(),
                            )
                        )
                        Toasts.toast(t("act.saved"))
                        pop()
                    },
                    modifier = Modifier.weight(1f),
                    disabled = bankName.isBlank() || accountNumber.isBlank(),
                )
            }
        },
    ) {
        Column {
            Field(label = t("bk.bankName"), value = bankName, onChangeText = { bankName = it })
            Field(label = t("bk.accountNumber"), value = accountNumber, onChangeText = { accountNumber = it }, placeholder = t("bk.bankPlaceholder"))
            Text(t("bk.maskedHint"), fontSize = F.tiny.sp, color = C().faint, modifier = Modifier.padding(bottom = 10.dp))
            Field(label = t("bk.holderName"), value = holder, onChangeText = { holder = it })
            Field(label = t("lg.balance"), value = balance, onChangeText = { balance = it }, keyboardType = "decimal-pad", placeholder = "0")
            if (ownerId == "general") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(C().card)
                        .clickable { showPicker = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MdiIcon("account-search-outline", 18, C().brand)
                    Text(t("bk.chooseFromContacts"), fontSize = F.body.sp, color = C().ink, modifier = Modifier.weight(1f))
                    Text(ownerNameOf(owner), fontSize = F.small.sp, color = C().sub)
                }
            }
        }
    }

    if (showPicker) {
        com.callmony.ui.components.ContactPicker(
            title = t("bk.chooseFromContacts"),
            onPick = { c -> showPicker = false; owner = com.callmony.model.contactOwnerId(c.recordID) },
            onBack = { showPicker = false },
        )
    }
}
