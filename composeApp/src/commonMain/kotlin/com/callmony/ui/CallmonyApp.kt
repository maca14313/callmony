package com.callmony.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.LANGUAGES
import com.callmony.i18n.currentLang
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.lib.dueStatus
import com.callmony.lib.now
import com.callmony.platform.PermState
import com.callmony.platform.Platform
import com.callmony.theme.F
import com.callmony.theme.osDark
import androidx.compose.runtime.collectAsState
import com.callmony.ui.components.BottomSheet
import com.callmony.ui.components.KeypadSheet
import com.callmony.ui.components.SheetScrim
import com.callmony.ui.kit.AppBackHandler
import com.callmony.ui.kit.C
import com.callmony.ui.kit.ConfirmHost
import com.callmony.ui.kit.MdiIcon
import com.callmony.ui.kit.Spinner
import com.callmony.ui.kit.SystemBarsAppearance
import com.callmony.ui.kit.Toaster
import com.callmony.ui.overlays.AboutOverlay
import com.callmony.ui.overlays.AlarmDialog
import com.callmony.ui.components.CalculatorSheet
import com.callmony.ui.overlays.ContactCreatorOverlay
import com.callmony.ui.overlays.ContactDetailOverlay
import com.callmony.ui.overlays.ContactEditorOverlay
import com.callmony.ui.overlays.EntryEditOverlay
import com.callmony.ui.overlays.ListDetailOverlay
import com.callmony.ui.overlays.ListEditOverlay
import com.callmony.ui.overlays.LockScreen
import com.callmony.ui.overlays.MergeOverlay
import com.callmony.ui.overlays.NoteEditOverlay
import com.callmony.ui.overlays.OnboardingSheet
import com.callmony.ui.overlays.ReminderEditOverlay
import com.callmony.ui.overlays.BankEditOverlay
import com.callmony.ui.overlays.SearchOverlay
import com.callmony.ui.overlays.SettingsOverlay
import com.callmony.ui.overlays.SpeedDialOverlay
import com.callmony.ui.screens.GeneralScreen
import com.callmony.ui.screens.RecentsScreen
import com.callmony.ui.screens.RemindersScreen
import com.callmony.ui.screens.TrashScreen
import androidx.compose.ui.layout.ContentScale
import com.callmony.ui.kit.rememberLogoBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* CallmonyApp — the shell: hydrate → splash → Header + screen + BottomNav
   + overlays + keypad + More + onboarding + alarm + lock.
   Port of App.tsx. */

