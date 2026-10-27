# Changelog

All notable changes to callmony. Dates follow the git history; the APK for each release is published on the [Releases page](https://github.com/maca14313/callmony/releases).

---

## v1.6.29 — the history icons are real buttons now; the ledger quick item lives inside the credit/debit/income/expense kind tabs; every text input visibly reacts to the touch (2026-10-27)

User reports (verbatim log: `sandbox/chat-logs/2026-10-27-v1.6.29-user-reports.md`):
"make the history icon more visible"; "the Ledger quick item — a label +
price — is should be in the credit, debit, expenses and incom not in the
outer ledger"; "Press feedback is not there for the input. like when i inter
a qountity in the items ledger or in other places there is no Press
feedback. all the inputs should have .check github, give me apk and push
even the apk".

- **The history icons are real buttons.** Two history controls existed and
  both were easy to miss. In the CALCULATOR, the history toggle was a bare
  22dp sub-toned glyph floating beside the Σ and copy icons — it is now a
  LABELLED brand pill (icon + bold "History", brand-soft fill + brand
  hairline, solid brand while the panel is open), and Σ/copy got visible
  34dp chips so nothing in the header is a naked faint icon. In CONTACT
  DETAIL, the history button in the total/settled row was a bare 18dp glyph
  in a transparent box — it is now a 34dp brand-soft circular chip with a
  brand hairline and a 20dp glyph.
- **The quick item moved into the kind tabs.** The v1.6.27
  `[Item label][Price][＋]` row rendered in the OUTER ledger and guessed a
  kind when no kind tab was active. It now renders ONLY while a KIND tab is
  active — Credit/Debit on a contact ledger, Income/Expense on the general
  ledger — placed after the filter strip, and the ＋ books the entry AS the
  kind being viewed. The outer view (All/Unpaid/Overdue) stays clean.
  Locked with `QuickItemKindTabsTest` (absent on All → present on the kind
  tab → absent again on All).
- **Every input visibly reacts to the touch.** The v1.6.25 press indication
  rides `LocalIndication`, which BasicTextField never consults — tapping
  ANY text field showed zero reaction. New `Modifier.pressVeil(corner)` is
  a self-contained pointer OBSERVER (never consumes — caret, long-press
  selection, paste and IME gestures are untouched) that paints the same
  16% veil over the input's own box while a finger is down. Applied to
  `Field` (covers the items-ledger quantity/unit/price inputs and every
  editor), the entry hero amount, the quick-item inputs, the contact-picker
  search, the dominant search screen, the onboarding country search and
  the keypad number display. Freeze-lesson compliant: every await suspends
  on a real event and the pressed flag is written once per gesture boundary.
- **Delivery per the standing request** ("give me apk and push even the
  apk"): APK 1.6.29 (versionCode 40, same release key as every release —
  installs as a direct update, data intact) committed under `releases/`,
  pushed, published as GitHub Release v1.6.29 with the APK attached, copy
  at `/home/z/my-project/download/callmony-1.6.29-kmp-release.apk`. Full
  battery 71/71 (16 classes, new: QuickItemKindTabsTest); both iOS targets
  compile.

---

## v1.6.28 — no-flash boot (appearance seeded before frame 1, hydrate overlapped with first-frame init, splash fades out); the long-press call-card sheet gains a horizontally-scrolling phone-numbers strip (2026-10-27)

User reports (verbatim log: `sandbox/chat-logs/2026-10-27-v1.6.28-user-reports.md`):
"still opens slow and there is some flash when its open"; "in LONG-PRESS the
call card it should also show the phone numbers options in one row at the top
that scrolla to side with icons that are in other place and to copy even by
long press".

- **The open flash, decoded.** Until hydrate published the persisted
  settings, the store's in-memory settings were the DEFAULTS (theme "auto",
  language "en") — and the splash renders from them. A dark-theme user saw
  the dark system splash → a LIGHT Compose splash (the flash) → the dark
  main UI; an am/om/ar/ti user saw the splash tagline flash English-first.
  `AppStore.seedBootAppearance(sysDark, themeDark, lang)` is new: called by
  MainActivity BEFORE `setContent` (and by the iOS `MainViewController` via
  the shared `scanPersistedBootPrefs()` scan) it installs the persisted
  theme + language + OS dark flag BEFORE the first Compose frame, so frame 1
  of the splash already carries the user's appearance. It is guarded — a
  no-op once hydrated — so an activity recreate can never clobber real
  settings.
- **The splash exit is a fade, not a cut.** The hydrated frame used to
  hard-swap the splash for the main UI. A one-shot 220 ms `SplashExitFade`
  now rides on top of whatever rendered (later sibling = higher z):
  terminating animation (nothing infinite — the v1.6.19 lesson), once per
  process, never replayed on rotation, and skipped entirely if the boot
  loads finished before the first frame (fast warm boots skip the splash
  altogether).
- **The open is faster.** v1.6.27 parallelized the four boot loads but
  hydrate still only STARTED from a LaunchedEffect — after the first frame
  was composed and applied. Hydrate is now kicked from the platform entry
  point BEFORE `setContent`, so the boot file reads (settings, call-log
  cache, contacts cache, PIN) overlap first-frame Compose init.
  `AppStore.hydrate` is idempotent (`kotlin.concurrent.Volatile` single-shot
  latch); the CallmonyApp LaunchedEffect call remains as the guarded no-op.
  The single-writer publish path, the splash bail-out and the boot harnesses
  are unchanged.
- **The long-press call-card sheet carries the numbers now.** Long-pressing
  a call CARD opens the per-row action sheet; above the action rows a
  horizontally-scrolling strip shows one tile per DISTINCT number of the
  saved contact (same dedupe key as the choose-a-number picker): the label
  pill + the FAVOURITE star (set/UNSET, live from `state.contactX`) on the
  top row, the pretty number + the COPY icon + the CALL icon on the bottom
  row — the same icons in the same roles as the picker. Tap calls, long-
  press copies (same toasts). A horizontal scrollable inside the sheet's
  vertical scrollable is different axes — safe (the v1.6.25 More-sheet crash
  was vertical-inside-vertical).
- Tests: `V1628Test` (seed installs theme+language+osDark, normalizes
  unknown languages, inert once hydrated) and `CallRowSheetStripTest`
  (Robolectric: long-press a real call card → sheet opens → both distinct
  numbers render as tiles → reaching the assertions is the measure-crash
  guard; also asserts boot ran through the new entry-point hydrate latch).
  Full battery 70/70 green; both iOS targets compile; APK 1.6.28 vc39,
  same release key as every prior release.

---

## v1.6.27 — fast boot again; anchored keypad; favourite-number star in the picker; overlap-free calculator with Advanced; visible press feedback; search kind tabs list contacts; ledger quick item (2026-10-27)

