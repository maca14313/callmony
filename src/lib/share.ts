import { normalizePhone } from "./utils";
import type { Contact } from "./types";

/* Communication shortcuts — standard deep links, no special integration. */

export const telLink = (number: string, countryCode = "ET") =>
  `tel:+${normalizePhone(number, countryCode)}`;

export const smsLink = (number: string, text?: string, countryCode = "ET") =>
  `sms:+${normalizePhone(number, countryCode)}${text ? `?body=${encodeURIComponent(text)}` : ""}`;

export const waLink = (number: string, text?: string, countryCode = "ET") =>
  `https://wa.me/${normalizePhone(number, countryCode)}${text ? `?text=${encodeURIComponent(text)}` : ""}`;

export const tgLink = (handle: string) => {
  const h = handle.trim().replace(/^@/, "");
  if (/^\+?\d+$/.test(h)) return `https://t.me/${h}`;
  return `https://t.me/${encodeURIComponent(h)}`;
};

export const mailtoLink = (address: string, subject?: string, body?: string) => {
  const params: string[] = [];
  if (subject) params.push(`subject=${encodeURIComponent(subject)}`);
  if (body) params.push(`body=${encodeURIComponent(body)}`);
  return `mailto:${address}${params.length ? `?${params.join("&")}` : ""}`;
};

export const openExternal = (url: string) => {
  try {
    window.open(url, "_blank", "noopener,noreferrer");
  } catch {
    /* noop */
  }
};

export const openLink = (url: string) => {
  try {
    window.open(url, "_blank", "noopener,noreferrer");
  } catch {
    /* noop */
  }
};

/** Web Share API with graceful fallback */
export const shareText = async (title: string, text: string): Promise<"shared" | "copied" | "failed"> => {
  const nav = navigator as Navigator & { share?: (d: ShareData) => Promise<void> };
  if (nav.share) {
    try {
      await nav.share({ title, text });
      return "shared";
    } catch {
      return "failed";
    }
  }
  return "copied";
};

export const shareFiles = async (
  title: string,
  files: File[]
): Promise<"shared" | "failed"> => {
  const nav = navigator as Navigator & { share?: (d: ShareData) => Promise<void> };
  if (nav.share && files.length) {
    try {
      await nav.share({ title, files });
      return "shared";
    } catch {
      return "failed";
    }
  }
  return "failed";
};

/** First usable phone for calling (primary first) */
export const primaryPhone = (c?: Contact | null): string | undefined => {
  if (!c) return undefined;
  const p = c.phones.find((x) => x.primary) || c.phones[0];
  return p?.number;
};

export const primaryEmail = (c?: Contact | null): string | undefined => {
  if (!c) return undefined;
  const e = c.emails.find((x) => x.primary) || c.emails[0];
  return e?.address;
};

export const hasWhatsApp = (c?: Contact | null) => !!c?.phones.length;

export const telegramHandle = (c?: Contact | null): string | undefined => {
  if (!c) return undefined;
  const tg = c.socials.find((s) => s.app === "telegram");
  if (tg) return tg.handle;
  const phone = c.phones.find((p) => p.primary) || c.phones[0];
  return phone ? `+${normalizePhone(phone.number)}` : undefined;
};

/** Payment-reminder message templates */
export const reminderTemplates = [
  {
    id: "gentle",
    title: "Gentle reminder",
    body: "Hi {name}, a gentle reminder about {amount} due on {date}. Thanks!",
  },
  {
    id: "receipt",
    title: "Payment receipt",
    body: "Hi {name}, I received your payment of {amount}. Thank you! Remaining balance: {balance}.",
  },
  {
    id: "bill",
    title: "Bill / pending items",
    body: "Hi {name}, here is your pending bill from \"{list}\":\n{items}\nTotal: {amount}\nThanks!",
  },
];

export const fillTemplate = (body: string, vars: Record<string, string>): string =>
  body.replace(/\{(\w+)\}/g, (_, k) => vars[k] ?? "");
