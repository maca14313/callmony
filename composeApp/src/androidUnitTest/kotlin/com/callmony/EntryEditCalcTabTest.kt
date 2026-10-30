package com.callmony

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
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

/* v1.6.39 — regression battery for the FULL-SCREEN CANVAS calculator
   (the user's rework):

     1. ⌫ DELETES THROUGH THE OPERATORS: after 200 × 100 the ⌫ key eats
        0,0,1 and then the × itself — never a digit of 200 (everything
        is text now, so the operator is just a character).
     2. THE "ball" BUG IS DEAD: text typed on the QWERTY survives the
        Aa → 123 switch — "ball" + the 2 0 0 keys becomes "ball 200"
        and the label sticks ("i type ball then click the number btn,
        the ball text disappeared why").
     3. The % key: 200 + 10% = 220 (percent of the running total).
     4. NEXT LINE (the small canvas button) locks the old line; lines
        SUM; the total bar offers MAKE A PAYMENT; Save persists the
        multi-line note and amount.
     5. LONG-PRESS A LOCKED LINE UNLOCKS IT for editing.
     6. Each line's OWN total long-presses into the SAME menu the last
        total has (make a payment of it / copy it).
     7. The native BACK button closes the keypad first, then collapses
        the canvas, then closes the editor.
     8. The canvas is full screen over the form; the collapsed band
        raises it again.
     9. Legacy amounts reopen as the first locked line (v1.6.38 kept).

   Harness notes (same as every compose harness here): hydrated is
   deliberately NOT reset in @Before; onboarded flips AFTER
   waitUntil(hydrated) so the boot's empty publish can never overwrite
   it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EntryEditCalcTabTest {

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
        // the FULL-SCREEN canvas is up (text mode is the DEFAULT)
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
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun saveFromCanvas() {
        rule.onNodeWithTag("calcBtnSave").performClick()
        rule.waitForIdle()
    }

    /* 1 — the ⌫ FIX: after 200 × 100, three ⌫ eat 0,0,1 and the FOURTH
       removes the × (with its spaces) — the locked line that commits
       afterwards is just "200", never "200 × 1…" */
    @Test
    fun backspaceDeletesTheOperatorBeforeTheLeftDigits() {
        ready()
        openEditor()
        toNumMode()
        listOf("2", "0", "0", "×", "1", "0", "0").forEach { key(it) }
        key("⌫"); key("⌫"); key("⌫")   // 0, 0, 1
        key("⌫")                        // the × itself
        // the line is just 200 now — the operator is GONE
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("200 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        nextLine()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("200")).fetchSemanticsNodes().isNotEmpty()
        }
        saveFromCanvas()
        val note = rule.runOnUiThread {
            AppStore.state.entries.lastOrNull { it.amount == 200.0 }?.exprNote
        }
        assertEquals("the × must be gone — the line commits as just 200", "200", note)
    }

    /* 2 — THE "ball" BUG: text typed on the QWERTY survives the Aa → 123
       switch and binds as the 200's label */
    @Test
    fun typedTextSurvivesTheModeSwitch() {
        ready()
        openEditor()

        val field = rule.onNodeWithTag("calcTextField")
        field.performClick()
        field.performTextInput("ball")
        rule.waitForIdle()

        // switch to 123 — "ball" MUST still be there (the v1.6.38 bug
        // wiped it: the field was re-serialized from the parsed tokens)
        toNumMode()
        listOf("2", "0", "0").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("200 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        nextLine()
        // the locked line carries the label AND the number
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("ball 200")).fetchSemanticsNodes().isNotEmpty()
        }
        saveFromCanvas()
        val savedId = rule.runOnUiThread {
            AppStore.state.entries.firstOrNull { it.amount == 200.0 && it.exprNote == "ball 200" }?.id
        }
        checkNotNull(savedId) {
            "the label did not survive the mode switch — entries now: " +
                rule.runOnUiThread {
                    AppStore.state.entries.joinToString("; ") { "amt=${it.amount} expr=${it.exprNote}" }
                }
        }
    }

    /* 3 — the % key: 200 + 10% = 220 (percent of the running total) */
    @Test
    fun percentKeyComputesPercentOfTheRunningTotal() {
        ready()
        openEditor()
        toNumMode()
        listOf("2", "0", "0", "+", "1", "0", "%").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("220 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        // the line carries its % marker into the locked ledger
        nextLine()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("200 + 10%")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /* 4 — NEXT LINE locks lines; lines SUM; the total bar can MAKE A
       PAYMENT; Save persists the multi-line note and amount */
    @Test
    fun nextLineLocksLinesSumAndTheTotalCanBecomeAPayment() {
        ready()
        openEditor()
        toNumMode()

        // line 1: 1 × 200 (the user's own example) → 200
        listOf("1", "×", "2", "0", "0").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("200 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        nextLine()
        // the committed line is LOCKED: tapping it pulls NOTHING down
        rule.onNodeWithTag("calcLine:0").performClick()
        rule.waitForIdle()
        // the fresh line keeps the armed input method (num): 100 → 300
        listOf("1", "0", "0").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("300 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // the total's PAYMENTS MODAL opens on tap (v1.6.40 — was a
        // dropdown): the original, the payment history, the actions
        rule.onNodeWithTag("calcLedgerTotal").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("ee.calcOriginal"), substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        // the modal's Make payment opens the payment sheet (the entry
        // is saved first — the editor stays open beneath it); dismiss
        // via the modal's own close (back) to end the test clean
        back()
        rule.waitForIdle()

        // Save: amount 300 + BOTH lines on the note
        saveFromCanvas()
        val savedId = rule.runOnUiThread {
            AppStore.state.entries.firstOrNull { e ->
                e.amount == 300.0 && e.exprNote == "1 × 200\n100"
            }?.id
        }
        checkNotNull(savedId) {
            "canvas entry was not persisted — entries now: " +
                rule.runOnUiThread {
                    AppStore.state.entries.joinToString("; ") { "amt=${it.amount} expr=${it.exprNote} items=${it.items.size}" }
                }
        }
    }

    /* 5 — LONG-PRESS a locked line and it UNLOCKS for editing: the line
       comes back down into the active field */
    @Test
    fun longPressUnlocksALockedLineForEditing() {
        ready()
        openEditor()
        toNumMode()
        listOf("1", "×", "2", "0", "0").forEach { key(it) }
        nextLine()

        // long-press the LOCKED line → it comes back down (text mode)
        rule.onNodeWithTag("calcLine:0").performTouchInput { longClick() }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTextField").fetchSemanticsNodes().isNotEmpty()
        }
        // the active field carries the line's text — keep editing it.
        // v1.6.40: the + 5 rides the KEYPAD (numbers typed on the QWERTY
        // are annotations now — that is the v1.6.40 semantics, pinned by
        // the text-mode battery below)
        toNumMode()
        listOf("+", "5").forEach { key(it) }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("205 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        // re-commit: the edited line replaces the old one
        nextLine()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("1 × 200 + 5")).fetchSemanticsNodes().isNotEmpty()
        }
        // and only ONE line exists (the old copy is gone)
        assertEquals(1, rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().size)
        assertTrue(
            "the unlocked line's old copy must be gone",
            rule.onAllNodesWithTag("calcLine:1").fetchSemanticsNodes().isEmpty(),
        )
    }

    /* 6 — each line's OWN total opens the SAME payments modal the last
       total has (v1.6.40: the modal with the payment list and the
       make-payment / copy actions) */
    @Test
    fun individualLineTotalsHaveTheLastTotalsMenu() {
        ready()
        openEditor()
        toNumMode()
        listOf("2", "0", "0").forEach { key(it) }
        nextLine()

        // tap the LINE'S OWN total → the shared payments modal is up
        // (the line's own original 200 is the headline)
        rule.onNodeWithTag("calcLineTotal:0").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("${t("ee.calcLineTotal")} · 200 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "the modal shows the original amount row",
            rule.onAllNodes(hasText("${t("ee.calcOriginal")}: 200 Br", substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
        // close it (a payment would open the payment sheet)
        back()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("${t("ee.calcOriginal")}: 200 Br", substring = true)).fetchSemanticsNodes().isEmpty()
        }
    }

    /* 7 — the native BACK button closes the keypad first, then collapses
       the canvas, then closes the editor itself */
    @Test
    fun backClosesTheKeypadThenTheCanvasThenTheEditor() {
        ready()
        openEditor()
        toNumMode()
        // the keypad is up
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcKey:1").fetchSemanticsNodes().isNotEmpty()
        }
        // back #1 — the KEYPAD closes; the canvas stays
        back()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcKey:1").fetchSemanticsNodes().isEmpty()
        }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcCollapse").fetchSemanticsNodes().isNotEmpty()
        }
        // back #2 — the canvas COLLAPSES to the form
        back()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcCollapsed").fetchSemanticsNodes().isNotEmpty()
        }
        // back #3 — the editor itself closes
        back()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcCollapsed").fetchSemanticsNodes().isEmpty() &&
                rule.onAllNodesWithTag("calcTextField").fetchSemanticsNodes().isEmpty()
        }
    }

    /* 8 — the canvas is FULL SCREEN over the form while it is up (the
       form's collapsed band does not exist); collapsing shows the band,
       and tapping the band raises the canvas again */
    @Test
    fun theCanvasIsFullScreenAndRaisesAgain() {
        ready()
        openEditor()
        // full screen: the canvas's own chrome is up, no collapsed band
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcCollapse").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, rule.onAllNodesWithTag("calcCollapsed").fetchSemanticsNodes().size)
        // collapse → the form (the band) shows
        rule.onNodeWithTag("calcCollapse").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcCollapsed").fetchSemanticsNodes().isNotEmpty()
        }
        // raise it again
        rule.onNodeWithTag("calcCollapsed").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTextField").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /* 9 — the removed tabs are GONE (Calculator + Recent only); a legacy
       amount reopens as its first locked line */
    @Test
    fun removedTabsAreGoneAndLegacyAmountsReopenAsALine() {
        ready()

        // a legacy entry: amount, no exprNote, no items
        val legacyId = rule.runOnUiThread {
            val id = com.callmony.lib.uid()
            AppStore.saveEntry(
                com.callmony.model.LedgerEntry(
                    id = id, ownerId = "u:me", type = "credit", kind = "credit",
                    amount = 500.0, date = com.callmony.lib.todayStr(),
                    createdAt = com.callmony.lib.now(), updatedAt = com.callmony.lib.now(),
                )
            )
            id
        }
        rule.waitForIdle()

        openEditor(legacyId)
        // the strip composes beneath the full-screen canvas: Calculator +
        // Recent — Just amount / Items / Quick item chips are REMOVED
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("ee.tabRecent")) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(
            "Just amount chip must be gone",
            0,
            rule.onAllNodes(hasText(t("ee.tabJustAmount")) and hasClickAction()).fetchSemanticsNodes().size,
        )
        assertEquals(
            "Items chip must be gone",
            0,
            rule.onAllNodes(hasText(t("ee.tabItems")) and hasClickAction()).fetchSemanticsNodes().size,
        )
        assertEquals(
            "Quick item chip must be gone",
            0,
            rule.onAllNodes(hasText(t("ee.tabQuick")) and hasClickAction()).fetchSemanticsNodes().size,
        )

        // the legacy 500 reopens as its first locked line → total 500
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("500 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
