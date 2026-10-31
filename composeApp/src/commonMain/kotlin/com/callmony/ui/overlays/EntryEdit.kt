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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.currentLang
import com.callmony.i18n.isRTL
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.CalcNote
import com.callmony.lib.CalcToken
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.lib.calcLedgerTotal
import com.callmony.lib.calcSteps
import com.callmony.lib.dateOf
import com.callmony.lib.displayCalcNote
import com.callmony.lib.evalCalcTokens
import com.callmony.lib.money
import com.callmony.lib.now
import com.callmony.lib.parseCalcLedger
import com.callmony.lib.parseCalcNote
import com.callmony.lib.quoteTextNumbers
import com.callmony.lib.r2
import com.callmony.lib.serializeCalcLedger
import com.callmony.lib.serializeCalcNote
import com.callmony.lib.sumPayments
import com.callmony.lib.ownerNameOf
import com.callmony.lib.todayStr
import com.callmony.lib.uid
import com.callmony.platform.NativeDateTime
import com.callmony.platform.Platform
import com.callmony.model.ENTRY_CATEGORIES as CATS
import com.callmony.model.EntryItem
import com.callmony.model.LedgerEntry
import com.callmony.theme.F
import com.callmony.ui.components.NoImeNumberField
import com.callmony.ui.components.Sheet
import com.callmony.ui.components.imeLiftPadding
import com.callmony.ui.kit.AppBackHandler
import com.callmony.ui.kit.Btn
import com.callmony.ui.kit.C
import com.callmony.ui.kit.Chip
import com.callmony.ui.kit.Confirm
import com.callmony.ui.kit.Field
import com.callmony.ui.kit.HostSheets
import com.callmony.ui.kit.IconBtn
import com.callmony.ui.kit.MdiIcon
import com.callmony.ui.kit.RootBottomOverlays
import com.callmony.ui.kit.Toasts
import com.callmony.ui.kit.vibrate
import com.callmony.ui.components.ScrollRow
import com.callmony.ui.sections.openPaymentSheet

