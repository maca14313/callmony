package com.callmony

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText

import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callmony.lib.AppStore
import com.callmony.lib.UiStore
import com.callmony.platform.DeviceCall
import com.callmony.platform.PermState
import com.callmony.ui.kit.Toasts
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.15 Recents regressions:
   1. "like if there is more than one consecutive back to back missed call
      from the same phone number, you have to show just single call card
      with indication how many times it was missed" — grouping.
   2. "show there phone number in the name place if the contract is not
      saved" — the carrier-cached name must never take the title slot.
   3. "the component where it shows the icons tabs … should be sticking
      under the two tabs when the call card scrolls" — sticky day header. */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecentsV1615Test {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun freshApp() {
        UiStore.closeAll()
        UiStore.tabInternal = "recents"
        UiStore.locked = false
        Toasts.items.clear()
        AppStore.state.hydrated = false
        AppStore.state.systemCalls = emptyList()
        AppStore.state.deviceContacts = emptyList()
    }

    private val base = System.currentTimeMillis() - 60_000L * 60

    @Test
    fun consecutiveMissedCallsCollapseIntoASingleCardWithCount() {
        rule.waitUntil(timeoutMillis = 20000) { AppStore.state.hydrated }
        rule.runOnUiThread {
            AppStore.state.settings = AppStore.state.settings.copy(onboarded = true)
            AppStore.state.callLogPerm = PermState.GRANTED
            AppStore.state.callLogSyncedAt = System.currentTimeMillis()
            // three BACK-TO-BACK missed calls from the SAME number…
            AppStore.state.systemCalls = listOf(
                DeviceCall("m3", null, "091100007", 3, base + 5 * 60_000L, 0),
                DeviceCall("m2", null, "091100007", 3, base + 4 * 60_000L, 0),
                DeviceCall("m1", null, "091100007", 3, base + 3 * 60_000L, 0),
                // …then a single missed call from a DIFFERENT number…
                DeviceCall("m4", null, "091100008", 3, base + 2 * 60_000L, 0),
                // …and an outgoing call that must stay its own card.
                DeviceCall("o1", null, "091100009", 2, base + 1 * 60_000L, 30),
            )
        }
        rule.waitForIdle()

        // the collapsed run renders exactly ONE "(3)" badge
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithText("(3)").fetchSemanticsNodes().isNotEmpty()
        }
        check(rule.onAllNodesWithText("(3)").fetchSemanticsNodes().size == 1) {
            "expected exactly one collapsed missed-run card, got ${rule.onAllNodesWithText("(3)").fetchSemanticsNodes().size}"
        }
        // the grouped number shows as ONE card (its title appears once)
        val title = rule.onAllNodesWithText("+251 9110 0007").fetchSemanticsNodes()
        check(title.size == 1) { "expected 1 card for the grouped number, got ${title.size}" }
        // the other missed call still renders its own card
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithText("+251 9110 0008").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun unsavedNumberIsTheCardTitleEvenWithACarrierCachedName() {
        rule.waitUntil(timeoutMillis = 20000) { AppStore.state.hydrated }
        rule.runOnUiThread {
            AppStore.state.settings = AppStore.state.settings.copy(onboarded = true)
            AppStore.state.callLogPerm = PermState.GRANTED
            AppStore.state.callLogSyncedAt = System.currentTimeMillis()
            // cachedName set by the carrier — the v1.6.14 title logic
            // preferred it over the number (user: "show there phone number
            // in the name place if the contact is not saved")
            AppStore.state.systemCalls = listOf(
                DeviceCall("c1", "Cached Carrier Name", "091123456", 1, base + 60_000L, 12),
            )
        }
        rule.waitForIdle()

        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithText("+251 9112 3456").fetchSemanticsNodes().isNotEmpty()
        }
        // the cached name must NOT be the title
        check(rule.onAllNodesWithText("Cached Carrier Name").fetchSemanticsNodes().isEmpty()) {
            "carrier-cached name took the title slot instead of the phone number"
        }
    }

    @Test
    fun dayHeaderStaysReachableWhileCardsScroll() {
        rule.waitUntil(timeoutMillis = 20000) { AppStore.state.hydrated }
        rule.runOnUiThread {
            AppStore.state.settings = AppStore.state.settings.copy(onboarded = true)
            AppStore.state.callLogPerm = PermState.GRANTED
            AppStore.state.callLogSyncedAt = System.currentTimeMillis()
            // newest first (i=1 newest) — the system call log is always
            // newest-first and Recents renders it in array order
            AppStore.state.systemCalls = (1..40).map { i ->
                DeviceCall("s$i", null, "0912%05d".format(i), 2, base + (i - 1) * 60_000L, 10)
            }
        }
        rule.waitForIdle()

        // deep-scroll the list — the sticky day header (with the "All"
        // filter chip) must still be composed/visible for interaction
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithText("+251 9120 0001").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasTestTag("recentsList"))
            .performScrollToNode(hasText("+251 9120 0040", substring = false))
        rule.waitForIdle()
        // chips from the sticky header stay available after the scroll
        check(rule.onAllNodesWithText("All", substring = true).fetchSemanticsNodes().isNotEmpty()) {
            "day-header filter chips vanished while the cards scrolled"
        }
    }
}
