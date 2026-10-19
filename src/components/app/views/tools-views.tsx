"use client";

/* =========================================================================
   Task 2-c — tools views
   RemindersView (main tab): segmented status groups + kind filter chips,
   notifications permission card, "+ New reminder".
   TrashView (main tab): per-type segmented trash with restore / delete
   forever / restore all / empty trash.
   ========================================================================= */

import { useMemo, useState } from "react";
import {
  BellRing,
  CreditCard,
  ListChecks,
  Notebook,
  Plus,
  RotateCcw,
  Trash2,
  Users,
} from "lucide-react";
import { useApp } from "@/lib/store";
import { useUI } from "@/lib/ui-store";
import { dueStatus } from "@/lib/calc";
import { displayName, money as fmtMoney, relTime } from "@/lib/utils";
import type { Contact, ID, LedgerEntry, Reminder, ReminderKind } from "@/lib/types";
import { useConfirm } from "../kit/Confirm";
import { toastOk } from "../kit/toast";
import {
  Button,
  Chip,
  EmptyState,
  IconButton,
  Money,
  SectionCard,
  SectionLabel,
  Segmented,
  cx,
} from "../kit/primitives";
import { ReminderRow } from "../sections/RemindersSection";

/* ================================================================== */
/* RemindersView                                                       */
/* ================================================================== */

type StatusSeg = "overdue" | "today" | "upcoming" | "done" | "all";
type KindFilter = "all" | ReminderKind;

const KIND_CHIPS: { id: KindFilter; label: string }[] = [
  { id: "all", label: "All kinds" },
  { id: "payment", label: "Payments" },
  { id: "todo", label: "To-dos" },
  { id: "birthday", label: "Birthdays" },
  { id: "followup", label: "Follow-ups" },
  { id: "custom", label: "Custom" },
];

