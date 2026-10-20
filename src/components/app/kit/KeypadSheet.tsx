"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { Grid3X3, PhoneCall, UserPlus, Video, X } from "lucide-react";
import { useApp } from "@/lib/store";
import { useUI } from "@/lib/ui-store";
import { displayName, normalizePhone, prettyPhone, t9Match, vibrate } from "@/lib/utils";
import type { Contact } from "@/lib/types";
import { useT } from "@/lib/i18n";
import { Avatar } from "./primitives";
import { toastOk } from "./toast";

/* ------------------------------------------------------------------ */
/* Keypad — a half-modal that rises from the bottom of the content     */
/* area. Sized with dvh clamps so the WHOLE keypad is always visible   */
/* on any screen height.                                               */
/* ------------------------------------------------------------------ */

const KEYS = [
  { d: "1", letters: "" },
  { d: "2", letters: "ABC" },
  { d: "3", letters: "DEF" },
  { d: "4", letters: "GHI" },
  { d: "5", letters: "JKL" },
  { d: "6", letters: "MNO" },
  { d: "7", letters: "PQRS" },
  { d: "8", letters: "TUV" },
  { d: "9", letters: "WXYZ" },
  { d: "*", letters: "" },
  { d: "0", letters: "+" },
  { d: "#", letters: "" },
];

