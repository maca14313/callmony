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
import { Toaster, ConfirmHost, IconBtn, Pill, Chip } from "./src/components/kit";
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
import { ContactEditor, ContactCreator } from "./src/overlays/ContactEditor";
import { EntryEditOverlay } from "./src/overlays/EntryEdit";
import { ListDetailOverlay, ListEditOverlay } from "./src/overlays/ListOverlays";
import { NoteEditOverlay, ReminderEditOverlay, BankEditOverlay } from "./src/overlays/Editors";
import { SettingsOverlay, LockScreen } from "./src/overlays/SettingsOverlay";
import { SearchOverlay, SpeedDialOverlay, MergeOverlay, AboutOverlay, AlarmDialog } from "./src/overlays/MiscOverlays";
import { OnboardingSheet } from "./src/overlays/OnboardingSheet";
import { loadCachedContacts, CONTACTS_TTL } from "./src/native/deviceContacts";
import { setupCallKeep } from "./src/native/callkeep";
import { maybeAutoSync, autoSnapshot } from "./src/lib/backup";
import { bootMark, setNavBarColor } from "./src/native/diag";
import { isSafeMode } from "./src/boot/crash-firewall";
import { ensureReminderReadiness, handleNotifeeEvent } from "./src/native/notify";
import notifee from "@notifee/react-native";

