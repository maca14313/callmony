package com.callmony.ui.components

import android.content.Context
import android.os.Looper
import android.text.style.ForegroundColorSpan
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.44 — the crash-report decode, pinned.

   The v1.6.43 firewall's clipboard capture finally caught the number-mode
   killer in the act. The report's own 60-frame chain, read bottom-up:

     et.setText(span)                 ← NoImeFieldActual.kt:142, INSIDE the
                                        AndroidView update block, which runs
                                        mid CompositionImpl.applyChanges
     Editor.maybeFireScheduledRestartInputForSetText
     InputMethodManager.restartInput → startInputInner
     TextView.onCreateInputConnection ← focusSearch(FOCUS_DOWN) INSIDE it
     Compose focus traversal → beyond the LazyList's visible bounds
     LazyListBeyondBoundsState.remeasure → subcompose → composeInitial
     ComposeRuntimeError "pending composition has not been applied" → CLOSE.

   The same synchronous beyond-bounds traversal fired on EVERY keystroke —
   the reported lag; the eventual re-entrant subcompose — the close.

   This battery pins BOTH halves of the fix:
     1. THE DOOR IS SHUT — NoImeEditText.onCreateInputConnection returns
        null (this field never talks to an IME; the soft keyboard has been
        suppressed since v1.6.15), so TextView's focusSearch never runs at
        all. The view stays a text editor so the native Copy/Cut/Paste/
        Select-all menu is untouched.
     2. THE WRITE LEAVES THE APPLY PHASE — a SideEffect that runs in the
        SAME apply pass, right AFTER the update block, must still see the
        OLD text (under v1.6.43 it already saw the new one — the exact
        re-entrancy that crashed), with the posted write landing right
        after on the looper, caret at the end, spans included. */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoImeFieldV1644Test {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun freshProbe() {
        NoImeFieldProbe.last = null
    }

    @After
    fun dropProbe() {
        NoImeFieldProbe.last = null
    }

    /* 1 — the chain's only entry door, closed twice over: a fresh view AND
       the exact instance the composition built. */
    @Test
    fun theCrashDoorIsShut_noInputConnectionEver() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val fresh = NoImeEditText(ctx)
        assertNull("the IME door must stay shut — it is where the crash chain entered", fresh.onCreateInputConnection(EditorInfo()))
        // the field stays a text editor: the native long-press menu
        // (Copy · Cut · Paste · Select all) never touches the InputConnection
        assertTrue("the native selection menu still treats this as an editor", fresh.onCheckIsTextEditor())

        rule.setContent {
            NoImeNumberField(
                value = "1", onValueChange = {},
                fontSizeSp = 17, textColor = Color.Black, cursorColor = Color.Black,
            )
        }
        rule.waitForIdle()
        val composed = NoImeFieldProbe.last
        assertNotNull("the composition must build the guarded NoImeEditText", composed)
        assertNull("the composed field must offer no InputConnection", composed!!.onCreateInputConnection(EditorInfo()))
    }

    /* 2 — THE structural fix: setText never runs inside the composition
       apply phase. SideEffect fires in the same apply pass, right after the
       update block — it must still see the OLD text; the write lands on the
       looper right after, with the caret at the end. */
    @Test
    fun textWritesNeverRunInsideTheCompositionApplyPhase() {
        var text by mutableStateOf("12")
        var seenAtApplyTime = "<unset>"
        rule.setContent {
            NoImeNumberField(
                value = text, onValueChange = {},
                fontSizeSp = 17, textColor = Color.Black, cursorColor = Color.Black,
            )
            SideEffect {
                seenAtApplyTime = NoImeFieldProbe.last?.text?.toString() ?: "<none>"
            }
        }
        rule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        val et = NoImeFieldProbe.last
        assertNotNull(et)
        assertEquals("12", et!!.text.toString())

        // the state change that recomposes the field
        rule.runOnUiThread { text = "125" }
        rule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()

        // the apply pass (update block + SideEffect in the same pass) saw the
        // OLD text — the synchronous setText that crashed the app is gone
        assertEquals(
            "the composition apply phase must NOT write the view — v1.6.43 did, and that re-entrancy closed the app",
            "12", seenAtApplyTime,
        )
        // the posted single-flight write landed afterwards, caret at the end
        assertEquals("125", et.text.toString())
        assertEquals(3, et.selectionStart)
        assertEquals(3, et.selectionEnd)
    }

    /* 3 — the exact crashing branch: the highlighted span write (the old
       NoImeFieldActual.kt:142) lands through the posted path with its
       per-character colours, and a text change re-posts with the new marks. */
    @Test
    fun highlightedSpansLandThroughThePostedPath() {
        var text by mutableStateOf("ab12")
        rule.setContent {
            NoImeNumberField(
                value = text, onValueChange = {},
                fontSizeSp = 17, textColor = Color.Black, cursorColor = Color.Black,
                highlight = setOf(0, 1), highlightColor = Color.Blue,
            )
        }
        rule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        val et = NoImeFieldProbe.last
        assertNotNull(et)
        assertEquals("ab12", et!!.text.toString())
        val spans = et.text.getSpans(0, et.text.length, ForegroundColorSpan::class.java)
        assertEquals("one span per TEXT-keyboard character", 2, spans.size)
        assertEquals(Color.Blue.toArgb(), spans[0].foregroundColor)
        assertEquals(Color.Blue.toArgb(), spans[1].foregroundColor)

        // a later change re-posts: the new text renders, the marks ride along
        rule.runOnUiThread { text = "zab12" }
        rule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("zab12", et.text.toString())
        assertEquals(
            "the marks re-apply over the new text",
            2, et.text.getSpans(0, et.text.length, ForegroundColorSpan::class.java).size,
        )
    }
}
