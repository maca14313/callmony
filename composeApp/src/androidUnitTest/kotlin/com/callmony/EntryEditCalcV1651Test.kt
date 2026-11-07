package com.callmony

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.model.LedgerEntry
import com.callmony.ui.kit.Confirm
import com.callmony.ui.kit.HostSheets
import com.callmony.ui.kit.RootBottomOverlays
import com.callmony.ui.kit.RootFab
import com.callmony.ui.kit.Toasts
import com.callmony.ui.overlays.ALARM_PULSE_CYCLES
import com.callmony.ui.overlays.prefixSlotDpOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.51 — regression battery for the two-report round + the two ANR
   field reports:

     ANR data protection (report #2: "this app should not loss all kind of
     data it have that user puts at it, even when installing again or
     update", filed beside two MAIN THREAD BLOCKED reports on a Galaxy A12
     where the system killed the process): the autosave is a MAX-WAIT loop
     now — a payable DRAFT persists while the typing is still going (the
     old debounce restarted on every keystroke, so continuous typing never
     persisted anything, and a draft had no autosave at all — a hard kill
     dropped it wholesale), an untouched editor still writes nothing, and
     a live row keeps updating mid-typing.

     The prefix slot (report #1: "the dot bised number is showing up in the
     second row, like 1."): the slot width is COMPUTED from the canvas's
     own font size and the widest number it can show — pinned pure — and
     the rendered slot follows it, with the prefix on ONE line (its height
     stays a single text line, not two). */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EntryEditCalcV1651Test {

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
        RootFab.content.value = null
        Confirm.req.value = null
        Toasts.items.clear()
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
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcKey:$k").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("calcKey:$k").performClick()
        rule.waitForIdle()
    }

    private fun leaveEditor() {
        rule.onNodeWithTag("calcCollapse").performClick()
        rule.waitForIdle()
    }

    /* 1 — the pure geometry: the slot is wide enough for the WIDEST number
       the canvas can show at the chosen px, floored at the v1.6.50
       reservation so sub-ladder sizes keep their exact look */
    @Test
    fun thePrefixSlotGeometryPinnedPure() {
        // one line, the ladder's smallest font: digit + period + air ≈ 1.18em
        assertEquals(12f * 1.18f, prefixSlotDpOf(12, 1), 0.01f)
        // "1." at the default 14px
        assertEquals(14f * 1.18f, prefixSlotDpOf(14, 1), 0.01f)
        // the PIXEL ladder's top stop: the slot grows with the font — the
        // fixed 14dp slot that wrapped "1."'s period is gone
        assertEquals(22f * 1.18f, prefixSlotDpOf(22, 1), 0.01f)
        // two-digit line numbers ("10.") reserve a wider slot
        assertEquals(22f * (0.60f * 2 + 0.28f + 0.30f), prefixSlotDpOf(22, 12), 0.01f)
        assertEquals(14f * (0.60f * 2 + 0.28f + 0.30f), prefixSlotDpOf(14, 10), 0.01f)
        // the v1.6.50 reservation floors anything below the ladder
        assertEquals(14f, prefixSlotDpOf(8, 1), 0.01f)
        // the alarm pulse is BOUNDED (the only infinite animation, gone)
        assertEquals(12, ALARM_PULSE_CYCLES)
    }

    /* 2 — the rendered slot follows the px choice and the prefix stays on
       ONE line: an entry saved at 22px with a number prefix reopens, and
       the "1." glyph's node is a single text line tall (a wrapped period
       would double the height) inside a slot wide enough for it */
    @Test
    fun thePrefixSlotHoldsTheDotOnOneRowAtBigFontSizes() {
        ready()
        val entryId = rule.runOnUiThread {
            val id = "t1651-bigfont"
            AppStore.saveEntry(
                LedgerEntry(
                    id = id, ownerId = "u:me", type = "credit", kind = "credit",
                    amount = 285.0, date = "2026-11-01",
                    exprNote = "285", calcPrefix = "number", calcFontSize = "22",
                ),
            )
            id
        }
        rule.waitForIdle()
        openEditor(entryId)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().isNotEmpty()
        }

        val density = rule.runOnUiThread { rule.activity.resources.displayMetrics.density }
        val prefix = rule.onNodeWithTag("calcLinePrefix:0", useUnmergedTree = true).fetchSemanticsNode()
        val lineText = rule.onNodeWithTag("calcLineText:0", useUnmergedTree = true).fetchSemanticsNode()

        // the slot is the COMPUTED width (22px, one committed line + the
        // active line → digits=1 → 1.18em), in real pixels
        val expectedPx = prefixSlotDpOf(22, 2) * density
        assertTrue(
            "the prefix slot must follow the canvas's 22px choice — " +
                "expected ≈$expectedPx px, got ${prefix.size.width} px",
            kotlin.math.abs(prefix.size.width - expectedPx) < 8f,
        )

        // THE REPORT: "the dot bised number is showing up in the second
        // row" — the prefix node must be ONE text line tall, not two (the
        // line text beside it renders at the same 22px, single line)
        assertTrue(
            "the prefix must stay on one line — prefix h=${prefix.size.height} px, " +
                "single line text h=${lineText.size.height} px",
            prefix.size.height < lineText.size.height * 1.55f,
        )
    }

    /* 3 — THE MAX-WAIT AUTOSAVE, the ANR report's data-loss hole: a
       payable DRAFT persists WHILE THE TYPING IS STILL GOING — no pause,
       no commit, no backing out. The old debounce restarted per keystroke
       (continuous typing = nothing saved) and a draft had no autosave at
       all, so a hard process kill dropped the whole draft */
    @Test
    fun aPayableDraftPersistsWhileTypingIsStillGoing() {
        ready()
        openEditor()
        toNumMode()
        listOf("2", "0", "0").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("= 200", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // the kill lands mid-typing: the virtual clock walks one max-wait
        // beat — the draft must ALREADY be a row in the store
        rule.mainClock.advanceTimeBy(700)
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.runOnUiThread {
                AppStore.state.entries.any { it.amount == 200.0 && it.exprNote == "200" }
            }
        }

        // and typing CONTINUES: more keystrokes, another beat — the live
        // row keeps updating without the hands ever leaving the keys
        listOf("+", "5", "0").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("= 250", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.mainClock.advanceTimeBy(700)
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.runOnUiThread {
                AppStore.state.entries.any {
                    it.amount == 250.0 && it.exprNote?.replace(" ", "") == "200+50"
                }
            }
        }
    }

    /* 4 — the gate: an UNTOUCHED editor writes nothing — the max-wait loop
       must not mint phantom rows for a canvas nobody typed on (the store
       singleton carries earlier methods' rows, so the pin is the DELTA) */
    @Test
    fun anUntouchedEditorWritesNothing() {
        ready()
        val before = rule.runOnUiThread { AppStore.state.entries.size }
        openEditor()
        // two full beats with nothing payable on the canvas
        rule.mainClock.advanceTimeBy(700)
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(700)
        rule.waitForIdle()
        val after = rule.runOnUiThread { AppStore.state.entries.size }
        assertEquals("an untouched editor must not create entries", before, after)
    }
}
