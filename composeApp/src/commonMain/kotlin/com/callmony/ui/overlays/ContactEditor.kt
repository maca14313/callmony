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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.lib.displayName
import com.callmony.lib.now
import com.callmony.lib.uid
import com.callmony.model.DeviceContact
import com.callmony.model.DeviceEmail
import com.callmony.model.DevicePhone
import com.callmony.platform.Platform
import com.callmony.theme.F
import com.callmony.ui.components.Sheet
import com.callmony.ui.kit.Btn
import com.callmony.ui.kit.C
import com.callmony.ui.kit.Field
import com.callmony.ui.kit.MdiIcon
import com.callmony.ui.kit.Spinner
import com.callmony.ui.kit.Toasts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ContactEditor / ContactCreator — the IN-APP editor (v1.5.4+): the OS
   external editor was replaced. First/Middle/Last + Company; phone rows
   with label cycling mobile→work→home→other and the default star; email
   rows; creator accepts prefilled phone/name; zero-lag save.
   Port of src/overlays/ContactEditor.tsx. */

private val LABEL_CYCLE = listOf("mobile", "work", "home", "other")
private val LABEL_ICON = mapOf(
    "mobile" to "cellphone",
    "work" to "briefcase-outline",
    "home" to "home-outline",
    "other" to "phone-outline",
    "main" to "phone",
    "office" to "domain",
)

private data class PhoneRow(var label: String, var number: String)
private data class EmailRow(var label: String, var address: String)

