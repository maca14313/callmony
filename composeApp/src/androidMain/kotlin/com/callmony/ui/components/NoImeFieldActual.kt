package com.callmony.ui.components

import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** Android actual: a real EditText — fully editable (native Copy / Cut /
 *  Paste / Select-all menu + selection handles) but the software keyboard
 *  never opens (setShowSoftInputOnFocus(false), exactly the RN edition's
 *  TextInput behavior). Programmatic setText is guarded so state round-trips
 *  never re-fire the watcher or fight the cursor. v1.6.34: alignEnd +
 *  maxLines serve the calculator display (right-aligned, wraps to 3).
 *  v1.6.39: alignStart serves the canvas calculator's active line.
 *  v1.6.41: highlight renders the TEXT-keyboard characters in their own
 *  colour (spans rebuilt on every programmatic sync), and focusToken
 *  re-requests REAL VIEW focus whenever it bumps — the caret is always
 *  visible in number mode without the IME ever opening.
 *  v1.6.42 — the user's #1 report ("when toggle to number mode and start
 *  typing it starts to lag, and finally the app closes"): the repeated
 *  unconditional requestFocus() calls are the one IMM-fighting input this
 *  interop makes on REAL devices (Robolectric's shadow IMM is a no-op, so
 *  no harness could see it) — every redundant focus request on a real
 *  Samsung/OneUI build can bounce the InputMethodManager (restartInput
 *  churn → the reported lag → the ANR close). Focus is now requested ONLY
 *  when the view does not already hold it, the delayed catch-up request
 *  fires only if the view is still unfocused by then, and the per-update
 *  textSize/textColor writes — each one a requestLayout() on every
 *  keystroke's recomposition — are cached so nothing touches the view
 *  when nothing changed. */
@Composable
actual fun NoImeNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    fontSizeSp: Int,
    textColor: Color,
    cursorColor: Color,
    modifier: Modifier,
    alignEnd: Boolean,
    alignStart: Boolean,
    maxLines: Int,
    highlight: Set<Int>,
    highlightColor: Color,
    focusToken: Int,
) {
    val density = LocalDensity.current
    val textSizePx = with(density) { fontSizeSp.sp.toPx() }
    val textColorArgb = textColor.toArgb()
    val hlArgb = if (highlightColor == Color.Unspecified) textColorArgb else highlightColor.toArgb()
    // a stable holder so the watcher survives recompositions
    val state = remember { NumberFieldState() }

    // v1.6.41 — the caret: real view focus, re-requested whenever the token
    //   bumps (canvas open, Aa/123 toggle, Next line, unlock).
    // v1.6.42 — GUARDED: only when the view does not already hold focus (see
    //   the class doc — the redundant requests were the IMM storm behind the
    //   number-mode lag/crash report); the delayed catch-up still covers the
    //   pre-attachment no-op, but it too fires only if still unfocused.
    LaunchedEffect(focusToken) {
        if (focusToken > 0) {
            state.field?.takeIf { !it.hasFocus() }?.requestFocus()
            kotlinx.coroutines.delay(220)
            state.field?.takeIf { !it.hasFocus() }?.requestFocus()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            EditText(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setShowSoftInputOnFocus(false) // the 12-key grid is the input method
                isSingleLine = maxLines <= 1
                setMaxLines(maxLines)
                imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NONE
                gravity = when {
                    alignEnd -> Gravity.END or Gravity.BOTTOM
                    alignStart -> Gravity.START or Gravity.TOP
                    else -> Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
                }
                background = null
                setPadding(0, 0, 0, 0)
                setTextColor(textColorArgb)
                textSize = pxToSp(textSizePx)
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                letterSpacing = 0.04f
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        if (state.programmatic) return
                        onValueChange(s?.toString() ?: "")
                    }
                })
                state.field = this
            }
        },
        update = { et ->
            state.programmatic = true
            val current = et.text?.toString() ?: ""
            if (current != value) {
                if (highlight.isNotEmpty()) {
                    // the TEXT-keyboard characters render in their own colour
                    val span = SpannableString(value)
                    highlight.forEach { i ->
                        if (i in 0 until value.length) {
                            span.setSpan(
                                ForegroundColorSpan(hlArgb), i, i + 1,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                            )
                        }
                    }
                    et.setText(span)
                } else {
                    et.setText(value)
                }
                et.setSelection(value.length) // the caret sits at the END
            }
            // v1.6.42 — cached: the colour/size setters each trigger a
            // requestLayout(); applying them on EVERY recomposition (every
            // keystroke) relaid the view out for nothing.
            if (state.appliedColor != textColorArgb) {
                et.setTextColor(textColorArgb)
                state.appliedColor = textColorArgb
            }
            val spSize = et.pxToSp(textSizePx)
            if (state.appliedSize != spSize) {
                et.textSize = spSize
                state.appliedSize = spSize
            }
            state.programmatic = false
        },
    )
}

private class NumberFieldState {
    var field: EditText? = null
    var programmatic = false
    // v1.6.42 — last-applied view cosmetics (see update): skip no-op writes
    var appliedColor = 0
    var appliedSize = -1f
}

private fun EditText.pxToSp(px: Float): Float = px / resources.displayMetrics.scaledDensity
