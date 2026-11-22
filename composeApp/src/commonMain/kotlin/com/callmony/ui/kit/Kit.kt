package com.callmony.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.compactMoney
import com.callmony.lib.displayName
import com.callmony.lib.initialsOf
import com.callmony.lib.money
import com.callmony.model.DeviceContact
import com.callmony.platform.Platform
import com.callmony.theme.F
import com.callmony.theme.ThemeColors
import com.callmony.theme.resolveTheme
import com.callmony.ui.icons.MdiIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/* callmony — shared UI kit (primitives, toast, confirm).
   A faithful port of the React Native edition's src/components/kit.tsx. */

@Composable
fun C(): ThemeColors = resolveTheme(AppStore.state.settings.theme)

/* ---------------- v1.6.25 — press feedback everywhere ---------------- */

/* The app is bare Compose (no MaterialTheme wrapper), so LocalIndication's
   default draws NOTHING — no tappable ever showed that it was tapped (user
   report: "every input on this app should have some indication it was
   clicked"). This is a compact bounded press indication: while pressed, a
   soft dark veil fades in over the component's own bounds; on release it
   fades out — flat and subtle (no ripple color surprises in either theme),
   works for every default-`clickable` in the tree once provided through
   LocalIndication at the app root. */
object PressIndication : androidx.compose.foundation.IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressIndicationNode(interactionSource)

    // equals/hashCode by identity — Compose caches instances with these.
    // A singleton: any constant hash satisfies the contract (equal objects
    // have equal hashes) and stays multiplatform (no System.* on native).
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 0x50524553 // "PRES"
}

private class PressIndicationNode(
    private val interactionSource: InteractionSource,
) : Modifier.Node(), DrawModifierNode {
    private val progress: Animatable<Float, AnimationVector1D> = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> progress.animateTo(
                        1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                    )
                    is PressInteraction.Release -> progress.animateTo(0f, tween(180))
                    is PressInteraction.Cancel -> progress.animateTo(0f, tween(120))
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val p = progress.value
        if (p > 0.01f) {
            // v1.6.27 — 0.16 veil (was 0.10): the v1.6.25 strength was
            // practically invisible on the near-white canvas — the user
            // reported inputs STILL didn't visibly react ("even in the
            // ledger"). v1.6.53 — LIGHT GLASS: the veil is BRAND-tinted
            // in light mode (the pressed = Brand50 spec rule for rows —
            // grey-black veils read muddy on the airy canvas); dark keeps
            // the black veil.
            val c = resolveTheme(AppStore.state.settings.theme)
            if (c.mode == "dark") drawRect(Color.Black.copy(alpha = p * 0.16f))
            else drawRect(c.brand.copy(alpha = p * 0.14f))
        }
    }
}

/* v1.6.29 — PRESS FEEDBACK FOR TEXT INPUTS (user report: "press feedback
   is not there for the input, like when I enter a quantity in the items
   ledger or in other places there is no press feedback, all the inputs
   should have press feedback"). The v1.6.25 PressIndication rides
   LocalIndication, which only `clickable`-family components consult — a
   BasicTextField never asks for an indication, so tapping ANY text field
   showed zero reaction. pressVeil() is a self-contained OBSERVER for an
   input's own box: it watches pointers over its bounds WITHOUT ever
   consuming them (the field keeps every gesture — caret placement,
   long-press selection, paste menu, IME) and paints the very same 16%
   veil while a finger is down, then fades it out.
   Freeze-lesson compliant by construction: every await in the gesture
   loop SUSPENDS on a real event (no busy-spin — the v1.6.24 autolock
   lesson) and the pressed flag is written exactly once per gesture
   boundary, never inside a spin (the snapshot-write lesson).
   `corner` rounds the veil to the input box's own radius (Field and the
   search rows are 14dp boxes) so the feedback never bleeds past the
   field's silhouette. */
fun Modifier.pressVeil(corner: Dp = 14.dp): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val veil = remember { Animatable(0f) }
    val cornerPx = with(LocalDensity.current) { corner.toPx() }
    LaunchedEffect(pressed) {
        veil.animateTo(
            targetValue = if (pressed) 1f else 0f,
            animationSpec = if (pressed) {
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            } else tween(180),
        )
    }
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                try {
                    pressed = true
                    // observe until EVERY pointer lifts; each await suspends
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.all { change -> !change.pressed }) break
                    }
                } finally {
                    pressed = false
                }
            }
        }
        .drawWithContent {
            drawContent()
            val v = veil.value
            if (v > 0.01f) {
                drawRoundRect(
                    color = Color.Black.copy(alpha = v * 0.16f),
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                )
            }
        }
}

/* v1.6.31 — RING COLOUR ON ACTIVE INPUTS (user report: "need a ring colour
   on the inputs when they are active"). Every input box showed (and still
   shows) the same resting hairline whether it was being typed into or not,
   so the active field was invisible in a form. focusRing() is a pure
   OBSERVER modifier for an input's own box: onFocusChanged reports focus
   gained by the box OR ANY DESCENDANT (the BasicTextField inside), and a
   brand-coloured 2dp ring fades in around the box's own corner radius while
   the input is active, then fades out — no focus is requested or moved, the
   caret/IME/long-press menu are untouched, and the resting hairline border
   stays underneath. Same animation discipline as pressVeil: a terminating
   tween/spring per state change, zero infinite animations. */
