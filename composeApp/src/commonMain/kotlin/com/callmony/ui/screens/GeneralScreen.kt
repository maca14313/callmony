package com.callmony.ui.screens

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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callmony.i18n.t
import com.callmony.lib.AppStore
import com.callmony.lib.Overlay
import com.callmony.lib.UiStore
import com.callmony.lib.compactMoney
import com.callmony.lib.displayName
import com.callmony.lib.entrySignedRemaining
import com.callmony.lib.listTotals
import com.callmony.lib.r2
import com.callmony.theme.F
import com.callmony.ui.kit.Avatar
import com.callmony.ui.kit.C
import com.callmony.ui.kit.Card
import com.callmony.ui.kit.MdiIcon
import com.callmony.ui.kit.Money
import com.callmony.ui.kit.SectionHeader
import com.callmony.ui.kit.Segmented
import com.callmony.ui.sections.BankSection
import com.callmony.ui.sections.LedgerSection
import com.callmony.ui.sections.ListsSection
import com.callmony.ui.sections.NotesSection
import com.callmony.ui.sections.RemindersSection

/* General tab — global income/expense/net hero, people with balances, and
   the five shared sections scoped to owner "general".
   Port of src/screens/GeneralScreen.tsx. */

@Composable
fun GeneralScreen() {
    val C = C()
    val entries = AppStore.state.entries
    val lists = AppStore.state.lists
    val contacts = AppStore.state.deviceContacts
    var seg by remember { mutableStateOf("ledger") }

    /* GLOBAL hero totals — every non-deleted entry regardless of owner */
    val global = remember(entries, lists) {
        var income = 0.0
        var expense = 0.0
        entries.forEach { e ->
            if (e.deletedAt != null) return@forEach
            val rem = entrySignedRemaining(e)
            if (e.type == "credit") income += rem else expense += -rem
        }
        var listsPending = 0.0
        lists.forEach { l ->
            if (l.deletedAt != null || !l.postToLedger || l.isTemplate) return@forEach
            listsPending += listTotals(l).pending
        }
        Triple(r2(income), r2(expense), r2(listsPending))
    }
    val net = r2(global.first - global.second + global.third)

    val people = remember(entries, lists, contacts) {
        val map = mutableMapOf<String, Double>()
        entries.forEach { e ->
            if (e.deletedAt != null || !e.ownerId.startsWith("c:")) return@forEach
            val id = e.ownerId.substring(2)
            map[id] = r2((map[id] ?: 0.0) + entrySignedRemaining(e))
        }
        lists.forEach { l ->
            if (l.deletedAt != null || !l.ownerId.startsWith("c:") || !l.postToLedger) return@forEach
            val id = l.ownerId.substring(2)
            map[id] = r2((map[id] ?: 0.0) + listTotals(l).pending)
        }
        contacts.map { it to (map[it.recordID] ?: 0.0) }
            .filter { kotlin.math.abs(it.second) > 0.005 }
            .sortedByDescending { kotlin.math.abs(it.second) }
    }
    val owedToYou = people.filter { it.second > 0 }.sumOf { it.second }
    val youOwe = people.filter { it.second < 0 }.sumOf { -it.second }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 28.dp),
    ) {
        // hero
        Card(Modifier.padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(C.brand),
                    contentAlignment = Alignment.Center,
                ) { MdiIcon("wallet-outline", 26, C.onBrand) }
                Column(Modifier.weight(1f)) {
                    Text("${t("hdr.general")}.", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = C.ink)
                    Text(t("gv.intro"), fontSize = F.small.sp, color = C.sub)
                    Text(t("gv.acrossEveryone"), fontSize = F.tiny.sp, color = C.faint, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, C.line, RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                ) {
                    Text(t("lg.income"), fontSize = F.tiny.sp, color = C.sub, fontWeight = FontWeight.SemiBold)
                    Money(global.first, size = 16, weight = FontWeight.ExtraBold, compact = true)
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, C.line, RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                ) {
                    Text(t("lg.expense"), fontSize = F.tiny.sp, color = C.sub, fontWeight = FontWeight.SemiBold)
                    Money(-global.second, size = 16, weight = FontWeight.ExtraBold, compact = true)
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(C.brandSoft)
                        .border(1.dp, C.brand, RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                ) {
                    Text(t("lg.net"), fontSize = F.tiny.sp, color = C.sub, fontWeight = FontWeight.SemiBold)
                    Money(net, colorize = true, size = 16, weight = FontWeight.ExtraBold, compact = true)
                }
            }
            if (global.third > 0.005) {
                Text(
                    "${t("lg.openLists")}: ${compactMoney(global.third)}",
                    fontSize = F.tiny.sp,
                    color = C.amber,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // people
        SectionHeader(t("gv.people"), subtitle = t("gv.peopleHint"))
        Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (owedToYou > 0.005) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color_green12())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text("${t("gv.owedToYou")} ${compactMoney(owedToYou)}", fontSize = F.tiny.sp, color = C.green, fontWeight = FontWeight.ExtraBold)
                }
            }
            if (youOwe > 0.005) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color_red10())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text("${t("gv.youOwe")} ${compactMoney(youOwe)}", fontSize = F.tiny.sp, color = C.red, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        if (people.isEmpty()) {
            Text(t("gv.noPeopleHint"), fontSize = F.small.sp, color = C.faint, modifier = Modifier.padding(bottom = 14.dp))
        } else {
            Card(Modifier.padding(bottom = 14.dp)) {
                people.take(30).forEach { (c, balance) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { UiStore.open(Overlay.Contact(c.recordID)) }
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Avatar(c, size = 34)
                        Text(
                            displayName(c),
                            fontSize = F.small.sp,
                            color = C.ink,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Money(balance, signed = true, colorize = true, size = 14, compact = true)
                    }
                }
            }
        }

        // segmented sections
        Segmented(
            listOf(
                Triple("ledger", t("tab.ledger"), null),
                Triple("lists", t("tab.lists"), null),
                Triple("notes", t("tab.notes"), null),
                Triple("reminders", t("tab.reminders"), null),
                Triple("accounts", t("tab.accounts"), null),
            ),
            seg,
        ) { seg = it }
        Spacer(Modifier.height(12.dp))
        when (seg) {
            "lists" -> ListsSection("general")
            "notes" -> NotesSection("general")
            "reminders" -> RemindersSection(ownerId = "general")
            "accounts" -> BankSection("general")
            else -> LedgerSection("general")
        }
    }
}

private fun Color_green12() = androidx.compose.ui.graphics.Color(0x1f1d9a6b)
private fun Color_red10() = androidx.compose.ui.graphics.Color(0x1ad64545)
