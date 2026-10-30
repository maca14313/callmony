package com.callmony.ui.overlays

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
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
import com.callmony.lib.parseCalcNote
import com.callmony.lib.r2
import com.callmony.lib.serializeCalcLedger
import com.callmony.lib.serializeCalcNote
import com.callmony.lib.ownerNameOf
import com.callmony.lib.todayStr
import com.callmony.lib.uid
import com.callmony.platform.NativeDateTime
import com.callmony.platform.Platform
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
import com.callmony.ui.kit.Toasts
import com.callmony.ui.kit.focusRing
import com.callmony.ui.kit.pressVeil
import com.callmony.ui.kit.vibrate
import com.callmony.ui.components.ScrollRow
import com.callmony.ui.sections.openPaymentSheet

/* EntryEdit — the ledger entry editor. v1.6.38: the ANNOTATED CALCULATOR
   IS the entry's amount input (Just amount / Items · qty × price / Quick
   item are REMOVED — documented in removed/ at the repo root): a
   near-full-screen paper canvas with a text-default active line, a
   locked multi-line ledger, % + ± + M+/M−/MR advanced keys, a small
   Next-line key, auto-save, and a tappable total that can become a
   payment. Kind chips, date/method, categories, due date, note,
   attachments, owner and groups are unchanged. */

