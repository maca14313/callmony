"use client";

/* =========================================================================
   Task 5-c — tools views
   RemindersView (main tab): segmented status groups + kind filter chips,
   notifications permission card, "+ New reminder".
   TrashView (main tab): per-type segmented trash with restore / delete
   forever / restore all / empty trash — incl. soft-deleted ledger groups.
   ========================================================================= */

import { useMemo, useState } from "react";
import {
  BellRing,
  CreditCard,
  Layers,
  ListChecks,
  Notebook,
  Plus,
  RotateCcw,
  Trash2,
  Users,
} from "lucide-react";
import { useApp } from "@/lib/store";
import { useUI } from "@/lib/ui-store";
import { useT } from "@/lib/i18n";
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

/* Kind chips — labels use the rm.kind* catalog keys (singular forms). */
const kindChips = (t: (k: string) => string): { id: KindFilter; label: string }[] => [
  { id: "all", label: t("rm.allKinds") },
  { id: "payment", label: t("rm.kindPayment") },
  { id: "todo", label: t("rm.kindTodo") },
  { id: "birthday", label: t("rm.kindBirthday") },
  { id: "followup", label: t("rm.kindFollowup") },
  { id: "custom", label: t("rm.kindCustom") },
];

export function RemindersView() {
  const ui = useUI();
  const t = useT();
  const remindersAll = useApp((s) => s.reminders);
  const settings = useApp((s) => s.settings);
  const updateSettings = useApp((s) => s.updateSettings);

  const [seg, setSeg] = useState<StatusSeg>("all");
  const [kind, setKind] = useState<KindFilter>("all");
  const chips = kindChips(t);

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
      ? t("rm.allClear")
      : t("rm.statsLine", { o: groups.overdue.length, t: groups.today.length, u: groups.upcoming.length });

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
    toastOk(granted ? t("rm.notifGranted") : t("rm.notifDeclined"));
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
          <h2 className="text-[25px] font-bold tracking-tight text-ink">{t("tab.reminders")}</h2>
          <p className="mt-0.5 text-xs font-medium text-mute">{stats}</p>
        </div>
        <Button
          size="md"
          className="h-11 shrink-0"
          onClick={() => ui.open({ type: "reminderEdit", ownerId: "general" })}
        >
          <Plus size={14} /> {t("att.newReminder")}
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
              <p className="text-[13px] font-bold text-ink">{t("rm.turnOnNotifications")}</p>
              <p className="mt-0.5 text-[11px] leading-4 text-mute">{t("rm.notifCardHint")}</p>
              <div className="mt-2.5 flex items-center gap-1">
                <Button size="md" className="h-11" onClick={() => void askNotif()}>
                  {t("rm.turnOn")}
                </Button>
                <button
                  type="button"
                  onClick={() => updateSettings({ notifAsked: true })}
                  className="flex h-11 items-center rounded-xl px-3 text-[11px] font-semibold text-mute transition hover:text-ink"
                >
                  {t("rm.notNow")}
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
          { value: "overdue", label: t("rm.overdue"), badge: groups.overdue.length },
          { value: "today", label: t("rm.today"), badge: groups.today.length },
          { value: "upcoming", label: t("rm.upcoming"), badge: groups.upcoming.length },
          { value: "done", label: t("rm.done") },
          { value: "all", label: t("act.all") },
        ]}
      />

      {/* kind filter chips */}
      <div className="app-scroll mt-3 flex gap-1.5 overflow-x-auto pb-0.5">
        {chips.map((k) => (
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
            title={kind === "all" ? t("rm.noReminders") : `No ${chips.find((k) => k.id === kind)?.label.toLowerCase()}`}
            hint={t("rm.remindersGeneralHint")}
            action={
              <Button
                variant="brand"
                size="lg"
                onClick={() => ui.open({ type: "reminderEdit", ownerId: "general" })}
              >
                <Plus size={15} /> {t("att.newReminder")}
              </Button>
            }
          />
        ) : (
          <>
            {/* MISSING keys: rm.nothingOverdue ("Nothing overdue 🎉"), rm.nothingToday ("Nothing due today"),
               rm.nothingUpcoming ("Nothing coming up"), rm.nothingDone ("Nothing completed yet") */}
            {renderGroup("overdue", t("rm.overdue"), groups.overdue, "Nothing overdue 🎉")}
            {renderGroup("today", t("rm.today"), groups.today, "Nothing due today")}
            {renderGroup("upcoming", t("rm.upcoming"), groups.upcoming, "Nothing coming up")}
            {renderGroup("done", t("rm.done"), groups.done, "Nothing completed yet")}
          </>
        )}
      </div>
    </div>
  );
}

