# Changelog

All notable changes to callmony. Dates follow the git history; the APK for each release is published on the [Releases page](https://github.com/maca14313/callmony/releases).

---

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
