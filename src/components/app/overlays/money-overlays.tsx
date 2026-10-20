"use client";

import { useMemo, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import {
  ArrowDownLeft,
  ArrowUpRight,
  Bell,
  Bookmark,
  Boxes,
  Calculator,
  CalendarClock,
  Check,
  ChevronDown,
  ChevronRight,
  CircleCheckBig,
  CircleDollarSign,
  Copy,
  CreditCard,
  Ellipsis,
  FileText,
  HandCoins,
  Inbox,
  List,
  ListChecks,
  ListPlus,
  MessageCircle,
  Pencil,
  Plus,
  Receipt,
  Repeat as RepeatIcon,
  RotateCcw,
  Share2,
  SquareCheckBig,
  Tag,
  Trash2,
  X,
} from "lucide-react";
import { useApp } from "@/lib/store";
import { useUI } from "@/lib/ui-store";
import { useT } from "@/lib/i18n";
import { takePendingEntryGroup } from "../sections/LedgerSection";
import type { ReactNode } from "react";
import {
  dueStatus,
  groupItems,
  itemPaid,
  itemRemaining,
  itemsCount,
  itemsTotal,
  lineTotal,
  listTotals,
  r2,
} from "@/lib/calc";
import { copyText, displayName, downloadBlob, fd, fdShort, money, todayStr, uid } from "@/lib/utils";
import { openExternal, primaryPhone, waLink } from "@/lib/share";
import { listShareText, listToPdfBlob } from "@/lib/pdf";
import {
  ENTRY_CATEGORIES,
  PAYMENT_METHODS,
  type CalcList,
  type EntryItem,
  type EntryKind,
  type ID,
  type LedgerEntry,
  type ListDiscount,
  type ListKind,
  type ListItem,
  type LinkRef,
  type OwnerId,
  type Payment,
  type Repeat,
} from "@/lib/types";
import { AttachEntities, EntityRefChips } from "../kit/EntityLinks";
import { CalcButton } from "../kit/Calculator";
import { ContactPicker } from "../kit/ContactPicker";
import { useConfirm } from "../kit/Confirm";
import { BottomSheet, Sheet } from "../kit/Sheet";
import { SwipeRow } from "../kit/SwipeRow";
import { toastErr, toastOk, toastUndoLast } from "../kit/toast";
import {
  Avatar,
  Button,
  Chip,
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
  ownerId = "general",
  onSubmit,
}: {
  open: boolean;
  onClose: () => void;
  remaining: number;
  currency: string;
  ownerId?: OwnerId;
  onSubmit: (p: { amount: number; date: string; note?: string; method?: string; refs: LinkRef[] }) => void;
}) {
  const t = useT();
  const [amount, setAmount] = useState("");
  const [date, setDate] = useState(todayStr());
  const [note, setNote] = useState("");
  const [method, setMethod] = useState("cash");
  const [refs, setRefs] = useState<LinkRef[]>([]);
  if (!open) return null;

  const submit = () => {
    const amt = parseNum(amount);
    if (amt <= 0) {
      toastErr(t("lg.amountGreaterThanZero"));
      return;
    }
    onSubmit({ amount: r2(amt), date: date || todayStr(), note: note.trim() || undefined, method, refs });
  };

  return (
    <BottomSheet open={open} onClose={onClose} title={t("lg.addPayment")}>
      <Field label={t("lg.amount")}>
        <div className="flex w-full items-center gap-2">
          <input
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            inputMode="decimal"
            placeholder="0"
            aria-label={t("lg.paymentAmount")}
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
          aria-label={t("lg.fillRemaining")}
        >
          <HandCoins size={12} aria-hidden /> Full remaining: <Money value={remaining} currency={currency} />
        </button>
      )}
      <div className="mt-3 grid grid-cols-2 gap-2">
        <Field label={t("lg.date")}>
          <input
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            aria-label={t("lg.paymentDate")}
            className={inputCls}
          />
        </Field>
        <Field label={t("lg.method")}>
          <select
            value={method}
            onChange={(e) => setMethod(e.target.value)}
            aria-label={t("lg.paymentMethod")}
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
      <Field label={t("lg.note")} className="mt-3">
        <input
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder={t("lg.optionalNote")}
          aria-label={t("lg.paymentNote")}
          className={inputCls}
        />
      </Field>
      {/* attached entities — banks, lists, notes, reminders, contacts & links */}
      <div className="mt-3">
        <AttachEntities refs={refs} onChange={setRefs} ownerId={ownerId} />
      </div>
      <Button variant="brand" size="lg" className="mt-4 w-full" onClick={submit}>
        <Plus size={15} /> {t("lg.addPayment")}
      </Button>
    </BottomSheet>
  );
}

/* ------------------------------------------------------------------ */
/* Item payment history (numbered rows, progress, add)                  */
/* ------------------------------------------------------------------ */

