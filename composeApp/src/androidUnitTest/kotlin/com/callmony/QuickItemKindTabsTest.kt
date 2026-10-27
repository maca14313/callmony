package com.callmony

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.UiStore
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.29 — regression test for the QUICK-ITEM RELOCATION (user report:
   "the Ledger quick item — a label + price — should be in the credit,
   debit, expenses and income, not in the outer ledger"). The
   [Item label][Price][＋] row must render ONLY while a KIND filter tab is
   active (Credit/Debit on a contact ledger, Income/Expense on the general
   ledger) — the outer/neutral view (All / Unpaid / Overdue) stays clean.

   Why tapping the kind chip needs care: on the general ledger THREE nodes
   read "Income" (the StatCard label, the ＋ Income ActionPill and the
   filter chip). The StatCard is not clickable and the ActionPill renders
   a LEADING SPACE (" Income"), so an exact hasText("Income") query with
   hasClickAction matches EXACTLY ONE node — the filter chip. Asserted,
   not assumed.

   Harness notes (same as MoreSheetCrashTest): hydrated is deliberately
   NOT reset in @Before; onboarded flips AFTER waitUntil(hydrated) so the
   boot's empty publish can never overwrite it. */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuickItemKindTabsTest {

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

    private fun quickLabelNodes() = rule.onAllNodesWithText(t("lg.quickItemLabel")).fetchSemanticsNodes()

    /* Real swipe gestures don't move scrollables under Robolectric NATIVE
       graphics (verified against a bare LazyColumn — see RecentsScrollTest),
       and performScrollTo doesn't reliably cross the NESTED horizontal
       ScrollRow around the chip strip, so the ledger screen's vertical
       scroll semantics action stands in for the gesture. Selecting THAT
       scrollable among the several horizontal chip ScrollRows: it is the
       only scrollable whose subtree contains the "Single entries" header. */
    private fun ledgerScrollMatcher() = hasScrollAction() and hasAnyDescendant(hasText(t("lg.ungrouped")))

    private fun scrollLedgerTo(chipText: String) {
        val verticals = rule.onAllNodes(ledgerScrollMatcher()).fetchSemanticsNodes()
        check(verticals.size == 1) { "expected 1 ledger screen scrollable, got ${verticals.size}" }
        rule.onNode(ledgerScrollMatcher()).performScrollToNode(hasText(chipText) and hasClickAction())
        rule.waitForIdle()
    }

    @Test
    fun quickItemOnlyLivesInsideTheKindTabs() {
        ready()

        // OUTER LEDGER (All filter): the quick item must NOT be there.
        check(quickLabelNodes().isEmpty()) { "quick item rendered on the OUTER ledger (filter=all)" }

        // Enter the Income kind tab (the general ledger's credit filter).
        // Exact-match query: the ＋ Income ActionPill renders " Income"
        // (leading space) so only the filter chip matches. The chip can sit
        // below the fold on the small Robolectric screen — scroll the
        // ledger's vertical scroll to it first, then tap.
        val incomeClickable = rule.onAllNodes(hasText(t("lg.income")) and hasClickAction())
            .fetchSemanticsNodes()
        check(incomeClickable.size == 1) { "expected exactly 1 clickable 'Income' node, got ${incomeClickable.size}" }
        scrollLedgerTo(t("lg.income"))
        rule.onAllNodes(hasText(t("lg.income")) and hasClickAction())[0].performClick()
        rule.waitForIdle()

        // The [Item label][Price][＋] quick row IS rendered inside the tab.
        rule.waitUntil(timeoutMillis = 10000) { quickLabelNodes().isNotEmpty() }
        check(rule.onAllNodesWithText(t("lg.quickItemPrice")).fetchSemanticsNodes().isNotEmpty()) {
            "quick item price input missing on the kind tab"
        }

        // Back to All → the row disappears again; the outer ledger stays clean.
        val allChip = rule.onAllNodes(hasText(t("lg.filterAll")) and hasClickAction()).fetchSemanticsNodes()
        check(allChip.size == 1) { "expected exactly 1 clickable 'All' node, got ${allChip.size}" }
        scrollLedgerTo(t("lg.filterAll"))
        rule.onAllNodes(hasText(t("lg.filterAll")) and hasClickAction())[0].performClick()
        rule.waitForIdle()
        check(quickLabelNodes().isEmpty()) { "quick item still rendered after returning to the outer ledger" }
    }
}
