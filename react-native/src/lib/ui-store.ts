/* callmony — UI/navigation store (tabs + overlay stack + alarm queue) */

import { create } from "zustand";
import type { EntryKind, LinkRef, Reminder } from "./types";

export type Tab = "contacts" | "recents" | "general" | "reminders" | "trash";

export type Overlay =
  | { type: "contact"; recordID: string; initialSeg?: "ledger" | "lists" | "notes" | "bank" | "reminders" | "info" }
  /* v1.6.8 — contact editing/creating moved onto the overlay stack (it used
     to be local state inside ContactDetail): the store can now SEE that an
     editor is open and defer the full address-book refresh (the bridge
     serialization of a whole book was the "freeze" while typing/saving),
     and the creator is reachable from anywhere in one open() call. */
  | { type: "contactEdit"; recordID: string }
  | { type: "contactNew"; phone?: string; name?: string }
  | { type: "list"; listId: string }
  | { type: "listEdit"; listId?: string; ownerId: string; kind?: "calculating" | "simple" | "checklist" | "todo" }
  | { type: "entryEdit"; entryId?: string; ownerId: string; entryType?: "credit" | "debit"; kind?: EntryKind; groupId?: string }
  | { type: "noteEdit"; noteId?: string; ownerId: string; linkTo?: LinkRef }
  | { type: "reminderEdit"; reminderId?: string; ownerId: string; prefill?: Partial<Reminder> }
  | { type: "bankEdit"; bankId?: string; ownerId: string }
  | { type: "settings" }
  | { type: "search"; query?: string }
  | { type: "calculator"; onPickKey?: string }
  | { type: "merge" }
  | { type: "speedDial" }
  | { type: "backup" }
  | { type: "about" };

interface UIState {
  tab: Tab;
  overlayStack: Overlay[];
  moreOpen: boolean;
  locked: boolean;
  alarmQueue: Reminder[];
  calculatorValue: number | null;
  calculatorPickHandler: ((value: number) => void) | null;
  keypadOpen: boolean;

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
  clearCalculatorHandler: () => void;
  setKeypadOpen: (v: boolean) => void;
}

export const useUI = create<UIState>((set, get) => ({
  /* the app opens on RECENTS (dialer-style home), exactly like the user's
     phone app — Contacts is one tap away, and it loads its own data lazily */
  tab: "recents",
  overlayStack: [],
  moreOpen: false,
  locked: false,
  alarmQueue: [],
  calculatorValue: null,
  calculatorPickHandler: null,
  keypadOpen: false,

  setTab: (t) => set({ tab: t, moreOpen: false }),
  open: (o) => set((s) => ({ overlayStack: [...s.overlayStack, o], moreOpen: false })),
  pop: () => set((s) => ({ overlayStack: s.overlayStack.slice(0, -1) })),
  /* closeAll also dismisses the keypad half-modal and the More sheet —
     otherwise tapping the bottom nav while the keypad is open looks like
     "nothing happens" (the view switched behind the scrim). */
  closeAll: () =>
    set({ overlayStack: [], calculatorPickHandler: null, keypadOpen: false, moreOpen: false }),
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
  clearCalculatorHandler: () => set({ calculatorPickHandler: null }),
  setKeypadOpen: (v) => set({ keypadOpen: v }),
}));
