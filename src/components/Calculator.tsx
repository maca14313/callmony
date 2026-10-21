/* Calculator — full expression in the main display, backspace with
   hold-to-repeat, memory keys, % , ± , history (last 30) and the
   "use value" flow for amount fields. */

import React, { useRef, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useUI } from "../lib/ui-store";
import { useT } from "../i18n";
import { copyText, vibrate } from "../lib/utils";
import { F, useThemeColors } from "../theme";
import { confirm, toast } from "./kit";
import { Sheet } from "./Sheet";

/* -------- safe expression evaluator (no eval, shunting-yard) -------- */

const PRECEDENCE: Record<string, number> = { "+": 1, "-": 1, "*": 2, "/": 2 };

const apply = (op: string, a: number, b: number): number => {
  switch (op) {
    case "+": return a + b;
    case "-": return a - b;
    case "*": return a * b;
    case "/": return b === 0 ? NaN : a / b;
    default: return b;
  }
};

export const evalExpression = (src: string): number | null => {
  const tokens = src.replace(/×/g, "*").replace(/÷/g, "/").replace(/−/g, "-").match(/(\d+\.?\d*|\.\d+|[+\-*/])/g);
  if (!tokens || !tokens.length) return null;
  const out: string[] = [];
  const ops: string[] = [];
  for (const tk of tokens) {
    if (/^[+\-*/]$/.test(tk)) {
      while (ops.length && PRECEDENCE[ops[ops.length - 1]] >= PRECEDENCE[tk]) out.push(ops.pop()!);
      ops.push(tk);
    } else {
      out.push(tk);
    }
  }
  while (ops.length) out.push(ops.pop()!);
  const stack: number[] = [];
  for (const tk of out) {
    if (/^[+\-*/]$/.test(tk)) {
      const b = stack.pop();
      const a = stack.pop();
      if (a === undefined || b === undefined) return null;
      stack.push(apply(tk, a, b));
    } else {
      const n = Number(tk);
      if (isNaN(n)) return null;
      stack.push(n);
    }
  }
  if (stack.length !== 1) return null;
  const res = Math.round((stack[0] + Number.EPSILON) * 1e10) / 1e10;
  return isNaN(res) || !isFinite(res) ? null : res;
};

const fmtResult = (n: number): string => {
  const s = Math.abs(n) >= 1e12 ? n.toExponential(6) : String(Math.round((n + Number.EPSILON) * 1e10) / 1e10);
  return s.length > 16 ? Number(n.toPrecision(12)).toString() : s;
};

interface HistItem {
  expr: string;
  result: string;
}

