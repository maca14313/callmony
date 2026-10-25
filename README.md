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

**1.6.21 (versionCode 31)** — see `react-native/docs/CHANGELOG.md` for the
feature history.

- **1.6.21** — the first v1.6.20 diagnostics report arrived and named the
  freeze exactly: **"Main thread blocked — no progress for 5991 ms"** with
  the main-thread stack parked inside the Compose snapshot system
  (`SnapshotKt.readable` ← `Snapshot.current` ← a state `setValue`) at the
  AUTolock touch observer (`awaitEachGesture { lastActive = now() }`). Two
  fixes landed together:
  1. **The per-touch snapshot write is gone** — `lastActive` was Compose
     state, so EVERY touch wrote to the snapshot system and could collide
     with a background publisher. Autolock never needs recomposition, so
     the timestamp is now a plain field on a remembered ref (gesture
     observer and idle poller both run on the main dispatcher): zero
     snapshot traffic per touch.
  2. **The single-writer snapshot rule** — no `Dispatchers.Default` worker
     ever touches Compose state again. `hydrate()` (16 assignments), the
     1239-contact publish, the 400-call sync, `KeypadIndex`, the boot
     bail-out and the notification-action receivers all do their heavy work
     on background threads and then publish through ONE batched
     main-dispatcher hop (single recomposition, zero cross-thread snapshot
     lock traffic). Even `t()` reads and the boot alarm re-arm capture run
     on main now.
  The diagnostics themselves were upgraded from the same lesson: the freeze
  report only carried the 6 deepest-stacked threads × 4 frames, and the
  lock holder was among the missing — freeze reports now dump EVERY thread,
  RUNNABLE/BLOCKED first, 16 frames each.
- **1.6.20** — "still the same, the app is freezing and crashing — make it
  show the problem in the app when it is trying to close": built exactly
  that. An ANR watchdog detects both freeze shapes (hard main-thread
  blockage ≥ 5 s, and saturation ≥ 4 s where the thread keeps running but
  every slot is eaten), captures the main-thread stack + the boot phase +
  a full app-state snapshot, and pops a **separate-process diagnostics
  screen** (plain Views, `:diag` process — immune to the frozen main
  process) showing exactly what is wrong, with Copy report / Reopen app /
  Close. A crash firewall gives uncaught exceptions the same treatment.
  If the system ANR-kills the app anyway, the report persists and the next
  launch opens on "Callmony froze and was closed last time — here's what
  was running" (full-screen in-app overlay, above even the lock screen).
  Settings gained a Diagnostics section with every stored report. Every
  boot milestone from `app.init` to `ui.idle` is stamped, so each report
  answers WHERE it froze with a machine-observed phase instead of a guess.
- **1.6.19** — the two v1.6.18 follow-up reports:
  1. **freeze at the welcome screen → "Callmony isn't responding" → closes
     itself** — root cause: while the call-log permission was unresolved
     (`UNKNOWN`) and no calls were cached, the Recents tab rendered an
     INFINITE `CircularProgressIndicator` — an endless Choreographer frame
     loop that ran invisibly UNDER the opaque onboarding sheet from the very
     first frame and never stopped, saturating the main thread (reproduced
     as a permanent boot hang in the Robolectric harness). The loading
     spinner now renders ONLY while a call-log ask/fetch is genuinely in
     flight (new `callLogBusy` state) AND the screen is actually visible;
     an unresolved permission falls through to the no-calls empty state,
     which never animates. Boot-time notification-channel creation + alarm
     re-arming also moved off the main thread.
  2. **empty states sat at the top-left** ("No matching contacts" with the
     mint circle icon) — `EmptyState` fills its container and centers
     itself (Recents, Reminders, Trash, Notes, Banks, contact picker…);
     inside scrollables it keeps its natural in-flow placement (horizontal
     centering only), exactly like the RN edition.
  - Regression harnesses added: `BootDiagTest` (fresh-install boot must
    idle — 23 s, previously hung forever) and `UpgradeBootDiagTest`
    (onboarded boot with 400 cached calls + reminders). 46/46 pure tests
    green; Android + both iOS targets compile.

