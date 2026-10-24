# callmony — UI Reference (every screen, overlay, component & gesture)

The complete visual/interaction reference for callmony v1.6.11. Companion documents: [USER_GUIDE.md](USER_GUIDE.md) (how to use), [FEATURES.md](FEATURES.md) (inventory), [ARCHITECTURE.md](ARCHITECTURE.md) (internals).

**Contents**
1. [Visual design system](#1-visual-design-system)
2. [App shell & navigation](#2-app-shell--navigation)
3. [Screens](#3-screens)
4. [Overlays (sheets & modals)](#4-overlays-sheets--modals)
5. [Shared component kit](#5-shared-component-kit)
6. [Gesture dictionary](#6-gesture-dictionary)
7. [States & feedback patterns](#7-states--feedback-patterns)

---

## 1. Visual design system

### 1.1 Color tokens (theme.ts)

| Token | Light | Dark | Used for |
|---|---|---|---|
| `canvas` | `#f7faf9` | `#0e1514` | app background |
| `card` | `#ffffff` | `#16211f` | cards, sheets |
| `cardAlt` | `#eef4f2` | `#1c2927` | inset cards, keypad digits |
| `ink` | `#172b2a` | `#e8f1ef` | primary text |
| `sub` | `#5c6f6c` | `#9db4b0` | secondary text |
| `faint` | `#8ba09c` | `#6b827e` | tertiary text, chevrons |
| `line` | `#e2ebe8` | `#243230` | hairlines |
| `brand` | `#16a085` | `#2fc4a6` | brand, CTAs, active states |
| `brandSoft` | `#e0f2ee` | `#16443c` | soft brand fills, active chips, avatar rings |
| `onBrand` | `#ffffff` | `#08201c` | text on brand |
| `green` | `#1d9a6b` | `#3ecf94` | incoming, they-owe-you, success |
| `red` | `#d64545` | `#ff7b72` | missed, you-owe-them, danger |
| `amber` | `#b97d10` | `#e3b341` | warnings, due-today, favorites gold |
| `blue` | `#4a7d9b` | `#7ab3d0` | SMS, secondary info |
| `chip` | `#e7efec` | `#20302d` | chip backgrounds |
| `scrim` | `rgba(9,22,21,.45)` | `rgba(0,0,0,.6)` | modal backdrops |
| `inputBg` | `#ffffff` | `#1c2927` | text inputs |
| `headerBg` | `#f7faf9` | `#0e1514` | top strip |

Theme setting: `light | dark | auto` (default **auto** — follows the OS). StatusBar (`light-content`/`dark-content`) and the **system navigation bar color** follow the resolved theme on every change and at cold start (values-night-v27).

### 1.2 Type scale (floor 13 — nothing smaller)

| Style | px | Weight | Use |
|---|---|---|---|
| hero | 30 | — | splash, calculator display, keypad display |
| title | 22 | — | sheet titles |
| h2 | 18 | 800 | headers, list titles |
| body | 16 | — | body text |
| small | 14 | — | secondary |
| tiny | 13 | — | meta, chips, day labels (11 for day date labels) |
| keypad | 30 | — | keypad digits |

`Money` component: compact formatting (|v| ≥ 100,000 → `999K` / `1.2M`), `fit` auto-shrink (min scale 0.42) so huge balances never wrap, `colorize` (green/red by sign), `signed` (+/−), hidden mode (`•••`) when "Hide balances" is on.

### 1.3 Avatars

Contact photo (or `thumbnailPath`) when available; otherwise initials on an 8-color pastel palette seeded by the first character of the name. Sizes: 34 (headers) · 36 (pickers, matches) · 44 (call cards) · 46 (contact rows) · 52 (favorites strip).

### 1.4 Brand identity

- Logo tile: rounded square with the `phone-in-talk` Material icon, brand background.
- Wordmark: **call**mony ("call" in ink, "mony" in brand).
- Tagline: *Talk. Track. Settle up.*
- Splash: 88×88 logo tile, 140 ms scale-settle (0.86 → 1, quad easing, native driver).

---

## 2. App shell & navigation

### 2.1 Structure

```
SafeAreaProvider
└─ AppErrorBoundary            (render errors → recovery card)
   └─ hydrated ? Shell : Splash
      Shell
      ├─ Header (top strip)
      ├─ Content (the active screen, flex 1)
      ├─ BottomNav              (flex sibling — can never cover content)
      ├─ OverlayHost            (stack of sheets; see §4)
      ├─ KeypadSheet            (while keypadOpen)
      ├─ MoreSheet              (while moreOpen)
      ├─ OnboardingSheet        (while !onboarded)
      ├─ AlarmDialog            (due reminders)
      ├─ LockScreen             (while locked)
      ├─ Toaster + ConfirmHost
```

### 2.2 Top strip (header)

- **Primary tabs (Recents, General):** logo tile 38×38 r12 · wordmark · tagline (tiny) · **search IconBtn** (magnify). Calculator and settings were deliberately moved out of the header (v1.6.8/user requests).
- **Secondary tabs (Reminders, Trash):** back arrow IconBtn + title (Recents/Reminders/Trash/General).
- Padding: top `max(insets.top, 8)`, row minHeight 52, hairline bottom border, horizontal 14.

### 2.3 Bottom navigation

5 icon-only slots, row height 70, each tile minHeight 64 / minWidth 60, ripple radius 34; active tile = 48×34 r12 `brandSoft` fill + brand icon (inactive = transparent + sub icon). `paddingBottom: max(insets.bottom, 6)` — safe-area aware. Accessibility: `tablist`/`tab` + `selected`.

| Slot | Icon | Active behavior |
|---|---|---|
| Recents | `history` | home tab |
| Keypad | `dialpad` | raised "floating dialer button" style (52×40, brand bg + white icon + 1.5 px brand border when inactive; white bg + brand icon when active) — opens/closes the keypad half-modal **over Recents** |
| Calculator | `calculator-variant` | toggle — opens/closes the calculator overlay over the current tab |
| General | `wallet-outline` | tab |
| More | `dots-horizontal-circle-outline` | opens More sheet; **red badge = overdue reminders** (>9 → "9+") |

Every tab tap also runs `closeAll()` (dismisses keypad + More) so nothing lingers behind a scrim.

### 2.4 More sheet

Bottom sheet: 8 tiles (flex-wrap, flexBasis 30%, maxWidth 150, r18, minHeight 84, icon 24 brand, tiny label, red badge cap "9+"): **Language** (translate; label = current language's native name) · **Theme** (weather-night/sunny/theme-light-dark; opens a nested chip sheet: Light/Dark/Auto) · **Reminders** (bell-outline + overdue badge) · **Merge duplicates** · **Speed dial** (grid) · **Trash** (trash-can-outline) · **Settings** (cog-outline) · **About** (information-outline). Footer: centered green pill "Works offline" (cloud-off-outline icon).

### 2.5 Hidden/secondary navigation

- **Contacts tab: hidden** — no bottom-nav slot; no `setTab("contacts")` call anywhere; the code and data remain (search/keypad/Recents resolution).
- **Reminders/Trash:** reached from the More sheet; their headers show a back arrow → Recents.

---

## 3. Screens

### 3.1 RecentsScreen (home)

Top → bottom:

1. **Credit summary strip** (when owedToYou or youOwe > 0.005): three slim stat cards — To receive (green arrow-bottom-left icon) · To pay (red arrow-top-right) · Net (brand-tinted, scale-balance icon); 10 px labels, compact money.
2. **Contacts-locked row** (permission denied + empty book): amber bordered row, `account-lock-outline` icon, title + 2-line hint, **Allow** button (→ **Open settings** after permanent denial).
3. **Favorites strip**: horizontal ScrollView, 52 px avatars in brand-soft rings + first name, 62 px tiles; always rendered.
4. **Owe chips row**: **They owe** (arrow-bottom-left) / **You owe** (arrow-top-right) — single-select filters.
5. **Fallback filter row**: same day-header chips + ＋ tile, shown only when a filter empties the list (so filters are never unreachable).
6. **FlatList** (60 rows/page, onEndReachedThreshold 3, initial 12, removeClippedSubviews off) with **sticky day headers** and pull-to-refresh.

**Day header**: tiny plain `Oct 24` label · **＋ account-plus create-contact icon** (16 px brand, 24×24 target, hitSlop 7) · spacer · compact direction chips: All (text) · Missed (`phone-missed` icon + count badge; red/danger tint when missed exist unfiltered) · Incoming · Outgoing · Favorites (star) — icon-only with accessibility labels.

**Call card**: 44 px avatar in brand-soft ring · name (bold 1 line, **red for missed**) · meta line (direction icon + `h:mm AM/PM` + `m:ss` when > 0) · **balance pill** (green `+300 · They owe you` / red `−150 · You owe them`; replaces the number) or pretty number (faint) · right column: relative time (`5m`/`3h`/`2d`/`Mon D`) + 40×40 redial circle (`phone-outline`, ripple, hitSlop 8).

**States:** permission denied → EmptyState + Allow + Open settings; first paint (perm unknown) → inline spinner "Loading calls"; empty list → `history` EmptyState ("No calls yet" / "No matching contacts" when filtered). iOS: an explainer hint above the list.

### 3.2 ContactsScreen (hidden tab — kept for reference)

Credit strip → favorites strip (when favorites exist, A–Z) → **two chip rows** (They owe / You owe; All / Settled (check icon) / Favorites (star)) → count + sync spinner + **sort pill** (cycles A–Z → Balance → Recent) + **New contact pill** (account-plus) → FlatList (80/page) with sticky A–Z letter headers (brand-soft chip; `#` for non-letters).

**Contact row**: 46 px avatar · display name · gold star when favorite · red "Blocked" pill when any normalized number is blocked · balance pill or first phone/email/company/"No phone" · chevron-right. Same swipe/tap/long-press set as call cards.

**Long-press menu sheet**: Call · WhatsApp · SMS · Edit contact (OS-editor path from this sheet) · Favorite toggle · Copy number · Share vCard · Block/Unblock number (red).

### 3.3 RemindersScreen

**Pinned header** (never scrolls away): "Reminders" + stats `N overdue · N today · N upcoming` + **New reminder** button (plus icon).

**Notification status card**: real OS state — green `bell-check-outline` + ON pill, or amber `bell-off-outline` + OFF pill + **Turn on** button (→ runtime ask; after a denial routes to the app's notification settings deep page) + Open settings fallback.

**Status Segmented** (default Overdue): Overdue (n) · Today (n) · Upcoming (n) · Done (n) · All. **Kind chips**: All + Payment · To-do · Birthday · Follow-up · Custom.

**Reminder groups** in fixed order: Overdue (red label) · Today (amber) · Upcoming · Done — each `Label · N`, rows sorted by effective due (snoozedUntil ?? dueAt).

**Reminder card**: 32 px done-circle (green filled + white check when done) · title (strikethrough when done; fallback "Custom") · pills: **Overdue** (red) / **Snoozed** (neutral, clock icon) · full date + time · repeat icon · trailing ⋮ → Alert menu (Done/Reopen · Snooze 10m · 1h · 3h · Tomorrow 9 AM · Edit · Cancel). **Trailing dashed "＋ Add reminder" row** always ends the list.

### 3.4 TrashScreen

Segmented: All (n) · Entry · Group · List · Note · Reminder · Bank. Rows newest-first: type icon in 36 px chip tile (`book-outline`/`folder-outline`/`clipboard-list-outline`/`notebook-outline`/`bell-outline`/`bank-outline`) · title (entry: "type · amount"; note: title/"Untitled"; bank: bankName/label) · sub `type · owner name` (useOwnerName → contact name / "General" / "Not found") · **restore icon btn** + **delete-forever icon btn** (danger). Bulk row: **Restore all** (visible rows) + **Empty** (danger confirm). Empty → `trash-can-outline` EmptyState. No auto-purge.

### 3.5 GeneralScreen

**Hero card**: `wallet-outline` brand tile 50 px + "General." + intro + global **Income / Expense / Net** boxes (Net brand-tinted; includes open post-to-ledger lists' pending) + amber "Open lists: X" footnote. **People section**: SectionHeader + green `Owed to you Σ` / red `You owe Σ` chips + up to 30 people cards (34 px avatar, name, signed colorized money; tap → contact). **Segmented sections** (side-scrolling): **Ledger · Lists · Notes · Reminders · Accounts** (default Ledger) — the five shared sections scoped to owner "general".

---

## 4. Overlays (sheets & modals)

Overlay stack types: `contact` (ContactDetail) · `contactEdit` · `contactNew` · `entryEdit` · `list` · `listEdit` · `noteEdit` · `reminderEdit` · `bankEdit` · `settings` · `search` · `calculator` · `speedDial` · `merge` · `backup` · `about`.

### 4.1 Sheet & BottomSheet kit

- **Sheet (full-screen)**: transparent Modal, **fade animation** (v1.6.10 — cheap window alpha; slide pushed the heavy tree through a transform layer = the "slow open" jank). Header: back chevron (hitSlop) · optional avatar · title (h2/800, `adjustsFontSizeToFit` 0.7) · subtitle with optional **long-press (400 ms) copy** · header-right actions. Body: ScrollView (padding 18/40, `keyboardShouldPersistTaps="handled"`) or raw. Optional pinned footer (hairline top, safe-area padded). **Toaster mounted inside the modal** (toasts stay visible above sheets). KeyboardAvoidingView (iOS padding).
- **BottomSheet (partial)**: transparent Modal, slide, **maxHeightPct 0.86**, top corners r26, elevation 12, grabber 44×5 r3 (whole header row tappable, hitSlop 6). Closes **four ways**: scrim tap · grabber/header tap · ✕ icon · hardware back. Body ScrollView (`nestedScrollEnabled`) or `noScroll` view. Toaster inside.

### 4.2 ContactDetail

Full-screen sheet. **First frame**: header (avatar 34, name, primary-number subtitle — long-press copies — pencil / star (gold `#b97d10` when favorite) / ⋯) + balance line (signed pill + "They owe you"/"You owe them"/"Settled up" + "(Open lists: X)" faint) + **12 borderless quick tiles** (6×2, ripple, auto-shrink labels): Call · SMS · WhatsApp · Telegram · Email · Ledger · Bank · Lists · Notes · Reminders · Info · ＋ Add to contact. **Deferred body**: the segmented section (first-frame tiles + InteractionManager → body).

Sub-sheets: **NumberPicker** (deduped numbers, labels, copy buttons, long-press copy, "＋ Add or edit numbers") · **TelegramPicker** (saved handles + `t.me/+number` rows + add rows) · **EmailPicker** · **AddToContactSheet** (Add phone/email/social, Edit) · **⋯ More sheet** (Edit · Share vCard · Email · Copy number · Open in dialer · Add social; actions run 180 ms after close) · **AddSocialSheet** (7 app chips; number apps prefill the handle) · **PhoneOptionsSheet** (Call · WhatsApp · SMS · Copy · Set as primary).

**Info tab**: call history (top 10 real-log rows: direction icon, "Incoming · 5m · 14:30 · 3:12"; **tap = call back**) · birthday card · phones (label chip + pretty number + gold star; tap → options; copy/call mini buttons) · emails · socials (**auto FROM NUMBER rows** for WhatsApp/Telegram/Signal/Viber + saved socials with open/copy/trash + dashed add) · addresses (tap copies) · work (company + title) · note · small Edit button.

### 4.3 ContactEditor / ContactCreator

Sheet, title "Edit contact"/"New contact". Fields: First · **Middle** · Last · Company (editor). **Phone rows** (memoized): label button cycling mobile→work→home→other · phone-pad input · **default star** (gold highlight, accessibility `selected`) · remove. "＋ Add phone" (defaults mobile) / "＋ Add email". Creator: accepts prefill phone/name; remove allowed only when >1 phone row. Load timeout 6 s with Cancel/Try-again; defensive union merge. Validations: needs name/company/phone; zero-phone save blocked; default must exist.

### 4.4 EntryEdit (ledger editor)

Sheet "New/Edit entry", subtitle = owner. Footer: Delete (confirm + undo) · Save. Kind chips (trending icons) → **hero amount** (decimal-pad, comma ok, calculator IconBtn, formatted caption; auto-calc display with "Auto-calculated from N items" when items exist) → **line items section** (saved-line cards `name · qty unit` / `qty unit × price = total` + per-line delete; tap = edit-in-place with banner + Cancel chip; dashed totals row `Total · Σqty · Σamount`; add-item form: name/qty/unit/price + quick unit chips `pic kg pcs bag box l`) → Date + Method (hint: cash · bank · card · mobile · other) → **Category chips** (12 built-in + custom + inline category manager) → **Due date** chip + date (auto-reminder on save) → Note (multiline) → Owner row (swap icon → ContactPicker) → Groups chips (No group / groups / New → inline sheet).

### 4.5 ListDetail / ListEdit

**ListDetail**: header (title, `kind · owner` subtitle, ⋯ Alert menu: Rename/edit · Duplicate · Save as template · Mark all paid · Clear payments · Delete) + footer (**WhatsApp · Share · Remind**). Sub-header card: owner Pill (tap → move) · repeat Pill · Template pill · **"Count pending in balance" switch + amber hint** · note · (repeating) "Duplicated for next period" button. **Totals card** (tap to edit: discount %/currency · tax % · extra): Subtotal · −Discount · +Tax · +Extra · **Total** · Paid · Pending (amber). "Group same items" switch. **Item rows**: tick circle · name (strikethrough when checked) · `qty unit × price · date · Paid X` · line total · expand chevron → row editor (name/qty/unit/price + calculator, row date, item due date, note, per-row discount %/amount, tax %, priority chips, Add payment, Mark all paid, Remove (confirm), payment history). Add-item form at the bottom. Payment sheet: amount + Fill-full-remaining + date + note + method Segmented.

**ListEdit**: title, note, kind chips (Calculating/Simple/Checklist/To-do), repeats chips, postToLedger switch, Templates switch, Save.

### 4.6 NoteEdit / ReminderEdit / BankEdit (Editors.tsx)

**NoteEdit**: Title · Body · Tags (comma-separated, `#` stripped) · **Pinned switch** · **color dots** (✕ + 5 palette: `#E4EDE7 #F7E8DF #E5E8F9 #F4E5EE #F4EEDB`). Footer Delete/Save.

**ReminderEdit**: Title + notes · kind chips · **two tappable rows** (calendar/clock icons) opening **native material pickers** (12-h time) + quick chips (Today/Tomorrow/Next week; 9:00 AM/1:00 PM/6:00 PM/8:00 PM) + live "Due:" preview · **live notification status** (green/amber + Turn on) · repeats chips (Once/Daily/Weekly/Monthly/Yearly + "Every month on the 1st") · **Alert me early** multi-chips (1 day / 1 hour before) · owner row. Footer: Delete · Save (bell icon). At save: past-time confirm (one-off), notification-permission ask, one-time battery-exemption dialog.

**BankEdit**: Bank name (required) · Account number (required, `•••• •••• •••• 1234` placeholder, masking hint) · Holder name · Balance · (General owner) "Choose from contacts" → picker.

### 4.7 SettingsOverlay

Sections: **Appearance** (Theme Segmented · Language row → LanguageSheet · Hide balances On/Off) · **Notifications** (Allow button + hint) · **Region** (Currency pills · country-code pill scroller) · **Security** (Set/Change/Remove PIN → PinFlow BottomSheet with 4 dots, auto-submit 120 ms, shake on wrong/mismatch · **Auto-lock Segmented**: Immediately / After 1 minute / After 5 minutes) · **Backup & restore** (Export backup · Restore → DocumentPicker → Merge/Replace Alert (double confirm) · last-backup footer + >30-day nudge) · **Email sync** (email input · Off/Daily/Weekly Segmented · Sync now · last-sync) · **Import & export** (Export vCard · Export CSV · Export transactions) · **About row** · **Danger zone** (red-bordered card: Delete all data → double confirm → wipe).

**LockScreen** (same file): absolute full-screen zIndex 85 · logo 62×62 · "callmony" · 4 dots · "Enter your PIN" · **Forgot PIN?** link (confirm → reset + unlock) · memoized PinPad (keys 68×56 r14: 1–9 / blank / 0 / backspace; ripple 34) · shake (±12, 3×60 ms) on wrong PIN.

### 4.8 MiscOverlays

- **Search overlay**: auto-focus input "Search everything…", 70 ms debounce; contacts-denied banner; six result groups (Contacts 50 / others 5); balance pills; empty-state hint. Every row opens its entity.
- **SpeedDial overlay**: 2–9 tiles (avatar or dashed ＋). Assigned → Alert (Call now / Replace / Remove from key / Cancel). Empty → ContactPicker "Assign a contact to key N".
- **Merge overlay**: refresh header (rescan, "Checking…" 400 ms); pair cards — primary (Keep pill) vs duplicate (Duplicate pill) with avatars; **Merge** (confirm) / **Dismiss**.
- **About overlay**: logo tile, "callmony", tagline, Version + Offline pills, 7 feature cards, privacy note, footer.
- **AlarmDialog**: transparent fade full-screen Modal; **pulsing bell** (cake for birthdays) + vibration pattern [0,400,200,400]; kind label · title · notes · status line ("Overdue · 14:30 · weekly"); **Done** button; snooze row 1 (10 minutes / 1 hour); row 2 (3 hours / Tomorrow 9 AM); Edit; Close.

### 4.9 OnboardingSheet

Dedicated full-screen Modal (slide, non-transparent) — never the generic Sheet (the Get-started cropping bug). Header back chevron **also saves**. Body: logo tile 74×74 · "Welcome to callmony" · hint · **currency pills** (18 + Other → 3-letter free text, A–Z, invalid falls back ETB) · **country-code search** + rows (`Ethiopia +251`, check-circle when selected; picking dismisses the keyboard) · **Get started** rendered twice — end-of-scroll AND pinned footer (hard 36 px floor + safe-area).

### 4.10 KeypadSheet

See USER_GUIDE §3. Visual: grabber → header (speed-dial grid icon · "Keypad" · ✕) → live match list (max 24% screen; shrinks first at the panel cap) → TextInput display (centered, 28/24/21 px by length; native selection handles) → paste button + backspace (when non-empty) → 3×4 flat key grid (r20 tiles, ripple, 44–62 px tall, LTR, letters under digits) → action row (**video** · **call** 54–64 px brand circle with `phone` icon, disabled when empty, accessibility "Call ‹number›" · **add contact**).

---

## 5. Shared component kit

| Component | Variants / props | Notes |
|---|---|---|
| `Btn` | primary / secondary / danger / ghost / soft · small · loading (spinner) | |
| `IconBtn` | 40 px circular, ripple, hitSlop | |
| `Chip` | active · compact · iconOnly · count badge ("9+") · danger | |
| `Segmented` | wrap or horizontal-scroll, counts | |
| `Pill` | brand / green / red / amber / blue / neutral | |
| `Card` | card/cardAlt fills, hairlines | |
| `SectionHeader` | title + hint | |
| `EmptyState` | icon tile + title + hint + action button | |
| `Money` | compact · fit · colorize · signed · hidden (•••) | |
| `Avatar` | photo/thumbnail or palette initials | |
| `Field` | labeled input wrapper | |
| `Spinner` / `Divider` | | |
| `toast(msg, kind)` | ok / info / error; **undo toast** with an Undo action (3 s / 5 s with action) | zustand-driven; Toaster mounted at shell root AND inside every Sheet/BottomSheet modal |
| `confirm(title, message, confirmLabel, danger)` | Promise\<boolean\> via ConfirmHost (fade Modal, Cancel + confirm) | |
| `SwipeRow` | see §6 | pure RN Animated + PanResponder |
| `Sheet` / `BottomSheet` | see §4.1 | fade / slide, Toaster inside |

---

## 6. Gesture dictionary

| Gesture | Spec | Where |
|---|---|---|
| **Tap** | native Pressable | everywhere |
| **Long-press (row)** | 650 ms, vibrate 30 ms, touch-slop cancel, snaps row closed | call/contact/reminder/note/bank/list cards |
| **Long-press (option row)** | 400 ms → copy number | action sheets, choosers |
| **Long-press (picker row)** | 500 ms → copy number | ContactPicker / NumberPicker |
| **Long-press (keypad 2–9)** | 380 ms → speed dial | keypad |
| **Long-press (keypad 0)** | 380 ms → `+` | keypad |
| **Long-press (subtitle)** | 400 ms → copy number | ContactDetail header |
| **Long-press (display)** | native text menu | keypad number display (Copy/Paste/Select-all) |
| **Swipe left/right (row)** | claimed when `|dx|>12 && |dx|>1.25·|dy|`; opens past 40% (velocity ×30 factored); snap 160 ms; overdrag ±24 px; re-swipe of open row works; tap-on-open closes first | SwipeRow surfaces |
| **Hold backspace (calc)** | 450 ms delay → repeat every 110 ms | calculator |
| **Hold backspace (keypad)** | 550 ms → clear all | keypad |
| **Pull-to-refresh** | Recents (forces FULL call-log re-read) | Recents |
| **Infinite scroll** | onEndReachedThreshold 3 (60-row pages Recents / 80 Contacts) | lists |
| **Any touch** | root responder-capture refreshes the autolock timer | whole app |

**Swipe action sets:**
- **Call card**: right → Call (green) + Favorite (gold); left → WhatsApp (#1faa53) + SMS (blue)
- **Contact row**: right → Call + Favorite-toggle; left → WhatsApp + SMS
- **Reminder**: left → Done/Reopen; right → Snooze 1h (amber) + Delete (red, confirm + undo)
- **Note**: left → pin/unpin (gold); right → Edit (blue) + Delete (red, confirm + undo)
- **Bank**: left → reveal; right → Share + Delete
- **List card**: left → open; right → Duplicate + Delete

---

## 7. States & feedback patterns

- **Toasts**: bottom-mounted, kind-colored (ok green / info brand / error red); **undo toasts** carry an Undo button replaying the last soft-delete reversal; visible above open sheets.
- **Confirm dialogs**: centered fade Modal, Cancel + confirm; danger variants red.
- **Empty states**: icon tile + title + hint + optional action on every list surface.
- **Permission-denied states**: Recents (call log) EmptyState + Allow + Open settings; contacts amber card (Recents top) / search banner; reminders status card with deep-link fix.
- **Loading states**: inline spinners only — never full-screen blocking (Recents "Loading calls" inline; contacts syncing spinner beside the count; "Saving contact" bottom-sheet spinner during add-to-contact).
- **Skeletons of reliability**: crash recovery card (red tile, Copy report / Continue / Reset), safe-mode banner (green), amber "Recovered from an error" dismissible banner.
- **Haptics**: 8–30 ms vibrations on keys, long-presses, pastes, calls, alarm patterns [0,400,200,400].
- **Accessibility**: tablist/tab + selected on the bottom nav; icon-only chips carry labels; `sr` roles on headers; minimum 44 px touch targets; `adjustsFontSizeToFit` on titles; selected state on the default-phone star.
