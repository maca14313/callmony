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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/* Sheet & BottomSheet — the overlay containers used by every modal in the
   app (port of src/components/kit.tsx Sheet + BottomSheet). */

/** Full-screen sheet — cheap fade animation (v1.6.10 perf fix). */
@Composable
fun Sheet(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    onLongPressSubtitle: (() -> Unit)? = null,
    avatar: (@Composable () -> Unit)? = null,
    headerRight: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    scrollable: Boolean = true,
    content: @Composable () -> Unit,
) {
    val C = C()
    Column(Modifier.fillMaxSize().background(C.canvas).statusBarsPadding()) {
        // header: back chevron · avatar · title (auto-shrink) · actions
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .height(56.dp),
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
                    fontSize = F.h2.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = C.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrEmpty()) {
                    LongPressText(
                        text = subtitle,
                        onLong = onLongPressSubtitle,
                    )
                }
            }
            if (headerRight != null) {
                Row(verticalAlignment = Alignment.CenterVertically) { headerRight() }
            }
        }
        // body
        Box(Modifier.weight(1f)) {
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
                    .background(C.canvas)
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) { footer() }
        }
    }
}

/** Small text that copies itself on long-press (400 ms, like RN edition). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LongPressText(
    text: String,
    onLong: (() -> Unit)? = null,
    color: Color? = null,
    fontSize: Int = 13,
    maxLines: Int = 1,
) {
    val C = C()
    Box(
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {},
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

/** Partial bottom sheet — grabber, whole header tappable, four close paths. */
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
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(C.card)
            .navigationBarsPadding(),
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
                    fontSize = F.h2.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = C.ink,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (headerRight != null) headerRight() else IconBtn("close", onClose, size = 36)
            }
        }
        Box(Modifier.weight(1f, fill = false)) {
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

/** Generic long-pressable row wrapper (650 ms like the RN cards). */
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
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
            onLongClick = {
                vibrate(longArrayOf(30))
                onLongClick?.invoke()
            },
        ),
    ) { content() }
}
