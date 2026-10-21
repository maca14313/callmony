/* callmony — app shell (React Native edition)
   Contacts & Recents from the phone, money ledger, calculator, five languages.
   100% offline: all app data lives on the device. */

import React, { Component, useEffect, useRef, useState } from "react";
import {
  Animated,
  AppState,
  Easing,
  PermissionsAndroid,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { SafeAreaProvider, useSafeAreaInsets } from "react-native-safe-area-context";
import { StatusBar } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "./src/lib/store";
import { useUI } from "./src/lib/ui-store";
import { useT } from "./src/i18n";
import { dueStatus } from "./src/lib/calc";
import { F, useThemeColors } from "./src/theme";
import { Toaster, ConfirmHost, IconBtn, Pill } from "./src/components/kit";
import { BottomNav } from "./src/components/BottomNav";
import { KeypadSheet } from "./src/components/KeypadSheet";
import { CalculatorSheet } from "./src/components/Calculator";
import { LanguageSheet } from "./src/components/LanguageSheet";
import { BottomSheet } from "./src/components/Sheet";
import { ContactsScreen } from "./src/screens/ContactsScreen";
import { RecentsScreen } from "./src/screens/RecentsScreen";
import { GeneralScreen } from "./src/screens/GeneralScreen";
import { RemindersScreen } from "./src/screens/RemindersScreen";
import { TrashScreen } from "./src/screens/TrashScreen";
import { ContactDetailOverlay } from "./src/overlays/ContactDetail";
import { EntryEditOverlay } from "./src/overlays/EntryEdit";
import { ListDetailOverlay, ListEditOverlay } from "./src/overlays/ListOverlays";
import { NoteEditOverlay, ReminderEditOverlay, BankEditOverlay } from "./src/overlays/Editors";
import { SettingsOverlay, LockScreen } from "./src/overlays/SettingsOverlay";
import { SearchOverlay, SpeedDialOverlay, MergeOverlay, AboutOverlay, AlarmDialog } from "./src/overlays/MiscOverlays";
import { fetchAllContacts, requestContactsPermission, checkContactsPermission } from "./src/native/deviceContacts";
import { setupCallKeep } from "./src/native/callkeep";

const App: React.FC = () => {
  const hydrated = useApp((s) => s.hydrated);

  /* BOOT: hydrate the persisted store. A 5s bail-out guarantees the app can
     never get stuck on the splash screen, even if storage misbehaves. */
  useEffect(() => {
    let done = false;
    const finish = () => {
      if (!done) {
        done = true;
        useApp.setState({ hydrated: true });
      }
    };
    const bail = setTimeout(finish, 5000);
    useApp
      .getState()
      .hydrate()
      .catch(() => undefined)
      .finally(finish);
    return () => clearTimeout(bail);
  }, []);

  return (
    <SafeAreaProvider>
      <AppErrorBoundary>
        {hydrated ? <Shell /> : <Splash />}
      </AppErrorBoundary>
    </SafeAreaProvider>
  );
};

/* ---------------- error boundary ---------------- */

class AppErrorBoundary extends Component<
  { children: React.ReactNode },
  { error: string | null }
> {
  state: { error: string | null } = { error: null };

  static getDerivedStateFromError(err: unknown): { error: string | null } {
    const message = err instanceof Error ? err.message : String(err);
    return { error: message || "Unknown error" };
  }

  render() {
    if (this.state.error) {
      return (
        <BootError
          message={this.state.error}
          onContinue={() => this.setState({ error: null })}
        />
      );
    }
    return this.props.children;
  }
}

/** Readable crash card — replaces the old silent splash freeze. */
const BootError: React.FC<{ message: string; onContinue: () => void }> = ({ message, onContinue }) => (
  <View style={shStyles.splash}>
    <View style={[shStyles.splashLogo, { backgroundColor: "#c0392b" }]}>
      <Icon name="alert-circle-outline" size={42} color="#fff" />
    </View>
    <Text style={{ fontSize: 22, fontWeight: "800", marginTop: 14 }}>Something went wrong</Text>
    <Text style={{ fontSize: 13, opacity: 0.7, marginTop: 6, textAlign: "center", paddingHorizontal: 30 }}>
      The app hit an unexpected error. Your data is safe on this device.
    </Text>
    <Text style={{ fontSize: 12, opacity: 0.5, marginTop: 10, paddingHorizontal: 30 }} numberOfLines={4}>
      {message}
    </Text>
    <Pressable
      onPress={onContinue}
      style={{ marginTop: 18, backgroundColor: "#16a085", paddingHorizontal: 26, paddingVertical: 12, borderRadius: 16 }}
    >
      <Text style={{ color: "#fff", fontSize: 15, fontWeight: "700" }}>Continue</Text>
    </Pressable>
  </View>
);

/* ---------------- splash ---------------- */

const Splash: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const scale = useRef(new Animated.Value(0.6)).current;
  useEffect(() => {
    Animated.timing(scale, {
      toValue: 1,
      duration: 420,
      easing: Easing.out(Easing.back(1.6)),
      useNativeDriver: true,
    }).start();
  }, [scale]);
  return (
    <View style={[shStyles.splash, { backgroundColor: C.canvas }]}>
      <Animated.View style={[shStyles.splashLogo, { backgroundColor: C.brand, transform: [{ scale }] }]}>
        <Icon name="phone-in-talk" size={42} color={C.onBrand} />
      </Animated.View>
      <Text style={{ fontSize: 30, fontWeight: "800", color: C.ink, marginTop: 14 }}>callmony</Text>
      <Text style={{ fontSize: F.small, color: C.sub, marginTop: 2 }}>Talk. Track. Settle up.</Text>
    </View>
  );
};

