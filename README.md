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

**1.6.14 (versionCode 24)** — see `react-native/docs/CHANGELOG.md` for the
feature history.

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
