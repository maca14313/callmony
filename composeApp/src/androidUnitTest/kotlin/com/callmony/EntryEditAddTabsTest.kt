package com.callmony

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasParent
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

/* v1.6.34 — regression test for the ADD-ITEM TABS (user report: "the
   quick item — label + price — should be inside ADD ITEM, as horizontally
   scrolling tabs: Items (qty × price) / Quick item / Just amount / other
   useful types"). The v1.6.27/29 quick row is GONE from the ledger view —
   it lives in the entry editor now:
     1. the ledger renders no quick-item inputs at all anymore
     2. the entry editor carries the tab strip (Just amount default)
     3. the QUICK tab books label + price as a qty-1 item, Save persists
        the entry with that item and the amount follows its total

   Harness notes (same as every compose harness here): hydrated is
   deliberately NOT reset in @Before; onboarded flips AFTER
   waitUntil(hydrated) so the boot's empty publish can never overwrite
   it. */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EntryEditAddTabsTest {

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

    @Test
    fun ledgerViewHasNoQuickRowAnymore() {
        ready()
        // the label input's placeholder is the row's fingerprint — it must
        // not exist anywhere on the ledger screen (the row moved into the
        // entry editor's tab strip)
        rule.waitForIdle()
        val ph = rule.onAllNodes(hasText(t("lg.quickItemLabel"))).fetchSemanticsNodes()
        check(ph.isEmpty()) { "quick item row still rendered on the ledger view" }
    }

    @Test
    fun quickTabBooksAPricedEntry() {
        ready()

        // open the NEW-ENTRY editor directly (the same overlay the ＋ opens)
        rule.runOnUiThread { UiStore.open(Overlay.EntryEdit(null, "u:me")) }
        rule.waitForIdle()

        // the tab strip is there: Just amount is the default active tab
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("ee.tabJustAmount")) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        check(rule.onAllNodes(hasText(t("ee.tabQuick")) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) {
            "quick tab chip missing"
        }
        check(rule.onAllNodes(hasText(t("ee.tabRecent")) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) {
            "recent tab chip missing"
        }
        // the quick inputs are NOT on the default (Just amount) tab
        check(rule.onAllNodes(hasText(t("lg.quickItemLabel"))).fetchSemanticsNodes().isEmpty()) {
            "quick inputs rendered before the quick tab was selected"
        }

        // switch to the QUICK tab — the label + price inputs appear.
        // v1.6.35: the tab strip gained the CALCULATOR chip at the far
        // left, which pushes QUICK past the initial viewport on the
        // harness's narrow window — the strip scrolls BY DESIGN (the
        // user's own spec), so bring the chip into view before clicking.
        val quickChip = rule.onAllNodes(hasText(t("ee.tabQuick")) and hasClickAction())[0]
        quickChip.performScrollTo()
        rule.waitForIdle()
        quickChip.performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("lg.quickItemLabel"))).fetchSemanticsNodes().isNotEmpty()
        }

        // type into the quick branch's inputs. The placeholder MERGES into
        // the editable node's own Text ('[Item label]' / '[Price]'), so the
        // field is matched by hasText on itself — exact, and immune to the
        // ledger screen's own ＋ pills behind the sheet. Each interaction
        // re-resolves at action time, so the matcher stays valid until the
        // text changes.
        val labelInput = hasSetTextAction() and hasText(t("lg.quickItemLabel"))
        val priceInput = hasSetTextAction() and hasText(t("lg.quickItemPrice"))
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(labelInput).fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodes(priceInput).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(labelInput)[0].performClick()
        rule.onAllNodes(labelInput)[0].performTextInput("Coffee")
        rule.waitForIdle()
        rule.onAllNodes(priceInput)[0].performClick()
        rule.onAllNodes(priceInput)[0].performTextInput("25")
        rule.waitForIdle()

        // the ＋ books it: matched as the plus-clickable whose direct parent
        // row holds the quick inputs (an editable descendant — the ledger's
        // ＋ Income pills behind the sheet never carry one)
        val quickPlus = hasContentDescription("plus") and hasClickAction() and
            hasParent(hasAnyDescendant(hasSetTextAction()))
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(quickPlus).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(quickPlus)[0].performClick()
        rule.waitForIdle()
        val rowText = rule.onAllNodes(hasText("Coffee · 1.0 × 25.0 = 25.0")).fetchSemanticsNodes()
        check(rowText.isNotEmpty()) { "quick item did not land in the item list" }

        // Save persists the entry with the qty-1 item; amount follows.
        // v1.6.35 diag: assert the button is actually ENABLED first — a
        // disabled Btn keeps its OnClick semantics but ignores the tap.
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("act.save")) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        val saveCfg = rule.onAllNodes(hasText(t("act.save")) and hasClickAction())
            .fetchSemanticsNodes()[0].config
        val saveDisabled = saveCfg.any {
            it.key == androidx.compose.ui.semantics.SemanticsProperties.Disabled && it.value == true
        }
        check(!saveDisabled) {
            "Save was DISABLED at click — the mirror never landed; toasts: " +
                com.callmony.ui.kit.Toasts.items.joinToString { it.msg }
        }
        rule.onAllNodes(hasText(t("act.save")) and hasClickAction())[0].performClick()
        rule.waitForIdle()
        val persisted = rule.runOnUiThread {
            AppStore.state.entries.any { e ->
                e.items.size == 1 && e.items[0].name == "Coffee" &&
                    e.items[0].qty == 1.0 && e.items[0].price == 25.0 && e.amount == 25.0
            }
        }
        check(persisted) {
            "quick-item entry was not persisted — entries now: " +
                rule.runOnUiThread {
                    AppStore.state.entries.joinToString("; ") { "amt=${it.amount} items=${it.items.map { i -> i.name + ':' + i.price }}" }
                }
        }
    }

    /* v1.6.37 — THE SUM (user report: "i click just amount and type 100,
       then the items… so the result should be 100 + 200 = 300… it should
       be like this for the others too") — the v1.6.35 "amount is its own"
       rule is superseded: the field holds the user's BASE and every item
       ADDS onto it for the saved entry amount:
         1. a quick item alone still saves as its own total (25)
         2. a typed base (30) + Coffee 25 + Tea 10 → the entry saves 65
            (30 + 35), and the TOTAL row shows the live sum before Save */
    @Test
    fun typedBasePlusItemsSumIntoTheEntryAmount() {
        ready()

        rule.runOnUiThread { UiStore.open(Overlay.EntryEdit(null, "u:me")) }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("ee.tabJustAmount")) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }

        // QUICK tab: book Coffee 25 (chips scroll horizontally by design)
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
        rule.onAllNodes(labelInput)[0].performTextInput("Coffee")
        rule.waitForIdle()
        rule.onAllNodes(priceInput)[0].performClick()
        rule.onAllNodes(priceInput)[0].performTextInput("25")
        rule.waitForIdle()
        val quickPlus = hasContentDescription("plus") and hasClickAction() and
            hasParent(hasAnyDescendant(hasSetTextAction()))
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(quickPlus).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(quickPlus)[0].performClick()
        rule.waitForIdle()

        // JUST AMOUNT tab: the field stays EMPTY (the base is the user's;
        // items no longer mirror into it — they ADD onto it instead)
        rule.onAllNodes(hasText(t("ee.tabJustAmount")) and hasClickAction())[0].performClick()
        rule.waitForIdle()
        val heroField = hasSetTextAction() and hasText("0")
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(heroField).fetchSemanticsNodes().isNotEmpty()
        }
        // the user types their BASE
        rule.onAllNodes(heroField)[0].performClick()
        rule.onAllNodes(heroField)[0].performTextInput("30")
        rule.waitForIdle()

        // QUICK tab again: book Tea 10 — it ADDS onto the base for the total
        val quickChip2 = rule.onAllNodes(hasText(t("ee.tabQuick")) and hasClickAction())[0]
        quickChip2.performScrollTo()
        rule.waitForIdle()
        quickChip2.performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(labelInput).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(labelInput)[0].performClick()
        rule.onAllNodes(labelInput)[0].performTextInput("Tea")
        rule.waitForIdle()
        rule.onAllNodes(priceInput)[0].performClick()
        rule.onAllNodes(priceInput)[0].performTextInput("10")
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(quickPlus).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(quickPlus)[0].performClick()
        rule.waitForIdle()

        // Save: base 30 + items 35 = 65 — the parts ADD (v1.6.37 sum model)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("entryTotal").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("entryTotal").assertTextContains("65", substring = true)
        rule.onAllNodes(hasText(t("act.save")) and hasClickAction())[0].performClick()
        rule.waitForIdle()
        val persisted = rule.runOnUiThread {
            AppStore.state.entries.any { e ->
                e.items.size == 2 && e.amount == 65.0 &&
                    e.items.sumOf { it.qty * it.price } == 35.0
            }
        }
        check(persisted) { "typed base + items did not ADD — amount must be the sum (65)" }
    }
}