fun Modifier.focusRing(corner: Dp = 14.dp, width: Dp = 2.dp): Modifier = composed {
    val brand = C().brand
    var focused by remember { mutableStateOf(false) }
    val ring = remember { Animatable(0f) }
    LaunchedEffect(focused) {
        ring.animateTo(
            targetValue = if (focused) 1f else 0f,
            animationSpec = if (focused) {
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            } else tween(160),
        )
    }
    this
        .onFocusChanged { focused = it.hasFocus }
        .drawWithContent {
            drawContent()
            val a = ring.value
            if (a > 0.01f) {
                val w = width.toPx()
                val hw = w / 2
                drawRoundRect(
                    color = brand.copy(alpha = a),
                    topLeft = androidx.compose.ui.geometry.Offset(hw, hw),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(corner.toPx(), corner.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * a),
                )
            }
        }
}

/* ---------------- hero numerals (spec 6) ---------------- */

/** v1.6.53 — HERO NUMERAL (spec 6: "Hero numerals 48-64sp ExtraLight/Light
 *  in Brand … Big numbers auto-shrink to one line; never overflow or
 *  clip"). Starts at 48sp Lexend Light in Brand and steps down through the
 *  scale until the text fits ONE line in the available width (measured
 *  with the platform text measurer — no overflow, no clip, no ellipsis).
 *  Negative values may go Danger red (the ledger's signed convention),
 *  which is a status, not a second brand. */
@Composable
fun HeroNumeral(
    text: String,
    modifier: Modifier = Modifier,
    negative: Boolean = false,
    tag: String? = null,
) {
    val C = C()
    val color = if (negative) C.red else C.brand
    val measurer = rememberTextMeasurer()
    val style = remember { TextStyle(fontWeight = FontWeight.Light) }
    // step down 48 -> 44 -> ... -> 20; pick the first size whose single
    // line fits the incoming constraint width (stepped ladder — hero
    // numerals read best on the stops; auto-shrink, never overflow/clip)
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val maxW = with(density) { maxWidth.toPx() }
        val fontSize by remember(text, maxW) {
            val ladder = listOf(48f, 44f, 40f, 36f, 32f, 28f, 24f, 20f)
            var chosen = 20f
            for (s in ladder) {
                val w = measurer.measure(
                    text,
                    style = style.copy(fontSize = s.sp),
                ).size.width.toFloat()
                if (w <= maxW) { chosen = s; break }
            }
            mutableStateOf(chosen.sp)
        }
        Text(
            text,
            fontSize = fontSize,
            fontWeight = FontWeight.Light,
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        )
    }
}

/* ---------------- toast ---------------- */

data class ToastItem(
    val id: Long,
    val msg: String,
    val kind: String, // ok | error | info
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
)

object Toasts {
    val items = androidx.compose.runtime.mutableStateListOf<ToastItem>()

    fun push(msg: String, kind: String = "ok", actionLabel: String? = null, action: (() -> Unit)? = null) {
        items.add(ToastItem(nowMs(), msg, kind, actionLabel, action))
    }

    fun drop(id: Long) {
        items.removeAll { it.id == id }
    }

    fun toast(msg: String, kind: String = "ok") = push(msg, kind)

    fun toastUndo() {
        val u = AppStore.state.lastUndo ?: return
        push(u.label, "info", t("act.undo")) { AppStore.undo() }
    }

    private fun nowMs(): Long = currentTimeMillisCompat()
}

internal expect fun currentTimeMillisCompat(): Long

@Composable
fun Toaster() {
    val C = C()
    val items = Toasts.items
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) {
            val first = items.first()
            delay(if (first.action != null) 5000 else 3000)
            Toasts.drop(first.id)
        }
    }
    if (items.isEmpty()) return
    val first = items.first()
    // fillMaxSize is essential: without it this Box wraps its content height
    // and the parent Box places it at the TOP — under the status bar / camera
    // cutout (the "toast covers the clock" bug). With fillMaxSize the
    // BottomCenter alignment pins the toast above the bottom nav, like RN's
    // `position: absolute; bottom: 96`.
    // v1.6.26: the Toaster now lives INSIDE the content area (between the
    // app header and the always-visible bottom nav), so a small 10dp offset
    // floats it just above the nav — the old 96dp was calibrated for the
    // full-screen host.
    Box(
        Modifier
            .fillMaxSize()
            .padding(bottom = 10.dp + RootBottomOverlays.lift.value)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            Modifier
                .widthIn(max = 380.dp)
                .shadow(14.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
                .clip(RoundedCornerShape(18.dp))
                // v1.6.52 — LIGHT GLASS toast: deep-ink glass pill.
                .background(if (C.mode == "dark") Color(0xFF1B2B45) else Color(0xFF152238))  // v1.6.54 — opaque toast
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
                // v1.6.35 — THE TOAST NO LONGER EATS TAPS. The pill used to
                // carry an EMPTY clickable — it swallowed every touch (the
                // Toaster floats bottom-center, exactly over sheet footers)
                // and did nothing with it, so tapping Save/Use-value within
                // the 3s toast window silently did NOTHING. The pill is now
                // click-through (no pointer modifier = hit-test skips it);
                // only an action's own label (Undo) stays tappable, and the
                // auto-dismiss (3s/5s) is unchanged.
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                first.msg,
                color = Color(0xFFEAF1FB),
                fontSize = 14.sp,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (first.action != null) {
                Text(
                    first.actionLabel ?: t("act.undo"),
                    color = Color(0xFF6FB2FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        first.action?.invoke()
                        Toasts.drop(first.id)
                    },
                )
            }
        }
    }
}

