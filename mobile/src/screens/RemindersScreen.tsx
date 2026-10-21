/* Reminders tab — status + kind filters, notification opt-in card. */

import React, { useMemo, useState } from "react";
import { ScrollView, StyleSheet, Text, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useUI } from "../lib/ui-store";
import { useT } from "../i18n";
import { dueStatus } from "../lib/calc";
import type { ReminderKind } from "../lib/types";
import { requestNotificationPermission } from "../native/notify";
import { F, useThemeColors } from "../theme";
import { Btn, Card, Chip, EmptyState, Segmented } from "../components/kit";
import { RemindersSection } from "../sections/RemindersSection";

type Status = "overdue" | "today" | "upcoming" | "done" | "all";

export const RemindersScreen: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const open = useUI((s) => s.open);
  const reminders = useApp((s) => s.reminders);
  const notifAsked = useApp((s) => s.settings.notifAsked);
  const updateSettings = useApp((s) => s.updateSettings);
  const [status, setStatus] = useState<Status>("overdue");
  const [kind, setKind] = useState<ReminderKind | "all">("all");

  const counts = useMemo(() => {
    const live = reminders.filter((r) => !r.deletedAt);
    return {
      overdue: live.filter((r) => !r.done && dueStatus(r.snoozedUntil ?? r.dueAt) === "overdue").length,
      today: live.filter((r) => !r.done && dueStatus(r.snoozedUntil ?? r.dueAt) === "today").length,
      upcoming: live.filter((r) => !r.done && dueStatus(r.snoozedUntil ?? r.dueAt) === "upcoming").length,
      done: live.filter((r) => r.done).length,
    };
  }, [reminders]);

  const askNotifications = async () => {
    const ok = await requestNotificationPermission();
    updateSettings({ notifAsked: true });
    // simple inline feedback via the chip row
  };

  return (
    <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 24 }} keyboardShouldPersistTaps="handled">
      <View style={{ flexDirection: "row", alignItems: "center", gap: 10, marginBottom: 12 }}>
        <View style={{ flex: 1 }}>
          <Text style={{ fontSize: F.h2, fontWeight: "800", color: C.ink }}>{t("tab.reminders")}</Text>
          <Text style={{ fontSize: F.small, color: C.sub }}>
            {t("rm.statsLine", { overdue: counts.overdue, today: counts.today, upcoming: counts.upcoming })}
          </Text>
        </View>
        <Btn small label={t("rm.newReminder")} icon="plus" onPress={() => open({ type: "reminderEdit", ownerId: "general" })} />
      </View>

      {!notifAsked && (
        <Card style={{ marginBottom: 12 }}>
          <View style={{ flexDirection: "row", alignItems: "center", gap: 10 }}>
            <Icon name="bell-ring-outline" size={22} color={C.brand} />
            <View style={{ flex: 1 }}>
              <Text style={{ fontSize: F.small, fontWeight: "700", color: C.ink }}>{t("rm.turnOnNotifications")}</Text>
              <Text style={{ fontSize: F.tiny, color: C.sub, marginTop: 2 }}>{t("rm.notifCardHint")}</Text>
            </View>
          </View>
          <View style={{ flexDirection: "row", gap: 8, marginTop: 10 }}>
            <Btn small label={t("rm.turnOn")} onPress={askNotifications} />
            <Btn small label={t("rm.notNow")} variant="secondary" onPress={() => updateSettings({ notifAsked: true })} />
          </View>
        </Card>
      )}

      <View style={{ marginBottom: 10 }}>
        <Segmented
          value={status}
          onChange={(k) => setStatus(k as Status)}
          options={[
            { key: "overdue", label: t("rm.overdue"), count: counts.overdue },
            { key: "today", label: t("rm.today"), count: counts.today },
            { key: "upcoming", label: t("rm.upcoming"), count: counts.upcoming },
            { key: "done", label: t("rm.done"), count: counts.done },
            { key: "all", label: t("act.all") },
          ]}
        />
      </View>

      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6, marginBottom: 12 }}>
        <Chip label={t("rm.allKinds")} active={kind === "all"} onPress={() => setKind("all")} />
        {(["payment", "todo", "birthday", "followup", "custom"] as ReminderKind[]).map((k) => (
          <Chip key={k} label={t(`rm.kind${k.charAt(0).toUpperCase()}${k.slice(1)}`)} active={kind === k} onPress={() => setKind(k)} />
        ))}
      </View>

      <RemindersSection status={status} kindFilter={kind} />
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  // reserved
});
