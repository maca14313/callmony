package com.callmony.ui.overlays

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.t
import com.callmony.lib.Diag
import com.callmony.lib.DiagReport
import com.callmony.lib.fdEpoch
import com.callmony.lib.ft
import com.callmony.platform.Platform
import com.callmony.ui.components.Sheet
import com.callmony.ui.kit.Btn
import com.callmony.ui.kit.C
import com.callmony.ui.kit.MdiIcon
import com.callmony.ui.kit.Toasts
import com.callmony.theme.F

/* v1.6.20 — the in-app half of the freeze/crash diagnostics.
   Rendered at the very top of the app shell (above the lock screen):

     • LIVE freeze caught this session (the app recovered) — banner card
       "The app just froze — here's what was blocking it"
     • PENDING report from a previous run that ended in a freeze/crash —
       "Callmony froze and was closed last time" with the exact phase +
       main-thread stack of what was running when it died
     • LIST mode (Settings → Diagnostics): every stored report, copyable.

   The other half is the separate-process DiagActivity, which pops over
   the app WHILE it is frozen (see androidMain diag/). */

@Composable
fun DiagnosticsOverlay() {
    if (Diag.listOpen) { DiagListSheet(); return }
    val live = Diag.live
    if (live != null) { DiagReportCard(live, isLive = true); return }
    val pending = Diag.unseen()
    if (pending != null) { DiagReportCard(pending, isLive = false); return }
}

/* ---------------- full-screen single-report card ---------------- */

@Composable
private fun DiagReportCard(report: DiagReport, isLive: Boolean) {
    val C = C()
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF7101418)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
        ) {
            // badge + title
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (report.kind == "crash") C.red else Color(0xFF16A085)),
                    contentAlignment = Alignment.Center,
                ) {
                    MdiIcon(
                        if (report.kind == "crash") "alert-circle-outline" else "alert-outline",
                        30, Color.White,
                    )
                }
                Column {
                    Text(
                        t(if (report.kind == "crash") "diag.crashTitle" else "diag.freezeTitle"),
                        fontSize = F.h2.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                    )
                    Text(
                        if (isLive) t("diag.recovered") else t(if (report.kind == "crash") "diag.crashSub" else "diag.freezeSub"),
                        fontSize = F.small.sp, color = Color(0xFF9AA4AF),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // headline
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1B1F24))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(report.headline, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFD7DDE3))
            }

            Spacer(Modifier.height(14.dp))

            // where + when
            DiagMeta(t("diag.where"), report.phase)
            DiagMeta(
                t("diag.when"),
                fdEpoch(report.id) + " · " + ft(report.id),
            )

            Spacer(Modifier.height(6.dp))

            // what was running
            Text(t("diag.running"), fontSize = F.small.sp, color = Color(0xFF9AA4AF), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1B1F24))
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                Text(
                    report.stack.ifEmpty { "—" },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFD7DDE3),
                    lineHeight = 16.sp,
                )
            }

            // app state (collapsible)
            var stateOpen by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { stateOpen = !stateOpen }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MdiIcon(if (stateOpen) "chevron-down" else "chevron-right", 18, Color(0xFF9AA4AF))
                Text(t("diag.state"), fontSize = F.small.sp, color = Color(0xFF9AA4AF), fontWeight = FontWeight.SemiBold)
            }
            if (stateOpen) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1B1F24))
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    Text(
                        report.detail,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFB9C2CC),
                        lineHeight = 15.sp,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // actions
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn(
                    label = t("diag.copy"),
                    onPress = {
                        Platform.share.copyText(Diag.reportText(report))
                        Toasts.toast(t("diag.copied"))
                    },
                    modifier = Modifier.weight(1f),
                )
                Btn(
                    label = t("diag.gotIt"),
                    variant = "secondary",
                    onPress = { Diag.dismiss(report.id) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DiagMeta(label: String, value: String) {
    Row(
        Modifier.padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, fontSize = F.small.sp, color = Color(0xFF9AA4AF), fontWeight = FontWeight.SemiBold)
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF242A31))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(value, fontSize = F.small.sp, color = Color(0xFFD7DDE3), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/* ---------------- Settings → Diagnostics list ---------------- */

@Composable
fun DiagListSheet() {
    val C = C()
    val reports = Diag.reports
    Sheet(title = t("diag.title"), onBack = { Diag.listOpen = false }) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (reports.isEmpty()) {
                Text(
                    t("diag.empty"),
                    fontSize = F.body.sp, color = C.sub,
                    modifier = Modifier.padding(vertical = 40.dp),
                )
            } else {
                reports.forEach { r ->
                    DiagListRow(r)
                    Spacer(Modifier.height(10.dp))
                }
                Btn(
                    label = t("diag.clearAll"),
                    variant = "danger",
                    onPress = { Diag.clearAll() },
                    modifier = Modifier.padding(top = 6.dp, bottom = 20.dp).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DiagListRow(r: DiagReport) {
    val C = C()
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(C.card)
            .clickable { open = !open },
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MdiIcon(
                if (r.kind == "crash") "alert-circle-outline" else "alert-outline",
                20, if (r.kind == "crash") C.red else Color(0xFF16A085),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    r.headline,
                    fontSize = F.small.sp,
                    color = C.ink,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (open) 6 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    t(if (r.kind == "crash") "diag.crashShort" else "diag.freezeShort") +
                        " · " + fdEpoch(r.id) + " " + ft(r.id) + " · " + r.phase,
                    fontSize = F.tiny.sp, color = C.sub,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            MdiIcon(if (open) "chevron-down" else "chevron-right", 18, C.faint)
        }
        if (open) {
            Box(Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp)) {
                Column {
                    Text(
                        r.stack.ifEmpty { "—" },
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = C.sub,
                        lineHeight = 14.sp,
                        maxLines = 30,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn(small = true, label = t("diag.copy"), onPress = {
                            Platform.share.copyText(Diag.reportText(r))
                            Toasts.toast(t("diag.copied"))
                        })
                        if (!r.seen) Btn(small = true, label = t("diag.gotIt"), variant = "secondary", onPress = { Diag.dismiss(r.id) })
                    }
                }
            }
        }
    }
}
