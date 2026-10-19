# Contact Ledger App: Build Specification

*A mobile-first, installable web app (PWA) that looks like a phone's contact app, where every contact can hold its own money records, lists, notes, files, accounts, messages and alarms.*

## 0. Instructions to the AI builder

Build the app described below as a **Progressive Web App (PWA)**. Follow these rules:

- Work in phases (Section 14). Finish and test one phase before starting the next.
- Mobile first. Design for a 360 to 430 px wide screen, and make it still work on tablet and desktop.
- Offline first. All data lives on the device. No account or server is required for the core app.
- Keep the code clean, typed, and commented where logic is non-obvious (especially calculations).
- Where this document says "must", it is required. Where it says "should", do it unless it is impractical, and explain why.
- If something is unclear, choose the simplest reasonable option, state the assumption in a README, and continue.

## 1. Product overview

**Name (working):** Contact Ledger

**Purpose:** One place to manage people and everything connected to them: money owed or paid, calculating lists, notes, files, bank details, messages, and reminders. It also works for personal items that have no contact.

**Core idea:** Everything is an **entry**. An entry can be linked to a contact or left unlinked. Unlinked entries live in the **General** space.

**Target user:** A single person (shopkeeper, freelancer, family finance keeper, landlord) who tracks who owes what and wants it in a familiar contact-style app.

## 2. Platform and installability

The app must be installable on a phone from the browser and behave like a native app.

- **Type:** PWA with a web app manifest and a service worker.
- **Manifest:** name, short name, `display: standalone`, theme and background colors, start URL `/`, icons at 192 px and 512 px (plus a maskable icon).
- **Service worker:** pre-cache the app shell so it opens offline. Use a versioned cache and show an "Update available" prompt when a new version is ready.
- **Install prompt:** show a custom "Install app" button (using `beforeinstallprompt`) on Android Chrome. On iOS Safari, show a short instruction card: Share, then Add to Home Screen.
- **Hosting:** must be served over HTTPS (any static host works).
- **Splash and status bar:** set theme color, use safe-area insets (`env(safe-area-inset-*)`) so nothing hides under notches or the home bar.

### Suggested tech stack

| Area | Choice |
| --- | --- |
| Framework | React + TypeScript + Vite |
| Styling | Tailwind CSS |
| Routing | React Router |
| Local database | IndexedDB through Dexie |
| State | Zustand (or React context for small state) |
| PWA | vite-plugin-pwa (Workbox) |
| PDF export | jsPDF or html2canvas |
| Dates | date-fns |
| Forms | React Hook Form + Zod |
| Testing | Vitest for calculation logic |

A different stack is fine if it meets every requirement here.

## 3. Design and UI

**Look and feel:** the stock phone contact app, clean and fast, with large touch targets (at least 44 px).

- **Theme:** light and dark mode, following the system by default, with a manual switch.
- **Colors:** one primary accent. Money colors: **green = they owe you (or income)**, **red = you owe them (or expense)**, grey = settled.
- **Typography:** system font stack. Large title headers that collapse on scroll.
- **Avatars:** round, with the contact photo or colored initials.
- **Lists:** alphabetical with sticky letter headers (A, B, C...) and a side index for quick jump.
- **Motion:** subtle transitions only. Respect reduced-motion settings.
- **Feedback:** toasts with Undo for deletes, skeletons while loading, friendly empty states with a call to action.
- **Accessibility:** labels on all controls, sufficient contrast, keyboard and screen-reader support.
- **Language:** English first. All text must be in a translation file so other languages can be added later. Support right-to-left layout.

### Navigation

**Bottom tab bar (4 tabs):**

1. **Contacts**: alphabetical contact list.
2. **General**: all entries with no contact (ledger, lists, notes, files, accounts).
3. **Reminders**: all alarms and to-dos, grouped by Overdue, Today, Upcoming.
4. **Search**: global search across everything.

A **floating "+" button** on every main screen opens a quick-add sheet: New contact, New transaction, New calculating list, New to-do or checklist, New note, New reminder, Attach file.

**Top bar:** title, a summary chip (see Home), and a menu for Settings.

## 4. Screens

### 4.1 Contacts (home)

- Search bar at the top that filters as you type (name, number, note text).
- Summary strip: **Total you will receive**, **Total you will pay**, **Net**.
- Contact rows: avatar, name, small subtitle (primary number or last activity), and on the right the **balance** in green or red.
- Filters: All, They owe me, I owe them, Settled, Has reminders, Favorites.
- Sort: name, balance, last activity.
- Swipe a row: left for Call, right for WhatsApp or Message. Long-press for multi-select (delete, export, favorite).

### 4.2 Contact detail page

**Header:** large avatar, name, and the balance.

