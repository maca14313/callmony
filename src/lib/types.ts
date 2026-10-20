/* ---------------------------------------------------------------
   callmony — data model
   Every record: id, createdAt, updatedAt, deletedAt (soft delete)
   ownerId: contact id | 'general'  (null-ish = General space)
   Money: numbers in major currency units, rounded via r2()
---------------------------------------------------------------- */

export type ID = string;
export type OwnerId = ID | "general";

export interface Phone {
  id: ID;
  label: string; // mobile | work | home | main | other | custom
  number: string;
  primary?: boolean;
}
export interface Email {
  id: ID;
  label: string;
  address: string;
  primary?: boolean;
}
export interface LinkItem {
  id: ID;
  label: string;
  url: string;
}
export interface Social {
  id: ID;
  app: "telegram" | "whatsapp" | "signal" | "instagram" | "x" | "other";
  handle: string; // username or phone
}
export interface Address {
  id: ID;
  label: string;
  text: string;
}
export interface CustomField {
  id: ID;
  key: string;
  value: string;
}

export interface Contact {
  id: ID;
  firstName: string;
  lastName: string;
  nickname?: string;
  company?: string;
  jobTitle?: string;
  phones: Phone[];
  emails: Email[];
  links: LinkItem[];
  socials: Social[];
  addresses: Address[];
  customFields: CustomField[];
  birthday?: string; // YYYY-MM-DD
  tags: string[];
  favorite: boolean;
  pinnedNote?: string;
  note?: string;
  photoId?: ID; // attachment id
  color?: string; // avatar bg
  textColor?: string;
  speedDial?: string; // '2'..'9'
  createdAt: number;
  updatedAt: number;
  deletedAt?: number;
}

/** A single payment record — every payment keeps its own row. */
export interface Payment {
  id: ID;
  amount: number;
  date: string; // YYYY-MM-DD
  note?: string;
  method?: string;
  auto?: "tick" | "settle"; // created by ticking a row / settle-up
  refs?: LinkRef[]; // attached banks / lists / notes / reminders / owners / links
  createdAt: number;
}

/**
 * Ledger entry.
 * credit  = "they owe you" (+)  · for General: income
 * debit   = "you owe them" (−)  · for General: expense
 * remaining = amount − Σ payments; negative remaining flips direction.
 */

/** How the entry type was chosen — display label (math: credit=+, debit=−). */
export type EntryKind = "income" | "expense" | "credit" | "debit";

export interface LedgerEntry {
  id: ID;
  ownerId: OwnerId;
  type: "credit" | "debit";
  kind?: EntryKind; // income/expense/credit/debit — user's chosen label (both contexts)
  amount: number;
  payments: Payment[];
  date: string; // YYYY-MM-DD
  dueDate?: string;
  category?: string;
  method?: string;
  note?: string;
  listId?: ID; // created from a calculating list
  groupId?: ID; // ledger group this entry belongs to
  refs?: LinkRef[]; // attached banks / lists / notes / reminders / owners / links
  attachments: ID[];
  createdAt: number;
  updatedAt: number;
  deletedAt?: number;
}

/** A named group of ledger entries — shows its own calculated totals. */
export interface LedgerGroup {
  id: ID;
  ownerId: OwnerId;
  title: string;
  note?: string;
  refs: LinkRef[]; // attached banks / lists / notes / reminders / owners / links
  createdAt: number;
  updatedAt: number;
  deletedAt?: number;
}

export interface ListDiscount {
  type: "pct" | "amount";
  value: number;
}

export interface ListItem {
  id: ID;
  name: string;
  qty: number;
  unit?: string; // pcs, kg, hrs...
  unitPrice: number;
  discount?: ListDiscount; // per-row discount
  taxPct?: number; // per-row tax
  payments: Payment[];
  checked: boolean; // ticked = fully paid
  date?: string; // day this row was taken (same item, different days)
  dueDate?: string;
  priority?: "low" | "medium" | "high";
  note?: string;
  sortOrder: number;
  attachments?: ID[];
  createdAt: number;
  updatedAt: number;
}

export type ListKind = "calculating" | "simple" | "checklist" | "todo";
export type Repeat = "none" | "daily" | "weekly" | "monthly" | "yearly";

export interface CalcList {
  id: ID;
  ownerId: OwnerId;
  kind: ListKind;
  title: string;
  note?: string;
  discount?: ListDiscount; // whole-list discount
  taxPct?: number; // whole-list tax %
  extraCharge?: number; // delivery etc.
  isTemplate: boolean;
  postToLedger: boolean; // count pending in owner's balance
  repeat: Repeat;
  currency?: string;
  items: ListItem[];
  attachments: ID[];
  refs?: LinkRef[]; // attached banks / notes / reminders / owners / links
  createdAt: number;
  updatedAt: number;
  deletedAt?: number;
}

export interface LinkRef {
  type: "contact" | "entry" | "list" | "note" | "reminder" | "bank" | "item" | "link";
  id: ID;
  label?: string;
  url?: string; // for type === "link"
}

export interface Note {
  id: ID;
  ownerId: OwnerId;
  title: string;
  body: string;
  tags: string[];
  pinned: boolean;
  color?: string;
  links: LinkRef[];
  attachments: ID[];
  createdAt: number;
  updatedAt: number;
  deletedAt?: number;
}