/* ---------------- shell ---------------- */

const Shell: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const insets = useSafeAreaInsets();
  const tab = useUI((s) => s.tab);
  const setTab = useUI((s) => s.setTab);
  const overlayStack = useUI((s) => s.overlayStack);
  const moreOpen = useUI((s) => s.moreOpen);
  const setMoreOpen = useUI((s) => s.setMoreOpen);
  const keypadOpen = useUI((s) => s.keypadOpen);
  const locked = useUI((s) => s.locked);
  const open = useUI((s) => s.open);
  const reminders = useApp((s) => s.reminders);
  const settings = useApp((s) => s.settings);
  const setDeviceContacts = useApp((s) => s.setDeviceContacts);
  const [langSheet, setLangSheet] = useState(false);

  /* Dialer-style boot: on first launch ask for contacts + call log + phone
     (already-granted ones resolve silently), then load the address book and
     the system call log IMMEDIATELY — the app opens with Contacts and
     Recents ready, like any built-in dialer. Both refresh when the user
     returns to the app (e.g. after a call or contact edit). */
  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      const status = await checkContactsPermission();
      if (status === "authorized") {
        const list = await fetchAllContacts().catch(() => []);
        if (!cancelled && list.length) setDeviceContacts(list);
      } else if (status === "undetermined") {
        const asked = await requestContactsPermission();
        if (asked === "authorized") {
          const list = await fetchAllContacts().catch(() => []);
          if (!cancelled && list.length) setDeviceContacts(list);
        }
      }
      useApp.getState().loadSystemCalls();
    };
    const boot = async () => {
      if (Platform.OS === "android") {
        try {
          await PermissionsAndroid.requestMultiple([
            PermissionsAndroid.PERMISSIONS.READ_CONTACTS,
            PermissionsAndroid.PERMISSIONS.READ_CALL_LOG,
            PermissionsAndroid.PERMISSIONS.CALL_PHONE,
          ]);
        } catch {
          /* user can still grant later from the per-tab permission cards */
        }
      }
      load();
    };
    boot();
    const sub = AppState.addEventListener("change", (state) => {
      if (state === "active") load();
    });
    return () => {
      cancelled = true;
      sub.remove();
    };
  }, [setDeviceContacts]);

  /* iOS CallKit registration (calls register with the native calling system) */
  useEffect(() => {
    setupCallKeep();
  }, []);

  /* lock on first load when a PIN is set */
  useEffect(() => {
    if (settings.pinHash) useUI.getState().setLocked(true);
  }, [settings.pinHash]);

  /* alarm watcher — every 15s: due reminders + lead-time pre-alerts */
  const firedLeadsRef = useRef<Set<string>>(new Set());
  useEffect(() => {
    const tick = () => {
      const now2 = Date.now();
      reminders.forEach((r) => {
        if (r.deletedAt || r.done) return;
        const effective = Math.max(r.dueAt, r.snoozedUntil ?? 0);
        if (effective <= now2) {
          useUI.getState().pushAlarm(r);
          return;
        }
        // lead-time pre-alerts (e.g. “5 min before”)
        (r.leadMinutes ?? []).forEach((lead) => {
          const key = `${r.id}:lead${lead}`;
          if (r.dueAt - lead * 60000 <= now2 && !firedLeadsRef.current.has(key)) {
            firedLeadsRef.current.add(key);
            useUI.getState().pushAlarm(r);
          }
        });
      });
    };
    tick();
    const timer = setInterval(tick, 15000);
    const sub = AppState.addEventListener("change", (state) => {
      if (state === "active") tick();
    });
    return () => {
      clearInterval(timer);
      sub.remove();
    };
  }, [reminders]);

  /* autolock: activity-based — locks after N minutes of INACTIVITY (any
     touch resets the timer), or immediately when backgrounded with
     autolockMin = 0. Re-checks when the app returns to the foreground. */
  const lastActive = useRef(Date.now());
  useEffect(() => {
    if (!settings.pinHash) return;
    lastActive.current = Date.now();
    const sub = AppState.addEventListener("change", (state) => {
      if (state === "background" && settings.autolockMin === 0) {
        useUI.getState().setLocked(true);
      }
      if (
        state === "active" &&
        settings.autolockMin > 0 &&
        Date.now() - lastActive.current > settings.autolockMin * 60000
      ) {
        useUI.getState().setLocked(true);
      }
    });
    const timer = setInterval(() => {
      if (settings.autolockMin > 0 && Date.now() - lastActive.current > settings.autolockMin * 60000) {
        useUI.getState().setLocked(true);
      }
    }, 10000);
    return () => {
      sub.remove();
      clearInterval(timer);
    };
  }, [settings.pinHash, settings.autolockMin]);

  const isSecondary = tab === "reminders" || tab === "trash";
  const overdueCount = reminders.filter((r) => !r.deletedAt && !r.done && dueStatus(r.snoozedUntil ?? r.dueAt) === "overdue").length;

  const headerTitle =
    tab === "contacts" ? t("nav.contacts") : tab === "recents" ? t("nav.recents") : tab === "general" ? t("hdr.general") : tab === "reminders" ? t("tab.reminders") : t("hdr.trash");

  return (
    <View
      style={[shStyles.root, { backgroundColor: C.canvas }]}
      /* any touch anywhere keeps the autolock timer alive (capture, never consumed) */
      onStartShouldSetResponderCapture={() => {
        lastActive.current = Date.now();
        return false;
      }}
    >
      <StatusBar barStyle={C.mode === "dark" ? "light-content" : "dark-content"} backgroundColor={C.canvas} />

      {/* header */}
      <View style={{ paddingTop: Math.max(insets.top, 8), backgroundColor: C.headerBg, paddingHorizontal: 14, paddingBottom: 8, borderBottomWidth: 1, borderBottomColor: C.line }}>
        <View style={{ flexDirection: "row", alignItems: "center", gap: 10, minHeight: 52 }}>
          {isSecondary ? (
            <>
              <IconBtn icon="arrow-left" size={40} onPress={() => setTab("contacts")} />
              <View style={{ flex: 1 }}>
                <Text style={{ fontSize: F.h2, fontWeight: "800", color: C.ink }}>{headerTitle}</Text>
              </View>
            </>
          ) : (
            <>
              <View style={[shStyles.logoTile, { backgroundColor: C.brand }]}>
                <Icon name="phone-in-talk" size={19} color={C.onBrand} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={{ fontSize: F.h2, fontWeight: "800", color: C.ink }}>
                  call<Text style={{ color: C.brand }}>mony</Text>
                </Text>
                <Text style={{ fontSize: F.tiny, color: C.sub }}>{t("hdr.tagline")}</Text>
              </View>
              <IconBtn icon="magnify" size={40} onPress={() => open({ type: "search" })} />
              <IconBtn icon="cog-outline" size={40} onPress={() => open({ type: "settings" })} />
            </>
          )}
        </View>
      </View>

      {/* content — the bottom nav is a flex sibling, it never covers content */}
      <View style={{ flex: 1 }}>
        {tab === "contacts" && <ContactsScreen />}
        {tab === "recents" && <RecentsScreen />}
        {tab === "general" && <GeneralScreen />}
        {tab === "reminders" && <RemindersScreen />}
        {tab === "trash" && <TrashScreen />}
      </View>

      {/* bottom navigation */}
      <BottomNav />

      {/* overlays */}
      {overlayStack.map((o, i) => (
        <OverlayHost key={`${o.type}-${i}`} overlay={o} />
      ))}

      {/* keypad half-modal */}
      {keypadOpen && <KeypadSheet />}

      {/* more sheet */}
      {moreOpen && <MoreSheet onClose={() => setMoreOpen(false)} onLanguage={() => setLangSheet(true)} overdueCount={overdueCount} />}

      {/* language sheet */}
      {langSheet && <LanguageSheet onClose={() => setLangSheet(false)} />}

      {/* alarm dialog */}
      <AlarmDialog />

      {/* lock screen */}
      {locked && <LockScreen />}

      {/* toast + confirm */}
      <Toaster />
      <ConfirmHost />
    </View>
  );
};