function ItemPayments({ listId, item, currency }: { listId: ID; item: ListItem; currency: string }) {
  const t = useT();
  const confirm = useConfirm();
  const addItemPayment = useApp((s) => s.addItemPayment);
  const removeItemPayment = useApp((s) => s.removeItemPayment);
  /* the list's owner — created entities / attachments land there */
  const ownerId = useApp((s) => s.lists.find((l) => l.id === listId && !l.deletedAt)?.ownerId ?? "general");
  const [payOpen, setPayOpen] = useState(false);

  const lt = lineTotal(item);
  const paid = itemPaid(item);
  const rem = itemRemaining(item);
  const n = item.payments.length;

  const remove = async (p: Payment, idx: number) => {
    const ok = await confirm({
      title: `Remove payment ${idx + 1}?`,
      message: `Payment of ${money(p.amount, currency)} on ${fd(p.date)} will be removed.`,
      confirmLabel: t("act.remove"),
      tone: "danger",
    });
    if (!ok) return;
    removeItemPayment(listId, item.id, p.id);
    toastOk(t("lg.paymentRemoved"));
  };

  return (
    <div className="rounded-2xl bg-surface2 p-3">
      <p className="mb-2 text-[10px] font-bold uppercase tracking-[1.5px] text-mute">{t("lg.payments")}</p>
      {n === 0 && <p className="pb-1 text-[11px] text-mute">{t("lg.noPaymentsYet")}</p>}
      <ol className="space-y-1">
        {item.payments.map((p, i) => (
          <li key={p.id} className="rounded-xl bg-surface px-2.5 py-1.5 shadow-key">
            <div className="flex items-center gap-2">
              <span className="w-3.5 shrink-0 text-[10.5px] font-bold tabular-nums text-mute">{i + 1}.</span>
              <span className="shrink-0 text-[11px] text-mute">{fdShort(p.date)}</span>
              <span className="shrink-0 text-[12px] font-bold text-ink">
                <Money value={p.amount} currency={currency} />
              </span>
              {(p.note || p.auto) && (
                <span className="min-w-0 flex-1 truncate text-[10px] text-mute">
                  {p.auto === "tick" ? "Ticked" : p.auto === "settle" ? t("lg.settleUp") : p.note}
                </span>
              )}
              <IconButton
                label={`Remove payment ${i + 1}`}
                className="h-9 w-9 shrink-0"
                onClick={() => void remove(p, i)}
              >
                <X size={13} />
              </IconButton>
            </div>
            {p.refs && p.refs.length > 0 && <EntityRefChips refs={p.refs} className="mt-1 pl-6" />}
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
        <Plus size={13} /> {t("lg.addPayment")}
      </Button>
      {payOpen && (
        <AddPaymentSheet
          open
          onClose={() => setPayOpen(false)}
          remaining={Math.max(rem, 0)}
          currency={currency}
          ownerId={ownerId}
          onSubmit={(p) => {
            addItemPayment(listId, item.id, p);
            toastOk(t("lg.paymentAdded"));
            setPayOpen(false);
          }}
        />
      )}
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
  const t = useT();
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
        confirmLabel: t("act.remove"),
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
      confirmLabel: t("act.remove"),
      tone: "danger",
    });
    if (!ok) return;
    removeListItem(listId, item.id);
    toastOk(t("ls.itemRemoved"));
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
                {t("lg.paid")} <Money value={paid} currency={currency} /> / <Money value={lt} currency={currency} />
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
          aria-label={t("ls.toggleItemDetails")}
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
              {t("lg.paid")} ✓
            </span>
          )}
          {rem < -0.005 && (
            <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-negsoft px-2 py-0.5 text-[10px] font-bold text-neg">
              {t("lg.overpaidBy")} <Money value={-rem} currency={currency} />
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
                <Field label={t("ls.itemName")} className="col-span-2">
                  <input
                    value={item.name}
                    onChange={(e) => updateListItem(listId, item.id, { name: e.target.value })}
                    aria-label={t("ls.itemName")}
                    className={cx(inputCls, "py-2.5 text-[13px]")}
                  />
                </Field>
                <Field label={t("ls.rowDate")}>
                  <input
                    type="date"
                    value={item.date ?? ""}
                    onChange={(e) => updateListItem(listId, item.id, { date: e.target.value || undefined })}
                    aria-label={t("ls.rowDate")}
                    className={cx(inputCls, "py-2.5 text-[13px]")}
                  />
                </Field>
                <Field label={t("ls.itemDueDate")}>
                  <input
                    type="date"
                    value={item.dueDate ?? ""}
                    onChange={(e) => updateListItem(listId, item.id, { dueDate: e.target.value || undefined })}
                    aria-label={t("ls.itemDueDate")}
                    className={cx(inputCls, "py-2.5 text-[13px]")}
                  />
                </Field>
                <Field label={t("ls.qty")}>
                  <NumField
                    value={item.qty}
                    ariaLabel={t("ls.quantity")}
                    onCommit={(v) => updateListItem(listId, item.id, { qty: v })}
                  />
                </Field>
                <Field label={t("ls.unit")}>
                  <input
                    value={item.unit ?? ""}
                    onChange={(e) => updateListItem(listId, item.id, { unit: e.target.value || undefined })}
                    placeholder={t("ls.unitPlaceholder")}
                    aria-label={t("ls.unit")}
                    className={cx(inputCls, "py-2.5 text-[13px]")}
                  />
                </Field>
                <Field label={t("ls.unitPrice")} className="col-span-2">
                  <NumField
                    calc
                    value={item.unitPrice}
                    ariaLabel={t("ls.unitPrice")}
                    onCommit={(v) => updateListItem(listId, item.id, { unitPrice: r2(v) })}
                  />
                </Field>
                <Field label={t("ls.rowDiscount")} className="col-span-2">
                  <ItemDiscountField listId={listId} item={item} />
                </Field>
                <Field label={t("ls.taxPct")} className="col-span-2">
                  <NumField
                    value={item.taxPct ?? 0}
                    ariaLabel={t("ls.taxPercent")}
                    onCommit={(v) => updateListItem(listId, item.id, { taxPct: v > 0 ? r2(v) : undefined })}
                  />
                </Field>
                <Field label={t("lg.note")} className="col-span-2">
                  <input
                    value={item.note ?? ""}
                    onChange={(e) => updateListItem(listId, item.id, { note: e.target.value || undefined })}
                    placeholder={t("lg.optionalNote")}
                    aria-label={t("ls.itemNote")}
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
                <Trash2 size={13} /> {t("act.remove")} · {item.name}
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
  const t = useT();
  const confirm = useConfirm();
  const toggleItemChecked = useApp((s) => s.toggleItemChecked);
  const removeListItem = useApp((s) => s.removeListItem);

  const done = list.items.filter((i) => i.checked).length;
  const total = list.items.length;

  const onRemove = async (item: ListItem) => {
    const ok = await confirm({
      title: `Remove “${item.name}”?`,
      message: "The row is removed from this list.",
      confirmLabel: t("act.remove"),
      tone: "danger",
    });
    if (!ok) return;
    removeListItem(list.id, item.id);
    toastOk(t("ls.itemRemoved"));
  };

  const onTick = async (item: ListItem, checked: boolean) => {
    const tickCount = item.payments.filter((p) => p.auto === "tick").length;
    if (!checked && tickCount > 0) {
      const ok = await confirm({
        title: "Remove tick payments?",
        message: `Ticking added ${tickCount} payment${tickCount !== 1 ? "s" : ""}. Unticking will remove them.`,
        confirmLabel: t("act.remove"),
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
                        {t(item.priority === "low" ? "ls.low" : item.priority === "medium" ? "ls.medium" : "ls.high")}
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
          title={t("ls.noItems")}
          hint={t("ls.noItemsHintB")}
        />
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Add-item form                                                        */
/* ------------------------------------------------------------------ */

function AddItemForm({ list }: { list: CalcList }) {
  const t = useT();
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
      toastErr(t("ls.giveItemName"));
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
    toastOk(t("ls.itemAdded"));
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
              <Field label={t("ls.itemName")}>
                <input
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder={t("ls.itemName")}
                  aria-label={t("ls.itemName")}
                  className={inputCls}
                />
              </Field>
              {isCalc && (
                <>
                  <div className="grid grid-cols-[1fr_92px] gap-2">
                    <Field label={t("ls.qty")}>
                      <input
                        value={qty}
                        onChange={(e) => setQty(e.target.value)}
                        inputMode="decimal"
                        aria-label={t("ls.quantity")}
                        className={inputCls}
                      />
                    </Field>
                    <Field label={t("ls.unit")}>
                      <input
                        value={unit}
                        onChange={(e) => setUnit(e.target.value)}
                        placeholder={t("ls.unitPlaceholder")}
                        aria-label={t("ls.unit")}
                        className={inputCls}
                      />
                    </Field>
                  </div>
                  <Field label={t("ls.unitPrice")}>
                    <div className="flex w-full items-center gap-2">
                      <input
                        value={price}
                        onChange={(e) => setPrice(e.target.value)}
                        inputMode="decimal"
                        placeholder="0"
                        aria-label={t("ls.unitPrice")}
                        className={inputCls}
                      />
                      <CalcButton onPick={(v) => setPrice(String(v))} />
                    </div>
                  </Field>
                  <Field label={t("ls.rowDate")} hint={t("ls.sameItemHint")}>
                    <input
                      type="date"
                      value={date}
                      onChange={(e) => setDate(e.target.value)}
                      aria-label={t("ls.rowDate")}
                      className={inputCls}
                    />
                  </Field>
                </>
              )}
              {isTodo && (
                <>
                  <Field label={t("ls.priority")}>
                    <Segmented
                      options={[
                        { value: "low", label: t("ls.low") },
                        { value: "medium", label: t("ls.medium") },
                        { value: "high", label: t("ls.high") },
                      ]}
                      value={priority}
                      onChange={setPriority}
                    />
                  </Field>
                  <Field label={t("ls.itemDueDate")}>
                    <input
                      type="date"
                      value={dueDate}
                      onChange={(e) => setDueDate(e.target.value)}
                      aria-label={t("ls.itemDueDate")}
                      className={inputCls}
                    />
                  </Field>
                </>
              )}
              <Field label={t("lg.note")}>
                <input
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  placeholder={t("lg.optionalNote")}
                  aria-label={t("ls.itemNote")}
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
  const t = useT();
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
    toastOk(t("lg.totalsUpdated"));
    onClose();
  };

  return (
    <BottomSheet open={open} onClose={onClose} title={t("ls.totalsCharges")}>
      <Field label={t("ls.listDiscount")}>
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
            aria-label={t("ls.discountValue")}
            className={inputCls}
          />
          <CalcButton onPick={(v) => setDVal(String(v))} />
        </div>
      </Field>
      <div className="mt-3 grid grid-cols-2 gap-2">
        <Field label={t("ls.taxPct")}>
          <input
            value={tax}
            onChange={(e) => setTax(e.target.value)}
            inputMode="decimal"
            placeholder="0"
            aria-label={t("ls.taxPercent")}
            className={inputCls}
          />
        </Field>
        <Field label={t("ls.extraCharge")}>
          <input
            value={extra}
            onChange={(e) => setExtra(e.target.value)}
            inputMode="decimal"
            placeholder="0"
            aria-label={t("ls.extraCharge")}
            className={inputCls}
          />
        </Field>
      </div>

      {/* live preview */}
      <div className="mt-4 rounded-2xl bg-surface2 p-3.5">
        <p className="mb-2 text-[10px] font-bold uppercase tracking-[1.5px] text-mute">{t("ls.preview")}</p>
        <div className="space-y-1.5 text-[11.5px] text-mute">
          <div className="flex justify-between">
            <span>{t("ls.subtotal")}</span>
            <span className="font-semibold text-ink">
              <Money value={preview.subtotal} currency={currency} />
            </span>
          </div>
          {preview.listDiscount > 0 && (
            <div className="flex justify-between">
              <span>{t("ls.discount")}</span>
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
              <span>{t("ls.extraCharge")}</span>
              <span className="font-semibold text-ink">
                + <Money value={preview.extra} currency={currency} />
              </span>
            </div>
          )}
          <div className="flex justify-between border-t border-line pt-1.5 text-[12.5px] font-bold text-ink">
            <span>{t("ls.total")}</span>
            <span>
              <Money value={preview.grand} currency={currency} />
            </span>
          </div>
          <div className="flex justify-between text-[10.5px]">
            <span>{t("lg.paid")}</span>
            <span className="text-pos">
              <Money value={preview.paid} currency={currency} />
            </span>
          </div>
          <div className="flex justify-between text-[10.5px] font-bold">
            <span>{t("lg.pending")}</span>
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
  const t = useT();
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
      <Sheet title={t("tab.lists")} onBack={() => ui.pop()}>
        <div className="px-4 py-6">
          <EmptyState
            icon={<List size={22} />}
            title={t("ls.listNotFound")}
            hint={t("ls.listNotFoundHint")}
          />
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
      toastErr(ownerContact ? t("cd.noPhone") : "General has no phone to send to");
      return;
    }
    openExternal(waLink(phone, shareText()));
    logMessage(list.ownerId, "whatsapp", shareText());
  };
  const onPdf = () => {
    downloadBlob(listToPdfBlob(list, ownerContact, currency), `${safeName(list.title)}.pdf`);
    toastOk(t("lg.pdfDownloaded"));
  };
  const onCopy = async () => {
    if (await copyText(shareText())) toastOk(t("act.copied"));
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
    toastOk(t("ls.remindSet"));
  };

  const dupNextPeriod = () => {
    const nid = duplicateList(list.id, { fresh: true });
    if (nid) {
      ui.open({ type: "list", listId: nid });
      toastOk(t("ls.duplicatedNext"));
    }
  };

  const onClearPayments = async () => {
    const ok = await confirm({
      title: t("ls.clearPayments") + "?",
      message: "Every payment row on every item is removed and ticks are reset.",
      confirmLabel: t("ls.clearPayments"),
      tone: "danger",
    });
    if (!ok) return;
    setMenuOpen(false);
    clearPaid(list.id);
    toastOk(t("ls.paymentsCleared"));
  };

  const onDelete = async () => {
    const ok = await confirm({
      title: `Delete “${list.title}”?`,
      message: "The list and its items move to Trash — you can undo from the toast.",
      confirmLabel: t("act.delete"),
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
            {t("ls.total")}{" "}
            <span className="text-[13px] font-bold normal-case tracking-normal text-ink">
              <Money value={totals.grand} currency={currency} />
            </span>
          </span>
          <span className="text-[10px] font-bold uppercase tracking-[1px] text-mute">
            {t("lg.paid")}{" "}
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
                {t("lg.pending")} <Money value={totals.pending} currency={currency} />
              </>
            ) : (
              `${t("ls.allSettled")} ✓`
            )}
          </span>
        </div>
      )}
      <div className="grid grid-cols-5 gap-1.5">
        <FooterAction icon={<MessageCircle size={17} />} label={t("act.whatsapp")} onClick={onWhatsApp} />
        <FooterAction icon={<FileText size={17} />} label={t("ls.pdfLabel")} onClick={onPdf} />
        <FooterAction icon={<Copy size={17} />} label={t("act.copy")} onClick={() => void onCopy()} />
        <FooterAction icon={<Bell size={17} />} label={t("ls.remind")} onClick={onReminder} />
        <FooterAction icon={<Ellipsis size={17} />} label={t("act.more")} onClick={() => setMenuOpen(true)} />
      </div>
    </div>
  );

  return (
    <Sheet
      title={list.title}
      subtitle={`${KIND_META[list.kind].label} · ${ownerName}`}
      onBack={() => ui.pop()}
      actions={
        <IconButton label={t("ls.listActions")} onClick={() => setMenuOpen(true)}>
          <Ellipsis size={17} />
        </IconButton>
      }
      footer={footer}
    >
      <div className="space-y-3 px-4 py-4 pb-8">
        {/* sub-header card */}
        <section className="rounded-[22px] border border-line bg-surface p-4 shadow-key" aria-label={t("ls.listDetails")}>
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
                <CircleDollarSign size={11} aria-hidden /> {t("lg.countedInBalance")}
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
                label={t("ls.countPending")}
                hint={t("ls.pendingHint")}
              />
              {list.postToLedger && Math.abs(totals.pending) > 0.005 && (
                <p className="mt-1 flex items-center gap-1 text-[10.5px] font-semibold text-amber">
                  <CircleDollarSign size={12} className="shrink-0" aria-hidden />
                  <span>
                    {t("lg.pending")} +<Money value={totals.pending} currency={currency} /> · {t("ls.countPending")}
                  </span>
                </p>
              )}
            </div>
          )}

          {list.repeat !== "none" && (
            <Button variant="soft" size="sm" className="mt-3 w-full" onClick={dupNextPeriod}>
              <RepeatIcon size={13} /> {t("ls.duplicatedNext")}
            </Button>
          )}
        </section>

        {/* totals & charges */}
        {isCalc && (
          <section
            className="cursor-pointer rounded-[22px] border border-line bg-surface p-4 shadow-key transition hover:bg-surface2 active:scale-[0.99]"
            onClick={() => setTotalsOpen(true)}
            aria-label={t("ls.totalsTapToEdit")}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === "Enter" || e.key === " ") setTotalsOpen(true);
            }}
          >
            <div className="flex items-center justify-between">
              <p className="flex items-center gap-2 text-[12px] font-bold text-ink">
                <Receipt size={15} className="text-brand" aria-hidden /> {t("ls.totalsCharges")}
              </p>
              <span className="flex items-center gap-1 text-[10px] font-semibold text-mute">
                {t("act.edit")} <ChevronRight size={13} aria-hidden />
              </span>
            </div>
            <div className="mt-2.5 space-y-1 text-[11px] text-mute">
              <div className="flex justify-between">
                <span>{t("ls.subtotal")}</span>
                <span className="font-semibold text-ink">
                  <Money value={totals.subtotal} currency={currency} />
                </span>
              </div>
              {totals.listDiscount > 0 && (
                <div className="flex justify-between">
                  <span>{t("ls.discount")}</span>
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
                  <span>{t("ls.extraCharge")}</span>
                  <span className="font-semibold text-ink">
                    + <Money value={totals.extra} currency={currency} />
                  </span>
                </div>
              )}
              <div className="flex justify-between border-t border-line pt-1 text-[12px] font-bold text-ink">
                <span>{t("ls.total")}</span>
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
              label={t("ls.groupSameItems")}
              hint={t("ls.groupSameHint")}
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
                title={t("ls.noItems")}
                hint={t("ls.noItemsHintA")}
              />
            )}
          </div>
        ) : (
          <PlainItems list={list} />
        )}

        {/* add item */}
        <AddItemForm list={list} />

        {/* attached entities — banks, lists, notes, reminders, contacts & links */}
        <section className="rounded-[22px] border border-line bg-surface p-4 shadow-key" aria-label={t("ee.attached")}>
          <p className="text-[10px] font-bold uppercase tracking-[1.5px] text-mute">{t("ee.links")}</p>
          <div className="mt-2">
            <AttachEntities
              refs={list.refs ?? []}
              ownerId={list.ownerId}
              onChange={(next) => saveList({ id: list.id, ownerId: list.ownerId, title: list.title, refs: next })}
            />
          </div>
        </section>
      </div>

      {/* ⋯ menu */}
      <BottomSheet open={menuOpen} onClose={() => setMenuOpen(false)} title={list.title}>
        <div className="space-y-1">
          <ActionRow
            icon={Pencil}
            label={t("ls.renameEdit")}
            onClick={() => {
              setMenuOpen(false);
              ui.open({ type: "listEdit", listId: list.id, ownerId: list.ownerId });
            }}
          />
          <ActionRow
            icon={Copy}
            label={t("act.duplicate")}
            sub={t("ls.copyWithPayments")}
            onClick={() => {
              setMenuOpen(false);
              const nid = duplicateList(list.id);
              if (nid) {
                ui.open({ type: "list", listId: nid });
                toastOk(t("ls.listDuplicated"));
              }
            }}
          />
          <ActionRow
            icon={Bookmark}
            label={t("ls.saveAsTemplate")}
            onClick={() => {
              duplicateList(list.id, { asTemplate: true });
              setMenuOpen(false);
              toastOk(t("ls.savedAsTemplate"));
            }}
          />
          {isCalc && (
            <ActionRow
              icon={CircleCheckBig}
              label={t("ls.allPaid")}
              sub={t("ls.onePaymentPerRow")}
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
              label={t("ls.clearPayments")}
              danger
              onClick={() => void onClearPayments()}
            />
          )}
          <ActionRow
            icon={Share2}
            label={t("act.share") + "…"}
            onClick={() => {
              setMenuOpen(false);
              setShareOpen(true);
            }}
          />
          <ActionRow icon={Trash2} label={t("ls.deleteList")} danger onClick={() => void onDelete()} />
        </div>
      </BottomSheet>

      {/* share sheet */}
      <BottomSheet open={shareOpen} onClose={() => setShareOpen(false)} title={t("ls.shareList")}>
        <div className="space-y-1">
          <ActionRow
            icon={MessageCircle}
            label={t("act.whatsapp")}
            sub={phone ? `Send to ${ownerName}` : "No phone number available"}
            onClick={onWhatsApp}
          />
          <ActionRow icon={FileText} label={t("ls.pdfBill")} sub={t("ls.downloadBill")} onClick={onPdf} />
          <ActionRow icon={Copy} label={t("lg.copyText")} sub={t("lg.pasteAnywhere")} onClick={() => void onCopy()} />
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
          toastOk(t("ls.listMoved"));
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
  const t = useT();
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
      toastErr(t("ls.giveListTitle"));
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
      title={listId ? "Edit list" : t("ls.newList")}
      subtitle={`${KIND_META[k].label} · ${ownerName}`}
      onBack={() => ui.pop()}
      footer={
        <Button variant="brand" size="lg" className="w-full" onClick={save}>
          <Check size={16} /> {t("act.save")}
        </Button>
      }
    >
      <div className="space-y-4 px-4 py-4 pb-6">
        <Field label={t("ls.listTitle")}>
          <input
            autoFocus
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder={t("ls.listPlaceholder")}
            aria-label={t("ls.listTitle")}
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

        <Field label={t("ls.listNote")}>
          <textarea
            value={note}
            onChange={(e) => setNote(e.target.value)}
            rows={2}
            placeholder={t("lg.optionalNote")}
            aria-label={t("ls.listNote")}
            className={cx(inputCls, "resize-none")}
          />
        </Field>

        <Field label={t("ls.repeat")}>
          <select
            value={repeat}
            onChange={(e) => setRepeat(e.target.value as Repeat)}
            aria-label={t("ls.repeat")}
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
            label={t("ls.saveAsTemplate")}
            hint={t("ls.reuseTemplate")}
          />
          {k === "calculating" && (
            <div className="mt-1 border-t border-line pt-1">
              <Switch
                checked={postToLedger}
                onChange={setPostToLedger}
                label={t("ls.countPending")}
                hint={t("ls.countPendingHint")}
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
  kind: kindProp,
}: {
  entryId?: ID;
  ownerId: ID | "general";
  entryType?: "credit" | "debit";
  /** user-chosen label: income / expense / credit / debit (all four available
      in BOTH the General and person ledgers — maps onto type credit/debit) */
  kind?: EntryKind;
}) {
  const t = useT();
  const ui = useUI();
  const confirm = useConfirm();
  const existing = useApp((s) => (entryId ? s.entries.find((e) => e.id === entryId && !e.deletedAt) : undefined));
  const contacts = useApp((s) => s.contacts);
  const groups = useApp((s) => s.groups);
  const settings = useApp((s) => s.settings);
  const saveEntry = useApp((s) => s.saveEntry);
  const deleteEntry = useApp((s) => s.deleteEntry);
  const moveEntity = useApp((s) => s.moveEntity);
  const saveReminder = useApp((s) => s.saveReminder);
  const saveGroup = useApp((s) => s.saveGroup);
  const addCustomCategory = useApp((s) => s.addCustomCategory);
  const removeCustomCategory = useApp((s) => s.removeCustomCategory);

  const [type, setType] = useState<"credit" | "debit">(
    existing?.type ?? (kindProp === "income" || kindProp === "expense" ? (kindProp === "income" ? "credit" : "debit") : undefined) ?? entryType ?? "credit"
  );
  const [kind, setKind] = useState<EntryKind>(
    existing?.kind ?? kindProp ?? (entryType === "debit" ? "debit" : entryType === "credit" ? "credit" : undefined) ??
      (ownerId === "general" ? (type === "credit" ? "income" : "expense") : type)
  );
  const [amount, setAmount] = useState(existing && existing.amount > 0 ? String(existing.amount) : "");
  const [date, setDate] = useState(existing?.date ?? todayStr());
  const [dueOn, setDueOn] = useState(!!existing?.dueDate);
  const [dueDate, setDueDate] = useState(existing?.dueDate ?? todayStr());
  /** "" = none — saving "" clears the category (saveEntry keeps "" as-is) */
  const [category, setCategory] = useState(existing?.category ?? "");
  const [method, setMethod] = useState(existing?.method ?? "cash");
  const [note, setNote] = useState(existing?.note ?? "");
  const [owner, setOwner] = useState<OwnerId>(existing?.ownerId ?? ownerId);
  const [pickerOpen, setPickerOpen] = useState(false);
  /** attached entities — plain ids, saved together with the entry (no draft-first flow needed) */
  const [draftRefs, setDraftRefs] = useState<LinkRef[]>(existing?.refs ?? []);
  /** shopping-style line items — while any line exists the amount = Σ qty × price
      (the manual amount string stays in state so removing all lines restores it) */
  const [items, setItems] = useState<EntryItem[]>(existing?.items ?? []);
  /** group selection — "" = none (clears on save). New entries pick up the pending group
      parked by the group ⋯ “Add entry” action. */
  const [groupId, setGroupId] = useState<ID | "">(() => {
    if (!entryId) {
      const pending = takePendingEntryGroup();
      if (pending && pending.ownerId === ownerId) return pending.groupId;
    }
    return existing?.groupId ?? "";
  });
  const [newGroupOpen, setNewGroupOpen] = useState(false);
  const [newGroupTitle, setNewGroupTitle] = useState("");
  /* custom-category creation / management sheet */
  const [catSheetOpen, setCatSheetOpen] = useState(false);
  const [newCatName, setNewCatName] = useState("");

  const ownerContact = owner === "general" ? null : contacts.find((c) => c.id === owner) ?? null;
  const ownerName = ownerContact ? displayName(ownerContact) : "General";

  /** all four entry kinds — available in BOTH the General and person ledgers.
      Picking one sets the label (kind) and the math direction (type). */
  const kindOptions: { value: EntryKind; label: string; hint: string; tone: "pos" | "neg"; icon: typeof ArrowDownLeft }[] = [
    { value: "income", label: t("lg.kindIncome"), hint: t("lg.kindIncomeHint"), tone: "pos", icon: ArrowDownLeft },
    { value: "expense", label: t("lg.kindExpense"), hint: t("lg.kindExpenseHint"), tone: "neg", icon: ArrowUpRight },
    { value: "credit", label: t("lg.kindCredit"), hint: t("lg.kindCreditHint"), tone: "pos", icon: HandCoins },
    { value: "debit", label: t("lg.kindDebit"), hint: t("lg.kindDebitHint"), tone: "neg", icon: CreditCard },
  ];

  const selectKind = (k: EntryKind) => {
    setKind(k);
    setType(k === "income" || k === "credit" ? "credit" : "debit");
  };

  const kindLabel = t(
    kind === "income"
      ? "lg.kindIncome"
      : kind === "expense"
        ? "lg.kindExpense"
        : kind === "credit"
          ? "lg.kindCredit"
          : "lg.kindDebit"
  );

  /** built-in + user-added categories (chips) */
  const customCategories = settings.customCategories ?? [];
  const allCategories = useMemo(
    () => [...ENTRY_CATEGORIES, ...customCategories.filter((c) => !ENTRY_CATEGORIES.includes(c))],
    [customCategories]
  );

  /** live groups of the currently selected owner */
  const ownerGroups = useMemo(
    () => groups.filter((g) => g.ownerId === owner && !g.deletedAt),
    [groups, owner]
  );

  /** create a group right from the selector and select it */
  const createGroupInline = () => {
    const title = newGroupTitle.trim();
    if (!title) {
      toastErr(t("lg.giveGroupName"));
      return;
    }
    const g = saveGroup({ ownerId: owner, title });
    setGroupId(g.id);
    setNewGroupTitle("");
    setNewGroupOpen(false);
  };

  /** create a custom category, select it and close the sheet */
  const createCategory = () => {
    const name = newCatName.trim();
    if (!name) {
      toastErr(t("lg.giveCategoryName"));
      return;
    }
    addCustomCategory(name);
    setCategory(name);
    setNewCatName("");
    setCatSheetOpen(false);
  };

  const removeCategory = (name: string) => {
    removeCustomCategory(name);
    // the removed category can no longer stay selected
    if (category === name) setCategory("");
  };

  /* ---- item lines ------------------------------------------------------ */
  const hasItems = items.length > 0;
  const itemsSum = itemsTotal(items);

  const updateItem = (id: ID, patch: Partial<EntryItem>) =>
    setItems((arr) => arr.map((it) => (it.id === id ? { ...it, ...patch } : it)));

  const removeItem = (id: ID) => setItems((arr) => arr.filter((it) => it.id !== id));

  const addItem = () =>
    setItems((arr) => [...arr, { id: uid(), name: "", qty: 0, unit: "", price: 0 }]);

  const save = () => {
    /* with item lines: amount = Σ qty × price and every line must be complete;
       without lines: manual amount, and [] clears the lines on an existing entry
       (saveEntry keeps undefined as-is) */
    const savedItems: EntryItem[] = items.map((it) => ({
      ...it,
      name: it.name.trim(),
      qty: r2(+it.qty || 0),
      unit: it.unit?.trim() || undefined,
      price: r2(+it.price || 0),
    }));
    if (hasItems && savedItems.some((it) => !it.name || !(it.qty > 0) || !(it.price > 0))) {
      toastErr(t("lg.itemIncomplete"));
      return;
    }
    const amt = hasItems ? itemsTotal(savedItems) : parseNum(amount);
    if (amt <= 0) {
      toastErr(t("lg.amountGreaterThanZero"));
      return;
    }
    const saved = saveEntry({
      ...(entryId ? { id: entryId } : {}),
      ownerId: owner,
      type,
      kind,
      amount: r2(amt),
      items: savedItems,
      date,
      // "" clears an existing due date (saveEntry keeps undefined as-is)
      dueDate: dueOn && dueDate ? dueDate : "",
      // "" clears the category — it is optional now
      category: category.trim(),
      method,
      note: note.trim() || "",
      // "" clears the group (saveEntry keeps undefined as-is)
      groupId: groupId || "",
      refs: draftRefs,
    });
    if (entryId && owner !== existing?.ownerId) moveEntity("entry", saved.id, owner);
    // auto-alarm scenario — a due date on a NEW entry sets the payment reminder
    if (!entryId && dueOn && dueDate) {
      saveReminder({
        ownerId: owner,
        title: t("lg.paymentDue"),
        notes: note.trim() || undefined,
        kind: "payment",
        dueAt: new Date(`${dueDate}T09:00`).getTime(),
        links: [{ type: "entry", id: saved.id, label: category.trim() || kindLabel }],
      });
    }
    toastOk(entryId ? "Entry updated" : "Entry added");
    ui.pop();
  };

  const remove = async () => {
    const ok = await confirm({
      title: "Delete entry?",
      message: "The entry and its payment history move to Trash — you can undo from the toast.",
      confirmLabel: t("act.delete"),
      tone: "danger",
    });
    if (!ok) return;
    if (entryId) {
      deleteEntry(entryId);
      toastUndoLast();
    }
    ui.pop();
  };

  return (
    <Sheet
      title={entryId ? t("lg.editEntry") : t("lg.newEntry")}
      subtitle={`${ownerName} · ${t("tab.ledger")}`}
      onBack={() => ui.pop()}
      footer={
        <div className="flex gap-2.5">
          {entryId && (
            <Button variant="surface" size="lg" className="flex-1 text-neg" onClick={() => void remove()}>
              <Trash2 size={15} /> {t("act.delete")}
            </Button>
          )}
          <Button variant="brand" size="lg" className={entryId ? "flex-[2]" : "w-full"} onClick={save}>
            <Check size={16} /> {t("act.save")}
          </Button>
        </div>
      }
    >
      <div className="space-y-4 px-4 py-4 pb-6">
        {/* type — all four kinds in both the General and person ledgers */}
        <div className="grid grid-cols-2 gap-2">
          {kindOptions.map((o) => (
            <TypeBtn
              key={o.value}
              active={kind === o.value}
              onClick={() => selectKind(o.value)}
              icon={o.icon}
              label={o.label}
              sub={o.hint}
              tone={o.tone}
            />
          ))}
        </div>

        {/* big amount — read-only Σ of the item lines while any line exists */}
        <div className="rounded-[22px] border border-line bg-surface p-4 shadow-key">
          <Field label={t("lg.amount")}>
            <div className="flex w-full items-center gap-2">
              <input
                value={hasItems ? String(itemsSum) : amount}
                onChange={(e) => setAmount(e.target.value)}
                inputMode="decimal"
                placeholder="0"
                aria-label={t("lg.entryAmount")}
                disabled={hasItems}
                className={cx(
                  "w-full rounded-2xl border border-line bg-surface2 px-4 py-3 text-[28px] font-bold tabular-nums tracking-tight text-ink placeholder:text-mute/60 focus:border-brand/50 focus:ring-2 focus:ring-brand/15",
                  hasItems && "opacity-70"
                )}
              />
              {!hasItems && <CalcButton onPick={(v) => setAmount(String(v))} />}
            </div>
          </Field>
          {hasItems && (
            <p className="mt-2.5 text-[11px] font-semibold text-brand">
              {t("lg.autoFromItems", { n: itemsCount(items) })}
            </p>
          )}
          {existing && existing.payments.length > 0 && (
            <p className="mt-2.5 text-[11px] text-mute">
              {t("lg.paid")} <Money value={paidOf(existing)} /> of <Money value={existing.amount} /> ·{" "}
              <Money value={r2(existing.amount - paidOf(existing))} /> left
            </p>
          )}
        </div>

        {/* items — shopping-style lines (name + qty [+ unit] × unit price) */}
        <section
          className="rounded-[22px] border border-line bg-surface p-4 shadow-key"
          aria-label={t("lg.itemsTitle")}
        >
          <p className="text-[10px] font-bold uppercase tracking-[1.5px] text-mute">{t("lg.itemsTitle")}</p>
          <p className="mt-0.5 text-[10.5px] text-mute">{t("lg.itemsHint")}</p>

          {items.length === 0 ? (
            <p className="mt-2.5 text-[11px] text-mute">{t("lg.noItemsYet")}</p>
          ) : (
            <div className="mt-2.5 space-y-2">
              {items.map((it) => (
                <div key={it.id} className="rounded-2xl border border-line bg-surface2/60 p-2.5">
                  {/* name + remove */}
                  <div className="flex items-center gap-2">
                    <input
                      value={it.name}
                      onChange={(e) => updateItem(it.id, { name: e.target.value })}
                      placeholder={t("lg.itemNamePh")}
                      aria-label={t("lg.itemName")}
                      className={cx(inputCls, "py-2 text-[13px] font-semibold")}
                    />
                    <IconButton
                      label={t("lg.removeItem")}
                      className="h-9 w-9 shrink-0"
                      onClick={() => removeItem(it.id)}
                    >
                      <X size={14} />
                    </IconButton>
                  </div>
                  {/* qty · unit · unit price */}
                  <div className="mt-2 grid grid-cols-[0.8fr_1fr_1.2fr] gap-2">
                    <Field label={t("lg.qty")}>
                      <NumField
                        value={it.qty}
                        ariaLabel={t("lg.qty")}
                        placeholder={t("lg.qtyPh")}
                        className="px-2.5 text-center"
                        onCommit={(v) => updateItem(it.id, { qty: r2(v) })}
                      />
                    </Field>
                    <Field label={t("lg.unit")}>
                      <input
                        value={it.unit ?? ""}
                        onChange={(e) => updateItem(it.id, { unit: e.target.value })}
                        placeholder={t("lg.unitPh")}
                        aria-label={t("lg.unit")}
                        className={cx(inputCls, "px-2.5 py-2.5 text-center text-[13px]")}
                      />
                    </Field>
                    <Field label={t("lg.unitPrice")}>
                      <NumField
                        value={it.price}
                        ariaLabel={t("lg.unitPrice")}
                        className="px-2.5 text-right"
                        onCommit={(v) => updateItem(it.id, { price: r2(v) })}
                      />
                    </Field>
                  </div>
                  {/* computed line total */}
                  <p className="mt-1.5 flex items-baseline justify-end gap-1.5">
                    <span className="text-[10px] font-bold uppercase tracking-[1px] text-mute">
                      {t("lg.lineTotal")}
                    </span>
                    <span className="text-[13px] font-bold tabular-nums text-ink">
                      <Money value={r2((+it.qty || 0) * (+it.price || 0))} />
                    </span>
                  </p>
                </div>
              ))}
            </div>
          )}

          <Button variant="soft" size="sm" className="mt-2.5 w-full" onClick={addItem}>
            <Plus size={13} /> {t("lg.addItem")}
          </Button>
        </section>

        {/* date + method */}
        <div className="grid grid-cols-2 gap-2">
          <Field label={t("lg.date")}>
            <input
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              aria-label={t("lg.entryDate")}
              className={inputCls}
            />
          </Field>
          <Field label={t("lg.method")}>
            <select
              value={method}
              onChange={(e) => setMethod(e.target.value)}
              aria-label={t("lg.paymentMethod")}
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

        {/* category — optional; built-in + custom chips + manage sheet */}
        <section
          className="rounded-[22px] border border-line bg-surface p-4 shadow-key"
          aria-label={t("cat.category")}
        >
          <p className="text-[10px] font-bold uppercase tracking-[1.5px] text-mute">{t("cat.category")}</p>
          <div className="app-scroll mt-2 flex gap-1.5 overflow-x-auto pb-1" role="group" aria-label={t("cat.category")}>
            <Chip active={category === ""} onClick={() => setCategory("")}>
              {t("act.none")}
            </Chip>
            {allCategories.map((c) => (
              <Chip key={c} active={category === c} onClick={() => setCategory(c)}>
                {c}
              </Chip>
            ))}
            <Chip onClick={() => setCatSheetOpen(true)}>
              <span className="flex items-center gap-1">
                <Plus size={11} aria-hidden /> {t("cat.newCategory")}
              </span>
            </Chip>
          </div>
        </section>

        <div className="rounded-[22px] border border-line bg-surface p-4 shadow-key">
          <Switch
            checked={dueOn}
            onChange={setDueOn}
            label={t("lg.dueDate")}
            hint={t("ls.remindHint")}
          />
          {dueOn && (
            <div className="mt-2">
              <input
                type="date"
                value={dueDate}
                onChange={(e) => setDueDate(e.target.value)}
                aria-label={t("lg.dueDate")}
                className={inputCls}
              />
            </div>
          )}
        </div>

        <Field label={t("lg.note")}>
          <textarea
            value={note}
            onChange={(e) => setNote(e.target.value)}
            rows={2}
            placeholder={t("ls.whatFor")}
            aria-label={t("lg.entryNote")}
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
              <p className="text-[10px] font-bold uppercase tracking-[1.5px] text-mute">{t("ee.owner")}</p>
              <p className="truncate text-[13px] font-semibold text-ink">{ownerName}</p>
            </div>
            <Button variant="soft" size="sm" onClick={() => setPickerOpen(true)}>
              Change
            </Button>
          </div>
        </div>

        {/* group — every live group of the entry's owner + inline creation */}
        <section
          className="rounded-[22px] border border-line bg-surface p-4 shadow-key"
          aria-label={t("ee.selectGroup")}
        >
          <div className="flex items-center justify-between gap-2">
            <p className="text-[10px] font-bold uppercase tracking-[1.5px] text-mute">{t("ee.selectGroup")}</p>
            <span className="flex items-center gap-1 text-[10px] font-semibold text-mute">
              <Boxes size={12} aria-hidden />{" "}
              {ownerGroups.length === 1 ? t("lg.oneGroup") : t("lg.groupsCount", { n: ownerGroups.length })}
            </span>
          </div>
          <div className="app-scroll mt-2 flex gap-1.5 overflow-x-auto pb-1">
            <Chip active={groupId === ""} onClick={() => setGroupId("")}>
              {t("ee.noGroup")}
            </Chip>
            {ownerGroups.map((g) => (
              <Chip key={g.id} active={groupId === g.id} onClick={() => setGroupId(g.id)}>
                {g.title}
              </Chip>
            ))}
            <Chip active={newGroupOpen} onClick={() => setNewGroupOpen((v) => !v)}>
              <span className="flex items-center gap-1">
                <Plus size={11} aria-hidden /> {t("act.new")}
              </span>
            </Chip>
          </div>
          {newGroupOpen && (
            <div className="mt-2 flex items-center gap-2">
              <input
                autoFocus
                value={newGroupTitle}
                onChange={(e) => setNewGroupTitle(e.target.value)}
                placeholder={t("lg.groupName")}
                aria-label={t("lg.newGroupName")}
                className={inputCls}
                onKeyDown={(e) => {
                  if (e.key === "Enter") createGroupInline();
                }}
              />
              <Button variant="soft" size="sm" onClick={createGroupInline}>
                <Plus size={13} /> {t("act.add")}
              </Button>
            </div>
          )}
        </section>

        {/* attached entities — banks, lists, notes, reminders, contacts & links */}
        <section
          className="rounded-[22px] border border-line bg-surface p-4 shadow-key"
          aria-label={t("ee.attached")}
        >
          <p className="text-[10px] font-bold uppercase tracking-[1.5px] text-mute">{t("ee.links")}</p>
          <p className="mt-0.5 text-[10.5px] text-mute">
            Banks, lists, notes, reminders, contacts &amp; web links — saved with the entry.
          </p>
          <div className="mt-2">
            <AttachEntities refs={draftRefs} onChange={setDraftRefs} ownerId={owner} />
          </div>
        </section>
      </div>

      {/* create / manage custom categories */}
      <BottomSheet open={catSheetOpen} onClose={() => setCatSheetOpen(false)} title={t("cat.newCategory")} zIndex={70}>
        <Field label={t("cat.categoryName")}>
          <input
            autoFocus
            value={newCatName}
            onChange={(e) => setNewCatName(e.target.value)}
            placeholder={t("cat.categoryName")}
            aria-label={t("cat.categoryName")}
            className={inputCls}
            onKeyDown={(e) => {
              if (e.key === "Enter") createCategory();
            }}
          />
        </Field>
        <Button variant="brand" size="lg" className="mt-3 w-full" onClick={createCategory}>
          <Plus size={15} /> {t("cat.addCustom")}
        </Button>
        {customCategories.length > 0 && (
          <div className="mt-4">
            <p className="mb-1.5 text-[10px] font-bold uppercase tracking-[1.5px] text-mute">{t("cat.custom")}</p>
            <div className="space-y-1">
              {customCategories.map((c) => (
                <div
                  key={c}
                  className="flex items-center gap-2 rounded-2xl border border-line bg-surface2 px-3 py-2"
                >
                  <span className="min-w-0 flex-1 truncate text-[12.5px] font-semibold text-ink">{c}</span>
                  <IconButton
                    label={t("cat.removeCustom")}
                    className="h-9 w-9 shrink-0"
                    onClick={() => removeCategory(c)}
                  >
                    <Trash2 size={14} />
                  </IconButton>
                </div>
              ))}
            </div>
            <p className="mt-2 text-[10px] leading-4 text-mute">{t("cat.customHint")}</p>
          </div>
        )}
      </BottomSheet>

      <ContactPicker
        open={pickerOpen}
        onClose={() => setPickerOpen(false)}
        onPick={(id) => {
          setOwner(id as OwnerId);
          // groups belong to an owner — switching owners clears the selection
          setGroupId("");
          setNewGroupOpen(false);
        }}
        allowGeneral
      />
    </Sheet>
  );
}

/** paid-so-far helper for the EntryEdit amount hint */
function paidOf(e: LedgerEntry): number {
  return r2((e.payments || []).reduce((s, p) => s + (+p.amount || 0), 0));
}
