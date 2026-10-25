# callmony — Complete Feature Inventory (100%)

Every feature in callmony v1.6.11, grouped by domain. Each entry notes **where it lives** and how it behaves. This is the exhaustive inventory — the user-facing "how to" is in [USER_GUIDE.md](USER_GUIDE.md).

**Legend:** ✅ fully working feature · ⚠️ deliberate placeholder · 🔁 automatic/background behavior

---

## 1. Calls & dialing

| Feature | Where | Details |
|---|---|---|
| ✅ **Instant calls (real, never simulated)** | keypad call button, call cards, contact tiles, redial button, speed dial | Own `ImmediateCall` native module fires Android `ACTION_CALL` through the default dialer app — the number is never dropped into the keypad. Fallback chain: CALL_PHONE runtime dialog → `ACTION_DIAL` pre-filled keypad → `tel:` link |
| ✅ **Real system call log** | Recents tab | Own `SysCallLog` native module reads the same `CallLog.Calls` provider the stock dialer uses (projection: id, cached name, number, type, date, duration) |
| ✅ **Instant open from cache** | every launch | Call log persisted to `Documents/calllog-cache.json`; hydration paints it in the first frame — no loading spinner on open |
| 🔁 **Incremental refresh** | background | `loadSince(newest−60s)` reads only newer rows; no-op refresh ⇒ zero setState/re-render/flicker; dedup by id; cap 400 rows kept |
| 🔁 **Full re-read policy** | background | Only when list empty, snapshot > 10 min old (reconciles deletions made in other apps), or pull-to-refresh (forces full) |
| ✅ **Direction filters** | sticky day headers | All · Missed (icon + live count badge, turns red when unfiltered missed calls exist) · Incoming · Outgoing · Favorites |
| ✅ **Day-grouped list** | Recents | Tiny plain `Oct 24` labels; sticky headers; infinite scroll 60 rows/page; pull-to-refresh |
| ✅ **Call cards** | Recents | Avatar (resolved by number in any format), full name (red for missed), icon-only direction, time, duration, **balance pill in place of the phone number**, relative time, redial button |
| ✅ **Card gestures** | Recents | Tap → detail/sheet · long-press (650 ms, vibration) → action sheet · swipe right → Call + Favorite · swipe left → WhatsApp + SMS |
| ✅ **Call action sheet** | long-press/unknown tap | Call · WhatsApp · SMS · Copy number · Favorite toggle · Call history · Create new contact (prefilled) · Add to existing contact (stay-open picker) · Delete log (iOS app log only) · long-press any row copies the number |
| ✅ **Blocked numbers** | Settings | Numbers in the blocked list refuse to be called (blocked check runs before every call); toggled per contact from the contact context sheet |
| ✅ **Redial** | call card right side | One-tap instant call, 40 px target with hit slop |
| ✅ **Call history per contact** | contact Info tab | Real system log matched against all the contact's numbers (top 10); tap a row to call back |
| ✅ **iOS path** | all call surfaces | Native `tel:` handoff with CallKit recents; app-own log of calls started from callmony |

## 2. Keypad

| Feature | Where | Details |
|---|---|---|
| ✅ **Dialer half-modal** | bottom nav Keypad slot | Rises over Recents (260 ms slide + scrim); closes via scrim, grabber, or ✕; top corners 28; safe-area padded |
| ✅ **12-key grid** | keypad | `1 2ABC 3DEF 4GHI 5JKL 6MNO 7PQRS 8TUV 9WXYZ * 0+ #`; flat tiles, ripple, 10 ms haptics; max 24 chars; long-press `0` → `+` |
| ✅ **Real text display** | keypad | A genuine TextInput ⇒ Android native **long-press Copy/Paste/Select-all with selection handles**; soft keyboard suppressed; font auto-shrinks 28/24/21 px |
| ✅ **Smart paste** | display + clipboard button | Strict pass (fully dialable) → lenient pass (extract ≥9-digit number from prose) → reject with toast; "Pasted" toast + vibration |
| ✅ **Backspace** | keypad | Tap deletes (8 ms haptic); hold 550 ms clears all (24 ms vibration) |
| ✅ **Live matches** | while typing ≥2 digits | Ranked contacts: phone prefix 4 > substring/suffix 3.5 > T9 3; tap → call, long-press → open contact; balance pill per row; cap 40 rows / 24% screen |
| ✅ **Exact-match pill** | under display | ≥6 digits fully equal → avatar + name pill; tap opens the contact |
| ✅ **National number normalization** | matching engine | `+251…`, `09…`, `9…` hit the same contact; index cached per book load |
| ✅ **Speed dial** | long-press 2–9 | Calls the assigned contact instantly (toast `Call · name`) or opens the assignment overlay |
| ✅ **Add contact** | keypad action row | Opens the in-app creator prefilled with the typed number |
| ⚠️ **Video button** | keypad action row | Placeholder — "video demo" info toast |

