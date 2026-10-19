import {
  BackupFile,
  CalcList,
  CallLog,
  Contact,
  Email,
  LedgerEntry,
  LinkItem,
  MessageLog,
  Note,
  Phone,
  Reminder,
  BankAccount,
  AttachmentMeta,
  Settings,
} from "./types";
import { displayName } from "./utils";
import { blobGet } from "./db";
import { base64FromBlob, blobFromBase64, uid } from "./utils";

/* ================= vCard ================= */

const esc = (s: string) => (s || "").replace(/\\/g, "\\\\").replace(/;/g, "\\;").replace(/,/g, "\\,").replace(/\n/g, "\\n");

export function toVCard(contacts: Contact[], single = false): string {
  const cards = contacts.map((c) => {
    const lines = ["BEGIN:VCARD", "VERSION:3.0", `N:${esc(c.lastName)};${esc(c.firstName)};;;`, `FN:${esc(displayName(c))}`];
    if (c.nickname) lines.push(`NICKNAME:${esc(c.nickname)}`);
    if (c.company) lines.push(`ORG:${esc(c.company)};${esc(c.jobTitle || "")}`);
    c.phones.forEach((p) => {
      const type = p.label === "mobile" ? "CELL" : p.label.toUpperCase();
      lines.push(`TEL;TYPE=${type}${p.primary ? ",PREF" : ""}:${p.number}`);
    });
    c.emails.forEach((e) => lines.push(`EMAIL;TYPE=${e.label.toUpperCase()}${e.primary ? ",PREF" : ""}:${e.address}`));
    c.addresses.forEach((a) => lines.push(`ADR;TYPE=${a.label.toUpperCase()}:;;;;${esc(a.text)};;;;`));
    if (c.birthday) lines.push(`BDAY:${c.birthday}`);
    if (c.note) lines.push(`NOTE:${esc(c.note)}`);
    c.links.forEach((l: LinkItem) => lines.push(`URL:${l.url}`));
    c.socials.forEach((s) => {
      if (s.app === "telegram") lines.push(`X-SOCIALPROFILE;TYPE=telegram:${s.handle}`);
      if (s.app === "whatsapp") lines.push(`X-SOCIALPROFILE;TYPE=whatsapp:${s.handle}`);
    });
    c.customFields.forEach((f) => lines.push(`X-CUSTOM-${f.key.toUpperCase().replace(/\s+/g, "_")}:${esc(f.value)}`));
    if (c.tags.length) lines.push(`CATEGORIES:${c.tags.map(esc).join(",")}`);
    lines.push(`REV:${new Date(c.updatedAt).toISOString()}`);
    lines.push("END:VCARD");
    return lines.join("\r\n");
  });
  return single && cards.length === 1 ? cards[0] : cards.join("\r\n");
}

/** Parse a .vcf file — handles 2.1/3.0/4.0 basics incl. quoted-printable + folded lines. */
export function parseVCard(text: string): Partial<Contact>[] {
  const unfolded = text.replace(/\r\n[ \t]/g, "").replace(/\n[ \t]/g, "");
  const blocks = unfolded.split(/BEGIN:VCARD/i).filter((b) => /END:VCARD/i.test(b));
  const out: Partial<Contact>[] = [];

  for (const block of blocks) {
    const body = block.replace(/END:VCARD/i, "");
    const lines = body.split(/\r?\n/).filter((l) => l.trim());
    const props: Record<string, string[]> = {};
    for (const line of lines) {
      const idx = line.indexOf(":");
      if (idx < 0) continue;
      let key = line.slice(0, idx).trim();
      let value = line.slice(idx + 1).trim();
      if (/ENCODING=QUOTED-PRINTABLE/i.test(key)) {
        value = value
          .replace(/=([0-9A-F]{2})/gi, (_, h) => String.fromCharCode(parseInt(h, 16)))
          .replace(/&amp;/g, "&");
        key = key.replace(/ENCODING=QUOTED-PRINTABLE/i, "");
      }
      key = key.split(";")[0].toUpperCase();
      (props[key] ||= []).push(value);
    }

    const fn = props.FN?.[0] || "";
    const n = props.N?.[0] || "";
    const [last = "", first = ""] = n ? n.split(";") : [];
    const name = fn || `${first} ${last}`.trim();

    const phones: Phone[] = [];
    (props.TEL || []).forEach((tel, i) => {
      phones.push({ id: uid(), label: "mobile", number: tel, primary: i === 0 });
    });
    const emails: Email[] = [];
    (props.EMAIL || []).forEach((mail, i) => {
      emails.push({ id: uid(), label: "home", address: mail, primary: i === 0 });
    });
    const links: LinkItem[] = (props.URL || []).map((u) => ({ id: uid(), label: "website", url: u }));

    if (!name && !phones.length && !emails.length) continue;
    out.push({
      firstName: first || name,
      lastName: last,
      company: props.ORG?.[0]?.split(";")[0],
      note: props.NOTE?.[0],
      birthday: props.BDAY?.[0],
      phones,
      emails,
      links,
    });
  }
  return out;
}

