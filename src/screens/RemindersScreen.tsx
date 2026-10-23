/* Reminders tab — status + kind filters + reminder health.
   v1.6.3 — "reminders still don't work" made MEASURABLE and fixable. The
   health card shows the THREE real-world failure points, each queried live
   from the OS and each fixable in one tap:
     1. Notifications permission  (denied once → runtime dialog can never
        re-show → the app's notification SETTINGS page is the only fix)
     2. Exact alarms (Android 12+ special access — notifee deep link)
     3. Battery-saver exemption (the #1 killer on Tecno/Infinix/Xiaomi/
        Samsung — our DiagModule walks the user to the exemption dialog)
   Plus a 20-second TEST ALERT that proves the whole chain end-to-end, and
   a live "armed" checkmark per reminder row (the OS scheduler's own answer
   to "is this really going to ring?"). */

import React, { useCallback, useEffect, useMemo, useState } from "react";
import { AppState, ScrollView, Text, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useUI } from "../lib/ui-store";
import { useT } from "../i18n";
import { dueStatus } from "../lib/calc";
import type { ReminderKind } from "../lib/types";
import {
  armedReminderIds,
  exactAlarmsAllowed,
  notificationsEnabled,
  openExactAlarmSettings,
  openNotificationSettingsDeep,
  requestNotificationPermission,
  sendTestAlert,
} from "../native/notify";
import { isBatteryExempt, requestBatteryExemption } from "../native/diag";
import { F, useThemeColors } from "../theme";
import { Btn, Card, Chip, Pill, Segmented, toast } from "../components/kit";
import { RemindersSection } from "../sections/RemindersSection";

type Status = "overdue" | "today" | "upcoming" | "done" | "all";

/* one row of the health card — icon + label + state + one-tap fixer */
const HealthRow: React.FC<{
  icon: string;
  iconColor: string;
  title: string;
  hint: string;
  ok: boolean | null;
  fixLabel?: string;
  onFix?: () => void;
}> = ({ icon, iconColor, title, hint, ok, fixLabel, onFix }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  return (
    <View style={{ flexDirection: "row", alignItems: "center", gap: 10, paddingVertical: 5 }}>
      <Icon name={icon} size={20} color={iconColor} />
      <View style={{ flex: 1, minWidth: 0 }}>
        <Text style={{ fontSize: F.small, fontWeight: "700", color: C.ink }} numberOfLines={1}>
          {title}
        </Text>
        <Text style={{ fontSize: F.tiny, color: C.sub, marginTop: 1 }} numberOfLines={2}>
          {hint}
        </Text>
      </View>
      {ok === null ? null : (
        <Pill label={ok ? "OK" : "!"} tone={ok ? "green" : "amber"} icon={ok ? "check-circle-outline" : "alert-outline"} />
      )}
      {!ok && fixLabel && onFix && <Btn small label={fixLabel} onPress={onFix} />}
    </View>
  );
};