- **1.6.18** — the seventeen user reports:
  1. **freeze / lag / "app closing itself"** — every store mutation used to
     serialize the whole collection and commit it to disk (twice: row +
     mirror) ON THE MAIN THREAD; stacked commits crossed the ANR line. All
     persistence now runs on a single-writer FIFO IO queue (`lib/Persist.kt`)
     — the main thread never touches the disk. The 1-minute auto-lock default
     (read as "closing itself" while reading) is now **Never** by default,
     with a Never chip in Settings → Security.
  2. **repeats at ANY interval + alarm** — `custom:<n><minutes|hours|days|
     weeks|months>` repeats with a number + unit picker in the reminder
     editor; ALARM reminders ring on the ALARM audio stream (dedicated
     channel, USAGE_ALARM — audible over silent mode), marked with the alarm
     icon and a human repeat label on the cards.
  3. **one-tap edit** — tapping an entry row opens the editor directly (was
     row-tap → expand → Edit). The item form starts collapsed for existing
     entries without items; its button reads "Save item" (was a second
     "Save" that confused the footer Save).
  4. **Record payment** — renamed from "Add payment", hoisted to a
     root-level host sheet (it used to compose inside the scrollable card
     and pop up at the top — read as "not working"), native calendar date
     picker, translated payment-method chips.
  5. **Single-entries ＋** — the section header carries a ＋ that creates an
     entry of the kind the active tab shows (Income/Expense on General,
     Credit/Debit on a contact).
  6. **attachments** — lists / notes / reminders attach to any credit /
     debit / expense / income (entry.refs): attach chips + picker sheet in
     the editor, tattached chips on the entry detail.
  7. **Settle up removed** (kept in the store API, unsurfaced).
  8. **modals at the top** — root-level `HostSheets` slot; sheets composed
     inside scrollables render through it, so bottom-center is guaranteed.
  9. **block contacts** — More sheet → Block/Unblock (in-app block list,
     red Blocked badge on the navbar, blocked-notice on calls).
  10. **search** — solid brand pill entry in the header + ADVANCED SEARCH
      TABS (All/Contacts/Entries/Lists/Notes/Reminders/Banks) with live
      result counts.
  11-14. **contact detail** — "＋ Add to contact" became a More sheet
      (sideways-scrolling labeled social strip, add-to options, general
      actions, Block/Unblock); the number picker rows carry the call/message
      action icon; the tab body sits on its own soft card with hairlines and
      per-block cards (no more flat slab); the navbar number: tap = call with
      a brand call icon, long-press = copy.
  15. **sandbox backup** — `sandbox/` in the repo now carries the worklog,
      chat logs and SDK installer, committed every round.

**1.6.17 (versionCode 27)** — see `react-native/docs/CHANGELOG.md` for the
feature history.

- **1.6.17** — the seven user reports: (1) **reminders actually fire** —
  editing a reminder no longer false-fails with "enter a valid date and
  time" (the editor seeded the display date format into the parser), a live
  NOW clock sits beside the due time for comparison, the clock format is
  switchable 12 h (AM/PM) ↔ 24 h everywhere (editor + Settings → Region),
  saving shows exactly how much time is left ("Saved · in 2h 15m") and every
  reminder card carries a live "in 2h 15m" / "3h late" countdown; repeating
  reminders now advance AND re-arm from the notification itself while the
  app is closed (the second occurrence of a daily reminder used to never
  fire), closing the alarm dialog advances repeats / completes one-shots
  like the RN edition, and the notification permission is requested at save
  time; (2) **bottom sheets are bottom-centered** — every modal sheet now
  carries its own scrim and pins itself bottom-center (the PIN keypad and
  several long-press menus appeared stuck to the top-left corner); (3) **the
  PIN works** — a fresh launch with a PIN set opens on the lock screen
  (nothing ever set `locked` before), autolock tracks real touches (the
  1/5-minute timers used to lock mid-use) and "immediately" locks when the
  app actually goes to the background; (4) **tabs no longer overlap and are
  compact** — every segmented tab strip carries its own vertical padding so
  it can never sit flush against the container above/below it, and the
  reminder due-date / due-time fields became aligned PickerFields; (5)
  **no more raw i18n keys** — `act.saveContact` and every other leaked key
  now render properly in all five languages; (6) **UI alignment** —
  side-by-side fields share the same "label above + 48 dp box" metrics
  (PickerField), and long text/numbers ellipsize instead of wrapping
  ugly; (7) **compact buttons** — less air, same comfort.
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
