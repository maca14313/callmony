package com.callmony.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.callmony.ui.kit.C

/** A Row that scrolls horizontally — used for every chip strip.
 *  v1.6.53 — LIGHT GLASS (spec 7: "rows of chips scroll horizontally with
 *  an edge fade; never clipped without a hint").
 *  v1.6.54 — ROUND 2, SPEC 4, EXACT: the fade is a REAL alpha mask now —
 *  graphicsLayer(compositingStrategy = Offscreen) + drawWithContent drawing
 *  a gradient with BlendMode.DstIn, so the chips genuinely dissolve at the
 *  edge instead of a Brand50 veil PAINTED OVER them (the v1.6.53 fake —
 *  it read as a blue smudge and covered the chip text). 24dp fade at both
 *  edges while content hides beyond them, plus 16dp content padding so
 *  the first and last chip are never cut. */
@Composable
fun ScrollRow(modifier: Modifier = Modifier, contentPadding: PaddingValues? = null, content: @Composable () -> Unit) {
    val state = rememberScrollState()
    val canLeft by remember { derivedStateOf { state.canScrollBackward } }
    val canRight by remember { derivedStateOf { state.canScrollForward } }
    Box(
        modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .horizontalScroll(state)
            .drawWithContent {
                drawContent()
                val w = 24.dp.toPx()
                // DstIn mask: transparent -> opaque over the fade band, so
                // the content's own alpha dissolves toward the edge (a real
                // fade, never a painted veil)
                if (canLeft) {
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.Black),
                            startX = 0f,
                            endX = w,
                        ),
                        size = Size(w, size.height),
                        blendMode = BlendMode.DstIn,
                    )
                }
                if (canRight) {
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Black, Color.Transparent),
                            startX = size.width - w,
                            endX = size.width,
                        ),
                        topLeft = Offset(size.width - w, 0f),
                        size = Size(w, size.height),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
    ) {
        Row(
            Modifier.padding(contentPadding ?: PaddingValues(horizontal = 16.dp)),
        ) { content() }
    }
}
