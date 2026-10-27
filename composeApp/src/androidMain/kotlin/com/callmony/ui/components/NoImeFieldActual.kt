package com.callmony.ui.components

import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.runtime.Composable
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
 *  maxLines serve the calculator display (right-aligned, wraps to 3). */
@Composable
actual fun NoImeNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    fontSizeSp: Int,
    textColor: Color,
    cursorColor: Color,
    modifier: Modifier,
    alignEnd: Boolean,
    maxLines: Int,
) {
    val density = LocalDensity.current
    val textSizePx = with(density) { fontSizeSp.sp.toPx() }
    val textColorArgb = textColor.toArgb()
    // a stable holder so the watcher survives recompositions
    val state = remember { NumberFieldState() }

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
                gravity = if (alignEnd) Gravity.END or Gravity.BOTTOM
                else Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
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
                et.setText(value)
                et.setSelection(value.length)
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
