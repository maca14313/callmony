/* Exporters — vCard 3.0, CSV (Google Contacts compatible), statements
   and bill texts. All output is shared through the native share sheet. */

import { shareText } from "./share";
import { fd, money } from "./utils";
import { entryRemaining, entrySignedRemaining, listTotals, lineTotal } from "./calc";
import type { CalcList, DeviceContact, LedgerEntry } from "./types";
import { useApp } from "./store";

const esc = (s: string): string => (s || "").replace(/([,;\\])/g, "\\$1").replace(/\n/g, "\\n");

/* ---------------- vCard ---------------- */

export const contactToVcard = (c: DeviceContact): string => {
  const lines: string[] = ["BEGIN:VCARD", "VERSION:3.0"];
  lines.push(`N:${esc(c.familyName)};${esc(c.givenName)};;;`);
  lines.push(`FN:${esc(c.displayName)}`);
  if (c.company) lines.push(`ORG:${esc(c.company)}`);
  if (c.jobTitle) lines.push(`TITLE:${esc(c.jobTitle)}`);
  c.phones.forEach((p) => lines.push(`TEL;TYPE=${(p.label || "mobile").toUpperCase()}:${esc(p.number)}`));
  c.emails.forEach((e) => lines.push(`EMAIL;TYPE=${(e.label || "internet").toUpperCase()}:${esc(e.address)}`));
  c.addresses.forEach((a) => lines.push(`ADR;TYPE=${(a.label || "home").toUpperCase()}:;;${esc(a.formatted)};;;;`));
  if (c.birthday) lines.push(`BDAY:${c.birthday}`);
  if (c.note) lines.push(`NOTE:${esc(c.note)}`);
  lines.push("END:VCARD");
  return lines.join("\n");
};

export const shareContactVcard = async (c: DeviceContact): Promise<boolean> =>
  shareText(`${c.displayName}.vcf`, contactToVcard(c));

export const exportContactsVcard = async (): Promise<boolean> => {
  const contacts = useApp.getState().deviceContacts;
  if (!contacts.length) return false;
  const body = contacts.map(contactToVcard).join("\n");
  return shareText("callmony-contacts.vcf", body);
};

/* ---------------- CSV ---------------- */

const csvCell = (v: unknown): string => {
  const s = String(v ?? "");
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
};
const csvRow = (cells: unknown[]): string => cells.map(csvCell).join(",");

export const contactsToCsv = (contacts: DeviceContact[]): string => {
  const head = csvRow([
    "Name", "Given Name", "Family Name", "Phone 1 - Type", "Phone 1 - Value",
    "Phone 2 - Type", "Phone 2 - Value", "Email 1 - Type", "Email 1 - Value",
    "Organization Name", "Organization Title", "Birthday", "Notes",
  ]);
  const rows = contacts.map((c) =>
    csvRow([
      c.displayName, c.givenName, c.familyName,
      c.phones[0]?.label ?? "", c.phones[0]?.number ?? "",
      c.phones[1]?.label ?? "", c.phones[1]?.number ?? "",
      c.emails[0]?.label ?? "", c.emails[0]?.address ?? "",
      c.company ?? "", c.jobTitle ?? "", c.birthday ?? "", c.note ?? "",
    ])
  );
  return [head, ...rows].join("\n");
};

export const exportContactsCsv = async (): Promise<boolean> => {
  const contacts = useApp.getState().deviceContacts;
  if (!contacts.length) return false;
  return shareText("callmony-contacts.csv", contactsToCsv(contacts));
};

export const entriesToCsv = (entries: LedgerEntry[]): string => {
  const head = csvRow(["Date", "Type", "Kind", "Amount", "Paid", "Remaining", "Category", "Method", "Due date", "Note", "Items"]);
  const rows = entries.map((e) =>
    csvRow([
      e.date, e.type, e.kind ?? "", e.amount,
      e.payments.reduce((s, p) => s + p.amount, 0),
      entryRemaining(e), e.category ?? "", e.method ?? "", e.dueDate ?? "",
      e.note ?? "",
      (e.items ?? []).map((i) => `${i.name} ${i.qty}${i.unit ? ` ${i.unit}` : ""} × ${i.price}`).join("; "),
    ])
  );
  return [head, ...rows].join("\n");
};

export const exportTransactionsCsv = async (): Promise<boolean> => {
  const entries = useApp.getState().entries.filter((e) => !e.deletedAt);
  if (!entries.length) return false;
  return shareText("callmony-transactions.csv", entriesToCsv(entries));
};

/* ---------------- statement (per owner) ---------------- */

export const statementText = (ownerName: string, entries: LedgerEntry[]): string => {
  const currency = useApp.getState().settings.currency;
  const sorted = [...entries].sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : a.createdAt - b.createdAt));
  let running = 0;
  const lines = sorted.map((e) => {
    const signed = entrySignedRemaining(e);
    running = Math.round((running + signed) * 100) / 100;
    const itemBits = (e.items ?? []).map((i) => `  ${i.name} ${i.qty}${i.unit ? ` ${i.unit}` : ""} × ${i.price} = ${i.qty * i.price}`);
    return `${fd(e.date)}  ${e.kind || e.type}  ${money(e.amount, currency)}${itemBits.length ? `\n${itemBits.join("\n")}` : ""}\n  → balance ${money(running, currency)}`;
  });
  const total = sorted.reduce((s, e) => s + entrySignedRemaining(e), 0);
  return [`callmony · ${ownerName}`, "".padEnd(28, "—"), ...lines, "".padEnd(28, "—"), `Net: ${money(total, currency)}`].join("\n");
};

export const shareStatement = async (ownerName: string, ownerId: string): Promise<boolean> => {
  const entries = useApp.getState().entries.filter((e) => e.ownerId === ownerId && !e.deletedAt);
  if (!entries.length) return false;
  return shareText(`${ownerName}-statement.txt`, statementText(ownerName, entries));
};

/* ---------------- list bill text ---------------- */

export const listShareText = (l: CalcList): string => {
  const currency = l.currency || useApp.getState().settings.currency;
  const totals = listTotals(l);
  const lines = l.items.map(
    (i) => `${i.checked ? "✓" : "•"} ${i.name} ${i.qty}${i.unit ? ` ${i.unit}` : ""} × ${i.unitPrice} = ${lineTotal(i)}`
  );
  return [
    `callmony · ${l.title}`,
    "".padEnd(24, "—"),
    ...lines,
    "".padEnd(24, "—"),
    `Subtotal: ${money(totals.subtotal, currency)}`,
    ...(totals.listDiscount ? [`Discount: −${money(totals.listDiscount, currency)}`] : []),
    ...(totals.listTax ? [`Tax: +${money(totals.listTax, currency)}`] : []),
    ...(totals.extra ? [`Extra: +${money(totals.extra, currency)}`] : []),
    `Total: ${money(totals.grand, currency)}`,
    `Paid: ${money(totals.paid, currency)} · Pending: ${money(totals.pending, currency)}`,
  ].join("\n");
};
