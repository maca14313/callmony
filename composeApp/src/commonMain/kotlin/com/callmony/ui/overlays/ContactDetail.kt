package com.callmony.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Calls
import com.callmony.lib.Messengers
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.model.contactOwnerId
import com.callmony.lib.displayName
import com.callmony.lib.durationStr
import com.callmony.lib.fd
import com.callmony.lib.fdEpoch
import com.callmony.lib.ft
import com.callmony.lib.money
import com.callmony.lib.normalizePhone
import com.callmony.lib.openListsPending
import com.callmony.lib.ownerFullBalance
import com.callmony.lib.prettyPhone
import com.callmony.lib.primaryPhone
import com.callmony.lib.shareContactVcard
import com.callmony.model.DeviceContact
import com.callmony.model.DevicePhone
import com.callmony.model.SocialLink
import com.callmony.platform.Platform
import com.callmony.theme.F
import com.callmony.ui.components.BottomSheet
import com.callmony.ui.components.LongPressText
import com.callmony.ui.components.PressableRow
import com.callmony.ui.components.Sheet
import com.callmony.ui.kit.Avatar
import com.callmony.ui.kit.Btn
import com.callmony.ui.kit.C
import com.callmony.ui.kit.Field
import com.callmony.ui.kit.IconBtn
import com.callmony.ui.kit.MdiIcon
import com.callmony.ui.kit.Money
import com.callmony.ui.kit.Divider
import com.callmony.ui.kit.Pill
import com.callmony.ui.kit.Segmented
import com.callmony.ui.kit.Spinner
import com.callmony.ui.kit.Toasts
import com.callmony.ui.sections.BankSection
import com.callmony.ui.sections.LedgerSection
import com.callmony.ui.sections.ListsSection
import com.callmony.ui.sections.NotesSection
import com.callmony.ui.sections.RemindersSection
import com.callmony.ui.components.ScrollRow

/* ContactDetail — nav-bar header (avatar · name · number · pencil · star · ⋯),
   balance line, the 6×2 quick-action grid, segmented sections and the Info
   tab with per-contact call history. Port of src/overlays/ContactDetail.tsx. */

private val SOCIAL_APPS = listOf(
    Triple("telegram", "Telegram", "send"),
    Triple("whatsapp", "WhatsApp", "whatsapp"),
    Triple("signal", "Signal", "shield-half-full"),
    Triple("viber", "Viber", "phone-classic"),
    Triple("instagram", "Instagram", "instagram"),
    Triple("x", "X", "twitter"),
    Triple("other", "Other", "link-variant"),
)

private fun socialIcon(app: String): String = SOCIAL_APPS.find { it.first == app }?.third ?: "link-variant"

private fun socialUrl(link: SocialLink): String = when (link.app) {
    "telegram" -> "https://t.me/${link.handle.removePrefix("@")}"
    "whatsapp" -> "https://wa.me/${link.handle.replace(Regex("\\D"), "")}"
    "instagram" -> "https://instagram.com/${link.handle.removePrefix("@")}"
    "x" -> "https://x.com/${link.handle.removePrefix("@")}"
    else -> link.handle
}

