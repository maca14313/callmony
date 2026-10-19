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
