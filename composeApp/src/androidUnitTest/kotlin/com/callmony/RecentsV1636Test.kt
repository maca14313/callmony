package com.callmony

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callmony.lib.AppStore
import com.callmony.lib.UiStore
import com.callmony.platform.DeviceCall
import com.callmony.platform.PermState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.36 — the ALL-DIRECTION consecutive-run collapse (user report:
   "when the same person have conversation consecutively make it in one
   call card just like the missed call if it is the same number for that
   person"). A back-to-back run of calls from ONE number — whatever the
   mix of directions — renders as ONE card headed by the newest call's
   direction, carrying the same "(n)" count badge the missed-run collapse
   always had. Non-consecutive calls from the same number stay their own
   cards (the run must be adjacent in the log).

   Same harness notes as every Recents battery here: hydrated is NOT
   reset in @Before (the post-boot reset deadlocked the splash); calls +
   contacts are cleared instead, and onboarded flips AFTER
   waitUntil(hydrated). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecentsV1636Test {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun freshApp() {
        UiStore.closeAll()
        UiStore.tabInternal = "recents"
        UiStore.locked = false
        AppStore.state.systemCalls = emptyList()
        AppStore.state.deviceContacts = emptyList()
    }

    private fun ready() {
        rule.waitUntil(timeoutMillis = 20000) { AppStore.state.hydrated }
        rule.runOnUiThread {
            AppStore.state.settings = AppStore.state.settings.copy(onboarded = true)
        }
        rule.waitForIdle()
    }

    private val base = System.currentTimeMillis() - 60_000L * 60

    @Test
    fun mixedDirectionRunsFromOneNumberCollapseIntoOneCard() {
        ready()
        rule.runOnUiThread {
            AppStore.state.settings = AppStore.state.settings.copy(onboarded = true)
            AppStore.state.callLogPerm = PermState.GRANTED
            AppStore.state.callLogSyncedAt = System.currentTimeMillis()
            // a back-to-back conversation with ONE number, mixed
            // directions (newest first): outgoing → incoming → incoming
            AppStore.state.systemCalls = listOf(
                DeviceCall("o1", null, "091100007", 2, base + 5 * 60_000L, 30),
                DeviceCall("i2", null, "091100007", 1, base + 4 * 60_000L, 12),
                DeviceCall("i1", null, "091100007", 1, base + 3 * 60_000L, 8),
                // a different number breaks nothing
                DeviceCall("i9", null, "091100008", 1, base + 2 * 60_000L, 5),
            )
        }
        rule.waitForIdle()

        // ONE card for the run, with the "(3)" badge — just like the
        // missed-call collapse
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithText("(3)").fetchSemanticsNodes().isNotEmpty()
        }
        val cards = rule.onAllNodesWithText("+251 9110 0007").fetchSemanticsNodes()
        check(cards.size == 1) { "expected 1 collapsed card for the mixed run, got ${cards.size}" }
        // the other number keeps its own card
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithText("+251 9110 0008").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun sameNumberButNotConsecutiveStaysSeparateCards() {
        ready()
        rule.runOnUiThread {
            AppStore.state.settings = AppStore.state.settings.copy(onboarded = true)
            AppStore.state.callLogPerm = PermState.GRANTED
            AppStore.state.callLogSyncedAt = System.currentTimeMillis()
            // same number, but ANOTHER number's call sits between the two
            AppStore.state.systemCalls = listOf(
                DeviceCall("o1", null, "091100007", 2, base + 5 * 60_000L, 30),
                DeviceCall("i9", null, "091100008", 1, base + 4 * 60_000L, 5),
                DeviceCall("i1", null, "091100007", 1, base + 3 * 60_000L, 8),
            )
        }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithText("+251 9110 0007").fetchSemanticsNodes().isNotEmpty()
        }
        val cards = rule.onAllNodesWithText("+251 9110 0007").fetchSemanticsNodes()
        check(cards.size == 2) { "non-consecutive same-number calls must stay 2 cards, got ${cards.size}" }
        // and no run badge anywhere
        check(rule.onAllNodesWithText("(2)").fetchSemanticsNodes().isEmpty()) {
            "a (2) badge rendered for non-consecutive calls"
        }
    }
}
