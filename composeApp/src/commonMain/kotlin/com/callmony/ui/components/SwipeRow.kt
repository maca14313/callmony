package com.callmony.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.callmony.ui.kit.C
import com.callmony.ui.kit.MdiIcon
import kotlinx.coroutines.launch
import kotlin.math.abs

/* SwipeRow — port of src/components/SwipeRow.tsx.
   Claims a drag when |dx| > 12 && |dx| > 1.25·|dy|; opens past 40 px
   (velocity factored); 160 ms snap; ±24 px overdrag clamp; tap-on-open
   closes first. Left & right action buttons render under the row. */

data class SwipeAction(
    val label: String,
    val icon: String,
    val color: Color,
    val onPress: () -> Unit,
)

@Composable
fun SwipeRow(
    key: Any?,
    left: List<SwipeAction> = emptyList(),
    right: List<SwipeAction> = emptyList(),
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    row: @Composable () -> Unit,
) {
    val C = C()
    val scope = rememberCoroutineScope()
    val x = remember(key) { Animatable(0f) }
    var openSide by remember(key) { mutableStateOf(0) } // -1 left-open, 1 right-open, 0 closed
    val actionW = 76f
    val density = androidx.compose.ui.platform.LocalDensity.current
    val maxLeft = if (left.isNotEmpty()) actionW * left.size * density.density else 0f
    val maxRight = if (right.isNotEmpty()) actionW * right.size * density.density else 0f

    // another row opening closes this one (single-open list behavior)
    LaunchedEffect(SwipeBus.token) {
        if (SwipeBus.openKey != null && SwipeBus.openKey != key && x.value != 0f) {
            scope.launch { x.animateTo(0f, tween(160)) }
            openSide = 0
        }
    }

    Box(modifier.fillMaxWidth()) {
        // underlay — actions revealed while dragging
        Row(Modifier.matchParentSize()) {
            if (left.isNotEmpty()) {
                Row(Modifier.fillMaxHeight()) {
                    left.forEach { a ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 3.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(a.color)
                                .pointerInput(a) {
                                    detectTapGestures {
                                        a.onPress()
                                        scope.launch { x.animateTo(0f, tween(160)) }
                                        openSide = 0
                                        SwipeBus.openKey = null
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) { MdiIcon(a.icon, 20, Color.White) }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight().background(C.card))
            if (right.isNotEmpty()) {
                Row(Modifier.fillMaxHeight()) {
                    right.forEach { a ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 3.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(a.color)
                                .pointerInput(a) {
                                    detectTapGestures {
                                        a.onPress()
                                        scope.launch { x.animateTo(0f, tween(160)) }
                                        openSide = 0
                                        SwipeBus.openKey = null
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) { MdiIcon(a.icon, 20, Color.White) }
                    }
                }
            }
        }
        // the row itself, translated by the swipe
        Box(
            Modifier
                .graphicsLayer { translationX = x.value }
                .pointerInput(maxLeft, maxRight) {
                    detectDragGestures(
                        onDragEnd = {
                            val v = x.value
                            val target = when {
                                v > 40 * density.density && maxLeft > 0 -> maxLeft
                                v < -40 * density.density && maxRight > 0 -> -maxRight
                                else -> 0f
                            }
                            openSide = if (target > 0) -1 else if (target < 0) 1 else 0
                            SwipeBus.openKey = if (openSide != 0) key else null
                            SwipeBus.token++
                            scope.launch { x.animateTo(target, tween(160)) }
                        },
                        onDragCancel = {
                            val back = when (openSide) {
                                -1 -> maxLeft
                                1 -> -maxRight
                                else -> 0f
                            }
                            scope.launch { x.animateTo(back, tween(160)) }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        val dx = dragAmount.x
                        val dy = dragAmount.y
                        val claimed = abs(x.value) > 0.5f ||
                            (abs(dx) > 12 && abs(dx) > 1.25f * abs(dy))
                        if (claimed) {
                            val over = 24f * density.density
                            val next = (x.value + dx).coerceIn(-maxRight - over, maxLeft + over)
                            scope.launch { x.snapTo(next) }
                        }
                    }
                },
        ) {
            PressableRow(
                onClick = {
                    if (openSide != 0) {
                        scope.launch { x.animateTo(0f, tween(160)) }
                        openSide = 0
                        SwipeBus.openKey = null
                    } else onTap()
                },
                onLongClick = { if (openSide == 0) onLongPress?.invoke() },
            ) { row() }
        }
    }
}

/** Only one swipe row open at a time. */
object SwipeBus {
    var openKey: Any? = null
    var token: Int = 0
}
