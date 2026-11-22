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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
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
   surfaces are >=96% opaque (nothing readable through an overlay).
   v1.6.54 — ROUND 2, EXACT (user: "i dont want the blue colour spilling
   in to things, the bg should be only white"): the screen background is
   PURE WHITE — the blue/peach gradient and BOTH radial glows are gone,
   brand blue lives only in components (nav, buttons, avatars, keypad,
   chips, focus states). Overlays are 100% OPAQUE white (the @96%
   residue let the page behind read through — "nothing behind a sheet
   may be readable" means alpha 1.0, not 0.96), and the scrim is the
   user's spec: Ink @40%. */

/** v1.6.54 — the overlay surface contract, pinned by LightGlassV1654Test:
 *  sheets/menus/dialogs are FULLY opaque (alpha 1.0) and the page behind
 *  a sheet dims under Ink @40%. */
object GlassTokensSpec {
    /** The light canvas: pure white (round 2 — no gradient, no glows). */
    val lightStops: List<Long> = listOf(0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF)
    const val LIGHT_GLOW_COUNT = 0
    const val BRAND_GLOW_MAX_ALPHA = 0f
    const val PEACH_GLOW_ALPHA = 0f
    // kept for the v1.6.53 pin surface (values unused with zero glows)
    val brandGlowAt = 0.10f to 0.12f
    val peachGlowAt = 0.92f to 0.90f
    /** The scrim behind sheets: Ink @40% (round 2, spec 1). */
    const val SHEET_SCRIM_ALPHA = 0.40f
}

/** v1.6.54 — the page background: PURE WHITE in light (round 2: the bg is
 *  only white — no gradient, no glows, nothing blue spilling in), the
 *  deep-navy solid in dark. Applied at the shell root and every
 *  full-screen surface. */
fun Modifier.glassCanvas(): Modifier = composed {
    val dark = C().mode == "dark"
    if (dark) {
        // dark twin: solid deep navy (same grammar as light — a flat page)
        this.background(Color(0xFF0B1626))
    } else {
        this.background(Color.White)
    }
}

/** GlassPanel — an OVERLAY surface (round 2, spec 1): 100% OPAQUE white
 *  (alpha 1.0 — the @96% residue let the page behind read through; the
 *  user's rule is "containerColor = Color.White, never alpha"), 1dp
 *  brand@10% hairline, Sheet radius. Used for headers, toolbars, keypad
 *  bars. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.Sheet,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = C().mode == "dark"
    val bg = if (dark) Color(0xFF152238) else Color.White
    val border = if (dark) Color(0x333B94FF) else AppColors.Brand.copy(alpha = 0.10f)
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape),
        content = content,
    )
}

/** GlassCard — 100% opaque white (round 2: cards sit on the pure-white
 *  page as real surfaces — nothing behind may read through, and the
 *  blue-tinted glass tone is gone), 1dp brand@10% border, 24dp radius,
 *  soft brand-tinted shadow, the standard floating card. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.Card,
    onPress: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = C().mode == "dark"
    val bg = if (dark) Color(0xFF152238) else Color.White
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
