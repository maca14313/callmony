"use client";

/* =========================================================================
   Task 2-c — RemindersSection (+ shared ReminderRow / ReminderActions
   reused by views/tools-views RemindersView).
   Compact Overdue/Today/Upcoming groups for an owner, done toggle, snooze
   quick actions, birthday-reminder one-tap, "See all" jump to Reminders tab.
   ========================================================================= */

import { useMemo, useState } from "react";
import { AlarmClock, Bell, Check, ChevronRight, Clock3, Pencil, Plus, Repeat, RotateCcw, Trash2 } from "lucide-react";
import { useApp } from "@/lib/store";
import { useUI } from "@/lib/ui-store";
import { dueStatus } from "@/lib/calc";
import { displayName, fdShort, ft } from "@/lib/utils";
import type { OwnerId, Reminder } from "@/lib/types";
import { BottomSheet } from "../kit/Sheet";
import { LinkChips, useOpenLink } from "../kit/LinkPicker";
import { SwipeRow } from "../kit/SwipeRow";
import { useConfirm } from "../kit/Confirm";
import { toastOk, toastUndoLast } from "../kit/toast";
import { EmptyState, SectionLabel, cx } from "../kit/primitives";

/* ------------------------------------------------------------------ */
/* shared helpers                                                       */
/* ------------------------------------------------------------------ */

const p2 = (n: number) => String(n).padStart(2, "0");

/** "09:05" from an epoch — used by ReminderEdit defaults. */
export const hhmm = (epoch: number): string => {
  const d = new Date(epoch);
  return `${p2(d.getHours())}:${p2(d.getMinutes())}`;
};

/** "Mar 5 · 9:00 AM" */
export const dueLabel = (r: Reminder): string => `${fdShort(r.dueAt)} · ${ft(r.dueAt)}`;

/** overdue | today | upcoming | done */
export const reminderStatus = (r: Reminder): "overdue" | "today" | "upcoming" | "done" =>
  r.done ? "done" : dueStatus(r.dueAt);

/** Next occurrence of a birthday (YYYY-MM-DD) at 9:00. */
export const nextBirthday9 = (iso: string): number => {
  const parts = iso.split("-").map((x) => parseInt(x, 10));
  const m = (parts[1] || 1) - 1;
  const d = parts[2] || 1;
  const now = new Date();
  let b = new Date(now.getFullYear(), m, d, 9, 0, 0, 0);
  if (b.getTime() <= Date.now()) b = new Date(now.getFullYear() + 1, m, d, 9, 0, 0, 0);
  return b.getTime();
};

/* ------------------------------------------------------------------ */
/* shared row (used here + RemindersView)                               */
/* ------------------------------------------------------------------ */

export function ReminderRow({ reminder: r, compact = false }: { reminder: Reminder; compact?: boolean }) {
  const ui = useUI();
  const openLink = useOpenLink();
  const completeReminder = useApp((s) => s.completeReminder);
  const reopenReminder = useApp((s) => s.reopenReminder);
  const [sheet, setSheet] = useState(false);

  const status = reminderStatus(r);
  const overdue = status === "overdue";
  const snoozed = !!r.snoozedUntil && r.snoozedUntil > Date.now();

  return (
    <>
      <SwipeRow
        onTap={() => ui.open({ type: "reminderEdit", reminderId: r.id, ownerId: r.ownerId })}
        onLongPress={() => setSheet(true)}
        className="mb-2"
      >
        <div
          className={cx(
            "flex items-center gap-1 rounded-2xl p-1.5 pr-3",
            overdue && !r.done ? "bg-negsoft" : "bg-surface",
            r.done && "opacity-60"
          )}
        >
          {/* round done toggle — 44px target */}
          <button
            type="button"
            role="checkbox"
            aria-checked={r.done}
            aria-label={r.done ? `Reopen “${r.title}”` : `Mark “${r.title}” done`}
            onPointerDown={(e) => e.stopPropagation()}
            onClick={(e) => {
              e.stopPropagation();
              if (r.done) reopenReminder(r.id);
              else completeReminder(r.id);
            }}
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full transition active:scale-90"
          >
            <span
              className={cx(
                "flex h-[25px] w-[25px] items-center justify-center rounded-full border-2 transition",
                r.done ? "border-brand bg-brand text-white" : "border-line2 text-transparent"
              )}
            >
              <Check size={13} strokeWidth={3.5} />
            </span>
          </button>

          <div className="min-w-0 flex-1">
            <p
              className={cx(
                "truncate text-[13.5px] font-semibold leading-tight",
                r.done ? "text-mute line-through" : "text-ink"
              )}
            >
              {r.title}
            </p>
            <div className="mt-1 flex items-center gap-1.5 text-[11px] text-mute">
              {overdue && !r.done && (
                <span className="shrink-0 rounded-full bg-neg/90 px-1.5 py-[1px] text-[9px] font-bold uppercase tracking-wide text-white">
                  Overdue
                </span>
              )}
              {snoozed && (
                <span className="flex shrink-0 items-center gap-0.5 font-semibold text-amber">
                  <Clock3 size={10} /> snoozed
                </span>
              )}
              <span className="truncate">{dueLabel(r)}</span>
              {r.repeat !== "none" && (
                <Repeat size={11} className="shrink-0 text-brand" aria-label={`Repeats ${r.repeat}`} />
              )}
            </div>
            {!compact && r.links.length > 0 && (
              <div className="mt-1.5">
                <LinkChips links={r.links} onOpen={openLink} />
              </div>
            )}
          </div>
        </div>
      </SwipeRow>

      <ReminderActions reminder={r} open={sheet} onClose={() => setSheet(false)} />
    </>
  );
}

