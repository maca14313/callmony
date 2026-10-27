package com.callmony

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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

/* v1.6.25 — regression test for the v1.6.24 crash: "when i click the more
   btn and the three dots in the navbar in the detail contact page the app
   closes". ROOT CAUSE: the More-sheet content wrapped itself in its own
   verticalScroll Column while BottomSheet ALREADY scrolls its content — a
   scrollable measured inside another scrollable receives INFINITE max
   height and Compose throws IllegalStateException at measure time, killing
   the app the moment either trigger (the More quick-action tile or the
   navbar dots-vertical) opened the sheet.

   Harness notes (the Recents-battery flakiness decoded):
   - Do NOT reset state.hydrated in @Before when the boot hydrate already
     ran — the hydrate LaunchedEffect(Unit) never re-runs, so a post-boot
     reset parks the app on the SPLASH forever. That was the nondeterministic
     ComposeTimeoutException this class hit before.
   - Seed AFTER waitUntil(hydrated) so the boot's empty publish can never
     overwrite the seeds.
   - The boot chain's +4s contacts refresh is inert under Robolectric
     (READ_CONTACTS is a runtime permission and is not granted), so the
     seeded contacts survive. */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoreSheetCrashTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun freshApp() {
        UiStore.closeAll()
        UiStore.tabInternal = "recents"
        UiStore.locked = false
        // NOTE: state.hydrated is deliberately NOT reset — see class docs.
    }

    private val contact: DeviceContact
        get() {
            val name = "More Sheet Person"
            return DeviceContact(
                recordID = "c77", displayName = name, givenName = name,
                middleName = null, familyName = "", nickname = null, company = null,
                jobTitle = null, note = null, birthday = null,
                phones = listOf(
                    DevicePhone("0911222333", "mobile", "0911222333"),
                    DevicePhone("0911444555", "home", "0911444555"),
                ),
                emails = listOf(), addresses = listOf(), hasPhoto = false,
            )
        }

    private fun openDetailWithSocials() {
        // boot hydrate completes during rule launch or shortly after; wait
        // for it so the empty publish lands BEFORE our seeds.
        rule.waitUntil(timeoutMillis = 90000) { AppStore.state.hydrated }
        rule.runOnUiThread {
            AppStore.state.settings = AppStore.state.settings.copy(onboarded = true)
            AppStore.state.deviceContacts = listOf(contact)
            AppStore.state.contactX = listOf(
                ContactX(
                    recordID = "c77", favorite = true,
                    socials = listOf(
                        SocialLink("s1", "telegram", "@moreperson"),
                        SocialLink("s2", "instagram", "@moreperson"),
                    ),
                )
            )
            UiStore.open(Overlay.Contact("c77"))
        }
        rule.waitForIdle()
        // the detail page itself rendered
        rule.waitUntil(timeoutMillis = 30000) {
            rule.onAllNodesWithText("More Sheet Person", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun quickActionMoreOpensSheetWithoutCrash() {
        openDetailWithSocials()
        // the MORE tile at the end of the first action row — [0] because
        // v1.6.26 hosts every overlay INSIDE the content area, which now
        // composes BEFORE the bottom nav (the bottom-nav More tab shares the
        // same glyph and comes after it in the tree).
        rule.onAllNodesWithContentDescription("dots-horizontal-circle-outline")[0].performClick()
        rule.waitForIdle()
        // the sheet content rendered — the Add-to group header
        rule.waitUntil(timeoutMillis = 30000) {
            rule.onAllNodesWithText(t("cd.addTo")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun navbarThreeDotsOpensSheetWithoutCrash() {
        openDetailWithSocials()
        // the navbar dots-vertical in the header
        rule.onAllNodesWithContentDescription("dots-vertical")[0].performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 30000) {
            rule.onAllNodesWithText(t("cd.addTo")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun moreSheetBlockRowRenders() {
        openDetailWithSocials()
        rule.onAllNodesWithContentDescription("dots-vertical")[0].performClick()
        rule.waitForIdle()
        // the Block/Unblock row at the bottom of the sheet
        rule.waitUntil(timeoutMillis = 30000) {
            rule.onAllNodesWithText(t("cd.blockContact")).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
