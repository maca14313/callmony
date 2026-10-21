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
  autolockMin: 1,
  hideBalances: false,
  notifAsked: false,
  blockedNumbers: [],
};

const read = async <T>(key: string, fallback: T): Promise<T> => {
  try {
    const raw = await AsyncStorage.getItem(key);
    if (!raw) return fallback;
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
};

const write = async (key: string, value: unknown): Promise<void> => {
  try {
    await AsyncStorage.setItem(key, JSON.stringify(value));
  } catch {
    /* storage full / unavailable — app keeps working in memory */
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
      read<ContactX[]>(K.contactX, []),
      read<LedgerEntry[]>(K.entries, []),
      read<LedgerGroup[]>(K.groups, []),
      read<CalcList[]>(K.lists, []),
      read<Note[]>(K.notes, []),
      read<Reminder[]>(K.reminders, []),
      read<BankAccount[]>(K.banks, []),
      read<CallLog[]>(K.recents, []),
      read<MessageLog[]>(K.messages, []),
      read<Partial<Settings>>(K.settings, {}),
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
export const saveSettings = (settings: Settings) => write(K.settings, settings);

export const wipeStorage = async (): Promise<void> => {
  try {
    await AsyncStorage.multiRemove(Object.values(K));
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
