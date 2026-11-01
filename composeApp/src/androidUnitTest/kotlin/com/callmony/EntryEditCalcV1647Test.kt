package com.callmony

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.platform.Platform
import com.callmony.platform.UiPlatform
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
import androidx.compose.ui.unit.dp

/* v1.6.47 — regression battery for the six-report round:

     1. "the inter btn is not the next btn it is the normal inter btn like
        in the netiv keyboard and in its row add the space key too" → the
        keypad's bottom row is the native keyboard's: a WIDE SPACE bar
        with a normal-width ENTER key on the right.
        v1.6.48, ROUND THREE ("the reason i wanted the inter btn in the
        number keypad is to go to next line in the same individual
        calculation, just like the netiv inter btn do, not to do what the
        next btn do"): ENTER now lands a real NEWLINE INSIDE the active
        line — same individual calculation — and Next (and only Next)
        commits + saves.
     2. "sometimes the netiv keyboard opens up on the number mode why" →
        the window-level IME suppression arms with the keypad itself and
        lifts when it leaves.
     3. "show bullet or numbers at the start of each line that the user
        can change" → the prefix chip cycles • → 1. → none, every
        committed line + the active one carry it, and the choice rides
        the entry as calcPrefix.
     4. "when i add payment sometimes it adds it, sometimes it not, or it
        will not update it" → after a line unlock (the old freeze path),
        a payment recorded through the sheet shows up LIVE and SURVIVES
        the next save.
     5. "when tauch around the text, number toggle or the sum total, some
        wird different modals opens up. why" → the lock pill (whose tap
        flipped a now-meaningless state) is GONE; a plain tap on a line
        does nothing.
        v1.6.48 ("bring back the lock icon, it's height should be equal
        to the text height"): the lock is back as a plain TEXT-HEIGHT
        glyph — still inert; the tap assertions stand. */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EntryEditCalcV1647Test {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /* v1.6.47 — a recording wrapper around the REAL platform ui (whatever
       the app registered): every call is noted, everything forwards, so
       the IME-suppression wiring can be pinned without disturbing the
       theme hooks the shell also uses. */
    private class RecordingUi(private val base: UiPlatform) : UiPlatform by base {
        val calls = mutableListOf<Boolean>()
        override fun setImeSuppressed(suppress: Boolean) {
            calls.add(suppress)
        }
    }

    private var origUi: UiPlatform? = null
    private var recorder: RecordingUi? = null

    @Before
    fun freshApp() {
        UiStore.closeAll()
        UiStore.tabInternal = "general"
        UiStore.locked = false
        origUi = Platform.ui
        recorder = RecordingUi(Platform.ui).also { Platform.ui = it }
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
        AppStore.state.settings = AppStore.state.settings.copy(language = "en")
        recorder = null
        origUi?.let { Platform.ui = it }
        origUi = null
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

    private fun space() {
        rule.onNodeWithTag("calcKeySpace").performClick()
        rule.waitForIdle()
    }

    private fun enter() {
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

    private fun entryCount(amount: Double): Int = rule.runOnUiThread {
        AppStore.state.entries.count { it.amount == amount }
    }

    /** back out of the editor entirely (keypad → canvas → sheet), waiting
     *  for the editor to really leave composition before returning */
    private fun leaveEditor() {
        repeat(4) {
            if (rule.onAllNodesWithTag("calcBtnNext").fetchSemanticsNodes().isEmpty() &&
                rule.onAllNodesWithTag("calcCollapsed").fetchSemanticsNodes().isEmpty()
            ) return
            rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
            rule.waitForIdle()
        }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcBtnNext").fetchSemanticsNodes().isEmpty() &&
                rule.onAllNodesWithTag("calcCollapsed").fetchSemanticsNodes().isEmpty()
        }
    }

    /* 1 — v1.6.48, REWRITTEN for the round-three enter contract: ENTER
       lands a NEWLINE INSIDE THE SAME individual calculation (exactly the
       native enter) — it neither commits nor saves: no ledger line appears
       and the store stays empty while the editor is open. The SPACE key
       beside it still inserts a real space (adjacent numbers fold as +,
       so "185 40" reads 225); the SAME line folds across the newline
       ("185 40\n5" still reads 230 — one calculation); Next commits the
       one line and saves. Harness note: 185 / 225 / 230 are amounts no
       other battery writes (the store is a JVM singleton across classes). */
    @Test
    fun theEnterKeyNewlinesInsideTheSameCalculation() {
        ready()
        openEditor()
        toNumMode()

        // 185 ␣ 40 on ONE line — the space lands, adjacent numbers fold as +
        listOf("1", "8", "5").forEach { key(it) }
        space()
        listOf("4", "0").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("225 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // ENTER — the native newline: a REAL \n lands in the active line's
        // text (the num field's own view holds it), and NOTHING commits
        enter()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.runOnUiThread {
                com.callmony.ui.components.NoImeFieldProbe.last?.text?.toString()?.contains("\n") == true
            }
        }
        assertEquals(
            "the enter key must not commit — the line stays active",
            0,
            rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().size,
        )

        // ⌫ over the newline removes JUST the newline (native backspace),
        // then it comes back for the fold check below
        rule.onNodeWithTag("calcKey:⌫").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.runOnUiThread {
                com.callmony.ui.components.NoImeFieldProbe.last?.text?.toString() == "185 40"
            }
        }
        enter()

        // the SAME individual calculation continues on the next visual
        // line: 5 more reads 230 live
        listOf("5").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("230 Br", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        Thread.sleep(300) // any stray save pulse would land here — none may
        rule.waitForIdle()
        assertEquals(
            "the enter key must NOT persist by itself — it is the newline, not Next",
            0,
            entryCount(230.0),
        )

        // NEXT commits the ONE line (break folded to a space in the
        // grammar) and saves — the store finally holds it
        next()
        awaitLine(0)
        rule.waitUntil(timeoutMillis = 15000) { entryCount(230.0) == 1 }
        val savedExpr = rule.runOnUiThread {
            AppStore.state.entries.first { it.amount == 230.0 }.exprNote
        }
        assertTrue(
            "the one line folds its newline to a space — got: $savedExpr",
            savedExpr?.replace(Regex("\\s*@\\d{4}-\\d{2}-\\d{2}"), "") == "185 + 40 + 5",
        )
    }

    /* 1b — NEXT is still the one commit+save key on the canvas (v1.6.46
       report #3's contract, now ENTER-free) */
    @Test
    fun nextStillCommitsAndSaves() {
        ready()
        openEditor()
        toNumMode()
        listOf("8", "8", "8").forEach { key(it) }
        next()
        awaitLine(0)
        rule.waitUntil(timeoutMillis = 15000) { entryCount(888.0) == 1 }
        // a second line through NEXT updates the SAME entry
        listOf("2").forEach { key(it) }
        next()
        awaitLine(1)
        rule.waitUntil(timeoutMillis = 15000) { entryCount(890.0) == 1 }
    }

    /* 2 — the window-level IME suppression arms with the keypad and lifts
       when it leaves (the "sometimes the native keyboard opens up on the
       number mode" firewall) */
    @Test
    fun imeSuppressionArmsWithTheKeypadAndLiftsWithIt() {
        ready()
        openEditor()
        toNumMode()
        rule.waitUntil(timeoutMillis = 15000) { recorder!!.calls.contains(true) }
        // toggling to text mode lifts the suppression (the QWERTY must work)
        rule.onNodeWithTag("calcModeText").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) { recorder!!.calls.last() == false }
        // arming numbers again re-suppresses
        toNumMode()
        rule.waitUntil(timeoutMillis = 15000) { recorder!!.calls.last() == true }
        // and leaving the editor never leaks the suppression
        leaveEditor()
        rule.waitUntil(timeoutMillis = 15000) { recorder!!.calls.last() == false }
    }

    /* 3 — the prefix: bullets by default, the chip cycles to numbers and
       to none, the active line carries its own prefix, and the choice
       rides the entry as calcPrefix */
    @Test
    fun thePrefixChipCyclesBulletNumberNoneAndPersists() {
        ready()
        openEditor()
        toNumMode()

        // default: the ACTIVE line already carries its bullet
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcActivePrefix").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "the active line's default prefix is a bullet",
            rule.onAllNodes(hasText("•")).fetchSemanticsNodes().isNotEmpty(),
        )

        // commit a line — it leads with its bullet too (430: an amount no
        // other battery writes). v1.6.48: Next commits now — the enter key
        // is the newline INSIDE the same calculation, never a commit
        listOf("4", "3", "0").forEach { key(it) }
        next()
        awaitLine(0)
        // harness lesson (the v1.6.42 one, again): the line row is
        // combinedClickable, so descendant tags MERGE into it — the
        // prefix needs the UNMERGED tree
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLinePrefix:0", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // tap the chip: • → 1. — line 0 becomes "1.", the active line "2."
        rule.onNodeWithTag("calcPrefixBtn").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("1.")).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "the active line carries the NEXT number while numbering",
            rule.onAllNodes(hasText("2.")).fetchSemanticsNodes().isNotEmpty(),
        )

        // tap again: numbers → none — the prefixes leave entirely
        rule.onNodeWithTag("calcPrefixBtn").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLinePrefix:0", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        assertEquals(
            "none mode must also drop the active line's prefix",
            0,
            rule.onAllNodesWithTag("calcActivePrefix").fetchSemanticsNodes().size,
        )

        // the choice persists: back out → the entry carries calcPrefix=none
        leaveEditor()
        rule.waitUntil(timeoutMillis = 15000) { entryCount(430.0) == 1 }
        val prefix = rule.runOnUiThread {
            AppStore.state.entries.first { it.amount == 430.0 }.calcPrefix
        }
        assertEquals("none", prefix)
    }

    /* 4 — THE PAYMENTS FREEZE (report #4): after a line unlock (the exact
       path that used to snapshot the payment list), a payment recorded
       through the sheet shows up on the total the same frame AND SURVIVES
       the next save — the old wipe took one Next to eat it. */
    @Test
    fun paymentsStayLiveAndSurviveSavesAfterALineUnlock() {
        ready()
        openEditor()
        toNumMode()
        listOf("3", "0", "5").forEach { key(it) }
        next()
        awaitLine(0)
        // harness note: 305 / 315 — amounts no other battery writes (the
        // store is a JVM singleton, and 300 is already poisoned)
        rule.waitUntil(timeoutMillis = 15000) { entryCount(305.0) == 1 }
        val id = rule.runOnUiThread { AppStore.state.entries.first { it.amount == 305.0 }.id }

        // THE FREEZE PATH: long-press the line into editing (the old code
        // snapshotted the payments here), then commit it back unchanged
        rule.onNodeWithTag("calcLine:0").performTouchInput { longClick() }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTextField").fetchSemanticsNodes().isNotEmpty()
        }
        toNumMode()
        next()
        awaitLine(0)
        rule.waitForIdle()

        // pay the total through the modal → the record sheet
        rule.onNodeWithTag("calcTotalPay").performClick()
        rule.waitForIdle()
        val payBtns = rule.onAllNodes(hasText(t("ee.calcPay"), substring = true) and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { payBtns.fetchSemanticsNodes().isNotEmpty() }
        payBtns[payBtns.fetchSemanticsNodes().size - 1].performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText(t("lg.recordPayment"), substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val saves = rule.onAllNodes(hasText(t("act.save")) and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { saves.fetchSemanticsNodes().isNotEmpty() }
        saves[saves.fetchSemanticsNodes().size - 1].performClick()
        rule.waitForIdle()

        // LIVE (the old freeze showed nothing): the total's paid line updates
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTotalPaidLine").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "the payment must show on the total right after the sheet",
            rule.onAllNodes(hasText("${t("lg.paid")} 305 Br", substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "the pay ring must read 0% once fully paid",
            rule.onAllNodes(hasText("0%")).fetchSemanticsNodes().isNotEmpty(),
        )

        // AND it must SURVIVE the next save (the old wipe ate it here)
        listOf("1", "0").forEach { key(it) }
        next()
        awaitLine(1)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.runOnUiThread { AppStore.state.entries.find { it.id == id }?.amount == 315.0 }
        }
        val paymentsAfterSave = rule.runOnUiThread {
            AppStore.state.entries.find { it.id == id }?.payments?.size
        }
        assertEquals(
            "the recorded payment must survive the post-unlock save (the v1.6.47 freeze/wipe)",
            1,
            paymentsAfterSave,
        )
    }

    /* 5 — v1.6.48, REWRITTEN ("bring back the lock icon, it's height
       should be equal to the text height"): the lock is back as a plain
       TEXT-HEIGHT glyph at the head of every committed line (and an OPEN
       one on the active line) — INERT, the v1.6.47 lesson stands: a plain
       tap on a line opens nothing, only a deliberate long-press edits. */
    @Test
    fun theLockIconIsBackInertAndATapOnALineDoesNothing() {
        ready()
        openEditor()
        toNumMode()
        listOf("6", "0", "0").forEach { key(it) }
        next()
        awaitLine(0)

        // the lock icon rides the committed line (unmerged tree — it sits
        // inside the combinedClickable row) …
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLineLock:0", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        // … its height never exceeds the line's own text height (report #7:
        // "it's height should be equal to the text height" — the 30dp pill
        // was the bulk; the prefix column's text is the yardstick)
        val lockH = rule.onNodeWithTag("calcLineLock:0", useUnmergedTree = true)
            .fetchSemanticsNode().size.height
        val textH = rule.onAllNodesWithTag("calcLinePrefix:0", useUnmergedTree = true)
            .fetchSemanticsNodes().first().size.height
        assertTrue(
            "the lock icon must never stand taller than the line's text (lock=$lockH text=$textH)",
            lockH <= textH,
        )
        // … it carries NO click action of its own (inert — the v1.6.47
        // lesson: tapping around the text must never do anything)
        assertEquals(
            "the lock icon is a notice, not a control",
            0,
            rule.onAllNodes(hasTestTag("calcLineLock:0") and hasClickAction(), useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
        // and the ACTIVE line carries the OPEN lock beside its field
        assertTrue(
            "the active line carries the open lock",
            rule.onAllNodesWithTag("calcActiveLock", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )
        // a plain tap on the line commits no modal and pulls nothing down
        rule.onNodeWithTag("calcLine:0").performClick()
        rule.waitForIdle()
        assertEquals(
            "no confirm modal from a plain tap",
            0,
            rule.onAllNodes(hasText(t("ee.calcDeleteLineTitle"))).fetchSemanticsNodes().size,
        )
        assertEquals(
            "the line stays committed after a tap",
            1,
            rule.onAllNodesWithTag("calcLine:0").fetchSemanticsNodes().size,
        )
        assertFalse(rule.onAllNodesWithTag("calcLineLock:1").fetchSemanticsNodes().isNotEmpty())
    }
}