@Composable
fun CallmonyApp() {
    val C = C()

    /* ---- the new logo (bundled compose resource; decoded once) ---- */
    val logoBmp by rememberLogoBitmap()

    /* ---- v1.6.15: the OS dark mode feeds the app's "auto" theme ----
       (theme.osDark was never synced from the platform flow, so "auto"
       always resolved to LIGHT on Android) */
    LaunchedEffect(Unit) {
        Platform.ui.osDarkFlow.collect { osDark.value = it }
    }

    /* ---- v1.6.15: status/navigation bar icons follow the APP canvas ----
       enableEdgeToEdge() derives icon appearance from the SYSTEM dark mode;
       with the system dark + app theme light the phone's clock/battery/
       signal turned invisible (white icons over the light canvas). */
    val osDarkNow by osDark.collectAsState()
    val lightCanvas = when (AppStore.state.settings.theme) {
        "light" -> true
        "dark" -> false
        else -> !osDarkNow
    }
    SystemBarsAppearance(lightCanvas)

    /* ---- v1.6.15: native back button closes modals / goes back first ----
       (previously back ALWAYS exited the app, even with a sheet open) */
    val canGoBack = UiStore.overlayStack.isNotEmpty() || UiStore.keypadOpen ||
        UiStore.moreOpen || UiStore.tab != "recents"
    AppBackHandler(enabled = canGoBack) {
        when {
            UiStore.overlayStack.isNotEmpty() -> UiStore.pop()
            UiStore.keypadOpen -> UiStore.keypadOpen = false
            UiStore.moreOpen -> UiStore.moreOpen = false
            UiStore.tab != "recents" -> UiStore.setTab("recents")
        }
    }

    /* ---- boot: hydrate with a 5 s bail-out ---- */
    LaunchedEffect(Unit) {
        AppStore.hydrate()
        AppStore.scope.launch {
            delay(5000)
            if (!AppStore.state.hydrated) AppStore.state.hydrated = true
        }
    }

    /* ---- shell boot effect (skipped in safe mode) ---- */
    LaunchedEffect(AppStore.state.hydrated) {
        if (!AppStore.state.hydrated) return@LaunchedEffect
        // permissions + system calls (Android asks once, dialer-style)
        AppStore.loadSystemCalls()
        // +4 s contacts refresh
        delay(4000)
        AppStore.refreshDeviceContacts()
        // +8 s reminder readiness
        delay(4000)
        Platform.notify.ensureChannel()
        AppStore.rearmReminders()
    }

    /* ---- alarm watcher (15 s interval) ---- */
    LaunchedEffect(Unit) {
        while (true) {
            val nowMs = now()
            AppStore.state.reminders.forEach { r ->
                if (r.deletedAt == null && !r.done) {
                    val effective = r.snoozedUntil ?: r.dueAt
                    val lead = r.leadMinutes.minOrNull() ?: 0
                    val dueSoon = effective <= nowMs && (r.lastFiredAt == null || r.lastFiredAt!! < effective - 60000)
                    val leadDue = lead > 0 && (effective - lead * 60000L) <= nowMs && !r.firedLeads.contains(lead)
                    if (dueSoon || leadDue) UiStore.pushAlarm(r)
                }
            }
            delay(15000)
        }
    }

    /* ---- autolock ---- */
    var lastActive by remember { mutableStateOf(now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10000)
            if (AppStore.state.settings.pinHash != null && !UiStore.locked) {
                val idle = now() - lastActive
                val limit = AppStore.state.settings.autolockMin
                when {
                    limit == 0 && !isAppForeground() -> UiStore.locked = true
                    limit > 0 && idle > limit * 60000L -> UiStore.locked = true
                }
            }
        }
    }

    if (!AppStore.state.hydrated) {
        // splash
        Box(Modifier.fillMaxSize().background(C.canvas), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val bmp = logoBmp
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "callmony",
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(24.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    // v1.6.15: brand tile + wordmark initial — NEVER the old
                    // phone-in-talk glyph (the eager decode in Application
                    // makes this branch effectively unreachable).
                    Box(
                        Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(C.brand),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "c",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = C.onBrand,
                        )
                    }
                }
                Text(
                    buildString { append("call"); append("mony") },
                    fontSize = F.title.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = C.ink,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(t("hdr.tagline"), fontSize = F.small.sp, color = C.sub)
            }
        }
        return
    }

    // lock screen while locked
    if (UiStore.locked) {
        LockScreen()
        return
    }

    Box(Modifier.fillMaxSize().background(C.canvas)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header()
            Box(Modifier.weight(1f)) {
                when (UiStore.tab) {
                    "general" -> GeneralScreen()
                    "reminders" -> RemindersScreen()
                    "trash" -> TrashScreen()
                    else -> RecentsScreen()
                }
            }
            BottomNav()
        }

        // onboarding
        if (!AppStore.state.settings.onboarded) {
            Box(Modifier.fillMaxSize().background(C.canvas)) { OnboardingSheet() }
        }

        // keypad half-modal over everything
        if (UiStore.keypadOpen) {
            SheetScrim(onDismiss = { UiStore.keypadOpen = false }) { KeypadSheet() }
        }

        // More sheet
        if (UiStore.moreOpen) {
            SheetScrim(onDismiss = { UiStore.moreOpen = false }) { MoreSheet() }
        }

        // overlay stack
        OverlayHost()

        // alarm dialog
        if (UiStore.alarmQueue.isNotEmpty()) AlarmDialog()

        // global toaster + confirm
        Toaster()
        ConfirmHost()
    }

    // touch anywhere refreshes the autolock timer
    DisposableEffect(Unit) {
        onDispose { }
    }
}

private fun isAppForeground(): Boolean = true

/* ---------------- Header ---------------- */

@Composable
private fun Header() {
    val C = C()
    val tab = UiStore.tab
    val primary = tab == "recents" || tab == "general"
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (primary) {
                // logo tile + wordmark + tagline + search (v1.6.13: the REAL
                // logo everywhere the old icon tile used to be)
                LogoTile(38, corner = 12)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("call", fontSize = F.body.sp, fontWeight = FontWeight.ExtraBold, color = C.ink)
                        Text("mony", fontSize = F.body.sp, fontWeight = FontWeight.ExtraBold, color = C.brand)
                    }
                    Text(t("hdr.tagline"), fontSize = F.tiny.sp, color = C.faint)
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { UiStore.open(Overlay.Search()) },
                    contentAlignment = Alignment.Center,
                ) { MdiIcon("magnify", 22, C.sub) }
            } else {
                // secondary: back + title
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { UiStore.setTab("recents") },
                    contentAlignment = Alignment.Center,
                ) { MdiIcon("arrow-left", 22, C.sub) }
                Text(
                    when (tab) {
                        "reminders" -> t("tab.reminders")
                        "trash" -> t("hdr.trash")
                        else -> t("hdr.recents")
                    },
                    fontSize = F.h2.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = C.ink,
                )
            }
        }
        // hairline
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.line).align(Alignment.BottomCenter))
    }
}

