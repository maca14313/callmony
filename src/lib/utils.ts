/* callmony — RN utilities (dates, money, phones, misc) */

import { Vibration } from "react-native";
import Clipboard from "@react-native-clipboard/clipboard";
import { sha256 } from "js-sha256";
import { AVATAR_PALETTE, CURRENCIES, DeviceContact } from "./types";

export const uid = (): string =>
  `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}${Math.random().toString(36).slice(2, 6)}`;

/* ---------------- display names (device contacts) ---------------- */

export const displayName = (c?: DeviceContact | null): string => {
  if (!c) return "Unknown";
  const n = `${c.givenName || ""} ${c.familyName || ""}`.trim();
  return n || c.nickname || c.company || c.displayName || "No name";
};

export const initialsOf = (c?: DeviceContact | null): string => {
  const name = displayName(c);
  const parts = name.split(/\s+/).filter(Boolean);
  if (!parts.length) return "?";
  return parts
    .slice(0, 2)
    .map((p) => p[0])
    .join("")
    .toUpperCase();
};

export const avatarColors = (c?: DeviceContact | null) => {
  const seed = (displayName(c) || "?").charCodeAt(0) || 65;
  const p = AVATAR_PALETTE[seed % AVATAR_PALETTE.length];
  return { color: p.color, text: p.text };
};

/* ---------------- money (deterministic, no Intl dependency) ---------------- */

const groupDigits = (intPart: string): string => intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ",");

const numberStr = (n: number): string => {
  const abs = Math.abs(n);
  const fixed = Number.isInteger(abs) ? String(abs) : abs.toFixed(2);
  const [i, f] = fixed.split(".");
  const g = groupDigits(i);
  return f ? `${g}.${f}` : g;
};

export const currencySymbol = (code: string): string =>
  CURRENCIES.find((c) => c.code === code)?.symbol || code;

export const money = (n: number, currency = "ETB", opts?: { signed?: boolean }): string => {
  const sym = currencySymbol(currency);
  const body = `${numberStr(n)} ${sym}`;
  if (opts?.signed && n !== 0) return `${n > 0 ? "+" : "−"}${body}`;
  return n < 0 ? `−${body}` : body;
};

export const compactMoney = (n: number, currency = "ETB", opts?: { signed?: boolean }): string => {
  const abs = Math.abs(n);
  if (abs >= 100000) {
    const v = abs >= 1000000 ? `${(abs / 1000000).toFixed(1)}M` : `${(abs / 1000).toFixed(0)}K`;
    const sign = opts?.signed && n !== 0 ? (n > 0 ? "+" : "−") : n < 0 ? "−" : "";
    return `${sign}${v} ${currencySymbol(currency)}`;
  }
  return money(n, currency, opts);
};

/* ---------------- dates (localized via i18n registry) ---------------- */

let DATE_NAMES: {
  months: string[];
  weekdays: string[];
  today: string;
  yesterday: string;
} = {
  months: ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"],
  weekdays: ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"],
  today: "Today",
  yesterday: "Yesterday",
};

export const setDateNames = (months: string[], weekdays: string[], today: string, yesterday: string) => {
  if (months?.length === 12) DATE_NAMES.months = months;
  if (weekdays?.length === 7) DATE_NAMES.weekdays = weekdays;
  DATE_NAMES.today = today || "Today";
  DATE_NAMES.yesterday = yesterday || "Yesterday";
};

export const todayStr = (): string => dateStr(new Date());
export const now = (): number => Date.now();

export const dateStr = (d: Date | number): string => {
  const x = typeof d === "number" ? new Date(d) : d;
  const p = (n: number) => String(n).padStart(2, "0");
  return `${x.getFullYear()}-${p(x.getMonth() + 1)}-${p(x.getDate())}`;
};

