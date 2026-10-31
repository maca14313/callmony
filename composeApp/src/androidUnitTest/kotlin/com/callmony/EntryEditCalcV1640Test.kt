package com.callmony

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.40 — regression battery for the annotated calculator rework:

     1. TEXT-MODE NUMBERS NEVER CALCULATE: "2 rooms" typed on the QWERTY
        shows no total; the keypad's 500 after it is the only math; the
        save keeps the split ("2" rooms 500 → amount 500, the quoted 2 is
        an annotation that reopens the same way).
     2. A pure TEXT line commits as an annotation line (kept on the note,
        never totalled).
     3. THE TOTAL sits after the last entry and opens the shared PAYMENTS
        MODAL (original · history · actions) on tap — the old dropdown is
        gone.
     4. MAKE PAYMENT REDUCES: recording a payment from the modal updates
        the entry AND the total's Paid · Remaining line live.
     5. Deleting a line goes through the CUSTOM CONFIRMATION modal.
     6. SAVE KEEPS THE EDITOR OPEN (the canvas stays up; the entry exists;
        the autosave owns later changes).
     7. The CLEAR button beside the Aa/123 toggle asks before clearing.

   Harness notes (same as every compose harness here): hydrated is
   deliberately NOT reset in @Before; onboarded flips AFTER
   waitUntil(hydrated) so the boot's empty publish can never overwrite
   it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EntryEditCalcV1640Test {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun freshApp() {
        UiStore.closeAll()
        UiStore.tabInternal = "general"
        UiStore.locked = false
        // NOTE: state.hydrated is deliberately NOT reset — see class docs.
    }

    private fun ready() {
        rule.waitUntil(timeoutMillis = 90000) { AppStore.state.hydrated }
        rule.runOnUiThread {
            AppStore.state.settings = AppStore.state.settings.copy(onboarded = true)
        }
        rule.waitForIdle()
    }

    private fun openEditor(entryId: String? = null) {
        rule.runOnUiThread { UiStore.open(Overlay.EntryEdit(entryId, "u:me")) }
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

    private fun toTextMode() {
        rule.onNodeWithTag("calcModeText").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTextField").fetchSemanticsNodes().isNotEmpty()
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

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun entriesNow(): String = rule.runOnUiThread {
        AppStore.state.entries.joinToString("; ") { "amt=${it.amount} expr=${it.exprNote} pays=${it.payments.size}" }
    }

    /* 1 — TEXT-MODE NUMBERS NEVER CALCULATE (the report: "if it is in text
       mode it shouldn't calculate the number that was written when it was
       in text mode") */
    @Test
    fun textModeNumbersAreNotCalculated() {
        ready()
        openEditor()

        // type "2 rooms" on the QWERTY — NO total may appear
        val field = rule.onNodeWithTag("calcTextField")
        field.performClick()
        field.performTextInput("2 rooms")
        rule.waitForIdle()
        assertEquals(
            "a number typed in text mode must not compute",
            0,
            rule.onAllNodesWithTag("calcTotal").fetchSemanticsNodes().size,
        )

        // the keypad's 500 after it is the ONLY math
        toNumMode()
        listOf("5", "0", "0").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("500 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // Save keeps the split: amount 500, note "2 rooms 500 with the 2 quoted
        rule.onNodeWithTag("calcBtnSave").performClick()
        rule.waitForIdle()
        val saved = rule.runOnUiThread {
            AppStore.state.entries.firstOrNull { it.amount == 500.0 }
        }
        checkNotNull(saved) { "entry not saved — entries: ${entriesNow()}" }
        assertEquals("the quoted 2 must persist as an annotation", "\"2\" rooms 500", saved.exprNote)

        // the quoted 2 reopens as an annotation: total still 500
        val reopened = rule.runOnUiThread {
            val e = AppStore.state.entries.first { it.amount == 500.0 }
            com.callmony.lib.parseCalcLedger(e.exprNote ?: "")
        }
        assertEquals(500.0, com.callmony.lib.calcLedgerTotal(reopened)!!, 0.0)
    }

    /* 2 — a pure TEXT line commits as an annotation line */
    @Test
    fun aPureTextLineCommitsAsAnAnnotation() {
        ready()
        openEditor()
        val field = rule.onNodeWithTag("calcTextField")
        field.performClick()
        field.performTextInput("note only")
        rule.waitForIdle()

        // Next line is armed for text (blank is the only disabled case)
        nextLine()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().isNotEmpty()
        }
        // the locked line carries the annotation verbatim
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("note only", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        // and nothing totalled: no amount anywhere
        assertEquals(0, rule.onAllNodesWithTag("calcTotal").fetchSemanticsNodes().size)
    }

    /* 3 — v1.6.42: the SUM total no longer opens a payments modal (report
       #6 — it is a pure read-out); an UNLOCKED entry's pay chip opens the
       shared modal (original · history) */
    @Test
    fun theSumTotalIsAReadOutAndUnlockedLinesOpenTheModal() {
        ready()
        openEditor()
        toNumMode()
        listOf("3", "0", "0").forEach { key(it) }
        nextLine()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().isNotEmpty()
        }

        // the sum total is INERT now — v1.6.42: its semantics carry NO
        // click action (the tag merges into an ancestor, so a performClick
        // would tap the merged node's center — poison for the pill toggles
        // below — hence the pure semantics probe instead of a dead tap)
        val totalNode = rule.onNodeWithTag("calcLedgerTotal").fetchSemanticsNode()
        assertEquals(
            "the sum total must not be clickable",
            false,
            totalNode.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick),
        )

        // unlock the entry → its pay chip opens the modal
        rule.onNodeWithTag("calcLineLock:0").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("calcLinePay:0").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("${t("ee.calcOriginal")}: 300 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "the no-payments note shows",
            rule.onAllNodes(hasText(t("ee.calcNoPayments"), substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
        back() // close the modal
        rule.waitForIdle()
    }

    /* 4 — MAKE PAYMENT REDUCES the amount (paid · remaining live) — paid
       through an UNLOCKED entry's chip (v1.6.42 flow) */
    @Test
    fun makePaymentReducesTheAmount() {
        ready()
        openEditor()
        toNumMode()
        listOf("3", "0", "0").forEach { key(it) }
        nextLine()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().isNotEmpty()
        }

        // unlock the entry → its modal → Pay (the editor saves first and
        // STAYS OPEN)
        rule.onNodeWithTag("calcLineLock:0").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("calcLinePay:0").performClick()
        rule.waitForIdle()
        val makePay = rule.onAllNodes(hasText(t("ee.calcPay"), substring = true) and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { makePay.fetchSemanticsNodes().isNotEmpty() }
        makePay[makePay.fetchSemanticsNodes().size - 1].performClick()
        rule.waitForIdle()

        // the PAYMENT SHEET is up with the amount prefilled — save it
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("lg.recordPayment"), substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        savePaySheet()
        rule.waitForIdle()

        // back on the editor: the total's Paid · Remaining line is LIVE
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTotalPaidLine").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "the total must show the paid 300",
            rule.onAllNodes(hasText("${t("lg.paid")} 300 Br", substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
        // and the payment is really on the entry
        val paid = rule.runOnUiThread {
            AppStore.state.entries.filter { it.amount == 300.0 }.sumOf { it.payments.size }
        }
        assertTrue("the payment must be recorded — entries: ${entriesNow()}", paid > 0)
    }

    private fun savePaySheet() {
        // the payment sheet's Save button (the last act.save in z-order;
        // the editor's own Save buttons sit under the sheet)
        val saveBtns = rule.onAllNodes(hasText(t("act.save")) and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { saveBtns.fetchSemanticsNodes().isNotEmpty() }
        saveBtns[saveBtns.fetchSemanticsNodes().size - 1].performClick()
        rule.waitForIdle()
    }

    /* 5 — deleting a line goes through the CUSTOM CONFIRMATION modal */
    @Test
    fun deletingALineAsksFirst() {
        ready()
        openEditor()
        toNumMode()
        listOf("2", "0", "0").forEach { key(it) }
        nextLine()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().isNotEmpty()
        }

        // tap the line's ✕ → the confirmation modal, the line still there
        rule.onNodeWithTag("calcLineDelete:0").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("ee.calcDeleteLineTitle"), substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "the line must still exist while the modal asks",
            rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().isNotEmpty(),
        )
        // confirm → the line is gone
        val confirmBtn = rule.onAllNodes(hasText(t("act.delete")) and hasClickAction())
        confirmBtn[0].performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().isEmpty()
        }
    }

    /* 6 — SAVE KEEPS THE EDITOR OPEN (and the autosave owns the rest) */
    @Test
    fun saveKeepsTheEditorOpen() {
        ready()
        openEditor()
        toNumMode()
        listOf("2", "5", "0").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("250 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithTag("calcBtnSave").performClick()
        rule.waitForIdle()
        // the editor + canvas are STILL up (the canvas's own Save button
        // exists in both input modes)
        assertTrue(
            "save must not close the editor",
            rule.onAllNodesWithTag("calcBtnSave").fetchSemanticsNodes().isNotEmpty(),
        )
        // ...and the active line still lives in num mode (the keypad is up)
        assertTrue(
            rule.onAllNodesWithTag("calcKey:1").fetchSemanticsNodes().isNotEmpty(),
        )
        // the entry exists exactly once
        val count = rule.runOnUiThread {
            AppStore.state.entries.count { it.amount == 250.0 }
        }
        assertEquals(1, count)

        // keep editing: keypad + 50 → 300 — the AUTOSAVE persists it
        listOf("+", "5", "0").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("300 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(timeoutMillis = 12000) {
            rule.runOnUiThread { AppStore.state.entries.any { it.amount == 300.0 } }
        }
    }

    /* 7 — v1.6.42: the CLEAR button beside the Aa/123 toggle is REMOVED
       (report #4: "want to remove x btn beside the text and numbers
       toggle that is for clear the line") — AC and ⌫ on the keypad own
       clearing the line */
    @Test
    fun theClearButtonIsGone() {
        ready()
        openEditor()
        assertEquals(
            "the clear-line ✕ must be gone",
            0,
            rule.onAllNodesWithTag("calcBtnClear").fetchSemanticsNodes().size,
        )
        // and clearing still works through the keypad's AC (in num mode)
        toNumMode()
        listOf("2", "5").forEach { key(it) }
        rule.waitForIdle()
        rule.onNodeWithTag("calcKey:AC").performClick()
        rule.waitForIdle()
        assertTrue(
            "AC still clears the active line",
            rule.onAllNodes(hasText("25", substring = true)).fetchSemanticsNodes().isEmpty(),
        )
    }
}
