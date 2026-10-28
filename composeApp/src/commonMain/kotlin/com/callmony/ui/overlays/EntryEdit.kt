package com.callmony.ui.overlays

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.lib.dateOf
import com.callmony.lib.money
import com.callmony.lib.now
import com.callmony.lib.parseExprNote
import com.callmony.lib.ownerNameOf
import com.callmony.lib.todayStr
import com.callmony.lib.uid
import com.callmony.platform.NativeDateTime
import com.callmony.model.ENTRY_CATEGORIES as CATS
import com.callmony.model.EntryItem
import com.callmony.model.LedgerEntry
import com.callmony.theme.F
import com.callmony.ui.components.Sheet
import com.callmony.ui.kit.Btn
import com.callmony.ui.kit.C
import com.callmony.ui.kit.Chip
import com.callmony.ui.kit.Confirm
import com.callmony.ui.kit.Field
import com.callmony.ui.kit.IconBtn
import com.callmony.ui.kit.MdiIcon
import com.callmony.ui.kit.focusRing
import com.callmony.ui.kit.pressVeil
import com.callmony.ui.kit.Toasts
import com.callmony.ui.components.ScrollRow
import com.callmony.ui.kit.Segmented

/* EntryEdit — the ledger entry editor: kind chips, hero amount, line items
   with math, date/method, categories (+custom), due date (auto reminder),
   note, owner, groups. Port of src/overlays/EntryEdit.tsx. */

private val KINDS = listOf(
    Triple("credit", "credit", "hand-coin-outline"),
    Triple("debit", "debit", "credit-card-outline"),
    Triple("credit", "income", "trending-up"),
    Triple("debit", "expense", "trending-down"),
)
private val QUICK_UNITS = listOf("pic", "kg", "pcs", "bag", "box", "l")

