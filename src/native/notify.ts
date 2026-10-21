/* Local notifications — notifee (reminders while the app is closed). */

import { Platform } from "react-native";
import notifee, { AndroidImportance, TriggerType, RepeatFrequency } from "@notifee/react-native";
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

    const repeatable = r.repeat !== "none" && due > Date.now();
    await notifee.createTriggerNotification(
      {
        id: `reminder-${r.id}`,
        title: r.title || kindLabel(r),
        subtitle: kindLabel(r),
        body: r.notes || t("rm.due"),
        android: {
          channelId: CHANNEL_ID,
          smallIcon: "ic_launcher",
          pressAction: { id: "default" },
        },
        ios: {
          sound: "default",
        },
      },
      {
        type: TriggerType.TIMESTAMP,
        timestamp: due,
        repeatFrequency: repeatable
          ? r.repeat === "daily"
            ? RepeatFrequency.DAILY
            : r.repeat === "weekly"
              ? RepeatFrequency.WEEKLY
              : undefined
          : undefined,
        alarmManager: {
          allowWhileIdle: true,
        },
      }
    );
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
