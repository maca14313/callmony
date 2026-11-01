package com.callmony.ui.components

import android.view.inputmethod.EditorInfo
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* v1.6.45 — report #1 ("i nottised sometimes the regular native keyboard
   shows up on the numbers mode"), pinned at the view level where the leak
   lives. The QWERTY field in TEXT mode is served by the system IME;
   toggling Aa → 123 removes it from composition, but "sometimes" nothing
   tells the IME to go away (the toggle's ime.hide races the field's
   disposal; returning to the app re-shows the keyboard for the last
   served editor; OEM builds keep it around on focus change). The number
   field therefore force-hides the IME on EVERY focus gain and EVERY
   window-focus gain — the two moments a keyboard can attach to it. This
   battery proves both paths really fire the hide, and that the v1.6.44
   crash door (the null InputConnection) stays shut alongside. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoImeFieldV1645Test {

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

    /* 1 — the field force-hides the IME when it GAINS FOCUS (the toggle /
       canvas-open / unlock path arms the caret through view focus) and
       when the WINDOW regains focus (the app-resume path — the classic
       "sometimes": the system re-shows the IME for the focused editor on
       return). */
    @Test
    fun focusGainsForceHideTheIme() {
        rule.setContent {
            NoImeNumberField(
                value = "1", onValueChange = {},
                fontSizeSp = 17, textColor = Color.Black, cursorColor = Color.Black,
                focusToken = 1,
            )
        }
        rule.waitForIdle()
        val et = NoImeFieldProbe.last
        assertNotNull("the guarded field must be composed", et)

        val baseline = rule.runOnUiThread { et!!.imeHideAttempts }
        // window-focus gain (app resume) → hide
        rule.runOnUiThread { et!!.onWindowFocusChanged(true) }
        val afterWindow = rule.runOnUiThread { et!!.imeHideAttempts }
        assertTrue(
            "a window-focus gain must fire the IME hide (the app-resume leak)",
            afterWindow > baseline,
        )
        // view-focus gain (toggle / open / unlock arming the caret) → hide.
        // clearFocus first: requestFocus on an ALREADY-focused view is a
        // no-op (the v1.6.42 guard lives on exactly that), so the round-trip
        // guarantees a real gain → onFocusChanged(true) → hide
        rule.runOnUiThread { et!!.clearFocus(); et!!.requestFocus() }
        val afterFocus = rule.runOnUiThread { et!!.imeHideAttempts }
        assertTrue(
            "a view-focus gain must fire the IME hide (the toggle leak)",
            afterFocus > afterWindow,
        )
        // and the v1.6.44 crash door is still shut through it all
        assertNull(et!!.onCreateInputConnection(EditorInfo()))
    }

    /* 2 — hiding is exception-proof and repeatable: focus churn never
       stops the field from being a no-IME editor. */
    @Test
    fun repeatedFocusChurnStaysQuietAndShut() {
        rule.setContent {
            NoImeNumberField(
                value = "12", onValueChange = {},
                fontSizeSp = 17, textColor = Color.Black, cursorColor = Color.Black,
                focusToken = 1,
            )
        }
        rule.waitForIdle()
        val et = NoImeFieldProbe.last!!
        repeat(5) {
            rule.runOnUiThread {
                et.onWindowFocusChanged(true)
                et.clearFocus()
                et.requestFocus()
            }
        }
        val attempts = rule.runOnUiThread { et.imeHideAttempts }
        assertTrue("five focus rounds must fire at least five hides — got $attempts", attempts >= 5)
        assertEquals(2, et.text.toString().length) // the field itself untouched
        assertNull(et.onCreateInputConnection(EditorInfo()))
    }
}
