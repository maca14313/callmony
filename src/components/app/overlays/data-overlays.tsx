"use client";

/* =========================================================================
   callmony — Settings / Lock / Search / Import-Export / Backup / Storage / About
   Owned by Task 2-d. Everything is local: IndexedDB store + Web APIs only.
   ========================================================================= */

import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { useTheme } from "next-themes";
import {
  AlertTriangle,
  ArrowLeftRight,
  Bell,
  BellOff,
  Check,
  CheckCircle2,
  ChevronRight,
  CloudOff,
  Delete as DeleteIcon,
  Download,
  EyeOff,
  File as FileIcon,
  FileDown,
  FileSpreadsheet,
  FileText,
  FileUp,
  HardDrive,
  Images,
  Info,
  KeyRound,
  Landmark,
  Languages,
  ListChecks,
  Loader2,
  Lock,
  LockOpen,
  Mail,
  Package,
  Paperclip,
  PhoneCall,
  Receipt,
  RefreshCw,
  SearchX,
  Share2,
  ShieldCheck,
  Smartphone,
  StickyNote,
  Table,
  Trash2,
  Unlink,
  Upload,
  Users,
  Wallet,
  Archive,
} from "lucide-react";

import { useApp } from "@/lib/store";
import { useUI } from "@/lib/ui-store";
import { LANGUAGES, useT } from "@/lib/i18n";
import { APP_VERSION, requestPersistence, useInstallPrompt } from "@/lib/pwa";
import {
  bytesStr,
  displayName,
  downloadBlob,
  fd,
  fdShort,
  hashPin,
  maskAccount,
  prettyPhone,
  relTime,
  uid,
  vibrate,
} from "@/lib/utils";
import {
  backupFilesToBlobs,
  buildBackup,
  contactsToCSV,
  entriesToCSV,
  parseBackupFile,
  parseContactsCSV,
  parseVCard,
  toVCard,
} from "@/lib/exporters";
import { blobPut, idbPut, metaSet } from "@/lib/db";
import {
  CURRENCIES,
  type AttachmentMeta,
  type BackupFile,
  type Contact,
  type ListKind,
  type Settings,
} from "@/lib/types";

import {
  Avatar,
  Button,
  Chip,
  EmptyState,
  Field,
  IconButton,
  Money,
  ProgressBar,
  SearchInput,
  SectionCard,
  SectionLabel,
  Segmented,
  cx,
  inputCls,
} from "../kit/primitives";
import { BottomSheet, Sheet } from "../kit/Sheet";
import { useConfirm } from "../kit/Confirm";
import { toastErr, toastOk } from "../kit/toast";
import { AttachmentViewer } from "../kit/Attachments";

/* ================================================================== */
/* Local types + constants                                              */
/* ================================================================== */

/* Contact Picker API (Android Chromium) — not yet in TS DOM lib */
interface PickedContact {
  name?: string[];
  tel?: string[];
  email?: string[];
}
interface ContactsManager {
  select: (props: string[], opts?: { multiple?: boolean }) => Promise<PickedContact[]>;
}
type NavigatorWithContacts = Navigator & { contacts?: ContactsManager };

const getContactsPicker = (): ContactsManager | undefined => {
  if (typeof navigator === "undefined") return undefined;
  return (navigator as NavigatorWithContacts).contacts;
};

const COUNTRIES: { code: string; label: string }[] = [
  { code: "ET", label: "Ethiopia (+251)" },
  { code: "US", label: "United States (+1)" },
  { code: "GB", label: "United Kingdom (+44)" },
  { code: "KE", label: "Kenya (+254)" },
  { code: "SA", label: "Saudi Arabia (+966)" },
  { code: "AE", label: "UAE (+971)" },
  { code: "IN", label: "India (+91)" },
  { code: "DE", label: "Germany (+49)" },
  { code: "FR", label: "France (+33)" },
  { code: "IT", label: "Italy (+39)" },
  { code: "ES", label: "Spain (+34)" },
  { code: "NL", label: "Netherlands (+31)" },
  { code: "CA", label: "Canada (+1)" },
  { code: "AU", label: "Australia (+61)" },
  { code: "NG", label: "Nigeria (+234)" },
  { code: "EG", label: "Egypt (+20)" },
  { code: "ZA", label: "South Africa (+27)" },
  { code: "TZ", label: "Tanzania (+255)" },
  { code: "UG", label: "Uganda (+256)" },
  { code: "SD", label: "Sudan (+249)" },
  { code: "SO", label: "Somalia (+252)" },
  { code: "ER", label: "Eritrea (+291)" },
];

const LIST_KIND_LABEL: Record<ListKind, string> = {
  calculating: "Calculating list",
  simple: "Simple list",
  checklist: "Checklist",
  todo: "To-do list",
};

const SEARCHES_KEY = "callmony-searches";

function readRecentSearches(): string[] {
  try {
    const raw = localStorage.getItem(SEARCHES_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed)
      ? parsed.filter((x): x is string => typeof x === "string").slice(0, 8)
      : [];
  } catch {
    return [];
  }
}

function recordRecentSearch(q: string): void {
  const clean = q.trim();
  if (clean.length < 2) return;
  try {
    const next = [
      clean,
      ...readRecentSearches().filter((x) => x.toLowerCase() !== clean.toLowerCase()),
    ].slice(0, 8);
    localStorage.setItem(SEARCHES_KEY, JSON.stringify(next));
  } catch {
    /* storage unavailable */
  }
}

/* ================================================================== */
/* Shared data logic (module-level, reads store imperatively)           */
/* ================================================================== */

const pad2 = (n: number) => String(n).padStart(2, "0");

const backupFilename = (): string => {
  const d = new Date();
  return `callmony-backup-${d.getFullYear()}${pad2(d.getMonth() + 1)}${pad2(d.getDate())}.json`;
};

async function buildBackupBlob(): Promise<{ blob: Blob; filename: string; files: number }> {
  const s = useApp.getState();
  const blob = await buildBackup(
    {
      contacts: s.contacts,
      entries: s.entries,
      groups: s.groups,
      lists: s.lists,
      notes: s.notes,
      reminders: s.reminders,
      banks: s.banks,
      recents: s.recents,
      messages: s.messages,
      attachments: s.attachments,
      settings: s.settings,
    },
    (id) => s.attachmentBlob(id)
  );
  return { blob, filename: backupFilename(), files: s.attachments.length };
}

function exportVCardAll(): void {
  const list = useApp.getState().contacts.filter((c) => !c.deletedAt);
  if (!list.length) {
    toastErr("No contacts to export yet");
    return;
  }
  downloadBlob(
    new Blob([toVCard(list)], { type: "text/vcard;charset=utf-8" }),
    "callmony-contacts.vcf"
  );
  toastOk(`${list.length} contacts exported (.vcf)`);
}

function exportContactsCsvAll(): void {
  const s = useApp.getState();
  const list = s.contacts.filter((c) => !c.deletedAt);
  if (!list.length) {
    toastErr("No contacts to export yet");
    return;
  }
  downloadBlob(
    new Blob([contactsToCSV(list)], { type: "text/csv;charset=utf-8" }),
    "callmony-contacts.csv"
  );
  toastOk(`${list.length} contacts exported (.csv)`);
}

function exportEntriesCsvAll(): void {
  const s = useApp.getState();
  const list = s.entries.filter((e) => !e.deletedAt);
  if (!list.length) {
    toastErr("No transactions to export yet");
    return;
  }
  downloadBlob(
    new Blob([entriesToCSV(list, s.contacts)], { type: "text/csv;charset=utf-8" }),
    "callmony-transactions.csv"
  );
  toastOk(`${list.length} transactions exported (.csv)`);
}

/** Replace = wipe IDB, then write every record from the backup directly to IDB. */
async function applyRestoreReplace(backup: BackupFile): Promise<void> {
  await useApp.getState().wipeAll();
  const d = backup.data;
  const blobs = await backupFilesToBlobs(backup);
  const jobs: Promise<void>[] = [];
  d.contacts.forEach((c) => jobs.push(idbPut("contacts", c)));
  d.entries.forEach((e) => jobs.push(idbPut("entries", e)));
  d.groups?.forEach((g) => jobs.push(idbPut("groups", g)));
  d.lists.forEach((l) => jobs.push(idbPut("lists", l)));
  d.notes.forEach((n) => jobs.push(idbPut("notes", n)));
  d.reminders.forEach((r) => jobs.push(idbPut("reminders", r)));
  d.banks.forEach((b) => jobs.push(idbPut("banks", b)));
  d.recents.forEach((r) => jobs.push(idbPut("recents", r)));
  d.messages.forEach((m) => jobs.push(idbPut("messages", m)));
  d.attachments.forEach((a) => jobs.push(idbPut("attachments", a)));
  blobs.forEach(({ id, blob }) => jobs.push(blobPut(id, blob)));
  /* metaSet is the canonical meta writer — hydrate() reads the {key, value} shape it produces. */
  jobs.push(
    metaSet("settings", { ...useApp.getState().settings, ...d.settings })
  );
  jobs.push(metaSet("demoSeeded", true));
  await Promise.all(jobs);
}

/** Merge = write only records whose id doesn't exist yet. */
async function applyRestoreMerge(backup: BackupFile): Promise<void> {
  const s = useApp.getState();
  const d = backup.data;
  const blobs = await backupFilesToBlobs(backup);
  const idsOf = <T extends { id: string }>(arr: T[]) => new Set(arr.map((x) => x.id));
  const have = {
    contacts: idsOf(s.contacts),
    entries: idsOf(s.entries),
    groups: idsOf(s.groups),
    lists: idsOf(s.lists),
    notes: idsOf(s.notes),
    reminders: idsOf(s.reminders),
    banks: idsOf(s.banks),
    recents: idsOf(s.recents),
    messages: idsOf(s.messages),
    attachments: idsOf(s.attachments),
  };
  const jobs: Promise<void>[] = [];
  d.contacts.filter((c) => !have.contacts.has(c.id)).forEach((c) => jobs.push(idbPut("contacts", c)));
  d.entries.filter((e) => !have.entries.has(e.id)).forEach((e) => jobs.push(idbPut("entries", e)));
  d.groups
    ?.filter((g) => !have.groups.has(g.id))
    .forEach((g) => jobs.push(idbPut("groups", g)));
  d.lists.filter((l) => !have.lists.has(l.id)).forEach((l) => jobs.push(idbPut("lists", l)));
  d.notes.filter((n) => !have.notes.has(n.id)).forEach((n) => jobs.push(idbPut("notes", n)));
  d.reminders.filter((r) => !have.reminders.has(r.id)).forEach((r) => jobs.push(idbPut("reminders", r)));
  d.banks.filter((b) => !have.banks.has(b.id)).forEach((b) => jobs.push(idbPut("banks", b)));
  d.recents.filter((r) => !have.recents.has(r.id)).forEach((r) => jobs.push(idbPut("recents", r)));
  d.messages.filter((m) => !have.messages.has(m.id)).forEach((m) => jobs.push(idbPut("messages", m)));
  d.attachments
    .filter((a) => !have.attachments.has(a.id))
    .forEach((a) => jobs.push(idbPut("attachments", a)));
  blobs
    .filter((b) => !have.attachments.has(b.id))
    .forEach(({ id, blob }) => jobs.push(blobPut(id, blob)));
  await Promise.all(jobs);
}

/* ================================================================== */
/* Small shared UI                                                      */
/* ================================================================== */