/* ---------------- overlay host ---------------- */

const OverlayHost: React.FC<{ overlay: ReturnType<typeof useUI.getState>["overlayStack"][number] }> = ({ overlay }) => {
  switch (overlay.type) {
    case "contact":
      return <ContactDetailOverlay params={overlay} />;
    case "entryEdit":
      return <EntryEditOverlay params={overlay} />;
    case "list":
      return <ListDetailOverlay params={overlay} />;
    case "listEdit":
      return <ListEditOverlay params={overlay} />;
    case "noteEdit":
      return <NoteEditOverlay params={overlay} />;
    case "reminderEdit":
      return <ReminderEditOverlay params={overlay} />;
    case "bankEdit":
      return <BankEditOverlay params={overlay} />;
    case "settings":
      return <SettingsOverlay />;
    case "search":
      return <SearchOverlay />;
    case "calculator":
      return <CalculatorSheet />;
    case "speedDial":
      return <SpeedDialOverlay />;
    case "merge":
      return <MergeOverlay />;
    case "backup":
      return <SettingsOverlay />;
    case "about":
      return <AboutOverlay />;
    default:
      return null;
  }
};

/* ---------------- more sheet ---------------- */

const MoreSheet: React.FC<{ onClose: () => void; onLanguage: () => void; overdueCount: number }> = ({
  onClose,
  onLanguage,
  overdueCount,
}) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const setTab = useUI((s) => s.setTab);
  const open = useUI((s) => s.open);
  const language = useApp((s) => s.settings.language);
  const native = { en: "English", am: "አማርኛ", om: "Afaan Oromoo", ar: "العربية", ti: "ትግርኛ" }[language ?? "en"];

  const tiles: { icon: string; label: string; badge?: number; run: () => void }[] = [
    { icon: "translate", label: native || "English", run: onLanguage },
    { icon: "bell-outline", label: t("tab.reminders"), badge: overdueCount, run: () => { setTab("reminders"); onClose(); } },
    { icon: "magnify", label: t("act.search"), run: () => { open({ type: "search" }); onClose(); } },
    { icon: "swap-horizontal-circle-outline", label: t("spd.mergeDuplicates"), run: () => { open({ type: "merge" }); onClose(); } },
    { icon: "grid", label: t("spd.speedDial"), run: () => { open({ type: "speedDial" }); onClose(); } },
    { icon: "trash-can-outline", label: t("hdr.trash"), run: () => { setTab("trash"); onClose(); } },
    { icon: "cog-outline", label: t("hdr.settings"), run: () => { open({ type: "settings" }); onClose(); } },
    { icon: "information-outline", label: t("st.about"), run: () => { open({ type: "about" }); onClose(); } },
  ];

  return (
    <BottomSheet onClose={onClose}>
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 10, paddingBottom: 10 }}>
        {tiles.map((tile) => (
          <Pressable
            key={tile.label}
            onPress={tile.run}
            android_ripple={{ color: "rgba(128,128,128,0.16)", borderless: true, radius: 52 }}
            style={({ pressed }) => [moreStyles.tile, { backgroundColor: pressed ? C.brandSoft : C.card, borderColor: C.line }]}
          >
            <Icon name={tile.icon} size={24} color={C.brand} />
            <Text style={{ fontSize: F.tiny, color: C.ink, fontWeight: "700", textAlign: "center" }} numberOfLines={1}>
              {tile.label}
            </Text>
            {!!tile.badge && tile.badge > 0 && (
              <View style={[moreStyles.badge, { backgroundColor: C.red }]}>
                <Text style={{ color: "#fff", fontSize: 13, fontWeight: "800" }}>{tile.badge > 9 ? "9+" : tile.badge}</Text>
              </View>
            )}
          </Pressable>
        ))}
      </View>
      <View style={{ alignItems: "center", paddingVertical: 6 }}>
        <Pill label={t("misc.offlineReady")} tone="green" icon="cloud-off-outline" />
      </View>
    </BottomSheet>
  );
};

const shStyles = StyleSheet.create({
  root: { flex: 1 },
  splash: { flex: 1, alignItems: "center", justifyContent: "center" },
  splashLogo: { width: 88, height: 88, borderRadius: 28, alignItems: "center", justifyContent: "center" },
  logoTile: { width: 38, height: 38, borderRadius: 12, alignItems: "center", justifyContent: "center" },
});

const moreStyles = StyleSheet.create({
  tile: {
    width: "30.5%",
    minWidth: 100,
    borderRadius: 18,
    borderWidth: 1,
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    paddingVertical: 14,
    minHeight: 84,
  },
  badge: {
    position: "absolute",
    top: 6,
    right: 8,
    minWidth: 20,
    height: 20,
    borderRadius: 10,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 5,
  },
});

export default App;
