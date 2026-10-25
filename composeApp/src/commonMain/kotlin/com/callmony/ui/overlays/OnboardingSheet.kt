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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.model.COUNTRY_CODES
import com.callmony.model.CURRENCIES
import com.callmony.theme.F
import com.callmony.ui.kit.Btn
import com.callmony.ui.kit.C
import com.callmony.ui.kit.Chip
import com.callmony.ui.kit.Field
import com.callmony.ui.kit.MdiIcon
import com.callmony.ui.kit.Toaster
import com.callmony.ui.components.ScrollRow

/* OnboardingSheet — first-run: currency + country code + Get started.
   Port of src/overlays/OnboardingSheet.tsx. */

@Composable
fun OnboardingSheet() {
    val C = C()
    var currency by remember { mutableStateOf(AppStore.state.settings.currency) }
    var otherCurrency by remember { mutableStateOf("") }
    var countrySearch by remember { mutableStateOf("") }
    var country by remember { mutableStateOf(AppStore.state.settings.countryCode) }

    val effectiveCurrency = if (currency == "OTHER") {
        otherCurrency.trim().uppercase().take(3).ifEmpty { "ETB" }
    } else currency

    /* v1.6.16 — the Get started button is PINNED at the bottom of the
       screen (always visible without scrolling); the country list scrolls
       UNDER it. Previously the button lived at the very end of the scroll,
       so users had to scroll the whole country list to reach it. */
    Column(
        Modifier
            .fillMaxSize()
            .background(C.canvas)
            // v1.6.14: the onboarding lives OUTSIDE the shell's padded column
            // — without these insets the "Welcome" title sat under the status
            // bar and Get started under the gesture/nav bar.
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
        ) {
        Spacer(Modifier.height(24.dp))
        // v1.6.14: the new logo here too (matches the splash)
        com.callmony.ui.LogoTile(74, corner = 24)
        Text("Welcome to callmony", fontSize = F.title.sp, fontWeight = FontWeight.ExtraBold, color = C.ink, modifier = Modifier.padding(top = 14.dp))
        Text(t("ob.hint"), fontSize = F.small.sp, color = C.sub)

        // currency pills
        Text(t("st.currency"), fontSize = F.body.sp, fontWeight = FontWeight.Bold, color = C.ink, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        Row(Modifier.fillMaxWidth()) {
            ScrollRow {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (CURRENCIES.map { it.code } + "OTHER").forEach { code ->
                        Chip(label = code, compact = true, active = currency == code, onPress = { currency = code })
                    }
                }
            }
        }
        if (currency == "OTHER") {
            Field(value = otherCurrency, onChangeText = { otherCurrency = it }, placeholder = "ETB", modifier = Modifier.padding(top = 8.dp))
        }

        // country picker
        Text(t("st.countryCode"), fontSize = F.body.sp, fontWeight = FontWeight.Bold, color = C.ink, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(C.inputBg)
                .border(1.dp, C.line, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MdiIcon("magnify", 18, C.faint)
            BasicTextField(
                value = countrySearch,
                onValueChange = { countrySearch = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = C.ink),
                cursorBrush = SolidColor(C.brand),
                modifier = Modifier.weight(1f),
                decorationBox = { inner -> if (countrySearch.isEmpty()) Text(t("act.search"), fontSize = 15.sp, color = C.faint) else inner() },
            )
        }
        val filtered = COUNTRY_CODES.filter {
            countrySearch.isBlank() || it.name.contains(countrySearch, true) || it.code.contains(countrySearch, true) || it.dial.contains(countrySearch)
        }
        filtered.forEach { cc ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (country == cc.code) C.brandSoft else C.card)
                    .border(1.dp, if (country == cc.code) C.brand else C.line, RoundedCornerShape(14.dp))
                    .clickable { country = cc.code }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(cc.name, fontSize = F.body.sp, color = C.ink, modifier = Modifier.weight(1f))
                Text("+${cc.dial}", fontSize = F.small.sp, color = C.sub)
                if (country == cc.code) MdiIcon("check-circle", 20, C.brand)
            }
        }

        Spacer(Modifier.height(24.dp))
        }
        // Get started — pinned footer, always visible (content scrolls under)
        Box(
            Modifier
                .fillMaxWidth()
                .background(C.canvas)
                .padding(top = 6.dp, bottom = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Btn(
                label = t("ob.getStarted"),
                onPress = {
                    AppStore.updateSettings {
                        it.copy(onboarded = true, currency = effectiveCurrency, countryCode = country)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            )
        }
    }
    // Toaster for this full-screen modal
}