export const fd = (date: string | number | Date): string => {
  const d = typeof date === "string" ? new Date(`${date}T00:00:00`) : new Date(date);
  if (isNaN(d.getTime())) return String(date);
  return `${DATE_NAMES.months[d.getMonth()]} ${d.getDate()}, ${d.getFullYear()}`;
};

export const fdShort = (date: string | number | Date): string => {
  const d = typeof date === "string" ? new Date(`${date}T00:00:00`) : new Date(date);
  if (isNaN(d.getTime())) return String(date);
  return `${DATE_NAMES.months[d.getMonth()]} ${d.getDate()}`;
};

export const ft = (epoch: number): string => {
  const d = new Date(epoch);
  const h = d.getHours();
  const ampm = h < 12 ? "AM" : "PM";
  const h12 = h % 12 === 0 ? 12 : h % 12;
  return `${h12}:${String(d.getMinutes()).padStart(2, "0")} ${ampm}`;
};

/* ---------------- forgiving date + time entry (v1.6.1) ----------------
   The reminder editor takes free-text date/time. The old parser fed
   `new Date(`${date}T${time}`)` — anything but exactly "YYYY-MM-DD" +
   "HH:MM" (e.g. "9:00 AM" like the placeholder suggested, or 01/06/2025)
   produced NaN → the "Pick a valid date and time" dead-end the user hit.
   These parsers accept every reasonable spelling instead. */

/** "2026-01-09" | "09-01-2026" | "9/1/26" | "2026.1.9" | "jan 9" … → {y,m,d}
    Day-first for ambiguous d/m/y (non-US convention); 2-digit years land in
    the 2000s. Month names (en) accepted. Returns null when unreadable. */
export const parseDateInput = (input: string, base?: Date): { y: number; m: number; d: number } | null => {
  const s = (input || "").trim().toLowerCase();
  if (!s) return null;
  const b = base ?? new Date();

  /* relative words */
  if (s === "today" || s === "tod" || s === "t") return { y: b.getFullYear(), m: b.getMonth() + 1, d: b.getDate() };
  if (/^tom(?:orrow)?$/.test(s)) { const t = new Date(b.getTime() + 86400000); return { y: t.getFullYear(), m: t.getMonth() + 1, d: t.getDate() }; }
  if (/^yes(?:terday)?$/.test(s)) { const t = new Date(b.getTime() - 86400000); return { y: t.getFullYear(), m: t.getMonth() + 1, d: t.getDate() }; }

  /* month-name forms: "jan 9", "9 jan", "january 9 2026" */
  const MONTHS = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"];
  const words = s.replace(/,/g, " ").split(/\s+/).filter(Boolean);
  const monthWordIdx = words.findIndex((w) => MONTHS.some((mo) => w.startsWith(mo) && w.length <= 9));
  if (monthWordIdx !== -1) {
    const m = MONTHS.findIndex((mo) => words[monthWordIdx].startsWith(mo)) + 1;
    const nums = words.filter((_, i) => i !== monthWordIdx).map(Number).filter((n) => !isNaN(n));
    if (nums.length >= 1) {
      const d = nums[0];
      const y = nums.length >= 2 ? (nums[1] < 100 ? 2000 + nums[1] : nums[1]) : b.getFullYear();
      if (d >= 1 && d <= 31 && m >= 1 && m <= 12 && y > 1900) return { y, m, d };
    }
    return null;
  }

  /* numeric forms split by - / . (and optional trailing time — ignored) */
  const datePart = s.split(/[ T]/)[0];
  const parts = datePart.split(/[-/.]/).map((x) => Number(x)).filter((n) => !isNaN(n));
  if (parts.length < 3) return null;
  const [a, c, e] = parts as [number, number, number];
  if (a >= 1000) {
    /* YYYY-MM-DD */
    const y = a, m = c, d = e;
    if (m >= 1 && m <= 12 && d >= 1 && d <= 31) return { y, m, d };
    return null;
  }
  /* D-M-Y (day first — the dominant non-US layout) */
  const d = a, m = c;
  const y = e < 100 ? 2000 + e : e;
  if (m >= 1 && m <= 12 && d >= 1 && d <= 31 && y > 1900) return { y, m, d };
  /* M-D-Y fallback when day-first is impossible */
  if (a <= 12 && c > 12 && c <= 31) return { y, m: a, d: c };
  return null;
};

