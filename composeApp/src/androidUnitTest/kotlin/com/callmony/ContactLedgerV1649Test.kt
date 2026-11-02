package com.callmony

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.model.ContactX
import com.callmony.model.DeviceContact
import com.callmony.model.DevicePhone
import com.callmony.ui.kit.HostSheets
import com.callmony.ui.kit.RootBottomOverlays
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

/* v1.6.49 — regression battery for the contact-detail ledger round
   (report #8):

     "in the contact detail page make the tabs + credit, + debit, to show
     up when the pluss icon for the ledger is clicked, dont want thos
     + credit, + debit up there, it should show up when the plus icon is
     clicked and dont need the other tabs bised them in the contact
     detail page."

   → on a CONTACT's ledger the +Credit/+Debit pills (and every other
     action pill beside them) are GONE from the page's top; the ledger
     FAB's tap — and the header row's own ＋ — opens a compact chooser
     (＋ Credit / ＋ Debit) instead, and each choice opens the canvas
     editor with that kind preset. The General ledger keeps its direct
     new-canvas behavior. */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContactLedgerV1649Test {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val contact = DeviceContact(
        recordID = "c99", displayName = "Kind Chooser Person", givenName = "Kind Chooser Person",
        middleName = null, familyName = "", nickname = null, company = null,
        jobTitle = null, note = null, birthday = null,
        phones = listOf(DevicePhone("0911777888", "mobile", "0911777888")),
        emails = listOf(), addresses = listOf(), hasPhoto = false,
    )

    @Before
    fun freshApp() {
        UiStore.closeAll()
        // the RECENTS tab composes behind the contact overlay — the General
        // tab's own ledger (with its ＋ Income/Expense/Credit pills) would
        // still be in the semantics tree behind the overlay and pollute the
        // "no pills on the contact page" assertions
        UiStore.tabInternal = "recents"
        UiStore.locked = false
        AppStore.state.deviceContacts = emptyList()
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
        AppStore.state.deviceContacts = emptyList()
        AppStore.state.settings = AppStore.state.settings.copy(language = "en")
    }

    private fun openContactLedger() {
        rule.waitUntil(timeoutMillis = 90000) { AppStore.state.hydrated }
        rule.runOnUiThread {
            AppStore.state.settings = AppStore.state.settings.copy(onboarded = true)
            AppStore.state.deviceContacts = listOf(contact)
            AppStore.state.contactX = listOf(
                ContactX(recordID = "c99", favorite = false, socials = listOf())
            )
            UiStore.open(Overlay.Contact("c99"))
        }
        rule.waitForIdle()
        // the contact's ledger segment composes — the FAB is up
        rule.waitUntil(timeoutMillis = 30000) {
            rule.onAllNodesWithText("Kind Chooser Person", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("ledgerFabNewCanvas").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /* 1 — the contact page's top action stack is GONE: no +Credit/+Debit
       pills, none of the other tabs beside them */
    @Test
    fun theContactLedgerLostItsTopAddPills() {
        openContactLedger()
        rule.waitForIdle()

        // the ＋ Credit / ＋ Debit pills are GONE from the page's top — they
        // live behind the plus icon's tap now. (The plain "Credit"/"Debit"
        // texts collide with the list's own FILTER chips — lg.credit and
        // lg.kindCredit read the same — so the pill check rides the ＋-
        // prefixed chooser text, plus the unique pill labels.)
        assertEquals(
            "no ＋ Credit chooser row up before the plus icon is tapped",
            0,
            rule.onAllNodes(hasText("＋ ${t("lg.kindCredit")}") and hasClickAction()).fetchSemanticsNodes().size,
        )
        assertEquals(
            "no ＋ Debit chooser row up before the plus icon is tapped",
            0,
            rule.onAllNodes(hasText("＋ ${t("lg.kindDebit")}") and hasClickAction()).fetchSemanticsNodes().size,
        )
        // the OTHER tabs beside them are gone too — the whole stack renders
        // on the General ledger only
        assertEquals(
            "no ＋ Income pill on the contact page",
            0,
            rule.onAllNodes(hasText(t("lg.kindIncome")) and hasClickAction()).fetchSemanticsNodes().size,
        )
        assertEquals(
            "no New group pill on the contact page",
            0,
            rule.onAllNodes(hasText(t("lg.newGroup")) and hasClickAction()).fetchSemanticsNodes().size,
        )
        assertEquals(
            "no Share statement pill on the contact page",
            0,
            rule.onAllNodes(hasText(t("lg.shareStatement")) and hasClickAction()).fetchSemanticsNodes().size,
        )
    }

    /* 2 — the FAB's tap asks the KIND first: the chooser offers ＋ Credit
       and ＋ Debit, and each opens the canvas with that kind preset */
    @Test
    fun theFabAsksTheKindAndEachChoiceOpensTheCanvas() {
        openContactLedger()

        // the FAB's tap opens the CHOOSER (not the canvas directly)
        rule.onNodeWithTag("ledgerFabNewCanvas").performClick()
        rule.waitForIdle()
        val credit = rule.onAllNodes(hasText("＋ ${t("lg.kindCredit")}") and hasClickAction())
        val debit = rule.onAllNodes(hasText("＋ ${t("lg.kindDebit")}") and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { credit.fetchSemanticsNodes().isNotEmpty() }
        assertTrue(
            "the chooser must offer ＋ Debit beside the ＋ Credit",
            debit.fetchSemanticsNodes().isNotEmpty(),
        )
        // while the chooser is up, the canvas is NOT
        assertEquals(
            "the chooser must come before the canvas",
            0,
            rule.onAllNodesWithTag("calcTextField").fetchSemanticsNodes().size,
        )

        // ＋ Debit opens the canvas with the kind preset — a committed line
        // saves the entry as the contact's own DEBIT
        debit[0].performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTextField").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("calcModeNum").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcKey:5").fetchSemanticsNodes().isNotEmpty()
        }
        listOf("5", "0").forEach { k ->
            rule.onNodeWithTag("calcKey:$k").performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithTag("calcBtnNext").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().isNotEmpty()
        }
        // the entry lands as the contact's own DEBIT (the ownerId rides the
        // chooser's call explicitly — v1.6.49 round two)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.runOnUiThread {
                AppStore.state.entries.any { it.ownerId == "c:c99" && it.amount == 50.0 && it.kind == "debit" }
            }
        }
    }
}