**Quick action buttons:** Call, SMS, WhatsApp, Telegram, Email, plus a "More" button for other apps.

**Tabs under the header:**

- **Overview:** phone numbers, emails, addresses, birthday, tags, custom fields, pinned note, next reminder.
- **Ledger:** all credit and debit entries with a running balance, filters by date, type, and status.
- **Lists:** calculating lists, checklists, and to-do lists for this contact.
- **Notes:** notes and saved messages.
- **Accounts:** the contact's bank or UPI details with copy and share buttons.
- **Files:** images, PDFs, and documents in a grid.
- **Reminders:** alarms linked to this contact.

### 4.3 General space

The same tabs (Ledger, Lists, Notes, Accounts, Files, Reminders) but for items that belong to no contact. "Accounts" here means the user's own bank accounts.

### 4.4 Add or edit contact

Fields: photo, first name, last name, nickname, company, multiple phone numbers (each labeled mobile, work, home, other, with one primary), multiple emails, addresses, birthday, social handles (Telegram username, Instagram, Signal, etc.), tags, notes, favorite toggle.

Options: **Import from phone** (see Section 9), and **Import vCard (.vcf)**.

### 4.5 Transaction form (credit and debit)

Fields: type (Credit or Debit), amount, date and time, contact (optional), category, note, attachments, payment method, and "Add reminder".

### 4.6 Calculating list screen

See Section 6.

### 4.7 Reminders screen

Grouped list with filters (type, contact). Each item has a checkbox, title, time, linked contact chip, and a repeat icon if recurring.

### 4.8 Search

One box searching contacts, numbers, transactions, notes, lists, list items, files (by name), and accounts. Results are grouped by type, with the match highlighted. Recent searches are shown below the box.

### 4.9 Settings

Theme, currency, number format, date format, default reminder time, security (PIN and biometrics), backup and restore, export, import, data wipe, language, about and version.

## 5. Data model

Every record has `id` (UUID), `createdAt`, `updatedAt`, and `deletedAt` (soft delete, so Undo and a Trash view work). `contactId` is **nullable** on every entry type, and `null` means it belongs to General.

```
Contact {
  id, firstName, lastName, nickname, company,
  photo (blob id), birthday, tags[], favorite,
  phones[{ label, number, isPrimary }],
  emails[{ label, address }],
  addresses[{ label, text }],
  socials[{ app, handle }],
  customFields[{ key, value }],
  pinnedNote
}

Transaction {
  id, contactId?, type: 'credit' | 'debit',
  amount, currency, date, category, note,
  method, listId?, attachments[]
}

List {
  id, contactId?, kind: 'calculating' | 'checklist' | 'todo' | 'simple',
  title, currency, discount?, tax?, extraCharge?,
  isTemplate, postToLedger: boolean, items[]
}

ListItem {
  id, listId, name, quantity, unit, unitPrice,
  discount?, tax?, paidAmount, checked,
  dueDate?, priority?, note, sortOrder
}

Note { id, contactId?, title, body, tags[], pinned, attachments[] }

BankAccount {
  id, contactId?, label, holderName, bankName,
  accountNumber, ifscOrSwift, upiId, branch, notes
}

MessageLog { id, contactId?, channel, text, direction, date }
MessageTemplate { id, title, body }   // supports {name} {amount} {date} placeholders

Attachment { id, ownerType, ownerId, name, mimeType, size, blob, thumbnail }

Reminder {
  id, contactId?, title, notes, kind,
  dueAt, repeat: none|daily|weekly|monthly|yearly|custom,
  leadTimes[] (e.g. 1 day, 1 hour before),
  linkedType?, linkedId?, done, doneAt
}

Settings { currency, theme, locale, pinHash, biometric, defaultReminderTime ... }
```

Balance rule for a contact: **balance = sum(credit) minus sum(debit)**, where credit means they owe you more (the app must show a clear label such as "They owe you" or "You owe them" so there is no ambiguity). Use integers in the smallest currency unit (or a decimal library) to avoid floating point errors.

## 6. Calculating lists (key feature)

A calculating list is a list where each row has money values and the app does the math.

**Row fields:** item name, quantity, unit (kg, pcs, hours, etc.), unit price, optional per-row discount and tax, and a note.

**Calculations:**

- Line total = quantity × unit price, minus the row discount, plus the row tax.
- List subtotal = sum of line totals.
- Grand total = subtotal, minus the list discount, plus the list tax, plus any extra charge.
- Paid = sum of paid amounts on rows (plus any lump payment applied to the list).
- Pending = Grand total minus Paid.

**Same and different items:**

- Rows with the same name and unit can be **grouped** to show a combined quantity and total. This is a toggle, and the raw rows stay intact.
- Rows with different prices all add into the one grand total.

**Ticking paid:**

