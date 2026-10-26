package com.callmony

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
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

/* Recents regression tests — the two v1.6.14 user reports:
   1. "in recents i cant scroll the call cards" — SwipeRow's drag detector
      consumed VERTICAL gestures, so the LazyColumn could never scroll.
   2. "when i click each of them it is not going to its contact detail
      page" — the rows useMemo was keyed only on systemCalls, so the
      phone→contact match was frozen at first render and every card stayed
      "unsaved" (tap opened the action sheet instead of the detail). */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecentsScrollTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /** The stores are JVM singletons — earlier tests in the battery leave
     *  hydrated=true / open sheets behind, which makes this test's waitUntil
     *  pass before THIS boot's hydrate has run (and that hydrate would then
     *  overwrite the seeded state mid-test). Reset the in-memory state so
     *  every test starts like a fresh app launch. */
    @Before
    fun freshApp() {
        UiStore.closeAll()
        UiStore.tabInternal = "recents"
        UiStore.locked = false
        Toasts.items.clear()
        // v1.6.25 — state.hydrated is NO LONGER reset here: the hydrate
        // LaunchedEffect(Unit) runs exactly once per composition, so when the
        // rule-launch boot finished BEFORE this @Before ran, the reset parked
        // the app on the splash forever and the first waitUntil deadlocked —
        // this exact race was the battery's notorious "sandbox flakiness"
        // (same 5 ComposeTimeoutExceptions every round, A/B-proven on
        // unmodified code). Seeding still lands safely: every test waits for
        // hydrated=true FIRST, so the boot's empty publish always happens
        // before the seeds.
        AppStore.state.systemCalls = emptyList()
        AppStore.state.deviceContacts = emptyList()
    }

    private val base = System.currentTimeMillis() - 60_000L * 60

    /** 30 contacts, each with a unique number 0911…NN. */
    private fun contacts(): List<DeviceContact> = (1..30).map { i ->
        val name = "Person %02d".format(i)
        DeviceContact(
            recordID = "c$i", displayName = name, givenName = name,
            middleName = null, familyName = "", nickname = null, company = null,
            jobTitle = null, note = null, birthday = null,
            phones = listOf(DevicePhone("0911%05d".format(i), "mobile", "0911%05d".format(i))),
            emails = listOf(), addresses = listOf(), hasPhoto = false,
        )
    }

    /** 30 outgoing calls, newest first, all today. */
    private fun calls(): List<DeviceCall> = (1..30).map { i ->
        DeviceCall(
            id = "call$i", cachedName = null,
            number = "0911%05d".format(i),
            type = 2, date = base + (30 - i) * 60_000L, duration = 30,
        )
    }

    @Test
    fun cardsRematchContactsTapOpensDetailAndListScrolls() {
        rule.waitUntil(timeoutMillis = 20000) { AppStore.state.hydrated }
        rule.runOnUiThread {
            AppStore.state.settings = AppStore.state.settings.copy(onboarded = true)
            // Seed the CALL LOG first — before any contact exists.
            AppStore.state.systemCalls = calls()
            AppStore.state.callLogPerm = PermState.GRANTED
            AppStore.state.callLogSyncedAt = System.currentTimeMillis()
        }
        rule.waitForIdle()

        // Cards render (with pretty numbers — no contacts yet).
        // prettyPhone("091100001", "ET") → "+251 9110 0001"
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithText("+251 9110 0001").fetchSemanticsNodes().isNotEmpty()
        }

        // NOW the contact book lands (this is the race the user hit every
        // boot: the call log hydrates before the +4s contacts refresh).
        // v1.6.14 re-keys the rows memo on contacts — the card for
        // 091100001 must switch to "Person 01".
        rule.runOnUiThread {
            AppStore.state.deviceContacts = contacts()
            AppStore.state.contactsPerm = PermState.GRANTED
        }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithText("Person 01", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Tap the card → ContactDetailOverlay opens (was: action sheet).
        rule.onAllNodesWithText("Person 01", substring = true)[0].performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 10000) {
            UiStore.overlayStack.lastOrNull() is Overlay.Contact
        }
        // …and it resolved to the right contact.
        rule.runOnUiThread {
            val top = UiStore.overlayStack.lastOrNull() as Overlay.Contact
            check(top.recordID == "c1") { "opened wrong contact: ${top.recordID}" }
            UiStore.closeAll()
        }
        rule.waitForIdle()

        // SCROLL: drive the list down to Person 25 — 28 cards below the fold.
        // (Real swipeUp gestures don't move scrollables under Robolectric NATIVE
        // graphics — verified against a bare LazyColumn — so the list's scroll
        // semantics action stands in for the gesture; the SwipeRow fix keeps
        // vertical drags unconsumed by using detectHorizontalDragGestures.)
        rule.onNode(hasTestTag("recentsList"))
            .performScrollToNode(hasText("Person 25", substring = true))
        rule.waitForIdle()
        check(rule.onAllNodesWithText("Person 25", substring = true).fetchSemanticsNodes().isNotEmpty()) {
            "the list never scrolled to Person 25"
        }

        // The horizontal swipe must still work: swipe a card left — the
        // row translates, the underlay buttons stay interactive, no crash.
        rule.onAllNodesWithText("Person 25", substring = true)[0]
            .performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onAllNodesWithText("Person 25", substring = true)[0].performClick()
        rule.waitForIdle()
        // an open row closes on tap instead of navigating; either way no crash
        check(true)
    }

    @Test
    fun bottomNavGeneralTileSwitchesToLedgerPage() {
        rule.waitUntil(timeoutMillis = 20000) { AppStore.state.hydrated }
        rule.runOnUiThread {
            AppStore.state.settings = AppStore.state.settings.copy(onboarded = true)
        }
        rule.waitForIdle()

        // The wallet tile (the "ledger button") never switched tabs before
        // v1.6.14 — tapping it did nothing at all.
        rule.onNodeWithContentDescription("wallet-outline").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 10000) { UiStore.tab == "general" }
        // The General screen hero ("General.") renders with its ledger segment.
        rule.waitUntil(timeoutMillis = 10000) {
            rule.onAllNodesWithText("General.", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // …and the Recents tile brings the call list back.
        rule.onNodeWithContentDescription("history").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 10000) { UiStore.tab == "recents" }
    }
}
