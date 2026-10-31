package com.callmony.ui.components

import android.content.Context
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
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
 *  when nothing changed. v1.6.44 — THE CRASH REPORT DECODED (the v1.6.43
 *  firewall's clipboard capture finally showed the killer): the report's
 *  60-frame chain is et.setText() inside the AndroidView update block —
 *  which runs MID CompositionImpl.applyChanges — firing
 *  Editor.maybeFireScheduledRestartInputForSetText → IMM.restartInput →
 *  startInputInner → TextView.onCreateInputConnection → focusSearch(
 *  FOCUS_DOWN), and that focus search walks the Compose tree BEYOND THE
 *  BOUNDS of a LazyList, forcing a synchronous remeasure/subcompose
 *  WHILE THE PARENT COMPOSITION IS STILL BEING APPLIED →
 *  ComposeRuntimeError "pending composition has not been applied" → the
 *  app closes. The same synchronous beyond-bounds traversal on EVERY
 *  keystroke is the reported LAG. Two independent fixes, either of which
 *  alone breaks the chain: (1) NoImeEditText.onCreateInputConnection
 *  returns null — this field never talks to an IME, so TextView's
 *  focusSearch never runs at all (native Copy/Cut/Paste/Select-all keep
 *  working — the ActionMode path never touches the InputConnection);
 *  (2) text writes LEAVE the apply phase entirely — the update block only
 *  records the latest pending text and posts ONE single-flight runnable,
 *  which applies the programmatic-guarded setText + caret right after the
 *  composition has applied. Re-entrancy is now structurally impossible. */
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
            NoImeEditText(ctx).apply {
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
            // v1.6.43 — fast exit: a recomposition where NOTHING changed
            // (the common case — the field recomposes on every keystroke's
            // parent scope) must touch the view ZERO times: no text compare,
            // no span rebuild, no setter.
            // v1.6.44 — the TEXT WRITE IS POSTED (see the class doc): this
            // block runs mid applyChanges, and the crash report proved a
            // synchronous et.setText() there re-enters the runtime through
            // IMM.restartInput → focusSearch → beyond-bounds LazyList
            // remeasure → subcompose → ComposeRuntimeError. The update now
            // only records the LATEST pending text/highlight and posts one
            // single-flight runnable; the write lands on the main looper
            // right after the composition has fully applied.
            val dirty = state.syncedValue != value ||
                state.syncedHighlight != highlight ||
                state.appliedColor != textColorArgb ||
                state.appliedSize != et.pxToSp(textSizePx)
            if (dirty) {
                state.pendingText = value
                state.pendingHighlight = highlight
                state.pendingHlArgb = hlArgb
                // single flight: however many recompositions land before the
                // looper frees up, ONE runnable is queued and it applies the
                // LATEST pending values when it runs
                if (!state.postQueued) {
                    state.postQueued = true
                    et.post {
                        state.postQueued = false
                        val text = state.pendingText ?: return@post
                        val hl = state.pendingHighlight
                        val hlColor = state.pendingHlArgb
                        state.pendingText = null
                        state.pendingHighlight = emptySet()
                        if (et.text?.toString() != text) {
                            state.programmatic = true
                            try {
                                if (hl.isNotEmpty()) {
                                    // the TEXT-keyboard characters render in
                                    // their own colour
                                    val span = SpannableString(text)
                                    hl.forEach { i ->
                                        if (i in 0 until text.length) {
                                            span.setSpan(
                                                ForegroundColorSpan(hlColor), i, i + 1,
                                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                                            )
                                        }
                                    }
                                    et.setText(span)
                                } else {
                                    et.setText(text)
                                }
                                // the caret sits at the END — but only MOVE it
                                // when it is not already there (a no-op
                                // setSelection still notifies)
                                if (et.selectionStart != text.length ||
                                    et.selectionEnd != text.length
                                ) {
                                    et.setSelection(text.length)
                                }
                            } finally {
                                state.programmatic = false
                            }
                        }
                    }
                }
                // remember what the view is about to hold — including after
                // an EXTERNAL edit (native paste/cut landed via the watcher),
                // so the next no-op recomposition really is free
                state.syncedValue = value
                state.syncedHighlight = highlight
                // v1.6.42 — cached: the colour/size setters each trigger a
                // requestLayout(); applying them on EVERY recomposition (every
                // keystroke) relaid the view out for nothing. These two stay
                // synchronous: requestLayout only SCHEDULES the next traversal
                // (unlike the old setText, which synchronously re-entered the
                // runtime through the IMM).
                if (state.appliedColor != textColorArgb) {
                    et.setTextColor(textColorArgb)
                    state.appliedColor = textColorArgb
                }
                val spSize = et.pxToSp(textSizePx)
                if (state.appliedSize != spSize) {
                    et.textSize = spSize
                    state.appliedSize = spSize
                }
            }
        },
    )
}

/** v1.6.44 — the crash report's entire 60-frame chain enters through ONE
 * door: TextView.onCreateInputConnection, whose focusSearch(FOCUS_DOWN)
 * synchronously walks the Compose tree (the beyond-bounds LazyList
 * remeasure that re-enters the runtime mid-apply). This field never talks
 * to an IME — setShowSoftInputOnFocus(false) has kept the soft keyboard
 * out since v1.6.15; the 12-key grid IS the input method — so the door is
 * simply CLOSED: onCreateInputConnection returns null and
 * InputMethodManager.startInputInner returns before any focus search can
 * run. The native long-press menu (Copy · Cut · Paste · Select all) and
 * the selection handles still work — the Editor's ActionMode path never
 * touches the InputConnection. */
internal class NoImeEditText(ctx: Context) : EditText(ctx) {
    init { NoImeFieldProbe.last = this }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? = null
}

/** v1.6.44 — test hook: the most recently created NoImeEditText, so the
 * regression battery can prove the crash door stays shut and that text
 * writes never re-enter the composition apply phase. Never read by app
 * code. */
internal object NoImeFieldProbe { var last: NoImeEditText? = null }

private class NumberFieldState {
    var field: EditText? = null
    var programmatic = false
    // v1.6.42 — last-applied view cosmetics (see update): skip no-op writes
    var appliedColor = 0
    var appliedSize = -1f
    // v1.6.43 — last-synced CONTENT (text + highlight): with these the
    // update block can prove a recomposition is a no-op and return before
    // touching the view at all
    var syncedValue: String? = null
    var syncedHighlight: Set<Int> = emptySet()
    // v1.6.44 — the posted single-flight write (see update): the latest
    // text/highlight waiting for the main looper, applied after applyChanges
    var pendingText: String? = null
    var pendingHighlight: Set<Int> = emptySet()
    var pendingHlArgb = 0
    var postQueued = false
}

private fun EditText.pxToSp(px: Float): Float = px / resources.displayMetrics.scaledDensity
