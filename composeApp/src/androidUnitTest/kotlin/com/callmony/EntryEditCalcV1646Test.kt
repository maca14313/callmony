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
        keypad on the right bottom under the -" → the keypad carries the
        ENTER key under the ± 0 . − row (geometry pinned in
        EntryEditCalcV1643Test 2a). v1.6.47 REWROTE the behaviour for the
        round-two report ("the inter btn is not the next btn… in its row
        add the space key too"): the enter is the plain NEWLINE (commit
        only — Next owns the save) and the SPACE bar joined its row; the
        tests below pin the new contract.
     2. "put the pay btn bised the TOTAL text and the circle: a ring
        filling as you pay, should show how much % is left from the total
        and dont say something like 1 left" → the PAY chip sits BESIDE the
        TOTAL row with the percentage ring on it (the percentage walk
        itself is pinned in EntryEditCalcV1645Test).
     3. "the next btn also should do what the save btn does, if we make it
        like that, we dont need the save btn" → NO act.save button exists
        anywhere in the editor (canvas up or collapsed); NEXT persists.
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

    /* 1 + 3 — v1.6.48, REWRITTEN AGAIN for the round-three enter contract
       ("the reason i wanted the inter btn in the number keypad is to go
       to next line in the same individual calculation, just like the
       netiv inter btn do, not to do what the next btn do"): the keypad's
       enter is the LITERAL NEWLINE INSIDE the same individual calculation
       — the line keeps calculating across the break and NOTHING commits
       by itself (Next owns the commit + SAVE); the space key sits in the
       enter's row. 275 ⏎ 70 stays ONE line reading 345 live; backing out
       persists the never-saved draft through the leave-time create — ONE
       entry, never a duplicate.
       Harness note: the store is a JVM singleton across the battery, so
       the amounts are deliberately ones no other test writes (275 / 345)
       and the "same entry" checks are ID-based. */
    @Test
    fun theEnterKeyNewlinesTheSameLineAndBackingOutPersistsIt() {
        ready()
        openEditor()
        toNumMode()
        listOf("2", "7", "5").forEach { key(it) }

        // ENTER — the newline INSIDE the same individual calculation
        enter()
        rule.waitForIdle()
        // nothing committed: the line stays active, no ledger block
        assertEquals(
            "the enter key must not commit — it is the newline, not Next",
            0,
            rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().size,
        )
        Thread.sleep(300) // any stray save pulse would land here — none may
        rule.waitForIdle()
        assertEquals(
            "the enter key must NOT persist by itself",
            0,
            entryCount(345.0),
        )

        // the SAME line continues on the next visual row: 70 → reads 345
        listOf("7", "0").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("345 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        // a trailing blank ENTER afterwards is a harmless no-op
        enter()
        rule.waitForIdle()
        // the canvas + keypad stayed up throughout (the enter key and the
        // space bar beside it are both still armed)
        assertTrue(
            "the keypad stays armed while the editor is open",
            rule.onAllNodesWithTag("calcKeyEnter").fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "the space bar sits in the enter key's row",
            rule.onAllNodesWithTag("calcKeySpace").fetchSemanticsNodes().isNotEmpty(),
        )

        // backing out persists the never-saved draft (the leave-time
        // create) — ONE entry, the one multi-visual-line calculation, never
        // a duplicate
        repeat(4) {
            if (rule.onAllNodesWithTag("calcBtnNext").fetchSemanticsNodes().isEmpty() &&
                rule.onAllNodesWithTag("calcCollapsed").fetchSemanticsNodes().isEmpty()
            ) return@repeat
            rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
            rule.waitForIdle()
        }
        rule.waitUntil(timeoutMillis = 15000) { entryCount(345.0) == 1 }
        // v1.6.49 — report #5 ("when it got locked, the line disappeared,
        // it just one line why"): the enter key's break rides the saved
        // grammar as the ⏎ marker (U+23CE) — one entry, the one
        // multi-visual-line calculation, the break PRESERVED
        val savedExpr = rule.runOnUiThread {
            AppStore.state.entries.first { it.amount == 345.0 }.exprNote
        }
        assertTrue(
            "the newline survives the save as the ⏎ marker — got: $savedExpr",
            savedExpr?.replace(Regex("\\s*@\\d{4}-\\d{2}-\\d{2}"), "") == "275 +\u23CE70",
        )
    }

    /* 3 — the Save button is GONE — v1.6.49: everywhere, because the FORM
       itself is gone (report #7): the canvas is the whole editor, and Next
       owns persistence since v1.6.47 (the enter key became the plain
       newline). ✕ closes the editor; no act.save control can exist in any
       state */
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
        // v1.6.49 — there is no form to collapse to: ✕ CLOSES the editor,
        // and no Save control composes anywhere in the app shell either
        rule.onNodeWithTag("calcCollapse").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTextField").fetchSemanticsNodes().isEmpty() &&
                rule.onAllNodesWithTag("calcCollapsed").fetchSemanticsNodes().isEmpty()
        }
        assertEquals(
            "no footer Save under any form (the form is gone)",
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
        // a line to make it payable: rooms 300 — NEXT commits AND saves
        // (v1.6.47: the keypad's enter is the plain newline now, so the
        // title's persistence test rides the Next button)
        toNumMode()
        listOf("3", "0", "0").forEach { key(it) }
        rule.onNodeWithTag("calcBtnNext").performClick()
        rule.waitForIdle()
        awaitLine(0)

        // the entry carries the title (Next saved it)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.runOnUiThread { AppStore.state.entries.any { it.calcTitle == "march rooms" } }
        }
        // v1.6.49 — there is no collapsed band anymore (the form is gone,
        // report #7): the TITLE FIELD itself sits under the header with the
        // typed title in it, and the export heads with it
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTitleField").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "the title field must carry the typed title",
            rule.onAllNodes(hasText("march rooms", substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
        // export: the title heads the copy
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