/* ---------------- confirm ---------------- */

object Confirm {
    data class Req(
        val title: String,
        val message: String? = null,
        val confirmLabel: String? = null,
        val danger: Boolean = false,
        val resolve: (Boolean) -> Unit,
    )

    val req = mutableStateOf<Req?>(null)

    fun ask(title: String, message: String? = null, confirmLabel: String? = null, danger: Boolean = false, onResult: (Boolean) -> Unit) {
        req.value = Req(title, message, confirmLabel, danger, onResult)
    }

    fun answer(v: Boolean) {
        req.value?.resolve?.invoke(v)
        req.value = null
    }
}

@Composable
fun ConfirmHost() {
    val req = Confirm.req.value ?: return
    val C = C()
    Box(
        Modifier
            .fillMaxSize()
            .background(C.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { Confirm.answer(false) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 340.dp)
                .shadow(24.dp, RoundedCornerShape(28.dp), ambientColor = C.brand.copy(alpha = 0.15f), spotColor = C.brand.copy(alpha = 0.15f))
                .clip(RoundedCornerShape(28.dp))
                // v1.6.54 — ROUND 2, SPEC 1: dialogs are 100% OPAQUE white.
                .background(if (C.mode == "dark") Color(0xFF152238) else Color.White)
                .border(1.dp, if (C.mode == "dark") Color(0x333B94FF) else C.brand.copy(alpha = 0.10f), RoundedCornerShape(28.dp))
                .padding(20.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
        ) {
            Text(req.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
            if (!req.message.isNullOrEmpty()) {
                Text(req.message, fontSize = 15.sp, color = C.sub, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn(label = t("act.cancel"), variant = "ghost", onPress = { Confirm.answer(false) }, modifier = Modifier.weight(1f))
                Btn(
                    label = req.confirmLabel ?: t("misc.confirm"),
                    variant = if (req.danger) "danger" else "primary",
                    onPress = { Confirm.answer(true) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/* ---------------- host sheets ---------------- */

/* v1.6.18 — THE "MODALS OPEN AT THE TOP" FIX.
 * Bottom sheets that were composed INSIDE a scrollable (the ledger entry
 * card's Record-payment, the group menus, the list row payment …) drew
 * their full-size scrim against an UNBOUNDED height constraint, so the
 * sheet could not pin to the bottom of the SCREEN — it appeared stuck to
 * the top of its little container or floated mid-scroll. These sheets now
 * open through a ROOT-LEVEL slot (exactly like Toaster / ConfirmHost),
 * where the host Box is the full-size app root: bottom-center placement
 * is guaranteed no matter how deeply nested the caller is. */
object HostSheets {
    data class Entry(val id: Long, val content: @Composable (close: () -> Unit) -> Unit)

    val stack = androidx.compose.runtime.mutableStateListOf<Entry>()
    private var nextId = 1L

    fun push(content: @Composable (close: () -> Unit) -> Unit) {
        val id = nextId++
        stack.add(Entry(id, content))
    }

    fun pop(id: Long) {
        stack.removeAll { it.id == id }
    }
}

/* v1.6.41 — ROOT-LEVEL BOTTOM OVERLAYS: the shell renders this slot AFTER
 *  the always-visible bottom nav — the ONLY place that can draw over it,
 *  exactly like the system IME does (user report: "the number keypads
 *  should cover the bottom navigation just like the normal keyboard does
 *  it"). The calculator's number keypad lives here while number mode is
 *  armed. `lift` is how much of the CONTENT area's bottom band the
 *  overlay covers, so the Toaster (and anything else anchoring on the
 *  content bottom) can rise above it and stay visible.
 *  v1.6.43 — `overlayTopPx`: the overlay's own TOP edge in WINDOW pixels,
 *  reported by the overlay through onGloballyPositioned (-1 = none). With
 *  it, any composable inside the content area can measure EXACTLY how much
 *  of its own bottom band the overlay covers (its own bottom edge minus
 *  the overlay's top) — no inset APIs, no assumptions about the system nav
 *  bar, immune to the shell's inset-consumption chains. */
object RootBottomOverlays {
    val content = androidx.compose.runtime.mutableStateOf<(@Composable () -> Unit)?>(null)
    val lift = androidx.compose.runtime.mutableStateOf(0.dp)
    val overlayTopPx = androidx.compose.runtime.mutableStateOf(-1f)
}

/* v1.6.47 — THE ROOT FAB SLOT (user report: "when the ledger tab is
   clicked or the ledger is open, it should show cercular + btn on bottom
   right side that opens new canvas"): the ledger's circular ＋ pins to the
   content area's BOTTOM-END while a ledger surface is showing — above the
   tab screens AND above the whole overlay stack (the contact detail is an
   overlay that embeds its own LedgerSection), but never above the sheets
   that render after it. The LEDGER SECTION itself publishes its FAB here
   (DisposableEffect — leaving the ledger segment clears it), and the
   SHELL decides visibility: while any overlay sits ABOVE a ledger surface
   (the entry editor, search, settings…) the slot stays empty-handed, so
   the ＋ never floats over a screen it does not belong to. The FAB's own
   Box is transparent and consumes no input — only the circle is
   clickable. */
object RootFab {
    val content = androidx.compose.runtime.mutableStateOf<(@Composable () -> Unit)?>(null)
}

@Composable
fun HostSheetsHost() {
    // v1.6.35 — the same stale-keyboard rule as OverlayHost: a host sheet
    // (attach picker, group menus, record payment…) opening on top of a
    // field with the IME up clears focus and hides the keyboard, so the
    // sheet is never covered by it (user report). No exemptions — none
    // of these autofocus a field.
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(HostSheets.stack.size) {
        if (HostSheets.stack.isNotEmpty()) {
            focus.clearFocus()
            keyboard?.hide()
        }
    }
    val snapshot = HostSheets.stack.toList()
    for (s in snapshot) {
        androidx.compose.runtime.key(s.id) {
            s.content { HostSheets.pop(s.id) }
        }
    }
}

/* ---------------- money ---------------- */

/** v1.6.54 — ROUND 2, SPEC 3: money values NEVER truncate. Money autosizes
 *  (measure-and-shrink) between MONEY_AUTOSIZE_MIN (14sp) and 44sp,
 *  maxLines = 1, so every amount always fits its width — "503886.2…",
 *  "Income 505272.…" and the hero "505272.8M Br" touching the screen edge
 *  were all clipped money. The step ladder mirrors HeroNumeral's grammar. */
const val MONEY_AUTOSIZE_MIN = 14
const val MONEY_AUTOSIZE_MAX = 44

@Composable
fun Money(
    value: Double,
    currency: String? = null,
    signed: Boolean = false,
    colorize: Boolean = false,
    size: Int = 15,
    weight: FontWeight = FontWeight.SemiBold,
    compact: Boolean = false,
) {
    val C = C()
    if (AppStore.state.settings.hideBalances) {
        Text("•••", fontSize = size.sp, fontWeight = weight, color = C.faint)
        return
    }
    val cur = currency ?: AppStore.state.settings.currency
    val text = if (compact && abs(value) >= 100000) compactMoney(value, cur, signed) else money(value, cur, signed)
    val color = if (colorize) { if (value > 0) C.green else if (value < 0) C.red else C.sub } else C.ink
    AutoFitText(
        text = text,
        maxFontSize = if (size <= MONEY_AUTOSIZE_MAX) size.sp else MONEY_AUTOSIZE_MAX.sp,
        minFontSize = MONEY_AUTOSIZE_MIN.sp,
        color = color,
        fontWeight = weight,
        maxLines = 1,
    )
}

/** v1.6.54 — measure-and-shrink text (round 2, spec 3): starts at
 *  maxFontSize and steps down the sp ladder until it fits ONE line in the
 *  available width; at the floor it lets the layout wrap/size naturally
 *  rather than clip. Shared by Money and any label that must never
 *  truncate. */
@Composable
fun AutoFitText(
    text: String,
    maxFontSize: androidx.compose.ui.unit.TextUnit,
    minFontSize: androidx.compose.ui.unit.TextUnit,
    color: Color,
    fontWeight: FontWeight,
    maxLines: Int = 1,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    val measurer = rememberTextMeasurer()
    val style = remember(fontWeight) { TextStyle(fontWeight = fontWeight) }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val maxW = with(density) { maxWidth.toPx() }
        // step down in 2sp units from max to min; fall back to the min size
        // (never ellipsize — the last resort is the smallest size on one
        // line; a width so small that even that cannot fit simply renders
        // the min-size line and lets the parent wrap content)
        val chosen by remember(text, maxW, maxFontSize, minFontSize) {
            val maxSp = maxFontSize.value
            val minSp = minFontSize.value
            var chosenSp = minSp
            var s = maxSp
            while (s >= minSp) {
                val w = measurer.measure(
                    text,
                    style = style.copy(fontSize = s.sp),
                ).size.width.toFloat()
                if (w <= maxW) { chosenSp = s; break }
                s -= 2f
            }
            mutableStateOf(chosenSp.sp)
        }
        Text(
            text,
            fontSize = chosen,
            fontWeight = fontWeight,
            color = color,
            maxLines = maxLines,
            softWrap = maxLines > 1,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/* ---------------- avatar ---------------- */

@Composable
fun Avatar(contact: DeviceContact?, size: Int = 46, photo: String? = null) {
    // v1.6.54 — ROUND 2, SPEC 5: every avatar is the BRAND gradient
    // (BrandLight -> BrandDark) with white 600-weight initials — contrast
    // far above 4.5:1 on both gradient stops. The random per-contact
    // pastel palette (white letters on washed-out hues, hard to read and
    // not brand colored) is gone.
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.26f).dp))
            .background(
                Brush.linearGradient(listOf(C().brandLight, C().brandDark))
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initialsOf(contact),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = maxOf(size * 0.36, 13.0).sp,
        )
    }
}

fun parseHexColor(hex: String): Color {
    val h = hex.removePrefix("#")
    return when (h.length) {
        6 -> Color(
            red = h.substring(0, 2).toInt(16) / 255f,
            green = h.substring(2, 4).toInt(16) / 255f,
            blue = h.substring(4, 6).toInt(16) / 255f,
            alpha = 1f,
        )
        8 -> Color(
            alpha = h.substring(0, 2).toInt(16) / 255f,
            red = h.substring(2, 4).toInt(16) / 255f,
            green = h.substring(4, 6).toInt(16) / 255f,
            blue = h.substring(6, 8).toInt(16) / 255f,
        )
        else -> Color.Gray
    }
}

/* ---------------- buttons / chips / cards ---------------- */

@Composable
fun MdiIcon(name: String, size: Int, color: Color, modifier: Modifier = Modifier) {
    // v1.6.52 — LIGHT GLASS: stroke-first. The Lucide stroke cut (1.8dp,
    // round caps) renders when the name has one; the legacy filled MDI
    // vector stays as fallback for names without a stroke mapping.
    val vector = com.callmony.ui.icons.LucideIcons.get(name)
        ?: com.callmony.ui.icons.MdiIcons[name]
    androidx.compose.foundation.Image(
        imageVector = vector,
        contentDescription = name,
        modifier = modifier.size(size.dp),
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color),
    )
}

@Composable
fun Btn(
    label: String? = null,
    icon: String? = null,
    onPress: () -> Unit,
    variant: String = "primary",
    small: Boolean = false,
    disabled: Boolean = false,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val C = C()
    val off = disabled || loading
    // v1.6.52 — LIGHT GLASS: primary is a PILL with the horizontal
    // BrandLight -> BrandDark gradient, white 500-weight text, min height
    // 52dp (36dp small), brand-tinted shadow, press = scale 0.95; secondary
    // is the glass/white pill with Brand text; soft/danger keep their
    // semantic fills.
    val shape = RoundedCornerShape(percent = 50)
    val fg = when (variant) {
        "primary", "danger" -> C.onBrand
        "soft" -> C.brand
        "secondary" -> C.brand
        else -> C.sub
    }
    val src = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScaleCompat(src)
            .then(
                when (variant) {
                    "primary" -> Modifier
                        .shadow(12.dp, shape, ambientColor = C.brand.copy(alpha = 0.25f), spotColor = C.brand.copy(alpha = 0.25f))
                        .clip(shape)
                        .background(Brush.horizontalGradient(listOf(C.brandLight, C.brandDark)))
                    "danger" -> Modifier
                        .shadow(12.dp, shape, ambientColor = C.red.copy(alpha = 0.22f), spotColor = C.red.copy(alpha = 0.22f))
                        .clip(shape)
                        .background(C.red)
                    "secondary" -> Modifier
                        .shadow(8.dp, shape, ambientColor = C.brand.copy(alpha = 0.10f), spotColor = C.brand.copy(alpha = 0.10f))
                        .clip(shape)
                        .background(Color.White)  // v1.6.54 — opaque (round 2, spec 1)
                        .border(1.dp, C.line, shape)
                    "soft" -> Modifier.clip(shape).background(C.brandSoft)
                    else -> Modifier.clip(shape)
                }
            )
            .clickable(interactionSource = src, indication = null, enabled = !off) { onPress() }
            .padding(horizontal = if (small) 16.dp else 22.dp)
            .heightIn(min = if (small) 36.dp else 52.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(17.dp),
                strokeWidth = 2.dp,
                color = fg,
            )
        } else if (icon != null) {
            MdiIcon(icon, 17, fg)
        }
        if (label != null) {
            Text(
                label,
                color = fg,
                fontSize = if (small) 13.sp else 15.sp,
                fontWeight = FontWeight.Medium,
                // v1.6.54 — ROUND 2, SPEC 3: button labels wrap to TWO
                // lines (softWrap + a height that fits two lines), never
                // ellipsize — "WhatsA…" and friends were cut action labels.
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Visible,
                textAlign = TextAlign.Center,
                lineHeight = if (small) 16.sp else 19.sp,
                modifier = Modifier.widthIn(max = 220.dp),
            )
        }
    }
}

/** Scale-only press motion for Btn (the veil comes from LocalIndication). */
private fun Modifier.pressScaleCompat(src: MutableInteractionSource): Modifier = composed {
    val pressed by src.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = tween(if (pressed) 120 else 180),
        label = "btnScale2",
    )
    this.scale(scale)
}

@Composable
fun IconBtn(
    icon: String,
    onPress: () -> Unit,
    size: Int = 40,
    color: Color? = null,
    bg: Color? = null,
    danger: Boolean = false,
) {
    val C = C()
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bg ?: Color.Transparent)
            .clickable { onPress() },
        contentAlignment = Alignment.Center,
    ) {
        MdiIcon(icon, maxOf(size / 2, 18), color ?: (if (danger) C.red else C.sub))
    }
}

@Composable
fun Chip(
    label: String,
    active: Boolean = false,
    onPress: (() -> Unit)? = null,
    icon: String? = null,
    danger: Boolean = false,
    count: Int? = null,
    compact: Boolean = false,
    iconOnly: Boolean = false,
    /** v1.6.26 — per-chip icon/text colour when INACTIVE (Segmented tabs
     *  with icons, e.g. the bank tab's blue tint). */
    tint: Color? = null,
) {
    val C = C()
    val shape = RoundedCornerShape(999.dp)
    // v1.6.53 — LIGHT GLASS chips, exactly per spec 7: selected = Brand50
    // fill + Brand text; unselected = WHITE fill + Muted text (the
    // v1.6.52 neutral-grey chip is gone — chips are white on the airy
    // canvas, never grey). The per-tab tint keeps working as the active
    // accent.
    val accent = tint ?: C.brand
    val bg = if (active) C.brandSoft else Color.White
    val body: @Composable () -> Unit = {
        Row(
            Modifier
                .then(if (!iconOnly) Modifier.border(1.dp, if (active) accent.copy(alpha = 0.35f) else C.line, shape) else Modifier)
                .background(bg, shape)
                .padding(
                    horizontal = when { iconOnly -> 6.dp; compact -> 9.dp; else -> 13.dp },
                    vertical = when { iconOnly -> 4.dp; compact -> 4.dp; else -> 7.dp },
                )
                .heightIn(min = if (compact) 28.dp else 36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
        ) {
            if (icon != null) {
                MdiIcon(icon, when { iconOnly -> 15; compact -> 13; else -> 15 }, if (active) accent else if (danger) C.red else (tint ?: C.sub))
            }
            if (!iconOnly) {
                Text(
                    label,
                    fontSize = if (compact) 12.sp else 14.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) accent else if (danger) C.red else (tint ?: C.sub),
                    // v1.6.54 — ROUND 2, SPEC 3: chip labels wrap to TWO
                    // lines, never ellipsize ("Re", "Accounts", "KES", "SA"
                    // were clipped chips). Chips in a strip keep one line —
                    // only standalone chips wrap.
                    maxLines = if (compact) 1 else 2,
                    softWrap = !compact,
                    overflow = TextOverflow.Visible,
                    lineHeight = if (compact) 14.sp else 17.sp,
                    textAlign = TextAlign.Center,
                )
            }
            if (count != null && count > 0) {
                Box(
                    Modifier
                        .widthIn(min = if (compact || iconOnly) 16.dp else 20.dp)
                        .height(if (compact || iconOnly) 16.dp else 20.dp)
                        .clip(CircleShape)
                        .background(if (active) accent else if (danger) C.red else C.brand),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (count > 9) "9+" else "$count",
                        fontSize = if (compact || iconOnly) 11.sp else 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
    if (onPress != null) {
        Box(Modifier.clickable { onPress() }) { body() }
    } else body()
}

@Composable
fun Card(modifier: Modifier = Modifier, onPress: (() -> Unit)? = null, children: @Composable () -> Unit) {
    val C = C()
    // v1.6.52 — LIGHT GLASS: the standard card is a floating glass card —
    // white @78% over the glow canvas, 1dp brand@10% hairline, 24dp radius,
    // brand-tinted shadow (~12dp; 20dp is reserved for hero cards).
    val shape = RoundedCornerShape(24.dp)
    val bg = if (C.mode == "dark") Color(0xFF152238) else Color.White  // v1.6.54 — opaque cards (round 2)
    val borderColor = if (C.mode == "dark") Color(0x333B94FF) else C.brand.copy(alpha = 0.10f)
    val shadowColor = C.brand.copy(alpha = if (C.mode == "dark") 0.28f else 0.12f)
    if (onPress != null) {
        Box(Modifier.clip(shape).clickable { onPress() }) {
            Column(
                modifier
                    .shadow(12.dp, shape, ambientColor = shadowColor, spotColor = shadowColor)
                    .clip(shape)
                    .background(bg)
                    .border(1.dp, borderColor, shape)
                    .padding(12.dp)
            ) { children() }
        }
    } else {
        Column(
            modifier
                .shadow(12.dp, shape, ambientColor = shadowColor, spotColor = shadowColor)
                .clip(shape)
                .background(bg)
                .border(1.dp, borderColor, shape)
                .padding(12.dp)
        ) { children() }
    }
}

@Composable
fun SectionHeader(title: String, right: @Composable (() -> Unit)? = null, subtitle: String? = null) {
    val C = C()
    Row(
        Modifier.padding(start = 0.dp, top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            // v1.6.53 — section titles are 14sp Medium (spec 6); the
            // ExtraBold shout is gone — Light Glass is calm.
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = C.ink)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, fontSize = 11.sp, color = C.sub, modifier = Modifier.padding(top = 1.dp))
            }
        }
        right?.invoke()
    }
}

@Composable
fun EmptyState(
    icon: String,
    title: String,
    hint: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val C = C()
    // v1.6.19 — THE "EMPTY STATE SITS TOP-LEFT" FIX. The old bare Column
    // (padding only) started at the TOP-LEFT of whatever container it was
    // in — screens with a sized content area (Recents "No matching
    // contacts", Reminders, Notes, Banks, picker…) showed the mint circle
    // + heading + hint glued to the top-left corner. Now the state fills
    // its container and centers. In scrollables (LazyColumn items, vertical
    // scroll columns) the height constraint is unbounded, where fillMaxSize
    // falls back to wrap-content — so list-empty states keep their natural
    // in-flow placement and only center horizontally (exactly the RN
    // behavior in both contexts).
    Box(
        Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center),
    ) {
        Column(
            Modifier.padding(vertical = 36.dp, horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(C.brandSoft),
                contentAlignment = Alignment.Center,
            ) { MdiIcon(icon, 30, C.brand) }
            Text(title, fontSize = F.body.sp, fontWeight = FontWeight.Bold, color = C.ink, modifier = Modifier.padding(top = 12.dp))
            if (!hint.isNullOrEmpty()) {
                Text(hint, fontSize = F.small.sp, color = C.sub, modifier = Modifier.padding(top = 4.dp), textAlign = TextAlign.Center, lineHeight = 20.sp)
            }
            if (actionLabel != null && onAction != null) {
                Box(Modifier.padding(top = 14.dp)) { Btn(label = actionLabel, onPress = onAction, small = true) }
            }
        }
    }
}

/** Segmented — compact tab strip of Chips (webapp style: "Ledger · Lists · …").
 *  v1.6.14: chips are COMPACT (28 dp tall, 12 sp) so five-to-six tabs fit
 *  side by side on a phone without crowding their neighbours.
 *  v1.6.17: the strip carries its OWN 5 dp vertical padding so it can never
 *  sit flush against the container above/below it — the "tabs overlap with
 *  the container" report — no matter whether the call site remembers to
 *  add spacing.
 *  v1.6.26 — TABS WITH ICONS (+ optional per-tab colour): `iconOf` supplies
 *  a glyph per key (contact detail: Ledger/Lists/Notes/Bank/Reminders/Info),
 *  and `tintOf` overrides a tab's accent (the Bank tab gets its own colour
 *  — user request). Both optional and positioned BEFORE onChange so every
 *  existing trailing-lambda call site keeps compiling unchanged. */
@Composable
fun Segmented(
    options: List<Triple<String, String, Int?>>,
    value: String,
    iconOf: ((String) -> String)? = null,
    tintOf: ((String) -> Color?)? = null,
    onChange: (String) -> Unit,
) {
    // v1.6.54 — ROUND 2, SPEC 4: the tab strip rides the shared ScrollRow
    // (real DstIn edge fades + 16dp content padding) — tabs were clipped
    // with no hint before.
    com.callmony.ui.components.ScrollRow(
        Modifier
            .padding(top = 5.dp, bottom = 5.dp)
            .fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 0.dp, end = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for ((key, label, count) in options) {
                Chip(
                    label = label,
                    active = value == key,
                    count = count,
                    compact = true,
                    onPress = { onChange(key) },
                    icon = iconOf?.invoke(key),
                    tint = tintOf?.invoke(key),
                )
            }
        }
    }
}

