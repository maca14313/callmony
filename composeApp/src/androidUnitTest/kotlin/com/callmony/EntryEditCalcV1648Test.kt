package com.callmony

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.48 — regression battery for the seven-report calculator round:

     1. "the ability to change the currency" → the canvas header's
        currency chip opens the compact picker; one tap re-renders every
        amount in the calculator (money()/compactMoney() default to the
        LIVE setting since v1.6.48).
     2. "the total number should be beside the 'TOTAL' label not under it
        in the sum total" → label + amount share ONE row (vertical bands
        overlap; v1.6.47 stacked them).
     3. "in the individual total, the date should sit beside it" → the
        commit date rides the line's OWN total row, not the text row.
     4. "i wrote the 555555555 in the number mode, and i wrote the
        dsssssss in text mode, so it should calculate the 555555555 even
        if there is no space between them" → the MODE-SPLIT pre-parse
        (applyModeSplit) — pure cases pinned in CalcNoteTest; here the
        glued line calculates live, commits, and saves split.
     5. "it's total should be equal in font size as it the others, wether
        it is locked or not" → the committed line's total node and the
        active line's live total node measure the same height.
     6. (the enter-key round-three contract lives in the rewritten
        v1.6.46/v1.6.47 batteries.)
     7. (the lock icon's return + inertness lives in the rewritten
        v1.6.47 battery.) */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EntryEditCalcV1648Test {

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
        com.callmony.ui.kit.RootFab.content.value = null
        Confirm.req.value = null
        Toasts.items.clear()
        // the currency battery writes a non-default setting — it MUST go
        // back (money() is live for every other class in this JVM)
        AppStore.state.settings = AppStore.state.settings.copy(language = "en", currency = "ETB")
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

    private fun next() {
        rule.onNodeWithTag("calcBtnNext").performClick()
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

    /** vertical-band overlap: two nodes sit BESIDE each other (same row) */
    private fun beside(
        aTop: Float, aHeight: Int, bTop: Float, bHeight: Int,
    ): Boolean {
        val aBot = aTop + aHeight
        val bBot = bTop + bHeight
        return aTop < bBot && bTop < aBot
    }

    /* 1 — THE CURRENCY: the header chip opens the picker, one tap lands
       the setting, and every amount in the calculator re-renders in the
       chosen currency the same frame (735: an amount no other battery
       writes). */
    @Test
    fun theCurrencyChipChangesEveryAmountInTheCalculator() {
        ready()
        openEditor()

        // the chip sits in the canvas header showing the live symbol
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcCurrencyBtn").fetchSemanticsNodes().isNotEmpty()
        }
        // the picker sheet — the same eight currencies Settings carries
        rule.onNodeWithTag("calcCurrencyBtn").performClick()
        rule.waitForIdle()
        val usd = rule.onAllNodes(hasText("USD $") and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { usd.fetchSemanticsNodes().isNotEmpty() }
        usd[0].performClick()
        rule.waitForIdle()

        // the setting landed
        rule.waitUntil(timeoutMillis = 15000) {
            rule.runOnUiThread { AppStore.state.settings.currency == "USD" }
        }
        // the chip now reads the dollar symbol
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("$")).fetchSemanticsNodes().isNotEmpty()
        }
        // and the calculator speaks dollars: the live total and the sum
        // total render the number, and the CURRENCY CHIPS read "$" beside
        // them — not the old hard-coded birr (money() defaults to the LIVE
        // setting since v1.6.48; v1.6.49 split the symbol into its own
        // tappable node)
        toNumMode()
        listOf("7", "3", "5").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("= 735", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTotalCur").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "the sum total's currency chip follows the live currency",
            rule.onAllNodes(hasText("$", substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /* 2 — THE SUM TOTAL: the number sits BESIDE the "TOTAL" label — their
       vertical bands overlap on one row (v1.6.47 stacked the amount UNDER
       the label; 426: an amount no other battery writes). */
    @Test
    fun theTotalNumberSitsBesideTheTotalLabel() {
        ready()
        openEditor()
        toNumMode()
        listOf("4", "2", "6").forEach { key(it) }
        next()
        awaitLine(0)

        val amount = rule.onNodeWithTag("calcTotalAmount").fetchSemanticsNode()
        val label = rule.onAllNodes(hasText(t("ee.calcTotal"))).fetchSemanticsNodes().first()
        assertTrue(
            "the total number must sit BESIDE the TOTAL label, not under it " +
                "(label y=${label.positionInRoot.y} h=${label.size.height}, amount y=${amount.positionInRoot.y} h=${amount.size.height})",
            beside(amount.positionInRoot.y, amount.size.height, label.positionInRoot.y, label.size.height),
        )
        // the amount rides the SAME row as the label — never below it
        assertTrue(
            "the amount's band starts above the label's bottom edge",
            amount.positionInRoot.y < label.positionInRoot.y + label.size.height,
        )
    }

    /* 3 — THE INDIVIDUAL TOTAL's DATE: the commit date sits BESIDE the
       line's own total — one band (v1.6.47 carried it beside the
       calculation text; 417: an amount no other battery writes). */
    @Test
    fun theIndividualTotalCarriesItsDateBesideIt() {
        ready()
        openEditor()
        toNumMode()
        listOf("4", "1", "7").forEach { key(it) }
        next()
        awaitLine(0)

        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLineTotal:0", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLineDate:0", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        val total = rule.onNodeWithTag("calcLineTotal:0", useUnmergedTree = true).fetchSemanticsNode()
        val date = rule.onNodeWithTag("calcLineDate:0", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(
            "the date must sit BESIDE the individual total, not beside the text " +
                "(total y=${total.positionInRoot.y} h=${total.size.height}, date y=${date.positionInRoot.y} h=${date.size.height})",
            beside(total.positionInRoot.y, total.size.height, date.positionInRoot.y, date.size.height),
        )
        // and the total row sits BELOW the calculation text — its own band
        val line = rule.onNodeWithTag("calcLine:0").fetchSemanticsNode()
        assertTrue(
            "the total rides inside its line block",
            total.positionInRoot.y > line.positionInRoot.y,
        )
    }

    /* 4 — THE MODE-SPLIT, LIVE AND SAVED: "555555555" typed on the keypad,
       "dsssssss" glued straight after on the QWERTY (no space, exactly the
       user's report) — the keypad number still calculates, the words ride
       the label slot, and the split survives the save. */
    @Test
    fun gluedQwertyTextDoesNotKillTheKeypadNumbersCalculation() {
        ready()
        openEditor()
        toNumMode()
        listOf("5", "5", "5", "5", "5", "5", "5", "5", "5").forEach { key(it) }

        // switch to TEXT mode and glue the letters right onto the digits
        rule.onNodeWithTag("calcModeText").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTextField").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("calcTextField").performTextInput("dsssssss")
        rule.waitForIdle()

        // LIVE: the keypad-written number calculates even glued
        // ("what will be considered is in what mode it was") — v1.6.49:
        // the live total renders "= 555,555,555" with the currency on
        // its own chip beside it
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("= 555,555,555", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // COMMIT + SAVE through Next: the split rides the entry
        next()
        awaitLine(0)
        rule.waitUntil(timeoutMillis = 15000) { entryCount(555555555.0) == 1 }
        val savedExpr = rule.runOnUiThread {
            AppStore.state.entries.first { it.amount == 555555555.0 }.exprNote
        }
        assertEquals(
            "the glued line saves SPLIT — the number its own token, the word its label",
            "555555555 dsssssss",
            savedExpr?.replace(Regex("\\s*@\\d{4}-\\d{2}-\\d{2}"), ""),
        )
        // and the committed line's own total still reads the number
        // (v1.6.49: the number renders alone — the currency is its own
        // tappable chip beside it)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLineTotal:0", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "the committed line's own total reads 555,555,555",
            rule.onAllNodes(hasText("555,555,555")).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /* 5 — ONE FONT SIZE, LOCKED OR NOT: the committed line's total node
       and the active line's live total node measure (to within a hair of
       padding) the same height — v1.6.47 rendered the locked read-out at
       14sp under a 21sp live total; 541: an amount no other battery
       writes. */
    @Test
    fun theLineTotalsMatchTheLiveTotalFontSize() {
        ready()
        openEditor()
        toNumMode()
        listOf("5", "4", "1").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTotal").fetchSemanticsNodes().isNotEmpty()
        }
        val liveH = rule.onNodeWithTag("calcTotal").fetchSemanticsNode().size.height

        next()
        awaitLine(0)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLineTotal:0", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        val lockedH = rule.onNodeWithTag("calcLineTotal:0", useUnmergedTree = true)
            .fetchSemanticsNode().size.height

        assertTrue(
            "one font size for the individual total, locked or not (live=$liveH locked=$lockedH)",
            kotlin.math.abs(liveH - lockedH) <= 12,
        )
    }
}