export type ReminderKind = "payment" | "todo" | "birthday" | "followup" | "custom";

export interface Reminder {
  id: ID;
  ownerId: OwnerId;
  title: string;
  notes?: string;
  kind: ReminderKind;
  dueAt: number; // epoch ms
  repeat: Repeat;
  leadMinutes?: number[]; // fire early too, e.g. [1440, 60]
  links: LinkRef[];
  done: boolean;
  doneAt?: number;
  snoozedUntil?: number;
  lastFiredAt?: number;
  firedLeads?: number[];
  attachments: ID[];
  createdAt: number;
  updatedAt: number;
  deletedAt?: number;
}

export interface BankAccount {
  id: ID;
  ownerId: OwnerId;
  label: string;
  holderName?: string;
  bankName?: string;
  accountNumber?: string;
  iban?: string;
  swift?: string;
  upiId?: string;
  branch?: string;
  notes?: string;
  balance?: number;
  currency?: string;
  attachments: ID[];
  createdAt: number;
  updatedAt: number;
  deletedAt?: number;
}

export interface CallLog {
  id: ID;
  phone: string; // normalized display
  contactId?: ID;
  direction: "incoming" | "outgoing" | "missed";
  at: number;
  duration: number; // seconds
  via: "app" | "tel";
  note?: string;
  createdAt: number;
}

export interface MessageLog {
  id: ID;
  ownerId: OwnerId;
  channel: "whatsapp" | "sms" | "telegram" | "email" | "other";
  text: string;
  date: number;
  createdAt: number;
}

export type AttachmentParentType =
  | "contact"
  | "entry"
  | "list"
  | "item"
  | "note"
  | "reminder"
  | "bank"
  | "message";

/** Metadata — the bytes live in the blobs store keyed by id. */
export interface AttachmentMeta {
  id: ID;
  parentId: ID;
  parentType: AttachmentParentType;
  name: string;
  mimeType: string;
  size: number;
  kind: "image" | "pdf" | "file";
  thumb?: string; // small data-url thumbnail for images
  createdAt: number;
}

export interface Settings {
  theme: "light" | "dark" | "auto";
  language?: "en" | "am" | "om" | "ar" | "ti";
  currency: string; // 'ETB' | 'USD' ...
  countryCode: string; // 'ET' default dial code +251
  pinHash?: { hash: string; salt: string };
  autolockMin: number; // 0 = lock immediately on blur
  hideBalances: boolean;
  /** outgoing calls: "dialer" = real call via the phone's default caller app (tel:), "demo" = in-app simulation */
  callMode?: "dialer" | "demo";
  /** user-added custom ledger categories (on top of the built-in ones) */
  customCategories?: string[];
  startTab?: string;
  lastBackupAt?: number;
  notifAsked: boolean;
  onboardingDone?: boolean;
  demoSeeded?: boolean;
  blockedNumbers: string[];
  speedDial?: Record<string, ID>; // keypad digit (2-9) → contact id
}

export interface BackupFile {
  app: "callmony" | "dial-ledger"; // "dial-ledger" = pre-rename backups (accepted)
  version: number;
  exportedAt: number;
  data: {
    contacts: Contact[];
    entries: LedgerEntry[];
    groups?: LedgerGroup[]; // ledger groups (optional = older backups)
    lists: CalcList[];
    notes: Note[];
    reminders: Reminder[];
    banks: BankAccount[];
    recents: CallLog[];
    messages: MessageLog[];
    settings: Partial<Settings>;
    attachments: AttachmentMeta[];
  };
  files: { id: ID; name: string; type: string; d: string }[]; // base64
}

export const CURRENCIES = [
  { code: "ETB", symbol: "Br", label: "Ethiopian Birr (Br)" },
  { code: "USD", symbol: "$", label: "US Dollar ($)" },
  { code: "EUR", symbol: "€", label: "Euro (€)" },
  { code: "GBP", symbol: "£", label: "British Pound (£)" },
  { code: "AED", symbol: "د.إ", label: "UAE Dirham" },
  { code: "SAR", symbol: "﷼", label: "Saudi Riyal" },
  { code: "KES", symbol: "KSh", label: "Kenyan Shilling" },
  { code: "INR", symbol: "₹", label: "Indian Rupee (₹)" },
];

export const ENTRY_CATEGORIES = [
  "General",
  "Loan",
  "Rent",
  "Sale",
  "Salary",
  "Groceries",
  "Transport",
  "Utilities",
  "Health",
  "School",
  "Other",
];

export const PAYMENT_METHODS = ["cash", "bank", "card", "mobile", "other"];

export const PHONE_LABELS = ["mobile", "work", "home", "main", "office", "other"];

export const AVATAR_PALETTE = [
  { color: "#E4EDE7", text: "#527561" },
  { color: "#F7E8DF", text: "#B7785B" },
  { color: "#E5E8F9", text: "#6B75B5" },
  { color: "#F4E5EE", text: "#A65F88" },
  { color: "#F4EEDB", text: "#9B8243" },
  { color: "#DFF0F0", text: "#4A8585" },
  { color: "#F0E9E4", text: "#8A7360" },
  { color: "#E7F0DC", text: "#67813F" },
];
