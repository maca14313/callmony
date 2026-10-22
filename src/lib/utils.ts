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

export const compactMoney = (n: number, currency = "ETB"): string => {
  const abs = Math.abs(n);
  if (abs >= 100000) {
    const v = abs >= 1000000 ? `${(abs / 1000000).toFixed(1)}M` : `${(abs / 1000).toFixed(0)}K`;
    return `${n < 0 ? "−" : ""}${v} ${currencySymbol(currency)}`;
  }
  return money(n, currency);
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

export const dayGroup = (epoch: number): string => {
  const d = new Date(epoch);
  const t = new Date();
  const y = new Date(Date.now() - 86400000);
  if (d.toDateString() === t.toDateString()) return DATE_NAMES.today;
  if (d.toDateString() === y.toDateString()) return DATE_NAMES.yesterday;
  if (Date.now() - epoch < 7 * 86400000) return DATE_NAMES.weekdays[d.getDay()];
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
   Matches ANY of: name (word-prefix > substring) · company · job title ·
   email · note · phone (prefix > dialer-style suffix > substring, in BOTH
   the raw “09…” and normalized “+251…” formats). Ranked by score so the
   best hit is always first. */

export interface ScoredContact<T> {
  contact: T;
  score: number;
}

export const scoreContact = <T extends {
  givenName?: string;
  familyName?: string;
  displayName?: string;
  company?: string;
  jobTitle?: string;
  note?: string;
  phones: { number: string }[];
  emails: { address: string }[];
}>(
  c: T,
  q: string,
  nameOf: (c: T) => string,
  countryCode: string
): number => {
  if (!q) return 0;
  const name = nameOf(c).toLowerCase();
  const words = name.split(/\s+/).filter(Boolean);
  let score = 0;

  /* name — word-prefix ranks highest (dialer behavior) */
  if (words.some((w) => w.startsWith(q))) score = Math.max(score, 6);
  else if (name.includes(q)) score = Math.max(score, 4);
  /* company / job title */
  const company = `${c.company || ""} ${c.jobTitle || ""}`.toLowerCase().trim();
  if (company && company.includes(q)) score = Math.max(score, 3);
  /* email */
  if (c.emails.some((e) => (e.address || "").toLowerCase().includes(q))) score = Math.max(score, 3);
  /* note */
  if ((c.note || "").toLowerCase().includes(q)) score = Math.max(score, 2);

  /* phone — digits only, both formats, prefix > suffix > substring */
  const digits = q.replace(/\D/g, "");
  if (digits.length >= 2) {
    for (const p of c.phones) {
      const raw = p.number.replace(/\D/g, "");
      const norm = normalizePhone(p.number, countryCode).replace(/\D/g, "");
      if (raw.startsWith(digits) || norm.startsWith(digits)) {
        score = Math.max(score, 5);
        break;
      }
      if (raw.endsWith(digits) || norm.endsWith(digits)) {
        score = Math.max(score, 4.5);
        continue;
      }
      if (raw.includes(digits) || norm.includes(digits)) {
        score = Math.max(score, 3.5);
      }
    }
  }
  return score;
};

/** Ranked contact search — use wherever a list of contacts is filtered. */
export const searchContacts = <T extends Parameters<typeof scoreContact>[0]>(
  contacts: T[],
  query: string,
  nameOf: (c: T) => string,
  countryCode: string,
  limit = Infinity
): ScoredContact<T>[] => {
  const q = query.trim().toLowerCase();
  if (q.length < 1) return [];
  const out: ScoredContact<T>[] = [];
  for (const c of contacts) {
    const s = scoreContact(c, q, nameOf, countryCode);
    if (s > 0) out.push({ contact: c, score: s });
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
