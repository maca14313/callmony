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
   (the glows are radial gradients, identical on every platform).
   v1.6.53 — THE SPEC, EXACTLY: the canvas is the spec's own 3 stops
   (#EAF3FF -> #FFFFFF -> #FFF3EE, nothing added), exactly TWO glows
   (brand top-left <=12%, warm peach bottom-right <=10% — the v1.6.52
   third teal glow and the 4th mid stop are gone), and the overlay
   surfaces are >=96% opaque (nothing readable through an overlay). */

/** v1.6.53 — the Light Glass canvas constants, exposed for the spec pin
 *  (LightGlassV1653Test): the EXACT three stops, the two glow positions
 *  and their alpha caps. If any value drifts from the user's design
 *  language the pin fails. */
object GlassTokensSpec {
    val lightStops: List<Long> = listOf(0xFFEAF3FF, 0xFFFFFFFF, 0xFFFFF3EE)
    const val LIGHT_GLOW_COUNT = 2
    val brandGlowAt = 0.10f to 0.12f
    val peachGlowAt = 0.92f to 0.90f
    const val BRAND_GLOW_MAX_ALPHA = 0.12f
    const val PEACH_GLOW_ALPHA = 0.10f
    // the canvas draws the brand glow at 0.12 and the peach at 0.10 — both
    // within the spec's caps; the pin reads this surfaced max
    const val LIGHT_GLOW_MAX_ALPHA = BRAND_GLOW_MAX_ALPHA
}

/** The Light Glass canvas: the spec's diagonal gradient
 *  #EAF3FF -> #FFFFFF -> #FFF3EE with exactly two soft radial glows —
 *  brand blue top-left (<=12% alpha) and warm peach bottom-right
 *  (<=10% alpha) — drawn with drawBehind, never Modifier.blur.
 *  The top 40% stays near-white so headers stay crisp.
 *  Applied at the shell root and every full-screen surface. */
fun Modifier.glassCanvas(): Modifier = composed {
    val dark = C().mode == "dark"
    if (dark) {
        // dark glass: deep navy canvas, same two-glow grammar
        this.background(
            Brush.linearGradient(
                listOf(Color(0xFF0B1626), Color(0xFF0E1B30), Color(0xFF0B1626)),
            )
        ).drawBehind {
            drawGlow(0.18f to 0.22f, 1.15f, Color(0xFF3B94FF), 0.12f)
            drawGlow(0.85f to 0.85f, 1.05f, Color(0xFFFF6B4A), 0.10f)
        }
    } else {
        this.drawBehind {
            // the spec's exact three stops, slightly diagonal
            drawRect(
                Brush.linearGradient(
                    listOf(Color(0xFFEAF3FF), Color(0xFFFFFFFF), Color(0xFFFFF3EE)),
                    start = Offset.Zero,
                    end = Offset(size.width * 0.55f, size.height.toFloat()),
                )
            )
            // glow 1 — brand blue, top-left, <=12% alpha
            drawGlow(GlassTokensSpec.brandGlowAt, 1.30f, AppColors.Brand, GlassTokensSpec.BRAND_GLOW_MAX_ALPHA)
            // glow 2 — warm peach, bottom-right, <=10% alpha
            drawGlow(GlassTokensSpec.peachGlowAt, 1.15f, AppColors.Accent, GlassTokensSpec.PEACH_GLOW_ALPHA)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlow(
    at: Pair<Float, Float>,
    radiusFactor: Float,
    color: Color,
    maxAlpha: Float,
) {
    val cx = size.width * at.first
    val cy = size.height * at.second
    val r = size.width * radiusFactor
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = maxAlpha), Color.Transparent),
            center = Offset(cx, cy),
            radius = r,
        ),
        radius = r,
        center = Offset(cx, cy),
    )
}

/** GlassPanel — an OVERLAY surface (spec 4b): white >=96% opaque (the
 *  content behind must NEVER be readable through it), 1dp brand@10%
 *  hairline, Sheet radius. Used for headers, toolbars, keypad bars. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.Sheet,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = C().mode == "dark"
    val bg = if (dark) Color(0xFF152238).copy(alpha = 0.96f) else Color.White.copy(alpha = 0.96f)
    val border = if (dark) Color(0x333B94FF) else AppColors.Brand.copy(alpha = 0.10f)
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
