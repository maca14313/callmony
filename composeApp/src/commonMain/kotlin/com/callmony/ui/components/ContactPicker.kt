package com.callmony.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.displayName
import com.callmony.lib.prettyPhone
import com.callmony.lib.searchContacts
import com.callmony.model.DeviceContact
import com.callmony.platform.Platform
import com.callmony.ui.kit.Avatar
import com.callmony.ui.kit.C
import com.callmony.ui.kit.EmptyState
import com.callmony.ui.kit.IconBtn
import com.callmony.ui.kit.MdiIcon
import com.callmony.ui.kit.Toaster
import com.callmony.ui.kit.Toasts
import com.callmony.ui.kit.vibrate

/* ContactPicker — full-screen search sheet used everywhere an owner or a
   contact target is chosen (port of src/components/ContactPicker.tsx).
   Long-press a row (500 ms) copies the number. */

@Composable
fun ContactPicker(
    title: String,
    onPick: (DeviceContact) -> Unit,
    onBack: () -> Unit,
    hint: String? = null,
) {
    val C = C()
    var query by remember { mutableStateOf("") }
    val contacts = AppStore.state.deviceContacts
    val countryCode = AppStore.state.settings.countryCode

    val results = remember(query, contacts) {
        if (query.isBlank()) contacts
        else searchContacts(contacts, query, ::displayName, countryCode).map { it.contact }
    }

    LaunchedEffect(Unit) {
        if (contacts.isEmpty()) AppStore.refreshDeviceContacts()
    }

    // v1.6.15: native back closes the picker itself (inner handler wins)
    com.callmony.ui.kit.AppBackHandler(enabled = true) { onBack() }

    Column(Modifier.fillMaxSize().background(C.canvas).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconBtn("arrow-left", onBack, size = 40)
            Text(
                title,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = C.ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // search field
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(C.inputBg)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MdiIcon("magnify", 20, C.faint)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 16.sp, color = C.ink),
                cursorBrush = SolidColor(C.brand),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(hint ?: t("sr.placeholder"), fontSize = 16.sp, color = C.faint)
                    } else inner()
                },
            )
            if (query.isNotEmpty()) {
                Box(Modifier.clip(CircleShape).clickable { query = "" }) {
                    MdiIcon("close", 16, C.faint, Modifier.padding(4.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f)) {
            if (results.isEmpty()) {
                EmptyState(
                    icon = "account-search-outline",
                    title = if (query.isBlank()) t("cd.noContactsYet") else t("sr.noResultsFor"),
                    hint = if (query.isBlank()) t("cd.noContactsHint") else query,
                )
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) { // v1.6.26: inset handled by the app nav below
                    items(results, key = { it.recordID }) { c ->
                        PressableRow(
                            onClick = { onPick(c) },
                            onLongClick = {
                                c.phones.firstOrNull()?.let {
                                    Platform.share.copyText(it.number)
                                    Toasts.toast(t("act.copied"))
                                }
                            },
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(11.dp),
                            ) {
                                Avatar(c, size = 36)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        displayName(c),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = C.ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        c.phones.firstOrNull()?.let { prettyPhone(it.number, countryCode) }
                                            ?: c.company ?: t("cd.noPhone"),
                                        fontSize = 13.sp,
                                        color = C.faint,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                MdiIcon("chevron-right", 20, C.faint)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
        Toaster()
    }
}
