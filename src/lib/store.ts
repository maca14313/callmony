/* callmony — app data store (zustand + AsyncStorage persistence) */

import { create } from "zustand";
import { Platform } from "react-native";
import {
  DEFAULT_SETTINGS,
  loadAll,
  saveBanks,
  saveContactX,
  saveEntries,
  saveGroups,
  saveLists,
  saveMessages,
  saveNotes,
  saveRecents,
  saveReminders,
  saveSettings,
  wipeStorage,
} from "./db";
import { entryRemaining, itemPaid, lineTotal, nextOccurrence, listTotals } from "./calc";
import { normalizePhone, todayStr, uid } from "./utils";
import { saveCachedContacts, fetchAllContacts, checkContactsPermission, requestContactsPermission } from "../native/deviceContacts";
import { isSafeMode } from "../boot/crash-firewall";
import type {
  BankAccount,
  CalcList,
  CallLog,
  ContactX,
  DeviceContact,
  ID,
  LedgerEntry,
  LedgerGroup,
  ListItem,
  MessageLog,
  Note,
  OwnerId,
  Payment,
  Reminder,
  Settings,
  SocialLink,
} from "./types";
import { scheduleReminderNotification, cancelReminderNotification } from "../native/notify";
import { fetchSystemCalls, isPermissionDeniedError, loadCachedCalls, saveCachedCalls, type DeviceCall } from "../native/callLog";

export type EntityKind = "entry" | "list" | "note" | "reminder" | "bank" | "group";

interface AppState {
  hydrated: boolean;
  deviceContacts: DeviceContact[];
  /* live device data — the real system call log (Android) */
  systemCalls: DeviceCall[];
  callLogPerm: "unknown" | "granted" | "denied";
  loadSystemCalls: () => Promise<void>;
  /* v1.6.7 — contacts access state: "denied" lets Recents / Search offer
     the one-tap fix (the address book silently empty was the root cause of
     "search not working" + "every card shows as unsaved" when the Contacts
     tab got hidden — nothing ever asked for READ_CONTACTS anymore). */
  contactsPerm: "unknown" | "granted" | "denied";
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
  lastUndo: { label: string; apply: () => void } | null;

  hydrate: () => Promise<void>;
  pushUndo: (label: string, apply: () => void) => void;
  undo: () => void;

  /* device contacts */
  setDeviceContacts: (list: DeviceContact[]) => void;
  /** when the deviceContacts array was last synced with the OS (ms). */
  contactsLoadedAt: number;
  /** re-query the OS address book right now (after an in-app contact edit). */
  refreshDeviceContacts: () => Promise<void>;
  toggleFavorite: (recordID: string) => void;
  setContactSpeedDial: (recordID: string, key: string | null) => void;
  addSocial: (recordID: string, app: SocialLink["app"], handle: string) => void;
  removeSocial: (recordID: string, socialId: ID) => void;
  setPrimaryPhone: (recordID: string, number: string) => void;

  /* ledger */
  saveEntry: (e: Partial<LedgerEntry> & { id?: ID; ownerId: OwnerId }) => void;
  deleteEntry: (id: ID) => void;
  restoreEntry: (id: ID) => void;
  purgeEntry: (id: ID) => void;
  addPayment: (entryId: ID, p: Partial<Payment>) => void;
  removePayment: (entryId: ID, paymentId: ID) => void;
  settleUp: (ownerId: OwnerId) => void;

  /* groups */
  saveGroup: (g: Partial<LedgerGroup> & { id?: ID; ownerId: OwnerId }) => void;
  deleteGroup: (id: ID) => void;
  restoreGroup: (id: ID) => void;
  purgeGroup: (id: ID) => void;

  /* lists */
  saveList: (l: Partial<CalcList> & { id?: ID; ownerId: OwnerId }) => void;
  deleteList: (id: ID) => void;
  restoreList: (id: ID) => void;
  purgeList: (id: ID) => void;
  duplicateList: (id: ID, opts?: { asTemplate?: boolean; fresh?: boolean }) => ID | null;
  addListItem: (listId: ID, item: Partial<ListItem>) => void;
  updateListItem: (listId: ID, itemId: ID, patch: Partial<ListItem>) => void;
  removeListItem: (listId: ID, itemId: ID) => void;
  addItemPayment: (listId: ID, itemId: ID, p: Partial<Payment>) => void;
  removeItemPayment: (listId: ID, itemId: ID, paymentId: ID) => void;
  toggleItemChecked: (listId: ID, itemId: ID) => void;
  markAllPaid: (listId: ID) => void;
  clearPaid: (listId: ID) => void;

  /* notes */
  saveNote: (n: Partial<Note> & { id?: ID; ownerId: OwnerId }) => void;
  deleteNote: (id: ID) => void;
  restoreNote: (id: ID) => void;
  purgeNote: (id: ID) => void;

  /* reminders */
  saveReminder: (r: Partial<Reminder> & { id?: ID; ownerId: OwnerId }) => void;
  deleteReminder: (id: ID) => void;
  restoreReminder: (id: ID) => void;
  purgeReminder: (id: ID) => void;
  completeReminder: (id: ID) => void;
  reopenReminder: (id: ID) => void;
  snoozeReminder: (id: ID, until: number) => void;
  fireReminder: (id: ID) => void;

  /* banks */
  saveBank: (b: Partial<BankAccount> & { id?: ID; ownerId: OwnerId }) => void;
  deleteBank: (id: ID) => void;
  restoreBank: (id: ID) => void;
  purgeBank: (id: ID) => void;

