package com.callmony

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.hasClickAction
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
import com.callmony.ui.kit.HostSheets
import com.callmony.ui.kit.RootBottomOverlays
import com.callmony.ui.kit.Toasts
import com.callmony.ui.kit.Confirm
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.46 — regression battery for the five-report round:

     1. "i want the inter btn just like in the native keyboard for the
        keypad on the right bottom under the -" → the keypad carries a
        WIDE ENTER bar under the ± 0 . − row (geometry pinned in
        EntryEditCalcV1643Test 2a); here the behaviour is pinned: ENTER
        commits the line AND saves the entry, again and again without
        ever duplicating it.
     2. "put the pay btn bised the TOTAL text and the circle: a ring
        filling as you pay, should show how much % is left from the total
        and dont say something like 1 left" → the PAY chip sits BESIDE the
        TOTAL row with the percentage ring on it (the percentage walk
        itself is pinned in EntryEditCalcV1645Test).
     3. "the next btn also should do what the save btn does, if we make it
        like that, we dont need the save btn" → NO act.save button exists
        anywhere in the editor (canvas up or collapsed); NEXT and ENTER
        both persist.
     4. "in top if the canvas put a input that does have just the bottom
        border so we can give the canvas a title" → the header's
        bottom-border-only title input heads the canvas, headlines the
        collapsed band, rides the entry as calcTitle, and heads the
        whole-ledger export.
     5. "make the fonts better for calculation, now it looks to bulky" →
        the native number field renders the active line in the LIGHT cut
        (non-bold, 18sp) instead of the old hard Bold. */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EntryEditCalcV1646Test {

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

    private fun openEditor() {
        rule.runOnUiThread { UiStore.open(Overlay.EntryEdit(null, "u:me")) }
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

    private fun enter() {
        rule.onNodeWithTag("calcKeyEnter").performClick()
        rule.waitForIdle()
    }

    private fun awaitLine(i: Int) {
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLine:$i").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun entryCount(amount: Double): Int = rule.runOnUiThread {
        AppStore.state.entries.count { it.amount == amount }
    }

    /* 1 + 3 — the keypad's ENTER commits AND saves: the first ENTER
       creates the entry with the committed line; a second line + ENTER
       UPDATES it (two lines, one entry — never a duplicate); the editor
       and the keypad stay up the whole time.
       Harness note: the store is a JVM singleton across the battery, so
       the amounts are deliberately ones no other test writes (275 / 345)
       and the "same entry" checks are ID-based. */
    @Test
    fun theEnterKeyCommitsAndSavesWithoutDuplicating() {
        ready()
        openEditor()
        toNumMode()
        listOf("2", "7", "5").forEach { key(it) }

        enter()
        awaitLine(0)
        rule.waitUntil(timeoutMillis = 15000) { entryCount(275.0) >= 1 }
        val id = rule.runOnUiThread {
            AppStore.state.entries.first { it.amount == 275.0 }.id
        }

        // a second line: 70 → the entry becomes 345, still THE SAME entry
        listOf("7", "0").forEach { key(it) }
        enter()
        awaitLine(1)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.runOnUiThread { AppStore.state.entries.find { it.id == id }?.amount == 345.0 }
        }
        assertEquals("the second ENTER updates the same entry", 1, entryCount(345.0))
        // the blank-line ENTER afterwards is a harmless no-op (nothing to
        // commit, nothing new to persist)
        enter()
        rule.waitForIdle()
        assertEquals("a blank ENTER must not duplicate anything", 1, entryCount(345.0))
        // and the canvas + keypad stayed up throughout
        assertTrue(
            "the editor must still be open",
            rule.onAllNodesWithTag("calcBtnNext").fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "the keypad must still be armed",
            rule.onAllNodesWithTag("calcKeyEnter").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /* 3 — the Save button is GONE, canvas up or collapsed: no act.save
       control anywhere in the editor (Next and ENTER own persistence) */
    @Test
    fun theSaveButtonIsGoneEverywhere() {
        ready()
        openEditor()
        assertEquals(
            "no Save control while the canvas is up",
            0,
            rule.onAllNodes(hasText(t("act.save")) and hasClickAction()).fetchSemanticsNodes().size,
        )
        assertEquals(
            "the canvas's calcBtnSave tag must be gone",
            0,
            rule.onAllNodesWithTag("calcBtnSave").fetchSemanticsNodes().size,
        )
        // collapse to the form — the footer band is gone too
        rule.onNodeWithTag("calcCollapse").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcCollapsed").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(
            "no footer Save under the collapsed form",
            0,
            rule.onAllNodes(hasText(t("act.save")) and hasClickAction()).fetchSemanticsNodes().size,
        )
    }

    /* 2 — the Pay chip sits BESIDE the TOTAL row, with the percentage ring
       on the row's leading edge (the ring's % walk is pinned in the
       v1.6.45 battery) */
    @Test
    fun thePayChipSitsBesideTheTotalRowWithTheRing() {
        ready()
        openEditor()
        toNumMode()
        listOf("3", "0", "0").forEach { key(it) }
        rule.onNodeWithTag("calcBtnNext").performClick()
        rule.waitForIdle()
        awaitLine(0)

        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTotalPay").fetchSemanticsNodes().isNotEmpty()
        }
        val totalRow = rule.onNodeWithTag("calcLedgerTotal").fetchSemanticsNode()
        val payChip = rule.onNodeWithTag("calcTotalPay").fetchSemanticsNode()
        val ring = rule.onNodeWithTag("calcPayRing").fetchSemanticsNode()
        // "put the pay btn bised the TOTAL text and the circle" — all three
        // share ONE row now: their vertical centres sit within one band
        assertTrue(
            "the pay chip must sit beside the TOTAL row (pay y=${payChip.positionInRoot.y}, row y=${totalRow.positionInRoot.y})",
            kotlin.math.abs(payChip.positionInRoot.y - totalRow.positionInRoot.y) < 60f,
        )
        assertTrue(
            "the ring must sit on the TOTAL row (ring y=${ring.positionInRoot.y}, row y=${totalRow.positionInRoot.y})",
            kotlin.math.abs(ring.positionInRoot.y - totalRow.positionInRoot.y) < 60f,
        )
        // the ring leads the row (the pay chip trails it)
        assertTrue(
            "the ring sits before the pay chip",
            ring.positionInRoot.x < payChip.positionInRoot.x,
        )
        // the TOTAL label is still right there
        assertTrue(
            "the TOTAL label rides the row",
            rule.onAllNodes(hasText(t("ee.calcTotal"))).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /* 4 — the canvas TITLE: the header's bottom-border input takes a title,
       the collapsed band headlines it, the entry persists it, and the
       whole-ledger export heads with it */
    @Test
    fun theCanvasTitleHeadsTheCanvasBandEntryAndExport() {
        ready()
        openEditor()
        // the title input is IN the header, with the placeholder until typed
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("ee.calcTitlePh"))).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("calcTitleField").performTextInput("march rooms")
        rule.waitForIdle()
        // the placeholder is gone once the title is typed
        assertEquals(
            "the placeholder must leave once the title is typed",
            0,
            rule.onAllNodes(hasText(t("ee.calcTitlePh"))).fetchSemanticsNodes().size,
        )
        // a line to make it payable: rooms 300
        toNumMode()
        listOf("3", "0", "0").forEach { key(it) }
        rule.onNodeWithTag("calcKeyEnter").performClick()
        rule.waitForIdle()
        awaitLine(0)

        // the entry carries the title (ENTER saved it)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.runOnUiThread { AppStore.state.entries.any { it.calcTitle == "march rooms" } }
        }
        // collapse — the band headlines the TITLE in the label's place
        rule.onNodeWithTag("calcCollapse").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcCollapsed").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "the collapsed band must headline the title",
            rule.onAllNodes(hasText("march rooms", substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
        // raise the canvas again and export: the title heads the copy
        rule.onNodeWithTag("calcCollapsed").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTotalCopy").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("calcTotalCopy").performClick()
        rule.waitForIdle()
        val clip = rule.runOnUiThread {
            val cm = rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.getItemAt(0)?.text?.toString()
        }
        checkNotNull(clip) { "the copy button must put the ledger on the clipboard" }
        assertTrue("the export must begin with the title — got: $clip", clip.startsWith("march rooms"))
        assertTrue("the export must still carry the total — got: $clip", clip.contains("${t("ee.calcTotal")}: 300"))
    }

    /* 5 — the de-bulked calculation font: the native number field renders
       the active line in the LIGHT cut (non-bold, 18sp) — the heavy Bold
       that made the canvas "look to bulky" is gone */
    @Test
    fun theActiveLineRendersInTheLightCut() {
        ready()
        openEditor()
        toNumMode()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcNumField").fetchSemanticsNodes().isNotEmpty()
        }
        val et = com.callmony.ui.components.NoImeFieldProbe.last
        checkNotNull(et) { "the native number field must be composed" }
        val (bold, sizeSp) = rule.runOnUiThread {
            et.typeface.isBold to et.textSize / et.resources.displayMetrics.scaledDensity
        }
        assertFalse("the active line must not render Bold any more", bold)
        assertEquals("the active line renders 18sp", 18f, sizeSp, 0.5f)
    }
}
