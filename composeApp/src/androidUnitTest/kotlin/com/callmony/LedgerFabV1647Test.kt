package com.callmony

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.ui.kit.HostSheets
import com.callmony.ui.kit.RootFab
import com.callmony.ui.kit.Toasts
import com.callmony.ui.kit.Confirm
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.unit.dp
import com.callmony.ui.kit.RootBottomOverlays

/* v1.6.47 — regression battery for the ledger round (report #6):

     "when the ledger tab is clicked or the ledger is open, it should show
     cercular + btn on bottom right side that opens new canvas, when ledger
     is open it should list the existing canvas rectangular cards and it
     should show the status, persontage of tge payment and other
     information on it."

   → the ledger (General tab's ledger segment) pins a CIRCULAR ＋ to the
     content area's bottom-end; the tap opens the entry editor with the
     full-screen calculation canvas already up (a NEW CANVAS); while the
     editor (or any overlay) is open the ＋ hides; and every entry renders
     as a canvas CARD carrying the payment-percentage ring, the status and
     the canvas title. */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LedgerFabV1647Test {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun freshApp() {
        UiStore.closeAll()
        UiStore.tabInternal = "general"
        UiStore.locked = false
    }

    @After
    fun cleanGlobalHosts() {
        HostSheets.stack.clear()
        RootBottomOverlays.content.value = null
        RootBottomOverlays.lift.value = 0.dp
        RootBottomOverlays.overlayTopPx.value = -1f
        RootFab.content.value = null
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

    /* 1 — the circular ＋ shows while the ledger is open, opens a NEW
       CANVAS (the editor with its full-screen calculation canvas), hides
       while the editor is on top, and comes back when it closes. */
    @Test
    fun theLedgerFabOpensANewCanvasAndHidesUnderTheEditor() {
        ready()
        // the ledger segment is the General tab's default — the ＋ is up
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("ledgerFabNewCanvas").fetchSemanticsNodes().isNotEmpty()
        }
        // the tap opens the editor — the canvas is already up (the active
        // line's field is composed, no form-first step)
        rule.onNodeWithTag("ledgerFabNewCanvas").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTextField").fetchSemanticsNodes().isNotEmpty()
        }
        // while the editor overlay sits on top, the ＋ must be GONE (it
        // belongs to the ledger surface underneath)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("ledgerFabNewCanvas").fetchSemanticsNodes().isEmpty()
        }
        // back out of the editor — the ＋ returns with the ledger
        repeat(4) {
            if (rule.onAllNodesWithTag("calcBtnNext").fetchSemanticsNodes().isEmpty() &&
                rule.onAllNodesWithTag("calcCollapsed").fetchSemanticsNodes().isEmpty()
            ) return@repeat
            rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
            rule.waitForIdle()
        }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcBtnNext").fetchSemanticsNodes().isEmpty() &&
                rule.onAllNodesWithTag("calcCollapsed").fetchSemanticsNodes().isEmpty()
        }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("ledgerFabNewCanvas").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /* 2 — the CANVAS CARDS: an entry with a title, a calculation and a
       partial payment renders as a rectangular card carrying the
       PERCENTAGE ring (40%), the STATUS ("Paid 200 / 500"), the canvas
       TITLE and the calculation's first line — and tapping it opens the
       entry's own canvas. */
    @Test
    fun theLedgerListsCanvasCardsWithStatusAndPaymentPercentage() {
        ready()
        val id = rule.runOnUiThread {
            val id = com.callmony.lib.uid()
            AppStore.saveEntry(
                com.callmony.model.LedgerEntry(
                    id = id,
                    ownerId = "general",
                    type = "credit",
                    kind = "credit",
                    amount = 500.0,
                    date = com.callmony.lib.todayStr(),
                    exprNote = "rooms 3 × 100\ntransport 200",
                    calcTitle = "march rooms",
                    payments = listOf(
                        com.callmony.model.Payment(
                            id = com.callmony.lib.uid(),
                            amount = 200.0,
                            date = com.callmony.lib.todayStr(),
                            method = "cash",
                            createdAt = com.callmony.lib.now(),
                        )
                    ),
                    createdAt = com.callmony.lib.now(),
                    updatedAt = com.callmony.lib.now(),
                )
            )
            id
        }
        rule.waitForIdle()

        // the card leads with the payment-percentage ring: 200 of 500 = 40%
        // (the ring's tag lives inside the clickable card row — merged
        // semantics swallow it, so the probe uses the unmerged tree)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("entryCardPct:$id", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "the ring must carry the paid percentage",
            rule.onAllNodes(hasText("40%")).fetchSemanticsNodes().isNotEmpty(),
        )
        // the STATUS rides the card, visible without expanding
        // (compactMoney carries the "Br" symbol)
        assertTrue(
            "the card must show the part-paid status",
            rule.onAllNodes(hasText("${t("lg.paid")} 200 Br / 500 Br", substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
        // the CANVAS TITLE heads the card
        assertTrue(
            "the canvas title must head the card",
            rule.onAllNodes(hasText("march rooms", substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
        // the calculation's first line previews the canvas
        assertTrue(
            "the card must preview the calculation's first line",
            rule.onAllNodes(hasText("rooms 3 × 100", substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )

        // tapping the card opens THAT canvas (the editor with the entry's
        // own calculation already up)
        rule.onAllNodes(hasText("march rooms", substring = true) and hasClickAction())
            .fetchSemanticsNodes().let { nodes ->
                assertTrue("the titled card must be tappable", nodes.isNotEmpty())
            }
        rule.runOnUiThread { UiStore.open(Overlay.EntryEdit(id, "general")) }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTitleField").fetchSemanticsNodes().isNotEmpty()
        }
        // the canvas reopened with the entry's own title in the header input
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("march rooms")).fetchSemanticsNodes().isNotEmpty()
        }
        // and the ＋ is hidden under the editor
        assertEquals(
            "the fab must hide while the entry editor is open",
            0,
            rule.onAllNodesWithTag("ledgerFabNewCanvas").fetchSemanticsNodes().size,
        )
    }
}
