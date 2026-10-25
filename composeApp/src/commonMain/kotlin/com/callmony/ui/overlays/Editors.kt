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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.lib.dateOf
import com.callmony.lib.dateStr
import com.callmony.lib.fd
import com.callmony.lib.fdEpoch
import kotlinx.coroutines.delay
import com.callmony.lib.ft
import com.callmony.lib.now
import com.callmony.lib.repeatLabelOf
import com.callmony.lib.ownerNameOf
import com.callmony.lib.timeLeftStr
import com.callmony.lib.todayStr
import com.callmony.lib.uid
import com.callmony.model.BankAccount
import com.callmony.model.Note
import com.callmony.model.contactOwnerId
import com.callmony.model.Reminder
import com.callmony.platform.NativeDateTime
import com.callmony.platform.Platform
import com.callmony.theme.F
import com.callmony.ui.components.Sheet
import com.callmony.ui.kit.Btn
import com.callmony.ui.kit.C
import com.callmony.ui.kit.Chip
import com.callmony.ui.kit.Confirm
import com.callmony.ui.kit.Field
import com.callmony.ui.kit.MdiIcon
import com.callmony.ui.kit.PickerField
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
    // v1.6.18 — ALARM reminders + custom repeat intervals
    var isAlarm by remember { mutableStateOf(existing?.isAlarm ?: false) }
    val existingCustom = existing?.repeat?.let { com.callmony.lib.parseCustomRepeat(it) }
    var customN by remember { mutableStateOf(existingCustom?.first?.toString() ?: "2") }
    var customUnit by remember { mutableStateOf(existingCustom?.second ?: "d") }
    // v1.6.17 FIX — "enter a valid date and time" when EDITING a reminder:
    // dueDate used to be initialised with fdEpoch() (the DISPLAY format
    // "Oct 25, 2026") instead of yyyy-MM-dd, so parseDue() always failed on
    // existing reminders and Save refused to work. dateStr(dateOf()) keeps
    // the machine format the parser expects.
    var dueDate by remember { mutableStateOf(existing?.let { dateStr(dateOf(it.snoozedUntil ?: it.dueAt)) } ?: todayStr()) }
    var dueTime by remember { mutableStateOf(existing?.let { dateOf(it.snoozedUntil ?: it.dueAt) }?.let { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" } ?: "09:00") }
    var notifOn by remember { mutableStateOf<Boolean?>(null) }

    // v1.6.17 — live clock: a ticking "now" shown right beside the due
    // time so the user can compare, plus the countdown preview.
    var nowTick by remember { mutableStateOf(now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowTick = now()
        }
    }

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

    /* v1.6.17 — the due-time field and quick chips render in the app-wide
     * clock format ("9:00 AM" vs "09:00"); the stored value stays 24-hour
     * HH:mm so the native picker keeps its simple hour/minute contract. */
    fun timeLabel(hhmm: String): String {
        val p = hhmm.split(":")
        val h = p.getOrNull(0)?.toIntOrNull() ?: return hhmm
        val m = p.getOrNull(1)?.toIntOrNull() ?: 0
        return if (com.callmony.lib.clock24()) hhmm
        else "${if (h % 12 == 0) 12 else h % 12}:${m.toString().padStart(2, '0')} ${if (h < 12) "AM" else "PM"}"
    }
    fun timeChipLabel(hhmm: String): String {
        val p = hhmm.split(":")
        val h = p.getOrNull(0)?.toIntOrNull() ?: return hhmm
        return if (com.callmony.lib.clock24()) hhmm
        else "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "AM" else "PM"}"
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
                                // v1.6.18: malformed custom intervals ("custom:",
                                // "custom:0d", …) degrade to one-shot instead of
                                // silently never repeating.
                                repeat = if (repeat.startsWith("custom:") && com.callmony.lib.parseCustomRepeat(repeat) == null) "none" else repeat,
                                leadMinutes = leads,
                                isAlarm = isAlarm,
                                createdAt = existing?.createdAt ?: now(),
                                updatedAt = now(),
                            )
                        )
                        if (at > now()) {
                            // v1.6.17 — after saving, show exactly how much
                            // time is left until it fires ("Saved · in 2h 15m").
                            Toasts.toast(t("rm.savedCountdown", mapOf("left" to timeLeftStr(at))))
                        } else {
                            Toasts.toast(t("rm.pastTitle"), "info")
                        }
                        // v1.6.17 — RN v1.6.2 parity: the notification
                        // permission is requested when a reminder is SAVED,
                        // not only from the Reminders tab status card. After
                        // one denial Android never re-shows the dialog, so
                        // this is an instant no-op then (the card remains).
                        if (notifOn == false) {
                            Platform.notify.requestNotifPermission { ok -> notifOn = ok }
                        }
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
            // date + time — v1.6.17: PickerFields (label above + 48 dp box),
            // exactly like the Field beside them, so the pair aligns. The
            // old label-inside boxes were shorter/taller than neighbours and
            // sat flush against the chip tabs below (the "overlap" report).
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    PickerField(
                        label = t("rm.dueDate"),
                        value = fd(dueDate),
                        icon = "calendar-blank-outline",
                        onPress = {
                            val initial = parseDue() ?: now()
                            NativeDateTime.pickDate(
                                epochMillis = initial,
                                onPick = { epoch -> dueDate = dateStr(dateOf(epoch)) },
                            )
                        },
                    )
                }
                Box(Modifier.weight(1f)) {
                    PickerField(
                        label = t("rm.dueTime"),
                        value = timeLabel(dueTime),
                        icon = "clock-outline",
                        onPress = {
                            val parts = dueTime.split(":")
                            val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
                            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                            // v1.6.17 — the picker follows the app-wide
                            // 12 h / 24 h clock choice (Settings → Region).
                            NativeDateTime.pickTime(
                                hourOfDay = h,
                                minute = m,
                                is24Hour = AppStore.state.settings.clock24,
                                onPick = { hh, mm ->
                                    dueTime = "${hh.toString().padStart(2, '0')}:${mm.toString().padStart(2, '0')}"
                                },
                            )
                        },
                    )
                }
            }
            // v1.6.17 — the NOW clock for comparison + 12/24 h toggle +
            // live countdown, all in one compact strip.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.brand.copy(alpha = 0.07f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MdiIcon("clock-outline", 16, C.brand)
                Text(
                    "${t("rm.now")}: ${ft(nowTick)}",
                    fontSize = F.small.sp,
                    color = C.ink,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (AppStore.state.settings.clock24) "12 h" else "24 h",
                    fontSize = F.tiny.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = C.brand,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(C.brand.copy(alpha = 0.12f))
                        .clickable { AppStore.updateSettings { it.copy(clock24 = !it.clock24) } }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            // quick chips
            Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ScrollRow {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(label = t("rm.today"), compact = true, onPress = { dueDate = todayStr() })
                        Chip(label = t("rm.tomorrow"), compact = true, onPress = {
                            dueDate = dateStr(dateOf(now() + 86400000))
                        })
                        Chip(label = t("rm.nextWeek"), compact = true, onPress = {
                            dueDate = dateStr(dateOf(now() + 7 * 86400000))
                        })
                        Chip(label = timeChipLabel("09:00"), compact = true, onPress = { dueTime = "09:00" })
                        Chip(label = timeChipLabel("13:00"), compact = true, onPress = { dueTime = "13:00" })
                        Chip(label = timeChipLabel("18:00"), compact = true, onPress = { dueTime = "18:00" })
                        Chip(label = timeChipLabel("20:00"), compact = true, onPress = { dueTime = "20:00" })
                    }
                }
            }
            // live preview — due moment + how much time is left (v1.6.17)
            val at = parseDue()
            val left = at?.let { timeLeftStr(it, nowTick) }
            Text(
                when {
                    at == null -> "${t("rm.due")}: ${t("rm.validDateTime")}"
                    else -> "${t("rm.due")}: ${fdEpoch(at)} · ${ft(at)} · $left"
                },
                fontSize = F.small.sp,
                color = if (at != null && at < nowTick) C.red else C.brand,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
                        // v1.6.18 — ANY interval: “every 90 minutes”, “every 2
                        // weeks”, “every 6 months” … picking Custom reveals the
                        // number + unit row right below.
                        val customActive = repeat.startsWith("custom:")
                        Chip(
                            label = t("rm.customInterval"),
                            compact = true,
                            active = customActive,
                            onPress = {
                                repeat = if (customActive) "none" else "custom:${(customN.toIntOrNull() ?: 0).coerceAtLeast(1)}$customUnit"
                            },
                        )
                    }
                }
            }
            if (repeat.startsWith("custom:")) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(C.chip)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(t("rm.everyN"), fontSize = F.small.sp, color = C.sub)
                    val nField = Modifier.widthIn(max = 64.dp)
                    Field(
                        value = customN,
                        onChangeText = { v ->
                            customN = v.filter { it.isDigit() }.take(3)
                            val n = customN.toIntOrNull() ?: 0
                            if (n > 0) repeat = "custom:$n$customUnit"
                        },
                        placeholder = "2",
                        keyboardType = "number-pad",
                        modifier = nField,
                    )
                    ScrollRow {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "m" to t("rm.unitMinutes"),
                                "h" to t("rm.unitHours"),
                                "d" to t("rm.unitDays"),
                                "w" to t("rm.unitWeeks"),
                                "mo" to t("rm.unitMonths"),
                            ).forEach { (u, label) ->
                                Chip(label = label, compact = true, active = customUnit == u, onPress = {
                                    customUnit = u
                                    val n = customN.toIntOrNull() ?: 0
                                    if (n > 0) repeat = "custom:$n$u"
                                })
                            }
                        }
                    }
                }
                Text(
                    repeatLabelOf(repeat),
                    fontSize = F.tiny.sp, color = C.brand,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            // v1.6.18 — ALARM reminders: rings on the alarm stream (audible
            // over silent / muted media) instead of the regular notification.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isAlarm) C.brand.copy(alpha = 0.08f) else C.chip)
                    .clickable { isAlarm = !isAlarm }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MdiIcon("alarm-check-outline", 20, if (isAlarm) C.brand else C.sub)
                Column(Modifier.weight(1f)) {
                    Text(t("rm.alarm"), fontSize = F.body.sp, color = C.ink, fontWeight = FontWeight.SemiBold)
                    Text(t("rm.alarmHint"), fontSize = F.tiny.sp, color = C.sub)
                }
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (isAlarm) C.brand else androidx.compose.ui.graphics.Color.Transparent)
                        .border(1.5.dp, if (isAlarm) C.brand else C.line, androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isAlarm) {
                        Text("✓", color = C.onBrand, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
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
