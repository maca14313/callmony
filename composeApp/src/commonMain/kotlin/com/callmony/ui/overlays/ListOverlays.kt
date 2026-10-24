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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.callmony.lib.Messengers
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.lib.lineTotal
import com.callmony.lib.listShareText
import com.callmony.lib.listTotals
import com.callmony.lib.now
import com.callmony.lib.ownerNameOf
import com.callmony.lib.prettyPhone
import com.callmony.lib.r2
import com.callmony.lib.todayStr
import com.callmony.lib.uid
import com.callmony.model.CalcList
import com.callmony.model.ListDiscount
import com.callmony.model.ListItem
import com.callmony.model.Payment
import com.callmony.platform.Platform
import com.callmony.theme.F
import com.callmony.ui.components.PressableRow
import com.callmony.ui.components.Sheet
import com.callmony.ui.kit.Btn
import com.callmony.ui.kit.C
import com.callmony.ui.kit.Chip
import com.callmony.ui.kit.Confirm
import com.callmony.ui.kit.Field
import com.callmony.ui.kit.IconBtn
import com.callmony.ui.kit.MdiIcon
import com.callmony.ui.kit.Money
import com.callmony.ui.kit.Pill
import com.callmony.ui.kit.Toasts
import com.callmony.ui.components.ScrollRow

/* ListDetail + ListEdit. Port of src/overlays/ListOverlays.tsx. */

private val KIND_ICON = mapOf(
    "calculating" to "calculator-variant-outline",
    "simple" to "format-list-bulleted",
    "checklist" to "check-bold",
    "todo" to "clipboard-check-outline",
)

