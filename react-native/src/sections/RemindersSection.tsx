/* RemindersSection — grouped by status (overdue / today / upcoming / done),
   kind chips, snooze actions, done toggle. Used by General, ContactDetail
   and the Reminders tab. */

import React, { useMemo } from "react";
import { Alert, Pressable, StyleSheet, Text, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useUI } from "../lib/ui-store";
import { useT } from "../i18n";
import { dueStatus } from "../lib/calc";
import { fd, ft } from "../lib/utils";
import type { Reminder, ReminderKind, OwnerId } from "../lib/types";
import { F, useThemeColors } from "../theme";
import { Btn, Card, EmptyState, IconBtn, Pill, confirm, toast, toastUndo } from "../components/kit";
import { SwipeRow } from "../components/SwipeRow";

export const RemindersSection: React.FC<{
  ownerId?: OwnerId;
  status?: "overdue" | "today" | "upcoming" | "done" | "all";
  kindFilter?: ReminderKind | "all";
  maxRows?: number;
  onSeeAll?: () => void;
}> = ({ ownerId, status = "all", kindFilter = "all", maxRows, onSeeAll }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const open = useUI((s) => s.open);
  const reminders = useApp((s) => s.reminders);
  const completeReminder = useApp((s) => s.completeReminder);
  const reopenReminder = useApp((s) => s.reopenReminder);
  const snoozeReminder = useApp((s) => s.snoozeReminder);
  const deleteReminder = useApp((s) => s.deleteReminder);

  const filtered = useMemo(() => {
    let list = reminders.filter((r) => !r.deletedAt);
    if (ownerId) list = list.filter((r) => r.ownerId === ownerId);
    if (status !== "all") {
      if (status === "done") list = list.filter((r) => r.done);
      else list = list.filter((r) => !r.done && dueStatus(r.snoozedUntil ?? r.dueAt) === status);
    }
    if (kindFilter !== "all") list = list.filter((r) => r.kind === kindFilter);
    list.sort((a, b) => (a.done ? (a.doneAt ?? 0) - (b.doneAt ?? 0) : (a.snoozedUntil ?? a.dueAt) - (b.snoozedUntil ?? b.dueAt)));
    return maxRows ? list.slice(0, maxRows) : list;
  }, [reminders, ownerId, status, kindFilter, maxRows]);

  const groups = useMemo(() => {
    const map = new Map<string, Reminder[]>();
    filtered.forEach((r) => {
      const key = r.done ? t("rm.done") : dueStatus(r.snoozedUntil ?? r.dueAt) === "overdue" ? t("rm.overdue") : dueStatus(r.snoozedUntil ?? r.dueAt) === "today" ? t("rm.today") : t("rm.upcoming");
      const arr = map.get(key) ?? [];
      arr.push(r);
      map.set(key, arr);
    });
    const order = [t("rm.overdue"), t("rm.today"), t("rm.upcoming"), t("rm.done")];
    return [...map.entries()].sort((a, b) => order.indexOf(a[0]) - order.indexOf(b[0]));
  }, [filtered, t]);

  if (filtered.length === 0) {
    return (
      <EmptyState
        icon="bell-off-outline"
        title={status === "all" ? t("rm.noReminders") : status === "done" ? t("rm.done") : status === "overdue" ? t("rm.nothingOverdue") : status === "today" ? t("rm.nothingToday") : t("rm.noReminders")}
        hint={ownerId === "general" ? t("rm.remindersGeneralHint") : t("rm.remindersHint")}
        actionLabel={t("rm.addReminder")}
        onAction={() => open({ type: "reminderEdit", ownerId: ownerId ?? "general" })}
      />
    );
  }

  return (
    <View>
      {groups.map(([label, list]) => (
        <View key={label} style={{ marginBottom: 8 }}>
          <Text style={[remStyles.groupLabel, { color: label === t("rm.overdue") ? C.red : label === t("rm.today") ? C.amber : C.sub }]}>
            {label} · {list.length}
          </Text>
          {list.map((r) => (
            <View key={r.id} style={{ marginBottom: 6 }}>
              <SwipeRow
                onTap={() => open({ type: "reminderEdit", reminderId: r.id, ownerId: r.ownerId })}
                leftActions={[
                  {
                    icon: r.done ? "restore" : "check",
                    color: r.done ? "#4a7d9b" : C.green,
                    onPress: () => (r.done ? reopenReminder(r.id) : completeReminder(r.id)),
                  },
                ]}
                rightActions={[
                  {
                    icon: "alarm-clock-plus",
                    color: "#b97d10",
                    onPress: () => {
                      snoozeReminder(r.id, Date.now() + 3600000);
                      toast(t("rm.snoozed", { label: "1h" }), "ok");
                    },
                  },
                  {
                    icon: "trash-can-outline",
                    color: C.red,
                    onPress: async () => {
                      const ok = await confirm(t("rm.deleteReminder"), t("rm.deleteMsg"), t("act.delete"), true);
                      if (ok) {
                        deleteReminder(r.id);
                        toastUndo();
                      }
                    },
                  },
                ]}
              >
                <Card>
                  <View style={{ flexDirection: "row", alignItems: "center", gap: 10 }}>
                    <Pressable
                      onPress={() => (r.done ? reopenReminder(r.id) : completeReminder(r.id))}
                      android_ripple={{ color: "rgba(128,128,128,0.2)", borderless: true, radius: 19 }}
                      style={[remStyles.doneBtn, { borderColor: r.done ? C.green : C.line, backgroundColor: r.done ? C.green : "transparent" }]}
                      accessibilityLabel={t("act.done")}
                    >
                      {r.done && <Icon name="check" size={17} color="#fff" />}
                    </Pressable>
                    <View style={{ flex: 1, gap: 2 }}>
                      <Text
                        style={{ fontSize: F.body, fontWeight: "700", color: C.ink, textDecorationLine: r.done ? "line-through" : "none" }}
                        numberOfLines={1}
                      >
                        {r.title || t("rm.kindCustom")}
                      </Text>
                      <View style={{ flexDirection: "row", alignItems: "center", gap: 5, flexWrap: "wrap" }}>
                        {!r.done && dueStatus(r.snoozedUntil ?? r.dueAt) === "overdue" && <Pill label={t("rm.overdue")} tone="red" />}
                        {!!r.snoozedUntil && !r.done && <Pill label={t("rm.snoozed")} tone="neutral" icon="clock-outline" />}
                        <Text style={{ fontSize: F.tiny, color: C.sub }}>
                          {fd(r.snoozedUntil ?? r.dueAt)} · {ft(r.snoozedUntil ?? r.dueAt)}
                        </Text>
                        {r.repeat !== "none" && <Icon name="repeat" size={13} color={C.sub} />}
                      </View>
                    </View>
                    <IconBtn
                      icon="dots-vertical"
                      size={32}
                      onPress={() =>
                        Alert.alert(r.title || t("rm.kindCustom"), undefined, [
                          {
                            text: r.done ? t("rm.reopen") : t("act.done"),
                            onPress: () => (r.done ? reopenReminder(r.id) : completeReminder(r.id)),
                          },
                          { text: t("rm.snooze10"), onPress: () => { snoozeReminder(r.id, Date.now() + 600000); toast(t("rm.snoozed", { label: "10m" }), "ok"); } },
                          { text: t("rm.snooze1h"), onPress: () => { snoozeReminder(r.id, Date.now() + 3600000); toast(t("rm.snoozed", { label: "1h" }), "ok"); } },
                          { text: t("rm.snooze3h"), onPress: () => { snoozeReminder(r.id, Date.now() + 3 * 3600000); toast(t("rm.snoozed", { label: "3h" }), "ok"); } },
                          {
                            text: t("rm.snoozeTomorrow"),
                            onPress: () => {
                              const t9 = new Date(Date.now() + 86400000);
                              t9.setHours(9, 0, 0, 0);
                              snoozeReminder(r.id, t9.getTime());
                              toast(t("rm.snoozed", { label: t("rm.snoozeTomorrow") }), "ok");
                            },
                          },
                          { text: t("act.edit"), onPress: () => open({ type: "reminderEdit", reminderId: r.id, ownerId: r.ownerId }) },
                          { text: t("act.cancel"), style: "cancel" },
                        ])
                      }
                    />
                  </View>
                </Card>
              </SwipeRow>
            </View>
          ))}
        </View>
      ))}
      {/* v1.6.9 — ALWAYS-reachable ＋ row (user report: "I want to add
          Reminders as many as I want, but I can't see any + icon once a
          Reminder exists"): with items on screen the empty-state button is
          gone, so a trailing dashed add row keeps the ＋ one tap away at
          the end of every reminder list (Reminders tab, General segment,
          contact detail). */}
      <Pressable
        onPress={() => open({ type: "reminderEdit", ownerId: ownerId ?? "general" })}
        android_ripple={{ color: "rgba(128,128,128,0.14)" }}
        style={[remStyles.addRow, { borderColor: C.line }]}
        accessibilityLabel={t("rm.addReminder")}
      >
        <Icon name="plus" size={16} color={C.brand} />
        <Text style={{ fontSize: F.small, color: C.brand, fontWeight: "700" }}>{t("rm.addReminder")}</Text>
      </Pressable>
      {maxRows && filtered.length >= maxRows && onSeeAll && (
        <Btn small label={t("act.showAll")} variant="soft" onPress={onSeeAll} />
      )}
    </View>
  );
};

const remStyles = StyleSheet.create({
  groupLabel: { fontSize: F.small, fontWeight: "800", marginBottom: 6 },
  doneBtn: { width: 32, height: 32, borderRadius: 16, borderWidth: 2, alignItems: "center", justifyContent: "center" },
  addRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    borderRadius: 14,
    borderWidth: 1,
    borderStyle: "dashed",
    paddingVertical: 10,
    marginTop: 2,
    marginBottom: 4,
    minHeight: 44,
  },
});