function Note({ icon, children }: { icon?: ReactNode; children: ReactNode }) {
  return (
    <p className="flex gap-2 px-1 pt-2 text-[11px] leading-[1.6] text-mute">
      {icon && <span className="mt-[2px] shrink-0 text-brand">{icon}</span>}
      <span>{children}</span>
    </p>
  );
}

function ToggleRow({
  label,
  hint,
  checked,
  onToggle,
  icon,
}: {
  label: string;
  hint?: string;
  checked: boolean;
  onToggle: () => void;
  icon?: ReactNode;
}) {
  return (
    <div className="flex items-center gap-3 py-1">
      {icon && (
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[13px] bg-brandsoft text-brand">
          {icon}
        </span>
      )}
      <div className="min-w-0 flex-1">
        <p className="text-[13.5px] font-semibold text-ink">{label}</p>
        {hint && <p className="mt-0.5 text-[11px] leading-4 text-mute">{hint}</p>}
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={label}
        onClick={onToggle}
        className={cx(
          "relative h-7 w-[46px] shrink-0 rounded-full border transition-colors duration-200",
          checked ? "border-brand bg-brand" : "border-line2 bg-surface2"
        )}
      >
        <span
          className={cx(
            "absolute top-1/2 h-[22px] w-[22px] -translate-y-1/2 rounded-full bg-white shadow-key transition-all duration-200",
            checked ? "left-[22px]" : "left-[1px]"
          )}
        />
      </button>
    </div>
  );
}

function ActionRow({
  icon,
  label,
  hint,
  right,
  onClick,
  danger,
  busy,
}: {
  icon?: ReactNode;
  label: string;
  hint?: ReactNode;
  right?: ReactNode;
  onClick?: () => void;
  danger?: boolean;
  busy?: boolean;
}) {
  const content = (
    <>
      <span
        className={cx(
          "flex h-10 w-10 shrink-0 items-center justify-center rounded-[13px]",
          danger ? "bg-negsoft text-neg" : "bg-brandsoft text-brand"
        )}
      >
        {busy ? <Loader2 size={17} className="animate-spin" /> : icon}
      </span>
      <div className="min-w-0 flex-1">
        <p className={cx("text-[13.5px] font-semibold", danger ? "text-neg" : "text-ink")}>
          {label}
        </p>
        {hint && <p className="mt-0.5 text-[11px] leading-4 text-mute">{hint}</p>}
      </div>
      {right ??
        (onClick && !busy ? (
          <ChevronRight size={17} className="shrink-0 text-mute" />
        ) : null)}
    </>
  );
  if (!onClick) {
    return <div className="flex items-center gap-3 py-3">{content}</div>;
  }
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      className="flex w-full items-center gap-3 py-3 text-left transition hover:opacity-90 active:scale-[0.99]"
    >
      {content}
    </button>
  );
}

/** Button that triggers a hidden file input. */
function FilePickButton({
  accept,
  onFiles,
  children,
  variant = "surface",
  size = "md",
  className,
}: {
  accept: string;
  onFiles: (files: File[]) => void;
  children: ReactNode;
  variant?: "brand" | "soft" | "surface" | "danger" | "ghost";
  size?: "sm" | "md" | "lg";
  className?: string;
}) {
  const ref = useRef<HTMLInputElement>(null);
  return (
    <>
      <input
        ref={ref}
        type="file"
        accept={accept}
        className="hidden"
        onChange={(e) => {
          const files = Array.from(e.target.files || []);
          e.target.value = "";
          if (files.length) onFiles(files);
        }}
      />
      <Button
        variant={variant}
        size={size}
        className={className}
        onClick={() => ref.current?.click()}
      >
        {children}
      </Button>
    </>
  );
}

/* ================================================================== */
/* PIN machinery                                                        */
/* ================================================================== */

const PIN_KEYS = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "del"] as const;

function PinDots({ count, filled, shakeKey }: { count: number; filled: number; shakeKey: number }) {
  return (
    <motion.div
      key={shakeKey}
      initial={{ x: 0 }}
      animate={shakeKey > 0 ? { x: [0, -10, 10, -8, 8, -4, 0] } : { x: 0 }}
      transition={{ duration: 0.45, ease: "easeInOut" }}
      className="flex items-center justify-center gap-4"
      role="status"
      aria-label={`${filled} of ${count} digits entered`}
    >
      {Array.from({ length: count }).map((_, i) => (
        <span
          key={i}
          className={cx(
            "h-3.5 w-3.5 rounded-full border-2 transition-all duration-150",
            i < filled ? "scale-110 border-brand bg-brand" : "border-line2 bg-transparent"
          )}
        />
      ))}
    </motion.div>
  );
}

function PinPad({
  onDigit,
  onDelete,
  disabled,
}: {
  onDigit: (d: string) => void;
  onDelete: () => void;
  disabled?: boolean;
}) {
  return (
    <div className="mx-auto grid w-full max-w-[270px] grid-cols-3 gap-x-4 gap-y-3">
      {PIN_KEYS.map((k, i) => {
        if (k === "") return <span key={i} aria-hidden />;
        if (k === "del") {
          return (
            <button
              key={i}
              type="button"
              disabled={disabled}
              onClick={onDelete}
              aria-label="Delete last digit"
              className="flex h-16 items-center justify-center rounded-[18px] text-mute transition hover:bg-surface2 active:scale-95 disabled:opacity-40"
            >
              <DeleteIcon size={24} />
            </button>
          );
        }
        return (
          <button
            key={i}
            type="button"
            disabled={disabled}
            onClick={() => onDigit(k)}
            aria-label={`PIN digit ${k}`}
            className="flex h-16 items-center justify-center rounded-[18px] bg-surface text-[24px] font-medium text-ink shadow-key transition hover:bg-surface2 active:scale-95 disabled:opacity-40"
          >
            {k}
          </button>
        );
      })}
    </div>
  );
}

/** Set / change PIN flow: (verify old) → enter new → re-enter → confirm modal. */
function PinFlow({ mode, onClose }: { mode: "set" | "change"; onClose: () => void }) {
  const confirm = useConfirm();
  const updateSettings = useApp((s) => s.updateSettings);
  const hasPin = !!useApp((s) => s.settings.pinHash);
  const [stage, setStage] = useState<"verify" | "new" | "confirm">(
    mode === "change" && hasPin ? "verify" : "new"
  );
  const [digits, setDigits] = useState("");
  const [firstPin, setFirstPin] = useState("");
  const [shakeKey, setShakeKey] = useState(0);
  const [busy, setBusy] = useState(false);

  const fail = useCallback((msg: string) => {
    vibrate([40, 70, 40]);
    setShakeKey((k) => k + 1);
    setDigits("");
    toastErr(msg);
  }, []);

  const submit = useCallback(
    async (pin: string) => {
      if (stage === "verify") {
        const ph = useApp.getState().settings.pinHash;
        if (!ph) {
          setDigits("");
          setStage("new");
          return;
        }
        setBusy(true);
        const { hash } = await hashPin(pin, ph.salt);
        setBusy(false);
        if (hash === ph.hash) {
          setDigits("");
          setStage("new");
        } else {
          fail("Wrong PIN — try again");
        }
        return;
      }
      if (stage === "new") {
        setFirstPin(pin);
        setDigits("");
        setStage("confirm");
        return;
      }
      /* confirm */
      if (pin !== firstPin) {
        fail("PINs don't match — start over");
        setStage("new");
        setFirstPin("");
        setDigits("");
        return;
      }
      setBusy(true);
      const ok = await confirm({
        tone: "brand",
        icon: <Lock size={19} />,
        title: mode === "change" ? "Change PIN?" : "Turn on PIN lock?",
        message:
          mode === "change"
            ? "Your screen-lock PIN will be updated."
            : "You'll enter this PIN whenever callmony opens or unlocks.",
        confirmLabel: mode === "change" ? "Change PIN" : "Enable lock",
      });
      if (!ok) {
        setBusy(false);
        setDigits("");
        setStage("new");
        setFirstPin("");
        return;
      }
      const hashed = await hashPin(pin);
      updateSettings({ pinHash: hashed });
      setBusy(false);
      toastOk(mode === "change" ? "PIN changed" : "PIN lock enabled");
      onClose();
    },
    [stage, firstPin, mode, confirm, updateSettings, onClose, fail]
  );

  const add = (d: string) => {
    if (busy) return;
    vibrate(8);
    const next = (digits + d).slice(0, 4);
    setDigits(next);
    if (next.length === 4) void submit(next);
  };

  const stageText =
    stage === "verify"
      ? "Enter your current PIN"
      : stage === "new"
        ? mode === "change"
          ? "Enter a new 4-digit PIN"
          : "Choose a 4-digit PIN"
        : "Re-enter your new PIN";

  return (
    <div className="flex flex-col items-center gap-5 pb-4 pt-2">
      <p className="text-[13px] font-semibold text-ink">{busy ? "Checking…" : stageText}</p>
      <PinDots count={4} filled={digits.length} shakeKey={shakeKey} />
      <PinPad onDigit={add} onDelete={() => setDigits((d) => d.slice(0, -1))} disabled={busy} />
    </div>
  );
}

function SetPinSheet({
  open,
  mode,
  onClose,
}: {
  open: boolean;
  mode: "set" | "change";
  onClose: () => void;
}) {
  /* PinFlow mounts fresh each time the sheet opens — its state resets itself. */
  return (
    <BottomSheet open={open} onClose={onClose} title={mode === "change" ? "Change PIN" : "Set PIN"}>
      {open ? <PinFlow mode={mode} onClose={onClose} /> : null}
    </BottomSheet>
  );
}

/* ================================================================== */
/* Notifications permission (Settings + About share this)               */
/* ================================================================== */

function useNotifPermission(): {
  perm: "default" | "granted" | "denied" | "unsupported";
  ask: () => Promise<void>;
} {
  const updateSettings = useApp((s) => s.updateSettings);
  /* overlays render client-side only (post-hydration), so a lazy read is safe */
  const [perm, setPerm] = useState<"default" | "granted" | "denied" | "unsupported">(() =>
    typeof Notification === "undefined" ? "unsupported" : Notification.permission
  );

  const ask = useCallback(async () => {
    if (typeof Notification === "undefined") {
      toastErr("Notifications aren't supported in this browser");
      return;
    }
    try {
      const result = await Notification.requestPermission();
      setPerm(result);
      updateSettings({ notifAsked: true });
      if (result === "granted") {
        toastOk("Notifications enabled");
        try {
          new Notification("callmony", { body: "Reminders will now appear here." });
        } catch {
          /* some platforms route through the service worker */
        }
      } else if (result === "denied") {
        toastOk("Blocked — you can change it in this site's browser settings");
      } else {
        toastOk("No worries — you can enable them anytime");
      }
    } catch {
      toastErr("Could not ask for permission");
    }
  }, [updateSettings]);

  return { perm, ask };
}

function NotifStatusRow({
  perm,
  onAsk,
}: {
  perm: "default" | "granted" | "denied" | "unsupported";
  onAsk: () => void;
}) {
  if (perm === "granted") {
    return (
      <ActionRow
        icon={<CheckCircle2 size={18} />}
        label="Notifications enabled"
        hint="Reminders will alert you, even with callmony in the background."
      />
    );
  }
  if (perm === "denied") {
    return (
      <ActionRow
        icon={<BellOff size={18} />}
        label="Notifications blocked"
        hint="Allow them from this site in your browser's site settings."
      />
    );
  }
  if (perm === "unsupported") {
    return (
      <ActionRow
        icon={<Bell size={18} />}
        label="Notifications unsupported"
        hint="This browser can't show reminder alerts."
      />
    );
  }
  return (
    <ActionRow
      icon={<Bell size={18} />}
      label="Allow notifications"
      hint="Reminder alerts, even when callmony is in the background."
      right={
        <Button variant="soft" size="sm" onClick={onAsk}>
          Allow
        </Button>
      }
    />
  );
}