  /* calls / messages */
  addCall: (c: Partial<CallLog>) => void;
  deleteRecent: (id: ID) => void;
  clearRecents: () => void;
  logMessage: (m: Partial<MessageLog> & { ownerId: OwnerId }) => void;

  /* settings / data */
  updateSettings: (patch: Partial<Settings>) => void;
  addCustomCategory: (name: string) => void;
  removeCustomCategory: (name: string) => void;
  toggleBlocked: (phone: string) => void;
  setSpeedDial: (key: string, recordID: string | null) => void;
  moveEntity: (kind: EntityKind, id: ID, ownerId: OwnerId) => void;
  restoreBackup: (data: {
    contactX?: ContactX[];
    entries?: LedgerEntry[];
    groups?: LedgerGroup[];
    lists?: CalcList[];
    notes?: Note[];
    reminders?: Reminder[];
    banks?: BankAccount[];
    recents?: CallLog[];
    messages?: MessageLog[];
    settings?: Partial<Settings>;
  }, mode: "merge" | "replace") => void;
  wipeAll: () => Promise<void>;
}

const now = () => Date.now();

export const useApp = create<AppState>((set, get) => ({
  hydrated: false,
  deviceContacts: [],
  contactsLoadedAt: 0,
  contactsPerm: "unknown",
  systemCalls: [],
  callLogPerm: "unknown",

  /* Load the device's real call log into the store (Android). Reads the
     same provider the built-in dialer uses; safe to call repeatedly.
     Every successful read also refreshes the disk snapshot so the next
     cold start renders Recents INSTANTLY (no spinner, no permission wait). */
  loadSystemCalls: async () => {
    if (Platform.OS !== "android") {
      set({ callLogPerm: "granted" });
      return;
    }
    try {
      const calls = await fetchSystemCalls(400);
      set({ systemCalls: calls, callLogPerm: "granted" });
      saveCachedCalls(calls);
    } catch (err) {
      set({
        systemCalls: [],
        callLogPerm: isPermissionDeniedError(err) ? "denied" : "granted",
      });
    }
  },

  contactX: [],
  entries: [],
  groups: [],
  lists: [],
  notes: [],
  reminders: [],
  banks: [],
  recents: [],
  messages: [],
  settings: DEFAULT_SETTINGS,
  lastUndo: null,

  hydrate: async () => {
    const data = await loadAll();
    set({
      ...data,
      hydrated: true,
    });
    /* cached call-log snapshot → Recents renders instantly on cold start;
       the real refresh happens in the background right after */
    try {
      const cachedCalls = await loadCachedCalls();
      if (cachedCalls.length) {
        set({ systemCalls: cachedCalls, callLogPerm: "granted" });
      }
    } catch {
      /* snapshot is optional */
    }
    // (re)schedule reminder notifications after boot — native calls stay
    // PAUSED in safe mode so a crashing boot can always be recovered
    if (!isSafeMode()) {
      data.reminders
        .filter((r) => !r.deletedAt && !r.done)
        .forEach((r) => scheduleReminderNotification(r));
    }
  },

  pushUndo: (label, apply) => set({ lastUndo: { label, apply } }),
  undo: () => {
    const u = get().lastUndo;
    if (!u) return;
    u.apply();
    set({ lastUndo: null });
  },

  /* ---------------- device contacts ---------------- */

  /* every set also refreshes the disk snapshot (debounced inside) so the
     next cold start opens with contacts instantly, no bridge round-trip */
  setDeviceContacts: (list) => {
    set({ deviceContacts: list, contactsLoadedAt: Date.now() });
    saveCachedContacts(list);
  },

  /* immediate OS re-query — used after contact create/edit sheets so the
     change is visible the moment the user returns (no 5-min TTL wait).
     v1.6.7 — ROOT-CAUSE fix: the Contacts TAB used to be the only place
     READ_CONTACTS was ever requested; hiding it left a fresh install (or a
     permission reset) with an EMPTY address book forever — no search hits,
     no keypad matches, every Recents card "unsaved". The permission dialog
     now fires here (undetermined path) AND at boot (requestMultiple). */
  refreshDeviceContacts: async () => {
    try {
      let status = await checkContactsPermission();
      if (status === "undetermined") {
        status = await requestContactsPermission();
      }
      if (status !== "authorized") {
        set({ contactsPerm: "denied" });
        return;
      }
      set({ contactsPerm: "granted" });
      const list = await fetchAllContacts();
      if (list.length) {
        set({ deviceContacts: list, contactsLoadedAt: Date.now() });
        saveCachedContacts(list);
      }
    } catch {
      /* leave the current list in place */
    }
  },

  toggleFavorite: (recordID) => {
    const { contactX } = get();
    const existing = contactX.find((c) => c.recordID === recordID);
    const next = existing
      ? contactX.map((c) =>
          c.recordID === recordID ? { ...c, favorite: !c.favorite, updatedAt: now() } : c
        )
      : [
          ...contactX,
          { recordID, favorite: true, tags: [], createdAt: now(), updatedAt: now() } as ContactX,
        ];
    set({ contactX: next });
    saveContactX(next);
  },

  setContactSpeedDial: (recordID, key) => {
    const { contactX } = get();
    const existing = contactX.find((c) => c.recordID === recordID);
    const next = existing
      ? contactX.map((c) => (c.recordID === recordID ? { ...c, speedDial: key ?? undefined, updatedAt: now() } : c))
      : [
          ...contactX,
          { recordID, favorite: false, tags: [], speedDial: key ?? undefined, createdAt: now(), updatedAt: now() } as ContactX,
        ];
    set({ contactX: next });
    saveContactX(next);
  },

  /* socials — as many handles as the user wants, stored app-side */
  addSocial: (recordID, app, handle) => {
    const { contactX } = get();
    const entry: SocialLink = { id: `soc-${Date.now()}-${Math.floor(Math.random() * 1e4)}`, app, handle: handle.trim() };
    const existing = contactX.find((c) => c.recordID === recordID);
    const next = existing
      ? contactX.map((c) =>
          c.recordID === recordID ? { ...c, socials: [...(c.socials ?? []), entry], updatedAt: now() } : c
        )
      : [...contactX, { recordID, favorite: false, tags: [], socials: [entry], createdAt: now(), updatedAt: now() } as ContactX];
    set({ contactX: next });
    saveContactX(next);
  },

  removeSocial: (recordID, socialId) => {
    const { contactX } = get();
    const next = contactX.map((c) =>
      c.recordID === recordID ? { ...c, socials: (c.socials ?? []).filter((s) => s.id !== socialId), updatedAt: now() } : c
    );
    set({ contactX: next });
    saveContactX(next);
  },

  /* the accurate number shown/dialed first for a contact */
  setPrimaryPhone: (recordID, number) => {
    const { contactX } = get();
    const existing = contactX.find((c) => c.recordID === recordID);
    const next = existing
      ? contactX.map((c) => (c.recordID === recordID ? { ...c, primaryPhone: number, updatedAt: now() } : c))
      : [...contactX, { recordID, favorite: false, tags: [], primaryPhone: number, createdAt: now(), updatedAt: now() } as ContactX];
    set({ contactX: next });
    saveContactX(next);
  },

  /* ---------------- ledger ---------------- */

  saveEntry: (e) => {
    const { entries } = get();
    const id = e.id || uid();
    const existing = entries.find((x) => x.id === id);
    const rec: LedgerEntry = {
      id,
      ownerId: e.ownerId,
      type: e.type ?? existing?.type ?? "credit",
      kind: e.kind,
      amount: e.amount ?? existing?.amount ?? 0,
      items: e.items ?? existing?.items,
      payments: e.payments ?? existing?.payments ?? [],
      date: e.date ?? existing?.date ?? todayStr(),
      dueDate: e.dueDate ?? undefined,
      category: e.category ?? undefined,
      method: e.method ?? existing?.method,
      note: e.note ?? existing?.note,
      listId: e.listId ?? existing?.listId,
      groupId: e.groupId ?? undefined,
      refs: e.refs ?? existing?.refs,
      createdAt: existing?.createdAt ?? now(),
      updatedAt: now(),
    };
    const next = existing ? entries.map((x) => (x.id === id ? rec : x)) : [rec, ...entries];
    next.sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : b.createdAt - a.createdAt));
    set({ entries: next });
    saveEntries(next);
  },

  deleteEntry: (id) => {
    const { entries } = get();
    const victim = entries.find((e) => e.id === id);
    const next = entries.map((e) => (e.id === id ? { ...e, deletedAt: now(), updatedAt: now() } : e));
    set({ entries: next });
    saveEntries(next);
    if (victim)
      get().pushUndo("Entry moved to Trash", () => {
        const restore = get().entries.map((e) => (e.id === id ? { ...victim, deletedAt: undefined } : e));
        set({ entries: restore });
        saveEntries(restore);
      });
  },

  restoreEntry: (id) => {
    const next = get().entries.map((e) => (e.id === id ? { ...e, deletedAt: undefined, updatedAt: now() } : e));
    set({ entries: next });
    saveEntries(next);
  },

  purgeEntry: (id) => {
    const next = get().entries.filter((e) => e.id !== id);
    set({ entries: next });
    saveEntries(next);
  },

  addPayment: (entryId, p) => {
    const { entries } = get();
    const next = entries.map((e) => {
      if (e.id !== entryId) return e;
      const payment: Payment = {
        id: uid(),
        amount: p.amount ?? 0,
        date: p.date ?? todayStr(),
        note: p.note,
        method: p.method,
        auto: p.auto,
        refs: p.refs,
        createdAt: now(),
      };
      return { ...e, payments: [...e.payments, payment], updatedAt: now() };
    });
    set({ entries: next });
    saveEntries(next);
  },

  removePayment: (entryId, paymentId) => {
    const { entries } = get();
    const next = entries.map((e) =>
      e.id === entryId ? { ...e, payments: e.payments.filter((p) => p.id !== paymentId), updatedAt: now() } : e
    );
    set({ entries: next });
    saveEntries(next);
  },

  settleUp: (ownerId) => {
    const { entries } = get();
    const mine = entries
      .filter((e) => e.ownerId === ownerId && !e.deletedAt)
      .sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : a.createdAt - b.createdAt));
    const net = mine.reduce((s, e) => s + (e.type === "credit" ? 1 : -1) * entryRemaining(e), 0);
    let left = Math.abs(net);
    if (left < 0.005) return;
    const wantType = net > 0 ? "credit" : "debit";
    const next = entries.map((e) => {
      if (e.ownerId !== ownerId || e.deletedAt || e.type !== wantType) return e;
      const rem = entryRemaining(e);
      if (rem <= 0.005 || left <= 0.005) return e;
      const pay = Math.min(rem, left);
      left = Math.round((left - pay) * 100) / 100;
      return {
        ...e,
        payments: [
          ...e.payments,
          { id: uid(), amount: pay, date: todayStr(), auto: "settle" as const, createdAt: now() },
        ],
        updatedAt: now(),
      };
    });
    set({ entries: next });
    saveEntries(next);
  },

  /* ---------------- groups ---------------- */

  saveGroup: (g) => {
    const { groups } = get();
    const id = g.id || uid();
    const existing = groups.find((x) => x.id === id);
    const rec: LedgerGroup = {
      id,
      ownerId: g.ownerId,
      title: g.title ?? existing?.title ?? "Group",
      note: g.note ?? existing?.note,
      refs: g.refs ?? existing?.refs ?? [],
      createdAt: existing?.createdAt ?? now(),
      updatedAt: now(),
    };
    const next = existing ? groups.map((x) => (x.id === id ? rec : x)) : [rec, ...groups];
    set({ groups: next });
    saveGroups(next);
  },

  deleteGroup: (id) => {
    const { groups, entries } = get();
    const victim = groups.find((g) => g.id === id);
    const victimEntries = entries.filter((e) => e.groupId === id);
    const nextGroups = groups.map((g) => (g.id === id ? { ...g, deletedAt: now(), updatedAt: now() } : g));
    const nextEntries = entries.map((e) => (e.groupId === id ? { ...e, groupId: undefined, updatedAt: now() } : e));
    set({ groups: nextGroups, entries: nextEntries });
    saveGroups(nextGroups);
    saveEntries(nextEntries);
    if (victim)
      get().pushUndo("Group deleted", () => {
        const rg = get().groups.map((g) => (g.id === id ? { ...victim, deletedAt: undefined } : g));
        const re = get().entries.map((e) =>
          victimEntries.some((v) => v.id === e.id) ? { ...e, groupId: id } : e
        );
        set({ groups: rg, entries: re });
        saveGroups(rg);
        saveEntries(re);
      });
  },

  restoreGroup: (id) => {
    const next = get().groups.map((g) => (g.id === id ? { ...g, deletedAt: undefined, updatedAt: now() } : g));
    set({ groups: next });
    saveGroups(next);
  },

  purgeGroup: (id) => {
    const next = get().groups.filter((g) => g.id !== id);
    set({ groups: next });
    saveGroups(next);
  },

  /* ---------------- lists ---------------- */

  saveList: (l) => {
    const { lists } = get();
    const id = l.id || uid();
    const existing = lists.find((x) => x.id === id);
    const rec: CalcList = {
      id,
      ownerId: l.ownerId,
      kind: l.kind ?? existing?.kind ?? "calculating",
      title: l.title ?? existing?.title ?? "List",
      note: l.note ?? existing?.note,
      discount: l.discount ?? existing?.discount,
      taxPct: l.taxPct ?? existing?.taxPct,
      extraCharge: l.extraCharge ?? existing?.extraCharge,
      isTemplate: l.isTemplate ?? existing?.isTemplate ?? false,
      postToLedger: l.postToLedger ?? existing?.postToLedger ?? false,
      repeat: l.repeat ?? existing?.repeat ?? "none",
      currency: l.currency ?? existing?.currency,
      items: l.items ?? existing?.items ?? [],
      refs: l.refs ?? existing?.refs,
      createdAt: existing?.createdAt ?? now(),
      updatedAt: now(),
    };
    const next = existing ? lists.map((x) => (x.id === id ? rec : x)) : [rec, ...lists];
    set({ lists: next });
    saveLists(next);
  },

  deleteList: (id) => {
    const { lists } = get();
    const victim = lists.find((l) => l.id === id);
    const next = lists.map((l) => (l.id === id ? { ...l, deletedAt: now(), updatedAt: now() } : l));
    set({ lists: next });
    saveLists(next);
    if (victim)
      get().pushUndo("List moved to Trash", () => {
        const restore = get().lists.map((l) => (l.id === id ? { ...victim, deletedAt: undefined } : l));
        set({ lists: restore });
        saveLists(restore);
      });
  },

  restoreList: (id) => {
    const next = get().lists.map((l) => (l.id === id ? { ...l, deletedAt: undefined, updatedAt: now() } : l));
    set({ lists: next });
    saveLists(next);
  },

  purgeList: (id) => {
    const next = get().lists.filter((l) => l.id !== id);
    set({ lists: next });
    saveLists(next);
  },

  duplicateList: (id, opts) => {
    const { lists } = get();
    const src = lists.find((l) => l.id === id);
    if (!src) return null;
    const newId = uid();
    const items: ListItem[] = opts?.fresh
      ? src.items.map((i) => ({
          ...i,
          id: uid(),
          payments: [],
          checked: false,
          createdAt: now(),
          updatedAt: now(),
        }))
      : src.items.map((i) => ({
          ...i,
          id: uid(),
          payments: i.payments.map((p) => ({ ...p, id: uid(), createdAt: now() })),
          createdAt: now(),
          updatedAt: now(),
        }));
    const copy: CalcList = {
      ...src,
      id: newId,
      title: src.title,
      isTemplate: !!opts?.asTemplate,
      items,
      createdAt: now(),
      updatedAt: now(),
      deletedAt: undefined,
    };
    const next = [copy, ...lists];
    set({ lists: next });
    saveLists(next);
    return newId;
  },

  addListItem: (listId, item) => {
    const { lists } = get();
    const next = lists.map((l) => {
      if (l.id !== listId) return l;
      const sortOrder = l.items.length ? Math.max(...l.items.map((i) => i.sortOrder)) + 1 : 0;
      const rec: ListItem = {
        id: uid(),
        name: item.name ?? "",
        qty: item.qty ?? 1,
        unit: item.unit,
        unitPrice: item.unitPrice ?? 0,
        discount: item.discount,
        taxPct: item.taxPct,
        payments: [],
        checked: false,
        date: item.date,
        dueDate: item.dueDate,
        priority: item.priority,
        note: item.note,
        sortOrder,
        createdAt: now(),
        updatedAt: now(),
      };
      return { ...l, items: [...l.items, rec], updatedAt: now() };
    });
    set({ lists: next });
    saveLists(next);
  },

  updateListItem: (listId, itemId, patch) => {
    const { lists } = get();
    const next = lists.map((l) =>
      l.id === listId
        ? {
            ...l,
            items: l.items.map((i) => (i.id === itemId ? { ...i, ...patch, updatedAt: now() } : i)),
            updatedAt: now(),
          }
        : l
    );
    set({ lists: next });
    saveLists(next);
  },

  removeListItem: (listId, itemId) => {
    const { lists } = get();
    const next = lists.map((l) =>
      l.id === listId ? { ...l, items: l.items.filter((i) => i.id !== itemId), updatedAt: now() } : l
    );
    set({ lists: next });
    saveLists(next);
  },

  addItemPayment: (listId, itemId, p) => {
    const { lists } = get();
    const next = lists.map((l) => {
      if (l.id !== listId) return l;
      return {
        ...l,
        items: l.items.map((i) => {
          if (i.id !== itemId) return i;
          const payment: Payment = {
            id: uid(),
            amount: p.amount ?? 0,
            date: p.date ?? todayStr(),
            note: p.note,
            method: p.method,
            auto: p.auto,
            refs: p.refs,
            createdAt: now(),
          };
          return { ...i, payments: [...i.payments, payment], updatedAt: now() };
        }),
        updatedAt: now(),
      };
    });
    set({ lists: next });
    saveLists(next);
  },

  removeItemPayment: (listId, itemId, paymentId) => {
    const { lists } = get();
    const next = lists.map((l) =>
      l.id === listId
        ? {
            ...l,
            items: l.items.map((i) =>
              i.id === itemId ? { ...i, payments: i.payments.filter((p) => p.id !== paymentId), updatedAt: now() } : i
            ),
            updatedAt: now(),
          }
        : l
    );
    set({ lists: next });
    saveLists(next);
  },

  toggleItemChecked: (listId, itemId) => {
    const { lists } = get();
    const list = lists.find((l) => l.id === listId);
    const item = list?.items.find((i) => i.id === itemId);
    if (!list || !item) return;
    if (!item.checked) {
      // tick → one payment of the remaining, tagged auto:"tick"
      const remaining = Math.max(Math.round((lineTotal(item) - itemPaid(item)) * 100) / 100, 0);
      if (remaining > 0) {
        get().addItemPayment(listId, itemId, { amount: remaining, auto: "tick" });
      }
      get().updateListItem(listId, itemId, { checked: true });
    } else {
      // untick → remove tick payments
      const next = lists.map((l) =>
        l.id === listId
          ? {
              ...l,
              items: l.items.map((i) =>
                i.id === itemId
                  ? { ...i, checked: false, payments: i.payments.filter((p) => p.auto !== "tick"), updatedAt: now() }
                  : i
              ),
              updatedAt: now(),
            }
          : l
      );
      set({ lists: next });
      saveLists(next);
    }
  },

  markAllPaid: (listId) => {
    const { lists } = get();
    const list = lists.find((l) => l.id === listId);
    if (!list) return;
    const next = lists.map((l) => {
      if (l.id !== listId) return l;
      return {
        ...l,
        items: l.items.map((i) => {
          const remaining = Math.max(Math.round((lineTotal(i) - itemPaid(i)) * 100) / 100, 0);
          if (remaining > 0.005)
            return {
              ...i,
              checked: true,
              payments: [
                ...i.payments,
                { id: uid(), amount: remaining, date: todayStr(), auto: "tick" as const, createdAt: now() },
              ],
              updatedAt: now(),
            };
          return lineTotal(i) === 0 ? { ...i, checked: true, updatedAt: now() } : i;
        }),
        updatedAt: now(),
      };
    });
    set({ lists: next });
    saveLists(next);
  },

  clearPaid: (listId) => {
    const { lists } = get();
    const next = lists.map((l) =>
      l.id === listId
        ? {
            ...l,
            items: l.items.map((i) => ({ ...i, payments: [], checked: false, updatedAt: now() })),
            updatedAt: now(),
          }
        : l
    );
    set({ lists: next });
    saveLists(next);
  },

  /* ---------------- notes ---------------- */

  saveNote: (n) => {
    const { notes } = get();
    const id = n.id || uid();
    const existing = notes.find((x) => x.id === id);
    const rec: Note = {
      id,
      ownerId: n.ownerId,
      title: n.title ?? existing?.title ?? "",
      body: n.body ?? existing?.body ?? "",
      tags: n.tags ?? existing?.tags ?? [],
      pinned: n.pinned ?? existing?.pinned ?? false,
      color: n.color ?? existing?.color,
      links: n.links ?? existing?.links ?? [],
      createdAt: existing?.createdAt ?? now(),
      updatedAt: now(),
    };
    const next = existing ? notes.map((x) => (x.id === id ? rec : x)) : [rec, ...notes];
    set({ notes: next });
    saveNotes(next);
  },

  deleteNote: (id) => {
    const { notes } = get();
    const victim = notes.find((n) => n.id === id);
    const next = notes.map((n) => (n.id === id ? { ...n, deletedAt: now(), updatedAt: now() } : n));
    set({ notes: next });
    saveNotes(next);
    if (victim)
      get().pushUndo("Note moved to Trash", () => {
        const restore = get().notes.map((n) => (n.id === id ? { ...victim, deletedAt: undefined } : n));
        set({ notes: restore });
        saveNotes(restore);
      });
  },

  restoreNote: (id) => {
    const next = get().notes.map((n) => (n.id === id ? { ...n, deletedAt: undefined, updatedAt: now() } : n));
    set({ notes: next });
    saveNotes(next);
  },

  purgeNote: (id) => {
    const next = get().notes.filter((n) => n.id !== id);
    set({ notes: next });
    saveNotes(next);
  },

  /* ---------------- reminders ---------------- */

  saveReminder: (r) => {
    const { reminders } = get();
    const id = r.id || uid();
    const existing = reminders.find((x) => x.id === id);
    const rec: Reminder = {
      id,
      ownerId: r.ownerId,
      title: r.title ?? existing?.title ?? "",
      notes: r.notes ?? existing?.notes,
      kind: r.kind ?? existing?.kind ?? "custom",
      dueAt: r.dueAt ?? existing?.dueAt ?? now(),
      repeat: r.repeat ?? existing?.repeat ?? "none",
      leadMinutes: r.leadMinutes ?? existing?.leadMinutes,
      links: r.links ?? existing?.links ?? [],
      done: r.done ?? existing?.done ?? false,
      createdAt: existing?.createdAt ?? now(),
      updatedAt: now(),
    };
    const next = existing ? reminders.map((x) => (x.id === id ? rec : x)) : [rec, ...reminders];
    set({ reminders: next });
    saveReminders(next);
    if (!rec.done && !rec.deletedAt) scheduleReminderNotification(rec);
    else cancelReminderNotification(rec.id);
  },

  deleteReminder: (id) => {
    const { reminders } = get();
    const victim = reminders.find((r) => r.id === id);
    const next = reminders.map((r) => (r.id === id ? { ...r, deletedAt: now(), updatedAt: now() } : r));
    set({ reminders: next });
    saveReminders(next);
    cancelReminderNotification(id);
    if (victim)
      get().pushUndo("Reminder moved to Trash", () => {
        const restore = get().reminders.map((r) => (r.id === id ? { ...victim, deletedAt: undefined } : r));
        set({ reminders: restore });
        saveReminders(restore);
        scheduleReminderNotification(victim);
      });
  },

  restoreReminder: (id) => {
    const next = get().reminders.map((r) => (r.id === id ? { ...r, deletedAt: undefined, updatedAt: now() } : r));
    set({ reminders: next });
    saveReminders(next);
    const rec = next.find((r) => r.id === id);
    if (rec && !rec.done) scheduleReminderNotification(rec);
  },

  purgeReminder: (id) => {
    const next = get().reminders.filter((r) => r.id !== id);
    set({ reminders: next });
    saveReminders(next);
    cancelReminderNotification(id);
  },

  completeReminder: (id) => {
    const { reminders } = get();
    const next = reminders.map((r) => (r.id === id ? { ...r, done: true, doneAt: now(), updatedAt: now() } : r));
    set({ reminders: next });
    saveReminders(next);
    cancelReminderNotification(id);
  },

  reopenReminder: (id) => {
    const { reminders } = get();
    const next = reminders.map((r) =>
      r.id === id ? { ...r, done: false, doneAt: undefined, snoozedUntil: undefined, updatedAt: now() } : r
    );
    set({ reminders: next });
    saveReminders(next);
    const rec = next.find((r) => r.id === id);
    if (rec) scheduleReminderNotification(rec);
  },

  snoozeReminder: (id, until) => {
    const { reminders } = get();
    const next = reminders.map((r) => (r.id === id ? { ...r, snoozedUntil: until, updatedAt: now() } : r));
    set({ reminders: next });
    saveReminders(next);
    cancelReminderNotification(id);
    const rec = next.find((r) => r.id === id);
    if (rec) scheduleReminderNotification({ ...rec, dueAt: until });
  },

  fireReminder: (id) => {
    // repeating reminder advances to its next occurrence
    const { reminders } = get();
    const rec = reminders.find((r) => r.id === id);
    if (!rec || rec.repeat === "none") return;
    const nextDue = nextOccurrence(rec.dueAt, rec.repeat);
    const next = reminders.map((r) =>
      r.id === id
        ? { ...r, dueAt: nextDue, lastFiredAt: now(), firedLeads: undefined, snoozedUntil: undefined, updatedAt: now() }
        : r
    );
    set({ reminders: next });
    saveReminders(next);
    const updated = next.find((r) => r.id === id);
    if (updated) scheduleReminderNotification(updated);
  },

  /* ---------------- banks ---------------- */

  saveBank: (b) => {
    const { banks } = get();
    const id = b.id || uid();
    const existing = banks.find((x) => x.id === id);
    const rec: BankAccount = {
      id,
      ownerId: b.ownerId,
      label: b.label ?? existing?.label ?? "",
      holderName: b.holderName ?? existing?.holderName,
      bankName: b.bankName ?? existing?.bankName,
      accountNumber: b.accountNumber ?? existing?.accountNumber,
      iban: b.iban ?? existing?.iban,
      swift: b.swift ?? existing?.swift,
      branch: b.branch ?? existing?.branch,
      notes: b.notes ?? existing?.notes,
      balance: b.balance ?? existing?.balance,
      currency: b.currency ?? existing?.currency,
      createdAt: existing?.createdAt ?? now(),
      updatedAt: now(),
    };
    const next = existing ? banks.map((x) => (x.id === id ? rec : x)) : [rec, ...banks];
    set({ banks: next });
    saveBanks(next);
  },

  deleteBank: (id) => {
    const { banks } = get();
    const victim = banks.find((b) => b.id === id);
    const next = banks.map((b) => (b.id === id ? { ...b, deletedAt: now(), updatedAt: now() } : b));
    set({ banks: next });
    saveBanks(next);
    if (victim)
      get().pushUndo("Account moved to Trash", () => {
        const restore = get().banks.map((b) => (b.id === id ? { ...victim, deletedAt: undefined } : b));
        set({ banks: restore });
        saveBanks(restore);
      });
  },

  restoreBank: (id) => {
    const next = get().banks.map((b) => (b.id === id ? { ...b, deletedAt: undefined, updatedAt: now() } : b));
    set({ banks: next });
    saveBanks(next);
  },

  purgeBank: (id) => {
    const next = get().banks.filter((b) => b.id !== id);
    set({ banks: next });
    saveBanks(next);
  },

  /* ---------------- calls / messages ---------------- */

  addCall: (c) => {
    const { recents } = get();
    const rec: CallLog = {
      id: uid(),
      phone: c.phone ?? "",
      ownerId: c.ownerId,
      direction: c.direction ?? "outgoing",
      at: c.at ?? now(),
      duration: c.duration ?? 0,
      via: c.via ?? "tel",
      createdAt: now(),
    };
    const next = [rec, ...recents].slice(0, 500);
    set({ recents: next });
    saveRecents(next);
  },

  deleteRecent: (id) => {
    const next = get().recents.filter((r) => r.id !== id);
    set({ recents: next });
    saveRecents(next);
  },

  clearRecents: () => {
    set({ recents: [] });
    saveRecents([]);
  },

  logMessage: (m) => {
    const { messages } = get();
    const rec: MessageLog = {
      id: uid(),
      ownerId: m.ownerId,
      channel: m.channel ?? "other",
      text: m.text ?? "",
      date: now(),
      createdAt: now(),
    };
    const next = [rec, ...messages].slice(0, 300);
    set({ messages: next });
    saveMessages(next);
  },

  /* ---------------- settings / data ---------------- */

  updateSettings: (patch) => {
    const next = { ...get().settings, ...patch };
    set({ settings: next });
    saveSettings(next);
  },

  addCustomCategory: (name) => {
    const name2 = name.trim();
    if (!name2) return;
    const cur = get().settings.customCategories ?? [];
    if (cur.includes(name2)) return;
    get().updateSettings({ customCategories: [...cur, name2] });
  },

  removeCustomCategory: (name) => {
    const cur = get().settings.customCategories ?? [];
    get().updateSettings({ customCategories: cur.filter((c) => c !== name) });
  },

  toggleBlocked: (phone) => {
    const norm = normalizePhone(phone, get().settings.countryCode);
    const cur = get().settings.blockedNumbers;
    const next = cur.includes(norm) ? cur.filter((b) => b !== norm) : [...cur, norm];
    get().updateSettings({ blockedNumbers: next });
  },

  setSpeedDial: (key, recordID) => {
    const cur = { ...(get().settings.speedDial ?? {}) };
    if (recordID) cur[key] = recordID;
    else delete cur[key];
    get().updateSettings({ speedDial: cur });
  },

  moveEntity: (kind, id, ownerId) => {
    if (kind === "entry") {
      const next = get().entries.map((e) => (e.id === id ? { ...e, ownerId, updatedAt: now() } : e));
      set({ entries: next });
      saveEntries(next);
    } else if (kind === "list") {
      const next = get().lists.map((l) => (l.id === id ? { ...l, ownerId, updatedAt: now() } : l));
      set({ lists: next });
      saveLists(next);
    } else if (kind === "note") {
      const next = get().notes.map((n) => (n.id === id ? { ...n, ownerId, updatedAt: now() } : n));
      set({ notes: next });
      saveNotes(next);
    } else if (kind === "reminder") {
      const next = get().reminders.map((r) => (r.id === id ? { ...r, ownerId, updatedAt: now() } : r));
      set({ reminders: next });
      saveReminders(next);
    } else if (kind === "bank") {
      const next = get().banks.map((b) => (b.id === id ? { ...b, ownerId, updatedAt: now() } : b));
      set({ banks: next });
      saveBanks(next);
    } else if (kind === "group") {
      const next = get().groups.map((g) => (g.id === id ? { ...g, ownerId, updatedAt: now() } : g));
      set({ groups: next });
      saveGroups(next);
    }
  },

  restoreBackup: (data, mode) => {
    if (mode === "replace") {
      const state: Partial<AppState> = {
        contactX: data.contactX ?? [],
        entries: data.entries ?? [],
        groups: data.groups ?? [],
        lists: data.lists ?? [],
        notes: data.notes ?? [],
        reminders: data.reminders ?? [],
        banks: data.banks ?? [],
        recents: data.recents ?? [],
        messages: data.messages ?? [],
        settings: { ...DEFAULT_SETTINGS, ...(data.settings ?? {}) },
      };
      set(state);
      saveContactX(state.contactX!);
      saveEntries(state.entries!);
      saveGroups(state.groups!);
      saveLists(state.lists!);
      saveNotes(state.notes!);
      saveReminders(state.reminders!);
      saveBanks(state.banks!);
      saveRecents(state.recents!);
      saveMessages(state.messages!);
      saveSettings(state.settings!);
      get().reminders.filter((r) => !r.done).forEach((r) => scheduleReminderNotification(r));
      return;
    }
    // merge — by id, existing records win (contactX keyed by recordID)
    const s = get();
    const mergeById = <T extends { id: string; deletedAt?: number }>(cur: T[], inc?: T[]): T[] => {
      if (!inc?.length) return cur;
      const ids = new Set(cur.map((x) => x.id));
      const add = inc.filter((x) => !ids.has(x.id) && !x.deletedAt);
      return [...add, ...cur];
    };
    const mergeContactX = (cur: ContactX[], inc?: ContactX[]): ContactX[] => {
      if (!inc?.length) return cur;
      const ids = new Set(cur.map((x) => x.recordID));
      const add = inc.filter((x) => !ids.has(x.recordID));
      return [...add, ...cur];
    };
    const next = {
      contactX: mergeContactX(s.contactX, data.contactX),
      entries: mergeById(s.entries, data.entries),
      groups: mergeById(s.groups, data.groups),
      lists: mergeById(s.lists, data.lists),
      notes: mergeById(s.notes, data.notes),
      reminders: mergeById(s.reminders, data.reminders),
      banks: mergeById(s.banks, data.banks),
      recents: mergeById(s.recents, data.recents),
      messages: mergeById(s.messages, data.messages),
    };
    set(next);
    saveContactX(next.contactX);
    saveEntries(next.entries);
    saveGroups(next.groups);
    saveLists(next.lists);
    saveNotes(next.notes);
    saveReminders(next.reminders);
    saveBanks(next.banks);
    saveRecents(next.recents);
    saveMessages(next.messages);
    if (data.settings?.language) get().updateSettings({ language: data.settings.language });
    next.reminders.filter((r) => !r.done).forEach((r) => scheduleReminderNotification(r));
  },

  wipeAll: async () => {
    await wipeStorage();
    set({
      contactX: [],
      entries: [],
      groups: [],
      lists: [],
      notes: [],
      reminders: [],
      banks: [],
      recents: [],
      messages: [],
      settings: { ...DEFAULT_SETTINGS },
      lastUndo: null,
    });
  },
}));