export const CalculatorSheet: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const pop = useUI((s) => s.pop);
  const pickHandler = useUI((s) => s.calculatorPickHandler);
  const clearPickHandler = useUI((s) => s.clearCalculatorHandler);

  const [expr, setExpr] = useState("");
  const [result, setResult] = useState<string | null>(null);
  const [justEvaluated, setJustEvaluated] = useState(false);
  const [memory, setMemory] = useState<number | null>(null);
  const [history, setHistory] = useState<HistItem[]>([]);
  const [showHistory, setShowHistory] = useState(false);
  const repeatTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const repeatInterval = useRef<ReturnType<typeof setInterval> | null>(null);

  const currentResult = result ?? (expr ? (() => {
    const v = evalExpression(expr);
    return v === null ? null : fmtResult(v);
  })() : null);
  const displayMain = justEvaluated && result !== null ? result : expr || "0";
  const displaySub = justEvaluated && result !== null ? expr : currentResult;

  const stopRepeat = () => {
    if (repeatTimer.current) clearTimeout(repeatTimer.current);
    if (repeatInterval.current) clearInterval(repeatInterval.current);
    repeatTimer.current = null;
    repeatInterval.current = null;
  };

  const backspace = () => {
    setJustEvaluated(false);
    setResult(null);
    setExpr((e) => e.slice(0, -1));
  };

  const startBackspaceRepeat = () => {
    stopRepeat();
    repeatTimer.current = setTimeout(() => {
      repeatInterval.current = setInterval(backspace, 110);
    }, 450);
  };

  const pressDigit = (d: string) => {
    vibrate(10);
    if (justEvaluated) {
      setExpr(d);
      setResult(null);
      setJustEvaluated(false);
      return;
    }
    setExpr((e) => {
      const lastNum = e.match(/(\d+\.?\d*|\.\d*)$/)?.[0] ?? "";
      if (d === "." && lastNum.includes(".")) return e;
      if (d !== "." && lastNum.replace(".", "").length >= 12) return e;
      return e + d;
    });
  };

  const pressOp = (op: "+" | "−" | "×" | "÷") => {
    vibrate(10);
    if (justEvaluated && result !== null) {
      setExpr(result);
      setResult(null);
      setJustEvaluated(false);
    }
    setExpr((e) => {
      if (!e) return op === "−" ? "−" : e;
      if (/[+\-−×÷]$/.test(e)) return e.slice(0, -1) + op;
      return e + op;
    });
  };

  const pressPercent = () => {
    vibrate(10);
    setExpr((e) => {
      if (!e || /[+\-−×÷]$/.test(e)) return e;
      const v = evalExpression(e);
      if (v === null) return e;
      return fmtResult(v / 100);
    });
  };

  const pressSign = () => {
    vibrate(10);
    setExpr((e) => {
      const m = e.match(/(\d+\.?\d*|\.\d*)$/);
      if (!m) return e;
      const start = m.index ?? 0;
      const before = e.slice(0, start);
      if (before.endsWith("−")) return before.slice(0, -1) + m[0];
      if (before.endsWith("+")) return before.slice(0, -1) + "−" + m[0];
      return before + "−" + m[0];
    });
  };

  const pressEquals = () => {
    vibrate(14);
    if (!expr) return;
    const v = evalExpression(expr);
    if (v === null) {
      setResult("Error");
      setJustEvaluated(true);
      return;
    }
    const res = fmtResult(v);
    setResult(res);
    setJustEvaluated(true);
    setHistory((h) => [{ expr, result: res }, ...h].slice(0, 30));
  };

  const currentValue = (): number | null => {
    if (justEvaluated && result !== null && result !== "Error") return Number(result);
    const v = evalExpression(expr);
    return v;
  };

  const memoryAdd = (sign: 1 | -1) => {
    const v = currentValue();
    if (v === null) return;
    setMemory((m) => Math.round(((m ?? 0) + sign * v) * 1e10) / 1e10);
    vibrate(10);
  };

  const memoryRecall = () => {
    if (memory === null) return;
    if (justEvaluated) {
      setExpr(fmtResult(memory));
      setResult(null);
      setJustEvaluated(false);
      return;
    }
    setExpr((e) => {
      if (!e || /[+\-−×÷]$/.test(e)) return e + fmtResult(memory);
      return fmtResult(memory);
    });
    vibrate(10);
  };

  const useValue = async () => {
    const v = currentValue();
    if (v === null) {
      toast(t("calc.noCalculations"), "error");
      return;
    }
    pickHandler?.(v);
    clearPickHandler();
    pop();
  };

  const copyResult = async () => {
    const v = currentValue();
    if (v === null) return;
    const ok = await copyText(String(fmtResult(v)));
    toast(ok ? t("act.copied") : t("act.couldntCopy"), ok ? "ok" : "error");
  };

  const KEYS: { label: string; kind: "fn" | "op" | "num" | "eq"; onPress: () => void; sub?: boolean }[] = [
    { label: "AC", kind: "fn", onPress: () => { vibrate(10); setExpr(""); setResult(null); setJustEvaluated(false); } },
    { label: "⌫", kind: "fn", onPress: backspace },
    { label: "%", kind: "fn", onPress: pressPercent },
    { label: "÷", kind: "op", onPress: () => pressOp("÷") },
    { label: "7", kind: "num", onPress: () => pressDigit("7") },
    { label: "8", kind: "num", onPress: () => pressDigit("8") },
    { label: "9", kind: "num", onPress: () => pressDigit("9") },
    { label: "×", kind: "op", onPress: () => pressOp("×") },
    { label: "4", kind: "num", onPress: () => pressDigit("4") },
    { label: "5", kind: "num", onPress: () => pressDigit("5") },
    { label: "6", kind: "num", onPress: () => pressDigit("6") },
    { label: "−", kind: "op", onPress: () => pressOp("−") },
    { label: "1", kind: "num", onPress: () => pressDigit("1") },
    { label: "2", kind: "num", onPress: () => pressDigit("2") },
    { label: "3", kind: "num", onPress: () => pressDigit("3") },
    { label: "+", kind: "op", onPress: () => pressOp("+") },
    { label: "±", kind: "fn", onPress: pressSign },
    { label: "0", kind: "num", onPress: () => pressDigit("0") },
    { label: ".", kind: "num", onPress: () => pressDigit(".") },
    { label: "=", kind: "eq", onPress: pressEquals },
  ];

  return (
    <Sheet
      title={t("nav.calculator")}
      subtitle={memory !== null ? `M = ${fmtResult(memory)}` : undefined}
      onBack={pop}
      headerRight={
        <View style={{ flexDirection: "row", gap: 4 }}>
          <Pressable hitSlop={8} onPress={() => setShowHistory((v) => !v)} style={{ padding: 6 }}>
            <Icon name="history" size={22} color={showHistory ? C.brand : C.sub} />
          </Pressable>
          <Pressable hitSlop={8} onPress={copyResult} style={{ padding: 6 }}>
            <Icon name="content-copy" size={20} color={C.sub} />
          </Pressable>
        </View>
      }
      scroll={false}
    >
      <View style={{ flex: 1, paddingHorizontal: 18 }}>
        {/* display */}
        <View style={[styles.display, { borderColor: C.line, backgroundColor: C.card }]}>
          <Text style={[styles.expr, { color: C.sub }]} numberOfLines={2} adjustsFontSizeToFit minimumFontScale={0.5}>
            {displayMain}
          </Text>
          <Text style={[styles.result, { color: displaySub === "Error" ? C.red : C.brand }]} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.4}>
            {displaySub ?? ""}
          </Text>
        </View>

        {/* memory row */}
        <View style={styles.memRow}>
          <MemBtn label="M+" onPress={() => memoryAdd(1)} C={C} />
          <MemBtn label="M−" onPress={() => memoryAdd(-1)} C={C} />
          <MemBtn label="MR" onPress={memoryRecall} C={C} active={memory !== null} />
          {memory !== null && (
            <MemBtn label={t("calc.clearMemory")} onPress={() => setMemory(null)} C={C} />
          )}
          {pickHandler && (
            <Pressable style={[styles.useBtn, { backgroundColor: C.brand }]} onPress={useValue}>
              <Text style={{ color: C.onBrand, fontWeight: "800", fontSize: F.small }}>{t("calc.useValue")}</Text>
            </Pressable>
          )}
        </View>

        {/* history */}
        {showHistory && (
          <View style={[styles.history, { borderColor: C.line, backgroundColor: C.card }]}>
            <ScrollView fadingEdgeLength={40} style={{ maxHeight: 150 }}>
              {history.length === 0 && (
                <Text style={{ color: C.sub, fontSize: F.small, textAlign: "center", paddingVertical: 14 }}>
                  {t("calc.noCalculations")}
                </Text>
              )}
              {history.map((h, i) => (
                <Pressable
                  key={`${h.expr}-${i}`}
                  onPress={() => { setExpr(h.expr); setResult(null); setJustEvaluated(false); }}
                  android_ripple={{ color: "rgba(128,128,128,0.12)" }}
                  style={{ paddingVertical: 8, paddingHorizontal: 10 }}
                >
                  <Text style={{ color: C.sub, fontSize: F.small }}>{h.expr} =</Text>
                  <Text style={{ color: C.ink, fontSize: F.body, fontWeight: "700" }}>{h.result}</Text>
                </Pressable>
              ))}
            </ScrollView>
          </View>
        )}

        {/* keypad */}
        <View style={{ gap: 10, marginTop: 14, paddingBottom: 20 }}>
          {[0, 1, 2, 3, 4].map((rowIdx) => (
            <View key={rowIdx} style={{ flexDirection: "row", gap: 10 }}>
              {KEYS.slice(rowIdx * 4, rowIdx * 4 + 4).map((k) => (
                <Pressable
                  key={k.label}
                  onPress={k.onPress}
                  onPressIn={k.label === "⌫" ? startBackspaceRepeat : undefined}
                  onPressOut={k.label === "⌫" ? stopRepeat : undefined}
                  android_ripple={{ color: "rgba(128,128,128,0.2)", borderless: true, radius: 44 }}
                  style={({ pressed }) => [
                    styles.key,
                    {
                      backgroundColor:
                        k.kind === "eq" ? C.ink : k.kind === "op" ? C.brandSoft : k.kind === "fn" ? C.chip : C.card,
                      opacity: pressed ? 0.75 : 1,
                      borderColor: C.line,
                    },
                  ]}
                >
                  <Text
                    style={{
                      fontSize: k.kind === "num" ? 26 : 22,
                      fontWeight: k.kind === "eq" || k.kind === "op" ? "800" : "600",
                      color: k.kind === "eq" ? C.onBrand : k.kind === "op" ? C.brand : C.ink,
                    }}
                  >
                    {k.label}
                  </Text>
                </Pressable>
              ))}
            </View>
          ))}
        </View>
      </View>
    </Sheet>
  );
};

