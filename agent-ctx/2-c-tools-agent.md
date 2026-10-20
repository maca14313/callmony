# Task 2-c — Tools Agent (Reminders / Notes / Bank / Trash)

Read this BEFORE working on related files. See also `/home/z/my-project/worklog.md` (foundation + conventions).

## Files I own (Task 2-c) — do not edit if you are not me

1. `src/components/app/views/tools-views.tsx` — `RemindersView()`, `TrashView()`
2. `src/components/app/overlays/tools-overlays.tsx` — `NoteEdit({noteId?, ownerId, linkTo?})`, `ReminderEdit({reminderId?, ownerId, prefill?})`, `BankEdit({bankId?, ownerId})`
3. `src/components/app/sections/NotesSection.tsx` — `NotesSection({ownerId})`
4. `src/components/app/sections/RemindersSection.tsx` — `RemindersSection({ownerId})` + **shared exports**: `ReminderRow({reminder, compact?})`, `ReminderActions({reminder, open, onClose})`, `dueLabel`, `reminderStatus`, `nextBirthday9`, `hhmm`
5. `src/components/app/sections/BankSection.tsx` — `BankSection({ownerId})` + `bankDetailsText(b, fallbackCurrency)`

## Conventions I followed (keep them if you touch these files)

- Zustand v5 selectors MUST return stable references (arrays/objects from state, `.find()`, or primitives). NEVER `useApp((s) => s.x.filter(...))` — new array per getSnapshot → infinite loop crash (see Blockers).
- Store `??` semantics: `undefined` keeps the previous value when editing. To CLEAR a field pass `""` / `[]` (my overlays do this for note.color, reminder.leadMinutes, text fields).
- New-record attachments use the "save draft first" flow (spec-sanctioned): `AttachArea` shows "Save draft & attach" until a real id exists, then the kit `AttachButton` + `AttachmentStrip ids={...}` take over; every `onAttached` pushes the meta id into the entity's `attachments` and re-saves.
- Sections expect the parent (ContactDetail / GeneralView) to provide horizontal padding; they render their own `SectionLabel` header + add button.
- `useConfirm()` for every destructive action; `toastUndoLast()` after soft deletes; purge (trash "delete forever") is permanent → plain `toastOk`.

## Blockers found in SHARED files (I did not touch them — please fix centrally)

1. **`src/components/app/views/KeypadView.tsx:29`** — `useApp((s) => s.contacts.filter((c) => !c.deletedAt))` returns a fresh array on every `getSnapshot` → React 19 + zustand v5 log "The result of getSnapshot should be cached to avoid an infinite loop" → "Maximum update depth exceeded" → **the entire app crashes on hydration** (SSR returns 200, client dies). Keypad is the default tab, so nothing renders. Fix: `const contactsAll = useApp((s) => s.contacts); const contacts = useMemo(() => contactsAll.filter(...), [contactsAll]);`
2. **`src/components/app/kit/ContactPicker.tsx:26`** — same unstable-filter pattern → same crash the moment any ContactPicker opens (ReminderEdit owner change, entry moves…). Same fix.
3. `/icons/icon-192.png` and `/icons/icon-512.png` 404 (manifest/metadata reference missing files).
4. Store: consider documenting that `undefined` in a save patch means "keep existing" (bit me for note.color / leadMinutes clearing).

## Status

- tsc: clean for my 5 files · eslint: clean for my 5 files · dev server: `GET / 200` (I restarted `.zscripts/dev.sh` — the PID was dead mid-session).
- Runtime smoke test of my views is impossible until Blocker 1/2 are fixed (client crashes before any tab renders).
