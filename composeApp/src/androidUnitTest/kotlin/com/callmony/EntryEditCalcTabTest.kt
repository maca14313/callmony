package com.callmony

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasAnyDescendant
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.37 — regression test for the PAPER annotated calculator (the
   user's UX blueprint): the equation is one inline ink-on-paper flow
   (no pills, no label-trigger icons on operators), every number carries
   a single tiny tag badge at its top-left corner whose popover is
   anchored AT the number (text field + Before/After position toggle),
   the keypad's NEXT LINE key commits the active expression into a
   stacked ledger with a running-total bar, and the entry amount is the
   typed base + items + the calculator's whole ledger.

   Pinned end-to-end, in ONE boot (same harness notes as every compose
   harness here: hydrated is deliberately NOT reset in @Before; onboarded
   flips AFTER waitUntil(hydrated) so the boot's empty publish can never
   overwrite it):
     1. the keypad builds the user's own example: 100 × 2 + 300 → the
        inline flow shows the live "= 500 Br" and the folds line
     2. the tag badges open the ANCHORED popover; labels land before
        (ball) / after (transport) their numbers
     3. NEXT LINE commits into the ledger; a second line's total joins
        the running bar (500 + 75 = 575)
     4. Save persists amount 575 AND the multi-line free text on
        exprNote; reopening rebuilds the ledger, and tapping a committed
        line pulls it back down for re-editing (300 → 350 → 625)
     5. THE SUM (the user's exact report): type 100 on Just amount, add
        ball 1 pc × 200 on Items → 100 + 200 = 300; reopen keeps the
        typed base 100 in the field
     6. the others too: calculator 200 + quick item 35 → 235 */
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

    /* tap a number's tiny tag badge → the ANCHORED popover opens (the
       field shows the describe hint), type the text, optionally flip the
       position toggle, Done */
    private fun label(tokenIndex: Int, text: String, after: Boolean = false) {
        rule.onNodeWithTag("calcTag:$tokenIndex").performClick()
        rule.waitForIdle()
        val field = hasSetTextAction() and hasText(t("ee.calcDescribe"))
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(field).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(field)[0].performClick()
        rule.onAllNodes(field)[0].performTextInput(text)
        rule.waitForIdle()
        if (after) {
            // flip the position toggle to AFTER — "200 birr" style labels
            val afterChip = hasText(t("ee.calcPosAfter")) and hasClickAction()
            rule.waitUntil(timeoutMillis = 15000) {
                rule.onAllNodes(afterChip).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onAllNodes(afterChip)[0].performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithTag("calcLabelDone").performClick()
        rule.waitForIdle()
    }

    @Test
    fun paperFlowLabelsLedgerAndTheFullRoundTrip() {
        ready()
        openCalcTab()

        // the user's own example, typed on the KEYPAD: 100 × 2 + 300
        listOf("1", "0", "0", "×", "2", "+", "3", "0", "0").forEach { key(it) }

        // the inline flow's LIVE total and the folds line (show your work)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTotal").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("500 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("100 × 2 = 200 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // the tag badges: ball BEFORE 100, transport AFTER 300 (the
        // user's position-based labeling)
        label(0, "ball")
        label(2, "transport", after = true)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("ball")).fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodes(hasText("transport")).fetchSemanticsNodes().isNotEmpty()
        }

        // NEXT LINE: the expression commits into the stacked ledger and
        // the running-total bar carries it (500)
        rule.onNodeWithTag("calcKey:Next").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLedgerTotal").fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodes(hasText("500 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // a second line under it: 50 + 25 → the bar runs to 575
        listOf("5", "0", "+", "2", "5").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("575 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // Save persists the whole thing: amount 575 + the multi-line note
        rule.onAllNodes(hasText(t("act.save")) and hasClickAction())[0].performClick()
        rule.waitForIdle()
        val savedId = rule.runOnUiThread {
            AppStore.state.entries.firstOrNull { e ->
                e.amount == 575.0 &&
                    e.exprNote == "ball 100 × 2 + 300 transport\n50 + 25"
            }?.id
        }
        checkNotNull(savedId) {
            "paper-calc entry was not persisted — entries now: " +
                rule.runOnUiThread {
                    AppStore.state.entries.joinToString("; ") { "amt=${it.amount} expr=${it.exprNote} items=${it.items.size}" }
                }
        }

        // REOPEN — the ledger rebuilds from the saved note with the total
        // still live (both lines, labels intact — the labels live INSIDE
        // the joined ledger-line text now, so match by substring)
        rule.runOnUiThread { UiStore.open(Overlay.EntryEdit(savedId, "u:me")) }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("ball", substring = true)).fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodes(hasText("transport", substring = true)).fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodes(hasText("575 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // pull the first line back down: tap the committed line → its
        // tokens land in the active row; edit 300 → 350 on the keypad
        rule.onNodeWithTag("calcLine:0").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcNum:2").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("calcNum:2").performClick()
        rule.waitForIdle()
        key("⌫")
        key("⌫")
        key("5")
        key("0")
        // the chain re-totals live (100 × 2 + 350 = 550; with the second
        // line the running bar reads 625) before Save
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("625 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(hasText(t("act.save")) and hasClickAction())[0].performClick()
        rule.waitForIdle()
        val updated = rule.runOnUiThread {
            AppStore.state.entries.firstOrNull { it.id == savedId }
        }
        checkNotNull(updated) { "edited entry vanished" }
        check(updated.amount == 625.0) { "edit did not re-total: ${updated.amount}" }
        check(updated.exprNote == "50 + 25\nball 100 × 2 + 350 transport") {
            "exprNote not round-tripped: ${updated.exprNote}"
        }
    }

    /* THE SUM, the user's exact report: "i click credit, then it shows
       New entry, then i click just amount and type in amount input 100,
       then i click the items, qty * price, ball 1 pc * 200 = 200, so the
       result should be 100 + 200 = 300" */
    @Test
    fun typedAmountPlusItemsSumIntoTheTotal() {
        ready()

        rule.runOnUiThread { UiStore.open(Overlay.EntryEdit(null, "u:me")) }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("ee.tabJustAmount")) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }

        // JUST AMOUNT: type 100 (the hero field shows the plain "0" hint)
        val heroField = hasSetTextAction() and hasText("0")
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(heroField).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(heroField)[0].performClick()
        rule.onAllNodes(heroField)[0].performTextInput("100")
        rule.waitForIdle()

        // ITEMS: ball 1 pc × 200
        val itemsChip = rule.onAllNodes(hasText(t("ee.tabItems")) and hasClickAction())[0]
        itemsChip.performScrollTo()
        rule.waitForIdle()
        itemsChip.performClick()
        rule.waitForIdle()
        val nameIn = hasSetTextAction() and hasText(t("lg.itemNamePh"))
        val unitIn = hasSetTextAction() and hasText(t("lg.unitPh"))
        val priceIn = hasSetTextAction() and hasText("0")
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(nameIn).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(nameIn)[0].performClick()
        rule.onAllNodes(nameIn)[0].performTextInput("ball")
        rule.waitForIdle()
        rule.onAllNodes(unitIn)[0].performClick()
        rule.onAllNodes(unitIn)[0].performTextInput("pc")
        rule.waitForIdle()
        // the price field's hint is "0" — same merge pattern as the hero
        rule.onAllNodes(priceIn)[0].performClick()
        rule.onAllNodes(priceIn)[0].performTextInput("200")
        rule.waitForIdle()
        val addBtn = hasText(t("lg.addItem")) and hasClickAction()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(addBtn).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(addBtn)[0].performClick()
        rule.waitForIdle()

        // 100 + 200 = 300 — the TOTAL row shows the entry's full sum
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("entryTotal").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("entryTotal").assertTextContains("300", substring = true)

        // Save: the entry amount IS the sum
        rule.onAllNodes(hasText(t("act.save")) and hasClickAction())[0].performClick()
        rule.waitForIdle()
        val savedId = rule.runOnUiThread {
            AppStore.state.entries.firstOrNull { e ->
                e.amount == 300.0 && e.items.size == 1 && e.exprNote == null
            }?.id
        }
        checkNotNull(savedId) {
            "sum entry was not persisted — entries now: " +
                rule.runOnUiThread {
                    AppStore.state.entries.joinToString("; ") { "amt=${it.amount} expr=${it.exprNote} items=${it.items.size}" }
                }
        }

        // REOPEN: the total stays 300 and the field keeps the typed base
        rule.runOnUiThread { UiStore.open(Overlay.EntryEdit(savedId, "u:me")) }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("entryTotal").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("entryTotal").assertTextContains("300", substring = true)
        // switch to Just amount: the field carries the DERIVED base 100
        val justTab = rule.onAllNodes(hasText(t("ee.tabJustAmount")) and hasClickAction())[0]
        justTab.performScrollTo()
        rule.waitForIdle()
        justTab.performClick()
        rule.waitForIdle()
        val baseField = hasSetTextAction() and hasText("100")
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(baseField).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /* "it should be like this for the others too": the calculator's
       result + a Quick item ADD into the entry amount (200 + 35 = 235) */
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

        // 200 from the calculator + 35 from the quick item → 235
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