/** "9" | "9:00" | "9.30" | "9:5" | "14:30" | "2 pm" | "9:00 AM" | "09:00:00"
    → {h, minute}. Returns null when unreadable. */
export const parseTimeInput = (input: string): { h: number; minute: number } | null => {
  const s = (input || "").trim().toLowerCase();
  if (!s) return null;
  const am = /\b(a\.?m\.?|am|morning)\b/.test(s);
  const pm = /\b(p\.?m\.?|pm|evening|night)\b/.test(s);
  const m = /^(\d{1,2})(?:[:.](\d{1,2}))?(?::(\d{2}))?$/.exec(s.replace(/\s*(a\.?m\.?|p\.?m\.?|am|pm|morning|evening|night)\s*$/, "").trim());
  if (!m) return null;
  let h = Number(m[1]);
  const minute = m[2] ? Number(m[2]) : 0;
  if (h > 24 || minute > 59) return null;
  if (pm && h < 12) h += 12;
  if (am && h === 12) h = 0;
  if (h === 24) h = 0;
  return { h, minute };
};

/** Combine the two into a real Date (null when either side is unreadable). */
export const parseDueLocal = (dateInput: string, timeInput: string, base?: Date): Date | null => {
  const d = parseDateInput(dateInput, base);
  const tm = parseTimeInput(timeInput);
  if (!d || !tm) return null;
  const dt = new Date(d.y, d.m - 1, d.d, tm.h, tm.minute, 0, 0);
  return isNaN(dt.getTime()) ? null : dt;
};

