package com.callmony

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

/* v1.6.38 — regression battery for the CANVAS CALCULATOR (the user's
   rework spec): the entry editor's amount input is the annotated
   calculator as a near-full-screen canvas —

     1. ⌫ DELETES THE OPERATOR: "if i write 200 * 100 and i click the x
        delete btn it deletes the 0,0,1 then it should delete the * but
        skips it and deletes 0,0,2" — the trailing × goes BEFORE the
        digits to its left.
     2. TEXT IS THE DEFAULT at line start (system QWERTY, native
        selection/clipboard); typing the free-text grammar parses LIVE
        into tokens ("ball 200 + 100 cups" → = 300 Br).
     3. The % key: 200 + 10% = 220 (percent of the running total).
     4. NEXT LINE is a small ⏎ key on the pad; the committed line LOCKS
        (no pull-back editing) and ✕ deletes it.
     5. THE SUM across lines: "ball 1 × 200" ⏎ + "100" → 300, Save
        persists amount 300 with both lines; the TOTAL bar's menu offers
        MAKE A PAYMENT of the total.
     6. Just amount / Items · qty × price / Quick item are GONE from the
        strip (Calculator + Recent only) — their removal is documented in
        removed/ at the repo root.

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
        // the canvas is up (the text field — text mode is the DEFAULT)
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

    /* 1 — the ⌫ FIX: after 200 × 100, three ⌫ eat 0,0,1 leaving "200 ×",
       the FOURTH ⌫ deletes the × (not a digit of 200) */
    @Test
    fun backspaceDeletesTheOperatorBeforeTheLeftDigits() {
        ready()
        openEditor()
        toNumMode()
        listOf("2", "0", "0", "×", "1", "0", "0").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcNum:0").fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithTag("calcNum:1").fetchSemanticsNodes().isNotEmpty()
        }
        // 100 → 10 → 1 → (removed)
        key("⌫"); key("⌫"); key("⌫")
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcNum:1").fetchSemanticsNodes().isEmpty()
        }
        // THE FIX: the next ⌫ removes the × — NOT a digit of 200
        key("⌫")
        rule.waitForIdle()
        // the operator chip is gone and the number still reads 200
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcOp:0").fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithTag("calcNum:0").performClick()
        rule.waitForIdle()
        // and the digit editing continues from 200
        key("⌫")
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("20")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /* 2 — TEXT DEFAULT: the fresh line is a text field; the free-text
       grammar parses live with words as labels and a live total */
    @Test
    fun textModeIsTheDefaultAndParsesTheGrammarLive() {
        ready()
        openEditor()

        val field = rule.onNodeWithTag("calcTextField")
        field.performClick()
        field.performTextInput("ball 200 + 100 cups")
        rule.waitForIdle()
        // the live total rides the steps line: "= 300 Br"
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("300 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // switch to 123 — the field's parsed tokens become the flow and
        // the keypad aims at the last number (100, underlined-active)
        toNumMode()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcNum:0").fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithTag("calcNum:1").fetchSemanticsNodes().isNotEmpty()
        }
        // the label text renders around the numbers
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("ball", substring = true)).fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodes(hasText("cups", substring = true)).fetchSemanticsNodes().isNotEmpty()
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
        // the number renders WITH its % marker
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("10%")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /* 4 + 5 — NEXT LINE locks the old line; lines SUM; the total bar can
       MAKE A PAYMENT; Save persists the multi-line note and amount */
    @Test
    fun nextLineLocksLinesSumAndTheTotalCanBecomeAPayment() {
        ready()
        openEditor()
        toNumMode()

        // line 1: ball 1 × 200 (the user's own example) → 200
        listOf("1", "×", "2", "0", "0").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("200 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        key("Next")
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().isNotEmpty()
        }
        // the committed line is LOCKED: tapping it pulls NOTHING down
        rule.onNodeWithTag("calcLine:0").performClick()
        rule.waitForIdle()
        assertTrue(
            "a locked line must not come back down for editing",
            rule.onAllNodesWithTag("calcNum:0").fetchSemanticsNodes().isEmpty(),
        )
        // the fresh line starts as TEXT again (the spec's default)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTextField").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("calcTextField").performTextInput("100")
        rule.waitForIdle()
        // TOTAL: 200 + 100 = 300
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("300 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // the total bar's menu offers MAKE A PAYMENT of the 300
        rule.onNodeWithTag("calcLedgerTotal").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("totalMenuPay").fetchSemanticsNodes().isNotEmpty()
        }
        // dismiss (the payment sheet flow is covered by the ledger tests)
        rule.onNodeWithTag("totalMenuCopy").performClick()
        rule.waitForIdle()

        // Save: amount 300 + BOTH lines on the note
        rule.onAllNodes(hasText(t("act.save")) and hasClickAction())[0].performClick()
        rule.waitForIdle()
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

    /* 6 — the removed tabs are GONE (Calculator + Recent only); the
       seeded legacy amount survives as its first locked line */
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
        // the strip: Calculator + Recent — Just amount / Items / Quick item
        // chips are REMOVED (v1.6.38)
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