/* ================================================================== */
/* Contacts import (phone picker / vCard / CSV)                         */
/* ================================================================== */

function ImportContent({
  contacts,
  source,
  onClose,
}: {
  contacts: Partial<Contact>[];
  source: string;
  onClose: () => void;
}) {
  const importContacts = useApp((s) => s.importContacts);
  const [mode, setMode] = useState<"skip" | "merge">("skip");

  const withPhone = contacts.filter((c) => (c.phones || []).length).length;
  const withEmail = contacts.filter((c) => (c.emails || []).length).length;

  const run = () => {
    const res = importContacts(contacts, mode);
    toastOk(`${res.added} added · ${res.merged} merged · ${res.skipped} skipped`);
    onClose();
  };

  return (
    <div className="space-y-4 pb-2">
      <p className="px-1 text-[11px] text-mute">{source}</p>
      <div className="rounded-2xl bg-surface2 p-4">
        <p className="text-[13px] font-bold text-ink">{contacts.length} contacts detected</p>
        <p className="mt-1 text-[11px] leading-5 text-mute">
          {withPhone} with a phone number · {withEmail} with an email
        </p>
      </div>
      <Segmented
        options={[
          { value: "skip", label: "Skip duplicates" },
          { value: "merge", label: "Merge" },
        ]}
        value={mode}
        onChange={setMode}
      />
      <p className="px-1 text-[11px] leading-5 text-mute">
        {mode === "skip"
          ? "Contacts whose phone number already exists in callmony are left untouched."
          : "New phone numbers and emails are added onto matching existing contacts."}
      </p>
      <Button variant="brand" size="lg" className="w-full" onClick={run}>
        Import {contacts.length} contacts
      </Button>
    </div>
  );
}

function ImportChoiceSheet({
  open,
  onClose,
  contacts,
  source,
}: {
  open: boolean;
  onClose: () => void;
  contacts: Partial<Contact>[];
  source: string;
}) {
  /* ImportContent mounts fresh each time the sheet opens — mode resets itself. */
  return (
    <BottomSheet
      open={open && contacts.length > 0}
      onClose={onClose}
      title={`Import ${contacts.length} contacts`}
    >
      {contacts.length > 0 ? <ImportContent contacts={contacts} source={source} onClose={onClose} /> : null}
    </BottomSheet>
  );
}

function useContactsImport(): {
  canPickPhone: boolean;
  pickPhone: () => Promise<void>;
  pickVCard: (files: File[]) => void;
  pickCsv: (files: File[]) => void;
  sheet: ReactNode;
} {
  const [pending, setPending] = useState<Partial<Contact>[]>([]);
  const [source, setSource] = useState("");
  const [open, setOpen] = useState(false);
  const canPickPhone = useMemo(() => {
    const picker = getContactsPicker();
    return !!picker && typeof picker.select === "function";
  }, []);

  const start = (list: Partial<Contact>[], src: string) => {
    if (!list.length) {
      toastErr("No importable contacts found in that file");
      return;
    }
    setPending(list);
    setSource(src);
    setOpen(true);
  };

  const pickVCard = (files: File[]) => {
    void (async () => {
      try {
        const text = await files[0].text();
        start(parseVCard(text), `${files[0].name} · vCard`);
      } catch {
        toastErr("Could not read that file");
      }
    })();
  };

  const pickCsv = (files: File[]) => {
    void (async () => {
      try {
        const text = await files[0].text();
        start(parseContactsCSV(text), `${files[0].name} · CSV`);
      } catch {
        toastErr("Could not read that file");
      }
    })();
  };

  const pickPhone = async () => {
    const api = getContactsPicker();
    if (!api || typeof api.select !== "function") {
      toastErr("Contact picker not supported on this device");
      return;
    }
    try {
      const picked = await api.select(["name", "tel", "email"], { multiple: true });
      const mapped: Partial<Contact>[] = picked.map((p) => {
        const parts = (p.name?.[0] || "").trim().split(/\s+/).filter(Boolean);
        return {
          firstName: parts[0] || "",
          lastName: parts.slice(1).join(" "),
          phones: (p.tel || []).map((number, i) => ({
            id: uid(),
            label: "mobile",
            number,
            primary: i === 0,
          })),
          emails: (p.email || []).map((address, i) => ({
            id: uid(),
            label: "home",
            address,
            primary: i === 0,
          })),
        };
      });
      if (!mapped.length) {
        toastOk("No contacts selected");
        return;
      }
      setPending(mapped);
      setSource("Picked with this phone's contact picker");
      setOpen(true);
    } catch {
      toastOk("Contact picker cancelled");
    }
  };

  const sheet = (
    <ImportChoiceSheet
      open={open}
      contacts={pending}
      source={source}
      onClose={() => setOpen(false)}
    />
  );

  return { canPickPhone, pickPhone, pickVCard, pickCsv, sheet };
}

/* ================================================================== */
/* Backup export / restore                                              */
/* ================================================================== */

function useBackupExport(): {
  exportBackup: () => Promise<void>;
  shareByEmail: () => Promise<void>;
  busy: boolean;
  sharing: boolean;
} {
  const [busy, setBusy] = useState(false);
  const [sharing, setSharing] = useState(false);

  const exportBackup = useCallback(async () => {
    setBusy(true);
    try {
      const { blob, filename } = await buildBackupBlob();
      downloadBlob(blob, filename);
      useApp.getState().updateSettings({ lastBackupAt: Date.now() });
      toastOk("Backup exported");
    } catch {
      toastErr("Backup failed — try again");
    }
    setBusy(false);
  }, []);

  const shareByEmail = useCallback(async () => {
    setSharing(true);
    try {
      const { blob, filename } = await buildBackupBlob();
      const file = new File([blob], filename, { type: "application/json" });
      const nav = navigator as Navigator & { canShare?: (d: ShareData) => boolean };
      if (nav.share && nav.canShare?.({ files: [file] })) {
        try {
          await nav.share({
            title: "callmony backup",
            text: "Your callmony backup file is attached. Keep it somewhere safe — restore it from Settings → Backup & restore.",
            files: [file],
          });
          useApp.getState().updateSettings({ lastBackupAt: Date.now() });
        } catch {
          /* user cancelled the share sheet */
        }
      } else {
        downloadBlob(blob, filename);
        toastOk("Backup downloaded — attach it to an email to send it");
      }
    } catch {
      toastErr("Could not build the backup");
    }
    setSharing(false);
  }, []);

  return { exportBackup, shareByEmail, busy, sharing };
}

function RestoreContent({ backup, onClose }: { backup: BackupFile; onClose: () => void }) {
  const confirm = useConfirm();
  const [mode, setMode] = useState<"replace" | "merge">("merge");
  const [busy, setBusy] = useState(false);

  const counts = [
    `${backup.data.contacts.length} contacts`,
    `${backup.data.entries.length} entries`,
    `${backup.data.groups?.length ?? 0} groups`,
    `${backup.data.lists.length} lists`,
    `${backup.data.notes.length} notes`,
    `${backup.data.reminders.length} reminders`,
    `${backup.data.banks.length} accounts`,
    `${(backup.files || []).length} files`,
  ];

  const apply = async () => {
    if (busy) return;
    const ok =
      mode === "replace"
        ? await confirm({
            tone: "danger",
            title: "Replace everything?",
            message:
              "All data currently on this device will be erased, then the backup will be restored in its place. This cannot be undone.",
            confirmLabel: "Erase & restore",
          })
        : await confirm({
            tone: "brand",
            title: "Merge backup in?",
            message:
              "Only records that don't already exist here will be added. Nothing current is deleted.",
            confirmLabel: "Merge in",
          });
    if (!ok) return;
    setBusy(true);
    try {
      if (mode === "replace") await applyRestoreReplace(backup);
      else await applyRestoreMerge(backup);
    } catch {
      setBusy(false);
      toastErr("Restore failed — is the file complete?");
      return;
    }
    toastOk("Restored — reloading…");
    setTimeout(() => window.location.reload(), 600);
  };

  return (
    <div className="space-y-4 pb-2">
      <div className="rounded-2xl bg-surface2 p-4">
        <p className="text-[13px] font-bold text-ink">Backup from {fd(backup.exportedAt)}</p>
        <p className="mt-1 text-[11px] leading-5 text-mute">{counts.join(" · ")}</p>
      </div>
      <Segmented
        options={[
          { value: "merge", label: "Merge" },
          { value: "replace", label: "Replace" },
        ]}
        value={mode}
        onChange={setMode}
      />
      <p className="px-1 text-[11px] leading-5 text-mute">
        {mode === "merge"
          ? "Adds anything missing. Existing records are left untouched."
          : "Wipes this device first, then writes the backup in its place."}
      </p>
      <Button
        variant={mode === "replace" ? "danger" : "brand"}
        size="lg"
        className="w-full"
        disabled={busy}
        onClick={apply}
      >
        {busy ? <Loader2 size={15} className="animate-spin" /> : null}
        {mode === "replace" ? "Erase & restore" : "Merge backup in"}
      </Button>
    </div>
  );
}

function RestoreChoiceSheet({
  open,
  backup,
  onClose,
}: {
  open: boolean;
  backup: BackupFile | null;
  onClose: () => void;
}) {
  /* RestoreContent mounts fresh per backup — its state resets itself. */
  if (!backup) return null;
  return (
    <BottomSheet open={open} onClose={onClose} title="Restore backup">
      <RestoreContent key={`${backup.exportedAt}-${backup.files?.length ?? 0}`} backup={backup} onClose={onClose} />
    </BottomSheet>
  );
}

/* ================================================================== */
/* Search overlay                                                       */
/* ================================================================== */

function Hi({ text, q }: { text: string; q: string }) {
  if (!q) return <>{text}</>;
  const idx = text.toLowerCase().indexOf(q.toLowerCase());
  if (idx < 0) return <>{text}</>;
  return (
    <>
      {text.slice(0, idx)}
      <mark className="rounded bg-brandsoft px-0.5 text-brand">
        {text.slice(idx, idx + q.length)}
      </mark>
      {text.slice(idx + q.length)}
    </>
  );
}

interface SearchHit {
  key: string;
  title: string;
  sub?: ReactNode;
  icon?: ReactNode;
  contact?: Contact | null;
  attachment?: AttachmentMeta;
  onGo: () => void;
}

interface SearchGroup {
  key: string;
  label: string;
  hits: SearchHit[];
}

function HitRow({ hit, query }: { hit: SearchHit; query: string }) {
  return (
    <button
      type="button"
      onClick={hit.onGo}
      aria-label={hit.title}
      className="flex w-full items-center gap-3 rounded-2xl border border-line bg-surface p-2.5 text-left shadow-key transition hover:bg-surface2 active:scale-[0.99]"
    >
      {hit.contact ? (
        <Avatar contact={hit.contact} size="sm" />
      ) : hit.attachment ? (
        hit.attachment.kind === "image" && hit.attachment.thumb ? (
          <img
            src={hit.attachment.thumb}
            alt={hit.attachment.name}
            className="h-9 w-9 shrink-0 rounded-[12px] object-cover"
          />
        ) : (
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[12px] bg-surface2 text-mute">
            {hit.attachment.kind === "pdf" ? (
              <FileText size={16} className="text-neg" />
            ) : (
              <FileIcon size={16} />
            )}
          </span>
        )
      ) : (
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[12px] bg-brandsoft text-brand">
          {hit.icon}
        </span>
      )}
      <span className="min-w-0 flex-1">
        <span className="block truncate text-[13px] font-semibold text-ink">
          <Hi text={hit.title} q={query} />
        </span>
        {hit.sub ? (
          <span className="mt-0.5 block truncate text-[11px] text-mute">
            {typeof hit.sub === "string" ? <Hi text={hit.sub} q={query} /> : hit.sub}
          </span>
        ) : null}
      </span>
    </button>
  );
}