private val KINDS = listOf(
    Triple("credit", "credit", "hand-coin-outline"),
    Triple("debit", "debit", "credit-card-outline"),
    Triple("credit", "income", "trending-up"),
    Triple("debit", "expense", "trending-down"),
)
private val QUICK_UNITS = listOf("pic", "kg", "pcs", "bag", "box", "l")

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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
    // v1.6.38 — THE SUM MODEL, simplified with the removed tabs: the
    // entry's amount = its line items + the calculator's whole ledger
    // (committed lines + the active line). There is no typed base any
    // more — the calculator IS the input. A LEGACY entry (amount, no
    // ledger, no items) reopens with its amount seeded as its first
    // locked line, so nothing is ever lost.
    val existingLedger = remember { parseCalcLedger(existing?.exprNote ?: "") }
    var calcLines by remember {
        mutableStateOf(
            existingLedger.ifEmpty {
                if (existing != null && existing.items.isEmpty() && existing.amount > 0.0) {
                    listOf(CalcNote(tokens = listOf(CalcToken(number = trimNum(existing.amount)))))
                } else emptyList()
            }
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
    // v1.6.38 — the input-mode strip is down to Calculator + Recent (the
    // Just amount / Items / Quick item tabs are removed; Recent keeps the
    // one-tap repeat booking). Everything opens on the CALCULATOR.
    var amountMode by remember { mutableStateOf("calc") }
    // v1.6.38 — THE CANVAS CALCULATOR (the user's rework spec):
    //   calcMode "text" (DEFAULT — fresh lines start as text on the
    //     system QWERTY, giving native selection + copy/cut/paste) or
    //     "num" (the custom keypad aims at calcActive). Tapping a number
    //     in the flow switches to num; the Aa/123 row switches either way.
    //   calcText mirrors the active line while in text mode (live-parsed
    //     into tokens — the SAME free-text grammar).
    //   Locked ledger lines (Next line) show above; the total bar can
    //     MAKE A PAYMENT of itself. % ± keys + Σ (M+ M− MR) advanced.
    var calcNote by remember { mutableStateOf(CalcNote()) } // the ACTIVE line
    var calcActive by remember { mutableStateOf(-1) }       // keypad target
    var calcMode by remember { mutableStateOf("text") }     // "text" | "num"
    var calcText by remember { mutableStateOf("") }         // text-mode source
    // the compact popover's target: "t<i>" = number i's label, "res" =
    // the result's label (before/after) — null = no popover
    var calcLabelTarget by remember { mutableStateOf<String?>(null) }
    var calcLabelText by remember { mutableStateOf("") }
    var calcLabelPos by remember { mutableStateOf("before") }
    var calcAdvanced by remember { mutableStateOf(false) }  // Σ panel
    var calcMemory by remember { mutableStateOf<Double?>(null) }
    var totalMenu by remember { mutableStateOf(false) }     // the total's menu
    var clipTarget by remember { mutableStateOf<String?>(null) } // long-press clipboard menu
    var catManager by remember { mutableStateOf(false) }
    var showOwnerPicker by remember { mutableStateOf(false) }

    // v1.6.38 — THE SUM, live: items + the calculator's whole contribution
    // (committed ledger lines + the active line).
    val itemsSum = items.sumOf { it.qty * it.price }
    val calcTotal = remember(calcNote) { evalCalcTokens(calcNote.tokens) }
    val calcStepList = remember(calcNote) { calcSteps(calcNote.tokens) }
    val calcLedgerSum = remember(calcLines) { calcLedgerTotal(calcLines) }
    val calcPartsTotal = r2((calcLedgerSum ?: 0.0) + (calcTotal ?: 0.0))
    // THE ENTRY AMOUNT: every line item + the whole calculator
    val effAmount = r2(itemsSum + calcPartsTotal)

    /* the entry object exactly as Save/autosave/make-payment write it */
    fun buildEntry(id: String): LedgerEntry = LedgerEntry(
        id = id,
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
        // the whole calculator state rides along: every committed ledger
        // line + the still-active line (if any), "\n"-separated
        exprNote = serializeCalcLedger(
            calcLines + if (calcNote.tokens.isNotEmpty()) listOf(calcNote) else emptyList()
        ).ifEmpty { null },
        groupId = groupSel,
        refs = refs,
        createdAt = existing?.createdAt ?: now(),
        updatedAt = now(),
    )

    fun maybeDueReminder(id: String) {
        // auto payment-reminder at 09:00 on the due date (new entries only)
        if (existing == null && dueOn && dueDate.length >= 8) {
            val parts = dueDate.split("-")
            val at = try {
                com.callmony.lib.KmpDate(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 9, 0, 0).time()
            } catch (_: Exception) { dateOf(now()).time() }
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
    }

    fun save(silent: Boolean = false) {
        if (effAmount <= 0) {
            if (!silent) Toasts.toast(t("lg.amountGreaterThanZero"), "error")
            return
        }
        val e = buildEntry(existing?.id ?: uid())
        AppStore.saveEntry(e)
        maybeDueReminder(e.id)
        if (!silent) {
            Toasts.toast(t("act.saved"))
            pop()
        }
    }

    /* v1.6.38 — AUTO-SAVE (user spec: "it should have auto save"): every
       change to an EXISTING entry persists ~600ms after the last
       keystroke and again on close — no Save needed, nothing is ever
       lost by backing out. New entries still Save explicitly (an
       unconfirmed draft must not appear in the book). Invalid totals
       (<= 0) never overwrite a good entry. */
    // v1.6.38 — the DELETE guard: the dispose-time auto-save must never
    // resurrect an entry the user just deleted
    var deleted by remember { mutableStateOf(false) }
    val autoKey = remember { mutableStateOf<String?>(null) }
    val currentKey = listOf(
        effAmount, serializeCalcLedger(calcLines + if (calcNote.tokens.isNotEmpty()) listOf(calcNote) else emptyList()),
        items.map { "${it.id}:${it.qty}:${it.price}" }, date, dueOn, dueDate, category, method, note, owner, groupSel,
        refs.map { "${it.type}:${it.id}" }, type, kindSel,
    ).toString()
    if (autoKey.value == null) autoKey.value = currentKey
    val initialKey = autoKey.value ?: currentKey
    if (existing != null) {
        LaunchedEffect(currentKey) {
            if (!deleted && currentKey != initialKey && effAmount > 0) {
                kotlinx.coroutines.delay(600)
                if (!deleted && currentKey != initialKey && effAmount > 0) save(silent = true)
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                if (!deleted && currentKey != initialKey && effAmount > 0) {
                    // persist whatever changed but never got debounced
                    AppStore.saveEntry(buildEntry(existing.id))
                }
            }
        }
    }

    /* v1.6.38 — "when clicking the total results it should have also the
       option to make payment of it": the total bar's menu can record a
       payment of the full total against this entry (saving it first
       when it is brand new). */
    fun makePaymentOf(total: Double) {
        if (total <= 0) return
        val id = existing?.id ?: uid()
        if (effAmount > 0) {
            AppStore.saveEntry(buildEntry(id))
            maybeDueReminder(id)
        }
        totalMenu = false
        if (existing == null) pop()
        openPaymentSheet(id, total)
    }

    /* open the COMPACT label popover for a target: "t<i>" = number i's
       label, "res" = the result's label ("= total 200" / "= 200 total").
       The field starts on the side that already carries text; a fresh
       label defaults to BEFORE ("ball 200"). */
    fun openCalcLabel(target: String) {
        when {
            target == "res" -> {
                val beforeFirst = calcNote.resultBefore.isNotBlank()
                val afterOnly = calcNote.resultBefore.isBlank() && calcNote.resultAfter.isNotBlank()
                calcLabelText = when {
                    beforeFirst -> calcNote.resultBefore
                    afterOnly -> calcNote.resultAfter
                    else -> ""
                }
                calcLabelPos = if (afterOnly) "after" else "before"
            }
            else -> {
                val tk = calcNote.tokens.getOrNull(target.drop(1).toIntOrNull() ?: -1)
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

    /* the Before/After toggle: swap to the other side's stored label when
       it has one; otherwise KEEP the typed text — flipping never loses
       what was just typed */
    fun toggleCalcLabelPos(pos: String) {
        if (pos == calcLabelPos) return
        val target = calcLabelTarget ?: return
        val other = when {
            target == "res" -> if (pos == "before") calcNote.resultBefore else calcNote.resultAfter
            else -> {
                val tk = calcNote.tokens.getOrNull(target.drop(1).toIntOrNull() ?: -1)
                (if (pos == "before") tk?.before else tk?.after) ?: ""
            }
        }
        if (other.isNotBlank()) calcLabelText = other
        calcLabelPos = pos
    }

    /* commit the popover: non-empty text lands on the CHOSEN side (the
       other side clears — ONE label, ONE position), an EMPTY submit
       deletes the label. */
    fun commitCalcLabel() {
        val target = calcLabelTarget ?: return
        val text = calcLabelText.trim()
        val toks = calcNote.tokens.toMutableList()
        when {
            target == "res" -> calcNote = when {
                text.isEmpty() -> calcNote.copy(resultBefore = "", resultAfter = "")
                calcLabelPos == "before" -> calcNote.copy(resultBefore = text, resultAfter = "")
                else -> calcNote.copy(resultAfter = text, resultBefore = "")
            }
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

    /* v1.6.38 — NEXT LINE (the small ⏎ key): commit the active expression
       into the ledger — the old line LOCKS (read-only; long-press copies)
       — and the fresh line starts in TEXT mode (the spec's default). */
    fun commitCalcLine() {
        if (calcNote.tokens.isEmpty()) return
        calcLines = calcLines + calcNote
        calcNote = CalcNote()
        calcText = ""
        calcActive = -1
        calcMode = "text"
    }

    fun deleteCalcLine(i: Int) {
        if (i !in calcLines.indices) return
        calcLines = calcLines.filterIndexed { j, _ -> j != i }
    }

    fun copyLineText(text: String) {
        Platform.share.copyText(text)
        Toasts.toast(t("act.copied"))
    }

    /* v1.6.38 — the keypad on the ACTIVE line's token chain. The ⌫ FIX
       (user report: "if i write 200 * 100 and i click the x delete btn
       it delete the 0,0,1 then it should delete the * but skips it and
       delete 0,0,2"): a TRAILING OPERATOR deletes FIRST — the × between
       the numbers goes before the digits to its left. % toggles the
       active number's percent (phone-calculator semantics in Calc.kt),
       ± flips its sign, AC clears the ACTIVE line only. */
    fun calcKey(k: String) {
        val toks = calcNote.tokens.toMutableList()
        when (k) {
            "AC" -> {
                calcNote = CalcNote()
                calcText = ""
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
                    when {
                        // THE FIX — the operator goes first
                        last.op != null -> toks[toks.lastIndex] = last.copy(op = null)
                        else -> {
                            val n = last.number.dropLast(1)
                            if (n.isEmpty()) toks.removeAt(toks.lastIndex)
                            else toks[toks.lastIndex] = last.copy(number = n)
                        }
                    }
                }
            }
            "%", "±" -> {
                val idx = if (calcActive in toks.indices) calcActive else toks.indexOfLast { it.number.isNotBlank() }
                if (idx in toks.indices) {
                    toks[idx] = if (k == "%") {
                        toks[idx].copy(pct = !toks[idx].pct)
                    } else {
                        val n = toks[idx].number
                        toks[idx].copy(number = if (n.startsWith("-")) n.drop(1) else "-$n")
                    }
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

    /* v1.6.38 — mode switching: text↔number. Entering TEXT seeds the
       field with the serialized active line and takes focus (system
       QWERTY, native selection/clipboard); entering NUM hides the IME
       and aims the keypad at the last number. */
    val calcFieldFocus = remember { FocusRequester() }
    val ime = LocalSoftwareKeyboardController.current
    val focusMgr = LocalFocusManager.current
    fun enterTextMode() {
        if (calcMode != "text") {
            calcText = serializeCalcNote(calcNote)
            calcMode = "text"
            calcActive = -1
        }
    }
    fun enterNumMode(aim: Int = -1) {
        if (calcMode != "num") {
            calcMode = "num"
            focusMgr.clearFocus()
            ime?.hide()
        }
        calcActive = if (aim >= 0) aim else calcNote.tokens.indexOfLast { it.number.isNotBlank() }
    }
    LaunchedEffect(calcMode) {
        if (calcMode == "text") {
            try { calcFieldFocus.requestFocus() } catch (_: Exception) {}
        }
    }

    Sheet(
        title = if (existing == null) t("lg.newEntry") else t("lg.editEntry"),
        subtitle = ownerNameOf(ownerId),
        onBack = pop,
        scrollable = false,
        // v1.6.38 — the footer buttons are SMALL (the rework spec: "the
        // next line and save btn should be very small")
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (existing != null) {
                    Btn(
                        label = t("act.delete"), variant = "danger", small = true,
                        onPress = {
                            Confirm.ask(t("lg.deleteEntryTitle"), t("lg.deleteEntryMsg"), t("act.delete"), danger = true) { ok ->
                                if (ok) { deleted = true; AppStore.deleteEntry(existing.id); Toasts.toastUndo(); pop() }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Btn(label = t("act.save"), small = true, onPress = { save() }, modifier = Modifier.weight(1f), disabled = effAmount <= 0)
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            // THE PINNED BAND: kind chips + the input-mode strip
            Column(Modifier.padding(horizontal = 18.dp)) {
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
                // v1.6.38 — Just amount / Items / Quick item are REMOVED
                // (user spec; documented in removed/). Calculator + Recent.
                Row(Modifier.padding(bottom = 8.dp)) {
                    ScrollRow {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip(compact = true, icon = "calculator-variant", label = t("ee.tabCalc"), active = amountMode == "calc", onPress = { amountMode = "calc" })
                            Chip(compact = true, icon = "history", label = t("ee.tabRecent"), active = amountMode == "recent", onPress = { amountMode = "recent" })
                        }
                    }
                }
            } // end of the pinned band
            // THE FORM: the sheet's only scrollable
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(top = 2.dp, bottom = 24.dp),
            ) {
            when (amountMode) {
                "calc" -> {
                    /* ---------------- THE CANVAS (near-full-screen) ---------------- */
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .heightIn(min = 320.dp)
                            .pressVeil(corner = 18.dp)
                            .focusRing(corner = 18.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(C.brand.copy(alpha = 0.06f))
                            .border(1.dp, C.line, RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        // header: the section label + the Σ advanced toggle
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(t("ee.calcLabel"), fontSize = F.small.sp, color = C.sub, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (calcAdvanced) C.brandSoft else C.card)
                                    .border(1.dp, if (calcAdvanced) C.brand else C.line, RoundedCornerShape(10.dp))
                                    .clickable { calcAdvanced = !calcAdvanced }
                                    .testTag("calcAdvanced"),
                                contentAlignment = Alignment.Center,
                            ) { MdiIcon("sigma", 17, if (calcAdvanced) C.brand else C.sub) }
                        }
                        // THE LOCKED LEDGER — committed lines (read-only;
                        // long-press copies the line, ✕ deletes it)
                        calcLines.forEachIndexed { i, line ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .combinedClickable(
                                        onClick = {}, // locked — nothing to pull back
                                        onLongClick = { vibrate(longArrayOf(20)); copyLineText(serializeCalcNote(line)) },
                                    )
                                    .testTag("calcLine:$i")
                                    .padding(horizontal = 4.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                MdiIcon("lock-outline", 13, C.faint)
                                Text(
                                    line.tokens.joinToString(" ") { tk ->
                                        listOfNotNull(
                                            tk.before.ifBlank { null },
                                            tk.number + if (tk.pct) "%" else "",
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
                                IconBtn("close", { deleteCalcLine(i) }, size = 24, danger = true)
                            }
                        }
                        // THE TOTAL BAR — everything the calculator adds
                        // (committed + active); TAP: make a payment of it
                        Box {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp, bottom = 2.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(C.card)
                                    .border(1.dp, C.line, RoundedCornerShape(12.dp))
                                    .combinedClickable(
                                        onClick = { if (effAmount > 0) totalMenu = true },
                                        onLongClick = { copyLineText(money(effAmount)) },
                                    )
                                    .testTag("calcLedgerTotal")
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(t("ee.calcTotal"), fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C.sub)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (itemsSum > 0.0) {
                                        Text("+ ${money(itemsSum)}", fontSize = F.tiny.sp, color = C.faint)
                                    }
                                    Text(money(effAmount), fontSize = F.body.sp, fontWeight = FontWeight.ExtraBold, color = C.brand)
                                }
                            }
                            if (totalMenu) {
                                androidx.compose.material3.DropdownMenu(
                                    expanded = true,
                                    onDismissRequest = { totalMenu = false },
                                    offset = DpOffset(0.dp, 6.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    containerColor = C.card,
                                    tonalElevation = 0.dp,
                                    shadowElevation = 6.dp,
                                ) {
                                    DropdownItem(text = "${t("ee.calcMakePayment")} · ${money(effAmount)}", tag = "totalMenuPay") { makePaymentOf(effAmount) }
                                    DropdownItem(text = t("ee.calcCopyTotal"), tag = "totalMenuCopy") { copyLineText(money(effAmount)); totalMenu = false }
                                }
                            }
                        }
                        // THE ACTIVE LINE — text (default) or the token flow
                        if (calcMode == "text") {
                            Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                                BasicTextField(
                                    value = calcText,
                                    onValueChange = {
                                        calcText = it
                                        calcNote = parseCalcNote(it)
                                    },
                                    textStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.ink),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                    cursorBrush = SolidColor(C.brand),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("calcTextField")
                                        .focusRequester(calcFieldFocus),
                                    decorationBox = { inner ->
                                        if (calcText.isEmpty()) {
                                            Column {
                                                Text(t("ee.calcPlaceholder"), fontSize = 15.sp, color = C.faint)
                                                Text(t("ee.calcHint"), fontSize = F.tiny.sp, color = C.faint, modifier = Modifier.padding(top = 4.dp))
                                            }
                                        } else inner()
                                    },
                                )
                            }
                        } else {
                            Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
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
                                            if (tk.before.isNotBlank()) {
                                                Text(
                                                    tk.before,
                                                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.sub,
                                                    modifier = Modifier
                                                        .combinedClickable(
                                                            onClick = { openCalcLabel("t$i") },
                                                            onLongClick = { clipTarget = "active" },
                                                        )
                                                        .testTag("calcLbl:$i"),
                                                )
                                            }
                                            Box {
                                                Text(
                                                    tk.number + if (tk.pct) "%" else "",
                                                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                                                    color = if (calcActive == i) C.brand else C.ink,
                                                    textDecoration = if (calcActive == i) TextDecoration.Underline else null,
                                                    modifier = Modifier
                                                        .combinedClickable(
                                                            onClick = { enterNumMode(i) },
                                                            onLongClick = { clipTarget = "active" },
                                                        )
                                                        .testTag("calcNum:$i")
                                                        .padding(top = 2.dp),
                                                )
                                                // the tiny tag badge pinned to the
                                                // top-left corner of the first digit
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
                                                if (calcLabelTarget == "t$i") {
                                                    CalcLabelPopover(
                                                        text = calcLabelText,
                                                        onText = { calcLabelText = it },
                                                        pos = calcLabelPos,
                                                        onPos = { toggleCalcLabelPos(it) },
                                                        onDone = { commitCalcLabel() },
                                                        onCancel = { cancelCalcLabel() },
                                                    )
                                                }
                                            }
                                            if (tk.after.isNotBlank()) {
                                                Text(
                                                    tk.after,
                                                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.sub,
                                                    modifier = Modifier
                                                        .combinedClickable(
                                                            onClick = { openCalcLabel("t$i") },
                                                            onLongClick = { clipTarget = "active" },
                                                        )
                                                        .testTag("calcLblAfter:$i"),
                                                )
                                            }
                                            // the operator — a plain glyph (tap cycles)
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
                                        // the live RESULT — flush in the same flow,
                                        // with its own tag badge (text after the =:
                                        // "= total 200" / "= 200 total")
                                        if (calcTotal != null) {
                                            if (calcNote.resultBefore.isNotBlank()) {
                                                Text(
                                                    calcNote.resultBefore,
                                                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.sub,
                                                    modifier = Modifier.clickable { openCalcLabel("res") },
                                                )
                                            }
                                            Text("=", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.sub)
                                            Box {
                                                Text(
                                                    money(calcTotal ?: 0.0),
                                                    fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = C.brand,
                                                    modifier = Modifier.testTag("calcTotal").padding(horizontal = 2.dp, vertical = 2.dp),
                                                )
                                                Box(
                                                    Modifier
                                                        .align(Alignment.TopStart)
                                                        .offset(x = (-7).dp, y = (-9).dp)
                                                        .size(14.dp)
                                                        .clip(RoundedCornerShape(5.dp))
                                                        .background(
                                                            when {
                                                                calcLabelTarget == "res" -> C.brand
                                                                calcNote.resultBefore.isNotBlank() || calcNote.resultAfter.isNotBlank() -> C.brandSoft
                                                                else -> C.chip
                                                            }
                                                        )
                                                        .clickable { openCalcLabel("res") }
                                                        .testTag("calcTag:res"),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    MdiIcon(
                                                        "tag", 9,
                                                        when {
                                                            calcLabelTarget == "res" -> C.onBrand
                                                            calcNote.resultBefore.isNotBlank() || calcNote.resultAfter.isNotBlank() -> C.brand
                                                            else -> C.sub
                                                        },
                                                    )
                                                }
                                                if (calcLabelTarget == "res") {
                                                    CalcLabelPopover(
                                                        text = calcLabelText,
                                                        onText = { calcLabelText = it },
                                                        pos = calcLabelPos,
                                                        onPos = { toggleCalcLabelPos(it) },
                                                        onDone = { commitCalcLabel() },
                                                        onCancel = { cancelCalcLabel() },
                                                    )
                                                }
                                            }
                                            if (calcNote.resultAfter.isNotBlank()) {
                                                Text(
                                                    calcNote.resultAfter,
                                                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.sub,
                                                    modifier = Modifier.clickable { openCalcLabel("res") },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // show your work — the live folds in ONE muted line
                        // (in text mode the "= total" preview lives here too)
                        if (calcStepList.isNotEmpty() || calcTotal != null) {
                            Text(
                                listOfNotNull(
                                    calcTotal?.let { "= ${money(it)}" },
                                    calcStepList.joinToString(" · ") { s -> "${s.expr} = ${money(s.value)}" },
                                ).joinToString("   "),
                                fontSize = F.tiny.sp, color = C.faint,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .testTag("calcSteps"),
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // the long-press CLIPBOARD menu (copy / cut / paste the
                        // active line — the native toolbar covers text mode)
                        if (clipTarget == "active") {
                            val lineText = serializeCalcNote(calcNote)
                            androidx.compose.material3.DropdownMenu(
                                expanded = true,
                                onDismissRequest = { clipTarget = null },
                                offset = DpOffset(0.dp, 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                containerColor = C.card,
                                tonalElevation = 0.dp,
                                shadowElevation = 6.dp,
                            ) {
                                DropdownItem(text = t("act.copy"), tag = "clipCopy") { copyLineText(lineText); clipTarget = null }
                                DropdownItem(text = t("act.cut"), tag = "clipCut") {
                                    copyLineText(lineText)
                                    calcNote = CalcNote(); calcText = ""; calcActive = -1
                                    clipTarget = null
                                }
                                DropdownItem(text = t("act.paste"), tag = "clipPaste") {
                                    val clip = Platform.share.readClipboard()
                                    if (!clip.isNullOrBlank()) {
                                        calcNote = parseCalcNote(clip)
                                        calcText = clip
                                        calcActive = -1
                                    }
                                    clipTarget = null
                                }
                            }
                        }
                    }
                }
                // RECENT — one-tap reuse of items booked before
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
                    if (effAmount > 0) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(t("lg.total"), fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C.sub)
                            Text(money(effAmount), fontSize = F.body.sp, fontWeight = FontWeight.ExtraBold, color = C.brand, modifier = Modifier.testTag("entryTotal"))
                        }
                    }
                }
            }
            // line items — editing an existing item opens the inline editor
            if (editingItem != null) {
                ItemEditor(
                    onAdd = { item -> items = items + item },
                    onUpdate = { item -> items = items.map { if (it.id == item.id) item else it } },
                    editing = editingItem,
                    onEditDone = { editingItem = null },
                )
            }
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
                        .clickable { editingItem = it }
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
            // date + method — date via the native Material calendar (v1.6.13)
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
            // v1.6.18 — ATTACHMENTS
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
            } // end of the scrolling form

            // v1.6.38 — THE MODE ROW + ADVANCED PANEL + KEYPAD, pinned at
            // the editor's bottom (calculator tab only). Fresh lines start
            // as TEXT; Aa/123 switches input; Σ reveals M+/M−/MR.
            if (amountMode == "calc") {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(C.card)
                                .border(1.dp, C.line, RoundedCornerShape(12.dp)),
                        ) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (calcMode == "text") C.brand else Color.Transparent)
                                    .clickable { enterTextMode() }
                                    .testTag("calcModeText")
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                            ) { Text("Aa", fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = if (calcMode == "text") C.onBrand else C.sub) }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (calcMode == "num") C.brand else Color.Transparent)
                                    .clickable { enterNumMode() }
                                    .testTag("calcModeNum")
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                            ) { Text("123", fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = if (calcMode == "num") C.onBrand else C.sub) }
                        }
                        if (calcAdvanced) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SmallCalcBtn("M+") { calcMemory = r2((calcMemory ?: 0.0) + (calcTotal ?: 0.0)) }
                                SmallCalcBtn("M−") { calcMemory = r2((calcMemory ?: 0.0) - (calcTotal ?: 0.0)) }
                                SmallCalcBtn("MR", enabled = calcMemory != null) {
                                    val m = calcMemory ?: return@SmallCalcBtn
                                    val toks = calcNote.tokens.toMutableList()
                                    if (calcActive in toks.indices) {
                                        toks[calcActive] = toks[calcActive].copy(number = trimNum(m), pct = false)
                                    } else {
                                        toks.add(CalcToken(number = trimNum(m)))
                                        calcActive = toks.lastIndex
                                    }
                                    calcNote = calcNote.copy(tokens = toks)
                                }
                                calcMemory?.let {
                                    Text(money(it), fontSize = F.tiny.sp, color = C.sub, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterVertically))
                                }
                            }
                        }
                    }
                    // the keypad — number mode only (text mode rides the
                    // system keyboard). 5 rows incl. AC % ± and the SMALL
                    // next-line key; digits are BIG and the modal does not
                    // grow (same capped 172–256dp band as before).
                    if (calcMode == "num") {
                        CalcPadArea(
                            onKey = { calcKey(it) },
                            onNext = { commitCalcLine() },
                            nextEnabled = calcNote.tokens.isNotEmpty(),
                        )
                    }
                }
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

/* ---------------- v1.6.38 — the canvas calculator's pad, popover, small buttons ---------------- */

/** A tiny menu row (the total's actions / the clipboard menu). */
@Composable
private fun DropdownItem(text: String, tag: String, onClick: () -> Unit) {
    val C = C()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .testTag(tag)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) { Text(text, fontSize = F.body.sp, color = C.ink, fontWeight = FontWeight.Medium) }
}

/** A very small pill button (M+ / M− / MR — the Σ advanced panel). */
@Composable
private fun SmallCalcBtn(label: String, enabled: Boolean = true, onPress: () -> Unit) {
    val C = C()
    Box(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (enabled) C.brandSoft else C.card)
            .border(1.dp, if (enabled) C.brand else C.line, RoundedCornerShape(9.dp))
            .clickable(enabled = enabled) { vibrate(longArrayOf(8)); onPress() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = F.tiny.sp, fontWeight = FontWeight.ExtraBold, color = if (enabled) C.brand else C.faint) }
}

/* The COMPACT label popover (user spec: "the small modal that shows to
   inter text is very ugly and it should be compact with very small
   btns"): a narrow card, a 38dp field, tiny Before/After chips and tiny
   ✕ / Done buttons — anchored at the number/result it labels. */
@Composable
private fun CalcLabelPopover(
    text: String,
    onText: (String) -> Unit,
    pos: String,
    onPos: (String) -> Unit,
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
        shape = RoundedCornerShape(12.dp),
        containerColor = C.card,
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            Modifier.widthIn(min = 208.dp, max = 264.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = onText,
                textStyle = TextStyle(fontSize = F.body.sp, color = C.ink),
                singleLine = true,
                cursorBrush = SolidColor(C.brand),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calcLabelField")
                    .focusRequester(fr)
                    .border(1.dp, C.line, RoundedCornerShape(9.dp))
                    .background(C.inputBg, RoundedCornerShape(9.dp))
                    .padding(horizontal = 10.dp)
                    .heightIn(min = 38.dp),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Row { Text(t("ee.calcDescribe"), fontSize = F.small.sp, color = C.faint) }
                        Box {}
                    } else inner()
                },
            )
            // Before / After — the label's position (ball 200 vs 200 birr)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("before" to t("ee.calcPosBefore"), "after" to t("ee.calcPosAfter")).forEach { (p, lbl) ->
                    Box(
                        Modifier
                            .height(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (pos == p) C.brand else C.chip)
                            .clickable { onPos(p) }
                            .testTag("calcPos:$p")
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(lbl, fontSize = F.tiny.sp, fontWeight = FontWeight.Bold, color = if (pos == p) C.onBrand else C.sub) }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconBtn("close", onCancel, size = 28)
                Box(
                    Modifier
                        .height(28.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(C.brand)
                        .clickable { onDone() }
                        .testTag("calcLabelDone"),
                    contentAlignment = Alignment.Center,
                ) { Text(t("act.done"), fontSize = F.tiny.sp, fontWeight = FontWeight.ExtraBold, color = C.onBrand) }
            }
        }
    }
}

/* The keypad (number mode): a 4×5 grid — AC ⌫ % ÷ / digits with × − + /
 ± 0 . ⏎. The ⏎ key IS the (small) NEXT LINE key — a normal key cell,
 not a bar (the rework spec). The band keeps the capped, flexing height
 (172–256dp) so the modal never grows; the DIGITS are big (24sp, was
 20 — the user's "keypad numbers are small"). */
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
            .padding(vertical = 4.dp)
            .heightIn(min = 172.dp, max = 256.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            listOf("AC", "⌫", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("±", "0", ".", "⏎"),
        ).forEach { rowKeys ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowKeys.forEach { k ->
                    if (k == "⏎") {
                        // the SMALL next-line key (the blueprint's Next as a key)
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
                        ) { MdiIcon("arrow-down", 20, C.onBrand.copy(alpha = if (nextEnabled) 1f else 0.6f)) }
                    } else {
                        CalcKey(
                            key = if (k == "−") "-" else k,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onPress = onKey,
                        )
                    }
                }
            }
        }
    }
}

