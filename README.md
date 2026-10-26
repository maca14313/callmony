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

**1.6.24 (versionCode 35)** — see `react-native/docs/CHANGELOG.md` for the
feature history.

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
