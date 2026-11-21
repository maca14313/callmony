package com.callmony.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.callmony.theme.AppColors
import com.callmony.theme.AppShapes
import com.callmony.ui.kit.C

/* v1.6.52 — THE LIGHT GLASS SURFACE KIT (user spec). GlassPanel /
   GlassCard / the gradient canvas with soft radial glows / press-scale
   motion — 100% commonMain Compose, no platform views, no Modifier.blur
   (the glows are radial gradients, identical on every platform). */

/** The Light Glass canvas: the near-white vertical-diagonal gradient plus
 *  2-3 large soft radial glows (brand / accent / teal at ~12-20% alpha)
 *  drawn with drawBehind — never Modifier.blur (inconsistent across
 *  platforms). Applied at the shell root and every full-screen surface. */
fun Modifier.glassCanvas(): Modifier = composed {
    val dark = C().mode == "dark"
    if (dark) {
        // dark glass: deep navy canvas, same glow grammar at lower alpha
        this.background(
            Brush.linearGradient(
                listOf(Color(0xFF0B1626), Color(0xFF0E1B30), Color(0xFF0B1626)),
            )
        ).drawBehind {
            drawGlow(0.18f to 0.22f, 1.15f, Color(0xFF3B94FF))
            drawGlow(0.85f to 0.16f, 1.05f, Color(0xFF0EA5E9))
        }
    } else {
        this.drawBehind {
            // #EAF3FF -> #FFFFFF -> #FFF3EE, slightly diagonal
            drawRect(
                Brush.linearGradient(
                    listOf(Color(0xFFEAF3FF), Color(0xFFFFFFFF), Color(0xFFF3F8FF), Color(0xFFFFF3EE)),
                    start = Offset.Zero,
                    end = Offset(size.width * 0.55f, size.height.toFloat()),
                )
            )
            drawGlow(0.06f to 0.16f, 1.25f, AppColors.Brand)
            drawGlow(0.94f to 0.13f, 1.1f, AppColors.Accent)
            drawGlow(0.5f to 0.10f, 0.9f, Color(0xFF14B8A6)) // teal breath
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlow(
    at: Pair<Float, Float>,
    radiusFactor: Float,
    color: Color,
) {
    val cx = size.width * at.first
    val cy = size.height * at.second
    val r = size.width * radiusFactor
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.55f), Color.Transparent),
            center = Offset(cx, cy),
            radius = r,
        ),
        radius = r,
        center = Offset(cx, cy),
    )
}

/** GlassPanel — white @62%, 1dp white @85% border, Sheet radius (the spec's
 *  softer panel for headers, toolbars, keypad bars). */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.Sheet,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = C().mode == "dark"
    val bg = if (dark) Color(0xFF152238).copy(alpha = 0.62f) else Color.White.copy(alpha = 0.62f)
    val border = if (dark) Color(0x59FFFFFF) else Color(0xD9FFFFFF)
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape),
        content = content,
    )
}

/** GlassCard — white @78%, 1dp brand@10% border, 24dp radius, brand-tinted
 *  shadow (ambient/spot Brand@12%), the spec's standard floating card. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.Card,
    onPress: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = C().mode == "dark"
    val bg = if (dark) Color(0xFF152238).copy(alpha = 0.78f) else Color.White.copy(alpha = 0.78f)
    val borderColor = if (dark) Color(0x333B94FF) else AppColors.Brand.copy(alpha = 0.10f)
    val shadowColor = AppColors.Brand.copy(alpha = if (dark) 0.28f else 0.12f)
    Box(
        modifier
            .shadow(20.dp, shape, ambientColor = shadowColor, spotColor = shadowColor)
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .then(
                if (onPress != null) {
                    Modifier.clickable(onClick = onPress)
                } else Modifier
            ),
        content = content,
    )
}

/** PrimaryButton surface — horizontal BrandLight -> BrandDark gradient. */
fun Modifier.brandGradient(shape: Shape): Modifier = composed {
    val C = C()
    background(
        Brush.horizontalGradient(listOf(C.brandLight, C.brandDark)),
        shape,
    )
}

/** Press-scale motion (spec: press = scale 0.92-0.97, 120-200ms tween) on
 *  top of the app-wide press veil. Observes the source WITHOUT consuming
 *  anything — pass the SAME MutableInteractionSource the clickable uses.
 *  (Not a Modifier.composed factory: call sites need the interaction
 *  source anyway, so this is a plain @Composable returning a Modifier. */
@Composable
fun Modifier.pressScale(
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource,
    pressed: Float = 0.95f,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = tween(if (isPressed) 120 else 180),
        label = "pressScale",
    )
    return this.scale(scale)
}

/** Convenience: a clickable + press-scale + veil box (buttons, tiles). */
@Composable
fun PressableScaleBox(
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    pressed: Float = 0.95f,
    content: @Composable BoxScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(src, pressed)
            .clickable(interactionSource = src, indication = null, onClick = onPress),
        content = content,
    )
}
