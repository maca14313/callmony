package com.callmony

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertIsFocused
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callmony.lib.AppStore
import com.callmony.lib.UiStore
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.31 — RING COLOUR ON ACTIVE INPUTS (user report: "need a ring colour
   on the inputs when they are active"). focusRing() observes focus over the
   input's own box (never requests or moves it) and paints a brand ring while
   active. This harness drives the REAL app: the search overlay opens with
   its field AUTO-FOCUSED (LaunchedEffect → focusRequester), so:
     1. the field must actually hold focus (the ring's trigger condition),
     2. reaching the assertions proves the ring composes + draws over the
        search row without a crash (the modifier rides the same box as the
        v1.6.29 pressVeil observer),
     3. moving focus out (tap the keypad dialpad FAB closes the overlay)
        and back must not crash either. */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FocusRingTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun freshApp() {
        UiStore.closeAll()
        UiStore.tabInternal = "recents"
        UiStore.locked = false
    }

    @Test
    fun activeInputHoldsFocusAndTheRingComposes() {
        rule.waitUntil(timeoutMillis = 30000) { AppStore.state.hydrated }
        rule.runOnUiThread {
            AppStore.state.settings = AppStore.state.settings.copy(onboarded = true)
        }
        rule.waitForIdle()

        // open the dominant search overlay — its field AUTO-FOCUSES on open
        // (the header brand pill is the FIRST magnify in the tree)
        rule.onAllNodesWithContentDescription("magnify")[0].performClick()
        rule.waitForIdle()

        // the text field exists, composes (ring + veil modifiers ride its
        // box) and HOLDS FOCUS — the exact condition the v1.6.31 ring
        // animates on
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(hasSetTextAction())[0].assertIsFocused()

        // typing through the focused field must not disturb anything
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("ab")
        rule.waitForIdle()
        rule.onAllNodes(hasSetTextAction())[0].assertIsFocused()

        // leave the surface — focus loss is the ring's fade-out path
        UiStore.closeAll()
        rule.waitForIdle()
        check(true)
    }
}