export function RemindersView() {
  const ui = useUI();
  const remindersAll = useApp((s) => s.reminders);
  const settings = useApp((s) => s.settings);
  const updateSettings = useApp((s) => s.updateSettings);

  const [seg, setSeg] = useState<StatusSeg>("all");
  const [kind, setKind] = useState<KindFilter>("all");

  const reminders = useMemo(() => remindersAll.filter((r) => !r.deletedAt), [remindersAll]);
  const filtered = useMemo(
    () => (kind === "all" ? reminders : reminders.filter((r) => r.kind === kind)),
    [reminders, kind]
  );

  const groups = useMemo(() => {
    const open = filtered.filter((r) => !r.done);
    const byStatus = (status: "overdue" | "today" | "upcoming") =>
      open.filter((r) => dueStatus(r.dueAt) === status).sort((a, b) => a.dueAt - b.dueAt);
    return {
      overdue: byStatus("overdue"),
      today: byStatus("today"),
      upcoming: byStatus("upcoming"),
      done: filtered
        .filter((r) => r.done)
        .sort((a, b) => (b.doneAt || b.updatedAt) - (a.doneAt || a.updatedAt)),
    };
  }, [filtered]);

  const stats =
    groups.overdue.length + groups.today.length + groups.upcoming.length === 0
      ? "All clear — nothing pending"
      : `${groups.overdue.length} overdue · ${groups.today.length} today · ${groups.upcoming.length} upcoming`;

  /* notifications permission card — honest about what works when */
  const canNotify = typeof Notification !== "undefined";
  const showNotifCard = canNotify && !settings.notifAsked;

  const askNotif = async () => {
    let granted = false;
    try {
      granted = (await Notification.requestPermission()) === "granted";
    } catch {
      granted = false;
    }
    updateSettings({ notifAsked: true });
    toastOk(
      granted
        ? "Notifications on — you'll be alerted in the background"
        : "No problem — alarms still work while callmony is open"
    );
  };

  const renderGroup = (
    key: "overdue" | "today" | "upcoming" | "done",
    label: string,
    rows: Reminder[],
    emptyHint: string
  ) => {
    if (seg !== "all" && seg !== key) return null;
    if (seg === "all" && rows.length === 0) return null;
    return (
      <section key={key} aria-label={label}>
        <SectionLabel
          right={
            rows.length ? (
              <span
                className={cx(
                  "rounded-full px-2 py-0.5 text-[10px] font-bold",
                  key === "overdue" ? "bg-negsoft text-neg" : "bg-surface2 text-mute"
                )}
              >
                {rows.length}
              </span>
            ) : undefined
          }
        >
          <span className={key === "overdue" ? "text-neg" : undefined}>{label}</span>
        </SectionLabel>
        {rows.length > 0 ? (
          <div>
            {rows.map((r) => (
              <ReminderRow key={r.id} reminder={r} />
            ))}
          </div>
        ) : (
          <p className="rounded-2xl border border-dashed border-line2 px-4 py-3 text-center text-[11px] text-mute">
            {emptyHint}
          </p>
        )}
      </section>
    );
  };

  return (
    <div className="tab-content">
      {/* heading */}
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="text-[25px] font-bold tracking-tight text-ink">Reminders</h2>
          <p className="mt-0.5 text-xs font-medium text-mute">{stats}</p>
        </div>
        <Button
          size="md"
          className="h-11 shrink-0"
          onClick={() => ui.open({ type: "reminderEdit", ownerId: "general" })}
        >
          <Plus size={14} /> New reminder
        </Button>
      </div>

      {/* notifications opt-in card */}
      {showNotifCard && (
        <SectionCard className="mt-4 border-brand/25 bg-brandsoft">
          <div className="flex items-start gap-3">
            <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[15px] bg-brand text-white shadow-key">
              <BellRing size={19} />
            </span>
            <div className="min-w-0 flex-1">
              <p className="text-[13px] font-bold text-ink">Turn on notifications</p>
              <p className="mt-0.5 text-[11px] leading-4 text-mute">
                Alarms always work while callmony is open; notifications alert you while the app is in
                the background.
              </p>
              <div className="mt-2.5 flex items-center gap-1">
                <Button size="md" className="h-11" onClick={() => void askNotif()}>
                  Turn on
                </Button>
                <button
                  type="button"
                  onClick={() => updateSettings({ notifAsked: true })}
                  className="flex h-11 items-center rounded-xl px-3 text-[11px] font-semibold text-mute transition hover:text-ink"
                >
                  Not now
                </button>
              </div>
            </div>
          </div>
        </SectionCard>
      )}

      {/* status segmented */}
      <Segmented<StatusSeg>
        className="mt-4"
        value={seg}
        onChange={setSeg}
        options={[
          { value: "overdue", label: "Overdue", badge: groups.overdue.length },
          { value: "today", label: "Today", badge: groups.today.length },
          { value: "upcoming", label: "Upcoming", badge: groups.upcoming.length },
          { value: "done", label: "Done" },
          { value: "all", label: "All" },
        ]}
      />

      {/* kind filter chips */}
      <div className="app-scroll mt-3 flex gap-1.5 overflow-x-auto pb-0.5">
        {KIND_CHIPS.map((k) => (
          <Chip key={k.id} active={kind === k.id} onClick={() => setKind(k.id)}>
            {k.label}
          </Chip>
        ))}
      </div>

      {/* groups */}
      <div className="mt-4 space-y-5 pb-4">
        {filtered.length === 0 ? (
          <EmptyState
            icon={<BellRing size={21} />}
            title={kind === "all" ? "No reminders yet" : `No ${KIND_CHIPS.find((k) => k.id === kind)?.label.toLowerCase()}`}
            hint="Payment due dates, follow-ups, birthdays — callmony will alarm you on time."
            action={
              <Button
                variant="brand"
                size="lg"
                onClick={() => ui.open({ type: "reminderEdit", ownerId: "general" })}
              >
                <Plus size={15} /> New reminder
              </Button>
            }
          />
        ) : (
          <>
            {renderGroup("overdue", "Overdue", groups.overdue, "Nothing overdue 🎉")}
            {renderGroup("today", "Today", groups.today, "Nothing due today")}
            {renderGroup("upcoming", "Upcoming", groups.upcoming, "Nothing coming up")}
            {renderGroup("done", "Done", groups.done, "Nothing completed yet")}
          </>
        )}
      </div>
    </div>
  );
}

/* ================================================================== */
/* TrashView                                                           */
/* ================================================================== */

type TrashTab = "all" | "contacts" | "entries" | "lists" | "notes" | "reminders" | "banks";

interface TrashItem {
  key: string;
  name: string;
  icon: React.ReactNode;
  iconCls: string;
  title: React.ReactNode;
  sub: string;
  deletedAt: number;
  restore: () => void;
  purge: () => void;
}

const TAB_LABELS: Record<TrashTab, string> = {
  all: "All",
  contacts: "Contacts",
  entries: "Entries",
  lists: "Lists",
  notes: "Notes",
  reminders: "Reminders",
  banks: "Accounts",
};