- Tapping the checkbox marks the whole row paid (paidAmount = line total).
- Long-press or tap the amount to enter a **partial payment** (such as 600 of 1,000). The row then shows a partial state and the remaining amount.
- Unticking reverses the payment.
- A **"Mark all paid"** and **"Clear paid"** action must exist.

**Totals bar:** a sticky bar at the bottom always shows **Total, Paid, Pending**, and updates instantly.

**Ledger link (per-list toggle `postToLedger`):**

- When ON, each time a payment is recorded the app creates a linked transaction on that contact's ledger (and removes or adjusts it if the payment is undone).
- When OFF, the list stays separate from the balance.
- Default is OFF, and the user is asked on first use. The linked transaction must show which list it came from, and tapping it opens that list.

**Other actions:**

- Duplicate a list, or save it as a **template** (for recurring monthly items).
- Reorder rows by dragging. Swipe to delete (with Undo).
- Export or share as **PDF or image** with the contact name, date, rows, and totals.
- "Send as message": generates a text such as "You owe 3,400 for these items" and opens WhatsApp, SMS, or another app.
- "Set reminder for pending amount" creates a reminder linked to the list.

**Other list types**

- **Simple list:** items with no amounts.
- **Checklist:** items with tick boxes and a progress indicator (3 of 8 done).
- **To-do list:** tasks with due date, priority (low, medium, high), optional alarm, and a done state. Overdue tasks are highlighted and appear on the Reminders tab.

## 7. Ledger (credit and debit)

- Add, edit, delete, and filter transactions.
- Per-contact running balance shown after each row.
- Categories (editable), payment methods (cash, bank, UPI, card, other).
- Filters: date range, type, category, contact, has attachment.
- Summary cards: total credit, total debit, net, for the chosen period.
- A statement view for one contact that can be exported as PDF and shared.
- Settle up: a button that adds an opposite entry equal to the current balance.
- General (no-contact) transactions count towards the user's own income and expense totals.

## 8. Notes, accounts, messages, files

**Notes:** title and body, with basic formatting (bold, bullets), tags, pinning, and attachments.

**Bank accounts:** save details and offer **Copy**, **Share as text**, and a QR for the UPI ID. Mask the account number by default with a tap to reveal.

**Messages:**

- A per-contact log of messages the user writes or saves inside the app. The app cannot read WhatsApp, Telegram, or SMS history, and the UI must not imply that it can.
- **Templates** with placeholders, such as a payment reminder: "Hi {name}, a gentle reminder about {amount} due on {date}."
- "Send" opens the chosen app with the text prefilled, and offers to save a copy to the log.

**Files and images:**

- Attach from camera, gallery, or file picker (images, PDF, documents).
- Show a thumbnail grid, full-screen image viewer with pinch to zoom, and PDF preview.
- Compress images on upload (maximum about 1600 px on the long side) and generate a small thumbnail.
- Store as blobs in IndexedDB, and show storage used in Settings.
- Share or download any file. Attach to any entry (transaction, list, note, reminder, contact).

## 9. Communication shortcuts

All shortcuts use standard links, so no special integration is needed.

| Action | Link format |
| --- | --- |
| Call | `tel:+<number>` |
| SMS | `sms:+<number>?body=<text>` |
| WhatsApp | `https://wa.me/<number-digits-only>?text=<text>` |
| Telegram (username) | `https://t.me/<username>` |
| Telegram (phone) | `https://t.me/+<number>` |
| Email | `mailto:<address>?subject=&body=` |
| Signal, Instagram, others | store the handle and open the matching web or app link |

Rules:

- Normalise numbers to international format using a default country code from Settings.
- Per-number actions: tapping a number offers Call, SMS, WhatsApp, Telegram, and Copy.
- Prefill message text from a template when the action starts from a list or ledger screen.
- **Phone book import:** use the Contact Picker API where supported (Android Chrome). Where it is not supported (iOS), offer **vCard (.vcf) import** and **CSV import** instead. Detect support and show only what works.

## 10. Reminders and alarms

Kinds: payment due, to-do, birthday, follow-up, custom. Each can be one-time or recurring, with lead times (for example 1 day before and 1 hour before).

**Features:**

- Snooze (10 min, 1 hour, tomorrow) and mark done.
- Reminders linked to a contact, list, or transaction, with a tap to open the linked item.
- Overdue items and today's items appear on the Reminders tab and as a badge on the tab icon.
- Quick presets: "Remind me when payment is due," "Every month on the 1st."

**Important platform limitation (must be handled honestly):**

A web app cannot guarantee exact alarms while it is closed. Implement in this order:

