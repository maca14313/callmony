/* Reminders tab — status + kind filters, notification status card.
   v1.6.2 — "reminders don't remind" made visible & fixable: the tab shows a
   live NOTIFICATIONS ON/OFF card (queries the real OS state, not a flag),
   with a one-tap enable and an Open-settings fallback when the OS dialog
   was permanently denied. */

import React, { useMemo, useState } from "react";
import { Linking, ScrollView, Text, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useUI } from "../lib/ui-store";
import { useT } from "../i18n";
import { dueStatus } from "../lib/calc";
import type { ReminderKind } from "../lib/types";
import { notificationsEnabled, openNotificationSettingsDeep, requestNotificationPermission } from "../native/notify";
import { F, useThemeColors } from "../theme";
import { Btn, Card, Chip, Pill, Segmented, toast } from "../components/kit";
import { RemindersSection } from "../sections/RemindersSection";

type Status = "overdue" | "today" | "upcoming" | "done" | "all";

export const RemindersScreen: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const open = useUI((s) => s.open);
  const reminders = useApp((s) => s.reminders);
  const updateSettings = useApp((s) => s.updateSettings);
  const [status, setStatus] = useState<Status>("overdue");
  const [kind, setKind] = useState<ReminderKind | "all">("all");
  /* live OS notification state — null while checking */
  const [notifOn, setNotifOn] = useState<boolean | null>(null);

  React.useEffect(() => {
    notificationsEnabled().then(setNotifOn).catch(() => setNotifOn(null));
  }, []);

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
    setNotifOn(ok);
    updateSettings({ notifAsked: true });
    toast(ok ? t("rm.notifGranted") : t("rm.notifDeclined"), ok ? "ok" : "info");
    /* v1.6.7 — after the FIRST denial Android never re-shows the runtime
       dialog; the one place the user can still flip notifications on is
       the app's notification settings page. Route them straight there
       instead of leaving a dead "Turn on" button. */
    if (!ok) {
      const deep = await openNotificationSettingsDeep();
      if (!deep) openSettings();
    }
  };

  const openSettings = async () => {
    try {
      await Linking.openSettings();
    } catch {
      /* noop */
    }
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

      {/* v1.6.2 — always-visible notification status: green = reminders ring
          while the app is closed; amber = fix in one tap */}
      <Card style={{ marginBottom: 12 }}>
        <View style={{ flexDirection: "row", alignItems: "center", gap: 10 }}>
          <Icon
            name={notifOn === true ? "bell-check-outline" : notifOn === false ? "bell-off-outline" : "bell-outline"}
            size={22}
            color={notifOn === true ? C.green : C.amber}
          />
          <View style={{ flex: 1 }}>
            <View style={{ flexDirection: "row", alignItems: "center", gap: 6 }}>
              <Text style={{ fontSize: F.small, fontWeight: "700", color: C.ink }}>
                {notifOn === true ? t("rm.notifOnTitle") : t("rm.turnOnNotifications")}
              </Text>
              <Pill
                label={notifOn === true ? "ON" : "OFF"}
                tone={notifOn === true ? "green" : "amber"}
                icon={notifOn === true ? "check-circle-outline" : "alert-outline"}
              />
            </View>
            <Text style={{ fontSize: F.tiny, color: C.sub, marginTop: 2 }}>
              {notifOn === true ? t("rm.notifOnHint") : t("rm.notifCardHint")}
            </Text>
          </View>
        </View>
        {notifOn !== true && (
          <View style={{ flexDirection: "row", gap: 8, marginTop: 10 }}>
            <Btn small label={t("rm.turnOn")} onPress={askNotifications} />
            <Btn small label={t("perm.openSettings")} variant="secondary" onPress={openSettings} />
          </View>
        )}
      </Card>

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