export function SearchOverlay({ query: initialQuery }: { query?: string }) {
  const pop = useUI((s) => s.pop);
  const open = useUI((s) => s.open);
  const setTab = useUI((s) => s.setTab);

  const contacts = useApp((s) => s.contacts);
  const entries = useApp((s) => s.entries);
  const lists = useApp((s) => s.lists);
  const notes = useApp((s) => s.notes);
  const reminders = useApp((s) => s.reminders);
  const banks = useApp((s) => s.banks);
  const attachments = useApp((s) => s.attachments);
  const countryCode = useApp((s) => s.settings.countryCode);

  const [query, setQuery] = useState(initialQuery ?? "");
  const [recents, setRecents] = useState<string[]>(() => readRecentSearches());
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [viewing, setViewing] = useState<AttachmentMeta | null>(null);

  const q = query.trim();
  const ql = q.toLowerCase();
  const digits = ql.replace(/\D/g, "");

  const contactById = useMemo(() => new Map(contacts.map((c) => [c.id, c])), [contacts]);

  const parentLabel = useCallback(
    (a: AttachmentMeta): string => {
      switch (a.parentType) {
        case "contact": {
          const c = contactById.get(a.parentId);
          return c ? displayName(c) : "removed contact";
        }
        case "entry": {
          const e = entries.find((x) => x.id === a.parentId);
          return e ? `Ledger · ${e.note || e.category || "entry"}` : "ledger entry";
        }
        case "list": {
          const l = lists.find((x) => x.id === a.parentId);
          return l ? `List · ${l.title}` : "list";
        }
        case "item": {
          const l = lists.find((x) => x.items.some((i) => i.id === a.parentId));
          return l ? `Item in ${l.title}` : "list item";
        }
        case "note": {
          const n = notes.find((x) => x.id === a.parentId);
          return n ? `Note · ${n.title || "untitled"}` : "note";
        }
        case "reminder": {
          const r = reminders.find((x) => x.id === a.parentId);
          return r ? `Reminder · ${r.title}` : "reminder";
        }
        case "bank": {
          const b = banks.find((x) => x.id === a.parentId);
          return b ? `Account · ${b.label}` : "account";
        }
        case "message":
          return "message log";
        default:
          return "file";
      }
    },
    [contactById, entries, lists, notes, reminders, banks]
  );

  const groups = useMemo<SearchGroup[]>(() => {
    if (q.length < 2) return [];
    const out: SearchGroup[] = [];

    /* contacts */
    const contactHits: SearchHit[] = [];
    for (const c of contacts) {
      if (c.deletedAt) continue;
      const name = displayName(c);
      const nameHit = name.toLowerCase().includes(ql);
      const phoneHit =
        digits.length >= 2 && c.phones.some((p) => p.number.replace(/\D/g, "").includes(digits));
      const emailHit = c.emails.some((e) => e.address.toLowerCase().includes(ql));
      const noteHit =
        (c.note || "").toLowerCase().includes(ql) ||
        (c.pinnedNote || "").toLowerCase().includes(ql);
      const tagHit = c.tags.some((t) => t.toLowerCase().includes(ql));
      const companyHit = (c.company || "").toLowerCase().includes(ql);
      if (!nameHit && !phoneHit && !emailHit && !noteHit && !tagHit && !companyHit) continue;
      let sub: string | undefined;
      if (!nameHit) {
        const ph = phoneHit
          ? c.phones.find((p) => p.number.replace(/\D/g, "").includes(digits))
          : undefined;
        sub =
          (ph ? prettyPhone(ph.number, countryCode) : undefined) ||
          c.emails.find((e) => e.address.toLowerCase().includes(ql))?.address ||
          (companyHit ? c.company : undefined) ||
          (tagHit ? `#${c.tags.find((t) => t.toLowerCase().includes(ql)) ?? ""}` : undefined) ||
          (noteHit ? (c.note || c.pinnedNote || "").slice(0, 60) : undefined);
      }
      contactHits.push({
        key: c.id,
        title: name,
        sub,
        contact: c,
        onGo: () => open({ type: "contact", contactId: c.id }),
      });
    }
    if (contactHits.length) out.push({ key: "contacts", label: "Contacts", hits: contactHits });

    /* ledger entries */
    const entryHits: SearchHit[] = [];
    for (const e of entries) {
      if (e.deletedAt) continue;
      const title = e.note || e.category || "Ledger entry";
      const hit =
        title.toLowerCase().includes(ql) ||
        (e.category || "").toLowerCase().includes(ql) ||
        String(e.amount).includes(ql);
      if (!hit) continue;
      const owner = e.ownerId === "general" ? null : contactById.get(e.ownerId);
      entryHits.push({
        key: e.id,
        title,
        icon: <ArrowLeftRight size={16} />,
        sub: (
          <span className="inline-flex items-center gap-1.5">
            <Money value={e.amount} />
            <span>· {e.type === "credit" ? "they owe you" : "you owe"}</span>
            <span>· {owner ? displayName(owner) : "General"}</span>
          </span>
        ),
        onGo: () => {
          if (owner) open({ type: "contact", contactId: owner.id });
          else setTab("general");
        },
      });
    }
    if (entryHits.length) out.push({ key: "entries", label: "Entries", hits: entryHits });

    /* lists (+ item names) */
    const listHits: SearchHit[] = [];
    for (const l of lists) {
      if (l.deletedAt) continue;
      const titleHit = l.title.toLowerCase().includes(ql);
      const noteHit = (l.note || "").toLowerCase().includes(ql);
      const item = l.items.find((i) => i.name.toLowerCase().includes(ql));
      if (!titleHit && !noteHit && !item) continue;
      listHits.push({
        key: l.id,
        title: l.title,
        sub: item
          ? `Item: ${item.name}`
          : noteHit
            ? (l.note || "").slice(0, 60)
            : LIST_KIND_LABEL[l.kind],
        icon: <ListChecks size={16} />,
        onGo: () => open({ type: "list", listId: l.id }),
      });
    }
    if (listHits.length) out.push({ key: "lists", label: "Lists", hits: listHits });

    /* notes */
    const noteHits: SearchHit[] = [];
    for (const n of notes) {
      if (n.deletedAt) continue;
      const titleHit = n.title.toLowerCase().includes(ql);
      const bodyHit = n.body.toLowerCase().includes(ql);
      const tagHit = n.tags.some((t) => t.toLowerCase().includes(ql));
      if (!titleHit && !bodyHit && !tagHit) continue;
      const idx = bodyHit ? n.body.toLowerCase().indexOf(ql) : -1;
      const snippet =
        idx >= 0
          ? `…${n.body.slice(Math.max(0, idx - 24), idx + ql.length + 24).trim()}…`
          : undefined;
      noteHits.push({
        key: n.id,
        title: n.title || "Untitled note",
        sub:
          snippet ||
          (tagHit ? `#${n.tags.find((t) => t.toLowerCase().includes(ql)) ?? ""}` : undefined),
        icon: <StickyNote size={16} />,
        onGo: () => open({ type: "noteEdit", noteId: n.id, ownerId: n.ownerId }),
      });
    }
    if (noteHits.length) out.push({ key: "notes", label: "Notes", hits: noteHits });

    /* reminders */
    const reminderHits: SearchHit[] = [];
    for (const r of reminders) {
      if (r.deletedAt) continue;
      const titleHit = r.title.toLowerCase().includes(ql);
      const notesHit = (r.notes || "").toLowerCase().includes(ql);
      if (!titleHit && !notesHit) continue;
      reminderHits.push({
        key: r.id,
        title: r.title,
        sub: titleHit
          ? r.done
            ? `Done · ${fdShort(r.dueAt)}`
            : `Due ${fdShort(r.dueAt)}`
          : (r.notes || "").slice(0, 60) || `Due ${fdShort(r.dueAt)}`,
        icon: <Bell size={16} />,
        onGo: () => open({ type: "reminderEdit", reminderId: r.id, ownerId: r.ownerId }),
      });
    }
    if (reminderHits.length)
      out.push({ key: "reminders", label: "Reminders", hits: reminderHits });

    /* bank accounts */
    const bankHits: SearchHit[] = [];
    for (const b of banks) {
      if (b.deletedAt) continue;
      const labelHit = b.label.toLowerCase().includes(ql);
      const bankHit = (b.bankName || "").toLowerCase().includes(ql);
      const holderHit = (b.holderName || "").toLowerCase().includes(ql);
      if (!labelHit && !bankHit && !holderHit) continue;
      bankHits.push({
        key: b.id,
        title: b.label,
        sub: [b.bankName, maskAccount(b.accountNumber)].filter(Boolean).join(" · ") || undefined,
        icon: <Landmark size={16} />,
        onGo: () => open({ type: "bankEdit", bankId: b.id, ownerId: b.ownerId }),
      });
    }
    if (bankHits.length) out.push({ key: "banks", label: "Accounts", hits: bankHits });

    /* files */
    const fileHits: SearchHit[] = [];
    for (const a of attachments) {
      if (!a.name.toLowerCase().includes(ql)) continue;
      fileHits.push({
        key: a.id,
        title: a.name,
        sub: `${bytesStr(a.size)} · ${parentLabel(a)}`,
        attachment: a,
        onGo: () => setViewing(a),
      });
    }
    if (fileHits.length) out.push({ key: "files", label: "Files", hits: fileHits });

    return out;
  }, [
    q,
    ql,
    digits,
    contacts,
    entries,
    lists,
    notes,
    reminders,
    banks,
    attachments,
    contactById,
    countryCode,
    open,
    setTab,
    parentLabel,
  ]);

  const go = (hit: SearchHit) => {
    recordRecentSearch(q);
    if (!hit.attachment) pop();
    hit.onGo();
  };

  const totalHits = groups.reduce((s, g) => s + g.hits.length, 0);

  return (
    <Sheet title="Search" onBack={pop}>
      <div className="px-4 pb-12">
        <div className="sticky top-0 z-10 -mx-4 bg-canvas/95 px-4 py-3 backdrop-blur">
          <SearchInput
            autoFocus
            value={query}
            onChange={setQuery}
            placeholder="Search everything…"
          />
        </div>

        {q.length === 0 ? (
          <section aria-label="Recent searches" className="pt-2">
            <SectionLabel
              right={
                recents.length > 0 ? (
                  <button
                    type="button"
                    onClick={() => {
                      try {
                        localStorage.removeItem(SEARCHES_KEY);
                      } catch {
                        /* ignore */
                      }
                      setRecents([]);
                    }}
                    className="text-[11px] font-semibold text-mute transition hover:text-neg"
                    aria-label="Clear recent searches"
                  >
                    Clear
                  </button>
                ) : undefined
              }
            >
              Recent searches
            </SectionLabel>
            {recents.length ? (
              <div className="flex flex-wrap gap-2">
                {recents.map((r) => (
                  <Chip key={r} className="py-2.5" onClick={() => setQuery(r)}>
                    {r}
                  </Chip>
                ))}
              </div>
            ) : (
              <p className="rounded-[18px] border border-dashed border-line2 px-4 py-6 text-center text-[11px] leading-5 text-mute">
                Search across contacts, ledger entries, lists, notes, reminders, bank accounts and
                files — all at once.
              </p>
            )}
          </section>
        ) : q.length < 2 ? (
          <p className="pt-8 text-center text-[11px] text-mute">Keep typing — at least 2 characters</p>
        ) : totalHits === 0 ? (
          <div className="pt-6">
            <EmptyState
              icon={<SearchX size={22} />}
              title={`No results for “${q}”`}
              hint="Try a name, phone number, word from a note, or an amount."
            />
          </div>
        ) : (
          <div className="space-y-5 pt-2">
            {groups.map((g) => {
              const shown = expanded.has(g.key) ? g.hits : g.hits.slice(0, 5);
              const more = g.hits.length - shown.length;
              return (
                <section key={g.key} aria-label={g.label}>
                  <SectionLabel right={<span className="text-[10px] text-mute">{g.hits.length}</span>}>
                    {g.label}
                  </SectionLabel>
                  <div className="space-y-2">
                    {shown.map((h) => (
                      <HitRow
                        key={h.key}
                        hit={{ ...h, onGo: () => go(h) }}
                        query={q}
                      />
                    ))}
                    {more > 0 && (
                      <button
                        type="button"
                        onClick={() =>
                          setExpanded((s) => new Set([...s, g.key]))
                        }
                        className="w-full rounded-2xl border border-dashed border-line2 py-3 text-[11px] font-semibold text-mute transition hover:border-brand hover:text-brand"
                      >
                        {more} more…
                      </button>
                    )}
                  </div>
                </section>
              );
            })}
          </div>
        )}
      </div>
      <AnimatePresence>
        {viewing && <AttachmentViewer meta={viewing} onClose={() => setViewing(null)} />}
      </AnimatePresence>
    </Sheet>
  );
}

