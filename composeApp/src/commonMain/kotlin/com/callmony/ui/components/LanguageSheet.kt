package com.callmony.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.LANGUAGES
import com.callmony.i18n.currentLang
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.ui.kit.C
import com.callmony.ui.kit.MdiIcon

/* LanguageSheet — the 5-language picker (native names, check-circle on the
   active row). Port of src/components/LanguageSheet.tsx. */

@Composable
fun LanguageSheet(onClose: () -> Unit) {
    val C = C()
    val active = currentLang().code
    BottomSheet(title = t("st.language"), onClose = onClose) {
        Column(Modifier.padding(vertical = 4.dp)) {
            for (lang in LANGUAGES) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            AppStore.updateSettings { it.copy(language = lang.code) }
                            onClose()
                        }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MdiIcon("translate", 20, C.sub)
                    Text(
                        lang.native,
                        fontSize = 16.sp,
                        fontWeight = if (lang.code == active) FontWeight.Bold else FontWeight.Medium,
                        color = if (lang.code == active) C.brand else C.ink,
                        modifier = Modifier.weight(1f),
                    )
                    if (lang.code == active) MdiIcon("check-circle", 20, C.brand)
                }
            }
        }
    }
}