/** v1.6.17 — live countdown to (or past) a due moment, e.g. "in 2h 15m"
 *  or "3h late". Ticks every 30 s; green while ahead, red once overdue.
 *  Used on reminder cards and anywhere a due moment is shown. */
@Composable
fun LiveCountdown(dueAt: Long, fontSize: androidx.compose.ui.unit.TextUnit? = null, bold: Boolean = false) {
    val C = C()
    var tick by remember { mutableStateOf(com.callmony.lib.now()) }
    LaunchedEffect(dueAt) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            tick = com.callmony.lib.now()
        }
    }
    val left = dueAt - tick
    Text(
        com.callmony.lib.timeLeftStr(dueAt, tick),
        fontSize = fontSize ?: F.tiny.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
        color = if (left >= 0) C.green else C.red,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Field(
    label: String? = null,
    value: String,
    onChangeText: (String) -> Unit,
    placeholder: String? = null,
    keyboardType: String? = null,
    multiline: Boolean = false,
    right: @Composable (() -> Unit)? = null,
    autoFocus: Boolean = false,
    onSubmit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val C = C()
    // v1.6.52 — LIGHT GLASS text fields (spec: "transparent background,
    // bottom hairline, focus = Brand underline + subtle Brand glow. Never
    // use the default filled M3 field"). The box is transparent over the
    // glass canvas; a 1dp Line hairline underlines it, and the focus ring
    // (v1.6.31) now draws a Brand UNDERLINE glow instead of a full box.
    var focused by remember { mutableStateOf(false) }
    val glow by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(160),
        label = "fieldGlow",
    )
    Column(modifier.padding(bottom = 12.dp)) {
        if (!label.isNullOrEmpty()) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = C.sub, modifier = Modifier.padding(bottom = 6.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.hasFocus }
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressVeil(corner = 10.dp)
                    .padding(horizontal = 4.dp)
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onChangeText,
                    textStyle = TextStyle(fontSize = F.body.sp, color = C.ink),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (keyboardType) {
                            "numeric" -> KeyboardType.Number
                            "phone-pad" -> KeyboardType.Phone
                            "email-address" -> KeyboardType.Email
                            "decimal-pad" -> KeyboardType.Decimal
                            else -> KeyboardType.Text
                        },
                    ),
                    singleLine = !multiline,
                    cursorBrush = SolidColor(C.brand),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = if (multiline) 10.dp else 0.dp),
                    decorationBox = { inner ->
                        if (value.isEmpty() && !placeholder.isNullOrEmpty()) {
                            Row {
                                Text(placeholder, fontSize = F.body.sp, color = C.faint)
                            }
                            Box {}
                        } else inner()
                    },
                )
                right?.invoke()
            }
            // the underline: resting Line hairline; focused = 2dp Brand +
            // soft brand glow riding just under the text baseline area
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(if (focused) 2.dp else 1.dp)
                    .background(if (glow > 0.5f) C.brand else C.line)
            )
            if (glow > 0.01f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(fraction = 1f)
                        .height(6.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(C.brand.copy(alpha = 0.18f * glow), Color.Transparent)
                            )
                        )
                )
            }
        }
    }
}

