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
 *  visible in number mode without the IME ever opening. */
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
    // bumps (canvas open, Aa/123 toggle, Next line, unlock) and again after
    // the focus-clears of any host-sheet/overlay settle — no IME ever.
    LaunchedEffect(focusToken) {
        if (focusToken > 0) {
            state.field?.requestFocus()
            kotlinx.coroutines.delay(220)
            state.field?.requestFocus()
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
            et.setTextColor(textColorArgb)
            et.textSize = et.pxToSp(textSizePx)
            state.programmatic = false
        },
    )
}

private class NumberFieldState {
    var field: EditText? = null
    var programmatic = false
}

private fun EditText.pxToSp(px: Float): Float = px / resources.displayMetrics.scaledDensity