@Composable
fun ContactDetailOverlay(recordID: String, initialSeg: String? = null) {
    val C = C()
    val pop = { UiStore.pop() }
    val contact = AppStore.state.deviceContacts.find { it.recordID == recordID }
    val entries = AppStore.state.entries
    val lists = AppStore.state.lists
    val countryCode = AppStore.state.settings.countryCode
    var seg by remember { mutableStateOf(initialSeg ?: "ledger") }
    var numPicker by remember { mutableStateOf<String?>(null) } // "call" | "sms" | "whatsapp"
    var moreOpen by remember { mutableStateOf(false) }
    var socialSheet by remember { mutableStateOf<String?>(null) }
    var phoneSheet by remember { mutableStateOf<String?>(null) }
    var tgPicker by remember { mutableStateOf(false) }
    var emailPicker by remember { mutableStateOf(false) }
    var bodyReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { bodyReady = true }

    if (contact == null) {
        Sheet(title = t("cd.notFound"), subtitle = t("cd.notFoundHint"), onBack = pop) {
            Text(t("cd.notFoundHint"), color = C.sub, fontSize = F.body.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 30.dp))
        }
        return
    }

    val ownerId = contactOwnerId(contact.recordID)
    val xEntry = AppStore.contactXOf(contact.recordID)
    val socials = xEntry?.socials ?: emptyList()
    val isFav = xEntry?.favorite == true
    val balance = ownerFullBalance(entries, lists, ownerId)
    val pendingLists = openListsPending(lists, ownerId)
    val phone = primaryPhone(contact)
    val email = contact.emails.firstOrNull()?.address

    fun dedupePhones(): List<DevicePhone> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<DevicePhone>()
        contact.phones.forEach { p ->
            val key = normalizePhone(p.number, countryCode).replace(Regex("\\D"), "").ifEmpty { p.number.replace(Regex("\\D"), "") }
            if (seen.add(key)) out.add(p)
        }
        return out
    }

    fun fireAction(action: String, number: String?) {
        if (number == null) {
            Toasts.toast(t("cd.noPhone"), "error")
            return
        }
        when (action) {
            "call" -> Calls.placeCall(number)
            "sms" -> Messengers.smsLink(number)
            "whatsapp" -> {
                val text = when {
                    balance > 0 -> "Hi ${displayName(contact)}, a reminder that ${money(kotlin.math.abs(balance))} is still to be settled (${fdEpoch(com.callmony.lib.now())})."
                    balance < 0 -> "Hi ${displayName(contact)}, I'd like to settle up ${money(kotlin.math.abs(balance))} that I owe you. How would you like to receive it?"
                    else -> null
                }
                AppStore.logMessage(ownerId, "whatsapp", text ?: "")
                Messengers.waLink(number, text)
            }
        }
        numPicker = null
    }

    fun runAction(action: String) {
        if (dedupePhones().isEmpty()) {
            Toasts.toast(t("cd.noPhone"), "error")
            return
        }
        numPicker = action
    }

    val quickActions = listOf(
        Triple("phone", t("act.call")) { runAction("call") },
        Triple("message-text-outline", t("act.sms")) { runAction("sms") },
        Triple("whatsapp", t("act.whatsapp")) { runAction("whatsapp") },
        Triple("send", t("act.telegram")) { tgPicker = true },
        Triple("email-outline", t("act.email")) { emailPicker = true },
        Triple("book-outline", t("tab.ledger")) { seg = "ledger" },
        Triple("bank-outline", t("tab.bank")) { seg = "bank" },
        Triple("format-list-checks", t("tab.lists")) { seg = "lists" },
        Triple("note-text-outline", t("tab.notes")) { seg = "notes" },
        Triple("bell-outline", t("tab.reminders")) { seg = "reminders" },
        Triple("information-outline", t("tab.info")) { seg = "info" },
        // v1.6.18 — was “＋ Add to contact”; the add-to options now live INSIDE
        // the More sheet together with everything else (user request).
        Triple("dots-horizontal-circle-outline", t("cd.more")) { moreOpen = true },
    )

    fun toggleFav() {
        AppStore.toggleFavorite(contact.recordID)
        Toasts.toast(if (isFav) t("cd.removedFromFavorites") else t("cd.addToFavorites"))
    }

    fun copyNumber() {
        if (phone != null) {
            Platform.share.copyText(phone)
            Toasts.toast(t("cd.numberCopied"))
        }
    }

    Sheet(
        title = displayName(contact),
        subtitle = phone?.let { prettyPhone(it, countryCode) } ?: contact.company ?: t("cd.noPhone"),
        onBack = pop,
        onLongPressSubtitle = { copyNumber() },
        // v1.6.18 — tapping the navbar number DIALS it; long-press still copies.
        onSubtitleTap = if (phone != null) ({ Calls.placeCall(phone) }) else null,
        avatar = { Avatar(contact, size = 34) },
        headerRight = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp, 38.dp).clickable { UiStore.open(Overlay.ContactEdit(recordID)) }, contentAlignment = Alignment.Center) {
                    MdiIcon("pencil-outline", 20, C.sub)
                }
                Box(Modifier.size(34.dp, 38.dp).clickable { toggleFav() }, contentAlignment = Alignment.Center) {
                    MdiIcon(if (isFav) "star" else "star-outline", 20, if (isFav) Color(0xFFb97d10) else C.sub)
                }
                Box(Modifier.size(30.dp, 38.dp).clickable { moreOpen = true }, contentAlignment = Alignment.Center) {
                    MdiIcon("dots-vertical", 20, C.sub)
                }
            }
        },
        scrollable = false,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 40.dp),
        ) {
            // balance line
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            when {
                                kotlin.math.abs(balance) < 0.005 -> C.cardAlt
                                balance > 0 -> Color(0x2416a085)
                                else -> Color(0x1fd64545)
                            }
                        )
                        .border(1.dp, C.line, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    MdiIcon(
                        when {
                            balance > 0.005 -> "arrow-bottom-left"
                            balance < -0.005 -> "arrow-top-right"
                            else -> "scale-balance"
                        },
                        13,
                        when {
                            balance > 0.005 -> C.green
                            balance < -0.005 -> C.red
                            else -> C.sub
                        },
                    )
                    Money(balance, signed = true, colorize = true, size = 15, weight = FontWeight.ExtraBold, compact = true)
                }
                Text(
                    if (balance > 0.005) t("cd.theyOweYou") else if (balance < -0.005) t("cd.youOweThem") else t("cd.settledUp"),
                    fontSize = F.small.sp,
                    color = C.sub,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                if (pendingLists > 0.005) {
                    Text("(${t("lg.openLists")}: ", fontSize = 11.sp, color = C.faint)
                    Money(pendingLists, signed = true, size = 11, weight = FontWeight.SemiBold, compact = true)
                    Text(")", fontSize = 11.sp, color = C.faint)
                }
                // v1.6.18 — blocked contacts carry a red badge on the navbar row
                if (phone != null && Calls.isBlocked(phone)) {
                    Pill(t("cd.blocked"), "red")
                }
            }
            // quick actions — 6×2 grid
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                for (row in 0..1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        quickActions.subList(row * 6, row * 6 + 6).forEach { (icon, label, run) ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { run() }
                                    .padding(vertical = 9.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                MdiIcon(icon, 22, C.brand)
                                Text(
                                    label,
                                    fontSize = F.tiny.sp,
                                    color = C.sub,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            // segmented tabs
            Segmented(
                listOf(
                    Triple("ledger", t("tab.ledger"), null),
                    Triple("lists", t("tab.lists"), null),
                    Triple("notes", t("tab.notes"), null),
                    Triple("bank", t("tab.bank"), null),
                    Triple("reminders", t("tab.reminders"), null),
                    Triple("info", t("tab.info"), null),
                ),
                seg,
            ) { seg = it }
            // v1.6.18 — SECTION SEPARATION: the tab body sits on its own soft
            // card surface with a hairline outline + generous top spacing —
            // the page read as one flat block before.
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(C.line)
                    .padding(0.dp),
            )
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(C.card)
                    .border(1.dp, C.line, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                if (!bodyReady) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 34.dp), contentAlignment = Alignment.Center) { Spinner(22) }
                } else {
                    when (seg) {
                        "lists" -> ListsSection(ownerId)
                        "notes" -> NotesSection(ownerId)
                        "bank" -> BankSection(ownerId)
                        "reminders" -> RemindersSection(ownerId = ownerId)
                        "info" -> ContactInfo(
                            contact = contact,
                            socials = socials,
                            onPhoneOptions = { phoneSheet = it },
                            onAddSocial = { socialSheet = it },
                            onRemoveSocial = { AppStore.removeSocial(recordID, it) },
                        )
                        else -> LedgerSection(ownerId)
                    }
                }
            }
        }
    }

    // number picker
    numPicker?.let { action ->
        NumberPickerSheet(
            phones = dedupePhones(),
            countryCode = countryCode,
            // v1.6.18 — the row icon matches the action the sheet was opened
            // for: a call icon when Call was tapped, a message icon for SMS.
            actionIcon = when (action) { "sms" -> "message-text-outline"; "whatsapp" -> "whatsapp"; else -> "phone" },
            onPick = { fireAction(action, it) },
            onClose = { numPicker = null },
            onAddEdit = {
                numPicker = null
                UiStore.open(Overlay.ContactEdit(recordID))
            },
        )
    }

    // telegram chooser
    if (tgPicker) {
        TelegramPickerSheet(
            socials = socials.filter { it.app == "telegram" },
            numbers = dedupePhones().map { it.number },
            countryCode = countryCode,
            onClose = { tgPicker = false },
            onAddHandle = { tgPicker = false; socialSheet = "telegram" },
            onAddEditNumbers = { tgPicker = false; UiStore.open(Overlay.ContactEdit(recordID)) },
        )
    }

    // email chooser
    if (emailPicker) {
        EmailPickerSheet(
            emails = contact.emails.map { it.address },
            onClose = { emailPicker = false },
            onAddEdit = { emailPicker = false; UiStore.open(Overlay.ContactEdit(recordID)) },
        )
    }

    // ＋ add-to-contact chooser — v1.6.18: GONE; everything lives in the
    // More sheet now (see below).

    // more sheet — v1.6.18 REBUILD: social strip that scrolls sideways with
    // labeled icons (every number’s WhatsApp/Telegram + saved socials), the
    // add-to options, the general actions, and Block / Unblock.
    if (moreOpen) {
        val blockedNow = phone != null && Calls.isBlocked(phone)
        BottomSheet(title = displayName(contact), onClose = { moreOpen = false }) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // social strip (horizontal, scrolls if it overflows)
                Text(t("cd.moreSocial"), fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C.sub, modifier = Modifier.padding(bottom = 8.dp))
                ScrollRow {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        dedupePhones().forEach { p ->
                            val digits = normalizePhone(p.number, countryCode).replace(Regex("\\D"), "")
                            if (digits.length >= 7) {
                                MoreSocialItem("whatsapp", "WhatsApp", Color(0xFF1faa53)) { Messengers.waLink(p.number) }
                                MoreSocialItem("send", "Telegram", C.blue) { Messengers.tgLink(digits) }
                            }
                        }
                        socials.forEach { s ->
                            MoreSocialItem(socialIcon(s.app), SOCIAL_APPS.find { it.first == s.app }?.second ?: s.app, C.sub) {
                                Platform.share.openUrl(socialUrl(s))
                            }
                        }
                        MoreSocialItem("plus", t("ce.addSocial"), C.brand) { moreOpen = false; socialSheet = "telegram" }
                    }
                }
                Divider()
                // add-to group
                Text(t("cd.addTo"), fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C.sub, modifier = Modifier.padding(bottom = 2.dp))
                listOf(
                    Triple("phone-plus", t("ce.addPhone")) { moreOpen = false; UiStore.open(Overlay.ContactEdit(recordID)) },
                    Triple("email-plus-outline", t("ce.addEmail")) { moreOpen = false; UiStore.open(Overlay.ContactEdit(recordID)) },
                    Triple("link-plus", t("ce.addSocial")) { moreOpen = false; socialSheet = "telegram" },
                ).forEach { (icon, label, run) ->
                    PressableRow(onClick = { run() }) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MdiIcon(icon, 20, C.ink)
                            Text(label, fontSize = 16.sp, color = C.ink)
                        }
                    }
                }
                Divider()
                // general group
                listOf(
                    Triple("pencil-outline", t("cd.editContact")) { UiStore.open(Overlay.ContactEdit(recordID)) },
                    Triple("card-account-details-outline", t("cd.shareVcard")) { shareContactVcard(contact) },
                    Triple("email-outline", t("act.email")) { email?.let { Messengers.mailTo(it) } ?: Toasts.toast(t("cd.noEmail"), "error") },
                    Triple("content-copy", t("cd.copyNumber")) { copyNumber() },
                    Triple("dialpad", t("cd.openInDialer")) { phone?.let { Platform.calls.dial(it) } },
                ).forEach { (icon, label, run) ->
                    PressableRow(onClick = { moreOpen = false; run() }) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MdiIcon(icon, 20, C.ink)
                            Text(label, fontSize = 16.sp, color = C.ink)
                        }
                    }
                }
                Divider()
                // v1.6.18 — block / unblock (in-app block list; calls from the
                // keypad / detail / search show the blocked notice)
                val blockLabel = if (blockedNow) t("cd.unblockContact") else t("cd.blockContact")
                PressableRow(onClick = {
                    if (phone != null) {
                        AppStore.toggleBlocked(phone)
                        Toasts.toast(if (blockedNow) t("cd.numberUnblocked") else t("cd.numberBlocked"))
                        moreOpen = false
                    } else Toasts.toast(t("cd.noPhone"), "error")
                }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MdiIcon(if (blockedNow) "lock-off-outline" else "lock-outline", 20, if (blockedNow) C.green else C.red)
                        Column(Modifier.weight(1f)) {
                            Text(blockLabel, fontSize = 16.sp, color = if (blockedNow) C.green else C.red)
                            Text(t("cd.blockedHint"), fontSize = F.tiny.sp, color = C.faint, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    // add-social sheet
    socialSheet?.let { app ->
        AddSocialSheet(
            initialApp = app,
            phoneNumber = phone,
            countryCode = countryCode,
            onClose = { socialSheet = null },
            onSave = { a, handle ->
                AppStore.addSocial(recordID, a, handle)
                Toasts.toast("${t("ce.addSocial")} ✓")
            },
        )
    }

    // per-phone options
    phoneSheet?.let { number ->
        BottomSheet(title = prettyPhone(number, countryCode), onClose = { phoneSheet = null }) {
            Column {
                listOf(
                    Triple("phone", t("act.call")) { Calls.placeCall(number) },
                    Triple("whatsapp", t("act.whatsapp")) { Messengers.waLink(number) },
                    Triple("message-text-outline", t("act.sms")) { Messengers.smsLink(number) },
                    Triple("content-copy", t("act.copy")) { Platform.share.copyText(number); Toasts.toast(t("act.copied")) },
                    Triple("star", t("cd.setPrimary")) {
                        AppStore.setPrimaryPhone(recordID, number)
                        Toasts.toast(t("cd.primaryUpdated"))
                    },
                ).forEach { (icon, label, run) ->
                    PressableRow(onClick = { phoneSheet = null; run() }) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MdiIcon(icon, 20, C.ink)
                            Text(label, fontSize = 16.sp, color = C.ink)
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- Info tab ---------------- */

@Composable
fun ContactInfo(
    contact: DeviceContact,
    socials: List<SocialLink>,
    onPhoneOptions: (String) -> Unit,
    onAddSocial: (String) -> Unit,
    onRemoveSocial: (String) -> Unit,
) {
    val C = C()
    val countryCode = AppStore.state.settings.countryCode
    val systemCalls = AppStore.state.systemCalls

    /* call history — the real system log matched against every number */
    val history = remember(contact.recordID, systemCalls) {
        val myNumbers = contact.phones.map { normalizePhone(it.number, countryCode).replace(Regex("\\D"), "") }.toSet()
        systemCalls.filter { c -> normalizePhone(c.number, countryCode).replace(Regex("\\D"), "") in myNumbers }
            .sortedByDescending { it.date }
            .take(10)
    }

    Column {
        // call history
        InfoBlock(t("cd.callHistory")) {
            if (history.isEmpty()) {
                Text(t("cd.noCallsYet"), fontSize = F.tiny.sp, color = C.faint)
            }
            history.forEach { c ->
                val dir = when (c.type) {
                    2 -> "outgoing" to "phone-outgoing"
                    3, 5, 6 -> "missed" to "phone-missed"
                    else -> "incoming" to "phone-incoming"
                }
                PressableRow(onClick = { Calls.placeCall(c.number) }) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MdiIcon(dir.second, 15, when (dir.first) {
                            "missed" -> C.red
                            "incoming" -> C.green
                            else -> C.brand
                        })
                        Text(
                            "${t("cd.${dir.first}")} · ${relTimeOf(c.date)} · ${ft(c.date)}" + (if (c.duration > 0) " · ${durationStr(c.duration)}" else ""),
                            fontSize = F.small.sp,
                            color = C.ink,
                            modifier = Modifier.weight(1f),
                        )
                        MdiIcon("phone-outline", 16, C.brand)
                    }
                }
            }
        }
        contact.birthday?.let {
            InfoBlock(t("cd.birthday")) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MdiIcon("cake-variant-outline", 17, C.amber)
                    Text(fd(it), fontSize = F.small.sp, color = C.ink)
                }
            }
        }
        if (contact.phones.isNotEmpty()) {
            InfoBlock(t("cd.phones")) {
                contact.phones.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onPhoneOptions(p.number) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Pill(p.label, "brand")
                        Text(prettyPhone(p.number, countryCode), fontSize = F.small.sp, color = C.ink, modifier = Modifier.weight(1f))
                        if (isPrimaryMark(p.number, contact)) MdiIcon("star", 14, Color(0xFFb97d10))
                        IconBtn("content-copy", { Platform.share.copyText(p.number); Toasts.toast(t("act.copied")) }, size = 28)
                        IconBtn("phone-outline", { Calls.placeCall(p.number) }, size = 28)
                    }
                }
            }
        }
        if (contact.emails.isNotEmpty()) {
            InfoBlock(t("cd.emails")) {
                contact.emails.forEach { e ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(e.label, "brand")
                        Text(e.address, fontSize = F.small.sp, color = C.blue, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconBtn("content-copy", { Platform.share.copyText(e.address); Toasts.toast(t("act.copied")) }, size = 28)
                    }
                }
            }
        }
        // socials — auto rows from numbers + saved socials + dashed add
        InfoBlock(t("cd.socials")) {
            // auto rows from number
            contact.phones.forEach { p ->
                val digits = normalizePhone(p.number, countryCode).replace(Regex("\\D"), "")
                if (digits.length >= 7) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MdiIcon("whatsapp", 16, Color(0xFF1faa53))
                        Text("WhatsApp · ${p.number}", fontSize = F.small.sp, color = C.ink, modifier = Modifier.weight(1f))
                        IconBtn("open-in-new", { Messengers.waLink(p.number) }, size = 28)
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MdiIcon("send", 16, C.blue)
                        Text("Telegram · ${p.number}", fontSize = F.small.sp, color = C.ink, modifier = Modifier.weight(1f))
                        IconBtn("open-in-new", { Messengers.tgLink(digits) }, size = 28)
                    }
                }
            }
            socials.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MdiIcon(socialIcon(s.app), 16, C.sub)
                    Text("${SOCIAL_APPS.find { it.first == s.app }?.second ?: s.app} · ${s.handle}", fontSize = F.small.sp, color = C.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconBtn("open-in-new", { Platform.share.openUrl(socialUrl(s)) }, size = 28)
                    IconBtn("trash-can-outline", { onRemoveSocial(s.id) }, size = 28, danger = true)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, C.line, RoundedCornerShape(12.dp))
                    .clickable { onAddSocial("telegram") }
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MdiIcon("plus", 15, C.brand)
                Text(" ${t("ce.addSocial")}", fontSize = F.small.sp, color = C.brand, fontWeight = FontWeight.Bold)
            }
        }
        if (contact.addresses.isNotEmpty()) {
            InfoBlock(t("cd.addresses")) {
                contact.addresses.forEach { a ->
                    PressableRow(onClick = { Platform.share.copyText(a.formatted); Toasts.toast(t("act.copied")) }) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill(a.label, "brand")
                            Text(a.formatted, fontSize = F.small.sp, color = C.sub, modifier = Modifier.weight(1f), maxLines = 2)
                        }
                    }
                }
            }
        }
        if (!contact.company.isNullOrEmpty() || !contact.jobTitle.isNullOrEmpty()) {
            InfoBlock(t("cd.work")) {
                Text(
                    listOfNotNull(contact.company, contact.jobTitle).joinToString(" · "),
                    fontSize = F.small.sp,
                    color = C.ink,
                )
            }
        }
        contact.note?.let {
            if (it.isNotEmpty()) {
                InfoBlock(t("ce.note")) { Text(it, fontSize = F.small.sp, color = C.sub) }
            }
        }
    }
}

