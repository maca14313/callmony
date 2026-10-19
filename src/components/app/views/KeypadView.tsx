"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { PhoneCall, UserPlus, Video } from "lucide-react";
import { useApp } from "@/lib/store";
import { useUI } from "@/lib/ui-store";
import { displayName, normalizePhone, prettyPhone, t9Match, vibrate } from "@/lib/utils";
import type { Contact } from "@/lib/types";
import { Avatar } from "../kit/primitives";
import { toastOk } from "../kit/toast";

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

export function KeypadView() {
  const [number, setNumber] = useState("");
  const allContacts = useApp((s) => s.contacts);
  const contacts = useMemo(() => allContacts.filter((c) => !c.deletedAt), [allContacts]);
  const settings = useApp((s) => s.settings);
  const ui = useUI();
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
      toastOk("Maximum number length reached");
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
      toastOk("Enter a number to get started");
      return;
    }
    const pretty = /[a-zA-Z]/.test(target)
      ? target
      : prettyPhone(target, settings.countryCode);
    ui.open({ type: "call", phone: pretty, contactId: contactId ?? exactMatch?.id });
  };

  /* keyboard support */
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.ctrlKey || e.metaKey || e.altKey) return;
      const target = e.target as HTMLElement;
      if (target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA")) return;
      if (ui.overlayStack.length) return;
      if (ui.tab !== "keypad") return;
      if (/^[0-9*#]$/.test(e.key)) addDigit(e.key);
      else if (e.key === "Backspace") deleteDigit();
      else if (e.key === "Escape") clearAll();
      else if (e.key === "Enter") makeCall();
      else if (e.key === "+") setNumber((n) => n + "+");
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [number, ui.tab, ui.overlayStack.length, exactMatch?.id]);

  /* speed dial via long-press on 2-9 */
  const speedDial = (digit: string) => {
    const contactId = digit !== "1" ? undefined : undefined;
    void contactId;
    const id = (settings as { speedDial?: Record<string, string> }).speedDial?.[digit];
    const contact = contacts.find((c) => c.id === id);
    if (contact) {
      const phone = contact.phones.find((p) => p.primary)?.number || contact.phones[0]?.number;
      if (phone) {
        toastOk(`Speed dial · ${displayName(contact)}`);
        makeCall(phone, contact.id);
        return;
      }
    }
    toastOk(`No contact on key ${digit} — set it in Favorites`);
  };

  return (
    <div className="tab-content">
      {/* number display */}
      <div className="relative flex min-h-[150px] flex-col items-center justify-center pt-3">
        <p className="mb-3 text-[10px] font-semibold uppercase tracking-[2.5px] text-[#9aa9a5] dark:text-mute">
          {number ? "Ready to connect" : "Enter a number"}
        </p>
        <div className="flex w-full items-center justify-center gap-1">
          <p
            className="min-h-[48px] max-w-full break-all px-1 text-center text-[32px] font-medium tracking-[1.2px] text-ink"
            aria-live="polite"
          >
            {number || ""}
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
              aria-label="Delete last digit (hold to clear)"
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
            onClick={() => ui.open({ type: "contact", contactId: exactMatch.id })}
            className="mt-1 flex items-center gap-1.5 rounded-full bg-brandsoft px-3 py-1 text-[12px] font-semibold text-brand transition active:scale-95"
          >
            <Avatar contact={exactMatch} size="xs" />
            {displayName(exactMatch)}
          </button>
        ) : (
          <p className="mt-1 h-4 text-[12px] font-medium text-brand">
            {matches[0] ? displayName(matches[0]) : ""}
          </p>
        )}
        {/* additional T9 matches */}
        {matches.length > (exactMatch ? 0 : 1) && (
          <div className="mt-2 flex flex-wrap justify-center gap-1.5">
            {matches
              .filter((m) => m.id !== exactMatch?.id)
              .slice(0, 2)
              .map((m) => (
                <button
                  key={m.id}
                  onClick={() => ui.open({ type: "contact", contactId: m.id })}
                  className="flex items-center gap-1.5 rounded-full border border-line2 bg-surface px-2.5 py-1 text-[11px] font-semibold text-ink shadow-key transition active:scale-95"
                >
                  <Avatar contact={m} size="xs" />
                  <span className="max-w-[110px] truncate">{displayName(m)}</span>
                </button>
              ))}
          </div>
        )}
      </div>

      {/* keypad */}
      <div className="mx-auto mt-1 grid max-w-[300px] grid-cols-3 gap-x-5 gap-y-[13px]">
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
            className="key flex h-[70px] flex-col items-center justify-center rounded-[23px] bg-surface shadow-key"
            aria-label={`${k.d}${k.letters ? ` ${k.letters}` : ""}${/[2-9]/.test(k.d) ? ", long-press for speed dial" : ""}`}
          >
            <span className="text-[27px] font-medium leading-8 text-ink">{k.d}</span>
            {k.letters && (
              <span className="mt-0.5 h-3 text-[9px] font-semibold tracking-[2.5px] text-mute">
                {k.letters}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* call controls */}
      <div className="mx-auto mt-6 flex max-w-[300px] items-center justify-between px-3">
        <button
          onClick={() => toastOk("Video calling is not connected in this demo")}
          className="flex h-11 w-11 items-center justify-center rounded-full border border-line2 bg-surface text-[#84938f] transition hover:bg-surface2 active:scale-95"
          aria-label="Video call"
        >
          <Video size={19} />
        </button>

        <button
          onClick={() => makeCall()}
          className="group flex h-[66px] w-[66px] items-center justify-center rounded-full bg-brand text-white shadow-floating transition duration-200 hover:scale-[1.04] hover:bg-brandink active:scale-95"
          aria-label={`Call ${number || "number"}`}
        >
          <PhoneCall size={27} />
        </button>

        <button
          onClick={() => {
            if (!number.trim()) {
              toastOk("Enter a phone number first");
              return;
            }
            ui.open({ type: "contactEdit", prefillPhone: number });
          }}
          className="flex h-11 w-11 items-center justify-center rounded-full border border-line2 bg-surface text-[#84938f] transition hover:bg-surface2 active:scale-95"
          aria-label="Add contact from number"
        >
          <UserPlus size={20} />
        </button>
      </div>

      <p className="mt-5 text-center text-[10px] tracking-wide text-[#a4b0ad] dark:text-mute/70">
        YOUR CONNECTION, SIMPLIFIED
      </p>
    </div>
  );
}
