package com.callmony.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.currentLang
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Diag
import com.callmony.model.COUNTRY_CODES
import com.callmony.model.CURRENCIES
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.lib.exportContactsCsv
import com.callmony.lib.exportContactsVcard
import com.callmony.lib.exportTransactionsCsv
import com.callmony.lib.hashPin
import com.callmony.lib.now
import com.callmony.ui.components.BottomSheet
import com.callmony.ui.components.PressableRow
import com.callmony.theme.F
import com.callmony.ui.components.Sheet
import com.callmony.ui.kit.Btn
import com.callmony.ui.kit.C
import com.callmony.ui.kit.Chip
import com.callmony.ui.kit.Confirm
import com.callmony.ui.kit.Field
import com.callmony.ui.kit.MdiIcon
import com.callmony.ui.kit.Pill
import com.callmony.ui.kit.Segmented
import com.callmony.ui.kit.Toasts
import com.callmony.ui.kit.vibrate
import com.callmony.ui.components.ScrollRow

/* SettingsOverlay + LockScreen + PinFlow. Port of src/overlays/SettingsOverlay.tsx. */

@Composable
fun SettingsOverlay() {
    val C = C()
    val pop = { UiStore.pop() }
    val settings = AppStore.state.settings
    var langSheet by remember { mutableStateOf(false) }
    var pinFlow by remember { mutableStateOf<String?>(null) } // set | change | remove | enter-current
    var restoreConfirm by remember { mutableStateOf(false) }

    Sheet(title = t("hdr.settings"), onBack = pop) {
        Column {
            /* ---- Appearance ---- */
            SectionLabel(t("st.appearance"))
            Column(Modifier.padding(bottom = 14.dp)) {
                Text(t("st.theme"), fontSize = F.small.sp, color = C.sub, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
                Segmented(
                    listOf(Triple("light", t("st.light"), null), Triple("dark", t("st.dark"), null), Triple("auto", t("st.auto"), null)),
                    settings.theme,
                ) { v -> AppStore.updateSettings { it.copy(theme = v) } }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(C.card)
                        .clickable { langSheet = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MdiIcon("translate", 18, C.sub)
                    Text(t("st.language"), fontSize = F.body.sp, color = C.ink, modifier = Modifier.weight(1f))
                    Text(currentLang().native, fontSize = F.small.sp, color = C.sub)
                    MdiIcon("chevron-right", 18, C.faint)
                }
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("st.hideBalances"), fontSize = F.small.sp, color = C.ink, modifier = Modifier.weight(1f))
                    EditorsSwitch(checked = settings.hideBalances, onChange = { v -> AppStore.updateSettings { it.copy(hideBalances = v) } })
                }
            }
            /* ---- Notifications ---- */
            SectionLabel(t("st.notifications"))
            Row(Modifier.padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("st.notificationsHint"), fontSize = F.small.sp, color = C.sub, modifier = Modifier.weight(1f))
                Btn(small = true, label = t("st.allow"), onPress = {
                    com.callmony.platform.Platform.notify.requestNotifPermission { ok ->
                        Toasts.toast(if (ok) t("rm.notifGranted") else t("rm.notifDeclined"), if (ok) "ok" else "info")
                    }
                })
            }
            /* ---- Region ---- */
            SectionLabel(t("st.region"))
            Column(Modifier.padding(bottom = 14.dp)) {
                Row(Modifier.padding(bottom = 8.dp)) {
                    ScrollRow {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CURRENCIES.forEach { cur ->
                                Chip(label = cur.code, compact = true, active = settings.currency == cur.code, onPress = {
                                    AppStore.updateSettings { it.copy(currency = cur.code) }
                                })
                            }
                        }
                    }
                }
                Row(Modifier.padding(bottom = 8.dp)) {
                    ScrollRow {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            COUNTRY_CODES.forEach { cc ->
                                Chip(label = cc.code, compact = true, active = settings.countryCode == cc.code, onPress = {
                                    AppStore.updateSettings { it.copy(countryCode = cc.code) }
                                })
                            }
                        }
                    }
                }
                // v1.6.17 — 12 h (AM/PM) vs 24 h clock, app-wide
                Text(t("st.clockFormat"), fontSize = F.small.sp, color = C.sub, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(label = t("st.clock12"), compact = true, active = !settings.clock24, onPress = {
                        AppStore.updateSettings { it.copy(clock24 = false) }
                    })
                    Chip(label = t("st.clock24"), compact = true, active = settings.clock24, onPress = {
                        AppStore.updateSettings { it.copy(clock24 = true) }
                    })
                }
            }
            /* ---- Security ---- */
            SectionLabel(t("st.security"))
            Column(Modifier.padding(bottom = 14.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(C.card)
                        .clickable { pinFlow = if (settings.pinHash == null) "set" else "change" }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MdiIcon("lock-outline", 18, C.sub)
                    Text(if (settings.pinHash == null) t("st.setPin") else t("st.changePin"), fontSize = F.body.sp, color = C.ink, modifier = Modifier.weight(1f))
                    MdiIcon("chevron-right", 18, C.faint)
                }
                if (settings.pinHash != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(C.card)
                            .clickable { pinFlow = "remove" }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MdiIcon("lock-off-outline", 18, C.red)
                        Text(t("st.removePin"), fontSize = F.body.sp, color = C.red, modifier = Modifier.weight(1f))
                    }
                }
                if (settings.pinHash != null) {
                    Text(t("st.autolock"), fontSize = F.small.sp, color = C.sub, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                    // v1.6.18: added "Never" — the v1.6.17 default (1 minute)
                    // surprised users as "the app closes itself" while they
                    // were simply reading the screen with no touch for a minute.
                    Segmented(
                        listOf(
                            Triple("0", t("st.immediately"), null),
                            Triple("1", t("st.after1"), null),
                            Triple("5", t("st.after5"), null),
                            Triple("-1", t("st.never"), null),
                        ),
                        settings.autolockMin.toString(),
                    ) { v -> AppStore.updateSettings { it.copy(autolockMin = v.toIntOrNull() ?: -1) } }
                }
            }
            /* ---- Backup & restore ---- */
            SectionLabel(t("st.backup"))
            Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(small = true, label = t("bkp.exportBackup"), icon = "download", variant = "secondary", onPress = {
                    val json = com.callmony.lib.Backup.exportAll()
                    com.callmony.platform.Platform.share.shareText(json, "callmony-backup.json")
                    AppStore.updateSettings { it.copy(lastBackupAt = now()) }
                    Toasts.toast(t("bkp.backupExported"))
                })
                Btn(small = true, label = t("bkp.restoreBackup"), icon = "restore", variant = "secondary", onPress = { restoreConfirm = true })
                settings.lastBackupAt?.let {
                    Text("${t("bkp.lastBackup")}: ${com.callmony.lib.fdEpoch(it)}", fontSize = F.tiny.sp, color = C.faint)
                }
            }
            /* ---- Import & export ---- */
            SectionLabel(t("ie.importExport"))
            Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(small = true, label = t("ie.exportVcf"), icon = "card-account-details-outline", variant = "secondary", onPress = {
                    if (exportContactsVcard()) Toasts.toast(t("ie.contactsExported")) else Toasts.toast(t("ie.noContactsToExport"), "error")
                })
                Btn(small = true, label = t("ie.exportCsv"), icon = "file-delimited-outline", variant = "secondary", onPress = {
                    if (exportContactsCsv()) Toasts.toast(t("ie.contactsExported")) else Toasts.toast(t("ie.noContactsToExport"), "error")
                })
                Btn(small = true, label = t("ie.exportTx"), icon = "cash-multiple", variant = "secondary", onPress = {
                    if (exportTransactionsCsv()) Toasts.toast(t("ie.txExported")) else Toasts.toast(t("ie.noTxToExport"), "error")
                })
            }
            /* ---- Diagnostics (v1.6.20) ---- */
            SectionLabel(t("st.diagnostics"))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.card)
                    .clickable { Diag.listOpen = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MdiIcon("alert-circle-outline", 18, if (Diag.reports.any { !it.seen }) C.red else C.sub)
                Text(t("st.diagnostics"), fontSize = F.body.sp, color = C.ink, modifier = Modifier.weight(1f))
                if (Diag.reports.isNotEmpty()) {
                    Text(
                        "${Diag.reports.size}",
                        fontSize = F.small.sp,
                        color = if (Diag.reports.any { !it.seen }) C.red else C.sub,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                MdiIcon("chevron-right", 18, C.faint)
            }
            /* ---- Danger zone ---- */
            SectionLabel(t("st.data"))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, C.red.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Text(t("st.deleteAllData"), fontSize = F.body.sp, fontWeight = FontWeight.Bold, color = C.red)
                Text(t("st.deleteAllHint"), fontSize = F.tiny.sp, color = C.sub, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                Btn(small = true, label = t("st.deleteAllData"), variant = "danger", onPress = {
                    Confirm.ask(t("st.reallyDeleteTitle"), t("st.reallyDeleteMsg"), t("st.wipeMyData"), danger = true) { ok1 ->
                        if (ok1) {
                            Confirm.ask(t("st.deleteAllTitle"), t("st.deleteAllMsg"), t("st.deleteEverything"), danger = true) { ok2 ->
                                if (ok2) {
                                    AppStore.wipeAll()
                                    Toasts.toast(t("st.allDataDeleted"))
                                    pop()
                                }
                            }
                        }
                    }
                })
            }
        }
    }

    if (langSheet) {
        com.callmony.ui.components.LanguageSheet { langSheet = false }
    }

    // restore sheet — paste the backup JSON, then Merge or Replace
    if (restoreConfirm) {
        var paste by remember { mutableStateOf("") }
        BottomSheet(title = t("bkp.restoreBackup"), onClose = { restoreConfirm = false }) {
            Column {
                Text(t("bkp.safetyNote"), fontSize = F.tiny.sp, color = C.sub, modifier = Modifier.padding(bottom = 8.dp))
                Field(value = paste, onChangeText = { paste = it }, placeholder = "{ \"app\": \"callmony\" … }", multiline = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn(small = true, label = t("bkp.mergeIn"), variant = "soft", onPress = {
                        val data = com.callmony.lib.Backup.parse(paste)
                        if (data == null) { Toasts.toast(t("bkp.notValidBackup"), "error"); return@Btn }
                        AppStore.restoreBackup(data, "merge")
                        Toasts.toast(t("bkp.restored"))
                        restoreConfirm = false
                    })
                    Btn(small = true, label = t("bkp.replaceTitle"), variant = "danger", onPress = {
                        val data = com.callmony.lib.Backup.parse(paste)
                        if (data == null) { Toasts.toast(t("bkp.notValidBackup"), "error"); return@Btn }
                        Confirm.ask(t("bkp.replaceTitle"), t("bkp.replaceMsg"), t("bkp.eraseAndRestore"), danger = true) { ok ->
                            if (ok) {
                                AppStore.restoreBackup(data, "replace")
                                Toasts.toast(t("bkp.restored"))
                                restoreConfirm = false
                            }
                        }
                    })
                }
            }
        }
    }

    pinFlow?.let { mode ->
        PinFlowSheet(mode) { pinFlow = null }
    }
}