User reports (verbatim log: `sandbox/chat-logs/2026-10-27-v1.6.27-user-reports.md`):
the app opens a little slower now, it shouldn't be; when typing on the keypad
the keypad gets pushed to the bottom under the navbar when the contacts show
at the top; long-pressing the call icon on a recent card should offer the
number options with an icon to set/UNSET the favourite (beside and before the
call icon), the card should dial the favourite number first everywhere, and
copy should work via long-press and a copy icon; the calculator keys overlap
(make them smaller with padding), M+ M− MR belong in an ADVANCED panel whose
icon sits beside the history icon, and the display should have no visible
border and show very long numbers; every input should visibly react when
tapped, even in the ledger; the search Credit/Debit/Income/Expense tabs are
not listing the CONTACTS that have them; in the contact detail the history
icon should sit in the same row as the total and settled-up, before them; the
ledger could have an item that is just a label and a price; check GitHub,
deliver the APK, push even the APK.

1. **Boot speed** (`Store.hydrate`) — v1.6.26 added the cached CONTACT BOOK
   to the boot path (the names-from-first-frame fix), but it ran AFTER
   `Db.loadAll()` and the cached call log, so the first frame waited the SUM
   of three big file reads + JSON decodes — the 1239-contact book is the
   largest blob in the app. All four loads (store, calls cache, contacts
   cache, PIN file) now run CONCURRENTLY on the Default pool and land in the
   SAME single batched main hop: the first frame waits the MAXIMUM of the
   loads, not their sum. Names still render from the very first frame.

2. **The keypad stays anchored** (`KeypadSheet`) — the live-match list was
   `heightIn(max = 250.dp)`: with matches + exact pill + number display +
   the 12-key grid + the action row the panel EXCEEDED the content area, and
   a bottom-anchored column clips its TAIL — the bottom key rows and the
   call button ended up UNDER the always-visible nav. The match list is now
   a WEIGHTED child (`weight(1f, fill = false)`): it takes whatever space
   is LEFT above the fixed keypad portion and scrolls inside it — contacts
   at the top, the keypad exactly where it was, at any match count.

3. **Choose-a-number picker** (`RecentsScreen`) — the long-press call-icon
   picker's rows gained: a COPY icon, long-press-to-copy, and the FAVOURITE
   star (set and UNSET — `Store.clearPrimaryPhone` is new; tapping the
   filled star clears it) sitting directly before the call icon. The
   contact-detail picker got the same order + unset behavior for
   consistency. FAVOURITE-FIRST DIALING now truly covers every surface:
   recent cards, the call row sheet, keypad live matches, keypad speed
   dial, the speed-dial grid sheet, search hits and the detail navbar all
   dial `primaryPhone()` (ContactX.primaryPhone → first stored phone).

4. **Calculator** (`Calculator`) — the keys were `aspectRatio(1f)`: the tile
   width forced a height taller than the row's weighted share, so tiles
   spilled over their neighbours on short/narrow screens. Keys now FILL THE
   ROW HEIGHT with an 8dp gap — overlap is structurally impossible. M+ /
   M− / MR / MC moved out of the base layout into an ADVANCED panel behind
   a Σ toggle that sits BESIDE the history icon in the header. The display
   lost its card tone as well as its border (fully flat) and AUTOSIZES its
   font (40→16sp by length, 3 lines) so very long numbers stay readable;
   the operand cap rose from 12 to 15 digits.

5. **Press feedback everywhere** (`Kit.PressIndication`) — the v1.6.25 veil
   at 10% black was practically invisible on the near-white canvas (the
   user reported inputs STILL didn't react, "even in the ledger"). The veil
   is 16% now, and the pointer-based tap targets that LocalIndication can
   never reach — the 12 dialer keys and the calculator's hold-to-repeat
   backspace — light up their tiles while pressed.

6. **Search kind tabs** (`MiscOverlays.SearchOverlay`) — THE actual bug: the
   result sections (contacts/entries/lists/notes/reminders/banks) lived in
   the final `else` branch of the results list, reachable ONLY with a
   query. In BROWSE mode (a kind/date/type chip picked with an empty query)
   the list rendered nothing but the all-empty state — clicking Credit
   showed a blank screen even when hundreds of contacts matched. The
   sections now render in BOTH modes; empty states fire only when their
   branch truly has nothing.

7. **Contact detail** (`ContactDetail`) — the history icon rides IN the
   total/settled-up row, before the balance pill and the status label (one
   tap jumps to the Info tab, whose first block is the contact's call
   history).

8. **Ledger quick item** (`LedgerSection`) — a [Item label] [Price] [＋] row
   at the top of Single entries creates a priced entry in ONE tap: the kind
   follows the active filter tab (Credit tab → credit, Debit → debit; a
   contact defaults to Credit, the general ledger to Expense), the label
   rides as the entry's single line item, and a single-item entry card now
   shows the ITEM'S LABEL as its title.

**Tests**: 61 green — the 55-test pure battery + V1627Test (favourite
set/unset/fallback, quick-item round-trip, the exact search kind predicate)
+ BootDiagTest + UpgradeBootDiagTest through the new concurrent hydrate
path. i18n: 4 new keys x5 languages (1065/1065 parity).
Android 1.6.27 vc38 built with the same signing key as every prior release
(installs over anything, data intact); both iOS targets compile.

---
## v1.6.26 — always-visible bottom nav; names-first recents; 55/45 calculator; search kind+custom-date; file backup/restore; contact-detail grid More (2026-10-27)

User reports (verbatim log: `sandbox/chat-logs/2026-10-27-v1.6.26-user-reports.md`):
the recents card showed the phone number instead of the name for a moment at
open; the call icon should offer the number options on long press; the bottom
navigation should be there ALWAYS, with nothing covered by it; the calculator
should be 55% keypad / 45% display, borderless display, colourful keys; the
search credit tab did not show the contacts that have them and the date tab
needs a custom range; the app should never lose data, even across a reinstall
or update; the contact detail first row should scroll with a fixed More tile
that shows a GRID of everything; the second row should go away in favour of
icon tabs with the bank in a different colour; "+ Expense" looked different
from its friends; the choose-a-number picker needs a favourite icon.

1. **Recents names at open** (`Store.hydrate`) — the cached call log was
   published at boot but the cached contact book was not, so Recents matched
   every card against an EMPTY book for the seconds the real fetch takes
   (the boot chain only starts it at +4 s): every saved contact's card showed
   the RAW PHONE NUMBER as its title until the book landed. The cached book
   (contacts-cache-v2.json) now publishes in the SAME batched main hop —
   the very first frame matches names, the fresh fetch replaces it silently.
2. **Long-press the call icon** (`CallCard`) — holding the call button on a
   recent card opens the "Choose a number" sheet listing the contact's
   distinct numbers when there is more than one (single-number contacts keep
   the plain tap-to-call). The picker is held at SCREEN level, not inside the
   card — a BottomSheet composed inside a LazyColumn item collapses its
   full-screen scrim to item size (unbounded height). The card (and swipe
   actions) now dial the contact's FAVOURITE number first
   (`primaryPhone()`: ContactX.primaryPhone → first phone → raw number).
3. **Always-visible bottom nav** (`CallmonyApp`) — every overlay used to
   render in a full-screen Box AFTER the shell Column (hiding the nav under
   contact detail / search / settings / calculator / keypad / sheets). All
   of them — OverlayHost, keypad, More sheet, alarm dialog, toaster,
   confirm, host sheets — now compose INSIDE the content area between the
   header and the nav. The nav is present everywhere, and because the
   content Box ENDS where the nav begins, nothing can render under it
   either. All the now-redundant per-screen system-bar paddings
   (Sheet/BottomSheet/Calculator/KeypadSheet/ContactPicker/Search) were
   stripped; the nav (and the full-screen splash/lock/onboarding surfaces)
   own their insets. The toast offset was recalibrated (96dp → 10dp) for
   the new host.
4. **Calculator 55/45** — the sheet body splits EXACTLY: display zone
   (display card + memory row + history) weight(45f), keypad weight(55f).
   The display lost its visible border; the main line grew to 40sp ExtraBold
   with the live preview above it (24sp). Every key is colourful: operators
   keep the brand green (brandSoft tile), AC is red, backspace amber, % and
   ± blue, = solid ink, and the digits + '.' cycle a soft five-hue tint
   (teal/blue/violet/amber/rose) with matching strong glyphs.
5. **Search** — (a) the kind row (Credit/Debit/Income/Expense) now filters
   CONTACTS too: a contact matches when it has ≥1 non-deleted entry of that
   kind inside the active date window — browsing Credit lists exactly the
   people you credited, and in query mode the kind/date filters narrow the
   name hits as well; (b) a CUSTOM date-range chip opens a from/to sheet
   (two native Material calendar PickerFields, both ends optional) and the
   range applies to entries AND contact matching.
6. **Data safety (reinstall/update)** — three layers: (a) explicit Android
   Auto Backup rules for BOTH engines (fullBackupContent pre-12 +
   dataExtractionRules 12+: sharedpref/file/database/external all ride the
   cloud backup and device-to-device transfer); (b) Export backup now shares
   a REAL FILE — the JSON is written to cacheDir/backups and served through
   a FileProvider so the system sheet offers "Save to Files / Drive /
   Downloads" (a backup saved to Downloads SURVIVES an uninstall — the old
   text-share pasted megabytes into a chat box); (c) Restore-from-file: a
   new transparent FilePickActivity hosts the SAF picker (ACTION_OPEN_
   DOCUMENT results only arrive on an Activity), streams the picked file's
   text back on the main thread and restores it (merge, with confirm).
   Update-installs never lost data (same signing key + lenient JSON loader
   + mirrored prefs commits) — this round covers the reinstall case.
