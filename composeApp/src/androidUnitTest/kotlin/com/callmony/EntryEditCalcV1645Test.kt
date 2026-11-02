package com.callmony

import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.lib.fd
import com.callmony.lib.todayStr
import com.callmony.ui.kit.HostSheets
import com.callmony.ui.kit.RootBottomOverlays
import com.callmony.ui.kit.Toasts
import com.callmony.ui.kit.Confirm
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.45 — regression battery for the user's report list:

     1. "i nottised sometimes the regular native keyboard shows up on the
        numbers mode" → NoImeEditText force-hides the IME on every focus /
        window-focus gain (pinned in NoImeFieldV1645Test, next to the
        v1.6.44 crash-door tests).
     2. "i want the pay btn to be just on the sum total not on the
        individual entry, and the history of the payment also should show
        when the payment btn is clicked on the sum total … with modal that
        have lists with their full notes. and need also a cercle that
        shows how many left" → the per-line pay chip / history / total-tap
        are GONE; the sum total carries the Pay chip (its modal = the
        history list with the full notes + the compact record sheet) and
        the LEFT CIRCLE, whose count walks down as payments settle the
        lines FIFO.
     3. "the colour separation … should be always there wether it is saved
        or edited" → textModeRanges drives the locked line's colours AND
        the marks rebuilt when a line is unlocked back into the active
        field (the num field's spans carry the word colours here, after a
        commit + long-press edit round-trip).
     4. "the record payment modal is to bulkt, agly, it should be very
        compact and easy" → the sheet's fat hint/headline/segmented blocks
        are gone (two input fields, one date picker, chip row, one save).
     5. "all things should have to display its date" → the line date and
        the payment date both render in the modal list.
     6. "why the copy btn is not including the total and it should be a
        copy btn for the sum total and everything in there" → the sum
        total's copy button lands the WHOLE ledger export on the
        clipboard, totals and dates included. */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EntryEditCalcV1645Test {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun freshApp() {
        UiStore.closeAll()
        UiStore.tabInternal = "general"
        UiStore.locked = false
    }

    @After
    fun cleanGlobalHosts() {
        HostSheets.stack.clear()
        RootBottomOverlays.content.value = null
        RootBottomOverlays.lift.value = 0.dp
        RootBottomOverlays.overlayTopPx.value = -1f
        Confirm.req.value = null
        Toasts.items.clear()
        AppStore.state.settings = AppStore.state.settings.copy(language = "en")
    }

    private fun ready() {
        rule.waitUntil(timeoutMillis = 90000) { AppStore.state.hydrated }
        rule.runOnUiThread {
            AppStore.state.settings = AppStore.state.settings.copy(onboarded = true)
        }
        rule.waitForIdle()
    }

    private fun openEditor() {
        rule.runOnUiThread { UiStore.open(Overlay.EntryEdit(null, "u:me")) }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTextField").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun toNumMode() {
        rule.onNodeWithTag("calcModeNum").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcKey:1").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun key(k: String) {
        rule.onNodeWithTag("calcKey:$k").performClick()
        rule.waitForIdle()
    }

    private fun nextLine() {
        rule.onNodeWithTag("calcBtnNext").performClick()
        rule.waitForIdle()
    }

    private fun awaitLine(i: Int) {
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLine:$i").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** The sum total's Pay chip → the payments modal → its Pay button → the
     * record sheet with `amount` typed in. Leaves the sheet SAVED. The
     * sheet's AMOUNT field is the second-to-last text input in z-order (its
     * note field is the last — everything beneath is the editor's own
     * form, composing under the full-screen canvas). */
    private fun payThroughTheTotal(amount: String) {
        rule.onNodeWithTag("calcTotalPay").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("ee.calcOriginal"), substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val payBtns = rule.onAllNodes(hasText(t("ee.calcPay"), substring = true) and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { payBtns.fetchSemanticsNodes().isNotEmpty() }
        payBtns[payBtns.fetchSemanticsNodes().size - 1].performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("lg.recordPayment"), substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val amountFields = rule.onAllNodes(hasSetTextAction())
        rule.waitUntil(timeoutMillis = 15000) { amountFields.fetchSemanticsNodes().size >= 2 }
        amountFields[amountFields.fetchSemanticsNodes().size - 2].performTextReplacement(amount)
        rule.waitForIdle()
        val saves = rule.onAllNodes(hasText(t("act.save")) and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { saves.fetchSemanticsNodes().isNotEmpty() }
        saves[saves.fetchSemanticsNodes().size - 1].performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("lg.recordPayment"), substring = true)).fetchSemanticsNodes().isEmpty()
        }
    }

    /* 2 — payments live ONLY on the sum total: no per-line chip, no
       per-line history block, a locked line's total is inert text */
    @Test
    fun payLivesOnlyOnTheSumTotal() {
        ready()
        openEditor()
        toNumMode()
        listOf("3", "0", "0").forEach { key(it) }
        nextLine()
        awaitLine(0)

        assertEquals(
            "the individual entry must not carry a pay chip",
            0,
            rule.onAllNodesWithTag("calcLinePay:0").fetchSemanticsNodes().size,
        )
        assertEquals(
            "the individual entry must not carry an on-canvas history block",
            0,
            rule.onAllNodesWithTag("calcLineHist:0").fetchSemanticsNodes().size,
        )
        // the line's total is INERT TEXT — tapping it opens nothing (the
        // tag lives in the unmerged tree now that the text has no click)
        rule.onNodeWithTag("calcLineTotal:0", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertEquals(
            "tapping a line's total must not open any modal",
            0,
            rule.onAllNodes(hasText(t("ee.calcOriginal"), substring = true)).fetchSemanticsNodes().size,
        )
        // the SUM TOTAL carries the chip — and it opens the modal
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTotalPay").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("calcTotalPay").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("${t("ee.calcTotal")} · 300 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "the empty history note shows in the modal",
            rule.onAllNodes(hasText(t("ee.calcNoPayments"), substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /* 2 — v1.6.46 — THE PAY RING ("a ring filling as you pay, should show
       how much % is left from the total and dont say something like 1
       left"): 2 lines totalling 500 → 100% left; pay 400 (of 500) → 20%
       left; pay the last 100 → 0% left. No "N left" caption anywhere. */
    @Test
    fun thePayRingCountsDownInPercentages() {
        ready()
        openEditor()
        toNumMode()
        listOf("3", "0", "0").forEach { key(it) }
        nextLine()
        awaitLine(0)
        toNumMode()
        listOf("2", "0", "0").forEach { key(it) }
        nextLine()
        awaitLine(1)

        // v1.6.49 — NOTHING PAID YET reads 0% (report #4: "why the
        // persontage in the cercle says 100 when there is no any payment") —
        // the ring counts what is PAID now, matching the entry cards
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcPayRing").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "an unpaid 500 must read 0% — the ring counts the paid share",
            rule.onAllNodes(hasText("0%")).fetchSemanticsNodes().isNotEmpty(),
        )
        // the v1.6.45 "N left" caption is GONE — no count text, no old tags
        assertEquals(
            "the 'N left' caption tag must be gone",
            0,
            rule.onAllNodesWithTag("calcLeftLabel").fetchSemanticsNodes().size,
        )
        assertEquals(
            "the old left-circle tags must be gone",
            0,
            rule.onAllNodesWithTag("calcLeftCircle").fetchSemanticsNodes().size,
        )
        assertEquals(
            "no '1 left' style text anywhere",
            0,
            rule.onAllNodes(hasText(" left", substring = true)).fetchSemanticsNodes().size,
        )

        // pay 400 of the 500 total → 400 paid → 80% PAID
        payThroughTheTotal("400")
        assertTrue(
            "after 400 of 500 the ring must read 80% paid",
            rule.onAllNodes(hasText("80%")).fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "the paid · remaining line shows 400 paid",
            rule.onAllNodes(hasText("${t("lg.paid")} 400 Br", substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )

        // the last 100 settles everything → a GREEN 100% paid
        payThroughTheTotal("100")
        assertTrue(
            "fully paid must read 100% (green)",
            rule.onAllNodes(hasText("100%")).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /* 2 + 5 — the modal the sum total's Pay opens lists the payments with
       their FULL notes and their DATES */
    @Test
    fun theModalHistoryListsFullNotesAndDates() {
        ready()
        openEditor()
        toNumMode()
        listOf("3", "0", "0").forEach { key(it) }
        nextLine()
        awaitLine(0)

        rule.onNodeWithTag("calcTotalPay").performClick()
        rule.waitForIdle()
        val payBtns = rule.onAllNodes(hasText(t("ee.calcPay"), substring = true) and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { payBtns.fetchSemanticsNodes().isNotEmpty() }
        payBtns[payBtns.fetchSemanticsNodes().size - 1].performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("lg.recordPayment"), substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        // a LONG note — its tail would vanish under any one-line clamp
        val fields = rule.onAllNodes(hasSetTextAction())
        rule.waitUntil(timeoutMillis = 15000) { fields.fetchSemanticsNodes().isNotEmpty() }
        fields[fields.fetchSemanticsNodes().size - 1]
            .performTextInput("paid by abel for the transport and the lunch of the whole family together")
        rule.waitForIdle()
        val saves = rule.onAllNodes(hasText(t("act.save")) and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { saves.fetchSemanticsNodes().isNotEmpty() }
        saves[saves.fetchSemanticsNodes().size - 1].performClick()
        rule.waitForIdle()

        // reopen the modal: the FULL note + the payment's date + → 0
        rule.onNodeWithTag("calcTotalPay").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcModalPayHist:0").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "the FULL note must show in the modal list",
            rule.onAllNodes(hasText("whole family together", substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "the payment row carries its date",
            rule.onAllNodes(hasText(fd(todayStr()), substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
        // 5 — and the line itself still shows its commit date
        assertTrue(
            "the committed line carries its date",
            rule.onAllNodes(hasText(fd(todayStr()), substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /* 4 — the record payment sheet is COMPACT: the fat hint paragraph is
       gone, exactly TWO text inputs (amount + note), the date picker, the
       method chips, and one save — nothing else stacked. */
    @Test
    fun theRecordPaymentSheetIsCompact() {
        ready()
        openEditor()
        toNumMode()
        listOf("3", "0", "0").forEach { key(it) }
        nextLine()
        awaitLine(0)

        rule.onNodeWithTag("calcTotalPay").performClick()
        rule.waitForIdle()
        val payBtns = rule.onAllNodes(hasText(t("ee.calcPay"), substring = true) and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { payBtns.fetchSemanticsNodes().isNotEmpty() }
        payBtns[payBtns.fetchSemanticsNodes().size - 1].performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("lg.recordPayment"), substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals(
            "the bulky hint paragraph must be gone",
            0,
            rule.onAllNodes(hasText(t("lg.paymentHint"), substring = true)).fetchSemanticsNodes().size,
        )
        assertEquals(
            "the kind · amount headline must be gone (the title carries it — the kind CHIPS in the pinned band are of course still there)",
            0,
            rule.onAllNodes(hasText("Credit · 300 Br", substring = true)).fetchSemanticsNodes().size,
        )
        assertTrue(
            "the amount comes prefilled with the remaining",
            rule.onAllNodes(hasText("300")).fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "the date picker rides the sheet",
            rule.onAllNodes(hasText(fd(todayStr()), substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "the method chips are compact chips, not fat segments",
            rule.onAllNodes(hasText(t("lg.pmCash"), substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /* 6 — the sum total's copy button lands EVERYTHING on the clipboard:
       the line with its own total and date, the payment with its note,
       and the sum total + paid · remaining. */
    @Test
    fun theSumTotalCopyIncludesEverything() {
        ready()
        openEditor()
        // the word on the QWERTY, the number on the keypad
        rule.onNodeWithTag("calcTextField").performTextInput("rooms")
        rule.waitForIdle()
        toNumMode()
        listOf("5", "0", "0").forEach { key(it) }
        nextLine()
        awaitLine(0)
        payThroughTheTotal("200")

        rule.onNodeWithTag("calcTotalCopy").performClick()
        rule.waitForIdle()
        val clip = rule.runOnUiThread {
            val cm = rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.getItemAt(0)?.text?.toString()
        }
        checkNotNull(clip) { "the copy button must put the ledger on the clipboard" }
        assertTrue("the line text must be there — got: $clip", clip.contains("rooms 500"))
        assertTrue("the line's OWN total must be included — got: $clip", clip.contains("= 500"))
        assertTrue("the line's commit date must be included — got: $clip", clip.contains(fd(todayStr())))
        assertTrue("the payment's note must be included — got: $clip", clip.contains("− 200"))
        assertTrue("the SUM total must be included — got: $clip", clip.contains("${t("ee.calcTotal")}: 500"))
        assertTrue("paid · remaining must be included — got: $clip", clip.contains("${t("lg.paid")}: 200"))
    }

    /* 3 — the colour separation survives the commit + long-press edit
       round-trip: unlocking the line back into the active field rebuilds
       the text-mode marks from the line's own tokens, so in number mode
       the word renders through the TEXT colour spans and the keypad
       number stays plain. */
    @Test
    fun colourSeparationSurvivesCommitAndEdit() {
        ready()
        openEditor()
        rule.onNodeWithTag("calcTextField").performTextInput("rooms")
        rule.waitForIdle()
        toNumMode()
        listOf("5", "0", "0").forEach { key(it) }
        nextLine()
        awaitLine(0)

        // pull the committed line back into the active field (it lands in
        // TEXT mode), then arm the native field in number mode
        rule.onNodeWithTag("calcLine:0").performTouchInput { longClick() }
        rule.waitForIdle()
        toNumMode()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcNumField").fetchSemanticsNodes().isNotEmpty()
        }
        rule.runOnUiThread { shadowOf(Looper.getMainLooper()).idle() }
        rule.waitForIdle()

        val et = com.callmony.ui.components.NoImeFieldProbe.last
        checkNotNull(et) { "the native number field must be composed" }
        val text = rule.runOnUiThread { et.text.toString() }
        assertTrue("the pulled-back line must keep its text — got: $text", text.contains("rooms"))
        val wordStart = text.indexOf("rooms")
        val numStart = text.indexOf("500")
        // the native field paints the TEXT-mode marks as PER-CHARACTER
        // spans — a character is coloured when some span wraps it
        val ranges = rule.runOnUiThread {
            et.text.getSpans(0, text.length, android.text.style.ForegroundColorSpan::class.java)
                .map { sp -> et.text.getSpanStart(sp) until et.text.getSpanEnd(sp) }
        }
        fun coloredAt(ci: Int) = ranges.any { ci in it }
        val wordColored = (wordStart until wordStart + "rooms".length).all { coloredAt(it) }
        val numColored = (numStart until numStart + 3).any { coloredAt(it) }
        assertTrue("the TEXT-mode word must carry its colour after the edit round-trip", wordColored)
        assertTrue("the keypad number must stay plain ink after the edit round-trip", !numColored)
    }
}