const MemBtn: React.FC<{ label: string; onPress: () => void; C: ReturnType<typeof useThemeColors>; active?: boolean }> = ({ label, onPress, C, active }) => (
  <Pressable
    onPress={onPress}
    android_ripple={{ color: "rgba(128,128,128,0.2)", borderless: true }}
    style={[styles.memBtn, { backgroundColor: active ? C.brandSoft : C.chip, borderColor: C.line }]}
  >
    <Text style={{ color: active ? C.brand : C.sub, fontSize: F.small, fontWeight: "700" }}>{label}</Text>
  </Pressable>
);

const styles = StyleSheet.create({
  display: { borderRadius: 20, borderWidth: 1, paddingHorizontal: 18, paddingVertical: 16, minHeight: 110, justifyContent: "flex-end" },
  expr: { fontSize: 30, fontWeight: "700", textAlign: "right", minHeight: 38 },
  result: { fontSize: 24, fontWeight: "800", textAlign: "right", marginTop: 4 },
  memRow: { flexDirection: "row", gap: 8, marginTop: 12, alignItems: "center" },
  memBtn: { borderRadius: 10, borderWidth: 1, paddingHorizontal: 12, paddingVertical: 8, minHeight: 38, justifyContent: "center" },
  useBtn: { borderRadius: 12, paddingHorizontal: 14, paddingVertical: 10, marginLeft: "auto", minHeight: 38, justifyContent: "center" },
  history: { borderRadius: 16, borderWidth: 1, marginTop: 12, maxHeight: 160 },
  key: {
    flex: 1,
    aspectRatio: 1,
    borderRadius: 22,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1,
    maxWidth: 96,
  },
});
