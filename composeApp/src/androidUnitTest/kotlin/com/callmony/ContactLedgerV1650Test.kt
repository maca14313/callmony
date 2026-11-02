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
import com.callmony.model.LedgerEntry
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

/* v1.6.50 — regression battery for the contact-detail page round:

     10. "in the contact detail page the cards of the single entries is
         not good ux or ui, think like ui or ux designer, why the content
         in is a mess" → the entry card reads as THREE ZONES: the hero row
         (ring + title + the amount), the calculation in its OWN soft
         container BELOW it, and the status strip with the edit / expand
         controls aligned at the foot.
     11. "in the contact detail page remove the row that have the title
         single entry, + and x in it" → the "Single entries" heading row
         (its ＋ and its collapse chevron) is GONE on a CONTACT's ledger —
         the cards list directly; the GENERAL ledger keeps its heading. */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContactLedgerV1650Test {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val contact = DeviceContact(
        recordID = "c77", displayName = "Card Layout Person", givenName = "Card Layout Person",
        middleName = null, familyName = "", nickname = null, company = null,
        jobTitle = null, note = null, birthday = null,
        phones = listOf(DevicePhone("0911666777", "mobile", "0911666777")),
        emails = listOf(), addresses = listOf(), hasPhoto = false,
    )

    private var seededId: String? = null

    @Before
    fun freshApp() {
        UiStore.closeAll()
        // the RECENTS tab composes behind the contact overlay — keeps the
        // General ledger (and its "Single entries" heading) out of the
        // contact-page assertions
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

    private fun ready() {
        rule.waitUntil(timeoutMillis = 90000) { AppStore.state.hydrated }
        rule.runOnUiThread {
            AppStore.state.settings = AppStore.state.settings.copy(onboarded = true)
        }
        rule.waitForIdle()
    }

    private fun openContactLedger() {
        rule.runOnUiThread {
            AppStore.state.deviceContacts = listOf(contact)
            AppStore.state.contactX = listOf(
                ContactX(recordID = "c77", favorite = false, socials = listOf())
            )
            UiStore.open(Overlay.Contact("c77"))
        }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 30000) {
            rule.onAllNodesWithText("Card Layout Person", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("ledgerFabNewCanvas").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /* 11 — THE "SINGLE ENTRIES" HEADING ROW IS GONE on the contact page:
       no title, no ＋, no collapse chevron — the entries list directly
       under the filter chips, and the circular ＋ FAB remains the add */
    @Test
    fun theSingleEntriesHeadingRowIsGoneOnTheContactPage() {
        ready()
        // seed one entry for the contact so the list (not the empty state)
        // composes
        seededId = rule.runOnUiThread {
            val id = com.callmony.lib.uid()
            AppStore.saveEntry(
                LedgerEntry(
                    id = id, ownerId = "c:c77", type = "credit", kind = "credit",
                    amount = 450.0, date = com.callmony.lib.todayStr(),
                    calcTitle = "Seeded card",
                    exprNote = "rooms 3 × 150",
                    createdAt = com.callmony.lib.now(), updatedAt = com.callmony.lib.now(),
                )
            )
            id
        }
        rule.waitForIdle()
        openContactLedger()

        // the heading row is GONE — the title itself
        assertEquals(
            "the 'Single entries' heading row must be gone on the contact page",
            0,
            rule.onAllNodesWithText(t("lg.ungrouped")).fetchSemanticsNodes().size,
        )
        // …and the entry card still lists (the ring composes, the math
        // preview composes)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("entryCardPct:$seededId", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("entryCardCalc:$seededId", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        // the FAB remains the one add affordance
        assertTrue(
            "the circular ＋ FAB must stay on the contact ledger",
            rule.onAllNodesWithTag("ledgerFabNewCanvas").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /* 11 — THE GENERAL LEDGER KEEPS ITS HEADING (the removal is scoped to
       the contact detail page exactly as the report asks) */
    @Test
    fun theGeneralLedgerKeepsItsSingleEntriesHeading() {
        ready()
        UiStore.tabInternal = "general"
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 30000) {
            rule.onAllNodesWithText(t("lg.ungrouped")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /* 10 — THE CARD READS AS THREE ZONES (report #10): the calculation
       preview sits in its OWN container BELOW the hero row, and the edit /
       expand controls sit on the card's foot strip — not crammed into the
       amount's row */
    @Test
    fun theEntryCardReadsAsThreeZones() {
        ready()
        seededId = rule.runOnUiThread {
            val id = com.callmony.lib.uid()
            AppStore.saveEntry(
                LedgerEntry(
                    id = id, ownerId = "c:c77", type = "credit", kind = "credit",
                    amount = 450.0, date = com.callmony.lib.todayStr(),
                    calcTitle = "Zone card",
                    exprNote = "rooms 3 × 150",
                    createdAt = com.callmony.lib.now(), updatedAt = com.callmony.lib.now(),
                )
            )
            id
        }
        rule.waitForIdle()
        openContactLedger()

        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("entryCardCalc:$seededId", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val calc = rule.onNodeWithTag("entryCardCalc:$seededId", useUnmergedTree = true).fetchSemanticsNode()
        val ring = rule.onNodeWithTag("entryCardPct:$seededId", useUnmergedTree = true).fetchSemanticsNode()
        val hero = ring

        // ZONE 2 sits BELOW the hero row's ring (its own block, not glued
        // inside the title's column)
        assertTrue(
            "the calculation container must sit BELOW the hero row " +
                "(ring y=${ring.positionInRoot.y} h=${ring.size.height}, calc y=${calc.positionInRoot.y})",
            calc.positionInRoot.y > hero.positionInRoot.y + hero.size.height,
        )

        // the card's edit affordance is still there and clickable
        val pencil = rule.onAllNodes(hasText("Zone card") and hasClickAction())
        assertTrue(
            "the card must stay tappable to the editor (one-tap edit)",
            pencil.fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