/* ================================================================== */
/* Settings                                                             */
/* ================================================================== */

export function SettingsView() {
  const pop = useUI((s) => s.pop);
  const openOverlay = useUI((s) => s.open);
  const t = useT();
  const settings = useApp((s) => s.settings);
  const updateSettings = useApp((s) => s.updateSettings);
  const wipeAll = useApp((s) => s.wipeAll);
  const attachments = useApp((s) => s.attachments);
  const contactsCount = useApp((s) => s.contacts.filter((c) => !c.deletedAt).length);
  const entriesCount = useApp((s) => s.entries.filter((e) => !e.deletedAt).length);
  const confirm = useConfirm();
  const { setTheme } = useTheme();

  const backupExport = useBackupExport();
  const contactsImport = useContactsImport();
  const notif = useNotifPermission();
  const { canInstall, promptInstall, isIOS } = useInstallPrompt();

  const [pinSheet, setPinSheet] = useState<{ open: boolean; mode: "set" | "change" }>({
    open: false,
    mode: "set",
  });
  const [langSheet, setLangSheet] = useState(false);
  /* overlays render client-side only, so a lazy read is safe */
  const [standalone] = useState(() =>
    typeof window !== "undefined" && window.matchMedia
      ? window.matchMedia("(display-mode: standalone)").matches
      : false
  );
  const [wiping, setWiping] = useState(false);
  const [restoreBackup, setRestoreBackup] = useState<BackupFile | null>(null);
  const restoreInputRef = useRef<HTMLInputElement>(null);
  const vcardInputRef = useRef<HTMLInputElement>(null);
  const csvInputRef = useRef<HTMLInputElement>(null);

  const applyTheme = (t: Settings["theme"]) => {
    setTheme(t === "auto" ? "system" : t);
    updateSettings({ theme: t });
  };

  const removePin = async () => {
    const ok = await confirm({
      tone: "danger",
      title: "Remove PIN lock?",
      message: "callmony will open without asking for a PIN again.",
      confirmLabel: "Remove",
    });
    if (!ok) return;
    updateSettings({ pinHash: undefined });
    toastOk("PIN lock removed");
  };

  const deleteAll = async () => {
    const first = await confirm({
      tone: "danger",
      title: "Delete ALL data?",
      message:
        "Every contact, ledger entry, list, note, reminder, bank account and file on this device will be permanently deleted.",
      confirmLabel: "Delete everything",
    });
    if (!first) return;
    const second = await confirm({
      tone: "danger",
      title: "Really delete everything?",
      message:
        "This is the point of no return. If you might want any of it back, export a backup first.",
      confirmLabel: "Yes, wipe my data",
    });
    if (!second) return;
    setWiping(true);
    await wipeAll();
    toastOk("All data deleted");
    setTimeout(() => window.location.reload(), 800);
  };

  const lastBackupAt = settings.lastBackupAt;
  const backupStale =
    !lastBackupAt || Date.now() - lastBackupAt > 30 * 24 * 60 * 60 * 1000;
  const filesTotal = attachments.reduce((s, a) => s + a.size, 0);

  const activeLang = LANGUAGES.find((l) => l.code === settings.language) ?? LANGUAGES[0];

  const pickLang = (code: string) => {
    updateSettings({ language: code as Settings["language"] });
    const lang = LANGUAGES.find((l) => l.code === code);
    toastOk(`Language set to ${lang?.native ?? code}`);
    setLangSheet(false);
  };

  return (
    <Sheet title="Settings" onBack={pop}>
      <div className="space-y-6 px-4 pb-14 pt-4">
        {/* Appearance */}
        <section aria-label={t("st.appearance")}>
          <SectionLabel>{t("st.appearance")}</SectionLabel>
          <SectionCard className="space-y-4">
            <div>
              <p className="mb-2 text-[13px] font-semibold text-ink">{t("st.theme")}</p>
              <Segmented
                options={[
                  { value: "light", label: t("st.light") },
                  { value: "dark", label: t("st.dark") },
                  { value: "auto", label: t("st.auto") },
                ]}
                value={settings.theme}
                onChange={applyTheme}
              />
            </div>
            <ActionRow
              icon={<Languages size={18} />}
              label={t("st.language")}
              hint={activeLang.native}
              onClick={() => setLangSheet(true)}
            />
            <ToggleRow
              icon={<EyeOff size={17} />}
              label={t("st.hideBalances")}
              hint="Blur money amounts everywhere until you turn this off."
              checked={settings.hideBalances}
              onToggle={() => updateSettings({ hideBalances: !settings.hideBalances })}
            />
          </SectionCard>
        </section>

        {/* Region */}
        <section aria-label="Region">
          <SectionLabel>Region</SectionLabel>
          <SectionCard className="space-y-4">
            <Field label="Currency">
              <select
                className={inputCls}
                value={settings.currency}
                onChange={(e) => updateSettings({ currency: e.target.value })}
                aria-label="Currency"
              >
                {CURRENCIES.map((c) => (
                  <option key={c.code} value={c.code}>
                    {c.label}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Default country code" hint="Used when adding phone numbers">
              <select
                className={inputCls}
                value={settings.countryCode}
                onChange={(e) => updateSettings({ countryCode: e.target.value })}
                aria-label="Default country code"
              >
                {COUNTRIES.map((c) => (
                  <option key={c.code} value={c.code}>
                    {c.label}
                  </option>
                ))}
              </select>
            </Field>
          </SectionCard>
        </section>

        {/* Security */}
        <section aria-label={t("st.security")}>
          <SectionLabel>{t("st.security")}</SectionLabel>
          <SectionCard>
            {settings.pinHash ? (
              <>
                <ActionRow
                  icon={<KeyRound size={18} />}
                  label="Change PIN"
                  hint="Verify the current one, then set a new 4-digit PIN."
                  onClick={() => setPinSheet({ open: true, mode: "change" })}
                />
                <ActionRow
                  icon={<LockOpen size={18} />}
                  label="Remove PIN"
                  hint="Open callmony without asking for a PIN again."
                  danger
                  onClick={removePin}
                />
                <div className="pt-3">
                  <Field label="Auto-lock">
                    <select
                      className={inputCls}
                      value={String(settings.autolockMin)}
                      onChange={(e) => updateSettings({ autolockMin: Number(e.target.value) })}
                      aria-label="Auto-lock timing"
                    >
                      <option value="0">Immediately when the app is left</option>
                      <option value="1">After 1 minute</option>
                      <option value="5">After 5 minutes</option>
                    </select>
                  </Field>
                </div>
              </>
            ) : (
              <ActionRow
                icon={<Lock size={18} />}
                label="Set PIN"
                hint="Ask for a 4-digit PIN when callmony opens or unlocks."
                onClick={() => setPinSheet({ open: true, mode: "set" })}
              />
            )}
            <Note icon={<ShieldCheck size={13} />}>
              The PIN locks the screen — your data stays on this device, unencrypted, so a
              forgotten PIN can be safely reset. Nothing is ever lost.
            </Note>
          </SectionCard>
        </section>

        {/* Notifications */}
        <section aria-label={t("st.notifications")}>
          <SectionLabel>{t("st.notifications")}</SectionLabel>
          <SectionCard>
            <NotifStatusRow perm={notif.perm} onAsk={() => void notif.ask()} />
            <Note icon={<Info size={13} />}>
              Honest limits: browsers fully suspend apps they have closed. Reminders alert while
              callmony is open or recent in the background — anything missed appears the next time you
              open the app.
            </Note>
          </SectionCard>
        </section>

        {/* Backup & restore */}
        <section aria-label={t("st.backup")}>
          <SectionLabel>{t("st.backup")}</SectionLabel>
          <SectionCard className="space-y-1">
            <ActionRow
              icon={<Archive size={18} />}
              label="Export backup"
              hint="One JSON file with everything — including attached files."
              onClick={() => void backupExport.exportBackup()}
              busy={backupExport.busy}
            />
            <ActionRow
              icon={<Upload size={18} />}
              label="Restore from backup"
              hint="Replace everything, or merge in what's missing."
              onClick={() => restoreInputRef.current?.click()}
            />
            {backupStale ? (
              <div className="mt-2 flex items-start gap-3 rounded-2xl border border-amber/30 bg-surface p-3.5">
                <AlertTriangle size={16} className="mt-0.5 shrink-0 text-amber" />
                <div className="min-w-0 flex-1">
                  <p className="text-[12px] font-semibold text-ink">
                    {lastBackupAt
                      ? "It's been over a month since your last backup"
                      : "You've never backed up"}
                  </p>
                  <p className="mt-0.5 text-[11px] leading-4 text-mute">
                    Your data lives only on this device — keep a copy somewhere safe.
                  </p>
                </div>
                <Button
                  variant="soft"
                  size="sm"
                  onClick={() => void backupExport.exportBackup()}
                  disabled={backupExport.busy}
                >
                  {backupExport.busy ? (
                    <Loader2 size={13} className="animate-spin" />
                  ) : (
                    <Archive size={13} />
                  )}
                  Back up
                </Button>
              </div>
            ) : (
              <p className="px-1 pt-2 text-[11px] text-mute">
                Last backup · {lastBackupAt ? relTime(lastBackupAt) : "—"}
              </p>
            )}
          </SectionCard>
        </section>

        {/* Import & export contacts */}
        <section aria-label="Import and export contacts">
          <SectionLabel>Import &amp; export contacts</SectionLabel>
          <SectionCard>
            {contactsImport.canPickPhone && (
              <ActionRow
                icon={<Smartphone size={18} />}
                label="Import from phone"
                hint="Pick contacts with this phone's contact picker."
                onClick={() => void contactsImport.pickPhone()}
              />
            )}
            <ActionRow
              icon={<FileUp size={18} />}
              label="Import vCard (.vcf)"
              hint="From iOS, Android, Outlook and most apps."
              onClick={() => vcardInputRef.current?.click()}
            />
            <ActionRow
              icon={<Table size={18} />}
              label="Import CSV"
              hint="Google Contacts export format supported."
              onClick={() => csvInputRef.current?.click()}
            />
            <ActionRow
              icon={<FileDown size={18} />}
              label="Export all contacts (.vcf)"
              hint={`${contactsCount} contacts`}
              onClick={exportVCardAll}
            />
            <ActionRow
              icon={<FileSpreadsheet size={18} />}
              label="Export contacts (.csv)"
              hint="For Excel and Google Sheets."
              onClick={exportContactsCsvAll}
            />
            <ActionRow
              icon={<Receipt size={18} />}
              label="Export transactions (.csv)"
              hint={`${entriesCount} ledger entries`}
              onClick={exportEntriesCsvAll}
            />
          </SectionCard>
        </section>

        {/* Email sync (honest) */}
        <section aria-label="Email sync">
          <SectionLabel>Email sync</SectionLabel>
          <SectionCard className="space-y-3">
            <div className="flex items-start gap-3 pt-1">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[13px] bg-brandsoft text-brand">
                <Mail size={18} />
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-[13.5px] font-semibold text-ink">Round-trip, no accounts</p>
                <p className="mt-0.5 text-[11px] leading-4 text-mute">
                  callmony never connects to Gmail or Outlook. Instead: export vCard/CSV → import
                  into your email contacts → export back anytime → import here. Reminder emails
                  can be sent from any contact or list.
                </p>
              </div>
            </div>
            <Button
              variant="surface"
              size="md"
              className="w-full"
              onClick={() => void backupExport.shareByEmail()}
              disabled={backupExport.sharing}
            >
              {backupExport.sharing ? (
                <Loader2 size={14} className="animate-spin" />
              ) : (
                <Share2 size={14} />
              )}
              Share backup by email
            </Button>
          </SectionCard>
        </section>

        {/* Storage */}
        <section aria-label={t("st.storage")}>
          <SectionLabel>{t("st.storage")}</SectionLabel>
          <SectionCard>
            <ActionRow
              icon={<HardDrive size={18} />}
              label={t("st.storage")}
              hint={`${attachments.length} files · ${bytesStr(filesTotal)}`}
              onClick={() => openOverlay({ type: "storage" })}
            />
          </SectionCard>
        </section>

        {/* Install */}
        <section aria-label="Install">
          <SectionLabel>Install</SectionLabel>
          <SectionCard>
            {standalone ? (
              <ActionRow
                icon={<CheckCircle2 size={18} />}
                label="Installed on this device"
                hint="callmony is running as an installed app."
              />
            ) : canInstall ? (
              <ActionRow
                icon={<Download size={18} />}
                label="Install app"
                hint="Add callmony to your home screen — it works offline."
                onClick={() => void promptInstall()}
              />
            ) : isIOS ? (
              <div className="flex items-start gap-3 py-3">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[13px] bg-brandsoft text-brand">
                  <Smartphone size={18} />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="text-[13.5px] font-semibold text-ink">Install on iPhone</p>
                  <p className="mt-0.5 text-[11px] leading-4 text-mute">
                    Tap the Share button in Safari, then “Add to Home Screen”.
                  </p>
                </div>
              </div>
            ) : (
              <ActionRow
                icon={<Smartphone size={18} />}
                label="Add to home screen"
                hint="Use your browser menu → “Install app” or “Add to Home screen”."
              />
            )}
          </SectionCard>
        </section>

        {/* About */}
        <section aria-label={t("st.about")}>
          <SectionLabel>{t("st.about")}</SectionLabel>
          <SectionCard>
            <ActionRow
              icon={<Info size={18} />}
              label="About callmony"
              hint={`Version ${APP_VERSION} · offline-first`}
              onClick={() => openOverlay({ type: "about" })}
            />
          </SectionCard>
        </section>

        {/* Data — danger zone */}
        <section aria-label="Data">
          <SectionLabel>Data</SectionLabel>
          <SectionCard>
            <ActionRow
              danger
              icon={<Trash2 size={18} />}
              label="Delete all data"
              hint="Erase everything on this device and start fresh."
              onClick={() => void deleteAll()}
              busy={wiping}
            />
            <Note icon={<Info size={13} />}>
              Export a backup first if you want to keep anything. The demo data callmony started with
              can be re-seeded later by clearing this site's data in your browser.
            </Note>
          </SectionCard>
        </section>
      </div>

      {/* hidden file inputs */}
      <input
        ref={restoreInputRef}
        type="file"
        accept=".json,application/json"
        className="hidden"
        aria-hidden
        onChange={async (e) => {
          const files = Array.from(e.target.files || []);
          e.target.value = "";
          if (!files.length) return;
          try {
            const parsed = await parseBackupFile(files[0]);
            setRestoreBackup(parsed);
          } catch {
            toastErr("That doesn't look like a callmony backup file");
          }
        }}
      />
      <input
        ref={vcardInputRef}
        type="file"
        accept=".vcf,text/vcard,text/directory"
        className="hidden"
        aria-hidden
        onChange={(e) => {
          const files = Array.from(e.target.files || []);
          e.target.value = "";
          if (files.length) contactsImport.pickVCard(files);
        }}
      />
      <input
        ref={csvInputRef}
        type="file"
        accept=".csv,text/csv"
        className="hidden"
        aria-hidden
        onChange={(e) => {
          const files = Array.from(e.target.files || []);
          e.target.value = "";
          if (files.length) contactsImport.pickCsv(files);
        }}
      />

      <SetPinSheet
        open={pinSheet.open}
        mode={pinSheet.mode}
        onClose={() => setPinSheet((p) => ({ ...p, open: false }))}
      />

      {/* language picker */}
      <BottomSheet open={langSheet} onClose={() => setLangSheet(false)} title={t("st.language")}>
        <div className="space-y-1.5 pb-2">
          {LANGUAGES.map((l) => {
            const active = settings.language === l.code;
            return (
              <button
                key={l.code}
                type="button"
                onClick={() => pickLang(l.code)}
                aria-pressed={active}
                className={cx(
                  "flex h-12 w-full items-center gap-3 rounded-2xl px-3 text-left transition active:scale-[0.98]",
                  active ? "border border-brand/40 bg-brandsoft" : "hover:bg-surface2"
                )}
              >
                <span
                  className={cx(
                    "flex h-9 w-9 shrink-0 items-center justify-center rounded-[13px] text-[13px] font-bold",
                    active ? "bg-brand text-white" : "bg-surface2 text-mute"
                  )}
                >
                  {l.code.toUpperCase()}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[13.5px] font-semibold text-ink">{l.native}</span>
                  <span className="block truncate text-[11px] text-mute">{l.label}</span>
                </span>
                {active && <Check size={17} className="shrink-0 text-brand" />}
              </button>
            );
          })}
        </div>
      </BottomSheet>
      <RestoreChoiceSheet
        open={!!restoreBackup}
        backup={restoreBackup}
        onClose={() => setRestoreBackup(null)}
      />
      {contactsImport.sheet}
    </Sheet>
  );
}

/* ================================================================== */
/* Lock screen                                                          */
/* ================================================================== */

export function LockScreen() {
  const updateSettings = useApp((s) => s.updateSettings);
  const setLocked = useUI((s) => s.setLocked);
  const confirm = useConfirm();

  const [digits, setDigits] = useState("");
  const [shakeKey, setShakeKey] = useState(0);
  const [busy, setBusy] = useState(false);

  const submit = useCallback(
    async (pin: string) => {
      const ph = useApp.getState().settings.pinHash;
      if (!ph) {
        setLocked(false);
        return;
      }
      setBusy(true);
      const { hash } = await hashPin(pin, ph.salt);
      setBusy(false);
      if (hash === ph.hash) {
        vibrate(12);
        setLocked(false);
      } else {
        vibrate(60);
        setShakeKey((k) => k + 1);
        setDigits("");
        toastErr("Wrong PIN");
      }
    },
    [setLocked]
  );

  const add = (d: string) => {
    if (busy) return;
    vibrate(8);
    const next = (digits + d).slice(0, 4);
    setDigits(next);
    if (next.length === 4) void submit(next);
  };

  /* physical keyboard support (re-attaches per render for a fresh closure) */
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.ctrlKey || e.metaKey || e.altKey) return;
      if (/^[0-9]$/.test(e.key)) add(e.key);
      else if (e.key === "Backspace") setDigits((d) => d.slice(0, -1));
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });

  const forgot = async () => {
    const ok = await confirm({
      tone: "danger",
      title: "Reset PIN?",
      message:
        "Your data is safe and stays on this device. The screen lock will be removed.",
      confirmLabel: "Reset lock",
    });
    if (!ok) return;
    updateSettings({ pinHash: undefined });
    setLocked(false);
    toastOk("Screen lock removed");
  };

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.2 }}
      className="absolute inset-0 z-[85] flex flex-col bg-canvas"
      role="dialog"
      aria-label="Locked — enter your PIN"
    >
      <div className="flex flex-1 flex-col items-center justify-center gap-2 px-6">
        <div className="flex h-16 w-16 items-center justify-center rounded-[22px] bg-brand text-white shadow-floating">
          <PhoneCall size={28} strokeWidth={1.8} />
        </div>
        <p className="text-[22px] font-bold tracking-[-1px] text-ink">
          call<span className="text-brand">mony</span>
        </p>
        <p className="mt-7 text-[15px] font-semibold text-ink">
          {busy ? "Checking…" : "Enter your PIN"}
        </p>
        <div className="mt-5">
          <PinDots count={4} filled={digits.length} shakeKey={shakeKey} />
        </div>
        <div className="mt-8 w-full">
          <PinPad onDigit={add} onDelete={() => setDigits((d) => d.slice(0, -1))} disabled={busy} />
        </div>
      </div>
      <div className="flex flex-col items-center gap-3 pb-[max(28px,env(safe-area-inset-bottom))]">
        <button
          type="button"
          onClick={() => void forgot()}
          className="px-4 py-2 text-[12px] font-semibold text-brand transition active:scale-95"
        >
          Forgot PIN?
        </button>
      </div>
    </motion.div>
  );
}