7. **Contact detail** — the first icon row is now a SIDE-SCROLLING strip
   (Call · SMS · WhatsApp · Telegram · Email) with a FIXED More tile
   pinned at the end that never scrolls away; the ledger icon and the
   whole 6×2 grid are GONE — the segmented tabs below carry their own
   icons now (book/lists/notes/bank/bell/info) with the BANK tab in its
   own blue (active AND inactive). The More sheet is a GRID: quick
   actions, every saved social WITH ITS LABEL, per-number WhatsApp +
   Telegram, an add-social tile and an edit tile — followed by the
   add-to rows, general actions and Block/Unblock (nothing lost).
   SocialLink gained an optional `label` field (default "" — old rows
   fall back to the app name everywhere they render); the Add-social
   sheet asks for it at creation ("e.g. Work, Family").
8. **"+ Expense" consistency** — the ledger's secondary actions
   (+ Expense / + Debit, New group, Share statement) were generic ghost
   chips under the primary row's custom pills; both rows now share ONE
   ActionPill design (brandSoft tile, brand hairline, glyph, bold tiny
   label) — every action button on the ledger page is identical.
9. **Favourite number star** — the choose-a-number picker rows carry a
   star (filled amber when active) that sets that number as the
   contact's primary/favourite number — the number the app dials first
   from the Recents card, the contact navbar and the pickers.

Verification: full unit suite 62/62 GREEN (incl. the 5-test Recents
Robolectric battery and the 3 MoreSheetCrashTest regression cases — the
latter updated for the new composition order: overlays now compose BEFORE
the bottom nav). Android `assembleRelease` clean; `aapt`: com.callmony
1.6.26 vc37, minSdk 23, targetSdk 34; `apksigner` SHA-256 `2f6d65be…869ad`
identical to every prior release (installs as a direct update, data
intact). Both iOS targets compile (klibs cross-compiled).

---

## v1.6.25 — the More-sheet crash fixed; press feedback everywhere; compact flat redesign; search filters before typing (2026-10-26)

The user tested v1.6.24 on their Samsung A12 and reported nine issues
(verbatim log: `sandbox/chat-logs/2026-10-26-v1.6.25-user-reports.md`),
led by a hard crash. All nine are fixed:

- **THE CRASH — "when i click the more btn and the three dots in the navbar
  in the detail contact page the app closes"**: the ContactDetail More sheet
  wrapped its content in its own `verticalScroll` Column while `BottomSheet`
  already scrolls its content — a vertically scrollable measured inside
  another scrollable receives an INFINITE max-height constraint and Compose
  throws `IllegalStateException` at measure time. Both triggers (the More
  quick-action tile and the navbar dots-vertical) set the same `moreOpen`
  flag, so both died instantly. The inner scroll is removed (the only
  call site in the app with the pattern) and the path is locked with three
  new Robolectric tests (`MoreSheetCrashTest`: quick action, navbar dots,
  block-row render — all compose the real sheet with real stores).
- **"every input on this app should have some indication it was clicked"**:
  the app is bare Compose (no MaterialTheme wrapper), so `LocalIndication`'s
  default draws nothing — no chip, tile, keypad key, card or row ever showed
  a press. New multiplatform `PressIndication` (an `IndicationNodeFactory` +
  `DrawModifierNode` pair: a bounded press veil that springs in on press and
  fades out on release) is provided through `LocalIndication` at the app
  root, reaching every default `clickable`/`combinedClickable` on every
  screen, sheet and overlay; `PressableRow` and `LongPressText` no longer
  null their indication; scrims and toasts stay perfectly quiet.
- **PIN "dots get coloured every time but not on the fourth"**: the submit
  ran synchronously in the same frame the 4th digit landed, so the unlock
  (or shake-and-clear) recomposed before the 4th dot ever painted. The
  submit now runs from a `LaunchedEffect` 160 ms after the 4th digit — the
  filled dot renders first, unlocking still works exactly as before.
- **"on the credit or others where is the icon to update the existing one"**:
  every credit/debit/income/expense card now carries a visible pencil
  button beside the amount that opens the editor directly (the row tap
  still edits too, and the chevron still expands the detail).
