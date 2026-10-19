"use client";

import { useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { Copy, History, X } from "lucide-react";
import { copyText } from "@/lib/utils";
import { Button, cx } from "./primitives";
import { toastOk } from "./toast";
import { useUI } from "@/lib/ui-store";

/* Calculator engine + keypad — used as a full view, a sheet from amount fields,
   and anywhere quick math is needed. */

type Op = "+" | "−" | "×" | "÷";

interface HistEntry {
  expr: string;
  result: string;
}

export function Calculator({ onUse }: { onUse?: (value: number) => void }) {
  const [display, setDisplay] = useState("0");
  const [acc, setAcc] = useState<number | null>(null);
  const [op, setOp] = useState<Op | null>(null);
  const [fresh, setFresh] = useState(true); // next digit starts a new number
  const [history, setHistory] = useState<HistEntry[]>([]);
  const [showHistory, setShowHistory] = useState(false);
  const [memory, setMemory] = useState<number | null>(null);

  const num = parseFloat(display.replace(/,/g, "")) || 0;

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

  const inputDigit = (d: string) => {
    if (fresh) {
      setDisplay(d === "." ? "0." : d);
      setFresh(false);
      return;
    }
    if (d === "." && display.includes(".")) return;
    if (display.replace(/[-.]/g, "").length >= 12) return;
    setDisplay(display === "0" && d !== "." ? d : display + d);
  };

  const chooseOp = (o: Op) => {
    if (op && !fresh && acc !== null) {
      const r = apply(acc, num, op);
      setAcc(r);
      setDisplay(fmt(r));
    } else {
      setAcc(num);
    }
    setOp(o);
    setFresh(true);
  };

  const equals = () => {
    if (op === null || acc === null) return;
    const r = apply(acc, num, op);
    const expr = `${fmt(acc)} ${op} ${fmt(num)}`;
    setDisplay(fmt(r));
    setHistory((h) => [{ expr, result: fmt(r) }, ...h].slice(0, 30));
    setAcc(null);
    setOp(null);
    setFresh(true);
  };

  const clearAll = () => {
    setDisplay("0");
    setAcc(null);
    setOp(null);
    setFresh(true);
  };

  const backspace = () => {
    if (fresh) return;
    setDisplay((d) => (d.length <= 1 || (d.length === 2 && d.startsWith("-")) ? "0" : d.slice(0, -1)));
  };

  const percent = () => {
    const r = acc !== null && op ? (num / 100) * acc : num / 100;
    setDisplay(fmt(r));
    setFresh(false);
  };

  const toggleSign = () => setDisplay((d) => (d.startsWith("-") ? d.slice(1) : d === "0" ? d : "-" + d));

  const pretty = display.length > 12 ? display : (() => {
    const [int, dec] = display.split(".");
    const grouped = int.replace(/\B(?=(\d{3})+(?!\d))/g, " ");
    return grouped + (dec !== undefined ? "." + dec : "");
  })();

  const KEYS: { k: string; sub?: string; cls?: string; fn: () => void }[] = [
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
    { k: "0", cls: "zero", fn: () => inputDigit("0") },
    { k: ".", fn: () => inputDigit(".") },
    { k: "=", cls: "eq", fn: equals },
  ];

  return (
    <div className="flex h-full flex-col">
      {/* memory + small hints */}
      <div className="flex items-center justify-between px-2 text-[10px] font-semibold text-mute">
        <div className="flex items-center gap-2">
          {memory !== null && (
            <button
              className="rounded-full bg-brandsoft px-2 py-0.5 text-brand"
              onClick={() => {
                setDisplay(fmt(memory));
                setFresh(false);
              }}
            >
              M {fmt(memory).slice(0, 8)}
            </button>
          )}
          {op && acc !== null && (
            <span className="tabular-nums">
              {fmt(acc)} {op}
            </span>
          )}
        </div>
        <div className="flex gap-1">
          <button
            className="rounded-full px-2 py-0.5 hover:bg-surface2"
            onClick={() => setMemory(num)}
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

      {/* display */}
      <div className="flex min-h-[86px] flex-col items-end justify-end px-3 pb-3 pt-2">
        <p
          className={cx(
            "max-w-full break-all text-right font-medium tabular-nums tracking-wide text-ink",
            pretty.length > 9 ? "text-[26px]" : "text-[34px]"
          )}
          aria-live="polite"
        >
          {pretty}
        </p>
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
            <div className="app-scroll max-h-32 overflow-y-auto rounded-2xl border border-line bg-surface p-2 mx-1">
              {history.length === 0 ? (
                <p className="py-3 text-center text-[11px] text-mute">No calculations yet</p>
              ) : (
                history.map((h, i) => (
                  <button
                    key={i}
                    className="flex w-full items-center justify-between rounded-xl px-3 py-2 text-left hover:bg-surface2"
                    onClick={() => {
                      setDisplay(h.result);
                      setFresh(false);
                      setShowHistory(false);
                    }}
                  >
                    <span className="text-[11px] text-mute">{h.expr} =</span>
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
            <History size={14} /> History
          </button>
          <button
            className="flex items-center gap-1.5 rounded-full px-3 py-1.5 text-[11px] font-semibold text-mute transition hover:bg-surface2"
            onClick={async () => {
              if (await copyText(display)) toastOk("Copied");
            }}
            aria-label="Copy result"
          >
            <Copy size={14} /> Copy
          </button>
        </div>
        {KEYS.map((key) => (
          <button
            key={key.k}
            onClick={key.fn}
            className={cx(
              "key flex h-[58px] items-center justify-center rounded-[19px] text-[21px] font-medium shadow-key transition active:scale-95",
              key.cls === "op" || key.cls === "eq"
                ? "bg-brand text-white shadow-floating"
                : key.cls === "func"
                  ? "bg-surface2 text-mute"
                  : "bg-surface text-ink",
              key.cls === "zero" && "col-span-2",
              key.cls === "eq" && "bg-brandink"
            )}
            aria-label={key.k}
          >
            {key.k}
          </button>
        ))}
        {onUse && (
          <div className="col-span-4 pt-1">
            <Button
              variant="brand"
              size="lg"
              className="w-full"
              onClick={() => onUse(parseFloat(display.replace(/,/g, "")) || 0)}
            >
              Use this value
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}

/** Calculator as an in-frame sheet (opened from amount inputs via useUI.openCalculator). */
export function CalculatorSheet() {
  const pop = useUI((s) => s.pop);
  const handler = useUI((s) => s.calculatorPickHandler);
  const closeAll = useUI((s) => s.closeAll);
  return (
    <div className="absolute inset-0 flex flex-col bg-canvas" style={{ zIndex: 70 }}>
      <header className="flex items-center justify-between border-b border-line px-3 py-3">
        <h2 className="px-2 text-[17px] font-bold tracking-tight text-ink">Calculator</h2>
        <button
          onClick={() => {
            if (handler) closeAll();
            else pop();
          }}
          aria-label="Close calculator"
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
                  closeAll();
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
