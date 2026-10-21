"use client";

import { create } from "zustand";
import {
  AttachmentMeta,
  AttachmentParentType,
  BankAccount,
  CalcList,
  CallLog,
  Contact,
  ID,
  LedgerEntry,
  LedgerGroup,
  LinkRef,
  MessageLog,
  Note,
  OwnerId,
  Payment,
  Reminder,
  Settings,
} from "./types";
import * as calc from "./calc";
import { displayName, normalizePhone, now, todayStr, uid } from "./utils";
import { r2 as r2Fallback } from "./calc";
import {
  blobDel,
  blobGet,
  blobPut,
  idbClear,
  idbDel,
  idbGetAll,
  idbPut,
  idbPutKey,
  metaGet,
  metaSet,
} from "./db";

/* ------------------------------------------------------------------ */
/* State                                                                */
/* ------------------------------------------------------------------ */

interface AppState {
  hydrated: boolean;
  contacts: Contact[];
  entries: LedgerEntry[];
  groups: LedgerGroup[];
  lists: CalcList[];
  notes: Note[];
  reminders: Reminder[];
  banks: BankAccount[];
  recents: CallLog[];
  messages: MessageLog[];
  attachments: AttachmentMeta[];
  settings: Settings;
  lastUndo: { label: string; fn: () => void } | null;

  hydrate: () => Promise<void>;
  pushUndo: (label: string, fn: () => void) => void;
  undo: () => boolean;

  /* contacts */
  saveContact: (c: Partial<Contact> & { id?: ID }) => Contact;
  deleteContact: (id: ID) => void;
  restoreContact: (id: ID) => void;
  purgeContact: (id: ID) => void;
  toggleFavorite: (id: ID) => void;
  mergeContacts: (primaryId: ID, dupId: ID) => void;
  findDuplicates: () => { primary: Contact; dup: Contact; reason: string }[];

  /* ledger entries */
  saveEntry: (e: Partial<LedgerEntry> & { id?: ID; ownerId: OwnerId; type: "credit" | "debit" }) => LedgerEntry;
  deleteEntry: (id: ID) => void;
  restoreEntry: (id: ID) => void;
  purgeEntry: (id: ID) => void;
  addPayment: (entryId: ID, payment: Partial<Payment> & { amount: number }) => void;
  removePayment: (entryId: ID, paymentId: ID) => void;
  removePaymentRefs: (entryId: ID, paymentId: ID, refs: LinkRef[]) => void;
  updatePaymentRefs: (entryId: ID, paymentId: ID, refs: LinkRef[]) => void;
  settleUp: (ownerId: OwnerId) => number;
  moveEntity: (kind: "entry" | "list" | "note" | "reminder" | "bank" | "group", id: ID, ownerId: OwnerId) => void;

  /* ledger groups */
  saveGroup: (g: Partial<LedgerGroup> & { id?: ID; ownerId: OwnerId; title: string }) => LedgerGroup;
  deleteGroup: (id: ID) => void;
  restoreGroup: (id: ID) => void;
  purgeGroup: (id: ID) => void;

  /* lists */
  saveList: (l: Partial<CalcList> & { id?: ID; ownerId: OwnerId; title: string }) => CalcList;
  deleteList: (id: ID) => void;
  restoreList: (id: ID) => void;
  purgeList: (id: ID) => void;
  duplicateList: (id: ID, opts?: { asTemplate?: boolean; fresh?: boolean }) => ID | undefined;
  addListItem: (listId: ID, item: Partial<CalcList["items"][number]>) => void;
  updateListItem: (listId: ID, itemId: ID, patch: Partial<CalcList["items"][number]>) => void;
  removeListItem: (listId: ID, itemId: ID) => void;
  addItemPayment: (listId: ID, itemId: ID, payment: Partial<Payment> & { amount: number }) => void;
  removeItemPayment: (listId: ID, itemId: ID, paymentId: ID) => void;
  toggleItemChecked: (listId: ID, itemId: ID, checked: boolean) => void;
  markAllPaid: (listId: ID) => number;
  clearPaid: (listId: ID) => void;

  /* notes */
  saveNote: (n: Partial<Note> & { id?: ID; ownerId: OwnerId }) => Note;
  deleteNote: (id: ID) => void;
  restoreNote: (id: ID) => void;
  purgeNote: (id: ID) => void;

  /* reminders */
  saveReminder: (r: Partial<Reminder> & { id?: ID; ownerId: OwnerId; title: string }) => Reminder;
  deleteReminder: (id: ID) => void;
  restoreReminder: (id: ID) => void;
  purgeReminder: (id: ID) => void;
  completeReminder: (id: ID) => void;
  reopenReminder: (id: ID) => void;
  snoozeReminder: (id: ID, until: number) => void;
  fireReminder: (id: ID) => void;

  /* banks */
  saveBank: (b: Partial<BankAccount> & { id?: ID; ownerId: OwnerId; label: string }) => BankAccount;
  deleteBank: (id: ID) => void;
  restoreBank: (id: ID) => void;
  purgeBank: (id: ID) => void;

  /* calls + messages */
  addCall: (c: Partial<CallLog> & { phone: string; direction: CallLog["direction"] }) => void;
  deleteRecent: (id: ID) => void;
  clearRecents: () => void;
  logMessage: (ownerId: OwnerId, channel: MessageLog["channel"], text: string) => void;

  /* attachments */
  addAttachment: (file: File, parentType: AttachmentParentType, parentId: ID) => Promise<AttachmentMeta | null>;
  removeAttachment: (id: ID) => Promise<void>;
  attachmentBlob: (id: ID) => Promise<Blob | undefined>;

  /* settings + data */
  updateSettings: (patch: Partial<Settings>) => void;
  addCustomCategory: (label: string) => void;
  removeCustomCategory: (label: string) => void;
  importContacts: (list: Partial<Contact>[], mode: "skip" | "merge") => { added: number; merged: number; skipped: number };
  wipeAll: () => Promise<void>;
}

const DEFAULT_SETTINGS: Settings = {
  theme: "auto",
  currency: "ETB",
  countryCode: "ET",
  autolockMin: 1,
  hideBalances: false,
  callMode: "dialer",
  customCategories: [],
  notifAsked: false,
  blockedNumbers: [],
};

/* ------------------------------------------------------------------ */
/* Store                                                                */
/* ------------------------------------------------------------------ */