export function KeypadSheet() {
  const t = useT();
  const open = useUI((s) => s.keypadOpen);
  const setKeypadOpen = useUI((s) => s.setKeypadOpen);
  const ui = useUI();
  const [number, setNumber] = useState("");
  const allContacts = useApp((s) => s.contacts);
  const contacts = useMemo(() => allContacts.filter((c) => !c.deletedAt), [allContacts]);
  const settings = useApp((s) => s.settings);
  const longPressRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const backspaceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  /* contact matches: T9 on name, or number contains/suffix */
  const matches = useMemo<Contact[]>(() => {
    if (number.length < 2) return [];
    const digitsOnly = number.replace(/[^\d]/g, "");
    const query = number.toLowerCase();
    const scored = contacts
      .map((c) => {
        let score = -1;
        const name = displayName(c).toLowerCase();
        if (t9Match(number, name)) score = 3;
        else if (name.includes(query)) score = 2;
        if (digitsOnly.length >= 3) {
          const hit = c.phones.some((p) => {
            const n = normalizePhone(p.number, settings.countryCode).replace(/[^\d]/g, "");
            return n.includes(digitsOnly) || digitsOnly.includes(n.slice(-digitsOnly.length));
          });
          if (hit) score = Math.max(score, 4);
        }
        return { c, score };
      })
      .filter((x) => x.score > 0)
      .sort((a, b) => b.score - a.score || displayName(a.c).localeCompare(displayName(b.c)));
    return scored.slice(0, 3).map((x) => x.c);
  }, [number, contacts, settings.countryCode]);

  const exactMatch = useMemo(
    () =>
      number.replace(/[^\d]/g, "").length >= 6
        ? contacts.find((c) =>
            c.phones.some(
              (p) => normalizePhone(p.number, settings.countryCode) === normalizePhone(number, settings.countryCode)
            )
          )
        : undefined,
    [number, contacts, settings.countryCode]
  );

  const addDigit = (d: string) => {
    if (number.length >= 24) {
      toastOk(t("kp.maxDigits"));
      return;
    }
    vibrate(8);
    setNumber((n) => n + d);
  };

  const deleteDigit = () => setNumber((n) => n.slice(0, -1));
  const clearAll = () => setNumber("");

  const makeCall = (phone?: string, contactId?: string) => {
    const target = phone ?? number;
    if (!target.trim()) {
      toastOk(t("kp.enterNumber"));
      return;
    }
    const pretty = /[a-zA-Z]/.test(target) ? target : prettyPhone(target, settings.countryCode);
    setKeypadOpen(false);
    ui.open({ type: "call", phone: pretty, contactId: contactId ?? exactMatch?.id });
  };

  /* keyboard support while the sheet is open */
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.ctrlKey || e.metaKey || e.altKey) return;
      const target = e.target as HTMLElement;
      if (target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA")) return;
      if (e.key === "Escape") {
        setKeypadOpen(false);
        return;
      }
      if (/^[0-9*#]$/.test(e.key)) addDigit(e.key);
      else if (e.key === "Backspace") deleteDigit();
      else if (e.key === "Enter") makeCall();
      else if (e.key === "+") setNumber((n) => n + "+");
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, number, exactMatch?.id]);

  /* speed dial via long-press on 2-9 */
  const speedDial = (digit: string) => {
    const id = (settings as { speedDial?: Record<string, string> }).speedDial?.[digit];
    const contact = contacts.find((c) => c.id === id);
    if (contact) {
      const phone = contact.phones.find((p) => p.primary)?.number || contact.phones[0]?.number;
      if (phone) {
        toastOk(t("kp.speedDialOf", { name: displayName(contact) }));
        makeCall(phone, contact.id);
        return;
      }
    }
    ui.open({ type: "speedDial" });
  };

  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.button
            key="kp-backdrop"
            aria-label={t("act.close")}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => setKeypadOpen(false)}
            className="absolute inset-0 cursor-default"
            style={{ zIndex: 64, background: "var(--app-scrim)" }}
          />
          <motion.div
            key="kp-panel"
            initial={{ y: "110%" }}
            animate={{ y: 0 }}
            exit={{ y: "110%" }}
            transition={{ type: "spring", damping: 30, stiffness: 320 }}
            className="keypad-sheet absolute inset-x-0 bottom-0 flex flex-col overflow-hidden rounded-t-[28px] border-t border-line bg-canvas shadow-soft"
            style={{ zIndex: 65 }}
            role="dialog"
            aria-label={t("nav.keypad")}
          >
            {/* header */}
            <div className="relative flex shrink-0 items-center justify-center px-4 pb-1 pt-2.5">
              <div className="absolute h-1 w-10 rounded-full bg-line2" />
              <h2 className="mt-2 text-[13px] font-bold tracking-wide text-mute">{t("nav.keypad")}</h2>
              <button
                onClick={() => setKeypadOpen(false)}
                aria-label={t("act.close")}
                className="absolute right-3 top-1.5 flex h-9 w-9 items-center justify-center rounded-full text-mute transition hover:bg-surface2"
              >
                <X size={17} />
              </button>
              <button
                onClick={() => {
                  setKeypadOpen(false);
                  ui.open({ type: "speedDial" });
                }}
                aria-label={t("kp.speedDial")}
                className="absolute left-3 top-1.5 flex h-9 w-9 items-center justify-center rounded-full text-mute transition hover:bg-surface2"
              >
                <Grid3X3 size={16} />
              </button>
            </div>

            {/* number display */}
            <div className="relative flex min-h-[clamp(56px,9dvh,84px)] shrink-0 flex-col items-center justify-center px-4">
              <div className="flex w-full items-center justify-center gap-1">
                <p
                  className="min-h-[38px] max-w-full break-all px-1 text-center text-[clamp(22px,4.5dvh,32px)] font-medium tracking-[1.2px] text-ink"
                  aria-live="polite"
                >
                  {number || <span className="text-[13px] font-semibold uppercase tracking-[2.5px] text-mute">{t("kp.enterNumber")}</span>}
                </p>
                {number && (
                  <button
                    onClick={deleteDigit}
                    onPointerDown={() => {
                      backspaceRef.current = setTimeout(clearAll, 600);
                    }}
                    onPointerUp={() => {
                      if (backspaceRef.current) clearTimeout(backspaceRef.current);
                    }}
                    onPointerLeave={() => {
                      if (backspaceRef.current) clearTimeout(backspaceRef.current);
                    }}
                    className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-[#93a29e] transition hover:bg-brandsoft hover:text-ink"
                    aria-label={t("kp.deleteDigit")}
                  >
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M21 4H8l-5 8 5 8h13a1 1 0 0 0 1-1V5a1 1 0 0 0-1-1z" />
                      <path d="m10 9 6 6m0-6-6 6" />
                    </svg>
                  </button>
                )}
              </div>
              {exactMatch ? (
                <button
                  onClick={() => {
                    setKeypadOpen(false);
                    ui.open({ type: "contact", contactId: exactMatch.id });
                  }}
                  className="mt-0.5 flex items-center gap-1.5 rounded-full bg-brandsoft px-3 py-1 text-[12px] font-semibold text-brand transition active:scale-95"
                >
                  <Avatar contact={exactMatch} size="xs" />
                  {displayName(exactMatch)}
                </button>
              ) : (
                <p className="mt-0.5 h-4 text-[12px] font-medium text-brand">
                  {matches[0] ? displayName(matches[0]) : ""}
                </p>
              )}
            </div>

            {/* keypad grid — dvh clamps keep it fully visible on any screen */}
            <div className="mx-auto grid w-full max-w-[300px] shrink-0 grid-cols-3 gap-x-4 gap-y-[clamp(6px,1.4dvh,13px)] px-4 pt-1">
              {KEYS.map((k) => (
                <button
                  key={k.d}
                  onClick={() => {
                    if (longPressRef.current) clearTimeout(longPressRef.current);
                    addDigit(k.d);
                  }}
                  onPointerDown={() => {
                    if (k.d === "0" || /[2-9]/.test(k.d)) {
                      longPressRef.current = setTimeout(() => {
                        vibrate(20);
                        if (k.d === "0") setNumber((n) => n.slice(0, -1) + "+");
                        else speedDial(k.d);
                      }, 550);
                    }
                  }}
                  onPointerUp={() => {
                    if (longPressRef.current) clearTimeout(longPressRef.current);
                  }}
                  onPointerLeave={() => {
                    if (longPressRef.current) clearTimeout(longPressRef.current);
                  }}
                  className="key flex h-[clamp(44px,8.2dvh,64px)] flex-col items-center justify-center rounded-[clamp(15px,2.4dvh,23px)] bg-surface shadow-key"
                  aria-label={`${k.d}${k.letters ? ` ${k.letters}` : ""}${/[2-9]/.test(k.d) ? ", long-press for speed dial" : ""}`}
                >
                  <span className="text-[clamp(19px,3.4dvh,27px)] font-medium leading-7 text-ink">{k.d}</span>
                  {k.letters && (
                    <span className="mt-0.5 h-3 text-[9px] font-semibold tracking-[2.5px] text-mute">
                      {k.letters}
                    </span>
                  )}
                </button>
              ))}
            </div>

            {/* call controls */}
            <div className="mx-auto flex w-full max-w-[300px] shrink-0 items-center justify-between px-5 pb-[max(10px,env(safe-area-inset-bottom))] pt-[clamp(6px,1.4dvh,14px)]">
              <button
                onClick={() => toastOk(t("kp.videoDemo"))}
                className="flex h-11 w-11 items-center justify-center rounded-full border border-line2 bg-surface text-[#84938f] transition hover:bg-surface2 active:scale-95"
                aria-label="Video call"
              >
                <Video size={19} />
              </button>

              <button
                onClick={() => makeCall()}
                className="group flex h-[clamp(56px,8.5dvh,66px)] w-[clamp(56px,8.5dvh,66px)] items-center justify-center rounded-full bg-brand text-white shadow-floating transition duration-200 hover:scale-[1.04] hover:bg-brandink active:scale-95"
                aria-label={number ? t("kp.callNumber", { number }) : t("act.dial")}
              >
                <PhoneCall size={26} />
              </button>

              <button
                onClick={() => {
                  if (!number.trim()) {
                    toastOk(t("kp.enterNumber"));
                    return;
                  }
                  setKeypadOpen(false);
                  ui.open({ type: "contactEdit", prefillPhone: number });
                }}
                className="flex h-11 w-11 items-center justify-center rounded-full border border-line2 bg-surface text-[#84938f] transition hover:bg-surface2 active:scale-95"
                aria-label={t("act.newContact")}
              >
                <UserPlus size={20} />
              </button>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
