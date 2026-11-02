package com.callmony

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.ui.kit.HostSheets
import com.callmony.ui.kit.RootBottomOverlays
import com.callmony.ui.kit.RootFab
import com.callmony.ui.kit.Toasts
import com.callmony.ui.kit.Confirm
import com.callmony.ui.overlays.calcFontSizesOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.49 — regression battery for the ten-report calculator + ledger
   round (the canvas-side reports):

     1. "the ability to change the individual currency by clicking the
        currency that is there bisede the total and also for the sum
        total" → the SUM's chip writes the entry's own calcCurrency, a
        committed line's chip rewrites that line's currency (riding the
        save as the " ~USD" marker), and the active line's chip rides
        its commit.
     2. "make the copy and the x btn in the row of the individual total"
        → the copy and delete buttons sit ON the line's total row, and
        the active line's copy sits on its live-total row.
     3. "the individual total font size should be smaller as the text and
        numbers… make the font size of the date smaller and lighter…
        why the individual calculation has so much padding on the left"
        → the date node renders SMALLER than the total; the ledger's
        left rail is tight (the prefix column starts near the canvas
        edge); the live total row indents past the prefix (aligned with
        the field above it).
     4. "make the sum total row very compact… why the persontage in the
        cercle says 100 when there is no any payment" → the ring shrank
        to 30dp and counts what is PAID (0% with nothing paid).
     5. "when it got locked, the line disappeared, it just one line why"
        → the enter key's break survives the LOCK, the SAVE (⏎ marker)
        and the REOPEN as a real visual line break.
     6. "put the title input under the header and i want the option to
        change the font size and put the font size icon on the header"
        → the title field composes below the header band; the Aa icon
        on the header opens the S/M/L picker and the choice rides the
        entry as calcFontSize.
     9. "make the entires list cards longer verticaly so it's shows most
        of it's calculation" → the ledger card previews up to four of
        the entry's own calculation lines (pure cases in CalcNoteTest
        pin the ⏎ grammar; here the card shows the whole math).
    10. "why there is no a way to update payment" → tapping a recorded
        payment in the payments modal opens the SAME sheet as an UPDATE
        form; the save rewrites the payment IN PLACE.

   Reports #7 (the edit-entry form's deletion) and #8 (the contact
   ledger's kind chooser) are pinned by the rewritten v1.6.38/39 and
   ContactLedgerV1649 batteries. */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EntryEditCalcV1649Test {

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
        // the currency batteries write non-default settings — they MUST go
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
        // the keypad re-arms asynchronously after a sheet closes (wantPad)
        // — wait for the key before clicking it
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcKey:$k").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("calcKey:$k").performClick()
        rule.waitForIdle()
    }

    private fun enter() {
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcKeyEnter").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("calcKeyEnter").performClick()
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

    private fun leaveEditor() {
        repeat(4) {
            if (rule.onAllNodesWithTag("calcBtnNext").fetchSemanticsNodes().isEmpty() &&
                rule.onAllNodesWithTag("calcKey:1").fetchSemanticsNodes().isEmpty()
            ) return@repeat
            rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
            rule.waitForIdle()
        }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcBtnNext").fetchSemanticsNodes().isEmpty()
        }
    }

    /** vertical-band overlap: two nodes sit BESIDE each other (same row) */
    private fun beside(aTop: Float, aHeight: Int, bTop: Float, bHeight: Int): Boolean {
        val aBot = aTop + aHeight
        val bBot = bTop + bHeight
        return aTop < bBot && bTop < aBot
    }

    /* 1 — THE CURRENCY BESIDE EVERY TOTAL IS TAPPABLE (report #1): the
       SUM's chip writes the entry's own currency; a committed line's chip
       rewrites that LINE's currency, riding the save as the " ~EUR"
       marker; the active line's chip rides its own commit */
    @Test
    fun theCurrencyBesideEveryTotalIsTappableAndScoped() {
        ready()
        openEditor()
        toNumMode()
        listOf("6", "1", "0").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("= 610", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // the SUM's chip — the entry's own display currency
        rule.onNodeWithTag("calcTotalCur").performClick()
        rule.waitForIdle()
        val usd = rule.onAllNodes(hasText("USD $") and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { usd.fetchSemanticsNodes().isNotEmpty() }
        usd[0].performClick()
        rule.waitForIdle()
        // the chip now renders the dollar symbol
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTotalCur").fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodes(hasText("$")).fetchSemanticsNodes().isNotEmpty()
        }

        // commit the line — the save carries the entry's sum currency
        next()
        awaitLine(0)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.runOnUiThread {
                AppStore.state.entries.any { it.calcCurrency == "USD" && it.amount == 610.0 }
            }
        }

        // the LINE's OWN chip — that line's currency, not the entry's
        rule.onNodeWithTag("calcLineCur:0").performClick()
        rule.waitForIdle()
        val eur = rule.onAllNodes(hasText("EUR €") and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { eur.fetchSemanticsNodes().isNotEmpty() }
        eur[0].performClick()
        rule.waitForIdle()
        // the line's chip now reads the euro symbol
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("€")).fetchSemanticsNodes().isNotEmpty()
        }
        // another Next commits + saves again — the line's currency rides
        // the note as the " ~EUR" marker
        listOf("5", "0").forEach { key(it) }
        next()
        awaitLine(1)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.runOnUiThread {
                AppStore.state.entries.any {
                    it.amount == 660.0 && it.exprNote?.contains("~EUR") == true
                }
            }
        }

        // the ACTIVE line's chip rides its own commit the same way
        rule.onNodeWithTag("calcActiveCur").performClick()
        rule.waitForIdle()
        val gbp = rule.onAllNodes(hasText("GBP £") and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { gbp.fetchSemanticsNodes().isNotEmpty() }
        gbp[0].performClick()
        rule.waitForIdle()
        listOf("2", "5").forEach { key(it) }
        next()
        awaitLine(2)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.runOnUiThread {
                AppStore.state.entries.any {
                    it.amount == 685.0 && it.exprNote?.contains("~GBP") == true
                }
            }
        }
    }

    /* 2 — COPY AND ✕ LIVE ON THE INDIVIDUAL TOTAL ROW (report #2): the
       committed line's total, copy and delete share ONE row, and the copy
       puts the line (with its total) on the clipboard */
    @Test
    fun copyAndDeleteLiveOnTheIndividualTotalRow() {
        ready()
        openEditor()
        toNumMode()
        listOf("6", "2", "0").forEach { key(it) }
        next()
        awaitLine(0)

        // the three nodes exist…
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLineTotal:0", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithTag("calcLineCopy:0", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithTag("calcLineDelete:0", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        // …ON THE SAME ROW as the line's total (vertical bands overlap)
        val total = rule.onNodeWithTag("calcLineTotal:0", useUnmergedTree = true).fetchSemanticsNode()
        val copy = rule.onNodeWithTag("calcLineCopy:0", useUnmergedTree = true).fetchSemanticsNode()
        val del = rule.onNodeWithTag("calcLineDelete:0", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(
            "the copy button must sit on the individual total's row " +
                "(total y=${total.positionInRoot.y} h=${total.size.height}, copy y=${copy.positionInRoot.y} h=${copy.size.height})",
            beside(total.positionInRoot.y, total.size.height, copy.positionInRoot.y, copy.size.height),
        )
        assertTrue(
            "the ✕ button must sit on the individual total's row",
            beside(total.positionInRoot.y, total.size.height, del.positionInRoot.y, del.size.height),
        )

        // the ACTIVE line's copy sits on ITS live-total row too (a fresh
        // number on the active line makes the live total compose)
        listOf("5", "0").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("= 50", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val live = rule.onNodeWithTag("calcTotal").fetchSemanticsNode()
        val liveCopy = rule.onNodeWithTag("calcActiveCopy", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(
            "the active line's copy must sit on its live-total row",
            beside(live.positionInRoot.y, live.size.height, liveCopy.positionInRoot.y, liveCopy.size.height),
        )

        // tapping the line's copy puts the line + its total on the clipboard
        rule.onNodeWithTag("calcLineCopy:0").performClick()
        rule.waitForIdle()
        val clip = rule.runOnUiThread {
            val cm = rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.getItemAt(0)?.text?.toString()
        }
        checkNotNull(clip) { "the line's copy button must put the line on the clipboard" }
        assertTrue("the copied line must carry its calculation — got: $clip", clip.contains("620"))
        assertTrue("the copied line must carry its total — got: $clip", clip.contains("="))
    }

    /* 3 — THE TOTAL's SIZE, THE DATE's SIZE, THE LEFT RAIL (report #3):
       the date renders SMALLER than the line's total; the ledger column
       starts near the canvas edge (no wasted left rail); the live total
       row aligns under the field (indents past the prefix column) */
    @Test
    fun theDateIsSmallerTheRailIsTightAndTheLiveTotalAligns() {
        ready()
        openEditor()
        toNumMode()
        listOf("6", "3", "0").forEach { key(it) }
        next()
        awaitLine(0)

        // the DATE node exists on the total row and renders SMALLER than
        // the total itself (11sp vs the line text's own size)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLineDate:0", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        val total = rule.onNodeWithTag("calcLineTotal:0", useUnmergedTree = true).fetchSemanticsNode()
        val date = rule.onNodeWithTag("calcLineDate:0", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(
            "the date must render smaller than the total " +
                "(total h=${total.size.height}, date h=${date.size.height})",
            date.size.height < total.size.height,
        )

        // the LEFT RAIL is tight: the line's prefix column starts within a
        // hair of the screen edge (v1.6.49 moved the ledger in 14dp → the
        // text starts ~32dp from the screen edge, was ~64dp — the full-
        // screen canvas starts at the window's left edge)
        val prefix = rule.onNodeWithTag("calcLinePrefix:0", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(
            "the individual calculation's left rail must be tight — got ${prefix.positionInRoot.x}px",
            prefix.positionInRoot.x < 60f,
        )

        // the LIVE TOTAL aligns with the field above it: it indents past
        // the prefix column exactly like the field does (a fresh number on
        // the active line makes the live total compose)
        listOf("5", "0").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("= 50", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val activePrefix = rule.onNodeWithTag("calcActivePrefix", useUnmergedTree = true).fetchSemanticsNode()
        val live = rule.onNodeWithTag("calcTotal").fetchSemanticsNode()
        assertTrue(
            "the live total must align under the field (past the prefix column) " +
                "(prefix x=${activePrefix.positionInRoot.x}, live x=${live.positionInRoot.x})",
            live.positionInRoot.x > activePrefix.positionInRoot.x,
        )
    }

    /* 4 — THE SUM TOTAL ROW IS COMPACT AND THE RING COUNTS WHAT IS PAID
       (report #4): the ring shrank to 30dp, and with NO payment it reads
       0% — never the old "100 with no any payment" */
    @Test
    fun theSumRowIsCompactAndTheRingReadsZeroWhenNothingIsPaid() {
        ready()
        openEditor()
        toNumMode()
        listOf("6", "4", "0").forEach { key(it) }
        next()
        awaitLine(0)

        // the ring exists and reads 0% with nothing paid
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcPayRing").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "with no payment the circle must read 0% — it counts the paid share",
            rule.onAllNodes(hasText("0%")).fetchSemanticsNodes().isNotEmpty(),
        )

        // the ring shrank to 30dp (420dpi → ~79px; the old 40dp ring was
        // ~105px)
        val ring = rule.onNodeWithTag("calcPayRing").fetchSemanticsNode()
        assertTrue(
            "the pay ring must be the compact 30dp cut — got ${ring.size.height}px",
            ring.size.height in 60..95,
        )
    }

    /* 5 — THE ENTER KEY's BREAK SURVIVES THE LOCK AND THE REOPEN
       (report #5): a calculation typed across two visual lines STAYS two
       lines when it locks, through the save, and on the reopen */
    @Test
    fun theEnterKeyBreakSurvivesTheLockAndTheReopen() {
        ready()
        openEditor()
        toNumMode()
        listOf("2", "8", "5").forEach { key(it) }
        enter()
        listOf("8", "0").forEach { key(it) }
        // the SAME individual calculation keeps calculating across the break
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("= 365", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        // commit — the locked line keeps BOTH visual lines
        next()
        awaitLine(0)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("285 +\n80", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // the saved grammar carries the ⏎ marker (the break, save-safe)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.runOnUiThread {
                AppStore.state.entries.any {
                    it.amount == 365.0 && it.exprNote?.contains("\u23CE") == true
                }
            }
        }
        val entryId = rule.runOnUiThread {
            AppStore.state.entries.first { it.amount == 365.0 && it.exprNote?.contains("\u23CE") == true }.id
        }

        // REOPEN — the line is STILL two visual lines (the user's exact
        // report: after the lock "the line disappeared, it just one line")
        leaveEditor()
        rule.runOnUiThread { UiStore.open(Overlay.EntryEdit(entryId, "u:me")) }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("285 +\n80", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /* 6 — THE TITLE UNDER THE HEADER + THE FONT-SIZE ICON ON THE HEADER
       (report #6): the title field composes BELOW the header band, the
       Aa icon sits ON it, and the S/M/L choice rides the entry */
    @Test
    fun theTitleSitsUnderTheHeaderAndTheFontIconScalesTheCanvas() {
        ready()
        openEditor()

        // the title field composes BELOW the header band (its top edge is
        // past the header's own controls)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTitleField").fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithTag("calcCollapse").fetchSemanticsNodes().isNotEmpty()
        }
        val title = rule.onNodeWithTag("calcTitleField").fetchSemanticsNode()
        val close = rule.onNodeWithTag("calcCollapse").fetchSemanticsNode()
        assertTrue(
            "the title input must sit UNDER the header " +
                "(close y=${close.positionInRoot.y} h=${close.size.height}, title y=${title.positionInRoot.y})",
            title.positionInRoot.y > close.positionInRoot.y + close.size.height - 4f,
        )

        // the FONT-SIZE icon sits ON the header row (same vertical band as
        // the close button)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcFontBtn").fetchSemanticsNodes().isNotEmpty()
        }
        val font = rule.onNodeWithTag("calcFontBtn").fetchSemanticsNode()
        assertTrue(
            "the font-size icon must sit on the header row",
            beside(font.positionInRoot.y, font.size.height, close.positionInRoot.y, close.size.height),
        )

        // the picker — three sizes; L rides the entry as calcFontSize
        rule.onNodeWithTag("calcFontBtn").performClick()
        rule.waitForIdle()
        val lChip = rule.onAllNodes(hasText("L 16sp", substring = true) and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { lChip.fetchSemanticsNodes().isNotEmpty() }
        lChip[0].performClick()
        rule.waitForIdle()
        toNumMode()
        listOf("4", "1", "0").forEach { key(it) }
        next()
        awaitLine(0)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.runOnUiThread {
                AppStore.state.entries.any { it.amount == 410.0 && it.calcFontSize == "l" }
            }
        }
    }

    /* 6b — the three cuts, pinned pure (the battery's own numbers) */
    @Test
    fun theThreeFontCuts() {
        assertEquals(com.callmony.ui.overlays.CalcFontSizes(16, 13), calcFontSizesOf("s"))
        assertEquals(com.callmony.ui.overlays.CalcFontSizes(18, 14), calcFontSizesOf("m"))
        assertEquals(com.callmony.ui.overlays.CalcFontSizes(20, 16), calcFontSizesOf("l"))
        // an absent/unknown value decodes to the default cut
        assertEquals(com.callmony.ui.overlays.CalcFontSizes(18, 14), calcFontSizesOf(null))
        assertEquals(com.callmony.ui.overlays.CalcFontSizes(18, 14), calcFontSizesOf("junk"))
    }

    /* 9 — THE CARD SHOWS MOST OF ITS CALCULATION (report #9): a two-line
       entry previews BOTH lines on its ledger card — and a ⏎ line folds
       to one visual row of the preview */
    @Test
    fun theEntryCardShowsMostOfItsCalculation() {
        ready()
        val ids = rule.runOnUiThread {
            val twoLine = com.callmony.lib.uid()
            AppStore.saveEntry(
                com.callmony.model.LedgerEntry(
                    id = twoLine, ownerId = "general", type = "credit", kind = "credit",
                    amount = 550.0, date = com.callmony.lib.todayStr(),
                    exprNote = "rooms 3 × 110\ntransport 220",
                    createdAt = com.callmony.lib.now(), updatedAt = com.callmony.lib.now(),
                )
            )
            val broken = com.callmony.lib.uid()
            AppStore.saveEntry(
                com.callmony.model.LedgerEntry(
                    id = broken, ownerId = "general", type = "credit", kind = "credit",
                    amount = 490.0, date = com.callmony.lib.todayStr(),
                    exprNote = "415 +\u23CE75 @${com.callmony.lib.todayStr()}",
                    createdAt = com.callmony.lib.now(), updatedAt = com.callmony.lib.now(),
                )
            )
            listOf(twoLine, broken)
        }
        rule.waitForIdle()

        // BOTH lines of the two-line entry preview on the card
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("entryCardCalc:${ids[0]}", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        // the preview carries both calculation lines (maxLines = 4 now)
        val cfg = rule.onNodeWithTag("entryCardCalc:${ids[0]}", useUnmergedTree = true).fetchSemanticsNode().config
        val text = cfg[androidx.compose.ui.semantics.SemanticsProperties.Text].first().text
        assertTrue(
            "the card must show the whole calculation, both lines — got: $text",
            text.contains("rooms 3 × 110") && text.contains("transport 220"),
        )

        // the ⏎ break folds to a space in the card's preview (one visual row
        // of the four, never a raw marker)
        val brokenCfg = rule.onNodeWithTag("entryCardCalc:${ids[1]}", useUnmergedTree = true).fetchSemanticsNode().config
        val brokenText = brokenCfg[androidx.compose.ui.semantics.SemanticsProperties.Text].first().text
        assertEquals("the ⏎ break folds to a space on the card — got: $brokenText", "415 + 75", brokenText)
    }

    /* 10 — A RECORDED PAYMENT UPDATES IN PLACE (report #10): tapping a
       payment in the modal's history list opens the SAME sheet as an
       UPDATE form — prefilled, and the save rewrites that payment */
    @Test
    fun aRecordedPaymentUpdatesInPlace() {
        ready()
        openEditor()
        toNumMode()
        listOf("5", "2", "0").forEach { key(it) }
        next()
        awaitLine(0)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.runOnUiThread {
                AppStore.state.entries.any { it.amount == 520.0 && it.exprNote != null }
            }
        }

        // record 400 against the sum total
        rule.onNodeWithTag("calcTotalPay").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("ee.calcOriginal"), substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val payBtns = rule.onAllNodes(hasText(t("ee.calcPay"), substring = true) and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { payBtns.fetchSemanticsNodes().isNotEmpty() }
        payBtns[payBtns.fetchSemanticsNodes().size - 1].performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("lg.recordPayment"), substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val fields = rule.onAllNodes(hasSetTextAction())
        rule.waitUntil(timeoutMillis = 15000) { fields.fetchSemanticsNodes().size >= 2 }
        fields[fields.fetchSemanticsNodes().size - 2].performTextReplacement("400")
        rule.waitForIdle()
        val saves = rule.onAllNodes(hasText(t("act.save")) and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { saves.fetchSemanticsNodes().isNotEmpty() }
        saves[saves.fetchSemanticsNodes().size - 1].performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.runOnUiThread {
                AppStore.state.entries.any { it.amount == 520.0 && it.payments.size == 1 && it.payments.first().amount == 400.0 }
            }
        }

        // recording closed the modal (makePaymentSheet nulls payModal) —
        // REOPEN it from the sum total's Pay chip: the recorded payment
        // now lists in its history
        rule.onNodeWithTag("calcTotalPay").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcModalPayHist:0").fetchSemanticsNodes().isNotEmpty()
        }
        // tap the RECORDED payment's row: the SAME sheet reopens as an
        // UPDATE form, prefilled with the 400
        rule.onNodeWithTag("calcModalPayHist:0").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("lg.updatePayment"), substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        // the amount field carries the recorded 400 — rewrite it to 450
        val editFields = rule.onAllNodes(hasSetTextAction())
        rule.waitUntil(timeoutMillis = 15000) { editFields.fetchSemanticsNodes().size >= 2 }
        editFields[editFields.fetchSemanticsNodes().size - 2].performTextReplacement("450")
        rule.waitForIdle()
        val updateBtns = rule.onAllNodes(hasText(t("lg.updatePayment")) and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { updateBtns.fetchSemanticsNodes().isNotEmpty() }
        updateBtns[updateBtns.fetchSemanticsNodes().size - 1].performClick()
        rule.waitForIdle()

        // the payment REWROTE IN PLACE: still ONE payment, the SAME id, the
        // new amount
        rule.waitUntil(timeoutMillis = 15000) {
            rule.runOnUiThread {
                AppStore.state.entries.any {
                    it.amount == 520.0 && it.payments.size == 1 && it.payments.first().amount == 450.0
                }
            }
        }
        val payment = rule.runOnUiThread {
            AppStore.state.entries.first { it.amount == 520.0 && it.payments.isNotEmpty() }.payments.first()
        }
        assertEquals("cash", payment.method)
    }

    private fun entryAmount(amount: Double): Int = rule.runOnUiThread {
        AppStore.state.entries.count { it.amount == amount }
    }
}