@Composable
fun EntryEditOverlay(
    entryId: String?,
    ownerId: String,
    entryType: String? = null,
    kind: String? = null,
    groupId: String? = null,
) {
    val C = C()
    val pop = { UiStore.pop() }
    val existing = entryId?.let { id -> AppStore.state.entries.find { it.id == id } }
    val groups = AppStore.state.groups.filter { it.ownerId == ownerId && it.deletedAt == null }

    var type by remember { mutableStateOf(existing?.type ?: entryType ?: "credit") }
    var kindSel by remember { mutableStateOf(existing?.kind ?: kind ?: "credit") }
    var amount by remember { mutableStateOf(existing?.amount?.let { trimNum(it) } ?: "") }
    var date by remember { mutableStateOf(existing?.date ?: todayStr()) }
    var dueDate by remember { mutableStateOf(existing?.dueDate ?: "") }
    var dueOn by remember { mutableStateOf(dueDate.isNotEmpty()) }
    var category by remember { mutableStateOf(existing?.category ?: "") }
    var method by remember { mutableStateOf(existing?.method ?: "cash") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var owner by remember { mutableStateOf(existing?.ownerId ?: ownerId) }
    var groupSel by remember { mutableStateOf(existing?.groupId ?: groupId) }
    var items by remember { mutableStateOf(existing?.items ?: listOf<EntryItem>()) }
    var editingItem by remember { mutableStateOf<EntryItem?>(null) }
    // v1.6.18 — attachments: lists / notes / reminders ride on entry.refs
    var refs by remember { mutableStateOf(existing?.refs ?: listOf<com.callmony.model.LinkRef>()) }
    // v1.6.34 — the ADD-MODE tabs state (Just amount / Items / Quick
    // item / Recent). An entry that already carries items opens on the
    // ITEMS tab; everything else opens on Just amount (the classic hero
    // field). The v1.6.18 showItemForm collapse is superseded: the tabs
    // ARE the add flow, and only the active tab renders an input form.
    // v1.6.35 — an entry saved from the annotated calculator reopens on
    // the CALCULATOR tab so its expression note is right there to edit.
    var amountMode by remember {
        mutableStateOf(
            if (existing?.exprNote != null) "calc"
            else if (items.isNotEmpty()) "items"
            else "amount"
        )
    }
    var quickLabel by remember { mutableStateOf("") }
    var quickPrice by remember { mutableStateOf("") }
    // v1.6.35 — the EXPRESSION-NOTE tab (the annotated calculator): the
    // raw text round-trips on entry.exprNote — prefilled when the entry
    // already carries one — and every keystroke re-applies the parsed
    // total to the amount state, so what the live preview shows is
    // exactly what Save writes.
    var calcText by remember { mutableStateOf(existing?.exprNote ?: "") }
    // v1.6.35 — amount ownership: false until the USER types an amount
    // (the hero field or the calc tab); items then stop mirroring into
    // the amount. Existing entries start true — their saved amount is
    // final from the first frame.
    var amountTouched by remember { mutableStateOf(existing != null) }
    var catManager by remember { mutableStateOf(false) }
    var showOwnerPicker by remember { mutableStateOf(false) }

    val itemsSum = items.sumOf { it.qty * it.price }
    val amountNum = amount.replace(",", ".").toDoubleOrNull()
    // v1.6.35 — THE AMOUNT IS ITS OWN (user spec: "the amount also be
    // just like Items (qty × price) and Quick item by its own, not the
    // auto calculated from {n} items"). The hero amount IS the entry
    // amount; items are a breakdown that never overrides it. On a NEW
    // entry the amount field still MIRRORS the items total as a plain
    // editable convenience until the user types their own value — after
    // that it is 100% the user's.
    val effAmount = amountNum ?: 0.0

    fun save() {
        if (effAmount <= 0) {
            Toasts.toast(t("lg.amountGreaterThanZero"), "error")
            return
        }
        val e = LedgerEntry(
            id = existing?.id ?: uid(),
            ownerId = owner,
            type = type,
            kind = kindSel,
            amount = effAmount,
            items = items,
            payments = existing?.payments ?: listOf(),
            date = date.ifEmpty { todayStr() },
            dueDate = dueOn.takeIf { it }?.let { dueDate.ifEmpty { null } },
            category = category.ifEmpty { null },
            method = method,
            note = note.ifEmpty { null },
            // v1.6.35 — the annotated calculation rides along, editable
            // on reopen (the CALCULATOR tab prefills from it)
            exprNote = calcText.trim().ifEmpty { null },
            groupId = groupSel,
            refs = refs,
            createdAt = existing?.createdAt ?: now(),
            updatedAt = now(),
        )
        AppStore.saveEntry(e)
        // auto payment-reminder at 09:00 on the due date (new entries only)
        if (existing == null && dueOn && dueDate.length >= 8) {
            val d = com.callmony.lib.dateOf(now())
            val parts = dueDate.split("-")
            val at = try {
                com.callmony.lib.KmpDate(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 9, 0, 0).time()
            } catch (_: Exception) { d.time() }
            if (at > now()) {
                AppStore.saveReminder(
                    com.callmony.model.Reminder(
                        id = uid(), ownerId = owner,
                        title = t("rm.kindPayment") + " · " + ownerNameOf(owner),
                        kind = "payment", dueAt = at, createdAt = now(), updatedAt = now(),
                    )
                )
            }
        }
        Toasts.toast(t("act.saved"))
        pop()
    }

    /* v1.6.35 — the amount field mirrors the items total ONLY while the
       user hasn't typed their own amount (new entries). One plain value
       sitting in the field — editable, overridable, never a live
       override from the {n} items. Declared before its callers (Kotlin
       local functions resolve top-down). */
    fun mirrorAmount(newItems: List<EntryItem>) {
        if (amountTouched) return
        amount = if (newItems.isEmpty()) "" else trimNum(newItems.sumOf { it.qty * it.price })
    }

    /* v1.6.34 — the QUICK-ITEM tab's ＋ (the v1.6.27/29 ledger row, now
       inside the add-item flow): label + price → a single qty-1 line item
       on THIS entry. The user can keep adding or hit Save — the entry
       amount follows the item total, exactly like the ledger row did. */
    fun addQuickItem() {
        val label = quickLabel.trim()
        val price = quickPrice.toDoubleOrNull()
        if (label.isEmpty()) {
            Toasts.toast(t("lg.quickItemNeedLabel"), "error")
            return
        }
        if (price == null || price <= 0) {
            Toasts.toast(t("lg.amountGreaterThanZero"), "error")
            return
        }
        val n = items + EntryItem(id = uid(), name = label, qty = 1.0, price = price)
        items = n
        mirrorAmount(n)
        quickLabel = ""
        quickPrice = ""
    }

    Sheet(
        title = if (existing == null) t("lg.newEntry") else t("lg.editEntry"),
        subtitle = ownerNameOf(ownerId),
        onBack = pop,
        // v1.6.35 — PINNED TABS (user spec): the Sheet's own scroll is OFF;
        // the kind chips + the add-mode tabs render once, pinned at the
        // top, and ONLY the form below carries a verticalScroll (never a
        // scrollable inside a scrollable — the v1.6.25 More-sheet lesson).
        scrollable = false,
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (existing != null) {
                    Btn(
                        label = t("act.delete"), variant = "danger",
                        onPress = {
                            Confirm.ask(t("lg.deleteEntryTitle"), t("lg.deleteEntryMsg"), t("act.delete"), danger = true) { ok ->
                                if (ok) { AppStore.deleteEntry(existing.id); Toasts.toastUndo(); pop() }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Btn(label = t("act.save"), onPress = { save() }, modifier = Modifier.weight(1f), disabled = effAmount <= 0)
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            // v1.6.35 — THE PINNED BAND: kind chips + add-mode tabs. These
            // rows NEVER scroll with the form below them (user spec), and
            // both are COMPACT — especially the kind row.
            Column(Modifier.padding(horizontal = 18.dp)) {
            // kind chips — compact (v1.6.35: "make them compact, especially
            // the above one")
            Row(Modifier.padding(top = 6.dp, bottom = 6.dp)) {
                ScrollRow {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        KINDS.forEach { (ty, kd, icon) ->
                            val label = t("lg.kind${kd.replaceFirstChar { it.uppercase() }}")
                            Chip(label = label, icon = icon, active = kindSel == kd, compact = true, onPress = { kindSel = kd; type = ty })
                        }
                    }
                }
            }
            // v1.6.34 — THE ADD-MODE TABS (user report: the quick item —
            // label + price — should be inside ADD ITEM, as horizontally
            // scrolling tabs: Items (qty × price) / Quick item / Just
            // amount / other useful types). The amount + items block is
            // now ONE flow behind a side-scrolling tab strip: pick HOW you
            // add — a plain number, full line items, a one-line label +
            // price, or a one-tap REUSE of an item booked before. Whatever
            // lands is the same EntryItem list (the entry amount follows
            // its total), so Save/effAmount logic is untouched. The
            // v1.6.27/29 quick row from the ledger view moved HERE (its
            // kind follows this editor's own kind chips).
            Row(Modifier.padding(bottom = 8.dp)) {
                ScrollRow {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // v1.6.35 — CALCULATOR / EXPRESSION NOTE tab chip, FIRST in the
            // strip (user spec: "add a new tab chip at the far left, before
            // Just amount") — the annotated calculator.
                        Chip(compact = true, icon = "calculator-variant", label = t("ee.tabCalc"), active = amountMode == "calc", onPress = { amountMode = "calc" })
                        Chip(compact = true, icon = "cash-check", label = t("ee.tabJustAmount"), active = amountMode == "amount", onPress = { amountMode = "amount" })
                        Chip(compact = true, icon = "format-list-bulleted", label = t("ee.tabItems"), active = amountMode == "items", onPress = { amountMode = "items" })
                        Chip(compact = true, icon = "clock-fast", label = t("ee.tabQuick"), active = amountMode == "quick", onPress = { amountMode = "quick" })
                        Chip(compact = true, icon = "history", label = t("ee.tabRecent"), active = amountMode == "recent", onPress = { amountMode = "recent" })
                    }
                }
            }
            } // end of the pinned band — the rows above stay fixed while
              // everything below scrolls (user spec)
            // v1.6.35 — THE FORM: the sheet's only scrollable. Same 18dp
            // gutters as before, so nothing visibly moves.
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(top = 2.dp, bottom = 24.dp),
            ) {
            when (amountMode) {
                // CALCULATOR / EXPRESSION NOTE (v1.6.35, the annotated
                // calculator) — free-form smart text area: the user writes
                // the math as a sentence with labels before or after every
                // number ("ball 100 x 2 price + 300 transport = 500"), the
                // parser picks out the arithmetic live, and the result card
                // shows exactly what it found ("100 × 2 + 300" = 500 Br).
                // A REAL editable field, so native copy/paste/cut work —
                // and every keystroke re-applies the parsed total to the
                // amount state, so what the preview shows is what saves.
                "calc" -> {
                    val parsed = remember(calcText) { parseExprNote(calcText) }
                    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                // v1.6.29 input press feedback + v1.6.31 active ring
                                .pressVeil(corner = 18.dp)
                                .focusRing(corner = 18.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(C.brand.copy(alpha = 0.08f))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(t("ee.calcLabel"), fontSize = F.small.sp, color = C.sub, fontWeight = FontWeight.SemiBold)
                            androidx.compose.foundation.text.BasicTextField(
                                value = calcText,
                                onValueChange = { raw ->
                                    calcText = raw.take(400)
                                    val p = parseExprNote(calcText)
                                    // the calc tab OWNS the amount while it is
                                    // being edited: what the preview shows is
                                    // exactly what Save writes
                                    amount = if (p.ok) trimNum(p.value ?: 0.0) else ""
                                    // v1.6.35 — the calc total is a typed
                                    // value: items never overwrite it
                                    amountTouched = true
                                },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = C.ink,
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(C.brand),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .heightIn(min = 76.dp),
                                decorationBox = { inner ->
                                    if (calcText.isEmpty()) {
                                        androidx.compose.foundation.layout.Row {
                                            Text(t("ee.calcPlaceholder"), fontSize = 15.sp, color = C.faint)
                                        }
                                        androidx.compose.foundation.layout.Box {}
                                    } else inner()
                                },
                            )
                        }
                        // the live extraction — the math it found + the running
                        // total, so a wrong guess is visible the moment it happens
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(C.card)
                                .border(1.dp, C.line, RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (parsed.ok) parsed.expr else t("ee.calcEmpty"),
                                    fontSize = F.small.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (parsed.ok) C.sub else C.faint,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    t("ee.calcHint"),
                                    fontSize = F.tiny.sp,
                                    color = C.faint,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                if (parsed.ok) money(parsed.value ?: 0.0) else "—",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = C.brand,
                            )
                        }
                    }
                }
                // JUST AMOUNT — the hero amount exactly as it always was
                // (v1.6.13 real decimal-pad fix; v1.6.29 press veil; v1.6.31
                // active ring; comma tolerated like the RN hero field).
                "amount" -> Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        // v1.6.29 — INPUT PRESS FEEDBACK on the hero amount too
                        .pressVeil(corner = 18.dp)
                        // v1.6.31 — ring colour while the amount field is active
                        .focusRing(corner = 18.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(C.brand.copy(alpha = 0.08f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                Column(Modifier.weight(1f)) {
                    Text(t("lg.amount"), fontSize = F.small.sp, color = C.sub, fontWeight = FontWeight.SemiBold)
                    androidx.compose.foundation.text.BasicTextField(
                        value = amount,
                        onValueChange = { raw ->
                            // digits, one dot/comma, keep it clean
                            val cleaned = raw
                                .replace(',', '.')
                                .filter { it.isDigit() || it == '.' }
                                .let { s ->
                                    val firstDot = s.indexOf('.')
                                    if (firstDot >= 0) s.substring(0, firstDot + 1) + s.substring(firstDot + 1).replace(".", "") else s
                                }
                                .take(14)
                            amount = cleaned
                            // v1.6.35 — a typed amount is THE amount; items
                            // stop mirroring into the field from here on
                            amountTouched = true
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = C.ink,
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                        ),
                        singleLine = true,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(C.brand),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        decorationBox = { inner ->
                            if (amount.isEmpty()) {
                                androidx.compose.foundation.layout.Row {
                                    Text("0", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = C.faint)
                                }
                                androidx.compose.foundation.layout.Box {}
                            } else inner()
                        },
                    )
                    if (amountNum != null) {
                        Text(money(amountNum), fontSize = F.tiny.sp, color = C.faint)
                    }
                }
                    if (items.isEmpty()) {
                        IconBtn("calculator-variant", { UiStore.openCalculator { v -> amount = trimNum(v) } }, size = 40)
                    }
                }
                // ITEMS — the full line-item editor (name · qty × price);
                // tapping an existing row below switches here for editing
                "items" -> ItemEditor(
                    onAdd = { item -> val n = items + item; items = n; mirrorAmount(n) },
                    onUpdate = { item -> val n = items.map { if (it.id == item.id) item else it }; items = n; mirrorAmount(n) },
                    editing = editingItem,
                    onEditDone = { editingItem = null },
                )
                // QUICK ITEM — label + price, one ＋ (v1.6.27's row, now
                // inside the add flow exactly as the user asked)
                "quick" -> Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.weight(1f)) {
                        Field(
                            value = quickLabel,
                            onChangeText = { quickLabel = it },
                            placeholder = t("lg.quickItemLabel"),
                            onSubmit = { addQuickItem() },
                        )
                    }
                    Box(Modifier.width(110.dp)) {
                        Field(
                            value = quickPrice,
                            onChangeText = { raw ->
                                quickPrice = raw.replace(',', '.')
                                    .filter { it.isDigit() || it == '.' }
                                    .let { s ->
                                        val firstDot = s.indexOf('.')
                                        if (firstDot >= 0) s.substring(0, firstDot + 1) + s.substring(firstDot + 1).replace(".", "") else s
                                    }
                                    .take(12)
                            },
                            placeholder = t("lg.quickItemPrice"),
                            keyboardType = "decimal-pad",
                            onSubmit = { addQuickItem() },
                        )
                    }
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(C.brand)
                            .clickable { addQuickItem() },
                        contentAlignment = Alignment.Center,
                    ) { MdiIcon("plus", 22, C.onBrand) }
                }
                // RECENT — one-tap reuse of items booked before (the
                // "other useful type": repeat bookings never re-typed)
                "recent" -> {
                    val recents = remember(AppStore.state.entries) { recentItems(AppStore.state.entries) }
                    if (recents.isEmpty()) {
                        Text(t("ee.noRecent"), fontSize = F.small.sp, color = C.faint, modifier = Modifier.padding(bottom = 10.dp))
                    } else {
                        Row(Modifier.padding(bottom = 10.dp)) {
                            ScrollRow {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    recents.forEach { (name, price) ->
                                        Chip(
                                            compact = true,
                                            label = if (price > 0) "$name · ${trimNum(price)}" else name,
                                            onPress = {
                                                val n = items + EntryItem(id = uid(), name = name, qty = 1.0, price = price)
                                                items = n
                                                mirrorAmount(n)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // line items — always visible no matter the tab, so whatever
            // the input method, the booked list + its total stay in view
            if (items.isNotEmpty()) {
                Text(t("lg.itemsTitle"), fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C.sub, modifier = Modifier.padding(bottom = 6.dp))
            }
            items.forEach { it ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(C.card)
                        .clickable { editingItem = it; amountMode = "items" }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${it.name} · ${it.qty}${it.unit?.let { u -> " $u" } ?: ""} × ${it.price} = ${it.qty * it.price}",
                        fontSize = F.small.sp, color = C.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    IconBtn("close", { val n = items.filter { x -> x.id != it.id }; items = n; mirrorAmount(n) }, size = 28, danger = true)
                }
            }
            // v1.6.34 — the old collapsed “＋ Add items” row is GONE: the
            // tab strip above IS the add flow now (an existing entry
            // starts on the amount tab, so the v1.6.18 double-save-looking
            // forms can never stack).
            if (items.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${t("lg.total")} · ${items.size}", fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C.sub)
                    Text(money(itemsSum), fontSize = F.body.sp, fontWeight = FontWeight.ExtraBold, color = C.brand)
                }
            } else {
                Spacer(Modifier.height(10.dp))
            }
            // date + method — date via the native Material calendar (v1.6.13).
            // v1.6.17: PickerField on both sides — identical "label above +
            // 48 dp box" metrics so the pair aligns (the old label-inside
            // date box sat shorter than the Field beside it).
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    com.callmony.ui.kit.PickerField(
                        label = t("lg.date"),
                        value = com.callmony.lib.fd(date),
                        icon = "calendar-blank-outline",
                        onPress = {
                            val initial = try {
                                com.callmony.lib.KmpDate(
                                    date.split("-").getOrNull(0)?.toInt() ?: dateOf(now()).year,
                                    (date.split("-").getOrNull(1)?.toIntOrNull() ?: (dateOf(now()).month0 + 1)) - 1,
                                    date.split("-").getOrNull(2)?.toIntOrNull() ?: dateOf(now()).day,
                                    12, 0, 0,
                                ).time()
                            } catch (_: Exception) { now() }
                            NativeDateTime.pickDate(
                                epochMillis = initial,
                                onPick = { epoch ->
                                    val d = dateOf(epoch)
                                    date = "${d.year}-${(d.month0 + 1).toString().padStart(2, '0')}-${d.day.toString().padStart(2, '0')}"
                                },
                            )
                        },
                    )
                }
                Box(Modifier.weight(1f)) { Field(label = t("lg.method"), value = method, onChangeText = { method = it }) }
            }
            // categories
            Text(t("cat.category"), fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C.sub, modifier = Modifier.padding(bottom = 6.dp))
            Row(Modifier.padding(bottom = 10.dp)) {
                ScrollRow {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (listOf("") + CATS + AppStore.state.settings.customCategories).forEach { cat ->
                            Chip(
                                label = cat.ifEmpty { t("cat.category") },
                                active = category == cat,
                                compact = true,
                                onPress = { category = cat },
                            )
                        }
                        Chip(label = "＋ ${t("cat.custom")}", compact = true, onPress = { catManager = true })
                    }
                }
            }
            // due date
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip(label = t("lg.dueDate"), icon = "calendar-blank-outline", active = dueOn, onPress = {
                    dueOn = !dueOn
                    if (dueOn && dueDate.isEmpty()) dueDate = todayStr()
                })
                if (dueOn) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, C.line, RoundedCornerShape(14.dp))
                            .background(C.inputBg, RoundedCornerShape(14.dp))
                            .clickable {
                                val initial = try {
                                    val p = dueDate.split("-")
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
                                        dueDate = "${d.year}-${(d.month0 + 1).toString().padStart(2, '0')}-${d.day.toString().padStart(2, '0')}"
                                    },
                                )
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MdiIcon("calendar-blank-outline", 16, C.brand)
                            Text(com.callmony.lib.fd(dueDate), fontSize = F.body.sp, color = C.ink, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            // note
            Field(label = t("lg.note"), value = note, onChangeText = { note = it }, placeholder = t("lg.optionalNote"), multiline = true)
            // v1.6.18 — ATTACHMENTS: any list, note or reminder can ride on
            // this credit / debit / income / expense (entry.refs, LinkRef).
            Text(t("ee.attached"), fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C.sub, modifier = Modifier.padding(bottom = 6.dp))
            if (refs.isEmpty()) {
                Text(t("ee.noAttachments"), fontSize = F.tiny.sp, color = C.faint, modifier = Modifier.padding(bottom = 8.dp))
            } else {
                ScrollRow(Modifier.padding(bottom = 8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        refs.forEach { ref ->
                            AttachedChip(ref = ref, onDetach = { refs = refs.filter { it.id != ref.id || it.type != ref.type } })
                        }
                    }
                }
            }
            Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(label = "＋ ${t("att.attach")}", icon = "link-plus", compact = true, onPress = {
                    com.callmony.ui.kit.HostSheets.push { close ->
                        AttachmentPicker(refs = refs, onChange = { refs = it }, onClose = close)
                    }
                })
            }
            // owner row
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.card)
                    .clickable { showOwnerPicker = true }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MdiIcon("swap-horizontal", 18, C.brand)
                Text(t("ee.owner"), fontSize = F.small.sp, color = C.sub)
                Text(ownerNameOf(owner), fontSize = F.body.sp, color = C.ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                MdiIcon("chevron-right", 18, C.faint)
            }
            // groups
            if (groups.isNotEmpty() || groupSel != null) {
                Text(t("ee.selectGroup"), fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C.sub, modifier = Modifier.padding(bottom = 6.dp))
                Row {
                    ScrollRow {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip(label = t("ee.noGroup"), active = groupSel == null, compact = true, onPress = { groupSel = null })
                            groups.forEach { g ->
                                Chip(label = g.title, active = groupSel == g.id, compact = true, onPress = { groupSel = g.id })
                            }
                        }
                    }
                }
            }
            } // end of the scrolling form (v1.6.35 pinned-tabs split)
        }
    }

    // owner picker — inline ContactPicker + General option
    if (showOwnerPicker) {
        com.callmony.ui.components.ContactPicker(
            title = t("ee.owner"),
            onPick = { c ->
                showOwnerPicker = false
                owner = com.callmony.model.contactOwnerId(c.recordID)
            },
            onBack = { showOwnerPicker = false },
        )
    }

    // inline category manager
    if (catManager) {
        var newCat by remember { mutableStateOf("") }
        com.callmony.ui.components.BottomSheet(title = t("cat.custom"), onClose = { catManager = false }) {
            Column {
                AppStore.state.settings.customCategories.forEach { cat ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(cat, fontSize = F.body.sp, color = C.ink, modifier = Modifier.weight(1f))
                        IconBtn("close", { AppStore.removeCustomCategory(cat) }, size = 30, danger = true)
                    }
                }
                Field(label = t("cat.categoryName"), value = newCat, onChangeText = { newCat = it })
                Btn(label = t("act.add"), onPress = {
                    if (newCat.isNotBlank()) {
                        AppStore.addCustomCategory(newCat.trim())
                        newCat = ""
                    }
                })
            }
        }
    }
}

