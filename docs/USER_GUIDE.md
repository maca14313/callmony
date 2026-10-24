# callmony — Complete User Guide

> **callmony** — *Talk. Track. Settle up.* Contacts, calls & a money ledger for your phone. 100% offline — all app data lives on your device. Contacts and the call log come from the phone itself.

This guide documents **every screen, every button, every gesture and every feature** of the app (v1.6.11).

**Contents**
1. [Getting started](#1-getting-started)
2. [The home screen — Recents](#2-the-home-screen--recents)
3. [Keypad](#3-keypad)
4. [Calculator](#4-calculator)
5. [Search](#5-search)
6. [Contact detail](#6-contact-detail)
7. [Adding & editing contacts](#7-adding--editing-contacts)
8. [The money ledger](#8-the-money-ledger)
9. [Calculating lists](#9-calculating-lists)
10. [Notes](#10-notes)
11. [Reminders](#11-reminders)
12. [Bank accounts](#12-bank-accounts)
13. [The General tab](#13-the-general-tab)
14. [Trash & undo](#14-trash--undo)
15. [Speed dial & merge duplicates](#15-speed-dial--merge-duplicates)
16. [Settings — everything explained](#16-settings--everything-explained)
17. [Security — the PIN lock](#17-security--the-pin-lock)
18. [Backup, email sync & exports](#18-backup-email-sync--exports)
19. [Languages & theming](#19-languages--theming)
20. [Troubleshooting & reliability](#20-troubleshooting--reliability)

---

## 1. Getting started

### 1.1 First launch

On the very first open, callmony shows a one-time **Welcome** screen:

- **Currency** — pick the currency all money amounts will use. Base set: ETB (Br), USD ($), EUR (€), GBP (£), AED, SAR, KES (KSh), INR (₹) plus regional ones (EGP, NGN, ZAR, TZS, UGX, SDG, DJF, SOS, CNY, TRY) and an **Other** pill that lets you type any 3-letter code. Default: **ETB**.
- **Country code** — pick your country (searchable list, e.g. *Ethiopia +251*). This is used when normalizing phone numbers so that `09…`, `+251…` and `9…` all match the same contact. Default: **ET (+251)**.
- **Get started** — saves your picks and enters the app. The button is pinned at the bottom so it can never be cropped, and the defaults are always pre-selected — the back arrow also saves, so the screen can never trap you.

You can change both at any time in **Settings → Region**.

### 1.2 Dialer-style permission asks (once per install)

Right after onboarding the app asks, in a single dialog group, for the same permissions a built-in dialer asks for:

| Permission | What it enables |
|---|---|
| **Contacts** (READ_CONTACTS) | the address book powering Recents names, Search, Keypad matching |
| **Call log** (READ_CALL_LOG) | the real system call history on the Recents tab |
| **Phone** (CALL_PHONE) | placing calls instantly without the system keypad |

Already-granted permissions resolve silently. If you refuse, the app still opens — each screen shows a friendly card with an **Allow** button (first tap re-asks; after a permanent denial the button becomes **Open settings**, the only place Android lets you undo it).

### 1.3 How the app opens (every launch)

- callmony **always opens on Recents** — like a real dialer.
- The **call log loads instantly** from an on-device cache — there is no "loading calls" wait. The system log is refreshed silently in the background (usually zero new rows cross that bridge at all).
- A **140 ms splash** flashes (never a spinner), and your data (ledger, lists, notes, reminders, banks, settings) is restored in one parallel read.

### 1.4 Navigation

**Bottom bar — 5 slots:**

| Slot | What it does |
|---|---|
| **Recents** (history icon) | Home tab — the call log |
| **Keypad** (dialpad icon, raised brand tile) | Opens the keypad as a half-modal **on top of Recents**; tap again to close |
| **Calculator** (calculator icon) | Opens the calculator as an overlay over the current tab; tap again to close (toggle) |
| **General** (wallet icon) | Money overview + General-owner data |
| **More** (dots icon) | Opens the More sheet (below). Shows a **red badge with the count of overdue reminders** (over 9 → "9+") |

**More sheet — 8 tiles + offline pill:**

| Tile | Action |
|---|---|
| **Language** | pick the app language (5 languages) |
| **Theme** | Light / Dark / Auto |
| **Reminders** | opens the Reminders tab (red overdue badge) |
| **Merge duplicates** | scan & merge duplicate contacts |
| **Speed dial** | assign contacts to keys 2–9 |
| **Trash** | opens the Trash tab |
| **Settings** | opens Settings |
| **About** | version & feature overview |

The footer pill reminds you the app **works fully offline**.

> **Where did the Contacts tab go?** The Contacts tab is intentionally hidden from the bottom bar — Recents, Search and the Keypad already use your whole address book. All contact features (detail pages, editor, favorites, merge, speed dial, blocked numbers) are still fully available.

**Top strip (Recents/General):** the callmony logo, the wordmark, the tagline *“Talk. Track. Settle up.”* and the **search button** (magnify icon) — the single entry point to global search.

**Secondary tabs** (Reminders, Trash) show a back arrow that returns to Recents.

---

## 2. The home screen — Recents

The real Android system call log (calls started from callmony on iOS), grouped by day, with balances and contacts woven in.

### 2.1 Layout — top to bottom

1. **Credit summary strip** (only when someone owes you money or you owe them): three slim cards —
   - **To receive** (green) — total owed to you
   - **To pay** (red) — total you owe
   - **Net** (brand tint) — the difference
2. **Contacts-locked row** (only if contacts permission is denied): amber card with an **Allow** button (re-asks; second tap becomes **Open settings**).
3. **Favorites strip** — always visible. Horizontal row of favorite contacts (avatar + first name). Tap opens the contact. (Shown even with zero favorites so it's always reachable.)
4. **Owe chips** — **They owe** and **You owe** — tap to filter the call list to contacts with that balance.
5. **The call list** — day-grouped call cards with sticky day headers, infinite scroll (60 rows/page) and pull-to-refresh.

### 2.2 Day headers

Each day header shows:

- **The date only** — a tiny plain `Oct 24` label (no "Today/Yesterday", no weekday — by design).
- **＋ create-contact icon** (the person-with-plus glyph) — opens the in-app contact creator. It's in **every** day header, always one tap away.
- **Direction filter chips** (sticky — they stay on screen while you scroll):
  - **All** (text chip)
  - **Missed** (phone-missed icon, with a live count badge; turns red whenever missed calls exist)
  - **Incoming** · **Outgoing** (icon-only)
  - **Favorites** (star icon)

### 2.3 Call cards

Each card shows:

- **Avatar** (photo or colored initials) — resolved from your address book by phone number, in any format (`+251…`, `09…`, `9…` all match).
- **Name** — the contact's full display name; an unknown number is pretty-printed; no number → "Unknown number". **Missed calls render the name in red.**
- **Meta line** — direction is **icon-only** (red missed / green incoming / brand outgoing), the time (`h:mm AM/PM`), and the duration (`m:ss`) when > 0.
- **Balance line** — if the contact owes you or you owe them, a colored pill **replaces the phone number**: `+300 · They owe you` (green) or `−150 · You owe them` (red). Otherwise the pretty-printed number is shown.
- **Right column** — relative time (`5m`, `3h`, `2d`, then `Mon D`) and a **redial button** (phone icon) — one tap, instant call.

### 2.4 Gestures on a call card

| Gesture | Action |
|---|---|
| **Tap** | known contact → opens the contact detail; unknown number → opens the action sheet |
| **Long-press** (650 ms, with vibration) | opens the action sheet |
| **Swipe right** | reveals **Call** (green) + **Favorite** (gold star) |
| **Swipe left** | reveals **WhatsApp** + **SMS** |
| **Pull down** at the top | full refresh of the system call log |

Swipes are horizontal-dominant — scrolling the list never half-opens a row.

### 2.5 The call action sheet (long-press or tap on an unknown number)

Title = contact name or number; sub-line = full date · time · duration.

- **Call** — instant call (first phone of the contact if known)
- **WhatsApp** — opens `wa.me` chat for that number
- **SMS** — opens the SMS app
- **Copy number** — clipboard + toast
- For known contacts: **Favorite toggle** and **Call history** (opens the contact's history)
- For unknown numbers:
  - **Create new contact** — opens the in-app creator **prefilled with the number**
  - **Add to existing contact** — swaps the sheet to a contact picker; pick a contact and the number is added to them loss-free (duplicate numbers are detected)
- **Long-press any option row** → copies the number
- (iOS app-log rows only) **Delete log**

### 2.6 How the call log stays fast

- The log is **cached on disk** — every open paints it instantly.
- Background refresh is **incremental** — only rows newer than the newest cached call are read (1-minute overlap, deduplicated). A refresh that changes nothing causes zero re-render — no flicker.
- A **full re-read** happens only when the list is empty, the snapshot is older than 10 minutes (reconciles deletions made in other apps), or you pull-to-refresh.

---

## 3. Keypad

The keypad rises as a **half-modal over Recents** (grabber → header with speed-dial grid icon, "Keypad" title and close ✕ → live matches → number display → 3×4 key grid → bottom action row). It closes via scrim tap, grabber tap or ✕.

### 3.1 Keys

`1` · `2 ABC` · `3 DEF` · `4 GHI` · `5 JKL` · `6 MNO` · `7 PQRS` · `8 TUV` · `9 WXYZ` · `*` · `0 +` · `#`

Every press vibrates lightly (10 ms). **Long-press `0`** types `+`. **Long-press 2–9** triggers **speed dial** (see §15) — calls the assigned contact instantly or opens the assignment screen.

### 3.2 The number display — real copy & paste

The display is a **real text input**, so Android's native **long-press Copy / Paste / Select-all** menu with selection handles works exactly like any other input. The soft keyboard is suppressed — digits come from the grid.

- **Paste sanitization** — a strict pass first (whole text must be dialable: digits `+ * #`, max 24 chars), then a lenient pass (extracts the first number-like run of ≥ 9 digits from copied prose). A fully non-dialable paste is rejected with a "No number in clipboard" toast; a successful paste vibrates and toasts "Pasted".
- **Paste button** (clipboard icon) — visible whenever the clipboard holds text or the display is empty; one tap pastes with the same extraction.
- **Backspace** — tap deletes one char (vibrates); **hold 550 ms clears everything**.
- Font auto-shrinks as the number grows (28 → 24 → 21 px).

### 3.3 Live matches while you type

From 2 digits on, matching contacts appear above the display: avatar, name, and the **balance pill** (or the number). **Tap → call.** **Long-press (380 ms) → open the contact.**

Matching is country-code-agnostic — the query is normalized to national form, so `+251…`, `09…` and `9…` hit the same contact. Scoring: phone prefix (4) > phone substring/suffix (3.5) > T9 name match (3), then name order. An **exact match** (≥ 6 digits equal) shows a pill under the display with the avatar + name — tap to open the contact.

### 3.4 The action row

- **Video** button — placeholder (shows an info toast)
- **Call** button — big brand circle. Empty number → "Enter a number" toast; otherwise the sheet closes and the call is placed **instantly** (see below)
- **Add-contact** button (person-plus) — opens the in-app contact creator prefilled with the typed number

### 3.5 How a call is placed (never a simulation)

1. Blocked-number check first (Settings blocked list) → error toast if blocked.
2. Android: our own **ImmediateCall** native module fires `ACTION_CALL` — the **default dialer app places the call instantly**; the number is never dropped into the keypad.
3. If the CALL_PHONE permission was denied: the runtime dialog is re-shown; still denied → `ACTION_DIAL` fallback (system keypad pre-filled) → "Continued in dialer" toast; any failure → `tel:` link.
4. iOS: native `tel:` handoff via CallKit.

Result feedback: `placed` (silent — you're already in the call), `dialer`, `failed` ("Call ✕" error toast).

---

## 4. Calculator

Opened from the bottom bar (toggle tile) or from the calculator icon in any amount field (the **Use value** flow). A full sheet with a back button, a history toggle and a copy-result icon in the header.

### 4.1 Layout

- **Display card** — main line: the full expression (or the result after `=`); sub line: the live evaluated result. Errors render red.
- **Memory row** — `M+` (add current value) · `M−` (subtract) · `MR` (recall) · clear-memory (when set) · **Use value** button (when opened from a field). The subtitle shows `M = value` while memory is set.
- **Keypad (4×5):**

```
 AC   ⌫   %   ÷
  7   8   9   ×
  4   5   6   −
  1   2   3   +
  ±   0   .   =
```

- **History panel** (toggle) — the last 30 calculations, newest first; **tap a row to reload that expression**.

### 4.2 Behavior

- **AC** clears everything; **⌫** deletes one char and **holds to repeat** (450 ms delay, then every 110 ms).
- **%** evaluates the current expression and divides by 100.
- **±** flips the sign of the last number only.
- Max 12 digits per number, one decimal point per number; after `=` a digit starts a fresh expression; a new operator replaces a trailing one; `−` may lead, `×÷` may not.
- **`=`** evaluates with real precedence (× ÷ over + −, left-associative) — a safe shunting-yard evaluator, no `eval`. Division by zero → "Error". Results are formatted compactly (huge values → exponential notation).
- Every key has haptics; **Use value** sends the current number back to the field that opened it.

---

## 5. Search

The magnify button in the top strip opens the global **Search everything** overlay. It debounces at 70 ms — results arrive essentially as you type, even with 5000 contacts.

### 5.1 What it searches

| Group | Cap | Matches on |
|---|---|---|
| **Contacts** | 50 | name (multi-token, initials, word-substring), company, email, phone in any format |
| **Ledger entries** | 5 | note, category, amount |
| **Lists** | 5 | title + item names |
| **Notes** | 5 | title + body |
| **Reminders** | 5 | title + notes |
| **Bank accounts** | 5 | bank name + holder |

Every result row opens the matching detail/editor. Contact rows carry the same **balance pill** as call cards.

### 5.2 The matching engine (advanced)

- **Multi-token queries** — `abdu mo` finds "Abdu Mohammed" (every token must match somewhere).
- **Initials runs** — `kb` → "Kalkidan Bekele".
- **Word-substring** — `abd` also hits "H**abd**ullah".
- **Phone matching in every format** — `+251…`, `09…`, plain `9…` (numbers are indexed raw + normalized + national).
- **T9** — letters on the keypad grid map to digits.
- Ranked by weighted score (exact number 7 · name prefix 6 · number prefix 6 · T9 5.5 · initials 5.2 · suffix 5 · word-substring 4.5 · name 4 · company/email 3), ties broken by name.
- A READ_CONTACTS-denied banner with an **Allow** button appears when applicable.

---

## 6. Contact detail

Opens from any call card, favorite tile, search result, keypad match, or the people list on General. A full-screen profile that **opens instantly** (fade animation; header + balance + the 12 tiles paint in the first frame, the section body mounts a beat later).

### 6.1 Header

- Back arrow · **avatar** · **full display name** · primary phone (or company, or "No phone number on this contact"). **Long-press the subtitle to copy the number.**
- **Pencil** → the in-app contact editor
- **Star** → favorite toggle (gold when on; toasts confirm)
- **⋯** → more sheet (Edit · Share vCard · Email · Copy number · Open in dialer · Add social)

### 6.2 Balance line

A pill with the signed amount: **They owe you** (green) / **You owe them** (red) / **Settled up** (neutral). Includes the pending amount of that contact's open post-to-ledger lists (shown as "(Open lists: X)" when non-zero).

### 6.3 The 12 quick-action tiles (6 × 2 grid)

| Tile | Behavior |
|---|---|
| **Call** | opens the number picker (deduplicated — the same number saved as `09…` and `+251…` appears once) |
| **SMS** | number picker → SMS app |
| **WhatsApp** | number picker → `wa.me` chat, **with a pre-filled message**: if they owe you → a gentle reminder incl. amount & due date; if you owe them → a settle-up request |
| **Telegram** | picker: saved handles + `t.me/+number` links built from the contact's numbers |
| **Email** | picker of saved addresses → mail app |
| **Ledger** · **Bank** · **Lists** · **Notes** · **Reminders** | jump to that section of the profile |
| **Info** | the info tab |
| **＋ Add to contact** | add phone / email / social, or edit |

### 6.4 The six sections

**Ledger** — that contact's entries, groups, stat cards and actions (identical to §8, scoped to the contact).

**Lists / Notes / Bank / Reminders** — the same sections as the General tab, scoped to the contact (§9–§12).

**Info tab:**
- **Call history** — the real system log matched against all of the contact's numbers (top 10). Direction icon, relative time, duration. **Tap a row to call back.**
- **Birthday** card (🎂) when present.
- **Phones** — deduplicated rows: label chip, pretty number, gold star for the default number. Tap → per-number options (Call · WhatsApp · SMS · Copy · **Set as primary number**). Long-press copies. Mini copy/call buttons on each row. A trailing "＋ Add or edit numbers" opens the editor.
- **Emails** — label + address; tap or long-press copies; a mini button composes mail.
- **Socials** —
  - **Auto rows**: WhatsApp, Telegram, Signal and Viber chats built automatically from the contact's number (`wa.me`, `t.me/+`, `signal.me/#p`, `viber://chat`) — no usernames needed. Labeled "FROM NUMBER".
  - **Saved socials** (unlimited): Telegram, WhatsApp, Signal, Viber, Instagram, X or any link — tap opens, copy and trash mini buttons per row.
  - "＋ Add social" → the add-social sheet (app chips; number-based apps pre-fill the handle with the contact's number).
- **Addresses, work info (company + job title), note** — when present; addresses copy on tap.

### 6.5 Add-to-contact sheet

**Add phone / Add email / Add social / Edit contact** — quick paths to extend the contact without leaving the profile.

---

## 7. Adding & editing contacts

All contact editing happens **inside callmony** — simple forms, zero information loss.

### 7.1 The creator (new contact)

Reached from: the ＋ icon in every Recents day header, the keypad's add button, the call action sheet ("Create new contact", prefilled with the number), and the keypad's live matches.

Fields: **First name · Middle name · Last name** · phone rows · email rows. Each phone row has a **label button that cycles** mobile → work → home → other, and a **default-number star** — the number callmony dials and shows first. "＋ Add phone" / "＋ Add email" add rows.

Saving requires a name or a phone. The contact is written to the real address book with a single native transaction, appears in the app instantly (no spinner), and the book re-syncs in the background.

### 7.2 The editor (existing contact)

Same fields, plus **Company**. It loads with a 6-second hard timeout (can never spin forever) and uses a defensive merge: if the native load ever returns fewer phones/emails than the app knows about, they are unioned back — **a save can never wipe numbers**.

Validations:
- A contact needs at least a name part, company, or a phone.
- A contact that **had** numbers cannot be saved with zero — "Keep at least one phone number…".
- The default star must be among the saved numbers (falls back to the first).

**Zero-lag save:** the sheet closes the instant you tap Save; the local list is patched immediately; the full address-book re-fetch is deferred. Notes, addresses, birthdays and photos on the OS side are **never touched** by a save.

### 7.3 Full-name fidelity

Display names are composed **First + Middle + Last** — middle names are stored, searched, exported and displayed everywhere (vCard `N:`/`FN:` and CSV carry them too).

---

## 8. The money ledger

The ledger lives in two places: the **General tab** (your own book) and **each contact's profile** (what's between you and them). Both have identical mechanics.

### 8.1 The four entry kinds

| Kind | Direction | Meaning |
|---|---|---|
| **Credit** ("They owe you") | + | you gave goods/money on credit — they owe you |
| **Debit** ("You owe them") | − | you received on credit — you owe them |
| **Income** | + | money in (General tab) |
| **Expense** | − | money out (General tab) |

Localized exactly: ይገባኛል / ይገባዋል·ታል (Amharic), Anaaf mala / Isaaf·Ishiif (Afaan Oromoo), يحق لي / يحق له·لها (Arabic), ይግባኣኒ'ዩ / ይግባኦ·ኣ'ዩ (Tigrinya).

### 8.2 The entry editor

- **Kind chips** — Income / Expense / Credit / Debit (trending icons).
- **Amount** — the big hero field, numeric-pad, comma tolerated, with a **calculator button** (Use value flow) and a formatted caption. **When line items exist it auto-calculates** from them (read-only with a live total).
- **Line items** — "Items (qty × price)": each line = *name × qty (+ unit) × unit price = line total* (e.g. `ball 3 pic × 100 = 300`). Saved lines are cards — **tap one to re-open the form pre-filled** (edit-in-place with a banner + Cancel chip). Quick unit chips: `pic kg pcs bag box l`. The dashed totals row shows Σ quantity and Σ amount.
- **Date** (defaults today) and **Method** (free text, default `cash` — hint: cash · bank · card · mobile · other).
- **Category** — 12 built-in chips (General, Loan, Rent, Sale, Salary, Groceries, Transport, Utilities, Health, School, Other) + your own custom categories (manageable inline: add/remove).
- **Due date** — toggle + date. **Saving a new entry with a due date auto-creates a reminder** for 9:00 AM that day ("Payment due", notes include the amount).
- **Note** — optional multiline.
- **Owner** — shown in the subtitle; tap the row to re-assign via the contact picker.
- **Groups** — "No group" / the owner's groups / **New** (inline create-and-select).
- Footer: **Delete** (with confirm + undo toast) and **Save**.

### 8.3 Entry cards & payments

Each card shows: the kind icon + label, the category pill, **Payment due** (red) / **Due today** (amber) pills, the items count pill, `date · running balance · payments n`, and the signed remaining amount. Expand for:

- The line items with their totals row
- The note
- **Payment history** — numbered rows (date · amount · method · note; auto payments marked `auto`) with per-row remove
- Status pills — **Paid X / Y**, **Pending X**, **Overpaid by X** (green), ✓ settled
- Buttons — **Add payment** · **Edit** · **Move** (to General or a contact) · **Delete** (soft + undo)

**The payment sheet:** amount + "Fill full remaining amount", date, note, and a method segmented control (Cash/Bank/Card/Mobile/Other).

### 8.4 Settle up

One button adds payments **dated today** that bring the balance to exactly zero: entries are walked oldest-first, netting credit against debit, until nothing remains. Toast confirms — "Settled ✓".

### 8.5 Groups

Collapsible group cards (folder icon, entry count, paid total, remaining balance) with a ⋯ menu: **Add entry / Rename / Delete** (deleting a group detaches its entries, nothing is lost; undo re-attaches). "New group" and "collapse all" chips at the top.

### 8.6 Filters & sorting

Ungrouped entries filter by **All / Credit (Income) / Debit (Expense) / Unpaid / Overdue**; sorted date-desc. The running balance is computed oldest → newest and shown on every card.

### 8.7 Share statement

A sheet with four paths: **WhatsApp** (the full statement text pre-filled to the contact's number), **Share** (a `.txt` file), **Copy**, and **Export CSV**. The statement lists every entry with its line items and running balance, ending in `Net:`.

### 8.8 The math (exact)

```
entryRemaining        = amount − Σ payments        (negative = overpaid)
entrySignedRemaining  = ±entryRemaining            (credit +, debit −)
ownerBalance          = Σ entrySignedRemaining
ownerFullBalance      = ownerBalance + open post-to-ledger list pending
Net (General)         = income − expense + open lists pending
```
All rounded to 2 decimals at every step; epsilon 0.005.

---

## 9. Calculating lists

Lists live on the General tab and inside contact profiles. Four kinds:

| Kind | What it's for |
|---|---|
| **Calculating list** | shopping/bill lists with prices, discounts, tax — full math |
| **Simple list** | plain item list |
| **Checklist** | tick-off list with same-item grouping |
| **To-do list** | items with priorities |

### 9.1 The list card

Kind icon, title, `n items · relative time · repeat`, and for calculating lists the **grand total** plus a paid pill (`2/5` amber, or `All settled ✓` green). An amber "Count pending in balance" pill appears when the list posts to the ledger.

**Gestures:** tap opens; long-press → menu (Open / Duplicate / Save as template / Delete); swipe left → open; swipe right → Duplicate + Delete.

### 9.2 List detail

- **Sub-header card** — owner pill (tap to move the list to another contact/General), repeat pill, template pill, the **"Count pending in balance" switch** (`postToLedger`), the list note, and — for repeating lists — a "Duplicated for next period" button.
- **Totals card** (calculating lists) — Subtotal · List discount (−) · Tax % (+) · Extra charge (+) · **Total** · Paid · Pending. Tap to edit the three charges.
- **"Group same items" switch** — groups rows with the same name+unit.
- **Item rows** — a tick circle (**tick-to-pay**: ticking records a payment of the full remaining and marks it paid), the name (strikethrough when paid), `qty unit × price · date · Paid X`, the line total, and an expand chevron.
- **Expanded row editor** — name, qty, unit, unit price (calculator button), **row date, item due date, item note, per-row discount (% or amount), tax %**, priority chips (Low/Medium/High), "Add payment", "Mark all paid", "Remove", and the row's payment history.
- **Add-item form** at the bottom (name required, qty > 0; unit price for calculating lists).
- **⋯ menu** — Rename/edit · Duplicate · Save as template · **Mark all paid** · **Clear payments** · Delete.
- **Footer buttons** — **WhatsApp** (the bill text to the owner's number), **Share** (bill text: ✓/• items, subtotal, −discount, +tax, +extra, total, paid/pending), **Remind** (creates a payment reminder for tomorrow 9:00 AM with the pending amount).

### 9.3 The math (exact)

```
lineTotal   = max(0, qty×price − rowDiscount + rowTax%)   (per item)
subtotal    = Σ lineTotal
listDiscount= % of subtotal, or flat
listTax     = % of (subtotal − discount)
grand       = subtotal − discount + tax + extra
pending     = grand − paid
```

### 9.4 Templates

Template lists get their own dashed-card section ("Templates · for repeat bills") — each with a **New** button that duplicates it with payments cleared and rows unticked, ready for the next period.

---

## 10. Notes

Notes live on the General tab and in contact profiles.

- **New note** button on top; sorted **pinned first**, then by last edit.
- **Card**: optional color strip (5 palette colors), title (or "Untitled note"), 2-line body preview, `#tag` chips (first 4, `+N` overflow), relative time.
- **Editor**: Title, Body, Tags (comma-separated), a **Pinned** switch ("Pinned notes stay on top"), and color dots. Requires a title or body.
- **Gestures**: tap → edit; **swipe left → pin/unpin** (gold); **swipe right → Edit (blue) + Delete** (confirm + undo toast).

---

## 11. Reminders

Reminders that **actually ring** — even when the app is closed — with self-healing alarms.

### 11.1 The Reminders tab

Reached from the More sheet (red badge = overdue count).

- **Pinned header** (always on screen): stats `N overdue · N today · N upcoming` + the **New reminder** button (never scrolls away).
- **Notification status card** — reads the **real OS notification state**: green "Notifications on" with an ON/OFF pill, or amber "Turn on notifications" with a **Turn on** button. After a first denial, the button routes to the app's notification settings page (the only fix Android allows) — plus an Open settings fallback.
- **Status filter** (segmented): **Overdue · Today · Upcoming · Done · All** (default Overdue), each with live counts.
- **Kind chips**: Payment · To-do · Birthday · Follow-up · Custom.
- **Reminder cards grouped** Overdue (red) · Today (amber) · Upcoming · Done; each card: the done-toggle circle (tap = complete/reopen), the title (strikethrough when done), **Overdue** (red) / **Snoozed** (clock) pills, full date + time, a repeat icon, and a **⋮ menu**: Done/Reopen · **Snooze 10 minutes / 1 hour / 3 hours / Tomorrow 9 AM** · Edit.
- **Swipes**: left → Done/Reopen; right → **Snooze 1h** + **Delete** (confirm + undo toast).
- A dashed **"＋ Add reminder"** row always ends the list.

### 11.2 The reminder editor

- **Title** ("What should remind you?") + notes.
- **Kind chips**: Payment · To-do · Birthday · Follow-up · Custom.
- **Date & time via native pickers** — tappable rows open Android's material date and 12-hour time pickers; nothing invalid can be typed. Quick chips: Today / Tomorrow / Next week, and 9:00 AM / 1:00 PM / 6:00 PM / 8:00 PM. A live preview shows "Due: …".
- **Live notification status** — green "Reminders will alert you even when callmony is closed" or amber + a Turn on button.
- **Repeats**: Once / Daily / Weekly / Monthly / Yearly (+ "Every month on the 1st").
- **Alert me early**: multi-select **1 day before** / **1 hour before** lead-time pre-alerts.
- **Owner**: a contact (via picker) or General.
- **At save**: notification permission is requested if needed; a **one-time battery-exemption system dialog** is shown (the #1 OEM alarm killer — Tecno/Infinix/Xiaomi/Samsung battery savers); past-time confirmations for one-off reminders.

### 11.3 When a reminder fires

- **A real system notification** (HIGH-importance channel, sound + vibration) with two **quick actions: Done and Snooze 10 minutes** — works whether the app is open or closed.
- **In-app full-screen alarm dialog** (when the app is open): a pulsing bell (a cake 🎂 for birthdays), the kind label, title, notes and status, and buttons **Done** · snooze **10 minutes / 1 hour / 3 hours / Tomorrow 9 AM** · **Edit** · Close. For repeating reminders, Done advances to the next occurrence.
- **Lead-time pre-alerts** fire the same dialog at due-minus-lead (once each).
- **Reliability machinery** (automatic): exact AlarmManager triggers with a WorkManager fallback; every app open / foreground return **re-arms any alarm the OS dropped**; reminders re-schedule after device reboots.

---

## 12. Bank accounts

On the General tab and in contact profiles.

- **Cards**: bank icon, bank name, holder name, optional balance; the account number is **masked** (`•••• •••• 1234`) with an **eye toggle to reveal** and a **copy button** (copies the full number).
- The section header shows the **total balance** across accounts (when non-zero).
- **Editor**: bank name + account number (required), holder name, balance; when the owner is General a "Choose from contacts" button assigns the account to a contact.
- **Gestures**: tap → edit; swipe left → reveal; swipe right → **Share** (a preview sheet with the full details and a Share button) + **Delete** (confirm + undo).

---

## 13. The General tab

Your own money & data hub (owner = "General", no contact).

- **Hero card** — **global totals across everyone**: Income (Σ credits) · Expense (Σ debits) · **Net** (income − expense + open lists pending), with an "Open lists: X" footnote when applicable.
- **People section** — chips `Owed to you Σ` (green) / `You owe Σ` (red), and a list of **up to 30 people with non-zero balances**, sorted by |balance| — tap any row to open that contact.
- **Segmented sections**: **Ledger · Lists · Notes · Reminders · Accounts** — the same five sections as contact profiles, scoped to General.

---

## 14. Trash & undo

Everything you delete anywhere (entries, groups, lists, notes, reminders, bank accounts) is **soft-deleted** — it lands in the **Trash** tab (More sheet) and an **undo toast** appears at the moment of deletion.

- **Segmented filter**: All / Entry / Group / List / Note / Reminder / Bank, each with a live count.
- Rows sorted newest-first, showing the type icon, title, owner and a relative time.
- Per row: **restore** (↩) and **delete-forever** (trash, danger) buttons.
- Bulk row: **Restore all** (everything visible) and **Empty** (confirm → purge everything visible).
- Nothing is auto-purged — deleted items stay until you empty the trash.

---

## 15. Speed dial & merge duplicates

### 15.1 Speed dial (More sheet)

A grid of keys **2–9**. Assign a contact to any key (from the keypad's long-press or this screen). Tapping an assigned tile offers **Call now / Replace / Remove from key**. Long-pressing the digit on the keypad instantly calls the assigned contact.

### 15.2 Merge duplicates (More sheet)

Scans the address book for contacts sharing a phone number (or the same name + email). Each pair shows the **Keep** (primary) and **Duplicate** contact with avatars; **Merge** combines phones, emails, tags and all history into the primary and deletes the duplicate (with a confirm); **Dismiss** skips the pair. A refresh button re-scans.

---

## 16. Settings — everything explained

Settings opens from the More sheet. Sections in order:

### Appearance
- **Theme** — Light / Dark / Auto (Auto follows the OS; default).
- **Language** — the 5-language sheet (see §19).
- **Hide balances** — On/Off: blurs every money amount in the app until turned back off.

### Notifications
- **Allow notifications** button + hint ("Reminder alerts, even when callmony is in the background"). Also requested automatically at reminder-save time.

### Region
- **Currency** pills (the 8 base currencies) — changeable here after onboarding.
- **Default country code** — horizontally scrollable `+dial` pills (22 countries). "Used when adding phone numbers."

### Security
- **PIN lock** — Set PIN / Change PIN / Remove PIN (see §17).
- **Auto-lock** (only with a PIN set) — **Immediately when the app is left / After 1 minute (default) / After 5 minutes**.

### Backup & restore
- **Export backup** — a JSON file with contacts-extra data, ledger, lists, notes, reminders, banks and settings → the system share sheet. (The PIN is never included.)
- **Restore** — pick any backup file → choose **Merge** ("only records that don't already exist here will be added") or **Replace** (destructive, double-confirmed "Erase & restore").
- Footer: "Last backup: …" + a nudge when it's been over a month.

### Email sync
- **Send backups to** — your email address.
- **Auto email backup** — Off / Daily / Weekly. On each app open (after the interval elapses) the app builds the backup **plus a vCard of every contact** and opens your email app with both attached — you just press Send. No accounts, no SMTP, nothing leaves the device except that email.
- **Sync now** — does it immediately.
- "Last sync: …"

### Import & export
- **Export vCard** — `callmony-contacts.vcf` (full names incl. middle, phones, emails, org, birthday, note).
- **Export CSV** — `callmony-contacts.csv`, Google-Contacts-compatible columns.
- **Export transactions** — `callmony-transactions.csv` (date, type, kind, amount, paid, remaining, category, method, due date, note, items).

### About
Opens the About overlay (version, offline pill, feature cards, privacy note).

### Danger zone
- **Delete all data** — "Erase everything on this device and start fresh" — double confirmation → wipe (app data only; device contacts are untouched).

---

## 17. Security — the PIN lock

- **Setup** (Settings → Security): enter a 4-digit PIN twice (dots fill as you type; mismatch → shake + restart). **Change** asks for the current PIN first. **Remove** asks for the current PIN and removes the lock — permanently.
- **Lock screen**: full-screen cover with the logo, 4 dots, a keypad, and a **Forgot PIN?** link — resetting the PIN unlocks the app and **keeps all data** (the note in Settings explains: the PIN locks the screen; data stays on the device unencrypted, so a forgotten PIN can be safely reset).
- **Auto-lock**: immediately on leaving the app, or after 1/5 minutes of **inactivity** (any touch anywhere resets the timer).
- **Storage detail**: the PIN hash (salted, 2000-iteration SHA-256) lives in Android's **`no_backup` directory** — explicitly excluded from backups. Removing the PIN is forever: even an uninstall+reinstall (with app data restore) cannot bring it back.

---

## 18. Backup, email sync & exports

**The data-safety ladder** (4 independent layers):

1. **Mirrored writes** — every save is written to primary storage **and** a mirror copy; reads fall back to the mirror if the primary is damaged.
2. **Automatic local snapshots** — every 6 hours the full data set is written to `Documents/backups/` (newest 8 kept).
3. **Android Auto Backup** — app data rides the OS backup (restore on reinstall). The PIN is deliberately excluded.
4. **Email backups** — daily/weekly (or Sync now): JSON backup + full vCard attached to a pre-composed email.

**Export formats:** JSON backup (restore-able), vCard, contacts CSV, transactions CSV, statements (txt), bill texts, bank details.

---

## 19. Languages & theming

### Languages

**English · አማርኛ (Amharic) · Afaan Oromoo · العربية (Arabic, RTL) · ትግርኛ (Tigrinya)** — 976 translated keys each. Pick in Settings → Language or the More sheet. Non-RTL switches apply instantly; switching to/from Arabic flips the layout direction on the next app launch (a restart notice is shown — standard Android behavior).

### Theming

Light / Dark / Auto. Dark mode covers every surface, including the system navigation bar and status bar. The palette:

| Token | Light | Dark |
|---|---|---|
| Canvas | `#f7faf9` | `#0e1514` |
| Card | `#ffffff` | `#16211f` |
| Ink | `#172b2a` | `#e8f1ef` |
| Brand | `#16a085` | `#2fc4a6` |
| Green / Red / Amber | `#1d9a6b` / `#d64545` / `#b97d10` | `#3ecf94` / `#ff7b72` / `#e3b341` |

Typography floors: nothing smaller than 13 px; hero 30 / title 22 / h2 18 / body 16 / small 14 / tiny 13 / keypad 30.

---

## 20. Troubleshooting & reliability

| Symptom | What happened / what to do |
|---|---|
| **"callmony closed unexpectedly" screen** | a previous crash was captured; you can **Copy report** for the developers, **Continue**, or Reset app data. Your data is safe. |
| **Safe mode banner** (green) | the app failed to boot 3 times in a row; permission prompts and background sync are paused so you can always get in. It clears after one stable session (35 s). |
| **Amber "Recovered from an error" banner** | a JS error was caught and contained — dismiss it; nothing was lost. |
| **Reminders don't ring** | check the notification status card on the Reminders tab (Turn on), accept the one-time battery-exemption dialog, and mark the app "unrestricted" in your phone's battery settings. Alarms self-heal on every app open. |
| **Contacts names missing in Recents** | contacts permission denied — use the Allow card at the top of Recents, or App settings → Permissions. |
| **A contact's number changed in another app** | the call log fully re-reads every 10 minutes; pull-to-refresh forces it now. The address book re-reads every 5 minutes of app use. |
| **Forgot the PIN** | lock screen → Forgot PIN? → confirm → the lock is removed, data kept. |

---

*This guide covers callmony v1.6.11 (versionCode 21). For the technical internals see [ARCHITECTURE.md](ARCHITECTURE.md); for the full feature inventory see [FEATURES.md](FEATURES.md); for every screen and component see [UI_REFERENCE.md](UI_REFERENCE.md).*