@Composable
fun ListDetailOverlay(listId: String) {
    val C = C()
    val pop = { UiStore.pop() }
    val list = AppStore.state.lists.find { it.id == listId }
    var menu by remember { mutableStateOf(false) }
    var totalsEdit by remember { mutableStateOf(false) }
    var expandedItem by remember { mutableStateOf<String?>(null) }
    var payItemFor by remember { mutableStateOf<String?>(null) }
    var groupSame by remember { mutableStateOf(false) }
    var movePicker by remember { mutableStateOf(false) }

    if (list == null) {
        Sheet(title = t("ls.listNotFound"), onBack = pop) {
            Text(t("ls.listNotFoundHint"), fontSize = F.body.sp, color = C.sub)
        }
        return
    }

    val totals = listTotals(list)
    val ownerPhone = if (list.ownerId.startsWith("c:"))
        AppStore.contactByRecord(list.ownerId.substring(2))?.phones?.firstOrNull()?.number
    else null

    fun duplicateNextPeriod() {
        val next = com.callmony.lib.nextOccurrence(now(), list.repeat)
        AppStore.saveList(
            list.copy(
                id = uid(),
                items = list.items.map { it.copy(payments = listOf(), checked = false) },
                createdAt = now(),
                updatedAt = now(),
            )
        )
        Toasts.toast(t("ls.duplicatedNext"))
    }

    Sheet(
        title = list.title,
        subtitle = "${t("ls.kind${list.kind.replaceFirstChar { it.uppercase() }}")} · ${ownerNameOf(list.ownerId)}",
        onBack = pop,
        headerRight = { IconBtn("dots-vertical", { menu = true }, size = 34) },
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(small = true, label = t("act.whatsapp"), icon = "whatsapp", onPress = {
                    if (ownerPhone != null) Messengers.waLink(ownerPhone, listShareText(list))
                    else Toasts.toast(t("cd.noPhone"), "error")
                })
                Btn(small = true, label = t("act.share"), icon = "share-variant-outline", variant = "secondary", onPress = {
                    Platform.share.shareText(listShareText(list), list.title)
                })
                Btn(small = true, label = t("ls.remind"), icon = "bell-outline", variant = "secondary", onPress = {
                    AppStore.saveReminder(
                        com.callmony.model.Reminder(
                            id = uid(), ownerId = list.ownerId,
                            title = "${t("ls.remind")} · ${list.title}",
                            kind = "payment",
                            dueAt = now() + 86400000,
                            createdAt = now(), updatedAt = now(),
                        )
                    )
                    Toasts.toast(t("ls.remindSet"))
                })
            }
        },
    ) {
        Column {
            // sub-header card
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(C.card)
                    .border(1.dp, C.line, RoundedCornerShape(16.dp))
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(ownerNameOf(list.ownerId), "brand", icon = "account-outline")
                    if (list.repeat != "none") Pill(t("rm.repeat${list.repeat.replaceFirstChar { it.uppercase() }}"), "neutral", icon = "repeat")
                    if (list.isTemplate) Pill(t("ls.templates"), "neutral")
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t("ls.countPending"), fontSize = F.small.sp, color = C.ink, modifier = Modifier.weight(1f))
                    EditorsSwitch(checked = list.postToLedger, onChange = { v ->
                        AppStore.saveList(list.copy(postToLedger = v, updatedAt = now()))
                    })
                }
                if (list.repeat != "none") {
                    Text(
                        t("ls.duplicatedNext"),
                        fontSize = F.tiny.sp, color = C.brand, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp).clickable { duplicateNextPeriod() },
                    )
                }
                list.note?.let {
                    if (it.isNotEmpty()) Text(it, fontSize = F.small.sp, color = C.sub, modifier = Modifier.padding(top = 6.dp))
                }
            }
            // totals card
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(C.brandSoft.copy(alpha = 0.5f))
                    .clickable { totalsEdit = true }
                    .padding(12.dp),
            ) {
                TotalRow(t("ls.subtotal"), totals.subtotal, C.sub)
                if (totals.listDiscount > 0.005) TotalRow("− ${t("ls.discount")}", -totals.listDiscount, C.brand)
                if (totals.listTax > 0.005) TotalRow("+ ${t("ls.taxPct")}", totals.listTax, C.brand)
                if (totals.extra > 0.005) TotalRow("+ ${t("ls.extraCharge")}", totals.extra, C.brand)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(t("ls.total"), fontSize = F.body.sp, fontWeight = FontWeight.ExtraBold, color = C.ink)
                    Money(totals.grand, size = 16, weight = FontWeight.ExtraBold)
                }
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(t("lg.paid"), fontSize = F.tiny.sp, color = C.sub)
                    Text(t("lg.pending"), fontSize = F.tiny.sp, color = C.amber)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Money(totals.paid, size = 14, weight = FontWeight.Bold)
                    Money(totals.pending, size = 14, weight = FontWeight.Bold)
                }
            }
            // group same items switch
            Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("ls.groupSameItems"), fontSize = F.small.sp, color = C.sub, modifier = Modifier.weight(1f))
                EditorsSwitch(checked = groupSame, onChange = { groupSame = it })
            }
            // items
            val displayItems = if (groupSame) {
                list.items.groupBy { "${it.name}|${it.unit ?: ""}" }.entries.sortedBy { it.key }
                    .map { (k, group) -> k to group }
            } else emptyList()

            if (groupSame) {
                displayItems.forEach { (key, group) ->
                    Column(Modifier.padding(bottom = 6.dp)) {
                        Text(
                            "Σ ${group.first().name} · ${group.size}",
                            fontSize = F.small.sp, fontWeight = FontWeight.Bold, color = C.brand,
                        )
                        group.forEach { CalcRow(it, {}) { AppStore.toggleItemChecked(listId, it.id) } }
                    }
                }
            } else {
                list.items.forEach { item ->
                    renderRowFull(item, expandedItem == item.id, {
                        expandedItem = if (expandedItem == item.id) null else item.id
                    }, {
                        AppStore.toggleItemChecked(listId, item.id)
                    }, {
                        payItemFor = item.id
                    }, listId)
                }
            }
            // add-item form
            AddItemForm(listId, isCalc = list.kind == "calculating")
            Spacer(Modifier.height(10.dp))
        }
    }

    // ⋯ menu
    if (menu) {
        com.callmony.ui.components.BottomSheet(title = list.title, onClose = { menu = false }) {
            Column {
                listOf(
                    Triple("pencil-outline", t("ls.renameEdit"), "edit"),
                    Triple("content-duplicate", t("act.duplicate"), "dup"),
                    Triple("content-save-edit-outline", t("ls.saveAsTemplate"), "tpl"),
                    Triple("check-all", t("ls.markAllPaid"), "paid"),
                    Triple("broom", t("ls.clearPayments"), "clear"),
                    Triple("trash-can-outline", t("act.delete"), "del"),
                ).forEach { (icon, label, key) ->
                    PressableRow(onClick = {
                        menu = false
                        when (key) {
                            "edit" -> UiStore.open(Overlay.ListEdit(list.id, list.ownerId))
                            "dup" -> AppStore.duplicateList(list.id)
                            "tpl" -> { AppStore.duplicateList(list.id, asTemplate = true); Toasts.toast(t("ls.savedAsTemplate")) }
                            "paid" -> { AppStore.markAllPaid(list.id); Toasts.toast(t("ls.allPaid")) }
                            "clear" -> { AppStore.clearPaid(list.id); Toasts.toast(t("ls.paymentsCleared")) }
                            "del" -> Confirm.ask(t("ls.deleteListTitle"), t("ls.deleteListMsg"), t("act.delete"), danger = true) { ok ->
                                if (ok) { AppStore.deleteList(list.id); Toasts.toastUndo(); pop() }
                            }
                        }
                    }) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MdiIcon(icon, 20, if (key == "del") C.red else C.ink)
                            Text(label, fontSize = 16.sp, color = if (key == "del") C.red else C.ink)
                        }
                    }
                }
            }
        }
    }

    // totals editor
    if (totalsEdit) {
        var dType by remember { mutableStateOf(list.discount?.type ?: "pct") }
        var dValue by remember { mutableStateOf(list.discount?.value?.toString() ?: "") }
        var taxPct by remember { mutableStateOf(list.taxPct?.toString() ?: "") }
        var extra by remember { mutableStateOf(list.extraCharge?.toString() ?: "") }
        com.callmony.ui.components.BottomSheet(title = t("ls.totalsCharges"), onClose = { totalsEdit = false }) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    Chip(label = "%", active = dType == "pct", onPress = { dType = "pct" })
                    Chip(label = t("lg.amount"), active = dType == "amount", onPress = { dType = "amount" })
                }
                Field(label = t("ls.listDiscount"), value = dValue, onChangeText = { dValue = it }, keyboardType = "decimal-pad", placeholder = "0")
                Field(label = t("ls.taxPercent"), value = taxPct, onChangeText = { taxPct = it }, keyboardType = "decimal-pad", placeholder = "0")
                Field(label = t("ls.extraCharge"), value = extra, onChangeText = { extra = it }, keyboardType = "decimal-pad", placeholder = "0")
                Btn(label = t("act.save"), onPress = {
                    AppStore.saveList(
                        list.copy(
                            discount = dValue.toDoubleOrNull()?.takeIf { it > 0 }?.let { ListDiscount(dType, it) },
                            taxPct = taxPct.toDoubleOrNull(),
                            extraCharge = extra.toDoubleOrNull(),
                            updatedAt = now(),
                        )
                    )
                    totalsEdit = false
                })
            }
        }
    }

    // row payment sheet
    payItemFor?.let { itemId ->
        val item = list.items.find { it.id == itemId }
        if (item != null) {
            var amount by remember { mutableStateOf("") }
            var payDate by remember { mutableStateOf(todayStr()) }
            var method by remember { mutableStateOf("cash") }
            com.callmony.ui.components.BottomSheet(title = t("lg.addPayment"), onClose = { payItemFor = null }) {
                Column {
                    Field(label = t("lg.paymentAmount"), value = amount, onChangeText = { amount = it }, keyboardType = "decimal-pad", placeholder = "0")
                    val remaining = r2(lineTotal(item) - item.payments.sumOf { it.amount })
                    if (remaining > 0.005) {
                        Row(Modifier.padding(bottom = 12.dp)) {
                            Btn(small = true, label = "${t("lg.fillRemaining")}: $remaining", variant = "soft", onPress = { amount = remaining.toString() })
                        }
                    }
                    Field(label = t("lg.paymentDate"), value = payDate, onChangeText = { payDate = it }, placeholder = "YYYY-MM-DD")
                    Btn(label = t("act.save"), onPress = {
                        val v = amount.replace(",", ".").toDoubleOrNull()
                        if (v == null || v <= 0) return@Btn
                        AppStore.addItemPayment(list.id, itemId, Payment(id = uid(), amount = v, date = payDate.ifEmpty { todayStr() }, method = method, createdAt = now()))
                        payItemFor = null
                        Toasts.toast(t("lg.paymentAdded"))
                    })
                }
            }
        }
    }

    // move owner picker
    if (movePicker) {
        com.callmony.ui.components.ContactPicker(
            title = t("ls.owner"),
            onPick = { c ->
                movePicker = false
                AppStore.moveEntity("list", list.id, com.callmony.model.contactOwnerId(c.recordID))
            },
            onBack = { movePicker = false },
        )
    }
}

