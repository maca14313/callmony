/* SafeRoot — the component registered with AppRegistry instead of App.
   Two layers of armor around the real app:
     1. LAZY EVALUATION: `require('./App')` happens inside render inside a
        try/catch — if the app's module graph itself fails to evaluate
        (a hard fatal in release), we show the recovery screen instead of
        dying at bundle load.
     2. CRASH FIREWALL: any uncaught JS error anywhere (effects, timers,
        handlers — places React boundaries can't reach) is caught by the
        global handler installed in index.js and surfaced here as a
        recoverable card. "Continue" retries, "Reset app data" wipes
        storage + caches (fixes corrupted persisted state, e.g. after an
        app update) and remounts the whole tree fresh. */

import React, { useEffect, useState } from "react";
import {
  Pressable,
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
  subscribeCrash,
  wipeAllAppData,
} from "./crash-firewall";

const BRAND = "#16a085";

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
    <Text style={rcStyles.title}>callmony recovered</Text>
    <Text style={rcStyles.body}>
      {bootFails > 1
        ? "The app hit the same problem more than once. Resetting your app data usually fixes this — your contacts and call log live on the phone and are never touched; only callmony's own notes (ledger, reminders…) are cleared."
        : "The app hit an unexpected error but stayed alive. Your data is safe. You can continue where you left off."}
    </Text>
    <Text style={rcStyles.msg} numberOfLines={6}>{message}</Text>
    <Pressable style={({ pressed }) => [rcStyles.btn, { opacity: pressed ? 0.85 : 1 }]} onPress={onContinue}>
      <Icon name="arrow-right" size={18} color="#fff" />
      <Text style={rcStyles.btnText}>Continue</Text>
    </Pressable>
    <Pressable style={({ pressed }) => [rcStyles.btnGhost, { opacity: pressed ? 0.7 : 1 }]} onPress={onReset}>
      <Icon name="refresh" size={18} color={BRAND} />
      <Text style={[rcStyles.btnTextGhost]}>Reset app data</Text>
    </Pressable>
  </View>
);

const SafeRoot: React.FC = () => {
  const [crashMessage, setCrashMessage] = useState<string | null>(null);
  const [banner, setBanner] = useState<string | null>(null);
  const [bootFails, setBootFails] = useState(0);
  const [mountKey, setMountKey] = useState(0);
  const [appModule, setAppModule] = useState<{ default: React.ComponentType } | null | undefined>(undefined);

  useEffect(() => subscribeCrash(() => {
    const s = getCrashState();
    const msg = s.message || "Unknown error";
    if (isBootPhase()) {
      /* young app = a real boot problem → full recovery card */
      setCrashMessage(msg);
    } else {
      /* mid-session hiccup → dismissible banner, app stays usable */
      setBanner(msg);
    }
  }), []);

  useEffect(() => { void readBootFails().then(setBootFails).catch(() => undefined); }, []);

  /* Clearing the crash should also dismiss the card */
  useEffect(() => {
    if (crashMessage === null) return;
    const unsub = subscribeCrash(() => {
      if (!getCrashState().message) setCrashMessage(null);
    });
    return unsub;
  }, [crashMessage]);

  const handleContinue = (): void => {
    clearCrash();
    setCrashMessage(null);
    setBanner(null);
    void resetBootFails().catch(() => undefined);
    setBootFails(0);
  };

  const handleReset = (): void => {
    void wipeAllAppData()
      .then(() => resetBootFails())
      .catch(() => undefined)
      .finally(() => {
        setCrashMessage(null);
        setBanner(null);
        setBootFails(0);
        setMountKey((k) => k + 1); /* full fresh remount of the real app */
      });
  };

  /* recovery card wins when a non-render crash was caught */
  if (crashMessage) {
    return (
      <RecoveryScreen
        message={crashMessage}
        bootFails={bootFails}
        onContinue={handleContinue}
        onReset={handleReset}
      />
    );
  }

  /* lazy + guarded module evaluation of the real app */
  let mod = appModule;
  if (mod === undefined) {
    try {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      mod = require("../../App") as { default: React.ComponentType };
      setAppModule(mod);
    } catch (err) {
      return (
        <RecoveryScreen
          message={`Startup: ${err instanceof Error ? err.message : String(err)}`}
          bootFails={bootFails}
          onContinue={() => { setAppModule(undefined); setMountKey((k) => k + 1); }}
          onReset={handleReset}
        />
      );
    }
  }
  if (mod === null) {
    return (
      <RecoveryScreen
        message="The app module could not be loaded."
        bootFails={bootFails}
        onContinue={() => setAppModule(undefined)}
        onReset={handleReset}
      />
    );
  }

  const RealApp = mod.default;
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
      <RealApp key={mountKey} />
    </View>
  );
};

const rcStyles = StyleSheet.create({
  root: { flex: 1, alignItems: "center", justifyContent: "center", backgroundColor: "#f7faf9", paddingHorizontal: 28 },
  logo: { width: 84, height: 84, borderRadius: 26, alignItems: "center", justifyContent: "center" },
  title: { fontSize: 24, fontWeight: "800", color: "#172b2a", marginTop: 16 },
  body: { fontSize: 13, color: "#5c6f6c", textAlign: "center", marginTop: 8, lineHeight: 19 },
  msg: { fontSize: 11, color: "#8ba09c", textAlign: "center", marginTop: 12, maxWidth: 320 },
  btn: { flexDirection: "row", alignItems: "center", gap: 8, backgroundColor: BRAND, paddingHorizontal: 28, paddingVertical: 13, borderRadius: 16, marginTop: 22 },
  btnText: { color: "#fff", fontSize: 15, fontWeight: "700" },
  btnGhost: { flexDirection: "row", alignItems: "center", gap: 8, paddingHorizontal: 24, paddingVertical: 11, borderRadius: 16, marginTop: 10, borderWidth: 1.5, borderColor: BRAND },
  btnTextGhost: { color: BRAND, fontSize: 14, fontWeight: "700" },
  banner: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingHorizontal: 14,
    paddingVertical: 8,
    backgroundColor: "#fdf6e3",
    borderBottomWidth: 1,
    borderBottomColor: "#f0e2b6",
  },
  bannerText: { flex: 1, fontSize: 12, color: "#7a5b12", fontWeight: "600" },
});

export default SafeRoot;