/* ================= CSV ================= */

const csvEscape = (v: string | number) => {
  const s = String(v ?? "");
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
};

export function contactsToCSV(contacts: Contact[]): string {
  const header = [
    "Name", "Given Name", "Family Name", "Phone 1 - Type", "Phone 1 - Value",
    "Phone 2 - Type", "Phone 2 - Value", "Email 1 - Type", "Email 1 - Value",
    "Email 2 - Type", "Email 2 - Value", "Organization Name", "Organization Title",
    "Birthday", "Notes", "Website 1 - Value",
  ];
  const rows = contacts.map((c) => [
    displayName(c), c.firstName, c.lastName,
    c.phones[0]?.label ?? "", c.phones[0]?.number ?? "",
    c.phones[1]?.label ?? "", c.phones[1]?.number ?? "",
    c.emails[0]?.label ?? "", c.emails[0]?.address ?? "",
    c.emails[1]?.label ?? "", c.emails[1]?.address ?? "",
    c.company ?? "", c.jobTitle ?? "", c.birthday ?? "", c.note ?? "", c.links[0]?.url ?? "",
  ]);
  return [header, ...rows].map((r) => r.map(csvEscape).join(",")).join("\r\n");
}

/** Generic CSV parse — auto-detects Google Contacts export or simple headers. */
export function parseContactsCSV(text: string): Partial<Contact>[] {
  const lines = splitCSV(text);
  if (!lines.length) return [];
  const header = lines[0].map((h) => h.toLowerCase().trim());
  const out: Partial<Contact>[] = [];

  const idx = (...names: string[]) => header.findIndex((h) => names.some((n) => h === n || h.startsWith(n)));

  const nameI = idx("name", "full name", "display name");
  const firstI = idx("given name", "first name");
  const lastI = idx("family name", "last name", "surname");
  const orgI = idx("organization name", "company", "organization");
  const noteI = idx("notes", "note");
  const bdayI = idx("birthday", "birth date");
  const phoneCols = header
    .map((h, i) => ({ h, i }))
    .filter(({ h }) => h.startsWith("phone") && (h.includes("value") || h.includes("number") || /^phone\s*\d*$/.test(h)))
    .map(({ i }) => i);
  const emailCols = header
    .map((h, i) => ({ h, i }))
    .filter(({ h }) => (h.startsWith("e-mail") || h.startsWith("email")) && (h.includes("value") || h.includes("address") || h === "email" || h === "e-mail" || /^e?mail\s*\d*$/.test(h)))
    .map(({ i }) => i);

  for (let r = 1; r < lines.length; r++) {
    const row = lines[r];
    if (!row.some((c) => c.trim())) continue;
    const name = nameI >= 0 ? row[nameI] : "";
    const first = firstI >= 0 ? row[firstI] : name.split(/\s+/)[0] || "";
    const last = lastI >= 0 ? row[lastI] : name.split(/\s+/).slice(1).join(" ");
    const phones: Phone[] = phoneCols
      .map((i) => row[i])
      .filter(Boolean)
      .map((number, i2) => ({ id: uid(), label: "mobile", number, primary: i2 === 0 }));
    const emails: Email[] = emailCols
      .map((i) => row[i])
      .filter(Boolean)
      .map((address, i2) => ({ id: uid(), label: "home", address, primary: i2 === 0 }));
    if (!name && !phones.length && !emails.length) continue;
    out.push({
      firstName: first,
      lastName: last,
      company: orgI >= 0 ? row[orgI] : undefined,
      note: noteI >= 0 ? row[noteI] : undefined,
      birthday: bdayI >= 0 ? row[bdayI] : undefined,
      phones,
      emails,
    });
  }
  return out;
}