@Composable
private fun TotalRow(label: String, value: Double, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = F.tiny.sp, color = color)
        Money(value, size = 13, weight = FontWeight.Bold, signed = value < 0)
    }
}

@Composable
private fun CalcRow(item: ListItem, onTap: () -> Unit, onTick: () -> Unit) {
    val C = C()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(C.card)
            .clickable { onTap() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .border(2.dp, if (item.checked) C.green else C.line, CircleShape)
                .background(if (item.checked) C.green else Color.Transparent)
                .clickable { onTick() },
            contentAlignment = Alignment.Center,
        ) { if (item.checked) MdiIcon("check", 15, Color.White) }
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                fontSize = F.body.sp,
                fontWeight = FontWeight.SemiBold,
                color = C.ink,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append("${item.qty}${item.unit?.let { " $it" } ?: ""} × ${item.unitPrice}")
                    item.date?.let { append(" · ${com.callmony.lib.fdShort(it)}") }
                    val paid = item.payments.sumOf { it.amount }
                    if (paid > 0) append(" · ${t("lg.paid")} ${com.callmony.lib.compactMoney(paid)}")
                },
                fontSize = F.tiny.sp, color = C.sub,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Money(lineTotal(item), size = 14, weight = FontWeight.Bold)
    }
}

@Composable
private fun renderRowFull(item: ListItem, expanded: Boolean, onTap: () -> Unit, onTick: () -> Unit, onPay: () -> Unit, listId: String) {
    val C = C()
    Column {
        CalcRow(item, onTap, onTick)
        if (expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.cardAlt.copy(alpha = 0.4f))
                    .padding(12.dp),
            ) {
                if (item.note?.isNotEmpty() == true) Text(item.note!!, fontSize = F.small.sp, color = C.sub, modifier = Modifier.padding(bottom = 6.dp))
                if (item.priority != null) Pill(t("ls.${item.priority}"), if (item.priority == "high") "red" else if (item.priority == "medium") "amber" else "neutral")
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Btn(small = true, label = t("lg.addPayment"), icon = "plus", variant = "soft", onPress = onPay)
                    Btn(small = true, label = t("act.remove"), icon = "trash-can-outline", variant = "danger", onPress = {
                        Confirm.ask(t("ls.removeTickTitle"), t("ls.removeTickMsg"), t("act.remove"), danger = true) { ok ->
                            if (ok) AppStore.removeListItem(listId, item.id)
                        }
                    })
                }
            }
        }
    }
}

