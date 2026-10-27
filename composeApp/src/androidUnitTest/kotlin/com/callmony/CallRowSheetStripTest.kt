package com.callmony

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callmony.lib.AppStore
import com.callmony.lib.prettyPhone
import com.callmony.lib.UiStore
import com.callmony.model.DeviceContact
import com.callmony.model.DevicePhone
import com.callmony.platform.DeviceCall
import com.callmony.platform.PermState
import com.callmony.ui.kit.Toasts
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.28 — regression test for the numbers strip (user report: "in
   LONG-PRESS the call card it should also show the phone numbers options
   in one row at the top that scrolls to side with icons that are in other
   place and to copy even by long press"). Long-pressing a SAVED-CONTACT
   call card opens the per-row action sheet; the new strip — a horizontal
   ScrollRow of NumberTiles rendered INSIDE the BottomSheet's vertical
   scroll — must render without the measure-time crash (different scroll
   axes; the v1.6.25 More-sheet lesson was a VERTICAL scrollable nested in
   a vertical one) and BOTH distinct numbers must be present as tiles.

   Harness notes (same as MoreSheetCrashTest): hydrated is deliberately NOT
   reset in @Before; seeds land AFTER waitUntil(hydrated) so the boot's
   empty publish can never overwrite them. */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CallRowSheetStripTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun freshApp() {
        UiStore.closeAll()
        UiStore.tabInternal = "recents"
        UiStore.locked = false
        Toasts.items.clear()
        AppStore.state.systemCalls = emptyList()
        AppStore.state.deviceContacts = emptyList()
    }

    private val contact: DeviceContact
        get() {
            val name = "Strip Person"
            return DeviceContact(
                recordID = "c88", displayName = name, givenName = name,
                middleName = null, familyName = "", nickname = null, company = null,
                jobTitle = null, note = null, birthday = null,
                phones = listOf(
                    DevicePhone("0911222333", "mobile", "0911222333"),
                    DevicePhone("0911444555", "work", "0911444555"),
                ),
                emails = listOf(), addresses = listOf(), hasPhoto = false,
            )
        }

    @Test
    fun longPressCallCardShowsBothNumbersAsTiles() {
        rule.waitUntil(timeoutMillis = 90000) { AppStore.state.hydrated }
        // v1.6.28 — the entry-point kick latched AND completed: boot hydrated
        // through the new MainActivity pre-setContent hydrate() (single-shot
        // guard held — the CallmonyApp LaunchedEffect call no-op'd safely)
        check(AppStore.hydrateStarted) { "boot did not run through the entry-point hydrate" }
        val base = System.currentTimeMillis() - 60_000L * 30
        rule.runOnUiThread {
            AppStore.state.settings = AppStore.state.settings.copy(onboarded = true)
            AppStore.state.callLogPerm = PermState.GRANTED
            AppStore.state.callLogSyncedAt = System.currentTimeMillis()
            AppStore.state.deviceContacts = listOf(contact)
            AppStore.state.contactX = emptyList()
            AppStore.state.systemCalls = listOf(
                DeviceCall("s1", null, "0911222333", 2, base, 0),
            )
        }
        rule.waitForIdle()

        // the call card renders with the contact NAME — long-press it to
        // open the per-row action sheet
        rule.waitUntil(timeoutMillis = 20000) {
            rule.onAllNodesWithText("Strip Person").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("Strip Person")[0].performTouchInput { longClick() }

        // the sheet is up and the strip shows BOTH numbers as tiles (the
        // sheet title repeats the name, so count semantics nodes, not
        // singles). A measure crash here kills the process — reaching the
        // assertions is the regression guard itself.
        val cc = AppStore.state.settings.countryCode
        rule.waitUntil(timeoutMillis = 20000) {
            rule.onAllNodesWithText(prettyPhone("0911222333", cc)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(timeoutMillis = 20000) {
            rule.onAllNodesWithText(prettyPhone("0911444555", cc)).fetchSemanticsNodes().isNotEmpty()
        }
        // the action rows below the strip are intact
        rule.waitUntil(timeoutMillis = 20000) {
            rule.onAllNodesWithText(com.callmony.i18n.t("act.call")).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