private fun relTimeOf(epoch: Long): String = com.callmony.lib.relTime(epoch)

private fun isPrimaryMark(number: String, contact: DeviceContact): Boolean {
    val p = primaryPhone(contact) ?: return false
    return p.replace(Regex("\\D"), "") == number.replace(Regex("\\D"), "")
}

@Composable
private fun InfoBlock(title: String, children: @Composable () -> Unit) {
    val C = C()
    // v1.6.18 — soft card + hairline per block: visual FILLING between the
    // sections (the tab read as one flat slab before).
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(C.card)
            .border(1.dp, C.line, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(title, fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C.sub, modifier = Modifier.padding(bottom = 5.dp))
        children()
    }
}

/** v1.6.18 — one labeled social tile in the More sheet’s sideways strip. */
@Composable
private fun MoreSocialItem(icon: String, label: String, tint: Color, onPress: () -> Unit) {
    val C = C()
    Column(
        Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(C.chip)
            .clickable { onPress() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MdiIcon(icon, 20, tint)
        Text(label, fontSize = F.tiny.sp, color = C.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/* ---------------- pickers ---------------- */

@Composable
fun NumberPickerSheet(
    phones: List<DevicePhone>,
    countryCode: String,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
    onAddEdit: () -> Unit,
    actionIcon: String = "phone",
) {
    val C = C()
    BottomSheet(title = t("cd.chooseNumber"), onClose = onClose) {
        Column {
            phones.forEach { p ->
                PressableRow(onClick = { onPick(p.number) }, onLongClick = { Platform.share.copyText(p.number); Toasts.toast(t("act.copied")) }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill(p.label, "brand")
                        Text(prettyPhone(p.number, countryCode), fontSize = F.body.sp, color = C.ink, modifier = Modifier.weight(1f))
                        // v1.6.18 — every row carries the ACTION icon (call /
                        // message) so the list reads like a list of dial buttons.
                        IconBtn(actionIcon, { onPick(p.number) }, size = 30)
                        IconBtn("content-copy", { Platform.share.copyText(p.number); Toasts.toast(t("act.copied")) }, size = 28)
                    }
                }
            }
            if (phones.isEmpty()) Text(t("cd.noPhone"), fontSize = F.small.sp, color = C.faint, modifier = Modifier.padding(vertical = 8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, C.line, RoundedCornerShape(12.dp))
                    .clickable { onAddEdit() }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                MdiIcon("plus", 15, C.brand)
                Text(" ${t("cd.addOrEditNumbers")}", fontSize = F.small.sp, color = C.brand, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TelegramPickerSheet(
    socials: List<SocialLink>,
    numbers: List<String>,
    countryCode: String,
    onClose: () -> Unit,
    onAddHandle: () -> Unit,
    onAddEditNumbers: () -> Unit,
) {
    val C = C()
    BottomSheet(title = t("act.telegram"), onClose = onClose) {
        Column {
            socials.forEach { s ->
                PressableRow(onClick = { Platform.share.openUrl(socialUrl(s)); onClose() }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MdiIcon("send", 18, C.blue)
                        Text(s.handle, fontSize = F.body.sp, color = C.ink, modifier = Modifier.weight(1f))
                    }
                }
            }
            numbers.forEach { n ->
                PressableRow(onClick = { Messengers.tgLink(normalizePhone(n, countryCode).replace(Regex("\\D"), "")); onClose() }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MdiIcon("send", 18, C.sub)
                        Text(prettyPhone(n, countryCode), fontSize = F.body.sp, color = C.ink, modifier = Modifier.weight(1f))
                    }
                }
            }
            ChooserAddRow("at", t("cd.noTelegram")) { onAddHandle() }
            ChooserAddRow("pencil-outline", t("cd.addOrEditNumbers")) { onAddEditNumbers() }
        }
    }
}

@Composable
fun EmailPickerSheet(emails: List<String>, onClose: () -> Unit, onAddEdit: () -> Unit) {
    val C = C()
    BottomSheet(title = t("cd.chooseEmail"), onClose = onClose) {
        Column {
            if (emails.isEmpty()) Text(t("cd.noEmail"), fontSize = F.small.sp, color = C.faint, modifier = Modifier.padding(vertical = 8.dp))
            emails.forEach { e ->
                PressableRow(onClick = { Messengers.mailTo(e); onClose() }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MdiIcon("email-outline", 18, C.blue)
                        Text(e, fontSize = F.body.sp, color = C.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            ChooserAddRow("email-plus-outline", t("cd.addOrEditNumbers")) { onAddEdit() }
        }
    }
}

@Composable
private fun ChooserAddRow(icon: String, label: String, onPress: () -> Unit) {
    val C = C()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, C.line, RoundedCornerShape(12.dp))
            .clickable { onPress() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MdiIcon(icon, 15, C.brand)
        Text(label, fontSize = F.small.sp, color = C.brand, fontWeight = FontWeight.Bold)
    }
}

/* ---------------- add-social sheet ---------------- */

@Composable
fun AddSocialSheet(
    initialApp: String,
    phoneNumber: String?,
    countryCode: String,
    onClose: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    val C = C()
    var app by remember { mutableStateOf(initialApp) }
    var handle by remember(initialApp) {
        mutableStateOf(
            if (app == "telegram" || app == "whatsapp")
                phoneNumber?.let { normalizePhone(it, countryCode).replace(Regex("\\D"), "") } ?: ""
            else ""
        )
    }

    BottomSheet(title = t("ce.addSocial"), onClose = onClose) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                ScrollRow {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SOCIAL_APPS.forEach { (key, label, icon) ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (app == key) C.brandSoft else C.chip)
                                    .clickable { app = key }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    MdiIcon(icon, 16, if (app == key) C.brand else C.sub)
                                    Text(label, fontSize = F.small.sp, color = if (app == key) C.brand else C.sub, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            Field(
                label = t("ce.socialPlaceholder"),
                value = handle,
                onChangeText = { handle = it },
                placeholder = if (app == "other") "https://…" else "@username",
            )
            Btn(label = t("act.save"), onPress = {
                if (handle.isNotBlank()) {
                    onSave(app, handle.trim())
                    onClose()
                }
            })
        }
    }
}