/* add-item form */
@Composable
private fun AddItemForm(listId: String, isCalc: Boolean) {
    val C = C()
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(C.cardAlt.copy(alpha = 0.5f))
            .padding(12.dp),
    ) {
        Field(label = t("ls.itemName"), value = name, onChangeText = { name = it }, placeholder = t("lg.giveItemName"))
        if (isCalc) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(0.7f)) { Field(label = t("lg.qty"), value = qty, onChangeText = { qty = it }, keyboardType = "decimal-pad") }
                Box(Modifier.weight(0.8f)) { Field(label = t("lg.unit"), value = unit, onChangeText = { unit = it }) }
                Box(Modifier.weight(1.2f)) { Field(label = t("lg.unitPrice"), value = price, onChangeText = { price = it }, keyboardType = "decimal-pad") }
            }
        }
        Row(Modifier.padding(top = 6.dp)) {
            Btn(small = true, label = t("lg.addItem"), icon = "plus", onPress = {
                if (name.isBlank()) {
                    Toasts.toast(t("lg.giveItemName"), "error")
                    return@Btn
                }
                AppStore.addListItem(
                    listId,
                    ListItem(
                        id = uid(),
                        name = name.trim(),
                        qty = qty.replace(",", ".").toDoubleOrNull() ?: 1.0,
                        unit = unit.ifEmpty { null },
                        unitPrice = price.replace(",", ".").toDoubleOrNull() ?: 0.0,
                        sortOrder = 0,
                        createdAt = now(),
                        updatedAt = now(),
                    )
                )
                Toasts.toast(t("ls.itemAdded"))
                name = ""; qty = "1"; unit = ""; price = ""
            })
        }
    }
}

