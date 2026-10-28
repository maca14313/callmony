package com.callmony.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.CalcNote
import com.callmony.lib.CalcToken
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.lib.calcLedgerTotal
import com.callmony.lib.calcSteps
import com.callmony.lib.dateOf
import com.callmony.lib.evalCalcTokens
import com.callmony.lib.money
import com.callmony.lib.now
import com.callmony.lib.parseCalcLedger
import com.callmony.lib.r2
import com.callmony.lib.serializeCalcLedger
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
import com.callmony.ui.kit.vibrate
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    // v1.6.37 — THE SUM MODEL (user report: "i click just amount and type
    // 100, then the items… ball 1 pc * 200 = 200, so the result should be
    // 100 + 200 = 300 but it is not doing this, but it should be like this
    // for the others too"): the amount field holds the user's BASE; the
    // entry's final amount is base + every line item + the calculator's
    // ledger total (Items, Quick item, Recent and the annotated calculator
    // all land in the same sum). On reopen the base is DERIVED back out of
    // the saved amount, so the field shows exactly what the user typed.
    val existingLedger = remember { parseCalcLedger(existing?.exprNote ?: "") }
    var amount by remember {
        mutableStateOf(
            existing?.let { e ->
                val parts = e.items.sumOf { it.qty * it.price } +
                    (calcLedgerTotal(existingLedger) ?: 0.0)
                val base = r2(e.amount - parts)
                if (base > 0.004) trimNum(base) else ""
            } ?: ""
        )
    }
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
    // v1.6.37 — the PAPER annotated calculator (the user's UX blueprint):
    // the equation renders as ONE inline text flow — no pills around
    // numbers or operators, no label-trigger icons beside + − × ÷ = —
    // every number carries a single tiny tag badge (12dp) at the
    // top-left corner of its first digit, tapping it opens a focused
    // popover anchored AT that number (text field + a Before/After
    // position toggle on the SYSTEM keyboard), and a NEXT LINE key on
    // the keypad commits the active expression into a stacked LEDGER
    // above, finished by a prominent running-total bar. Completed lines
    // keep their whole token list, so reopening an entry rebuilds the
    // ledger and the labels (same free-text grammar, now "\n"-separated
    // for multi-line ledgers — see parseCalcLedger in lib/Calc.kt).
    var calcLines by remember { mutableStateOf(existingLedger) }
    var calcNote by remember { mutableStateOf(CalcNote()) } // the ACTIVE line
    // which number the keypad is typing into (-1 = none active)
    var calcActive by remember { mutableStateOf(-1) }
    // the popover's target: "t<i>" = number i's label, "rb"/"ra" = the
    // result's labels (legacy notes) — null = no popover, keypad is up
    var calcLabelTarget by remember { mutableStateOf<String?>(null) }
    var calcLabelText by remember { mutableStateOf("") }
    // which SIDE of the number the label sits on ("before"/"after")
    var calcLabelPos by remember { mutableStateOf("before") }
    var catManager by remember { mutableStateOf(false) }
    var showOwnerPicker by remember { mutableStateOf(false) }

    // v1.6.37 — THE SUM, live on every keystroke: base + items + the
    // calculator's whole contribution (committed ledger lines + the
    // active line). The TOTAL row and Save both read this.
    val itemsSum = items.sumOf { it.qty * it.price }
    val baseNum = amount.replace(",", ".").toDoubleOrNull()
    // the active line's live total + its fold steps
    val calcTotal = remember(calcNote) { evalCalcTokens(calcNote.tokens) }
    val calcStepList = remember(calcNote) { calcSteps(calcNote.tokens) }
    // the committed ledger lines' total, then everything the calculator adds
    val calcLedgerSum = remember(calcLines) { calcLedgerTotal(calcLines) }
    val calcPartsTotal = r2((calcLedgerSum ?: 0.0) + (calcTotal ?: 0.0))
    val partsSum = r2(itemsSum + calcPartsTotal)
    // THE ENTRY AMOUNT: the typed base + every part (100 + 200 = 300)
    val effAmount = r2((baseNum ?: 0.0) + partsSum)

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
            // v1.6.37 — the whole calculator state rides along: every
            // committed ledger line + the still-active line (if any),
            // "\n"-separated in the same free-text grammar
            exprNote = serializeCalcLedger(
                calcLines + if (calcNote.tokens.isNotEmpty()) listOf(calcNote) else emptyList()
            ).ifEmpty { null },
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

    /* v1.6.37 — open the LABEL POPOVER for a target: "t<i>" = number
       i's label (its tiny tag badge or the label text itself), "rb"/
       "ra" = the result's before/after labels (legacy notes). For a
       number the field starts on the side that already carries text,
       and the Before/After toggle swaps sides — the label MOVES on
       commit, exactly the user's position-based labeling spec
       ("ball 200" vs "200 Birr"). */
    fun openCalcLabel(target: String) {
        when {
            target == "rb" -> { calcLabelText = calcNote.resultBefore; calcLabelPos = "before" }
            target == "ra" -> { calcLabelText = calcNote.resultAfter; calcLabelPos = "after" }
            else -> {
                val tk = calcNote.tokens.getOrNull(target.drop(1).toIntOrNull() ?: -1)
                // start on the side that already carries text; a FRESH
                // label defaults to BEFORE (the blueprint's "ball 200")
                val beforeFirst = tk != null && tk.before.isNotBlank()
                val afterOnly = tk != null && tk.before.isBlank() && tk.after.isNotBlank()
                calcLabelText = when {
                    beforeFirst -> tk?.before ?: ""
                    afterOnly -> tk?.after ?: ""
                    else -> ""
                }
                calcLabelPos = if (afterOnly) "after" else "before"
            }
        }
        calcLabelTarget = target
    }

    /* the Before/After toggle inside the popover: swap the field to the
       other side's stored label when it has one (legacy both-side notes);
       otherwise KEEP the typed text — flipping the position must never
       lose what was just typed */
    fun toggleCalcLabelPos(pos: String) {
        if (pos == calcLabelPos) return
        val target = calcLabelTarget ?: return
        val other = when {
            target == "rb" -> calcNote.resultBefore
            target == "ra" -> calcNote.resultAfter
            else -> {
                val tk = calcNote.tokens.getOrNull(target.drop(1).toIntOrNull() ?: -1)
                (if (pos == "before") tk?.before else tk?.after) ?: ""
            }
        }
        if (other.isNotBlank()) calcLabelText = other
        calcLabelPos = pos
    }

    /* commit the popover: non-empty text lands on the CHOSEN side (the
       other side is cleared — ONE label, ONE position, the spec's
       "ball 200" / "200 Birr"), and an EMPTY submit deletes the label
       (the v1.6.36 re-edit rule, kept). */
    fun commitCalcLabel() {
        val target = calcLabelTarget ?: return
        val text = calcLabelText.trim()
        val toks = calcNote.tokens.toMutableList()
        when {
            target == "rb" -> calcNote = calcNote.copy(resultBefore = text)
            target == "ra" -> calcNote = calcNote.copy(resultAfter = text)
            else -> {
                val idx = target.drop(1).toIntOrNull() ?: -1
                if (idx in toks.indices) {
                    toks[idx] = when {
                        text.isEmpty() -> toks[idx].copy(before = "", after = "")
                        calcLabelPos == "before" -> toks[idx].copy(before = text, after = "")
                        else -> toks[idx].copy(after = text, before = "")
                    }
                    calcNote = calcNote.copy(tokens = toks)
                }
            }
        }
        calcLabelTarget = null
        calcLabelText = ""
    }

    fun cancelCalcLabel() {
        calcLabelTarget = null
        calcLabelText = ""
    }

    /* v1.6.37 — NEXT LINE (the keypad's Enter): commit the active
       expression into the stacked ledger and start a fresh line under
       it; tapping a committed line pulls it back down for re-editing
       (its whole token list — labels included — comes with it). */
    fun commitCalcLine() {
        if (calcNote.tokens.isEmpty()) return
        calcLines = calcLines + calcNote
        calcNote = CalcNote()
        calcActive = -1
    }

    fun pullCalcLine(i: Int) {
        if (i !in calcLines.indices) return
        calcNote = calcLines[i]
        calcLines = calcLines.filterIndexed { j, _ -> j != i }
        calcActive = -1
    }

    /* v1.6.36/37 — the keypad logic on the ACTIVE line's token chain:
       digits type into the ACTIVE number (or continue/start one), an
       operator key terminates the number, ⌫ edits it (an emptied number
       is removed with its labels), AC clears the ACTIVE line only (the
       committed ledger lines each carry their own ✕). The entry total
       (effAmount) recomputes live from the state — no mirroring. */
    fun calcKey(k: String) {
        val toks = calcNote.tokens.toMutableList()
        when (k) {
            "AC" -> {
                calcNote = CalcNote()
                calcActive = -1
                return
            }
            "⌫" -> {
                if (calcActive in toks.indices) {
                    val n = toks[calcActive].number.dropLast(1)
                    if (n.isEmpty()) {
                        toks.removeAt(calcActive)
                        calcActive = -1
                    } else toks[calcActive] = toks[calcActive].copy(number = n)
                } else if (toks.isNotEmpty()) {
                    val last = toks.last()
                    val n = last.number.dropLast(1)
                    if (n.isEmpty()) toks.removeAt(toks.lastIndex)
                    else toks[toks.lastIndex] = last.copy(number = n)
                }
            }
            ".", in "0".."9" -> {
                if (calcActive in toks.indices) {
                    val tk = toks[calcActive]
                    toks[calcActive] = if (k == ".") {
                        if (tk.number.contains('.')) return
                        tk.copy(number = tk.number.ifEmpty { "0" } + ".")
                    } else tk.copy(number = (tk.number + k).take(12))
                } else if (toks.isNotEmpty() && toks.last().op == null) {
                    // no active number: digits continue the chain's last one
                    val last = toks.last()
                    toks[toks.lastIndex] = if (k == ".") {
                        if (last.number.contains('.')) return
                        last.copy(number = last.number.ifEmpty { "0" } + ".")
                    } else last.copy(number = (last.number + k).take(12))
                    calcActive = toks.lastIndex
                } else {
                    toks.add(CalcToken(number = if (k == ".") "0." else k))
                    calcActive = toks.lastIndex
                }
            }
            "+", "-", "×", "÷" -> {
                val idx = if (calcActive in toks.indices) calcActive else toks.lastIndex
                if (toks.isEmpty()) return
                toks[idx] = toks[idx].copy(op = k)
                calcActive = -1
            }
        }
        calcNote = calcNote.copy(tokens = toks)
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
                // CALCULATOR — v1.6.37, the PAPER annotated calculator (the
                // user's UX blueprint): the equation is ONE inline ink-on-
                // paper flow — labels, numbers and operators share size /
                // weight / baseline, no pill boxes, no label-trigger icons
                // on + − × ÷ = — every number carries a single tiny tag
                // badge at the top-left corner of its first digit; tapping
                // it opens a focused popover anchored AT that number (text
                // field + Before/After position toggle on the system
                // keyboard). The STACKED LEDGER above holds the committed
                // lines (Next Line on the keypad) with a prominent running
                // total bar; tapping a committed line pulls it back down
                // for re-editing.
                "calc" -> {
                    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                        // header: the section label + AC (clears the ACTIVE line)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(t("ee.calcLabel"), fontSize = F.small.sp, color = C.sub, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (calcNote.tokens.isNotEmpty()) {
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(C.card)
                                        .border(1.dp, C.red, RoundedCornerShape(999.dp))
                                        .clickable { calcKey("AC") }
                                        .testTag("calcKey:AC")
                                        .padding(horizontal = 10.dp, vertical = 3.dp),
                                ) { Text("AC", fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C.red) }
                            }
                        }
                        // THE STACKED LEDGER — committed lines + the running
                        // total bar (the blueprint's ledger view, above the
                        // active row; the bar counts the active line too, so
                        // the number never jumps on commit)
                        if (calcLines.isNotEmpty()) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(C.card)
                                    .border(1.dp, C.line, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                calcLines.forEachIndexed { i, line ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { pullCalcLine(i) }
                                            .testTag("calcLine:$i")
                                            .padding(horizontal = 4.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            line.tokens.joinToString(" ") { tk ->
                                                listOfNotNull(
                                                    tk.before.ifBlank { null },
                                                    tk.number,
                                                    tk.after.ifBlank { null },
                                                ).joinToString(" ") + (tk.op?.let { " $it" } ?: "")
                                            },
                                            fontSize = F.small.sp, color = C.sub, fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            money(evalCalcTokens(line.tokens) ?: 0.0),
                                            fontSize = F.small.sp, color = C.ink, fontWeight = FontWeight.SemiBold,
                                        )
                                        IconBtn("close", { calcLines = calcLines.filterIndexed { j, _ -> j != i } }, size = 24, danger = true)
                                    }
                                }
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(t("ee.calcTotal"), fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C.sub)
                                    Text(
                                        money(calcPartsTotal),
                                        fontSize = F.body.sp, fontWeight = FontWeight.ExtraBold, color = C.brand,
                                        modifier = Modifier.testTag("calcLedgerTotal"),
                                    )
                                }
                            }
                        }
                        // THE ACTIVE LINE — one inline ink-on-paper flow: the
                        // only affordances are each number's tiny tag badge
                        // (top-left of its first digit) and the live "= total"
                        // tail, flush in the same flow
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .pressVeil(corner = 18.dp)
                                .focusRing(corner = 18.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(C.brand.copy(alpha = 0.08f))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            if (calcNote.tokens.isEmpty()) {
                                Text(t("ee.calcPlaceholder"), fontSize = 15.sp, color = C.faint)
                                Text(t("ee.calcHint"), fontSize = F.tiny.sp, color = C.faint, modifier = Modifier.padding(top = 4.dp))
                            } else {
                                FlowRow(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    calcNote.tokens.forEachIndexed { i, tk ->
                                        // the label BEFORE the number (plain text, same metrics)
                                        if (tk.before.isNotBlank()) {
                                            Text(
                                                tk.before,
                                                fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.sub,
                                                modifier = Modifier
                                                    .clickable { openCalcLabel("t$i") }
                                                    .testTag("calcLbl:$i"),
                                            )
                                        }
                                        // the NUMBER + its single tiny tag badge
                                        Box {
                                            Text(
                                                tk.number,
                                                fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                                                color = if (calcActive == i) C.brand else C.ink,
                                                textDecoration = if (calcActive == i) TextDecoration.Underline else null,
                                                modifier = Modifier
                                                    .clickable { calcActive = i }
                                                    .testTag("calcNum:$i")
                                                    .padding(top = 2.dp),
                                            )
                                            // the 12dp tag badge pinned to the
                                            // top-left corner of the first digit —
                                            // the ONLY label affordance (brand
                                            // while labeled / editing, faint otherwise)
                                            Box(
                                                Modifier
                                                    .align(Alignment.TopStart)
                                                    .offset(x = (-7).dp, y = (-9).dp)
                                                    .size(14.dp)
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(
                                                        when {
                                                            calcLabelTarget == "t$i" -> C.brand
                                                            tk.before.isNotBlank() || tk.after.isNotBlank() -> C.brandSoft
                                                            else -> C.chip
                                                        }
                                                    )
                                                    .then(
                                                        if (tk.before.isBlank() && tk.after.isBlank() && calcLabelTarget != "t$i")
                                                            Modifier.border(1.dp, C.line, RoundedCornerShape(5.dp))
                                                        else Modifier
                                                    )
                                                    .clickable { openCalcLabel("t$i") }
                                                    .testTag("calcTag:$i"),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                MdiIcon(
                                                    "tag", 9,
                                                    when {
                                                        calcLabelTarget == "t$i" -> C.onBrand
                                                        tk.before.isNotBlank() || tk.after.isNotBlank() -> C.brand
                                                        else -> C.sub
                                                    },
                                                )
                                            }
                                            // the focused popover, anchored AT
                                            // this number (not the screen bottom)
                                            if (calcLabelTarget == "t$i") {
                                                CalcLabelPopover(
                                                    text = calcLabelText,
                                                    onText = { calcLabelText = it },
                                                    pos = calcLabelPos,
                                                    onPos = { toggleCalcLabelPos(it) },
                                                    showToggle = true,
                                                    onDone = { commitCalcLabel() },
                                                    onCancel = { cancelCalcLabel() },
                                                )
                                            }
                                        }
                                        // the label AFTER the number
                                        if (tk.after.isNotBlank()) {
                                            Text(
                                                tk.after,
                                                fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.sub,
                                                modifier = Modifier
                                                    .clickable { openCalcLabel("t$i") }
                                                    .testTag("calcLbl:$i"),
                                            )
                                        }
                                        // the operator AFTER this number — a
                                        // plain glyph (tap cycles + − × ÷), NO
                                        // chip, NO label trigger
                                        tk.op?.let { op ->
                                            Text(
                                                op,
                                                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.sub,
                                                modifier = Modifier
                                                    .clickable {
                                                        val toks = calcNote.tokens.toMutableList()
                                                        if (i in toks.indices) {
                                                            val next = when (op) { "+" -> "-"; "-" -> "×"; "×" -> "÷"; else -> "+" }
                                                            toks[i] = toks[i].copy(op = next)
                                                            calcNote = calcNote.copy(tokens = toks)
                                                        }
                                                    }
                                                    .testTag("calcOp:$i")
                                                    .padding(horizontal = 2.dp),
                                            )
                                        }
                                    }
                                    // the live RESULT — flush in the same flow
                                    // ("ball 200 + 100 cups = 300 Br"); the
                                    // result's own labels (legacy notes) render
                                    // as plain text around it, tappable to edit
                                    if (calcTotal != null) {
                                        if (calcNote.resultBefore.isNotBlank()) {
                                            Box {
                                                Text(
                                                    calcNote.resultBefore,
                                                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.sub,
                                                    modifier = Modifier.clickable { openCalcLabel("rb") },
                                                )
                                                if (calcLabelTarget == "rb") {
                                                    CalcLabelPopover(
                                                        text = calcLabelText,
                                                        onText = { calcLabelText = it },
                                                        pos = calcLabelPos,
                                                        onPos = {},
                                                        showToggle = false,
                                                        onDone = { commitCalcLabel() },
                                                        onCancel = { cancelCalcLabel() },
                                                    )
                                                }
                                            }
                                        }
                                        Text("=", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.sub)
                                        Text(
                                            money(calcTotal ?: 0.0),
                                            fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = C.brand,
                                            modifier = Modifier
                                                .testTag("calcTotal")
                                                .padding(horizontal = 2.dp),
                                        )
                                        if (calcNote.resultAfter.isNotBlank()) {
                                            Box {
                                                Text(
                                                    calcNote.resultAfter,
                                                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.sub,
                                                    modifier = Modifier.clickable { openCalcLabel("ra") },
                                                )
                                                if (calcLabelTarget == "ra") {
                                                    CalcLabelPopover(
                                                        text = calcLabelText,
                                                        onText = { calcLabelText = it },
                                                        pos = calcLabelPos,
                                                        onPos = {},
                                                        showToggle = false,
                                                        onDone = { commitCalcLabel() },
                                                        onCancel = { cancelCalcLabel() },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                // show your work — the live folds in ONE muted
                                // line under the flow (paper keeps its work)
                                if (calcStepList.isNotEmpty()) {
                                    Text(
                                        calcStepList.joinToString(" · ") { "${it.expr} = ${money(it.value)}" },
                                        fontSize = F.tiny.sp, color = C.faint,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp)
                                            .testTag("calcSteps"),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
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
                            // v1.6.37 — the typed value is the BASE; items
                            // and the calculator ADD onto it for the entry
                            // total (see effAmount / the TOTAL row below)
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
                    if (baseNum != null) {
                        Text(money(baseNum), fontSize = F.tiny.sp, color = C.faint)
                    }
                }
                    if (items.isEmpty()) {
                        IconBtn("calculator-variant", { UiStore.openCalculator { v -> amount = trimNum(v) } }, size = 40)
                    }
                }
                // ITEMS — the full line-item editor (name · qty × price);
                // tapping an existing row below switches here for editing
                "items" -> ItemEditor(
                    onAdd = { item -> items = items + item },
                    onUpdate = { item -> items = items.map { if (it.id == item.id) item else it } },
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
                                                items = items + EntryItem(id = uid(), name = name, qty = 1.0, price = price)
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
                    IconBtn("close", { items = items.filter { x -> x.id != it.id } }, size = 28, danger = true)
                }
            }
            // v1.6.34 — the old collapsed “＋ Add items” row is GONE: the
            // tab strip above IS the add flow now (an existing entry
            // starts on the amount tab, so the v1.6.18 double-save-looking
            // forms can never stack).
            // v1.6.37 — THE ENTRY TOTAL row (the user's sum flow: type 100,
            // add a 200 item → "100 + 200 = 300"): base + items +
            // calculator, live. Skipped on the calc tab when it would just
            // repeat the calculator's own running-total bar.
            val showTotal = effAmount > 0 &&
                !(amountMode == "calc" && items.isEmpty() && (baseNum ?: 0.0) <= 0.0)
            if (showTotal) {
                Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t("lg.total"), fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C.sub)
                        Text(
                            money(effAmount),
                            fontSize = F.body.sp, fontWeight = FontWeight.ExtraBold, color = C.brand,
                            modifier = Modifier.testTag("entryTotal"),
                        )
                    }
                    // the breakdown, exactly the user's own arithmetic
                    if ((baseNum ?: 0.0) > 0.0 && partsSum > 0.0) {
                        Text(
                            "${trimNum(baseNum ?: 0.0)} + ${trimNum(partsSum)}",
                            fontSize = F.tiny.sp, color = C.faint,
                        )
                    }
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

            // v1.6.37 — THE SMART KEYPAD (annotated calculator's tab only):
            // the numeric pad + its NEXT LINE key stay pinned at the bottom
            // above the Save footer. While the LABEL POPOVER is open the pad
            // HIDES — the popover is anchored at the number and carries the
            // system QWERTY itself, so nothing sits at the screen bottom
            // overlapping the date/method pickers (the user's report).
            if (amountMode == "calc" && calcLabelTarget == null) {
                CalcPadArea(
                    onKey = { calcKey(it) },
                    onNext = { commitCalcLine() },
                    nextEnabled = calcNote.tokens.isNotEmpty(),
                )
            }
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

/* ---------------- v1.6.37 — the paper calculator's pad + popover ---------------- */

/* The FOCUSED LABEL POPOVER, anchored directly at the number it edits
   (the user's blueprint: no more jumping to an overlay at the screen
   bottom): a standard text field on the SYSTEM QWERTY keyboard, a
   clean Before/After toggle for the label's position ("ball 200" vs
   "200 Birr"), a ✕ cancel and a proper Done button. Submitting an
   EMPTY field deletes the label. DropdownMenu keeps the popover
   on-screen whatever the anchor's position. */
@Composable
private fun CalcLabelPopover(
    text: String,
    onText: (String) -> Unit,
    pos: String,
    onPos: (String) -> Unit,
    showToggle: Boolean,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val C = C()
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { fr.requestFocus() } catch (_: Exception) {}
    }
    androidx.compose.material3.DropdownMenu(
        expanded = true,
        onDismissRequest = onCancel,
        offset = DpOffset(0.dp, 6.dp),
        shape = RoundedCornerShape(14.dp),
        containerColor = C.card,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            Modifier.widthIn(min = 250.dp, max = 310.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = onText,
                textStyle = TextStyle(fontSize = F.body.sp, color = C.ink),
                singleLine = true,
                cursorBrush = SolidColor(C.brand),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calcLabelField")
                    .focusRequester(fr)
                    .pressVeil()
                    .focusRing()
                    .border(1.dp, C.line, RoundedCornerShape(12.dp))
                    .background(C.inputBg, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp)
                    .heightIn(min = 44.dp),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Row { Text(t("ee.calcDescribe"), fontSize = F.body.sp, color = C.faint) }
                        Spacer(Modifier.height(0.dp))
                        Box {}
                    } else inner()
                },
            )
            if (showToggle) {
                // Before / After — the label's position (ball 200 vs 200 birr)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(label = t("ee.calcPosBefore"), compact = true, active = pos == "before", onPress = { onPos("before") })
                    Chip(label = t("ee.calcPosAfter"), compact = true, active = pos == "after", onPress = { onPos("after") })
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconBtn("close", onCancel, size = 36)
                Btn(
                    label = t("act.done"),
                    onPress = onDone,
                    small = true,
                    modifier = Modifier.testTag("calcLabelDone"),
                )
            }
        }
    }
}

/* The keypad area pinned at the editor's bottom (calc tab only): the
   4×4 numeric pad + the full-width NEXT LINE key that commits the
   active expression into the stacked ledger.

   SIZING (the layout lesson from the harness's short window — kept
   from v1.6.36): the pad is capped and FLEXES DOWN on short screens —
   the rows are weighted children of a min/max-heighted column, so they
   share whatever height the editor can spare instead of being crushed
   to zero-height tails. Keys always stay tappable; tall screens get
   the full-height keys. */
@Composable
private fun CalcPadArea(
    onKey: (String) -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean,
) {
    val C = C()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp)
            .heightIn(min = 172.dp, max = 256.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            listOf("7", "8", "9", "÷"),
            listOf("4", "5", "6", "×"),
            listOf("1", "2", "3", "−"),
            listOf(".", "0", "⌫", "+"),
        ).forEach { rowKeys ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowKeys.forEach { k ->
                    CalcKey(
                        key = if (k == "−") "-" else k,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onPress = onKey,
                    )
                }
            }
        }
        // v1.6.37 — NEXT LINE: commits the active expression into the
        // stacked ledger and clears the line for the next calculation
        // (the blueprint's multi-line paper flow)
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (nextEnabled) C.brand else C.brand.copy(alpha = 0.25f))
                    .clickable(enabled = nextEnabled) {
                        vibrate(longArrayOf(8))
                        onNext()
                    }
                    .testTag("calcKey:Next"),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MdiIcon("arrow-down", 16, C.onBrand.copy(alpha = if (nextEnabled) 1f else 0.6f))
                    Text(
                        t("ee.calcNext"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = C.onBrand.copy(alpha = if (nextEnabled) 1f else 0.6f),
                    )
                }
            }
        }
    }
}

/* One key of the numeric calculator pad — flat tile, brand-tinted
   operators, red backspace (the app calculator's colour language). The
   key FILLS its row cell (the rows flex — see CalcPadArea). */
@Composable
private fun CalcKey(key: String, modifier: Modifier = Modifier, onPress: (String) -> Unit) {
    val C = C()
    val op = key in listOf("+", "-", "×", "÷")
    val display = if (key == "-") "−" else key
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(when {
                key == "⌫" -> Color(0x1dd64545)
                op -> C.brandSoft
                else -> C.card
            })
            .border(
                1.dp,
                when {
                    key == "⌫" -> C.red
                    op -> C.brand
                    else -> C.line
                },
                RoundedCornerShape(14.dp),
            )
            .clickable {
                vibrate(longArrayOf(8))
                onPress(key)
            }
            .testTag("calcKey:$key"),
        contentAlignment = Alignment.Center,
    ) {
        if (key == "⌫") {
            MdiIcon("backspace-outline", 20, C.red)
        } else {
            Text(
                display,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (op) C.brand else C.ink,
            )
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

