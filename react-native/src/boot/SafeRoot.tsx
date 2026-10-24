/* SafeRoot — the component registered with AppRegistry instead of App.
   Layers of armor around the real app:
     1. PREPARE-FIRST BOOT: a minimal splash renders instantly; bootFails and
        the LAST NATIVE CRASH REPORT (captured by CrashReporter.kt before the
        process died) are read from disk BEFORE the app mounts, so:
          - the user SEES what killed the app last time (Copy report / Reset),
          - safe mode (>= 3 failed boots) engages before any app effect runs.
     2. LAZY + GUARDED EVALUATION: require('./App') happens inside a try/catch
        — a module-eval failure shows the recovery screen, never a fatal.
     3. ERROR BOUNDARY around the real app — no RENDER error can escape to
        RN's root handling.
     4. CRASH FIREWALL (index.js): any uncaught JS error (effects, timers,
        handlers) is caught by the global handler and surfaced as a
        recoverable card. */

import React, { Component, useEffect, useState } from "react";
import {
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import {
  clearCrash,
  getCrashState,
  isBootPhase,
  readBootFails,
  resetBootFails,
  setSafeMode,
  subscribeCrash,
  wipeAllAppData,
} from "./crash-firewall";
import { bootMark, clearCrashReport, readCrashReport } from "../native/diag";

const BRAND = "#16a085";

const Btn: React.FC<{
  icon: string;
  label: string;
  primary?: boolean;
  onPress: () => void;
}> = ({ icon, label, primary, onPress }) => (
  <Pressable
    style={({ pressed }) => [
      rcStyles.btn,
      primary ? { backgroundColor: BRAND, borderColor: BRAND } : null,
      { opacity: pressed ? 0.85 : 1 },
    ]}
    onPress={onPress}
    accessibilityRole="button"
    accessibilityLabel={label}
  >
    <Icon name={icon} size={17} color={primary ? "#fff" : BRAND} />
    <Text style={primary ? rcStyles.btnText : rcStyles.btnTextGhost}>{label}</Text>
  </Pressable>
);

const Body: React.FC<{ message: string; bootFails: number }> = ({ message, bootFails }) => (
  <>
    <Text style={rcStyles.title}>callmony recovered</Text>
    <Text style={rcStyles.body}>
      {bootFails > 1
        ? "The app hit the same problem more than once. Resetting your app data usually fixes this — your contacts and call log live on the phone and are never touched; only callmony's own notes (ledger, reminders…) are cleared."
        : "The app hit an unexpected error but stayed alive. Your data is safe. You can continue where you left off."}
    </Text>
    <Text style={rcStyles.msg} numberOfLines={6}>{message}</Text>
  </>
);

const RecoveryScreen: React.FC<{
  message: string;
  bootFails: number;
  onContinue: () => void;
  onReset: () => void;
}> = ({ message, bootFails, onContinue, onReset }) => (
  <View style={rcStyles.root}>
    <View style={[rcStyles.logo, { backgroundColor: bootFails > 1 ? "#b97d10" : BRAND }]}>
      <Icon name={bootFails > 1 ? "database-refresh-outline" : "shield-check-outline"} size={40} color="#fff" />
    </View>
    <Body message={message} bootFails={bootFails} />
    <Btn icon="arrow-right" label="Continue" primary onPress={onContinue} />
    <Btn icon="refresh" label="Reset app data" onPress={onReset} />
  </View>
);

/* ---------------- last native crash report ---------------- */

const CrashReportScreen: React.FC<{
  report: string;
  onContinue: () => void;
  onReset: () => void;
}> = ({ report, onContinue, onReset }) => (
  <View style={[rcStyles.root, { justifyContent: "flex-start", paddingTop: 64 }]}>
    <View style={[rcStyles.logo, { backgroundColor: "#c0392b" }]}>
      <Icon name="bug-outline" size={40} color="#fff" />
    </View>
    <Text style={rcStyles.title}>callmony closed unexpectedly</Text>
    <Text style={rcStyles.body}>
      The previous run ended with a crash. The report below says exactly what happened — send it to the developers to get it fixed. Your data is safe.
    </Text>
    <ScrollView style={rcStyles.reportBox} contentContainerStyle={{ padding: 10 }}>
      <Text style={rcStyles.reportText} selectable>
        {report.trim() || "(no details recorded)"}
      </Text>
    </ScrollView>
    <Btn
      icon="content-copy"
      label="Copy report"
      onPress={() => {
        try {
          const { Clipboard } = require("react-native").NativeModules as {
            Clipboard: { setString: (s: string) => void };
          };
          Clipboard.setString(report.trim());
        } catch {
          /* selectable text above still allows manual copy */
        }
      }}
    />
    <Btn icon="arrow-right" label="Continue" primary onPress={onContinue} />
    <Btn icon="refresh" label="Reset app data" onPress={onReset} />
  </View>
);

/* ---------------- minimal first-paint splash ---------------- */

const MiniSplash: React.FC = () => (
  <View style={rcStyles.root}>
    <View style={[rcStyles.logo, { backgroundColor: BRAND }]}>
      <Icon name="phone-in-talk" size={40} color="#fff" />
    </View>
    <Text style={rcStyles.title}>callmony</Text>
  </View>
);

/* ---------------- error boundary (nothing escapes to RN's root) ---------------- */

class SafeBoundary extends Component<
  { children: React.ReactNode; onError: (msg: string) => void },
  { error: string | null }
> {
  state = { error: null as string | null };
  static getDerivedStateFromError(err: unknown): { error: string | null } {
    const message = err instanceof Error ? err.message : String(err);
    return { error: message || "Unknown render error" };
  }
  componentDidCatch(err: unknown): void {
    this.props.onError(err instanceof Error ? err.message : String(err));
  }
  render(): React.ReactNode {
    if (this.state.error) return null; /* the recovery card is shown by SafeRoot */
    return this.props.children;
  }
}

/* ---------------- SafeRoot ---------------- */

type Phase =
  | { kind: "prepare" }
  | { kind: "report"; report: string; bootFails: number }
  | { kind: "run"; module: { default: React.ComponentType } | null; evalError: string | null; bootFails: number; safeMode: boolean };

const SafeRoot: React.FC = () => {
  const [phase, setPhase] = useState<Phase>({ kind: "prepare" });
  const [crashMessage, setCrashMessage] = useState<string | null>(null);
  const [banner, setBanner] = useState<string | null>(null);
  const [mountKey, setMountKey] = useState(0);

  /* live crash subscription — recovery card while young, banner later */
  useEffect(() => subscribeCrash(() => {
    const s = getCrashState();
    const msg = s.message || "Unknown error";
    if (isBootPhase()) setCrashMessage(msg);
    else setBanner(msg);
  }), []);

  useEffect(() => {
    if (crashMessage === null) return;
    const unsub = subscribeCrash(() => {
      if (!getCrashState().message) setCrashMessage(null);
    });
    return unsub;
  }, [crashMessage]);

  /* PREPARE: read bootFails + the last native crash report, evaluate the app
     module — all guarded — then run. This runs ONCE (and after Reset). */
  useEffect(() => {
    if (phase.kind !== "prepare") return;
    let cancelled = false;
    (async () => {
      bootMark("safroot-prepare");
      const [fails, report] = await Promise.all([
        readBootFails().catch(() => 0),
        readCrashReport(),
      ]);
      if (cancelled) return;

      if (report && report.trim()) {
        /* show the last native crash BEFORE anything else can die */
        bootMark("native-crash-report-shown");
        setPhase({ kind: "report", report, bootFails: fails });
        return;
      }

      const safeMode = fails >= 3;
      setSafeMode(safeMode);
      bootMark(safeMode ? "safe-mode" : "normal-mode");

      /* guarded app module evaluation */
      try {
        // eslint-disable-next-line @typescript-eslint/no-var-requires
        const mod = require("../../App") as { default: React.ComponentType };
        bootMark("app-required");
        if (!cancelled) setPhase({ kind: "run", module: mod, evalError: null, bootFails: fails, safeMode });
      } catch (err) {
        bootMark("app-require-failed");
        if (!cancelled)
          setPhase({
            kind: "run",
            module: null,
            evalError: err instanceof Error ? err.message : String(err),
            bootFails: fails,
            safeMode,
          });
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [phase.kind === "prepare", mountKey]);

  /* a boot that survives 35 s without any crash counts as healthy →
     consecutive-failure counter resets, safe mode lifts next open */
  useEffect(() => {
    if (phase.kind !== "run" || phase.module === null) return;
    const id = setTimeout(() => {
      bootMark("boot-healthy");
      void resetBootFails().catch(() => undefined);
    }, 35000);
    return () => clearTimeout(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [phase.kind]);

  const handleContinue = (): void => {
    clearCrash();
    setCrashMessage(null);
    setBanner(null);
    void resetBootFails().catch(() => undefined);
  };

  const handleReset = (): void => {
    void wipeAllAppData()
      .then(() => resetBootFails())
      .then(() => clearCrashReport())
      .catch(() => undefined)
      .finally(() => {
        clearCrash();
        setCrashMessage(null);
        setBanner(null);
        /* full fresh boot: re-prepare (re-reads diagnostics, re-requires app) */
        setPhase({ kind: "prepare" });
        setMountKey((k) => k + 1);
      });
  };

  /* recovery card wins while a live crash is young */
  if (crashMessage) {
    return (
      <RecoveryScreen
        message={crashMessage}
        bootFails={phase.kind === "run" || phase.kind === "report" ? phase.bootFails : 0}
        onContinue={handleContinue}
        onReset={handleReset}
      />
    );
  }

  if (phase.kind === "prepare") return <MiniSplash />;

  if (phase.kind === "report") {
    return (
      <CrashReportScreen
        report={phase.report}
        onContinue={() => {
          void clearCrashReport();
          setPhase({ kind: "prepare" });
          setMountKey((k) => k + 1);
        }}
        onReset={handleReset}
      />
    );
  }

  if (phase.module === null) {
    return (
      <RecoveryScreen
        message={`Startup: ${phase.evalError ?? "The app module could not be loaded."}`}
        bootFails={phase.bootFails}
        onContinue={() => {
          setPhase({ kind: "prepare" });
          setMountKey((k) => k + 1);
        }}
        onReset={handleReset}
      />
    );
  }

  const RealApp = phase.module.default;
  return (
    <View style={{ flex: 1, backgroundColor: "#f7faf9" }}>
      {banner && (
        <View style={rcStyles.banner}>
          <Icon name="shield-alert-outline" size={15} color="#b97d10" />
          <Text style={rcStyles.bannerText} numberOfLines={2}>{"Recovered from an error — your data is safe"}</Text>
          <Pressable hitSlop={10} onPress={() => setBanner(null)} accessibilityLabel="Dismiss">
            <Icon name="close" size={16} color="#5c6f6c" />
          </Pressable>
        </View>
      )}
      {phase.safeMode && (
        <View style={rcStyles.safeBanner}>
          <Icon name="shield-half-full" size={15} color="#2f6a58" />
          <Text style={rcStyles.safeText} numberOfLines={2}>
            {"Safe mode — permission prompts and background sync are paused. Opens normally after a stable session."}
          </Text>
        </View>
      )}
      <SafeBoundary
        key={mountKey}
        onError={() => setCrashMessage("Render error (caught by boundary)")}
      >
        <RealApp key={mountKey} />
      </SafeBoundary>
    </View>
  );
};

const rcStyles = StyleSheet.create({
  root: { flex: 1, alignItems: "center", justifyContent: "center", backgroundColor: "#f7faf9", paddingHorizontal: 28 },
  logo: { width: 84, height: 84, borderRadius: 26, alignItems: "center", justifyContent: "center" },
  title: { fontSize: 24, fontWeight: "800", color: "#172b2a", marginTop: 16 },
  body: { fontSize: 13, color: "#5c6f6c", textAlign: "center", marginTop: 8, lineHeight: 19 },
  msg: { fontSize: 11, color: "#8ba09c", textAlign: "center", marginTop: 12, maxWidth: 320 },
  btn: {
    flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8,
    paddingHorizontal: 28, paddingVertical: 13, borderRadius: 16, marginTop: 14,
    borderWidth: 1.5, borderColor: BRAND, minWidth: 240,
  },
  btnText: { color: "#fff", fontSize: 15, fontWeight: "700" },
  btnTextGhost: { color: BRAND, fontSize: 14, fontWeight: "700" },
  reportBox: {
    alignSelf: "stretch", maxHeight: 220, marginTop: 14, borderRadius: 14,
    backgroundColor: "#fff", borderWidth: 1, borderColor: "#e3ecea",
  },
  reportText: { fontSize: 10.5, fontFamily: undefined, color: "#43524f", lineHeight: 15 },
  banner: {
    flexDirection: "row", alignItems: "center", gap: 8, paddingHorizontal: 14, paddingVertical: 8,
    backgroundColor: "#fdf6e3", borderBottomWidth: 1, borderBottomColor: "#f0e2b6",
  },
  safeBanner: {
    flexDirection: "row", alignItems: "center", gap: 8, paddingHorizontal: 14, paddingVertical: 8,
    backgroundColor: "#eef6f2", borderBottomWidth: 1, borderBottomColor: "#d3e6dd",
  },
  bannerText: { flex: 1, fontSize: 12, color: "#7a5b12", fontWeight: "600" },
  safeText: { flex: 1, fontSize: 12, color: "#2f6a58", fontWeight: "600" },
});

export default SafeRoot;