/* ================================================================== */
/* TrashView                                                           */
/* ================================================================== */

type TrashTab = "all" | "contacts" | "entries" | "groups" | "lists" | "notes" | "reminders" | "banks";

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

/* Trash segment labels, translated in-component. "Entries" has no catalog
   key yet (MISSING: tr.entries "Entries"). */
const TRASH_TAB_LABEL_KEY: Record<Exclude<TrashTab, "entries">, string> = {
  all: "act.all",
  contacts: "nav.contacts",
  groups: "lg.groups",
  lists: "tab.lists",
  notes: "tab.notes",
  reminders: "tab.reminders",
  banks: "tab.accounts",
};

export function TrashView() {
  const confirm = useConfirm();
  const t = useT();
  const [tab, setTab] = useState<TrashTab>("all");

  const contacts = useApp((s) => s.contacts);
  const entries = useApp((s) => s.entries);
  const groupsAll = useApp((s) => s.groups);
  const lists = useApp((s) => s.lists);
  const notes = useApp((s) => s.notes);
  const reminders = useApp((s) => s.reminders);
  const banks = useApp((s) => s.banks);
  const currency = useApp((s) => s.settings.currency);
  const restoreContact = useApp((s) => s.restoreContact);
  const purgeContact = useApp((s) => s.purgeContact);
  const restoreEntry = useApp((s) => s.restoreEntry);
  const purgeEntry = useApp((s) => s.purgeEntry);
  const restoreGroup = useApp((s) => s.restoreGroup);
  const purgeGroup = useApp((s) => s.purgeGroup);
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
    ownerId === "general" ? t("misc.general") : displayName(contactById.get(ownerId));

  /* MISSING key: tr.deletedAgo ("Deleted {time}") */
  const deletedSub = (ownerId: string | undefined, deletedAt: number): string => {
    const when = `Deleted ${relTime(deletedAt)}`;
    return ownerId ? `${ownerLabel(ownerId)} · ${when}` : when;
  };

  /* translated label for a trash segment */
  const tabLabel = (x: TrashTab): string =>
    x === "entries" ? "Entries" : t(TRASH_TAB_LABEL_KEY[x]);

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
          /* "Deleted …" kept in English — MISSING key: tr.deletedAgo ("Deleted {time}") */
          sub: c.company ? `${c.company} · Deleted ${relTime(c.deletedAt)}` : `Deleted ${relTime(c.deletedAt)}`,
          deletedAt: c.deletedAt,
          restore: () => restoreContact(c.id),
          purge: () => void purgeContact(c.id),
        });
      });

    if (tab === "all" || tab === "entries")
      entries.forEach((e: LedgerEntry) => {
        if (!e.deletedAt) return;
        const name = `${e.type === "credit" ? t("lg.credit") : t("lg.debit")} ${fmtMoney(e.amount, currency)}`;
        out.push({
          key: `e-${e.id}`,
          name,
          icon: <CreditCard size={17} />,
          iconCls: "bg-brandsoft text-pos",
          title: (
            <span className="flex min-w-0 items-baseline gap-1.5 truncate">
              <span className="shrink-0">{e.type === "credit" ? t("lg.credit") : t("lg.debit")}</span>
              <Money value={e.amount} className="truncate" />
            </span>
          ),
          sub: deletedSub(e.ownerId, e.deletedAt),
          deletedAt: e.deletedAt,
          restore: () => restoreEntry(e.id),
          purge: () => void purgeEntry(e.id),
        });
      });

    if (tab === "all" || tab === "groups")
      groupsAll.forEach((g) => {
        if (!g.deletedAt) return;
        out.push({
          key: `g-${g.id}`,
          name: g.title,
          icon: <Layers size={17} />,
          iconCls: "bg-brandsoft text-brand",
          title: <span className="truncate">{g.title}</span>,
          sub: deletedSub(g.ownerId, g.deletedAt),
          deletedAt: g.deletedAt,
          restore: () => restoreGroup(g.id),
          purge: () => void purgeGroup(g.id),
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
          sub: `${l.items.length} ${t("ls.items")} · Deleted ${relTime(l.deletedAt)}`,
          deletedAt: l.deletedAt,
          restore: () => restoreList(l.id),
          purge: () => void purgeList(l.id),
        });
      });

    if (tab === "all" || tab === "notes")
      notes.forEach((n) => {
        if (!n.deletedAt) return;
        /* MISSING key: nt.untitled ("Untitled note") */
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
      groups: groupsAll.filter((g) => g.deletedAt).length,
      lists: lists.filter((l) => l.deletedAt).length,
      notes: notes.filter((n) => n.deletedAt).length,
      reminders: reminders.filter((r) => r.deletedAt).length,
      banks: banks.filter((b) => b.deletedAt).length,
    }),
    [contacts, entries, groupsAll, lists, notes, reminders, banks]
  );

  const purgeOne = async (it: TrashItem) => {
    /* MISSING keys: tr.deleteForeverTitle ("Delete forever?"),
       tr.deleteForeverMsg ("“{name}” will be permanently deleted. This cannot be undone."),
       tr.deleteForeverLabel ("Delete forever") */
    const ok = await confirm({
      title: "Delete forever?",
      message: `“${it.name}” will be permanently deleted. This cannot be undone.`,
      confirmLabel: "Delete forever",
      tone: "danger",
    });
    if (!ok) return;
    it.purge();
    toastOk(t("tr.deletedForever"));
  };

  const restoreAll = () => {
    if (!items.length) return;
    items.forEach((it) => it.restore());
    toastOk(t("tr.restoredN", { n: items.length }));
  };

  const emptyTrash = async () => {
    if (!items.length) return;
    const scope = tab === "all" ? `all ${items.length} items` : `the ${items.length} ${tabLabel(tab).toLowerCase()}`;
    /* MISSING keys: tr.emptyTrashTitle ("Empty trash?"),
       tr.emptyTrashMsg ("Permanently delete {scope} in trash? This cannot be undone."),
       tr.emptyTrashLabel ("Empty trash") */
    const ok = await confirm({
      title: "Empty trash?",
      message: `Permanently delete ${scope} in trash? This cannot be undone.`,
      confirmLabel: "Empty trash",
      tone: "danger",
    });
    if (!ok) return;
    items.forEach((it) => it.purge());
    toastOk(t("tr.trashEmptied"));
  };

  return (
    <div className="tab-content">
      {/* heading */}
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="text-[25px] font-bold tracking-tight text-ink">{t("hdr.trash")}</h2>
          {/* MISSING keys: tr.itemsStay ("deleted items stay until you empty the trash"),
              tr.trashIntro ("Deleted items land here first") */}
          <p className="mt-0.5 text-xs font-medium text-mute">
            {items.length
              ? `${items.length} item${items.length === 1 ? "" : "s"} · deleted items stay until you empty the trash`
              : "Deleted items land here first"}
          </p>
        </div>
        {items.length > 0 && (
          <div className="flex shrink-0 gap-2">
            {/* MISSING key: tr.restoreAll ("Restore all") */}
            <Button variant="surface" size="sm" className="h-10" onClick={restoreAll}>
              <RotateCcw size={13} /> Restore all
            </Button>
            {/* MISSING key: tr.empty ("Empty") */}
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
          { value: "all", label: tabLabel("all"), badge: items.length },
          { value: "contacts", label: tabLabel("contacts"), badge: counts.contacts },
          { value: "entries", label: tabLabel("entries"), badge: counts.entries },
          { value: "groups", label: tabLabel("groups"), badge: counts.groups },
          { value: "lists", label: tabLabel("lists"), badge: counts.lists },
          { value: "notes", label: tabLabel("notes"), badge: counts.notes },
          { value: "reminders", label: tabLabel("reminders"), badge: counts.reminders },
          { value: "banks", label: tabLabel("banks"), badge: counts.banks },
        ]}
      />

      {/* rows */}
      <div className="mt-4 space-y-2 pb-4">
        {items.length === 0 ? (
          <EmptyState
            icon={<Trash2 size={21} />}
            title={t("tr.trashEmpty")}
            hint={
              tab === "all"
                ? t("tr.trashEmptyHint")
                : t("tr.noDeletedX", { name: tabLabel(tab).toLowerCase() })
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
                label={`${t("st.restore")} ${it.name}`}
                variant="surface"
                onClick={() => {
                  it.restore();
                  toastOk(t("tr.restored"));
                }}
              >
                <RotateCcw size={15} />
              </IconButton>
              {/* MISSING key: tr.deleteForeverName ("Delete {name} forever") */}
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
