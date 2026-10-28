package com.callmony

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyDescendant
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.36 — regression test for the ANNOTATED CALCULATOR v2 (the user's
   updated spec): the Calculator tab is a chip-driven interlocking
   "Text + Calculator" row — a [＋ Text] toggle before and after every
   number and around the result, a custom numeric keypad pinned at the
   bottom, live step-by-step intermediate results, and a full round trip
   through the saved exprNote.

   Pinned end-to-end, in ONE boot (same harness notes as every compose
   harness here: hydrated is deliberately NOT reset in @Before; onboarded
   flips AFTER waitUntil(hydrated) so the boot's empty publish can never
   overwrite it):
     1. the keypad builds the user's own example: 100 × 2 + 300 → the
        steps card shows "Step 1: 100 × 2 = 200 Br" and "TOTAL 500 Br"
     2. the [＋ Text] slots open the QWERTY label field (ball / price /
        transport) and the labels land as chips
     3. Save persists amount 500 AND the serialized free text on exprNote
        ("ball 100 × 2 price + 300 transport" — the v1.6.35 grammar)
     4. reopening rebuilds the chips; tapping a number chip and editing it
        on the keypad re-totals live (300 → 350: 550) and the round trip
        works (amount AND exprNote)
     5. the SUM MIRROR: on a new entry the calculator total + line items
        ADD into the entry amount (calc 200 + quick item 35 → 235) */
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

    private fun openCalcTab() {
        rule.runOnUiThread { UiStore.open(Overlay.EntryEdit(null, "u:me")) }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("ee.tabCalc")) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        // the Calculator chip is FIRST in the strip (far left, the user's
        // own spec) — visible without scrolling
        rule.onAllNodes(hasText(t("ee.tabCalc")) and hasClickAction())[0].performClick()
        rule.waitForIdle()
        // the numeric keypad is pinned at the bottom
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcKey:1").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun key(k: String) {
        rule.onNodeWithTag("calcKey:$k").performClick()
        rule.waitForIdle()
    }

    private fun label(slot: String, text: String) {
        rule.onNodeWithTag("calcLbl:$slot").performClick()
        rule.waitForIdle()
        val field = hasSetTextAction() and hasText(t("ee.calcLabelHint"))
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(field).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(field)[0].performClick()
        rule.onAllNodes(field)[0].performTextInput(text)
        rule.waitForIdle()
        rule.onNodeWithTag("calcLabelDone").performClick()
        rule.waitForIdle()
    }

    @Test
    fun chipsKeypadStepsAndTheFullRoundTrip() {
        ready()
        openCalcTab()

        // the user's own example, typed on the KEYPAD: 100 × 2 + 300
        listOf("1", "0", "0", "×", "2", "+", "3", "0", "0").forEach { key(it) }

        // the LIVE STEP-BY-STEP card: Step 1 is the × fold, then the + fold
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("${t("ee.calcStep")} 1: 100 × 2")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("${t("ee.calcStep")} 2: 200 + 300")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("500 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // the [＋ Text] toggles: ball before 100, price after 2, transport
        // after 300 (the user's annotated example)
        label("b0", "ball")
        label("a1", "price")
        label("a2", "transport")
        // the labels render as chips
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("ball")).fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodes(hasText("price")).fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodes(hasText("transport")).fetchSemanticsNodes().isNotEmpty()
        }

        // Save persists the entry: amount follows the total, the chips
        // serialize to the v1.6.35 free-text grammar on exprNote
        rule.onAllNodes(hasText(t("act.save")) and hasClickAction())[0].performClick()
        rule.waitForIdle()
        val savedId = rule.runOnUiThread {
            AppStore.state.entries.firstOrNull { e ->
                e.amount == 500.0 && e.exprNote == "ball 100 × 2 price + 300 transport"
            }?.id
        }
        checkNotNull(savedId) {
            "annotated-calc entry was not persisted — entries now: " +
                rule.runOnUiThread {
                    AppStore.state.entries.joinToString("; ") { "amt=${it.amount} expr=${it.exprNote} items=${it.items.size}" }
                }
        }

        // REOPEN the saved entry — it lands on the Calculator tab with the
        // chips rebuilt from the saved text and the total still live
        rule.runOnUiThread { UiStore.open(Overlay.EntryEdit(savedId, "u:me")) }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("ball")).fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodes(hasText("transport")).fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodes(hasText("500 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // tap the 300 chip and edit it on the keypad: ⌫⌫ → 3, then 50 → 350;
        // the chain re-totals live (100 × 2 + 350 = 550) before Save
        rule.onNodeWithTag("calcNum:2").performClick()
        rule.waitForIdle()
        key("⌫")
        key("⌫")
        key("5")
        key("0")
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("550 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(hasText(t("act.save")) and hasClickAction())[0].performClick()
        rule.waitForIdle()
        val updated = rule.runOnUiThread {
            AppStore.state.entries.firstOrNull { it.id == savedId }
        }
        checkNotNull(updated) { "edited entry vanished" }
        check(updated.amount == 550.0) { "edit did not re-total: ${updated.amount}" }
        check(updated.exprNote == "ball 100 × 2 price + 350 transport") { "exprNote not round-tripped: ${updated.exprNote}" }
    }

    @Test
    fun calculatorPlusItemsSumIntoTheEntryAmount() {
        ready()
        openCalcTab()

        // 100 × 2 = 200 on the calculator
        listOf("1", "0", "0", "×", "2").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("200 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // switch to the QUICK tab and book Tea · 35 (the strip scrolls by
        // design — bring the chip into view first)
        val quickChip = rule.onAllNodes(hasText(t("ee.tabQuick")) and hasClickAction())[0]
        quickChip.performScrollTo()
        rule.waitForIdle()
        quickChip.performClick()
        rule.waitForIdle()
        val labelInput = hasSetTextAction() and hasText(t("lg.quickItemLabel"))
        val priceInput = hasSetTextAction() and hasText(t("lg.quickItemPrice"))
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(labelInput).fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodes(priceInput).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(labelInput)[0].performClick()
        rule.onAllNodes(labelInput)[0].performTextInput("Tea")
        rule.waitForIdle()
        rule.onAllNodes(priceInput)[0].performClick()
        rule.onAllNodes(priceInput)[0].performTextInput("35")
        rule.waitForIdle()
        val quickPlus = hasContentDescription("plus") and hasClickAction() and
            hasParent(hasAnyDescendant(hasSetTextAction()))
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(quickPlus).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(quickPlus)[0].performClick()
        rule.waitForIdle()

        // THE SUM (user report: "why it is not adding the result of the
        // calculator / Items / Quick item and others that are in the same
        // single entries"): 200 from the calculator + 35 from the quick
        // item → the entry amount is 235
        rule.onAllNodes(hasText(t("act.save")) and hasClickAction())[0].performClick()
        rule.waitForIdle()
        val saved = rule.runOnUiThread {
            AppStore.state.entries.firstOrNull { e ->
                e.exprNote == "100 × 2" && e.items.size == 1
            }
        }
        checkNotNull(saved) {
            "sum entry was not persisted — entries now: " +
                rule.runOnUiThread {
                    AppStore.state.entries.joinToString("; ") { "amt=${it.amount} expr=${it.exprNote} items=${it.items.size}" }
                }
        }
        check(saved.amount == 235.0) {
            "calculator total + items did not ADD into the amount: ${saved.amount} (expected 235.0)"
        }
    }
}