export const useApp = create<AppState>((set, get) => {
  /* ---- internal helpers ---- */
  const touch = <T extends { updatedAt: number }>(e: T): T => ({ ...e, updatedAt: now() });

  const persistContact = (c: Contact) => void idbPut("contacts", c);
  const persistEntry = (e: LedgerEntry) => void idbPut("entries", e);
  const persistGroup = (g: LedgerGroup) => void idbPut("groups", g);
  const persistList = (l: CalcList) => void idbPut("lists", l);
  const persistNote = (n: Note) => void idbPut("notes", n);
  const persistReminder = (r: Reminder) => void idbPut("reminders", r);
  const persistBank = (b: BankAccount) => void idbPut("banks", b);
  const persistRecent = (c: CallLog) => void idbPut("recents", c);
  const persistMessage = (m: MessageLog) => void idbPut("messages", m);
  const persistAttachment = (a: AttachmentMeta) => void idbPut("attachments", a);
  const persistSettings = (s: Settings) => void metaSet("settings", s);

  const updateEntry = (id: ID, fn: (e: LedgerEntry) => LedgerEntry) => {
    const e = get().entries.find((x) => x.id === id);
    if (!e) return;
    const next = touch(fn(e));
    set((s) => ({ entries: s.entries.map((x) => (x.id === id ? next : x)) }));
    persistEntry(next);
  };

  const updateList = (id: ID, fn: (l: CalcList) => CalcList) => {
    const l = get().lists.find((x) => x.id === id);
    if (!l) return;
    const next = touch(fn(l));
    set((s) => ({ lists: s.lists.map((x) => (x.id === id ? next : x)) }));
    persistList(next);
  };

  const updateReminder = (id: ID, fn: (r: Reminder) => Reminder) => {
    const r = get().reminders.find((x) => x.id === id);
    if (!r) return;
    const next = touch(fn(r));
    set((s) => ({ reminders: s.reminders.map((x) => (x.id === id ? next : x)) }));
    persistReminder(next);
  };

  return {
    hydrated: false,
    contacts: [],
    entries: [],
    groups: [],
    lists: [],
    notes: [],
    reminders: [],
    banks: [],
    recents: [],
    messages: [],
    attachments: [],
    settings: DEFAULT_SETTINGS,
    lastUndo: null,

    /* ---------------- hydrate ---------------- */
    hydrate: async () => {
      if (get().hydrated) return;
      const [contacts, entries, groups, lists, notes, reminders, banks, recents, messages, attachments] =
        await Promise.all([
          idbGetAll<Contact>("contacts"),
          idbGetAll<LedgerEntry>("entries"),
          idbGetAll<LedgerGroup>("groups"),
          idbGetAll<CalcList>("lists"),
          idbGetAll<Note>("notes"),
          idbGetAll<Reminder>("reminders"),
          idbGetAll<BankAccount>("banks"),
          idbGetAll<CallLog>("recents"),
          idbGetAll<MessageLog>("messages"),
          idbGetAll<AttachmentMeta>("attachments"),
        ]);
      let settings = (await metaGet<Settings>("settings")) || DEFAULT_SETTINGS;
      settings = { ...DEFAULT_SETTINGS, ...settings };

      const seeded = await metaGet<boolean>("demoSeeded");
      if (!seeded && contacts.length === 0 && entries.length === 0) {
        const demo = buildDemoData();
        set({
          contacts: demo.contacts,
          entries: demo.entries,
          groups: demo.groups,
          lists: demo.lists,
          notes: demo.notes,
          reminders: demo.reminders,
          banks: demo.banks,
          recents: demo.recents,
          messages: [],
          attachments: [],
          settings: { ...settings, demoSeeded: true },
        });
        await metaSet("demoSeeded", true);
        demo.contacts.forEach(persistContact);
        demo.entries.forEach(persistEntry);
        demo.groups.forEach(persistGroup);
        demo.lists.forEach(persistList);
        demo.notes.forEach(persistNote);
        demo.reminders.forEach(persistReminder);
        demo.banks.forEach(persistBank);
        demo.recents.forEach(persistRecent);
        await metaSet("settings", { ...settings, demoSeeded: true });
        set({ hydrated: true });
        return;
      }

      set({
        contacts,
        entries,
        groups,
        lists,
        notes,
        reminders,
        banks,
        recents: recents.sort((a, b) => b.at - a.at),
        messages,
        attachments,
        settings,
        hydrated: true,
      });
    },

    pushUndo: (label, fn) => set({ lastUndo: { label, fn } }),
    undo: () => {
      const u = get().lastUndo;
      if (!u) return false;
      set({ lastUndo: null });
      u.fn();
      return true;
    },

    /* ---------------- contacts ---------------- */
    saveContact: (input) => {
      const existing = input.id ? get().contacts.find((c) => c.id === input.id) : undefined;
      const id = input.id || uid();
      const c: Contact = {
        id,
        firstName: input.firstName ?? existing?.firstName ?? "",
        lastName: input.lastName ?? existing?.lastName ?? "",
        nickname: input.nickname ?? existing?.nickname,
        company: input.company ?? existing?.company,
        jobTitle: input.jobTitle ?? existing?.jobTitle,
        phones: input.phones ?? existing?.phones ?? [],
        emails: input.emails ?? existing?.emails ?? [],
        links: input.links ?? existing?.links ?? [],
        socials: input.socials ?? existing?.socials ?? [],
        addresses: input.addresses ?? existing?.addresses ?? [],
        customFields: input.customFields ?? existing?.customFields ?? [],
        birthday: input.birthday ?? existing?.birthday,
        tags: input.tags ?? existing?.tags ?? [],
        favorite: input.favorite ?? existing?.favorite ?? false,
        pinnedNote: input.pinnedNote ?? existing?.pinnedNote,
        note: input.note ?? existing?.note,
        photoId: input.photoId ?? existing?.photoId,
        color: input.color ?? existing?.color,
        textColor: input.textColor ?? existing?.textColor,
        speedDial: input.speedDial ?? existing?.speedDial,
        createdAt: existing?.createdAt ?? now(),
        updatedAt: now(),
      };
      // only one primary phone/email
      if (c.phones.some((p) => p.primary)) c.phones.forEach((p, i) => { if (i > 0 && p.primary && c.phones[0].primary) p.primary = false; });
      set((s) => ({
        contacts: existing
          ? s.contacts.map((x) => (x.id === id ? c : x))
          : [...s.contacts, c],
      }));
      persistContact(c);
      return c;
    },

    deleteContact: (id) => {
      const c = get().contacts.find((x) => x.id === id);
      if (!c) return;
      const next = { ...c, deletedAt: now() };
      set((s) => ({ contacts: s.contacts.map((x) => (x.id === id ? next : x)) }));
      persistContact(next);
      get().pushUndo(`“${displayName(c)}” moved to Trash`, () => get().restoreContact(id));
    },

    restoreContact: (id) => {
      const c = get().contacts.find((x) => x.id === id);
      if (!c) return;
      const next = { ...c, deletedAt: undefined };
      set((s) => ({ contacts: s.contacts.map((x) => (x.id === id ? next : x)) }));
      persistContact(next);
    },

    purgeContact: async (id) => {
      set((s) => ({ contacts: s.contacts.filter((x) => x.id !== id) }));
      await idbDel("contacts", id);
    },

    toggleFavorite: (id) => {
      const c = get().contacts.find((x) => x.id === id);
      if (!c) return;
      const next = touch({ ...c, favorite: !c.favorite });
      set((s) => ({ contacts: s.contacts.map((x) => (x.id === id ? next : x)) }));
      persistContact(next);
    },

    mergeContacts: (primaryId, dupId) => {
      const { contacts, entries, lists, notes, reminders, banks, recents, attachments } = get();
      const primary = contacts.find((c) => c.id === primaryId);
      const dup = contacts.find((c) => c.id === dupId);
      if (!primary || !dup) return;

      const unionById = <T extends { id: string }>(a: T[], b: T[]) => {
        const ids = new Set(a.map((x) => x.id));
        return [...a, ...b.filter((x) => !ids.has(x.id))];
      };
      const phoneKeys = new Set(primary.phones.map((p) => normalizePhone(p.number)));
      const merged: Contact = touch({
        ...primary,
        phones: [
          ...primary.phones,
          ...dup.phones.filter((p) => !phoneKeys.has(normalizePhone(p.number))),
        ],
        emails: unionById(primary.emails, dup.emails),
        links: unionById(primary.links, dup.links),
        socials: unionById(primary.socials, dup.socials),
        addresses: unionById(primary.addresses, dup.addresses),
        customFields: unionById(primary.customFields, dup.customFields),
        tags: [...new Set([...primary.tags, ...dup.tags])],
        favorite: primary.favorite || dup.favorite,
        birthday: primary.birthday || dup.birthday,
        note: [primary.note, dup.note].filter(Boolean).join("\n") || undefined,
      });

      const remapOwner = <T extends { ownerId: OwnerId }>(arr: T[], fn: (x: T) => void) =>
        arr.map((x) => {
          if (x.ownerId === dupId) {
            const n = { ...x, ownerId: primaryId, updatedAt: now() };
            fn(n as T);
            return n;
          }
          return x;
        });

      const nextEntries = remapOwner(entries, (e) => persistEntry(e));
      const nextLists = remapOwner(lists, (l) => persistList(l));
      const nextNotes = remapOwner(notes, (n) => persistNote(n));
      const nextReminders = remapOwner(reminders, (r) => persistReminder(r));
      const nextBanks = remapOwner(banks, (b) => persistBank(b));
      const nextRecents = recents.map((r) =>
        r.contactId === dupId ? (persistRecent({ ...r, contactId: primaryId }), { ...r, contactId: primaryId }) : r
      );
      const nextAttachments = attachments.map((a) =>
        a.parentType === "contact" && a.parentId === dupId
          ? (persistAttachment({ ...a, parentId: primaryId }), { ...a, parentId: primaryId })
          : a
      );

      set({
        contacts: contacts.map((x) => (x.id === primaryId ? merged : x)).filter((x) => x.id !== dupId),
        entries: nextEntries,
        lists: nextLists,
        notes: nextNotes,
        reminders: nextReminders,
        banks: nextBanks,
        recents: nextRecents,
        attachments: nextAttachments,
      });
      persistContact(merged);
      void idbDel("contacts", dupId);
    },

    findDuplicates: () => {
      const { contacts } = get();
      const live = contacts.filter((c) => !c.deletedAt);
      const results: { primary: Contact; dup: Contact; reason: string }[] = [];
      const seen = new Set<ID>();
      for (let i = 0; i < live.length; i++) {
        for (let j = i + 1; j < live.length; j++) {
          const a = live[i];
          const b = live[j];
          if (seen.has(a.id) || seen.has(b.id)) continue;
          const aPhones = new Set(a.phones.map((p) => normalizePhone(p.number)));
          const sharedPhone = b.phones.some((p) => aPhones.has(normalizePhone(p.number)));
          const sameName =
            displayName(a).toLowerCase() === displayName(b).toLowerCase() && displayName(a) !== "Unknown";
          const aEmails = new Set(a.emails.map((e) => e.address.toLowerCase()));
          const sharedEmail = b.emails.some((e) => aEmails.has(e.address.toLowerCase()));
          if (sharedPhone || (sameName && sharedEmail)) {
            results.push({
              primary: a,
              dup: b,
              reason: sharedPhone ? "Same phone number" : "Same name and email",
            });
            seen.add(b.id);
          }
        }
      }
      return results;
    },

    /* ---------------- ledger entries ---------------- */
    saveEntry: (input) => {
      const existing = input.id ? get().entries.find((e) => e.id === input.id) : undefined;
      const id = input.id || uid();
      const e: LedgerEntry = {
        id,
        ownerId: input.ownerId,
        type: input.type,
        amount: r2Fallback(input.amount ?? existing?.amount ?? 0),
        payments: input.payments ?? existing?.payments ?? [],
        date: input.date ?? existing?.date ?? todayStr(),
        dueDate: input.dueDate ?? existing?.dueDate,
        category: input.category ?? existing?.category,
        kind: input.kind ?? existing?.kind,
        method: input.method ?? existing?.method,
        note: input.note ?? existing?.note,
        listId: input.listId ?? existing?.listId,
        groupId: input.groupId === "" ? undefined : input.groupId ?? existing?.groupId,
        refs: input.refs ?? existing?.refs,
        items: input.items ?? existing?.items,
        attachments: input.attachments ?? existing?.attachments ?? [],
        createdAt: existing?.createdAt ?? now(),
        updatedAt: now(),
      };
      set((s) => ({
        entries: existing ? s.entries.map((x) => (x.id === id ? e : x)) : [...s.entries, e],
      }));
      persistEntry(e);
      return e;
    },

    deleteEntry: (id) => {
      const e = get().entries.find((x) => x.id === id);
      if (!e) return;
      const next = { ...e, deletedAt: now() };
      set((s) => ({ entries: s.entries.map((x) => (x.id === id ? next : x)) }));
      persistEntry(next);
      get().pushUndo("Entry moved to Trash", () => get().restoreEntry(id));
    },

    restoreEntry: (id) => {
      const e = get().entries.find((x) => x.id === id);
      if (!e) return;
      const next = { ...e, deletedAt: undefined };
      set((s) => ({ entries: s.entries.map((x) => (x.id === id ? next : x)) }));
      persistEntry(next);
    },

    purgeEntry: async (id) => {
      set((s) => ({ entries: s.entries.filter((x) => x.id !== id) }));
      await idbDel("entries", id);
    },

    /* ---------------- ledger groups ---------------- */
    saveGroup: (input) => {
      const existing = input.id ? get().groups.find((g) => g.id === input.id) : undefined;
      const id = input.id || uid();
      const g: LedgerGroup = {
        id,
        ownerId: input.ownerId,
        title: input.title?.trim() || existing?.title || "Group",
        note: input.note ?? existing?.note,
        refs: input.refs ?? existing?.refs ?? [],
        createdAt: existing?.createdAt ?? now(),
        updatedAt: now(),
      };
      set((s) => ({
        groups: existing ? s.groups.map((x) => (x.id === id ? g : x)) : [...s.groups, g],
      }));
      persistGroup(g);
      return g;
    },

    deleteGroup: (id) => {
      const g = get().groups.find((x) => x.id === id);
      if (!g) return;
      const members = get().entries.filter((e) => e.groupId === id && !e.deletedAt);
      // un-assign member entries (their history stays, they just become single entries)
      members.forEach((m) => {
        const next = touch({ ...m, groupId: undefined });
        set((s) => ({ entries: s.entries.map((x) => (x.id === m.id ? next : x)) }));
        persistEntry(next);
      });
      const next = { ...g, deletedAt: now() };
      set((s) => ({ groups: s.groups.map((x) => (x.id === id ? next : x)) }));
      persistGroup(next);
      get().pushUndo("Group deleted", () => {
        get().restoreGroup(id);
        members.forEach((m) => {
          const back = touch({ ...m, groupId: id });
          set((s) => ({ entries: s.entries.map((x) => (x.id === m.id ? back : x)) }));
          persistEntry(back);
        });
      });
    },

    restoreGroup: (id) => {
      const g = get().groups.find((x) => x.id === id);
      if (!g) return;
      const next = { ...g, deletedAt: undefined };
      set((s) => ({ groups: s.groups.map((x) => (x.id === id ? next : x)) }));
      persistGroup(next);
    },

    purgeGroup: async (id) => {
      set((s) => ({ groups: s.groups.filter((x) => x.id !== id) }));
      await idbDel("groups", id);
    },

    addPayment: (entryId, payment) => {
      const p: Payment = {
        id: uid(),
        amount: r2Fallback(payment.amount),
        date: payment.date || todayStr(),
        note: payment.note,
        method: payment.method,
        auto: payment.auto,
        refs: payment.refs,
        createdAt: now(),
      };
      updateEntry(entryId, (e) => ({ ...e, payments: [...e.payments, p] }));
    },

    removePayment: (entryId, paymentId) => {
      updateEntry(entryId, (e) => ({
        ...e,
        payments: e.payments.filter((p) => p.id !== paymentId),
      }));
    },

    removePaymentRefs: (entryId, paymentId, refs) => {
      updateEntry(entryId, (e) => ({
        ...e,
        payments: e.payments.map((p) => (p.id === paymentId ? { ...p, refs } : p)),
      }));
    },

    updatePaymentRefs: (entryId, paymentId, refs) => {
      updateEntry(entryId, (e) => ({
        ...e,
        payments: e.payments.map((p) => (p.id === paymentId ? { ...p, refs } : p)),
      }));
    },

    settleUp: (ownerId) => {
      const balance = calc.ownerBalance(get().entries, ownerId);
      if (Math.abs(balance) < 0.005) return 0;
      let left = Math.abs(balance);
      const ordered = get()
        .entries.filter((e) => e.ownerId === ownerId && !e.deletedAt)
        .sort((a, b) => a.date.localeCompare(b.date) || a.createdAt - b.createdAt);
      for (const e of ordered) {
        if (left < 0.005) break;
        const rem = calc.entryRemaining(e);
        // paying down: reduce |remaining| in the direction that zeroes the balance
        const effective = e.type === "credit" ? rem : -rem;
        if (Math.sign(effective) === Math.sign(balance)) continue; // same side, skip
        const pay = Math.min(Math.abs(rem), left);
        if (pay > 0.005) {
          get().addPayment(e.id, { amount: r2Fallback(pay), date: todayStr(), auto: "settle", note: "Settle up" });
          left = r2Fallback(left - pay);
        }
      }
      return Math.abs(balance);
    },

    moveEntity: (kind, id, ownerId) => {
      if (ownerId === get().entries.find((e) => e.id === id)?.ownerId && kind === "entry") return;
      switch (kind) {
        case "entry":
          updateEntry(id, (e) => ({ ...e, ownerId }));
          break;
        case "group":
          set((s) => {
            const g = s.groups.find((x) => x.id === id);
            if (g) {
              const next = touch({ ...g, ownerId });
              persistGroup(next);
              return { groups: s.groups.map((x) => (x.id === id ? next : x)) };
            }
            return {};
          });
          // move the member entries along with the group
          set((s) => ({
            entries: s.entries.map((e) =>
              e.groupId === id && !e.deletedAt ? touch({ ...e, ownerId }) : e
            ),
          }));
          get().entries.filter((e) => e.groupId === id).forEach((e) => e.ownerId === ownerId && persistEntry(e));
          break;
        case "list":
          updateList(id, (l) => ({ ...l, ownerId }));
          break;
        case "note":
          set((s) => {
            const n = s.notes.find((x) => x.id === id);
            if (n) {
              const next = touch({ ...n, ownerId });
              persistNote(next);
              return { notes: s.notes.map((x) => (x.id === id ? next : x)) };
            }
            return {};
          });
          break;
        case "reminder":
          updateReminder(id, (r) => ({ ...r, ownerId }));
          break;
        case "bank":
          set((s) => {
            const b = s.banks.find((x) => x.id === id);
            if (b) {
              const next = touch({ ...b, ownerId });
              persistBank(next);
              return { banks: s.banks.map((x) => (x.id === id ? next : x)) };
            }
            return {};
          });
          break;
      }
    },

    /* ---------------- lists ---------------- */
    saveList: (input) => {
      const existing = input.id ? get().lists.find((l) => l.id === input.id) : undefined;
      const id = input.id || uid();
      const l: CalcList = {
        id,
        ownerId: input.ownerId,
        kind: input.kind ?? existing?.kind ?? "calculating",
        title: input.title,
        note: input.note ?? existing?.note,
        discount: input.discount ?? existing?.discount,
        taxPct: input.taxPct ?? existing?.taxPct,
        extraCharge: input.extraCharge ?? existing?.extraCharge,
        isTemplate: input.isTemplate ?? existing?.isTemplate ?? false,
        postToLedger: input.postToLedger ?? existing?.postToLedger ?? false,
        repeat: input.repeat ?? existing?.repeat ?? "none",
        currency: input.currency ?? existing?.currency,
        items: input.items ?? existing?.items ?? [],
        attachments: input.attachments ?? existing?.attachments ?? [],
        createdAt: existing?.createdAt ?? now(),
        updatedAt: now(),
      };
      set((s) => ({ lists: existing ? s.lists.map((x) => (x.id === id ? l : x)) : [...s.lists, l] }));
      persistList(l);
      return l;
    },

    deleteList: (id) => {
      const l = get().lists.find((x) => x.id === id);
      if (!l) return;
      const next = { ...l, deletedAt: now() };
      set((s) => ({ lists: s.lists.map((x) => (x.id === id ? next : x)) }));
      persistList(next);
      get().pushUndo(`“${l.title}” moved to Trash`, () => get().restoreList(id));
    },

    restoreList: (id) => {
      const l = get().lists.find((x) => x.id === id);
      if (!l) return;
      const next = { ...l, deletedAt: undefined };
      set((s) => ({ lists: s.lists.map((x) => (x.id === id ? next : x)) }));
      persistList(next);
    },

    purgeList: async (id) => {
      set((s) => ({ lists: s.lists.filter((x) => x.id !== id) }));
      await idbDel("lists", id);
    },

    duplicateList: (id, opts) => {
      const l = get().lists.find((x) => x.id === id);
      if (!l) return undefined;
      const nid = uid();
      const copy: CalcList = {
        ...l,
        id: nid,
        title: opts?.asTemplate ? `${l.title} (template)` : `${l.title} (copy)`,
        isTemplate: !!opts?.asTemplate || (opts?.fresh ? false : l.isTemplate),
        postToLedger: l.postToLedger,
        items: opts?.fresh
          ? l.items.map((i) => ({
              ...i,
              id: uid(),
              payments: [],
              checked: false,
              createdAt: now(),
              updatedAt: now(),
            }))
          : l.items.map((i) => ({ ...i, id: uid(), createdAt: now(), updatedAt: now() })),
        attachments: [],
        createdAt: now(),
        updatedAt: now(),
        deletedAt: undefined,
      };
      set((s) => ({ lists: [...s.lists, copy] }));
      persistList(copy);
      return nid;
    },

    addListItem: (listId, item) => {
      const i: CalcList["items"][number] = {
        id: uid(),
        name: item.name || "New item",
        qty: item.qty ?? 1,
        unit: item.unit,
        unitPrice: r2Fallback(item.unitPrice ?? 0),
        discount: item.discount,
        taxPct: item.taxPct,
        payments: [],
        checked: false,
        date: item.date || todayStr(),
        dueDate: item.dueDate,
        priority: item.priority,
        note: item.note,
        sortOrder: item.sortOrder ?? now(),
        createdAt: now(),
        updatedAt: now(),
      };
      updateList(listId, (l) => ({ ...l, items: [...l.items, i] }));
    },

    updateListItem: (listId, itemId, patch) => {
      updateList(listId, (l) => ({
        ...l,
        items: l.items.map((i) => (i.id === itemId ? touch({ ...i, ...patch }) : i)),
      }));
    },

    removeListItem: (listId, itemId) => {
      updateList(listId, (l) => ({ ...l, items: l.items.filter((i) => i.id !== itemId) }));
    },

    addItemPayment: (listId, itemId, payment) => {
      const p: Payment = {
        id: uid(),
        amount: r2Fallback(payment.amount),
        date: payment.date || todayStr(),
        note: payment.note,
        method: payment.method,
        auto: payment.auto,
        refs: payment.refs,
        createdAt: now(),
      };
      updateList(listId, (l) => ({
        ...l,
        items: l.items.map((i) => (i.id === itemId ? { ...i, payments: [...i.payments, p] } : i)),
      }));
    },

    removeItemPayment: (listId, itemId, paymentId) => {
      updateList(listId, (l) => ({
        ...l,
        items: l.items.map((i) =>
          i.id === itemId ? { ...i, payments: i.payments.filter((p) => p.id !== paymentId) } : i
        ),
      }));
    },

    /** Tick = adds remaining balance as ONE payment. Untick = removes tick-payments. */
    toggleItemChecked: (listId, itemId, checked) => {
      const l = get().lists.find((x) => x.id === listId);
      const item = l?.items.find((i) => i.id === itemId);
      if (!l || !item) return;
      if (checked) {
        const remaining = calc.itemRemaining(item);
        if (remaining > 0.005) {
          get().addItemPayment(listId, itemId, {
            amount: remaining,
            date: todayStr(),
            auto: "tick",
          });
        }
        get().updateListItem(listId, itemId, { checked: true });
      } else {
        get().updateListItem(listId, itemId, {
          checked: false,
          payments: item.payments.filter((p) => p.auto !== "tick"),
        });
      }
    },

    markAllPaid: (listId) => {
      const l = get().lists.find((x) => x.id === listId);
      if (!l) return 0;
      let added = 0;
      l.items.forEach((i) => {
        const rem = calc.itemRemaining(i);
        if (rem > 0.005) {
          get().addItemPayment(listId, i.id, { amount: rem, date: todayStr(), auto: "tick" });
          get().updateListItem(listId, i.id, { checked: true });
          added++;
        }
      });
      return added;
    },

    clearPaid: (listId) => {
      updateList(listId, (l) => ({
        ...l,
        items: l.items.map((i) => ({ ...i, payments: [], checked: false })),
      }));
    },

    /* ---------------- notes ---------------- */
    saveNote: (input) => {
      const existing = input.id ? get().notes.find((n) => n.id === input.id) : undefined;
      const id = input.id || uid();
      const n: Note = {
        id,
        ownerId: input.ownerId,
        title: input.title ?? existing?.title ?? "Untitled note",
        body: input.body ?? existing?.body ?? "",
        tags: input.tags ?? existing?.tags ?? [],
        pinned: input.pinned ?? existing?.pinned ?? false,
        color: input.color ?? existing?.color,
        links: input.links ?? existing?.links ?? [],
        attachments: input.attachments ?? existing?.attachments ?? [],
        createdAt: existing?.createdAt ?? now(),
        updatedAt: now(),
      };
      set((s) => ({ notes: existing ? s.notes.map((x) => (x.id === id ? n : x)) : [...s.notes, n] }));
      persistNote(n);
      return n;
    },

    deleteNote: (id) => {
      const n = get().notes.find((x) => x.id === id);
      if (!n) return;
      const next = { ...n, deletedAt: now() };
      set((s) => ({ notes: s.notes.map((x) => (x.id === id ? next : x)) }));
      persistNote(next);
      get().pushUndo("Note moved to Trash", () => get().restoreNote(id));
    },

    restoreNote: (id) => {
      const n = get().notes.find((x) => x.id === id);
      if (!n) return;
      const next = { ...n, deletedAt: undefined };
      set((s) => ({ notes: s.notes.map((x) => (x.id === id ? next : x)) }));
      persistNote(next);
    },

    purgeNote: async (id) => {
      set((s) => ({ notes: s.notes.filter((x) => x.id !== id) }));
      await idbDel("notes", id);
    },

    /* ---------------- reminders ---------------- */
    saveReminder: (input) => {
      const existing = input.id ? get().reminders.find((r) => r.id === input.id) : undefined;
      const id = input.id || uid();
      const r: Reminder = {
        id,
        ownerId: input.ownerId,
        title: input.title,
        notes: input.notes ?? existing?.notes,
        kind: input.kind ?? existing?.kind ?? "custom",
        dueAt: input.dueAt ?? existing?.dueAt ?? now(),
        repeat: input.repeat ?? existing?.repeat ?? "none",
        leadMinutes: input.leadMinutes ?? existing?.leadMinutes,
        links: input.links ?? existing?.links ?? [],
        done: input.done ?? existing?.done ?? false,
        doneAt: input.doneAt ?? existing?.doneAt,
        snoozedUntil: input.snoozedUntil ?? existing?.snoozedUntil,
        lastFiredAt: input.lastFiredAt ?? existing?.lastFiredAt,
        firedLeads: input.firedLeads ?? existing?.firedLeads ?? [],
        attachments: input.attachments ?? existing?.attachments ?? [],
        createdAt: existing?.createdAt ?? now(),
        updatedAt: now(),
      };
      set((s) => ({
        reminders: existing ? s.reminders.map((x) => (x.id === id ? r : x)) : [...s.reminders, r],
      }));
      persistReminder(r);
      return r;
    },

    deleteReminder: (id) => {
      const r = get().reminders.find((x) => x.id === id);
      if (!r) return;
      const next = { ...r, deletedAt: now() };
      set((s) => ({ reminders: s.reminders.map((x) => (x.id === id ? next : x)) }));
      persistReminder(next);
      get().pushUndo("Reminder moved to Trash", () => get().restoreReminder(id));
    },

    restoreReminder: (id) => {
      const r = get().reminders.find((x) => x.id === id);
      if (!r) return;
      const next = { ...r, deletedAt: undefined };
      set((s) => ({ reminders: s.reminders.map((x) => (x.id === id ? next : x)) }));
      persistReminder(next);
    },

    purgeReminder: async (id) => {
      set((s) => ({ reminders: s.reminders.filter((x) => x.id !== id) }));
      await idbDel("reminders", id);
    },

    completeReminder: (id) => {
      updateReminder(id, (r) => ({
        ...r,
        done: true,
        doneAt: now(),
        snoozedUntil: undefined,
      }));
    },

    reopenReminder: (id) => {
      updateReminder(id, (r) => ({ ...r, done: false, doneAt: undefined }));
    },

    snoozeReminder: (id, until) => {
      updateReminder(id, (r) => ({ ...r, snoozedUntil: until }));
    },

    fireReminder: (id) => {
      updateReminder(id, (r) => {
        const next = calc.nextOccurrence(r.dueAt, r.repeat);
        return {
          ...r,
          lastFiredAt: now(),
          firedLeads: [],
          snoozedUntil: undefined,
          dueAt: r.repeat !== "none" ? next : r.dueAt,
        };
      });
    },

    /* ---------------- banks ---------------- */
    saveBank: (input) => {
      const existing = input.id ? get().banks.find((b) => b.id === input.id) : undefined;
      const id = input.id || uid();
      const b: BankAccount = {
        id,
        ownerId: input.ownerId,
        label: input.label,
        holderName: input.holderName ?? existing?.holderName,
        bankName: input.bankName ?? existing?.bankName,
        accountNumber: input.accountNumber ?? existing?.accountNumber,
        iban: input.iban ?? existing?.iban,
        swift: input.swift ?? existing?.swift,
        upiId: input.upiId ?? existing?.upiId,
        branch: input.branch ?? existing?.branch,
        notes: input.notes ?? existing?.notes,
        balance: input.balance ?? existing?.balance,
        currency: input.currency ?? existing?.currency,
        attachments: input.attachments ?? existing?.attachments ?? [],
        createdAt: existing?.createdAt ?? now(),
        updatedAt: now(),
      };
      set((s) => ({ banks: existing ? s.banks.map((x) => (x.id === id ? b : x)) : [...s.banks, b] }));
      persistBank(b);
      return b;
    },

    deleteBank: (id) => {
      const b = get().banks.find((x) => x.id === id);
      if (!b) return;
      const next = { ...b, deletedAt: now() };
      set((s) => ({ banks: s.banks.map((x) => (x.id === id ? next : x)) }));
      persistBank(next);
      get().pushUndo("Account moved to Trash", () => get().restoreBank(id));
    },

    restoreBank: (id) => {
      const b = get().banks.find((x) => x.id === id);
      if (!b) return;
      const next = { ...b, deletedAt: undefined };
      set((s) => ({ banks: s.banks.map((x) => (x.id === id ? next : x)) }));
      persistBank(next);
    },

    purgeBank: async (id) => {
      set((s) => ({ banks: s.banks.filter((x) => x.id !== id) }));
      await idbDel("banks", id);
    },

    /* ---------------- calls + messages ---------------- */
    addCall: (c) => {
      const call: CallLog = {
        id: uid(),
        phone: c.phone,
        contactId: c.contactId,
        direction: c.direction,
        at: c.at ?? now(),
        duration: c.duration ?? 0,
        via: c.via ?? "app",
        note: c.note,
        createdAt: now(),
      };
      set((s) => ({ recents: [call, ...s.recents] }));
      persistRecent(call);
    },

    deleteRecent: (id) => {
      set((s) => ({ recents: s.recents.filter((r) => r.id !== id) }));
      void idbDel("recents", id);
    },

    clearRecents: () => {
      set({ recents: [] });
      void idbClear("recents");
    },

    logMessage: (ownerId, channel, text) => {
      const m: MessageLog = {
        id: uid(),
        ownerId,
        channel,
        text,
        date: now(),
        createdAt: now(),
      };
      set((s) => ({ messages: [m, ...s.messages] }));
      persistMessage(m);
    },

    /* ---------------- attachments ---------------- */
    addAttachment: async (file, parentType, parentId) => {
      try {
        const id = uid();
        let blob: Blob = file;
        let kind: AttachmentMeta["kind"] = "file";
        let thumb: string | undefined;

        if (file.type.startsWith("image/")) {
          kind = "image";
          const processed = await compressImage(file, 1600, 0.82);
          blob = processed.blob;
          const t = await compressImage(file, 240, 0.6);
          thumb = await new Promise<string>((resolve) => {
            const r = new FileReader();
            r.onload = () => resolve(String(r.result));
            r.onerror = () => resolve("");
            r.readAsDataURL(t.blob);
          });
        } else if (file.type === "application/pdf") {
          kind = "pdf";
        }

        const meta: AttachmentMeta = {
          id,
          parentId,
          parentType,
          name: file.name || `file-${new Date().toISOString().slice(0, 10)}`,
          mimeType: file.type || "application/octet-stream",
          size: blob.size,
          kind,
          thumb,
          createdAt: now(),
        };
        await blobPut(id, blob);
        set((s) => ({ attachments: [...s.attachments, meta] }));
        persistAttachment(meta);
        return meta;
      } catch {
        return null;
      }
    },

    removeAttachment: async (id) => {
      const meta = get().attachments.find((a) => a.id === id);
      if (!meta) return;
      set((s) => ({ attachments: s.attachments.filter((a) => a.id !== id) }));
      await idbDel("attachments", id);
      await blobDel(id);
    },

    attachmentBlob: (id) => blobGet(id),

    /* ---------------- settings + data ---------------- */
    updateSettings: (patch) => {
      const next = { ...get().settings, ...patch };
      set({ settings: next });
      persistSettings(next);
    },

    addCustomCategory: (label: string) => {
      const name = label.trim();
      if (!name) return;
      const cur = get().settings.customCategories ?? [];
      if (cur.some((c) => c.toLowerCase() === name.toLowerCase())) return;
      get().updateSettings({ customCategories: [...cur, name] });
    },

    removeCustomCategory: (label: string) => {
      const cur = get().settings.customCategories ?? [];
      get().updateSettings({ customCategories: cur.filter((c) => c !== label) });
    },

    importContacts: (list, mode) => {
      let added = 0;
      let merged = 0;
      let skipped = 0;
      list.forEach((partial) => {
        const phones = (partial.phones || []).map((p) => ({
          ...p,
          id: p.id || uid(),
          number: p.number,
        }));
        const key = phones[0] ? normalizePhone(phones[0].number) : "";
        const existing = key
          ? get().contacts.find(
              (c) =>
                !c.deletedAt &&
                c.phones.some((p) => normalizePhone(p.number) === key)
            )
          : undefined;
        if (existing) {
          if (mode === "merge") {
            const newPhones = phones.filter(
              (p) => !existing.phones.some((ep) => normalizePhone(ep.number) === normalizePhone(p.number))
            );
            const newEmails = (partial.emails || []).filter(
              (e) => !existing.emails.some((ee) => ee.address.toLowerCase() === e.address.toLowerCase())
            );
            get().saveContact({
              ...existing,
              phones: [...existing.phones, ...newPhones],
              emails: [...existing.emails, ...newEmails],
              company: existing.company || partial.company,
              birthday: existing.birthday || partial.birthday,
              note: [existing.note, partial.note].filter(Boolean).join("\n") || undefined,
            });
            merged++;
          } else {
            skipped++;
          }
        } else {
          get().saveContact({ ...partial, phones, emails: partial.emails || [] } as Partial<Contact>);
          added++;
        }
      });
      return { added, merged, skipped };
    },

    wipeAll: async () => {
      const stores = ["contacts", "entries", "groups", "lists", "notes", "reminders", "banks", "recents", "messages", "attachments", "blobs"] as const;
      await Promise.all(stores.map((s) => idbClear(s)));
      await idbPutKey("meta", "demoSeeded", true);
      set({
        contacts: [],
        entries: [],
        groups: [],
        lists: [],
        notes: [],
        reminders: [],
        banks: [],
        recents: [],
        messages: [],
        attachments: [],
        lastUndo: null,
      });
    },
  };
});

