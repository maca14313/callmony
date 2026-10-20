"use client";

import { useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { Toaster, toast } from "sonner";
import {
  Calculator as CalcIcon,
  Clock3,
  Grid3X3,
  Inbox,
  RotateCcw,
  Search,
  Settings,
  Trash2,
  Bell,
  Info,
  Users,
} from "lucide-react";
import { useApp } from "@/lib/store";
import { useUI, type Overlay } from "@/lib/ui-store";
import { dueStatus } from "@/lib/calc";
import { checkVersion, registerSW, useInstallPrompt, useTick } from "@/lib/pwa";
import { useLang, useT } from "@/lib/i18n";
import { ConfirmProvider } from "./kit/Confirm";
import { BottomSheet, Sheet } from "./kit/Sheet";
import { Button, cx } from "./kit/primitives";
import { AlarmDialog } from "./kit/AlarmDialog";
import { CalculatorSheet } from "./kit/Calculator";
import { KeypadSheet } from "./kit/KeypadSheet";
import { ActionPickerHost } from "./kit/ActionPicker";

import { ContactsView, RecentsView } from "./views/contacts-views";
import { GeneralView } from "./views/money-views";
import { RemindersView, TrashView } from "./views/tools-views";

import {
  CallScreen,
  ContactDetail,
  ContactEdit,
  MergeOverlay,
  SpeedDialOverlay,
} from "./overlays/contacts-overlays";
import { EntryEdit, ListDetail, ListEdit } from "./overlays/money-overlays";
import { BankEdit, NoteEdit, ReminderEdit } from "./overlays/tools-overlays";
import {
  AboutOverlay,
  BackupOverlay,
  ImportExportOverlay,
  LockScreen,
  SearchOverlay,
  SettingsView,
  StorageOverlay,
} from "./overlays/data-overlays";

/* ------------------------------------------------------------------ */
/* Splash                                                              */
/* ------------------------------------------------------------------ */

function Splash() {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-4 bg-canvas">
      <motion.div
        initial={{ scale: 0.8, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ type: "spring", damping: 16, stiffness: 220 }}
        className="flex h-16 w-16 items-center justify-center rounded-[22px] bg-brand text-white shadow-floating"
      >
        <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.8 19.8 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.12 4.18 2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.12.96.36 1.9.7 2.8a2 2 0 0 1-.45 2.11L8.1 9.9a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.9.34 1.84.58 2.8.7A2 2 0 0 1 22 16.92z" />
        </svg>
      </motion.div>
      <p className="text-[21px] font-bold tracking-[-1px] text-ink">
        call<span className="text-brand">mony</span>
      </p>
      <p className="text-[11px] text-mute">Loading your data…</p>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Status bar (device chrome)                                          */
/* ------------------------------------------------------------------ */

function StatusBar() {
  const [time, setTime] = useState("");
  useEffect(() => {
    const update = () =>
      setTime(new Date().toLocaleTimeString([], { hour: "numeric", minute: "2-digit" }));
    update();
    const i = setInterval(update, 20000);
    return () => clearInterval(i);
  }, []);
  return (
    <div className="flex shrink-0 items-center justify-between px-7 pt-[max(14px,env(safe-area-inset-top))] pb-1 text-[12px] font-semibold tracking-wide text-ink">
      <span>{time || "9:41"}</span>
      <div className="flex items-center gap-1.5" aria-hidden>
        <svg width="17" height="13" viewBox="0 0 17 13" fill="currentColor">
          <rect x="0" y="7" width="3" height="6" rx="1" />
          <rect x="4.5" y="5" width="3" height="8" rx="1" />
          <rect x="9" y="2.5" width="3" height="10.5" rx="1" />
          <rect x="13.5" y="0" width="3" height="13" rx="1" />
        </svg>
        <svg width="16" height="13" viewBox="0 0 24 20" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round">
          <path d="M2 6C7-0.2 17-0.2 22 6" />
          <path d="M5.5 10C9-0.1 15-0.1 18.5 10" />
          <path d="M9.5 14C11 12.5 13 12.5 14.5 14" />
          <circle cx="12" cy="17" r="1" fill="currentColor" stroke="none" />
        </svg>
        <svg width="23" height="13" viewBox="0 0 25 14" fill="none">
          <rect x="1" y="1" width="20" height="12" rx="3" stroke="currentColor" strokeWidth="1.4" />
          <rect x="3.2" y="3.2" width="15.5" height="7.6" rx="1.5" fill="currentColor" />
          <path d="M23 5V9" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        </svg>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Overlay host — renders every overlay EXCEPT calls (those are        */
/* frame-level so the bottom nav hides while on a call).               */
/* ------------------------------------------------------------------ */

function OverlayHost() {
  const stack = useUI((s) => s.overlayStack);
  const visible = useMemo(() => stack.filter((o) => o.type !== "call"), [stack]);
  return (
    <AnimatePresence>
      {visible.map((o, i) => (
        <OverlayItem key={`${o.type}-${i}`} overlay={o} depth={i} />
      ))}
    </AnimatePresence>
  );
}

/** Call overlays render at the FRAME level — they cover the bottom nav. */
function CallOverlayHost() {
  const stack = useUI((s) => s.overlayStack);
  const calls = useMemo(() => stack.filter((o) => o.type === "call"), [stack]);
  return (
    <AnimatePresence>
      {calls.map((o, i) => (
        <OverlayItem key={`call-${i}`} overlay={o} depth={i} />
      ))}
    </AnimatePresence>
  );
}

function OverlayItem({ overlay, depth }: { overlay: Overlay; depth: number }) {
  const pop = useUI((s) => s.pop);
  const z = 40 + depth;
  const sheetProps = { zIndex: z };

  switch (overlay.type) {
    case "contact":
      return <ContactDetail contactId={overlay.contactId} />;
    case "contactEdit":
      return (
        <ContactEdit
          contactId={overlay.contactId}
          prefillPhone={overlay.prefillPhone}
          prefillName={overlay.prefillName}
        />
      );
    case "call":
      return <CallScreen phone={overlay.phone} contactId={overlay.contactId} direction={overlay.direction} />;
    case "list":
      return <ListDetail listId={overlay.listId} />;
    case "listEdit":
      return <ListEdit listId={overlay.listId} ownerId={overlay.ownerId} kind={overlay.kind} />;
    case "entryEdit":
      return (
        <EntryEdit entryId={overlay.entryId} ownerId={overlay.ownerId} entryType={overlay.entryType} />
      );
    case "noteEdit":
      return <NoteEdit noteId={overlay.noteId} ownerId={overlay.ownerId} linkTo={overlay.linkTo} />;
    case "reminderEdit":
      return (
        <ReminderEdit reminderId={overlay.reminderId} ownerId={overlay.ownerId} prefill={overlay.prefill} />
      );
    case "bankEdit":
      return <BankEdit bankId={overlay.bankId} ownerId={overlay.ownerId} />;
    case "settings":
      return <SettingsView />;
    case "search":
      return <SearchOverlay query={overlay.query} />;
    case "calculator":
      return <CalculatorSheet />;
    case "merge":
      return <MergeOverlay />;
    case "speedDial":
      return <SpeedDialOverlay />;
    case "import":
      return <ImportExportOverlay />;
    case "backup":
      return <BackupOverlay />;
    case "storage":
      return <StorageOverlay />;
    case "about":
      return <AboutOverlay />;
    default:
      void sheetProps;
      void pop;
      return null;
  }
}

/* ------------------------------------------------------------------ */
/* Alarm watcher                                                       */
/* ------------------------------------------------------------------ */

function AlarmWatcher() {
  useTick(15000);
  const reminders = useApp((s) => s.reminders);
  const pushAlarm = useUI((s) => s.pushAlarm);
  const locked = useUI((s) => s.locked);
  const firedRef = useRef<Set<string>>(new Set());

  useEffect(() => {
    if (locked) return;
    const now = Date.now();
    reminders.forEach((r) => {
      if (r.deletedAt || r.done) return;
      const effective = Math.max(r.dueAt, r.snoozedUntil || 0);
      const notYetFired = !r.lastFiredAt || r.lastFiredAt < Math.max(r.dueAt, r.snoozedUntil || r.dueAt);
      const key = `${r.id}:${Math.floor(effective / 60000)}`;
      if (effective <= now && notYetFired && !firedRef.current.has(key)) {
        firedRef.current.add(key);
        pushAlarm(r);
      }
      // lead-time pre-alerts
      (r.leadMinutes || []).forEach((lead) => {
        const leadAt = r.dueAt - lead * 60000;
        const lkey = `${r.id}:lead${lead}`;
        if (leadAt <= now && r.dueAt > now && !firedRef.current.has(lkey) && !(r.firedLeads || []).includes(lead)) {
          firedRef.current.add(lkey);
          pushAlarm(r);
        }
      });
    });
  }, [reminders, pushAlarm, locked]);

  const alarm = useUI((s) => s.alarmQueue[0]);
  return alarm ? <AlarmDialog reminder={alarm} /> : null;
}

/* ------------------------------------------------------------------ */
/* Autolock                                                            */
/* ------------------------------------------------------------------ */

function AutolockWatcher() {
  const settings = useApp((s) => s.settings);
  const setLocked = useUI((s) => s.setLocked);
  const lastActive = useRef(Date.now());

  useEffect(() => {
    if (!settings.pinHash) return;
    const bump = () => (lastActive.current = Date.now());
    const events: (keyof WindowEventMap)[] = ["pointerdown", "keydown", "wheel", "touchstart"];
    events.forEach((e) => window.addEventListener(e, bump, { passive: true }));
    const onHide = () => {
      if (settings.autolockMin === 0) setLocked(true);
    };
    const onShow = () => {
      if (settings.autolockMin === 0) return;
      if (Date.now() - lastActive.current > settings.autolockMin * 60000) setLocked(true);
    };
    document.addEventListener("visibilitychange", () => {
      if (document.hidden) onHide();
      else onShow();
    });
    const interval = setInterval(() => {
      if (settings.autolockMin > 0 && Date.now() - lastActive.current > settings.autolockMin * 60000) {
        setLocked(true);
      }
    }, 10000);
    return () => {
      events.forEach((e) => window.removeEventListener(e, bump));
      clearInterval(interval);
    };
  }, [settings.pinHash, settings.autolockMin, setLocked]);

  // lock on first load if PIN set
  useEffect(() => {
    if (settings.pinHash) setLocked(true);
  }, [settings.pinHash, setLocked]);

  return null;
}

/* ------------------------------------------------------------------ */
/* Bottom navigation — ALWAYS visible (except while on a call).        */
/* Recents · Contacts · Keypad (opens half-modal) · Calculator · More  */
/* ------------------------------------------------------------------ */

function BottomNav() {
  const t = useT();
  const tab = useUI((s) => s.tab);
  const setTab = useUI((s) => s.setTab);
  const setMoreOpen = useUI((s) => s.setMoreOpen);
  const keypadOpen = useUI((s) => s.keypadOpen);
  const setKeypadOpen = useUI((s) => s.setKeypadOpen);
  const closeAll = useUI((s) => s.closeAll);
  const openCalculator = useUI((s) => s.openCalculator);
  const stack = useUI((s) => s.overlayStack);
  const calcActive = stack.length > 0 && stack[stack.length - 1].type === "calculator";
  const overdue = useApp(
    (s) => s.reminders.filter((r) => !r.deletedAt && !r.done && dueStatus(r.dueAt) === "overdue").length
  );

  const items: {
    key: string;
    label: string;
    icon: typeof Clock3;
    active: boolean;
    onClick: () => void;
  }[] = [
    {
      key: "recents",
      label: t("nav.recents"),
      icon: Clock3,
      active: tab === "recents",
      onClick: () => {
        closeAll();
        setTab("recents");
      },
    },
    {
      key: "contacts",
      label: t("nav.contacts"),
      icon: Users,
      active: tab === "contacts",
      onClick: () => {
        closeAll();
        setTab("contacts");
      },
    },
    {
      key: "keypad",
      label: t("nav.keypad"),
      icon: Grid3X3,
      active: keypadOpen,
      onClick: () => {
        closeAll();
        setKeypadOpen(true);
      },
    },
    {
      key: "calculator",
      label: t("nav.calculator"),
      icon: CalcIcon,
      active: calcActive,
      onClick: () => {
        closeAll();
        openCalculator();
      },
    },
  ];

  return (
    <nav
      className="relative z-20 shrink-0 border-t border-line bg-surface/95 px-3 pb-[max(14px,env(safe-area-inset-bottom))] pt-2.5 backdrop-blur-xl"
      aria-label="Main navigation"
    >
      <div className="mx-auto flex max-w-[380px] items-center justify-around">
        {items.map((n) => {
          const Icon = n.icon;
          const isKeypad = n.key === "keypad";
          return (
            <button
              key={n.key}
              onClick={n.onClick}
              aria-label={n.label}
              aria-current={n.active ? "page" : undefined}
              className={cx(
                "nav-item flex min-w-[58px] flex-col items-center gap-1 transition",
                n.active ? "text-brand" : "text-[#a3b0ac] dark:text-mute"
              )}
            >
              {isKeypad && n.active ? (
                <span className="flex h-8 w-8 items-center justify-center rounded-[12px] bg-brand text-white shadow-key">
                  <Icon size={17} fill="currentColor" />
                </span>
              ) : (
                <Icon size={20} strokeWidth={1.7} />
              )}
              <span className={cx("text-[10px]", n.active ? "font-bold" : "font-medium")}>{n.label}</span>
            </button>
          );
        })}
        <button
          onClick={() => setMoreOpen(true)}
          aria-label={t("nav.more")}
          className="nav-item relative flex min-w-[58px] flex-col items-center gap-1 text-[#a3b0ac] transition dark:text-mute"
        >
          <span className="relative">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
              <circle cx="5" cy="12" r="1.7" />
              <circle cx="12" cy="12" r="1.7" />
              <circle cx="19" cy="12" r="1.7" />
            </svg>
            {overdue > 0 && (
              <span className="absolute -right-1.5 -top-1 flex h-3.5 min-w-3.5 items-center justify-center rounded-full bg-neg px-0.5 text-[8px] font-bold text-white">
                {overdue > 9 ? "9+" : overdue}
              </span>
            )}
          </span>
          <span className="text-[10px] font-medium">{t("nav.more")}</span>
        </button>
      </div>
      <div className="mx-auto mt-2.5 h-[4px] w-[105px] rounded-full bg-line2" aria-hidden />
    </nav>
  );
}

/* ------------------------------------------------------------------ */
/* More sheet                                                          */
/* ------------------------------------------------------------------ */

function MoreSheet() {
  const ui = useUI();
  const t = useT();
  const { canInstall, promptInstall, isIOS } = useInstallPrompt();
  const trashCount = useApp(
    (s) =>
      s.contacts.filter((c) => c.deletedAt).length +
      s.entries.filter((e) => e.deletedAt).length +
      s.groups.filter((g) => g.deletedAt).length +
      s.lists.filter((l) => l.deletedAt).length +
      s.notes.filter((n) => n.deletedAt).length +
      s.reminders.filter((r) => r.deletedAt).length +
      s.banks.filter((b) => b.deletedAt).length
  );
  const overdue = useApp(
    (s) => s.reminders.filter((r) => !r.deletedAt && !r.done && dueStatus(r.dueAt) === "overdue").length
  );

  const items: { label: string; icon: typeof Inbox; badge?: number; onClick: () => void }[] = [
    { label: t("hdr.general"), icon: Inbox, onClick: () => ui.setTab("general") },
    { label: t("tab.reminders"), icon: Bell, badge: overdue, onClick: () => ui.setTab("reminders") },
    { label: t("nav.calculator"), icon: CalcIcon, onClick: () => ui.openCalculator() },
    { label: t("act.search"), icon: Search, onClick: () => ui.open({ type: "search" }) },
    { label: t("hdr.trash"), icon: Trash2, badge: trashCount || undefined, onClick: () => ui.setTab("trash") },
    { label: t("hdr.settings"), icon: Settings, onClick: () => ui.open({ type: "settings" }) },
    { label: t("st.about"), icon: Info, onClick: () => ui.open({ type: "about" }) },
  ];

  return (
    <BottomSheet open={ui.moreOpen} onClose={() => ui.setMoreOpen(false)} title={t("nav.more")}>
      <div className="grid grid-cols-3 gap-2.5 pb-2">
        {items.map((it) => {
          const Icon = it.icon;
          return (
            <button
              key={it.label}
              onClick={it.onClick}
              className="relative flex flex-col items-center gap-2 rounded-[20px] border border-line bg-surface px-2 py-4 shadow-key transition active:scale-95 hover:bg-surface2"
            >
              <Icon size={20} className="text-brand" />
              <span className="text-[11px] font-semibold text-ink">{it.label}</span>
              {!!it.badge && (
                <span className="absolute right-2 top-2 flex h-4 min-w-4 items-center justify-center rounded-full bg-neg px-1 text-[9px] font-bold text-white">
                  {it.badge > 9 ? "9+" : it.badge}
                </span>
              )}
            </button>
          );
        })}
      </div>
      {(canInstall || isIOS) && (
        <button
          onClick={async () => {
            if (canInstall) await promptInstall();
            else
              toast("Install on iPhone: Share → Add to Home Screen", {
                duration: 5000,
              });
          }}
          className="mt-1 flex w-full items-center gap-3 rounded-[20px] bg-brandsoft px-4 py-3.5 text-left transition active:scale-[0.98]"
        >
          <span className="flex h-9 w-9 items-center justify-center rounded-[13px] bg-brand text-white">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 3v12m0 0 4-4m-4 4-4-4M4 21h16" />
            </svg>
          </span>
          <span>
            <span className="block text-[13px] font-bold text-ink">Install callmony on this device</span>
            <span className="block text-[11px] text-mute">Works offline like a native app</span>
          </span>
        </button>
      )}
    </BottomSheet>
  );
}

/* ------------------------------------------------------------------ */
/* Header                                                              */
/* ------------------------------------------------------------------ */

function AppHeader() {
  const t = useT();
  const ui = useUI();
  const secondary =
    ui.tab === "general" ? t("hdr.general") : ui.tab === "reminders" ? t("tab.reminders") : ui.tab === "trash" ? t("hdr.trash") : undefined;

  if (secondary) {
    return (
      <header className="flex shrink-0 items-center gap-2 border-b border-line bg-canvas px-3 py-3">
        <button
          onClick={() => ui.setTab("contacts")}
          aria-label={t("act.back")}
          className="flex h-9 w-9 items-center justify-center rounded-full text-mute transition hover:bg-surface2"
        >
          <RotateCcw size={17} />
        </button>
        <h1 className="text-[17px] font-bold tracking-tight text-ink">{secondary}</h1>
      </header>
    );
  }

  return (
    <header className="flex shrink-0 items-center justify-between px-6 pb-3 pt-4">
      <div>
        <div className="flex items-center gap-2">
          <div className="flex h-9 w-9 items-center justify-center rounded-[13px] bg-brand text-white shadow-[0_5px_14px_rgba(22,160,133,0.2)]">
            <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.8 19.8 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.12 4.18 2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.12.96.36 1.9.7 2.8a2 2 0 0 1-.45 2.11L8.1 9.9a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.9.34 1.84.58 2.8.7A2 2 0 0 1 22 16.92z" />
            </svg>
          </div>
          <h1 className="text-[23px] font-bold tracking-[-1.2px] text-ink">
            call<span className="text-brand">mony</span>
          </h1>
        </div>
        <p className="mt-1 pl-0.5 text-[12px] text-mute">{t("hdr.tagline")}</p>
      </div>
      <div className="flex gap-2">
        <Button
          variant="surface"
          size="sm"
          className="h-10 w-10 rounded-full p-0"
          onClick={() => ui.open({ type: "search" })}
          aria-label={t("act.search")}
        >
          <Search size={18} />
        </Button>
        <Button
          variant="ghost"
          size="sm"
          className="h-10 w-10 rounded-full border border-line2 bg-surface p-0 shadow-key"
          onClick={() => ui.open({ type: "settings" })}
          aria-label={t("hdr.settings")}
        >
          <Settings size={18} />
        </Button>
      </div>
    </header>
  );
}

/* ------------------------------------------------------------------ */
/* Main app                                                            */
/* ------------------------------------------------------------------ */

export function CallmonyApp() {
  // hydration-safe "is client" check without setState-in-effect
  const mounted = useSyncExternalStore(
    () => () => undefined,
    () => true,
    () => false
  );
  const hydrated = useApp((s) => s.hydrated);
  const hydrate = useApp((s) => s.hydrate);
  const tab = useUI((s) => s.tab);
  const locked = useUI((s) => s.locked);
  const { dir } = useLang();

  useEffect(() => {
    void hydrate();
    registerSW(() =>
      toast("Update ready — tap to refresh", {
        action: { label: "Refresh", onClick: () => window.location.reload() },
        duration: 8000,
      })
    );
    const t = setTimeout(() => {
      void checkVersion((v) =>
        toast(`Version ${v} available — tap to refresh`, {
          action: { label: "Refresh", onClick: () => window.location.reload() },
          duration: 8000,
        })
      );
    }, 3000);
    const onlineCheck = () => void checkVersion(() => window.location.reload());
    window.addEventListener("online", onlineCheck);
    return () => {
      clearTimeout(t);
      window.removeEventListener("online", onlineCheck);
    };
  }, [hydrate]);

  const view = useMemo(() => {
    switch (tab) {
      case "recents":
        return <RecentsView />;
      case "contacts":
        return <ContactsView />;
      case "reminders":
        return <RemindersView />;
      case "general":
        return <GeneralView />;
      case "trash":
        return <TrashView />;
      default:
        return <ContactsView />;
    }
  }, [tab]);

  if (!mounted || !hydrated) {
    return (
      <div className="min-h-[100dvh] w-full bg-appbg sm:py-6">
        <div className="relative mx-auto flex h-[100dvh] w-full max-w-[430px] flex-col overflow-hidden bg-canvas shadow-2xl sm:h-[min(900px,calc(100dvh-48px))] sm:rounded-[34px]">
          <Splash />
        </div>
      </div>
    );
  }

  return (
    <ConfirmProvider>
      <div className="min-h-[100dvh] w-full bg-appbg sm:py-6">
        <div
          dir={dir}
          className="relative mx-auto flex h-[100dvh] w-full max-w-[430px] flex-col overflow-hidden bg-canvas shadow-2xl sm:h-[min(900px,calc(100dvh-48px))] sm:rounded-[34px]"
          role="application"
          aria-label="callmony contacts and ledger app"
        >
          <StatusBar />
          <AppHeader />

          {/* Content area — views + overlay sheets live here, ABOVE the nav.
              Sheets (absolute inset-0 of this container) never cover the nav. */}
          <div className="relative flex min-h-0 flex-1 flex-col overflow-hidden">
            <main className="app-scroll flex-1 overflow-y-auto overscroll-contain px-5 pb-10">
              {view}
            </main>
            <OverlayHost />
            <KeypadSheet />
            <MoreSheet />
            <ActionPickerHost />
          </div>

          {/* Always-visible bottom navigation (hidden only by call/lock screens) */}
          <BottomNav />

          {/* Frame-level: calls cover everything incl. the nav */}
          <CallOverlayHost />
          <AlarmWatcher />
          <AutolockWatcher />
          <AnimatePresence>{locked && <LockScreen />}</AnimatePresence>
          <Toaster position="bottom-center" offset={20} gap={8} />
        </div>
      </div>
    </ConfirmProvider>
  );
}