@Composable
fun Divider() {
    val C = C()
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .height(1.dp)
            .background(C.line)
    )
}

/** v1.6.17 — PickerField: a tappable read-only field (native date/time
 *  pickers). Mirrors Field's exact metrics (label above, 48 dp input box)
 *  so a PickerField beside a Field aligns perfectly — the old inline
 *  label-inside-the-box variant sat taller/shorter than its neighbour and
 *  the pair looked broken ("one has a label, the other doesn't, one gets
 *  pushed below"). */
@Composable
fun PickerField(
    label: String,
    value: String,
    icon: String? = null,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val C = C()
    // v1.6.52 — LIGHT GLASS: the picker mirrors Field's underline treatment
    // (transparent box, bottom hairline) so a PickerField beside a Field
    // still aligns — the v1.6.17 metrics contract, restyled.
    Column(modifier.padding(bottom = 12.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = C.sub, modifier = Modifier.padding(bottom = 6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onPress() }
                .padding(horizontal = 4.dp)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) MdiIcon(icon, 17, C.brand)
            Text(
                value,
                fontSize = F.body.sp,
                color = C.ink,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            MdiIcon("chevron-down", 18, C.faint)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
    }
}

@Composable
fun Pill(
    label: String,
    tone: String = "neutral",
    icon: String? = null,
) {
    val C = C()
    // v1.6.53 — status pills per spec 7: tinted background + matching
    // text + icon/signal, never color alone. Semantic tones (green/red/
    // amber) always carry a marker: the caller's icon when given, else a
    // small sign dot so the status survives greyscale and color-blind
    // viewing.
    val (bg, fg, semantic: Boolean) = when (tone) {
        "brand" -> Triple(C.brandSoft, C.brand, false)
        "green" -> Triple(Color(0x241d9a6b), C.green, true)
        "red" -> Triple(Color(0x21d64545), C.red, true)
        "amber" -> Triple(Color(0x24b97d10), C.amber, true)
        "blue" -> Triple(Color(0x244a7d9b), C.blue, true)
        else -> Triple(C.chip, C.sub, false)
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) MdiIcon(icon, 13, fg)
        else if (semantic) {
            // the sign dot: a 4dp filled circle in the tone's own ink —
            // the "always comes with an icon or sign, never color alone"
            // rule, applied without inventing new glyphs
            Box(Modifier.size(4.dp).clip(CircleShape).background(fg))
        }
        // v1.6.54 — round 2, spec 3: pill labels wrap to two lines, never
        // ellipsize ("Income 505272.…" was a truncated pill)
        Text(label, fontSize = F.tiny.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 2, softWrap = true, overflow = TextOverflow.Visible, lineHeight = 14.sp)
    }
}

@Composable
fun Spinner(size: Int = 22) {
    val C = C()
    CircularProgressIndicator(modifier = Modifier.size(size.dp), strokeWidth = 2.dp, color = C.brand)
}

/** helper — contact name fallback */
fun contactName(c: DeviceContact?): String = displayName(c)

fun vibrate(pattern: LongArray = longArrayOf(15)) {
    Platform.notify.vibrate(pattern)
}
