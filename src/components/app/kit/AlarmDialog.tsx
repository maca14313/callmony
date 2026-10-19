"use client";

import { useEffect, useRef } from "react";
import { AlarmClock, BellRing, Check, Clock3, ExternalLink } from "lucide-react";
import { motion } from "framer-motion";
import type { Reminder } from "@/lib/types";
import { useApp } from "@/lib/store";
import { useUI } from "@/lib/ui-store";
import { Button } from "./primitives";
import { useOpenLink } from "./LinkPicker";

/* In-app alarm: fires while the app is open; repeats reschedule automatically.
   Also fires a browser Notification when permitted. */

let audioCtx: AudioContext | null = null;

function beep(times = 2) {
  try {
    audioCtx = audioCtx || new (window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext)();
    const ctx = audioCtx;
    if (ctx.state === "suspended") void ctx.resume();
    for (let i = 0; i < times; i++) {
      const o = ctx.createOscillator();
      const g = ctx.createGain();
      o.connect(g);
      g.connect(ctx.destination);
      o.type = "sine";
      o.frequency.value = 830;
      const t0 = ctx.currentTime + i * 0.55;
      g.gain.setValueAtTime(0.0001, t0);
      g.gain.exponentialRampToValueAtTime(0.18, t0 + 0.05);
      g.gain.exponentialRampToValueAtTime(0.0001, t0 + 0.42);
      o.start(t0);
      o.stop(t0 + 0.45);
    }
  } catch {
    /* audio not available */
  }
}

export function notify(title: string, body: string) {
  try {
    if (typeof Notification !== "undefined" && Notification.permission === "granted") {
      new Notification(title, { body, icon: "/icons/icon-192.png", tag: "callmony-reminder" });
    }
  } catch {
    /* noop */
  }
}

export function AlarmDialog({ reminder }: { reminder: Reminder }) {
  const app = useApp();
  const ui = useUI();
  const openLink = useOpenLink();
  const firedRef = useRef(false);

  const contactName =
    reminder.ownerId !== "general"
      ? displayNameSafe(app.contacts.find((c) => c.id === reminder.ownerId))
      : null;

  useEffect(() => {
    if (firedRef.current) return;
    firedRef.current = true;
    beep(3);
    vibrate_([180, 120, 180, 120, 180]);
    notify(reminder.title, reminder.notes || "Reminder from callmony.");
  }, [reminder]);

  const link = reminder.links?.[0];

  const dismiss = (action: "done" | "fire" | "snooze", snoozeMs?: number) => {
    if (action === "done") app.completeReminder(reminder.id);
    if (action === "fire") app.fireReminder(reminder.id);
    if (action === "snooze" && snoozeMs) app.snoozeReminder(reminder.id, Date.now() + snoozeMs);
    ui.shiftAlarm();
  };

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="absolute inset-0 flex items-center justify-center p-6"
      style={{ zIndex: 96, background: "var(--app-scrim)" }}
      role="alertdialog"
      aria-modal="true"
      aria-label={`Alarm: ${reminder.title}`}
    >
      <motion.div
        initial={{ scale: 0.9, y: 16 }}
        animate={{ scale: 1, y: 0 }}
        transition={{ type: "spring", damping: 22, stiffness: 300 }}
        className="w-full max-w-[330px] rounded-[28px] border border-line bg-surface p-5 shadow-soft"
      >
        <div className="flex flex-col items-center text-center">
          <div className="pulse-ring flex h-16 w-16 items-center justify-center rounded-[24px] bg-brand text-white shadow-floating">
            <BellRing size={28} />
          </div>
          <p className="mt-4 text-[10px] font-bold uppercase tracking-[2.4px] text-brand">
            {reminder.kind === "birthday" ? "Birthday" : reminder.kind === "payment" ? "Payment due" : "Reminder"}
          </p>
          <h3 className="mt-1.5 text-[17px] font-bold leading-snug text-ink">{reminder.title}</h3>
          {contactName && <p className="mt-1 text-xs font-semibold text-mute">{contactName}</p>}
          {reminder.notes && <p className="mt-2 text-xs leading-5 text-mute">{reminder.notes}</p>}
          <p className="mt-3 flex items-center gap-1.5 text-[11px] text-mute">
            <AlarmClock size={12} /> {new Date(reminder.dueAt).toLocaleString()}
            {reminder.repeat !== "none" && <span className="text-brand">· repeats {reminder.repeat}</span>}
          </p>
        </div>

        <div className="mt-5 space-y-2">
          <Button variant="brand" size="lg" className="w-full" onClick={() => dismiss("done")}>
            <Check size={16} /> Mark done
          </Button>
          {link && (
            <Button
              variant="soft"
              size="lg"
              className="w-full"
              onClick={() => {
                openLink(link);
                ui.shiftAlarm();
              }}
            >
              <ExternalLink size={15} /> Open {link.type}
            </Button>
          )}
          <div className="grid grid-cols-2 gap-2">
            <Button variant="surface" size="md" onClick={() => dismiss("snooze", 10 * 60000)}>
              <Clock3 size={14} /> 10 min
            </Button>
            <Button variant="surface" size="md" onClick={() => dismiss("snooze", 60 * 60000)}>
              <Clock3 size={14} /> 1 hour
            </Button>
          </div>
          {reminder.repeat === "none" ? (
            <button
              className="w-full py-1.5 text-center text-[11px] font-semibold text-mute hover:text-ink"
              onClick={() => dismiss("fire")}
            >
              Dismiss
            </button>
          ) : (
            <button
              className="w-full py-1.5 text-center text-[11px] font-semibold text-mute hover:text-ink"
              onClick={() => dismiss("fire")}
            >
              Snooze until next {reminder.repeat === "daily" ? "day" : reminder.repeat.replace("ly", "")}
            </button>
          )}
        </div>
      </motion.div>
    </motion.div>
  );
}

function displayNameSafe(c?: { firstName: string; lastName: string; nickname?: string } | null): string | null {
  if (!c) return null;
  const n = `${c.firstName || ""} ${c.lastName || ""}`.trim();
  return n || c.nickname || null;
}

function vibrate_(pattern: number[]) {
  try {
    navigator.vibrate?.(pattern);
  } catch {
    /* noop */
  }
}