/* EntryEdit — the ledger entry editor. v1.6.40: numbers written in TEXT
   mode never calculate (they carry a quote marker through the save), the
   TOTAL sits AFTER the last calculation entry on the paper itself (no
   different background) and long-presses into the SAME payments modal as
   every individual line total — the original amount, the payment history
   with the new amount after each payment, and Make payment / Copy inside;
   make payment now visibly REDUCES the amount (paid · remaining live on
   the total and on paid lines) with quick buttons after the totals;
   locked lines show their FULL calculation (no clamp); the ✕ remove and
   a new VISIBLE clear button beside the Aa/123 toggle ask through a custom
   confirmation modal; the lock is a noticeable pill long-press unlocks;
   the cursor sits at the right place (end) after a toggle or Next line;
   the toggle matches the Save / Next-line height; Save KEEPS the editor
   open and autosave takes over after the first save.
   v1.6.39 base: full-screen canvas over the editor — one unified
   native-editable line in both modes (typed text can never disappear on
   a mode switch — the "ball" bug), the small Aa/123/Next-line/Save
   buttons on the canvas, a compact keypad the native back button closes,
   long-press unlocks a locked line, a copy icon on every line. ✕ or the
   native back button collapses the canvas to the form; kind chips,
   date/method, categories, due date, note, attachments, owner and groups
   live there, unchanged. */

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
    // the entry being edited: `existing` is the INITIAL snapshot (seeds the
    // form once); v1.6.40 — `currentEntry` is LIVE: after the FIRST Save of
    // a brand-new entry (Save no longer closes the editor) every later
    // write/autosave/payment reads and updates the SAME row — no duplicate
    // entries, and payments recorded from the calculator land on it while
    // the editor stays open underneath the payment sheet.
    val existing = entryId?.let { id -> AppStore.state.entries.find { it.id == id } }
    var createdId by remember { mutableStateOf<String?>(null) }
    val currentEntry = (entryId ?: createdId)?.let { id -> AppStore.state.entries.find { it.id == id } }
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
    // v1.6.39 — THE CANVAS CALCULATOR, rebuilt around ONE text source:
    //   calcText IS the active line — typed on the system QWERTY in text
    //   mode or built by the keypad in num mode, but the SAME line either
    //   way, so switching modes can never lose what was typed (the
    //   v1.6.38 "i type ball then click the number btn, the ball text
    //   disappeared" report died here: nothing re-serializes the field).
    //   calcNote / tokens / total / steps are all DERIVED from it, live.
    //   calcMode "text" (DEFAULT — system QWERTY) | "num" (the compact
    //   keypad; the field keeps native selection + copy/cut/paste).
    //   calcExpanded — the canvas is FULL SCREEN over every thing in the
    //   editor; ✕ / the native back button collapses it (back with the
    //   keypad up closes the keypad first).
    //   Locked ledger lines: long-press UNLOCKS a line for editing; every
    //   line carries a copy icon; each line's own total long-presses into
    //   the same menu as the last total (make payment / copy).
    var calcText by remember { mutableStateOf("") }         // the ACTIVE line
    var calcMode by remember { mutableStateOf("text") }     // "text" | "num"
    var calcExpanded by remember { mutableStateOf(true) }    // the full-screen canvas
    // v1.6.41 — THE CARET TOKEN: bumped whenever the active field should
    // (re)take focus — the canvas opening or raising, the Aa / 123 toggle,
    // Next line, an unlock — so the blinking caret is ALWAYS visible where
    // writing continues ("it should be there always"). Starts at 1: the
    // freshly opened canvas grabs focus immediately.
    var focusToken by remember { mutableStateOf(1) }
    // v1.6.40 — WHICH characters of the active line were typed on the
    // TEXT keyboard: a number carrying ANY of them is an annotation,
    // never math ("if it is in text mode it shouldn't calculate the number
    // that was written when it was in text mode"). Maintained by the
    // prefix/suffix diff in onCalcEdited — every mutation of the line
    // (QWERTY keystroke, keypad key, native paste/cut, ± re-serialize)
    // flows through it, so the indices survive every edit.
    var qwertyIdx by remember { mutableStateOf(setOf<Int>()) }
    // v1.6.40 — the text-mode cursor: remembered across the Aa ↔ 123
    // swaps so the blinking caret sits at the RIGHT place (the end where
    // writing continues), not reset before the first word (the report:
    // "when i toggle from number to text why the blinking thing goes
    // right before the first text"). null = default to the end.
    var calcSelection by remember { mutableStateOf<TextRange?>(null) }
    // v1.6.41 — UNDO / REDO of the calculator editing (every keystroke
    // through the funnel, every line commit / unlock / delete / clear):
    // a snapshot of (lines, text, qwerty marks) before each mutation, capped
    // at 60; redo is cleared by any new edit, as everywhere.
    var undoStack by remember { mutableStateOf(listOf<CalcSnap>()) }
    var redoStack by remember { mutableStateOf(listOf<CalcSnap>()) }
    var calcAdvanced by remember { mutableStateOf(false) }  // Σ panel
    var calcMemory by remember { mutableStateOf<Double?>(null) }
    // v1.6.40 — the shared PAYMENTS MODAL (long-press on the last total
    // AND on every individual line total): -1 = the grand total,
    // i = ledger line i, null = closed.
    var payModal by remember { mutableStateOf<Int?>(null) }
    // v1.6.42 — the PAYMENT GATE per entry (report #8: "the pay btn beside
    // the individual entrys should only be enabled after it gets
    // unlocked"): the indices of the ledger lines whose lock pill has been
    // TAPPED to unlock. A freshly committed line is locked — its PAY chip
    // sits disabled; one tap on the pill flips the whole entry block to the
    // unlocked look (different colour + separation) and arms the chip.
    // Long-press still pulls the line into the active field for editing,
    // exactly as before.
    var unlockedLines by remember { mutableStateOf(setOf<Int>()) }
    // v1.6.40 — payments remapped after a line was deleted or unlocked
    // (its payments stay recorded at entry level; later lines shift down)
    var paymentsOverride by remember { mutableStateOf<List<com.callmony.model.Payment>?>(null) }
    var catManager by remember { mutableStateOf(false) }
    var showOwnerPicker by remember { mutableStateOf(false) }

    // v1.6.39 — THE SUM, live: items + the calculator's whole contribution
    // (committed ledger lines + the active line). calcNote is DERIVED
    // from calcText through the free-text grammar, live.
    // v1.6.40 — the parse runs through quoteTextNumbers first: numbers
    // written on the TEXT keyboard are quoted → words, never math.
    val itemsSum = items.sumOf { it.qty * it.price }
    val calcNote = remember(calcText, qwertyIdx) { parseCalcNote(quoteTextNumbers(calcText, qwertyIdx)) }
    val calcTotal = remember(calcNote) { evalCalcTokens(calcNote.tokens) }
    val calcStepList = remember(calcNote) { calcSteps(calcNote.tokens) }
    val calcLedgerSum = remember(calcLines) { calcLedgerTotal(calcLines) }
    val calcPartsTotal = r2((calcLedgerSum ?: 0.0) + (calcTotal ?: 0.0))
    // THE ENTRY AMOUNT: every line item + the whole calculator
    val effAmount = r2(itemsSum + calcPartsTotal)

    // v1.6.40 — THE PAYMENTS, live: what has been paid against this entry
    // (recorded from the calculator's own Make payment or the ledger's
    // Record payment), and what each ledger line has been paid. The total
    // and the paid lines show paid · remaining — "the make payment is not
    // reducing the amount" now visibly reduces it.
    val entryPayments = paymentsOverride ?: currentEntry?.payments ?: emptyList()
    val paidTotal = sumPayments(entryPayments)
    val remainingTotal = r2(effAmount - paidTotal)

    /* the entry object exactly as Save/autosave/make-payment write it */
    fun buildEntry(id: String): LedgerEntry = LedgerEntry(
        id = id,
        ownerId = owner,
        type = type,
        kind = kindSel,
        amount = effAmount,
        items = items,
        payments = entryPayments,
        date = date.ifEmpty { todayStr() },
        dueDate = dueOn.takeIf { it }?.let { dueDate.ifEmpty { null } },
        category = category.ifEmpty { null },
        method = method,
        note = note.ifEmpty { null },
        // the whole calculator state rides along: every committed ledger
        // line + the still-active line (if any), "\n"-separated
        exprNote = serializeCalcLedger(
            calcLines + if (calcNote.tokens.isNotEmpty() || calcNote.text.isNotBlank()) listOf(calcNote) else emptyList()
        ).ifEmpty { null },
        groupId = groupSel,
        refs = refs,
        createdAt = currentEntry?.createdAt ?: now(),
        updatedAt = now(),
    )

    fun maybeDueReminder(id: String, wasNew: Boolean) {
        // auto payment-reminder at 09:00 on the due date (new entries only)
        if (wasNew && dueOn && dueDate.length >= 8) {
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

    /* v1.6.40 — SAVE KEEPS THE EDITOR OPEN ("when i click save it should
       not close and it should have auto save"): the first Save of a
       brand-new entry creates it and from then on the v1.6.38 AUTOSAVE
       owns every later change — 600 ms after the last keystroke and again
       on close. Invalid totals (<= 0) still refuse to save. */
    fun save(silent: Boolean = false) {
        if (effAmount <= 0) {
            if (!silent) Toasts.toast(t("lg.amountGreaterThanZero"), "error")
            return
        }
        val wasNew = currentEntry == null
        val id = currentEntry?.id ?: uid()
        if (wasNew) createdId = id
        AppStore.saveEntry(buildEntry(id))
        maybeDueReminder(id, wasNew)
        if (!silent) Toasts.toast(t("act.saved"))
    }

    /* v1.6.38 — AUTO-SAVE (user spec: "it should have auto save"): every
       change to an EXISTING entry persists ~600ms after the last
       keystroke and again on close — no Save needed, nothing is ever
       lost by backing out. New entries still Save explicitly (an
       unconfirmed draft must not appear in the book). Invalid totals
       (<= 0) never overwrite a good entry.
       v1.6.42 — the key is only COMPUTED for a live entry (report #1: the
       whole-ledger serialization ran on EVERY keystroke even for a
       brand-new, not-yet-saved entry — pure waste while typing). */
    // v1.6.38 — the DELETE guard: the dispose-time auto-save must never
    // resurrect an entry the user just deleted
    var deleted by remember { mutableStateOf(false) }
    val autoKey = remember { mutableStateOf<String?>(null) }
    val currentKey = if (currentEntry != null) listOf(
        effAmount, serializeCalcLedger(calcLines + if (calcNote.tokens.isNotEmpty() || calcNote.text.isNotBlank()) listOf(calcNote) else emptyList()),
        items.map { "${it.id}:${it.qty}:${it.price}" }, date, dueOn, dueDate, category, method, note, owner, groupSel,
        refs.map { "${it.type}:${it.id}" }, type, kindSel,
        entryPayments.map { "${it.id}:${it.amount}:${it.lineIdx}" },
    ).toString() else ""
    if (autoKey.value == null) autoKey.value = currentKey
    val initialKey = autoKey.value ?: currentKey
    // v1.6.40 — the entry is live after the FIRST Save too (currentEntry),
    // so a brand-new entry autosaves exactly like an opened one
    if (currentEntry != null) {
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
                    AppStore.saveEntry(buildEntry(currentEntry.id))
                }
            }
        }
    }

    /* v1.6.38 — "when clicking the total results it should have also the
       option to make payment of it" + v1.6.40: the payment is made
       against the LAST total or an individual LINE (lineIdx), the editor
       SAVES FIRST and STAYS OPEN underneath the payment sheet (HostSheets
       render above the overlays), and the total/paid lines update LIVE as
       the payment lands — the reduction is finally visible. */
    fun makePaymentOf(total: Double, lineIdx: Int? = null) {
        if (total <= 0) return
        val wasNew = currentEntry == null
        val id = currentEntry?.id ?: uid()
        if (effAmount > 0) {
            if (wasNew) createdId = id
            AppStore.saveEntry(buildEntry(id))
            maybeDueReminder(id, wasNew)
        }
        payModal = null
        // v1.6.41 — the payment can never go above the remaining total
        // ("when making payment it should not go above the total")
        openPaymentSheet(id, total, lineIdx, maxAmount = total)
    }

    /* v1.6.41 — UNDO / REDO: a snapshot of the whole calculator editing
       state (ledger lines + the active line + its QWERTY marks) rides the
       undo stack before every mutation — every funnel keystroke, every
       line commit / unlock / delete / clear. Restored snapshots never
       push again (undo ↔ redo just walk the two stacks). */
    fun pushUndo() {
        undoStack = (undoStack + CalcSnap(calcLines, calcText, qwertyIdx)).takeLast(60)
        redoStack = emptyList()
    }

    fun undoCalc() {
        if (undoStack.isEmpty()) return
        vibrate(longArrayOf(12))
        redoStack = redoStack + CalcSnap(calcLines, calcText, qwertyIdx)
        val s = undoStack.last()
        undoStack = undoStack.dropLast(1)
        calcLines = s.lines
        calcText = s.text
        qwertyIdx = s.qwerty
        calcSelection = null
        focusToken++
    }

    fun redoCalc() {
        if (redoStack.isEmpty()) return
        vibrate(longArrayOf(12))
        undoStack = undoStack + CalcSnap(calcLines, calcText, qwertyIdx)
        val s = redoStack.last()
        redoStack = redoStack.dropLast(1)
        calcLines = s.lines
        calcText = s.text
        qwertyIdx = s.qwerty
        calcSelection = null
        focusToken++
    }

    /* v1.6.39 — NEXT LINE (the small canvas button under the active
       line): commit the active line into the ledger — the old line LOCKS
       (long-press unlocks it again) — and the fresh line keeps the armed
       input method. v1.6.40 — a pure TEXT line (words only, or every
       number written in text mode) commits as an annotation line: kept
       on the note, never totalled. v1.6.41 — undoable, and the fresh
       line's caret is right there (token). */
    fun commitCalcLine() {
        val note = parseCalcNote(quoteTextNumbers(calcText, qwertyIdx))
        if (note.tokens.isEmpty() && note.text.isBlank()) return
        pushUndo()
        // v1.6.42 — the commit STAMPS THE DATE (report #10: "it should show
        // the date for each entry"): the line carries the day it was
        // committed, rendered beside it and persisted through the save. A
        // fresh line commits LOCKED — the pay gate opens with the pill.
        calcLines = calcLines + note.copy(date = todayStr())
        calcText = ""
        qwertyIdx = emptySet()
        calcSelection = TextRange(0)
        focusToken++ // the caret shows on the fresh line
    }

    /* v1.6.39 — long-press a LOCKED line and it UNLOCKS for editing: the
       line comes back down into the active field. Whatever was being
       typed commits first, so nothing is ever lost. v1.6.40 — the caret
       lands at the END (the right place to keep writing), the line's
       payments stay recorded at entry level, and the QWERTY marks reset
       (the serialized text carries its own quote markers). v1.6.41 —
       undoable. */
    fun unlockCalcLine(i: Int) {
        val line = calcLines.getOrNull(i) ?: return
        pushUndo()
        val rest = calcLines.filterIndexed { j, _ -> j != i }
        calcLines = if (calcNote.tokens.isNotEmpty() || calcNote.text.isNotBlank()) rest + calcNote else rest
        calcText = displayCalcNote(line)
        qwertyIdx = emptySet()
        calcSelection = TextRange(calcText.length)
        calcMode = "text"
        focusToken++
        paymentsOverride = entryPayments.map { p -> if (p.lineIdx == i) p.copy(lineIdx = null) else p }
        unlockedLines = unlockedLines.mapNotNull { u -> when {
            u == i -> null
            u > i -> u - 1
            else -> u
        } }.toSet()
    }

    /* v1.6.40 — deleting a line goes through the custom CONFIRMATION modal;
       the deleted line's payments stay recorded (they move to the entry
       level), later lines' payments shift down with them. v1.6.41 —
       undoable. */
    fun deleteCalcLine(i: Int) {
        if (i !in calcLines.indices) return
        pushUndo()
        calcLines = calcLines.filterIndexed { j, _ -> j != i }
        paymentsOverride = entryPayments.map { p ->
            when {
                p.lineIdx == i -> p.copy(lineIdx = null)
                p.lineIdx != null && p.lineIdx > i -> p.copy(lineIdx = p.lineIdx - 1)
                else -> p
            }
        }
        unlockedLines = unlockedLines.mapNotNull { u -> when {
            u == i -> null
            u > i -> u - 1
            else -> u
        } }.toSet()
    }

    fun copyLineText(text: String) {
        Platform.share.copyText(text)
        Toasts.toast(t("act.copied"))
    }

    /* v1.6.40 — THE ONE EDIT FUNNEL: every change to the active line —
       QWERTY keystroke, keypad key, native paste/cut, the ± re-serialize —
       lands here, and the diff (common prefix + suffix) keeps the
       text-keyboard index set ALIVE through arbitrary edits: untouched
       characters keep their marks, the retyped middle region is
       text-keyboard only when the change came from the QWERTY (a keypad
       or native-menu edit leaves the region unmarked). That set decides
       which numbers are annotations instead of math. v1.6.41 — every
       funnel edit is UNDOABLE (a snapshot rides the undo stack). */
    fun onCalcEdited(newText: String, fromQwerty: Boolean) {
        val old = calcText
        if (newText == old) return
        pushUndo()
        var p = 0
        while (p < old.length && p < newText.length && old[p] == newText[p]) p++
        var s = 0
        while (s < old.length - p && s < newText.length - p && old[old.length - 1 - s] == newText[newText.length - 1 - s]) s++
        val delta = newText.length - old.length
        qwertyIdx = buildSet {
            qwertyIdx.forEach { i ->
                when {
                    i < p -> add(i)
                    i >= old.length - s -> add(i + delta)
                    // inside the changed region: retyped — marks drop
                }
            }
            if (fromQwerty) for (i in p until newText.length - s) add(i)
        }
        calcText = newText
    }

    /* v1.6.39 — the keypad writes the SAME free-text grammar the QWERTY
       does (the unified active line): digits append — with a space after
       a word, so typing "ball" then the 2 0 0 keys becomes "ball 200"
       and the label sticks; operators land as standalone tokens; ⌫
       deletes the last meaningful character (a trailing operator dies as
       soon as the digits to its right are gone — the v1.6.38 ⌫ fix is
       inherent now: everything is text); % marks the number the line
       ends with; ± flips its sign (swapping a + / − operator between
       numbers, gluing a − otherwise); AC clears the line; = opens the
       result zone (the words after = label the total, never math).
       v1.6.40 — every mutation flows through onCalcEdited (keypad source:
       the new characters carry NO text-keyboard mark, so keypad numbers
       keep calculating) and resets the caret to the end. */
    fun calcKey(k: String) {
        when (k) {
            "AC" -> { calcText = ""; qwertyIdx = emptySet() }
            "⌫" -> onCalcEdited(calcText.trimEnd().dropLast(1), false)
            ".", in "0".."9" -> {
                val sep = if (calcText.isNotEmpty() && (calcText.last().isLetter() || calcText.last() == '%')) " " else ""
                onCalcEdited(calcText + sep + k, false)
            }
            "+", "-", "×", "÷" -> {
                val base = calcText.trimEnd()
                if (base.isEmpty()) {
                    if (k == "-") onCalcEdited("-", false) // a leading minus starts a negative number
                    return
                }
                // a trailing operator is REPLACED, never doubled; the
                // trailing space keeps the next digit a standalone token
                onCalcEdited((if (base.last() in "+-−×÷") base.dropLast(1) + k else "$base $k") + " ", false)
            }
            "%" -> {
                val base = calcText.trimEnd()
                if (base.isNotEmpty() && base.last().isDigit()) onCalcEdited(base + "%", false)
            }
            "±" -> {
                val note = parseCalcNote(quoteTextNumbers(calcText, qwertyIdx))
                val idx = note.tokens.indexOfLast { it.number.isNotBlank() }
                if (idx < 0) return
                val toks = note.tokens.toMutableList()
                val prevOp = if (idx > 0) toks[idx - 1].op else null
                when {
                    // between numbers the + ↔ − swap IS the sign flip
                    prevOp == "+" || prevOp == "-" ->
                        toks[idx - 1] = toks[idx - 1].copy(op = if (prevOp == "+") "-" else "+")
                    // otherwise glue/toggle a − onto the number itself
                    else -> {
                        val n = toks[idx].number
                        toks[idx] = toks[idx].copy(number = if (n.startsWith("-")) n.drop(1) else "-$n")
                    }
                }
                onCalcEdited(serializeCalcNote(note.copy(tokens = toks)), false)
            }
            "=" -> onCalcEdited(calcText.trimEnd() + " = ", false)
        }
        calcSelection = null // external edit → the caret defaults to the end
    }

    /* v1.6.39 — mode switching just arms the INPUT METHOD: the field is
       the same line in both modes (the QWERTY BasicTextField in text
       mode, the IME-suppressed native field in num mode — both with
       native selection + copy/cut/paste), and calcText carries over
       untouched: nothing re-serializes it, so nothing can disappear.
       v1.6.41 — every switch bumps the CARET TOKEN: the freshly armed
       field takes focus again (the num field via real view focus — no
       IME — the text field with the system keyboard). */
    val calcFieldFocus = remember { FocusRequester() }
    val ime = LocalSoftwareKeyboardController.current
    val focusMgr = LocalFocusManager.current
    fun enterTextMode() {
        calcMode = "text"
        focusToken++
    }
    fun enterNumMode() {
        if (calcMode != "num") {
            calcMode = "num"
            focusToken++
            ime?.hide()
        }
    }

    /* v1.6.39 — the native BACK button closes the keypad (the user's
       spec): the line stays, both keyboards are gone; Aa / 123 arm one
       again. */
    fun closeKeypad() {
        calcMode = "text"
        focusMgr.clearFocus()
        ime?.hide()
    }

    /* v1.6.39 — collapse the full-screen canvas down to the form.
       v1.6.41 — raising it again re-arms the caret. */
    fun collapseCanvas() {
        calcExpanded = false
        focusMgr.clearFocus()
        ime?.hide()
    }

    /* v1.6.41 — THE CARET, ALWAYS: whenever the token bumps, the active
       field of the CURRENT mode takes focus — text mode via the Compose
       focus request, number mode through the field's own token param
       (real view focus inside the Android actual). The double request
       (immediately + after the host-sheet / overlay focus-clears settle)
       beats the OverlayHost's open-clear and any sheet pop. */
    LaunchedEffect(focusToken, calcExpanded, calcMode) {
        if (!calcExpanded || focusToken == 0) return@LaunchedEffect
        if (calcMode == "text") {
            try { calcFieldFocus.requestFocus() } catch (_: Exception) {}
            kotlinx.coroutines.delay(200)
            try { calcFieldFocus.requestFocus() } catch (_: Exception) {}
        }
    }

    Sheet(
        title = if (currentEntry == null) t("lg.newEntry") else t("lg.editEntry"),
        subtitle = ownerNameOf(ownerId),
        onBack = pop,
        scrollable = false,
        // v1.6.38 — the footer buttons are SMALL (the rework spec: "the
        // next line and save btn should be very small"). v1.6.41 — the
        // DELETE-ENTRY button is GONE ("don't want the delete entry btn") —
        // an entry is deleted from the ledger list, never mid-edit; the
        // footer is Save alone.
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    /* v1.6.39 — the canvas is FULL SCREEN over every thing
                       in the editor while it is open (the canvas block
                       rendered after the Sheet). This is its collapsed
                       seat in the form — shown ONLY while the canvas is
                       DOWN; tap to raise it back over everything. */
                    if (!calcExpanded) {
                        Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(C.brand.copy(alpha = 0.06f))
                            .border(1.dp, C.line, RoundedCornerShape(14.dp))
                            .clickable { calcExpanded = true; focusToken++ }
                            .testTag("calcCollapsed")
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MdiIcon("calculator-variant", 17, C.brand)
                        Column(Modifier.weight(1f)) {
                            Text(t("ee.calcLabel"), fontSize = F.small.sp, color = C.sub, fontWeight = FontWeight.SemiBold)
                            val preview = calcLines.firstOrNull()?.let { serializeCalcNote(it) }
                                ?: calcText.ifEmpty { null }
                            if (preview != null) {
                                Text(preview, fontSize = F.tiny.sp, color = C.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Text(money(effAmount), fontSize = F.body.sp, fontWeight = FontWeight.ExtraBold, color = C.brand)
                        MdiIcon("chevron-right", 18, C.faint)
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

        }
    }

    // v1.6.39 — THE FULL-SCREEN CANVAS: over every thing in the editor —
    // the form, the pinned bands, the footer. ✕ (or the native back
    // button) collapses it down to the form; back with the keypad up
    // closes the keypad first; Aa / 123 arm the input method; Next line
    // and Save are SMALL buttons ON the canvas, on the line under the
    // active one (the user's v1.6.39 spec).
    // v1.6.41 — the canvas is RTL-AWARE ("it should support also right to
    // left" — the whole canvas mirrors for Arabic), its header is VERY
    // COMPACT with UNDO / REDO beside the collapse chevron, and while the
    // NUMBER KEYPAD is armed it renders at the SHELL ROOT — covering the
    // bottom navigation exactly like the system keyboard does — while the
    // canvas reserves exactly the band it covers.
    if (amountMode == "calc" && calcExpanded) {
        AppBackHandler(enabled = true) {
            when {
                calcMode == "num" -> closeKeypad()
                else -> collapseCanvas()
            }
        }
        // v1.6.41 — the number keypad renders at the ROOT (over the nav);
        // hide it while a payments modal / host sheet is open so nothing
        // floats under it.
        val wantPad = calcMode == "num" && payModal == null && HostSheets.stack.isEmpty()
        LaunchedEffect(wantPad) {
            RootBottomOverlays.content.value = if (wantPad) {
                { CalcKeypadHostContent(onKey = { calcKey(it) }, rtl = isRTL(currentLang().code)) }
            } else {
                RootBottomOverlays.overlayTopPx.value = -1f
                null
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                RootBottomOverlays.content.value = null
                RootBottomOverlays.lift.value = 0.dp
                RootBottomOverlays.overlayTopPx.value = -1f
            }
        }
        // v1.6.43 — the band of the content area the root keypad covers is
        // now MEASURED DIRECTLY: the keypad reports its own top edge in
        // window pixels (RootBottomOverlays.overlayTopPx) and the canvas
        // reports its own bottom edge — the difference is exactly the
        // covered band, whatever the phone's navigation mode (gesture bar,
        // 3-button, nothing) and whatever insets the shell consumed on the
        // way down. The old CALC_PAD_H-minus-navBelow arithmetic silently
        // assumed the keypad reached the window BOTTOM; v1.6.43 lifts the
        // keys ABOVE the phone's system navigation bar (navigationBarsPadding
        // inside the host), so the band grew by exactly that system inset —
        // which the direct measurement absorbs for free.
        val density = LocalDensity.current
        var canvasBottomPx by remember { mutableStateOf(-1f) }
        val overlayTopPx = RootBottomOverlays.overlayTopPx.value
        // covered band = (the content area's bottom edge) − (the keypad's
        // top edge): positive exactly while the keypad reaches UP past the
        // app's bottom nav into the canvas. Inset-independent by
        // construction — the sysNav slice the keypad no longer occupies and
        // the sysNav slice the app nav gained cancel out.
        val padLift = if (wantPad && overlayTopPx >= 0f && canvasBottomPx >= 0f) {
            with(density) { (canvasBottomPx - overlayTopPx).coerceAtLeast(0f).toDp() }
        } else 0.dp
        LaunchedEffect(padLift) { RootBottomOverlays.lift.value = padLift }
        CompositionLocalProvider(
            LocalLayoutDirection provides if (isRTL(currentLang().code)) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
        Column(
            Modifier
                .fillMaxSize()
                // OUTSIDE the paddings: reports the canvas's TRUE bottom edge
                // (the top of the app's bottom nav) — v1.6.43 pairs it with
                // RootBottomOverlays.overlayTopPx (the keypad's measured top)
                // so the reserved band is the exact covered band, and a
                // padLift change can never feed back into it (the padding it
                // feeds sits below this measurement point)
                .onGloballyPositioned { c ->
                    val b = c.positionInWindow().y + c.size.height
                    if (b != canvasBottomPx) canvasBottomPx = b
                }
                .background(C.canvas)
                .imeLiftPadding()
                .padding(bottom = padLift),
        ) {
            // header — v1.6.41: VERY COMPACT (38dp band, 32/28dp controls,
            // small title) with UNDO / REDO right beside the collapse
            // chevron ("need undo,redo btns at the top in header and make
            // the header very compact, so it won't take too much space").
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .height(38.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { collapseCanvas() }
                        .testTag("calcCollapse"),
                    contentAlignment = Alignment.Center,
                ) { MdiIcon("chevron-down", 18, C.sub) }
                // UNDO — disabled when the stack is empty
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (undoStack.isNotEmpty()) C.brandSoft else Color.Transparent)
                        .clickable(enabled = undoStack.isNotEmpty()) { undoCalc() }
                        .testTag("calcBtnUndo"),
                    contentAlignment = Alignment.Center,
                ) { MdiIcon("undo", 16, if (undoStack.isNotEmpty()) C.brand else C.faint) }
                // REDO — disabled when the stack is empty
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (redoStack.isNotEmpty()) C.brandSoft else Color.Transparent)
                        .clickable(enabled = redoStack.isNotEmpty()) { redoCalc() }
                        .testTag("calcBtnRedo"),
                    contentAlignment = Alignment.Center,
                ) { MdiIcon("redo", 16, if (redoStack.isNotEmpty()) C.brand else C.faint) }
                Text(
                    t("ee.calcLabel"),
                    fontSize = F.small.sp,
                    fontWeight = FontWeight.Bold,
                    color = C.sub,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (calcAdvanced) C.brandSoft else C.card)
                        .border(1.dp, if (calcAdvanced) C.brand else C.line, RoundedCornerShape(9.dp))
                        .clickable { calcAdvanced = !calcAdvanced }
                        .testTag("calcAdvanced"),
                    contentAlignment = Alignment.Center,
                ) { MdiIcon("sigma", 15, if (calcAdvanced) C.brand else C.sub) }
            }
            // THE LEDGER — committed (locked) lines above the active line.
            // v1.6.39: long-press UNLOCKS a line back into the active
            // field; every line carries a copy icon (locked or not).
            // v1.6.40: locked lines show their FULL calculation (no
            // clamping — "it should show the full calculation when it is
            // locked, dont clamp it"), the lock is a NOTICEABLE brand pill
            // that begs to be pressed (long-press unlocks), each line's own
            // total opens the SAME payments modal as the last total (tap or
            // long-press), and removing a line asks through the custom
            // confirmation modal.
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp)
                    .padding(top = 4.dp, bottom = 8.dp),
            ) {
                calcLines.forEachIndexed { i, line ->
                    val lineTotal = evalCalcTokens(line.tokens) // null = pure annotation line
                    val linePays = entryPayments.filter { it.lineIdx == i }
                    val linePaid = sumPayments(linePays)
                    val lineRemain = lineTotal?.let { r2(it - linePaid) }
                    val lineShown = if (linePaid > 0.005) lineRemain else lineTotal
                    // v1.6.42 — the PAY GATE: a committed line is LOCKED (its
                    // pay chip + total sit disabled); tapping the lock pill
                    // UNLOCKS the entry — different colours, separation, and
                    // the pay chip arms ("should only be enabled after it gets
                    // unlocked"). Long-press still edits the line.
                    val isUnlocked = i in unlockedLines
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            // v1.6.42 — the locked/unlocked SEPARATION: the
                            // whole entry block carries its state colour
                            .background(
                                if (isUnlocked) C.brand.copy(alpha = 0.07f)
                                else C.cardAlt.copy(alpha = 0.35f),
                            )
                            .combinedClickable(
                                onClick = {}, // long-press unlocks into editing (v1.6.39)
                                onLongClick = { vibrate(longArrayOf(20)); unlockCalcLine(i) },
                            )
                            .testTag("calcLine:$i")
                            .padding(horizontal = 4.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // v1.6.40 — the lock is a NOTICEABLE pill; v1.6.42 — a
                        // TAP flips the entry's lock state (the pay gate),
                        // long-press still unlocks the line into editing. The
                        // icon + colour say the state: brand padlock = locked,
                        // green open padlock = unlocked (payments enabled).
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isUnlocked) C.green.copy(alpha = 0.14f) else C.brandSoft)
                                .border(1.dp, if (isUnlocked) C.green else C.brand, RoundedCornerShape(10.dp))
                                .combinedClickable(
                                    onClick = {
                                        vibrate(longArrayOf(14))
                                        unlockedLines = if (isUnlocked) unlockedLines - i else unlockedLines + i
                                    },
                                    onLongClick = { vibrate(longArrayOf(20)); unlockCalcLine(i) },
                                )
                                .testTag("calcLineLock:$i"),
                            contentAlignment = Alignment.Center,
                        ) { MdiIcon(if (isUnlocked) "lock-off-outline" else "lock-outline", 16, if (isUnlocked) C.green else C.brand) }
                        Column(Modifier.weight(1f)) {
                            // the FULL calculation, wrapping — never clamped;
                            // v1.6.41 — the TEXT-mode pieces (the quoted
                            // numbers) render in their own colour;
                            // v1.6.42 — the commit DATE rides beside it
                            // (report #10), the date marker never in the text
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    coloredLockedLine(displayCalcNote(line), C.sub, TEXTMODE_COLOR),
                                    fontSize = F.small.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                                if (line.date.isNotEmpty()) {
                                    Text(
                                        com.callmony.lib.fd(line.date),
                                        fontSize = F.tiny.sp, color = C.faint, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.testTag("calcLineDate:$i"),
                                    )
                                }
                            }
                            // v1.6.42 — the entry's PAYMENT HISTORY sits BEFORE
                            // (above) ITS TOTAL as a list with the FULL note
                            // (report #8), headed by the lock state — locked
                            // and unlocked entries render in their own colours
                            // with a clear separation — and separated from the
                            // total by a hairline.
                            if (linePays.isNotEmpty()) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 3.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isUnlocked) C.brand.copy(alpha = 0.08f)
                                            else C.cardAlt.copy(alpha = 0.4f),
                                        )
                                        .testTag("calcLineHist:$i"),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        MdiIcon(
                                            if (isUnlocked) "lock-off-outline" else "lock-outline",
                                            11,
                                            if (isUnlocked) C.green else C.sub,
                                        )
                                        Text(
                                            t(if (isUnlocked) "ee.calcEntryUnlocked" else "ee.calcEntryLocked"),
                                            fontSize = F.tiny.sp,
                                            color = if (isUnlocked) C.green else C.sub,
                                            fontWeight = FontWeight.ExtraBold,
                                        )
                                    }
                                    var run = lineTotal
                                    linePays.forEachIndexed { pi, p ->
                                        run = r2((run ?: 0.0) - p.amount)
                                        PaymentHistoryRow(
                                            date = com.callmony.lib.fd(p.date),
                                            amount = p.amount,
                                            note = p.note,
                                            remaining = run,
                                            tag = "calcLinePayHist:$i:$pi",
                                            onOpen = { if (isUnlocked) payModal = i },
                                        )
                                    }
                                }
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .height(1.dp)
                                        .background(C.line),
                                )
                            }
                            // v1.6.41 — the line's OWN total sits BELOW the
                            // calculation ("when calculation line is locked it
                            // own total should show below it not on it's
                            // side"); v1.6.42 — the PAY button beside it is
                            // ENABLED ONLY ON THE UNLOCKED ENTRY (locked =
                            // greyed, inert), same for tapping the total.
                            if (lineShown != null) {
                                Row(
                                    Modifier.padding(top = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        money(lineShown),
                                        fontSize = F.small.sp, color = if (isUnlocked) C.ink else C.faint, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .combinedClickable(
                                                enabled = isUnlocked,
                                                onClick = { payModal = i },
                                                onLongClick = { vibrate(longArrayOf(20)); payModal = i },
                                            )
                                            .testTag("calcLineTotal:$i")
                                            .padding(horizontal = 2.dp, vertical = 2.dp),
                                    )
                                    PayChip(tag = "calcLinePay:$i", enabled = isUnlocked) { payModal = i }
                                    if (linePaid > 0.005) {
                                        Text(
                                            "${t("lg.paid")} ${money(linePaid)}",
                                            fontSize = F.tiny.sp, color = C.faint,
                                        )
                                    }
                                }
                            }
                        }
                        // COPY — every calculation line, locked or unlocked
                        CopyBtn(tag = "calcLineCopy:$i") { copyLineText(displayCalcNote(line)) }
                        // v1.6.40 — removing asks first (custom modal)
                        DeleteBtn(tag = "calcLineDelete:$i") {
                            Confirm.ask(t("ee.calcDeleteLineTitle"), t("ee.calcDeleteLineMsg"), t("act.delete"), danger = true) { ok ->
                                if (ok) deleteCalcLine(i)
                            }
                        }
                    }
                }
                // THE ACTIVE LINE — ONE native editable field in BOTH modes
                // (v1.6.39): text mode rides the system QWERTY; num mode
                // shows the SAME line in the IME-suppressed native field.
                // Long-press opens the NATIVE Copy · Cut · Paste ·
                // Select-all menu with selection handles either way —
                // exactly like any other input (the user's spec).
                // v1.6.40 — text mode is a TextFieldValue whose SELECTION
                // survives the Aa ↔ 123 swaps: after a toggle (or Next line)
                // the blinking caret sits where writing continues — the END
                // — instead of jumping before the first word. Edits from
                // EITHER field land in onCalcEdited, so text-keyboard
                // numbers are annotations and keypad numbers calculate.
                // v1.6.41 — WHAT WAS WRITTEN IN TEXT MODE renders in its own
                // colour (visual transformation in text mode, spans in the
                // native field in num mode), and the num field carries the
                // focus token so its caret is always live.
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.weight(1f)) {
                        if (calcMode == "text") {
                            // read in composition so colour changes recompose
                            val qwertyMarks = qwertyIdx
                            val inkColor = C.ink
                            val modeColor = VisualTransformation { src ->
                                val b = AnnotatedString.Builder(src.text)
                                var i = 0
                                while (i < src.text.length) {
                                    val q = i in qwertyMarks
                                    var j = i
                                    while (j < src.text.length && (j in qwertyMarks) == q) j++
                                    b.addStyle(SpanStyle(color = if (q) TEXTMODE_COLOR else inkColor), i, j)
                                    i = j
                                }
                                TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
                            }
                            BasicTextField(
                                value = TextFieldValue(calcText, calcSelection ?: TextRange(calcText.length)),
                                onValueChange = { v ->
                                    calcSelection = v.selection
                                    onCalcEdited(v.text, fromQwerty = true)
                                },
                                // v1.6.42 — NO PLACEHOLDER (report #2: "when i
                                // click the next line it is not showing it just
                                // shows the placeholder example. and we don't
                                // need the place holder"): the old decorationBox
                                // REPLACED the field with example text whenever
                                // the line was empty — after Next line the fresh
                                // line showed the placeholder INSTEAD of the
                                // caret. The field now always renders (caret
                                // included) and keeps a stable 44dp write area,
                                // and textDirection = Content makes the FIRST
                                // typed word set the paragraph direction (report
                                // #5: RTL "not working when i type text at first"
                                // — Arabic text starts from the right end now,
                                // exactly like the number field already did).
                                textStyle = TextStyle(
                                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.ink,
                                    textDirection = androidx.compose.ui.text.style.TextDirection.Content,
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                cursorBrush = SolidColor(C.brand),
                                visualTransformation = modeColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .testTag("calcTextField")
                                    .focusRequester(calcFieldFocus),
                            )
                        } else {
                            NoImeNumberField(
                                value = calcText,
                                onValueChange = { onCalcEdited(it, fromQwerty = false) },
                                fontSizeSp = 17,
                                textColor = C.ink,
                                cursorColor = C.brand,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag("calcNumField"),
                                alignStart = true,
                                maxLines = 3,
                                highlight = qwertyIdx,
                                highlightColor = TEXTMODE_COLOR,
                                focusToken = focusToken,
                            )
                        }
                    }
                    // the active line's copy icon — locked or not, same as
                    // every committed line (v1.6.39)
                    CopyBtn(tag = "calcActiveCopy") { copyLineText(calcText) }
                }
                // the live result + the show-your-work steps
                if (calcTotal != null) {
                    Text(
                        "= ${money(calcTotal)}",
                        fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = C.brand,
                        modifier = Modifier.padding(top = 6.dp).testTag("calcTotal"),
                    )
                }
                if (calcStepList.isNotEmpty()) {
                    Text(
                        calcStepList.joinToString("   ") { s -> "${s.expr} = ${money(s.value)}" },
                        fontSize = F.tiny.sp, color = C.faint,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("calcSteps"),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // THE TOTAL — v1.6.40: ALWAYS AFTER THE LAST CALCULATION ENTRY
            // (it lived at the top of the canvas before), printed straight
            // on the paper — NO different background.
            // v1.6.42 — REPORT #6 ("I don't want Pay chip and payment history
            // beside and bottom of the sum total. it enough if it shows how
            // much is paid and how much is remaining like it do now"): the
            // sum total is a pure read-out again — NO pay chip beside it, NO
            // payment-history rows under it, no payments modal. Payments are
            // made on the individual entries (their own unlocked PAY chips,
            // report #8); the sum simply shows Paid · Remaining once anything
            // is paid.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(top = 2.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .testTag("calcLedgerTotal")
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(t("ee.calcTotal"), fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C.sub)
                    Spacer(Modifier.weight(1f))
                    if (itemsSum > 0.0) {
                        Text("+ ${money(itemsSum)}", fontSize = F.tiny.sp, color = C.faint)
                    }
                    Text(money(effAmount), fontSize = F.body.sp, fontWeight = FontWeight.ExtraBold, color = C.brand)
                }
                if (paidTotal > 0.005) {
                    Text(
                        "${t("lg.paid")} ${money(paidTotal)} · ${t("lg.remaining")} ${money(remainingTotal)}",
                        fontSize = F.tiny.sp, color = C.faint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("calcTotalPaidLine")
                            .padding(horizontal = 4.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
            // THE SMALL CONTROLS — ON the canvas, on the NEXT LINE under
            // the active one, and SMALL (the user's v1.6.39 spec): the
            // Aa / 123 input method, Next, Save.
            // v1.6.40 — the toggle matches the Save / Next HEIGHT.
            // v1.6.42 — REPORT #4: the clear-✕ beside the Aa/123 toggle is
            // REMOVED ("want to remove x btn beside the text and numbers
            // toggle that is for clear the line") — AC on the keypad and
            // ⌫ already clear the active line; report #9 renamed the
            // "Next line" button to plain "Next".
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(C.card)
                        .border(1.dp, C.line, RoundedCornerShape(9.dp)),
                ) {
                    Box(
                        Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (calcMode == "text") C.brand else Color.Transparent)
                            .clickable { enterTextMode() }
                            .testTag("calcModeText")
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Aa", fontSize = F.tiny.sp, fontWeight = FontWeight.ExtraBold, color = if (calcMode == "text") C.onBrand else C.sub) }
                    Box(
                        Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (calcMode == "num") C.brand else Color.Transparent)
                            .clickable { enterNumMode() }
                            .testTag("calcModeNum")
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("123", fontSize = F.tiny.sp, fontWeight = FontWeight.ExtraBold, color = if (calcMode == "num") C.onBrand else C.sub) }
                }
                Spacer(Modifier.weight(1f))
                // Next — SMALL (v1.6.40: a text-only line commits as an
                // annotation line too — only a blank line is disabled)
                val nextEnabled = calcText.isNotBlank()
                Box(
                    Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (nextEnabled) C.brandSoft else C.card)
                        .border(1.dp, if (nextEnabled) C.brand else C.line, RoundedCornerShape(9.dp))
                        .clickable(enabled = nextEnabled) { vibrate(longArrayOf(8)); commitCalcLine() }
                        .testTag("calcBtnNext")
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(t("ee.calcNext"), fontSize = F.tiny.sp, fontWeight = FontWeight.ExtraBold, color = if (nextEnabled) C.brand else C.faint) }
                // Save — SMALL (the canvas's own; the footer's stays for
                // the collapsed form)
                Box(
                    Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (effAmount > 0) C.brand else C.brand.copy(alpha = 0.25f))
                        .clickable(enabled = effAmount > 0) { save() }
                        .testTag("calcBtnSave")
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(t("act.save"), fontSize = F.tiny.sp, fontWeight = FontWeight.ExtraBold, color = C.onBrand) }
            }
            // the Σ advanced panel — M+ / M− / MR + the memory readout
            if (calcAdvanced) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SmallCalcBtn("M+") { calcMemory = r2((calcMemory ?: 0.0) + (calcTotal ?: 0.0)) }
                    SmallCalcBtn("M−") { calcMemory = r2((calcMemory ?: 0.0) - (calcTotal ?: 0.0)) }
                    SmallCalcBtn("MR", enabled = calcMemory != null) {
                        val m = calcMemory ?: return@SmallCalcBtn
                        val base = calcText.trimEnd()
                        onCalcEdited(if (base.isEmpty()) trimNum(m) else "$base ${trimNum(m)}", false)
                    }
                    calcMemory?.let {
                        Text(money(it), fontSize = F.tiny.sp, color = C.sub, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterVertically))
                    }
                }
            }
            // v1.6.41 — the keypad itself no longer lives in the canvas: it
            // renders at the SHELL ROOT — covering the bottom navigation
            // exactly like the system keyboard does — while the canvas
            // reserves the band it covers (padLift above). The native back
            // button still closes it (the canvas's own back handler).
        }
        } // end of the RTL-aware canvas
    }

    // v1.6.40 — THE SHARED PAYMENTS MODAL (the long-press target of the
    // LAST total and of every individual line total): the ORIGINAL amount
    // of the calculation stays the headline, the list shows every recorded
    // payment WITH THE NEW AMOUNT after it ("the new amount history"), and
    // Make payment / Copy sit at the bottom. The editor stays open beneath
    // it; recording a payment updates the total's paid · remaining live.
    // v1.6.41 — COMPACT ("its modal should be very compact it has to much
    // things in it"): one summary line, tight history rows, one action row
    // — and the Make payment opens the sheet CAPPED at the remaining
    // ("when making payment it should not go above the total"). RTL-aware.
    if (payModal != null) {
        val modalLine = payModal?.takeIf { it >= 0 }
        val modalOriginal = if (modalLine != null) {
            evalCalcTokens(calcLines.getOrNull(modalLine)?.tokens ?: emptyList()) ?: 0.0
        } else effAmount
        val modalPays = if (modalLine != null) entryPayments.filter { it.lineIdx == modalLine } else entryPayments
        val modalPaid = sumPayments(modalPays)
        val modalRemain = r2(modalOriginal - modalPaid)
        val modalPayAmount = if (modalRemain > 0) modalRemain else modalOriginal
        CompositionLocalProvider(
            LocalLayoutDirection provides if (isRTL(currentLang().code)) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
        com.callmony.ui.components.BottomSheet(
            title = "${if (modalLine != null) t("ee.calcLineTotal") else t("ee.calcTotal")} · ${money(modalOriginal)}",
            onClose = { payModal = null },
        ) {
            Column {
                // ONE compact summary line — original · paid · remaining
                Text(
                    "${t("ee.calcOriginal")}: ${money(modalOriginal)} · ${t("lg.paid")} ${money(modalPaid)} · ${t("lg.remaining")} ${money(modalRemain)}",
                    fontSize = F.tiny.sp, color = C.sub, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                // THE PAYMENT HISTORY — tight rows, the note and the new
                // amount it left on each
                if (modalPays.isEmpty()) {
                    Text(t("ee.calcNoPayments"), fontSize = F.tiny.sp, color = C.faint, modifier = Modifier.padding(bottom = 6.dp))
                } else {
                    var run = modalOriginal
                    modalPays.forEachIndexed { pi, p ->
                        run = r2(run - p.amount)
                        PaymentHistoryRow(
                            date = com.callmony.lib.fd(p.date),
                            amount = p.amount,
                            note = p.note,
                            remaining = run,
                            tag = "calcModalPayHist:$pi",
                            onOpen = {},
                        )
                    }
                }
                // THE ACTIONS — one compact row
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn(
                        small = true, label = "${t("ee.calcPay")} · ${money(modalPayAmount)}", variant = "soft",
                        onPress = { makePaymentOf(modalPayAmount, modalLine) },
                        modifier = Modifier.weight(1f),
                    )
                    Btn(
                        small = true, label = t("ee.calcCopyTotal"), variant = "secondary",
                        onPress = { copyLineText(money(modalOriginal)); payModal = null },
                        modifier = Modifier.weight(1f),
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

/* ---------------- v1.6.38 — the canvas calculator's small buttons ---------------- */

/** A very small pill button (M+ / M− / MR — the Σ advanced panel; v1.6.40
 *  also the Make-payment / Payments chips under the total). */
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

/* v1.6.39 — the per-line COPY icon: every calculation line carries
   one, locked or unlocked (the user's spec: "each calculation line
   should have copy icons locked or unlocked"). */
@Composable
private fun CopyBtn(tag: String, onPress: () -> Unit) {
    val C = C()
    Box(
        Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onPress() }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) { MdiIcon("content-copy", 13, C.sub) }
}

/* v1.6.40 — the per-line REMOVE button: a small red ✕ that asks through
   the custom confirmation modal before the line is deleted. */
@Composable
private fun DeleteBtn(tag: String, onPress: () -> Unit) {
    val C = C()
    Box(
        Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onPress() }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) { MdiIcon("close", 13, C.red) }
}

/* v1.6.41 — THE ROOT-LEVEL KEYPAD HOST: while number mode is armed the
   calculator's keypad pins to the shell's root slot — AFTER the bottom
   navigation — so it COVERS the nav exactly like the system IME does
   ("the number keypads should cover the bottom navigation just like the
   normal keyboard does it"), and it mirrors for RTL. Fixed height so the
   canvas reserves precisely the band it covers.
   v1.6.43 — THE KEYS SIT ABOVE THE PHONE'S SYSTEM NAVIGATION BAR (user
   report: "the keypad should be not under the phone netiv navigation, it
   should be just on top of the apps bottom navigation not the mobile, the
   keypad should have some bottom padding"): navigationBarsPadding inside
   the host stops the bottom key row from reaching into the gesture-bar /
   3-button zone where taps fight the system (and the keys were literally
   under the phone's nav). The canvas-coloured background still paints
   through that system band — no see-through hole under the keypad — the
   grid gains real bottom padding, and the host reports its TOP edge in
   window pixels so the canvas reserves the exact band it covers (see the
   padLift note above). */
private val CALC_PAD_H = 224.dp

/** The text-keyboard colour — everything written in TEXT mode renders in
 *  this blue, everything written in NUMBER mode stays ink ("it should be
 *  different colour for what was written in text and number modes"). */
internal val TEXTMODE_COLOR = Color(0xFF4A7D9B)

@Composable
private fun CalcKeypadHostContent(onKey: (String) -> Unit, rtl: Boolean) {
    val C = C()
    CompositionLocalProvider(
        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        // the transparent full-screen Box never consumes input — only the
        // keypad's own keys are clickable — so taps above it reach the canvas
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // v1.6.43 — the overlay's top edge in WINDOW pixels:
                    // the canvas pairs this with its own bottom edge to
                    // reserve the exact covered band (see padLift above)
                    .onGloballyPositioned { c ->
                        val y = c.positionInWindow().y
                        if (RootBottomOverlays.overlayTopPx.value != y) {
                            RootBottomOverlays.overlayTopPx.value = y
                        }
                    }
                    // the background sits OUTSIDE the nav padding so the
                    // canvas colour still fills the system-nav band — no
                    // see-through hole under the keypad
                    .background(C.canvas)
                    // v1.6.43 — never under the PHONE's navigation bar
                    .navigationBarsPadding()
                    .height(CALC_PAD_H)
                    .testTag("calcPadRoot"),
            ) {
                CalcPadGrid(onKey = onKey)
            }
        }
    }
}

/* The keypad grid — v1.6.41 layout per the user's spec: the = key is
   GONE ("do we need the \" = \" in the number mode keyboard" — the result
   zone opens by typing \" = \" in text mode), and the ⌫ sits in ÷'s OLD
   top-right place with ÷ moved DOWN a row ("the x icon that deletes
   numbers should be in the place of ÷ the ÷ should get down on row").
   v1.6.42 — the + is BACK in its CLASSIC spot, the 1 2 3 row ("why did
   you removed the pluss + from the keypad" — the v1.6.41 column shift
   had slid it down into ='s old bottom-right corner where muscle memory
   never found it); the less-typed − takes the bottom-right slot instead:

    AC   [    %    ]  ⌫
     7    8    9    ÷
     4    5    6    ×
     1    2    3    +
     ±    0    .    −            */
@Composable
private fun CalcPadGrid(onKey: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            // v1.6.43 — real bottom padding on the keypad (the user's ask:
            // "the keypad should have some bottom padding") — the keys
            // already stop above the phone's system nav bar; this keeps a
            // visible breathing strip under the ± 0 . − row
            .padding(top = 6.dp, bottom = 10.dp)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            CalcKey("AC", Modifier.weight(1f).fillMaxHeight(), onKey)
            CalcKey("%", Modifier.weight(2f).fillMaxHeight(), onKey)
            CalcKey("⌫", Modifier.weight(1f).fillMaxHeight(), onKey)
        }
        listOf(
            listOf("7", "8", "9", "÷"),
            listOf("4", "5", "6", "×"),
            listOf("1", "2", "3", "+"),
            listOf("±", "0", ".", "−"),
        ).forEach { rowKeys ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
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
    }
}

/* One key of the numeric calculator pad — flat tile, brand-tinted
   operators, red backspace, blue % (the app calculator's language).
   v1.6.38: DIGITS 24sp (bigger glyphs, same cell). v1.6.39: = joins the
   operator styling. v1.6.41: = is gone; ⌫ is the top-right key. */
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

/* v1.6.41 — the PAY chip beside every total (each line's own): opens the
   compact payments modal.
   v1.6.42 — the ENABLED state (report #8): the chip only opens the modal
   on an UNLOCKED entry — while the line is locked it renders greyed and
   inert ("the pay btn beside the individual entrys should only be enabled
   after it gets unlocked"). */
@Composable
private fun PayChip(tag: String, enabled: Boolean = true, onPress: () -> Unit) {
    val C = C()
    Box(
        Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) C.brandSoft else C.cardAlt.copy(alpha = 0.5f))
            .border(1.dp, if (enabled) C.brand else C.line, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { vibrate(longArrayOf(8)); onPress() }
            .testTag(tag)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(t("ee.calcPay"), fontSize = F.tiny.sp, fontWeight = FontWeight.ExtraBold, color = if (enabled) C.brand else C.faint) }
}

