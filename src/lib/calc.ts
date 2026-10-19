import type { CalcList, LedgerEntry, ListItem, Payment, Repeat } from "./types";

/** Round to 2 decimals — used on every money computation. */
export const r2 = (n: number): number => Math.round((n + Number.EPSILON) * 100) / 100;

export const sumPayments = (pays: Payment[] | undefined): number =>
  r2((pays || []).reduce((s, p) => s + (+p.amount || 0), 0));

/* ---------------- Ledger ---------------- */

/** remaining on an entry; negative = overpaid → direction flips */
export const entryRemaining = (e: LedgerEntry): number =>
  r2((+e.amount || 0) - sumPayments(e.payments));

/** signed remaining: credit positive (they owe you), debit negative */
export const entrySignedRemaining = (e: LedgerEntry): number =>
  r2((e.type === "credit" ? 1 : -1) * entryRemaining(e));

/**
 * Balance for an owner (contact or 'general').
 * positive → they owe you (green) · negative → you owe them (red) · 0 → settled
 */
export const ownerBalance = (entries: LedgerEntry[], ownerId: string): number =>
  r2(
    entries
      .filter((e) => e.ownerId === ownerId && !e.deletedAt)
      .reduce((s, e) => s + entrySignedRemaining(e), 0)
  );

/** Pending from calculating lists that count in the owner's balance (postToLedger ON) */
export const openListsPending = (lists: CalcList[], ownerId: string): number =>
  r2(
    lists
      .filter((l) => l.ownerId === ownerId && !l.deletedAt && l.postToLedger && !l.isTemplate)
      .reduce((s, l) => s + listTotals(l).pending, 0)
  );

/** Full balance incl. open lists — what the contact header shows. */
export const ownerFullBalance = (
  entries: LedgerEntry[],
  lists: CalcList[],
  ownerId: string
): number => r2(ownerBalance(entries, ownerId) + openListsPending(lists, ownerId));

export const totalsForOwner = (entries: LedgerEntry[], ownerId: string) => {
  const mine = entries.filter((e) => e.ownerId === ownerId && !e.deletedAt);
  const credit = r2(
    mine.filter((e) => e.type === "credit").reduce((s, e) => s + entrySignedRemaining(e), 0)
  );
  const debit = r2(
    mine.filter((e) => e.type === "debit").reduce((s, e) => s + -entrySignedRemaining(e), 0)
  );
  return { credit, debit, net: r2(credit - debit) };
};

/* ---------------- Calculating lists ---------------- */

/** line total = qty × price − row discount + row tax */
export const lineTotal = (i: ListItem): number => {
  const base = r2((+i.qty || 0) * (+i.unitPrice || 0));
  let t = base;
  if (i.discount) {
    t = r2(t - (i.discount.type === "pct" ? r2((base * i.discount.value) / 100) : i.discount.value));
  }
  if (i.taxPct) t = r2(t + r2((t * i.taxPct) / 100));
  return Math.max(t, 0);
};

export const itemPaid = (i: ListItem): number => sumPayments(i.payments);

export const itemRemaining = (i: ListItem): number => r2(lineTotal(i) - itemPaid(i));

export interface ListTotals {
  subtotal: number;
  listDiscount: number;
  listTax: number;
  extra: number;
  grand: number;
  paid: number;
  pending: number;
  count: number;
  paidCount: number;
}

export const listTotals = (l: CalcList): ListTotals => {
  const subtotal = r2(l.items.reduce((s, i) => s + lineTotal(i), 0));
  let listDiscount = 0;
  if (l.discount) {
    listDiscount =
      l.discount.type === "pct" ? r2((subtotal * l.discount.value) / 100) : l.discount.value;
  }
  const afterDiscount = r2(subtotal - listDiscount);
  const listTax = l.taxPct ? r2((afterDiscount * l.taxPct) / 100) : 0;
  const extra = l.extraCharge || 0;
  const grand = r2(afterDiscount + listTax + extra);
  const paid = r2(l.items.reduce((s, i) => s + itemPaid(i), 0));
  const pending = r2(grand - paid);
  const count = l.items.length;
  const paidCount = l.items.filter((i) => itemRemaining(i) <= 0.005 && lineTotal(i) > 0).length +
    l.items.filter((i) => lineTotal(i) === 0 && i.checked).length;
  return { subtotal, listDiscount, listTax, extra, grand, paid, pending, count, paidCount };
};

/** group rows with same name+unit for display (raw rows stay intact) */
export const groupItems = (items: ListItem[]) => {
  const map = new Map<string, ListItem[]>();
  items.forEach((i) => {
    const key = `${(i.name || "").trim().toLowerCase()}|${(i.unit || "").trim().toLowerCase()}`;
    const arr = map.get(key) || [];
    arr.push(i);
    map.set(key, arr);
  });
  return [...map.values()];
};

/* ---------------- Reminder recurrence ---------------- */

const addDays = (d: Date, n: number) => new Date(d.getTime() + n * 86400000);
const sameDay = (a: Date, b: Date) =>
  a.getFullYear() === b.getFullYear() &&
  a.getMonth() === b.getMonth() &&
  a.getDate() === b.getDate();

/** Next occurrence strictly after `from` for a repeating reminder. */
export const nextOccurrence = (dueAt: number, repeat: Repeat, from = Date.now()): number => {
  if (repeat === "none") return dueAt;
  const d = new Date(dueAt);
  const f = new Date(from);
  let next = d;
  let guard = 0;
  while (next.getTime() <= from && guard < 500) {
    guard++;
    switch (repeat) {
      case "daily":
        next = addDays(next, 1);
        break;
      case "weekly":
        next = addDays(next, 7);
        break;
      case "monthly": {
        const n = new Date(next);
        const day = n.getDate();
        n.setDate(1);
        n.setMonth(n.getMonth() + 1);
        const dim = new Date(n.getFullYear(), n.getMonth() + 1, 0).getDate();
        n.setDate(Math.min(day, dim));
        next = n;
        break;
      }
      case "yearly": {
        const n = new Date(next);
        const day = n.getDate();
        const month = n.getMonth();
        n.setFullYear(n.getFullYear() + 1);
        n.setDate(1);
        n.setMonth(month);
        const dim = new Date(n.getFullYear(), n.getMonth() + 1, 0).getDate();
        n.setDate(Math.min(day, dim));
        next = n;
        break;
      }
      default:
        return next.getTime();
    }
  }
  // for daily repeats, if fired today already, move to tomorrow
  if (repeat === "daily" && sameDay(next, f) && next.getTime() <= from) {
    next = addDays(next, 1);
  }
  return next.getTime();
};

/** Due status for display */
export const dueStatus = (dueAt: number, done?: boolean): "overdue" | "today" | "upcoming" => {
  if (done) return "upcoming";
  const now = new Date();
  const d = new Date(dueAt);
  if (d.getTime() <= now.getTime()) return "overdue";
  if (sameDay(d, now)) return "today";
  return "upcoming";
};