## 3. Contacts

| Feature | Where | Details |
|---|---|---|
| ✅ **Full address book** | data layer | Whole book loaded via `getAllWithoutPhotos`, normalized (labels, birthdays), A–Z sorted; kept as a disk snapshot (`contacts-cache-v2.json`, TTL 5 min, write debounce 1500 ms) — 5000 contacts open as fast as 50 |
| ✅ **Instant snapshot paint** | boot | Legacy v1 snapshot renders instantly but is marked stale so the TTL refresh rewrites it with full (middle) names; legacy file deleted after first v2 save |
| 🔁 **Single-flight refresh** | background | One shared in-flight whole-book fetch for boot (+4 s)/foreground/post-save/editor-close callers; deferred behind InteractionManager; **waits while any contact editor is open** (the typing-freeze fix) |
| ✅ **Full display names** | everywhere | First + **Middle** + Last composed everywhere (display, initials, vCard `N:`/`FN:`, CSV) — `store landlord Work` never truncates to `store Work` |
| ✅ **Contact detail profile** | overlay | Nav-bar header (avatar/name/number + pencil/star/⋯), balance line, **12 quick-action tiles**, six sections (Ledger/Lists/Notes/Bank/Reminders/Info) — opens instantly (fade + first-frame tiles + deferred body) |
| ✅ **12 quick tiles** | contact detail | Call · SMS · WhatsApp (prefilled reminder/settle-up text!) · Telegram · Email · Ledger · Bank · Lists · Notes · Reminders · Info · ＋ Add to contact |
| ✅ **Number picker (deduped)** | call/SMS/WhatsApp tiles | Same number in `09…`/`+251…` formats appears once; long-press copies; per-row copy buttons; "＋ Add or edit numbers" |
| ✅ **Social links from numbers** | contact Info tab + pickers | Auto rows for WhatsApp (`wa.me`), Telegram (`t.me/+`), Signal (`signal.me/#p/`), Viber (`viber://chat`) built from the contact's number — no usernames needed |
| ✅ **Custom socials** | Add-social sheet | Telegram · WhatsApp · Signal · Viber · Instagram · X · Other (link) — unlimited per contact; number-based apps pre-fill the handle |
| ✅ **Default phone star** | editor + Info tab | One tap marks the number the app dials/shows first; "Set as primary" in per-number options |
| ✅ **In-app contact creator** | day headers, keypad, action sheet | First/Middle/Last name, phone rows (label cycling mobile→work→home→other, default star), email rows; accepts prefilled number/name; single native applyBatch; instant local insert |
| ✅ **In-app contact editor** | detail pencil | Same + Company; 6 s load timeout; defensive union merge — a save can never wipe known phones/emails; zero-lag save (sheet closes instantly, local patch, deferred re-fetch); notes/addresses/birthdays/photos on the OS side never touched |
| ✅ **Add to existing contact** | call action sheet | Stay-open ContactPicker → native `addPhoneToContact` (loss-free union, dup detection) → local patch + re-sync after 800 ms |
| ✅ **Favorites** | strips on Recents | Gold star toggle from cards/detail; favorites strip (avatar + first name); favorites filter chip |
| ✅ **A–Z sections** | Contacts data | Sticky letter headers when sorted A–Z; non-A–Z → `#` |
| ✅ **Sort modes** | contacts | A–Z (default) · Balance (|balance| desc) · Recent (newest OS contacts first) — cycling sort pill |
| ✅ **Merge duplicates** | More sheet | Scan by same normalized phone (>5 digits) or same name; pair cards (Keep vs Duplicate); merge = union phones/emails into primary + delete duplicate; dismiss pairs; rescan |
| ✅ **Share vCard** | contact ⋯ menu | Single-contact `.vcf` via system share; full-book export in Settings |
| ✅ **Contact permission UX** | Recents/Search | Denial → amber card with Allow (re-ask) → Open settings (permanent denial path); granted → "Contacts ready" toast |
| ℹ️ **Contacts tab** | hidden | Deliberately hidden from the bottom nav; all contact features remain via Recents/Search/Keypad/General |