export const relTime = (epoch: number): string => {
  const diff = Date.now() - epoch;
  const m = Math.floor(diff / 60000);
  if (m < 1) return "·";
  if (m < 60) return `${m}m`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h`;
  const d = Math.floor(h / 24);
  if (d < 7) return `${d}d`;
  return fdShort(epoch);
};

/* v1.6.8 — the Recents day headers show ONLY "Mon D" (e.g. "Oct 24"):
   no Today/Yesterday/weekday names, no badge — the user asked for the
   smallest possible label to save vertical space in the list. */
export const dayGroup = (epoch: number): string => {
  const d = new Date(epoch);
  return `${DATE_NAMES.months[d.getMonth()]} ${d.getDate()}`;
};

export const durationStr = (secs: number): string => {
  if (secs <= 0) return "—";
  const m = Math.floor(secs / 60);
  const s = Math.floor(secs % 60);
  if (m === 0) return `${s}s`;
  return `${m}:${String(s).padStart(2, "0")}`;
};

export const clockStr = (secs: number): string => {
  const m = Math.floor(secs / 60);
  const s = Math.floor(secs % 60);
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
};

/* ---------------- phones ---------------- */

const DIAL_CODES: Record<string, string> = {
  ET: "251", ER: "291", DJ: "253", SO: "252", KE: "254", SD: "249", EG: "20",
  SA: "966", AE: "971", US: "1", GB: "44", CA: "1", DE: "49", FR: "33",
  IT: "39", ES: "34", NL: "31", IN: "91", NG: "234", ZA: "27", TZ: "255", UG: "256",
};

export const dialCodeOf = (countryCode: string): string => DIAL_CODES[countryCode] || "251";

export const normalizePhone = (raw: string, countryCode = "ET"): string => {
  let n = (raw || "").replace(/[^\d+*#]/g, "");
  if (!n) return "";
  if (/^0+$/.test(n)) return n; // "0"/"00" — meaningless, keep as-is (never matches)
  if (n.startsWith("00")) n = `+${n.slice(2)}`; // 00-international prefix → +
  if (n.startsWith("+")) {
    const rest = n.slice(1);
    const cc = DIAL_CODES[countryCode] || "251";
    /* "+251 09…" (country code + trunk zero — a very common Ethiopian
       storage format) is malformed E.164: the 0 never belongs after the
       country code, so drop it → both "+251 09…" and "09…" normalize
       to the same key and always match. */
    if (rest.startsWith(cc + "0")) return rest.slice(0, cc.length) + rest.slice(cc.length + 1);
    return rest;
  }
  if (n.includes("*") || n.includes("#")) return n.replace(/\D/g, ""); // USSD — digits only
  const cc = DIAL_CODES[countryCode] || "251";
  if (n.startsWith("0")) n = n.slice(1);
  return `${cc}${n}`;
};

/**
 * NATIONAL significant digits — the number with the country code AND the
 * trunk zero removed: "0912…", "+251912…" and "251912…" all → "912…".
 * v1.6.2 (user request): search must match numbers whether they carry a
 * country code or not, so every comparison happens on this common tail.
 */
export const nationalDigits = (rawOrDigits: string, countryCode = "ET"): string => {
  const norm = normalizePhone(rawOrDigits, countryCode).replace(/\D/g, "");
  if (!norm) return "";
  const cc = DIAL_CODES[countryCode] || "251";
  if (norm.startsWith(cc) && norm.length > cc.length) return norm.slice(cc.length).replace(/^0+/, "");
  return norm;
};

export const prettyPhone = (raw: string, countryCode = "ET"): string => {
  if (!raw) return "";
  if (raw.includes("*") || raw.includes("#")) return raw; // USSD codes as typed
  const digits = normalizePhone(raw, countryCode);
  if (digits.replace(/^0+/, "").length < 3) return raw; // degenerate ("0", "00") → raw
  if (digits.startsWith("251") && digits.length >= 12) {
    return `+251 ${digits.slice(3, 6)} ${digits.slice(6, 9)} ${digits.slice(9)}`;
  }
  if (digits.length > 8) {
    return `+${digits.slice(0, digits.length - 8)} ${digits.slice(-8, -4)} ${digits.slice(-4)}`;
  }
  return raw;
};

export const T9_MAP: Record<string, string> = {
  "2": "abc", "3": "def", "4": "ghi", "5": "jkl", "6": "mno",
  "7": "pqrs", "8": "tuv", "9": "wxyz",
};

export const t9Match = (number: string, name: string): boolean => {
  if (!number) return true;
  const letters = name.toLowerCase().replace(/[^a-z]/g, "");
  const keys = number.replace(/[^2-9]/g, "");
  if (!keys || !letters) return false;
  if (letters.length < keys.length) return false;
  for (let i = 0; i <= letters.length - keys.length; i++) {
    let ok = true;
    for (let j = 0; j < keys.length; j++) {
      if (!T9_MAP[keys[j]]?.includes(letters[i + j])) {
        ok = false;
        break;
      }
    }
    if (ok) return true;
  }
  return false;
};

/* ---------------- scored contact search (global search overlay) ----------------
   v1.6.2 — COUNTRY-CODE-AGNOSTIC + INDEXED + T9 (user request: "the search
   should match the numbers if it has country code or not and the search
   should be very very very fast and advanced"):
   · every phone is pre-indexed in THREE forms — raw digits, normalized
     (with country code) and NATIONAL significant digits (cc + trunk zero
     stripped) — and the query is normalized the same way, so "0912…",
     "+251912…" and "912…" all find each other;
   · the whole index is built ONCE per address-book load and cached (the
     contacts array is a stable store reference) — a keystroke only runs
     cheap string comparisons over the cached entries, no re-normalization;
   · pure-digit queries also T9-match names (dialer-style: 564 → "joh…");
   · ranking: exact number 7 · name word-prefix 6 · number prefix 6 ·
     T9 name 5.5 · number suffix (last digits) 5 · name substring 4 ·
     company/email 3 · number substring 3.5 · note 2. */

export interface ScoredContact<T> {
  contact: T;
  score: number;
}

interface SearchableContact {
  givenName?: string;
  familyName?: string;
  displayName?: string;
  company?: string;
  jobTitle?: string;
  note?: string;
  phones: { number: string }[];
  emails: { address: string }[];
}

export interface SearchIndexEntry<T> {
  contact: T;
  name: string;
  nameLower: string;
  words: string[];
  companyLower: string;
  emailLower: string;
  noteLower: string;
  /** a-z only — T9 dialer matching */
  letters: string;
  phones: { raw: string; norm: string; nat: string }[];
}

const buildEntry = <T extends SearchableContact>(c: T, nameOf: (c: T) => string, countryCode: string): SearchIndexEntry<T> => ({
  contact: c,
  name: nameOf(c),
  nameLower: nameOf(c).toLowerCase(),
  words: nameOf(c).toLowerCase().split(/\s+/).filter(Boolean),
  companyLower: `${c.company || ""} ${c.jobTitle || ""}`.toLowerCase().trim(),
  emailLower: (c.emails || []).map((e) => (e.address || "").toLowerCase()).join(" "),
  noteLower: (c.note || "").toLowerCase(),
  letters: nameOf(c).toLowerCase().replace(/[^a-z]/g, ""),
  phones: (c.phones || []).map((p) => {
    const norm = normalizePhone(p.number, countryCode).replace(/\D/g, "");
    const cc = DIAL_CODES[countryCode] || "251";
    const nat =
      norm.startsWith(cc) && norm.length > cc.length ? norm.slice(cc.length).replace(/^0+/, "") : norm;
    return { raw: (p.number || "").replace(/\D/g, ""), norm, nat };
  }),
});

/* index cache — keyed by the (stable) contacts array reference */
interface IndexCacheRow {
  cc: string;
  nameOf: (c: never) => string;
  entries: SearchIndexEntry<never>[];
}
const INDEX_CACHE = new WeakMap<object, IndexCacheRow>();

/** Build (or fetch the cached) search index for a contacts list. */
export const getContactSearchIndex = <T extends SearchableContact>(
  contacts: T[],
  countryCode: string,
  nameOf: (c: T) => string
): SearchIndexEntry<T>[] => {
  const cached = INDEX_CACHE.get(contacts as unknown as object);
  if (cached && cached.cc === countryCode && cached.nameOf === (nameOf as unknown as (c: never) => string)) {
    return cached.entries as unknown as SearchIndexEntry<T>[];
  }
  const entries = contacts.map((c) => buildEntry(c, nameOf, countryCode));
  INDEX_CACHE.set(contacts as unknown as object, {
    cc: countryCode,
    nameOf: nameOf as unknown as (c: never) => string,
    entries: entries as unknown as SearchIndexEntry<never>[],
  });
  return entries;
};

/** Best phone score for one entry — qd = raw query digits, qnat = national form. */
const phoneScore = (phones: { raw: string; norm: string; nat: string }[], qd: string, qnat: string): number => {
  if (qd.length < 2) return 0;
  let best = 0;
  for (const p of phones) {
    let s = 0;
    if (p.raw === qd || p.norm === qd || (qnat && p.nat === qnat)) s = 7;
    else if (p.raw.startsWith(qd) || p.norm.startsWith(qd) || (qnat && p.nat.startsWith(qnat))) s = 6;
    else if (p.raw.endsWith(qd) || p.norm.endsWith(qd) || (qnat && p.nat.endsWith(qnat))) s = 5;
    else if (p.raw.includes(qd) || p.norm.includes(qd) || (qnat && p.nat.includes(qnat))) s = 3.5;
    if (s > best) best = s;
    if (best >= 7) break;
  }
  return best;
};

const scoreEntry = (e: SearchIndexEntry<unknown>, q: string, qd: string, qnat: string, digitsOnly: boolean): number => {
  let score = 0;
  /* name — word-prefix ranks highest (dialer behavior) */
  if (e.words.some((w) => w.startsWith(q))) score = Math.max(score, 6);
  else if (e.nameLower.includes(q)) score = Math.max(score, 4);
  /* T9 name matching for pure-digit queries (3+ digits) — dialer-style */
  if (digitsOnly && qd.length >= 3 && e.letters && t9Match(qd, e.name)) score = Math.max(score, 5.5);
  if (!digitsOnly) {
    /* company / job title · email · note (skipped for digit queries = speed) */
    if (e.companyLower && e.companyLower.includes(q)) score = Math.max(score, 3);
    if (e.emailLower && e.emailLower.includes(q)) score = Math.max(score, 3);
    if (e.noteLower && e.noteLower.includes(q)) score = Math.max(score, 2);
  }
  /* phone — raw / normalized / national, prefix > suffix > substring */
  if (qd.length >= 2) score = Math.max(score, phoneScore(e.phones, qd, qnat));
  return score;
};

export const scoreContact = <T extends SearchableContact>(
  c: T,
  q: string,
  nameOf: (c: T) => string,
  countryCode: string
): number => {
  if (!q) return 0;
  const query = q.trim().toLowerCase();
  const qd = query.replace(/\D/g, "");
  return scoreEntry(buildEntry(c, nameOf, countryCode) as unknown as SearchIndexEntry<unknown>, query, qd, nationalDigits(qd, countryCode), query === qd);
};

/** Ranked contact search — use wherever a list of contacts is filtered. */
export const searchContacts = <T extends SearchableContact>(
  contacts: T[],
  query: string,
  nameOf: (c: T) => string,
  countryCode: string,
  limit = Infinity
): ScoredContact<T>[] => {
  const q = query.trim().toLowerCase();
  if (q.length < 1) return [];
  const qd = q.replace(/\D/g, "");
  const qnat = nationalDigits(qd, countryCode);
  const digitsOnly = q === qd;
  const entries = getContactSearchIndex(contacts, countryCode, nameOf);
  const out: ScoredContact<T>[] = [];
  for (const e of entries) {
    const s = scoreEntry(e as unknown as SearchIndexEntry<unknown>, q, qd, qnat, digitsOnly);
    if (s > 0) out.push({ contact: e.contact, score: s });
  }
  out.sort((a, b) => b.score - a.score || nameOf(a.contact).localeCompare(nameOf(b.contact)));
  return out.slice(0, limit);
};

/* ---------------- misc ---------------- */

export const escapeRegExp = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

export const maskAccount = (acc?: string): string => {
  if (!acc) return "";
  const clean = acc.replace(/\s+/g, "");
  if (clean.length <= 4) return clean;
  return `•••• •••• ${clean.slice(-4)}`;
};

export const bytesStr = (n: number): string => {
  if (n < 1024) return `${n} B`;
  if (n < 1048576) return `${(n / 1024).toFixed(0)} KB`;
  if (n < 1073741824) return `${(n / 1048576).toFixed(1)} MB`;
  return `${(n / 1073741824).toFixed(2)} GB`;
};

export const clamp = (n: number, min: number, max: number) => Math.min(max, Math.max(min, n));

export const copyText = async (text: string): Promise<boolean> => {
  try {
    Clipboard.setString(text);
    return true;
  } catch {
    return false;
  }
};

export const vibrate = (pattern: number | number[] = 15) => {
  try {
    Vibration.vibrate(pattern);
  } catch {
    /* noop */
  }
};

/** Hash PIN — salted iterated SHA-256 (screen lock only; data is NOT encrypted). */
export const hashPin = (pin: string, salt?: string): { hash: string; salt: string } => {
  const s = salt || uid();
  let h = `${s}:${pin}`;
  for (let i = 0; i < 2000; i++) h = sha256(h);
  return { hash: h, salt: s };
};