/* ------------------------------------------------------------------ */
/* long-press action sheet (shared)                                     */
/* ------------------------------------------------------------------ */

export function ReminderActions({
  reminder,
  open,
  onClose,
}: {
  reminder: Reminder;
  open: boolean;
  onClose: () => void;
}) {
  const confirm = useConfirm();
  const ui = useUI();
  const completeReminder = useApp((s) => s.completeReminder);
  const reopenReminder = useApp((s) => s.reopenReminder);
  const snoozeReminder = useApp((s) => s.snoozeReminder);
  const deleteReminder = useApp((s) => s.deleteReminder);

  const tomorrow9 = () => {
    const t = new Date();
    t.setDate(t.getDate() + 1);
    t.setHours(9, 0, 0, 0);
    return t.getTime();
  };

  const snooze = (until: number, label: string) => {
    snoozeReminder(reminder.id, until);
    toastOk(`Snoozed · ${label}`);
    onClose();
  };

  const remove = async () => {
    const ok = await confirm({
      title: "Delete reminder?",
      message: `“${reminder.title}” will be moved to Trash — you can undo right after.`,
      confirmLabel: "Delete",
      tone: "danger",
    });
    if (!ok) return;
    deleteReminder(reminder.id);
    toastUndoLast();
    onClose();
  };

  return (
    <BottomSheet open={open} onClose={onClose} title={reminder.title}>
      <div className="space-y-1.5 pb-2">
        <ActionRow
          icon={reminder.done ? <RotateCcw size={16} /> : <Check size={16} />}
          label={reminder.done ? "Reopen reminder" : "Mark done"}
          onClick={() => {
            if (reminder.done) reopenReminder(reminder.id);
            else completeReminder(reminder.id);
            toastOk(reminder.done ? "Reminder reopened" : "Marked done");
            onClose();
          }}
        />
        {!reminder.done && (
          <>
            <ActionRow
              icon={<Clock3 size={16} />}
              label="Snooze 10 minutes"
              onClick={() => snooze(Date.now() + 10 * 60000, "10 minutes")}
            />
            <ActionRow
              icon={<Clock3 size={16} />}
              label="Snooze 1 hour"
              onClick={() => snooze(Date.now() + 60 * 60000, "1 hour")}
            />
            <ActionRow
              icon={<AlarmClock size={16} />}
              label={`Snooze until tomorrow · ${fdShort(tomorrow9())} 9:00`}
              onClick={() => snooze(tomorrow9(), `until ${fdShort(tomorrow9())} · 9:00`)}
            />
          </>
        )}
        <ActionRow
          icon={<Pencil size={16} />}
          label="Edit reminder"
          onClick={() => {
            onClose();
            ui.open({ type: "reminderEdit", reminderId: reminder.id, ownerId: reminder.ownerId });
          }}
        />
        <ActionRow icon={<Trash2 size={16} />} label="Delete" danger onClick={() => void remove()} />
      </div>
    </BottomSheet>
  );
}

function ActionRow({
  icon,
  label,
  onClick,
  danger,
}: {
  icon: React.ReactNode;
  label: string;
  onClick: () => void;
  danger?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cx(
        "flex h-12 w-full items-center gap-3 rounded-2xl px-3 text-left transition active:scale-[0.98]",
        danger ? "text-neg hover:bg-negsoft" : "text-ink hover:bg-surface2"
      )}
    >
      <span
        className={cx(
          "flex h-9 w-9 shrink-0 items-center justify-center rounded-[13px]",
          danger ? "bg-negsoft text-neg" : "bg-brandsoft text-brand"
        )}
      >
        {icon}
      </span>
      <span className="min-w-0 flex-1 truncate text-[13px] font-semibold">{label}</span>
    </button>
  );
}

/* ------------------------------------------------------------------ */
/* section                                                              */
/* ------------------------------------------------------------------ */

interface Group {
  key: "overdue" | "today" | "upcoming";
  label: string;
  rows: Reminder[];
}