const App: React.FC = () => {
  const hydrated = useApp((s) => s.hydrated);

  /* BOOT: hydrate the persisted store. A 5s bail-out guarantees the app can
     never get stuck on the splash screen, even if storage misbehaves.
     v1.6.2 (user request): the app ALWAYS opens on RECENTS — the dialer-style
     home — regardless of which tab was active when it closed. */
  useEffect(() => {
    bootMark("app-mounted");
    let done = false;
    const finish = () => {
      if (!done) {
        done = true;
        useApp.setState({ hydrated: true });
        useUI.setState({ tab: "recents" });
      }
    };
    const bail = setTimeout(finish, 5000);
    useApp
      .getState()
      .hydrate()
      .then(() => bootMark("hydrated"))
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
  const scale = useRef(new Animated.Value(0.86)).current;
  useEffect(() => {
    /* INSTANT-OPEN FEEL (user request): a 140ms settle, not a 420ms
       spring — the splash must read as a flash, never as "loading" */
    Animated.timing(scale, {
      toValue: 1,
      duration: 140,
      easing: Easing.out(Easing.quad),
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
  const onboarded = useApp((s) => s.settings.onboarded);
  const [langSheet, setLangSheet] = useState(false);

  /* ZERO-LAG BOOT: the shell paints instantly; nothing here can block it.
     1. The DISK snapshot of the address book is applied in the background
        (so search / keypad / recents name-resolution work immediately —
        no bridge traffic, no spinner).
     2. The system call log is refreshed in the background from the snapshot
        cache first (instant Recents), then re-read from the OS.
     3. The OS address book is NEVER queried at boot — the Contacts tab
        fetches it itself the first time it is opened (and then only when
        the snapshot is older than CONTACTS_TTL).
     4. Auto email sync never fires on a cold open (it would cover the app
        with a composer); it only runs after returning to the foreground,
        deferred by 12 s so the UI is fully settled first. */
  const lastLogRef = useRef(0);
  useEffect(() => {
    let cancelled = false;
    let syncTimer: ReturnType<typeof setTimeout> | null = null;

    /* SAFE MODE (>= 3 failed boots): every native-touching boot step is
       skipped so the user can always get in; the banner explains why and a
       stable session lifts it automatically on the next open. */
    if (isSafeMode()) {
      bootMark("shell-safe-mode");
      return;
    }

    const boot = async () => {
      bootMark("shell-boot");
      /* 1 — cached contacts snapshot: instant, off the UI thread's way.
         v1.6.10 — a LEGACY (v1) snapshot is applied for the instant paint
         but marked stale: its stored names predate the middle-name fix, so
         contactsLoadedAt=0 makes the deferred TTL refresh re-fetch the book
         (single-flight, background) and rewrite the cache with full names. */
      try {
        const cache = await loadCachedContacts();
        if (!cancelled && cache) {
          useApp.setState({ deviceContacts: cache.list, contactsLoadedAt: cache.legacy ? 0 : cache.savedAt });
        }
      } catch {
        /* cache is optional */
      }
      if (cancelled) return;

      /* 2 — permissions, THEN the call log read (on first run the dialogs
         must resolve before we query — otherwise a transient "denied"
         flash covers Recents; on later boots this resolves instantly).
         v1.6.7 — READ_CONTACTS rides along: with the Contacts TAB hidden
         nothing else ever asked, and a fresh install (or an Android
         permission auto-reset) left the address book empty — search,
         keypad matching and Recents name-resolution all silently died.
         One requestMultiple = one dialog group, asked once per install. */
      if (Platform.OS === "android") {
        try {
          await PermissionsAndroid.requestMultiple([
            PermissionsAndroid.PERMISSIONS.READ_CALL_LOG,
            PermissionsAndroid.PERMISSIONS.CALL_PHONE,
            PermissionsAndroid.PERMISSIONS.READ_CONTACTS,
          ]);
        } catch {
          /* the denied card in Recents handles refusal */
        }
      }
      if (cancelled) return;

      /* 3 — call log: the disk cache was applied BEFORE the first paint (one
         parallel read inside hydrate — Recents never flashes "Loading calls"),
         so this background refresh is INCREMENTAL (v1.6.11): only rows newer
         than the newest cached call cross the bridge, and a refresh that
         changes nothing does not even re-render the list. A full re-read
         happens just once per 10 minutes to reconcile deletions. */
      lastLogRef.current = 0;
      useApp.getState().loadSystemCalls();
      /* v1.6.6 — the Contacts TAB is hidden (user request), so the OS
         address book would never refresh on its own — the search and the
         keypad contact matching must keep working. A DEFERRED (4 s)
         silent re-query keeps names / favorites / balances fresh without
         touching the boot path — the snapshot renders first, always.
         v1.6.9 — TTL-GUARDED (user report: "most laggy version ever"):
         the refresh only runs when the snapshot is actually STALE, and
         refreshDeviceContacts itself is single-flight — no more repeated
         whole-book bridge dumps. */
      setTimeout(() => {
        if (cancelled) return;
        const s = useApp.getState();
        if (Date.now() - s.contactsLoadedAt >= CONTACTS_TTL) {
          s.refreshDeviceContacts().catch(() => undefined);
        }
      }, 4000);
      /* v1.6.6 — SAFETY SNAPSHOT (deferred 10 s, throttled 6 h): the whole
         data set lands in Documents/backups/ on every session — the local
         layer of the no-data-loss guarantee, itself carried by Android
         Auto Backup. */
      setTimeout(() => {
        if (!cancelled) autoSnapshot().catch(() => undefined);
      }, 10000);
      /* v1.6.7 — REMINDER READINESS (deferred 8 s so the OS dialog never
         covers the first paint): channel + one-time notification-permission
         ask for users who already have reminders + SELF-HEAL re-arm of any
         alarm the OS dropped. This is the ported v1.6.4 system — without
         it, an OEM battery-saver kill or a permission reset left reminders
         dead forever ("reminders still don't work"). */
      setTimeout(() => {
        if (!cancelled) runReminderReadiness();
      }, 8000);
      bootMark("boot-done");
    };

    const runReminderReadiness = () => {
      try {
        const s = useApp.getState();
        const autoAsk = s.reminders.length > 0 && !s.settings.notifAsked;
        ensureReminderReadiness(s.reminders, { autoAskPermission: autoAsk })
          .then((res) => {
            if (res.asked) useApp.getState().updateSettings({ notifAsked: true });
          })
          .catch(() => undefined);
      } catch {
        /* never block boot */
      }
    };

    const onForeground = () => {
      /* v1.6.7 — self-heal reminders on foreground return (throttled inside
         ensureReminderReadiness): OEM battery savers kill scheduled alarms
         while the user is away; re-arming here is what keeps them ringing
         day after day. */
      runReminderReadiness();
      /* call log refresh (throttled) + contacts refresh silently (v1.6.6:
         the Contacts tab is hidden but search/keypad matching still need a
         fresh address book) when the snapshot went stale. v1.6.9 — the
         foreground path used to re-serialize the ENTIRE address book over
         the bridge on EVERY app-switch; now it only refreshes when the
         snapshot is older than the 5-minute TTL. */
      if (Date.now() - lastLogRef.current >= 10000) {
        lastLogRef.current = Date.now();
        useApp.getState().loadSystemCalls();
      }
      {
        const s = useApp.getState();
        if (Date.now() - s.contactsLoadedAt >= CONTACTS_TTL) {
          s.refreshDeviceContacts().catch(() => undefined);
        }
      }
      /* auto email sync — only on FOREGROUND RETURN, deferred 12 s */
      if (syncTimer) clearTimeout(syncTimer);
      syncTimer = setTimeout(() => maybeAutoSync().catch(() => undefined), 12000);
    };

    boot();
    const sub = AppState.addEventListener("change", (state) => {
      if (state === "active") onForeground();
    });
    return () => {
      cancelled = true;
      if (syncTimer) clearTimeout(syncTimer);
      sub.remove();
    };
  }, []);

  /* iOS CallKit registration (calls register with the native calling system) */
  useEffect(() => {
    setupCallKeep();
  }, []);

  /* v1.6.2 — reminder NOTIFICATION events while the app is open (foreground):
     tapping "Done" / "Snooze 10m" on the notification itself updates the
     reminder right away — same code path the background handler runs. */
  useEffect(() => {
    return notifee.onForegroundEvent((event) => {
      handleNotifeeEvent(event).catch(() => undefined);
    });
  }, []);

  /* v1.6.1 — system navigation bar follows the RESOLVED theme: dark mode no
     longer leaves a mismatched (light/black) bar under the gesture area.
     values-night covers the cold start; this keeps it synced afterwards. */
  useEffect(() => {
    setNavBarColor(C.canvas, C.mode === "light");
  }, [C.canvas, C.mode]);

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
              <IconBtn icon="arrow-left" size={40} onPress={() => setTab("recents")} />
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
              {/* v1.6.8 — calculator moved to the BOTTOM NAV (user request);
                  the navbar keeps search only */}
              <IconBtn icon="magnify" size={40} onPress={() => open({ type: "search" })} />
              {/* settings cog REMOVED from the header (user request) —
                  Settings lives in the More sheet at the bottom nav */}
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

      {/* first-run onboarding — currency + country code (settings.onboarded) */}
      {!onboarded && <OnboardingSheet />}

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
    /* v1.6.8 — editor + creator live on the overlay stack: reachable from
       anywhere (Recents ＋ tile, call options, keypad), and the store can
       defer the whole-book refresh while they are open (the freeze fix) */
    case "contactEdit":
      return <ContactEditor recordID={overlay.recordID} onClose={() => useUI.getState().pop()} />;
    case "contactNew":
      return (
        <ContactCreator
          prefillPhone={overlay.phone}
          prefillName={overlay.name}
          onClose={() => useUI.getState().pop()}
        />
      );
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
  const theme = useApp((s) => s.settings.theme);
  const updateSettings = useApp((s) => s.updateSettings);
  const [themeSheet, setThemeSheet] = useState(false);
  const native = { en: "English", am: "አማርኛ", om: "Afaan Oromoo", ar: "العربية", ti: "ትግርኛ" }[language ?? "en"];

  const tiles: { icon: string; label: string; badge?: number; run: () => void }[] = [
    { icon: "translate", label: native || "English", run: onLanguage },
    /* v1.6.1 — Theme lives here too (user request), one tap from anywhere:
       opens a compact Light/Dark/Auto chooser, same action as Settings. */
    {
      icon: theme === "dark" ? "weather-night" : theme === "light" ? "weather-sunny" : "theme-light-dark",
      label: `${t("st.theme")} · ${theme === "dark" ? t("st.dark") : theme === "light" ? t("st.light") : t("st.auto")}`,
      run: () => setThemeSheet(true),
    },
    { icon: "bell-outline", label: t("tab.reminders"), badge: overdueCount, run: () => { setTab("reminders"); onClose(); } },
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

      {/* theme chooser — the SAME action the Settings sheet performs */}
      {themeSheet && (
        <BottomSheet title={t("st.theme")} onClose={() => setThemeSheet(false)} noScroll>
          <View style={{ flexDirection: "row", gap: 8, paddingVertical: 8, paddingBottom: 16 }}>
            {([
              { key: "light", label: t("st.light"), icon: "weather-sunny" },
              { key: "dark", label: t("st.dark"), icon: "weather-night" },
              { key: "auto", label: t("st.auto"), icon: "theme-light-dark" },
            ] as const).map((o) => (
              <Chip
                key={o.key}
                label={o.label}
                icon={o.icon}
                active={theme === o.key}
                onPress={() => {
                  updateSettings({ theme: o.key });
                  setThemeSheet(false);
                }}
              />
            ))}
          </View>
        </BottomSheet>
      )}
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
    /* flexBasis instead of minWidth — three tiles ALWAYS fit a row on any
       screen width (minWidth caused out-of-screen overflow on narrow phones) */
    flexBasis: "30%",
    flexGrow: 1,
    maxWidth: 150,
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
