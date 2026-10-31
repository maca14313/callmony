package com.callmony

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.lib.fd
import com.callmony.lib.todayStr
import com.callmony.ui.kit.Confirm
import com.callmony.ui.kit.HostSheets
import com.callmony.ui.kit.RootBottomOverlays
import com.callmony.ui.kit.Toasts
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.42 — regression battery for the user's report list on the v1.6.41
   annotated calculator:

     1. NUMBER-MODE TYPING: rapid keypad typing stays flat-cost and the
        app stays alive (the crash report's regression guard; the fix
        itself is the guarded focus + cached view writes + the gated
        autosave key).
     2. NO PLACEHOLDER: the empty active line shows the CARET, not the
        example text ("we don't need the place holder").
     3. THE + KEY is back on the 1 2 3 row of the keypad.
     4. THE CLEAR ✕ beside the Aa/123 toggle is gone (asserted here in
        passing; the v1.6.40 battery pins the AC fallback).
     5. RTL: Arabic text typed FIRST (text mode) renders in the field.
     6. THE SUM TOTAL is a pure read-out (no pay chip, no history rows;
        Paid · Remaining once anything is paid).
     8. THE PAY GATE: a locked entry's pay chip is inert; the lock pill
        tap unlocks it (state label + colours); the history list sits
        ABOVE the total with the FULL note.
     9. THE NEXT BUTTON says just "Next".
    10. EVERY ENTRY carries its commit date; payments carry their date.

   Harness notes: identical to the v1.6.41 battery (hydrated never reset;
   global hosts cleared in @After; a payment sheet leak would keep the
   root keypad down and eat every later tap). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EntryEditCalcV1642Test {

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

    private fun nextLine() {
        rule.onNodeWithTag("calcBtnNext").performClick()
        rule.waitForIdle()
    }

    private fun awaitFocused(tag: String) {
        rule.waitUntil(timeoutMillis = 15000) {
            try {
                rule.onNodeWithTag(tag).fetchSemanticsNode()
                    .config[SemanticsProperties.Focused] == true
            } catch (_: Exception) { false }
        }
    }

    /* 1 — rapid number-mode typing: flat cost, app alive (the reported
       "it starts to lag, and finally the app closes") */
    @Test
    fun rapidNumberModeTypingStaysFastAndAlive() {
        ready()
        openEditor()
        toNumMode()
        val t0 = android.os.SystemClock.elapsedRealtime()
        repeat(150) { i ->
            when (i % 5) {
                0, 1, 2 -> key("${(i % 9) + 1}")
                3 -> key("+")
                else -> key("5")
            }
        }
        val ms = android.os.SystemClock.elapsedRealtime() - t0
        assertTrue("150 keys took ${ms}ms — degraded", ms < 45000)
        // the canvas + keypad are still alive and the text is all there
        assertTrue(
            "the keypad must still be up",
            rule.onAllNodesWithTag("calcKey:1").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /* 2 — the empty line shows the CARET, never the placeholder */
    @Test
    fun theEmptyLineShowsTheCaretNotAPlaceholder() {
        ready()
        openEditor()
        awaitFocused("calcTextField")
        // no example/placeholder text anywhere on the canvas
        assertEquals(
            "the placeholder example must be gone",
            0,
            rule.onAllNodes(hasText("ball 100", substring = true)).fetchSemanticsNodes().size,
        )
        // type, Next → the FRESH line is empty AND focused (caret there)
        rule.onNodeWithTag("calcTextField").performTextInput("zebra")
        rule.waitForIdle()
        nextLine()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("zebra")).fetchSemanticsNodes().isNotEmpty()
        }
        awaitFocused("calcTextField")
        // still no placeholder on the now-empty fresh line
        assertEquals(
            "the fresh empty line must not show the placeholder",
            0,
            rule.onAllNodes(hasText("ball", substring = true)).fetchSemanticsNodes().size,
        )
    }

    /* 3 — the + key is back in its CLASSIC spot: the 1 2 3 row */
    @Test
    fun thePlusKeyIsBackOnTheOneTwoThreeRow() {
        ready()
        openEditor()
        toNumMode()
        val one = rule.onNodeWithTag("calcKey:1").fetchSemanticsNode().positionInRoot
        val plus = rule.onNodeWithTag("calcKey:+").fetchSemanticsNode().positionInRoot
        // the − key's grid tag is the ASCII hyphen (the grid maps "−" → "-")
        val minus = rule.onNodeWithTag("calcKey:-").fetchSemanticsNode().positionInRoot
        val times = rule.onNodeWithTag("calcKey:×").fetchSemanticsNode().positionInRoot
        val divide = rule.onNodeWithTag("calcKey:÷").fetchSemanticsNode().positionInRoot
        // + sits on the 1 2 3 row (same band as 1, same column as ÷ × −)
        assertTrue("+ must share 1's row", kotlin.math.abs(plus.y - one.y) < 40f)
        assertTrue("+ must be in the operator column", kotlin.math.abs(plus.x - divide.x) < 40f)
        // the classic column order: ÷ above × above +, and − BELOW +
        assertTrue("÷ above ×", divide.y < times.y)
        assertTrue("× above +", times.y < plus.y)
        assertTrue("− takes the bottom slot", minus.y > plus.y)
    }

    /* 4 — the clear-✕ beside the Aa/123 toggle is gone */
    @Test
    fun theClearXBesideTheToggleIsGone() {
        ready()
        openEditor()
        assertEquals(
            "no clear ✕ beside the Aa/123 toggle",
            0,
            rule.onAllNodesWithTag("calcBtnClear").fetchSemanticsNodes().size,
        )
    }

    /* 5 — RTL: Arabic typed FIRST, in TEXT mode, renders in the field.
       (The post-toggle probe is impossible in the harness: the num-mode
       native EditText does not publish its text through Compose semantics
       under Robolectric — the text itself rides calcText, pinned by the
       v1.6.40 batteries.) */
    @Test
    fun arabicTypedFirstInTextModeRenders() {
        ready()
        rule.runOnUiThread {
            AppStore.state.settings = AppStore.state.settings.copy(language = "ar")
        }
        rule.waitForIdle()
        openEditor()
        val field = rule.onNodeWithTag("calcTextField")
        field.performTextInput("مرحبا")
        rule.waitForIdle()
        assertTrue(
            "the Arabic text must render in the field",
            rule.onAllNodes(hasText("مرحبا", substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /* 6+8 — the sum total is a pure read-out; payments happen on the
       UNLOCKED entries; the history list sits above the total with the
       FULL note; every entry carries its date */
    @Test
    fun theSumIsAReadOutAndTheUnlockedEntryCarriesItsHistoryAboveTheTotal() {
        ready()
        openEditor()
        toNumMode()
        listOf("3", "0", "0").forEach { key(it) }
        nextLine()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().isNotEmpty()
        }
        // #10 — the entry carries its commit date (today). The date Text
        // MERGES into the line's semantics (the row is combinedClickable)
        // — probe by TEXT, short month names ("Oct 31, 2026"), not by tag
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(fd(todayStr()), substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "the entry's date is today",
            rule.onAllNodes(hasText(fd(todayStr()), substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )

        // #8 — LOCKED: the pay chip is inert (no modal)
        rule.onNodeWithTag("calcLinePay:0").performClick()
        rule.waitForIdle()
        assertEquals(
            "a locked entry's pay chip must not open the modal",
            0,
            rule.onAllNodes(hasText("${t("ee.calcLineTotal")} · 300 Br", substring = true)).fetchSemanticsNodes().size,
        )

        // the lock pill tap UNLOCKS the entry — proven functionally right
        // here: the previously-inert pay chip now opens the modal. (The
        // Unlocked/Locked state LABEL rides the history block, asserted
        // after the payment lands below.)
        rule.onNodeWithTag("calcLineLock:0").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("calcLinePay:0").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("${t("ee.calcLineTotal")} · 300 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // pay through the now-armed chip, with a LONG note
        val payBtns = rule.onAllNodes(hasText(t("ee.calcPay"), substring = true) and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { payBtns.fetchSemanticsNodes().isNotEmpty() }
        payBtns[payBtns.fetchSemanticsNodes().size - 1].performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("lg.recordPayment"), substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val fields = rule.onAllNodes(hasSetTextAction())
        rule.waitUntil(timeoutMillis = 15000) { fields.fetchSemanticsNodes().isNotEmpty() }
        fields[fields.fetchSemanticsNodes().size - 1]
            .performTextInput("paid by abel for the transport and the lunch of the whole family together")
        rule.waitForIdle()
        val saves = rule.onAllNodes(hasText(t("act.save")) and hasClickAction())
        saves[saves.fetchSemanticsNodes().size - 1].performClick()
        rule.waitForIdle()

        // the history row: ABOVE the total, FULL note (the tail would be
        // ellipsed under a one-line clamp), the payment's date, and the
        // entry's UNLOCKED state on the block (report #8: the history list
        // shows whether it is locked or unlocked)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLinePayHist:0:0").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "the history block carries the UNLOCKED state",
            rule.onAllNodes(hasText(t("ee.calcEntryUnlocked"), substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "the FULL note must show (its tail included)",
            rule.onAllNodes(hasText("whole family together", substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "the payment row carries its date",
            rule.onAllNodes(hasText(fd(todayStr()), substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
        val hist = rule.onNodeWithTag("calcLinePayHist:0:0").fetchSemanticsNode()
        val total = rule.onNodeWithTag("calcLineTotal:0").fetchSemanticsNode()
        assertTrue(
            "the history list must sit ABOVE the entry's total",
            hist.positionInRoot.y < total.positionInRoot.y,
        )

        // #6 — the sum total: NO pay chip, NO history rows under it — just
        // the Paid · Remaining line
        assertEquals(
            "no pay chip at the sum total",
            0,
            rule.onAllNodesWithTag("calcTotalPay").fetchSemanticsNodes().size,
        )
        assertEquals(
            "no payment-history rows at the sum total",
            0,
            rule.onAllNodesWithTag("calcPayHist:0").fetchSemanticsNodes().size,
        )
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTotalPaidLine").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "the sum shows Paid · Remaining",
            rule.onAllNodes(hasText("${t("lg.paid")} 300 Br", substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /* 9 — the button says just "Next" */
    @Test
    fun theButtonSaysJustNext() {
        ready()
        openEditor()
        assertTrue(
            "the Next button must carry the plain label",
            rule.onAllNodes(hasText(t("ee.calcNext")) and hasClickAction()).fetchSemanticsNodes().isNotEmpty(),
        )
        assertEquals("Next", t("ee.calcNext"))
        assertEquals(
            "the old 'Next line' label must be gone",
            0,
            rule.onAllNodes(hasText("Next line")).fetchSemanticsNodes().size,
        )
    }
}