@Composable
fun ContactEditorOverlay(recordID: String) {
    val pop = { UiStore.pop() }
    val contact = AppStore.state.deviceContacts.find { it.recordID == recordID }
    var loading by remember { mutableStateOf(contact == null) }
    var timedOut by remember { mutableStateOf(false) }

    var given by remember { mutableStateOf(contact?.givenName ?: "") }
    var middle by remember { mutableStateOf(contact?.middleName ?: "") }
    var family by remember { mutableStateOf(contact?.familyName ?: "") }
    var company by remember { mutableStateOf(contact?.company ?: "") }
    var phones by remember { mutableStateOf(contact?.phones?.map { PhoneRow(it.label, it.number) } ?: listOf(PhoneRow("mobile", ""))) }
    var emails by remember { mutableStateOf(contact?.emails?.map { EmailRow(it.label, it.address) }.orEmpty()) }
    var defaultIdx by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }

    /* if the contact is not in the local cache yet (deep link), fetch once */
    LaunchedEffect(recordID) {
        if (contact == null) {
            var waited = 0
            while (AppStore.state.deviceContacts.none { it.recordID == recordID } && waited < 6000) {
                delay(300); waited += 300
            }
            loading = false
            if (waited >= 6000) timedOut = true
        }
    }

    /* re-fill fields once the contact appears */
    val resolved = AppStore.state.deviceContacts.find { it.recordID == recordID }
    LaunchedEffect(resolved?.recordID) {
        if (resolved != null && loading) {
            loading = false
            given = resolved.givenName
            middle = resolved.middleName ?: ""
            family = resolved.familyName
            company = resolved.company ?: ""
            phones = resolved.phones.map { PhoneRow(it.label, it.number) }.ifEmpty { listOf(PhoneRow("mobile", "")) }
            emails = resolved.emails.map { EmailRow(it.label, it.address) }
        }
    }

    if (loading) {
        Sheet(title = t("ce.editContact"), onBack = pop) {
            Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spinner(26)
                    if (timedOut) {
                        Text(t("ce.loadFailed"), fontSize = F.small.sp, color = C().red, modifier = Modifier.padding(top = 10.dp))
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Btn(small = true, label = t("act.retry"), onPress = { timedOut = false; loading = true; AppStore.refreshDeviceContacts() })
                            Btn(small = true, label = t("act.cancel"), variant = "secondary", onPress = pop)
                        }
                    }
                }
            }
        }
        return
    }

    val canSave = (given.isNotBlank() || family.isNotBlank() || middle.isNotBlank() || company.isNotBlank()) &&
        phones.any { it.number.replace(Regex("\\D"), "").length >= 3 }

    Sheet(
        title = t("ce.editContact"),
        subtitle = resolved?.let { displayName(it) },
        onBack = pop,
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn(
                    label = t("act.saveContact"),
                    onPress = {
                        val valid = phones.filter { it.number.isNotBlank() }
                        if (valid.isEmpty()) {
                            Toasts.toast(t("ce.atLeastOnePhone"), "error")
                            return@Btn
                        }
                        saving = true
                        val ok = Platform.contacts.updateContact(
                            recordID,
                            given.trim(), middle.trim().ifEmpty { null }, family.trim(), company.trim().ifEmpty { null },
                            valid.map { it.label to it.number },
                            emails.filter { it.address.isNotBlank() }.map { it.label to it.address },
                        )
                        saving = false
                        if (ok) {
                            AppStore.patchDeviceContact(recordID) { old ->
                                old.copy(
                                    givenName = given.trim(),
                                    middleName = middle.trim().ifEmpty { null },
                                    familyName = family.trim(),
                                    company = company.trim().ifEmpty { null },
                                    phones = valid.map { DevicePhone(it.number, it.label, it.number) },
                                    emails = emails.filter { it.address.isNotBlank() }.map { DeviceEmail(it.address, it.label, it.address) },
                                )
                            }
                            Toasts.toast(t("ce.contactUpdated"))
                            pop()
                        } else Toasts.toast(t("cd.saveFailed"), "error")
                    },
                    disabled = !canSave || saving,
                    loading = saving,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Field(label = t("ce.firstName"), value = given, onChangeText = { given = it }) }
                Box(Modifier.weight(1f)) { Field(label = t("ce.middleName"), value = middle, onChangeText = { middle = it }) }
            }
            Field(label = t("ce.lastName"), value = family, onChangeText = { family = it })
            Field(label = t("ce.company"), value = company, onChangeText = { company = it })

            Text(t("cd.phones"), fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C().sub, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
            phones.forEachIndexed { i, row ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(C().chip)
                            .clickable {
                                phones = phones.toMutableList().also {
                                    it[i] = it[i].copy(label = LABEL_CYCLE[(LABEL_CYCLE.indexOf(it[i].label) + 1) % LABEL_CYCLE.size])
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            MdiIcon(LABEL_ICON[row.label] ?: "phone-outline", 16, C().sub)
                            Text(t("cd.${row.label}"), fontSize = F.small.sp, color = C().sub, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        Field(value = row.number, onChangeText = { v -> phones = phones.toMutableList().also { it[i] = it[i].copy(number = v) } }, keyboardType = "phone-pad", placeholder = t("ce.phonePlaceholder"))
                    }
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (defaultIdx == i) Color(0x2eb97d10) else Color.Transparent)
                            .clickable { defaultIdx = i },
                        contentAlignment = Alignment.Center,
                    ) { MdiIcon(if (defaultIdx == i) "star" else "star-outline", 19, if (defaultIdx == i) Color(0xFFb97d10) else C().faint) }
                    if (phones.size > 1) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).clickable { if (phones.size > 1) phones = phones.filterIndexed { idx, _ -> idx != i } }, contentAlignment = Alignment.Center) {
                            MdiIcon("close", 18, C().red)
                        }
                    }
                }
            }
            Row(Modifier.padding(bottom = 10.dp)) {
                Btn(small = true, label = t("ce.addPhone"), icon = "phone-plus", variant = "soft", onPress = { phones = phones + PhoneRow("mobile", "") })
            }

            Text(t("cd.emails"), fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C().sub, modifier = Modifier.padding(bottom = 6.dp))
            emails.forEachIndexed { i, row ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    Box(Modifier.weight(1f)) {
                        Field(value = row.address, onChangeText = { v -> emails = emails.toMutableList().also { it[i] = it[i].copy(address = v) } }, keyboardType = "email-address", placeholder = t("ce.emailPlaceholder"))
                    }
                    if (emails.size > 0) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).clickable { emails = emails.filterIndexed { idx, _ -> idx != i } }, contentAlignment = Alignment.Center) {
                            MdiIcon("close", 18, C().red)
                        }
                    }
                }
            }
            Row(Modifier.padding(bottom = 6.dp)) {
                Btn(small = true, label = t("ce.addEmail"), icon = "email-plus-outline", variant = "soft", onPress = { emails = emails + EmailRow("home", "") })
            }
        }
    }
}

