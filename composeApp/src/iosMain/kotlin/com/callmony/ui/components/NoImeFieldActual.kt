package com.callmony.ui.components

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/** iOS actual: an editable field (UIKit's keyboard is suppressed by the
 *  SwiftUI shell when embedded; the 12-key grid remains the dialer input).
 *  v1.6.34: alignEnd + maxLines serve the calculator display. v1.6.39:
 *  alignStart serves the canvas calculator's active line. v1.6.41: the
 *  highlight / focusToken params keep the expect signature common (the
 *  colour spans and the view-focus caret are Android-driven). */
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
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = maxLines <= 1,
        maxLines = if (maxLines <= 1) 1 else maxLines,
        textStyle = TextStyle(
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp,
            color = textColor,
            textAlign = when {
                alignEnd -> TextAlign.End
                alignStart -> TextAlign.Start
                else -> TextAlign.Center
            },
        ),
        cursorBrush = SolidColor(cursorColor),
        modifier = modifier,
    )
}