/* One key of the numeric calculator pad — flat tile, brand-tinted
   operators, red backspace, blue % (the app calculator's language).
   v1.6.38: DIGITS 24sp (bigger glyphs, same cell — "the keypad numbers
   are small but don't increase the size of the modal"). */
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
                key == "AC" -> Color(0x1dd64545)
                key == "%" -> Color(0x1f4a7d9b)
                key == "±" -> Color(0x1f4a7d9b)
                op -> C.brandSoft
                else -> C.card
            })
            .border(
                1.dp,
                when {
                    key == "⌫" || key == "AC" -> C.red
                    key == "%" || key == "±" -> Color(0xFF4a7d9b)
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
        } else if (key == "AC") {
            Text("AC", fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C.red)
        } else {
            Text(
                display,
                fontSize = if (key.length == 1 && key[0].isDigit()) 24.sp else 21.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    op -> C.brand
                    key == "%" || key == "±" -> Color(0xFF4a7d9b)
                    else -> C.ink
                },
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

/* line item edit form (v1.6.38: still reachable by tapping an existing
   item row — the ADD tab is gone but editing what exists stays) */
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
 * Reminders), live from the store.
 */
@Composable
fun AttachmentPicker(refs: List<com.callmony.model.LinkRef>, onChange: (List<com.callmony.model.LinkRef>) -> Unit, onClose: () -> Unit) {
    val C = C()
    var tab by remember { mutableStateOf("list") }
    com.callmony.ui.components.BottomSheet(title = t("att.attachExisting"), onClose = onClose) {
        Column {
            com.callmony.ui.kit.Segmented(
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
