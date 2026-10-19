"use client";

import { create } from "zustand";
import type { ID, LinkRef, Reminder } from "./types";

export type Tab =
  | "keypad"
  | "recents"
  | "contacts"
  | "favorites"
  | "reminders"
  | "general"
  | "trash";

export type Overlay =
  | { type: "contact"; contactId: ID }
  | { type: "contactEdit"; contactId?: ID; prefillPhone?: string; prefillName?: string }
  | { type: "list"; listId: ID }
  | { type: "listEdit"; listId?: ID; ownerId: ID | "general"; kind?: "calculating" | "simple" | "checklist" | "todo" }
  | { type: "entryEdit"; entryId?: ID; ownerId: ID | "general"; entryType?: "credit" | "debit" }
  | { type: "noteEdit"; noteId?: ID; ownerId: ID | "general"; linkTo?: LinkRef }
  | { type: "reminderEdit"; reminderId?: ID; ownerId: ID | "general"; prefill?: Partial<Reminder> }
  | { type: "bankEdit"; bankId?: ID; ownerId: ID | "general" }
  | { type: "settings" }
  | { type: "search"; query?: string }
  | { type: "calculator"; onPickKey?: string }
  | { type: "call"; phone: string; contactId?: ID; direction?: "incoming" | "outgoing" | "missed" }
  | { type: "merge" }
  | { type: "speedDial" }
  | { type: "import" }
  | { type: "backup" }
  | { type: "storage" }
  | { type: "about" };

interface UIState {
  tab: Tab;
  overlayStack: Overlay[];
  moreOpen: boolean;
  locked: boolean;
  alarmQueue: Reminder[];
  calculatorValue: number | null;
  calculatorPickHandler: ((value: number) => void) | null;

  setTab: (t: Tab) => void;
  open: (o: Overlay) => void;
  pop: () => void;
  closeAll: () => void;
  closeType: (type: Overlay["type"]) => void;
  setMoreOpen: (v: boolean) => void;
  setLocked: (v: boolean) => void;
  pushAlarm: (r: Reminder) => void;
  shiftAlarm: () => Reminder | undefined;
  openCalculator: (onPick?: (value: number) => void) => void;
  setCalculatorValue: (v: number | null) => void;
}

export const useUI = create<UIState>((set, get) => ({
  tab: "keypad",
  overlayStack: [],
  moreOpen: false,
  locked: false,
  alarmQueue: [],
  calculatorValue: null,
  calculatorPickHandler: null,

  setTab: (t) => set({ tab: t, moreOpen: false }),
  open: (o) => set((s) => ({ overlayStack: [...s.overlayStack, o], moreOpen: false })),
  pop: () => set((s) => ({ overlayStack: s.overlayStack.slice(0, -1) })),
  closeAll: () => set({ overlayStack: [], calculatorPickHandler: null }),
  closeType: (type) =>
    set((s) => ({ overlayStack: s.overlayStack.filter((o) => o.type !== type) })),
  setMoreOpen: (v) => set({ moreOpen: v }),
  setLocked: (v) => set({ locked: v }),
  pushAlarm: (r) =>
    set((s) => (s.alarmQueue.some((a) => a.id === r.id) ? s : { alarmQueue: [...s.alarmQueue, r] })),
  shiftAlarm: () => {
    const [first, ...rest] = get().alarmQueue;
    set({ alarmQueue: rest });
    return first;
  },
  openCalculator: (onPick) =>
    set({ overlayStack: [...get().overlayStack, { type: "calculator" }], calculatorPickHandler: onPick ?? null }),
  setCalculatorValue: (v) => set({ calculatorValue: v }),
}));
