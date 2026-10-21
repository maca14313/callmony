"use client";

import { useEffect, useRef, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { Copy, Delete, History, X } from "lucide-react";
import { copyText } from "@/lib/utils";
import { useT } from "@/lib/i18n";
import { Button, cx } from "./primitives";
import { toastOk } from "./toast";
import { useUI } from "@/lib/ui-store";

/* Calculator engine + keypad — used as a sheet from amount fields and
   anywhere quick math is needed. The WHOLE calculation ("100 + 50 =")
   renders in the MAIN display with big numbers; the result gets the
   biggest line once "=" is pressed. */

type Op = "+" | "−" | "×" | "÷";

interface HistEntry {
  expr: string;
  result: string;
}

export function Calculator({ onUse }: { onUse?: (value: number) => void }) {
  const t = useT();
  /* one state object + functional updaters → rapid/batched key presses
     can never read a stale closure (digits were lost otherwise). */
  interface CalcState {
    display: string;
    expr: string; // "100 +" — shown in the main display area
    acc: number | null;
    op: Op | null;
    fresh: boolean; // next digit starts a new number
  }
  const [cs, setCs] = useState<CalcState>({ display: "0", expr: "", acc: null, op: null, fresh: true });
  const [history, setHistory] = useState<HistEntry[]>([]);
  const [showHistory, setShowHistory] = useState(false);
  const [memory, setMemory] = useState<number | null>(null);

  const { display, expr } = cs;

  const fmt = (n: number): string => {
    if (!isFinite(n)) return "Error";
    const s = Math.abs(n) >= 1e12 ? n.toExponential(6) : String(Math.round(n * 1e10) / 1e10);
    return s.length > 14 ? Number(n.toPrecision(10)).toString() : s;
  };

  const apply = (a: number, b: number, o: Op): number => {
    switch (o) {
      case "+": return a + b;
      case "−": return a - b;
      case "×": return a * b;
      case "÷": return b === 0 ? NaN : a / b;
    }
  };

  const inputDigit = (d: string) =>
    setCs((s) => {
      if (s.fresh) {
        return {
          ...s,
          display: d === "." ? "0." : d,
          fresh: false,
          expr: s.expr.endsWith("=") ? "" : s.expr,
        };
      }
      if (d === "." && s.display.includes(".")) return s;
      if (s.display.replace(/[-.]/g, "").length >= 12) return s;
      return { ...s, display: s.display === "0" && d !== "." ? d : s.display + d };
    });

  const chooseOp = (o: Op) =>
    setCs((s) => {
      const n = parseFloat(s.display.replace(/,/g, "")) || 0;
      if (s.op && !s.fresh && s.acc !== null) {
        const r = apply(s.acc, n, s.op);
        return { ...s, acc: r, display: fmt(r), expr: `${fmt(r)} ${o}`, op: o, fresh: true };
      }
      return { ...s, acc: n, expr: `${fmt(n)} ${o}`, op: o, fresh: true };
    });

  const equals = () => {
    const s = cs; // each real key press / click is its own event → committed state is fresh
    if (s.op === null || s.acc === null) return;
    const n = parseFloat(s.display.replace(/,/g, "")) || 0;
    const r = apply(s.acc, n, s.op);
    const fullExpr = `${fmt(s.acc)} ${s.op} ${fmt(n)} =`;
    setCs({ ...s, display: fmt(r), expr: fullExpr, acc: null, op: null, fresh: true });
    setHistory((h) => [{ expr: fullExpr, result: fmt(r) }, ...h].slice(0, 30));
  };

  const clearAll = () => setCs({ display: "0", expr: "", acc: null, op: null, fresh: true });

  const backspace = () =>
    setCs((s) =>
      s.fresh
        ? s
        : { ...s, display: s.display.length <= 1 || (s.display.length === 2 && s.display.startsWith("-")) ? "0" : s.display.slice(0, -1) }
    );

  /* ⌫ hold-to-repeat: the press itself deletes one digit on pointerdown;
     holding past 450ms repeats every 110ms (real-calculator UX). Timer ids
     live in refs so re-renders never orphan them; any pointer exit clears
     both. backspace only calls setCs with a pure updater → the interval's
     closure stays valid across re-renders. */
  const holdDelay = useRef<ReturnType<typeof setTimeout> | null>(null);
  const holdLoop = useRef<ReturnType<typeof setInterval> | null>(null);
  const pressFired = useRef(false); // the click after a pointer press is a duplicate

  const stopHold = () => {
    if (holdDelay.current !== null) clearTimeout(holdDelay.current);
    if (holdLoop.current !== null) clearInterval(holdLoop.current);
    holdDelay.current = null;
    holdLoop.current = null;
  };

  // never leave a repeat loop running after unmount
  useEffect(
    () => () => {
      if (holdDelay.current !== null) clearTimeout(holdDelay.current);
      if (holdLoop.current !== null) clearInterval(holdLoop.current);
    },
    []
  );

  const percent = () =>
    setCs((s) => {
      const n = parseFloat(s.display.replace(/,/g, "")) || 0;
      const r = s.acc !== null && s.op ? (n / 100) * s.acc : n / 100;
      return { ...s, display: fmt(r), fresh: false };
    });

  const toggleSign = () =>
    setCs((s) => ({
      ...s,
      display: s.display.startsWith("-") ? s.display.slice(1) : s.display === "0" ? s.display : "-" + s.display,
    }));

  const pretty = display.length > 12 ? display : (() => {
    const [int, dec] = display.split(".");
    const grouped = int.replace(/\B(?=(\d{3})+(?!\d))/g, " ");
    return grouped + (dec !== undefined ? "." + dec : "");
  })();

  /* ------- the full calculation, rendered as the main big display ------- */
  const afterEquals = expr.includes("=");
  const fullExpr = afterEquals
    ? expr // "100 + 50 ="
    : cs.op && cs.acc !== null
      ? `${fmt(cs.acc)} ${cs.op} ${cs.fresh ? "" : pretty}` // "100 + 50" while typing
      : pretty; // plain number
  const resultLine = afterEquals ? pretty : null;

  /* static Tailwind buckets — arbitrary values must be static to be compiled */
  const bucket = (len: number, sizes: [string, string, string, string]) =>
    len <= 9 ? sizes[0] : len <= 14 ? sizes[1] : len <= 22 ? sizes[2] : sizes[3];

  const KEYS: { k: string; sub?: string; cls?: string; fn: () => void; icon?: typeof Delete; hold?: boolean }[] = [
    { k: "C", cls: "func", fn: clearAll },
    { k: "±", cls: "func", fn: toggleSign },
    { k: "%", cls: "func", fn: percent },
    { k: "÷", cls: "op", fn: () => chooseOp("÷") },
    { k: "7", fn: () => inputDigit("7") },
    { k: "8", fn: () => inputDigit("8") },
    { k: "9", fn: () => inputDigit("9") },
    { k: "×", cls: "op", fn: () => chooseOp("×") },
    { k: "4", fn: () => inputDigit("4") },
    { k: "5", fn: () => inputDigit("5") },
    { k: "6", fn: () => inputDigit("6") },
    { k: "−", cls: "op", fn: () => chooseOp("−") },
    { k: "1", fn: () => inputDigit("1") },
    { k: "2", fn: () => inputDigit("2") },
    { k: "3", fn: () => inputDigit("3") },
    { k: "+", cls: "op", fn: () => chooseOp("+") },
    { k: "0", fn: () => inputDigit("0") },
    { k: ".", fn: () => inputDigit(".") },
    { k: "⌫", cls: "func", fn: backspace, icon: Delete, hold: true },
    { k: "=", cls: "eq", fn: equals },
  ];

  return (
    <div className="flex h-full flex-col">
      {/* memory row */}
      <div className="flex items-center justify-between px-3 pt-1 text-[10px] font-semibold text-mute">
        <div className="flex items-center gap-2">
          {memory !== null && (
            <button
              className="rounded-full bg-brandsoft px-2 py-0.5 text-brand"
              onClick={() => {
                setCs((s) => ({ ...s, display: fmt(memory), fresh: false }));
              }}
            >
              M {fmt(memory).slice(0, 8)}
            </button>
          )}
        </div>
        <div className="flex gap-1">
          <button
            className="rounded-full px-2 py-0.5 hover:bg-surface2"
            onClick={() => setMemory(parseFloat(display.replace(/,/g, "")) || 0)}
            aria-label="Store in memory"
          >
            M+
          </button>
          <button
            className="rounded-full px-2 py-0.5 hover:bg-surface2"
            onClick={() => setMemory(null)}
            aria-label="Clear memory"
          >
            MC
          </button>
        </div>
      </div>

      {/* MAIN DISPLAY — the whole calculation in big numbers */}
      <div className="flex min-h-[96px] flex-col items-end justify-end px-4 pb-3 pt-2">
        {resultLine && (
          <p
            className={cx(
              "max-w-full break-all text-right font-semibold tabular-nums tracking-wide text-mute",
              bucket(fullExpr.length, ["text-[24px]", "text-[20px]", "text-[16px]", "text-[13px]"])
            )}
            aria-live="polite"
          >
            {fullExpr}
          </p>
        )}
        {resultLine ? (
          <p
            className={cx(
              "max-w-full break-all text-right font-bold tabular-nums tracking-wide text-ink",
              bucket(resultLine.length, ["text-[42px]", "text-[34px]", "text-[26px]", "text-[20px]"])
            )}
            aria-live="polite"
          >
            {resultLine}
          </p>
        ) : (
          <p
            className={cx(
              "max-w-full break-all text-right font-medium tabular-nums tracking-wide text-ink",
              bucket(fullExpr.length, ["text-[38px]", "text-[30px]", "text-[24px]", "text-[18px]"])
            )}
            aria-live="polite"
          >
            {fullExpr}
          </p>
        )}
      </div>

      {/* history */}
      <AnimatePresence initial={false}>
        {showHistory && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            className="overflow-hidden"
          >
            <div className="app-scroll max-h-32 overflow-y-auto rounded-2xl border border-line bg-surface p-2 mx-2">
              {history.length === 0 ? (
                <p className="py-3 text-center text-[11px] text-mute">{t("calc.noCalculations")}</p>
              ) : (
                history.map((h, i) => (
                  <button
                    key={i}
                    className="flex w-full items-center justify-between rounded-xl px-3 py-2 text-left hover:bg-surface2"
                    onClick={() => {
                      setCs((s) => ({ ...s, display: h.result, expr: h.expr, fresh: false }));
                      setShowHistory(false);
                    }}
                  >
                    <span className="text-[11px] text-mute">{h.expr}</span>
                    <span className="text-xs font-semibold tabular-nums text-ink">{h.result}</span>
                  </button>
                ))
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* keypad */}
      <div className="mt-auto grid grid-cols-4 gap-2.5 p-2 pb-3">
        <div className="col-span-4 flex items-center justify-between px-1 pb-1">
          <button
            className="flex items-center gap-1.5 rounded-full px-3 py-1.5 text-[11px] font-semibold text-mute transition hover:bg-surface2"
            onClick={() => setShowHistory((v) => !v)}
            aria-label="Toggle history"
          >
            <History size={14} /> {t("calc.history")}
          </button>
          <button
            className="flex items-center gap-1.5 rounded-full px-3 py-1.5 text-[11px] font-semibold text-mute transition hover:bg-surface2"
            onClick={async () => {
              if (await copyText(display)) toastOk(t("act.copied"));
            }}
            aria-label="Copy result"
          >
            <Copy size={14} /> {t("act.copy")}
          </button>
        </div>
        {KEYS.map((key) => {
          const KeyIcon = key.icon;
          return (
            <button
              key={key.k}
              onClick={
                key.hold
                  ? () => {
                      /* pointerdown already deleted — this click is a duplicate
                         unless it was keyboard activation (Enter/Space) */
                      if (pressFired.current) {
                        pressFired.current = false;
                        return;
                      }
                      key.fn();
                    }
                  : key.fn
              }
              onPointerDown={
                key.hold
                  ? () => {
                      pressFired.current = true;
                      key.fn();
                      stopHold();
                      holdDelay.current = setTimeout(() => {
                        holdDelay.current = null;
                        holdLoop.current = setInterval(() => key.fn(), 110);
                      }, 450);
                    }
                  : undefined
              }
              onPointerUp={key.hold ? stopHold : undefined}
              onPointerLeave={key.hold ? stopHold : undefined}
              onPointerCancel={key.hold ? stopHold : undefined}
              className={cx(
                "key flex h-[clamp(44px,7.5dvh,58px)] items-center justify-center rounded-[19px] text-[21px] font-medium shadow-key transition active:scale-95",
                key.cls === "op" || key.cls === "eq"
                  ? "bg-brand text-white shadow-floating"
                  : key.cls === "func"
                    ? "bg-surface2 text-mute"
                    : "bg-surface text-ink",
                key.cls === "eq" && "bg-brandink"
              )}
              aria-label={key.hold ? t("calc.backspace") : key.k}
            >
              {KeyIcon ? <KeyIcon size={20} /> : key.k}
            </button>
          );
        })}
        {onUse && (
          <div className="col-span-4 pt-1">
            <Button
              variant="brand"
              size="lg"
              className="w-full"
              onClick={() => onUse(parseFloat(display.replace(/,/g, "").replace(/ /g, "")) || 0)}
            >
              {t("calc.useValue")}
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}

/** Calculator as an in-frame sheet (opened from amount inputs via useUI.openCalculator). */
export function CalculatorSheet() {
  const t = useT();
  const pop = useUI((s) => s.pop);
  const handler = useUI((s) => s.calculatorPickHandler);
  const clearHandler = useUI((s) => s.clearCalculatorHandler);
  /* Only the calculator sheet itself is popped — the sheet that opened it
     (EntryEdit, Add payment, …) must stay open so the picked value lands. */
  const dismiss = () => {
    pop();
    clearHandler();
  };
  return (
    <div className="absolute inset-0 flex flex-col bg-canvas" style={{ zIndex: 70 }}>
      <header className="flex items-center justify-between border-b border-line px-3 py-3">
        <h2 className="px-2 text-[17px] font-bold tracking-tight text-ink">{t("nav.calculator")}</h2>
        <button
          onClick={dismiss}
          aria-label={t("act.close")}
          className="flex h-9 w-9 items-center justify-center rounded-full text-mute hover:bg-surface2"
        >
          <X size={18} />
        </button>
      </header>
      <div className="flex-1 overflow-hidden">
        <Calculator
          onUse={
            handler
              ? (v) => {
                  handler(v);
                  dismiss();
                }
              : undefined
          }
        />
      </div>
    </div>
  );
}

/** Small calculator icon button for amount inputs. */
export function CalcButton({ onPick }: { onPick: (v: number) => void }) {
  const openCalculator = useUI((s) => s.openCalculator);
  return (
    <button
      type="button"
      aria-label="Open calculator"
      onClick={() => openCalculator(onPick)}
      className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border border-line bg-surface text-brand shadow-key transition active:scale-95"
    >
      <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
        <rect x="5" y="3" width="14" height="18" rx="3" />
        <path d="M9 7h6M9 11h.01M12 11h.01M15 11h.01M9 14h.01M12 14h.01M15 14h.01M9 17h.01M12 17h.01M15 17h.01" />
      </svg>
    </button>
  );
}
