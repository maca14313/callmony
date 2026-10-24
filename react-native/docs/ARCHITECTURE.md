# callmony — Architecture & Internals

How callmony works under the hood. Companion documents: [USER_GUIDE.md](USER_GUIDE.md) · [FEATURES.md](FEATURES.md) · [UI_REFERENCE.md](UI_REFERENCE.md).

**Contents**
1. [Tech stack & repo layout](#1-tech-stack--repo-layout)
2. [Boot pipeline](#2-boot-pipeline)
3. [Data model](#3-data-model)
4. [The store (zustand)](#4-the-store-zustand)
5. [Persistence, caches & data safety](#5-persistence-caches--data-safety)
6. [Native modules (Android)](#6-native-modules-android)
7. [Notifications & alarms](#7-notifications--alarms)
8. [Search & matching engine](#8-search--matching-engine)
9. [Money math](#9-money-math)
10. [Internationalization](#10-internationalization)
11. [Permissions](#11-permissions)
12. [Android app configuration](#12-android-app-configuration)
13. [Stability engineering](#13-stability-engineering)
14. [Performance engineering](#14-performance-engineering)
15. [CI/CD & delivery](#15-cicd--delivery)
16. [The web edition](#16-the-web-edition)

---

## 1. Tech stack & repo layout

| Layer | Technology |
|---|---|
| Framework | React Native 0.75.4 · React 18.3.1 · Hermes · TypeScript |
| State | zustand 4.5.5 |
| Storage | @react-native-async-storage/async-storage 2.2.0 (mirrored) + react-native-fs 2.20.0 (disk caches/snapshots/backups) |
| Notifications | @notifee/react-native 7.8.2 (exact alarms + WorkManager fallback) |
| Contacts | react-native-contacts 7.0.8 (reads) + **own ContactEditModule** (writes) |
| Call log / calling | **own native modules** (SysCallLog, ImmediateCall) |
| Other | clipboard, datetimepicker 8.2.0, document-picker 9.3.1, callkeep 4.6.1 (iOS), vector-icons (MaterialCommunityIcons), js-sha256 (PIN), safe-area-context |
| Android | minSdk 23 · target/compileSdk 34 · Kotlin 1.9.24 · Gradle tuned for 2–4 GB RAM |
| iOS | Xcode 15+ · CallKit (callkeep) · Info.plist permissions |

```
/                       React Native app (this is the product)
  App.tsx               shell: hydrate, permissions, navigation, autolock, alarm watcher
  index.js              SafeRoot registration + crash firewall + notifee background task
  src/
    boot/               SafeRoot (phased boot, recovery UI), crash-firewall
    screens/            RecentsScreen, ContactsScreen, RemindersScreen, TrashScreen, GeneralScreen
    sections/           LedgerSection, ListsSection, NotesSection, RemindersSection, BankSection
    overlays/           ContactDetail, ContactEditor, EntryEdit, Editors (note/reminder/bank),
                        ListOverlays, MiscOverlays (search/speeddial/merge/about/alarm),
                        SettingsOverlay, OnboardingSheet
    components/         Sheet, BottomSheet (in kit), KeypadSheet, Calculator, ContactPicker,
                        SwipeRow, LanguageSheet, kit.tsx (Btn/Chip/Pill/Money/Avatar/toast/confirm…)
    lib/                store, db, types, calc, backup, exporters, share, utils, pin-file, ui-store
    native/             calls, callLog, deviceContacts, email, notify, diag, callkeep (JS bridges)
    i18n/               en, am, om, ar, ti (976 keys each) + index (t/useT/setLang)
    theme.ts            palette + type scale
  android/              native app (modules below) + release keystore
  web/                  Next.js PWA sibling edition (see §16)
  docs/                 documentation (this suite)
```

---

## 2. Boot pipeline

```
index.js (release)
 ├─ installCrashFirewall()          BEFORE the app graph evaluates — no JS error can kill the process
 ├─ notifee.onBackgroundEvent       headless Done/Snooze handler (lazily required)
 └─ AppRegistry.registerComponent("callmony", SafeRoot)

SafeRoot
 ├─ prepare:  MiniSplash (instant) ∥ readBootFails ∥ readCrashReport (native last-crash.txt)
 ├─ report:   native crash report exists → CrashReportScreen (Copy report / Continue / Reset)
 ├─ safe mode fails ≥ 3 → pause every native-touching boot step
 ├─ guarded require("../../App") → failure → RecoveryScreen
 └─ run:  <App/> inside SafeBoundary   (render errors never escape)

App
 ├─ hydrate() with a 5 s bail-out timer (splash can never hang)
 │    Promise.all([ loadAll() (AsyncStorage + mirrors),
 │                   loadCachedCalls() (calllog-cache.json),
 │                   readPinFile() (no_backup/pin.json) ])
 │    → ONE set(): collections + full cached call log + PIN merged;
 │      restored/legacy pinHash scrubbed from persisted settings
 ├─ always opens on Recents
 └─ Shell boot effect (skipped in safe mode):
      1. contacts disk snapshot  → deviceContacts immediately (background)
      2. PermissionsAndroid.requestMultiple([READ_CALL_LOG, CALL_PHONE, READ_CONTACTS])
      3. loadSystemCalls()        → incremental loadSince(newest−60s, cap 200)
      4. +4 s  contacts TTL refresh (if snapshot ≥ 5 min old; single-flight)
      5. +8 s  ensureReminderReadiness (channel, permission ask, alarm re-arm)
      6. +10 s autoSnapshot() (throttled 6 h)
      └─ bootMark("boot-done");  35 s healthy uptime resets the fail counter
```

**Foreground return:** reminder readiness self-heal (5 min throttle) · call-log refresh (10 s throttle) · contacts refresh (5 min TTL) · auto email sync (deferred 12 s; only when scheduled + interval elapsed).

**Alarm watcher:** a 15 s interval + immediate tick on foreground — due reminders (and lead-time pre-alerts, deduped per `(id, lead)`) push into the AlarmDialog queue.

**Autolock:** `autolockMin === 0` locks on background; otherwise a 10 s interval + foreground re-check against `lastActive`, which any touch anywhere refreshes (root responder-capture).

---

## 3. Data model

Conventions: every record has `id`, `createdAt`, `updatedAt`, optional soft-delete `deletedAt` (epoch ms). Ownership: `OwnerId = "c:<recordID>" | "general"`.

### Device-side (read from the OS address book)
- **DeviceContact** — recordID, displayName (given+**middle**+family; company fallback), givenName, middleName?, familyName, nickname?, company?, jobTitle?, note?, birthday? (YYYY-MM-DD), phones[], emails[], addresses[], thumbnailPath?, hasPhoto
- **DevicePhone** — id (=number), label (mobile|work|home|main|office|other), number
- **DeviceEmail / DeviceAddress** — id, label, address/formatted

### App-local (persisted in AsyncStorage)
- **ContactX** (per recordID) — favorite, tags[], pinnedNote?, speedDial? ('2'..'9'), socials[] (`{id, app: telegram|whatsapp|signal|viber|instagram|x|other, handle}`), primaryPhone?, timestamps
- **LedgerEntry** — ownerId, type credit|debit (math sign), kind? income|expense|credit|debit (label), amount, items[] (`{id, name, qty, unit?, price}` — amount = Σ qty×price when present), payments[] (`{id, amount, date, note?, method?, auto?: "tick"|"settle", refs?}`), date, dueDate?, category?, method?, note?, listId?, groupId?, refs?, timestamps, deletedAt?
- **LedgerGroup** — ownerId, title, note?, refs[], timestamps, deletedAt?
- **CalcList** — ownerId, kind calculating|simple|checklist|todo, title, note?, discount? (`{type: pct|amount, value}`), taxPct?, extraCharge?, isTemplate, postToLedger, repeat none|daily|weekly|monthly|yearly, currency?, items[], refs?, timestamps, deletedAt?
- **ListItem** — name, qty, unit?, unitPrice, discount?, taxPct?, payments[], checked, date?, dueDate?, priority? low|medium|high, note?, sortOrder, timestamps
- **Note** — ownerId, title, body, tags[], pinned, color?, links[], timestamps, deletedAt?
- **Reminder** — ownerId, title, notes?, kind payment|todo|birthday|followup|custom, dueAt (epoch), repeat, leadMinutes?[] (pre-alerts), links[], done, doneAt?, snoozedUntil?, lastFiredAt?, firedLeads?[], timestamps, deletedAt?
- **BankAccount** — ownerId, label, holderName?, bankName?, accountNumber?, iban?, swift?, branch?, notes?, balance?, currency?, timestamps, deletedAt?
- **LinkRef** — `{type: contact|entry|list|note|reminder|bank|item|link, id, label?, url?}`
- **CallLog** (iOS in-app recents) — phone, ownerId?, direction, at, duration, via app|tel
- **MessageLog** — ownerId, channel whatsapp|sms|telegram|email|other, text, date (cap 300)

### Settings (full)
theme light|dark|auto · language? en|am|om|ar|ti · currency (default ETB) · countryCode (default ET) · pinHash? {hash,salt} (in-memory only) · autolockMin (0|1|5, default 1) · hideBalances · customCategories?[] · startTab? · lastBackupAt? · notifAsked · batteryAsked? · blockedNumbers[] · speedDial? {digit: recordID} · autoBackupEmail? · autoBackupEvery? (0|1|7 days) · lastAutoSyncAt? · onboarded? · lastSnapshotAt?

### Constants
CURRENCIES (8): ETB USD EUR GBP AED SAR KES INR · ENTRY_CATEGORIES (12): General Loan Rent Sale Salary Groceries Transport Utilities Health School Other · PAYMENT_METHODS: cash bank card mobile other · COUNTRY_CODES (22): ET ER DJ SO KE SD EG SA AE US GB CA DE FR IT ES NL IN NG ZA TZ UG · AVATAR_PALETTE: 8 pastel pairs.

---

## 4. The store (zustand)

**State slices:** hydrated · deviceContacts + contactsLoadedAt + contactsPerm · systemCalls + callLogPerm + callLogSyncedAt · contactX · entries · groups · lists · notes · reminders · banks · recents · messages · settings · lastUndo.

**Action groups** (all persist through `db.ts`):
- *Boot/undo*: hydrate, pushUndo(label, apply), undo
- *Call log*: loadSystemCalls({full?})
- *Contacts*: setDeviceContacts, refreshDeviceContacts (single-flight), patchDeviceContact (zero-lag local patch + re-sort), addDeviceContactToList, toggleFavorite, setContactSpeedDial, addSocial, removeSocial, setPrimaryPhone
- *Ledger*: saveEntry (upsert, date-desc sort), deleteEntry (soft + undo), restoreEntry, purgeEntry, addPayment, removePayment, settleUp (FIFO netting with `auto:"settle"` payments dated today)
- *Groups*: saveGroup, deleteGroup (detaches entries; undo re-attaches), restoreGroup, purgeGroup
- *Lists*: saveList, delete/restore/purge, duplicateList ({asTemplate?, fresh?}), addListItem (sortOrder max+1), updateListItem, removeListItem, addItemPayment, removeItemPayment, toggleItemChecked (tick ⇒ auto "tick" payment of remaining), markAllPaid, clearPaid
- *Notes/Reminders/Banks*: save/delete(soft+undo)/restore/purge; reminders additionally completeReminder, reopenReminder, snoozeReminder, fireReminder (repeat advance) — all coordinating notifications
- *Calls/messages*: addCall (cap 500), deleteRecent, clearRecents, logMessage (cap 300)
- *Settings/data*: updateSettings (pinHash patches route to the pin file), add/removeCustomCategory, toggleBlocked, setSpeedDial, moveEntity(kind, id, ownerId), restoreBackup(data, merge|replace), wipeAll

**restoreBackup modes:** *merge* = by-id union, existing records win, deleted incoming skipped, only `language` setting taken; *replace* = swap all collections + settings (PIN stripped, defaults merged) + reschedule notifications.

---

## 5. Persistence, caches & data safety

### AsyncStorage layout
11 primary keys `callmony.{contactX, entries, groups, lists, notes, reminders, banks, recents, messages, settings, hydrated}` + **11 mirror copies** `callmony.mirror.*` — every write does primary `setItem` then a fire-and-forget mirror write; reads fall back to the mirror on a miss or JSON parse failure. Reads are shape-sanitized (non-array → [], non-object → fallback) so a corrupted blob can never crash boot. `saveSettings` strips `pinHash` before persisting.

### Disk caches (react-native-fs, Documents/)
| Cache | File | Policy |
|---|---|---|
| Contacts v2 | `contacts-cache-v2.json` `{savedAt, count, list[]}` | TTL 5 min; write debounce 1500 ms; legacy v1 file renders instantly but flagged stale (middle names) → silent re-fetch; legacy deleted after first v2 save |
| Call log | `calllog-cache.json` `{savedAt, list[]}` | painted at hydrate; rewritten after every successful sync |

### Call-log sync policy (v1.6.11)
- **Incremental**: `loadSince(newest − 60 s)`, cap 200 fetched / 400 kept; dedup by id; no-op ⇒ **no setState at all**; shallow-equal guard (ids+timestamps) also skips.
- **Full** (cap 400): list empty · snapshot > 10 min (`CALLS_FULL_TTL`, reconciles external deletions) · pull-to-refresh.
- Android-only; other platforms → granted + empty.

### Single-flight contacts refresh
One shared in-flight promise for all callers; deferred behind `InteractionManager.runAfterInteractions`; **waits (1.5 s × ≤40 polls) while a contactEdit/contactNew overlay is open** — the fix for the typing-freeze; permission check→request→state; `getAllWithoutPhotos` + normalize + A–Z sort.

### The data-safety ladder
1. AsyncStorage primary + mirror (torn write damages at most one of the pair)
2. Auto snapshots → `Documents/backups/` every 6 h (newest 8 kept)
3. Android Auto Backup (`allowBackup` + rules — see §12)
4. Email backups (JSON + vCard via `ACTION_SEND_MULTIPLE` + FileProvider) — daily/weekly, user presses Send
5. The PIN lives only in `no_backup/pin.json` — **never** backed up or restored

### Backup file format
`{app:"callmony", version:1, exportedAt, data:{contactX, entries, groups, lists, notes, reminders, banks, recents, messages, settings:{language,currency,countryCode}}}` — PIN excluded by design.

---

## 6. Native modules (Android)

### ImmediateCallModule (`CallMonyImmediateCall`)
- `immediateCall(number)` — sanitizes to tel-URI chars, checks CALL_PHONE, fires **`Intent.ACTION_CALL`** (NEW_TASK) → the default phone app places the call instantly. Resolves `placed | denied | failed`.
- `dial(number)` — `ACTION_DIAL` fallback (pre-filled keypad). Resolves `dialer | failed`.
- JS `placeCall`: blocked check → immediateCall → on `denied` one runtime re-ask → `dial` → ultimate `tel:` link. iOS: `tel:` handoff + app-side call log.

### SysCallLogModule (`CallMonySysCallLog`)
- `load(limit)` — full `CallLog.Calls` query (projection `_ID, CACHED_NAME, NUMBER, TYPE, DATE, DURATION`, DATE DESC, limit 1..2000, default 400); rejects `denied` without READ_CALL_LOG.
- `loadSince(since, limit)` — selection `DATE > ?` on the indexed DATE column (v1.6.11 incremental path), cap 200.
- JS mapping: type 1 incoming · 2 outgoing · 3 missed · 4 voicemail→incoming · 5 rejected→missed · 6 blocked→missed · 7 answered-ext→incoming; ids `sys-<rowid>`; timestamps ≤ 0 filtered.

### ContactEditModule (`CallMonyContactEdit`) — surgical ContactsContract editing
> Replaces react-native-contacts' `updateContact`, which rewrote every data row's MIMETYPE (corrupting notes/addresses) and scoped deletes to one rawContact while numbers span several raw rows (Google/Phone/WhatsApp accounts) — contacts lost numbers; the AsyncTask bridge could die unsettled.

- `loadContact(id)` — worker thread, **always settles** (6 s JS timeout as belt-and-braces); returns every phone/email across **all raw rows**.
- `saveContact(fields)` — one `applyBatch`: StructuredName/Organization updated **by row id**; phones/emails deleted by `(MIMETYPE AND contact_id)` across all raw rows then re-inserted under the primary raw row. **Notes, addresses, birthdays, photos are never touched.**
- `createContact(fields)` — one applyBatch: device-local raw contact (null account) with back-referenced data inserts; resolves the new contact_id.
- JS helpers: loadContactForEdit (defensive union merge), saveContactEdit (WRITE_CONTACTS ask), createDeviceContact, addPhoneToContact (dup-check → `added|duplicate|failed`), deleteDeviceContact, findDuplicateContacts (same normalized phone >5 digits, or same name).

### EmailModule (`CallMonyEmail`)
`compose(to, subject, body, files)` — `ACTION_SEND_MULTIPLE` (message/rfc822 with FileProvider attachments / text/plain without), wrapped in a chooser; user presses Send. No credentials, no silent sending.

### DiagModule (`CallMonyDiag`)
`mark(label)` (boot breadcrumbs) · `readCrashReport` / `clearCrashReport` / `readBootMarks` · `setNavBarColor(color, lightIcons)` · `isIgnoringBatteryOptimizations` / `requestIgnoreBatteryOptimizations` (one-tap exemption dialog).

### CrashReporter (installed in MainApplication)
Chains the default uncaught-exception handler: writes `filesDir/last-crash.txt` (time, thread, version, stack, boot-marks tail) then delegates. `boot-marks.txt` keeps the last 60 marks / 8 KB. Both ride Auto Backup.

### CallKeep (iOS only)
CallKit registration (appName, no video, includesCallsInRecents). Android deliberately skips (would prompt to become the default phone app).

---

## 7. Notifications & alarms

- **Channel** `"reminders"` — HIGH importance, sound, vibration.
- **Scheduling** (`scheduleReminderNotification`): cancel-then-create; skips done/deleted/past-due (in-app alarm covers the present); notification id `reminder-<id>`; **quick actions Done + Snooze 10m**; TIMESTAMP trigger with native repeatFrequency for daily/weekly.
- **Exact alarms**: AlarmManager `allowWhileIdle` first (SCHEDULE_EXACT_ALARM/USE_EXACT_ALARM declared) → **WorkManager fallback** on throw.
- **Event handling**: shared `handleNotifeeEvent` for foreground (`onForegroundEvent`) and the headless background task (index.js) — action `done` → completeReminder; `snooze10` → snooze(now+10m); tap → app opens, AlarmDialog takes over. The store is lazily `require`d (breaks the store↔notify import cycle); the headless path hydrates if needed; never throws.
- **Self-heal** (`ensureReminderReadiness`, boot +8 s + every foreground, 5 min throttle): channel ensured · permission asked once (only with existing reminders) · **every alarm the OS is not holding is re-armed** (`getTriggerNotificationIds` diff).
- **Reboot**: RECEIVE_BOOT_COMPLETED re-schedules.

---

## 8. Search & matching engine

- **Index**: built once per address-book load and WeakMap-cached — per contact: pre-lowered name, name words, initials, company, emails, and phones in **three forms** (raw, normalized, national). `normalizePhone`: strips non-`[\d+*#]`, `00`→`+`, strips the trunk 0 after a country code, local → `cc+digits`.
- **Phone index** (store): two maps — full normalized digits and **last 9 digits** (national significant part; ≥7-digit tails) → O(1) `contactByPhone`; numbers <5 digits never match.
- **Scoring**: exact number 7 · name prefix 6 · number prefix 6 · T9 5.5 · initials 5.2 · number suffix 5 · word-substring 4.5 · name substring 4 · company/email 3 · number-substring 3.5 · note 2; multi-token = weakest token score + 0.4 bonus (all tokens must hit); tie-break by code-point compare on the pre-lowered name (localeCompare dropped — O(n log n) → O(n)).
- **Surfaces**: global search (70 ms debounce, caps: contacts 50 / others 5), ContactPicker, NumberPicker dedup, keypad live matches (prefix 4 / substring-suffix 3.5 / T9 3, cap 60), exact-match pill (≥6 digits).
- **Performance**: benchmarked 1–6 ms per keystroke over a 5000-contact book.

---

## 9. Money math

```
r2(x)                 = round(x + Number.EPSILON) to 2 decimals    (everywhere)
entryRemaining        = amount − Σ payments                        (negative = overpaid)
entrySignedRemaining  = (credit ? +1 : −1) × entryRemaining
ownerBalance          = Σ entrySignedRemaining
openListsPending      = Σ listTotals(l).pending   over postToLedger, non-template, non-deleted lists
ownerFullBalance      = ownerBalance + openListsPending
totalsForOwner        = {credit, debit, net}
settleUp              = FIFO netting with auto:"settle" payments dated today until |net| ≤ 0.005

lineTotal (item)      = max(0, qty×price − discount + tax%)        (discount: % of base or flat)
listTotals            = subtotal = Σ lineTotal
                        listDiscount = pct ? subtotal×v/100 : v
                        listTax      = (subtotal − listDiscount)×taxPct/100
                        grand        = subtotal − listDiscount + listTax + extra
                        pending      = grand − paid
nextOccurrence(dueAt, repeat, from)  — strictly-future advance; monthly/yearly clamped to month length; 500-iteration guard
```
Epsilon everywhere: 0.005. `Money` UI: compact ≥100k → K/M, `fit` auto-shrink, colorize, signed, hidden.

---

## 10. Internationalization

- 5 locales: **en · am (አማርኛ) · om (Afaan Oromoo) · ar (العربية, RTL) · ti (ትግርኛ)** — exactly **976 keys each**, EN fallback for misses.
- 30 key namespaces (largest): `lg.*` 118 ledger · `cd.*` 106 contact detail · `st.*` 105 settings · `ls.*` 89 lists · `rm.*` 73 reminders · `ce.*` 56 contact editor · `bkp.*` 45 backup · `ie.*` 43 import/export · `act.*` 38 actions · `sto.*` 36 storage · `bk.*` 31 bank · … down to `rn.*` 2 (restart notice).
- API: `t(key, vars)` imperative · `useT()` reactive · `setLang(lang)` → `{rtlChanged}` (I18nManager allowRTL/forceRTL; layout flips next launch — restart alert shown for RTL switches; non-RTL applies instantly).
- Localized month/weekday/today/yesterday via `date.*` keys → `setDateNames`.
- Owe-phrase localization (v1.6.11, exact user wording): credit → ይገባኛል / Anaaf mala / يحق لي / ይግባኣኒ'ዩ; debit → ይገባዋል·ታል / Isaaf·Ishiif / يحق له·لها / ይግባኦ·ኣ'ዩ — applied on every surface (chips, cards, keypad, detail, ledger hints, General).

---

## 11. Permissions

| Permission | Runtime? | Why |
|---|---|---|
| `INTERNET` | — | RN runtime / dev server |
| `CALL_PHONE` | ✔ | ACTION_CALL instant calls |
| `READ_CONTACTS` | ✔ | address book (Recents resolution, search, keypad) |
| `WRITE_CONTACTS` | ✔ | in-app create/edit/add-number |
| `READ_CALL_LOG` | ✔ | real system call log |
| `POST_NOTIFICATIONS` | ✔ (13+) | reminder notifications |
| `SCHEDULE_EXACT_ALARM` (maxSdk 32) + `USE_EXACT_ALARM` | — | exact reminder alarms |
| `RECEIVE_BOOT_COMPLETED` | — | re-arm reminders after reboot |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | — | battery-exemption dialog |
| `VIBRATE` | — | haptics |
| `READ_PHONE_STATE` | — | telephony state |

Boot ask: one `requestMultiple` group (call log + phone + contacts), dialer-style, once per install. A `<queries>` block keeps wa.me / t.me / signal.me / viber / instagram / x / mailto / sms / tel intent resolution working on Android 11+.

---

## 12. Android app configuration

- **applicationId** `com.callmony` · **versionName 1.6.11** · **versionCode 21** · minSdk 23 · target/compileSdk 34 · Kotlin 1.9.24 · Hermes.
- **ABIs**: armeabi-v7a, arm64-v8a, x86, x86_64 (universal APK). **Proguard** disabled in release.
- **Signing**: release keystore committed for sideloading (`callmony-release.keystore` + `keystore.properties`); falls back to debug signing if absent — replace + GitHub secrets for store distribution.
- **Backup rules** (`backup_rules.xml` ≤11 / `data_extraction_rules.xml` 12+, cloud + device-transfer): `database` (RKStorage SQLite), `file` (snapshots/backups/caches/crash logs), `sharedpref` — cache never leaves; `no_backup/` is OS-excluded so the PIN never rides backups.
- `supportsRtl="true"` · FileProvider `${applicationId}.fileprovider` (backup paths) · MainActivity singleTask + adjustResize + uiMode config change.

---

## 13. Stability engineering

- **Crash firewall** (release, installed before app eval): replaces `ErrorUtils.setGlobalHandler` — every JS exception/rejection recorded, **never forwarded to native** → the process stays alive; same-message dedup within 5 s; boot-phase failures (< 30 s uptime) bump `callmony.bootFails`.
- **Safe mode** (≥ 3 failed boots): all native-touching boot steps paused (no prompts, no call-log read, no notifications, no sync) — the user is never locked out; lifts after a 35 s stable session.
- **SafeRoot phases** (prepare → report → run) with `bootMark` breadcrumbs written natively; app module `require`d inside try/catch; `SafeBoundary` contains render errors.
- **CrashReporter.kt**: process-wide handler → `filesDir/last-crash.txt` + `boot-marks.txt` (60 lines / 8 KB) — surfaced on next open as the recovery screen (Copy report / Continue / Reset).
- **Hydrate bail-out**: 5 s timer force-completes hydration — the splash can never hang.
- **Later errors**: dismissible amber "Recovered from an error — your data is safe" banner.
- `wipeAllAppData()`: AsyncStorage.clear + both disk caches unlinked + crash report cleared.

---

## 14. Performance engineering

| Technique | Detail |
|---|---|
| Instant cold start | 140 ms splash; parallel hydrate in ONE set(); cached call log painted in the first frame |
| Deferred boot work | contacts refresh +4 s · reminder readiness +8 s · snapshot +10 s · email sync +12 s (foreground-only) |
| Incremental call log | loadSince — typically **zero rows** over the bridge on open; no-op ⇒ no setState, no re-render, no flicker |
| Contacts disk snapshot | 5000 contacts open as fast as 50; TTL 5 min; debounced writes (1500 ms) |
| Single-flight refresh | one shared whole-book fetch for all callers; InteractionManager-deferred; **paused while an editor is open** |
| Zero-lag saves | sheet closes instantly → local patch → deferred re-fetch (contacts, entries, lists) |
| Instant contact detail | fade animation (window alpha, not a transform layer) + first-frame header/tiles + InteractionManager-deferred body |
| Memoization | SwipeRow / PinPad / PhoneInputRow memoized; virtualized lists (~15 rows mounted); removeClippedSubviews disabled (v1.5.2 boot crash) |
| Fast search | cached index, 70 ms debounce, code-point tie-break, capped scans — 1–6 ms/keystroke at 5000 contacts |
| Throttles | call-log 10 s · reminder readiness 5 min · snapshots 6 h · crash dedup 5 s |
| Native-driver animations | splash, shake, keypad slide |

---

## 15. CI/CD & delivery

- **No `.github/` in-repo** (the automation token lacks the Workflows permission) — `docs/build-android.workflow.yml` holds the exact workflow; `docs/README-ci.md` documents the one-time browser paste into the GitHub UI.
- **Workflow "Android APK"**: on push to `main`, tags `v*`, and manual dispatch → Node 20 + Temurin JDK 17 → `npm ci` → `./gradlew assembleRelease` → artifact upload → **GitHub Release** published (`v<versionName>`) with install notes.
- Every release APK is signed and universal (all 4 ABIs, Android 6.0+). Local build notes live in the root README (JDK 17 with jlink, `local.properties`, low-RAM Gradle tuning).

---

## 16. The web edition (`web/`)

A parallel **Next.js 16 + React 19 + Tailwind 4 + shadcn/Radix** edition — same product DNA delivered as an **installable offline-first PWA**:

- All data in browser **IndexedDB** (`dial-ledger` v3: contacts/entries/groups/lists/notes/reminders/banks/recents/messages/attachments + blobs); no accounts, no server DB (the Prisma schema is scaffold-only). Service worker + install prompt + `/api/version` + `/api/health`.
- **Contacts are app-managed** (own model with customFields) — not the device address book; no runtime permission dialogs.
- Web-only extras: **file attachments** on every entity, **PDF export** (lists, statements), **vCard/CSV import parsers**, demo seed data.
- i18n: the same 5 languages (902 keys — the 74 RN-specific keys like `perm.*`/`ob.*` don't apply).
- Deployment: cPanel/Passenger hosting without SSH via a custom `app.js` server + `scripts/build-and-package.sh` staging (`builds/callmony-deploy-v1.2.0.tar.gz`); a standalone `admin/index.html` audits backup files locally.

Run it: `cd web && bun install && bun run dev`.

---

*Internals complete for v1.6.11 (versionCode 21).*
