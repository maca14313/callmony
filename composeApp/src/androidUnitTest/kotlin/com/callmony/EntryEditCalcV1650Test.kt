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
import com.callmony.ui.components.NoImeFieldProbe
import com.callmony.ui.kit.HostSheets
import com.callmony.ui.kit.RootBottomOverlays
import com.callmony.ui.kit.RootFab
import com.callmony.ui.kit.Toasts
import com.callmony.ui.kit.Confirm
import com.callmony.ui.overlays.BACKSPACE_REPEAT_DELAY_MS
import com.callmony.ui.overlays.BACKSPACE_REPEAT_MS
import com.callmony.ui.overlays.SPACE_REPEAT_DELAY_MS
import com.callmony.ui.overlays.SPACE_REPEAT_MS
import com.callmony.ui.overlays.keyRepeatTicks
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.50 — regression battery for the thirteen-report refinement round
   (the canvas-side reports):

     1. "the copy btn that is bised the pay btn in the sum total should
        copy all things in that canvas" → the sum row's copy carries the
        TITLE, every committed line with its total, the ACTIVE line with
        its live total, and the TOTAL block.
     2. "keep all 100% of it's functionality but let's call the next btn
        save btn" → the button reads SAVE and still commits + persists.
     4. "the text and calculations font size should be thw same when it is
        edited and when it is locked" → ONE size feeds the native num-mode
        field (its real textSize) and the locked lines (their measured
        height scales with the px choice); "it should be consistent if
        there is bullet, number or notting" → the prefix slot is always
        reserved at the same x in all three prefix modes.
     5. "the part where it shows how the calculation went should be in some
        contener and should be aligned with what upper it horizontaly" →
        the steps ride their own bordered container whose left edge lines
        up with the active field above it.
     7. "in the number keypad it should be very responsive fast… when i
        click the x delete btn it us not fast and if I press it longer it
        just delete one thing, not continew deleting" → one tap deletes
        exactly one character instantly (the semantics door), the space
        bar taps exactly one space, and the repeat schedule is pinned
        pure (native feel: 350ms + 55ms for ⌫, 420ms + 90ms for space).
     9. "why the bottom border of the title to far from the title" → the
        underline sits within ~2dp of the title text's box. */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EntryEditCalcV1650Test {

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

    private fun save() {
        rule.onNodeWithTag("calcBtnNext").performClick()
        rule.waitForIdle()
    }

    private fun awaitLine(i: Int) {
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcLine:$i").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun clipText(): String? = rule.runOnUiThread {
        val cm = rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.primaryClip?.getItemAt(0)?.text?.toString()
    }

    private fun activeFieldText(): String =
        rule.runOnUiThread { NoImeFieldProbe.last?.text?.toString() ?: "" }

    /* 1 — THE COPY BESIDE THE PAY BUTTON COPIES ALL THINGS ON THE CANVAS
       (report #1): title, committed line + its total, the ACTIVE line with
       its live total, and the TOTAL block all ride the clipboard */
    @Test
    fun theCopyBesidePayCarriesAllThingsOnTheCanvas() {
        ready()
        openEditor()

        // the canvas title
        rule.onNodeWithTag("calcTitleField").performTextReplacement("Rent")
        rule.waitForIdle()
        // a committed line — the NUMBERS come from the KEYPAD (numbers
        // typed on the QWERTY are annotations by design, v1.6.48), so the
        // line actually calculates: type the word in text mode, then the
        // digits in num mode
        rule.onNodeWithTag("calcTextField").performTextReplacement("rooms")
        rule.waitForIdle()
        toNumMode()
        listOf("3", "×", "1", "0", "0").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("= 300", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        save()
        awaitLine(0)
        // and a STILL-ACTIVE line (not committed — the old export dropped it)
        rule.onNodeWithTag("calcModeText").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTextField").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("calcTextField").performTextReplacement("transport")
        rule.waitForIdle()
        toNumMode()
        listOf("5", "0").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("= 50", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithTag("calcTotalCopy", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        val clip = clipText()
        checkNotNull(clip) { "the sum-total copy must reach the clipboard" }
        assertTrue("the copy must carry the TITLE — got: $clip", clip.contains("Rent"))
        assertTrue("the copy must carry the committed line — got: $clip", clip.contains("rooms 3 × 100"))
        assertTrue("the copy must carry the committed line's total — got: $clip", clip.contains("300"))
        assertTrue("the copy must carry the ACTIVE line — got: $clip", clip.contains("transport 50"))
        assertTrue("the copy must carry the TOTAL block — got: $clip", clip.contains(t("ee.calcTotal")))
        assertTrue("the copy must carry the grand total 350 — got: $clip", clip.contains("350"))
    }

    /* 2 — THE BUTTON IS CALLED SAVE NOW AND STILL COMMITS + PERSISTS
       (report #2: "keep all 100% of it's functionality but let's call the
       next btn save btn") */
    @Test
    fun theCommitButtonIsCalledSaveAndStillCommitsAndPersists() {
        ready()
        openEditor()

        // the label reads SAVE (the key kept its name; the word is the fix)
        assertEquals("Save", t("ee.calcNext"))

        // 100% the same functionality: the tap commits the line AND saves
        // (the button arms once there is a line to commit)
        toNumMode()
        listOf("7", "7", "0").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("= 770", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val saveBtn = rule.onAllNodes(hasText("Save", substring = true) and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { saveBtn.fetchSemanticsNodes().isNotEmpty() }
        saveBtn[0].performClick()
        rule.waitForIdle()
        awaitLine(0)
        rule.waitUntil(timeoutMillis = 15000) {
            rule.runOnUiThread {
                AppStore.state.entries.any { it.amount == 770.0 && it.exprNote?.contains("770") == true }
            }
        }
    }

    /* 4 — ONE SIZE, EDITED AND LOCKED ALIKE (report #4, the one asked
       "many times"): the native num-mode field's REAL text size equals the
       canvas's chosen px, and it follows the px picker — the same single
       number the locked lines render at (pinned pure in the v1.6.49
       battery's pixel model test) */
    @Test
    fun theActiveFieldRendersAtTheCanvasSingleSize() {
        ready()
        openEditor()
        toNumMode()

        // default: 14 px drives the native field's real textSize (the
        // probe reports PIXELS — sp × scaledDensity at 420dpi)
        key("5")
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("= 5", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val density = rule.runOnUiThread { rule.activity.resources.displayMetrics.scaledDensity }
        assertEquals(
            "the num-mode field must render at the canvas's default 14px",
            14f * density,
            rule.runOnUiThread { NoImeFieldProbe.last?.textSize } ?: -1f,
            1.5f,
        )

        // choose 16 px — a VISIBLE chip in the strip's first screen (the
        // ladder scrolls; the later stops sit off-viewport) — the SAME
        // choice scales the field, no re-open
        rule.onNodeWithTag("calcFontBtn").performClick()
        rule.waitForIdle()
        val pxChip = rule.onAllNodes(hasText("16 px", substring = true) and hasClickAction())
        rule.waitUntil(timeoutMillis = 15000) { pxChip.fetchSemanticsNodes().isNotEmpty() }
        pxChip[0].performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcKey:1").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(
            "the num-mode field must follow the px choice (edited == locked)",
            16f * density,
            rule.runOnUiThread { NoImeFieldProbe.last?.textSize } ?: -1f,
            1.5f,
        )
    }

    /* 4 — THE PREFIX SLOT IS ALWAYS RESERVED AND CONSISTENT (report #4:
       "it should be consistent if there is bullet, number or notting") —
       the slot sits at the same x in every prefix mode, and in NONE mode
       it still renders (empty) so the content column never shifts */
    @Test
    fun thePrefixSlotIsAlwaysReservedAndConsistent() {
        ready()
        openEditor()
        rule.onNodeWithTag("calcTextField").performTextReplacement("120")
        rule.waitForIdle()
        save()
        awaitLine(0)

        fun prefixX(): Float =
            rule.onNodeWithTag("calcLinePrefix:0", useUnmergedTree = true).fetchSemanticsNode().positionInRoot.x

        // bullet (the default)
        val bulletX = prefixX()
        // number mode — same slot, same x
        rule.onNodeWithTag("calcPrefixBtn").performClick()
        rule.waitForIdle()
        assertTrue(
            "the prefix slot must sit at the same x in NUMBER mode " +
                "(bullet x=$bulletX, number x=${prefixX()})",
            kotlin.math.abs(prefixX() - bulletX) < 2f,
        )
        // none mode — the slot STILL renders (reserved) at the same x
        rule.onNodeWithTag("calcPrefixBtn").performClick()
        rule.waitForIdle()
        assertTrue(
            "the prefix slot must STILL be reserved in NONE mode at the same x " +
                "(bullet x=$bulletX, none x=${prefixX()})",
            kotlin.math.abs(prefixX() - bulletX) < 2f,
        )
        // the ACTIVE line's slot is reserved too — in NONE mode
        assertTrue(
            "the active line's prefix slot must be reserved (none mode)",
            rule.onAllNodesWithTag("calcActivePrefix", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /* 5 — THE STEPS RIDE THEIR OWN CONTAINER, ALIGNED (report #5): the
       show-your-work block exists as a distinct container and its left
       edge lines up with the active field above it */
    @Test
    fun theStepsRideTheirOwnContainerAlignedWithTheLineAbove() {
        ready()
        openEditor()

        // the numbers come from the KEYPAD (QWERTY numbers are annotations
        // by design) so the line actually calculates and produces steps
        toNumMode()
        listOf("2", "×", "3", "0", "0", "+", "5", "0").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("= 650", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcSteps", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // the CONTAINER exists and sits BELOW the field
        val box = rule.onNodeWithTag("calcStepsBox", useUnmergedTree = true).fetchSemanticsNode()
        // num mode's native field — same content column the text field uses
        val field = rule.onNodeWithTag("calcNumField").fetchSemanticsNode()
        assertTrue(
            "the steps container must sit BELOW the line it explains " +
                "(field y=${field.positionInRoot.y} h=${field.size.height}, box y=${box.positionInRoot.y})",
            box.positionInRoot.y > field.positionInRoot.y + field.size.height - 4f,
        )
        // and its left edge ALIGNS with the field above it (the prefix
        // column indent, ±2px)
        assertTrue(
            "the steps container must align with the content above it " +
                "(field x=${field.positionInRoot.x}, box x=${box.positionInRoot.x})",
            kotlin.math.abs(box.positionInRoot.x - field.positionInRoot.x) < 2f,
        )
    }

    /* 7 — THE KEYPAD IS INSTANT: one tap on ⌫ deletes exactly ONE
       character (fires on the semantics door — the tap itself now fires on
       touch-DOWN in the real UI) */
    @Test
    fun backspaceTapDeletesExactlyOneCharacter() {
        ready()
        openEditor()
        toNumMode()
        listOf("1", "2", "3").forEach { key(it) }
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("= 123", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("calcKey:⌫").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) { activeFieldText() == "12" }
        assertEquals("one ⌫ tap must delete exactly one character", "12", activeFieldText())
    }

    /* 7 — THE SPACE BAR TAPS EXACTLY ONE SPACE (and never doubles one) */
    @Test
    fun spaceTapAppendsExactlyOneSpace() {
        ready()
        openEditor()
        toNumMode()
        key("5")
        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodes(hasText("= 5", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("calcKeySpace").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) { activeFieldText() == "5 " }
        assertEquals("one space tap must append exactly one space", "5 ", activeFieldText())
        // a second tap never doubles it (the v1.6.47 guard)
        rule.onNodeWithTag("calcKeySpace").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 15000) { activeFieldText() == "5 " }
        assertEquals("a second space tap must not double the space", "5 ", activeFieldText())
    }

    /* 7 — THE REPEAT SCHEDULE, PINNED PURE: the native feel — ⌫ wakes
       after 350ms and re-fires every 55ms; space after 420ms every 90ms */
    @Test
    fun theKeyRepeatScheduleMatchesTheNativeFeel() {
        // ⌫: nothing before the delay, first tick AT the delay, then cadence
        assertEquals(0, keyRepeatTicks(0L, BACKSPACE_REPEAT_DELAY_MS, BACKSPACE_REPEAT_MS))
        assertEquals(0, keyRepeatTicks(349L, BACKSPACE_REPEAT_DELAY_MS, BACKSPACE_REPEAT_MS))
        assertEquals(1, keyRepeatTicks(350L, BACKSPACE_REPEAT_DELAY_MS, BACKSPACE_REPEAT_MS))
        assertEquals(2, keyRepeatTicks(350L + BACKSPACE_REPEAT_MS, BACKSPACE_REPEAT_DELAY_MS, BACKSPACE_REPEAT_MS))
        // a full second of holding ⌫ chews through a dozen characters
        val oneSecond = keyRepeatTicks(1000L, BACKSPACE_REPEAT_DELAY_MS, BACKSPACE_REPEAT_MS)
        assertTrue("a 1s hold must repeat at least 10 times — got $oneSecond", oneSecond >= 10)
        // space: slower cadence, still repeats
        assertEquals(0, keyRepeatTicks(419L, SPACE_REPEAT_DELAY_MS, SPACE_REPEAT_MS))
        assertTrue(
            "a 1s space hold must repeat several times",
            keyRepeatTicks(1000L, SPACE_REPEAT_DELAY_MS, SPACE_REPEAT_MS) >= 5,
        )
    }

    /* 9 — THE TITLE's UNDERLINE HUGS THE TITLE (report #9: "why the bottom
       border of the title to far from the title"): the hairline sits
       within ~2dp of the title field's own box */
    @Test
    fun theTitleUnderlineHugsTheTitle() {
        ready()
        openEditor()

        rule.waitUntil(timeoutMillis = 15000) {
            rule.onAllNodesWithTag("calcTitleRow").fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithTag("calcTitleField").fetchSemanticsNodes().isNotEmpty()
        }
        val row = rule.onNodeWithTag("calcTitleRow").fetchSemanticsNode()
        val field = rule.onNodeWithTag("calcTitleField").fetchSemanticsNode()
        val fieldBottom = field.positionInRoot.y + field.size.height
        val borderY = row.positionInRoot.y + row.size.height
        val gapPx = borderY - fieldBottom
        assertTrue(
            "the title's underline must hug the title (gap ${gapPx}px ≈ ${gapPx / 2.625f}dp — " +
                "the old cut left ~8dp of air under the text)",
            gapPx in 0f..16f,
        )
    }
}