export function entriesToCSV(entries: LedgerEntry[], contacts: Contact[]): string {
  const header = ["Date", "Type", "Contact", "Amount", "Paid", "Remaining", "Category", "Method", "Note", "Payments"];
  const rows = entries.map((e) => {
    const contact = e.ownerId === "general" ? "" : displayName(contacts.find((c) => c.id === e.ownerId));
    const paid = (e.payments || []).reduce((s, p) => s + p.amount, 0);
    const payments = (e.payments || []).map((p) => `${p.date}: ${p.amount}`).join(" | ");
    return [
      e.date, e.type, contact, e.amount, paid, Math.round((e.amount - paid) * 100) / 100,
      e.category ?? "", e.method ?? "", e.note ?? "", payments,
    ];
  });
  return [header, ...rows].map((r) => r.map(csvEscape).join(",")).join("\r\n");
}

function splitCSV(text: string): string[][] {
  const rows: string[][] = [];
  let cur: string[] = [];
  let val = "";
  let quoted = false;
  const src = text.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
  for (let i = 0; i < src.length; i++) {
    const ch = src[i];
    if (quoted) {
      if (ch === '"') {
        if (src[i + 1] === '"') {
          val += '"';
          i++;
        } else quoted = false;
      } else val += ch;
    } else if (ch === '"') {
      quoted = true;
    } else if (ch === ",") {
      cur.push(val);
      val = "";
    } else if (ch === "\n") {
      cur.push(val);
      rows.push(cur);
      cur = [];
      val = "";
    } else val += ch;
  }
  if (val || cur.length) {
    cur.push(val);
    rows.push(cur);
  }
  return rows;
}

/* ================= Backup ================= */

export async function buildBackup(
  data: {
    contacts: Contact[];
    entries: LedgerEntry[];
    lists: CalcList[];
    notes: Note[];
    reminders: Reminder[];
    banks: BankAccount[];
    recents: CallLog[];
    messages: MessageLog[];
    attachments: AttachmentMeta[];
    settings: Partial<Settings>;
  },
  getBlob: (id: string) => Promise<Blob | undefined>
): Promise<Blob> {
  const files: BackupFile["files"] = [];
  for (const a of data.attachments) {
    const blob = await getBlob(a.id);
    if (!blob) continue;
    files.push({ id: a.id, name: a.name, type: a.mimeType, d: await base64FromBlob(blob) });
  }
  const backup: BackupFile = {
    app: "callmony",
    version: 1,
    exportedAt: Date.now(),
    data: {
      contacts: data.contacts,
      entries: data.entries,
      lists: data.lists,
      notes: data.notes,
      reminders: data.reminders,
      banks: data.banks,
      recents: data.recents,
      messages: data.messages,
      settings: data.settings,
      attachments: data.attachments,
    },
    files,
  };
  return new Blob([JSON.stringify(backup)], { type: "application/json" });
}

export async function parseBackupFile(file: File): Promise<BackupFile> {
  const text = await file.text();
  const json = JSON.parse(text) as BackupFile;
  if (json.app !== "callmony" && json.app !== "dial-ledger")
    throw new Error("Not a callmony backup file");
  return json;
}

export const backupFilesToBlobs = async (backup: BackupFile): Promise<{ id: string; blob: Blob }[]> => {
  const out: { id: string; blob: Blob }[] = [];
  for (const f of backup.files || []) {
    try {
      out.push({ id: f.id, blob: blobFromBase64(f.d) });
    } catch {
      /* skip bad file */
    }
  }
  return out;
};

export { blobGet };
