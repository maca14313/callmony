"use client";

/* ------------------------------------------------------------------ */
/* callmony i18n — English · አማርኛ · Afaan Oromo · العربية · ትግርኛ      */
/* EN is the source catalog — every other language must reach parity. */
/* ------------------------------------------------------------------ */

import { useApp } from "@/lib/store";
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
  return fill(DICTS[lang][key] ?? EN[key] ?? key, vars);
}

/** Reactive translate — component re-renders when the language setting changes. */
export function useT(): (key: string, vars?: Record<string, string | number>) => string {
  const language = useApp((s) => s.settings.language);
  const lang = normalizeLang(language);
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

export const setLang = (lang: Lang) => useApp.getState().updateSettings({ language: lang });
