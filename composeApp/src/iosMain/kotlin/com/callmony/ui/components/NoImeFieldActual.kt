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
 *  SwiftUI shell when embedded; the 12-key grid remains the dialer input). */
@Composable
actual fun NoImeNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    fontSizeSp: Int,
    textColor: Color,
    cursorColor: Color,
    modifier: Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp,
            color = textColor,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(cursorColor),
        modifier = modifier,
    )
}
