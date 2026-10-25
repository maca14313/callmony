package com.callmony.ui.kit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.avatarColors
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
import kotlin.math.abs

/* callmony — shared UI kit (primitives, toast, confirm).
   A faithful port of the React Native edition's src/components/kit.tsx. */

@Composable
fun C(): ThemeColors = resolveTheme(AppStore.state.settings.theme)

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
    Box(
        Modifier
            .fillMaxSize()
            .padding(bottom = 96.dp)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            Modifier
                .widthIn(max = 380.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (C.mode == "dark") Color(0xFF243230) else Color(0xFF1f2f2c))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {}
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                first.msg,
                color = Color(0xFFe8f1ef),
                fontSize = 14.sp,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (first.action != null) {
                Text(
                    first.actionLabel ?: t("act.undo"),
                    color = C.brand,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W800,
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
                .clip(RoundedCornerShape(20.dp))
                .background(C.card)
                .padding(20.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
        ) {
            Text(req.title, fontSize = 18.sp, fontWeight = FontWeight.W800, color = C.ink)
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

/* ---------------- money ---------------- */

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
    Text(text, fontSize = size.sp, fontWeight = weight, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/* ---------------- avatar ---------------- */

@Composable
fun Avatar(contact: DeviceContact?, size: Int = 46, photo: String? = null) {
    val palette = avatarColors(contact)
    val (bg, fg) = parseHexPair(palette)
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initialsOf(contact),
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = maxOf(size * 0.36, 13.0).sp,
        )
    }
}

private fun parseHexPair(p: Pair<String, String>): Pair<Color, Color> =
    parseHexColor(p.first) to parseHexColor(p.second)

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
    androidx.compose.foundation.Image(
        imageVector = MdiIcons[name],
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
    val bg = when (variant) {
        "primary" -> C.brand
        "danger" -> C.red
        "secondary" -> C.card
        "soft" -> C.brandSoft
        else -> Color.Transparent
    }
    val fg = when (variant) {
        "primary", "danger" -> C.onBrand
        "soft" -> C.brand
        "secondary" -> C.ink
        else -> C.sub
    }
    val shape = RoundedCornerShape(if (small) 12.dp else 14.dp)
    Row(
        modifier
            .then(if (variant == "secondary") Modifier.border(1.dp, C.line, shape) else Modifier)
            .clip(shape)
            .background(bg)
            .clickable(enabled = !off) { onPress() }
            .padding(horizontal = if (small) 14.dp else 18.dp, vertical = if (small) 8.dp else 12.dp)
            .heightIn(min = if (small) 40.dp else 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = C.brand,
            )
        } else if (icon != null) {
            MdiIcon(icon, 18, fg)
        }
        if (label != null) {
            Text(label, color = fg, fontSize = if (small) 14.sp else 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
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
) {
    val C = C()
    val shape = RoundedCornerShape(999.dp)
    val bg = if (active) C.brand else C.chip
    val body: @Composable () -> Unit = {
        Row(
            Modifier
                .then(if (!iconOnly) Modifier.border(1.dp, if (active) C.brand else C.line, shape) else Modifier)
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
                MdiIcon(icon, when { iconOnly -> 15; compact -> 13; else -> 15 }, if (active) C.onBrand else if (danger) C.red else C.sub)
            }
            if (!iconOnly) {
                Text(
                    label,
                    fontSize = if (compact) 12.sp else 14.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) C.onBrand else if (danger) C.red else C.ink,
                    maxLines = 1,
                )
            }
            if (count != null && count > 0) {
                Box(
                    Modifier
                        .widthIn(min = if (compact || iconOnly) 16.dp else 20.dp)
                        .height(if (compact || iconOnly) 16.dp else 20.dp)
                        .clip(CircleShape)
                        .background(if (active) C.onBrand else if (danger) C.red else C.brand),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (count > 9) "9+" else "$count",
                        fontSize = if (compact || iconOnly) 11.sp else 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (active) C.brand else Color.White,
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
    val shape = RoundedCornerShape(18.dp)
    val inner = @Composable {
        Column(
            modifier
                .clip(shape)
                .background(C.card)
                .border(1.dp, C.line, shape)
                .padding(16.dp)
        ) { children() }
    }
    if (onPress != null) {
        Box(Modifier.clip(shape).clickable { onPress() }) { inner() }
    } else inner()
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
            Text(title, fontSize = F.body.sp, fontWeight = FontWeight.ExtraBold, color = C.ink)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, fontSize = F.tiny.sp, color = C.sub, modifier = Modifier.padding(top = 1.dp))
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

/** Segmented — compact tab strip of Chips (webapp style: "Ledger · Lists · …").
 *  v1.6.14: chips are COMPACT (28 dp tall, 12 sp) so five-to-six tabs fit
 *  side by side on a phone without crowding their neighbours. */
@Composable
fun Segmented(
    options: List<Triple<String, String, Int?>>,
    value: String,
    onChange: (String) -> Unit,
) {
    Row(
        Modifier.horizontalScrollCompat(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for ((key, label, count) in options) {
            Chip(label = label, active = value == key, count = count, compact = true, onPress = { onChange(key) })
        }
    }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier {
    val state = androidx.compose.foundation.rememberScrollState()
    return this.horizontalScroll(state)
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
    Column(modifier.padding(bottom = 12.dp)) {
        if (!label.isNullOrEmpty()) {
            Text(label, fontSize = F.small.sp, fontWeight = FontWeight.SemiBold, color = C.sub, modifier = Modifier.padding(bottom = 6.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, C.line, RoundedCornerShape(14.dp))
                .background(C.inputBg, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp)
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

@Composable
fun Pill(
    label: String,
    tone: String = "neutral",
    icon: String? = null,
) {
    val C = C()
    val (bg, fg) = when (tone) {
        "brand" -> C.brandSoft to C.brand
        "green" -> Color(0x241d9a6b) to C.green
        "red" -> Color(0x21d64545) to C.red
        "amber" -> Color(0x24b97d10) to C.amber
        "blue" -> Color(0x244a7d9b) to C.blue
        else -> C.chip to C.sub
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
        Text(label, fontSize = F.tiny.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
