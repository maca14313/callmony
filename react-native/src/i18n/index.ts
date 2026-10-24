/* ------------------------------------------------------------------ */
/* callmony i18n — English · አማርኛ · Afaan Oromo · العربية · ትግርኛ        */
/* EN is the source catalog — every other language must reach parity. */
/* ------------------------------------------------------------------ */

import { I18nManager } from "react-native";
import { useApp } from "../lib/store";
import { setDateNames } from "../lib/utils";
import { EN } from "./en";
import { AM } from "./am";
import { OM } from "./om";
import { AR } from "./ar";
import { TI } from "./ti";

export type Lang = "en" | "am" | "om" | "ar" | "ti";

export const LANGUAGES: { code: Lang; label: string; native: string; rtl: boolean }[] = [
  { code: "en", label: "English", native: "English", rtl: false },
  { code: "am", label: "Amharic", native: "አማርኛ", rtl: false },
  { code: "om", label: "Afaan Oromo", native: "Afaan Oromoo", rtl: false },
  { code: "ar", label: "Arabic", native: "العربية", rtl: true },
  { code: "ti", label: "Tigrinya", native: "ትግርኛ", rtl: false },
];

export const isRTL = (lang: Lang | undefined): boolean => {
  const l = LANGUAGES.find((x) => x.code === lang);
  return !!l?.rtl;
};

const DICTS: Record<Lang, Record<string, string>> = { en: EN, am: AM, om: OM, ar: AR, ti: TI };

const normalizeLang = (v: string | undefined): Lang => {
  const l = (v || "en") as Lang;
  return DICTS[l] ? l : "en";
};

/** Replace {placeholders} in a translated string. */
const fill = (s: string, vars?: Record<string, string | number>): string =>
  vars ? s.replace(/\{(\w+)\}/g, (_, k) => String(vars[k] ?? "")) : s;

/** Imperative translate — reads the current language from the store (no reactivity). */
export function t(key: string, vars?: Record<string, string | number>): string {
  const lang = normalizeLang(useApp.getState()?.settings?.language);
  ensureDateNames(lang);
  return fill(DICTS[lang][key] ?? EN[key] ?? key, vars);
}

/** Reactive translate — component re-renders when the language setting changes. */
export function useT(): (key: string, vars?: Record<string, string | number>) => string {
  const language = useApp((s) => s.settings.language);
  const lang = normalizeLang(language);
  ensureDateNames(lang);
  const dict = DICTS[lang];
  return (key: string, vars?: Record<string, string | number>) => fill(dict[key] ?? EN[key] ?? key, vars);
}

/** Current language + direction, reactive. */
export function useLang(): { lang: Lang; rtl: boolean; dir: "ltr" | "rtl" } {
  const language = useApp((s) => s.settings.language);
  const lang = normalizeLang(language);
  const rtl = isRTL(lang);
  return { lang, rtl, dir: rtl ? "rtl" : "ltr" };
}

/** Localized date names for the date formatter (utils). */
export const applyDateNames = (lang: Lang) => {
  const d = DICTS[lang];
  const months = (d["date.months"] ?? EN["date.months"] ?? "").split(",").map((s) => s.trim());
  const weekdays = (d["date.weekdays"] ?? EN["date.weekdays"] ?? "").split(",").map((s) => s.trim());
  setDateNames(months, weekdays, d["date.today"] ?? "Today", d["date.yesterday"] ?? "Yesterday");
};

/* Lazily keep date names in sync with the active language (never at module
   evaluation — the store/i18n/notify import graph must stay cycle-safe). */
let dateNamesLang: Lang | null = null;
const ensureDateNames = (lang: Lang) => {
  if (dateNamesLang !== lang) {
    dateNamesLang = lang;
    applyDateNames(lang);
  }
};

/**
 * Switch language. RTL (Arabic) flips the whole layout — applied on the
 * next launch (standard React Native behavior) with a restart hint.
 */
export const setLang = (lang: Lang): { rtlChanged: boolean } => {
  const prev = normalizeLang(useApp.getState().settings.language);
  useApp.getState().updateSettings({ language: lang });
  applyDateNames(lang);
  const rtlChanged = isRTL(prev) !== isRTL(lang);
  if (rtlChanged) {
    try {
      I18nManager.allowRTL(isRTL(lang));
      I18nManager.forceRTL(isRTL(lang));
    } catch {
      /* noop */
    }
  }
  return { rtlChanged };
};

// enable RTL layouts app-wide (actual direction resolves per language)
I18nManager.allowRTL(true);
