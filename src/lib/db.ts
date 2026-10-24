/* AsyncStorage-backed persistence — one key per collection, JSON serialized.
   Simple, robust, and fully offline (no servers, no sync). */

import AsyncStorage from "@react-native-async-storage/async-storage";
import type {
  CalcList,
  CallLog,
  ContactX,
  LedgerEntry,
  LedgerGroup,
  MessageLog,
  Note,
  Reminder,
  BankAccount,
  Settings,
} from "./types";

const K = {
  contactX: "callmony.contactX",
  entries: "callmony.entries",
  groups: "callmony.groups",
  lists: "callmony.lists",
  notes: "callmony.notes",
  reminders: "callmony.reminders",
  banks: "callmony.banks",
  recents: "callmony.recents",
  messages: "callmony.messages",
  settings: "callmony.settings",
  hydrated: "callmony.hydrated",
} as const;

export const DEFAULT_SETTINGS: Settings = {
  theme: "auto",
  currency: "ETB",
  countryCode: "ET",
  onboarded: false,
  autolockMin: 1,
  hideBalances: false,
  notifAsked: false,
  blockedNumbers: [],
};

const read = async <T>(key: string, fallback: T): Promise<T> => {
  try {
    const raw = await AsyncStorage.getItem(key);
    if (!raw) {
      /* v1.6.3 — primary miss: try the mirror before giving up (a corrupted
         or half-lost primary write must never read as "empty collection") */
      const mirrored = await AsyncStorage.getItem(mirrorKey(key));
      if (mirrored) {
        try {
          return JSON.parse(mirrored) as T;
        } catch {
          /* both bad → fallback */
        }
      }
      return fallback;
    }
    try {
      return JSON.parse(raw) as T;
    } catch {
      /* v1.6.3 — primary parse failure (interrupted write / bit rot):
         the mirror key holds the last good copy of this collection */
      const mirrored = await AsyncStorage.getItem(mirrorKey(key));
      if (mirrored) {
        try {
          return JSON.parse(mirrored) as T;
        } catch {
          /* mirror bad too → fallback */
        }
      }
      return fallback;
    }
  } catch {
    return fallback;
  }
};

/* v1.6.3 — every collection is stored TWICE: `callmony.entries` (primary)
   and `callmony.mirror.entries` (the safety copy). AsyncStorage itself
   commits atomically per key (SQLite transaction), so a torn write can
   only ever damage ONE of the two — reads fall back to the healthy copy.
   The mirror also rides along in Android Auto Backup + local snapshots. */
const mirrorKey = (key: string): string => key.replace(/^callmony\./, "callmony.mirror.");

/* v1.5.1 — shape-sanitized read: stored JSON with the WRONG shape (e.g.
   after an interrupted write or an app update) must never poison the store
   and crash the app at open. Non-arrays / non-objects fall back to empty. */
const readArr = async <T>(key: string): Promise<T[]> => {
  const v = await read<T[] | unknown>(key, []);
  return Array.isArray(v) ? (v as T[]) : [];
};

const readObj = async <T extends object>(key: string, fallback: T): Promise<T> => {
  const v = await read<T | unknown>(key, fallback);
  if (v && typeof v === "object" && !Array.isArray(v)) return v as T;
  return fallback;
};

const write = async (key: string, value: unknown): Promise<void> => {
  try {
    const json = JSON.stringify(value);
    /* primary first (reads hit it), then the mirror copy (v1.6.3 safety).
       A failure writing the mirror is logged-by-silence: the primary is
       still authoritative; a failure writing the PRIMARY leaves the
       previous good value in place (atomic per key). */
    await AsyncStorage.setItem(key, json);
    AsyncStorage.setItem(mirrorKey(key), json).catch(() => undefined);
  } catch {
    /* storage full / unavailable — app keeps working in memory; the
       previous on-disk value (and its mirror) remain intact */
  }
};

export interface PersistedData {
  contactX: ContactX[];
  entries: LedgerEntry[];
  groups: LedgerGroup[];
  lists: CalcList[];
  notes: Note[];
  reminders: Reminder[];
  banks: BankAccount[];
  recents: CallLog[];
  messages: MessageLog[];
  settings: Settings;
}

export const loadAll = async (): Promise<PersistedData> => {
  const [contactX, entries, groups, lists, notes, reminders, banks, recents, messages, settings] =
    await Promise.all([
      readArr<ContactX>(K.contactX),
      readArr<LedgerEntry>(K.entries),
      readArr<LedgerGroup>(K.groups),
      readArr<CalcList>(K.lists),
      readArr<Note>(K.notes),
      readArr<Reminder>(K.reminders),
      readArr<BankAccount>(K.banks),
      readArr<CallLog>(K.recents),
      readArr<MessageLog>(K.messages),
      readObj<Partial<Settings>>(K.settings, {}),
    ]);
  return {
    contactX,
    entries,
    groups,
    lists,
    notes,
    reminders,
    banks,
    recents,
    messages,
    settings: { ...DEFAULT_SETTINGS, ...settings },
  };
};

export const saveContactX = (contactX: ContactX[]) => write(K.contactX, contactX);
export const saveEntries = (entries: LedgerEntry[]) => write(K.entries, entries);
export const saveGroups = (groups: LedgerGroup[]) => write(K.groups, groups);
export const saveLists = (lists: CalcList[]) => write(K.lists, lists);
export const saveNotes = (notes: Note[]) => write(K.notes, notes);
export const saveReminders = (reminders: Reminder[]) => write(K.reminders, reminders);
export const saveBanks = (banks: BankAccount[]) => write(K.banks, banks);
export const saveRecents = (recents: CallLog[]) => write(K.recents, recents);
export const saveMessages = (messages: MessageLog[]) => write(K.messages, messages);
/* v1.6.9 — the PIN hash NEVER persists here anymore: settings ride Android
   Auto Backup, and a backup-restored pin was resurrecting pins the user had
   removed before uninstalling. The pin now lives only in the OS no_backup
   directory (lib/pin-file.ts) — app data restores, the pin never does. */
export const saveSettings = (settings: Settings) => {
  const persistable = { ...settings };
  delete persistable.pinHash;
  return write(K.settings, persistable);
};

export const wipeStorage = async (): Promise<void> => {
  try {
    await AsyncStorage.multiRemove([...Object.values(K), ...Object.values(K).map(mirrorKey)]);
  } catch {
    /* noop */
  }
};

export const storageSize = async (): Promise<number> => {
  try {
    const keys = await AsyncStorage.getAllKeys();
    let total = 0;
    for (const k of keys) {
      const v = await AsyncStorage.getItem(k);
      total += (v?.length || 0) + k.length;
    }
    return total;
  } catch {
    return 0;
  }
};