/* ================================================================== */
/* Import & export overlay                                              */
/* ================================================================== */

export function ImportExportOverlay() {
  const pop = useUI((s) => s.pop);
  const contactsImport = useContactsImport();
  const contactsCount = useApp((s) => s.contacts.filter((c) => !c.deletedAt).length);
  const entriesCount = useApp((s) => s.entries.filter((e) => !e.deletedAt).length);
  const [dragOver, setDragOver] = useState(false);
  const csvInputRef = useRef<HTMLInputElement>(null);

  return (
    <Sheet title="Import & export" subtitle="Move contacts in and out of callmony" onBack={pop}>
      <div className="space-y-6 px-4 pb-14 pt-4">
        {contactsImport.canPickPhone && (
          <section aria-label="Import from this phone">
            <SectionLabel>From this phone</SectionLabel>
            <SectionCard>
              <ActionRow
                icon={<Smartphone size={18} />}
                label="Pick contacts"
                hint="Use the system contact picker — nothing is read without your choice."
                onClick={() => void contactsImport.pickPhone()}
              />
            </SectionCard>
          </section>
        )}

        <section aria-label="Import vCard">
          <SectionLabel>vCard (.vcf)</SectionLabel>
          <SectionCard className="space-y-3">
            <div
              onDragOver={(e) => {
                e.preventDefault();
                setDragOver(true);
              }}
              onDragLeave={() => setDragOver(false)}
              onDrop={(e) => {
                e.preventDefault();
                setDragOver(false);
                const files = Array.from(e.dataTransfer?.files || []);
                if (files.length) contactsImport.pickVCard(files);
              }}
              className={cx(
                "flex flex-col items-center gap-1.5 rounded-2xl border-2 border-dashed px-4 py-7 text-center transition",
                dragOver ? "border-brand bg-brandsoft" : "border-line2 bg-surface2"
              )}
              aria-label="Drop a vCard file here"
            >
              <FileUp size={22} className="text-brand" />
              <p className="text-[12.5px] font-semibold text-ink">Drop a .vcf file here</p>
              <p className="text-[10.5px] text-mute">iOS, Android, Outlook & most apps export vCard</p>
            </div>
            <FilePickButton accept=".vcf,text/vcard,text/directory" onFiles={contactsImport.pickVCard}>
              <FileUp size={14} /> Choose .vcf file
            </FilePickButton>
          </SectionCard>
        </section>

        <section aria-label="Import CSV">
          <SectionLabel>CSV</SectionLabel>
          <SectionCard>
            <ActionRow
              icon={<Table size={18} />}
              label="Import CSV file"
              hint="Google Contacts export format is supported."
              onClick={() => csvInputRef.current?.click()}
            />
            <input
              ref={csvInputRef}
              type="file"
              accept=".csv,text/csv"
              className="hidden"
              aria-hidden
              onChange={(e) => {
                const files = Array.from(e.target.files || []);
                e.target.value = "";
                if (files.length) contactsImport.pickCsv(files);
              }}
            />
          </SectionCard>
        </section>

        <section aria-label="Export">
          <SectionLabel>Export</SectionLabel>
          <SectionCard className="space-y-3">
            <p className="px-1 text-[11px] leading-5 text-mute">
              {contactsCount} contacts · {entriesCount} transactions in callmony
            </p>
            <div className="grid grid-cols-1 gap-2">
              <Button variant="soft" size="lg" onClick={exportVCardAll}>
                <FileDown size={15} /> Contacts (.vcf)
              </Button>
              <Button variant="surface" size="lg" onClick={exportContactsCsvAll}>
                <FileSpreadsheet size={15} /> Contacts (.csv)
              </Button>
              <Button variant="surface" size="lg" onClick={exportEntriesCsvAll}>
                <Receipt size={15} /> Transactions (.csv)
              </Button>
            </div>
          </SectionCard>
        </section>

        <Note icon={<Info size={13} />}>
          Duplicates are matched by phone number. “Skip” leaves existing contacts untouched;
          “Merge” adds new numbers and emails onto them.
        </Note>
      </div>
      {contactsImport.sheet}
    </Sheet>
  );
}

