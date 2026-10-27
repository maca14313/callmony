# callmony — Kotlin Multiplatform edition

**callmony** — *Talk. Track. Settle up.* — a contacts-first money ledger
Android app (and now iOS too), rebuilt as a **100% Kotlin Multiplatform
(KMP) + Compose Multiplatform** application from the React Native edition.

This repository contains:

| Path | What it is |
|---|---|
| `composeApp/` | **The KMP app (the product).** One shared Compose UI for Android + iOS |
| `react-native/` | The original React Native edition (reference — features & docs parity) |
| `iosApp/` | The iOS Xcode project (SwiftUI shell + shared framework) |

---

## What is callmony?

A phone-first ledger for people who do business by phone call:

- **Calls & Recents** — the real system call log, instant `ACTION_CALL` dialing,
  call cards with balance pills, favorites, direction filters
- **Keypad** — 12-key dialer half-modal with T9 live matches, exact-match pill,
  smart paste, speed dial (long-press 2–9)
- **Contacts** — full address book with 12 quick-action tiles, per-contact
  ledger/lists/notes/reminders/banks, socials, in-app create & edit
- **Money ledger** — credit/debit/income/expense entries, line items with math,
  payments, settle-up, groups, categories, due dates with auto-reminders
- **Calculating lists** — tick-to-pay rows, per-row discounts/tax, whole-list
  charges, post-to-ledger, templates, repeats, bill sharing
- **Notes / Reminders / Bank accounts** — pinned notes with colors, reminder
  notifications with exact alarms + snooze, masked bank accounts
- **Calculator** — full expression evaluator with memory + use-value flow
- **Search** — everything-search across contacts, entries, lists, notes,
  reminders and banks with weighted ranking (T9, initials, multi-token)
- **5 languages** — English · አማርኛ · Afaan Oromoo · العربية (RTL) · ትግርኛ
- **PIN lock** — 4-digit PIN, autolock, salted SHA-256 storage
- **Data safety** — mirrored writes, JSON backup/restore (merge or replace),
  vCard/CSV/statements exports
- **Offline-first** — no network dependency anywhere

See `react-native/docs/` for the complete feature inventory
(FEATURES.md), UI reference (UI_REFERENCE.md), internals (ARCHITECTURE.md)
and user guide (USER_GUIDE.md). The KMP edition implements the same
feature set.

## Building

### Android APK

Requirements: JDK 17+, Android SDK (platform 34 + build-tools 34).

```bash
./gradlew :composeApp:assembleRelease
# → composeApp/build/outputs/apk/release/composeApp-release.apk
```

The release APK is signed with the committed sideloading keystore
(`composeApp/callmony-release.keystore` — same as the RN edition).

### iOS (requires a Mac with Xcode 15+)

```bash
cd iosApp && open iosApp.xcodeproj
# The Xcode build phase runs:
#   ./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
```

## Architecture (KMP edition)

```
composeApp/src/
  commonMain/kotlin/com/callmony/
    lib/       Store (zustand-port using Compose snapshot state), Db (mirrored
               JSON persistence), Calc (money math), Utils (dates/phones/T9
               search), Exporters (vCard/CSV/statements), Calls (dialing)
    model/     Types — the exact RN data model (all @Serializable)
    i18n/      en/am/om/ar/ti — 976 keys each (same as the RN edition)
    platform/  Platform service interfaces (calls, call log, contacts, notify,
               share, kv-store, ui) — implemented per platform
    theme/     The exact RN palette (light/dark tokens)
    ui/        The whole Compose UI:
      CallmonyApp.kt    shell (header, bottom nav, more sheet, overlays)
      kit/              Btn/Chip/Pill/Card/Money/Avatar/toast/confirm…
      components/       Sheet/BottomSheet, SwipeRow, Keypad, Calculator,
                        ContactPicker, LanguageSheet
      screens/          Recents, General, Reminders, Trash
      sections/         Ledger, Lists, Notes, Reminders, Banks (shared)
      overlays/         ContactDetail/Editor, EntryEdit, ListOverlays,
                        Editors, Settings+Lock, Onboarding, Search, SpeedDial,
                        Merge, About, AlarmDialog
  androidMain/          Android actuals + services (CallLog provider,
                        ACTION_CALL, ContactsContract batch editing,
                        AlarmManager notifications, clipboard/share)
  iosMain/              iOS actuals + services (tel: handoff, UIPasteboard,
                        NSUserDefaults/files persistence)
iosApp/                 Xcode project (SwiftUI shell)
```

Every screen, sheet, gesture and business rule mirrors the React Native
edition 1:1 — the i18n catalogs, theme tokens, money math (r2/epsilon),
search scoring, phone normalization and data model are exact ports of the
RN source in `react-native/src/`.

## Versioning

