package com.callmony

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.ui.kit.HostSheets
import com.callmony.ui.kit.RootBottomOverlays
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

/* v1.6.43 — regression battery for the user's report list:

     1. STILL-CRASHING number-mode typing → the CRASH FIREWALL (its own
        CrashFirewallTest pins log + file + clipboard + hand-over). Here the
        typing path itself stays under guard: rapid keys keep the app alive
        with the v1.6.43 no-op-recomposition view writes.
     2. THE KEYPAD NEVER SITS UNDER THE PHONE'S NAVIGATION BAR ("the keypad
        should be not under the phone netiv navigation, it should be just
        on top of the apps bottom navigation not the mobile, the keypad
        should have some bottom padding"): the host applies
        navigationBarsPadding (keys stop above the gesture-bar zone — under
        Robolectric the inset is 0, so what is pinned here is the WIRING:
        the overlay reports its measured top edge, the canvas reserves the
        EXACT covered band — 224dp keypad − 71dp app nav = 153dp — and the
        reservation collapses when the keypad closes).

   The 153dp pin is the core invariant: if the keypad's height, the app
   nav's shape, or the direct-measurement wiring drifts, the canvas's
   Next/Save controls would sit under the keypad — exactly the regression
   this guards. (All dp→px values are exact at the harness' 420dpi:
   2.625 = 21/8, so 224dp·21/8 − 71dp·21/8 = 153dp exactly.) */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EntryEditCalcV1643Test {

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

    private fun openEditor(entryId: String? = null) {
        rule.runOnUiThread { UiStore.open(Overlay.EntryEdit(entryId, "u:me")) }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTextField").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun toNumMode() {
        rule.onNodeWithTag("calcModeNum").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcKey:1").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun key(k: String) {
        rule.onNodeWithTag("calcKey:$k").performClick()
        rule.waitForIdle()
    }

    /* 1 — rapid number-mode typing stays fast and alive with the v1.6.43
       zero-touch no-op recompositions in the native field */
    @Test
    fun rapidNumberModeTypingStaysFastAndAlive() {
        ready()
        openEditor()
        toNumMode()
        val t0 = android.os.SystemClock.elapsedRealtime()
        repeat(120) { i ->
            when (i % 4) {
                0, 1, 2 -> key("${(i % 9) + 1}")
                else -> key("+")
            }
        }
        val ms = android.os.SystemClock.elapsedRealtime() - t0
        assertTrue("120 keys took ${ms}ms — degraded", ms < 45000)
        assertTrue(
            "the keypad must still be up",
            rule.onAllNodesWithTag("calcKey:1").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /* 2a — the overlay REPORTS its top edge and the canvas reserves the
       EXACT covered band: keypad 224dp over an app nav of 71dp → 153dp.
       This is the wiring that keeps the canvas's Next/Save controls clear
       of the keypad while the keys themselves sit above the phone's system
       navigation bar. */
    @Test
    fun theCanvasReservesTheExactKeypadBand() {
        ready()
        openEditor()
        toNumMode()
        // the keypad measured itself and reported its top edge
        rule.waitUntil(timeoutMillis = 15000) { RootBottomOverlays.overlayTopPx.value >= 0f }
        // the canvas reserved exactly the covered band (one frame later the
        // lift lands — wait for it)
        rule.waitUntil(timeoutMillis = 15000) { RootBottomOverlays.lift.value > 0.dp }
        val lift = RootBottomOverlays.lift.value.value
        assertTrue(
            "reserved band must be 153dp (224dp keypad − 71dp app nav), was $lift dp",
            kotlin.math.abs(lift - 153f) < 0.5f,
        )
        // the keypad still reaches DOWN over the app's bottom navigation
        // (the covered band is real, not degenerate)
        assertTrue("the keypad must intrude into the content area", lift > 100f)
        // the keys kept a healthy size (the nav padding + grid padding did
        // not squeeze the rows into slivers)
        rule.onNodeWithTag("calcKey:1").assertHeightIsAtLeast(30.dp)
        rule.onNodeWithTag("calcKey:0").assertHeightIsAtLeast(30.dp)
    }

    /* 2b — closing the keypad collapses the reservation: the top-edge
       report resets and the lift falls back to zero (nothing anchors above
       a keypad that is no longer there) */
    @Test
    fun closingTheKeypadCollapsesTheReservation() {
        ready()
        openEditor()
        toNumMode()
        rule.waitUntil(timeoutMillis = 15000) { RootBottomOverlays.overlayTopPx.value >= 0f }
        rule.waitUntil(timeoutMillis = 15000) { RootBottomOverlays.lift.value > 0.dp }
        // the native back button closes the keypad first (v1.6.39 rule)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) { RootBottomOverlays.overlayTopPx.value < 0f }
        rule.waitUntil(timeoutMillis = 15000) { RootBottomOverlays.lift.value == 0.dp }
        assertEquals("no keypad content left at the root", null, RootBottomOverlays.content.value)
        // the canvas itself is still up (back collapsed the keypad only)
        assertTrue(
            "the canvas stays after the keypad closes",
            rule.onAllNodesWithTag("calcBtnNext").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /* 2c — re-arming the keypad re-reports and re-reserves (toggle away and
       back: Aa → 123) */
    @Test
    fun rearmingTheKeypadReMeasures() {
        ready()
        openEditor()
        toNumMode()
        rule.waitUntil(timeoutMillis = 15000) { RootBottomOverlays.lift.value > 0.dp }
        val firstTop = RootBottomOverlays.overlayTopPx.value
        // toggle to text — keypad down
        rule.onNodeWithTag("calcModeText").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) { RootBottomOverlays.overlayTopPx.value < 0f }
        // toggle back to numbers — keypad up again with the same geometry
        rule.onNodeWithTag("calcModeNum").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) { RootBottomOverlays.overlayTopPx.value >= 0f }
        rule.waitUntil(timeoutMillis = 15000) { RootBottomOverlays.lift.value > 0.dp }
        assertEquals(
            "the re-measured top edge matches (stable geometry)",
            firstTop,
            RootBottomOverlays.overlayTopPx.value,
            0.5f,
        )
    }
}
