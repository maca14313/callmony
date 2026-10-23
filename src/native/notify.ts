/* Local notifications — notifee (reminders while the app is closed).
   v1.6.2 — "reminders don't remind" root causes fixed:
   1. EXACT ALARMS: `alarmManager: {allowWhileIdle: true}` needs the
      SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM permissions on Android 12+ —
      without them createTriggerNotification THROWS and the silent catch
      meant NO notification was ever scheduled. The permissions are now in
      the manifest, plus a WorkManager fallback if exact scheduling still
      fails (inexact beats silent).
   2. QUICK ACTIONS on the notification itself: "Done" and "Snooze 10m"
      work while the app is closed (background headless task in index.js)
      and while it is open (onForegroundEvent in App.tsx).
   3. The notification permission is requested when a reminder is SAVED —
      not only from the Reminders tab card. */

import { Platform } from "react-native";
import notifee, { AndroidImportance, RepeatFrequency, TriggerType, type Event } from "@notifee/react-native";
import type { Reminder } from "../lib/types";
import { nextOccurrence } from "../lib/calc";
import { t } from "../i18n";

const CHANNEL_ID = "reminders";
let channelReady = false;

export const ensureChannel = async (): Promise<void> => {
  if (channelReady || Platform.OS !== "android") return;
  try {
    await notifee.createChannel({
      id: CHANNEL_ID,
      name: "Reminders",
      importance: AndroidImportance.HIGH,
      sound: "default",
      vibration: true,
    });
    channelReady = true;
  } catch {
    /* noop */
  }
};

export const requestNotificationPermission = async (): Promise<boolean> => {
  try {
    const settings = await notifee.requestPermission();
    return settings.authorizationStatus >= 1; // AUTHORIZED / PROVISIONAL
  } catch {
    return false;
  }
};

/** Are notifications enabled right now? (drives the Reminders tab status chip) */
export const notificationsEnabled = async (): Promise<boolean> => {
  try {
    const settings = await notifee.getNotificationSettings();
    return settings.authorizationStatus >= 1; // AUTHORIZED / PROVISIONAL
  } catch {
    return false;
  }
};

const kindLabel = (r: Reminder): string => {
  switch (r.kind) {
    case "payment":
      return t("rm.kindPayment");
    case "todo":
      return t("rm.kindTodo");
    case "birthday":
      return t("rm.kindBirthday");
    case "followup":
      return t("rm.kindFollowup");
    default:
      return t("rm.kindCustom");
  }
};

/** (Re)schedule the local notification for a reminder. */
export const scheduleReminderNotification = async (r: Reminder): Promise<void> => {
  try {
    await cancelReminderNotification(r.id);
    if (r.done || r.deletedAt) return;
    const due = r.snoozedUntil ?? r.dueAt;
    if (due <= Date.now()) return; // in-app alarm handles it
    await ensureChannel();

    const repeatable = r.repeat === "daily" || r.repeat === "weekly";
    const notification = {
      id: `reminder-${r.id}`,
      title: r.title || kindLabel(r),
      subtitle: kindLabel(r),
      body: r.notes || t("rm.due"),
      android: {
        channelId: CHANNEL_ID,
        smallIcon: "ic_launcher",
        pressAction: { id: "default" },
        autoCancel: true,
        /* quick actions — v1.6.2 "advanced reminders": resolve the reminder
           straight from the notification, app open OR closed */
        actions: [
          { title: t("act.done"), pressAction: { id: "done" } },
          { title: t("rm.snooze10"), pressAction: { id: "snooze10" } },
        ],
      },
      ios: {
        sound: "default",
      },
    };
    const trigger = (exact: boolean) => ({
      type: TriggerType.TIMESTAMP as const,
      timestamp: due,
      repeatFrequency: repeatable
        ? r.repeat === "daily"
          ? RepeatFrequency.DAILY
          : RepeatFrequency.WEEKLY
        : undefined,
      ...(exact ? { alarmManager: { allowWhileIdle: true } } : {}),
    });

    /* exact AlarmManager first (fires on time even in Doze); if the device
       refuses (missing/revoked exact-alarm permission) fall back to
       WorkManager — a slightly late reminder beats no reminder at all. */
    try {
      await notifee.createTriggerNotification(
        notification,
        trigger(true) as Parameters<typeof notifee.createTriggerNotification>[1]
      );
    } catch {
      await notifee.createTriggerNotification(
        notification,
        trigger(false) as Parameters<typeof notifee.createTriggerNotification>[1]
      );
    }
  } catch {
    /* notifications unavailable — reminders still fire in-app */
  }
};

export const cancelReminderNotification = async (id: string): Promise<void> => {
  try {
    await notifee.cancelTriggerNotification(`reminder-${id}`);
    await notifee.cancelNotification(`reminder-${id}`);
  } catch {
    /* noop */
  }
};

export const cancelAllReminderNotifications = async (): Promise<void> => {
  try {
    await notifee.cancelAllNotifications();
  } catch {
    /* noop */
  }
};

/** Reschedule everything (e.g. after boot or restore). */
export const rescheduleAll = async (reminders: Reminder[]): Promise<void> => {
  for (const r of reminders) {
    if (!r.done && !r.deletedAt) {
      // repeating reminders whose time passed: advance to the next occurrence
      const eff = r.snoozedUntil ?? r.dueAt;
      if (r.repeat !== "none" && eff <= Date.now()) {
        const next = nextOccurrence(eff, r.repeat);
        await scheduleReminderNotification({ ...r, dueAt: next });
      } else {
        await scheduleReminderNotification(r);
      }
    }
  }
};