**1.6.27 (versionCode 38)** — see `react-native/docs/CHANGELOG.md` for the
feature history.

- **1.6.27** — the speed + picker + calculator round: (1) **BOOT IS FAST
  AGAIN** — v1.6.26's names-from-first-frame fix loaded the cached contact
  book AFTER the store and call log (the SUM of three big reads); all boot
  loads now run CONCURRENTLY (the MAX); (2) **THE KEYPAD STAYS ANCHORED** —
  the live-match list used to grow the panel past the content area, pushing
  the key grid under the always-visible nav; the list now takes only the
  space LEFT above the fixed keypad; (3) **CHOOSE-A-NUMBER PICKER** (long-
  press a recent card's call icon): every row gained a copy icon, long-press
  copy, and the FAVOURITE star (set/UNSET) directly before the call icon —
  and the favourite number now dials first EVERYWHERE (cards, keypad
  matches, speed dial, detail pickers); (4) **THE CALCULATOR** — keys can
  never overlap again (they fill the row height instead of a square
  aspect), M+/M−/MR moved into an ADVANCED panel behind a Σ toggle beside
  the history icon, the display is fully borderless and AUTOSIZES to show
  very long numbers (15-digit operands); (5) **PRESS FEEDBACK** — the veil
  is clearly visible now (16% alpha) and the pointer-based keys (dialer
  keys, calculator backspace) light up too; (6) **SEARCH KIND TABS** —
  clicking Credit/Debit/Income/Expense with no query showed NOTHING (the
  result sections only rendered in query mode); browse mode now lists the
  CONTACTS that have those entries; (7) **CONTACT DETAIL** — the history
  icon rides IN the total/settled-up row, before it; (8) **LEDGER QUICK
  ITEM** — a label + price row creates a priced entry in one tap (kind
  follows the active tab); single-item cards show the item's label as their
  title. 61 tests green (59 pure + the 2 boot harnesses); 4 new i18n keys x5 (1065/1065 parity); same
  signing key as always — installs over anything, data intact.

**1.6.26 (versionCode 37 — previous)** — see `react-native/docs/CHANGELOG.md` for the
feature history.

- **1.6.26** — the always-visible nav + data-safety round: (1) **RECENTS SHOWS
  NAMES FROM THE FIRST FRAME** — the cached call log published at boot but the
  cached CONTACT BOOK did not, so every saved contact's card flashed its raw
  phone number for the seconds the real fetch takes; the cached book now
  publishes in the same batched main hop; (2) **LONG-PRESS THE CALL ICON** on
  a recent card picks WHICH number to call when the contact has several (and
  the card dials the FAVOURITE number first everywhere); (3) **THE BOTTOM NAV
  IS ALWAYS VISIBLE** — every overlay (contact detail, search, settings,
  calculator, keypad, sheets, toasts) moved INSIDE the content area between
  the header and the nav, so the nav is present everywhere and nothing can
  render under it; (4) **THE CALCULATOR REBUILT 55/45** — keypad 55% of the
  body, display 45%, display borderless with a 40sp main line, and every key
  colourful (operators brand green, AC red, backspace amber, % blue, =
  solid ink, digits cycling a soft five-hue tint); (5) **SEARCH: the kind
  tabs now list CONTACTS too** (Credit shows exactly the people you
  credited, inside the active date window) and a **CUSTOM DATE RANGE**
  (from/to via the native calendar, open ends allowed); (6) **NO DATA LOSS
  ON REINSTALL** — Android Auto Backup rules pinned for both backup engines
  (cloud + device-transfer), Export backup now shares a REAL .json FILE
  (save it to Downloads/Drive — it survives an uninstall), and
  Restore-from-file picks that file back from anywhere on the phone;
  (7) **CONTACT DETAIL REBUILT** — the first icon row is a side-scrolling
  Call/SMS/WhatsApp/Telegram/Email strip with a FIXED More tile at the end,
  the 6x2 grid is gone, the tabs carry icons with the BANK tab in its own
  blue, and the More sheet is a GRID (quick actions + every saved social
  WITH ITS LABEL + per-number WhatsApp/Telegram + add-social + edit);
  socials get a LABEL field when created; the ledger's "+ Expense" row now
  matches its sibling buttons exactly; (8) **favourite-number star** in the
  choose-a-number picker. 62/62 tests green; APK 1.6.26 vc37, same release
  key as always (installs over anything, data intact).
- **1.6.25** — the v1.6.24 follow-up round, crash-first: (1) **THE MORE-SHEET
  CRASH FIXED** — tapping More (quick action) or the navbar three dots in a
  contact's page killed the app: the sheet wrapped its content in a
  `verticalScroll` while `BottomSheet` already scrolls, so the inner
  scrollable was measured with infinite height and Compose threw at measure
  time; the inner scroll is gone and the path is locked with 3 regression
  tests; (2) **every input shows it was clicked** — the app is bare Compose
  (no MaterialTheme), so nothing ever rendered a press indication; a new
  multiplatform bounded press veil is provided app-root through
  `LocalIndication`; (3) **the PIN's 4th dot now colors** — the submit ran in
  the same frame the 4th digit landed, so the unlock/shake recomposed before
  the dot painted; the submit now runs one beat later (160 ms); (4) **entry
  cards carry a visible pencil edit button** ("where is the icon to update
  the existing one"); (5) **Single-entries tabs** got a labeled kind-aware
  ＋ add chip and the filter strip its own padding (no more cards flush
  against the chips); (6) **attachments of lists/notes/reminders now ride on
  the collapsed entry card and open exactly like their own sections** when
  tapped; (7) **search filters BEFORE typing** — type tabs are always
  visible, plus a new kind row (Credit/Debit/Income/Expense) and date row
  (Today/7 days/30 days/Overdue); an empty query with a filter selected
  browses everything of that type; (8) **the compact/flat redesign** per the
  user's spec — Credit/Debit/Income in one fixed row, three summary cards in
  one row, 4dp/8dp spacing system, consistent 16/12/10dp radii, flat surfaces
  + subtle borders (the v1.6.24 shadows are gone), compact 48dp sheet
  headers, tighter quick-action grid and entry cards — all with the original
  palette only, every label/amount/control preserved; (9) the Recents test
  battery's notorious 5-round flakiness root-caused (a `@Before` reset of
  `state.hydrated` deadlocked the splash whenever boot finished first) —
  full suite 65/65 green for the first time. Same release key as every prior
  version — installs directly over anything, data intact.
- **1.6.24** — everything from the user's full report list is back, on the
  freeze-free foundation: the v1.6.17/18 feature work (working reminders
  with any-interval repeat + ALARM mode, live NOW clock with 12/24 h and
  post-save countdowns, a PIN that truly locks with a bottom-center keypad,
  compact no-overlap tabs and buttons, zero raw i18n keys, one-tap entry
  editing, Record payment, per-tab ＋ for single entries, attachments of
  lists/notes/reminders on any entry, settle-up removed, bottom-anchored
  host sheets, contact blocking, dominant search entry + advanced filter
  tabs, the rebuilt contact detail with More sheet / labeled social strip /
  action icons on numbers / section separation / navbar call icon, aligned
  side-by-side fields, no ugly wraps) — PLUS every freeze root-cause fix
  from v1.6.19–23 is baked in (gated boot spinner, single-writer IO
  persistence queue, single-batch state publishing, the fixed touch
  observer, single-flight contacts fetch), and the v1.6.20–23 diagnostics
  experiment (watchdog, freeze reports, diag screen/process) is REMOVED —
  it was never requested and only caused confusion. UI polish: cards,
  primary buttons, bottom sheets and the keypad now float with soft
  shadows. Same release key as every prior version — installs directly
  over anything, data intact.
- **1.6.16 rollback (versionCode 34)** — full rollback to v1.6.16 at the
  user's request: the app source tree is byte-identical to the original
  v1.6.16 release (commit `34076f2`) except ONE build line — `versionCode`
  26 → 34, because Android refuses to install an APK with a lower build
  number over an existing install (uninstalling first would delete user
  data). Data written by v1.6.17–1.6.23 is read cleanly by v1.6.16 (its
  JSON loader sets `ignoreUnknownKeys` + `coerceInputValues`, and the
  storage keys/files are unchanged), so the rollback keeps existing data.
  v1.6.17–1.6.23 remain on the Releases page and in git history.
- **1.6.16** — the six user reports: (1) **calling from Search made easy** —
  every contact hit now carries the primary number + a one-tap call button
  (no detour through the detail sheet); (2) **Get started is pinned** on the
  onboarding — the country list scrolls UNDER the always-visible button
  instead of the button living at the very end of the scroll; (3) **keypad
  opens instantly** — the live-match index now builds on a background
  dispatcher shared across opens (it used to rebuild synchronously on every
  open), per-keystroke balance scans became a one-shot map, and the Vibrator
  service is cached (a binder lookup used to run on EVERY keypress — the
  typing lag); (4) **splash matches the app theme** — the launch window +
  Android-12 system splash follow the persisted APP theme (dark users no
  longer see a white flash first); (5) **forgiving search** — cross-word
  prefixes ("abebeke" → Abebe Kebede) and fuzzy subsequences ("abk",
  "kbede") now match, with the per-contact index cached like the RN edition;
  (6) **beautiful system notifications** — the round logo as large icon,
  brand-teal accent, reminder-kind subtext, expanded BigText body, and the
  RN-parity **Done / Snooze 10 minutes quick actions** that resolve reminders
  straight from the shade (app open OR closed), plus a self-dismissing
  confirmation. The status-bar icon is the new monogram (white alpha mask).

- **1.6.15** — the fifteen user reports: (1) **keypad lag eliminated** — live
  contact matching re-normalized every phone of every contact on every
  keystroke; a precomputed index (name / T9 letters / raw·national·normalized
  digit strings) makes each keystroke plain `startsWith`/`contains` work;
  (2) **status-bar icons follow the app theme** — `enableEdgeToEdge()` picked
  the SYSTEM dark mode, so with the system dark + app light the clock/battery
  turned invisible; appearance now tracks the app canvas (and the OS dark
  flow finally feeds the app's "auto" theme); (3) **search opens the keyboard
  by itself** (auto-focused field); (4) **the native back button behaves** —
  back closes the top sheet / picker / alarm / keypad / More sheet and
  returns to Recents instead of killing the app (root + nested handlers);
  (5) **the keypad tile sits in the MIDDLE of the bottom navigation**
  (Recents · Calculator · Keypad · General · More); (6) **copy / cut / paste
  in the keypad number display** — a real native EditText with the software
  keyboard suppressed (RN `showSoftInputOnFocus` parity): long-press shows
  the full Copy · Cut · Paste · Select-all menu with selection handles;
  (7) **the day header (date · ＋ new-contact · filter chips) is STICKY**
  while the call cards scroll (RN `stickyHeaderIndices` parity);
  (8) **richer swipe actions** — left: Call · WhatsApp · SMS · Favourite,
  right: Copy number · Create contact / Info (was call + favourite only);
  (9) **data-safety hardening** — the SharedPreferences mirror key is now
  actually READ as a fallback (writes were mirrored but reads never fell
  back), writes commit synchronously in one atomic edit, and file writes are
  atomic (tmp → fsync → rename) with a rotated `.bak` + read fallback;
  (10) **no more "old logo"** — the logo decodes eagerly at process start
  (the splash/navbar can never flash the fallback tile), a second independent
  copy lives in `res/drawable-nodpi` as a fallback source, and every
  phone-in-talk fallback glyph is gone (splash, navbar, PIN screen, About);
  (11) **contact-detail ledger puts +Credit/+Debit before +Income/+Expense**
  (General keeps income-first); (12) **back-to-back missed calls from the
  same number collapse into ONE card with a "(N)" badge** (stock-dialer
  behavior); (13) **unsaved numbers show the PHONE NUMBER as the card
  title** (the carrier-cached name no longer takes the slot);
  (14) **save-button spinner fixed** (brand spinner on brand background was
  invisible — uses the button's foreground now) and About shows the live
  version; (15) **iOS actually compiles again** — the v1.6.13 alert-based
  date picker used UIKit symbols that don't exist in the Linux cross-compile
  klibs; the pickers were rewritten with the verified-binding subset
  (bottom-sheet UIDatePicker, tap-outside-to-confirm).
- **1.6.14** — the five user reports: (1) Recents finally scrolls over the
  call cards (SwipeRow consumed *vertical* drags before — now only horizontal
  swipes are claimed, vertical drags reach the list); (2) call cards re-match
  contacts the moment the contact book loads or changes (the rows memo was
  frozen on the call-log state — RN parity restored), so tapping a card opens
  the contact detail instead of the "unsaved number" sheet; (3) the bottom-nav
  General/Ledger tile actually navigates (nav tiles never called `setTab`), the
  calculator tile toggles like RN, segmented tab strips are compact; (4)
  created contacts get the **aggregate contact id** (createContact used to
  return the raw-contact id — nothing matched it afterwards), plus a
  TTL-guarded contact refresh every foreground so contacts created outside the
  app appear in search/Recents without a restart; (5) insets sweep —
  onboarding, lock screen, calculator keypad, footer-less sheets, search and
  picker lists all clear the status bar and the gesture/nav bar.
- **1.6.13** — keypad crash fix (`parseHexColor` NumberFormatException on
  every avatar), calculator precision (ASCII-operator `applyOp` bug — "2 × 2"
  returned the last operand — plus decimal-exact formatting), real amount
  input in the ledger entry editor, native Material date/time pickers for
  reminders & due dates, `ACTION_CALL`-first dialing with the `CALL_PHONE`
  runtime dialog (100% the RN ImmediateCall chain), `smsto:` messaging
  handoff, system call-log permission flow, `WRITE_CONTACTS` gate on contact
  saves/merges, registered `AlarmReceiver` + direct boot re-arm for
  reminders, re-cropped launcher icons (glyph safe in every mask), real logo
  in the header/splash/About, toasts pinned above the bottom nav (never under
  the camera cutout), bottom padding everywhere.
- **1.6.12** — instant-close crash fix (recursive `heightIn` extension), the
  new logo on every surface, first true iOS compile.
- **1.6.11** — the KMP edition: 100% replica of the RN app.
