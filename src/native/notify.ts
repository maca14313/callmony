/* Local notifications — notifee (reminders while the app is closed).
   v1.6.3 — "reminders STILL don't work" final root causes addressed:
   1. NOTIFICATION PERMISSION: after ONE denial Android never re-shows the
      runtime dialog — requestPermission() just returns false forever. The
      only real fix is the notification SETTINGS page, so
      openNotificationSettingsDeep() now exists and every status card offers
      it. (v1.6.2 only called requestPermission — on denied devices the
      reminder saved silently and never rang.)
   2. EXACT ALARMS: `alarmManager: {allowWhileIdle: true}` needs
      SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM on Android 12+; the state is
      now VISIBLE (exactAlarmsAllowed()) and fixable in one tap
      (openAlarmPermissionSettings()).
   3. OEM BATTERY KILLERS (the big one on Tecno/Infinix/Xiaomi/Samsung):
      battery savers kill background alarms. isBatteryExempt() /
      requestBatteryExemption() (DiagModule) expose + fix it.
   4. VERIFICATION: after every schedule we CHECK the trigger really armed
      (getTriggerNotificationIds) — reminders list shows a live "armed"
      state, save toasts "Scheduled ✓", and a 20-second TEST ALERT proves
      the whole chain end-to-end. */

import { Platform } from "react-native";
import notifee, {
  AndroidCategory,
  AndroidImportance,
  AndroidVisibility,
  RepeatFrequency,
  TriggerType,
  type Event,
} from "@notifee/react-native";
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

/** v1.6.3 — the REAL fixer for a denied permission: opens this app's
    notification settings page (the runtime dialog can never be re-shown
    after the first denial). */
export const openNotificationSettingsDeep = async (): Promise<boolean> => {
  try {
    await notifee.openNotificationSettings();
    return true;
  } catch {
    return false;
  }
};

/** v1.6.3 — can the app schedule EXACT alarms (Android 12+ special access)? */
export const exactAlarmsAllowed = async (): Promise<boolean> => {
  try {
    const settings = await notifee.getNotificationSettings();
    // alarm: 1 = ENABLED, 0 = DISABLED, -1 = NOT_SUPPORTED (pre-12 → fine)
    const alarm = (settings as { alarm?: number }).alarm;
    return alarm === undefined ? true : alarm >= 0 ? alarm === 1 : true;
  } catch {
    return true; // assume fine — the schedule call itself will fall back
  }
};

/** v1.6.3 — one-tap deep link to the exact-alarms special access page. */
export const openExactAlarmSettings = async (): Promise<boolean> => {
  try {
    await notifee.openAlarmPermissionSettings();
    return true;
  } catch {
    return false;
  }
};

/** v1.6.3 — is this reminder's notification really armed in the OS right now? */
export const isReminderArmed = async (id: string): Promise<boolean> => {
  try {
    const ids = await notifee.getTriggerNotificationIds();
    return ids.includes(`reminder-${id}`);
  } catch {
    return false;
  }
};

/** v1.6.3 — the ids of every reminder currently armed in the OS scheduler. */
export const armedReminderIds = async (): Promise<Set<string>> => {
  try {
    const ids = await notifee.getTriggerNotificationIds();
    return new Set(ids.filter((x) => x.startsWith("reminder-")).map((x) => x.slice("reminder-".length)));
  } catch {
    return new Set();
  }
};

/** v1.6.3 — TEST ALERT: proves the entire chain (permission → channel →
    scheduler → heads-up notification) in ~20 seconds. Returns success so
    the UI can tell the user what to look for. */
export const sendTestAlert = async (): Promise<boolean> => {
  try {
    await ensureChannel();
    await notifee.createTriggerNotification(
      {
        id: "reminder-test",
        title: t("rm.testTitle"),
        subtitle: "callmony",
        body: t("rm.testBody"),
        android: {
          channelId: CHANNEL_ID,
          smallIcon: "ic_launcher",
          category: AndroidCategory.ALARM,
          visibility: AndroidVisibility.PUBLIC,
          pressAction: { id: "default" },
          autoCancel: true,
        },
        ios: { sound: "default" },
      },
      {
        type: TriggerType.TIMESTAMP,
        timestamp: Date.now() + 20000,
        alarmManager: { allowWhileIdle: true },
      }
    );
    return true;
  } catch {
    /* exact-alarm refused → inexact still proves the chain */
    try {
      await notifee.createTriggerNotification(
        {
          id: "reminder-test",
          title: t("rm.testTitle"),
          subtitle: "callmony",
          body: t("rm.testBody"),
          android: {
            channelId: CHANNEL_ID,
            smallIcon: "ic_launcher",
            category: AndroidCategory.ALARM,
            visibility: AndroidVisibility.PUBLIC,
            pressAction: { id: "default" },
            autoCancel: true,
          },
          ios: { sound: "default" },
        },
        { type: TriggerType.TIMESTAMP, timestamp: Date.now() + 20000 }
      );
      return true;
    } catch {
      return false;
    }
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

/** (Re)schedule the local notification for a reminder.
    v1.6.3 — returns true when the trigger is REALLY armed in the OS
    (verified via getTriggerNotificationIds), so callers can show the
    user a live "Scheduled ✓" instead of trusting a silent catch. */
export const scheduleReminderNotification = async (r: Reminder): Promise<boolean> => {
  try {
    await cancelReminderNotification(r.id);
    if (r.done || r.deletedAt) return false;
    const due = r.snoozedUntil ?? r.dueAt;
    if (due <= Date.now()) return false; // in-app alarm handles it
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
        /* v1.6.3 — ALARM category + public lockscreen visibility: the
           reminder behaves like an alarm (Doze-exempt, lock-screen safe)
           instead of a quiet message. */
        category: AndroidCategory.ALARM,
        visibility: AndroidVisibility.PUBLIC,
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
    /* VERIFY — don't trust the try/catch: the OS scheduler is the source
       of truth for "will this actually ring". */
    return await isReminderArmed(r.id);
  } catch {
    /* notifications unavailable — reminders still fire in-app */
    return false;
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

/* ---------------- v1.6.4 — reminder READINESS (self-healing) ----------------
   The REAL upgrade path behind "reminders still don't work": users coming
   from versions that never asked for the notification permission (v1.5.x)
   have reminders that are silently un-ringing. On every app open (throttled
   5 minutes):
     1. the alarm channel exists,
     2. the notification permission is asked ONCE — only when the user
        already has reminders and was never asked before (the dialog shows
        at most one time per install; after a denial Android never re-shows
        it and this becomes an instant no-op),
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
