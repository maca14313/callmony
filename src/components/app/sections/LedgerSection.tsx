"use client";

import { useMemo, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import {
  ArrowDownLeft,
  ArrowUpRight,
  Banknote,
  CalendarClock,
  ChevronDown,
  Copy,
  Ellipsis,
  FileDown,
  FileText,
  HandCoins,
  Inbox,
  MessageCircle,
  Pencil,
  Plus,
  Scale,
  Share2,
  Trash2,
  UserRound,
  X,
} from "lucide-react";
import { useApp } from "@/lib/store";
import { useUI } from "@/lib/ui-store";
import {
  dueStatus,
  entryRemaining,
  entrySignedRemaining,
  openListsPending,
  r2,
  totalsForOwner,
} from "@/lib/calc";
import { copyText, displayName, downloadBlob, fd, fdShort, money, todayStr } from "@/lib/utils";
import { openExternal, primaryPhone, waLink } from "@/lib/share";
import { statementShareText, statementToPdfBlob } from "@/lib/pdf";
import { entriesToCSV } from "@/lib/exporters";
import { PAYMENT_METHODS, type Contact, type ID, type LedgerEntry, type OwnerId, type Payment } from "@/lib/types";
import { AttachmentStrip } from "../kit/Attachments";
import { CalcButton } from "../kit/Calculator";
import { ContactPicker } from "../kit/ContactPicker";
import { useConfirm } from "../kit/Confirm";
import { BottomSheet } from "../kit/Sheet";
import { toastErr, toastOk, toastUndoLast } from "../kit/toast";
import {
  Button,
  Chip,
  EmptyState,
  Field,
  IconButton,
  Money,
  ProgressBar,
  SectionLabel,
  cx,
  inputCls,
} from "../kit/primitives";

/* ------------------------------------------------------------------ */
/* helpers                                                              */
/* ------------------------------------------------------------------ */

type LedgerFilter = "all" | "credit" | "debit" | "unpaid" | "overdue";

const dueMs = (d: string) => new Date(`${d}T23:59:59`).getTime();

const parseNum = (s: string): number => {
  const n = parseFloat(String(s).replace(/,/g, ""));
  return isNaN(n) ? 0 : n;
};

/** Due-date chip — red when overdue, amber when today. */
function DueBadge({ dueDate }: { dueDate: string }) {
  const st = dueStatus(dueMs(dueDate));
  return (
    <span
      className={cx(
        "inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-bold",
        st === "overdue" ? "bg-negsoft text-neg" : st === "today" ? "bg-amber/15 text-amber" : "bg-surface2 text-mute"
      )}
    >
      <CalendarClock size={11} aria-hidden />
      {st === "overdue" ? "Overdue · " : st === "today" ? "Due today · " : "Due "}
      {fdShort(dueDate)}
    </span>
  );
}

function StatCard({
  label,
  value,
  tone,
  signed,
}: {
  label: string;
  value: number;
  tone: "pos" | "neg" | "mute";
  signed?: boolean;
}) {
  return (
    <div className="rounded-2xl border border-line bg-surface px-2 py-3 text-center shadow-key">
      <p className="text-[9px] font-bold uppercase tracking-[1.3px] text-mute">{label}</p>
      <p
        className={cx(
          "mt-1 text-[13px] font-bold tabular-nums",
          tone === "pos" ? "text-pos" : tone === "neg" ? "text-neg" : "text-ink"
        )}
      >
        <Money value={value} signed={signed} />
      </p>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Add-payment sheet                                                    */
/* ------------------------------------------------------------------ */

function AddPaymentSheet({
  open,
  onClose,
  remaining,
  onSubmit,
}: {
  open: boolean;
  onClose: () => void;
  remaining: number;
  onSubmit: (p: { amount: number; date: string; note?: string; method?: string }) => void;
}) {
  const [amount, setAmount] = useState("");
  const [date, setDate] = useState(todayStr());
  const [note, setNote] = useState("");
  const [method, setMethod] = useState("cash");
  if (!open) return null;

  const submit = () => {
    const amt = parseNum(amount);
    if (amt <= 0) {
      toastErr("Enter an amount greater than 0");
      return;
    }
    onSubmit({ amount: r2(amt), date: date || todayStr(), note: note.trim() || undefined, method });
  };

  return (
    <BottomSheet open={open} onClose={onClose} title="Add payment">
      <Field label="Amount">
        <div className="flex w-full items-center gap-2">
          <input
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            inputMode="decimal"
            placeholder="0"
            aria-label="Payment amount"
            className={cx(inputCls, "text-lg font-bold tabular-nums")}
          />
          <CalcButton onPick={(v) => setAmount(String(v))} />
        </div>
      </Field>
      {remaining > 0.005 && (
        <button
          type="button"
          onClick={() => setAmount(String(remaining))}
          className="mt-2 flex items-center gap-1.5 rounded-full bg-brandsoft px-3 py-1.5 text-[11px] font-bold text-brand transition active:scale-95"
          aria-label="Fill full remaining amount"
        >
          <HandCoins size={12} aria-hidden /> Full remaining: <Money value={remaining} />
        </button>
      )}
      <div className="mt-3 grid grid-cols-2 gap-2">
        <Field label="Date">
          <input
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            aria-label="Payment date"
            className={inputCls}
          />
        </Field>
        <Field label="Method">
          <select
            value={method}
            onChange={(e) => setMethod(e.target.value)}
            aria-label="Payment method"
            className={inputCls}
          >
            {PAYMENT_METHODS.map((m) => (
              <option key={m} value={m}>
                {m[0].toUpperCase() + m.slice(1)}
              </option>
            ))}
          </select>
        </Field>
      </div>
      <Field label="Note" className="mt-3">
        <input
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="Optional note"
          aria-label="Payment note"
          className={inputCls}
        />
      </Field>
      <Button variant="brand" size="lg" className="mt-4 w-full" onClick={submit}>
        <Plus size={15} /> Add payment
      </Button>
    </BottomSheet>
  );
}

/* ------------------------------------------------------------------ */
/* Entry card                                                           */
/* ------------------------------------------------------------------ */

function RemainingChip({ entry }: { entry: LedgerEntry }) {
  const rem = entryRemaining(entry);
  if (rem < -0.005) {
    return (
      <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-negsoft px-2 py-0.5 text-[10px] font-bold text-neg">
        Overpaid by <Money value={-rem} />
      </span>
    );
  }
  if (Math.abs(rem) <= 0.005) {
    return (
      <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-brandsoft px-2 py-0.5 text-[10px] font-bold text-pos">
        Settled ✓
      </span>
    );
  }
  return (
    <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-surface2 px-2 py-0.5 text-[10px] font-bold text-mute">
      Paid <Money value={r2(entry.amount - rem)} /> of <Money value={entry.amount} />
    </span>
  );
}

function EntryCard({
  entry,
  running,
  isGeneral,
  ownerId,
  contact,
}: {
  entry: LedgerEntry;
  running: number;
  isGeneral: boolean;
  ownerId: OwnerId;
  contact: Contact | null;
}) {
  const ui = useUI();
  const confirm = useConfirm();
  const entries = useApp((s) => s.entries);
  const settings = useApp((s) => s.settings);
  const addPayment = useApp((s) => s.addPayment);
  const removePayment = useApp((s) => s.removePayment);
  const deleteEntry = useApp((s) => s.deleteEntry);
  const moveEntity = useApp((s) => s.moveEntity);

  const [expanded, setExpanded] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [payOpen, setPayOpen] = useState(false);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [shareOpen, setShareOpen] = useState(false);

  const credit = entry.type === "credit";
  const remaining = entryRemaining(entry);
  const paid = r2(entry.amount - remaining);
  const phone = primaryPhone(contact);
  const ownerEntries = useMemo(
    () =>
      entries
        .filter((e) => e.ownerId === ownerId && !e.deletedAt)
        .sort((a, b) => a.date.localeCompare(b.date) || a.createdAt - b.createdAt),
    [entries, ownerId]
  );
  const ownerLabel = contact ? displayName(contact) : "General";

  const removePay = async (p: Payment, idx: number) => {
    const ok = await confirm({
      title: `Remove payment ${idx + 1}?`,
      message: `Payment of ${money(p.amount, settings.currency)} on ${fd(p.date)} will be removed.`,
      confirmLabel: "Remove",
      tone: "danger",
    });
    if (!ok) return;
    removePayment(entry.id, p.id);
    toastOk("Payment removed");
  };

  const onDelete = async () => {
    const ok = await confirm({
      title: "Delete entry?",
      message: `“${entry.note || entry.category || (credit ? "Credit" : "Debit")}” and its payment history move to Trash.`,
      confirmLabel: "Delete",
      tone: "danger",
    });
    if (!ok) return;
    setMenuOpen(false);
    deleteEntry(entry.id);
    toastUndoLast();
  };

  const menuActions: { key: string; icon: typeof Pencil; label: string; danger?: boolean; onClick: () => void }[] = [
    {
      key: "edit",
      icon: Pencil,
      label: "Edit",
      onClick: () => {
        setMenuOpen(false);
        ui.open({ type: "entryEdit", entryId: entry.id, ownerId: entry.ownerId });
      },
    },
    {
      key: "pay",
      icon: HandCoins,
      label: "Add payment",
      onClick: () => {
        setMenuOpen(false);
        setPayOpen(true);
      },
    },
    {
      key: "move",
      icon: UserRound,
      label: "Move to contact…",
      onClick: () => {
        setMenuOpen(false);
        setPickerOpen(true);
      },
    },
    ...(ownerId !== "general"
      ? [
          {
            key: "general",
            icon: Inbox,
            label: "Move to General",
            onClick: () => {
              setMenuOpen(false);
              moveEntity("entry", entry.id, "general");
              toastOk("Moved to General");
            },
          },
        ]
      : []),
    {
      key: "share",
      icon: Share2,
      label: "Share statement…",
      onClick: () => {
        setMenuOpen(false);
        setShareOpen(true);
      },
    },
    { key: "delete", icon: Trash2, label: "Delete", danger: true, onClick: () => void onDelete() },
  ];

  const nPayments = entry.payments.length;

  return (
    <div className="rounded-[22px] border border-line bg-surface p-4 shadow-key">
      {/* header */}
      <div className="flex items-start gap-3">
        <span
          className={cx(
            "flex h-10 w-10 shrink-0 items-center justify-center rounded-[14px]",
            credit ? "bg-brandsoft text-pos" : "bg-negsoft text-neg"
          )}
          aria-hidden
        >
          {credit ? <ArrowDownLeft size={18} /> : <ArrowUpRight size={18} />}
        </span>
        <button
          type="button"
          className="min-w-0 flex-1 text-left"
          onClick={() => setExpanded((v) => !v)}
          aria-expanded={expanded}
          aria-label={`Entry: ${entry.category || (credit ? "Credit" : "Debit")}. Tap to toggle payment history.`}
        >
          <span className="flex items-baseline justify-between gap-2">
            <span className="truncate text-[13px] font-semibold text-ink">
              {entry.category || (credit ? (isGeneral ? "Income" : "Credit") : isGeneral ? "Expense" : "Debit")}
            </span>
            <span className="shrink-0 text-[15px] font-bold">
              <Money value={credit ? entry.amount : -entry.amount} signed />
            </span>
          </span>
          <span className="mt-0.5 flex items-baseline justify-between gap-2">
            <span className="truncate text-[11px] text-mute">
              {fd(entry.date)}
              {entry.method ? ` · ${entry.method}` : ""}
            </span>
            <span className="shrink-0 text-[10px] font-medium">
              <Money value={running} signed className="text-mute" />
            </span>
          </span>
        </button>
        <IconButton
          label="Entry actions"
          className="h-10 w-10 shrink-0"
          onClick={() => setMenuOpen(true)}
        >
          <Ellipsis size={16} />
        </IconButton>
      </div>

      {/* meta chips + note */}
      <div className="mt-2.5 flex flex-wrap items-center gap-1.5 pl-[52px]">
        <RemainingChip entry={entry} />
        {entry.dueDate && <DueBadge dueDate={entry.dueDate} />}
        <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-surface2 px-2 py-0.5 text-[10px] font-semibold text-mute">
          <ChevronDown
            size={11}
            className={cx("transition-transform", expanded && "rotate-180")}
            aria-hidden
          />
          {nPayments} payment{nPayments !== 1 ? "s" : ""}
        </span>
      </div>
      {entry.note && <p className="mt-1.5 pl-[52px] text-[11px] leading-4 text-mute">{entry.note}</p>}

      {/* mini attachment strip — only mounted when there is something to show */}
      {entry.attachments.length > 0 && (
        <div className="mt-2 pl-[52px]">
          <AttachmentStrip parentType="entry" parentId={entry.id} ids={entry.attachments} />
        </div>
      )}

      {/* expandable payment history */}
      <AnimatePresence initial={false}>
        {expanded && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2, ease: "easeOut" }}
            className="overflow-hidden"
          >
            <div className="mt-3 border-t border-line pt-3">
              <p className="mb-2 text-[10px] font-bold uppercase tracking-[1.5px] text-mute">Payment history</p>
              {nPayments === 0 && <p className="pb-1 text-[11px] text-mute">No payments yet</p>}
              <ol className="space-y-1.5">
                {entry.payments.map((p, i) => (
                  <li
                    key={p.id}
                    className="flex items-center gap-2 rounded-xl bg-surface2 px-2.5 py-1.5"
                  >
                    <span className="w-4 shrink-0 text-[10.5px] font-bold tabular-nums text-mute">{i + 1}.</span>
                    <span className="shrink-0 text-[11px] text-mute">{fdShort(p.date)}</span>
                    <span className="shrink-0 text-[12px] font-bold text-ink">
                      <Money value={p.amount} />
                    </span>
                    {(p.note || p.method || p.auto) && (
                      <span className="min-w-0 flex-1 truncate text-[10px] text-mute">
                        {p.auto === "tick" ? "Ticked" : p.auto === "settle" ? "Settle up" : p.note || p.method}
                      </span>
                    )}
                    <IconButton
                      label={`Remove payment ${i + 1}`}
                      className="h-9 w-9 shrink-0"
                      onClick={() => void removePay(p, i)}
                    >
                      <X size={13} />
                    </IconButton>
                  </li>
                ))}
              </ol>
              {entry.amount > 0 && nPayments > 0 && (
                <ProgressBar className="mt-2.5" value={(paid / entry.amount) * 100} />
              )}
              <p className="mt-1.5 text-[10.5px] text-mute">
                {nPayments} payment{nPayments !== 1 ? "s" : ""} ·{" "}
                {remaining > 0.005 ? (
                  <>
                    <Money value={remaining} /> left
                  </>
                ) : remaining < -0.005 ? (
                  "overpaid"
                ) : (
                  "all paid ✓"
                )}
              </p>
              <div className="mt-2.5 flex gap-2">
                <Button variant="soft" size="sm" onClick={() => setPayOpen(true)}>
                  <Plus size={13} /> Add payment
                </Button>
                <Button
                  variant="surface"
                  size="sm"
                  onClick={() => ui.open({ type: "entryEdit", entryId: entry.id, ownerId: entry.ownerId })}
                >
                  <Pencil size={13} /> Edit
                </Button>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* ⋯ menu */}
      <BottomSheet open={menuOpen} onClose={() => setMenuOpen(false)} title={entry.note || entry.category || "Entry"}>
        <div className="space-y-1">
          {menuActions.map((a) => {
            const Icon = a.icon;
            return (
              <button
                key={a.key}
                type="button"
                onClick={a.onClick}
                className={cx(
                  "flex w-full items-center gap-3 rounded-2xl px-3 py-3 text-left transition hover:bg-surface2 active:scale-[0.98]",
                  a.danger && "text-neg"
                )}
              >
                <Icon size={17} className={a.danger ? "text-neg" : "text-brand"} aria-hidden />
                <span className="text-[13px] font-semibold">{a.label}</span>
              </button>
            );
          })}
        </div>
      </BottomSheet>

      {/* move to contact */}
      <ContactPicker
        open={pickerOpen}
        onClose={() => setPickerOpen(false)}
        onPick={(id) => {
          moveEntity("entry", entry.id, id as OwnerId);
          toastOk("Entry moved");
        }}
      />

      {/* share statement */}
      <BottomSheet open={shareOpen} onClose={() => setShareOpen(false)} title="Share statement">
        <div className="space-y-1">
          <button
            type="button"
            onClick={() => {
              if (!phone) {
                toastErr(contact ? "No phone number for this contact" : "General has no phone to send to");
                return;
              }
              openExternal(waLink(phone, statementShareText(ownerEntries, contact, settings.currency)));
            }}
            className={cx(
              "flex w-full items-center gap-3 rounded-2xl px-3 py-3 text-left transition hover:bg-surface2 active:scale-[0.98]",
              !phone && "opacity-40"
            )}
          >
            <MessageCircle size={17} className="text-brand" aria-hidden />
            <span className="min-w-0 flex-1">
              <span className="block text-[13px] font-semibold text-ink">WhatsApp</span>
              <span className="block text-[10.5px] text-mute">
                {phone ? `Send to ${ownerLabel}` : "No phone number available"}
              </span>
            </span>
          </button>
          <button
            type="button"
            onClick={() => {
              downloadBlob(
                statementToPdfBlob(ownerEntries, contact, settings.currency),
                `${ownerLabel.replace(/[\\/:*?"<>|]+/g, "-")}-statement.pdf`
              );
              toastOk("Statement PDF downloaded");
            }}
            className="flex w-full items-center gap-3 rounded-2xl px-3 py-3 text-left transition hover:bg-surface2 active:scale-[0.98]"
          >
            <FileText size={17} className="text-brand" aria-hidden />
            <span className="min-w-0 flex-1">
              <span className="block text-[13px] font-semibold text-ink">PDF statement</span>
              <span className="block text-[10.5px] text-mute">Download — entries with running balance</span>
            </span>
          </button>
          <button
            type="button"
            onClick={async () => {
              if (await copyText(statementShareText(ownerEntries, contact, settings.currency))) {
                toastOk("Statement copied");
              }
            }}
            className="flex w-full items-center gap-3 rounded-2xl px-3 py-3 text-left transition hover:bg-surface2 active:scale-[0.98]"
          >
            <Copy size={17} className="text-brand" aria-hidden />
            <span className="min-w-0 flex-1">
              <span className="block text-[13px] font-semibold text-ink">Copy text</span>
              <span className="block text-[10.5px] text-mute">Paste it anywhere</span>
            </span>
          </button>
          <button
            type="button"
            onClick={() => {
              const csv = entriesToCSV(ownerEntries, useApp.getState().contacts);
              downloadBlob(
                new Blob([csv], { type: "text/csv;charset=utf-8" }),
                `${ownerLabel.replace(/[\\/:*?"<>|]+/g, "-")}-ledger.csv`
              );
              toastOk("CSV exported");
            }}
            className="flex w-full items-center gap-3 rounded-2xl px-3 py-3 text-left transition hover:bg-surface2 active:scale-[0.98]"
          >
            <FileDown size={17} className="text-brand" aria-hidden />
            <span className="min-w-0 flex-1">
              <span className="block text-[13px] font-semibold text-ink">Export CSV</span>
              <span className="block text-[10.5px] text-mute">Spreadsheet-friendly</span>
            </span>
          </button>
        </div>
      </BottomSheet>

      {/* add payment */}
      <AddPaymentSheet
        open={payOpen}
        onClose={() => setPayOpen(false)}
        remaining={Math.max(remaining, 0)}
        onSubmit={(p) => {
          addPayment(entry.id, p);
          toastOk("Payment added");
          setPayOpen(false);
        }}
      />
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* LedgerSection                                                        */
/* ------------------------------------------------------------------ */

export function LedgerSection({ ownerId }: { ownerId: OwnerId }) {
  const ui = useUI();
  const confirm = useConfirm();
  const entries = useApp((s) => s.entries);
  const lists = useApp((s) => s.lists);
  const contacts = useApp((s) => s.contacts);
  const settleUp = useApp((s) => s.settleUp);

  const [filter, setFilter] = useState<LedgerFilter>("all");

  const isGeneral = ownerId === "general";
  const contact = useMemo(
    () => (ownerId === "general" ? null : contacts.find((c) => c.id === ownerId && !c.deletedAt) ?? null),
    [contacts, ownerId]
  );
  const ownerName = contact ? displayName(contact) : "General";

  const totals = useMemo(() => totalsForOwner(entries, ownerId), [entries, ownerId]);
  const listsPending = useMemo(() => openListsPending(lists, ownerId), [lists, ownerId]);

  /** date desc rows with running balance computed oldest → newest */
  const rows = useMemo(() => {
    const mine = entries.filter((e) => e.ownerId === ownerId && !e.deletedAt);
    const asc = [...mine].sort((a, b) => a.date.localeCompare(b.date) || a.createdAt - b.createdAt);
    const running = new Map<ID, number>();
    let run = 0;
    for (const e of asc) {
      run = r2(run + entrySignedRemaining(e));
      running.set(e.id, run);
    }
    return asc.reverse().map((e) => ({ entry: e, running: running.get(e.id) ?? 0 }));
  }, [entries, ownerId]);

  const filtered = useMemo(() => {
    if (filter === "all") return rows;
    return rows.filter(({ entry }) => {
      const rem = entryRemaining(entry);
      if (filter === "credit") return entry.type === "credit";
      if (filter === "debit") return entry.type === "debit";
      if (filter === "unpaid") return rem > 0.005;
      return rem > 0.005 && !!entry.dueDate && dueStatus(dueMs(entry.dueDate)) === "overdue";
    });
  }, [rows, filter]);

  const filterLabels: Record<LedgerFilter, string> = {
    all: "All",
    credit: isGeneral ? "Income" : "Credit",
    debit: isGeneral ? "Expense" : "Debit",
    unpaid: "Unpaid",
    overdue: "Overdue",
  };

  const openEntry = (entryType: "credit" | "debit") =>
    ui.open({ type: "entryEdit", ownerId, entryType });

  const onSettleUp = async () => {
    const ok = await confirm({
      title: "Settle up?",
      message: isGeneral
        ? "Adds payments dated today that bring the balance to zero."
        : `Adds payments dated today that bring the balance with ${ownerName} to zero.`,
      confirmLabel: "Settle up",
      tone: "brand",
    });
    if (!ok) return;
    const applied = settleUp(ownerId);
    toastOk(applied > 0 ? "Settled ✓" : "Already settled up");
  };

  return (
    <section aria-label={`Ledger for ${ownerName}`}>
      <SectionLabel
        right={
          <span className="text-[10px] font-semibold text-mute">
            {rows.length} entr{rows.length !== 1 ? "ies" : "y"}
          </span>
        }
      >
        {isGeneral ? "Income & Expense" : "Ledger"}
      </SectionLabel>

      {/* summary strip */}
      <div className="grid grid-cols-3 gap-2">
        <StatCard label={isGeneral ? "Income" : "They owe you"} value={totals.credit} tone="pos" />
        <StatCard label={isGeneral ? "Expense" : "You owe them"} value={totals.debit} tone="neg" />
        <StatCard
          label="Net"
          value={totals.net}
          tone={totals.net > 0.005 ? "pos" : totals.net < -0.005 ? "neg" : "mute"}
          signed
        />
      </div>

      {listsPending !== 0 && (
        <p className="mt-2 flex items-center gap-1.5 rounded-2xl bg-amber/10 px-3 py-2 text-[11px] font-semibold text-amber">
          <CalendarClock size={13} className="shrink-0" aria-hidden />
          <span>
            Open calculating lists: +<Money value={listsPending} /> (counted in balance)
          </span>
        </p>
      )}

      {/* actions */}
      <div className="mt-3 flex flex-wrap gap-2">
        <Button variant="surface" size="sm" onClick={() => void onSettleUp()}>
          <Scale size={13} /> Settle up
        </Button>
        <Button variant="soft" size="sm" className="text-pos" onClick={() => openEntry("credit")}>
          <ArrowDownLeft size={13} /> {isGeneral ? "+ Income" : "+ Credit"}
        </Button>
        <Button variant="surface" size="sm" className="text-neg" onClick={() => openEntry("debit")}>
          <ArrowUpRight size={13} /> {isGeneral ? "+ Expense" : "+ Debit"}
        </Button>
      </div>

      {/* filters */}
      <div className="app-scroll mt-3 flex gap-1.5 overflow-x-auto pb-1" role="group" aria-label="Ledger filters">
        {(Object.keys(filterLabels) as LedgerFilter[]).map((f) => (
          <Chip key={f} active={filter === f} onClick={() => setFilter(f)}>
            {filterLabels[f]}
          </Chip>
        ))}
      </div>

      {/* entries */}
      <div className="mt-3 space-y-2.5">
        {filtered.map(({ entry, running }) => (
          <EntryCard
            key={entry.id}
            entry={entry}
            running={running}
            isGeneral={isGeneral}
            ownerId={ownerId}
            contact={contact}
          />
        ))}

        {!filtered.length && rows.length > 0 && (
          <EmptyState
            icon={<Banknote size={20} />}
            title="Nothing matches this filter"
            hint="Try another filter to see more entries."
            action={
              <Button variant="soft" size="sm" onClick={() => setFilter("all")}>
                Show all
              </Button>
            }
          />
        )}

        {!rows.length && (
          <EmptyState
            icon={<Banknote size={22} />}
            title={isGeneral ? "No income or expenses yet" : "No ledger entries yet"}
            hint={
              isGeneral
                ? "Track money in and out — everything stays on this device."
                : `Track what you and ${ownerName} owe each other. Settle up anytime.`
            }
            action={
              <div className="flex gap-2">
                <Button variant="soft" size="sm" className="text-pos" onClick={() => openEntry("credit")}>
                  <Plus size={13} /> {isGeneral ? "Income" : "Credit"}
                </Button>
                <Button variant="surface" size="sm" className="text-neg" onClick={() => openEntry("debit")}>
                  <Plus size={13} /> {isGeneral ? "Expense" : "Debit"}
                </Button>
              </div>
            }
          />
        )}
      </div>
    </section>
  );
}