/* ---------------- selectors / helpers ---------------- */

export const contactByRecord = (recordID: string): DeviceContact | undefined =>
  useApp.getState().deviceContacts.find((c) => c.recordID === recordID);

/* Phone index — rebuilt automatically whenever the deviceContacts array
   identity changes. Two keys per number: the full normalized digits and the
   LAST 9 DIGITS (the national significant part of an Ethiopian mobile
   number). The tail key is what makes calls logged as "+251 09…" (double
   prefix), "09…", "9…" or "+251 9…" all resolve to the same contact. */
let phoneIdxFor: DeviceContact[] | null = null;
let phoneIdxFull = new Map<string, DeviceContact>();
let phoneIdxTail = new Map<string, DeviceContact>();

const ensurePhoneIndex = () => {
  const { deviceContacts, settings } = useApp.getState();
  if (phoneIdxFor === deviceContacts) return;
  phoneIdxFor = deviceContacts;
  phoneIdxFull = new Map();
  phoneIdxTail = new Map();
  deviceContacts.forEach((c) => {
    c.phones.forEach((p) => {
      const d = normalizePhone(p.number, settings.countryCode).replace(/\D/g, "");
      if (d.length < 5) return;
      if (!phoneIdxFull.has(d)) phoneIdxFull.set(d, c);
      const tail = d.slice(-9);
      if (tail.length >= 7 && !phoneIdxTail.has(tail)) phoneIdxTail.set(tail, c);
    });
  });
};

/** Resolve a phone number (any local/international format) to a contact.
    O(1) via the index; empty or too-short numbers never match. */
export const contactByPhone = (phone: string): DeviceContact | undefined => {
  const { settings } = useApp.getState();
  const d = normalizePhone(phone ?? "", settings.countryCode).replace(/\D/g, "");
  if (d.length < 5) return undefined; // empty / private / short codes → no match
  ensurePhoneIndex();
  const full = phoneIdxFull.get(d);
  if (full) return full;
  const tail = d.slice(-9);
  if (tail.length >= 7) {
    const hit = phoneIdxTail.get(tail);
    if (hit) return hit;
  }
  return undefined;
};

export const favoriteRecordIds = (): Set<string> =>
  new Set(useApp.getState().contactX.filter((c) => c.favorite).map((c) => c.recordID));

export const openListCount = (ownerId: OwnerId): number =>
  useApp
    .getState()
    .lists.filter((l) => l.ownerId === ownerId && !l.deletedAt && !l.isTemplate && l.postToLedger && listTotals(l).pending > 0.005)
    .length;