private fun trimNum(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

/* v1.6.34 — the RECENT tab's pool: distinct item labels (with the last
   price they were booked at) across every entry, newest entry first —
   the one-tap repeat-booking list. Pure function, harness-testable. */
internal fun recentItems(entries: List<com.callmony.model.LedgerEntry>): List<Pair<String, Double>> {
    val seen = HashSet<String>()
    val out = mutableListOf<Pair<String, Double>>()
    entries
        .sortedByDescending { it.updatedAt }
        .forEach { e ->
            e.items.forEach { item ->
                val name = item.name.trim()
                if (name.isNotEmpty() && seen.add(name.lowercase())) {
                    out.add(name to item.price)
                }
            }
        }
    return out.take(14)
}

/* line item add/edit form */
@Composable
private fun ItemEditor(
    onAdd: (EntryItem) -> Unit,
    onUpdate: (EntryItem) -> Unit,
    editing: EntryItem?,
    onEditDone: () -> Unit,
) {
    val C = C()
    var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
    var qty by remember(editing) { mutableStateOf(editing?.qty?.let { trimNum(it) } ?: "1") }
    var unit by remember(editing) { mutableStateOf(editing?.unit ?: "") }
    var price by remember(editing) { mutableStateOf(editing?.price?.let { trimNum(it) } ?: "") }

    if (editing != null) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(C.brandSoft)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t("act.edit"), fontSize = F.small.sp, color = C.brand, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(t("act.cancel"), fontSize = F.small.sp, color = C.sub, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                onEditDone()
                name = ""; qty = "1"; unit = ""; price = ""
            })
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(C.cardAlt.copy(alpha = 0.5f))
            .padding(12.dp),
    ) {
        Field(label = t("lg.itemName"), value = name, onChangeText = { name = it }, placeholder = t("lg.itemNamePh"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(0.7f)) { Field(label = t("lg.qty"), value = qty, onChangeText = { qty = it }, keyboardType = "decimal-pad", placeholder = "1") }
            Box(Modifier.weight(0.9f)) { Field(label = t("lg.unit"), value = unit, onChangeText = { unit = it }, placeholder = t("lg.unitPh")) }
            Box(Modifier.weight(1.2f)) { Field(label = t("lg.unitPrice"), value = price, onChangeText = { price = it }, keyboardType = "decimal-pad", placeholder = "0") }
        }
        Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ScrollRow {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QUICK_UNITS.forEach { u ->
                        Chip(label = u, compact = true, active = unit == u, onPress = { unit = if (unit == u) "" else u })
                    }
                }
            }
        }
        Row(Modifier.padding(top = 8.dp)) {
            Btn(
                small = true, label = if (editing == null) t("lg.addItem") else t("lg.saveItem"), icon = "plus",
                onPress = {
                    val q = qty.replace(",", ".").toDoubleOrNull() ?: 1.0
                    val p = price.replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (name.isBlank() || p <= 0) {
                        Toasts.toast(t("lg.itemIncomplete"), "error")
                        return@Btn
                    }
                    val item = EntryItem(id = editing?.id ?: uid(), name = name.trim(), qty = q, unit = unit.ifEmpty { null }, price = p)
                    if (editing == null) onAdd(item) else onUpdate(item)
                    onEditDone()
                    name = ""; qty = "1"; unit = ""; price = ""
                },
            )
        }
    }
}

