package com.callmony.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.callmony.i18n.t
import com.callmony.platform.Platform
import com.callmony.theme.F
import com.callmony.ui.kit.C
import com.callmony.ui.kit.IconBtn
import com.callmony.ui.kit.Toasts
import com.callmony.ui.kit.vibrate
import com.callmony.ui.components.glassCanvas
/* Sheet & BottomSheet — the overlay containers used by every modal in the
   app (port of src/components/kit.tsx Sheet + BottomSheet). */

/* v1.6.36 — POSITION-AWARE IME LIFT (user report on the v1.6.35 fix: "now
   the save button and its container goes up higher, that it covers the
   input and other components"). Every sheet renders INSIDE the app's
   content area, which already ends one BOTTOM-NAV above the window
   bottom — a plain imePadding() lifts by the FULL keyboard height, so the
   footer band floated a whole nav higher than the keyboard top, squeezing
   the form under it (and some Samsung/OneUI builds double-apply the inset,
   floating it even higher). This modifier measures the real distance from
   the composable's bottom edge to the WINDOW bottom (the nav, exactly),
   consumes that much of the IME inset, and only then applies imePadding —
   the footer comes to rest EXACTLY on the keyboard's top edge, giving the
   form back the nav-height of dead space. Keyboard closed → IME inset is
   0 → no padding at all, byte-identical to before. */
fun Modifier.imeLiftPadding(): Modifier =
    composed {
        var belowPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        this
            .onGloballyPositioned { c ->
                val root = c.findRootCoordinates()
                val bottom =
                    (root.size.height - (c.positionInWindow().y + c.size.height)).toInt().coerceAtLeast(0)
                if (belowPx != bottom) belowPx = bottom
            }
            .consumeWindowInsets(
                androidx.compose.foundation.layout.PaddingValues(
                    bottom = with(density) { belowPx.toDp() },
                )
            )
            .imePadding()
    }

/** Full-screen sheet — cheap fade animation (v1.6.10 perf fix). */
@Composable
fun Sheet(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    onLongPressSubtitle: (() -> Unit)? = null,
    /** v1.6.18 — tapping the subtitle (e.g. the phone number in the contact
     *  navbar) performs this action (place the call); long-press still copies
     *  (onLongPressSubtitle). A small call icon rides beside the text.
     *  v1.6.36 — the icon can carry its OWN action (onSubtitleIconTap):
     *  the contact navbar's number text now opens the phone-number options
     *  (user report) while the little icon keeps dialing — old features
     *  intact, one tap apart. */
    onSubtitleTap: (() -> Unit)? = null,
    onSubtitleIconTap: (() -> Unit)? = null,
    avatar: (@Composable () -> Unit)? = null,
    headerRight: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    scrollable: Boolean = true,
    content: @Composable () -> Unit,
) {
    val C = C()
    // v1.6.15: the native back button closes the sheet itself (inner
    // handlers take precedence over the shell's root back handler)
    com.callmony.ui.kit.AppBackHandler(enabled = true) { onBack() }
    // v1.6.26 — sheets render INSIDE the shell's content area (between the
    // app header and the always-visible bottom nav), so they no longer pad
    // the system bars themselves — the shell already did it above, and the
    // nav below owns the gesture-bar inset.
    // v1.6.35 — IME LIFT; v1.6.36 — position-aware lift.
    // v1.6.52 — LIGHT GLASS: the sheet canvas is the glow gradient.
    Column(
        Modifier
            .fillMaxSize()
            .glassCanvas()
            .imeLiftPadding()
    ) {
        // header: back chevron · avatar · title (auto-shrink) · actions
        // v1.6.25 — compact (redesign spec): 48dp header band, 2dp band
        // padding — every control preserved, just less air.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconBtn("arrow-left", onBack, size = 40)
            if (avatar != null) {
                Box(Modifier.padding(start = 2.dp)) { avatar() }
            }
            Column(Modifier.weight(1f).padding(start = if (avatar != null) 6.dp else 4.dp)) {
                Text(
                    title,
                    // v1.6.53 — titles are 20sp SemiBold (spec 6)
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = C.ink,
                    // v1.6.54 — ROUND 2, SPEC 3: titles may wrap to two
                    // lines (maxLines = 2, softWrap) — never ellipsize
                    // ("Calcul…" was cut off). Only names may ellipsize.
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Visible,
                    lineHeight = 24.sp,
                )
                if (!subtitle.isNullOrEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LongPressText(
                            text = subtitle,
                            onLong = onLongPressSubtitle,
                            onTap = onSubtitleTap,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        // v1.6.18 — the call icon: its OWN action when set
                        // (v1.6.36 — the contact navbar: the icon dials while
                        // the number text beside it opens the options);
                        // long-press on the text copies it (as before).
                        if (onSubtitleTap != null) {
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(C.brandSoft)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { (onSubtitleIconTap ?: onSubtitleTap)() },
                                contentAlignment = Alignment.Center,
                            ) {
                                com.callmony.ui.kit.MdiIcon("phone", 15, C.brand)
                            }
                        }
                    }
                }
            }
            if (headerRight != null) {
                Row(verticalAlignment = Alignment.CenterVertically) { headerRight() }
            }
        }
        // body
        // v1.6.14: footer-less sheets (ContactDetail, Calculator…) used to
        // let their content run down to the screen edge — under the phone's
        // gesture/navigation bar. v1.6.26: the always-visible app nav now
        // terminates the content area above the system bar, so the old
        // navigationBarsPadding here would just add dead air above it.
        Box(
            Modifier
                .weight(1f)
        ) {
            if (scrollable) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp)
                        .padding(top = 6.dp, bottom = 24.dp),
                ) { content() }
            } else {
                Column(Modifier.fillMaxSize()) { content() }
            }
        }
        if (footer != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) { footer() }
        }
    }
}