export function TrashView() {
  const confirm = useConfirm();
  const [tab, setTab] = useState<TrashTab>("all");

  const contacts = useApp((s) => s.contacts);
  const entries = useApp((s) => s.entries);
  const lists = useApp((s) => s.lists);
  const notes = useApp((s) => s.notes);
  const reminders = useApp((s) => s.reminders);
  const banks = useApp((s) => s.banks);
  const currency = useApp((s) => s.settings.currency);
  const restoreContact = useApp((s) => s.restoreContact);
  const purgeContact = useApp((s) => s.purgeContact);
  const restoreEntry = useApp((s) => s.restoreEntry);
  const purgeEntry = useApp((s) => s.purgeEntry);
  const restoreList = useApp((s) => s.restoreList);
  const purgeList = useApp((s) => s.purgeList);
  const restoreNote = useApp((s) => s.restoreNote);
  const purgeNote = useApp((s) => s.purgeNote);
  const restoreReminder = useApp((s) => s.restoreReminder);
  const purgeReminder = useApp((s) => s.purgeReminder);
  const restoreBank = useApp((s) => s.restoreBank);
  const purgeBank = useApp((s) => s.purgeBank);

  const contactById = useMemo(() => {
    const m = new Map<ID, Contact>();
    contacts.forEach((c) => m.set(c.id, c));
    return m;
  }, [contacts]);

  const ownerLabel = (ownerId: string): string =>
    ownerId === "general" ? "General" : displayName(contactById.get(ownerId));

  const deletedSub = (ownerId: string | undefined, deletedAt: number): string => {
    const when = `Deleted ${relTime(deletedAt)}`;
    return ownerId ? `${ownerLabel(ownerId)} · ${when}` : when;
  };

  /* Derivation is cheap (filtering small local arrays) — computed per render
     so the React compiler can auto-memoize it reliably. */
  const items: TrashItem[] = (() => {
    const out: TrashItem[] = [];

    if (tab === "all" || tab === "contacts")
      contacts.forEach((c) => {
        if (!c.deletedAt) return;
        out.push({
          key: `c-${c.id}`,
          name: displayName(c),
          icon: <Users size={17} />,
          iconCls: "bg-brandsoft text-brand",
          title: <span className="truncate">{displayName(c)}</span>,
          sub: c.company ? `${c.company} · Deleted ${relTime(c.deletedAt)}` : `Deleted ${relTime(c.deletedAt)}`,
          deletedAt: c.deletedAt,
          restore: () => restoreContact(c.id),
          purge: () => void purgeContact(c.id),
        });
      });

    if (tab === "all" || tab === "entries")
      entries.forEach((e: LedgerEntry) => {
        if (!e.deletedAt) return;
        const name = `${e.type === "credit" ? "Credit" : "Debit"} ${fmtMoney(e.amount, currency)}`;
        out.push({
          key: `e-${e.id}`,
          name,
          icon: <CreditCard size={17} />,
          iconCls: "bg-brandsoft text-pos",
          title: (
            <span className="flex min-w-0 items-baseline gap-1.5 truncate">
              <span className="shrink-0">{e.type === "credit" ? "Credit" : "Debit"}</span>
              <Money value={e.amount} className="truncate" />
            </span>
          ),
          sub: deletedSub(e.ownerId, e.deletedAt),
          deletedAt: e.deletedAt,
          restore: () => restoreEntry(e.id),
          purge: () => void purgeEntry(e.id),
        });
      });

    if (tab === "all" || tab === "lists")
      lists.forEach((l) => {
        if (!l.deletedAt) return;
        out.push({
          key: `l-${l.id}`,
          name: l.title,
          icon: <ListChecks size={17} />,
          iconCls: "bg-brandsoft text-brand",
          title: <span className="truncate">{l.title}</span>,
          sub: `${l.items.length} items · Deleted ${relTime(l.deletedAt)}`,
          deletedAt: l.deletedAt,
          restore: () => restoreList(l.id),
          purge: () => void purgeList(l.id),
        });
      });

    if (tab === "all" || tab === "notes")
      notes.forEach((n) => {
        if (!n.deletedAt) return;
        out.push({
          key: `n-${n.id}`,
          name: n.title || "Untitled note",
          icon: <Notebook size={17} />,
          iconCls: "bg-surface2 text-amber",
          title: <span className="truncate">{n.title || "Untitled note"}</span>,
          sub: deletedSub(n.ownerId, n.deletedAt),
          deletedAt: n.deletedAt,
          restore: () => restoreNote(n.id),
          purge: () => void purgeNote(n.id),
        });
      });

    if (tab === "all" || tab === "reminders")
      reminders.forEach((r) => {
        if (!r.deletedAt) return;
        out.push({
          key: `r-${r.id}`,
          name: r.title,
          icon: <BellRing size={17} />,
          iconCls: "bg-negsoft text-neg",
          title: <span className="truncate">{r.title}</span>,
          sub: deletedSub(r.ownerId, r.deletedAt),
          deletedAt: r.deletedAt,
          restore: () => restoreReminder(r.id),
          purge: () => void purgeReminder(r.id),
        });
      });

    if (tab === "all" || tab === "banks")
      banks.forEach((b) => {
        if (!b.deletedAt) return;
        out.push({
          key: `b-${b.id}`,
          name: b.label,
          icon: <CreditCard size={17} />,
          iconCls: "bg-surface2 text-ink",
          title: <span className="truncate">{b.label}</span>,
          sub: b.bankName ? `${b.bankName} · Deleted ${relTime(b.deletedAt)}` : `Deleted ${relTime(b.deletedAt)}`,
          deletedAt: b.deletedAt,
          restore: () => restoreBank(b.id),
          purge: () => void purgeBank(b.id),
        });
      });

    return out.sort((a, b) => b.deletedAt - a.deletedAt);
  })();

  const counts = useMemo(
    () => ({
      contacts: contacts.filter((c) => c.deletedAt).length,
      entries: entries.filter((e) => e.deletedAt).length,
      lists: lists.filter((l) => l.deletedAt).length,
      notes: notes.filter((n) => n.deletedAt).length,
      reminders: reminders.filter((r) => r.deletedAt).length,
      banks: banks.filter((b) => b.deletedAt).length,
    }),
    [contacts, entries, lists, notes, reminders, banks]
  );

  const purgeOne = async (it: TrashItem) => {
    const ok = await confirm({
      title: "Delete forever?",
      message: `“${it.name}” will be permanently deleted. This cannot be undone.`,
      confirmLabel: "Delete forever",
      tone: "danger",
    });
    if (!ok) return;
    it.purge();
    toastOk("Deleted forever");
  };

  const restoreAll = () => {
    if (!items.length) return;
    items.forEach((it) => it.restore());
    toastOk(`Restored ${items.length} item${items.length === 1 ? "" : "s"}`);
  };

  const emptyTrash = async () => {
    if (!items.length) return;
    const scope = tab === "all" ? `all ${items.length} items` : `the ${items.length} ${TAB_LABELS[tab].toLowerCase()}`;
    const ok = await confirm({
      title: "Empty trash?",
      message: `Permanently delete ${scope} in trash? This cannot be undone.`,
      confirmLabel: "Empty trash",
      tone: "danger",
    });
    if (!ok) return;
    items.forEach((it) => it.purge());
    toastOk("Trash emptied");
  };

  return (
    <div className="tab-content">
      {/* heading */}
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="text-[25px] font-bold tracking-tight text-ink">Trash</h2>
          <p className="mt-0.5 text-xs font-medium text-mute">
            {items.length
              ? `${items.length} item${items.length === 1 ? "" : "s"} · deleted items stay until you empty the trash`
              : "Deleted items land here first"}
          </p>
        </div>
        {items.length > 0 && (
          <div className="flex shrink-0 gap-2">
            <Button variant="surface" size="sm" className="h-10" onClick={restoreAll}>
              <RotateCcw size={13} /> Restore all
            </Button>
            <Button variant="danger" size="sm" className="h-10" onClick={() => void emptyTrash()}>
              <Trash2 size={13} /> Empty
            </Button>
          </div>
        )}
      </div>

      {/* type segmented */}
      <Segmented<TrashTab>
        className="mt-4"
        value={tab}
        onChange={setTab}
        options={[
          { value: "all", label: TAB_LABELS.all, badge: items.length },
          { value: "contacts", label: TAB_LABELS.contacts, badge: counts.contacts },
          { value: "entries", label: TAB_LABELS.entries, badge: counts.entries },
          { value: "lists", label: TAB_LABELS.lists, badge: counts.lists },
          { value: "notes", label: TAB_LABELS.notes, badge: counts.notes },
          { value: "reminders", label: TAB_LABELS.reminders, badge: counts.reminders },
          { value: "banks", label: TAB_LABELS.banks, badge: counts.banks },
        ]}
      />

      {/* rows */}
      <div className="mt-4 space-y-2 pb-4">
        {items.length === 0 ? (
          <EmptyState
            icon={<Trash2 size={21} />}
            title="Trash is empty 🎉"
            hint={
              tab === "all"
                ? "Deleted contacts, entries, lists, notes, reminders and accounts appear here."
                : `No deleted ${TAB_LABELS[tab].toLowerCase()} right now.`
            }
          />
        ) : (
          items.map((it) => (
            <div
              key={it.key}
              className="flex items-center gap-3 rounded-2xl border border-line bg-surface p-2.5 pr-2 shadow-key"
            >
              <span
                className={cx(
                  "flex h-10 w-10 shrink-0 items-center justify-center rounded-[14px]",
                  it.iconCls
                )}
                aria-hidden
              >
                {it.icon}
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-[13px] font-semibold text-ink">{it.title}</p>
                <p className="truncate text-[11px] text-mute">{it.sub}</p>
              </div>
              <IconButton
                label={`Restore ${it.name}`}
                variant="surface"
                onClick={() => {
                  it.restore();
                  toastOk("Restored");
                }}
              >
                <RotateCcw size={15} />
              </IconButton>
              <IconButton
                label={`Delete ${it.name} forever`}
                variant="danger"
                onClick={() => void purgeOne(it)}
              >
                <Trash2 size={15} />
              </IconButton>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
