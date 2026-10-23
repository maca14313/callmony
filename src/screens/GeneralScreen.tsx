/* General tab — aggregated overview: GLOBAL income/expense/net hero (every
   owner — contacts AND general, plus open post-to-ledger lists), people with
   balances, and the full General ledger / lists / notes / reminders / accounts. */

import React, { useMemo, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useUI } from "../lib/ui-store";
import { useT } from "../i18n";
import { entrySignedRemaining, listTotals, r2 } from "../lib/calc";
import { displayName, money } from "../lib/utils";
import { F, useThemeColors } from "../theme";
import { Avatar, Card, Money, Segmented, SectionHeader } from "../components/kit";
import { LedgerSection } from "../sections/LedgerSection";
import { ListsSection } from "../sections/ListsSection";
import { NotesSection } from "../sections/NotesSection";
import { BankSection } from "../sections/BankSection";
import { RemindersSection } from "../sections/RemindersSection";

type Seg = "ledger" | "lists" | "notes" | "reminders" | "accounts";

export const GeneralScreen: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const open = useUI((s) => s.open);
  const entries = useApp((s) => s.entries);
  const lists = useApp((s) => s.lists);
  const contacts = useApp((s) => s.deviceContacts);
  const [seg, setSeg] = useState<Seg>("ledger");

  /* GLOBAL hero totals (user request: the hero must reflect the balance “as
     a general”, not only what was entered in the General tab) — every
     non-deleted entry regardless of ownerId, mirroring the people-map sign
     convention EXACTLY:
       income  = Σ credit entries' entrySignedRemaining        (credit is +)
       expense = Σ debit entries' −entrySignedRemaining         (debit is −)
       listsPending = Σ open postToLedger non-template lists' pending
       net = income − expense + listsPending
     so net ≡ (Σ of every person's balance) + (general balance), the same
     math the people map and ownerFullBalance use. */
  const global = useMemo(() => {
    let income = 0;
    let expense = 0;
    entries.forEach((e) => {
      if (e.deletedAt) return;
      const rem = entrySignedRemaining(e);
      if (e.type === "credit") income += rem;
      else expense += -rem;
    });
    let listsPending = 0;
    lists.forEach((l) => {
      if (l.deletedAt || !l.postToLedger || l.isTemplate) return;
      listsPending += listTotals(l).pending;
    });
    income = r2(income);
    expense = r2(expense);
    listsPending = r2(listsPending);
    return { income, expense, listsPending, net: r2(income - expense + listsPending) };
  }, [entries, lists]);
  const people = useMemo(() => {
    const map = new Map<string, number>();
    entries.forEach((e) => {
      if (e.deletedAt || !e.ownerId.startsWith("c:")) return;
      const id = e.ownerId.slice(2);
      map.set(id, Math.round(((map.get(id) ?? 0) + entrySignedRemaining(e)) * 100) / 100);
    });
    // open calculating-lists set to “post to ledger” count toward the balance
    lists.forEach((l) => {
      if (l.deletedAt || !l.ownerId.startsWith("c:") || !l.postToLedger) return;
      const id = l.ownerId.slice(2);
      const pending = listTotals(l).pending;
      map.set(id, Math.round(((map.get(id) ?? 0) + pending) * 100) / 100);
    });
    return contacts
      .map((c) => ({ c, balance: map.get(c.recordID) ?? 0 }))
      .filter((x) => Math.abs(x.balance) > 0.005)
      .sort((a, b) => Math.abs(b.balance) - Math.abs(a.balance));
  }, [entries, lists, contacts]);
  const owedToYou = people.filter((p) => p.balance > 0).reduce((s, p) => s + p.balance, 0);
  const youOwe = people.filter((p) => p.balance < 0).reduce((s, p) => s - p.balance, 0);

  return (
    <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 24 }} keyboardShouldPersistTaps="handled">
      {/* hero */}
      <Card style={{ marginBottom: 12 }}>
        <View style={{ flexDirection: "row", alignItems: "center", gap: 12 }}>
          <View style={[genStyles.heroIcon, { backgroundColor: C.brand }]}>
            <Icon name="wallet-outline" size={26} color={C.onBrand} />
          </View>
          <View style={{ flex: 1 }}>
            <Text style={{ fontSize: F.title, fontWeight: "800", color: C.ink }}>{t("hdr.general")}.</Text>
            <Text style={{ fontSize: F.small, color: C.sub }}>{t("gv.intro")}</Text>
            <Text style={{ fontSize: F.tiny, color: C.faint, marginTop: 2 }}>{t("gv.acrossEveryone")}</Text>
          </View>
        </View>
        <View style={{ flexDirection: "row", gap: 8, marginTop: 12 }}>
          <View style={[genStyles.stat, { borderColor: C.line }]}>
            <Text style={{ fontSize: F.tiny, color: C.sub, fontWeight: "600" }}>{t("lg.income")}</Text>
            <Money value={global.income} size={16} weight="800" fit />
          </View>
          <View style={[genStyles.stat, { borderColor: C.line }]}>
            <Text style={{ fontSize: F.tiny, color: C.sub, fontWeight: "600" }}>{t("lg.expense")}</Text>
            <Money value={-global.expense} size={16} weight="800" fit />
          </View>
          <View style={[genStyles.stat, { borderColor: C.brand, backgroundColor: C.brandSoft }]}>
            <Text style={{ fontSize: F.tiny, color: C.sub, fontWeight: "600" }}>{t("lg.net")}</Text>
            <Money value={global.net} colorize size={16} weight="800" fit />
          </View>
        </View>
        {global.listsPending > 0.005 && (
          <Text style={{ fontSize: F.tiny, color: C.amber, marginTop: 8 }}>
            {t("lg.openLists")}: {money(global.listsPending)}
          </Text>
        )}
      </Card>

      {/* people */}
      <SectionHeader title={t("gv.people")} subtitle={t("gv.peopleHint")} />
      <View style={{ flexDirection: "row", gap: 6, marginBottom: 8 }}>
        {owedToYou > 0.005 && (
          <View style={[genStyles.chipStat, { backgroundColor: "rgba(29,154,107,0.12)" }]}>
            <Text style={{ fontSize: F.tiny, color: C.green, fontWeight: "800" }}>
              {t("gv.owedToYou")} {money(owedToYou)}
            </Text>
          </View>
        )}
        {youOwe > 0.005 && (
          <View style={[genStyles.chipStat, { backgroundColor: "rgba(214,69,69,0.1)" }]}>
            <Text style={{ fontSize: F.tiny, color: C.red, fontWeight: "800" }}>
              {t("gv.youOwe")} {money(youOwe)}
            </Text>
          </View>
        )}
      </View>
      {people.length === 0 ? (
        <Text style={{ fontSize: F.small, color: C.faint, marginBottom: 14 }}>{t("gv.noPeopleHint")}</Text>
      ) : (
        <Card style={{ marginBottom: 14, padding: 8 }}>
          {people.slice(0, 30).map(({ c, balance }) => (
            <Pressable
              key={c.recordID}
              onPress={() => open({ type: "contact", recordID: c.recordID })}
              android_ripple={{ color: "rgba(128,128,128,0.1)" }}
              style={({ pressed }) => [genStyles.personRow, { backgroundColor: pressed ? C.chip : "transparent" }]}
            >
              <Avatar contact={c} size={34} />
              <Text style={{ flex: 1, fontSize: F.small, color: C.ink, fontWeight: "600" }} numberOfLines={1}>
                {displayName(c)}
              </Text>
              <Money value={balance} signed colorize size={14} />
            </Pressable>
          ))}
        </Card>
      )}

      {/* segmented sections — single-row side-scroll, compact */}
      <Segmented
        scroll
        value={seg}
        onChange={(k) => setSeg(k as Seg)}
        options={[
          { key: "ledger", label: t("tab.ledger") },
          { key: "lists", label: t("tab.lists") },
          { key: "notes", label: t("tab.notes") },
          { key: "reminders", label: t("tab.reminders") },
          { key: "accounts", label: t("tab.accounts") },
        ]}
      />
      <View style={{ marginTop: 12 }}>
        {seg === "ledger" && <LedgerSection ownerId="general" />}
        {seg === "lists" && <ListsSection ownerId="general" />}
        {seg === "notes" && <NotesSection ownerId="general" />}
        {seg === "reminders" && <RemindersSection ownerId="general" />}
        {seg === "accounts" && <BankSection ownerId="general" />}
      </View>
    </ScrollView>
  );
};

const genStyles = StyleSheet.create({
  heroIcon: { width: 50, height: 50, borderRadius: 16, alignItems: "center", justifyContent: "center" },
  stat: { flex: 1, minWidth: 0, borderRadius: 14, borderWidth: 1, paddingHorizontal: 10, paddingVertical: 9, gap: 2 },
  chipStat: { borderRadius: 10, paddingHorizontal: 10, paddingVertical: 6 },
  personRow: { flexDirection: "row", alignItems: "center", gap: 10, paddingHorizontal: 8, paddingVertical: 7, borderRadius: 12, minHeight: 50 },
});
