/* Trash — soft-deleted app entities, restore / delete forever / empty. */

import React, { useMemo, useState } from "react";
import { ScrollView, StyleSheet, Text, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useT } from "../i18n";
import { F, useThemeColors } from "../theme";
import { Btn, Card, EmptyState, IconBtn, Segmented, confirm, toast } from "../components/kit";
import { useOwnerName } from "../components/ContactPicker";

type TrashType = "all" | "entries" | "groups" | "lists" | "notes" | "reminders" | "banks";

interface TrashRow {
  id: string;
  type: Exclude<TrashType, "all">;
  icon: string;
  title: string;
  sub: string;
  ownerId: string;
  restore: () => void;
  purge: () => void;
}

export const TrashScreen: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const s = useApp();
  const [filter, setFilter] = useState<TrashType>("all");

  const rows: TrashRow[] = useMemo(() => {
    const out: TrashRow[] = [];
    s.entries.filter((e) => e.deletedAt).forEach((e) =>
      out.push({
        id: e.id,
        type: "entries",
        icon: "book-outline",
        title: `${e.kind || e.type} · ${e.amount}`,
        sub: `${t("misc.entry")} · ${e.date}`,
        ownerId: e.ownerId,
        restore: () => s.restoreEntry(e.id),
        purge: () => s.purgeEntry(e.id),
      })
    );
    s.groups.filter((g) => g.deletedAt).forEach((g) =>
      out.push({
        id: g.id,
        type: "groups",
        icon: "folder-outline",
        title: g.title,
        sub: t("lg.group"),
        ownerId: g.ownerId,
        restore: () => s.restoreGroup(g.id),
        purge: () => s.purgeGroup(g.id),
      })
    );
    s.lists.filter((l) => l.deletedAt).forEach((l) =>
      out.push({
        id: l.id,
        type: "lists",
        icon: "clipboard-list-outline",
        title: l.title,
        sub: t("misc.list"),
        ownerId: l.ownerId,
        restore: () => s.restoreList(l.id),
        purge: () => s.purgeList(l.id),
      })
    );
    s.notes.filter((n) => n.deletedAt).forEach((n) =>
      out.push({
        id: n.id,
        type: "notes",
        icon: "notebook-outline",
        title: n.title || t("nt.untitled"),
        sub: t("misc.note"),
        ownerId: n.ownerId,
        restore: () => s.restoreNote(n.id),
        purge: () => s.purgeNote(n.id),
      })
    );
    s.reminders.filter((r) => r.deletedAt).forEach((r) =>
      out.push({
        id: r.id,
        type: "reminders",
        icon: "bell-outline",
        title: r.title,
        sub: t("misc.reminder"),
        ownerId: r.ownerId,
        restore: () => s.restoreReminder(r.id),
        purge: () => s.purgeReminder(r.id),
      })
    );
    s.banks.filter((b) => b.deletedAt).forEach((b) =>
      out.push({
        id: b.id,
        type: "banks",
        icon: "bank-outline",
        title: b.bankName || b.label,
        sub: t("misc.bank"),
        ownerId: b.ownerId,
        restore: () => s.restoreBank(b.id),
        purge: () => s.purgeBank(b.id),
      })
    );
    return out.sort((a, b) => 0);
  }, [s, t]);

  const visible = filter === "all" ? rows : rows.filter((r) => r.type === filter);

  const counts = {
    entries: rows.filter((r) => r.type === "entries").length,
    groups: rows.filter((r) => r.type === "groups").length,
    lists: rows.filter((r) => r.type === "lists").length,
    notes: rows.filter((r) => r.type === "notes").length,
    reminders: rows.filter((r) => r.type === "reminders").length,
    banks: rows.filter((r) => r.type === "banks").length,
  };

  const restoreAll = () => {
    visible.forEach((r) => r.restore());
    toast(t("tr.restoredN", { n: visible.length }), "ok");
  };

  const empty = async () => {
    const ok = await confirm(t("tr.emptyTrashTitle"), t("tr.emptyTrashMsg"), t("tr.empty"), true);
    if (!ok) return;
    visible.forEach((r) => r.purge());
    toast(t("tr.trashEmptied"), "ok");
  };

  return (
    <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 24 }}>
      <View style={{ marginBottom: 12 }}>
        <Segmented
          value={filter}
          onChange={(k) => setFilter(k as TrashType)}
          options={[
            { key: "all", label: t("act.all"), count: rows.length },
            { key: "entries", label: t("misc.entry"), count: counts.entries },
            { key: "lists", label: t("misc.list"), count: counts.lists },
            { key: "notes", label: t("misc.note"), count: counts.notes },
            { key: "reminders", label: t("misc.reminder"), count: counts.reminders },
            { key: "banks", label: t("misc.bank"), count: counts.banks },
          ]}
        />
      </View>

      {rows.length === 0 ? (
        <EmptyState icon="trash-can-outline" title={t("tr.trashEmpty")} hint={t("tr.trashEmptyHint")} />
      ) : (
        <>
          <View style={{ flexDirection: "row", gap: 8, marginBottom: 12 }}>
            <Btn small label={t("tr.restoreAll")} icon="restore" variant="soft" onPress={restoreAll} />
            <Btn small label={t("tr.empty")} icon="trash-can-outline" variant="danger" onPress={empty} />
          </View>
          {visible.map((r) => (
            <TrashRowCard key={`${r.type}-${r.id}`} row={r} />
          ))}
        </>
      )}
    </ScrollView>
  );
};

const TrashRowCard: React.FC<{ row: TrashRow }> = ({ row }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const ownerName = useOwnerName(row.ownerId);
  return (
    <Card style={{ marginBottom: 8 }}>
      <View style={{ flexDirection: "row", alignItems: "center", gap: 10 }}>
        <View style={[trStyles.icon, { backgroundColor: C.chip }]}>
          <Icon name={row.icon} size={18} color={C.sub} />
        </View>
        <View style={{ flex: 1 }}>
          <Text style={{ fontSize: F.body, fontWeight: "700", color: C.ink }} numberOfLines={1}>
            {row.title}
          </Text>
          <Text style={{ fontSize: F.tiny, color: C.sub }} numberOfLines={1}>
            {row.sub} · {ownerName}
          </Text>
        </View>
        <IconBtn icon="restore" size={34} onPress={row.restore} />
        <IconBtn icon="trash-can-outline" size={34} danger onPress={row.purge} />
      </View>
    </Card>
  );
};

const trStyles = StyleSheet.create({
  icon: { width: 36, height: 36, borderRadius: 12, alignItems: "center", justifyContent: "center" },
});