1. **In-app reminders:** always work, shown when the app opens, with a badge.
2. **Notifications:** use the Notifications API and service worker where permitted, requesting permission at a sensible moment (not on first launch). Use Web Push if a small optional server is added later.
3. Show a clear note in Settings: "For guaranteed alarms when the app is closed, install the native version."
4. Structure the reminder code behind an interface so it can be swapped for native local notifications if the app is later wrapped with **Capacitor**.

## 11. Security and privacy

- **App lock:** optional PIN (stored as a salted hash), with biometric unlock through WebAuthn where available. Auto-lock after a configurable idle time and when the app is backgrounded.
- **Encryption at rest (should):** encrypt sensitive fields and files with a key derived from the PIN using the Web Crypto API.
- **No tracking, no ads, no third-party analytics.** Data never leaves the device unless the user exports it.
- **Hide balances** toggle for privacy in public places.
- Mask bank account numbers by default.
- Provide **Delete all data** with a confirmation step.

## 12. Backup, export, import

- **Full backup:** one file (JSON plus attached files in a ZIP) that the user can save or share, with an optional password.
- **Restore:** import a backup, with a choice to merge or replace.
- **Exports:** contacts (CSV and vCard), transactions (CSV and PDF), lists (PDF and image), and contact statements (PDF).
- **Reminder to back up:** a gentle prompt if there has been no backup for 30 days.
- Request persistent storage (`navigator.storage.persist()`) so the browser does not evict data, and warn on iOS that clearing browser data deletes the app's data.
- **Optional later:** cloud sync, which is out of scope for version 1.

## 13. Non-functional requirements

- **Performance:** first load under 3 seconds on a mid-range phone, interactions under 100 ms, and smooth scrolling with virtualized lists for 5,000+ contacts or 50,000+ transactions.
- **Reliability:** all writes are transactional. No data loss on a crash or reload.
- **Compatibility:** latest two versions of Chrome and Safari (Android and iOS), plus desktop Chrome, Edge, and Firefox.
- **Offline:** every feature except sharing links must work with no connection.
- **Quality:** unit tests for all calculation and balance logic (this is the most error-sensitive code), plus basic end-to-end tests for add contact, add transaction, and tick paid.
- **Lighthouse targets:** PWA badge passed, and Performance, Accessibility, and Best Practices all at 90 or above.

## 14. Build phases

**Phase 1: Foundation**

- Project setup, PWA manifest and service worker, theming, bottom navigation, database layer, settings.

**Phase 2: Contacts**

- Contact list, add and edit, detail page, phone numbers and emails, quick-action buttons (call, SMS, WhatsApp, Telegram, email), search within contacts.

**Phase 3: Money**

- Transactions, contact balances, ledger tab, General space, summary cards, statement export.

**Phase 4: Lists**

- Simple, checklist, and to-do lists, then calculating lists with paid ticking, partial payment, totals bar, grouping, templates, and the ledger link.

**Phase 5: Notes, accounts, files**

- Notes, bank accounts, message templates and log, file and image upload with compression and viewer.

**Phase 6: Reminders**

- Reminder engine, recurrence, snooze, notifications, Reminders tab, links to contacts and lists.

**Phase 7: Security, backup, polish**

- PIN and biometrics, encryption, backup and restore, imports (vCard, CSV, Contact Picker), global search, accessibility and performance pass, install prompts.

## 15. Acceptance checklist

The app is complete when all of these are true:

- It installs to the home screen on Android and iOS and opens full-screen with its own icon.
- It works fully offline after the first load.
- The home screen looks and behaves like a contact app, with balances colored green or red.
- Any entry type can be created with or without a contact.
- A calculating list correctly computes line totals, grand total, paid, and pending, including partial payments, and the numbers match in the totals bar.
- Ticking paid optionally posts to the ledger and reverses cleanly when unticked.
- Call, SMS, WhatsApp, Telegram, and email buttons open the right app with the right number.
- Images and files can be attached, previewed, and shared.
- Reminders appear in-app and, where permitted, as notifications, with snooze and recurrence.
- Global search finds contacts, entries, lists, notes, and accounts.
- A PIN locks the app, and a backup can be exported and restored with no data lost.
- All calculation logic has passing unit tests.

## 16. Out of scope for version 1

- Reading WhatsApp, Telegram, or SMS conversations automatically.
- Reading the phone's call log (not possible in a web app).
- Multi-user sharing and live cloud sync.
- Guaranteed exact alarms while the app is fully closed (needs a native wrapper).

## 17. Open decisions (ask the owner, or use these defaults)

| Question | Default if not answered |
| --- | --- |
| Does ticking paid post to the ledger automatically? | Off, with a per-list toggle |
| Single currency or multiple? | Single default currency, selectable per list |
| Default country code for numbers | Taken from Settings, asked on first launch |
| Is a cloud backend wanted? | No, local only |
| Native wrapper later? | Yes, keep the code Capacitor-friendly |