/* ---------------- BottomNav ---------------- */

@Composable
private fun BottomNav() {
    val C = C()
    val overdueCount = AppStore.state.reminders.count { it.deletedAt == null && !it.done && dueStatus(it.snoozedUntil ?: it.dueAt) == "overdue" }
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 6.dp)
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // v1.6.15 (user request): the KEYPAD sits in the MIDDLE slot of
            // the five-tile bar: Recents · Calculator · Keypad · General · More
            NavSlot("recents", "history", "Recents", Modifier.weight(1f))
            NavSlot("calculator", "calculator-variant", "Calculator", Modifier.weight(1f)) {
                // RN parity: tap toggles the calculator sheet over the current tab
                val calcOpen = UiStore.overlayStack.any { it is Overlay.Calculator }
                if (calcOpen) UiStore.closeType("Calculator")
                else UiStore.open(Overlay.Calculator())
            }
            // keypad — the floating dialer button (middle of the bar)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val active = UiStore.keypadOpen
                Box(
                    Modifier
                        .size(52.dp, 40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (active) C.card else C.brand)
                        .border(1.5.dp, C.brand, RoundedCornerShape(14.dp))
                        .clickable {
                            // RN parity: tap while open closes; otherwise the
                            // dialer opens ON TOP of the Recents tab
                            UiStore.closeAll()
                            if (!active) {
                                UiStore.setTab("recents")
                                UiStore.keypadOpen = true
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { MdiIcon("dialpad", 22, if (active) C.brand else Color.White) }
            }
            NavSlot("general", "wallet-outline", "General", Modifier.weight(1f))
            // More
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(48.dp, 34.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (UiStore.moreOpen) C.brandSoft else Color.Transparent)
                        .clickable { UiStore.moreOpen = !UiStore.moreOpen },
                    contentAlignment = Alignment.Center,
                ) {
                    MdiIcon("dots-horizontal-circle-outline", 24, if (UiStore.moreOpen) C.brand else C.sub)
                    if (overdueCount > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(C.red),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (overdueCount > 9) "9+" else "$overdueCount",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- Logo tile (the new logo, everywhere) ---------------- */

/** The new logo as a rounded tile; falls back to a plain brand tile while
 *  the bitmap decodes (v1.6.15: no more old-glyph fallback — the eager
 *  decode at process start means the real logo is there from frame one).
 *  Used by the header/navbar, splash and About. */
@Composable
fun LogoTile(sizeDp: Int, corner: Int = sizeDp / 3) {
    val C = C()
    val bmp by rememberLogoBitmap()
    if (bmp != null) {
        Image(
            bitmap = bmp!!,
            contentDescription = "callmony logo",
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(corner.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(corner.dp))
                .background(C.brand),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "c",
                fontSize = (sizeDp * 0.5f).sp,
                fontWeight = FontWeight.ExtraBold,
                color = C.onBrand,
            )
        }
    }
}

@Composable
private fun NavSlot(tab: String, icon: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val C = C()
    val active = UiStore.tab == tab
    Box(modifier.height(64.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(48.dp, 34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (active) C.brandSoft else Color.Transparent)
                .clickable {
                    // v1.6.14 FIX: the nav tiles never switched tabs — tapping
                    // General (the wallet/ledger page) or Recents did nothing.
                    // RN parity: closeAll() first, then setTab for real tabs.
                    UiStore.closeAll()
                    if (tab != "calculator") UiStore.setTab(tab)
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) { MdiIcon(icon, 24, if (active) C.brand else C.sub) }
    }
}

/* ---------------- More sheet ---------------- */

@Composable
fun MoreSheet() {
    val C = C()
    val lang = currentLang()
    var langSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var themeSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val overdue = AppStore.state.reminders.count { it.deletedAt == null && !it.done && dueStatus(it.snoozedUntil ?: it.dueAt) == "overdue" }
    BottomSheet(onClose = { UiStore.moreOpen = false }) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MoreTile("translate", lang.native, Modifier.weight(1f)) {
                    langSheet = true
                }
                MoreTile(
                    when (AppStore.state.settings.theme) { "light" -> "weather-sunny"; "dark" -> "weather-night"; else -> "theme-light-dark" },
                    t("st.theme"), Modifier.weight(1f),
                ) { themeSheet = true }
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MoreTile("bell-outline", t("tab.reminders"), Modifier.weight(1f), badge = overdue) {
                    UiStore.setTab("reminders")
                }
                MoreTile("account-multiple-plus-outline", t("spd.mergeDuplicates"), Modifier.weight(1f)) {
                    UiStore.open(Overlay.Merge)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MoreTile("view-grid-outline", t("kp.speedDial"), Modifier.weight(1f)) { UiStore.open(Overlay.SpeedDial) }
                MoreTile("trash-can-outline", t("hdr.trash"), Modifier.weight(1f)) { UiStore.setTab("trash") }
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MoreTile("cog-outline", t("hdr.settings"), Modifier.weight(1f)) { UiStore.open(Overlay.SettingsSheet) }
                MoreTile("information-outline", t("st.aboutApp"), Modifier.weight(1f)) { UiStore.open(Overlay.About) }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0x1f1d9a6b))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MdiIcon("cloud-off-outline", 14, C.green)
                        Text(t("misc.offlineReady"), fontSize = F.tiny.sp, color = C.green, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (langSheet) {
        com.callmony.ui.components.LanguageSheet { langSheet = false }
    }
    if (themeSheet) {
        ThemeSheet { themeSheet = false }
    }
}

@Composable
private fun MoreTile(icon: String, label: String, modifier: Modifier = Modifier, badge: Int = 0, onPress: () -> Unit) {
    val C = C()
    Column(
        modifier
            .height(84.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(C.card)
            .border(1.dp, C.line, RoundedCornerShape(18.dp))
            .clickable { onPress() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            MdiIcon(icon, 24, C.brand)
            if (badge > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(15.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(C.red),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (badge > 9) "9+" else "$badge", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        Text(
            label,
            fontSize = F.tiny.sp,
            color = C.sub,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp),
        )
    }
}

/* ---------------- OverlayHost ---------------- */

@Composable
private fun OverlayHost() {
    UiStore.overlayStack.lastOrNull()?.let { overlay ->
        Box(Modifier.fillMaxSize().background(com.callmony.ui.kit.C().canvas)) {
            when (overlay) {
                is Overlay.Contact -> ContactDetailOverlay(overlay.recordID, overlay.initialSeg)
                is Overlay.ContactEdit -> ContactEditorOverlay(overlay.recordID)
                is Overlay.ContactNew -> ContactCreatorOverlay(overlay.phone, overlay.name)
                is Overlay.ListDetail -> ListDetailOverlay(overlay.listId)
                is Overlay.ListEdit -> ListEditOverlay(overlay.listId, overlay.ownerId, overlay.kind)
                is Overlay.EntryEdit -> EntryEditOverlay(overlay.entryId, overlay.ownerId, overlay.entryType, overlay.kind, overlay.groupId)
                is Overlay.NoteEdit -> NoteEditOverlay(overlay.noteId, overlay.ownerId)
                is Overlay.ReminderEdit -> ReminderEditOverlay(overlay.reminderId, overlay.ownerId)
                is Overlay.BankEdit -> BankEditOverlay(overlay.bankId, overlay.ownerId)
                is Overlay.SettingsSheet -> SettingsOverlay()
                is Overlay.Search -> SearchOverlay()
                is Overlay.Calculator -> CalculatorSheet()
                is Overlay.Merge -> MergeOverlay()
                is Overlay.SpeedDial -> SpeedDialOverlay()
                is Overlay.Backup -> SettingsOverlay()
                is Overlay.About -> AboutOverlay()
            }
        }
    }
}

@Composable
private fun ThemeSheet(onClose: () -> Unit) {
    val C = C()
    BottomSheet(title = t("st.theme"), onClose = onClose) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "light" to t("st.light"),
                    "dark" to t("st.dark"),
                    "auto" to t("st.auto"),
                ).forEach { (k, label) ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (AppStore.state.settings.theme == k) C.brandSoft else C.card)
                            .border(1.dp, if (AppStore.state.settings.theme == k) C.brand else C.line, RoundedCornerShape(14.dp))
                            .clickable {
                                AppStore.updateSettings { it.copy(theme = k) }
                                onClose()
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, fontSize = F.body.sp, color = C.ink, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