/* ---------------- v1.6.7 — reminder READINESS (ported from the v1.6.4
   self-heal system; the v1.6.2 revert had dropped it, and reminders went
   back to dying silently whenever the OS or an OEM battery saver killed
   them) ---------------- */

/** The REAL fixer for a denied notification permission: after the first
    denial Android never re-shows the runtime dialog — the app's
    notification settings page is the only path left. */
export const openNotificationSettingsDeep = async (): Promise<boolean> => {
  try {
    await notifee.openNotificationSettings();
    return true;
  } catch {
    return false;
  }
};

/** Can the app schedule EXACT alarms (Android 12+ special access)? */
export const exactAlarmsAllowed = async (): Promise<boolean> => {
  try {
    const settings = await notifee.getNotificationSettings();
    // alarm: 1 = ENABLED, 0 = DISABLED, -1 = NOT_SUPPORTED (pre-12 → fine)
    const alarm = (settings as { alarm?: number }).alarm;
    if (alarm === undefined) return true;
    return alarm === 1 || alarm < 0;
  } catch {
    return true; // assume fine — the schedule call itself will fall back
  }
};

/** The ids of every reminder currently armed in the OS scheduler. */
export const armedReminderIds = async (): Promise<Set<string>> => {
  try {
    const ids = await notifee.getTriggerNotificationIds();
    return new Set(ids.filter((x) => x.startsWith("reminder-")).map((x) => x.slice("reminder-".length)));
  } catch {
    return new Set();
  }
};

/** ensureReminderReadiness — runs at boot (+8 s) and on every foreground
    return, throttled to once per 5 minutes:
    1. the notification channel exists,
    2. the notification permission is asked ONCE — only when the user
       already has reminders and was never asked before (after a denial
       Android never re-shows the dialog and this is an instant no-op),
    3. every alarm the OS reports as NOT armed is re-armed — OEM battery
       savers (Tecno/Infinix/Xiaomi/Samsung) kill scheduled alarms in the
       field, and without this they used to stay dead forever. */
let readinessAt = 0;

export const ensureReminderReadiness = async (
  reminders: Reminder[],
  opts: { autoAskPermission?: boolean } = {}
): Promise<{ asked: boolean; granted: boolean; rearmed: number }> => {
  if (Platform.OS !== "android") return { asked: false, granted: false, rearmed: 0 };
  const now = Date.now();
  if (now - readinessAt < 5 * 60000) return { asked: false, granted: false, rearmed: 0 };
  readinessAt = now;
  await ensureChannel().catch(() => undefined);
  const pending = reminders.filter((r) => !r.done && !r.deletedAt);
  if (!pending.length) return { asked: false, granted: false, rearmed: 0 };
  let asked = false;
  let granted = false;
  if (opts.autoAskPermission && !(await notificationsEnabled())) {
    asked = true; // the dialog was shown (or is already denied — instant no-op)
    granted = await requestNotificationPermission();
  }
  /* SELF-HEAL — re-arm anything the OS is not holding right now */
  const armed = await armedReminderIds();
  const missing = pending.filter((r) => !armed.has(r.id));
  if (missing.length) await rescheduleAll(missing);
  return { asked, granted, rearmed: missing.length };
};

/* ---------------- notification event handling (Done / Snooze 10m) ----------------
   Runs from App.tsx (foreground) AND the headless background task in
   index.js (app closed). The store is required lazily so the
   store → notify → store import cycle never fires at module-eval time. */

interface StoreApi {
  hydrate: () => Promise<void>;
  completeReminder: (id: string) => void;
  snoozeReminder: (id: string, until: number) => void;
  reminders: Reminder[];
}

const getStore = (): StoreApi | null => {
  try {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const mod = require("../lib/store");
    return (mod as { useApp: { getState: () => StoreApi } }).useApp.getState();
  } catch {
    return null;
  }
};

/** Handle a notifee press/action event (shared by foreground + background). */
export const handleNotifeeEvent = async (event: Event): Promise<void> => {
  try {
    const type = (event as { type?: number }).type ?? -1;
    const detail = (event as { detail?: { pressAction?: { id?: string }; notification?: { id?: string } } }).detail;
    const actionId = detail?.pressAction?.id;
    const notifId = detail?.notification?.id ?? "";
    const reminderId = notifId.replace(/^reminder-/, "");

    if (!reminderId || !actionId) return;

    const store = getStore();
    if (!store) return;
    /* background/headless: make sure the persisted data is loaded */
    if (!store.reminders?.length) {
      try {
        await store.hydrate();
      } catch {
        /* noop */
      }
    }

    if (actionId === "done") {
      store.completeReminder(reminderId);
      await cancelReminderNotification(reminderId);
    } else if (actionId === "snooze10") {
      store.snoozeReminder(reminderId, Date.now() + 10 * 60000);
      await cancelReminderNotification(reminderId);
    }
    /* 'default' (tap) → the app opens; the in-app alarm dialog takes over. */
  } catch {
    /* never crash the headless task */
  }
};
