package com.callmony

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.38 — the ADD-MODE strip after the removal (user spec: "remove the
   just amount, qty*price and Quick item"): Calculator + Recent are what
   remain. Just amount / Items · qty × price / Quick item are GONE —
   documented with all their features in removed/ at the repo root. This
   battery pins:
     1. the ledger view still renders no quick-item row
     2. the RECENT tab books a one-tap repeat item, and the entry amount
        = the calculator's ledger + those items (the SUM still holds)
     3. an existing item row still opens the inline editor (editability
        survives the tab's removal)

   Harness notes (same as every compose harness here): hydrated is
   deliberately NOT reset in @Before; onboarded flips AFTER
   waitUntil(hydrated) so the boot's empty publish can never overwrite
   it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
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
        rule.waitForIdle()
        val ph = rule.onAllNodes(hasText(t("lg.quickItemLabel"))).fetchSemanticsNodes()
        check(ph.isEmpty()) { "quick item row still rendered on the ledger view" }
    }

    /* the calculator's ledger + a RECENT item SUM into the entry amount
       (the v1.6.37 SUM semantics, kept after the tab removal) */
    @Test
    fun recentItemsAndTheCalculatorSumIntoTheEntryAmount() {
        ready()

        // seed one booked item so the RECENT pool has something to reuse
        rule.runOnUiThread {
            AppStore.saveEntry(
                com.callmony.model.LedgerEntry(
                    id = com.callmony.lib.uid(), ownerId = "u:me", type = "credit", kind = "credit",
                    amount = 35.0,
                    items = listOf(com.callmony.model.EntryItem(id = com.callmony.lib.uid(), name = "Tea", qty = 1.0, price = 35.0)),
                    date = com.callmony.lib.todayStr(),
                    createdAt = com.callmony.lib.now(), updatedAt = com.callmony.lib.now(),
                )
            )
        }
        rule.waitForIdle()

        rule.runOnUiThread { UiStore.open(Overlay.EntryEdit(null, "u:me")) }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTextField").fetchSemanticsNodes().isNotEmpty()
        }

        // the calculator line: 100 × 2 → 200
        rule.onNodeWithTag("calcModeNum").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcKey:1").fetchSemanticsNodes().isNotEmpty()
        }
        listOf("1", "0", "0", "×", "2").forEach {
            rule.onNodeWithTag("calcKey:$it").performClick()
            rule.waitForIdle()
        }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("200 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // v1.6.39 — the canvas is FULL SCREEN over the form now: collapse
        // it to reach the input-mode strip
        rule.onNodeWithTag("calcCollapse").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcCollapsed").fetchSemanticsNodes().isNotEmpty()
        }

        // RECENT: switch to the tab, then one tap books Tea · 35 again
        val recentTab = rule.onAllNodes(hasText(t("ee.tabRecent")) and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { recentTab.fetchSemanticsNodes().isNotEmpty() }
        recentTab[0].performClick()
        rule.waitForIdle()
        val recentChip = rule.onAllNodes(hasText("Tea · 35", substring = true) and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { recentChip.fetchSemanticsNodes().isNotEmpty() }
        recentChip[0].performClick()
        rule.waitForIdle()

        // 200 (calculator) + 35 (item) = 235 on the total bar
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("235 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // Save → the entry carries the item AND the calc note, amount 235
        rule.onAllNodes(hasText(t("act.save")) and hasClickAction())[0].performClick()
        rule.waitForIdle()
        val saved = rule.runOnUiThread {
            AppStore.state.entries.firstOrNull { e -> e.amount == 235.0 && e.items.size == 1 }
        }
        checkNotNull(saved) {
            "sum entry was not persisted — entries now: " +
                rule.runOnUiThread {
                    AppStore.state.entries.joinToString("; ") { "amt=${it.amount} expr=${it.exprNote} items=${it.items.size}" }
                }
        }
        assertEquals("100 × 2", saved.exprNote)
        assertEquals("Tea", saved.items.first().name)
    }

    /* tapping an existing item row opens the inline item editor — the
       qty × price ADD tab is gone but EDITING what exists stays */
    @Test
    fun tappingAnExistingItemOpensTheInlineEditor() {
        ready()

        val id = rule.runOnUiThread {
            val eid = com.callmony.lib.uid()
            AppStore.saveEntry(
                com.callmony.model.LedgerEntry(
                    id = eid, ownerId = "u:me", type = "credit", kind = "credit",
                    amount = 25.0,
                    items = listOf(com.callmony.model.EntryItem(id = com.callmony.lib.uid(), name = "Coffee", qty = 1.0, price = 25.0)),
                    date = com.callmony.lib.todayStr(),
                    createdAt = com.callmony.lib.now(), updatedAt = com.callmony.lib.now(),
                )
            )
            eid
        }
        rule.waitForIdle()
        rule.runOnUiThread { UiStore.open(Overlay.EntryEdit(id, "u:me")) }
        rule.waitForIdle()

        // v1.6.39 — the FULL-SCREEN canvas opens over the whole form:
        // collapse it to reach the item rows
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcCollapse").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("calcCollapse").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcCollapsed").fetchSemanticsNodes().isNotEmpty()
        }

        // tap the Coffee item row → the inline editor's Save item button
        // appears (its name field prefilled)
        val itemRow = rule.onAllNodes(hasText("Coffee", substring = true))
        rule.waitUntil(timeoutMillis = 15000) { itemRow.fetchSemanticsNodes().isNotEmpty() }
        itemRow[0].performClick()
        rule.waitForIdle()
        val saveItemBtn = rule.onAllNodes(hasText(t("lg.saveItem")) and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { saveItemBtn.fetchSemanticsNodes().isNotEmpty() }
        // the prefilled name field is editable (scroll the editor into view
        // first — the canvas owns the top of the form)
        val nameField = rule.onAllNodes(hasSetTextAction() and hasText("Coffee"))
        rule.waitUntil(timeoutMillis = 15000) { nameField.fetchSemanticsNodes().isNotEmpty() }
        nameField[0].performClick()
        rule.waitForIdle()
        nameField[0].performTextReplacement("Coffee X")
        rule.waitForIdle()
        saveItemBtn[0].performScrollTo().performClick()
        rule.waitForIdle()
        // the row itself re-renders with the new name before the entry saves
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("Coffee X", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        // then the ENTRY's Save persists the edited item (the footer is
        // pinned — always visible, never inside the form scroll)
        rule.onAllNodes(hasText(t("act.save")) and hasClickAction())[0].performClick()
        rule.waitForIdle()
        val renamed = rule.runOnUiThread {
            AppStore.state.entries.find { it.id == id }?.items?.firstOrNull()?.name
        }
        assertEquals("Coffee X", renamed)
    }
}