## 4. Search

| Feature | Where | Details |
|---|---|---|
| ✅ **Global search overlay** | header magnify | "Search everything" — 70 ms debounce, index-based (1–6 ms per keystroke on a 5000-contact book) |
| ✅ **Six result groups** | search | Contacts (cap 50) · Ledger entries (5) · Lists (5) · Notes (5) · Reminders (5) · Bank accounts (5); every row opens its entity |
| ✅ **Advanced matching** | search + pickers + keypad | Multi-token (`abdu mo`), initials runs (`kb` → Kalkidan Bekele), word-substring (`abd` → Habdullah), T9, phone matching in raw/normalized/national forms |
| ✅ **Weighted ranking** | search | exact number 7 · name prefix 6 · number prefix 6 · T9 5.5 · initials 5.2 · suffix 5 · word-substring 4.5 · name 4 · company/email 3 · number-substring 3.5; multi-token = weakest token + 0.4; code-point tie-break |
| ✅ **Balance pills in results** | search rows | Same math as the cards |

## 5. Money ledger

| Feature | Where | Details |
|---|---|---|
| ✅ **Four entry kinds** | entry editor | Credit ("They owe you" +) · Debit ("You owe them" −) · Income (+) · Expense (−); exact localized labels in am/om/ar/ti |
| ✅ **Line items with math** | entry editor | name × qty (+unit) × unit price = line total; entry amount auto-calculates from items; tap-to-edit in place with banner + cancel |
| ✅ **Hero amount input** | entry editor | Big brand field, decimal-pad, comma tolerated, calculator button (Use-value flow), formatted caption |
| ✅ **Payments** | entry cards | amount · date · note · method (Cash/Bank/Card/Mobile/Other); fill-full-remaining button; payment history with remove; `auto:"tick"` / `auto:"settle"` markers |
| ✅ **Settle-up** | ledger chips | One tap nets credit vs debit with payments dated today, oldest-first, until zero — toast "Settled ✓" |
| ✅ **Due dates + auto-reminder** | entry editor | Due-date toggle; saving a new entry with a due date auto-creates a payment reminder at 09:00 that day |
| ✅ **Groups** | ledger | Collapsible group cards (count, paid, remaining); inline create/rename; delete detaches entries (nothing lost) + undo |
| ✅ **Categories** | entry editor | 12 built-in (General, Loan, Rent, Sale, Salary, Groceries, Transport, Utilities, Health, School, Other) + custom categories (add/remove inline) |
| ✅ **Running balance** | entry cards | Computed oldest → newest, shown per card |
| ✅ **Stat cards** | ledger header | They owe you / You owe them (+ Income/Expense on General) + Net; open-lists info card |
| ✅ **Filters** | ledger | All / Credit (Income) / Debit (Expense) / Unpaid / Overdue |
| ✅ **Statement sharing** | share sheet | WhatsApp (prefilled text to the contact's number) · Share (.txt) · Copy · Export CSV — full statement with line items, running balances and Net |
| ✅ **Balance pills everywhere** | cards, search, keypad, pickers | Green "They owe you" / red "You owe them" with signed compact money |
| ✅ **Owe chips** | Recents + contacts filters | They owe / You owe list filters |
| ✅ **Move entries** | entry card | Re-assign owner (contact ↔ General) with confirm |
| ✅ **Hide balances** | Settings | Blurs every money amount in the app |

## 6. Calculating lists

| Feature | Where | Details |
|---|---|---|
| ✅ **Four list kinds** | new-list picker | Calculating (totals & charges) · Simple · Checklist (group same items) · To-do (priorities) |
| ✅ **Tick-to-pay rows** | list detail | Ticking records a payment of the full remaining and marks paid; untick asks and strips tick payments |
| ✅ **Per-row economics** | row editor | qty × price, per-row discount (% or flat) and tax %, row date, item due date, note, priority (Low/Medium/High) |
| ✅ **Whole-list charges** | totals sheet | List discount (% or flat) · Tax % · Extra charge → Subtotal/Total/Paid/Pending card |
| ✅ **Mark all paid / Clear payments** | ⋯ menu | Adds remaining as tick payments (zero-priced rows just tick) / wipes payments + unticks |
| ✅ **Post to ledger** | list switch | Pending amount counts in the owner's balance (shown in "(Open lists: X)") |
| ✅ **Templates** | templates section | Save-as-template; New button duplicates fresh (payments cleared, unticked) |
| ✅ **Repeats** | list editor | Once/Daily/Weekly/Monthly/Yearly + "Duplicated for next period" button |
| ✅ **Group same items** | list detail switch | Groups rows by name+unit with Σ headers |
| ✅ **Share bill** | footer | WhatsApp (to owner's number) · Share (bill text: ✓/• rows, subtotal, −discount, +tax, +extra, total, paid/pending) · Remind (payment reminder tomorrow 09:00 with pending amount) |
| ✅ **Duplicate / move** | cards + detail | Duplicate (optionally as template/fresh); owner pill moves the list |

## 7. Notes · Reminders · Banks

### Notes
| Feature | Details |
|---|---|
| ✅ Title/body/tags editor | tags comma-separated, `#` stripped, shown as chips (first 4 + `+N`) |
| ✅ Pinned notes | switch in editor; pinned sort first; swipe-left pin/unpin |
| ✅ Color strips | 5-color palette + none; 4 px left-edge strip |
| ✅ Swipe right | Edit (blue) + Delete (confirm + undo) |

### Reminders
| Feature | Details |
|---|---|
| ✅ Kinds | Payment · To-do · Birthday · Follow-up · Custom |
| ✅ Native date/time pickers | Android material pickers (follow the app-wide 12 h AM/PM ↔ 24 h clock choice) — no invalid input possible; quick chips (Today/Tomorrow/Next week; 9 AM/1 PM/6 PM/8 PM); live due preview with remaining time |
| ✅ Live NOW clock + countdown | The editor shows a ticking now-clock beside the due time (with inline 12/24 h toggle); saving toasts the time left and every card carries a live "in 2h 15m" / "3h late" countdown |
| ✅ Repeats | Once/Daily/Weekly/Monthly/Yearly + "Every month on the 1st"; Done advances to next occurrence; KMP v1.6.17: repeats advance AND re-arm from the notification while the app is closed |
| ✅ Lead-time pre-alerts | 1 day / 1 hour before (multi-select), fire once each |
| ✅ Snooze | 10 min · 1 h · 3 h · Tomorrow 9 AM (rows, ⋮ menu, swipe, notification action, alarm dialog) |
| ✅ Real notifications | HIGH channel, sound+vibration, quick actions Done/Snooze 10m (works app-open AND closed via headless task) |
| ✅ Exact alarms | AlarmManager allowWhileIdle first, WorkManager fallback; SCHEDULE_EXACT_ALARM/USE_EXACT_ALARM declared |
| 🔁 Self-heal | Boot +8 s and every foreground return (throttle 5 min): channel ensured, permission asked once, **every alarm the OS dropped is re-armed** |
| ✅ Battery exemption | One-time system dialog at first reminder save (OEM alarm-killer countermeasure) + status visibility |
| ✅ Reboot re-arm | RECEIVE_BOOT_COMPLETED reschedules reminders after device restart |
| ✅ Notification status card | Real OS state; Turn on → runtime ask → deep-links to notification settings after denial |
| ✅ Full-screen alarm dialog | Pulsing bell (cake for birthdays), vibration pattern, kind/title/notes/status, Done/snooze×4/Edit/Close |
| ✅ Status filters + kind filters | Overdue/Today/Upcoming/Done/All (live counts) + 5 kind chips |
| ✅ Pinned header | Stats line + New reminder button always on screen; trailing dashed add row in every list |

### Bank accounts
| Feature | Details |
|---|---|
| ✅ Masked account numbers | `•••• •••• 1234` default; eye toggle reveals; copy button copies full number |
| ✅ Fields | bank name + account number (required), holder, balance, optional owner assignment via contact picker |
| ✅ Share | Preview sheet with full details → system share |
| ✅ Section total | Σ balances in the header when non-zero |

## 8. Calculator

| Feature | Details |
|---|---|
| ✅ Full expression calculator | 4×5 keypad (AC ⌫ % ÷ · 7 8 9 × · 4 5 6 − · 1 2 3 + · ± 0 . =); shunting-yard evaluator (no eval); precedence ×÷ over +−, left-assoc; ÷0 → Error |
| ✅ Live dual display | expression + live result; result formatted (≥1e12 → exponential; >16 chars → 12 sig digits) |
| ✅ Memory keys | M+ / M− / MR / clear; `M = value` subtitle |
| ✅ History | Last 30, newest first, tap to reload expression |
| ✅ Use-value flow | Opens from any amount field; sends the value back and closes |
| ✅ Copy result | Header icon copies the formatted value |
| ✅ Backspace hold-to-repeat | 450 ms delay, then every 110 ms |
| ✅ Input rules | 12 digits/number, one decimal per number, ± flips last number, % evaluates÷100, trailing-operator replacement |

## 9. Data safety & persistence

| Feature | Details |
|---|---|
| 🔁 Mirrored writes | Every AsyncStorage save writes a mirror copy; reads fall back on parse failure |
| 🔁 Shape-sanitized reads | Corrupted blobs degrade to defaults — can never crash boot |
| 🔁 Auto snapshots | Full data set → `Documents/backups/` every 6 h; newest 8 kept |
| 🔁 Android Auto Backup | allowBackup + backup_rules/data_extraction_rules (database, files, sharedpref; cache never leaves) |
| ✅ JSON backup/restore | Export via share sheet; restore with Merge (by-id union, existing wins) or Replace (double-confirmed); PIN excluded by design |
| ✅ Email sync | Daily/weekly/auto: JSON + full vCard attached to a pre-composed email; user presses Send; no accounts/SMTP |
| ✅ Exports | vCard (full names), contacts CSV (Google-compatible), transactions CSV, statements (.txt), bill texts |
| ✅ Wipe | Danger zone: double-confirm wipe of app data (device contacts untouched) |

## 10. Security

| Feature | Details |
|---|---|
| ✅ 4-digit PIN lock | Setup/change/remove flows with shake feedback; auto-submit after 4th digit |
| ✅ Lock screen | Full-screen cover; Forgot PIN? → safe reset (data kept) |
| ✅ Activity-based autolock | Immediately-on-background / 1 min / 5 min inactivity (any touch resets); default 1 min |
| ✅ PIN storage | Salted 2000-iteration SHA-256 hash in Android's `no_backup` dir — never backed up, never restored; removal is permanent |

## 11. Stability & performance

| Feature | Details |
|---|---|
| ✅ Crash firewall | Global JS handler (installed before app eval) — no JS error can kill the process; dedup within 5 s |
| ✅ Crash reporter | Native `last-crash.txt` + boot breadcrumbs (`boot-marks.txt`, 60 lines/8 KB); next-open recovery screen with Copy report |
| ✅ Safe mode | After 3 failed boots: permission prompts/sync paused; clears after a stable 35 s session |
| ✅ Error boundaries | Render errors contained (recovery card / amber banner) |
| ✅ Hydrate bail-out | 5 s timer — the splash can never hang |
| ✅ Zero-lag boot | Splash 140 ms; parallel hydrate (store + call cache + PIN in one set); deferred contacts refresh +4 s, reminder readiness +8 s, snapshot +10 s, email sync +12 s |
| ✅ No-op-safe refreshes | Unchanged data ⇒ no setState, no flicker |
| ✅ Memoized rows | SwipeRow/PhoneInputRow/PinPad memoized; virtualized lists (~15 rows mounted); removeClippedSubviews disabled (boot-crash fix) |
| ✅ Fade sheet animations | Cheap window alpha instead of Android transform layers |

## 12. Internationalization & theming

| Feature | Details |
|---|---|
| ✅ 5 languages | English · አማርኛ · Afaan Oromoo · العربية (RTL) · ትግርኛ — 976 keys each, EN fallback |
| ✅ RTL support | I18nManager allowRTL/forceRTL; layout flips next launch with a restart notice |
| ✅ Localized dates | Month/weekday names + today/yesterday per language |
| ✅ Light/Dark/Auto theme | Full palette incl. status bar + system navigation bar following the resolved theme |
| ✅ Onboarding localization-relevant defaults | Currency ETB + country ET default, 22 country codes, 18+ currencies |

## 13. Platform & delivery

| Feature | Details |
|---|---|
| ✅ Android 6.0+ (minSdk 23, target 34) | Universal APK (4 ABIs), versionCode 21 |
| ✅ iOS parity code | CallKit handoff, Info.plist permissions, Podfile (builds from a Mac) |
| ✅ CI/CD | GitHub Actions workflow (docs copy — token lacks Workflows scope): push to main → signed release APK → GitHub Releases |
| ✅ Offline-first | No network dependency anywhere; "Works offline" pill in the More sheet |
| ℹ️ Web edition | `web/` — Next.js PWA sibling (browser edition of the money features) |

---

*Inventory complete for v1.6.11. Placeholders (⚠️) are the keypad video button only. Hidden-by-design: the Contacts bottom-nav tab (features intact).*
