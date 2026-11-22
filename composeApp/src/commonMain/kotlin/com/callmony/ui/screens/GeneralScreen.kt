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
import androidx.compose.foundation.layout.widthIn
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
import com.callmony.ui.kit.HeroNumeral
import com.callmony.ui.kit.MdiIcon
import com.callmony.ui.kit.Money
import com.callmony.ui.kit.Pill
import com.callmony.ui.kit.SectionHeader
import com.callmony.ui.kit.Segmented
import com.callmony.ui.components.brandGradient
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
    // v1.6.34 — perf: the two headline sums are remembered on `people`
    // (they rescanned the pair list on every recomposition of this screen)
    val owedToYou = remember(people) { people.filter { it.second > 0 }.sumOf { it.second } }
    val youOwe = remember(people) { people.filter { it.second < 0 }.sumOf { -it.second } }

    Column(
        Modifier
            .fillMaxWidth()
            // v1.6.53 — LIGHT GLASS (spec 5): max content width 480dp,
            // centered on wide windows (landscape / desktop).
            .widthIn(max = 480.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // hero — v1.6.52 LIGHT GLASS: the NET becomes a hero numeral
        // (40sp Lexend Light in Brand — the spec's large light-weight
        // numerals); income/expense/lists sit in soft-tinted pills below.
        Card(Modifier.padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(50.dp)
                        .brandGradient(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) { MdiIcon("wallet-outline", 26, C.onBrand) }
                Column(Modifier.weight(1f)) {
                    Text("${t("hdr.general")}.", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
                    Text(t("gv.intro"), fontSize = F.small.sp, color = C.sub)
                    Text(t("gv.acrossEveryone"), fontSize = F.tiny.sp, color = C.faint, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Row(
                Modifier.padding(top = 14.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    t("lg.net"),
                    fontSize = 13.sp,
                    color = C.sub,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 9.dp),
                )
                // v1.6.53 — the NET is a true HERO NUMERAL (spec 6: 48-64sp
                // ExtraLight/Light in Brand, auto-shrink to one line, never
                // overflow or clip) — HeroNumeral measures and steps down
                // the ladder until the money string fits one line. Negative
                // nets go Danger red (the signed convention; red is a
                // status, not a second brand).
                HeroNumeral(
                    compactMoney(net, signed = false),
                    negative = net < 0,
                    modifier = Modifier.weight(1f),
                    tag = "heroNumeral:generalNet",
                )
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(C.brandSoft.copy(alpha = 0.55f))
                        .border(1.dp, C.line, RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                ) {
                    Text(t("lg.income"), fontSize = F.tiny.sp, color = C.sub, fontWeight = FontWeight.Medium)
                    Money(global.first, size = 16, weight = FontWeight.Medium, compact = true)
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(C.brandSoft.copy(alpha = 0.55f))
                        .border(1.dp, C.line, RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                ) {
                    Text(t("lg.expense"), fontSize = F.tiny.sp, color = C.sub, fontWeight = FontWeight.Medium)
                    Money(-global.second, size = 16, weight = FontWeight.Medium, compact = true)
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(C.brandSoft.copy(alpha = 0.55f))
                        .border(1.dp, C.line, RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                ) {
                    Text(t("lg.openLists"), fontSize = F.tiny.sp, color = C.sub, fontWeight = FontWeight.Medium)
                    Money(global.third, size = 16, weight = FontWeight.Medium, compact = true)
                }
            }
        }

        // people
        SectionHeader(t("gv.people"), subtitle = t("gv.peopleHint"))
        Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (owedToYou > 0.005) {
                // v1.6.53 — status pills through the kit (tinted bg +
                // matching text + sign, never color alone); the raw hex
                // boxes are gone.
                Pill("${t("gv.owedToYou")} ${compactMoney(owedToYou)}", tone = "green", icon = "arrow-down-left")
            }
            if (youOwe > 0.005) {
                Pill("${t("gv.youOwe")} ${compactMoney(youOwe)}", tone = "red", icon = "arrow-up-right")
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
