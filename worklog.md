# dial. — Contacts & Ledger · Project Worklog

Offline-first PWA (Next.js 16 App Router, TypeScript, Tailwind 4, no Prisma/no server DB).
All data lives in IndexedDB via a Zustand store. Single route `/`. Phone-frame UI (max-w-430) with
teal brand (#16a085), ink (#172b2a), canvas (#f7faf9) + dark mode (#0e1514 canvas, #2fc4a6 brand).

---
Task ID: 1
Agent: main (Z.ai Code)
Task: Foundation — theme, layout, types, calc engine, IndexedDB layer, Zustand store (all mutations), UI/navigation store, share/export/PDF libs, shared UI kit, App shell, KeypadView, PWA version API, typed stubs for feature modules.

Work Log:
- Rewrote `src/app/globals.css` — dial. design tokens mapped to Tailwind 4 `@theme inline` (kept shadcn tokens intact).
- Rewrote `src/app/layout.tsx` — Inter font, manifest/icons metadata, ThemeProvider (next-themes, class strategy), viewport-fit=cover.
- `src/lib/types.ts` — full data model (Contact with unlimited phones/emails/links/socials/addresses/customFields, LedgerEntry+Payment, CalcList+ListItem, Note, Reminder, BankAccount, CallLog, MessageLog, AttachmentMeta, Settings, BackupFile).
- `src/lib/calc.ts` — pure money math: lineTotal, listTotals (subtotal/list discount/tax/extra/grand/paid/pending), entryRemaining, ownerBalance, openListsPending (postToLedger), ownerFullBalance, nextOccurrence (repeats), dueStatus.
- `src/lib/db.ts` — IndexedDB wrapper (stores: contacts/entries/lists/notes/reminders/banks/recents/messages/attachments/meta/blobs; meta + blob helpers).
- `src/lib/utils.ts` — uid, dates, money formatting, phone normalize/pretty, T9 match, maskAccount, copyText, hashPin (PBKDF2), base64⇄blob, downloadBlob, vibrate.
- `src/lib/share.ts` — tel/sms/wa/tg/mailto links, Web Share helpers, primaryPhone/Email, telegramHandle, reminder templates + fillTemplate.
- `src/lib/store.ts` — THE state layer (Zustand `useApp`): hydrate (auto-seeds demo data first run), all CRUD with write-through persistence + soft delete (trash) + single-level undo, contacts (save/delete/restore/purge/toggleFavorite/mergeContacts/findDuplicates), entries (save/delete/addPayment/removePayment/settleUp/moveEntity), lists (save/delete/duplicateList{asTemplate,fresh}/addListItem/updateListItem/removeListItem/addItemPayment/removeItemPayment/toggleItemChecked/markAllPaid/clearPaid), notes, reminders (complete/reopen/snooze/fire), banks, calls (addCall/deleteRecent/clearRecents), messages (logMessage), attachments (addAttachment w/ image compression+thumb/removeAttachment/attachmentBlob), settings, importContacts (skip/merge), wipeAll.
- `src/lib/ui-store.ts` — `useUI`: tab (keypad/recents/contacts/favorites/reminders/general/trash), overlayStack (typed Overlay union), moreOpen, locked, alarmQueue, openCalculator(onPick).
- `src/lib/exporters.ts` — vCard export/parse (2.1/3.0/4.0 + quoted-printable), contacts CSV (Google format) + generic parse, entries CSV, buildBackup/parseBackupFile (files as base64).
- `src/lib/pdf.ts` — jsPDF: listToPdfBlob (a5 bill), statementToPdfBlob (a4 w/ running balance), listShareText, statementShareText (WhatsApp-ready text).
- `src/lib/pwa.ts` — registerSW (update toast), checkVersion (/api/version), useInstallPrompt, requestPersistence, useTick.
- `src/app/api/version/route.ts` — version endpoint.
- Shared UI kit in `src/components/app/kit/`: primitives (Avatar, Money, Button, IconButton, Chip, SectionCard, SectionLabel, EmptyState, Segmented, ProgressBar, Field, inputCls, SearchInput, BalancePill, cx), Sheet + BottomSheet (in-frame), ConfirmProvider/useConfirm (custom modal — REQUIRED for confirmations), toast helpers (toastOk/toastErr/toastUndo/toastUndoLast), SwipeRow (tap/long-press/swipe actions), Attachments (AttachButton/AttachmentStrip/AttachmentViewer/FilesOfOwner), LinkPicker (LinkChips/LinkPicker/AddLinkButton/useOpenLink), ContactPicker, Calculator (+CalculatorSheet+CalcButton), AlarmDialog (in-app alarm w/ beep + notification).
- `src/components/app/App.tsx` — phone frame shell: StatusBar (live clock), AppHeader (dial. logo / secondary back header), view router, BottomNav (Favorites/Recents/Contacts/Keypad/More + overdue badge), MoreSheet (General/Reminders/Calculator/Search/Trash/Settings/About + install), OverlayHost (stacked overlays → components), AlarmWatcher, AutolockWatcher (PIN), SW registration + version check.
- `src/components/app/views/KeypadView.tsx` — full keypad (T9 contact matching, exact match chip, speed-dial long-press, backspace hold-to-clear, keyboard support, call/add-contact/video buttons).
- Typed STUBS created for: views/contacts-views (2-a), views/money-views (2-b), views/tools-views (2-c), overlays/contacts-overlays (2-a), overlays/money-overlays (2-b), overlays/tools-overlays (2-c), overlays/data-overlays (2-d), sections/{Ledger,Lists}Section (2-b), sections/{Notes,Reminders,Bank}Section (2-c).
- Dev server verified: GET / 200.

Stage Summary:
- Foundation complete & compiling. All state logic + shared UI owned centrally.
- Agents 2-a…2-d MUST only replace their own stub files (contracts fixed) and must NOT modify shared files.

---

## SHARED CONVENTIONS (all agents MUST follow)

### Design tokens (Tailwind classes available)
- Backgrounds: `bg-canvas` (app bg), `bg-appbg` (page behind frame), `bg-surface` (cards), `bg-surface2` (subtle fill), `bg-brandsoft`/`bg-brandsoft2` (teal tint), `bg-negsoft` (red tint), `bg-brand` (teal), `bg-neg` (red)
- Text: `text-ink`, `text-mute`, `text-brand`, `text-brandink`, `text-pos`, `text-neg`, `text-amber`
- Borders: `border-line`, `border-line2`
- Shadows: `shadow-key`, `shadow-soft`, `shadow-floating`
- Radius style: cards `rounded-[22px]`, rows `rounded-2xl`, buttons `rounded-xl`, avatars `rounded-[17px]`
- NEVER use indigo/blue Tailwind defaults. Dark mode works automatically via tokens.
- Headings: view titles `text-[25px] font-bold tracking-tight`, sheet titles `text-[17px] font-bold`.

### Kit imports (from `@/components/app/kit/…`)
- `primitives`: `cx, Avatar{contact,name,size}, Money{value,signed}, Button{variant:brand|soft|surface|danger|ghost,size}, IconButton{variant,label}, Chip, SectionCard, SectionLabel, EmptyState{icon,title,hint,action}, Segmented{options,value,onChange}, ProgressBar{value}, Field{label,hint}, inputCls, SearchInput{value,onChange,placeholder}, BalancePill{value}`
- `Sheet`: `Sheet{title,subtitle,onBack,onClose,actions,footer,children}` (full-screen overlay; render `<Sheet …>` as your component root with `absolute inset-0` behavior built-in; use `useUI().pop()` for back), `BottomSheet{open,onClose,title,children}` (action menus/pickers)
- `Confirm`: `const confirm = useConfirm(); await confirm({title, message, confirmLabel, tone:'danger'|'brand'|'default'})` → REQUIRED for every destructive/irreversible action (user explicitly requested custom confirmation modals — never use window.confirm)
- `toast`: `toastOk(msg)`, `toastErr(msg)`, `toastUndo(label, onUndoFn)` — after store deletes, call `toastUndoLast()` (it wires the store undo)
- `SwipeRow`: `<SwipeRow left={[{key,icon,label,className:'bg-brand',onClick}]} right={[…]} onTap onLongPress>` — swipe actions + tap + long-press
- `Attachments`: `<AttachButton parentType parentId onAttached/>` (📎 attach anywhere), `<AttachmentStrip parentType parentId ids?/>` (thumb grid + viewer), `<FilesOfOwner ownerId/>` (Files tab)
- `LinkPicker`: `<LinkChips links onChange? onOpen?/>`, `<AddLinkButton links onChange/>`, `useOpenLink()`
- `ContactPicker`: `<ContactPicker open onClose onPick(ownerId) allowGeneral/>`
- `Calculator`: open via `useUI().openCalculator((value:number)=>{…})` from any amount input, or `CalcButton{onPick}`

### Store API (`useApp` from `@/lib/store`)
Read: `contacts, entries, lists, notes, reminders, banks, recents, messages, attachments, settings, hydrated`.
Mutate: `saveContact, deleteContact, restoreContact, purgeContact, toggleFavorite, mergeContacts(primaryId,dupId), findDuplicates()`, `saveEntry, deleteEntry, addPayment(entryId,{amount,date,note,method}), removePayment(entryId,paymentId), settleUp(ownerId), moveEntity(kind,id,ownerId)`, `saveList, deleteList, duplicateList(id,{asTemplate,fresh}), addListItem(listId,{name,qty,unit,unitPrice,…}), updateListItem, removeListItem, addItemPayment(listId,itemId,{amount,date,note}), removeItemPayment, toggleItemChecked(listId,itemId,checked), markAllPaid(listId), clearPaid(listId)`, `saveNote, deleteNote…`, `saveReminder, deleteReminder, completeReminder, snoozeReminder(id,until), fireReminder`, `saveBank, deleteBank…`, `addCall, clearRecents, logMessage`, `addAttachment(file,parentType,parentId), removeAttachment`, `updateSettings(patch)`.
Calc helpers from `@/lib/calc`: `lineTotal(item), itemPaid, itemRemaining, listTotals(list), entryRemaining, entrySignedRemaining, ownerBalance(entries,ownerId), openListsPending(lists,ownerId), ownerFullBalance, nextOccurrence, dueStatus`.
Utils from `@/lib/utils`: `displayName, initialsOf, money(n,currency,{signed}), fd(date), fdShort, relTime, dayGroup, durationStr, todayStr, normalizePhone, prettyPhone, maskAccount, copyText, bytesStr, downloadBlob, vibrate, uid, now`.
Share helpers from `@/lib/share`: `telLink, smsLink, waLink(number,text), tgLink, mailtoLink, openExternal, shareText, primaryPhone, primaryEmail, telegramHandle, fillTemplate, reminderTemplates`.
PDF/share-text from `@/lib/pdf`: `listToPdfBlob(list,contact), statementToPdfBlob(entries,contact), listShareText(list,contact), statementShareText(entries,contact)`.
Export/import from `@/lib/exporters`: `toVCard, parseVCard, contactsToCSV, parseContactsCSV, entriesToCSV, buildBackup(data,getBlob), parseBackupFile, backupFilesToBlobs`.

### Navigation (`useUI` from `@/lib/ui-store`)
`tab, setTab(tab), open(overlay), pop(), closeAll(), moreOpen, locked, openCalculator(onPick)`.
Overlay types: `{type:'contact',contactId}`, `{type:'contactEdit',contactId?,prefillPhone?,prefillName?}`, `{type:'list',listId}`, `{type:'listEdit',listId?,ownerId,kind?}`, `{type:'entryEdit',entryId?,ownerId,entryType?}`, `{type:'noteEdit',noteId?,ownerId,linkTo?}`, `{type:'reminderEdit',reminderId?,ownerId,prefill?}`, `{type:'bankEdit',bankId?,ownerId}`, `{type:'settings'}`, `{type:'search'}`, `{type:'call',phone,contactId?}`, `{type:'merge'}`, `{type:'speedDial'}`, `{type:'import'}`, `{type:'backup'}`, `{type:'storage'}`, `{type:'about'}`, `{type:'calculator'}`.

### Rules for all agents
1. ONLY modify files you own (listed in your task). Never touch shared files (lib/*, kit/*, App.tsx, other agents' files). If you need a shared change, note it in your worklog entry — do not make it.
2. Add `"use client"` at the top of every component file. TypeScript strict. No test code.
3. Icons: `lucide-react` (installed). Use them everywhere instead of inline SVG (except tiny decorative ones).
4. Money display: ALWAYS use `<Money value signed?/>` (it honors hide-balances + currency). Dates: `fd()/fdShort()/relTime()/dayGroup()`.
5. Confirmations: ALWAYS `useConfirm()` — never `window.confirm`/`prompt`.
6. After deletes via store, show `toastUndoLast()`.
7. Amount inputs: pair with `CalcButton` (calculator).
8. Run `bun run lint` when done and fix errors IN YOUR FILES ONLY.
9. Verify dev server compiles: check the tail of `/home/z/my-project/dev.log` — GET / should be 200 with no errors. If your file breaks the build, fix it.
10. Append your work record to this worklog (template in system prompt).

---
Task ID: 2-c
Agent: tools agent
Task: RemindersView + TrashView tabs, NoteEdit/ReminderEdit/BankEdit overlays, Notes/Reminders/Bank sections (tools slice of the app).

Work Log:
- Read foundation (worklog, types, store, calc, utils, ui-store, full kit, App.tsx, KeypadView exemplar) before writing code.
- `sections/RemindersSection.tsx` — compact owner section: Overdue/Today/Upcoming groups (max 5 rows + "See all" → closeAll+setTab('reminders')), 44px round done checkbox (complete/reopen), tap→ReminderEdit, long-press→shared BottomSheet (Mark done/Reopen, Snooze 10 min/1 hour/tomorrow 9:00, Edit, Delete w/ confirm+undo), 🎂 birthday one-tap (next birthday 9:00, yearly repeat) when contact has birthday and no birthday reminder, "+ Add". Also exports the SHARED `ReminderRow`/`ReminderActions` (+ `dueLabel`, `reminderStatus`, `nextBirthday9`, `hhmm`) reused by RemindersView.
- `sections/NotesSection.tsx` — pinned-first then updatedAt desc; card = title, line-clamp-2 body, tag chips, Pin indicator, Paperclip count, pastel color strip (note.color); tap→NoteEdit; long-press→Pin/Unpin·Edit·Delete (confirm→deleteNote→toastUndoLast); EmptyState + "+ New note".
- `sections/BankSection.tsx` — account cards: label+bankName, masked `maskAccount()` w/ eye reveal, per-field copy (Copy→copyText→toastOk), Holder/IBAN/SWIFT/UPI/Branch rows, `<Money/>` balance pill, AttachmentStrip(parentType 'bank'); per-account share BottomSheet ("Share as text" via shareText w/ copyText fallback, "Copy details" + preview); tap→BankEdit; long-press→Edit/Delete; "+ Add account".
- `overlays/tools-overlays.tsx` — NoteEdit (title/body/tags/pin switch/5 color dots/links via LinkChips+AddLinkButton/linkTo pre-add/attachments), ReminderEdit (title/notes, kind chips, date+time inputs → `new Date(`${date}T${time}`)`, repeat chips + "Every month on the 1st" preset, lead chips 1440/60, owner chip + ContactPicker(allowGeneral) w/ moveEntity for edits, links, attachments, prefill? support, past-time confirm on save, delete w/ undo), BankEdit (label*/bank/holder/account/IBAN/SWIFT/UPI/branch/balance+CalcButton/notes/attachments). New-record attachments use spec-sanctioned "save draft → get id → attach" flow (`AttachArea`); every onAttached re-saves the entity's attachments array. Clearing fields uses ""/[] (not undefined) to defeat the store's `??`-keeps-old semantics.
- `views/tools-views.tsx` — RemindersView: big title + stats, honest notifications opt-in card (only when Notification API exists && !settings.notifAsked; requestPermission → updateSettings({notifAsked:true}) both paths), Segmented Overdue/Today/Upcoming/Done/All w/ badges, kind filter chips (all/payment/todo/birthday/followup/custom), grouped rows w/ red-tinted overdue rows + Overdue badge + Repeat icon + snoozed chip + read-only LinkChips (onOpen via useOpenLink), per-group empty states, "+ New reminder" (ownerId 'general'). TrashView: Segmented All/Contacts/Entries/Lists/Notes/Reminders/Accounts w/ count badges, rows (type icon, title — entries as "Credit/Debit + <Money/>", "Deleted 2h ago" relTime + owner label), Restore + Delete forever (confirm; purge* promises voided), top "Restore all" + "Empty trash" (scoped to current tab; confirm), EmptyState "Trash is empty 🎉".
- Verification: `bunx tsc --noEmit` clean for all 5 files; `bun run lint` clean for all 5 files (fixed: lucide `Repeat` icon vs `Repeat` type name clash → `Repeat as RepeatKind`; React-compiler `preserve-manual-memoization` on the big TrashView derivation → converted to plain per-render IIFE; removed unused imports/disable directives); dev server: `GET / 200` (dev.sh PID had died mid-session — restarted via `.zscripts/dev.sh`).
- Runtime browser smoke test attempted (agent-browser): **client-side crash occurs before any of my views mount** — root cause is in shared Task-1 files, not mine (details below).

Stage Summary:
- Tools slice fully implemented: 2 main tabs, 3 overlay sheets, 3 owner sections; all store integrations (save/delete/restore/purge/complete/reopen/snooze/moveEntity/updateSettings), Money component for all money display, useConfirm() for every destructive action, toastUndoLast() after soft deletes, lucide icons, token classes, mobile-first ≥44px targets on primary actions, aria-labels on icon buttons, useMemo derivations from stable store refs.
- SHARED-FILE CHANGE REQUESTS (I did NOT touch them):
  1. **`src/components/app/views/KeypadView.tsx:29`** — `useApp((s) => s.contacts.filter(...))` returns a new array per getSnapshot → zustand v5 + React 19 → "The result of getSnapshot should be cached" + "Maximum update depth exceeded" → the app crashes on hydration (SSR is 200, client dies; Next dev overlay points at KeypadView). Keypad is the default tab so NOTHING renders client-side, for every agent. Fix = select `s.contacts`, filter with useMemo.
  2. **`src/components/app/kit/ContactPicker.tsx:26`** — identical unstable-filter pattern → same crash the moment any ContactPicker opens.
  3. `/icons/icon-192.png` + `/icons/icon-512.png` → 404 (manifest/metadata reference missing files).
  4. Store convention worth documenting: `undefined` in a save patch = "keep existing" (clearing requires ""/[]).
- Other notes: my sections assume the hosting sheet/view provides horizontal padding; other lint/tsc errors present in App.tsx, pwa.ts, data-overlays.tsx, money-overlays.tsx belong to their owners; dev server needed a manual restart this session.

---
Task ID: 2-a
Agent: contacts-calls agent
Task: Contacts & calls module — ContactsView/FavoritesView/RecentsView + ContactDetail/ContactEdit/CallScreen/MergeOverlay/SpeedDialOverlay.

Work Log:
- Read the full foundation first (worklog, types, store, ui-store, utils, share, calc, full kit incl. Sheet/SwipeRow/Confirm/Attachments/LinkPicker/ContactPicker, KeypadView exemplar, App.tsx mount points).
- Found both owned files already substantially implemented (a previous 2-a session evidently crashed before appending its worklog entry — no 2-a record existed). Audited every line against the task spec instead of blindly rewriting.
- `views/contacts-views.tsx` (1043 lines) verified against spec: header/subtitle, SearchInput over name/nickname/company/job/note/pinnedNote/tags/phones/emails/socials + digit matching, To receive/To pay/Net summary strip via `ownerFullBalance` (only when balances exist), filter chips (All/They owe me/I owe them/Settled/Favorites) + cycling sort (A–Z ⇄ Balance ⇄ Recent activity incl. recents/entries/lists timestamps), alphabetical groups with sticky letter headers, SwipeRow rows (Avatar, name, primary phone or "Telegram only" subtitle, BalancePill + chevron; swipe-right Call bg-brand + Favorite bg-brandink; swipe-left WhatsApp bg-[#1faa53] + Message bg-brandink + Delete bg-neg → confirm → deleteContact → toastUndoLast), long-press BottomSheet (Call/WhatsApp/Telegram/Edit/Favorite/Share vCard/Block-Unblock/Delete), "+ Add new", EmptyState, blocked chip from `settings.blockedNumbers`.
- Fixed spec deviation #1: swipe-left Message action used a `Send` icon → changed to `MessageSquare` (spec: "Message (bg-brandink MessageSquare)").
- `overlays/contacts-overlays.tsx` (2112 lines) verified: ContactDetail (Sheet root + onBack=ui.pop, star/⋯ header actions, hero Avatar xl + BalancePill lg + "They owe you/You owe them/Settled up" + openListsPending line, quick actions Call/SMS/WhatsApp-with-`fillTemplate`-prefilled-payment-reminder-when-balance≠0/Telegram/Email/More, Segmented Overview/Ledger/Lists/Notes/Bank/Files/Reminders rendering LedgerSection/ListsSection/NotesSection/BankSection/RemindersSection/FilesOfOwner, Overview field-group cards with per-phone BottomSheet incl. Set-as-primary, not-found fallback); ContactEdit (unlimited dynamic phones/emails/socials/links/addresses/customFields with per-row remove + per-group add, single-primary star toggles, birthday/tags/favorite switch/note, photo pick → save → addAttachment → saveContact photoId, prefillPhone/prefillName, name-or-phone validation with toastErr, delete with confirm+undo); CallScreen (full-screen custom layer var(--app-canvas), dialing 1.5s → connected live `clockStr` timer → ended 1.4s → pop, pulse-ring hero, Mute/Keypad(DTMF)/Speaker/Message/Add-contact controls, End bg-neg PhoneOff, telLink/waLink real-device handoff row, phone-based contact matching, isTop guard so "Add contact" mid-call doesn't unmount the call); MergeOverlay (findDuplicates pairs, keep/dup cards + reason, merge-with-confirm / dismiss / rescan); SpeedDialOverlay (2–9 grid, empty-key ContactPicker → updateSettings speedDial, assigned-key Call/Replace/Remove sheet, long-press hint).
- Fixed spec deviation #2: declining a simulated incoming call logged direction `"missed"` → now logs `"incoming"` with 0 duration (spec: "declining logs an incoming call with 0 duration and pops").
- `bunx tsc --noEmit`: zero errors in both owned files. `bun run lint`: zero errors/warnings in both owned files (project lint fails only on App.tsx:509 + pwa.ts:61 `react-hooks/set-state-in-effect` — foundation-owned).
- Browser smoke test (agent-browser, headless): app hydrates client-side (the zustand v5 unstable-selector crashes agent 2-c reported in KeypadView:29 / ContactPicker:26 are now FIXED in those shared files — both select `s.contacts` + useMemo); ContactsView renders rows + balances + swipe actions; ContactDetail opens with hero/quick actions/7 tabs/phone sheet; CallScreen dialing→connected→End closes and the outgoing call appears at the top of RecentsView; FavoritesView renders 2-col cards + Shortcuts/Speed dial card; zero page/console errors throughout.
- dev.log: `GET / 200` with clean compiles.

Stage Summary:
- Contacts & calls slice complete and runtime-verified: 3 views + 5 overlays, all store integrations (save/delete/merge/toggleFavorite/findDuplicates/addCall/deleteRecent/clearRecents/logMessage/updateSettings/addAttachment), Money component for every money display, useConfirm() for every destructive action, toastUndoLast() after soft deletes, lucide icons only, dark-mode-safe token classes, ≥44px touch targets, aria-labels throughout, useMemo derivations from stable store refs (no unstable zustand selectors).
- SHARED-FILE CHANGE REQUESTS (not made):
  1. `src/lib/types.ts` — `Settings` lacks `speedDial?: Record<string, string>` even though the task spec and KeypadView/contacts-views/contacts-overlays all reference `settings.speedDial`; three call sites currently use local type casts. Works at runtime (updateSettings persists it; hydrate merges it back) — purely a typing gap worth formalizing.
  2. `src/components/app/kit/Confirm.tsx:98` — passes `autoFocus` to kit `Button` which doesn't accept it (tsc error, harmless at runtime).
  3. `src/components/app/kit/Attachments.tsx:303` — `FilesOfOwner`'s `idsOf` param type rejects `CalcList[]` (ListItem has no `attachments`) → tsc error; suggest widening the param or using union types.
  4. Pre-existing scaffold noise: all `src/components/ui/*.tsx` import a non-existent `cn` from `@/lib/utils` (unused by the app); `/icons/icon-192.png` + `/icons/icon-512.png` 404 (also reported by 2-c); project lint errors in App.tsx:509 + pwa.ts:61 belong to the foundation owner.
- Note: recents hard-deletes (deleteRecent / clearRecents) have no store-level undo by design (recents aren't soft-deleted) — confirmed with confirm() dialogs + toasts.

---
Task ID: 2-b
Agent: money agent
Task: Money slice — LedgerSection, ListsSection, ListDetail/ListEdit/EntryEdit overlays, GeneralView (ledger + calculating lists + payment histories + share/PDF).

Work Log:
- Read the full foundation first (worklog, types, store, calc, utils, share, pdf, exporters, ui-store, entire kit, App.tsx, KeypadView exemplar). Session picked up from an interrupted earlier 2-b run: all four owned files were present but unverified and two spec gaps remained; this session completed, hardened and verified them.
- `sections/LedgerSection.tsx` — 3-stat summary strip (They owe you/You owe them/Net; Income/Expense/Net for general) + amber "Open calculating lists: +X (counted in balance)" line via openListsPending; Settle up (confirm → settleUp → toastOk "Settled ✓"); +Credit/+Debit quick-adds; filter chips All/Credit|Income/Debit|Expense/Unpaid/Overdue; entries date-desc with cumulative running balance (computed oldest→newest, shown right-aligned per row); entry cards: ArrowDownLeft/ArrowUpRight tone icons, <Money signed/>, remaining chip (Paid X of Y / Settled ✓ / Overpaid by X), category·method·date, note, overdue DueBadge (dueStatus), mini AttachmentStrip; expandable payment history: numbered rows (1., 2., …) with date+amount+note and per-row remove (confirm "Remove payment N?"), progress bar, "N payments · M left", "＋ Add payment" BottomSheet (amount+CalcButton, full-remaining fill chip, date default todayStr(), method, note) → addPayment; ⋯ menu: Edit / Add payment / Move to contact (ContactPicker allowGeneral → moveEntity) / Move to General (contact-owned) / Share statement (WhatsApp via statementShareText+waLink, PDF via statementToPdfBlob+downloadBlob, Copy, CSV via entriesToCSV) / Delete (confirm → deleteEntry → toastUndoLast); empty states with quick-add CTA.
- `overlays/money-overlays.tsx` —
  - ListDetail (Sheet, sticky footer): sub-header card with tappable owner chip (ContactPicker allowGeneral → moveEntity('list')), kind/repeat/postToLedger badges, note; postToLedger Switch ("Count pending in {owner}'s balance") + live "Pending +X added to balance" (calculating only); "Totals & charges" card → TotalsSheet (list discount pct/amount + CalcButton, tax %, extra charge, live preview via listTotals); "Group same items" Switch → groupItems() grouped rows (combined qty/total/paid, expandable to raw rows); CalcItemRow: TickCheckbox, name, "QTY × PRICE · date", line total, Paid ✓ / amber "Paid X / Y" + mini progress / Overpaid chip, overdue badge; expanded panel: inline name/date/due/qty/unit/unit-price(+CalcButton)/row-discount(pct|amt)/tax%/note editors (updateListItem), numbered payment history with per-row remove (confirm), progress, "N payments · M left", Add payment → addItemPayment, Remove item (confirm); tick → toggleItemChecked (store adds ONE auto:'tick' payment of the remaining), untick → confirm "Remove tick payments?" first, cancel leaves it checked; checklist/todo/simple kinds (done-progress header, priority chips, due badges, long-press remove); collapsible Add-item form (name/qty/unit/price+CalcButton/row-date/note/priority+due for todo); sticky totals footer (Total/Paid/Pending — red when >0, "All settled ✓" when 0) + WhatsApp/PDF/Copy/Reminder(saveReminder "Pending amount of {title}", kind payment, link to list)/More; AttachButton+strip (multi-file safe via useApp.getState); ⋯ menu: Rename/Duplicate/Save as template/All paid (markAllPaid)/Clear payments (clearPaid — same history the ticks wrote)/Share…/Delete (confirm → deleteList → toastUndoLast → pop); "Duplicate for next period" when repeat ≠ none (duplicateList fresh → open new).
  - ListEdit: title, kind (fixed when passed/editing, 2×2 selector when new), note, repeat select, "Save as template" + postToLedger (calculating) switches; save → saveList → pop + open ListDetail for the new list.
  - EntryEdit: Credit/Debit|Income/Expense segmented (TypeBtn w/ icons), BIG text-[28px] amount + CalcButton + paid-so-far hint, date, due-date Switch + date (NEW entry + dueDate → auto saveReminder "Payment due", notes, kind payment, dueAt T09:00, link to entry — exact spec payload), category (ENTRY_CATEGORIES), method (PAYMENT_METHODS), note, owner row (Change → ContactPicker allowGeneral → setOwner for new / moveEntity on save when editing); NEW: draft-first attachments — "Save draft &amp; attach" (ensureDraft validates amount, saves via saveEntry, keeps the sheet open) then AttachButton/Strip manage entry.attachments ids (onAttached pushes meta.id + saveEntry, reads fresh state so rapid multi-file attaches never clobber); save (amount > 0 else toastErr) updates the draft in place; Delete offered for existing entries AND drafts (confirm → deleteEntry → toastUndoLast → pop).
- `sections/ListsSection.tsx` — "Lists" + "+ New" → kind-picker BottomSheet (Calculating/Checklist/To-do/Simple with icons + hints) → listEdit; active list cards (title, N items, kind/repeat/postToLedger badges; calculating: grand/paid/pending <Money/> + ProgressBar + "All settled ✓"); tap → ListDetail; long-press/⋯ → Open/Duplicate/Save as template/Delete (confirm+undo); "Templates · for repeat bills" section with Use → duplicateList({fresh:true}) → opens the new list; EmptyState.
- `views/money-views.tsx` — GeneralView: brand hero ("General — your personal space", Income/Expense/Net from totalsForOwner + open-lists info line), Segmented Ledger/Lists/Notes/Reminders/Accounts/Files rendering LedgerSection/ListsSection/NotesSection/RemindersSection/BankSection (2-c imports) and FilesOfOwner for ownerId 'general'.
- Verification: `bunx tsc --noEmit` — zero errors in all 4 owned files; `bun run lint` (full project) — zero errors/warnings in all 4 owned files (remaining project errors belong to App.tsx, pwa.ts, kit files — see below); dev server recompiled after every edit: `GET / 200`, no compile errors. Zustand discipline: every selector returns a raw array / stable object ref (.find) / primitive; all derived lists via useMemo.

Stage Summary:
- Money slice fully implemented per spec: general-vs-contact labels (Income/Expense vs Credit/Debit, "They owe you"/"You owe them"/"Settled up"), balance-flip overpaid chips, payment-history-as-rows (numbered, progress, add/remove), tick-adds-one-payment with confirm-before-untick, settle-up, move-to-contact/General, open-lists pending in balance, templates & repeat duplication, share via WhatsApp/PDF/copy, due-date auto-reminder, draft-first attachments for new entries. Money component used for every money value (honors hideBalances + per-list currency override), useConfirm for every destructive action, toastUndoLast after entry/list deletes, CalcButton on every amount input, lucide icons, token classes, ≥44px targets, aria-labels throughout.
- SHARED-FILE CHANGE REQUESTS (I did NOT touch them):
  1. `src/components/ui/*` (30+ shadcn files) — all fail tsc: they import `cn` from `@/lib/utils`, which doesn't export it (project uses `cx` in kit/primitives). Unused by the dial. app so runtime is fine, but `tsc --noEmit` is very noisy.
  2. `src/components/app/kit/Confirm.tsx:98` — passes `autoFocus` to kit `Button`, which doesn't accept it (TS2322).
  3. `src/components/app/kit/Attachments.tsx:303` — TS2345: `CalcList[]` not assignable to the structural param of the local `idsOf` helper in FilesOfOwner.
  4. `/icons/icon-192.png` + `/icons/icon-512.png` → 404 (manifest/metadata reference missing files) — still present (also reported by 2-c).
  5. `bun run lint` project errors all outside my files: `App.tsx:509` + `src/lib/pwa.ts:61` (react-hooks/set-state-in-effect).
  6. kit observation: `AttachmentStrip`'s per-file remove (x) deletes the blob/meta but never updates the parent entity's `attachments` id array (no onRemove hook) — leaves harmless dangling ids on entries/lists.
- Good news for all agents: the two client-crash unstable selectors reported by 2-c (KeypadView:29, ContactPicker:26) have since been fixed by their owners — the app now renders client-side.
- Store conventions relied on (worth documenting): `undefined` in a save patch = "keep existing" (clear with ""/[]); zero-valued discount objects ({value:0}) act as "no discount".

---
Task ID: 2-d
Agent: settings-data-pwa agent
Task: Settings/Lock/Search/Import-Export/Backup/Storage/About overlays + PWA manifest & service worker (data slice).

Work Log:
- Read foundation (worklog, types, store, utils, exporters, db, pwa, ui-store, full kit, App.tsx, KeypadView exemplar) before touching anything. Found the three owned files already carried a near-complete prior-draft implementation with no worklog entry — audited it line-by-line against the task spec instead of rewriting, then fixed every deviation.
- `src/components/app/overlays/data-overlays.tsx` fixes applied:
  - Restore-REPLACE meta writes switched from raw `idbPutKey("meta", …)` (wrong shape — metaGet reads `{key,value}`) to the canonical `metaSet("settings", {…currentSettings, …backupSettings})` + `metaSet("demoSeeded", true)` per spec.
  - LockScreen: keypad keys bumped to h-16 touch targets, wrong-PIN haptic changed to spec `vibrate(60)`, removed the dishonest disabled "Fingerprint / Face unlock — coming soon" teaser (not in spec, contradicts honest-UI bar), dropped now-unused selector/import.
  - AboutOverlay: added the missing tagline "Simple connections, better conversations." under the logo mark.
  - SettingsView · Notifications: added the missing honest background-limits note ("browsers fully suspend apps they have closed…").
  - SettingsView · Data: note reworded to the spec-true statement — demo data CAN be re-seeded by clearing site data (old text claimed the opposite).
- `public/manifest.json` rewritten to the exact spec JSON (name/short_name/description/start_url/scope/display/orientation/colors/lang/categories + 3 icons incl. maskable).
- `public/sw.js` hardened to spec: `/manifest.json` now included in the stale-while-revalidate branch (was falling into generic cache-first); every `cache.put` wrapped so rejections never surface; precache keeps per-URL `.catch` + allSettled so missing icons can't break install; network-only `/api/`, network-first navigations with cached-shell fallback, cache-first other same-origin GETs (`ok` + `basic` only); SKIP_WAITING message handler.
- Verification: `bunx tsc --noEmit` — ZERO errors in data-overlays.tsx; `bun run lint` — ZERO errors/warnings in my file (the 2 lint errors + 5 warnings live in App.tsx, pwa.ts, Attachments.tsx, primitives.tsx, KeypadView.tsx — shared/other-owned).
- End-to-end browser smoke test (agent-browser, headless): app mounts; Settings sheet renders all 11 sections; Set-PIN flow (enter→re-enter→confirm modal→enabled); triggered autolock and verified LockScreen (wrong PIN → shake + "Wrong PIN" toast + dots cleared; correct PIN → unlock); Search overlay across contacts/entries/lists/reminders/banks with `<mark>` highlighting and recent-searches chips (recorded on hit click, clear-all works); vCard import via file upload → "2 contacts detected" preview → skip/merge choice → "2 added · 0 merged · 0 skipped" toast; backup export → downloaded `dial-backup-YYYYMMDD.json` (inspected: 9 contacts/6 entries/3 lists/2 notes/3 reminders/2 banks/settings; 0 files as expected pre-db-fix) + lastBackupAt line updated; merge-restore confirm → apply → clean reload with data intact; Storage overlay (84 KB used / 10 GB quota, Best-effort badge, persistence button); About overlay with tagline/privacy/credits.
- dev.log: `GET / 200` (only known `/icons/*.png` 404s — icons come later; sw.js precache tolerates them). `GET /sw.js` 200 + `GET /manifest.json` 200.

Stage Summary:
- Implemented & verified: SettingsView (appearance/theme+hideBalances, region currency+country, PIN security w/ set/change/remove/autolock + honest unencrypted-data note, backup&restore w/ stale>30d prompt card, contacts import/export incl. phone Contact-Picker API, honest email-sync card w/ Web-Share backup, storage row, double-confirm wipe-all, install prompts Android/iOS/standalone, notifications w/ honest limits, about row w/ APP_VERSION), LockScreen (logo, dots, 3×4 h-16 pad, keyboard input, shake+toast+vibrate on wrong PIN, forgot-PIN reset via useConfirm), SearchOverlay (everything-search w/ grouping, 5-per-group + "N more…", substring highlight, recents in localStorage 'dial-searches' max 8, AttachmentViewer for file hits), ImportExportOverlay (phone/vCard/drag-drop/CSV + exports row), BackupOverlay (export summary+progress, restore summary→Replace/Merge→confirm→apply→reload, honest data-loss note), StorageOverlay (estimate bar, persistent badge + requestPersistence, image/pdf/file breakdown, biggest-10 w/ confirm-delete, trash-file + orphan cleanup), AboutOverlay; manifest.json; sw.js. All confirmations via useConfirm, toasts via kit, tokens only, ≥44px targets, aria-labels throughout, no `any`, zustand selectors only return stable refs/primitives.
- SHARED-FILE CHANGE REQUESTS (did NOT touch them — CRITICAL #1):
  1. `src/lib/db.ts` — TWO bugs, empirically verified in the running app: (a) `STORES` omits `"blobs"`, so the blobs object store is NEVER created (the `s === "blobs"` branches at db.ts:33-34 are dead code, TS2367) → `blobPut/blobGet/blobDel` silently fail → attachment bytes are session-only and `buildBackup` embeds 0 files; (b) the `meta` store is created with in-line `keyPath:"id"` but `idbPutKey/metaSet` pass an out-of-line key → Chrome throws DataError ("The object store uses in-line keys and the key parameter was provided") → ALL meta writes silently fail (verified: meta store count = 0) → settings never persist: theme/currency/hideBalances/lastBackupAt/notifAsked AND pinHash (PIN lock is lost on every reload — verified). Fix: add `"blobs"` to STORES, create `"meta"` + `"blobs"` WITHOUT keyPath (out-of-line), and bump DB_VERSION to 2 (v1 DBs never re-run onupgradeneeded otherwise). This also clears the store.ts TS error below.
  2. `src/lib/store.ts:1067` — `idbClear("blobs")` is a type error ("blobs" not assignable to `StoreName`); dev mode (SWC) doesn't typecheck so it ships silently. Resolved by fix #1.
  3. Other pre-existing shared errors (not mine, FYI): `kit/Attachments.tsx:303` TS2345 (CalcList[] vs structural param type), `kit/Confirm.tsx:98` TS2322 (`autoFocus` not a Button prop), `lib/calc.ts:94` TS18048 (`l.extraCharge` possibly undefined), lint `react-hooks/set-state-in-effect` errors in `App.tsx:509` and `lib/pwa.ts:61`.
  4. `kit/primitives.tsx` — `Button` doesn't spread rest props, so `aria-label`s passed in App.tsx's header (Search/Settings) are silently dropped (a11y gap; snapshot shows unnamed buttons).
  5. `/icons/icon-192.png`, `/icons/icon-512.png`, `/icons/maskable-512.png` still 404 — manifest/SW reference them by design; main agent generates them later.
- Note: restore-REPLACE settings + attachment bytes only take effect after shared db.ts fix #1 (record writes already work — merge-restore verified end-to-end today).

---
Task ID: 3
Agent: main (Z.ai Code)
Task: Rebrand to callmony, verify end-to-end, build standalone admin console, package client+admin .tar builds, push to github.com/maca14313/callmony.

Work Log:
- Read full worklog + audited project state: Tasks 1, 2-a…2-d all complete; previously flagged shared bugs (db.ts v2 out-of-line meta/blobs stores, `cn` export, speedDial typing, calc.ts extraCharge) already fixed by prior session; `bun run lint` clean, `bunx tsc --noEmit` → 0 errors in src/ (remaining errors only in sandbox examples/ + skills/).
- REBRAND dial. → callmony everywhere:
  - App.tsx: splash + header logo `call`+teal`mony`, tagline "Talk. Track. Settle up.", rename DialApp→CallmonyApp, install-text, aria-label; page.tsx import updated.
  - layout.tsx metadata (title/applicationName/appleWebApp.title), manifest.json (name/short_name), sw.js (cache `callmony-v-1.0.0`, offline message), pwa.ts (localStorage `callmony-sw-version`).
  - pdf.ts share/PDF footers, exporters.ts (backup app id now "callmony"; parser accepts BOTH "callmony" and legacy "dial-ledger" backups), types.ts BackupFile app union, api/version app id.
  - data-overlays.tsx (~45 strings incl. LockScreen/About logos, backup/export filenames `callmony-backup-*`, `callmony-contacts.vcf/csv`, `callmony-transactions.csv`, searches key), tools-overlays/tools-views/contacts-views/contacts-overlays comments + hints, AlarmDialog notification tag/body.
  - Internal IndexedDB name `dial-ledger` kept on purpose (no data loss for existing preview data); backups stay cross-compatible.
- Browser end-to-end verification (agent-browser): title callmony ✓, live reminder alarm fired (PAYMENT DUE modal w/ snooze/dismiss) ✓, Contacts list w/ balances + swipe actions ✓, ContactDetail 7 tabs ✓, Ledger tab entry + expandable numbered payment history (Remove payment 1/2, Add payment) ✓, CallScreen (Mute/Speaker/End/Open in dialer) → call logged atop Recents ✓, More sheet → General view Income/Expense ✓, zero page/console errors; VLM-verified screenshots (keypad/ledger): branding + premium teal design intact.
- Admin console built (`admin/index.html`, standalone single file, zero deps, inline CSS, light/dark): open/drag-drop callmony backup OR load embedded sample data; dashboard stats (contacts, owes-you/you-owe, open-lists pending, overdue, banks, calls, files+bytes); 6 data-health checks (duplicates, broken cross-links, orphan/missing-bytes attachments, trash, overpaid entries, overdue reminders) with "Fix in export"; 8 tabs (Contacts/Ledger/Lists/Reminders/Banks/Calls/Notes/Files) with search, trash filter, CSV export per tab, image lightbox for embedded files; "Export cleaned backup" drops trashed records + orphan files + broken links. Fixed status-order bug (overpaid now reachable). Browser + VLM verified.
- Packaging: builds/callmony-client-v1.0.0.tar (full client source, 127 entries, ~1.3MB — excludes node_modules/.next/skills/sandbox dirs/.env) + builds/callmony-admin-v1.0.0.tar (admin/) + SHA256SUMS.
- Git: untracked junk (tool-results, download), updated .gitignore (tool-results/, agent-ctx/, /download/), committed and pushed to https://github.com/maca14313/callmony.git (main).

Stage Summary:
- App is 100% callmony-branded, offline-first PWA, browser-verified; admin console shipped as second artifact; both .tar builds + source on GitHub under the unique name "callmony" (never used before).
- Backup format: app id "callmony" (legacy "dial-ledger" accepted on restore); SW cache callmony-v-1.0.0; version endpoint /api/version.
- Admin console mirrors lib/calc.ts math exactly (entryRemaining/listTotals/ownerBalance) so audits match the app.

---
Task ID: 4
Agent: main (Z.ai Code)
Task: Foundation for the 12 new UX requirements — ledger groups + entity-links data layer, i18n (5 languages + RTL), new app shell (always-visible bottom nav w/ Calculator, keypad half-modal), ActionPicker + EntityLinks kits, Calculator display fix.

Work Log:
- `src/lib/types.ts` — NEW `LedgerGroup {id, ownerId, title, note?, refs, …}`; `LedgerEntry.groupId?` + `refs?: LinkRef[]`; `Payment.refs?`; `CalcList.refs?`; `LinkRef` gained `"link"` type + `url?`; `Settings.language?: "en"|"am"|"om"|"ar"|"ti"`; BackupFile.data.groups?.
- `src/lib/db.ts` — added `"groups"` keyed store, DB_VERSION 3.
- `src/lib/store.ts` — `groups: LedgerGroup[]` state + hydrate + demo seed (group "Shop restock" w/ 2 Abel entries) + `saveGroup/deleteGroup(soft, unassigns members, undo restores)/restoreGroup/purgeGroup` + `moveEntity("group",…)` (moves member entries too) + `removePaymentRefs/updatePaymentRefs` + saveEntry merges `groupId` (pass `""` to clear) & `refs` + addPayment/addItemPayment pass `refs` + wipeAll clears groups.
- `src/lib/calc.ts` — `groupTotals(entries, groupId)` → {count, net, remaining, paid}.
- `src/lib/exporters.ts` — buildBackup accepts + writes `groups`.
- `src/lib/i18n.ts` — NEW: LANGUAGES (en/am/om/ar/ti w/ native names + rtl flag), ~150-key dictionaries for all five languages, `t()` imperative, `useT()` reactive, `useLang()` {lang, rtl, dir}, `setLang()`. Best-effort translations; English fallback for missing keys.
- `src/lib/ui-store.ts` — Tab shrunk to contacts|recents|general|reminders|trash (keypad & favorites tabs REMOVED); default tab "contacts"; NEW `keypadOpen/setKeypadOpen`; NEW `actionPicker` state + `openAction/closeAction`; `ContactAction` type exported.
- `src/components/app/kit/ActionPicker.tsx` — NEW: `useContactActions().act(contactOrId, "call"|"sms"|"whatsapp"|"telegram"|"email", {text})` — 0 targets → toastErr, 1 → direct, multiple → shared picker sheet (`ActionPickerHost`, mounted in App, zIndex 80) listing every number/handle/email with the DEFAULT (primary) marked w/ star badge. Runs tel-call overlay / sms/wa/tg/mailto links + logs messages when text given.
- `src/components/app/kit/EntityLinks.tsx` — NEW (replaces file attachments): `AttachEntities {refs, onChange}` — chips + "Attach" button → picker BottomSheet w/ Segmented icon tabs (Contacts incl. General / Lists / Notes / Reminders / Banks / Link-add) + search + multi-select + picked-summary + Done. `EntityRefChips {refs, onChange?}` — read-only (tap opens the entity) or editable (X removes). `useRefMeta()` resolves labels from the store.
- `src/components/app/kit/KeypadSheet.tsx` — NEW: full keypad as a bottom half-modal (backdrop + rounded panel, zIndex 64/65) rising over the current tab; ALL sizes use dvh clamps (`h-[clamp(44px,8.2dvh,64px)]` keys etc.) so the whole keypad is visible on ANY screen height; number display + backspace/hold-clear, T9 matches, exact-match chip, speed-dial long-press + Grid3X3 header button → speedDial overlay, video/add-contact/call row, keyboard support, Escape/X/backdrop closes. `views/KeypadView.tsx` DELETED.
- `src/components/app/kit/Calculator.tsx` — REWRITTEN display: running expression ("100 +" / "100 + 50 =") now renders IN THE MAIN DISPLAY AREA as a muted line above the big number (was tiny top-left); top row shows memory only; "Use this value" wired through calculatorPickHandler; keys dvh-clamped; i18n labels.
- `src/components/app/kit/primitives.tsx` — Segmented label widened to ReactNode (icon tabs).
- `src/components/app/App.tsx` — RESTRUCTURED shell: frame = [StatusBar][AppHeader][content-area(relative flex-1 min-h-0: main + OverlayHost + KeypadSheet + MoreSheet + ActionPickerHost][BottomNav(always visible)][CallOverlayHost + AlarmWatcher + Autolock + LockScreen + Toaster(frame-level)]. Sheets now render inside the content area → the bottom nav is ALWAYS visible except on call/lock screens (call overlays + LockScreen render at frame level). BottomNav items: Recents · Contacts · Keypad(opens half-modal, active when open) · Calculator(active when top overlay) · More; every nav tap closes open overlays first. Views: recents/contacts/reminders/general/trash (favorites & keypad views removed). `dir` attribute on the frame follows useLang() (RTL for Arabic). i18n applied to nav/header/more-sheet.
- Verified: `bunx tsc --noEmit` ZERO errors in src/; dev server `GET / 200`.

Stage Summary:
- All shared contracts for requirements 1,3,4,5,6,7,10,11,12 are in place. Views/overlays still need to ADOPT them (agents 5-a/5-b/5-c):
  5-a contacts: 6-icon grid, Ledger-first tabs, edit-beside-name, copy icons, act() everywhere, favorites in Recents, remove Files tab, delete FavoritesView.
  5-b money: ledger groups UI (groups first + totals + expand), ledger-first already default tab in ContactDetail (5-a), entity refs on entries/groups/payments, remove file attachments, GeneralView remove Files segment.
  5-c tools/data: BankEdit → bank name + account number only, BankSection simplified, language setting UI, backup includes groups, remove file-attachment UI in NoteEdit/ReminderEdit.
- Store conventions (unchanged): undefined patch = keep old, ""/[] = clear. saveEntry groupId "" clears the group.

---
Task ID: 5-c
Agent: tools + data agent
Task: Bank simplify (BankEdit two fields + BankSection display), remove file-attachment UI from NoteEdit/ReminderEdit, language setting UI, backup/restore ledger groups, TrashView groups segment, i18n for tools views + settings labels.

Work Log:
- Read worklog (all conventions + Task 4 foundation), lib/i18n.ts (useT/LANGUAGES/t), kit/EntityLinks.tsx, lib/types.ts (LedgerGroup, BackupFile.data.groups, Settings.language), lib/exporters.ts buildBackup groups param, lib/db.ts KEYED_STORES (groups included), store saveBank/saveGroup/restoreGroup/purgeGroup semantics before editing.
- `overlays/tools-overlays.tsx` — REWROTE: (1) BankEdit simplified to EXACTLY two fields — Bank name + Account number (owner implied by where it's created from; sheet subtitle still shows "In {owner}"). Save → `saveBank({ id, ownerId, label: bankName, bankName, accountNumber })` with label synced to bank name; passes trimmed values so an emptied accountNumber would be "" (store clear convention); validates bankName + accountNumber required (toastErr) — verified in browser that empty-number save is blocked and the stored number survives. Delete-with-confirm + toastUndoLast kept. (2) NoteEdit/ReminderEdit: removed the entire AttachArea/"save draft & attach" flow + AttachButton/AttachmentStrip/AttachmentMeta/CalcButton imports; entity Links (LinkChips/AddLinkButton) kept and working; all other fields untouched (title/body/tags/pin/colors; kind/date/time/repeat+monthly-1st preset/lead chips/owner picker+moveEntity/past-time confirm/prefill). (3) i18n: t("act.save")/t("act.delete") on footer buttons + confirm labels via useT().
- `sections/BankSection.tsx` — REWROTE card: bank name (bankName||label) as title + resolved holder (owner contact name, skipped for General), masked accountNumber with eye reveal, kit IconButton copy (copyText → toastOk(t("act.copied")), pointerdown stopPropagation wrapper preserves swipe-row semantics), share action. REMOVED balance <Money/> pill, Holder/IBAN/SWIFT/UPI/branch DetailRow, AttachmentStrip + Money/fmtMoney imports. Share sheet now shares exactly holder (when resolvable) + bank name + FULL account number (bankDetailsText signature changed; only used in-file). Tap→BankEdit, long-press→Edit/Delete sheet (t("act.edit")/t("act.delete")), "+ Add account" kept.
- `views/tools-views.tsx` — RemindersView: Segmented + group labels via t("rm.overdue")/t("rm.today")/t("rm.upcoming")/t("rm.done"), heading t("tab.reminders"). TrashView: NEW "groups" segment (label t("lg.groups"), Layers icon) showing soft-deleted LedgerGroups with Restore (restoreGroup) + Delete-forever (purgeGroup, confirm) — Restore-all/Empty-trash scope follows automatically; counts + empty-state hint updated; heading t("hdr.trash"). No Files/attachment references existed here (nothing to remove).
- `overlays/data-overlays.tsx` — (1) NEW Language row in Appearance (ActionRow + Languages icon, t("st.language"), hint = active native name) right after Theme → BottomSheet listing LANGUAGES w/ native+English names, active gets Check mark; selecting → updateSettings({language}) + toast "Language set to {native}". (2) buildBackupBlob now passes `groups: s.groups`. (3) applyRestoreReplace: `d.groups?.forEach((g) => jobs.push(idbPut("groups", g)))`; applyRestoreMerge: same with have.groups id-filter (merge path only writes IDB then reloads — no in-memory seeding to mirror). (4) RestoreContent counts line includes groups. (5) i18n section/row labels: st.appearance/theme/light/dark/auto/language/hideBalances/backup/storage/about/notifications/security (+ matching aria-labels); long honest paragraphs left English as instructed.
- Verification: `bunx tsc --noEmit` → 0 errors in src/; `bun run lint` → 0 errors/warnings in my 4 files (only pre-existing kit/KeypadSheet.tsx:125 unused-disable warning remains, foundation-owned); dev server GET / 200 with clean compiles, no runtime errors in dev.log tail.
- Browser smoke test (agent-browser, headless): Settings → Language row → picker lists English/አማርኛ/Afaan Oromoo/العربية/ትግርኛ → Amharic switches app instantly (nav "እውቂያዎች", Appearance "መልክ", toast); Arabic flips frame dir=rtl; back to English restores ltr. Bank tab (Abel): card shows Commercial Bank + holder + "•••• •••• 4321", eye reveals full number, no balance/IBAN rows; share sheet preview = "Holder: Abel Tesfaye\nCommercial Bank\nAccount: 1000987654321"; BankEdit (edit) = 2 prefilled fields + Save/Delete; New bank account = 2 empty fields + Save only; created "Awash Bank" account (label synced, masked correctly), deleted it via confirm modal + Undo toast appeared. TrashView: Groups segment present; seeded a soft-deleted group directly into IDB → appears with Restore/Delete-forever; delete-forever purges it from IDB; re-seeded + Restore clears deletedAt in IDB (both store round-trips verified); test data cleaned up (trash empty again). NoteEdit/ReminderEdit sheets: all fields render, NO Files/attach section. Bun script confirmed buildBackup embeds groups in the exported JSON.
- Note: parallel-agent Fast-Refresh full reloads reset overlay state repeatedly mid-test (refs kept invalidating) — retried affected steps; final state re-verified after each reload.

Stage Summary:
- Bank slice simplified end-to-end (2-field create/edit, masked+copy+share display, owner-implied holder) per user requirement; file-attachment UI fully removed from NoteEdit/ReminderEdit (entity links retained); Language setting with 5 languages incl. RTL-Arabic flip; backups now include ledger groups and both restore paths (replace + merge) write groups; TrashView gained a Groups segment with restore/purge; i18n applied to trivial labels across all four owned files. tsc + lint clean in owned files; GET / 200.
- SHARED-FILE CHANGE REQUESTS (I did NOT touch them):
  1. `src/components/app/App.tsx:424-431` — More-sheet trash badge (`trashCount`) sums contacts/entries/lists/notes/reminders/banks but NOT `s.groups` → soft-deleted groups are invisible in the badge. Suggest adding `+ s.groups.filter((g) => g.deletedAt).length`.
  2. `src/components/app/kit/KeypadSheet.tsx:125` — unused eslint-disable directive (only remaining lint warning in the project; foundation-owned).
  3. Pre-existing observation for 5-a/5-b: NoteEdit/ReminderEdit save calls no longer pass `attachments`, so legacy attachment id arrays on old notes/reminders are preserved untouched (harmless; FilesOfOwner legacy UI still reads them if kept).
---
Task ID: 5-a
Agent: contacts-calls agent
Task: Contacts UI overhaul — ContactDetail ledger-first + 6-action grid + copy icons; act() routing everywhere; favorites strip in Recents; delete FavoritesView; i18n adoption.

Work Log:
- `overlays/contacts-overlays.tsx` (ContactDetail):
  - Replaced the horizontal quick-action chip row with a FIXED 2×3 grid (`grid grid-cols-3 gap-2.5`) of 6 compact icon buttons (`GridAction`: h-11 w-11 rounded-2xl border-line bg-surface shadow-key, 19px icons, 9px label below). Row 1 Call/SMS/WhatsApp (WhatsApp icon whatsapp-green `text-[#1faa53]`, SMS keeps MessageCircle), Row 2 Bank (Landmark, FIRST) / Telegram (Send) / More (MoreVertical, LAST). Deleted old `QuickAction` component.
  - Grid behavior: Call/SMS → `act(contact,"call"/"sms")`; WhatsApp → `act(contact,"whatsapp",{text:waReminder})` (pre-filled payment-reminder preserved); Telegram → `act(contact,"telegram")`; Bank → `setTab("bank")` (navigation); More → existing more-sheet. Call/SMS/WhatsApp disabled when no phones, Telegram disabled when no telegram target.
  - More sheet: added Email ActionRow → `act(contact,"email")` (with primary email as sub); kept Share vCard / Copy number / Open in dialer / Simulate incoming call. Title t("act.more").
  - Tabs reordered Ledger → Lists → Notes → Bank → Reminders → Info (renamed from Overview, t("tab.info")); DEFAULT tab = "ledger"; removed the "files" tab + `FilesOfOwner` import + the AttachButton/AttachmentStrip attachments section from Info (file attachments being removed app-wide).
  - Hero: small pencil IconButton (variant surface h-9 w-9, aria-label t("cd.editContact")) right beside the contact name → opens `{type:"contactEdit"}`. Star + ⋯ header actions kept.
  - Info tab: every field row got an explicit copy IconButton (h-9 w-9, Copy 14) — phones (separate button at row end next to star/chevron), emails (explicit copy + kept copy-on-tap + mail button), socials (copy handle), links (copy URL), addresses (functional copy button replaces the old decorative icon), custom fields (copy value). All toasts `toastOk(t("act.copied"))`.
  - i18n via `useT()`: tab labels, cd.theyOweYou/youOweThem/settledUp, cd.addToFavorites/removedFromFavorites toasts, lg.openLists/lg.countedInBalance, act.* labels.
  - CallScreen (task D): behavior untouched; translated the two trivially-keyed labels (Message → act.message, Add contact → act.newContact, WhatsApp handoff → act.whatsapp).
- `views/contacts-views.tsx`:
  - ContactsView: added `useT()` + `useContactActions()`; swipe Call/WhatsApp/Message AND long-press sheet Call/WhatsApp/Telegram + NEW Message row all route through `act(contact,…)` (multi-number contacts now get the shared ActionPicker with the starred default). Favorites filter chip kept, labeled t("cd.favorites"). Title t("nav.contacts"); favorite toasts, act.* labels, cd.editContact, act.newContact applied.
  - DELETED FavoritesView entirely (+ SPEED_KEYS/speedDialAssignments helpers, Grid3X3/UserRound/SectionCard/Settings/openExternal/smsLink/tgLink/waLink unused imports removed). No tab-"favorites" references remain (FilterKey "favorites" chip stays).
  - RecentsView: NEW favorites strip at top — round avatar circles (Avatar size md + `rounded-full` via twMerge override) + first name, gap-3 horizontal scroll pb-1, FAVORITES label t("cd.favorites"), rendered only when ≥1 favorite, tap opens contact detail. All/Missed/Incoming/Outgoing filter + call rows + direct redial unchanged (recents call a specific number). Title t("nav.recents"); unknown-number sheet Call/Add-contact rows translated.
- Verification: `bunx tsc --noEmit` ZERO errors in src; `bun run lint` — only the pre-existing KeypadSheet.tsx warning (shared kit, not mine); GET / 200; dev.log clean. Browser smoke test (agent-browser + VLM): ContactDetail opens ledger-first with the 6-button grid + pencil beside name; Call grid button opens the call screen; Bank grid button switches to the Bank tab; More sheet lists Email; Info tab shows copy buttons on phones/emails/socials; Recents shows the FAVORITES avatar strip (members match live favorite state) above search + chips; zero page/console errors.

Stage Summary:
- ContactDetail is now ledger-first with a fixed 6-action 2×3 grid, edit-beside-name, copy-on-every-info-row, no Files/attachments; all contact actions go through `useContactActions().act()` so the shared number-picker (starred default) appears for multi-number contacts.
- FavoritesView is gone; favorites live as a compact avatar strip atop Recents (plus the Contacts filter chip).
- i18n (useT) applied to titles, tabs, balance labels, action labels, toasts wherever dictionary keys exist; English kept where no key exists (Phones/Emails/Socials/Links section titles, filter chips They owe me/I owe them/Settled, etc.).
- SHARED-FILE CHANGE REQUESTS: none blocking. Note: EN dictionary lacks keys for some recurring strings in my views (filter chips "They owe me"/"I owe them"/"Settled"/"All"/"Missed", section titles Phones/Emails/Socials/Links/Addresses/Custom fields, "Share vCard", "Delete contact", swipe labels Favorite/Unfavorite, "Recent calls" subtitle) — if the main agent adds keys, adoption is trivial. Also note `Attachments` kit (AttachButton/AttachmentStrip/FilesOfOwner/AttachmentViewer) is now unused by contacts files (data-overlays may still use AttachmentViewer) — candidate for removal once 5-b/5-c finish de-attaching.

---
Task ID: 5-b
Agent: money agent (work completed; context deadline hit before this entry — entry written by main agent after full audit)
Task: Money slice — ledger groups with calculated totals, entity links on entries/groups/payments, remove file attachments, i18n.

Work Log:
- `sections/LedgerSection.tsx` — GROUPS section rendered first: collapsible group cards (title, N entries, groupTotals() net + pending colored via <Money signed/>), expand shows member EntryCards inside; group ⋯ menu: Add entry (setPendingEntryGroup → opens entryEdit; EntryEdit consumes takePendingEntryGroup on mount), Rename (BottomSheet + input → saveGroup), Attach (AttachEntities on group.refs → saveGroup), Move to contact (moveEntity("group",…)), Delete (confirm → deleteGroup → toastUndoLast). "+ New group" (FolderPlus → title sheet → saveGroup). Ungrouped entries exclude grouped ones. Entry cards: EntityRefChips replace mini AttachmentStrip; numbered payment rows show payment.refs chips. Add-payment sheet (shared AddPaymentSheet w/ AttachEntities refs state) → addPayment({…, refs}). i18n lg.*/cd.*/act.* keys.
- `overlays/money-overlays.tsx` — EntryEdit: Group selector chips ([None] + owner groups + inline "+ New group" creation via saveGroup; passes groupId "" to clear), AttachEntities draftRefs included in saveEntry (file-attachment draft flow REMOVED). ListDetail: AttachEntities bound to list.refs → saveList; item payment history + AddPaymentSheet (with refs) via addItemPayment. All AttachButton/AttachmentStrip imports removed.
- `views/money-views.tsx` — GeneralView Segmented: Ledger/Lists/Notes/Reminders/Accounts (Files segment + FilesOfOwner import REMOVED); i18n tab labels.
- Verification (main agent, post-audit): `bunx tsc --noEmit` ZERO errors in src/; `bun run lint` ZERO warnings after KeypadSheet fix; GET / 200.

Stage Summary:
- Ledger now opens with groups (each calculating its own net/pending totals) + single ungrouped entries; entries/groups/payments all accept entity attachments (banks, lists, notes, reminders, owners, links) via the EntityLinks kit; file attachments fully removed from the money slice; backup/restore includes groups (5-c).
- Module-level pending-entry-group handshake (setPendingEntryGroup/takePendingEntryGroup exported from LedgerSection) bridges "Add entry" from a group menu into EntryEdit without touching shared ui-store.

---
Task ID: 6
Agent: main (Z.ai Code)
Task: End-to-end browser verification of all 12 new requirements + fixes found during verification.

Work Log:
- Agent-browser (430×932 phone viewport + VLM screenshot analysis) verified every requirement:
  1. Ledger groups + ledger-first: ContactDetail opens on the LEDGER tab (tabs: Ledger/Lists/Notes/Bank/Reminders/Info); GROUPS section shows "Shop restock" (2 entries, Net +ETB 5,750, Pending ETB 3,500 — group math exact), expandable with member entry cards; SINGLE ENTRIES section shows "All 2 entries are inside groups."; New group button present. ✓
  2. Bank simplification: card = bank name + holder + masked number + copy + eye + share (no balance/IBAN/SWIFT/branch); "New bank account" sheet has EXACTLY 2 inputs (Bank name, Account number) + subtitle "In Abel Tesfaye"; created "Awash Bank" account end-to-end. ✓
  3. Quick actions: fixed 2×3 grid of compact 44px icon buttons — Row1 Call/SMS/WhatsApp, Row2 Bank(first)/Telegram/More(last). ✓
  4. Action picker w/ default: Telegram grid button on Abel → shared sheet "Telegram · Abel", "Choose a number", @abelt row starred "Default" (teal), phone row radio; nav visible behind scrim. Swipe/long-press actions route through the same act() (verified in code by 5-a). ✓
  5. Entity attachments: entry form LINKS section with AttachEntities (picker: 6 icon tabs contacts/lists/notes/reminders/banks/link + search + multi-select + General row + Done); attached "Sara Mohammed" to an entry — chip renders; AddPaymentSheet includes AttachEntities; group cards show refs chips + Attach menu. ✓
  6. File attachments removed: no Files tab anywhere; AttachButton/AttachmentStrip/FilesOfOwner referenced by ZERO view files (kit file only); GeneralView segments = Ledger/Lists/Notes/Reminders/Accounts. ✓
  7. Calculator: expression "100 +" / "100 + 50 =" / "250 ÷ 5 =" renders IN THE MAIN DISPLAY AREA above the big number. TWO REAL BUGS FOUND & FIXED: (a) stale-closure state — batched key presses lost digits → rewrote engine to a single state object with functional updaters; (b) "Use this value" called closeAll() which closed the SHEET THAT OPENED THE CALCULATOR (EntryEdit/Add payment vanished and the value was lost) → CalculatorSheet now pops only itself + new ui-store action clearCalculatorHandler(); verified 40×3=→Use this value→ amount field shows 120 with the entry sheet still open. ✓
  8. Copy icons: Info tab rows (phones/emails/socials) each show a Copy IconButton (+ email keeps copy+mail pair). ✓
  9. Edit beside name: pencil IconButton right beside the hero contact name. ✓
  10. Keypad: nav Keypad → half-modal rises from the bottom (rounded top, drag handle, X, speed-dial button); whole keypad (display + 3×4 keys + call row) fully visible; keys use dvh clamps; Contacts dimmed behind; typed "11" → Call screen opened. ✓
  11. Bottom nav: Recents/Contacts/Keypad/Calculator/More always visible (verified behind sheets/pickers); during a call the call screen covers it (hit-test at nav center returned the call-screen element, hitInsideNav=false); FAVORITES avatar strip (Abel/Daniel/Sara) atop Recents + Favorites filter chip in Contacts; FavoritesView deleted. ✓
  12. Languages: Settings → Language row → sheet lists English/አማርኛ/Afaan Oromoo/العربية/ትግርኛ; Amharic switched nav to "የቅርብ ጊዜ | እውቂያዎች | የቁጥር ሰሌዳ | ካልኩሌተር | ተጨማሪ"; Arabic flipped the frame dir=rtl with translated Settings (اللغة/إخفاء المبالغ/الأمان); switched back to English. ✓
- Extra hardening from verification: desktop frame height now `sm:h-[min(900px,calc(100dvh-48px))]` (frame never exceeds the viewport on short desktop screens); More-sheet trash badge includes soft-deleted groups; removed KeypadSheet unused eslint-disable (project lints 100% clean).
- Zero page errors / console errors across the whole session; `bunx tsc --noEmit` 0 errors in src/; `bun run lint` 0 problems; GET / 200 throughout.

Stage Summary:
- All 12 user requirements browser-verified working, including two real calculator bugs fixed at the root (stale-closure digit loss + use-value destroying the opening sheet). App is fully functional on phone viewport; lint/tsc clean.

---
Task ID: 7
Agent: main (Z.ai Code)
Task: Finalization — admin console groups-safety, v1.1.0 tar builds, git commit + push.

Work Log:
- admin/index.html exportCleaned hardened for the new data model: `d.groups = d.groups || []` + groups added to keptIds/live-filter; entries' groupId cleaned when its group was trashed; entry.refs / payment.refs / group.refs / list.refs filtered by a fixed `valid()` that keeps "item"/"link" refs and the "general" contact owner (previously those would have been wrongly dropped).
- Built builds/callmony-client-v1.1.0.tar (129 entries: src, public, scripts, configs, worklog) + builds/callmony-admin-v1.1.0.tar; SHA256SUMS.txt regenerated for all four tars (v1.0.0 + v1.1.0).
- Git: staged all changes (new kits ActionPicker/EntityLinks/KeypadSheet, i18n, App shell, all view/section/overlay updates, deleted KeypadView, builds) → commit e4cdf1b "callmony v1.1.0 — …" → pushed to https://github.com/maca14313/callmony.git main (912e203..e4cdf1b).
- Final state: `bunx tsc --noEmit` 0 errors in src/, `bun run lint` 0 problems, GET / 200, no console/page errors.

Stage Summary:
- v1.1.0 shipped: all 12 user requirements implemented and browser-verified; source + client/admin .tar builds + checksums on GitHub.
---
Task ID: 8-foundation
Agent: main (Z.ai Code)
Task: v1.2.0 foundation — 16-point revision round: types/store/ui-store/i18n catalog/App shell/Calculator/LanguageSheet.

Work Log:
- types.ts: added EntryKind ("income"|"expense"|"credit"|"debit"), LedgerEntry.kind?, Settings.callMode? ("dialer"|"demo", default dialer = real tel: calls) and Settings.customCategories? (user-added ledger categories).
- store.ts: DEFAULT_SETTINGS { callMode: "dialer", customCategories: [] }; saveEntry passes kind through; NEW actions addCustomCategory(label) / removeCustomCategory(label) (persisted via updateSettings).
- ui-store.ts: closeAll() now also resets keypadOpen + moreOpen + actionPicker (fixes "nav does nothing while keypad open"); entryEdit overlay accepts kind?: EntryKind.
- i18n.ts: REWROTE — full EN catalog (~430 keys: nav/tab/act/hdr/cd/cl/ce/kp/calc/lg/cat/gv/ee/bk/ls/nt/rm/st/sr/ie/bkp/sto/tr/spd/att/misc). t()/useT() now take optional vars ({n}/{name}/… placeholders). AM/OM/AR/TI extended with core keys + R12 nav.contacts overrides (ስልክ ቁጥሮች / Galmee Bilbilaa / ቁፅሪ ስልኪ) — Agent 9-d brings them to full parity. DO NOT edit i18n.ts unless you are 9-d.
- App.tsx: REMOVED StatusBar (the "top thing above the navbar"); BottomNav now 6 items (Recents/Contacts/Keypad-toggle/Calculator/GENERAL-new/More) — keypad item toggles the sheet; MoreSheet: General+Calculator rows removed (nav tabs now), NEW Language row (native-name sub) → shared LanguageSheet; AppHeader: primary header for general too (secondary only reminders/trash), safe-area top padding; main pb-10→pb-14; suppressHydrationWarning on both frame divs (stale SW-cached HTML vs new bundle was the hydration mismatch source).
- kit/LanguageSheet.tsx: NEW shared language picker bottom sheet (used by MoreSheet; Settings should adopt it too — 9-c).
- kit/Calculator.tsx: display reworked — the WHOLE calculation ("100 + 50" while typing, "100 + 50 =" after) renders as the MAIN big-number display (static Tailwind size buckets), result gets the biggest bold line after "=".
- money-overlays.tsx (minimal pre-edit for parallel safety): EntryEdit accepts kind?: EntryKind prop (kind state initialized from it, saved into saveEntry) — Agent 9-b builds the 4-type selector UI on top. App.tsx OverlayItem forwards overlay.kind.
- Verified: bunx tsc --noEmit 0 errors in src; bunx eslint src 0 problems; GET / 200.

Stage Summary:
- Foundation for the 16-point round is in: real-call settings, custom categories, entry kind, full EN i18n catalog with interpolation, 6-icon nav with General, More-sheet Language, no StatusBar, keypad/nav interplay fix, hydration guard, big-number calculator. Parallel agents 9-a/b/c/d now implement the feature work on top.

---
Task ID: 9-a
Agent: 9-a (parallel feature round)
Task: Real tel: call handoff · Bank holder name + link-from-contacts · Bank quick-grid modal · 12-icon (6×2) quick-action grid · i18n adoption in owned files.

Files owned/changed (ONLY these):
- src/lib/share.ts · src/components/app/overlays/contacts-overlays.tsx · src/components/app/kit/ActionPicker.tsx · src/components/app/kit/KeypadSheet.tsx · src/components/app/sections/BankSection.tsx · src/components/app/overlays/tools-overlays.tsx

Work Log:
- share.ts: NEW `openTel(number, countryCode = "ET")` — sets window.location.href = telLink(...) in try/catch (far more reliable than window.open for tel: URIs on mobile/PWA).
- CallScreen (contacts-overlays.tsx) — REAL CALL HANDOFF: new phases "handingoff" + "handedoff" on CallPhase. For outgoing calls (direction !== "incoming") when settings.callMode !== "demo" (dialer = default, undefined treated as dialer): phase starts "handingoff" (status t("cl.handingOff")), a 450ms timer lets the in-app screen slide in, then fires openTel(phone, cc) exactly once (handoffFiredRef guard), transitions to "handedoff" (status t("cl.continuedInDialer")) and logs the outgoing call immediately via new `finalize()` (extracted from logAndClose; finalizedRef guarantees EXACTLY ONE log entry, duration 0). End button in handingoff/handedoff = finalize (guarded no-op if already logged) + closeScreen. Mute/speaker/keypad/DTMF controls hidden during handoff phases (add-contact escape hatch stays when !contact); DTMF section also gated off. Bottom pills kept: "Phone dialer" → label t("cl.phoneDialer"), after handoff t("cl.openDialerAgain"), onClick sets handoffFiredRef + openTel (tap during handingoff fires early, auto-timer then only transitions+logs — never double-opens); WhatsApp pill unchanged (openExternal waLink). Incoming simulation and demo mode (dialing→1.5s→connected timer→ended→logAndClose) unchanged. All CallScreen strings now cl.*/act.* keys.
- KeypadSheet.tsx: makeCall still opens the call overlay (CallScreen does the tel: handoff). Adopted kp.maxDigits, kp.speedDialOf ({name}), kp.videoDemo, kp.deleteDigit, kp.callNumber ({number}) / act.dial for the call button aria, kp.speedDial for the Grid3X3 button.
- ActionPicker.tsx: runAction "call" still opens the call overlay; act() error toasts now use imperative t() (tNow) with cd.noPhone / cd.noEmail / cd.noTelegram (reactive useT would go stale inside the [] memo); ActionPickerHost fallback name uses cd.contact.
- BankEdit (tools-overlays.tsx) — HOLDER NAME + LINK FROM CONTACTS: third field "Holder name" (bk.holderName / bk.holderOptional, placeholder "Abebe K."), local `owner` state (initialized from ownerId prop) + holderName state (from bank.holderName). When owner === "general": dashed "Choose from contacts" button (bk.chooseFromContacts, UserRound) opens the shared ContactPicker kit sheet (allowGeneral=false, title bk.chooseFromContacts); picking a contact sets holderName to the display name AND setOwner(contact.id) — save() persists saveBank({ id, ownerId: owner, label: bankName, bankName, accountNumber, holderName: holderName.trim() || "" }) so the account moves under the person (empty string clears, store convention). Sheet subtitle (useOwnerLabel(owner)) reactively shows "in {name}" + read-only row shows "Saved under · {name}" chip (nt.savedUnder). When owner !== "general": read-only holder row (bk.holder + person's name + saved-under chip) instead of the picker. Confirm dialogs now bk.deleteTitle/bk.deleteMsg ({name}); all field labels/hints/placeholders/toasts on bk.* keys.
- BankSection.tsx: person-owner cards keep showing the resolved contact name; GENERAL accounts now render a "Holder · {bank.holderName}" line (bk.holder) when holderName is set; share text uses effective holder (holder ?? b.holderName) via bankDetailsText. Adopted bk.* keys: accountsTitle, noAccounts(Hint), addAccount, deleteTitle/deleteMsg ({name}), shared, shareAsText, copyDetails, act.couldntCopy for failures.
- ContactDetail (contacts-overlays.tsx) — BANK QUICK-GRID MODAL: the grid Bank icon no longer setTab("bank"); it opens a local BottomSheet (bankSheetOpen) titled bk.accountsOf ({name}) listing THIS contact's banks: row = bank name + masked number (maskAccount) + reveal Eye toggle + copy IconButton (toast act.copied/act.couldntCopy); tapping a row → ui.open({ type: "bankEdit", bankId, ownerId: contact.id }); footer "+ bk.addAccount" → ui.open({ type: "bankEdit", ownerId: contact.id }); empty state bk.noAccounts + bk.noAccountsHint + add button. Segmented tabs' own Bank tab left untouched.
- ContactDetail quick-action grid — HARD REQUIREMENT 6 cols × 2 rows = 12 tiles, `grid grid-cols-6 gap-1`: Row1 Call(PhoneCall,brand)/SMS/WhatsApp(#1faa53, act() w/ waReminder)/Telegram/Mail(disabled when no emails)/Ledger(Wallet→setTab("ledger")). Row2 Bank(Landmark, FIRST → BankAccountsSheet)/Lists(ListChecks)/Notes(StickyNote)/Reminders(Bell)/Info(Info)/More(MoreVertical, LAST → setMoreOpen(true)). New GridTile: plain 17px icon + text-[8px] label, py-1.5, NO border/bg/shadow chrome, `flex flex-col items-center gap-1 rounded-xl transition active:scale-95 hover:bg-surface2 min-h-[44px] w-full`, disabled → opacity-40 + pointer-events-none + aria-disabled. Old GridAction removed.
- i18n adoption sweep in all 6 files (labels, placeholders, toasts, confirms with {name}/{n} interpolation, aria-labels, empty states) using existing catalog keys; composed arias from act.copy/act.open + value; cross-namespace reuse of exact matches (nt.titleRequired, nt.savedUnder, nt.optionalDetails, misc.in/misc.general, ee.links, lg.attachTo, cd.noPhone); module-level shareVCard uses imperative t (tStatic). MergeOverlay/SpeedDialOverlay translated (spd.mergeTitle/mergeMsg {a}{b}/contactsMerged/rescan/noDuplicates/everyUnique, kp.speedDialSet/speedDialCleared {key}, spd.speedDial/oneLongPress/replace).

MISSING KEYS (kept English literals — main agent to add to i18n EN + translations):
- cl.callWith "Call with {name}" (call dialog aria)
- cd.work "Work" · cd.deleteContact "Delete contact" · cd.writeEmail "Write email to {address}"
- ce.notFoundHint "It may have been deleted. Try again from the contacts list." · ce.deleteMsg "“{name}” will move to Trash. You can undo right after." · ce.contactSaved / ce.contactUpdated · act.saving "Saving…" · ce.saveChanges / ce.saveContact · ce.photoAlt "Contact photo" · ce.changePhoto "Tap to change photo" / ce.addPhoto "Add photo" · (ContactEdit per-row arias "Phone number {n}", "Label for link {n}"… left English) · "Website" placeholder
- spd.pairsFound "{n} pairs found" · spd.mergeBtn "Merge" · spd.keep / spd.duplicate / spd.dismissPair / spd.mergeInto "Merge into {name}" · spd.longPressHint · spd.assignKey "Assign key {key}" · spd.chooseContact "Choose a contact" · spd.keyOf "Key {key} · {name}" · spd.assignToKey "Assign a contact to key {key}" · spd.callNow / spd.removeFromKey
- nt.untitledNote / nt.noteSaved / nt.noteUpdated / nt.deleteTitle / nt.deleteMsg ({title}) / nt.tagsExample "work, invoice"
- rm.newReminder / rm.kindLabel "Kind" / rm.repeatOnce|Daily|Weekly|Monthly|Yearly / rm.everyMonthFirst (button label; rm.monthlyFirst is the toast) / rm.lead1d / rm.lead1h / rm.leadHint / rm.change / rm.moveHint / rm.pastTitle / rm.pastMsg / rm.saveAnyway / rm.reminderSaved / rm.reminderUpdated / rm.deleteMsg ({title}) / rm.pickDateTime / rm.repeats "repeats {repeat}"
- bk.holderPlaceholder "Abebe K." · bk.noNumber "No account number" · bk.showNumber / bk.hideNumber / bk.detailsCopied / bk.copyFallback "Copied — sharing not supported here"
- kp.videoCall "Video call" (aria)

Deliberately left untouched: Segmented tabs incl. their Bank tab; incoming-call simulation; demo-mode call flow; KeypadSheet/ActionPicker call-overlay openings; views/*, store/ui-store/types/i18n/App (other agents); money/data overlays.

Verification: bunx tsc --noEmit → 0 errors in src/ (examples/skills pre-existing only); bunx eslint on the 6 owned files → 0 problems 0 warnings; dev.log GET / 200, no compile errors; logic re-read (exactly-once call log via finalizedRef; handoff ref prevents double tel: open incl. StrictMode + early pill tap; presetMonthlyFirst Date-shadowing bug fixed by renaming to `now`).