- **Single-entries tabs ("should have a btn to add new one / should not
  overlap with its cards")**: the section header now shows a LABELED
  kind-aware add chip (＋ Credit on the Credit tab, ＋ Income on General's
  income tab, …) next to the plain ＋, and the filter strip carries its own
  vertical padding so the chips never sit flush against the cards.
- **Attachments ("it means to add to it… just like they do with their own
  if we clicked them")**: attached lists/notes/reminders now ride directly
  on the COLLAPSED entry card (they used to hide inside the expanded
  detail), and tapping one opens the entity exactly like its own section
  does — list → list detail, note → note editor, reminder → reminder
  editor (shared `openRef` helper). Attaching still lives in the entry
  editor (＋ Attach → the three-tab picker).
- **Search "advanced tabs… even before the search begins… like credit, and
  others, date and more"**: the type tabs (All/Contacts/Entries/Lists/
  Notes/Reminders/Banks with live counts) are always visible now, plus a
  new always-visible refinement row — kind (All kinds/Credit/Debit/Income/
  Expense) and date (Any date/Today/Last 7 days/Last 30 days/Overdue).
  With an empty query, picking a filter enters BROWSE mode: the list shows
  everything of that type, pre-filtered, before a single letter is typed.
  Five new i18n keys across all five languages.
- **The redesign ("improve the ui… beautiful, compact, modern… most
  important the ui is so ugly and bulky")**, executed to the user's strict
  spec — original palette only, every label/amount/control preserved:
  flat surfaces + subtle borders instead of the v1.6.24 shadows (cards
  hairline-only at 16dp corners/12dp inset, primary buttons flat, sheets
  and keypad 10→6dp lift); compact 48dp sheet navbar; the contact balance
  pill directly beneath the header; the 6×2 quick-action grid at 7dp
  tiles / 20dp icons / 2dp gaps; three financial summary cards in one row
  (Net keeps its selected-state styling); Credit/Debit/Income (General:
  Income/Expense/Credit) as three equal-width brand-soft buttons in one
  FIXED row with the remaining actions in a scroll row beneath; compact
  entry cards (34dp icon tiles, 30dp icon buttons, 6dp card gaps); the
  whole spacing snapped to the 4dp/8dp system.
- **Bonus — the Recents Robolectric battery is green for the first time in
  five rounds**: while building the crash repro, the notorious flakiness
  (the same 5 `ComposeTimeoutException`s every round, previously
  A/B-proven as sandbox load) was decoded — the batteries' `@Before` reset
  `state.hydrated = false`, but the hydrate `LaunchedEffect` runs once per
  composition, so whenever the rule-launch boot finished before `@Before`
  ran, the reset parked the app on the splash forever and the first
  `waitUntil` deadlocked. Test-only fix (no hydrated reset; every test
  already waits for `hydrated=true` before seeding). Full suite 65/65.

Build: Android `assembleRelease` clean (versionCode 36 / versionName
1.6.25, minSdk 23, targetSdk 34); `apksigner` SHA-256 `2f6d65be…869ad`
identical to every prior release (installs over anything, data intact);
both iOS targets compile; 65/65 unit tests green.

---

## v1.6.24 — every reported feature back, on the freeze-free foundation; diagnostics removed; UI depth polish (2026-10-26)

The user re-sent the full report list on top of the v1.6.16 rollback
baseline ("v1.6.16 is the last version that didn't freeze — don't make it
crash like you did before"). Everything on that list was already solved by
the v1.6.17/18 work; the freeze those versions carried was root-caused and
fixed during v1.6.19–23. So v1.6.24 = the feature work restored ON TOP of
the fixes:

- **Restored from v1.6.17**: reminders that actually fire (edit
  false-positive fix, permission at save, closed-app repeat advance +
  re-arm); live NOW clock + 12/24 h everywhere; saved-countdown and live
  card countdowns; the PIN truly locks (launch lock, real-touch autolock,
  background immediate) with the keypad bottom-center, not top-left;
  compact no-overlap tabs; all raw i18n keys defined in all 5 languages;
  aligned side-by-side fields; no ugly wraps; compact buttons.
- **Restored from v1.6.18**: repeat reminders at ANY interval
  (custom:<n>m|h|d|w|mo) + ALARM mode (dedicated channel on the ALARM
  stream, audible over silent); one-tap entry editing (no more two save
  buttons); Record payment (renamed, root-level host sheet, native date
  picker); Single-entries ＋ creates the tab's kind; attachments
  (lists/notes/reminders on any credit/debit/expense/income via entry.refs
  + picker sheet); Settle up removed; modals anchored to the bottom
  (HostSheets root-level slot); block contacts (More sheet + Blocked badge
  + blocked-notice calls); dominant solid search pill + advanced filter
  tabs with live counts; the rebuilt contact detail (More sheet with
  sideways-scrolling labeled social strip + add-to options + Block, action
  icons on number rows, soft-card section separation, navbar number
  tap-to-call with call icon + long-press copy).
- **Baked in from v1.6.19–23 (the freeze fixes — the whole point)**: the
  boot spinner renders only while a call-log ask/fetch is genuinely in
  flight (no more invisible infinite animation under the onboarding sheet
  → ANR); ALL persistence runs on the single-writer IO queue (the main
  thread never touches the disk); every store publication happens in one
  batched main-thread hop (no background thread ever touches Compose
  snapshot state); the autolock touch observer awaits the down FIRST and
  refreshes per event until release (the old observer returned without
  awaiting anything, which busy-spun the main thread from the first touch
  until the 5-second ANR — the actual "freezing and crushing"); the
  contacts fetch is single-flight through a Mutex gate (no more two
  concurrent whole-book reads on the permission grant).
- **Removed**: the entire v1.6.20–23 diagnostics experiment — ANR
  watchdog, freeze reports, crash firewall, the separate :diag process and
  its screen, the in-app diagnostics overlay, the Settings row. It was
  never requested, and its reports (including three false positives)
  caused confusion. The only survivor is a 15-line BootTrace phase marker
  that the Robolectric boot harnesses assert against (the regression
  guard for the freeze-on-open class).
- **UI polish**: cards, primary buttons, bottom sheets and the keypad
  now float with soft shadows over their surfaces (the "flat / bulky"
  report).
- Verified: full pure test suite + BootDiagTest/UpgradeBootDiagTest/
  StartupTest green through the diag-free path; both iOS targets compile;
  APK `com.callmony` 1.6.24 versionCode 35, signed with the same release
  key as every prior version (installs directly over anything, data
  intact). The 5 Recents Robolectric timeouts reproduce identically on
  unmodified code (documented sandbox-load flakiness, A/B-proven in the
  v1.6.22 round).

---

## v1.6.16 rollback (versionCode 34) — full rollback to v1.6.16 (2026-10-26)

At the user's request, the app went back to **v1.6.16 exactly**. The source
tree of this release is byte-identical to the original v1.6.16 release
(commit `34076f2`) except one build-config line: `versionCode` 26 → 34.
Android refuses to install an APK whose build number is lower than the
installed one, and the only alternative (uninstall first) would delete user
data — so the number was raised while the code stayed at v1.6.16. v1.6.16's
persistence layer reads data written by v1.6.17–1.6.23 without issues
(`ignoreUnknownKeys` + `coerceInputValues` JSON, unchanged storage keys), so
existing data survives the rollback. v1.6.17–1.6.23 remain available on the
[Releases page](https://github.com/maca14313/callmony/releases) and in git
history.

---

## v1.6.16 — KMP: search call buttons, instant keypad, themed splash, fuzzy search, rich notifications (2026-10-25)

User-reported round six on the KMP edition:

1. **Calling from Search made easy** — contact hits show the primary number
   and a one-tap call button; the row itself still opens the detail sheet.
2. **Onboarding Get started pinned** — the currency/country form scrolls
   under an always-visible footer button.
3. **Keypad performance** — the live-match contact index builds on
   `Dispatchers.Default` and is cached process-wide (zero index work when
   the sheet opens, reused across opens); live-match rows read balances from
   a one-shot owner→balance map instead of a full ledger scan per row per
   keystroke; the Vibrator system service is cached (a binder lookup ran on
   every keypress — the "laggy typing"); LazyColumn keys are collision-proof.
4. **Splash background matches the app theme** — `MainActivity` resolves the
   persisted theme (dark / auto+system) before `super.onCreate()` and picks
   `Theme.Callmony` or the new runtime-addressable `Theme.Callmony.Dark`;
   the Android-12+ system splash background rides the same window color.
5. **Forgiving contact search** — two new matchers: cross-word-boundary
   prefixes (`abebeke` → "Abebe Kebede") and fuzzy subsequences (`abk`,
   `kbede`); per-contact search index cached per book identity (RN parity).
6. **Rich reminder notifications** — round logo large icon (circle-clipped,
   brand ring), brand accent color, kind subtext, BigText body, tap-to-open,
   and **Done / Snooze-10-min quick actions** (NotificationActionReceiver:
   live AppStore update when the app is running, persisted-store JSON
   surgery when it is not) + a self-dismissing confirmation; channel gains
   description, vibration pattern, lights and badges; the status-bar icon is
   the new monogram as a white alpha mask; BootReceiver re-arms carry the
   localized labels.

## v1.6.15 (KMP) — versionCode 25
- **Keypad typing is instant** (the "when i type on the keypad it lags" report): the live contact matcher re-normalized EVERY phone of EVERY contact (3 regex passes per number) on EVERY keystroke — hundreds of regex compilations per digit on a real book. Matching now runs over a precomputed index (display name, T9 letters, raw/national/normalized digit strings per phone) built once per contact-book change; each keystroke is plain `startsWith`/`contains` over that index. The exact-match pill uses the same index.
- **The phone's clock/battery/signal are visible in LIGHT mode** (the "i cant see the phones date, charge present… when it is on dark mode but not on light mode" report): `enableEdgeToEdge()` derives the status/navigation bar icon appearance from the SYSTEM dark mode, not the app theme — with the system in dark and the app in LIGHT, white icons sat on the light canvas and vanished. A `SystemBarsAppearance` expect/actual now drives the insets controller from the APP canvas color on every theme change. Bonus fix: the OS dark-mode flow was never wired into the app's "auto" theme — `theme.osDark` is now collected from the platform, so "auto" actually follows the OS.
- **Search opens the keyboard on its own** (the "when i click the search icon the keyboard should open" report): the search overlay's field auto-focuses the moment the overlay composes (`FocusRequester`), so the IME is up before you touch anything.
- **The native back button closes modals instead of the app** (the "go back native btn closes the app instead of closing modal and go back" report): a root `AppBackHandler` (expect/actual around androidx's back dispatcher) closes in order — top overlay → keypad → More sheet → back to Recents — and only lets the system exit when nothing is open. Every Sheet, BottomSheet, ContactPicker and the alarm dialog register their own handler, so nested modals (action sheet over contact detail, language sheet over More, "add to existing contact" picker) close one level at a time.
- **The keypad tile is the MIDDLE of the bottom navigation** (the "make the keypad icon on the middle" report): the bar reads Recents · Calculator · **Keypad** · General · More.
- **Copy, cut and paste work in the keypad's number display** (the "why can't i paste and cut in the keypad number display like normal input" report): the display was a `readOnly` BasicTextField — Copy only, Paste/Cut disabled by the toolkit. It's now a real native `EditText` with `setShowSoftInputOnFocus(false)` (exactly the RN edition's TextInput trick): the full long-press menu — Copy · Cut · Paste · Select all — with selection handles, while the software keyboard NEVER opens and the 12-key grid stays the only way to type. The paste sanitizer still strips everything non-dialable.
- **The filter bar sticks under the owe chips while cards scroll** (the "component where it shows the icons tabs for all, missed, incoming, outgoing, favourite and the date and the create contact btn should be sticking" report): every day header (date · ＋ new-contact · the five direction chips) is a LazyColumn `stickyHeader` — RN `stickyHeaderIndices` parity, the chips ride along pinned above the scrolling cards.
- **Swipe a call card and you get the full quick-action set** (the "i only see the call and favourite" report): left drag now reveals Call · WhatsApp · SMS · Favourite; right drag reveals Copy number · Create contact (or the contact's call-history Info when saved). The long-press sheet keeps every action.
- **Data-safety hardening** (the "most secure app so that any data cannot lost" report): (a) the SharedPreferences mirror key (`key~m`, written on every save since the RN edition) was NEVER read back — a corrupted primary row silently returned null and the data "vanished"; reads now fall back primary → mirror; (b) prefs writes are a single synchronous `commit()` of value + mirror in ONE atomic editor (async `apply()` could drop the last writes on a hard kill; saves run on background coroutines so the UI never blocks); (c) document-file writes are ATOMIC — tmp file → fsync → rename over the target, with the previous good file rotated to `.bak` and a `.bak` read-fallback — a crash or full disk can never leave a truncated file where your ledger used to be.
- **No more "old logo" anywhere** (the "logo in the splash screen is the old one and also in the navbar" report): the logo now decodes EAGERLY on a background thread started in `Application.onCreate`, so the very first composed frame — the splash — already carries the real bitmap (the fallback tile that used to flash at every cold start was being read as "the old logo"); a second, independent copy of the logo lives in `res/drawable-nodpi` as a fallback source if the asset read ever fails; and every remaining `phone-in-talk` fallback glyph (splash, navbar tile, PIN lock screen) is replaced — the fallback is now a plain brand tile that can't be mistaken for any old logo. The About pill shows the live version constant.
- **Contact detail: +Credit/+Debit before +Income/+Expense** (the "add credit and debit tabs should be before the income and expense" report): the contact ledger's add-chips lead with credit/debit (they're the contact-first kinds); the General tab keeps income/expense first. The entry editor's kind picker already read credit → debit → income → expense.
- **Back-to-back missed calls collapse into one card** (the "more than one consecutive back to back missed call… single call card with indication how many times" report): runs of consecutive missed calls from the SAME number in the log collapse into the newest call's card carrying a bold red "(N)" badge — stock-dialer behavior; the missed filter, counts and per-number grouping all respect it.
- **Unsaved numbers show the NUMBER as the card title** (the "show there phone number in the name place if the contract is not saved" report): the carrier-cached name no longer takes the title slot — an unsaved call renders its pretty-printed phone number big and bold, exactly like the stock dialer.
- **Save-button spinner**: the loading spinner inside `Btn` uses the button's foreground color (a brand-colored spinner on the brand background was invisible); it only appears while an actual save/write is in flight.
- **iOS compiles again**: the v1.6.13/v1.6.14 iOS date picker used `UIAlertController`/`UIAlertAction` members that the Linux cross-compile klibs don't expose (both iOS targets have been failing since it landed). The pickers were rewritten against the verified-binding subset: a bottom-sheet `UIDatePicker` (wheels) with a dimmed tap-to-confirm background, hour/minute derived from the wheel delta. Both `iosArm64` and `iosSimulatorArm64` are green again.
- **Tests: 28/28 green** — the 25 from v1.6.14 plus RecentsV1615Test (missed-run collapse renders exactly one "(3)" badge and one card for the grouped number; a carrier-cached name never takes an unsaved card's title; the sticky day-header chips stay reachable after a deep scroll).

## v1.6.14 (KMP) — versionCode 24
- **Recents scrolls over the call cards** (the "i can't scroll the call cards" report): the ported SwipeRow used `detectDragGestures` and called `change.consume()` on EVERY drag — the detector passes touch slop in ANY direction, so a vertical swipe over a card was eaten by the row and the LazyColumn beneath never scrolled. Now `detectHorizontalDragGestures` claims horizontal motion only; vertical drags fall straight through to the list, horizontal swipes still reveal the call/WhatsApp/SMS action buttons (tap-on-open closes first, single-open bus, ±24px overdrag — all unchanged).
- **Call cards re-match contacts live** (the "clicking a card doesn't go to its contact detail" report): the `rows` memo was keyed on the call log only — the RN `useMemo` deps are `[systemCalls, appRecents, contacts]`. The port froze the phone→contact match at first render, so every card stayed "unsaved number" after the +4s contact book load (or after creating a contact), and taps opened the action sheet instead of the contact detail. `contacts` is a memo key again; cards flip to named+balance the moment the book lands.
- **The bottom-nav General/Ledger tile navigates** (the "ledger btn is not going to ledger page" report): the nav tiles never called `setTab` — tapping the wallet (General/Ledger) or Recents tile did nothing at all. Full RN `handle()` parity now: every tile `closeAll()`s first, real tabs `setTab`, the keypad opens the dialer ON TOP of Recents (tap-while-open closes), and the calculator tile TOGGLES its sheet (open→close, closed→open) instead of re-opening.
- **Segmented tab strips are compact** (the "tabs overlap / make things compact" report): every segmented control (General's Ledger·Lists·Notes·Reminders·Accounts, contact detail's six segments, Trash's seven) renders compact 28dp chips in a single side-scroll row — five-to-six tabs fit side by side on a phone with no crowding.
- **Created contacts are findable** (the "can't find what I created, even in search" report): `createContact` returned `results[0].uri.lastPathSegment` — the **raw-contact id** — while everything else in the app (fetchAll, detail lookups, favorites, speed dials, socials, merges) keys on the **aggregate Contacts._ID**. Created contacts carried an identity nothing matched afterwards. The batch now resolves the aggregate CONTACT_ID from the new raw row and returns that; the local list patch, contactX extensions and the provider agree on one id.
- **Contacts refresh on foreground**: `MainActivity.onResume` now runs a TTL-guarded `maybeRefreshContacts()` (60s) — contacts created/edited in the device Contacts app (or any other app) while callmony sat in the background appear in search, Recents matching and the keypad without an app restart. RN parity (boot + foreground TTL guards).
- **Nothing under the status bar or gesture bar** (the "covered by the native bottom navigation and topbar" report): insets sweep — the onboarding sheet (first launch: "Welcome" under the clock, Get-started under the gesture bar), the PIN lock screen, the calculator's bottom key row, footer-less full-screen sheets (contact detail etc.), the search overlay's result list and the contact picker's list all pad `statusBarsPadding`/`navigationBarsPadding` now.
- **The new logo in the onboarding too** — matching the splash tile.
- **Search verified at RN parity**: 8 new unit tests lock the ranked matcher (word prefixes, word SUBSTRINGS — 'abd' hits Habdullah, INITIALS runs — 'kb' → Kalkidan Bekele, multi-token AND queries, phone-fragment + local/E.164 forms, ranking). No behavior change — proof it was never the algorithm; the frozen rows memo and the raw-id bug were.
- **New UI regression tests (25/25 green)**: RecentsScrollTest — boots the real app, seeds the call log BEFORE any contact exists, proves the card flips from "+251 9110 0001" to "Person 01" when the book lands, taps it into the contact DETAIL overlay, scrolls 28 cards deep via the list's scroll semantics, and swipes a card horizontally with no crash; plus the bottom-nav wallet tile → General ledger page → back to Recents. (Real swipeUp gestures don't move scrollables under Robolectric NATIVE graphics — verified against a bare LazyColumn — so the scroll semantics action stands in for the gesture.) Battery also runs with a store reset between tests (the JVM singletons used to leak state across test classes).

## v1.6.13 (KMP) — versionCode 23
- **Keypad typing crash fixed**: `parseHexColor` parsed hex through `("0x" + s).toInt(16)` — Java/Kotlin REJECT the `0x` prefix under an explicit radix → `NumberFormatException` on every `Avatar()` render. Typing 2+ digits on the keypad rendered live-match avatars → instant close. Now a plain `substring().toInt(16)` parse; the Robolectric KeypadTest taps through the whole flow with seeded contacts.
- **Calculator math fixed (the "2 × 2 = 2.000000002" report)**: THREE stacked bugs — (1) `applyOp` matched the Unicode operator glyphs (`− × ÷`) but the shunting-yard normalizes to ASCII (`- * /`) first, so every non-`+` expression fell into `else -> b` and returned the LAST OPERAND; (2) the old `+1e-10` epsilon nudge polluted every result ("2 × 2 = 2.0000000001"); (3) `fmtFixed` derived the decimal fraction via `Double.toString()`, which switches to scientific notation at 1e7 and produced strings like "0.0000003.0E11" for ≥8 decimals. Now: both operator spellings matched, epsilon gone, integer-math fraction, exact-integer fast path + 12-significant-digit formatting (0.1+0.2 → "0.3", 12345678.9 never scientific). CalcFormatTest (5 cases) locks it in.
- **The ledger actually takes an amount**: the entry editor's hero amount was a display-only `Text` — nothing could be typed, Save stayed disabled. Now a real decimal-pad `BasicTextField` (comma tolerated, one decimal point, 14 chars) exactly like the RN hero field; the calculator "Use value" flow still feeds it.
- **Reminders fire**: `AlarmReceiver` was never registered in the manifest (alarms fired into the void); `BootReceiver` now re-schedules every pending reminder straight from the persisted store instead of nudging a dead receiver; the channel is ensured and notifications carry HIGH importance.
- **Advanced date/time setters**: reminders, ledger dates and due dates use the OS's own pickers now (Android Material `DatePickerDialog`/`TimePickerDialog`, iOS `UIDatePicker` wheels) — the same native experience `@react-native-community/datetimepicker` gave the RN edition; nothing invalid can be entered, the old plain text inputs are gone.
- **Calls — 100% the RN ImmediateCall chain**: `CALL_PHONE` granted → `ACTION_CALL` (the DEFAULT dialER app places the call instantly; the number is never dropped into the keypad); missing → the runtime dialog once → instant call on grant, pre-filled `ACTION_DIAL` keypad on deny. `CALL_PHONE` is finally REQUESTED at runtime (it never was — that's why every call dumped into the dialer).
- **SMS handoff**: `ACTION_SENDTO` + `smsto:` (resolves to the DEFAULT messaging app, conversation pre-filled; plain `sms:` via `ACTION_VIEW` only reaches apps that declare it). Manifest `<queries>` extended.
- **System call log**: `READ_CALL_LOG` is requested at runtime (it never was — `fetchAll()` silently returned `[]` and the permission looked granted); denial surfaces the amber card with a working re-ask; returning from the dialer/messenger triggers an incremental refresh so calls made through the handoff appear immediately.
- **"Couldn't save contact" fixed**: `WRITE_CONTACTS` is now requested before every contact mutation (creator, editor, add-phone-to-contact, merge duplicates) — it was never asked for, so `applyBatch` always failed.
- **Toasts never under the camera**: the shell toaster is `fillMaxSize`-anchored to the bottom (above the bottom nav) — a wrapping Box got pinned at the top under the status bar/cutout and hid the clock/battery.
- **Logo**: re-cropped — the white monogram sits at 43% of the 108dp adaptive canvas (70% of the visible safe-zone circle ≈ the source logo's own proportions), so NO launcher mask (circle/squircle/rounded-square/tear) can clip it; legacy icons full-bleed teal with the glyph at ~58%; the real logo image is in the header, splash and About sheet (decoded once, cached process-wide).
- **Keypad display**: `readOnly` — tapping it no longer opens the text IME (RN parity: `showSoftInputOnFocus=false`); the 12-key grid is the input; long-press Copy still works.
- **Bottom breathing room**: Recents/General/Reminders/Trash content padded above the nav bar.

## v1.6.12 (KMP) — versionCode 22
- **Instant-close crash fixed**: a file-local `Modifier.heightIn(min:)` extension recursed into itself (shadowing the framework import) — any `Btn`/`Chip` render → `StackOverflowError` → app closes instantly. Extension deleted, framework import restored.
- **New logo** everywhere (launcher icons all densities + adaptive, splash, in-app asset), iOS AppIcon added, and the first true iOS compile (both KMP iOS targets green on Linux via klibs cross-compilation).

## v1.6.11 — versionCode 21
- **No more "loading calls" on every open**: `hydrate()` reads the persisted store, the PIN file and the call-log disk cache **in parallel** and applies them in ONE `set()` — the old order flipped `hydrated:true` before the cache read, so Recents mounted empty and flashed the loading spinner on every open. The Shell's first paint now carries the full cached call log.
- **No more 400-row re-read every open**: new native `SysCallLogModule.loadSince(since)` queries `CallLog.Calls` with `DATE > ?` — the everyday refresh is **incremental** (only rows newer than the newest known call, 1-minute overlap, dedup by id), typically zero rows over the bridge. Full re-read only when the list is empty, the snapshot is older than the 10-minute TTL (reconciles deletions made in other apps), or pull-to-refresh. A refresh that changes nothing skips the `systemCalls` set entirely — no re-render, no flicker.
- **"They owe you" / "You owe them" phrases use the user's exact wording on every surface** (Recents/Contacts chips, call cards, keypad results, contact detail, ledger stat cards, kind-picker hints, General overview) — am (ይገባኛል / ይገባዋል·ታል), om (Anaaf mala / Isaaf·Ishiif), ar (يحق لي / يحق له·لها), ti (ይግባኣኒ'ዩ / ይግባኦ·ኣ'ዩ); web i18n mirror synced.

## v1.6.10 — versionCode 20
- **Full names**: the middle name is a real part of every contact — `toDeviceContact` composes given+MIDDLE+family (dropping it was why "store landlord Work" showed as "store Work"); displayName/initials follow; visible Middle-name field in editor and creator; local save patch composes it; vCard N/FN + CSV carry it; disk cache migrated to `contacts-cache-v2` (legacy v1 renders instantly but is marked stale so the TTL guard silently re-fetches with full names; legacy file deleted after the first v2 save).
- **Fast contact detail**: Sheet modals animate with a cheap **fade** instead of slide (Android pushed the whole heavy tree through a transform layer on every open — the "too slow to open" jank); ContactDetail mounts header/balance/12 quick tiles in the first frame with the section body deferred via InteractionManager.
- **Create-contact icon in every day header** is now the **account-plus** glyph (person-with-plus), also on the empty-filter fallback row and the Contacts new-contact chip.
- **Advanced + fast search**: 12-hit cap gone (50 ranked hits); **multi-token** queries ("abdu mo"), **initials** runs ("kb" → Kalkidan Bekele), **word-substring** matching ("abd" → Habdullah); tie-break dropped O(n log n) localeCompare for a code-point compare on a pre-lowered index; debounce 140 ms → 70 ms — benchmarked 1–6 ms per keystroke over a 5000-contact book.
- Translations per user: credit → ይገባኛል / Anaaf mala / يحق لي / ይግባኣኒ'ዩ and debit → ይገባዋል·ታል / Isaaf·Ishiif / يحق له·لها / ይግባኦ·ኣ'ዩ (am/om/ar/ti on lg.credit, lg.debit, kindCredit, kindDebit).

## v1.6.9 — versionCode 19
- **Lag fix**: contacts refresh is now **single-flight** (one in-flight whole-book fetch shared by boot/foreground/save/editor-close callers, InteractionManager-deferred) and TTL-guarded at boot + foreground — the stacked bridge dumps were the "most laggy version ever".
- Reminders ＋ **always visible**: header pinned above the scroll + trailing ＋ Add-reminder row in every reminder list.
- The ＋ new-contact icon moved **into every day header** and off the top strip.
- Recents options: "Add to existing contact" was dead (onPress closed + unmounted the sheet before the picker state could set — now a stay-open item); labels unambiguous: "Create new contact" vs "Add to existing contact" (i18n ×5).
- **PIN moved to the Android `no_backup` directory** — saveSettings strips it from the backed-up store, hydrate discards legacy/restored hashes, set/remove/wipe sync the file: removing the PIN is forever, even across uninstall + reinstall (app data still restores).
- Keypad display is a **real TextInput** — native long-press Copy/Paste/Select-all with selection handles, keyboard suppressed, pasted text sanitized to dialable chars.
- System nav bar matches the app inside every sheet (Theme.FullScreenDialog never set navigationBarColor — overridden in app res values/values-v27/values-night-v27, verified in the APK).

## v1.6.8 — versionCode 18
- Keypad display long-press opens Copy/Paste/Clear like a regular input (clipboard button kept).
- Recents day headers slimmed — tiny plain "Oct 24" labels (no Today/Yesterday/weekday, no badge).
- Call cards are icon-only for direction (no outgoing/incoming/missed text).
- Reminders use **native date/time pickers** (@react-native-community/datetimepicker) — free-text inputs that could accept anything are gone.
- **Contacts fully in-app**: own simple create-new-contact form, "Add to contact" for unsaved numbers right in the recents options list, DEFAULT-phone star per number, zero-lag Save (sheet closes instantly + local patch; the whole-book bridge re-fetch that froze Save AND typing is deferred while any editor is open — the actual root cause), native `createContact()` added to ContactEditModule.
- Calculator moved to the **bottom navigation** (toggle tile), removed from the navbar.
- Speed: memoized editor rows, no blocking bridge dumps while typing.

## v1.6.7 — versionCode 17
- **Root cause of dead contact search + keypad matching + every-recent-card-"unsaved"**: hiding the Contacts tab had hidden the only READ_CONTACTS ask — permission now requested at boot (`requestMultiple`) and re-checked in `refreshDeviceContacts`, with `contactsPerm` state + one-tap Allow-contacts cards in Recents/Search when denied.
- Keypad paste hardened — 300 ms long-press, clipboard button visible whenever the clipboard holds text, lenient extraction of a number embedded in copied text (≥9 digits), "Pasted" toast.
- ＋ new-contact chip beside the favorites star chip in the Recents day headers.
- Saved-contact cards open the contact detail again once the book loads.
- Reminders-when-closed reliability ported back — `ensureReminderReadiness` (channel + one-time notification-permission auto-ask + self-heal re-arm at boot/foreground, 5 min throttle), battery-exemption native methods restored with the one-time ask at reminder save; Reminders tab Turn-on deep-links to notification settings after a denial.

## v1.6.6 — versionCode 16
- **Code reverted to the v1.6.2 tree** per user request; reminders-when-closed = the v1.6.2 notifee system (OS alarms + reboot re-arm + Done/Snooze background actions); unsaved-number contact-detail page removed; Contacts tab hidden in BottomNav only (contacts code intact, book refreshes in background); keypad PASTE added; onboarding = dedicated full-screen modal with pinned Get-started; favorites strip always visible with trailing ＋ new-contact tile; safest-app stack ported (mirror writes + fallback reads, 6 h auto snapshots, Android Auto Backup).

## v1.6.5 — versionCode 15
- Keypad PASTE (clipboard button beside the display + long-press paste, sanitized — the display was custom Text so Android had nothing to paste into); onboarding rebuilt as a dedicated full-screen modal with the Get-started button pinned in its own bottom container (hard 36 px floor + safe-area); contacts verified NOT removed; ＋ new-contact tile beside favorites; 3-layer data safety carried over; i18n kp.paste / kp.noNumberInClipboard ×5.

## v1.6.4 — versionCode 14
- Reminders that ring without hunting: notification permission auto-asked **once at app open** for users who already have reminders (the upgrade path — the real reason reminders "still don't work" on phones that never asked); self-healing alarms (every open / foreground return re-arms anything the OS scheduler lost); one-time battery-exemption system dialog at the first reminder save (Tecno/Infinix/Xiaomi/Samsung alarm killer #1).

## v1.6.3 — versionCode 13
- Reminders that provably ring (health card: notifications + exact-alarm + battery-exemption checks each fixable in one tap, alarm-category notifications, armed-verification toasts + per-row armed badges, 20 s test alert); unsaved numbers open a contact-detail sheet whose hero saves them to the address book; onboarding Get-started can never crop; Contacts tab parked (Recents is the home — they-owe/you-owe list ALL contacts with balances, ＋ new-contact tile, deferred background refresh); safest-app data stack: mirrored AsyncStorage writes with mirror-fallback reads, automatic local safety snapshots, Android Auto Backup, Settings data-safety card.

## v1.6.2 — versionCode 12
- Always open on Recents; reminders that actually ring (exact-alarm perms + WorkManager fallback + notification Done/Snooze actions + permission at save + live ON/OFF status cards + snooze 10m/1h/3h/tomorrow); call-card tap vs long-press misfire fixed (native Pressable long-press, 650 ms, touch-slop cancel); onboarding Get-started never crops; copy icon works in the choosers; country-code-agnostic cached search index with national-number matching + T9 + suffix match (search, keypad, pickers).

## v1.6.1
- Fixed contact-editing data loss + editor hang (own ContactEditModule — surgical row-scoped ContactsContract ops), toasts visible above sheets, calculator-backed amount input, reminder date handling, dark nav bar, unified chooser sheets with add/edit rows.

## v1.6.0 — versionCode 10
- 10-item feedback wave: instant-open feel (last-tab restore, 140 ms splash, settings cog out of the header), in-app advanced contact editor, real full-balance numbers with large-value fit, chooser sheets, borderless quick actions, long-press copy, call-history tap-to-call, Recents direction filters as icon-only chips inside sticky day headers, They-owe/You-owe own row on both tabs, compact summary boxes, balance pill in search + keypad results, General hero = global balance, first-run currency + country-code onboarding, native nav bar color.

## v1.5.3 — versionCode 9
- Fixed a v1.5.2 native startup crash: `IndexOutOfBoundsException` in `ReactClippingViewManager.addView` via `manageChildren` — Android VirtualizedList's default `removeClippedSubviews=true` swaps list data at boot while all cells are clipped; disabled clipping on all three FlatLists.

## v1.5.2
- Native crash reporter (`last-crash.txt` shown on next open), boot breadcrumbs, safe mode after 3 failed boots, render boundary; balance+owes pill in the phone-number place on cards; contact-detail hero removed (nav-bar name/number/avatar/star/dots + one balance line); Recents credit chips on their own row + compact filters and summary boxes.

## v1.5.1
- Startup crash fix: release-mode crash firewall (global error handler override + SafeRoot recovery root with Continue/Reset-data), shape-sanitized persistence reads — the app can no longer close itself on open.

## v1.5.0
- Zero-lag boot (Recents default + call-log snapshot), tab-scoped contact fetch with infinite scroll, fixed dead social/email icons (manifest `<queries>` + openURL), keypad flat keys, recents/contacts card beauty pass.

## v1.4.0
- Instant cached contacts, number-based social links, webapp-parity hero, auto email sync.

## v1.3.0
- Keypad webapp-parity + full contacts/recents/detail overhaul.

## v1.2.0
- Performance & UX overhaul; metro blockList for web/docs/sandbox folders (inotify watch limit).

## v1.1.0 (mobile)
- Fixed splash freeze (hydrate was never called); own native ImmediateCall + SysCallLog modules (instant dial via the default dialer app, real system call log); dialer-style boot permissions + instant contacts/recents; restructure: RN app at root + web app in `web/`; parity fixes (undo toasts, full balances incl. open lists, activity-based autolock, lead-time alerts, trash groups chip, WhatsApp statements both directions, tap-to-edit line items & list rows); CI workflow (docs copy) + version bump.

## v1.1.0 (web era)
- Ledger groups, entity attachments, action picker, keypad half-modal, calculator fixes, 5 languages.

## v1.0.0
- **912e203** callmony v1.0.0 — offline-first PWA dialer, contacts & money ledger (web).
- **6172c2e** callmony React Native v1.0.0 — production app for Android & iOS.
- **a5c4340** mobile: publish v1.0.0 APK to Releases; low-memory build tuning + JDK17/jlink local-build notes.

---

*Full commit history: `git log` in the repository root.*
