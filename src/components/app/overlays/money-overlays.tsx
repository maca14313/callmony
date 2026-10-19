"use client";

import { useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import {
  ArrowDownLeft,
  ArrowUpRight,
  Bell,
  Bookmark,
  Calculator,
  CalendarClock,
  Check,
  ChevronDown,
  ChevronRight,
  CircleCheckBig,
  CircleDollarSign,
  Copy,
  Ellipsis,
  FileText,
  HandCoins,
  Inbox,
  List,
  ListChecks,
  ListPlus,
  MessageCircle,
  Paperclip,
  Pencil,
  Plus,
  Receipt,
  Repeat as RepeatIcon,
  RotateCcw,
  Share2,
  SquareCheckBig,
  Trash2,
  X,
} from "lucide-react";
import { useApp } from "@/lib/store";
import { useUI } from "@/lib/ui-store";
import type { ReactNode } from "react";
import {
  dueStatus,
  groupItems,
  itemPaid,
  itemRemaining,
  lineTotal,
  listTotals,
  r2,
} from "@/lib/calc";
import { copyText, displayName, downloadBlob, fd, fdShort, money, todayStr } from "@/lib/utils";
import { openExternal, primaryPhone, waLink } from "@/lib/share";
import { listShareText, listToPdfBlob } from "@/lib/pdf";
import {
  ENTRY_CATEGORIES,
  PAYMENT_METHODS,
  type AttachmentMeta,
  type CalcList,
  type ID,
  type LedgerEntry,
  type ListDiscount,
  type ListKind,
  type ListItem,
  type OwnerId,
  type Payment,
  type Repeat,
} from "@/lib/types";
import { AttachButton, AttachmentStrip } from "../kit/Attachments";
import { CalcButton } from "../kit/Calculator";
import { ContactPicker } from "../kit/ContactPicker";
import { useConfirm } from "../kit/Confirm";
import { BottomSheet, Sheet } from "../kit/Sheet";
import { SwipeRow } from "../kit/SwipeRow";
import { toastErr, toastOk, toastUndoLast } from "../kit/toast";
import {
  Avatar,
  Button,
  EmptyState,
  Field,
  IconButton,
  Money,
  ProgressBar,
  Segmented,
  cx,
  inputCls,
} from "../kit/primitives";

/* ------------------------------------------------------------------ */
/* shared bits                                                          */
/* ------------------------------------------------------------------ */

const KIND_META: Record<ListKind, { label: string; icon: typeof List; hint: string }> = {
  calculating: { label: "Calculating", icon: Calculator, hint: "Items with qty, price, payments & totals" },
  checklist: { label: "Checklist", icon: ListChecks, hint: "Tick things off — see done progress" },
  todo: { label: "To-do", icon: SquareCheckBig, hint: "Tasks with priority & due dates" },
  simple: { label: "Simple", icon: List, hint: "A plain list of things" },
};

const REPEAT_LABEL: Record<Repeat, string> = {
  none: "No repeat",
  daily: "Daily",
  weekly: "Weekly",
  monthly: "Monthly",
  yearly: "Yearly",
};

const PRIORITY_META: Record<NonNullable<ListItem["priority"]>, { label: string; cls: string }> = {
  low: { label: "Low", cls: "bg-surface2 text-mute" },
  medium: { label: "Medium", cls: "bg-amber/10 text-amber" },
  high: { label: "High", cls: "bg-negsoft text-neg" },
};

const dueMs = (d: string) => new Date(`${d}T23:59:59`).getTime();

const parseNum = (s: string): number => {
  const n = parseFloat(String(s).replace(/,/g, ""));
  return isNaN(n) ? 0 : n;
};

const safeName = (s: string) => s.replace(/[\\/:*?"<>|]+/g, "-");

/** Token-styled toggle switch. */
function Switch({
  checked,
  onChange,
  label,
  hint,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  label: string;
  hint?: string;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className="flex w-full items-center justify-between gap-3 py-1.5 text-left transition active:scale-[0.99]"
    >
      <span className="min-w-0">
        <span className="block text-[13px] font-semibold text-ink">{label}</span>
        {hint && <span className="mt-0.5 block text-[10.5px] leading-4 text-mute">{hint}</span>}
      </span>
      <span
        className={cx(
          "relative h-7 w-12 shrink-0 rounded-full transition-colors",
          checked ? "bg-brand" : "bg-line2"
        )}
        aria-hidden
      >
        <span
          className={cx(
            "absolute top-1 h-5 w-5 rounded-full bg-white shadow-key transition-all duration-200",
            checked ? "left-6" : "left-1"
          )}
        />
      </span>
    </button>
  );
}

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

/** 44px touch target checkbox with a compact visual box. */
function TickCheckbox({
  checked,
  onChange,
  name,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  name: string;
}) {
  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={checked}
      aria-label={`Mark ${name} as paid`}
      onClick={() => onChange(!checked)}
      className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl transition active:scale-90"
    >
      <span
        className={cx(
          "flex h-6 w-6 items-center justify-center rounded-[8px] border-2 transition",
          checked ? "border-brand bg-brand text-white" : "border-line2 bg-surface text-transparent"
        )}
        aria-hidden
      >
        <Check size={14} strokeWidth={3} />
      </span>
    </button>
  );
}

/** Numeric input with local draft state — commits on blur, CalcButton optional. */
function NumField({
  value,
  onCommit,
  ariaLabel,
  placeholder,
  calc,
  className,
}: {
  value: number;
  onCommit: (n: number) => void;
  ariaLabel: string;
  placeholder?: string;
  calc?: boolean;
  className?: string;
}) {
  const [text, setText] = useState(value ? String(value) : "");
  return (
    <div className="flex w-full items-center gap-2">
      <input
        value={text}
        inputMode="decimal"
        placeholder={placeholder || "0"}
        aria-label={ariaLabel}
        onChange={(e) => setText(e.target.value)}
        onBlur={() => onCommit(parseNum(text))}
        className={cx(inputCls, "py-2.5 text-[13px] font-semibold tabular-nums", className)}
      />
      {calc && (
        <CalcButton
          onPick={(v) => {
            setText(v ? String(v) : "");
            onCommit(r2(v));
          }}
        />
      )}
    </div>
  );
}

/** Bottom-sheet list action row. */
function ActionRow({
  icon: Icon,
  label,
  sub,
  danger,
  onClick,
}: {
  icon: typeof Bell;
  label: string;
  sub?: string;
  danger?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cx(
        "flex w-full items-center gap-3 rounded-2xl px-3 py-3 text-left transition active:scale-[0.98]",
        danger ? "text-neg hover:bg-negsoft/50" : "hover:bg-surface2"
      )}
    >
      <Icon size={17} className={danger ? "text-neg" : "text-brand"} aria-hidden />
      <span className="min-w-0 flex-1">
        <span className="block text-[13px] font-semibold text-ink">{label}</span>
        {sub && <span className="block text-[10.5px] text-mute">{sub}</span>}
      </span>
    </button>
  );
}

/** Footer quick action (44px target). */
function FooterAction({
  icon,
  label,
  onClick,
}: {
  icon: ReactNode;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      className="flex h-11 flex-col items-center justify-center gap-0.5 rounded-2xl border border-line bg-surface text-[9px] font-bold text-mute shadow-key transition hover:bg-surface2 active:scale-95"
    >
      <span className="text-brand">{icon}</span>
      {label}
    </button>
  );
}

/** Add-payment bottom sheet — shared by entry + item payment histories. */
function AddPaymentSheet({
  open,
  onClose,
  remaining,
  currency,
  onSubmit,
}: {
  open: boolean;
  onClose: () => void;
  remaining: number;
  currency: string;
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
          <HandCoins size={12} aria-hidden /> Full remaining: <Money value={remaining} currency={currency} />
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
/* Item payment history (numbered rows, progress, add)                  */
/* ------------------------------------------------------------------ */

function ItemPayments({ listId, item, currency }: { listId: ID; item: ListItem; currency: string }) {
  const confirm = useConfirm();
  const addItemPayment = useApp((s) => s.addItemPayment);
  const removeItemPayment = useApp((s) => s.removeItemPayment);
  const [payOpen, setPayOpen] = useState(false);

  const lt = lineTotal(item);
  const paid = itemPaid(item);
  const rem = itemRemaining(item);
  const n = item.payments.length;

  const remove = async (p: Payment, idx: number) => {
    const ok = await confirm({
      title: `Remove payment ${idx + 1}?`,
      message: `Payment of ${money(p.amount, currency)} on ${fd(p.date)} will be removed.`,
      confirmLabel: "Remove",
      tone: "danger",
    });
    if (!ok) return;
    removeItemPayment(listId, item.id, p.id);
    toastOk("Payment removed");
  };

  return (
    <div className="rounded-2xl bg-surface2 p-3">
      <p className="mb-2 text-[10px] font-bold uppercase tracking-[1.5px] text-mute">Payments</p>
      {n === 0 && <p className="pb-1 text-[11px] text-mute">No payments yet</p>}
      <ol className="space-y-1">
        {item.payments.map((p, i) => (
          <li key={p.id} className="flex items-center gap-2 rounded-xl bg-surface px-2.5 py-1.5 shadow-key">
            <span className="w-3.5 shrink-0 text-[10.5px] font-bold tabular-nums text-mute">{i + 1}.</span>
            <span className="shrink-0 text-[11px] text-mute">{fdShort(p.date)}</span>
            <span className="shrink-0 text-[12px] font-bold text-ink">
              <Money value={p.amount} currency={currency} />
            </span>
            {(p.note || p.auto) && (
              <span className="min-w-0 flex-1 truncate text-[10px] text-mute">
                {p.auto === "tick" ? "Ticked" : p.auto === "settle" ? "Settle up" : p.note}
              </span>
            )}
            <IconButton
              label={`Remove payment ${i + 1}`}
              className="h-9 w-9 shrink-0"
              onClick={() => void remove(p, i)}
            >
              <X size={13} />
            </IconButton>
          </li>
        ))}
      </ol>
      {lt > 0.005 && n > 0 && <ProgressBar className="mt-2" value={(paid / lt) * 100} />}
      <p className="mt-1.5 text-[10.5px] text-mute">
        {n} payment{n !== 1 ? "s" : ""} ·{" "}
        {rem > 0.005 ? (
          <>
            <Money value={rem} currency={currency} /> left
          </>
        ) : rem < -0.005 ? (
          "overpaid"
        ) : (
          "all paid ✓"
        )}
      </p>
      <Button size="sm" variant="soft" className="mt-2 w-full" onClick={() => setPayOpen(true)}>
        <Plus size={13} /> Add payment
      </Button>
      <AddPaymentSheet
        open={payOpen}
        onClose={() => setPayOpen(false)}
        remaining={Math.max(rem, 0)}
        currency={currency}
        onSubmit={(p) => {
          addItemPayment(listId, item.id, p);
          toastOk("Payment added");
          setPayOpen(false);
        }}
      />
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Per-row discount editor                                              */
/* ------------------------------------------------------------------ */

function ItemDiscountField({ listId, item }: { listId: ID; item: ListItem }) {
  const updateListItem = useApp((s) => s.updateListItem);
  const [type, setType] = useState<ListDiscount["type"]>(item.discount?.type ?? "pct");

  const commit = (t: ListDiscount["type"], v: number) => {
    updateListItem(listId, item.id, v > 0 ? { discount: { type: t, value: r2(v) } } : { discount: undefined });
  };

  return (
    <div className="flex w-full items-center gap-2">
      <Segmented
        className="w-[116px] shrink-0"
        options={[
          { value: "pct", label: "%" },
          { value: "amount", label: "Amt" },
        ]}
        value={type}
        onChange={(t) => {
          setType(t);
          commit(t, item.discount?.value ?? 0);
        }}
      />
      <NumField
        value={item.discount?.value ?? 0}
        ariaLabel="Row discount value"
        onCommit={(v) => commit(type, v)}
      />
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Calculating item row                                                 */
/* ------------------------------------------------------------------ */

function CalcItemRow({
  listId,
  item,
  expanded,
  onToggleExpand,
  currency,
}: {
  listId: ID;
  item: ListItem;
  expanded: boolean;
  onToggleExpand: () => void;
  currency: string;
}) {
  const confirm = useConfirm();
  const toggleItemChecked = useApp((s) => s.toggleItemChecked);
  const updateListItem = useApp((s) => s.updateListItem);
  const removeListItem = useApp((s) => s.removeListItem);

  const lt = lineTotal(item);
  const paid = itemPaid(item);
  const rem = itemRemaining(item);
  const tickCount = item.payments.filter((p) => p.auto === "tick").length;

  const onTick = async (checked: boolean) => {
    if (!checked && tickCount > 0) {
      const ok = await confirm({
        title: "Remove tick payments?",
        message: `Ticking added ${tickCount} payment${tickCount !== 1 ? "s" : ""}. Unticking will remove them.`,
        confirmLabel: "Remove",
        tone: "danger",
      });
      if (!ok) return; // checkbox stays checked — it is store-controlled
    }
    toggleItemChecked(listId, item.id, checked);
  };

  const removeItem = async () => {
    const ok = await confirm({
      title: `Remove “${item.name}”?`,
      message: "The row and its payment history are removed from this list.",
      confirmLabel: "Remove",
      tone: "danger",
    });
    if (!ok) return;
    removeListItem(listId, item.id);
    toastOk("Item removed");
  };

  return (
    <div className="rounded-2xl border border-line bg-surface shadow-key">
      {/* collapsed */}
      <div className="flex items-center gap-1 p-2.5 pl-1.5">
        <TickCheckbox checked={item.checked} onChange={(v) => void onTick(v)} name={item.name} />
        <button
          type="button"
          onClick={onToggleExpand}
          aria-expanded={expanded}
          aria-label={`${item.name}. Tap to toggle row details.`}
          className="min-w-0 flex-1 py-1 text-left"
        >
          <span className="flex items-baseline justify-between gap-2">
            <span
              className={cx(
                "truncate text-[12.5px] font-semibold text-ink",
                item.checked && "line-through decoration-line2"
              )}
            >
              {item.name}
            </span>
            <span className="shrink-0 text-[12.5px] font-bold text-ink">
              <Money value={lt} currency={currency} />
            </span>
          </span>
          <span className="mt-0.5 flex items-baseline justify-between gap-2">
            <span className="truncate text-[10.5px] tabular-nums text-mute">
              {item.qty}
              {item.unit ? ` ${item.unit}` : ""} × {item.unitPrice.toLocaleString("en-US")}
              {item.date ? ` · ${fdShort(item.date)}` : ""}
            </span>
            {paid > 0.005 && rem > 0.005 && (
              <span className="shrink-0 text-[10px] font-bold text-amber">
                Paid <Money value={paid} currency={currency} /> / <Money value={lt} currency={currency} />
              </span>
            )}
          </span>
          {paid > 0.005 && rem > 0.005 && (
            <span className="mt-1.5 block">
              <ProgressBar value={lt > 0.005 ? (paid / lt) * 100 : 0} />
            </span>
          )}
        </button>
        <button
          type="button"
          onClick={onToggleExpand}
          aria-label="Toggle item details"
          className="flex h-11 w-9 shrink-0 items-center justify-center rounded-xl text-mute transition hover:bg-surface2"
        >
          <ChevronDown size={16} className={cx("transition-transform", expanded && "rotate-180")} />
        </button>
      </div>

      {/* badges */}
      {(rem <= 0.005 && lt > 0.005) || item.dueDate ? (
        <div className="flex flex-wrap items-center gap-1.5 px-3 pb-2.5 pl-[54px]">
          {rem <= 0.005 && lt > 0.005 && (
            <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-brandsoft px-2 py-0.5 text-[10px] font-bold text-pos">
              Paid ✓
            </span>
          )}
          {rem < -0.005 && (
            <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-negsoft px-2 py-0.5 text-[10px] font-bold text-neg">
              Overpaid by <Money value={-rem} currency={currency} />
            </span>
          )}
          {item.dueDate && <DueBadge dueDate={item.dueDate} />}
        </div>
      ) : null}

      {/* expanded panel */}
      <AnimatePresence initial={false}>
        {expanded && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.18, ease: "easeOut" }}
            className="overflow-hidden"
          >
            <div className="space-y-3 border-t border-line px-3 py-3">
              <div className="grid grid-cols-2 gap-2">
                <Field label="Name" className="col-span-2">
                  <input
                    value={item.name}
                    onChange={(e) => updateListItem(listId, item.id, { name: e.target.value })}
                    aria-label="Item name"
                    className={cx(inputCls, "py-2.5 text-[13px]")}
                  />
                </Field>
                <Field label="Row date">
                  <input
                    type="date"
                    value={item.date ?? ""}
                    onChange={(e) => updateListItem(listId, item.id, { date: e.target.value || undefined })}
                    aria-label="Row date"
                    className={cx(inputCls, "py-2.5 text-[13px]")}
                  />
                </Field>
                <Field label="Due date">
                  <input
                    type="date"
                    value={item.dueDate ?? ""}
                    onChange={(e) => updateListItem(listId, item.id, { dueDate: e.target.value || undefined })}
                    aria-label="Item due date"
                    className={cx(inputCls, "py-2.5 text-[13px]")}
                  />
                </Field>
                <Field label="Qty">
                  <NumField
                    value={item.qty}
                    ariaLabel="Quantity"
                    onCommit={(v) => updateListItem(listId, item.id, { qty: v })}
                  />
                </Field>
                <Field label="Unit">
                  <input
                    value={item.unit ?? ""}
                    onChange={(e) => updateListItem(listId, item.id, { unit: e.target.value || undefined })}
                    placeholder="pcs, kg…"
                    aria-label="Unit"
                    className={cx(inputCls, "py-2.5 text-[13px]")}
                  />
                </Field>
                <Field label="Unit price" className="col-span-2">
                  <NumField
                    calc
                    value={item.unitPrice}
                    ariaLabel="Unit price"
                    onCommit={(v) => updateListItem(listId, item.id, { unitPrice: r2(v) })}
                  />
                </Field>
                <Field label="Row discount" className="col-span-2">
                  <ItemDiscountField listId={listId} item={item} />
                </Field>
                <Field label="Tax %" className="col-span-2">
                  <NumField
                    value={item.taxPct ?? 0}
                    ariaLabel="Row tax percent"
                    onCommit={(v) => updateListItem(listId, item.id, { taxPct: v > 0 ? r2(v) : undefined })}
                  />
                </Field>
                <Field label="Note" className="col-span-2">
                  <input
                    value={item.note ?? ""}
                    onChange={(e) => updateListItem(listId, item.id, { note: e.target.value || undefined })}
                    placeholder="Optional note"
                    aria-label="Item note"
                    className={cx(inputCls, "py-2.5 text-[13px]")}
                  />
                </Field>
              </div>

              <ItemPayments listId={listId} item={item} currency={currency} />

              <Button
                variant="surface"
                size="sm"
                className="w-full text-neg"
                onClick={() => void removeItem()}
              >
                <Trash2 size={13} /> Remove item
              </Button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Plain (non-calculating) items — checklist / todo / simple            */
/* ------------------------------------------------------------------ */

function PlainItems({ list }: { list: CalcList }) {
  const confirm = useConfirm();
  const toggleItemChecked = useApp((s) => s.toggleItemChecked);
  const removeListItem = useApp((s) => s.removeListItem);

  const done = list.items.filter((i) => i.checked).length;
  const total = list.items.length;

  const onRemove = async (item: ListItem) => {
    const ok = await confirm({
      title: `Remove “${item.name}”?`,
      message: "The row is removed from this list.",
      confirmLabel: "Remove",
      tone: "danger",
    });
    if (!ok) return;
    removeListItem(list.id, item.id);
    toastOk("Item removed");
  };

  const onTick = async (item: ListItem, checked: boolean) => {
    const tickCount = item.payments.filter((p) => p.auto === "tick").length;
    if (!checked && tickCount > 0) {
      const ok = await confirm({
        title: "Remove tick payments?",
        message: `Ticking added ${tickCount} payment${tickCount !== 1 ? "s" : ""}. Unticking will remove them.`,
        confirmLabel: "Remove",
        tone: "danger",
      });
      if (!ok) return;
    }
    toggleItemChecked(list.id, item.id, checked);
  };

  return (
    <div className="space-y-2">
      {list.kind === "checklist" && total > 0 && (
        <div className="rounded-[22px] border border-line bg-surface p-4 shadow-key">
          <div className="flex items-center justify-between text-[11px] font-semibold text-mute">
            <span>
              {done} of {total} done
            </span>
            <span className="tabular-nums">{Math.round((done / total) * 100)}%</span>
          </div>
          <ProgressBar className="mt-2" value={(done / total) * 100} />
        </div>
      )}

      {list.items.map((item) => (
        <div key={item.id} onContextMenu={(e) => { e.preventDefault(); void onRemove(item); }}>
          <SwipeRow onLongPress={() => void onRemove(item)} className="rounded-2xl">
            <div className="flex items-center gap-1 rounded-2xl border border-line bg-surface p-2.5 pl-1.5 shadow-key">
              <TickCheckbox checked={item.checked} onChange={(v) => void onTick(item, v)} name={item.name} />
              <div className="min-w-0 flex-1 py-1">
                <p
                  className={cx(
                    "truncate text-[12.5px] font-semibold text-ink",
                    item.checked && "line-through decoration-line2"
                  )}
                >
                  {item.name}
                </p>
                {item.note && <p className="truncate text-[10.5px] text-mute">{item.note}</p>}
                {list.kind === "todo" && (item.priority || item.dueDate) && (
                  <div className="mt-1 flex flex-wrap items-center gap-1.5">
                    {item.priority && (
                      <span
                        className={cx(
                          "inline-flex shrink-0 items-center rounded-full px-2 py-0.5 text-[10px] font-bold",
                          PRIORITY_META[item.priority].cls
                        )}
                      >
                        {PRIORITY_META[item.priority].label}
                      </span>
                    )}
                    {item.dueDate && <DueBadge dueDate={item.dueDate} />}
                  </div>
                )}
              </div>
            </div>
          </SwipeRow>
        </div>
      ))}

      {!total && (
        <EmptyState
          icon={<ListPlus size={20} />}
          title="No items yet"
          hint="Add the first item below — long-press any row to remove it."
        />
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Add-item form                                                        */
/* ------------------------------------------------------------------ */

function AddItemForm({ list }: { list: CalcList }) {
  const addListItem = useApp((s) => s.addListItem);
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [qty, setQty] = useState("1");
  const [unit, setUnit] = useState("");
  const [price, setPrice] = useState("");
  const [date, setDate] = useState(todayStr());
  const [note, setNote] = useState("");
  const [priority, setPriority] = useState<NonNullable<ListItem["priority"]>>("medium");
  const [dueDate, setDueDate] = useState("");

  const isCalc = list.kind === "calculating";
  const isTodo = list.kind === "todo";

  const submit = () => {
    if (!name.trim()) {
      toastErr("Give the item a name");
      return;
    }
    addListItem(list.id, {
      name: name.trim(),
      ...(isCalc
        ? {
            qty: parseNum(qty) > 0 ? r2(parseNum(qty)) : 1,
            unit: unit.trim() || undefined,
            unitPrice: r2(parseNum(price)),
            date: date || todayStr(),
          }
        : {}),
      note: note.trim() || undefined,
      ...(isTodo ? { priority, dueDate: dueDate || undefined } : {}),
    });
    toastOk("Item added");
    setName("");
    setNote("");
    setPrice("");
    setQty("1");
    setUnit("");
    setDueDate("");
    setPriority("medium");
  };

  return (
    <div className="rounded-[22px] border border-line bg-surface shadow-key">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="flex w-full items-center justify-between px-4 py-3.5 text-left"
      >
        <span className="flex items-center gap-2 text-[13px] font-bold text-brand">
          <Plus size={16} aria-hidden /> Add item
        </span>
        <ChevronDown size={16} className={cx("text-mute transition-transform", open && "rotate-180")} aria-hidden />
      </button>
      <AnimatePresence initial={false}>
        {open && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.18, ease: "easeOut" }}
            className="overflow-hidden"
          >
            <div className="space-y-3 border-t border-line px-4 py-3.5">
              <Field label="Name">
                <input
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Item name"
                  aria-label="Item name"
                  className={inputCls}
                />
              </Field>
              {isCalc && (
                <>
                  <div className="grid grid-cols-[1fr_92px] gap-2">
                    <Field label="Qty">
                      <input
                        value={qty}
                        onChange={(e) => setQty(e.target.value)}
                        inputMode="decimal"
                        aria-label="Quantity"
                        className={inputCls}
                      />
                    </Field>
                    <Field label="Unit">
                      <input
                        value={unit}
                        onChange={(e) => setUnit(e.target.value)}
                        placeholder="kg"
                        aria-label="Unit"
                        className={inputCls}
                      />
                    </Field>
                  </div>
                  <Field label="Unit price">
                    <div className="flex w-full items-center gap-2">
                      <input
                        value={price}
                        onChange={(e) => setPrice(e.target.value)}
                        inputMode="decimal"
                        placeholder="0"
                        aria-label="Unit price"
                        className={inputCls}
                      />
                      <CalcButton onPick={(v) => setPrice(String(v))} />
                    </div>
                  </Field>
                  <Field label="Row date" hint="Same item can appear on different days at different prices">
                    <input
                      type="date"
                      value={date}
                      onChange={(e) => setDate(e.target.value)}
                      aria-label="Row date"
                      className={inputCls}
                    />
                  </Field>
                </>
              )}
              {isTodo && (
                <>
                  <Field label="Priority">
                    <Segmented
                      options={[
                        { value: "low", label: "Low" },
                        { value: "medium", label: "Medium" },
                        { value: "high", label: "High" },
                      ]}
                      value={priority}
                      onChange={setPriority}
                    />
                  </Field>
                  <Field label="Due date">
                    <input
                      type="date"
                      value={dueDate}
                      onChange={(e) => setDueDate(e.target.value)}
                      aria-label="Item due date"
                      className={inputCls}
                    />
                  </Field>
                </>
              )}
              <Field label="Note">
                <input
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  placeholder="Optional note"
                  aria-label="Item note"
                  className={inputCls}
                />
              </Field>
              <Button variant="brand" size="lg" className="w-full" onClick={submit}>
                <Plus size={15} /> Add item
              </Button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Totals & charges sheet                                               */
/* ------------------------------------------------------------------ */

function TotalsSheet({ open, onClose, list }: { open: boolean; onClose: () => void; list: CalcList }) {
  const saveList = useApp((s) => s.saveList);
  const settings = useApp((s) => s.settings);
  const [dType, setDType] = useState<ListDiscount["type"]>(list.discount?.type ?? "pct");
  const [dVal, setDVal] = useState(list.discount?.value ? String(list.discount.value) : "");
  const [tax, setTax] = useState(list.taxPct ? String(list.taxPct) : "");
  const [extra, setExtra] = useState(list.extraCharge ? String(list.extraCharge) : "");
  if (!open) return null;

  const dv = parseNum(dVal);
  const tv = parseNum(tax);
  const ev = parseNum(extra);
  const currency = list.currency || settings.currency;
  const preview = listTotals({
    ...list,
    discount: dv > 0 ? { type: dType, value: r2(dv) } : undefined,
    taxPct: tv > 0 ? r2(tv) : undefined,
    extraCharge: ev > 0 ? r2(ev) : undefined,
  });

  const apply = () => {
    saveList({
      id: list.id,
      ownerId: list.ownerId,
      title: list.title,
      // zero-value discount object ≙ no discount (saveList keeps undefined as-is)
      discount: dv > 0 ? { type: dType, value: r2(dv) } : { type: dType, value: 0 },
      taxPct: tv > 0 ? r2(tv) : 0,
      extraCharge: ev > 0 ? r2(ev) : 0,
    });
    toastOk("Totals updated");
    onClose();
  };

  return (
    <BottomSheet open={open} onClose={onClose} title="Totals & charges">
      <Field label="List discount">
        <div className="flex w-full items-center gap-2">
          <Segmented
            className="w-[116px] shrink-0"
            options={[
              { value: "pct", label: "%" },
              { value: "amount", label: "Amt" },
            ]}
            value={dType}
            onChange={setDType}
          />
          <input
            value={dVal}
            onChange={(e) => setDVal(e.target.value)}
            inputMode="decimal"
            placeholder="0"
            aria-label="Discount value"
            className={inputCls}
          />
          <CalcButton onPick={(v) => setDVal(String(v))} />
        </div>
      </Field>
      <div className="mt-3 grid grid-cols-2 gap-2">
        <Field label="Tax %">
          <input
            value={tax}
            onChange={(e) => setTax(e.target.value)}
            inputMode="decimal"
            placeholder="0"
            aria-label="Tax percent"
            className={inputCls}
          />
        </Field>
        <Field label="Extra charge">
          <input
            value={extra}
            onChange={(e) => setExtra(e.target.value)}
            inputMode="decimal"
            placeholder="0"
            aria-label="Extra charge"
            className={inputCls}
          />
        </Field>
      </div>

      {/* live preview */}
      <div className="mt-4 rounded-2xl bg-surface2 p-3.5">
        <p className="mb-2 text-[10px] font-bold uppercase tracking-[1.5px] text-mute">Preview</p>
        <div className="space-y-1.5 text-[11.5px] text-mute">
          <div className="flex justify-between">
            <span>Subtotal</span>
            <span className="font-semibold text-ink">
              <Money value={preview.subtotal} currency={currency} />
            </span>
          </div>
          {preview.listDiscount > 0 && (
            <div className="flex justify-between">
              <span>Discount</span>
              <span className="font-semibold text-pos">
                − <Money value={preview.listDiscount} currency={currency} />
              </span>
            </div>
          )}
          {preview.listTax > 0 && (
            <div className="flex justify-between">
              <span>Tax</span>
              <span className="font-semibold text-ink">
                + <Money value={preview.listTax} currency={currency} />
              </span>
            </div>
          )}
          {preview.extra > 0 && (
            <div className="flex justify-between">
              <span>Extra</span>
              <span className="font-semibold text-ink">
                + <Money value={preview.extra} currency={currency} />
              </span>
            </div>
          )}
          <div className="flex justify-between border-t border-line pt-1.5 text-[12.5px] font-bold text-ink">
            <span>Total</span>
            <span>
              <Money value={preview.grand} currency={currency} />
            </span>
          </div>
          <div className="flex justify-between text-[10.5px]">
            <span>Paid</span>
            <span className="text-pos">
              <Money value={preview.paid} currency={currency} />
            </span>
          </div>
          <div className="flex justify-between text-[10.5px] font-bold">
            <span>Pending</span>
            <span className={preview.pending > 0.005 ? "text-neg" : "text-pos"}>
              <Money value={preview.pending} currency={currency} />
            </span>
          </div>
        </div>
      </div>

      <Button variant="brand" size="lg" className="mt-4 w-full" onClick={apply}>
        <Check size={15} /> Apply
      </Button>
    </BottomSheet>
  );
}

/* ================================================================== */
/* ListDetail                                                           */
/* ================================================================== */

export function ListDetail({ listId }: { listId: ID }) {
  const ui = useUI();
  const confirm = useConfirm();
  const list = useApp((s) => s.lists.find((l) => l.id === listId && !l.deletedAt));
  const contacts = useApp((s) => s.contacts);
  const settings = useApp((s) => s.settings);

  const saveList = useApp((s) => s.saveList);
  const deleteList = useApp((s) => s.deleteList);
  const duplicateList = useApp((s) => s.duplicateList);
  const moveEntity = useApp((s) => s.moveEntity);
  const markAllPaid = useApp((s) => s.markAllPaid);
  const clearPaid = useApp((s) => s.clearPaid);
  const saveReminder = useApp((s) => s.saveReminder);
  const logMessage = useApp((s) => s.logMessage);

  const [menuOpen, setMenuOpen] = useState(false);
  const [shareOpen, setShareOpen] = useState(false);
  const [totalsOpen, setTotalsOpen] = useState(false);
  const [ownerPickerOpen, setOwnerPickerOpen] = useState(false);
  const [groupOn, setGroupOn] = useState(false);
  const [openGroupId, setOpenGroupId] = useState<ID | null>(null);
  const [expandedItemId, setExpandedItemId] = useState<ID | null>(null);

  if (!list) {
    return (
      <Sheet title="List" onBack={() => ui.pop()}>
        <div className="px-4 py-6">
          <EmptyState icon={<List size={22} />} title="List not found" hint="It may have been deleted." />
        </div>
      </Sheet>
    );
  }

  const isCalc = list.kind === "calculating";
  const totals = listTotals(list);
  const ownerContact = list.ownerId === "general" ? null : contacts.find((c) => c.id === list.ownerId) ?? null;
  const ownerName = ownerContact ? displayName(ownerContact) : "General";
  const phone = primaryPhone(ownerContact);
  const currency = list.currency || settings.currency;
  const KindIcon = KIND_META[list.kind].icon;

  const shareText = () => listShareText(list, ownerContact, currency);

  const onWhatsApp = () => {
    if (!phone) {
      toastErr(ownerContact ? "No phone number for this contact" : "General has no phone to send to");
      return;
    }
    openExternal(waLink(phone, shareText()));
    logMessage(list.ownerId, "whatsapp", shareText());
  };
  const onPdf = () => {
    downloadBlob(listToPdfBlob(list, ownerContact, currency), `${safeName(list.title)}.pdf`);
    toastOk("PDF downloaded");
  };
  const onCopy = async () => {
    if (await copyText(shareText())) toastOk("Copied to clipboard");
  };
  const onReminder = () => {
    const d = new Date();
    d.setDate(d.getDate() + 1);
    d.setHours(9, 0, 0, 0);
    saveReminder({
      ownerId: list.ownerId,
      title: `Pending amount of ${list.title}`,
      kind: "payment",
      dueAt: d.getTime(),
      links: [{ type: "list", id: list.id, label: list.title }],
    });
    toastOk("Reminder set for tomorrow 9:00");
  };

  const dupNextPeriod = () => {
    const nid = duplicateList(list.id, { fresh: true });
    if (nid) {
      ui.open({ type: "list", listId: nid });
      toastOk("Duplicated for next period");
    }
  };

  const onClearPayments = async () => {
    const ok = await confirm({
      title: "Clear all payments?",
      message: "Every payment row on every item is removed and ticks are reset.",
      confirmLabel: "Clear",
      tone: "danger",
    });
    if (!ok) return;
    setMenuOpen(false);
    clearPaid(list.id);
    toastOk("Payments cleared");
  };

  const onDelete = async () => {
    const ok = await confirm({
      title: `Delete “${list.title}”?`,
      message: "The list and its items move to Trash — you can undo from the toast.",
      confirmLabel: "Delete",
      tone: "danger",
    });
    if (!ok) return;
    setMenuOpen(false);
    deleteList(list.id);
    toastUndoLast();
    ui.pop();
  };

  const toggleExpand = (id: ID) => setExpandedItemId((cur) => (cur === id ? null : id));

  const footer = (
    <div>
      {isCalc && (
        <div className="flex items-baseline justify-between gap-2 px-0.5 pb-2">
          <span className="text-[10px] font-bold uppercase tracking-[1px] text-mute">
            Total{" "}
            <span className="text-[13px] font-bold normal-case tracking-normal text-ink">
              <Money value={totals.grand} currency={currency} />
            </span>
          </span>
          <span className="text-[10px] font-bold uppercase tracking-[1px] text-mute">
            Paid{" "}
            <span className="text-[13px] font-bold normal-case tracking-normal text-pos">
              <Money value={totals.paid} currency={currency} />
            </span>
          </span>
          <span
            className={cx(
              "text-[13px] font-bold",
              totals.pending > 0.005 ? "text-neg" : "text-pos"
            )}
          >
            {totals.pending > 0.005 ? (
              <>
                Pending <Money value={totals.pending} currency={currency} />
              </>
            ) : (
              "All settled ✓"
            )}
          </span>
        </div>
      )}
      <div className="grid grid-cols-5 gap-1.5">
        <FooterAction icon={<MessageCircle size={17} />} label="WhatsApp" onClick={onWhatsApp} />
        <FooterAction icon={<FileText size={17} />} label="PDF" onClick={onPdf} />
        <FooterAction icon={<Copy size={17} />} label="Copy" onClick={() => void onCopy()} />
        <FooterAction icon={<Bell size={17} />} label="Remind" onClick={onReminder} />
        <FooterAction icon={<Ellipsis size={17} />} label="More" onClick={() => setMenuOpen(true)} />
      </div>
    </div>
  );

  return (
    <Sheet
      title={list.title}
      subtitle={`${KIND_META[list.kind].label} · ${ownerName}`}
      onBack={() => ui.pop()}
      actions={
        <IconButton label="List actions" onClick={() => setMenuOpen(true)}>
          <Ellipsis size={17} />
        </IconButton>
      }
      footer={footer}
    >
      <div className="space-y-3 px-4 py-4 pb-8">
        {/* sub-header card */}
        <section className="rounded-[22px] border border-line bg-surface p-4 shadow-key" aria-label="List details">
          <div className="flex flex-wrap items-center gap-1.5">
            <button
              type="button"
              onClick={() => setOwnerPickerOpen(true)}
              aria-label={`Owner: ${ownerName}. Tap to change.`}
              className="flex items-center gap-1.5 rounded-full border border-line bg-surface2 py-1 pl-1.5 pr-2.5 text-[11px] font-semibold text-ink transition hover:bg-brandsoft active:scale-95"
            >
              {ownerContact ? (
                <Avatar contact={ownerContact} size="xs" className="h-6 w-6 rounded-lg text-[9px]" />
              ) : (
                <Inbox size={12} className="text-mute" aria-hidden />
              )}
              {ownerName}
              <ChevronRight size={12} className="text-mute" aria-hidden />
            </button>
            <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-brandsoft px-2.5 py-1 text-[10px] font-bold text-brand">
              <KindIcon size={11} aria-hidden />
              {KIND_META[list.kind].label}
            </span>
            {list.repeat !== "none" && (
              <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-surface2 px-2.5 py-1 text-[10px] font-bold text-mute">
                <RepeatIcon size={11} aria-hidden /> {REPEAT_LABEL[list.repeat]}
              </span>
            )}
            {isCalc && list.postToLedger && (
              <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-amber/10 px-2.5 py-1 text-[10px] font-bold text-amber">
                <CircleDollarSign size={11} aria-hidden /> counts in balance
              </span>
            )}
          </div>

          {list.note && <p className="mt-2.5 text-[11.5px] leading-5 text-mute">{list.note}</p>}

          {isCalc && (
            <div className="mt-2.5 border-t border-line pt-1">
              <Switch
                checked={list.postToLedger}
                onChange={(v) =>
                  saveList({ id: list.id, ownerId: list.ownerId, title: list.title, postToLedger: v })
                }
                label={`Count pending in ${ownerName}'s balance`}
                hint="The pending amount shows in the ledger balance"
              />
              {list.postToLedger && Math.abs(totals.pending) > 0.005 && (
                <p className="mt-1 flex items-center gap-1 text-[10.5px] font-semibold text-amber">
                  <CircleDollarSign size={12} className="shrink-0" aria-hidden />
                  <span>
                    Pending +<Money value={totals.pending} currency={currency} /> added to balance
                  </span>
                </p>
              )}
            </div>
          )}

          {list.repeat !== "none" && (
            <Button variant="soft" size="sm" className="mt-3 w-full" onClick={dupNextPeriod}>
              <RepeatIcon size={13} /> Duplicate for next period
            </Button>
          )}
        </section>

        {/* totals & charges */}
        {isCalc && (
          <section
            className="cursor-pointer rounded-[22px] border border-line bg-surface p-4 shadow-key transition hover:bg-surface2 active:scale-[0.99]"
            onClick={() => setTotalsOpen(true)}
            aria-label="Totals and charges — tap to edit"
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === "Enter" || e.key === " ") setTotalsOpen(true);
            }}
          >
            <div className="flex items-center justify-between">
              <p className="flex items-center gap-2 text-[12px] font-bold text-ink">
                <Receipt size={15} className="text-brand" aria-hidden /> Totals &amp; charges
              </p>
              <span className="flex items-center gap-1 text-[10px] font-semibold text-mute">
                Edit <ChevronRight size={13} aria-hidden />
              </span>
            </div>
            <div className="mt-2.5 space-y-1 text-[11px] text-mute">
              <div className="flex justify-between">
                <span>Subtotal</span>
                <span className="font-semibold text-ink">
                  <Money value={totals.subtotal} currency={currency} />
                </span>
              </div>
              {totals.listDiscount > 0 && (
                <div className="flex justify-between">
                  <span>Discount</span>
                  <span className="font-semibold text-pos">
                    − <Money value={totals.listDiscount} currency={currency} />
                  </span>
                </div>
              )}
              {totals.listTax > 0 && (
                <div className="flex justify-between">
                  <span>Tax</span>
                  <span className="font-semibold text-ink">
                    + <Money value={totals.listTax} currency={currency} />
                  </span>
                </div>
              )}
              {totals.extra > 0 && (
                <div className="flex justify-between">
                  <span>Extra charge</span>
                  <span className="font-semibold text-ink">
                    + <Money value={totals.extra} currency={currency} />
                  </span>
                </div>
              )}
              <div className="flex justify-between border-t border-line pt-1 text-[12px] font-bold text-ink">
                <span>Total</span>
                <span>
                  <Money value={totals.grand} currency={currency} />
                </span>
              </div>
            </div>
          </section>
        )}

        {/* grouping toggle */}
        {isCalc && (
          <div className="rounded-[22px] border border-line bg-surface px-4 py-1.5 shadow-key">
            <Switch
              checked={groupOn}
              onChange={(v) => {
                setGroupOn(v);
                if (!v) setOpenGroupId(null);
              }}
              label="Group same items"
              hint="Combine rows with the same name & unit"
            />
          </div>
        )}

        {/* items */}
        {isCalc ? (
          <div className="space-y-2">
            {groupOn
              ? groupItems(list.items).map((group) => {
                  const groupId = group[0].id;
                  const gQty = group.reduce((s, i) => s + (+i.qty || 0), 0);
                  const gTotal = r2(group.reduce((s, i) => s + lineTotal(i), 0));
                  const gPaid = r2(group.reduce((s, i) => s + itemPaid(i), 0));
                  const open = openGroupId === groupId;
                  return (
                    <div key={groupId} className="rounded-[22px] border border-line bg-surface2 p-2">
                      <button
                        type="button"
                        onClick={() => setOpenGroupId(open ? null : groupId)}
                        aria-expanded={open}
                        className="flex w-full items-center gap-3 rounded-2xl px-2 py-2.5 text-left transition hover:bg-surface/60"
                      >
                        <span className="min-w-0 flex-1">
                          <span className="block truncate text-[13px] font-bold text-ink">{group[0].name}</span>
                          <span className="block truncate text-[10.5px] text-mute">
                            {group.length} row{group.length !== 1 ? "s" : ""} · QTY {gQty}
                            {group[0].unit ? ` ${group[0].unit}` : ""} · Paid{" "}
                            <Money value={gPaid} currency={currency} />
                          </span>
                        </span>
                        <span className="shrink-0 text-[13px] font-bold text-ink">
                          <Money value={gTotal} currency={currency} />
                        </span>
                        <ChevronDown
                          size={16}
                          className={cx("shrink-0 text-mute transition-transform", open && "rotate-180")}
                          aria-hidden
                        />
                      </button>
                      <AnimatePresence initial={false}>
                        {open && (
                          <motion.div
                            initial={{ height: 0, opacity: 0 }}
                            animate={{ height: "auto", opacity: 1 }}
                            exit={{ height: 0, opacity: 0 }}
                            transition={{ duration: 0.18, ease: "easeOut" }}
                            className="overflow-hidden"
                          >
                            <div className="space-y-2 px-1 pb-1 pt-1">
                              {group.map((i) => (
                                <CalcItemRow
                                  key={i.id}
                                  listId={list.id}
                                  item={i}
                                  expanded={expandedItemId === i.id}
                                  onToggleExpand={() => toggleExpand(i.id)}
                                  currency={currency}
                                />
                              ))}
                            </div>
                          </motion.div>
                        )}
                      </AnimatePresence>
                    </div>
                  );
                })
              : list.items.map((i) => (
                  <CalcItemRow
                    key={i.id}
                    listId={list.id}
                    item={i}
                    expanded={expandedItemId === i.id}
                    onToggleExpand={() => toggleExpand(i.id)}
                    currency={currency}
                  />
                ))}
            {list.items.length === 0 && (
              <EmptyState
                icon={<ListPlus size={20} />}
                title="No items yet"
                hint="Add the first item with the form below."
              />
            )}
          </div>
        ) : (
          <PlainItems list={list} />
        )}

        {/* add item */}
        <AddItemForm list={list} />

        {/* attachments */}
        <section className="rounded-[22px] border border-line bg-surface p-4 shadow-key" aria-label="Attachments">
          <div className="flex items-center justify-between gap-2">
            <p className="text-[10px] font-bold uppercase tracking-[1.5px] text-mute">Attachments</p>
            <AttachButton
              compact
              parentType="list"
              parentId={list.id}
              onAttached={(meta) => {
                // read fresh from the store — rapid multi-file attaches re-render between picks
                const cur = useApp.getState().lists.find((l) => l.id === list.id);
                saveList({
                  id: list.id,
                  ownerId: list.ownerId,
                  title: list.title,
                  attachments: [...(cur?.attachments ?? list.attachments), meta.id],
                });
              }}
            />
          </div>
          <div className="mt-3">
            {list.attachments.length > 0 ? (
              <AttachmentStrip parentType="list" parentId={list.id} ids={list.attachments} />
            ) : (
              <p className="text-[10.5px] text-mute">Receipts or photos for this list — stored offline.</p>
            )}
          </div>
        </section>
      </div>

      {/* ⋯ menu */}
      <BottomSheet open={menuOpen} onClose={() => setMenuOpen(false)} title={list.title}>
        <div className="space-y-1">
          <ActionRow
            icon={Pencil}
            label="Rename / edit"
            onClick={() => {
              setMenuOpen(false);
              ui.open({ type: "listEdit", listId: list.id, ownerId: list.ownerId });
            }}
          />
          <ActionRow
            icon={Copy}
            label="Duplicate"
            sub="Copy with payments as-is"
            onClick={() => {
              setMenuOpen(false);
              const nid = duplicateList(list.id);
              if (nid) {
                ui.open({ type: "list", listId: nid });
                toastOk("List duplicated");
              }
            }}
          />
          <ActionRow
            icon={Bookmark}
            label="Save as template"
            onClick={() => {
              duplicateList(list.id, { asTemplate: true });
              setMenuOpen(false);
              toastOk("Saved as template");
            }}
          />
          {isCalc && (
            <ActionRow
              icon={CircleCheckBig}
              label="All paid"
              sub="Adds one payment per pending row"
              onClick={() => {
                setMenuOpen(false);
                const n = markAllPaid(list.id);
                toastOk(n > 0 ? `${n} item${n !== 1 ? "s" : ""} marked paid` : "Nothing pending");
              }}
            />
          )}
          {isCalc && (
            <ActionRow
              icon={RotateCcw}
              label="Clear payments"
              danger
              onClick={() => void onClearPayments()}
            />
          )}
          <ActionRow
            icon={Share2}
            label="Share…"
            onClick={() => {
              setMenuOpen(false);
              setShareOpen(true);
            }}
          />
          <ActionRow icon={Trash2} label="Delete list" danger onClick={() => void onDelete()} />
        </div>
      </BottomSheet>

      {/* share sheet */}
      <BottomSheet open={shareOpen} onClose={() => setShareOpen(false)} title="Share list">
        <div className="space-y-1">
          <ActionRow
            icon={MessageCircle}
            label="WhatsApp"
            sub={phone ? `Send to ${ownerName}` : "No phone number available"}
            onClick={onWhatsApp}
          />
          <ActionRow icon={FileText} label="PDF bill" sub="Download a clean bill" onClick={onPdf} />
          <ActionRow icon={Copy} label="Copy text" sub="Paste it anywhere" onClick={() => void onCopy()} />
        </div>
      </BottomSheet>

      {/* totals settings */}
      {isCalc && <TotalsSheet open={totalsOpen} onClose={() => setTotalsOpen(false)} list={list} />}

      {/* owner picker */}
      <ContactPicker
        open={ownerPickerOpen}
        onClose={() => setOwnerPickerOpen(false)}
        onPick={(id) => {
          moveEntity("list", list.id, id as OwnerId);
          toastOk("List moved");
        }}
      />
    </Sheet>
  );
}

/* ================================================================== */
/* ListEdit                                                             */
/* ================================================================== */

export function ListEdit({
  listId,
  ownerId,
  kind,
}: {
  listId?: ID;
  ownerId: ID | "general";
  kind?: ListKind;
}) {
  const ui = useUI();
  const existing = useApp((s) => (listId ? s.lists.find((l) => l.id === listId && !l.deletedAt) : undefined));
  const contacts = useApp((s) => s.contacts);
  const saveList = useApp((s) => s.saveList);

  const [title, setTitle] = useState(existing?.title ?? "");
  const [note, setNote] = useState(existing?.note ?? "");
  const [k, setK] = useState<ListKind>(kind ?? existing?.kind ?? "calculating");
  const [repeat, setRepeat] = useState<Repeat>(existing?.repeat ?? "none");
  const [asTemplate, setAsTemplate] = useState(existing?.isTemplate ?? false);
  const [postToLedger, setPostToLedger] = useState(existing?.postToLedger ?? false);

  const ownerContact = (existing?.ownerId ?? ownerId) === "general"
    ? null
    : contacts.find((c) => c.id === (existing?.ownerId ?? ownerId)) ?? null;
  const ownerName = ownerContact ? displayName(ownerContact) : "General";
  const kindFixed = !!listId || !!kind;

  const save = () => {
    if (!title.trim()) {
      toastErr("Give the list a title");
      return;
    }
    const saved = saveList({
      ...(listId ? { id: listId } : {}),
      ownerId: existing?.ownerId ?? ownerId,
      title: title.trim(),
      note: note.trim() || "",
      kind: k,
      repeat,
      isTemplate: asTemplate,
      ...(k === "calculating" ? { postToLedger } : {}),
    });
    toastOk(listId ? "List updated" : "List created");
    if (!listId) {
      ui.pop();
      ui.open({ type: "list", listId: saved.id });
    } else {
      ui.pop();
    }
  };

  return (
    <Sheet
      title={listId ? "Edit list" : "New list"}
      subtitle={`${KIND_META[k].label} · ${ownerName}`}
      onBack={() => ui.pop()}
      footer={
        <Button variant="brand" size="lg" className="w-full" onClick={save}>
          <Check size={16} /> Save list
        </Button>
      }
    >
      <div className="space-y-4 px-4 py-4 pb-6">
        <Field label="Title">
          <input
            autoFocus
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="e.g. Grocery run, Rent…"
            aria-label="List title"
            className={cx(inputCls, "text-[15px] font-semibold")}
          />
        </Field>

        {/* kind — fixed when passed / editing, selector when new without kind */}
        <div>
          <p className="mb-1.5 text-[10px] font-bold uppercase tracking-[1.5px] text-mute">Kind</p>
          {kindFixed ? (
            <div className="flex items-center gap-3 rounded-2xl border border-line bg-surface2 px-3.5 py-3">
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-surface text-brand shadow-key" aria-hidden>
                {(() => {
                  const Icon = KIND_META[k].icon;
                  return <Icon size={16} />;
                })()}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block text-[13px] font-bold text-ink">{KIND_META[k].label}</span>
                <span className="block text-[10.5px] text-mute">{KIND_META[k].hint}</span>
              </span>
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-2">
              {(Object.keys(KIND_META) as ListKind[]).map((opt) => {
                const Icon = KIND_META[opt].icon;
                return (
                  <button
                    key={opt}
                    type="button"
                    onClick={() => setK(opt)}
                    aria-pressed={k === opt}
                    className={cx(
                      "flex items-center gap-2.5 rounded-2xl border p-3 text-left transition active:scale-[0.97]",
                      k === opt ? "border-brand/50 bg-brandsoft shadow-key" : "border-line bg-surface"
                    )}
                  >
                    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-surface2 text-brand" aria-hidden>
                      <Icon size={16} />
                    </span>
                    <span className="min-w-0 text-[12px] font-bold text-ink">{KIND_META[opt].label}</span>
                  </button>
                );
              })}
              <p className="col-span-2 text-[10px] text-mute">{KIND_META[k].hint}</p>
            </div>
          )}
        </div>

        <Field label="Note">
          <textarea
            value={note}
            onChange={(e) => setNote(e.target.value)}
            rows={2}
            placeholder="Optional note"
            aria-label="List note"
            className={cx(inputCls, "resize-none")}
          />
        </Field>

        <Field label="Repeat">
          <select
            value={repeat}
            onChange={(e) => setRepeat(e.target.value as Repeat)}
            aria-label="Repeat"
            className={inputCls}
          >
            {(Object.keys(REPEAT_LABEL) as Repeat[]).map((r) => (
              <option key={r} value={r}>
                {REPEAT_LABEL[r]}
              </option>
            ))}
          </select>
        </Field>

        <div className="space-y-1 rounded-[22px] border border-line bg-surface p-4 shadow-key">
          <Switch
            checked={asTemplate}
            onChange={setAsTemplate}
            label="Save as template"
            hint="Reuse it for repeat bills"
          />
          {k === "calculating" && (
            <div className="mt-1 border-t border-line pt-1">
              <Switch
                checked={postToLedger}
                onChange={setPostToLedger}
                label="Count pending in balance"
                hint="Include this list pending amount in the owner balance"
              />
            </div>
          )}
        </div>
      </div>
    </Sheet>
  );
}

/* ================================================================== */
/* EntryEdit                                                            */
/* ================================================================== */

function TypeBtn({
  active,
  onClick,
  icon: Icon,
  label,
  sub,
  tone,
}: {
  active: boolean;
  onClick: () => void;
  icon: typeof ArrowDownLeft;
  label: string;
  sub: string;
  tone: "pos" | "neg";
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={cx(
        "flex min-h-[56px] w-full items-center gap-2.5 rounded-2xl border px-3.5 py-3 text-left transition active:scale-[0.97]",
        active
          ? tone === "pos"
            ? "border-brand/50 bg-brandsoft"
            : "border-neg/40 bg-negsoft"
          : "border-line bg-surface"
      )}
    >
      <span
        className={cx(
          "flex h-9 w-9 shrink-0 items-center justify-center rounded-[13px]",
          active ? (tone === "pos" ? "bg-brand text-white" : "bg-neg text-white") : "bg-surface2 text-mute"
        )}
        aria-hidden
      >
        <Icon size={16} />
      </span>
      <span className="min-w-0">
        <span className={cx("block truncate text-[13px] font-bold", active ? (tone === "pos" ? "text-pos" : "text-neg") : "text-ink")}>
          {label}
        </span>
        <span className="block truncate text-[10px] text-mute">{sub}</span>
      </span>
    </button>
  );
}

export function EntryEdit({
  entryId,
  ownerId,
  entryType,
}: {
  entryId?: ID;
  ownerId: ID | "general";
  entryType?: "credit" | "debit";
}) {
  const ui = useUI();
  const confirm = useConfirm();
  /** draft id — a NEW entry can save early as a draft so files attach to a real id */
  const [draftId, setDraftId] = useState<ID | null>(null);
  const currentId: ID | undefined = entryId ?? draftId ?? undefined;
  const existing = useApp((s) => (currentId ? s.entries.find((e) => e.id === currentId && !e.deletedAt) : undefined));
  const contacts = useApp((s) => s.contacts);
  const saveEntry = useApp((s) => s.saveEntry);
  const deleteEntry = useApp((s) => s.deleteEntry);
  const moveEntity = useApp((s) => s.moveEntity);
  const saveReminder = useApp((s) => s.saveReminder);

  const [type, setType] = useState<"credit" | "debit">(existing?.type ?? entryType ?? "credit");
  const [amount, setAmount] = useState(existing && existing.amount > 0 ? String(existing.amount) : "");
  const [date, setDate] = useState(existing?.date ?? todayStr());
  const [dueOn, setDueOn] = useState(!!existing?.dueDate);
  const [dueDate, setDueDate] = useState(existing?.dueDate ?? todayStr());
  const [category, setCategory] = useState(existing?.category ?? "General");
  const [method, setMethod] = useState(existing?.method ?? "cash");
  const [note, setNote] = useState(existing?.note ?? "");
  const [owner, setOwner] = useState<OwnerId>(existing?.ownerId ?? ownerId);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [attachments, setAttachments] = useState<ID[]>(existing?.attachments ?? []);

  const ownerContact = owner === "general" ? null : contacts.find((c) => c.id === owner) ?? null;
  const ownerName = ownerContact ? displayName(ownerContact) : "General";
  const isGeneral = owner === "general";

  /** Save the current form as a draft so files can attach to a real id (new entries only). */
  const ensureDraft = (): boolean => {
    if (currentId) return true;
    const amt = parseNum(amount);
    if (amt <= 0) {
      toastErr("Enter an amount greater than 0 first");
      return false;
    }
    const saved = saveEntry({
      ownerId: owner,
      type,
      amount: r2(amt),
      date,
      dueDate: dueOn && dueDate ? dueDate : "",
      category,
      method,
      note: note.trim() || "",
    });
    setDraftId(saved.id);
    return true;
  };

  /** Keep the entry's attachment ids in sync with every added file. */
  const onAttached = (meta: AttachmentMeta) => {
    if (!currentId) return;
    setAttachments((ids) => [...ids, meta.id]);
    // read fresh from the store — rapid multi-file attaches re-render between picks
    const cur = useApp.getState().entries.find((e) => e.id === currentId);
    saveEntry({
      id: currentId,
      ownerId: cur?.ownerId ?? owner,
      type: cur?.type ?? type,
      attachments: [...(cur?.attachments ?? []), meta.id],
    });
  };

  const save = () => {
    const amt = parseNum(amount);
    if (amt <= 0) {
      toastErr("Enter an amount greater than 0");
      return;
    }
    const saved = saveEntry({
      ...(currentId ? { id: currentId } : {}),
      ownerId: owner,
      type,
      amount: r2(amt),
      date,
      // "" clears an existing due date (saveEntry keeps undefined as-is)
      dueDate: dueOn && dueDate ? dueDate : "",
      category,
      method,
      note: note.trim() || "",
      attachments,
    });
    if (entryId && owner !== existing?.ownerId) moveEntity("entry", saved.id, owner);
    // auto-alarm scenario — a due date on a NEW entry sets the payment reminder
    if (!entryId && dueOn && dueDate) {
      saveReminder({
        ownerId: owner,
        title: "Payment due",
        notes: note.trim() || undefined,
        kind: "payment",
        dueAt: new Date(`${dueDate}T09:00`).getTime(),
        links: [{ type: "entry", id: saved.id, label: category }],
      });
    }
    toastOk(entryId ? "Entry updated" : "Entry added");
    ui.pop();
  };

  const remove = async () => {
    const ok = await confirm({
      title: "Delete entry?",
      message: "The entry and its payment history move to Trash — you can undo from the toast.",
      confirmLabel: "Delete",
      tone: "danger",
    });
    if (!ok) return;
    if (currentId) {
      deleteEntry(currentId);
      toastUndoLast();
    }
    ui.pop();
  };

  return (
    <Sheet
      title={
        entryId
          ? "Edit entry"
          : type === "credit"
            ? isGeneral
              ? "New income"
              : "New credit"
            : isGeneral
              ? "New expense"
              : "New debit"
      }
      subtitle={`${ownerName} · ledger`}
      onBack={() => ui.pop()}
      footer={
        <div className="flex gap-2.5">
          {currentId && (
            <Button variant="surface" size="lg" className="flex-1 text-neg" onClick={() => void remove()}>
              <Trash2 size={15} /> Delete
            </Button>
          )}
          <Button variant="brand" size="lg" className={currentId ? "flex-[2]" : "w-full"} onClick={save}>
            <Check size={16} /> Save entry
          </Button>
        </div>
      }
    >
      <div className="space-y-4 px-4 py-4 pb-6">
        {/* type */}
        <div className="grid grid-cols-2 gap-2">
          <TypeBtn
            active={type === "credit"}
            onClick={() => setType("credit")}
            icon={ArrowDownLeft}
            label={isGeneral ? "Income" : "Credit"}
            sub={isGeneral ? "money in" : "they owe you"}
            tone="pos"
          />
          <TypeBtn
            active={type === "debit"}
            onClick={() => setType("debit")}
            icon={ArrowUpRight}
            label={isGeneral ? "Expense" : "Debit"}
            sub={isGeneral ? "money out" : "you owe them"}
            tone="neg"
          />
        </div>

        {/* big amount */}
        <div className="rounded-[22px] border border-line bg-surface p-4 shadow-key">
          <Field label="Amount">
            <div className="flex w-full items-center gap-2">
              <input
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                inputMode="decimal"
                placeholder="0"
                aria-label="Entry amount"
                className="w-full rounded-2xl border border-line bg-surface2 px-4 py-3 text-[28px] font-bold tabular-nums tracking-tight text-ink placeholder:text-mute/60 focus:border-brand/50 focus:ring-2 focus:ring-brand/15"
              />
              <CalcButton onPick={(v) => setAmount(String(v))} />
            </div>
          </Field>
          {existing && existing.payments.length > 0 && (
            <p className="mt-2.5 text-[11px] text-mute">
              Paid <Money value={paidOf(existing)} /> of <Money value={existing.amount} /> ·{" "}
              <Money value={r2(existing.amount - paidOf(existing))} /> left
            </p>
          )}
        </div>

        {/* date + due */}
        <div className="grid grid-cols-2 gap-2">
          <Field label="Date">
            <input
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              aria-label="Entry date"
              className={inputCls}
            />
          </Field>
          <Field label="Category">
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              aria-label="Category"
              className={inputCls}
            >
              {ENTRY_CATEGORIES.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </Field>
        </div>

        <div className="rounded-[22px] border border-line bg-surface p-4 shadow-key">
          <Switch
            checked={dueOn}
            onChange={setDueOn}
            label="Due date"
            hint="Sets a payment reminder automatically"
          />
          {dueOn && (
            <div className="mt-2">
              <input
                type="date"
                value={dueDate}
                onChange={(e) => setDueDate(e.target.value)}
                aria-label="Due date"
                className={inputCls}
              />
            </div>
          )}
        </div>

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

        <Field label="Note">
          <textarea
            value={note}
            onChange={(e) => setNote(e.target.value)}
            rows={2}
            placeholder="What is this for?"
            aria-label="Entry note"
            className={cx(inputCls, "resize-none")}
          />
        </Field>

        {/* owner */}
        <div className="rounded-[22px] border border-line bg-surface p-4 shadow-key">
          <div className="flex items-center gap-3">
            {ownerContact ? (
              <Avatar contact={ownerContact} size="sm" />
            ) : (
              <span className="flex h-9 w-9 items-center justify-center rounded-[13px] bg-surface2 text-mute" aria-hidden>
                <Inbox size={15} />
              </span>
            )}
            <div className="min-w-0 flex-1">
              <p className="text-[10px] font-bold uppercase tracking-[1.5px] text-mute">Owner</p>
              <p className="truncate text-[13px] font-semibold text-ink">{ownerName}</p>
            </div>
            <Button variant="soft" size="sm" onClick={() => setPickerOpen(true)}>
              Change
            </Button>
          </div>
        </div>

        {/* attachments — a new entry saves a draft first so files attach to a real id */}
        <section className="rounded-[22px] border border-line bg-surface p-4 shadow-key" aria-label="Attachments">
          <div className="flex items-center justify-between gap-2">
            <p className="text-[10px] font-bold uppercase tracking-[1.5px] text-mute">Attachments</p>
            {currentId ? (
              <AttachButton compact parentType="entry" parentId={currentId} onAttached={onAttached} />
            ) : (
              <Button variant="soft" size="sm" onClick={ensureDraft}>
                <Paperclip size={13} /> Save draft &amp; attach
              </Button>
            )}
          </div>
          <div className="mt-3">
            {currentId && attachments.length > 0 ? (
              <AttachmentStrip parentType="entry" parentId={currentId} ids={attachments} />
            ) : (
              <p className="text-[10.5px] text-mute">Receipts or proofs — stored offline.</p>
            )}
          </div>
        </section>
      </div>

      <ContactPicker
        open={pickerOpen}
        onClose={() => setPickerOpen(false)}
        onPick={(id) => setOwner(id as OwnerId)}
        allowGeneral
      />
    </Sheet>
  );
}

/** paid-so-far helper for the EntryEdit amount hint */
function paidOf(e: LedgerEntry): number {
  return r2((e.payments || []).reduce((s, p) => s + (+p.amount || 0), 0));
}