/* ------------------------------------------------------------------ */
/* helpers                                                              */
/* ------------------------------------------------------------------ */

async function compressImage(
  file: File,
  maxSide: number,
  quality: number
): Promise<{ blob: Blob; width: number; height: number }> {
  const img = await new Promise<HTMLImageElement>((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const i = new Image();
    i.onload = () => {
      URL.revokeObjectURL(url);
      resolve(i);
    };
    i.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error("bad image"));
    };
    i.src = url;
  });
  const scale = Math.min(1, maxSide / Math.max(img.width, img.height));
  const w = Math.max(1, Math.round(img.width * scale));
  const h = Math.max(1, Math.round(img.height * scale));
  const canvas = document.createElement("canvas");
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext("2d");
  if (!ctx) return { blob: file, width: img.width, height: img.height };
  ctx.drawImage(img, 0, 0, w, h);
  const blob = await new Promise<Blob>((resolve) =>
    canvas.toBlob((b) => resolve(b || file), "image/jpeg", quality)
  );
  return { blob, width: w, height: h };
}

/* ------------------------------------------------------------------ */
/* demo seed data                                                       */
/* ------------------------------------------------------------------ */

function buildDemoData() {
  const t = now();
  const mk = (
    first: string,
    last: string,
    phone: string,
    favorite: boolean,
    extra: Partial<Contact> = {}
  ): Contact => ({
    id: uid(),
    firstName: first,
    lastName: last,
    phones: [{ id: uid(), label: "mobile", number: phone, primary: true }],
    emails: [],
    links: [],
    socials: [],
    addresses: [],
    customFields: [],
    tags: [],
    favorite,
    createdAt: t,
    updatedAt: t,
    ...extra,
  });

  const abel = mk("Abel", "Tesfaye", "+251 911 234 567", true, {
    company: "Tesfaye Trading",
    emails: [{ id: uid(), label: "work", address: "abel@tesfaye.com", primary: true }],
    socials: [{ id: uid(), app: "telegram", handle: "abelt" }],
    birthday: "1992-04-17",
    color: "#E4EDE7",
    textColor: "#527561",
    speedDial: "2",
  });
  const sara = mk("Sara", "Mohammed", "+251 912 345 678", true, {
    color: "#F7E8DF",
    textColor: "#B7785B",
    speedDial: "3",
  });
  const daniel = mk("Daniel", "Bekele", "+251 913 456 789", true, {
    color: "#E5E8F9",
    textColor: "#6B75B5",
  });
  const hana = mk("Hana", "Alemu", "+251 914 567 890", false, {
    color: "#F4E5EE",
    textColor: "#A65F88",
    socials: [{ id: uid(), app: "telegram", handle: "hanaa" }],
  });
  const yonas = mk("Yonas", "Girma", "+251 915 678 901", false, {
    color: "#F4EEDB",
    textColor: "#9B8243",
  });
  const mimi = mk("Mimi", "Hassan", "+251 916 789 012", false, {
    color: "#DFF0F0",
    textColor: "#4A8585",
  });
  const kebede = mk("Kebede", "Wolde", "+251 917 123 456", false, {
    company: "Wolde Store",
    color: "#F0E9E4",
    textColor: "#8A7360",
  });
  const contacts = [abel, sara, daniel, hana, yonas, mimi, kebede];

  const pay = (amount: number, daysAgo: number, auto?: Payment["auto"]): Payment => ({
    id: uid(),
    amount,
    date: new Date(t - daysAgo * 86400000).toISOString().slice(0, 10),
    auto,
    createdAt: t - daysAgo * 86400000,
  });

  const entries: LedgerEntry[] = [
    {
      id: uid(),
      ownerId: abel.id,
      type: "credit",
      amount: 5000,
      payments: [pay(2000, 6)],
      date: new Date(t - 20 * 86400000).toISOString().slice(0, 10),
      category: "Loan",
      method: "cash",
      note: "Borrowed for shop restock",
      attachments: [],
      createdAt: t,
      updatedAt: t,
    },
    {
      id: uid(),
      ownerId: abel.id,
      type: "credit",
      amount: 750,
      payments: [pay(250, 3)],
      date: new Date(t - 4 * 86400000).toISOString().slice(0, 10),
      category: "Sale",
      note: "Weekly supply pickup",
      attachments: [],
      createdAt: t,
      updatedAt: t,
    },
    {
      id: uid(),
      ownerId: sara.id,
      type: "credit",
      amount: 1200,
      payments: [pay(1200, 2, "settle")],
      date: new Date(t - 12 * 86400000).toISOString().slice(0, 10),
      category: "Sale",
      attachments: [],
      createdAt: t,
      updatedAt: t,
    },
    {
      id: uid(),
      ownerId: daniel.id,
      type: "debit",
      amount: 800,
      payments: [pay(300, 4)],
      date: new Date(t - 9 * 86400000).toISOString().slice(0, 10),
      category: "Transport",
      attachments: [],
      createdAt: t,
      updatedAt: t,
    },
    {
      id: uid(),
      ownerId: hana.id,
      type: "credit",
      amount: 950,
      payments: [],
      dueDate: new Date(t + 3 * 86400000).toISOString().slice(0, 10),
      date: new Date(t - 2 * 86400000).toISOString().slice(0, 10),
      category: "General",
      attachments: [],
      createdAt: t,
      updatedAt: t,
    },
    {
      id: uid(),
      ownerId: "general",
      type: "debit",
      amount: 460,
      payments: [],
      date: todayStr(),
      category: "Groceries",
      note: "Monthly market run",
      attachments: [],
      createdAt: t,
      updatedAt: t,
    },
    {
      id: uid(),
      ownerId: "general",
      type: "credit",
      amount: 8500,
      payments: [],
      date: new Date(t - 5 * 86400000).toISOString().slice(0, 10),
      category: "Salary",
      attachments: [],
      createdAt: t,
      updatedAt: t,
    },
  ];

  const li = (
    name: string,
    qty: number,
    price: number,
    daysAgo = 0,
    payments: Payment[] = []
  ): CalcList["items"][number] => ({
    id: uid(),
    name,
    qty,
    unit: "pcs",
    unitPrice: price,
    payments,
    checked: payments.length > 0,
    date: new Date(t - daysAgo * 86400000).toISOString().slice(0, 10),
    sortOrder: t,
    createdAt: t,
    updatedAt: t,
  });

  const lists: CalcList[] = [
    {
      id: uid(),
      ownerId: yonas.id,
      kind: "calculating",
      title: "Shop order — March",
      postToLedger: true,
      repeat: "monthly",
      items: [
        li("Cooking oil 5L", 4, 420, 10),
        li("Sugar 1kg", 12, 88, 10),
        li("Flour 1kg", 10, 62, 8, [pay(300, 7, "tick")]),
        li("Tea 250g", 6, 95, 6),
        li("Soap", 20, 35, 6, [pay(400, 5), pay(120, 3)]),
      ],
      taxPct: 0,
      isTemplate: false,
      attachments: [],
      createdAt: t,
      updatedAt: t,
    },
    {
      id: uid(),
      ownerId: "general",
      kind: "checklist",
      title: "Monthly shopping",
      postToLedger: false,
      repeat: "none",
      items: [
        { ...li("Rice 5kg", 1, 0), checked: true },
        li("Pasta", 1, 0),
        li("Milk powder", 1, 0),
        { ...li("Detergent", 1, 0), checked: true },
      ],
      isTemplate: false,
      attachments: [],
      createdAt: t,
      updatedAt: t,
    },
    {
      id: uid(),
      ownerId: "general",
      kind: "calculating",
      title: "Rent & utilities (template)",
      isTemplate: true,
      postToLedger: false,
      repeat: "monthly",
      items: [
        li("House rent", 1, 9000),
        li("Electricity", 1, 240),
        li("Water", 1, 60),
        li("Internet", 1, 350),
      ],
      attachments: [],
      createdAt: t,
      updatedAt: t,
    },
  ];

  const notes: Note[] = [
    {
      id: uid(),
      ownerId: abel.id,
      title: "Repayment promise",
      body: "Abel promised to clear the remaining 3,000 before the end of the month. Prefers cash.",
      tags: ["money"],
      pinned: true,
      links: [],
      attachments: [],
      createdAt: t,
      updatedAt: t,
    },
    {
      id: uid(),
      ownerId: "general",
      title: "Market prices",
      body: "Oil went up by 30 Br this week. Compare with Merkato wholesale before restocking.",
      tags: ["shopping"],
      pinned: false,
      links: [],
      attachments: [],
      createdAt: t,
      updatedAt: t,
    },
  ];

  const reminders: Reminder[] = [
    {
      id: uid(),
      ownerId: abel.id,
      title: "Collect remaining from Abel",
      notes: "Remaining 3,000 Br on the loan.",
      kind: "payment",
      dueAt: t + 60 * 60 * 1000,
      repeat: "none",
      links: [],
      done: false,
      attachments: [],
      createdAt: t,
      updatedAt: t,
    },
    {
      id: uid(),
      ownerId: "general",
      title: "Pay house rent",
      kind: "payment",
      dueAt: t + 3 * 86400000,
      repeat: "monthly",
      links: [],
      done: false,
      attachments: [],
      createdAt: t,
      updatedAt: t,
    },
    {
      id: uid(),
      ownerId: hana.id,
      title: `Hana's birthday 🎂`,
      kind: "birthday",
      dueAt: t + 6 * 86400000,
      repeat: "yearly",
      links: [],
      done: false,
      attachments: [],
      createdAt: t,
      updatedAt: t,
    },
  ];

  const banks: BankAccount[] = [
    {
      id: uid(),
      ownerId: "general",
      label: "Main account",
      holderName: "Me",
      bankName: "Commercial Bank",
      accountNumber: "1000234567890",
      branch: "Bole Branch",
      balance: 24350,
      currency: "ETB",
      attachments: [],
      createdAt: t,
      updatedAt: t,
    },
    {
      id: uid(),
      ownerId: abel.id,
      label: "Abel — CBE",
      holderName: "Abel Tesfaye",
      bankName: "Commercial Bank",
      accountNumber: "1000987654321",
      swift: "CBETETAA",
      attachments: [],
      createdAt: t,
      updatedAt: t,
    },
  ];

  const recents: CallLog[] = [
    { id: uid(), phone: "+251 911 234 567", contactId: abel.id, direction: "outgoing", at: t - 3600000, duration: 254, via: "app", createdAt: t },
    { id: uid(), phone: "+251 912 345 678", contactId: sara.id, direction: "incoming", at: t - 7200000, duration: 96, via: "app", createdAt: t },
    { id: uid(), phone: "+251 921 111 222", direction: "missed", at: t - 10800000, duration: 0, via: "app", createdAt: t },
    { id: uid(), phone: "+251 913 456 789", contactId: daniel.id, direction: "outgoing", at: t - 86400000, duration: 51, via: "tel", createdAt: t },
    { id: uid(), phone: "+251 914 567 890", contactId: hana.id, direction: "outgoing", at: t - 90000000, duration: 0, via: "app", createdAt: t },
  ];

  /* demo ledger group — shows grouped entries with calculated totals */
  const shopGroup: LedgerGroup = {
    id: uid(),
    ownerId: abel.id,
    title: "Shop restock",
    note: "Everything tied to the shop restock — group totals update as payments land.",
    refs: [],
    createdAt: t,
    updatedAt: t,
  };
  entries[0].groupId = shopGroup.id;
  entries[1].groupId = shopGroup.id;

  return { contacts, entries, groups: [shopGroup], lists, notes, reminders, banks, recents };
}

/* selectors — NOTE: always select stable references (arrays/objects from state),
   then filter in useMemo at the call site. Array-returning filters here would
   create new references each call and crash React 19 + zustand v5. */
export const useContactsState = () => useApp((s) => s.contacts);
export const useEntriesState = () => useApp((s) => s.entries);

export const useContact = (id?: ID | "general") =>
  useApp((s) => (id && id !== "general" ? s.contacts.find((c) => c.id === id && !c.deletedAt) : undefined));

export const useHydrated = () => useApp((s) => s.hydrated);