export const RemindersScreen: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const open = useUI((s) => s.open);
  const reminders = useApp((s) => s.reminders);
  const updateSettings = useApp((s) => s.updateSettings);
  const [status, setStatus] = useState<Status>("overdue");
  const [kind, setKind] = useState<ReminderKind | "all">("all");

  /* live OS states — null while checking */
  const [notifOn, setNotifOn] = useState<boolean | null>(null);
  const [exactOn, setExactOn] = useState<boolean | null>(null);
  const [batteryOn, setBatteryOn] = useState<boolean | null>(null);
  const [armedCount, setArmedCount] = useState<number | null>(null);
  const [testing, setTesting] = useState(false);

  const refreshHealth = useCallback(async () => {
    const [n, e, b, armed] = await Promise.all([
      notificationsEnabled().catch(() => null),
      exactAlarmsAllowed().catch(() => null),
      isBatteryExempt().catch(() => null),
      armedReminderIds().catch(() => new Set<string>()),
    ]);
    setNotifOn(n);
    setExactOn(e);
    setBatteryOn(b);
    const due = reminders.filter((r) => !r.deletedAt && !r.done && Math.max(r.snoozedUntil ?? r.dueAt, 0) > Date.now());
    setArmedCount(due.filter((r) => armed.has(r.id)).length);
  }, [reminders]);

  useEffect(() => {
    refreshHealth();
    /* re-check when the user comes back from a settings page — the fixes
       happen outside the app, so the card must re-read the OS on return */
    const sub = AppState.addEventListener("change", (state) => {
      if (state === "active") refreshHealth();
    });
    return () => sub.remove();
  }, [refreshHealth]);

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
    let ok = await requestNotificationPermission();
    if (!ok) {
      /* denied once → the runtime dialog is gone forever; the settings
         page is the ONLY real fix (v1.6.3 root cause #1) */
      toast(t("rm.notifOpenSettings"), "info");
      ok = await openNotificationSettingsDeep();
    }
    updateSettings({ notifAsked: true });
    toast(ok ? t("rm.notifGranted") : t("rm.notifDeclined"), ok ? "ok" : "info");
    setTimeout(refreshHealth, 400);
  };

  const fixExact = async () => {
    const ok = await openExactAlarmSettings();
    if (!ok) toast(t("rm.exactUnsupported"), "info");
  };

  const fixBattery = async () => {
    const ok = await requestBatteryExemption();
    if (!ok) toast(t("rm.batteryManual"), "info");
  };

  const runTest = async () => {
    setTesting(true);
    const ok = await sendTestAlert();
    setTesting(false);
    toast(ok ? t("rm.testSent") : t("rm.testFail"), ok ? "ok" : "error");
    setTimeout(refreshHealth, 800);
  };

  const healthOk = notifOn === true && (exactOn !== false) && (batteryOn !== false);

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

      {/* ---------- v1.6.3 REMINDER HEALTH — the three real failure points,
          queried from the OS, each fixable in one tap ---------- */}
      <Card style={{ marginBottom: 12 }}>
        <View style={{ flexDirection: "row", alignItems: "center", gap: 8, marginBottom: 4 }}>
          <Icon name={healthOk ? "shield-check-outline" : "shield-alert-outline"} size={18} color={healthOk ? C.green : C.amber} />
          <Text style={{ fontSize: F.body, fontWeight: "800", color: C.ink, flex: 1 }}>{t("rm.healthTitle")}</Text>
          {armedCount != null && (
            <Text style={{ fontSize: F.tiny, color: armedCount > 0 ? C.green : C.sub, fontWeight: "700" }}>
              {t("rm.armedCount", { n: armedCount })}
            </Text>
          )}
        </View>

        <HealthRow
          icon={notifOn === true ? "bell-check-outline" : notifOn === false ? "bell-off-outline" : "bell-outline"}
          iconColor={notifOn === true ? C.green : C.amber}
          title={t("rm.healthNotif")}
          hint={notifOn === true ? t("rm.notifOnHint") : t("rm.notifOffHint")}
          ok={notifOn}
          fixLabel={t("rm.turnOn")}
          onFix={askNotifications}
        />
        <HealthRow
          icon={exactOn === false ? "alarm-off" : "alarm-check"}
          iconColor={exactOn === false ? C.amber : C.green}
          title={t("rm.healthExact")}
          hint={exactOn === false ? t("rm.exactOffHint") : t("rm.exactOnHint")}
          ok={exactOn === null ? null : exactOn}
          fixLabel={t("rm.fixExact")}
          onFix={fixExact}
        />
        <HealthRow
          icon={batteryOn === false ? "battery-alert-bluetooth" : "battery-charging-high"}
          iconColor={batteryOn === false ? C.amber : C.green}
          title={t("rm.healthBattery")}
          hint={batteryOn === false ? t("rm.batteryOffHint") : t("rm.batteryOnHint")}
          ok={batteryOn === null ? null : batteryOn}
          fixLabel={t("rm.fixBattery")}
          onFix={fixBattery}
        />

        <View style={{ flexDirection: "row", alignItems: "center", gap: 8, marginTop: 8, paddingTop: 8, borderTopWidth: 1, borderTopColor: C.line }}>
          <View style={{ flex: 1 }}>
            <Text style={{ fontSize: F.small, fontWeight: "700", color: C.ink }}>{t("rm.testAlert")}</Text>
            <Text style={{ fontSize: F.tiny, color: C.sub, marginTop: 1 }}>{t("rm.testHint")}</Text>
          </View>
          <Btn small label={testing ? "…" : t("rm.testGo")} icon="bell-ring-outline" variant="soft" onPress={runTest} disabled={testing} />
        </View>
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
