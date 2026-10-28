package com.callmony

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.model.ContactX
import com.callmony.model.DeviceContact
import com.callmony.model.DevicePhone
import com.callmony.model.SocialLink
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.36 — regression test for the CONTACT DETAIL navbar number (user
   report: "when the phone number under the contact name is clicked it
   should show the options of the phone numbers … just like the call btn
   icon bellow, and don't remove it old features") and the balance row
   (user report: "remove the settled up and move the where the amount
   shows to the middle horizontaly").

   Pinned:
     1. tapping the NUMBER TEXT under the contact name opens the SAME
        choose-a-number sheet the Call button opens (both numbers listed,
        with their copy/star/call rows) — even though one tap used to
        dial directly
     2. the old features are still THERE: the little call icon beside
        the number keeps its own dial action (still composed beside the
        number), long-press copy stays wired (the LongPressText itself)
     3. the "Settled up" label is GONE from the balance row (a zero
        balance shows just the centered amount pill, no text label) */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContactDetailV1636Test {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun freshApp() {
        UiStore.closeAll()
        UiStore.tabInternal = "recents"
        UiStore.locked = false
        // state.hydrated is NOT reset (see the Recents battery notes) — but
        // the Recents list BEHIND the overlay must be empty: earlier
        // classes in the same JVM seed calls for the SAME numbers
        // (CallRowSheetStripTest uses 0911222333 too), and a behind-the-
        // overlay call card carries the SAME pretty-printed number text as
        // the navbar subtitle — [0] would tap the covered card, not the
        // subtitle.
        AppStore.state.systemCalls = emptyList()
        AppStore.state.deviceContacts = emptyList()
    }

    private val contact = DeviceContact(
        recordID = "c88", displayName = "Number Tap Person", givenName = "Number Tap Person",
        middleName = null, familyName = "", nickname = null, company = null,
        jobTitle = null, note = null, birthday = null,
        phones = listOf(
            DevicePhone("0911222333", "mobile", "0911222333"),
            DevicePhone("0911444555", "home", "0911444555"),
        ),
        emails = listOf(), addresses = listOf(), hasPhoto = false,
    )

    private fun openDetail() {
        rule.waitUntil(timeoutMillis = 90000) { AppStore.state.hydrated }
        rule.runOnUiThread {
            AppStore.state.settings = AppStore.state.settings.copy(onboarded = true)
            AppStore.state.deviceContacts = listOf(contact)
            AppStore.state.contactX = listOf(
                ContactX(recordID = "c88", favorite = true, socials = listOf(SocialLink("s1", "telegram", "@numbertap")))
            )
            UiStore.open(Overlay.Contact("c88"))
        }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 30000) {
            rule.onAllNodesWithText("Number Tap Person", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun tappingTheNavbarNumberOpensThePhoneOptions() {
        openDetail()

        // the "Settled up" label is GONE (zero balance for this contact —
        // the row shows the centered amount pill only)
        check(rule.onAllNodesWithText(t("cd.settledUp")).fetchSemanticsNodes().isEmpty()) {
            "the settled-up label still renders in the balance row"
        }

        // the number under the contact name (the navbar subtitle) — tap it
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithText("+251 911 222 333").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("+251 911 222 333")[0].performClick()
        rule.waitForIdle()

        // the SAME choose-a-number sheet the Call button opens — both of
        // the contact's numbers are listed
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithText(t("cd.chooseNumber")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithText("+251 911 222 333").fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithText("+251 911 444 555").fetchSemanticsNodes().isNotEmpty()
        }
        // the picker's add/edit-numbers row is there too (full parity with
        // the call button's sheet)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithText(t("cd.addOrEditNumbers"), substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun theCallIconBesideTheNumberIsStillItsOwnButton() {
        openDetail()
        // the little brand pill with the phone glyph still composes beside
        // the number (its own tap = dial, kept from v1.6.18)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasContentDescription("phone") and hasClickAction())
                .fetchSemanticsNodes().isNotEmpty()
        }
        // and the navbar number text itself is present (the LongPressText
        // that opens the options on tap + copies on long-press)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithText("+251 911 222 333").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