@Composable
private fun SectionLabel(label: String) {
    val C = C()
    Text(
        label,
        fontSize = F.small.sp,
        fontWeight = FontWeight.ExtraBold,
        color = C.brand,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/* ---------------- PIN flow ---------------- */

@Composable
fun PinFlowSheet(mode: String, onClose: () -> Unit) {
    val C = C()
    val settings = AppStore.state.settings
    var phase by remember { mutableStateOf(mode) } // set | change | remove | enter-current | new | confirm
    var entered by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var shake by remember { mutableStateOf(false) }
    var currentPin by remember { mutableStateOf("") }

    val title = when (phase) {
        "set" -> t("st.choosePin")
        "enter-current" -> t("st.enterCurrentPin")
        "new" -> t("st.enterNewPin")
        "confirm" -> t("st.reenterPin")
        "remove" -> t("st.enterCurrentPin")
        else -> t("st.changePin")
    }

    fun submit(pin: String) {
        vibrate(longArrayOf(10))
        when (phase) {
            "set" -> { firstPin = pin; phase = "confirm" }
            "change" -> phase = "enter-current"
            "enter-current" -> {
                if (AppStore.state.settings.pinHash != null && verifyPin(pin)) phase = "new"
                else if (AppStore.state.settings.pinHash == null) phase = "new"
                else { shake = true; Toasts.toast(t("st.wrongPin"), "error") }
            }
            "new" -> { firstPin = pin; phase = "confirm" }
            "confirm" -> {
                if (pin == firstPin) {
                    val h = hashPin(pin)
                    AppStore.setPin(com.callmony.model.PinHash(h.hash, h.salt))
                    Toasts.toast(t("act.saved"))
                    onClose()
                } else { shake = true; Toasts.toast(t("st.pinsDontMatch"), "error") }
            }
            "remove" -> {
                if (verifyPin(pin)) {
                    AppStore.setPin(null)
                    Toasts.toast(t("st.pinRemoved"))
                    onClose()
                } else { shake = true; Toasts.toast(t("st.wrongPin"), "error") }
            }
        }
        entered = ""
    }

    BottomSheet(title = title, onClose = onClose) {
        // v1.6.17: fillMaxWidth so horizontalAlignment actually centers the
        // dots + pad (a wrap-width Column inside the sheet used to stick to
        // the LEFT edge — "the PIN keypad is aligned to left").
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(4) { i ->
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (i < entered.length) C.brand else C.chip)
                            .then(if (shake) Modifier.border(2.dp, C.red, CircleShape) else Modifier),
                    )
                }
            }
            PinPad(onKey = { d ->
                if (entered.length < 4) {
                    entered += d
                    if (entered.length == 4) submit(entered)
                }
            }, onBack = { if (entered.isNotEmpty()) entered = entered.dropLast(1) })
        }
    }
}

