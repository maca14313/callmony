/* Communication shortcuts — deep links via Linking, plus message templates. */

import { Linking, Share as RNShare, Platform } from "react-native";
import { normalizePhone } from "./utils";
import { useApp } from "./store";
import { toast } from "../components/kit";
import type { DeviceContact, SocialLink } from "./types";

/* Open a deep link / web URL.
   IMPORTANT: we deliberately do NOT pre-check Linking.canOpenURL — on
   Android 11+ (API 30+) package-visibility filtering makes canOpenURL
   return false for mailto:/sms:/tel:/whatsapp/… unless the manifest
   declares <queries> for them, which made every social + email button
   silently "do nothing". Opening directly and catching the error is the
   reliable cross-version behavior (the OS resolver shows a picker or
   the app; a genuinely missing handler rejects and we report it). */
export const openURL = async (url: string): Promise<boolean> => {
  try {
    await Linking.openURL(url);
    return true;
  } catch {
    return false;
  }
};

/** openURL + user feedback: a toast when NO app could handle the link, so
    a tap can never again "do nothing" silently. */
export const openWithFeedback = async (url: string, failToast: string): Promise<boolean> => {
  const ok = await openURL(url);
  if (!ok) toast(failToast, "error");
  return ok;
};

export const smsLink = (number: string, text?: string): string => {
  const sep = Platform.OS === "ios" ? "&" : "?";
  return `sms:${number}${text ? `${sep}body=${encodeURIComponent(text)}` : ""}`;
};

export const waLink = (number: string, text?: string, countryCode = "ET"): string => {
  const norm = normalizePhone(number, countryCode);
  const clean = norm.replace(/[^\d]/g, "");
  return `https://wa.me/${clean}${text ? `?text=${encodeURIComponent(text)}` : ""}`;
};

export const tgLink = (handle: string): string => {
  const h = handle.trim().replace(/^@/, "");
  /* numeric handle = a phone number → +<e164> deep link (same builder the
     webapp uses for telegramHandle(contact) fallbacks) */
  if (/^\+?[\d\s()-]+$/.test(h)) {
    const digits = h.replace(/\D/g, "");
    if (digits.length >= 6) return `https://t.me/+${digits}`;
  }
  return `https://t.me/${encodeURIComponent(h)}`;
};

export const mailtoLink = (address: string, subject?: string, body?: string): string => {
  const params: string[] = [];
  if (subject) params.push(`subject=${encodeURIComponent(subject)}`);
  if (body) params.push(`body=${encodeURIComponent(body)}`);
  return `mailto:${address}${params.length ? `?${params.join("&")}` : ""}`;
};

export const shareText = async (title: string, message: string): Promise<boolean> => {
  try {
    await RNShare.share({ title, message }, { dialogTitle: title });
    return true;
  } catch {
    return false;
  }
};

/** First usable phone for calling — respects the app-side “primary phone"
    override, then mobile-label, then the first number. This is the number
    shown in the contact header and dialed by default (always accurate). */
export const primaryPhone = (c?: DeviceContact | null): string | undefined => {
  if (!c) return undefined;
  const stored = useApp.getState().contactX.find((x) => x.recordID === c.recordID)?.primaryPhone;
  if (stored) {
    const match = c.phones.find((p) => p.number.replace(/\D/g, "") === stored.replace(/\D/g, ""));
    if (match) return match.number;
  }
  const p = c.phones.find((x) => /mobile|main/i.test(x.label)) || c.phones[0];
  return p?.number;
};

export const primaryEmail = (c?: DeviceContact | null): string | undefined => {
  if (!c) return undefined;
  return c.emails[0]?.address;
};

/** Telegram handle — a saved social handle if any, else the primary phone. */
export const telegramHandle = (c?: DeviceContact | null, countryCode = "ET"): string | undefined => {
  if (!c) return undefined;
  const social = useApp
    .getState()
    .contactX.find((x) => x.recordID === c.recordID)
    ?.socials?.find((s) => s.app === "telegram");
  if (social) return social.handle;
  const phone = primaryPhone(c);
  return phone ? `+${normalizePhone(phone, countryCode)}` : undefined;
};

/** Build the deep-link URL for a saved social handle (null = copy instead). */
export const socialUrl = (s: SocialLink): string | null => {
  const h = s.handle.trim().replace(/^@/, "");
  switch (s.app) {
    case "telegram":
      return tgLink(s.handle);
    case "whatsapp":
      return waLink(h);
    case "instagram":
      return `https://instagram.com/${encodeURIComponent(h)}`;
    case "x":
      return `https://x.com/${encodeURIComponent(h)}`;
    case "signal":
      return `https://signal.me/#p/${encodeURIComponent(h)}`;
    default:
      return /^https?:\/\//i.test(h) ? h : null;
  }
};

/* ---------------- social links BUILT FROM A PHONE NUMBER ----------------
   Every messaging app that accepts a phone-number deep link gets one
   generated straight from the contact's number — no handle needed:
     WhatsApp  → https://wa.me/<e164>
     Telegram  → https://t.me/+<e164>       (same as the web edition)
     Signal    → https://signal.me/#p/<e164>
     Viber     → viber://chat?number=%2B<e164>
   Instagram / X / other need real usernames → null. */

export const NUMBER_SOCIAL_APPS: SocialLink["app"][] = ["whatsapp", "telegram", "signal", "viber"];

export const socialLinkFromNumber = (
  app: SocialLink["app"],
  number: string,
  countryCode = "ET"
): string | null => {
  const e164 = normalizePhone(number, countryCode).replace(/\D/g, "");
  if (!e164 || e164.length < 6) return null;
  switch (app) {
    case "whatsapp":
      return `https://wa.me/${e164}`;
    case "telegram":
      return `https://t.me/+${e164}`;
    case "signal":
      return `https://signal.me/#p/%2B${e164}`;
    case "viber":
      return `viber://chat?number=%2B${e164}`;
    default:
      return null;
  }
};

/** True when the app can open a chat from a bare phone number. */
export const appTakesNumber = (app: SocialLink["app"]): boolean => NUMBER_SOCIAL_APPS.includes(app);

/** Payment-reminder message templates (same as the web edition). */
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
    body: 'Hi {name}, here is your pending bill from "{list}":\n{items}\nTotal: {amount}\nThanks!',
  },
];

export const fillTemplate = (body: string, vars: Record<string, string>): string =>
  body.replace(/\{(\w+)\}/g, (_, k) => vars[k] ?? "");