/* ================================================================== */
/* Backup & restore overlay                                             */
/* ================================================================== */

export function BackupOverlay() {
  const pop = useUI((s) => s.pop);
  const settings = useApp((s) => s.settings);
  const attachments = useApp((s) => s.attachments);
  const contactsCount = useApp((s) => s.contacts.filter((c) => !c.deletedAt).length);
  const entriesCount = useApp((s) => s.entries.filter((e) => !e.deletedAt).length);
  const listsCount = useApp((s) => s.lists.filter((l) => !l.deletedAt).length);
  const backupExport = useBackupExport();

  const [backup, setBackup] = useState<BackupFile | null>(null);
  const [parsing, setParsing] = useState(false);
  const restoreInputRef = useRef<HTMLInputElement>(null);

  const filesSize = attachments.reduce((s, a) => s + a.size, 0);
  const lastBackupAt = settings.lastBackupAt;
  const backupStale = !lastBackupAt || Date.now() - lastBackupAt > 30 * 24 * 60 * 60 * 1000;

  return (
    <Sheet title="Backup & restore" subtitle="One file with everything, on your terms." onBack={pop}>
      <div className="space-y-6 px-4 pb-14 pt-4">
        {/* export */}
        <section aria-label="Export backup">
          <SectionLabel>Export</SectionLabel>
          <SectionCard className="space-y-1">
            <ActionRow
              icon={<Archive size={18} />}
              label="Export backup"
              hint={
                backupExport.busy
                  ? "Building backup…"
                  : `${contactsCount} contacts · ${entriesCount} entries · ${listsCount} lists · ${attachments.length} files (${bytesStr(filesSize)})`
              }
              onClick={() => void backupExport.exportBackup()}
              busy={backupExport.busy}
            />
            <Note icon={<ShieldCheck size={13} />}>
              Includes contacts, ledger, lists, notes, reminders, bank accounts, settings and every
              attached file — as one .json you can keep anywhere.
            </Note>
            {lastBackupAt && !backupStale && (
              <p className="px-1 pt-2 text-[11px] text-mute">
                Last backup · {relTime(lastBackupAt)} ({fd(lastBackupAt)})
              </p>
            )}
          </SectionCard>
        </section>

        {/* restore */}
        <section aria-label="Restore backup">
          <SectionLabel>Restore</SectionLabel>
          <SectionCard>
            <ActionRow
              icon={<Upload size={18} />}
              label="Restore from backup"
              hint={parsing ? "Reading file…" : "Pick a callmony-backup .json file."}
              onClick={() => restoreInputRef.current?.click()}
              busy={parsing}
            />
            <input
              ref={restoreInputRef}
              type="file"
              accept=".json,application/json"
              className="hidden"
              aria-hidden
              onChange={async (e) => {
                const files = Array.from(e.target.files || []);
                e.target.value = "";
                if (!files.length) return;
                setParsing(true);
                try {
                  const parsed = await parseBackupFile(files[0]);
                  setBackup(parsed);
                } catch {
                  toastErr("Not a valid callmony backup file");
                }
                setParsing(false);
              }}
            />
            <Note icon={<Info size={13} />}>
              “Merge” adds only what's missing. “Replace” wipes this device first — export a
              fresh backup before replacing.
            </Note>
          </SectionCard>
        </section>

        {backupStale && (
          <div className="flex items-start gap-3 rounded-[22px] border border-amber/30 bg-surface p-4 shadow-key">
            <AlertTriangle size={17} className="mt-0.5 shrink-0 text-amber" />
            <div className="min-w-0 flex-1">
              <p className="text-[12.5px] font-semibold text-ink">
                {lastBackupAt
                  ? "It's been over a month since your last backup"
                  : "You've never backed up"}
              </p>
              <p className="mt-0.5 text-[11px] leading-4 text-mute">
                callmony keeps everything locally — a backup is the only way to move or recover it.
              </p>
            </div>
          </div>
        )}

        <Note icon={<AlertTriangle size={13} />}>
          Backups are only as safe as the place you keep them. Clearing this browser's site data
          deletes callmony and everything in it — and on iOS, Safari may clear unused site data
          automatically. Back up regularly and keep a copy off this device (email, cloud drive,
          USB).
        </Note>
      </div>

      <RestoreChoiceSheet open={!!backup} backup={backup} onClose={() => setBackup(null)} />
    </Sheet>
  );
}

/* ================================================================== */
/* Storage overlay                                                      */
/* ================================================================== */