/* ---------------- v1.6.18 attachments ---------------- */

private val REF_ICON = mapOf(
    "list" to "clipboard-list-outline",
    "note" to "notebook-outline",
    "reminder" to "bell-outline",
)

/** A single attached entity chip with a detach ✕ (used in the entry editor). */
@Composable
private fun AttachedChip(ref: com.callmony.model.LinkRef, onDetach: () -> Unit) {
    val C = C()
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(C.brand.copy(alpha = 0.08f))
            .padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MdiIcon(REF_ICON[ref.type] ?: "link-variant", 15, C.brand)
        Text(ref.label ?: ref.id, fontSize = F.small.sp, color = C.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconBtn("close", onDetach, size = 26, danger = true)
    }
}

/**
 * v1.6.18 — the Attach picker: one sheet, three tabs (Lists / Notes /
 * Reminders), live from the store. Tapping a row attaches or detaches;
 * the ＋ quick-create row at the top opens the corresponding editor on
 * top of this sheet (the new entity shows up here as soon as it exists).
 */
@Composable
fun AttachmentPicker(refs: List<com.callmony.model.LinkRef>, onChange: (List<com.callmony.model.LinkRef>) -> Unit, onClose: () -> Unit) {
    val C = C()
    var tab by remember { mutableStateOf("list") }
    com.callmony.ui.components.BottomSheet(title = t("att.attachExisting"), onClose = onClose) {
        Column {
            Segmented(
                listOf(
                    Triple("list", t("misc.list"), null),
                    Triple("note", t("misc.note"), null),
                    Triple("reminder", t("misc.reminder"), null),
                ),
                tab,
            ) { tab = it }
            Spacer(Modifier.height(10.dp))
            val pool: List<Triple<String, String, String>> = when (tab) {
                "list" -> AppStore.state.lists
                    .filter { it.deletedAt == null }
                    .map { Triple(it.id, it.title.ifEmpty { t("ls.untitled") }, "list") }
                "note" -> AppStore.state.notes
                    .filter { it.deletedAt == null }
                    .map { Triple(it.id, it.title.ifEmpty { t("nt.untitled") }, "note") }
                else -> AppStore.state.reminders
                    .filter { it.deletedAt == null }
                    .map { Triple(it.id, it.title.ifEmpty { t("rm.kindCustom") }, "reminder") }
            }
            if (pool.isEmpty()) {
                Text(t("ee.noAttachments"), fontSize = F.tiny.sp, color = C.faint, modifier = Modifier.padding(bottom = 8.dp))
            } else {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(pool.size, key = { pool[it].first }) { i ->
                        val (id, label, ty) = pool[i]
                        val attached = refs.any { it.id == id && it.type == ty }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (attached) C.brand.copy(alpha = 0.08f) else Color.Transparent)
                                .clickable {
                                    onChange(
                                        if (attached) refs.filter { !(it.id == id && it.type == ty) }
                                        else refs + com.callmony.model.LinkRef(type = ty, id = id, label = label)
                                    )
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MdiIcon(REF_ICON[ty] ?: "link-variant", 18, if (attached) C.brand else C.sub)
                            Text(label, fontSize = F.body.sp, color = C.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (attached) MdiIcon("check-circle", 20, C.green)
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 10.dp)) {
                Btn(small = true, label = t("act.close"), variant = "secondary", onPress = onClose)
            }
        }
    }
}

