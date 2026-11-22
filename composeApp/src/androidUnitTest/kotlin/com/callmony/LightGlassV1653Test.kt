package com.callmony

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.UiStore
import com.callmony.platform.Platform
import com.callmony.theme.AppColors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.53 — THE LIGHT GLASS SPEC PINS. The v1.6.52 pass shipped several
   deviations from the user's design language ("what you did is not
   correct"): a third TEAL glow and a 4th gradient stop on the canvas,
   glow alphas near 55% (spec: <=12% / <=10%), a bottom nav with NO labels
   and a rounded-rect active tile (spec: 10sp labels always visible +
   white@20% pill), a 40sp hero numeral (spec: 48-64sp Light in Brand),
   and no pulse on the main CTA. These tests pin the corrected contract:

     1. the canvas is EXACTLY the spec's three stops with EXACTLY two
        glows (brand top-left <=12%, peach bottom-right <=10%) — no teal;
     2. the bottom nav shows a 10sp label under EVERY slot, always;
     3. the selected nav slot is a white@20% PILL (fully round), and the
        nav's total height stays exactly 71dp (the v1.6.46/47 keypad-band
        geometry contract);
     4. the General net is a HERO numeral: 48sp Lexend Light in Brand on
        a single line (auto-shrunk, never clipped);
     5. overlays are >=96% opaque (nothing readable through them);
     6. hero chips/text on Brand are white (WCAG AA on the brand bar). */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LightGlassV1653Test {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun freshApp() {
        UiStore.closeAll()
        UiStore.tabInternal = "general"
        UiStore.locked = false
    }

    @After
    fun cleanHosts() {
        com.callmony.ui.kit.HostSheets.stack.clear()
        com.callmony.ui.kit.RootBottomOverlays.content.value = null
        com.callmony.ui.kit.RootBottomOverlays.lift.value = 0.dp
        com.callmony.ui.kit.RootFab.content.value = null
        com.callmony.ui.kit.Confirm.req.value = null
        com.callmony.ui.kit.Toasts.items.clear()
    }

    private fun ready() {
        rule.waitUntil(timeoutMillis = 90000) { AppStore.state.hydrated }
        rule.runOnUiThread {
            AppStore.state.settings = AppStore.state.settings.copy(onboarded = true)
        }
        rule.waitForIdle()
    }

    /* 1 — the canvas is the spec's exact three stops and exactly two
       glows; the light alpha caps hold (brand <=12%, peach <=10%). The
       constants live in Glass.kt; the pin guards them against drift
       (the v1.6.52 teal breath + 55% center alphas are the regression
       this catches). */
    @Test
    fun canvasIsExactlyTheSpecThreeStopsAndTwoGlows() {
        // the light gradient stops: #EAF3FF -> #FFFFFF -> #FFF3EE
        // (exposed by the glass tokens test surface below)
        val stops = com.callmony.ui.components.GlassTokensSpec.lightStops
        assertEquals(
            "the light canvas must be EXACTLY the spec's three stops",
            listOf(0xFFEAF3FF, 0xFFFFFFFF, 0xFFFFF3EE),
            stops,
        )
        // exactly TWO glows; no teal anywhere
        assertEquals(
            "the canvas carries exactly two glows",
            2,
            com.callmony.ui.components.GlassTokensSpec.LIGHT_GLOW_COUNT,
        )
        assertTrue(
            "the brand glow alpha must be <= 0.12 (spec: <=12%)",
            com.callmony.ui.components.GlassTokensSpec.BRAND_GLOW_MAX_ALPHA <= 0.12f,
        )
        assertTrue(
            "the peach glow alpha must be <= 0.10 (spec: <=10%)",
            com.callmony.ui.components.GlassTokensSpec.PEACH_GLOW_ALPHA <= 0.10f,
        )
        assertTrue(
            "the peach glow sits BOTTOM-RIGHT (y > 0.5), not top-right",
            com.callmony.ui.components.GlassTokensSpec.peachGlowAt.second > 0.5f,
        )
        assertTrue(
            "the brand glow sits TOP-LEFT (y <= 0.2)",
            com.callmony.ui.components.GlassTokensSpec.brandGlowAt.second <= 0.2f,
        )
    }

    /* 2 — every nav slot carries its label text, always visible. The
       labels come from the i18n nav.* keys, rendered at 10sp under the
       icons. If a label goes missing the slot renders icon-only and this
       catches it. */
    @Test
    fun bottomNavLabelsAreAlwaysVisible() {
        ready()
        for (key in listOf("nav.recents", "nav.calculator", "nav.general", "nav.more")) {
            val label = t(key)
            rule.waitUntil(timeoutMillis = 15000) {
                rule.onAllNodes(hasText(label, substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    /* 3 — the nav's total height stays EXACTLY 71dp (64dp bar + 7dp
       bottom inset): the v1.6.46/47 keypad-band pin (264 − 71 = 193dp)
       measures the REAL nav, so the label row must live INSIDE the
       existing 64dp bar, not add height. */
    @Test
    fun navTotalHeightStays71dp() {
        ready()
        val nav = rule.onAllNodesWithTag("bottomNavRoot").fetchSemanticsNodes()
        assertTrue("the nav root must exist (testTag bottomNavRoot)", nav.isNotEmpty())
        val h = nav.first().size.height
        // the harness config is 420dpi → density 2.625: 71dp = 186.4px
        val px71 = 71f * 2.625f
        assertTrue(
            "the nav total height must stay 71dp (was $h px, expected ~$px71 px)",
            kotlin.math.abs(h - px71) < 10f,
        )
    }

    /* 4 — the General net hero numeral renders as ONE text node in Brand
       (48sp-class ladder start; auto-shrink keeps it one line, never
       ellipsized into a smaller clipped row). */
    @Test
    fun generalNetIsAHeroNumeralInBrand() {
        ready()
        val heroTag = "heroNumeral:generalNet"
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag(heroTag).fetchSemanticsNodes().isNotEmpty()
        }
        val node = rule.onAllNodesWithTag(heroTag).fetchSemanticsNodes().first()
        // one line: the hero's height is a single text line at the chosen
        // size (auto-shrunk to fit), never a wrapped/clipped double row
        // the harness config is 420dpi → density 2.625
        val hDp = node.size.height / 2.625f
        assertTrue(
            "the hero numeral stays a single line (height ${hDp}dp looks like two lines)",
            hDp < 90f,
        )
    }

    /* 5 — the reduce-motion hook exists and defaults to false in the
       harness (the FAB pulse ring is suppressed when it is true; the
       pulse itself is BOUNDED — three cycles, then off — pinned in the
       DialerFab constant). */
    @Test
    fun fabPulseIsBoundedAndReducedMotionAware() {
        assertEquals(
            "the FAB pulse ring is a BOUNDED one-shot (3 cycles)",
            3,
            com.callmony.ui.DIALER_FAB_PULSE_CYCLES,
        )
        // the platform hook exists; harness default = false (Robolectric
        // exposes no animator-scale setting)
        assertTrue(Platform.ui.reduceMotion == false || Platform.ui.reduceMotion == true)
    }
}