@Composable
fun ContactCreatorOverlay(prefillPhone: String?, prefillName: String?) {
    val pop = { UiStore.pop() }
    var given by remember { mutableStateOf(prefillName ?: "") }
    var middle by remember { mutableStateOf("") }
    var family by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var phones by remember { mutableStateOf(listOf(PhoneRow("mobile", prefillPhone ?: ""))) }
    var emails by remember { mutableStateOf(listOf<EmailRow>()) }
    var defaultIdx by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    val C = C()

    val canSave = (given.isNotBlank() || family.isNotBlank() || middle.isNotBlank() || company.isNotBlank()) &&
        phones.any { it.number.replace(Regex("\\D"), "").length >= 3 }

    Sheet(
        title = t("ce.newContact"),
        onBack = pop,
        footer = {
            Btn(
                label = t("ce.saveContact"),
                onPress = {
                    val valid = phones.filter { it.number.isNotBlank() }
                    if (valid.isEmpty()) {
                        Toasts.toast(t("ce.atLeastOnePhone"), "error")
                        return@Btn
                    }
                    saving = true
                    val id = Platform.contacts.createContact(
                        given.trim(), middle.trim().ifEmpty { null }, family.trim(), company.trim().ifEmpty { null },
                        valid.map { it.label to it.number },
                        emails.filter { it.address.isNotBlank() }.map { it.label to it.address },
                    )
                    saving = false
                    if (id != null) {
                        AppStore.addDeviceContactToList(
                            DeviceContact(
                                recordID = id,
                                displayName = listOf(given.trim(), middle.trim(), family.trim()).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { company.trim() },
                                givenName = given.trim(),
                                middleName = middle.trim().ifEmpty { null },
                                familyName = family.trim(),
                                company = company.trim().ifEmpty { null },
                                phones = valid.map { DevicePhone(it.number, it.label, it.number) },
                                emails = emails.filter { it.address.isNotBlank() }.map { DeviceEmail(it.address, it.label, it.address) },
                            )
                        )
                        Toasts.toast(t("ce.contactSaved"))
                        pop()
                    } else Toasts.toast(t("cd.saveFailed"), "error")
                },
                disabled = !canSave || saving,
                loading = saving,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Field(label = t("ce.firstName"), value = given, onChangeText = { given = it }) }
                Box(Modifier.weight(1f)) { Field(label = t("ce.middleName"), value = middle, onChangeText = { middle = it }) }
            }
            Field(label = t("ce.lastName"), value = family, onChangeText = { family = it })
            Field(label = t("ce.company"), value = company, onChangeText = { company = it })

            Text(t("cd.phones"), fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C.sub, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
            phones.forEachIndexed { i, row ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(C.chip)
                            .clickable {
                                phones = phones.toMutableList().also {
                                    it[i] = it[i].copy(label = LABEL_CYCLE[(LABEL_CYCLE.indexOf(it[i].label) + 1) % LABEL_CYCLE.size])
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            MdiIcon(LABEL_ICON[row.label] ?: "phone-outline", 16, C.sub)
                            Text(t("cd.${row.label}"), fontSize = F.small.sp, color = C.sub, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        Field(value = row.number, onChangeText = { v -> phones = phones.toMutableList().also { it[i] = it[i].copy(number = v) } }, keyboardType = "phone-pad", placeholder = t("ce.phonePlaceholder"))
                    }
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (defaultIdx == i) Color(0x2eb97d10) else Color.Transparent)
                            .clickable { defaultIdx = i },
                        contentAlignment = Alignment.Center,
                    ) { MdiIcon(if (defaultIdx == i) "star" else "star-outline", 19, if (defaultIdx == i) Color(0xFFb97d10) else C.faint) }
                    if (phones.size > 1) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).clickable { if (phones.size > 1) phones = phones.filterIndexed { idx, _ -> idx != i } }, contentAlignment = Alignment.Center) {
                            MdiIcon("close", 18, C.red)
                        }
                    }
                }
            }
            Row(Modifier.padding(bottom = 10.dp)) {
                Btn(small = true, label = t("ce.addPhone"), icon = "phone-plus", variant = "soft", onPress = { phones = phones + PhoneRow("mobile", "") })
            }

            Text(t("cd.emails"), fontSize = F.small.sp, fontWeight = FontWeight.ExtraBold, color = C.sub, modifier = Modifier.padding(bottom = 6.dp))
            emails.forEachIndexed { i, row ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    Box(Modifier.weight(1f)) {
                        Field(value = row.address, onChangeText = { v -> emails = emails.toMutableList().also { it[i] = it[i].copy(address = v) } }, keyboardType = "email-address", placeholder = t("ce.emailPlaceholder"))
                    }
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).clickable { emails = emails.filterIndexed { idx, _ -> idx != i } }, contentAlignment = Alignment.Center) {
                        MdiIcon("close", 18, C.red)
                    }
                }
            }
            Row(Modifier.padding(bottom = 6.dp)) {
                Btn(small = true, label = t("ce.addEmail"), icon = "email-plus-outline", variant = "soft", onPress = { emails = emails + EmailRow("home", "") })
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
