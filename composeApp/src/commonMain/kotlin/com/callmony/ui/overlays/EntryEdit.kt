package com.callmony.ui.overlays

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
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
import com.callmony.lib.applyModeSplit
import com.callmony.lib.calcBreakFold
import com.callmony.lib.currencySymbol
import com.callmony.lib.moneyNumber
import com.callmony.lib.r2
import com.callmony.lib.serializeCalcLedger
import com.callmony.lib.serializeCalcNote
import com.callmony.lib.sumPayments
import com.callmony.lib.textModeRanges
import com.callmony.lib.ownerNameOf
import com.callmony.lib.todayStr
import com.callmony.lib.uid
import com.callmony.platform.NativeDateTime
import com.callmony.platform.Platform
import com.callmony.model.CURRENCIES
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
import com.callmony.ui.kit.calcFontFamily
import com.callmony.ui.components.ScrollRow
import com.callmony.ui.sections.openPaymentSheet
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/* EntryEdit — THE LEDGER ENTRY EDITOR. v1.6.49: the canvas IS the editor —
   the form page that said "Edit entry" is GONE (report #7: "we don't want
   the page that says edit entry for the ledger"), so closing the canvas
   (✕ / the native back) closes the editor itself, and every field the form
   used to edit rides along unchanged for existing rows (kind, date,
   method, note, attachments, items, owner, group — seeded once, kept
   through every save). The canvas header carries the font-size icon
   (report #6) with the TITLE INPUT moved UNDER it, every individual total
   shows its OWN tappable currency beside it (report #1) with the copy / ✕
   buttons on the total's own row (report #2), the totals read one size
   smaller than before on a much tighter left edge (report #3), and the sum
   total is one very compact row whose ring counts the PAID percentage
   (report #4 — it said 100 with no payment because it counted what was
   LEFT).
   v1.6.48 base: numbers written in TEXT mode never calculate (quote +
   mode-split markers), the TOTAL label and its number share one row, the
   enter key is a newline INSIDE the same individual calculation (kept
   through the commit as the ⏎ marker), the lock glyph rides at text
   height, Next is the one commit+save key, and the header's currency chip
   writes the live app-wide setting.
   v1.6.50 — THE THIRTEEN-REPORT REFINEMENT ROUND: the currency beside a
   total is PLAIN TEXT again (report #3: only the HEADER's currency chip is
   tappable); the canvas renders at ONE font size in EVERY state — edited
   or locked (report #4, the repeatedly-asked one) — chosen from a PIXEL
   LADDER of eleven options (report #6); the prefix slot is always reserved
   and centered so bullet / number / none all align (report #4); the
   show-your-work steps ride a soft container aligned with the line above
   (report #5); the copy beside the Pay button carries ALL things on the
   canvas, the active line included (report #1); the commit button is
   called SAVE now, same action (report #2); the whole calculation surface
   renders in the bundled IBM Plex Sans typeface (report #8); the keypad's
   keys fire on touch-DOWN and ⌫ / space REPEAT under a held finger
   (report #7); and the title's underline hugs the title (report #9). */

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
    // v1.6.49 — the canvas IS the whole editor (report #7: "we don't want
    // the page that says edit entry for the ledger"): every field the old
    // form used to carry (kind, date, due, category, method, note, owner,
    // group, items, attachments) is seeded once from the existing row and
    // rides along through every save below — nothing about an old entry
    // changes unless the canvas itself changes it.
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
    // v1.6.18 — attachments: lists / notes / reminders ride on entry.refs
    var refs by remember { mutableStateOf(existing?.refs ?: listOf<com.callmony.model.LinkRef>()) }
    // v1.6.39 — THE CANVAS CALCULATOR, rebuilt around ONE text source:
    //   calcText IS the active line — typed on the system QWERTY in text
    //   mode or built by the keypad in num mode, but the SAME line either
    //   way, so switching modes can never lose what was typed (the
    //   v1.6.38 "i type ball then click the number btn, the ball text
    //   disappeared" report died here: nothing re-serializes the field).
    //   calcNote / tokens / total / steps are all DERIVED from it, live.
    //   calcMode "text" (DEFAULT — system QWERTY) | "num" (the compact
    //   keypad; the field keeps native selection + copy/cut/paste).
    //   Locked ledger lines: long-press UNLOCKS a line for editing; every
    //   line carries a copy icon on its own total row.
    var calcText by remember { mutableStateOf("") }         // the ACTIVE line
    var calcMode by remember { mutableStateOf("text") }     // "text" | "num"
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
    /* v1.6.47 — THE LOCK PILL + unlockedLines are GONE (report #5: "when
       tauch around the text, number toggle or the sum total, some wird
       different modals opens up. why"): the pill's TAP used to flip a
       locked/unlocked state whose only remaining job since v1.6.45 (when
       per-line pay chips moved to the sum total) was recolouring the
       block — a tap "around the text" that changed colours for no
       reason. The line now renders as one calm block: prefix · text ·
       date · own total, with copy and delete at the end; a DELIBERATE
       long-press still pulls the line into the active field for editing
       (the documented v1.6.39 affordance). */
    /* v1.6.47 — report #4 ("when i add payment sometimes it adds it,
       sometimes it not, or it will not update it") — THE PAYMENTS VIEW
       FREEZE, killed. The old `paymentsOverride` SNAPSHOT the payment
       list the moment a line was unlocked or deleted, and every later
       read (the total's paid · remaining, the modal's history, every
       buildEntry save) kept serving that frozen copy: a payment recorded
       through the sheet never showed up ("it will not update it"), and
       the NEXT save PERSISTED the stale list — wiping the recorded
       payment right out of the store ("sometimes it not"). The remap is
       now keyed by PAYMENT ID: the live store list is remapped through
       it on every read, so payments recorded later pass through
       untouched and ride every save. */
    var payRemap by remember { mutableStateOf<Map<String, Int?>?>(null) }
    // v1.6.46 — THE CANVAS TITLE (report #4: "in top if the canvas put a
    // input that does have just the bottom border so we can give the canvas
    // a title"): a bottom-border-only input UNDER the header (v1.6.49,
    // report #6: "put the title input under the header") — it rides the
    // entry as calcTitle (optional, so every older row decodes without it)
    // and heads the whole-ledger copy.
    var calcTitle by remember { mutableStateOf(existing?.calcTitle ?: "") }
    // v1.6.47 — THE LINE PREFIX (report #3: "show bullet or numbers at the
    // start of each line that the user can change"): every committed line
    // (and the active one) leads with • / 1. 2. 3. / nothing. The choice
    // cycles through the small chip on the header and rides the entry as
    // calcPrefix (absent = bullet, the shown-by-default style the report
    // asks for; old rows decode without it, backups mirror it).
    var prefixMode by remember { mutableStateOf(existing?.calcPrefix ?: "bullet") }
    // v1.6.48 — THE HEADER CURRENCY CHIP (report #1: "the ability to change
    // the currency"): the canvas header carries the live currency's symbol;
    // the tap opens the compact picker sheet — one tap re-renders every
    // amount in the app (money() reads the live setting since v1.6.48).
    // v1.6.50 — report #3 ("don't make The currency beside every total is
    // TAPPABLE, just what in the header is enough"): this chip is now the
    // ONLY tappable currency on the canvas — the per-line / per-total
    // glyphs render as plain text with no click.
    var currencySheet by remember { mutableStateOf(false) }
    // v1.6.49 — the per-line / per-entry currency DATA still rides the
    // grammar (a line's own " ~USD" marker, the entry's calcCurrency) and
    // still renders beside its total — but since v1.6.50 there is no UI to
    // change it from the totals row anymore; the header chip owns the
    // choice.
    var entryCur by remember { mutableStateOf(existing?.calcCurrency) }
    var calcCur by remember { mutableStateOf<String?>(null) }
    // v1.6.49 — THE FONT SIZE (report #6: "i want the option to change the
    // font size and put the font size icon on the header"). v1.6.50 — the
    // choice is a plain PIXEL NUMBER ("12"…"22", report #6: "many options
    // by px like in others") and it scales THE WHOLE canvas's calculation
    // content — active line, locked lines, totals, steps — at ONE size
    // (report #4: the same when edited and when locked); it rides the entry
    // as calcFontSize so a reopened canvas keeps the reader's chosen size.
    var calcFontSizeSel by remember { mutableStateOf(calcFontSpOf(existing?.calcFontSize).toString()) }
    var fontSheet by remember { mutableStateOf(false) }
    // v1.6.46 — THE SAVE PULSE (report #3: "the next btn also should do
    // what the save btn does"): NEXT commits the line AND persists the
    // entry — and since v1.6.47 it is the ONE key that does both: the
    // keypad's ENTER became the plain newline (report #1, "the inter btn
    // is not the next btn"), so persistence rides Next, the autosave,
    // and the leave-time create. The save runs one recomposition LATER
    // (the pulse bumps, this effect fires) because the entry must be built
    // from the POST-commit ledger — a same-handler save() would read the
    // pre-commit derived values (effAmount, calcNote…) captured before
    // commitCalcLine() mutated the lines.
    var savePulse by remember { mutableStateOf(0) }

    // v1.6.39 — THE SUM, live: items + the calculator's whole contribution
    // (committed ledger lines + the active line). calcNote is DERIVED
    // from calcText through the free-text grammar, live.
    // v1.6.48 — TWO steps in the parse funnel: (1) report #4's MODE SPLIT —
    // applyModeSplit separates characters typed on DIFFERENT keyboards
    // inside one glued token ("555555555dsssssss" → the keypad 555555555
    // calculates, the QWERTY dsssssss rides its label slot) and quotes
    // text-keyboard numbers (quoteTextNumbers' rule, folded in);
    // (2) report #6's NEWLINE FOLD — the enter key is a real newline INSIDE
    // the same individual calculation now, and the 1:1 index-aligned fold
    // keeps every derived value AND the QWERTY marks valid.
    // v1.6.49 — the fold now writes the ⏎ marker instead of a plain space:
    // a COMMITTED line keeps its visual break (report #5 — the locked line
    // used to collapse to "just one line") while the saved grammar stays
    // one-calculation-per-line forever (the marker is whitespace to every
    // rule and never splits a line on reload).
    val itemsSum = items.sumOf { it.qty * it.price }
    val calcNote = remember(calcText, qwertyIdx) {
        parseCalcNote(applyModeSplit(calcBreakFold(calcText), qwertyIdx))
    }
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
    // v1.6.47 — the payments, LIVE through the remap (see payRemap): the
    // store's own list, with line indices shifted for deleted / unlocked
    // lines — a payment recorded while the editor is open shows up here
    // the same frame and rides every save (the freeze was report #4).
    val entryPayments = remember(payRemap, currentEntry) {
        val live = currentEntry?.payments ?: emptyList()
        val m = payRemap ?: return@remember live
        live.map { p -> if (p.id in m) p.copy(lineIdx = m[p.id]) else p }
    }
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
        // v1.6.46 — the canvas title rides along (blank never persists)
        calcTitle = calcTitle.trim().ifEmpty { null },
        // v1.6.47 — the line-prefix choice rides along (bullet is the
        // default when absent, so it only really needs writing for the
        // non-default styles — but writing it always keeps reopening exact)
        calcPrefix = prefixMode,
        // v1.6.49 — the sum-total currency and the canvas font size ride
        // along the same optional-default way (report #1 + report #6)
        calcCurrency = entryCur,
        calcFontSize = calcFontSizeSel,
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
       keystroke and again on close — nothing is ever lost by backing out.
       v1.6.46 — with the SAVE BUTTON GONE (report #3: Next does what Save
       did) the "new entries save explicitly" rule lives on the save PULSE
       instead (Next / the keypad's ENTER) plus the leave-time create below
       — backing out of a calculator that holds anything payable persists
       it, so nothing can be lost by leaving either. Invalid totals (<= 0)
       never overwrite a good entry.
       v1.6.42 — the key is only COMPUTED for a live entry (report #1: the
       whole-ledger serialization ran on EVERY keystroke even for a
       brand-new, not-yet-saved entry — pure waste while typing). */
    // v1.6.38 — the DELETE guard: the dispose-time auto-save must never
    // resurrect an entry the user just deleted
    var deleted by remember { mutableStateOf(false) }
    val autoKey = remember { mutableStateOf<String?>(null) }
    val currentKey = if (currentEntry != null) listOf(
        effAmount, serializeCalcLedger(calcLines + if (calcNote.tokens.isNotEmpty() || calcNote.text.isNotBlank()) listOf(calcNote) else emptyList()),
        // v1.6.46 — the item NAME rides the key too: a rename (qty and
        // price untouched) changed NOTHING here before, so neither the
        // autosave nor the leave-time save ever fired for it — with the
        // Save button gone that would have silently dropped every
        // item-rename on backing out (caught by the v1.6.46 battery)
        items.map { "${it.id}:${it.qty}:${it.price}:${it.name}" }, date, dueOn, dueDate, category, method, note, owner, groupSel,
        refs.map { "${it.type}:${it.id}" }, type, kindSel, calcTitle, prefixMode, entryCur, calcFontSizeSel,
        entryPayments.map { "${it.id}:${it.amount}:${it.lineIdx}" },
    ).toString() else ""
    if (autoKey.value == null) autoKey.value = currentKey
    val initialKey = autoKey.value ?: currentKey
    // v1.6.40 — the entry is live after the FIRST Save too (currentEntry),
    // so a brand-new entry autosaves exactly like an opened one
    // v1.6.51 — THE MAX-WAIT AUTOSAVE (report #2, beside the two ANR
    // reports: "this app should not loss all kind of data it have that
    // user puts at it, even when installing again or update"). The old
    // debounce RESTARTED on every keystroke — continuous typing never
    // paused 600 ms, so nothing persisted until the hands left the keys,
    // and a never-saved DRAFT had no autosave at all: a hard process kill
    // (exactly what the two MAIN THREAD BLOCKED reports on the Samsung
    // are) dropped everything typed since the last pause, or the whole
    // draft. This loop writes AT MOST 600 ms behind the canvas — live
    // entry or payable draft alike — so any kill, at any moment, can only
    // ever cost the last half-second of typing. The dirty key still gates
    // it (an untouched editor writes nothing), the <= 0 guard inside
    // save() still stands, and the leave-time save below still catches
    // whatever lands inside the last beat.
    val autoSave by rememberUpdatedState { save(silent = true) }
    val keyNow by rememberUpdatedState(currentKey)
    val effNow by rememberUpdatedState(effAmount)
    val liveNow by rememberUpdatedState(currentEntry != null)
    val savedKey = remember { mutableStateOf<String?>(initialKey) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(600)
            val dirtyLive = liveNow && keyNow != "" && keyNow != savedKey.value
            val dirtyDraft = !liveNow && effNow > 0 && savedKey.value == ""
            if (!deleted && (dirtyLive || dirtyDraft)) {
                autoSave()
                savedKey.value = keyNow
            }
        }
    }
    // v1.6.46 — THE SAVE PULSE: Next bumped it after a commit (v1.6.47:
    // ENTER no longer feeds it — the enter key is the plain newline); the
    // effect runs one recomposition later, so the entry is built from the
    // post-commit ledger (see commitAndSave). The silent save self-guards
    // (<= 0 never persists).
    LaunchedEffect(savePulse) {
        if (savePulse > 0) save(silent = true)
    }
    // v1.6.46 — THE LEAVE-TIME SAVE, staleness-proofed: a plain
    // DisposableEffect(Unit) closure would freeze the FIRST composition's
    // derived values (effAmount, currentKey…), so the action is refreshed
    // through rememberUpdatedState and the dispose handler always runs the
    // LATEST one. Two branches: a LIVE entry persists whatever changed but
    // never got debounced; a NEVER-SAVED draft with something payable is
    // CREATED here — with the Save button gone this is the only thing
    // standing between "typed a calculation, backed out" and "lost it".
    val leaveSave by rememberUpdatedState {
        val ce = currentEntry
        when {
            deleted -> {}
            // a LIVE entry: persist whatever changed but never got debounced
            ce != null -> if (currentKey != initialKey && effAmount > 0) {
                AppStore.saveEntry(buildEntry(ce.id))
            }
            // a NEVER-SAVED draft with something payable: CREATE it now
            effAmount > 0 -> {
                val id = uid()
                AppStore.saveEntry(buildEntry(id))
                maybeDueReminder(id, true)
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { leaveSave() }
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
        // v1.6.48 — the DERIVED parse (newlines folded to ⏎, mode-split
        // applied — see calcNote above): committing hands the ledger the
        // exact tokens the live total has been reading all along.
        val note = calcNote
        if (note.tokens.isEmpty() && note.text.isBlank()) return
        pushUndo()
        // v1.6.42 — the commit STAMPS THE DATE (report #10: "it should show
        // the date for each entry"): the line carries the day it was
        // committed, rendered beside it and persisted through the save. A
        // fresh line commits LOCKED — the pay gate opens with the pill.
        // v1.6.49 — the ACTIVE line's chosen currency rides the commit
        // (report #1: the line's OWN display money, " ~USD" in the save).
        calcLines = calcLines + note.copy(date = todayStr(), currency = calcCur)
        calcText = ""
        qwertyIdx = emptySet()
        calcCur = null
        calcSelection = TextRange(0)
        focusToken++ // the caret shows on the fresh line
    }

    /* v1.6.46 — COMMIT + SAVE, THE ONE ACTION (report #3: "the next btn
       also should do what the save btn does, if we make it like that, we
       dont need the save btn"): whatever was being typed locks into the
       ledger (if anything) and the entry persists — one beat later, after
       the recomposition, through the save pulse (see savePulse above).
       The canvas's Next button is the one route here since v1.6.47 (the
       keypad's ENTER key became the plain newline — report #1, "the inter
       btn is not the next btn"); the Save button is GONE — nothing else
       in the editor saves explicitly any more. */
    fun commitAndSave() {
        if (calcText.isNotBlank()) commitCalcLine()
        savePulse++
    }

    /* v1.6.50 — report #1 ("the copy btn that is bised the pay btn in the
       sum total should copy all things in that canvas"): the ACTIVE line
       rides the whole-canvas export too, as its last line with its live
       total — the copy beside the Pay button now carries literally
       everything the canvas holds. */
    fun activeExportNote(): List<CalcNote> =
        if (calcNote.tokens.isNotEmpty() || calcNote.text.isNotBlank()) listOf(calcNote) else emptyList()

    /* v1.6.39 — long-press a LOCKED line and it UNLOCKS for editing: the
       line comes back down into the active field. Whatever was being
       typed commits first, so nothing is ever lost. v1.6.40 — the caret
       lands at the END (the right place to keep writing), the line's
       payments stay recorded at entry level, and the QWERTY marks are
       REBUILT FROM THE LINE'S OWN TOKENS (v1.6.45 report #3: quoted
       numbers and every word — letters only ever come from the text
       keyboard — are TEXT-mode content, so the blue/ink separation an
       edited line shows is exactly the one it had when it was committed).
       v1.6.41 — undoable. */
    fun unlockCalcLine(i: Int) {
        val line = calcLines.getOrNull(i) ?: return
        pushUndo()
        val rest = calcLines.filterIndexed { j, _ -> j != i }
        calcLines = if (calcNote.tokens.isNotEmpty() || calcNote.text.isNotBlank()) rest + calcNote else rest
        calcText = displayCalcNote(line)
        qwertyIdx = textModeRanges(calcText)
        // v1.6.49 — the line's own currency comes back with it (the
        // re-commit keeps it unless the user changes it again)
        calcCur = line.currency
        calcSelection = TextRange(calcText.length)
        calcMode = "text"
        focusToken++
        // v1.6.47 — the line's payments move to ENTRY LEVEL through the
        // ID-keyed remap (the live list keeps flowing — report #4)
        payRemap = (payRemap ?: emptyMap()) + entryPayments.mapNotNull { p ->
            when (val li = p.lineIdx) {
                null -> null
                i -> p.id to null
                else -> if (li > i) p.id to (li - 1) else null
            }
        }.toMap()
    }

    /* v1.6.40 — deleting a line goes through the custom CONFIRMATION modal;
       the deleted line's payments stay recorded (they move to the entry
       level), later lines' payments shift down with them. v1.6.41 —
       undoable. */
    fun deleteCalcLine(i: Int) {
        if (i !in calcLines.indices) return
        pushUndo()
        calcLines = calcLines.filterIndexed { j, _ -> j != i }
        // v1.6.47 — later lines' payments shift down through the ID-keyed
        // remap; the deleted line's stay recorded at entry level (live)
        payRemap = (payRemap ?: emptyMap()) + entryPayments.mapNotNull { p ->
            when (val li = p.lineIdx) {
                null -> null
                i -> p.id to null
                else -> if (li > i) p.id to (li - 1) else null
            }
        }.toMap()
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
            // v1.6.48 — ⌫ over a NEWLINE removes just the newline (the
            // native backspace's exact contract over an enter); everywhere
            // else the old rule stands — the last meaningful character
            // dies together with its trailing whitespace.
            "⌫" -> if (calcText.endsWith("\n")) {
                onCalcEdited(calcText.dropLast(1), false)
            } else {
                onCalcEdited(calcText.trimEnd().dropLast(1), false)
            }
            // v1.6.48 — THE ENTER KEY IS THE NEWLINE INSIDE THE SAME
            // INDIVIDUAL CALCULATION (report #6: "the reason i wanted the
            // inter btn in the number keypad is to go to next line in the
            // same individual calculation, just like the netiv inter btn
            // do, not to do what the next btn do"): the break lands in the
            // active line's own text, the line keeps calculating across it
            // (the grammar splits on whitespace), and Next stays the one
            // commit + save key. Never doubles a trailing newline.
            "\n" -> {
                if (calcText.isNotEmpty() && !calcText.endsWith("\n")) {
                    onCalcEdited(calcText + "\n", false)
                }
            }
            // v1.6.47 — THE SPACE KEY (report #1: "in its row add the space
            // key too"): the keypad's bottom row is the native keyboard's
            // — a wide blank space bar beside the enter key. It appends ONE
            // space (never doubles one), through the undoable funnel, with
            // no text-keyboard mark — keypad numbers keep calculating.
            // v1.6.50 — the REPEAT path (report #7: "also thw space btn also
            // just do one space why"): a held space bar types REAL
            // repeated spaces exactly like the native keyboard — the
            // repeat ticks bypass the doubling guard (the grammar folds
            // whitespace runs anyway, so nothing breaks).
            "␣" -> {
                if (calcText.isNotEmpty() && !calcText.endsWith(" ")) {
                    onCalcEdited(calcText + " ", false)
                }
            }
            // v1.6.50 — the space bar's REPEAT spelling: one space per
            // tick, unconditionally (the native held-space behaviour)
            "␣!" -> onCalcEdited(calcText + " ", false)
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
                // v1.6.48 — the ± reads the SAME derived parse (mode-split,
                // newline-folded) the live total reads
                val note = calcNote
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
       IME — the text field with the system keyboard).
       v1.6.45 — report #1 ("i nottised sometimes the regular native
       keyboard shows up on the numbers mode"): the QWERTY field's focus
       is CLEARED FIRST, so the system IME loses its served editor before
       the field leaves composition — plus the num field itself now
       force-hides the IME on every focus / window-focus gain (see
       NoImeEditText). The toggle's own ime.hide() stays as a third belt. */
    val calcFieldFocus = remember { FocusRequester() }
    val ime = LocalSoftwareKeyboardController.current
    val focusMgr = LocalFocusManager.current
    fun enterTextMode() {
        calcMode = "text"
        focusToken++
    }
    fun enterNumMode() {
        if (calcMode != "num") {
            focusMgr.clearFocus() // the QWERTY editor drops focus BEFORE it dies
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

    /* v1.6.49 — THE CANVAS IS THE EDITOR (report #7: "we don't want the
       page that says edit entry for the ledger"): the form page that used
       to sit under the canvas is GONE — ✕ / the native back button now
       CLOSES the editor itself (the leave-time save underneath persists
       whatever the 600ms autosave has not caught yet), and every field the
       form used to edit rides along unchanged for existing rows (kind,
       date, due, category, method, note, owner, group, items, attachments
       — seeded once at the top, kept through every save). */
    fun closeEditor() {
        focusMgr.clearFocus()
        ime?.hide()
        pop()
    }

    /* v1.6.41 — THE CARET, ALWAYS: whenever the token bumps, the active
       field of the CURRENT mode takes focus — text mode via the Compose
       focus request, number mode through the field's own token param
       (real view focus inside the Android actual). The double request
       (immediately + after the host-sheet / overlay focus-clears settle)
       beats the OverlayHost's open-clear and any sheet pop. */
    LaunchedEffect(focusToken, calcMode) {
        if (focusToken == 0) return@LaunchedEffect
        if (calcMode == "text") {
            try { calcFieldFocus.requestFocus() } catch (_: Exception) {}
            kotlinx.coroutines.delay(200)
            try { calcFieldFocus.requestFocus() } catch (_: Exception) {}
        }
    }

    // v1.6.39 — THE CANVAS CALCULATOR, full screen — and since v1.6.49 the
    // WHOLE editor (the form is gone). ✕ (or the native back button) closes
    // the editor; back with the keypad up closes the keypad first; Aa / 123
    // arm the input method; Next is the SMALL commit+save button ON the
    // canvas (the user's v1.6.39 spec).
    // v1.6.41 — the canvas is RTL-AWARE ("it should support also right to
    // left" — the whole canvas mirrors for Arabic), its header is VERY
    // COMPACT with UNDO / REDO beside the close button, and while the
    // NUMBER KEYPAD is armed it renders at the SHELL ROOT — covering the
    // bottom navigation exactly like the system keyboard does — while the
    // canvas reserves exactly the band it covers.
    AppBackHandler(enabled = true) {
        when {
            calcMode == "num" -> closeKeypad()
            else -> closeEditor()
        }
    }
        // v1.6.41 — the number keypad renders at the ROOT (over the nav);
        // hide it while a payments modal / host sheet is open so nothing
        // floats under it.
        val wantPad = calcMode == "num" && payModal == null && !currencySheet &&
            !fontSheet && HostSheets.stack.isEmpty()
        // v1.6.47 — report #2 ("sometimes the netiv keyboard opens up on
        // the number mode why"): the window-level IME SUPPRESSION arms with
        // the keypad itself. The v1.6.45 fix force-hid the IME on the num
        // field's own focus / window-focus gains — but the leak's remaining
        // path never touches that field: open the payment sheet from the
        // total's Pay chip (its amount/note fields take the focus and raise
        // the system QWERTY), close it, and the keypad comes BACK while the
        // IME LINGERS over it — no editor served, no focus change, nothing
        // to re-hide it. So while the keypad is up the WINDOW itself now
        // refuses the soft keyboard (SOFT_INPUT_STATE_ALWAYS_HIDDEN + an
        // immediate IMM hide — see UiPlatform.setImeSuppressed), and the
        // suppression lifts the moment the keypad leaves. The Compose-side
        // ime.hide() stays as the instant belt.
        LaunchedEffect(wantPad) {
            if (wantPad) {
                try { ime?.hide() } catch (_: Exception) {}
                com.callmony.platform.Platform.ui.setImeSuppressed(true)
            } else {
                com.callmony.platform.Platform.ui.setImeSuppressed(false)
            }
            RootBottomOverlays.content.value = if (wantPad) {
                // v1.6.48 — onEnter, ROUND THREE (report #6: "the reason i
                // wanted the inter btn in the number keypad is to go to
                // next line in the same individual calculation, just like
                // the netiv inter btn do, not to do what the next btn
                // do"): the enter key inserts a real NEWLINE into the
                // ACTIVE LINE — the same individual calculation keeps
                // calculating across the break, exactly the native enter's
                // contract. It neither commits nor saves: Next (and only
                // Next) owns the commit + SAVE of v1.6.46 report #3.
                { CalcKeypadHostContent(onKey = { calcKey(it) }, onEnter = { calcKey("\n") }, rtl = isRTL(currentLang().code)) }
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
                // the IME suppression cannot outlive the canvas — a leaked
                // ALWAYS_HIDDEN window would eat the QWERTY in text mode
                com.callmony.platform.Platform.ui.setImeSuppressed(false)
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
            // v1.6.50 — the canvas's ONE font size (report #4: "the text and
            // calculations font size should be thw same when it is edited
            // and when it is locked"): a single number scales EVERYTHING —
            // the active field, the locked lines, every total, the prefix
            // and the steps — so the text never changes size between
            // editing and locking again. And everything renders in the
            // calculation typeface (report #8).
            val fs = calcFontSizesOf(calcFontSizeSel)
            val calcFf = calcFontFamily()
            // v1.6.51 — THE PREFIX SLOT FOLLOWS THE FONT (report #1: "the dot
            // bised number is showing up in the second row"): the fixed 14dp
            // reservation could not hold "1." once the PIXEL ladder climbed
            // past it (Plex Bold's digit + period run wider than 14dp from
            // ~15px up), so the period wrapped onto its own second row
            // inside the slot. The width is now COMPUTED from the canvas's
            // own size and the widest number the canvas can show — the slot
            // grows exactly as the text does (fontScale included), stays ONE
            // reserved width for the whole canvas so bullet / number / none
            // still start the content at the very same x, and the prefix
            // itself can never wrap again (single line, no soft wrap).
            val prefixW = prefixSlotDpOf(fs.sp, calcLines.size + 1)
                .dp * LocalDensity.current.fontScale
            // header — v1.6.41: VERY COMPACT (38dp band, 32/28dp controls)
            // with UNDO / REDO beside the close button ("need undo,redo
            // btns at the top in header and make the header very compact,
            // so it won't take too much space"). v1.6.49 — the TITLE INPUT
            // moved UNDER the header (report #6: "put the title input under
            // the header") and the FONT-SIZE icon joined the right-side
            // chips (report #6: "put the font size icon on the header").
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .height(38.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // v1.6.49 — ✕ CLOSES THE EDITOR (the form is gone — report #7)
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { closeEditor() }
                        .testTag("calcCollapse"),
                    contentAlignment = Alignment.Center,
                ) { MdiIcon("close", 18, C.sub) }
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
                Spacer(Modifier.weight(1f))
                // v1.6.47 — THE PREFIX TOGGLE (report #3: "show bullet or
                // numbers at the start of each line that the user can
                // change"): a small chip that cycles • → 1. → — (none). Its
                // glyph is the style itself, so what you see is what every
                // line gets.
                val nextPrefix = when (prefixMode) { "bullet" -> "number"; "number" -> "none"; else -> "bullet" }
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (prefixMode != "none") C.brandSoft else C.card)
                        .border(1.dp, if (prefixMode != "none") C.brand else C.line, RoundedCornerShape(9.dp))
                        .clickable { prefixMode = nextPrefix }
                        .testTag("calcPrefixBtn"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when (prefixMode) { "number" -> "1."; "none" -> "—"; else -> "•" },
                        fontSize = F.tiny.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (prefixMode != "none") C.brand else C.faint,
                    )
                }
                // v1.6.48 — THE HEADER CURRENCY CHIP (the app-wide LIVE
                // setting): the tap opens the compact picker sheet — one tap
                // and every amount in the app follows (money() reads the
                // live setting).
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(C.brandSoft)
                        .border(1.dp, C.brand, RoundedCornerShape(9.dp))
                        .clickable { currencySheet = true }
                        .testTag("calcCurrencyBtn"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        currencySymbol(AppStore.state.settings.currency),
                        fontSize = F.tiny.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = C.brand,
                        maxLines = 1,
                    )
                }
                // v1.6.49 — THE FONT-SIZE CHIP (report #6: "i want the option
                // to change the font size and put the font size icon on the
                // header"): two A glyphs in the chosen size's own scale; the
                // tap opens the PIXEL picker (v1.6.50: many options by px,
                // 12…22 — report #6 round two).
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (calcFontSpOf(calcFontSizeSel) != DEFAULT_CALC_SP) C.brandSoft else C.card)
                        .border(1.dp, if (calcFontSpOf(calcFontSizeSel) != DEFAULT_CALC_SP) C.brand else C.line, RoundedCornerShape(9.dp))
                        .clickable { fontSheet = true }
                        .testTag("calcFontBtn"),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("A", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = if (calcFontSpOf(calcFontSizeSel) != DEFAULT_CALC_SP) C.brand else C.sub)
                        Text("A", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (calcFontSpOf(calcFontSizeSel) != DEFAULT_CALC_SP) C.brand else C.sub)
                    }
                }
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
            // v1.6.49 — THE TITLE INPUT, UNDER THE HEADER (report #6: "put
            // the title input under the header"): the bottom-hairline-only
            // field v1.6.46 introduced, now a full-width row of its own
            // beneath the header band. Its placeholder is the old label;
            // the typed title rides the entry as calcTitle and heads the
            // whole-ledger copy. Tapping it opens the system QWERTY; the
            // active line's caret is untouched (the focus token only
            // re-arms on toggles, never on title typing).
            // v1.6.50 — report #9 ("why the bottom border of the title to
            // far from the title"): the row no longer carries its own
            // min-height band (the old heightIn(min = 30.dp) + 4dp bottom
            // padding pushed the hairline ~8dp under the text) — it hugs
            // the field's own line box and sits just 2dp below it, so the
            // underline reads as the title's border, not a stray rule
            // floating under the whole area.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 2.dp, bottom = 2.dp)
                    .heightIn(min = 24.dp)
                    .testTag("calcTitleRow")
                    .drawBehind {
                        // the ONLY decoration: the bottom border
                        drawLine(
                            color = C.line,
                            strokeWidth = 1.dp.toPx(),
                            start = Offset(0f, size.height - 0.5f),
                            end = Offset(size.width, size.height - 0.5f),
                        )
                    },
            ) {
                BasicTextField(
                    value = calcTitle,
                    onValueChange = { calcTitle = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = F.small.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = calcFontFamily(),
                        color = C.ink,
                        textDirection = androidx.compose.ui.text.style.TextDirection.Content,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    cursorBrush = SolidColor(C.brand),
                    // the placeholder rides a decorationBox so the field
                    // itself never carries example text (the v1.6.42
                    // lesson: a placeholder must never replace the caret)
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth()) {
                            inner()
                            if (calcTitle.isEmpty()) {
                                Text(
                                    t("ee.calcTitlePh"),
                                    fontSize = F.small.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = C.faint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("calcTitleField"),
                )
            }
            // THE LEDGER — committed (locked) lines above the active line.
            // v1.6.39: long-press UNLOCKS a line back into the active
            // field; every line carries a copy icon (locked or not).
            // v1.6.40: locked lines show their FULL calculation (no
            // clamping — "it should show the full calculation when it is
            // locked, dont clamp it"), the lock is a NOTICEABLE brand pill
            // that begs to be pressed (long-press unlocks), and removing a
            // line asks through the custom confirmation modal.
            // v1.6.45 — report #2 ("i want the pay btn to be just on the sum
            // total not on the individual entry, and the history of the
            // payment also should show when the payment btn is clicked on
            // the sum total not on the individual entry"): the per-line PAY
            // chip, the per-line payment-history block and the total's
            // tap-to-pay are ALL GONE — payments live on the SUM TOTAL's
            // own Pay button + payments modal now. The line keeps its
            // date, its total and its paid hint.
            // v1.6.47 — report #3 ("show bullet or numbers at the start of
            // each line that the user can change"): every committed line
            // leads with the canvas's PREFIX — a bullet • by default, or
            // its 1. 2. 3. number, or nothing — chosen by the small chip
            // beside the canvas title (calcPrefixBtn) and persisted with
            // the entry as calcPrefix. Report #5 — the LOCK PILL is GONE
            // (see its note above): the block renders one calm strip and
            // only a deliberate long-press edits.
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    // v1.6.49 — report #3: the whole ledger moved IN —
                    // 14dp → 10dp — no wasted left rail
                    .padding(horizontal = 10.dp)
                    .padding(top = 4.dp, bottom = 8.dp),
            ) {
                calcLines.forEachIndexed { i, line ->
                    val lineTotal = evalCalcTokens(line.tokens) // null = pure annotation line
                    val linePays = entryPayments.filter { it.lineIdx == i }
                    val linePaid = sumPayments(linePays)
                    val lineRemain = lineTotal?.let { r2(it - linePaid) }
                    val lineShown = if (linePaid > 0.005) lineRemain else lineTotal
                    // v1.6.47 — the line's PREFIX (bullet / number / none)
                    val linePrefix = calcPrefixOf(prefixMode, i)
                    // v1.6.49 — the line's OWN display currency (report #1):
                    // its explicit choice, else the entry's sum currency,
                    // else the live setting (CurrencyChip's null path)
                    val lineCur = line.currency ?: entryCur
                    Row(
                        Modifier
                            .fillMaxWidth()
                            // v1.6.48 — report #5: the lines sit TIGHTER
                            // ("it shouldn't have to much space between
                            // them vertically")
                            .padding(top = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(C.cardAlt.copy(alpha = 0.35f))
                            .combinedClickable(
                                onClick = {}, // a plain tap does NOTHING (report #5 — no surprise modals)
                                onLongClick = { vibrate(longArrayOf(20)); unlockCalcLine(i) },
                            )
                            .testTag("calcLine:$i")
                            // v1.6.49 — report #3: a MUCH tighter left edge —
                            // "why the individual calculation has so much
                            // padding on the left, it is west of space": the
                            // lock moved onto the total row, the prefix
                            // column and both paddings shrank (the text now
                            // starts ~32dp from the screen edge, was ~64dp)
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // v1.6.50 — the prefix slot is ALWAYS RESERVED and
                        // centered (report #4: "it should be consistent if
                        // there is bullet, number or notting") — the content
                        // column starts at the same x in every prefix mode,
                        // and the glyph-to-content gap is one tight constant.
                        Box(
                            Modifier
                                .width(prefixW)
                                .testTag("calcLinePrefix:$i"),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            linePrefix?.let { p ->
                                Text(
                                    p,
                                    fontSize = fs.sp.sp,
                                    color = C.faint,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = calcFf,
                                    // v1.6.51 — the prefix is ONE line, always
                                    // (report #1: the period after the number
                                    // must never drop to a second row)
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            // the FULL calculation, wrapping — never clamped;
                            // v1.6.49 — the ⏎ markers render as REAL line
                            // breaks, so a calculation the enter key wrote
                            // across several lines STAYS several lines when
                            // it locks (report #5: "when it got locked, the
                            // line disappeared, it just one line why")
                            Text(
                                coloredLockedLine(displayCalcNote(line), C.sub, TEXTMODE_COLOR),
                                fontSize = fs.sp.sp, fontWeight = FontWeight.Medium,
                                fontFamily = calcFf,
                                modifier = Modifier.fillMaxWidth().testTag("calcLineText:$i"),
                            )
                            // v1.6.49 — THE TOTAL ROW (reports #1 #2 #3): the
                            // copy and ✕ buttons live ON this row (report #2:
                            // "make the copy and the x btn in the row of the
                            // individual total"), the total itself reads at
                            // the line TEXT's own size (report #3: "should be
                            // smaller as the text and numbers… wether it is
                            // locked or not… or when editing, always" — since
                            // v1.6.50 the SAME single size the active field
                            // renders at, by construction) with its currency
                            // glyph beside it (report #1 — PLAIN TEXT since
                            // v1.6.50, report #3: only the header's currency
                            // is tappable) and the date smaller and lighter
                            // (report #3), the lock glyph at text height at
                            // the row's end. The row ALWAYS renders — a
                            // pure-annotation line keeps its buttons
                            // reachable.
                            Row(
                                Modifier.padding(top = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (lineShown != null) {
                                    Text(
                                        moneyNumber(lineShown),
                                        fontSize = fs.sp.sp, color = C.faint, fontWeight = FontWeight.SemiBold,
                                        fontFamily = calcFf,
                                        modifier = Modifier.testTag("calcLineTotal:$i"),
                                    )
                                }
                                CurrencyText(cur = lineCur, tag = "calcLineCur:$i")
                                if (linePaid > 0.005) {
                                    Text(
                                        "${t("lg.paid")} ${money(linePaid, lineCur)}",
                                        fontSize = 11.sp, color = C.faint,
                                        modifier = Modifier.testTag("calcLinePaid:$i"),
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                if (line.date.isNotEmpty()) {
                                    Text(
                                        com.callmony.lib.fd(line.date),
                                        fontSize = 11.sp, color = C.faint, fontWeight = FontWeight.Normal,
                                        modifier = Modifier.testTag("calcLineDate:$i"),
                                    )
                                }
                                // v1.6.48 — THE LOCK ICON at text height, now
                                // at the total row's end (v1.6.49 moved it off
                                // the left edge): a plain inert notice that
                                // the line is locked; only a DELIBERATE
                                // long-press on the line edits it
                                MdiIcon(
                                    "lock-outline", fs.sp, C.faint,
                                    Modifier.testTag("calcLineLock:$i"),
                                )
                                CopyBtn(tag = "calcLineCopy:$i") { copyLineText(lineCopyText(displayCalcNote(line), lineTotal)) }
                                DeleteBtn(tag = "calcLineDelete:$i") {
                                    Confirm.ask(t("ee.calcDeleteLineTitle"), t("ee.calcDeleteLineMsg"), t("act.delete"), danger = true) { ok ->
                                        if (ok) deleteCalcLine(i)
                                    }
                                }
                            }
                        }
                    }
                }
                // THE ACTIVE LINE — ONE native editable field in BOTH modes
                // (v1.6.39): text mode rides the system QWERTY; num mode
                // shows the SAME line in the IME-suppressed native field.
                // Long-press opens the NATIVE Copy · Cut · Paste ·
                // Select-all menu with selection handles either way.
                // v1.6.49 — its copy button and open lock moved DOWN onto the
                // live-total row (report #3's left-edge fix), so the field
                // starts right after the prefix.
                // v1.6.50 — the prefix slot is ALWAYS RESERVED (report #4:
                // "it should be consistent if there is bullet, number or
                // notting") — the field's left edge sits at the same x a
                // committed line's content does, in every prefix mode, and
                // the field renders at the SAME single size (and in the SAME
                // calculation typeface) the locked lines render in.
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier.width(prefixW).testTag("calcActivePrefix"),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        calcPrefixOf(prefixMode, calcLines.size)?.let { p ->
                            Text(
                                p,
                                fontSize = fs.sp.sp,
                                color = C.faint,
                                fontWeight = FontWeight.Bold,
                                fontFamily = calcFf,
                                // v1.6.51 — one line, no wrap, ever
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
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
                                textStyle = TextStyle(
                                    // v1.6.50 — ONE size for the whole canvas
                                    // (report #4: the same when edited and when
                                    // locked) in the calculation typeface
                                    // (report #8) — the active field, the locked
                                    // lines and every total render identically
                                    fontSize = fs.sp.sp, fontWeight = FontWeight.Medium, color = C.ink,
                                    fontFamily = calcFf,
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
                                // v1.6.50 — the SAME single size the locked
                                // lines render at (report #4) in the
                                // calculation typeface (report #8)
                                fontSizeSp = fs.sp,
                                textColor = C.ink,
                                cursorColor = C.brand,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag("calcNumField"),
                                alignStart = true,
                                // v1.6.48 — SIX lines: the enter key lands
                                // real newlines inside the same individual
                                // calculation now (report #6)
                                maxLines = 6,
                                highlight = qwertyIdx,
                                highlightColor = TEXTMODE_COLOR,
                                focusToken = focusToken,
                                bold = false,
                                calcFont = true,
                            )
                        }
                    }
                }
                // v1.6.49 — THE LIVE TOTAL ROW, aligned with the field above
                // it (report #3: "when editing why is the individual total
                // not aligned horizontaly with what above it, why it is
                // pushed to the left"): it indents by exactly the prefix
                // column (v1.6.50 — UNCONDITIONALLY now: the slot is always
                // reserved, so the alignment holds in every prefix mode),
                // reads at the canvas's ONE size, carries the ACTIVE line's
                // currency glyph (PLAIN TEXT since v1.6.50, report #3), the
                // open lock and the copy button. ALWAYS rendered — the
                // buttons stay reachable even on a line that does not
                // compute yet.
                Row(
                    Modifier
                        .padding(top = 3.dp)
                        .padding(start = prefixW + 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (calcTotal != null) {
                        Text(
                            "= ${moneyNumber(calcTotal)}",
                            fontSize = fs.sp.sp, fontWeight = FontWeight.SemiBold, color = C.brand,
                            fontFamily = calcFf,
                            modifier = Modifier.testTag("calcTotal"),
                        )
                    }
                    CurrencyText(cur = calcCur ?: entryCur, tag = "calcActiveCur")
                    Spacer(Modifier.weight(1f))
                    // the active line's OPEN lock — text-height, inert
                    MdiIcon(
                        "lock-off-outline", fs.sp, C.brand,
                        Modifier.testTag("calcActiveLock"),
                    )
                    // the active line's copy — same slot as every committed
                    // line's (report #2); the copy INCLUDES the live total
                    CopyBtn(tag = "calcActiveCopy") { copyLineText(lineCopyText(calcText, calcTotal)) }
                }
                // v1.6.50 — THE SHOW-YOUR-WORK STEPS, IN A CONTAINER (report
                // #5: "the part where it shows how the calculation went
                // should be in some contener and should be aligned with what
                // upper it horizontaly"): the step-by-step evaluation rides a
                // soft card-alt container with a hairline, indented by
                // exactly the prefix column so its left edge lines up with
                // the line text above it — a distinct, calm block instead of
                // loose faint text floating on the canvas.
                if (calcStepList.isNotEmpty()) {
                    Box(
                        Modifier
                            .padding(top = 4.dp)
                            .padding(start = prefixW + 4.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(C.cardAlt.copy(alpha = 0.5f))
                            .border(1.dp, C.line, RoundedCornerShape(8.dp))
                            .testTag("calcStepsBox"),
                    ) {
                        Text(
                            calcStepList.joinToString("   ") { s -> "${s.expr} = ${money(s.value)}" },
                            fontSize = F.tiny.sp, color = C.faint,
                            fontFamily = calcFf,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp).testTag("calcSteps"),
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // THE SUM TOTAL — v1.6.49 — VERY COMPACT (report #4: "make the
            // sum total row very compact, it is talking to much space
            // verticaly"): the ring shrank 40dp → 30dp, the double padding
            // bands are gone, and the ring counts the PAID percentage —
            // "why the persontage in the cercle says 100 when there is no
            // any payment": it counted what was LEFT, so a fresh entry read
            // 100%; it now counts what is PAID (0% until money moves, green
            // at 100%), matching the entry cards' own rings. The sum's own
            // TAPPABLE currency rides beside the number (report #1).
            val paidFraction = if (effAmount > 0.005) (paidTotal / effAmount).toFloat().coerceIn(0f, 1f) else 0f
            val pctPaid = if (effAmount > 0.005) {
                kotlin.math.round((paidTotal / effAmount) * 100.0).toInt().coerceIn(0, 100)
            } else 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 1.dp)
                    .testTag("calcLedgerTotal"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (effAmount > 0.005) {
                    PayRing(
                        percentPaid = pctPaid,
                        fraction = paidFraction,
                        fullyPaid = remainingTotal <= 0.005 && paidTotal > 0.005,
                        tag = "calcPayRing",
                        pctTag = "calcPayRingPct",
                    )
                }
                Column(Modifier.weight(1f)) {
                    // v1.6.48 — the total number sits BESIDE the "TOTAL"
                    // label — ONE row: label, amount, the sum's currency
                    // glyph (v1.6.50 — PLAIN TEXT: only the HEADER's currency
                    // is tappable now, report #3), the items hint
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(t("ee.calcTotal"), fontSize = F.small.sp, fontWeight = FontWeight.SemiBold, color = C.sub)
                        Text(
                            moneyNumber(effAmount),
                            fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.brand,
                            fontFamily = calcFf,
                            modifier = Modifier.testTag("calcTotalAmount"),
                        )
                        CurrencyText(cur = entryCur, tag = "calcTotalCur")
                        if (itemsSum > 0.0) {
                            Text("+ ${money(itemsSum)}", fontSize = 11.sp, color = C.faint)
                        }
                    }
                    if (paidTotal > 0.005) {
                        Text(
                            "${t("lg.paid")} ${money(paidTotal, entryCur)} · ${t("lg.remaining")} ${money(remainingTotal, entryCur)}",
                            fontSize = 11.sp, color = C.faint,
                            modifier = Modifier.testTag("calcTotalPaidLine"),
                        )
                    }
                }
                PayChip(
                    tag = "calcTotalPay",
                    // always armed: the chip is the HISTORY's entry point too
                    // (its own Pay action hides when nothing remains)
                    enabled = effAmount > 0,
                ) { payModal = -1 }
                // v1.6.45 — report #6: a copy button FOR THE SUM TOTAL AND
                // EVERYTHING IN IT. v1.6.50 — report #1 round two ("the copy
                // btn that is bised the pay btn in the sum total should copy
                // ALL things in that canvas"): the export now carries the
                // ACTIVE line too (with its live total) — title, every
                // committed line with its own total + date, every payment,
                // the items hint, the TOTAL and Paid · Remaining: literally
                // everything the canvas holds at the moment of the tap.
                CopyBtn(tag = "calcTotalCopy") {
                    copyLineText(
                        formatCalcLedgerExport(
                            calcLines + activeExportNote(), entryPayments, itemsSum, effAmount, paidTotal,
                            title = calcTitle.trim().ifEmpty { null },
                            totalLabel = t("ee.calcTotal"), paidLabel = t("lg.paid"),
                            remainingLabel = t("lg.remaining"), itemsLabel = t("lg.items"),
                        )
                    )
                }
            }
            // THE SMALL CONTROLS — ON the canvas, on the NEXT LINE under
            // the active one, and SMALL (the user's v1.6.39 spec): the
            // Aa / 123 input method and the commit button.
            // v1.6.40 — the toggle matches the button's HEIGHT.
            // v1.6.42 — REPORT #4: the clear-✕ beside the Aa/123 toggle is
            // REMOVED ("want to remove x btn beside the text and numbers
            // toggle that is for clear the line") — AC on the keypad and
            // ⌫ already clear the active line.
            // v1.6.46 — report #3: the SAVE button is GONE and the commit
            // button does what it did ("the next btn also should do what
            // the save btn does"): the tap commits the line (if any) and
            // persists the entry through the save pulse.
            // v1.6.50 — report #2 ("keep all 100% of it's functionality but
            // let's call the next btn save btn"): the label now reads SAVE
            // (the key ee.calcNext carries the new wording in every
            // language) — the behavior is untouched, 100% the same action.
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
                // Save — SMALL; commit + PERSIST (v1.6.46 report #3 gave it
                // the save duty, v1.6.50 report #2 renamed it: "keep all
                // 100% of it's functionality but let's call the next btn
                // save btn"). A text-only line commits as an annotation line
                // first (v1.6.40); blank with nothing payable is the only
                // disabled case.
                val nextEnabled = calcText.isNotBlank() || effAmount > 0.005
                Box(
                    Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (nextEnabled) C.brandSoft else C.card)
                        .border(1.dp, if (nextEnabled) C.brand else C.line, RoundedCornerShape(9.dp))
                        .clickable(enabled = nextEnabled) { vibrate(longArrayOf(8)); commitAndSave() }
                        .testTag("calcBtnNext")
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(t("ee.calcNext"), fontSize = F.tiny.sp, fontWeight = FontWeight.ExtraBold, color = if (nextEnabled) C.brand else C.faint) }
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
    // v1.6.49 note: the form is GONE — the canvas IS the whole editor — so
    // the old `if (amountMode == "calc" && calcExpanded) {` wrapper (whose
    // closer used to sit here) no longer exists; the editor's trailing
    // blocks below live directly in the function body until its real end.

    // v1.6.40 — THE SHARED PAYMENTS MODAL. v1.6.45 — report #2: the modal
    // opens from THE SUM TOTAL'S PAY BUTTON ONLY (the per-line chips and
    // history blocks are gone from the canvas): the list shows EVERY
    // recorded payment — date, amount, the new amount it left, and the
    // FULL note (wrapping, never ellipsized) — under one compact summary
    // line, with Pay (the compact record sheet, capped at the remaining)
    // and Copy (the WHOLE ledger export, report #6) beneath. The editor
    // stays open under it; recording a payment updates the total's
    // paid · remaining + the LEFT CIRCLE live. RTL-aware.
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
                    modifier = Modifier.padding(bottom = 6.dp).testTag("calcModalSummary"),
                )
                // THE PAYMENT HISTORY — tight rows, the note IN FULL and the
                // new amount it left on each (v1.6.45 report #2: "the history
                // of payment should show with modal that have lists with
                // their full notes")
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
                            // v1.6.49 — report #10 ("why there is no a way to
                            // update payment"): tapping a recorded payment
                            // opens ITS editor — amount / date / method /
                            // note, saved in place through
                            // AppStore.updatePayment
                            onOpen = { currentEntry?.let { ce -> openPaymentSheet(ce.id, edit = p) } },
                        )
                    }
                }
                // THE ACTIONS — one compact row (the Pay action only shows
                // while something remains; the history list + Copy stay)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (modalRemain > 0.005) {
                        Btn(
                            small = true, label = "${t("ee.calcPay")} · ${money(modalPayAmount)}", variant = "soft",
                            onPress = { makePaymentOf(modalPayAmount, modalLine) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Btn(
                        small = true, label = t("ee.calcCopyTotal"), variant = "secondary",
                        onPress = {
                            copyLineText(
                                formatCalcLedgerExport(
                                    calcLines + activeExportNote(), entryPayments, itemsSum, effAmount, paidTotal,
                                    totalLabel = t("ee.calcTotal"), paidLabel = t("lg.paid"),
                                    remainingLabel = t("lg.remaining"), itemsLabel = t("lg.items"),
                                    title = calcTitle.trim().ifEmpty { null },
                                )
                            )
                            payModal = null
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        }
    }

    // v1.6.48 — THE HEADER CURRENCY PICKER (report #1: "the ability to
    // change the currency"): the canvas header's currency chip opens this
    // compact sheet — the same eight currencies the Settings region row
    // carries. One tap writes settings.currency and every money() in the
    // app re-renders the same frame (the live default since v1.6.48); the
    // keypad hides while the sheet is up (wantPad) and re-arms on close.
    if (currencySheet) {
        com.callmony.ui.components.BottomSheet(
            title = t("st.currency"),
            onClose = { currencySheet = false },
        ) {
            Column {
                Row(Modifier.padding(bottom = 6.dp)) {
                    ScrollRow {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CURRENCIES.forEach { cur ->
                                Chip(
                                    label = "${cur.code} ${cur.symbol}",
                                    compact = true,
                                    active = AppStore.state.settings.currency == cur.code,
                                    onPress = {
                                        AppStore.updateSettings { it.copy(currency = cur.code) }
                                        currencySheet = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // v1.6.49 — THE SCOPED CURRENCY PICKER is GONE (v1.6.50 report #3:
    // "don't make The currency beside every total is TAPPABLE, just what
    // in the header is enough") — the per-line / per-total glyphs render
    // as plain text and the HEADER's picker above is the one currency
    // control on the canvas. The per-line currency DATA still rides the
    // grammar untouched (a line's own " ~USD" marker, the entry's
    // calcCurrency) so old rows keep rendering exactly as they were saved.

    // v1.6.50 — THE FONT-SIZE PICKER, IN PIXELS (report #6: "the font size
    // options should have meny options by px like in others"): eleven
    // numbered stops — 12 px → 22 px — one tap each; the choice rides the
    // entry as calcFontSize (a plain number string) so a reopened canvas
    // keeps it, and pre-v1.6.50 S/M/L rows decode to their own numbers.
    if (fontSheet) {
        com.callmony.ui.components.BottomSheet(
            title = t("st.fontSize"),
            onClose = { fontSheet = false },
        ) {
            Column {
                Row(Modifier.padding(bottom = 6.dp)) {
                    ScrollRow {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CALC_FONT_PX_OPTIONS.forEach { px ->
                                Chip(
                                    label = "$px px",
                                    compact = true,
                                    active = calcFontSpOf(calcFontSizeSel) == px,
                                    onPress = { calcFontSizeSel = px.toString(); fontSheet = false },
                                )
                            }
                        }
                    }
                }
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
/* v1.6.49 — 22dp (report #2): the copy button rides the individual
   total's OWN row now, so it shrank with it. */
@Composable
private fun CopyBtn(tag: String, onPress: () -> Unit) {
    val C = C()
    Box(
        Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(7.dp))
            .clickable { onPress() }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) { MdiIcon("content-copy", 12, C.sub) }
}

/* v1.6.40 — the per-line REMOVE button: a small red ✕ that asks through
   the custom confirmation modal before the line is deleted. */
@Composable
private fun DeleteBtn(tag: String, onPress: () -> Unit) {
    val C = C()
    Box(
        Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(7.dp))
            .clickable { onPress() }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) { MdiIcon("close", 12, C.red) }
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
/* v1.6.46 — the keypad's total height. v1.6.47 — still 264dp: the
   native bottom row (space + enter) simply became the SIXTH peer row
   (~37dp each) instead of a 34dp action band, so every key kept its
   size, the canvas's v1.6.43 direct-measurement reservation is
   untouched, and the pinned band stays 264 − 71 = 193dp. */
private val CALC_PAD_H = 264.dp

/** The text-keyboard colour — everything written in TEXT mode renders in
 *  this blue, everything written in NUMBER mode stays ink ("it should be
 *  different colour for what was written in text and number modes"). */
internal val TEXTMODE_COLOR = Color(0xFF4A7D9B)

@Composable
private fun CalcKeypadHostContent(onKey: (String) -> Unit, onEnter: () -> Unit, rtl: Boolean) {
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
                CalcPadGrid(onKey = onKey, onEnter = onEnter)
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
   never found it); the less-typed − takes the bottom-right slot instead.
   v1.6.46 — report #1: a wide ENTER filled its own row under ± 0 . −.
   v1.6.47 — report #1, round two ("the inter btn is not the next btn it
   is the normal inter btn like in the netiv keyboard and in its row add
   the space key too"): the bottom row is the NATIVE keyboard's bottom
   row — a WIDE BLANK SPACE BAR with a NORMAL-WIDTH enter key on the
   right, a peer of the five key rows (not an action bar). And the enter
   key is the NORMAL enter: a newline — it commits the active line and
   hands over a fresh one, and NOTHING else. It is NOT the Next button:
   Next (report #3 of v1.6.46) commits AND SAVES; enter never saves —
   persistence stays with the autosave, the leave-time create, and Next.
   The pad keeps its 264dp and all six rows share it (~37dp each):

    AC   [    %    ]  ⌫
     7    8    9    ÷
     4    5    6    ×
     1    2    3    +
     ±    0    .    −
   [      space      ] ⏎    */
@Composable
private fun CalcPadGrid(onKey: (String) -> Unit, onEnter: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            // v1.6.43 — real bottom padding on the keypad (the user's ask:
            // "the keypad should have some bottom padding") — the keys
            // already stop above the phone's system nav bar; this keeps a
            // visible breathing strip under the space row
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
        // v1.6.47 — THE NATIVE BOTTOM ROW: the wide blank SPACE bar with
        // the normal-width ENTER key on its right — one of the six key
        // rows now, not an action band
        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            SpaceKey(Modifier.weight(2.6f).fillMaxHeight(), onKey)
            EnterKey(Modifier.weight(1f).fillMaxHeight(), onEnter)
        }
    }
}

/* v1.6.50 — THE INSTANT, REPEATING KEY (report #7: "in the number keypad
   it should be very responsive fast and like when i click the x delete btn
   it us not fast and if I press it longer it just delete one thing, not
   continew deleting why, also thw space btn also just do one space why"):
   Compose's plain clickable fires its action on RELEASE — the felt lag —
   and a long-press did nothing after the first character. A keypad key
   should behave like a native one:
     - the action fires THE INSTANT the finger lands (DOWN, not up), so
       every key reads as instant;
     - keys that repeat natively (⌫, the space bar) REPEAT under a held
       finger: after a short initial delay the action re-fires on a tight
       interval until the finger lifts (exactly how the system keyboard's
       backspace and space behave);
     - the ONE-OFF press vibration rides only the initial landing — a
       repeating key never buzzes 16 times a second.
   Accessibility/tests keep their door: an OnClick SEMANTIC ACTION is
   published that performs exactly one press (performClick and TalkBack
   both route through it). */
@Composable
private fun Modifier.pressKey(
    key: Any?,
    initialDelayMs: Long? = null,
    repeatMs: Long = 60L,
    onDown: () -> Unit = {},
    onTick: () -> Unit,
    onRepeatTick: (() -> Unit)? = null,
): Modifier {
    val scope = rememberCoroutineScope()
    val tick by rememberUpdatedState(onTick)
    val repeatTick by rememberUpdatedState(onRepeatTick ?: onTick)
    return this
        .pointerInput(key) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                onDown()
                tick()
                if (initialDelayMs == null) {
                    // a one-off key: just watch for the release
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.changes.any { !it.pressed }) break
                    }
                } else {
                    // a REPEATING key: the ticker lives in the OUTER scope so
                    // it keeps firing on its own clock while the finger
                    // holds still (a still press raises no pointer events);
                    // the release (or a cancel) cancels it in the finally
                    val job = scope.launch {
                        kotlinx.coroutines.delay(initialDelayMs)
                        while (isActive) {
                            repeatTick()
                            kotlinx.coroutines.delay(repeatMs)
                        }
                    }
                    try {
                        while (true) {
                            val ev = awaitPointerEvent()
                            if (ev.changes.any { !it.pressed }) break
                        }
                    } finally {
                        job.cancel()
                    }
                }
            }
        }
        .semantics {
            onClick(label = null) {
                tick()
                true
            }
        }
}

/* v1.6.47 — THE SPACE BAR (report #1: "in its row add the space key
 * too"): the native keyboard's blank wide bar — card fill, hairline,
 * no glyph (native bars carry no label). v1.6.50 — report #7: the bar
 * fires the instant the finger lands and REPEATS while it is held (the
 * system keyboard's own space behaviour — "also thw space btn also just
 * do one space why"). */
@Composable
private fun SpaceKey(modifier: Modifier = Modifier, onPress: (String) -> Unit) {
    val C = C()
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(C.card)
            .border(1.dp, C.line, RoundedCornerShape(14.dp))
            .pressKey(
                key = "space",
                initialDelayMs = SPACE_REPEAT_DELAY_MS,
                repeatMs = SPACE_REPEAT_MS,
                onDown = { vibrate(longArrayOf(8)) },
                onTick = { onPress("␣") },
                onRepeatTick = { onPress("␣!") },
            )
            .testTag("calcKeySpace"),
        contentAlignment = Alignment.Center,
    ) {}
}

/* v1.6.46 — the ENTER key. v1.6.47 — report #1, round two: a
 * NORMAL-WIDTH brand-filled key (the native keyboard's return), glyph
 * only, never disabled — and it is the NORMAL ENTER, not the commit+save
 * button: the tap COMMITS the active line (the canvas's newline) and
 * hands over a fresh one; it never saves (Save owns the persist).
 * v1.6.50 — the press fires on DOWN (report #7, one-off). */
@Composable
private fun EnterKey(modifier: Modifier = Modifier, onEnter: () -> Unit) {
    val C = C()
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(C.brand)
            .pressKey(
                key = "enter",
                onDown = { vibrate(longArrayOf(8)) },
                onTick = { onEnter() },
            )
            .testTag("calcKeyEnter"),
        contentAlignment = Alignment.Center,
    ) { MdiIcon("keyboard-return", 20, C.onBrand) }
}

/* One key of the numeric calculator pad — flat tile, brand-tinted
   operators, red backspace, blue % (the app calculator's language).
   v1.6.38: DIGITS 24sp (bigger glyphs, same cell). v1.6.39: = joins the
   operator styling. v1.6.41: = is gone; ⌫ is the top-right key.
   v1.6.46 — the de-bulked calculation font (report #5: "make the fonts
   better for calculation, now it looks to bulky"): digits 24→23sp and
   Bold→SemiBold, operators 21→20sp SemiBold — the keys read lighter and
   calmer without losing their tap-target size. The digits/operators
   render in the calculation typeface (v1.6.50, report #8) so the keys and
   the line they write share one visual voice.
   v1.6.50 — report #7: every key fires on DOWN (instant), and ⌫ REPEATS
   while held — "if I press it longer it just delete one thing, not
   continew deleting why" — after a short delay, at a tight cadence,
   until the finger lifts (the native backspace contract). */
@Composable
private fun CalcKey(key: String, modifier: Modifier = Modifier, onPress: (String) -> Unit) {
    val C = C()
    val op = key in listOf("+", "-", "×", "÷")
    val display = if (key == "-") "−" else key
    val ff = calcFontFamily()
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
            .pressKey(
                key = key,
                initialDelayMs = if (key == "⌫") BACKSPACE_REPEAT_DELAY_MS else null,
                repeatMs = BACKSPACE_REPEAT_MS,
                onDown = { vibrate(longArrayOf(8)) },
                onTick = { onPress(key) },
            )
            .testTag("calcKey:$key"),
        contentAlignment = Alignment.Center,
    ) {
        if (key == "⌫") {
            MdiIcon("backspace-outline", 20, C.red)
        } else if (key == "AC") {
            Text("AC", fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C.red, fontFamily = ff)
        } else {
            Text(
                display,
                fontSize = if (key.length == 1 && key[0].isDigit()) 23.sp else 20.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ff,
                color = when {
                    op -> C.brand
                    key == "%" || key == "±" -> Color(0xFF4a7d9b)
                    else -> C.ink
                },
            )
        }
    }
}

/* v1.6.50 — the key-repeat timing (report #7): a ~350ms hold before a
 * repeating key starts re-firing, then a ~55ms cadence — the system
 * keyboard's own feel, fast enough to chew through a number, slow enough
 * to stay controllable. Pure constants, pinned by the harness. */
internal val BACKSPACE_REPEAT_DELAY_MS = 350L
internal val BACKSPACE_REPEAT_MS = 55L
internal val SPACE_REPEAT_DELAY_MS = 420L
internal val SPACE_REPEAT_MS = 90L

/** v1.6.50 — how many REPEAT ticks a hold of `holdMs` produces under the
 *  pressKey timing (the initial press-on-down is NOT a tick): the first
 *  repeat lands at `initialDelayMs`, then every `repeatMs`. Pure, so the
 *  harness pins the native-feel schedule exactly. */
internal fun keyRepeatTicks(holdMs: Long, initialDelayMs: Long, repeatMs: Long): Int {
    if (holdMs < initialDelayMs) return 0
    return ((holdMs - initialDelayMs) / repeatMs).toInt() + 1
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

/* v1.6.41 — the locked line's text with the TEXT-mode pieces coloured.
   v1.6.45 — report #3 ("the colour separation for what is written in both
   mode should be always there wether it is saved or edited"): the ranges
   come from textModeRanges — QUOTED numbers AND every word (letters can
   only come from the text keyboard) — the exact same rule the active
   field's qwerty marks are rebuilt from, so a line looks identical live,
   committed and re-edited. Pure, so the harness pins the span layout. */
internal fun coloredLockedLine(s: String, base: Color, quoted: Color): AnnotatedString {
    val b = AnnotatedString.Builder(s)
    if (s.isNotEmpty()) b.addStyle(SpanStyle(color = base), 0, s.length)
    val marks = textModeRanges(s)
    var i = 0
    while (i < s.length) {
        if (i in marks) {
            var j = i
            while (j < s.length && j in marks) j++
            b.addStyle(SpanStyle(color = quoted), i, j)
            i = j
        } else {
            i++
        }
    }
    return b.toAnnotatedString()
}

/* v1.6.45 — report #6 ("why the copy btn is not including the total"):
   a line's copy carries its OWN total when it has one. Pure. */
internal fun lineCopyText(display: String, total: Double?): String =
    if (total != null) "$display = ${money(total)}" else display

/* v1.6.47 — report #3 ("show bullet or numbers at the start of each line
 * that the user can change"): the prefix a line of index `i` carries —
 * "•" for every line in bullet mode, "1." "2." "3." in number mode, null
 * when the canvas is set to none. Pure, so the harness pins the walk. */
internal fun calcPrefixOf(mode: String, i: Int): String? = when (mode) {
    "number" -> (i + 1).toString() + "."
    "none" -> null
    else -> "•"
}

/** v1.6.45 — report #6 ("it should be a copy btn for the sum total and
 *  everything in there"): the WHOLE calculator as plain text — every
 *  committed line with its date and its own total, each line's payments
 *  (date · amount · note · what it left), the line items sum, the TOTAL,
 *  and Paid · Remaining. Labels are passed in translated so the export
 *  speaks the app's language; pure, so the harness pins the format.
 *  v1.6.46 — the CANVAS TITLE heads the export when there is one (it is
 *  part of the calculation now). */
internal fun formatCalcLedgerExport(
    lines: List<CalcNote>,
    payments: List<com.callmony.model.Payment>,
    itemsSum: Double,
    total: Double,
    paid: Double,
    totalLabel: String,
    paidLabel: String,
    remainingLabel: String,
    itemsLabel: String,
    title: String? = null,
): String {
    val sb = StringBuilder()
    if (!title.isNullOrBlank()) sb.append(title.trim()).append("\n\n")
    lines.forEachIndexed { i, line ->
        val lt = evalCalcTokens(line.tokens)
        sb.append(lineCopyText(displayCalcNote(line), lt))
        if (line.date.isNotEmpty()) sb.append("  ·  ${com.callmony.lib.fd(line.date)}")
        sb.append('\n')
        var run = lt
        payments.filter { it.lineIdx == i }.forEach { p ->
            run = r2((run ?: 0.0) - p.amount)
            sb.append("    − ${money(p.amount)}  ·  ${com.callmony.lib.fd(p.date)}")
            if (!p.note.isNullOrBlank()) sb.append("  —  ${p.note!!.trim()}")
            if (run != null) sb.append("  (→ ${money(run)})")
            sb.append('\n')
        }
    }
    // v1.6.45 — ENTRY-LEVEL payments (made against the sum total) walk the
    // grand total down after the lines, each with its date and full note
    var entryRun = r2(total - sumPayments(payments.filter { it.lineIdx != null }))
    payments.filter { it.lineIdx == null }.forEach { p ->
        entryRun = r2(entryRun - p.amount)
        sb.append("    − ${money(p.amount)}  ·  ${com.callmony.lib.fd(p.date)}")
        if (!p.note.isNullOrBlank()) sb.append("  —  ${p.note!!.trim()}")
        sb.append("  (→ ${money(entryRun)})\n")
    }
    if (itemsSum > 0.0) sb.append("$itemsLabel: ${money(itemsSum)}\n")
    sb.append("$totalLabel: ${money(total)}\n")
    sb.append("$paidLabel: ${money(paid)} · $remainingLabel: ${money(r2(total - paid))}\n")
    return sb.toString().trimEnd('\n')
}

/* v1.6.46 — the sum-total ring: its sweep FILLS with the paid fraction
   (brand while paying, green once done). v1.6.49 — report #4 ("why the
   persontage in the cercle says 100 when there is no any payment"): the
   centre now carries the PERCENTAGE PAID — the old percentage-LEFT read
   100% on a fresh entry with nothing paid at all; it reads 0% now and
   walks to a green 100% as money lands, matching the entry cards' rings.
   The ring shrank 40dp → 30dp for the compact sum-total row. */
@Composable
private fun PayRing(
    percentPaid: Int,
    fraction: Float,
    fullyPaid: Boolean,
    tag: String,
    pctTag: String,
) {
    val C = C()
    Box(
        Modifier
            .size(30.dp)
            .testTag(tag),
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
                    color = if (fullyPaid) C.green else C.brand,
                    startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            "$percentPaid%",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (fullyPaid) C.green else C.brand,
            modifier = Modifier.testTag(pctTag),
        )
    }
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

/* v1.6.49 — THE PREFIX COLUMN's width (report #3: the left edge tightened
   — 20dp → 16dp; with the lock off the left chain the calculation text
   starts ~32dp from the screen edge instead of ~64dp).
   v1.6.50 — report #4 ("there is a large gap between the bullet, number,
   from the content at the start… it should be consistent if there is
   bullet, number or notting"): 16dp → 14dp, CENTERED, and the slot is
   ALWAYS RESERVED — bullet, number or nothing, the column (and therefore
   the content's left edge) sits at exactly the same x on every line and
   in every prefix mode, and the glyph-to-content gap is one tight
   constant instead of the wide left-aligned hole a tiny • left.
   v1.6.51 — report #1 ("the dot bised number is showing up in the second
   row, like 1. it should be like this but the dot aft5the number is
   going to the second row"): a FIXED 14dp slot could not hold "1." at the
   bigger PIXEL-ladder sizes — IBM Plex Sans Bold's digit (≈0.60em) plus
   period (≈0.28em) overtake 14dp from ~15px up, and the Text WRAPPED the
   period onto a second row inside the slot. The width is now COMPUTED:
   the canvas's own font size × the advance of the WIDEST number the
   canvas can show (lineCount drives the digit count), floored at the
   v1.6.50 reservation so small canvases keep their exact look. One width
   per canvas → the same-x contract survives untouched. Pure, so the
   harness pins the geometry. */
internal fun prefixSlotDpOf(fontPx: Int, lineCount: Int): Float {
    val digits = maxOf(1, lineCount).toString().length
    // Plex Sans Bold advances in em: digit ≈ 0.60, period ≈ 0.28, plus
    // 0.30em of air so the glyph never sits against the slot's edge
    val em = 0.60f * digits + 0.28f + 0.30f
    return maxOf(14f, fontPx * em)
}

/* v1.6.50 — THE CANVAS'S FONT SIZE, ONE NUMBER (report #4: "the text and
 *  calculations font size should be thw same when it is edited and when it
 *  is locked, please i told you this many times but you didn't listen" —
 *  the v1.6.49 cut still rendered the ACTIVE line at 18sp while a LOCKED
 *  line rendered at 14sp, so the text visibly shrank the moment it
 *  committed): there is now exactly ONE size — the active field, the
 *  locked ledger lines, every total, the prefix and the steps all render
 *  at it, in every state, by construction.
 *  Report #6 ("the font size options should have meny options by px like
 *  in others"): the choice is a plain PIXEL NUMBER ("12"…"22") stored on
 *  the entry as calcFontSize; the old "s"/"m"/"l" markers decode to
 *  13/14/16 so every pre-v1.6.50 row reopens at the size it was saved
 *  with. Pure, so the harness pins the numbers. */
internal data class CalcFontSizes(val sp: Int)

internal val DEFAULT_CALC_SP = 14

internal fun calcFontSpOf(mode: String?): Int = when (mode) {
    "s" -> 13
    "l" -> 16
    "m" -> 14
    else -> mode?.toIntOrNull()?.coerceIn(CALC_FONT_PX_MIN, CALC_FONT_PX_MAX) ?: DEFAULT_CALC_SP
}

internal fun calcFontSizesOf(mode: String?): CalcFontSizes = CalcFontSizes(calcFontSpOf(mode))

internal val CALC_FONT_PX_MIN = 12
internal val CALC_FONT_PX_MAX = 22

/** v1.6.50 — the picker's PIXEL LADDER (report #6: "many options by px
 *  like in others"): eleven stops, 12px → 22px. Pure, so the harness pins
 *  the ladder itself. */
internal val CALC_FONT_PX_OPTIONS: List<Int> = (CALC_FONT_PX_MIN..CALC_FONT_PX_MAX).toList()

/* v1.6.49 — the currency glyph beside every total (that round's report #1
 *  made it a TAPPABLE chip).
 * v1.6.50 — report #3 ("don't make The currency beside every total is
 *  TAPPABLE, just what in the header is enough"): the chip is now PLAIN
 *  TEXT — no border, no fill, no click — a small glyph that simply states
 *  which currency the number beside it renders in (the line's own choice
 *  → the entry's sum currency → the live setting). The ONE tappable
 *  currency control left on the canvas is the HEADER's chip
 *  (calcCurrencyBtn), which writes the live app-wide setting. The tag
 *  stays so the harness can pin the not-tappable contract. */
@Composable
private fun CurrencyText(cur: String?, tag: String) {
    val C = C()
    Text(
        currencySymbol(cur ?: AppStore.state.settings.currency),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = C.faint,
        maxLines = 1,
        modifier = Modifier.testTag(tag),
    )
}