/* ---------------- ListEdit ---------------- */

@Composable
fun ListEditOverlay(listId: String?, ownerId: String, kind: String? = null) {
    val C = C()
    val pop = { UiStore.pop() }
    val existing = listId?.let { id -> AppStore.state.lists.find { it.id == id } }

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var kindSel by remember { mutableStateOf(existing?.kind ?: kind ?: "calculating") }
    var repeat by remember { mutableStateOf(existing?.repeat ?: "none") }
    var postToLedger by remember { mutableStateOf(existing?.postToLedger ?: false) }
    var template by remember { mutableStateOf(existing?.isTemplate ?: false) }

    Sheet(
        title = if (existing == null) t("ls.newList") else t("ls.renameEdit"),
        subtitle = ownerNameOf(ownerId),
        onBack = pop,
        footer = {
            Btn(
                label = t("act.save"),
                onPress = {
                    if (title.isBlank()) {
                        Toasts.toast(t("ls.giveListTitle"), "error")
                        return@Btn
                    }
                    AppStore.saveList(
                        CalcList(
                            id = existing?.id ?: uid(),
                            ownerId = ownerId,
                            kind = kindSel,
                            title = title.trim(),
                            note = note.ifEmpty { null },
                            isTemplate = template,
                            postToLedger = postToLedger,
                            repeat = repeat,
                            items = existing?.items ?: listOf(),
                            createdAt = existing?.createdAt ?: now(),
                            updatedAt = now(),
                        )
                    )
                    Toasts.toast(t("act.saved"))
                    pop()
                },
                modifier = Modifier.fillMaxWidth(),
                disabled = title.isBlank(),
            )
        },
    ) {
        Column {
            Field(label = t("ls.listTitle"), value = title, onChangeText = { title = it }, placeholder = t("ls.listPlaceholder"))
            Field(label = t("ls.listNote"), value = note, onChangeText = { note = it }, placeholder = t("ls.whatFor"), multiline = true)
            Text(t("ls.kindCalculating"), fontSize = F.small.sp, color = C.sub, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
            Row(Modifier.padding(bottom = 12.dp)) {
                ScrollRow {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("calculating", "simple", "checklist", "todo").forEach { k ->
                            Chip(label = t("ls.kind${k.replaceFirstChar { it.uppercase() }}"), compact = true, active = kindSel == k, onPress = { kindSel = k })
                        }
                    }
                }
            }
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
                        ).forEach { (k, label) -> Chip(label = label, compact = true, active = repeat == k, onPress = { repeat = k }) }
                    }
                }
            }
            Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("ls.countPending"), fontSize = F.small.sp, color = C.ink, modifier = Modifier.weight(1f))
                EditorsSwitch(checked = postToLedger, onChange = { postToLedger = it })
            }
            Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("ls.templates"), fontSize = F.small.sp, color = C.ink, modifier = Modifier.weight(1f))
                EditorsSwitch(checked = template, onChange = { template = it })
            }
            Text(t("ls.countPendingHint"), fontSize = F.tiny.sp, color = C.faint)
        }
    }
}
