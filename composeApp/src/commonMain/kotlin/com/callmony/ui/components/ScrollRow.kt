package com.callmony.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.callmony.theme.AppColors
import com.callmony.ui.kit.C

/** A Row that scrolls horizontally — used for every chip strip.
 *  v1.6.53 — LIGHT GLASS (spec 7: "rows of chips scroll horizontally with
 *  an edge fade; never clipped without a hint"): while the strip can
 *  scroll further, a soft 16dp brand-tinted fade hints at the content
 *  hiding beyond each edge. The fade reads as canvas (near-white), so it
 *  works over the glow gradient without a scissor; it disappears when an
 *  edge is reached. */
@Composable
fun ScrollRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberScrollState()
    val dark = C().mode == "dark"
    val canLeft by remember { derivedStateOf { state.canScrollBackward } }
    val canRight by remember { derivedStateOf { state.canScrollForward } }
    Box(
        modifier
            .horizontalScroll(state)
            .drawWithContent {
                drawContent()
                val w = 16.dp.toPx()
                val fade = if (dark) Color(0xFF0B1626) else AppColors.Brand50
                if (canLeft) {
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(fade, Color.Transparent),
                            startX = 0f,
                            endX = w,
                        ),
                        size = androidx.compose.ui.geometry.Size(w, size.height),
                    )
                }
                if (canRight) {
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, fade),
                            startX = size.width - w,
                            endX = size.width,
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(size.width - w, 0f),
                        size = androidx.compose.ui.geometry.Size(w, size.height),
                    )
                }
            },
    ) {
        Row { content() }
    }
}
