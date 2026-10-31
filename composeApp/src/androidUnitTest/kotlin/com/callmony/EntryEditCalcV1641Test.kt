package com.callmony

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.ui.kit.Confirm
import com.callmony.ui.kit.HostSheets
import com.callmony.ui.kit.RootBottomOverlays
import com.callmony.ui.kit.Toasts
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.41 — regression battery for the annotated calculator rework:

     1. THE CARET IS ALWAYS THERE: the text field is focused when the
        canvas opens, after Next line, and after a num → text toggle.
     2. The = key is GONE; ⌫ took ÷'s place (top-right) and ÷ moved down.
     3. The keypad covers the BOTTOM NAVIGATION (root-level, like the IME).
     4. The line's own total sits BELOW the locked line, with the PAY chip
        beside it; the sum total carries a pay chip too.
     5. A payment cannot go above the remaining total (capped in the sheet).
     6. The DELETE-ENTRY button is gone from the footer.
     7. UNDO / REDO walk the calculator editing stack.
     8. The payment history shows ON THE CANVAS, notes included.
     9. RTL: the canvas composes and computes under Arabic.

   Harness notes (same as every compose harness here): hydrated is
   deliberately NOT reset in @Before; onboarded flips AFTER
   waitUntil(hydrated) so the boot's empty publish can never overwrite
   it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EntryEditCalcV1641Test {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun freshApp() {
        UiStore.closeAll()
        UiStore.tabInternal = "general"
        UiStore.locked = false
        // NOTE: state.hydrated is deliberately NOT reset — see class docs.
    }

    /* Robolectric reuses one sandbox across same-config tests, so GLOBAL
       hosts must be cleared after every test: a leaked open payment sheet
       (HostSheets entry) would keep the calculator's root keypad down and
       its scrim would swallow taps in every later test; the RTL test's
       Arabic would leave every later test reading Arabic strings. */
    @After
    fun cleanGlobalHosts() {
        HostSheets.stack.clear()
        RootBottomOverlays.content.value = null
        RootBottomOverlays.lift.value = 0.dp
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

    private fun awaitFocused(tag: String) {
        rule.waitUntil(timeoutMillis = 15000) {
            try {
                rule.onNodeWithTag(tag).fetchSemanticsNode()
                    .config[SemanticsProperties.Focused] == true
            } catch (_: Exception) { false }
        }
    }

    /* 1 — the caret is ALWAYS there: focused at canvas open, after Next
       line, and after a num → text toggle */
    @Test
    fun theCaretIsAlwaysWhereWritingContinues() {
        ready()
        openEditor()
        awaitFocused("calcTextField")

        // type a word, Next line → the fresh line is focused again
        // ("zebra" is NOT in the placeholder — the placeholder text itself
        // would match a hasText("ball") probe when the field empties)
        val field = rule.onNodeWithTag("calcTextField")
        field.performTextInput("zebra")
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("zebra", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("calcBtnUndo").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("zebra", substring = true)).fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithTag("calcBtnRedo").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("zebra", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        // toggle num → text: the caret comes back on the text field
        toNumMode()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcKey:1").fetchSemanticsNodes().isNotEmpty()
        }
        toTextMode()
        awaitFocused("calcTextField")
    }

    /* 2 — the = key is GONE; ⌫ sits in ÷'s place (top-right) and ÷ moved
       down a row */
    @Test
    fun equalsKeyIsGoneAndBackspaceTookDividePlace() {
        ready()
        openEditor()
        toNumMode()

        assertEquals(
            "the = key must be gone",
            0,
            rule.onAllNodesWithTag("calcKey:=").fetchSemanticsNodes().size,
        )
        val backspace = rule.onNodeWithTag("calcKey:⌫").fetchSemanticsNode()
        val divide = rule.onNodeWithTag("calcKey:÷").fetchSemanticsNode()
        assertTrue(
            "⌫ must sit ABOVE ÷ (it took its top-row place)",
            backspace.positionInRoot.y < divide.positionInRoot.y,
        )
        // and in the same (operator) column
        assertTrue(
            "⌫ and ÷ share the column",
            kotlin.math.abs(backspace.positionInRoot.x - divide.positionInRoot.x) < 40f,
        )
    }

    /* 3 — the keypad covers the BOTTOM NAVIGATION (root-level, like the
       system IME): its bottom edge reaches the window bottom */
    @Test
    fun theKeypadCoversTheBottomNavigation() {
        ready()
        openEditor()
        toNumMode()
        val pad = rule.onNodeWithTag("calcPadRoot").fetchSemanticsNode()
        val rootH = rule.onRoot().fetchSemanticsNode().size.height.toFloat()
        val padBottom = pad.positionInRoot.y + pad.size.height
        assertTrue(
            "the keypad must reach the window bottom (covers the nav) — padBottom=$padBottom rootH=$rootH",
            padBottom >= rootH - 80f,
        )
        // and it is TALLER than the old in-canvas band: it also covers the
        // nav band, so its top sits above the content area's bottom
        val controls = rule.onNodeWithTag("calcBtnNext").fetchSemanticsNode()
        assertTrue(
            "the controls row must sit above the keypad top",
            controls.positionInRoot.y + controls.size.height <= pad.positionInRoot.y + 8f,
        )
    }

    /* 4 — v1.6.42: the line's own total sits BELOW the locked line with the
       PAY chip beside it (DISABLED while locked); the sum total carries NO
       pay chip anymore (report #6 — it is a pure read-out now) */
    @Test
    fun lineTotalsSitBelowWithAPayChip() {
        ready()
        openEditor()
        toNumMode()
        listOf("1", "×", "2", "0", "0").forEach { key(it) }
        nextLine()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().isNotEmpty()
        }

        // the total is BELOW the line's text
        val lineText = rule.onAllNodes(hasText("1 × 200", substring = true))
            .fetchSemanticsNodes().first()
        val lineTotal = rule.onNodeWithTag("calcLineTotal:0").fetchSemanticsNode()
        assertTrue(
            "the line's own total must sit BELOW its calculation",
            lineTotal.positionInRoot.y > lineText.positionInRoot.y,
        )
        // the pay chip beside it (v1.6.42: locked = present but INERT)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLinePay:0").fetchSemanticsNodes().isNotEmpty()
        }
        // the sum total carries NO pay chip (report #6)
        assertEquals(
            "the sum total must not carry a pay chip",
            0,
            rule.onAllNodesWithTag("calcTotalPay").fetchSemanticsNodes().size,
        )
    }

    /* 5 — v1.6.42: a payment cannot go above the remaining total — paid
       through an UNLOCKED line's pay chip (the sum total is a read-out
       now; the gate opens with the lock pill) */
    @Test
    fun aPaymentCannotGoAboveTheTotal() {
        ready()
        openEditor()
        toNumMode()
        listOf("3", "0", "0").forEach { key(it) }
        nextLine()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().isNotEmpty()
        }
        // unlock the entry (the pay gate), then open its modal
        rule.onNodeWithTag("calcLineLock:0").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("calcLinePay:0").performClick()
        rule.waitForIdle()

        // the modal's Pay (prefilled with the remaining 300)
        val payBtns = rule.onAllNodes(hasText(t("ee.calcPay"), substring = true) and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { payBtns.fetchSemanticsNodes().isNotEmpty() }
        payBtns[payBtns.fetchSemanticsNodes().size - 1].performClick()
        rule.waitForIdle()

        // the payment sheet: replace the AMOUNT (matched by its prefilled
        // 300 — the editor's own fields compose beneath the sheet) with 400
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("lg.recordPayment"), substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val amountField = rule.onAllNodes(hasSetTextAction() and hasText("300"))
        rule.waitUntil(timeoutMillis = 15000) { amountField.fetchSemanticsNodes().isNotEmpty() }
        amountField[0].performTextReplacement("400")
        rule.waitForIdle()
        // Save → refused with the cap toast, nothing recorded
        val saves = rule.onAllNodes(hasText(t("act.save")) and hasClickAction())
        saves[saves.fetchSemanticsNodes().size - 1].performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("lg.paymentTooMuch"), substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val overCapRecorded = rule.runOnUiThread {
            AppStore.state.entries.any { e -> e.payments.any { it.amount == 400.0 } }
        }
        assertTrue("the over-cap payment must NOT be recorded", !overCapRecorded)
    }

    /* 6 — the DELETE-ENTRY button is gone from the editor */
    @Test
    fun theDeleteEntryButtonIsGone() {
        ready()
        openEditor()
        assertEquals(
            "no delete-entry button in the editor",
            0,
            rule.onAllNodes(hasText(t("act.delete")) and hasClickAction()).fetchSemanticsNodes().size,
        )
    }

    /* 7 — UNDO / REDO walk the calculator editing stack */
    @Test
    fun undoRedoWalkTheEditingStack() {
        ready()
        openEditor()

        // type on the QWERTY → undo → gone → redo → back
        val field = rule.onNodeWithTag("calcTextField")
        field.performTextInput("zebra")
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("zebra", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("calcBtnUndo").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("zebra", substring = true)).fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithTag("calcBtnRedo").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("zebra", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // a committed line: undo brings it back down, redo re-commits it
        toNumMode()
        listOf("2", "0", "0").forEach { key(it) }
        nextLine()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("calcBtnUndo").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().isEmpty()
        }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("200", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("calcBtnRedo").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /* 8 — v1.6.42: the payment history shows ON THE CANVAS above the paid
       entry's total, notes included — paid through the entry's own
       UNLOCKED pay chip */
    @Test
    fun thePaymentHistoryShowsOnTheCanvasWithNotes() {
        ready()
        openEditor()
        toNumMode()
        listOf("3", "0", "0").forEach { key(it) }
        nextLine()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().isNotEmpty()
        }
        // the pay gate: unlock the entry, open its modal, pay the full 300
        rule.onNodeWithTag("calcLineLock:0").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("calcLinePay:0").performClick()
        rule.waitForIdle()
        val payBtns = rule.onAllNodes(hasText(t("ee.calcPay"), substring = true) and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { payBtns.fetchSemanticsNodes().isNotEmpty() }
        payBtns[payBtns.fetchSemanticsNodes().size - 1].performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("lg.recordPayment"), substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val fields = rule.onAllNodes(hasSetTextAction())
        rule.waitUntil(timeoutMillis = 15000) { fields.fetchSemanticsNodes().isNotEmpty() }
        fields[fields.fetchSemanticsNodes().size - 1].performTextInput("lunch money")
        rule.waitForIdle()
        val saves = rule.onAllNodes(hasText(t("act.save")) and hasClickAction())
        saves[saves.fetchSemanticsNodes().size - 1].performClick()
        rule.waitForIdle()

        // back on the canvas: the history row carries the note and → 0
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLinePayHist:0:0").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "the note rides the on-canvas history",
            rule.onAllNodes(hasText("lunch money", substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "the history row shows the new amount",
            rule.onAllNodes(hasText("0 Br", substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
        // and the history sits ABOVE the entry's total (report #8)
        val hist = rule.onNodeWithTag("calcLinePayHist:0:0").fetchSemanticsNode()
        val total = rule.onNodeWithTag("calcLineTotal:0").fetchSemanticsNode()
        assertTrue(
            "the history list must sit ABOVE the total",
            hist.positionInRoot.y < total.positionInRoot.y,
        )
    }

    /* 9 — RTL: the canvas composes and computes under Arabic */
    @Test
    fun theCanvasSupportsRightToLeft() {
        ready()
        rule.runOnUiThread {
            AppStore.state.settings = AppStore.state.settings.copy(language = "ar")
        }
        rule.waitForIdle()
        openEditor()
        toNumMode()
        listOf("1", "0", "0").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("100 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        // the keypad is up (RTL-mirrored) and the total row exists
        assertTrue(
            "the total row renders in RTL",
            rule.onAllNodesWithTag("calcLedgerTotal").fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