private fun verifyPin(pin: String): Boolean {
    val h = AppStore.state.settings.pinHash ?: return false
    return hashPin(pin, h.salt).hash == h.hash
}

@Composable
fun PinPad(onKey: (String) -> Unit, onBack: () -> Unit) {
    val C = C()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫"),
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { k ->
                    if (k.isEmpty()) {
                        Box(Modifier.size(68.dp, 56.dp))
                    } else {
                        Box(
                            Modifier
                                .size(68.dp, 56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(C.card)
                                .border(1.dp, C.line, RoundedCornerShape(14.dp))
                                .clickable { if (k == "⌫") onBack() else onKey(k) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                k,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = C.ink,
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- LockScreen ---------------- */

@Composable
fun LockScreen() {
    val C = C()
    var entered by remember { mutableStateOf("") }
    var shake by remember { mutableStateOf(false) }

    fun submit(pin: String) {
        vibrate(longArrayOf(10))
        if (verifyPin(pin)) {
            UiStore.locked = false
        } else {
            shake = true
            entered = ""
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.canvas)
            // v1.6.14: the lock screen renders outside the shell column — pad
            // the system bars so the PIN pad never sits under the nav bar.
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(C.brand),
            contentAlignment = Alignment.Center,
        ) { com.callmony.ui.LogoTile(62, corner = 18) }
        Text("callmony", fontSize = F.title.sp, fontWeight = FontWeight.ExtraBold, color = C.ink, modifier = Modifier.padding(top = 10.dp))
        Text(t("st.enterPin"), fontSize = F.small.sp, color = C.sub, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (i < entered.length) C.brand else C.chip)
                        .then(if (shake) Modifier.border(2.dp, C.red, CircleShape) else Modifier),
                )
            }
        }
        PinPad(onKey = { d ->
            if (entered.length < 4) {
                entered += d
                if (entered.length == 4) submit(entered)
            }
        }, onBack = { if (entered.isNotEmpty()) entered = entered.dropLast(1) })
        Text(
            t("st.forgotPin"),
            fontSize = F.small.sp,
            color = C.brand,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(top = 18.dp)
                .clickable {
                    Confirm.ask(t("st.resetPinConfirm"), t("st.removePinMsg"), t("act.confirm")) { ok ->
                        if (ok) {
                            AppStore.setPin(null)
                            UiStore.locked = false
                        }
                    }
                },
        )
        Spacer(Modifier.weight(1f))
    }
}
