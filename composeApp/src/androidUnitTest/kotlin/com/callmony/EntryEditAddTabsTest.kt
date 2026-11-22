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
   documented with all their features in removed/ at the repo root.
   v1.6.49 — report #7 ("we don't want the page that says edit entry for
   the ledger"): the WHOLE FORM is gone — the canvas IS the editor. The
   Recent tab and the inline item editor went with it, so this battery
   now pins:
     1. the ledger view still renders no quick-item row
     2. the canvas-only editor: no tabs, no collapsed band, no item
        rows — and backing out still persists the calculator's note
        through the leave-time create
     3. an items entry opens straight into the canvas with its items
        intact (data preserved even though the form UI is gone)

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

    /* v1.6.49 — the FORM is gone (report #7): the editor opens straight
       into the canvas; the Recent tab and every item row are gone, and
       backing out persists the calculator's note through the leave-time
       create — the SUM semantics the old test pinned, minus the form */
    @Test
    fun recentItemsAndTheCalculatorSumIntoTheEntryAmount() {
        ready()

        rule.runOnUiThread { UiStore.open(Overlay.EntryEdit(null, "u:me")) }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTextField").fetchSemanticsNodes().isNotEmpty()
        }

        // the calculator line: 100 × 2 → 200 (the live total renders the
        // number alone — the currency is its own tappable chip, v1.6.49)
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
            rule.onAllNodes(hasText("= 200", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // v1.6.49 — NO FORM COMPOSES: no Recent tab, no collapsed band
        assertEquals(
            "the Recent tab must be gone (the form is deleted)",
            0,
            rule.onAllNodes(hasText(t("ee.tabRecent")) and hasClickAction()).fetchSemanticsNodes().size,
        )
        assertEquals(
            "no collapsed band — the canvas is the whole editor",
            0,
            rule.onAllNodesWithTag("calcCollapsed").fetchSemanticsNodes().size,
        )

        // backing out of the editor persists the never-saved draft through
        // the leave-time create — the entry carries the calc note, amount 200.
        // v1.6.49: the KEYPAD is still armed in num mode, so the FIRST back
        // closes it (AppBackHandler's num branch) — the second closes the
        // editor and fires the leave-time save
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcKey:1").fetchSemanticsNodes().isEmpty()
        }
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTextField").fetchSemanticsNodes().isEmpty()
        }
        val saved = rule.runOnUiThread {
            AppStore.state.entries.firstOrNull {
                e -> e.amount == 200.0 &&
                    e.exprNote?.replace(Regex("\\s*@\\d{4}-\\d{2}-\\d{2}"), "") == "100 × 2"
            }
        }
        checkNotNull(saved) {
            "sum entry was not persisted — entries now: " +
                rule.runOnUiThread {
                    AppStore.state.entries.joinToString("; ") { "amt=${it.amount} expr=${it.exprNote} items=${it.items.size}" }
                }
        }
        assertEquals("100 × 2", saved.exprNote?.replace(Regex("\\s*@\\d{4}-\\d{2}-\\d{2}"), ""))
    }

    /* v1.6.49 — an entry WITH items opens straight into the canvas (the
       form — and its inline item editor — is gone, report #7): no item
       row composes, and the items data rides the entry untouched */
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

        // the CANVAS opens directly — no collapse, no form underneath
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcCollapse").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(
            "no collapsed band — the canvas is the whole editor",
            0,
            rule.onAllNodesWithTag("calcCollapsed").fetchSemanticsNodes().size,
        )
        assertEquals(
            "no inline item editor composes anymore",
            0,
            rule.onAllNodes(hasText(t("lg.saveItem")) and hasClickAction()).fetchSemanticsNodes().size,
        )

        // the items entry still computes: 25 item rides the sum total row
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("25", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // back out — the items data is intact (the form UI is gone, not
        // the data)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.runOnUiThread { AppStore.state.entries.find { it.id == id }?.items?.size == 1 }
        }
        val items = rule.runOnUiThread {
            AppStore.state.entries.find { it.id == id }?.items
        }
        assertEquals("Coffee", items?.first()?.name)
    }
}
