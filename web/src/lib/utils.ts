import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";
import { AVATAR_PALETTE, Contact, ID } from "./types";

/** shadcn/ui class merger (used by src/components/ui/*) */
export const cn = (...inputs: ClassValue[]) => twMerge(clsx(inputs));

export const uid = (): ID =>
  typeof crypto !== "undefined" && crypto.randomUUID
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;

export const now = (): number => Date.now();

export const todayStr = (): string => dateStr(new Date());

export const dateStr = (d: Date | number): string => {
  const x = typeof d === "number" ? new Date(d) : d;
  const p = (n: number) => String(n).padStart(2, "0");
  return `${x.getFullYear()}-${p(x.getMonth() + 1)}-${p(x.getDate())}`;
};

export const displayName = (c?: Contact | null): string => {
  if (!c) return "Unknown";
  const n = `${c.firstName || ""} ${c.lastName || ""}`.trim();
  return n || c.nickname || c.company || "No name";
};

export const initialsOf = (c?: Contact | null): string => {
  const name = displayName(c);
  const parts = name.split(/\s+/).filter(Boolean);
  if (!parts.length) return "?";
  return parts
    .slice(0, 2)
    .map((p) => p[0])
    .join("")
    .toUpperCase();
};

export const avatarColors = (c?: Contact | null) => {
  if (c?.color && c?.textColor) return { color: c.color, text: c.textColor };
  const seed = (displayName(c) || "?").charCodeAt(0) || 65;
  const p = AVATAR_PALETTE[seed % AVATAR_PALETTE.length];
  return { color: p.color, text: p.text };
};

/* ---------------- money ---------------- */

export const money = (n: number, currency = "ETB", opts?: { signed?: boolean }): string => {
  try {
    const formatted = new Intl.NumberFormat("en-US", {
      style: "currency",
      currency,
      maximumFractionDigits: 2,
      minimumFractionDigits: 0,
    }).format(Math.abs(n));
    if (opts?.signed && n !== 0) return `${n > 0 ? "+" : "−"}${formatted}`;
    return n < 0 ? `−${formatted}` : formatted;
  } catch {
    return `${n < 0 ? "−" : ""}${Math.abs(n).toFixed(2)} ${currency}`;
  }
};

export const compactMoney = (n: number, currency = "ETB"): string => {
  try {
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency,
      notation: Math.abs(n) >= 100000 ? "compact" : "standard",
      maximumFractionDigits: Math.abs(n) >= 100000 ? 1 : 2,
    }).format(n);
  } catch {
    return `${n.toFixed(2)}`;
  }
};

/* ---------------- dates ---------------- */

export const fd = (date: string | number | Date): string => {
  const d = typeof date === "string" ? new Date(`${date}T00:00:00`) : new Date(date);
  if (isNaN(d.getTime())) return String(date);
  return d.toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
};

export const fdShort = (date: string | number | Date): string => {
  const d = typeof date === "string" ? new Date(`${date}T00:00:00`) : new Date(date);
  if (isNaN(d.getTime())) return String(date);
  return d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
};

export const ft = (epoch: number): string =>
  new Date(epoch).toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" });

export const relTime = (epoch: number): string => {
  const diff = Date.now() - epoch;
  const m = Math.floor(diff / 60000);
  if (m < 1) return "just now";
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  const d = Math.floor(h / 24);
  if (d === 1) return "yesterday";
  if (d < 7) return `${d}d ago`;
  return fdShort(epoch);
};

export const dayGroup = (epoch: number): string => {
  const d = new Date(epoch);
  const t = new Date();
  const y = new Date(Date.now() - 86400000);
  if (d.toDateString() === t.toDateString()) return "Today";
  if (d.toDateString() === y.toDateString()) return "Yesterday";
  if (Date.now() - epoch < 7 * 86400000)
    return d.toLocaleDateString(undefined, { weekday: "long" });
  return d.toLocaleDateString(undefined, { month: "long", day: "numeric" });
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
  ET: "251", US: "1", GB: "44", KE: "254", SA: "966", AE: "971", IN: "91",
  DE: "49", FR: "33", IT: "39", ES: "34", NL: "31", CA: "1", AU: "61",
  NG: "234", EG: "20", ZA: "27", TZ: "255", UG: "256", SD: "249", SO: "252", ER: "291",
};

/** Normalize to E.164-ish digits with country code. */
export const normalizePhone = (raw: string, countryCode = "ET"): string => {
  let n = (raw || "").replace(/[^\d+*#]/g, "");
  if (n.startsWith("00")) n = `+${n.slice(2)}`;
  if (n.startsWith("+")) return n.slice(1);
  const cc = DIAL_CODES[countryCode] || "251";
  if (n.startsWith("0")) n = n.slice(1);
  return `${cc}${n}`;
};

export const prettyPhone = (raw: string, countryCode = "ET"): string => {
  const digits = normalizePhone(raw, countryCode);
  if (raw.includes("*") || raw.includes("#")) return raw; // USSD codes as typed
  if (digits.startsWith("251") && digits.length >= 12) {
    return `+251 ${digits.slice(3, 6)} ${digits.slice(6, 9)} ${digits.slice(9)}`;
  }
  if (digits.length > 8) {
    return `+${digits.slice(0, digits.length - 8)} ${digits.slice(-8, -4)} ${digits.slice(-4)}`;
  }
  return raw;
};

/** T9 keypad letters */
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
  // contiguous subsequence match
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
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    try {
      const ta = document.createElement("textarea");
      ta.value = text;
      ta.style.position = "fixed";
      ta.style.opacity = "0";
      document.body.appendChild(ta);
      ta.select();
      document.execCommand("copy");
      document.body.removeChild(ta);
      return true;
    } catch {
      return false;
    }
  }
};

/** hash PIN with PBKDF2 (screen lock only — data itself is NOT encrypted) */
export const hashPin = async (pin: string, salt?: string): Promise<{ hash: string; salt: string }> => {
  const s = salt || uid();
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey("raw", enc.encode(pin), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", salt: enc.encode(s), iterations: 60000, hash: "SHA-256" },
    key,
    256
  );
  const hash = btoa(String.fromCharCode(...new Uint8Array(bits)));
  return { hash, salt: s };
};

export const base64FromBlob = (blob: Blob): Promise<string> =>
  new Promise((resolve) => {
    const r = new FileReader();
    r.onload = () => resolve(String(r.result));
    r.onerror = () => resolve("");
    r.readAsDataURL(blob);
  });

export const blobFromBase64 = (dataUrl: string): Blob => {
  const [head, body] = dataUrl.split(",");
  const mime = /:(.*?);/.exec(head)?.[1] || "application/octet-stream";
  const bin = atob(body);
  const arr = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
  return new Blob([arr], { type: mime });
};

export const downloadBlob = (blob: Blob, filename: string) => {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 4000);
};

export const vibrate = (pattern: number | number[]) => {
  try {
    navigator.vibrate?.(pattern);
  } catch {
    /* noop */
  }
};
