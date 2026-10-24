package com.callmony.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Calls
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.model.contactOwnerId
import com.callmony.lib.displayName
import com.callmony.lib.nationalDigits
import com.callmony.lib.normalizePhone
import com.callmony.lib.ownerFullBalance
import com.callmony.lib.prettyPhone
import com.callmony.lib.t9Match
import com.callmony.model.DeviceContact
import com.callmony.platform.Platform
import com.callmony.ui.kit.Avatar
import com.callmony.ui.kit.C
import com.callmony.ui.kit.IconBtn
import com.callmony.ui.kit.MdiIcon
import com.callmony.ui.kit.Money
import com.callmony.ui.kit.Toasts
import com.callmony.ui.kit.vibrate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/* KeypadSheet — the dialer half-modal rising over Recents (260 ms slide).
   Port of src/components/KeypadSheet.tsx. */

private data class Key(val d: String, val letters: String? = null)

private val KEYS = listOf(
    Key("1"), Key("2", "ABC"), Key("3", "DEF"),
    Key("4", "GHI"), Key("5", "JKL"), Key("6", "MNO"),
    Key("7", "PQRS"), Key("8", "TUV"), Key("9", "WXYZ"),
    Key("*"), Key("0", "+"), Key("#"),
)

@Composable
fun KeypadSheet() {
    val C = C()
    val close = { UiStore.keypadOpen = false }
    var number by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var clearJob by remember { mutableStateOf<Job?>(null) }
    var longFired by remember { mutableStateOf(false) }

    val contacts = AppStore.state.deviceContacts
    val countryCode = AppStore.state.settings.countryCode

    val digits = number.replace(Regex("\\D"), "")

    /* live matches — phone prefix 4 > substring/suffix 3.5 > T9 3 */
    val matches = remember(digits, contacts, countryCode) {
        if (digits.length < 2) emptyList()
        else {
            val qnat = nationalDigits(digits, countryCode)
            val out = mutableListOf<Triple<DeviceContact, Double, String>>() // contact, score, nameLower
            outer@ for (c in contacts) {
                val name = displayName(c)
                var score = -1.0
                if (t9Match(digits, name)) score = 3.0
                for (p in c.phones) {
                    val raw = p.number.replace(Regex("\\D"), "")
                    val norm = normalizePhone(p.number, countryCode).replace(Regex("\\D"), "")
                    val nat = nationalDigits(p.number, countryCode)
                    if (raw.startsWith(digits) || norm.startsWith(digits) || (qnat.isNotEmpty() && nat.startsWith(qnat))) {
                        score = 4.0; break
                    }
                    if (raw.contains(digits) || norm.contains(digits) || (qnat.isNotEmpty() && (nat.contains(qnat) || nat.endsWith(qnat)))) {
                        if (score < 3.5) score = 3.5; break
                    }
                }
                if (score > 0) {
                    out.add(Triple(c, score, name.lowercase()))
                    if (out.size >= 60) break@outer
                }
            }
            out.sortedWith(compareByDescending<Triple<DeviceContact, Double, String>> { it.second }.thenComparing { it.third })
                .map { it.first }
        }
    }

    /* exact-match pill — ≥6 digits fully equal */
    val exactMatch = remember(number, contacts, countryCode) {
        if (digits.length < 6) null
        else {
            val typed = normalizePhone(number, countryCode).replace(Regex("\\D"), "")
            contacts.find { c -> c.phones.any { normalizePhone(it.number, countryCode).replace(Regex("\\D"), "") == typed } }
        }
    }

    fun pressKey(d: String) {
        vibrate(longArrayOf(10))
        if (number.length < 24) number += d
    }

    fun longPressKey(d: String) {
        if (d == "0") {
            if (number.length < 24) number += "+"
            vibrate(longArrayOf(14))
            return
        }
        if (d in "2".."9") {
            val recordID = AppStore.state.contactX.find { it.speedDial == d }?.recordID
                ?: AppStore.state.settings.speedDial[d]
            val target = recordID?.let { rid -> contacts.find { it.recordID == rid } }
            val phone = target?.phones?.firstOrNull()?.number
            if (target != null && phone != null) {
                Toasts.toast("${t("act.call")} · ${displayName(target)}", "info")
                Calls.placeCall(phone)
                close()
                return
            }
            UiStore.open(Overlay.SpeedDial)
        }
    }

    fun backspace() {
        if (longFired) {
            longFired = false
            return
        }
        if (number.isNotEmpty()) number = number.dropLast(1)
        vibrate(longArrayOf(8))
    }

    fun startBackspaceHold() {
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(550)
            longFired = true
            number = ""
            vibrate(longArrayOf(24))
        }
    }

    fun stopHold() {
        clearJob?.cancel()
        clearJob = null
    }

    fun pasteFromClipboard() {
        val raw = Platform.share.readClipboard() ?: ""
        val strict = raw.replace(Regex("[\\s\\-().\u2013\u2014]"), "")
        var clean = if (Regex("^\\+?[0-9*#]+$").matches(strict)) strict else ""
        if (clean.isEmpty()) {
            val g = Regex("\\+?[0-9][0-9\\s\\-().\u2013\u2014]{6,}[0-9*#]").find(raw)
            if (g != null) {
                val d = g.value.replace(Regex("[\\s\\-().\u2013\u2014]"), "")
                if (d.replace(Regex("\\D"), "").length >= 9) clean = d
            }
        }
        if (clean.isEmpty()) {
            Toasts.toast(t("kp.noNumberInClipboard"), "info")
            return
        }
        number = clean.take(24)
        vibrate(longArrayOf(12))
        Toasts.toast(t("kp.pasted"))
    }

    fun onChangeNumber(text: String) {
        val compact = text.replace(Regex("[\\s\\-().\u2013\u2014]"), "")
        var clean: String? = if (Regex("^\\+?[0-9*#]+$").matches(compact)) compact.take(24) else null
        if (clean == null) {
            val g = Regex("\\+?[0-9][0-9\\s\\-().\u2013\u2014]{6,}[0-9*#]").find(text)
            if (g != null) {
                val d = g.value.replace(Regex("[\\s\\-().\u2013\u2014]"), "")
                if (d.replace(Regex("\\D"), "").length >= 9) clean = d.take(24)
            }
        }
        if (clean == null) {
            val dialable = text.replace(Regex("[^0-9*+#]"), "")
            clean = dialable.take(24).ifEmpty { null }
        }
        if (clean == null) {
            if (text.trim().isNotEmpty()) Toasts.toast(t("kp.noNumberInClipboard"), "info")
            return
        }
        val cleanedSomething = clean != text.replace(Regex("[\\s\\-().\u2013\u2014]"), "").take(24)
        number = clean
        if (cleanedSomething) {
            vibrate(longArrayOf(12))
            Toasts.toast(t("kp.pasted"))
        }
    }

    fun call() {
        if (number.isEmpty()) {
            Toasts.toast(t("kp.enterNumber"), "info")
            return
        }
        vibrate(longArrayOf(16))
        close()
        when (Calls.placeCall(number)) {
            "blocked" -> Toasts.toast(t("cd.blocked"), "error")
            "denied", "dialer" -> Toasts.toast(t("cl.continuedInDialer"), "info")
            "failed" -> Toasts.toast(t("act.call") + " ✕", "error")
        }
    }

    fun addContact() {
        if (number.isEmpty()) return
        close()
        UiStore.open(Overlay.ContactNew(phone = number))
    }

    // panel
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(C.card)
            .navigationBarsPadding(),
    ) {
        // grabber
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.line))
        }
        // header: speed-dial grid · Keypad · ✕
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { UiStore.open(Overlay.SpeedDial) },
                contentAlignment = Alignment.Center,
            ) { MdiIcon("view-grid-outline", 20, C.sub) }
            Text(
                t("nav.keypad").uppercase(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color(0xFF7a8c88),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconBtn("close", close, size = 36)
        }
        // live matches (max 24% of the screen)
        if (matches.isNotEmpty()) {
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp)
                    .padding(horizontal = 10.dp),
            ) {
                items(matches, key = { it.recordID }) { c ->
                    val bal = ownerFullBalance(AppStore.state.entries, AppStore.state.lists, contactOwnerId(c.recordID))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                val phone = c.phones.firstOrNull()?.number
                                if (phone != null) {
                                    close()
                                    Calls.placeCallWithToast(phone)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        Avatar(c, size = 36)
                        Column(Modifier.weight(1f)) {
                            Text(
                                displayName(c),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = C.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                c.phones.firstOrNull()?.let { prettyPhone(it.number, countryCode) } ?: t("cd.noPhone"),
                                fontSize = 13.sp,
                                color = C.faint,
                                maxLines = 1,
                            )
                        }
                        if (bal != 0.0) Money(bal, signed = true, colorize = true, compact = true, size = 13)
                    }
                }
            }
        }
        // exact-match pill
        if (exactMatch != null) {
            Row(
                Modifier
                    .padding(top = 3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(C.brandSoft)
                    .clickable {
                        close()
                        UiStore.open(Overlay.Contact(exactMatch.recordID))
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Avatar(exactMatch, size = 20)
                Text(
                    displayName(exactMatch),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = C.brand,
                )
            }
        }
        // number display — real text field (native copy/paste menu)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .heightIn(min = 62.dp, max = 84.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = number,
                onValueChange = { onChangeNumber(it) },
                textStyle = TextStyle(
                    fontSize = when {
                        number.length <= 12 -> 28.sp
                        number.length <= 16 -> 24.sp
                        else -> 21.sp
                    },
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp,
                    color = C.ink,
                    textAlign = TextAlign.Center,
                ),
                singleLine = true,
                cursorBrush = SolidColor(C.brand),
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp),
            )
            // paste + backspace
            if (number.isEmpty()) {
                IconBtn("content-paste", { pasteFromClipboard() }, size = 38, color = C.sub)
            } else {
                IconBtn("backspace-outline", { backspace() }, size = 38, color = C.sub)
            }
        }
        if (number.isNotEmpty() && exactMatch == null) Spacer(Modifier.height(2.dp))
        // 3×4 key grid — flat tiles, letters under digits
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            for (row in listOf(0, 3, 6, 9)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    for (k in KEYS.subList(row, row + 3)) {
                        Column(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp, max = 62.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(C.card)
                                .pointerInput(k.d) {
                                    detectTapGestures(
                                        onPress = {
                                            pressKey(k.d)
                                            tryAwaitRelease()
                                        },
                                        onLongPress = { longPressKey(k.d) },
                                    )
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(k.d, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = C.ink)
                            if (k.letters != null) {
                                Text(
                                    k.letters,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = C.faint,
                                    letterSpacing = 2.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
        // action row: video · call · add-contact
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(C.card)
                    .clickable { Toasts.toast(t("kp.videoDemo"), "info") },
                contentAlignment = Alignment.Center,
            ) { MdiIcon("video-outline", 19, C.sub) }
            // call button
            Box(
                Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(if (number.isNotEmpty()) C.brand else C.faint)
                    .clickable(enabled = number.isNotEmpty()) { call() },
                contentAlignment = Alignment.Center,
            ) { MdiIcon("phone", 26, Color.White) }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(C.card)
                    .clickable(enabled = number.isNotEmpty()) { addContact() },
                contentAlignment = Alignment.Center,
            ) { MdiIcon("account-plus-outline", 20, if (number.isNotEmpty()) C.sub else C.faint) }
        }
    }
}