export function StorageOverlay() {
  const pop = useUI((s) => s.pop);
  const confirm = useConfirm();
  const attachments = useApp((s) => s.attachments);
  const contacts = useApp((s) => s.contacts);
  const entries = useApp((s) => s.entries);
  const lists = useApp((s) => s.lists);
  const notes = useApp((s) => s.notes);
  const reminders = useApp((s) => s.reminders);
  const banks = useApp((s) => s.banks);
  const messages = useApp((s) => s.messages);
  const removeAttachment = useApp((s) => s.removeAttachment);

  const [estimate, setEstimate] = useState<{ usage: number; quota: number } | null>(null);
  const [persisted, setPersisted] = useState(false);
  const [checking, setChecking] = useState(true);
  const [cleaning, setCleaning] = useState(false);

  /* no synchronous setState inside — safe to call from an effect */
  const refresh = useCallback(async () => {
    try {
      const est = await navigator.storage?.estimate?.();
      if (est) setEstimate({ usage: est.usage ?? 0, quota: est.quota ?? 0 });
      setPersisted(!!(await navigator.storage?.persisted?.()));
    } catch {
      /* not supported */
    }
    setChecking(false);
  }, []);

  useEffect(() => {
    const t = setTimeout(() => void refresh(), 0);
    return () => clearTimeout(t);
  }, [refresh]);

  const breakdown = useMemo(() => {
    const acc: Record<"image" | "pdf" | "file", { count: number; size: number }> = {
      image: { count: 0, size: 0 },
      pdf: { count: 0, size: 0 },
      file: { count: 0, size: 0 },
    };
    attachments.forEach((a) => {
      acc[a.kind].count++;
      acc[a.kind].size += a.size;
    });
    return acc;
  }, [attachments]);

  const biggest = useMemo(
    () => [...attachments].sort((a, b) => b.size - a.size).slice(0, 10),
    [attachments]
  );

  const orphans = useMemo(() => {
    const parentState = new Map<string, boolean>(); // id → isTrashed
    const put = (id: string, trashed: boolean) => parentState.set(id, trashed);
    contacts.forEach((c) => put(c.id, !!c.deletedAt));
    entries.forEach((e) => put(e.id, !!e.deletedAt));
    lists.forEach((l) => {
      put(l.id, !!l.deletedAt);
      l.items.forEach((i) => put(i.id, !!l.deletedAt));
    });
    notes.forEach((n) => put(n.id, !!n.deletedAt));
    reminders.forEach((r) => put(r.id, !!r.deletedAt));
    banks.forEach((b) => put(b.id, !!b.deletedAt));
    messages.forEach((m) => put(m.id, false));
    const trashed: AttachmentMeta[] = [];
    const orphaned: AttachmentMeta[] = [];
    attachments.forEach((a) => {
      const state = parentState.get(a.parentId);
      if (state === undefined) orphaned.push(a);
      else if (state) trashed.push(a);
    });
    return { trashed, orphaned };
  }, [attachments, contacts, entries, lists, notes, reminders, banks, messages]);

  const removeFile = async (a: AttachmentMeta) => {
    const ok = await confirm({
      tone: "danger",
      title: "Delete this file?",
      message: `${a.name} (${bytesStr(a.size)}) will be permanently removed.`,
      confirmLabel: "Delete",
    });
    if (!ok) return;
    await removeAttachment(a.id);
    toastOk("File removed");
  };

  const cleanFiles = async (list: AttachmentMeta[], label: string) => {
    if (!list.length) {
      toastOk("Nothing to clean");
      return;
    }
    const total = list.reduce((s, a) => s + a.size, 0);
    const ok = await confirm({
      tone: "danger",
      title: `Remove ${list.length} ${label}?`,
      message: `${bytesStr(total)} of storage will be freed. These files are deleted permanently — the records themselves stay.`,
      confirmLabel: "Remove",
    });
    if (!ok) return;
    setCleaning(true);
    for (const a of list) await removeAttachment(a.id);
    setCleaning(false);
    toastOk(`${list.length} files removed · ${bytesStr(total)} freed`);
  };

  const makePersistent = async () => {
    const ok = await requestPersistence();
    setPersisted(ok);
    toastOk(
      ok
        ? "Storage is now persistent — the browser won't evict callmony's data"
        : "Browser declined — data stays best-effort only"
    );
  };

  const kindRows = [
    { key: "image" as const, label: "Images", icon: <Images size={16} /> },
    { key: "pdf" as const, label: "PDFs", icon: <FileText size={16} /> },
    { key: "file" as const, label: "Other files", icon: <Package size={16} /> },
  ];

  const totalFiles = attachments.length;
  const totalSize = attachments.reduce((s, a) => s + a.size, 0);

  return (
    <Sheet title="Storage" subtitle="Everything callmony keeps on this device" onBack={pop}>
      <div className="space-y-6 px-4 pb-14 pt-4">
        {/* browser storage */}
        <section aria-label="Browser storage">
          <SectionLabel
            right={
              <button
                type="button"
                onClick={() => {
                  setChecking(true);
                  void refresh();
                }}
                aria-label="Refresh storage info"
                className="flex h-8 w-8 items-center justify-center rounded-full text-mute transition hover:bg-surface2 hover:text-brand"
              >
                <RefreshCw size={14} className={checking ? "animate-spin" : ""} />
              </button>
            }
          >
            Browser storage
          </SectionLabel>
          <SectionCard className="space-y-3">
            {estimate ? (
              <>
                <div className="flex items-end justify-between">
                  <p className="text-[13px] font-semibold text-ink">
                    {bytesStr(estimate.usage)} used
                  </p>
                  <p className="text-[11px] text-mute">
                    {estimate.quota ? `of ${bytesStr(estimate.quota)} allowed` : ""}
                  </p>
                </div>
                <ProgressBar
                  value={estimate.quota ? (estimate.usage / estimate.quota) * 100 : 0}
                />
              </>
            ) : (
              <p className="text-[11px] text-mute">Measuring…</p>
            )}
            <div className="flex flex-wrap items-center gap-2 pt-1">
              <span
                className={cx(
                  "inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[10px] font-bold",
                  persisted ? "bg-brandsoft text-pos" : "bg-surface2 text-mute"
                )}
              >
                {persisted ? <CheckCircle2 size={11} /> : <AlertTriangle size={11} />}
                {persisted ? "Persistent" : "Best-effort"}
              </span>
              {!persisted && (
                <Button variant="soft" size="sm" onClick={() => void makePersistent()}>
                  Make storage persistent
                </Button>
              )}
            </div>
            <Note icon={<ShieldCheck size={13} />}>
              Persistent storage asks the browser not to evict callmony's data under space pressure.
              It's granted automatically most of the time once you install the app.
            </Note>
          </SectionCard>
        </section>

        {/* breakdown */}
        <section aria-label="Files breakdown">
          <SectionLabel>Files</SectionLabel>
          <SectionCard>
            <div className="divide-y divide-line">
              {kindRows.map((k) => (
                <div key={k.key} className="flex items-center gap-3 py-3">
                  <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[12px] bg-brandsoft text-brand">
                    {k.icon}
                  </span>
                  <p className="flex-1 text-[13px] font-semibold text-ink">{k.label}</p>
                  <p className="text-[11px] text-mute">
                    {breakdown[k.key].count} · {bytesStr(breakdown[k.key].size)}
                  </p>
                </div>
              ))}
            </div>
            <div className="flex items-center justify-between pt-3">
              <p className="text-[12px] font-bold text-ink">Total</p>
              <p className="text-[12px] font-bold text-brand">
                {totalFiles} files · {bytesStr(totalSize)}
              </p>
            </div>
          </SectionCard>
        </section>

        {/* largest files */}
        <section aria-label="Largest files">
          <SectionLabel>Largest files</SectionLabel>
          {biggest.length ? (
            <SectionCard className="divide-y divide-line">
              {biggest.map((a) => (
                <div key={a.id} className="flex items-center gap-3 py-2.5">
                  {a.kind === "image" && a.thumb ? (
                    <img
                      src={a.thumb}
                      alt={a.name}
                      className="h-10 w-10 shrink-0 rounded-[12px] object-cover"
                    />
                  ) : (
                    <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[12px] bg-surface2 text-mute">
                      {a.kind === "pdf" ? (
                        <FileText size={17} className="text-neg" />
                      ) : (
                        <FileIcon size={16} />
                      )}
                    </span>
                  )}
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-[12.5px] font-semibold text-ink">{a.name}</p>
                    <p className="text-[10.5px] text-mute">
                      {bytesStr(a.size)} · {a.kind}
                    </p>
                  </div>
                  <IconButton
                    label={`Delete ${a.name}`}
                    variant="danger"
                    onClick={() => void removeFile(a)}
                  >
                    <Trash2 size={15} />
                  </IconButton>
                </div>
              ))}
            </SectionCard>
          ) : (
            <EmptyState
              icon={<Paperclip size={22} />}
              title="No files yet"
              hint="Attach photos, receipts or PDFs to any contact, entry, list, note or reminder."
            />
          )}
        </section>

        {/* cleanup */}
        <section aria-label="Cleanup">
          <SectionLabel>Cleanup</SectionLabel>
          <SectionCard>
            <ActionRow
              icon={<Trash2 size={18} />}
              label="Empty trash files"
              hint={
                orphans.trashed.length
                  ? `${orphans.trashed.length} files belong to trashed items · ${bytesStr(
                      orphans.trashed.reduce((s, a) => s + a.size, 0)
                    )}`
                  : "No files belonging to trashed items"
              }
              onClick={() => void cleanFiles(orphans.trashed, "trash files")}
              busy={cleaning}
            />
            <ActionRow
              icon={<Unlink size={18} />}
              label="Remove orphaned files"
              hint={
                orphans.orphaned.length
                  ? `${orphans.orphaned.length} files have no owner record · ${bytesStr(
                      orphans.orphaned.reduce((s, a) => s + a.size, 0)
                    )}`
                  : "No orphaned files"
              }
              onClick={() => void cleanFiles(orphans.orphaned, "orphaned files")}
              busy={cleaning}
            />
            <Note icon={<Info size={13} />}>
              Trash files belong to items sitting in Trash; orphaned files lost their owner record
              entirely (e.g. after restores or merges). Cleaning never touches live records.
            </Note>
          </SectionCard>
        </section>
      </div>
    </Sheet>
  );
}

/* ================================================================== */
/* About overlay                                                        */
/* ================================================================== */

const ABOUT_FEATURES: { icon: ReactNode; label: string; text: string }[] = [
  { icon: <Users size={16} />, label: "Contacts & calls", text: "Unlimited phones, emails, links, tags" },
  { icon: <Wallet size={16} />, label: "Money ledger", text: "Who owes whom, payments, statements" },
  { icon: <ListChecks size={16} />, label: "Calculating lists", text: "Quotes, checklists & bills with tax" },
  { icon: <StickyNote size={16} />, label: "Notes", text: "Pinned, tagged, linkable to anything" },
  { icon: <Bell size={16} />, label: "Reminders", text: "One-off & repeating, with alarms" },
  { icon: <Landmark size={16} />, label: "Bank accounts", text: "Masked numbers, UPI, IBAN, SWIFT" },
  { icon: <Paperclip size={16} />, label: "Files", text: "Receipts & photos on any record" },
  { icon: <CloudOff size={16} />, label: "Offline", text: "Works with zero connection" },
];

export function AboutOverlay() {
  const pop = useUI((s) => s.pop);
  const { canInstall, promptInstall, isIOS } = useInstallPrompt();
  const notif = useNotifPermission();
  const [standalone] = useState(() =>
    typeof window !== "undefined" && window.matchMedia
      ? window.matchMedia("(display-mode: standalone)").matches
      : false
  );

  return (
    <Sheet title="About callmony" onBack={pop}>
      <div className="space-y-6 px-4 pb-14 pt-6">
        <div className="flex flex-col items-center gap-3 text-center">
          <div className="flex h-[72px] w-[72px] items-center justify-center rounded-[24px] bg-brand text-white shadow-floating">
            <PhoneCall size={32} strokeWidth={1.7} />
          </div>
          <div>
            <p className="text-[24px] font-bold tracking-[-1px] text-ink">
              call<span className="text-brand">mony</span>
            </p>
            <p className="mt-0.5 text-[12px] text-mute">
              Talk. Track. Settle up.
            </p>
            <p className="mt-1 text-[11px] font-semibold text-mute">
              Contacts, Calls &amp; Ledger · v{APP_VERSION}
            </p>
          </div>
          <p className="rounded-full bg-brandsoft px-3.5 py-1.5 text-[11px] font-bold text-brand">
            100% offline · your data never leaves this device
          </p>
        </div>

        <section aria-label="What's inside">
          <SectionLabel>What&rsquo;s inside</SectionLabel>
          <div className="grid grid-cols-2 gap-2.5">
            {ABOUT_FEATURES.map((f) => (
              <div
                key={f.label}
                className="rounded-[18px] border border-line bg-surface p-3.5 shadow-key"
              >
                <span className="flex h-8 w-8 items-center justify-center rounded-[11px] bg-brandsoft text-brand">
                  {f.icon}
                </span>
                <p className="mt-2 text-[12px] font-bold text-ink">{f.label}</p>
                <p className="mt-0.5 text-[10.5px] leading-4 text-mute">{f.text}</p>
              </div>
            ))}
          </div>
        </section>

        <section aria-label="Install on your device">
          <SectionLabel>Install on your device</SectionLabel>
          <SectionCard className="space-y-3">
            {standalone ? (
              <ActionRow
                icon={<CheckCircle2 size={18} />}
                label="Already installed"
                hint="callmony is running as an installed app."
              />
            ) : canInstall ? (
              <>
                <ActionRow
                  icon={<Download size={18} />}
                  label="Install app"
                  hint="Home-screen icon, full-screen, works offline."
                  onClick={() => void promptInstall()}
                />
              </>
            ) : isIOS ? (
              <div className="flex items-start gap-3 py-2">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[13px] bg-brandsoft text-brand">
                  <Smartphone size={18} />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="text-[13.5px] font-semibold text-ink">On iPhone &amp; iPad</p>
                  <p className="mt-0.5 text-[11px] leading-4 text-mute">
                    Open callmony in Safari → tap the Share button → “Add to Home Screen”.
                  </p>
                </div>
              </div>
            ) : (
              <div className="flex items-start gap-3 py-2">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[13px] bg-brandsoft text-brand">
                  <Download size={18} />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="text-[13.5px] font-semibold text-ink">On Android &amp; desktop</p>
                  <p className="mt-0.5 text-[11px] leading-4 text-mute">
                    Open your browser menu → “Install app” / “Add to Home screen”.
                  </p>
                </div>
              </div>
            )}
          </SectionCard>
        </section>

        <section aria-label="Notifications">
          <SectionLabel>Notifications</SectionLabel>
          <SectionCard>
            <NotifStatusRow perm={notif.perm} onAsk={() => void notif.ask()} />
          </SectionCard>
        </section>

        <section aria-label="Privacy">
          <SectionLabel>Privacy</SectionLabel>
          <SectionCard>
            <Note icon={<ShieldCheck size={13} />}>
              No accounts. No servers. No analytics, no trackers, no cloud. Everything you create
              lives in this browser, on this device — export a backup any time to move or keep it
              safe.
            </Note>
          </SectionCard>
        </section>

        <p className="pb-2 text-center text-[10.5px] text-mute">
          Crafted with care · callmony v{APP_VERSION} · PWA ready
        </p>
      </div>
    </Sheet>
  );
}
