/* ---------------------------------------------------------------
   callmony — data model (React Native edition)
   Contacts come from the DEVICE (react-native-contacts); app data
   (ledger / lists / notes / reminders / banks / settings) lives in
   AsyncStorage. Every record: id, createdAt, updatedAt, deletedAt.
   ownerId: "general" | "c:<device recordID>"
---------------------------------------------------------------- */

export type ID = string;
/** "general" or a device contact: "c:<recordID>" */
export type OwnerId = ID | "general";
export const contactOwnerId = (recordID: string): OwnerId => `c:${recordID}`;
export const ownerIdToRecordId = (ownerId: string): string | null =>
  ownerId.startsWith("c:") ? ownerId.slice(2) : null;

/* ---------------- device contacts ---------------- */

export interface DevicePhone {
  id: string;
  label: string; // mobile | work | home | main | office | other | custom
  number: string;
}
export interface DeviceEmail {
  id: string;
  label: string;
  address: string;
}
export interface DeviceAddress {
  id: string;
  label: string;
  formatted: string;
}

/** A contact from the phone's address book (normalized). */
export interface DeviceContact {
  recordID: string;
  displayName: string; // given + family (or company fallback)
  givenName: string;
  familyName: string;
  nickname?: string;
  company?: string;
  jobTitle?: string;
  note?: string;
  birthday?: string; // YYYY-MM-DD (normalized from the OS)
  phones: DevicePhone[];
  emails: DeviceEmail[];
  addresses: DeviceAddress[];
  thumbnailPath?: string;
  hasPhoto: boolean;
}

/** App-local extensions layered on top of a device contact. */
export interface ContactX {
  recordID: string;
  favorite: boolean;
  tags: string[];
  pinnedNote?: string;
  speedDial?: string; // '2'..'9'
  createdAt: number;
  updatedAt: number;
}

/* ---------------- money ledger ---------------- */

/** A single payment record — every payment keeps its own row. */
export interface Payment {
  id: ID;
  amount: number;
  date: string; // YYYY-MM-DD
  note?: string;
  method?: string;
  auto?: "tick" | "settle"; // created by ticking a row / settle-up
  refs?: LinkRef[];
  createdAt: number;
}

/** One shopping-style line on a ledger entry — name + qty (+ unit) × unit price. */
export interface EntryItem {
  id: ID;
  name: string; // "ball"
  qty: number; // 3
  unit?: string; // "pic" / "kg" / "bag" — free text, optional
  price: number; // unit price 100 → line total 300
}

export type EntryKind = "income" | "expense" | "credit" | "debit";

export interface LedgerEntry {
  id: ID;
  ownerId: OwnerId;
  type: "credit" | "debit"; // math: credit = +, debit = −
  kind?: EntryKind; // user-chosen label (income/expense/credit/debit — both contexts)
  amount: number;
  /** line items qty×price; amount = Σ qty×price when present */
  items?: EntryItem[];
  payments: Payment[];
  date: string; // YYYY-MM-DD
  dueDate?: string;
  category?: string;
  method?: string;
  note?: string;
  listId?: ID;
  groupId?: ID;
  refs?: LinkRef[];
  createdAt: number;
  updatedAt: number;
  deletedAt?: number;
}

export interface LedgerGroup {
  id: ID;
  ownerId: OwnerId;
  title: string;
  note?: string;
  refs: LinkRef[];
  createdAt: number;
  updatedAt: number;
  deletedAt?: number;
}

/* ---------------- calculating lists ---------------- */

export interface ListDiscount {
  type: "pct" | "amount";
  value: number;
}

export interface ListItem {
  id: ID;
  name: string;
  qty: number;
  unit?: string;
  unitPrice: number;
  discount?: ListDiscount;
  taxPct?: number;
  payments: Payment[];
  checked: boolean;
  date?: string;
  dueDate?: string;
  priority?: "low" | "medium" | "high";
  note?: string;
  sortOrder: number;
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
  discount?: ListDiscount;
  taxPct?: number;
  extraCharge?: number;
  isTemplate: boolean;
  postToLedger: boolean;
  repeat: Repeat;
  currency?: string;
  items: ListItem[];
  refs?: LinkRef[];
  createdAt: number;
  updatedAt: number;
  deletedAt?: number;
}

/* ---------------- links / notes / reminders / banks ---------------- */

export interface LinkRef {
  type: "contact" | "entry" | "list" | "note" | "reminder" | "bank" | "item" | "link";
  id: ID;
  label?: string;
  url?: string;
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
  leadMinutes?: number[];
  links: LinkRef[];
  done: boolean;
  doneAt?: number;
  snoozedUntil?: number;
  lastFiredAt?: number;
  firedLeads?: number[];
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
  branch?: string;
  notes?: string;
  balance?: number;
  currency?: string;
  createdAt: number;
  updatedAt: number;
  deletedAt?: number;
}

/* ---------------- logs ---------------- */

export interface CallLog {
  id: ID;
  phone: string; // normalized display
  ownerId?: OwnerId; // resolved device contact
  direction: "incoming" | "outgoing" | "missed";
  at: number;
  duration: number; // seconds
  via: "app" | "tel";
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

/* ---------------- settings / backup ---------------- */

export interface Settings {
  theme: "light" | "dark" | "auto";
  language?: "en" | "am" | "om" | "ar" | "ti";
  currency: string; // 'ETB' ...
  countryCode: string; // 'ET' default dial code +251
  pinHash?: { hash: string; salt: string };
  autolockMin: number; // 0 = lock immediately on blur
  hideBalances: boolean;
  customCategories?: string[];
  startTab?: string;
  lastBackupAt?: number;
  notifAsked: boolean;
  blockedNumbers: string[];
  speedDial?: Record<string, string>; // keypad digit → recordID
}

export interface BackupFile {
  app: "callmony";
  version: number;
  exportedAt: number;
  data: {
    contactX: ContactX[];
    entries: LedgerEntry[];
    groups: LedgerGroup[];
    lists: CalcList[];
    notes: Note[];
    reminders: Reminder[];
    banks: BankAccount[];
    recents: CallLog[];
    messages: MessageLog[];
    settings: Partial<Settings>;
  };
}

/* ---------------- constants ---------------- */

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

export const COUNTRY_CODES: { code: string; dial: string; name: string }[] = [
  { code: "ET", dial: "251", name: "Ethiopia" },
  { code: "ER", dial: "291", name: "Eritrea" },
  { code: "DJ", dial: "253", name: "Djibouti" },
  { code: "SO", dial: "252", name: "Somalia" },
  { code: "KE", dial: "254", name: "Kenya" },
  { code: "SD", dial: "249", name: "Sudan" },
  { code: "EG", dial: "20", name: "Egypt" },
  { code: "SA", dial: "966", name: "Saudi Arabia" },
  { code: "AE", dial: "971", name: "UAE" },
  { code: "US", dial: "1", name: "United States" },
  { code: "GB", dial: "44", name: "United Kingdom" },
  { code: "CA", dial: "1", name: "Canada" },
  { code: "DE", dial: "49", name: "Germany" },
  { code: "FR", dial: "33", name: "France" },
  { code: "IT", dial: "39", name: "Italy" },
  { code: "ES", dial: "34", name: "Spain" },
  { code: "NL", dial: "31", name: "Netherlands" },
  { code: "IN", dial: "91", name: "India" },
  { code: "NG", dial: "234", name: "Nigeria" },
  { code: "ZA", dial: "27", name: "South Africa" },
  { code: "TZ", dial: "255", name: "Tanzania" },
  { code: "UG", dial: "256", name: "Uganda" },
];