/* v1.6.41 — ONE PAYMENT HISTORY ROW (on the canvas above each paid total
   and inside the compact modal): the date, the amount, the NOTE, and the
   new amount it left.
   v1.6.42 — the note shows IN FULL (report #8: "with showing its full
   note") — it wraps instead of ellipsing, and the whole row stacks when
   the note runs long. */
@Composable
private fun PaymentHistoryRow(
    date: String,
    amount: Double,
    note: String?,
    remaining: Double,
    tag: String,
    onOpen: () -> Unit,
) {
    val C = C()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onOpen() }
            .testTag(tag)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        MdiIcon("cash-check", 12, C.brand)
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(date, fontSize = F.tiny.sp, color = C.faint)
                Text("− ${money(amount)}", fontSize = F.tiny.sp, color = C.sub, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("→ ${money(remaining)}", fontSize = F.tiny.sp, color = C.faint, fontWeight = FontWeight.Bold)
            }
            if (!note.isNullOrBlank()) {
                Text(
                    note!!.trim(),
                    fontSize = F.tiny.sp, color = C.faint,
                    modifier = Modifier.fillMaxWidth().padding(top = 1.dp),
                )
            }
        }
    }
}

/* v1.6.41 — the locked line's text with the TEXT-mode pieces (the quoted
   numbers) coloured — pure, so the harness pins the span layout. */
internal fun coloredLockedLine(s: String, base: Color, quoted: Color): AnnotatedString {
    val b = AnnotatedString.Builder(s)
    if (s.isNotEmpty()) b.addStyle(SpanStyle(color = base), 0, s.length)
    var i = 0
    while (i < s.length) {
        if (s[i] == ' ') { i++; continue }
        var j = i
        while (j < s.length && s[j] != ' ') j++
        val tok = s.substring(i, j)
        if (tok.length >= 2 && tok.startsWith("\"") && tok.endsWith("\"")) {
            b.addStyle(SpanStyle(color = quoted), i, j)
        }
        i = j
    }
    return b.toAnnotatedString()
}

/** v1.6.41 — one UNDO snapshot of the calculator editing state. */
private data class CalcSnap(
    val lines: List<CalcNote>,
    val text: String,
    val qwerty: Set<Int>,
)

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