export function RemindersSection({ ownerId }: { ownerId: OwnerId }) {
  const ui = useUI();
  const remindersAll = useApp((s) => s.reminders);
  const contacts = useApp((s) => s.contacts);
  const saveReminder = useApp((s) => s.saveReminder);

  const reminders = useMemo(
    () => remindersAll.filter((r) => r.ownerId === ownerId && !r.deletedAt),
    [remindersAll, ownerId]
  );

  const groups = useMemo<Group[]>(() => {
    const open = reminders.filter((r) => !r.done);
    const mk = (key: Group["key"], label: string, status: "overdue" | "today" | "upcoming") => ({
      key,
      label,
      rows: open.filter((r) => dueStatus(r.dueAt) === status).sort((a, b) => a.dueAt - b.dueAt),
    });
    return [
      mk("overdue", "Overdue", "overdue"),
      mk("today", "Today", "today"),
      mk("upcoming", "Upcoming", "upcoming"),
    ];
  }, [reminders]);

  /* birthday one-tap: contact has a birthday and no yearly reminder yet */
  const contact = useMemo(
    () => (ownerId === "general" ? undefined : contacts.find((c) => c.id === ownerId && !c.deletedAt)),
    [contacts, ownerId]
  );
  const hasBirthdayReminder = useMemo(
    () => reminders.some((r) => r.kind === "birthday"),
    [reminders]
  );
  const showBirthday = !!contact?.birthday && !hasBirthdayReminder;

  const addBirthday = () => {
    if (!contact?.birthday) return;
    saveReminder({
      ownerId,
      title: `${displayName(contact)}'s birthday`,
      kind: "birthday",
      dueAt: nextBirthday9(contact.birthday),
      repeat: "yearly",
    });
    toastOk(`Birthday reminder added · ${fdShort(nextBirthday9(contact.birthday))}`);
  };

  const seeAll = () => {
    ui.closeAll();
    ui.setTab("reminders");
  };

  const addBtn = (
    <button
      type="button"
      onClick={() => ui.open({ type: "reminderEdit", ownerId })}
      aria-label="Add reminder"
      className="flex h-11 items-center gap-1 rounded-xl bg-brand px-3.5 text-[11px] font-semibold text-white shadow-key transition hover:bg-brandink active:scale-95"
    >
      <Plus size={13} /> Add
    </button>
  );

  if (!reminders.length && !showBirthday) {
    return (
      <section aria-label="Reminders">
        <SectionLabel right={addBtn}>Reminders</SectionLabel>
        <EmptyState
          icon={<Bell size={21} />}
          title="No reminders here"
          hint="Payments, follow-ups, birthdays — anything you don't want to forget."
        />
      </section>
    );
  }

  return (
    <section aria-label="Reminders" className="mt-1">
      <SectionLabel right={addBtn}>Reminders</SectionLabel>

      {showBirthday && (
        <button
          type="button"
          onClick={addBirthday}
          className="mb-3 flex h-11 w-full items-center gap-2.5 rounded-2xl border border-brand/25 bg-brandsoft px-4 text-left shadow-key transition active:scale-[0.98]"
        >
          <span className="text-base" aria-hidden>
            🎂
          </span>
          <span className="min-w-0 flex-1">
            <span className="block truncate text-[12.5px] font-bold text-ink">
              Add birthday reminder
            </span>
            <span className="block truncate text-[10.5px] text-mute">
              {fdShort(nextBirthday9(contact?.birthday || ""))} · repeats yearly
            </span>
          </span>
          <ChevronRight size={15} className="shrink-0 text-brand" />
        </button>
      )}

      <div className="space-y-4">
        {groups.map((g) => (
          <div key={g.key}>
            <SectionLabel
              right={
                g.rows.length ? (
                  <span className="rounded-full bg-surface2 px-2 py-0.5 text-[10px] font-bold text-mute">
                    {g.rows.length}
                  </span>
                ) : undefined
              }
            >
              <span className={g.key === "overdue" ? "text-neg" : undefined}>{g.label}</span>
            </SectionLabel>
            {g.rows.length ? (
              <>
                {g.rows.slice(0, 5).map((r) => (
                  <ReminderRow key={r.id} reminder={r} compact />
                ))}
                {g.rows.length > 5 && (
                  <button
                    type="button"
                    onClick={seeAll}
                    className="flex h-11 w-full items-center justify-center gap-1.5 rounded-2xl border border-line2 bg-surface text-[12px] font-semibold text-brand shadow-key transition active:scale-[0.98]"
                  >
                    See all {g.rows.length} <ChevronRight size={14} />
                  </button>
                )}
              </>
            ) : (
              <p className="rounded-2xl border border-dashed border-line2 px-4 py-2.5 text-center text-[11px] text-mute">
                {g.key === "overdue" ? "Nothing overdue 🎉" : g.key === "today" ? "Nothing due today" : "Nothing coming up"}
              </p>
            )}
          </div>
        ))}
      </div>
    </section>
  );
}
