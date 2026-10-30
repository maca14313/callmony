package com.callmony.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/* NoImeNumberField — the keypad's number display.

   v1.6.15 FIX: the display was a readOnly BasicTextField, which shows the
   Copy menu item but DISABLES Paste and Cut — the user could not paste or
   cut like a normal input. RN parity is a REAL editable text field with the
   software keyboard suppressed (showSoftInputOnFocus=false), which keeps the
   full native long-press menu: Copy · Cut · Paste · Select all + selection
   handles, while the 12-key grid stays the only way to type.

   expect/actual because "editable but no IME" is a native trick:
     - Android: a real EditText with setShowSoftInputOnFocus(false)
     - iOS:     a plain editable field (UIKit suppresses via inputView)

   v1.6.34 — the CALCULATOR display reuses this field for its native
   copy/paste/cut behaviour (user report: "PASTE should be what someone can
   do in a normal input — press long on some part and native options show
   up like copy, paste, cut"), so the signature grows two defaulted params:
   alignEnd (the calculator display right-aligns) and maxLines (long
   expressions wrap to 3). The keypad call-site is unchanged.
   v1.6.39: alignStart serves the canvas calculator's active line — a
   left-aligned, wrapping free-text sentence. */

@Composable
expect fun NoImeNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    fontSizeSp: Int,
    textColor: Color,
    cursorColor: Color,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    alignEnd: Boolean = false,
    alignStart: Boolean = false,
    maxLines: Int = 1,
)