/** Small text that copies itself on long-press (400 ms, like RN edition).
 *  v1.6.18: optional onTap — when set, a plain tap performs that action
 *  (the contact navbar number: tap = call, long-press = copy). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LongPressText(
    text: String,
    onLong: (() -> Unit)? = null,
    onTap: (() -> Unit)? = null,
    color: Color? = null,
    fontSize: Int = 13,
    maxLines: Int = 1,
    modifier: Modifier = Modifier,
) {
    val C = C()
    // v1.6.25 — default indication (visible tap feedback) when tappable
    val tappable = onTap != null || onLong != null
    Box(
        modifier.combinedClickable(
            enabled = tappable,
            onClick = { if (onTap != null) onTap() },
            onLongClick = {
                vibrate(longArrayOf(20))
                if (onLong != null) onLong()
                else {
                    Platform.share.copyText(text)
                    Toasts.toast(t("act.copied"))
                }
            },
        ),
    ) {
        Text(
            text,
            fontSize = fontSize.sp,
            color = color ?: C.sub,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Partial bottom sheet — grabber, whole header tappable, four close paths.
 *  v1.6.17: the sheet now carries its OWN full-size scrim and pins itself to
 *  the BOTTOM-CENTER of the host. Previously every BottomSheet rendered as a
 *  bare Column — inside OverlayHost's plain Box that meant TOP-START, so the
 *  PIN keypad, restore sheet and all long-press menus appeared stuck to the
 *  top-left corner. Wrapping every call site was error-prone; the container
 *  now guarantees correct placement for all ~25 usages. */
@Composable
fun BottomSheet(
    title: String? = null,
    onClose: () -> Unit,
    maxHeightPct: Float = 0.86f,
    noScroll: Boolean = false,
    headerRight: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val C = C()
    // v1.6.15: the native back button closes the bottom sheet first
    com.callmony.ui.kit.AppBackHandler(enabled = true) { onClose() }
    // v1.6.54 — ROUND 2, SPEC 1: the sheet surface is 100% OPAQUE white
    // (containerColor = Color.White, never alpha — the @96% glass let the
    // page behind read through: "Credit Nov 7…", "People", contact names
    // were visible through Add entry / Keypad / More). The scrim is the
    // spec's Ink @40%. 36dp top corners + brand hairline + brand-tinted
    // shadow unchanged.
    val sheetShape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    Box(
        Modifier
            .fillMaxSize()
            .background(C.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClose() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // absorb taps so touching the sheet itself never dismisses
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {}
                .shadow(20.dp, sheetShape, ambientColor = C.brand.copy(alpha = 0.18f), spotColor = C.brand.copy(alpha = 0.18f))
                .clip(sheetShape)
                .background(if (C.mode == "dark") Color(0xFF101D33) else Color.White)
                // v1.6.35 — IME LIFT: a bottom sheet carrying a text input
                // (custom category, quick add…) rises above the keyboard
                // instead of being covered by it.
                // v1.6.36 — POSITION-AWARE (see imeLiftPadding): it rests
                // exactly on the keyboard top, never a nav-band higher.
                .imeLiftPadding(),
                // v1.6.26: no navigationBarsPadding — BottomSheets anchor on
                // top of the always-visible app nav inside the content area;
                // the nav owns the system gesture-bar inset.
        ) {
            // grabber row — tapping it closes the sheet
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onClose() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(44.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(C.line),
                )
            }
            if (title != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        // v1.6.53 — sheet titles are 20sp SemiBold (spec 6)
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = C.ink,
                        modifier = Modifier.weight(1f),
                        // v1.6.54 — ROUND 2, SPEC 3: two lines, no ellipsis
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Visible,
                        lineHeight = 24.sp,
                    )
                    if (headerRight != null) headerRight() else IconBtn("close", onClose, size = 36)
                }
            }
            Box(Modifier.weight(1f, fill = false).heightIn(max = (maxHeightPct * 760).dp)) {
                if (noScroll) {
                    Column(Modifier.fillMaxWidth()) { content() }
                } else {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp)
                            .padding(bottom = 18.dp),
                    ) { content() }
                }
            }
            // NOTE: no Toaster here — the single shell-level Toaster in
            // CallmonyApp renders after this sheet in z-order, so toasts show on
            // top of every sheet. A second instance here duplicated every toast.
        }
    }
}

/** Scrim wrapper for half-modals (KeypadSheet, pickers…) — tap dismisses. */
@Composable
fun SheetScrim(onDismiss: () -> Unit, fill: Boolean = false, content: @Composable () -> Unit) {
    val C = C()
    Box(
        Modifier
            .fillMaxSize()
            .background(C.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
    ) {
        Box(
            Modifier
                .align(if (fill) Alignment.Center else Alignment.BottomCenter)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
        ) { content() }
    }
}

/** Generic long-pressable row wrapper (650 ms like the RN cards).
 *  v1.6.25 — default indication: rows now visibly react to taps (user
 *  report: every input needs a clicked indication) — the explicit
 *  indication=null is gone; the bounded press veil comes from the app
 *  root's LocalIndication. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PressableRow(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.combinedClickable(
            onClick = onClick,
            onLongClick = {
                vibrate(longArrayOf(30))
                onLongClick?.invoke()
            },
        ),
    ) { content() }
}
