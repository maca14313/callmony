# Changelog

All notable changes to callmony. Dates follow the git history; the APK for each release is published on the [Releases page](https://github.com/maca14313/callmony/releases).

---

# v1.6.22 — KMP: the freeze decoded for real — awaitEachGesture busy-spin fixed (2026-10-26)

## Why

"still the same, still crushing, freezing" — the v1.6.21 app sent its own
diagnostics report, and this time the capture was complete enough to name
the true root cause:

```
kind: freeze   phase: ui.main
problem: Main thread blocked — no progress for 5003 ms

Thread: main (main, RUNNABLE)
  at AtomicReferenceFieldUpdater…get
  at kotlinx.coroutines.JobSupport.isActive
  at ForEachGestureKt$awaitEachGesture$2.invokeSuspend(ForEachGesture.kt:102)
  … resumed from offerPointerEvent ← touch dispatch ← looper
```

Three facts, one conclusion: the main thread was **RUNNABLE** (not waiting
on any lock — and the full thread dump showed every other thread parked,
so no lock holder existed), it was parked at **the `while (isActive)` loop
condition inside `awaitEachGesture` itself**, and it had made **zero
progress for 5 seconds**. A thread that is "running" a plain atomic read
for 5 seconds is a thread executing the same loop over and over: a
**busy-spin**.

## What was actually wrong (since v1.6.17)

The autolock touch observer on the root Box of the main UI — present on
every screen:

```kotlin
.pointerInput(Unit) {
    awaitEachGesture {
        lastActive.v = now()   // ← returns without awaiting ANY event
    }
}
```

`awaitEachGesture` is `awaitPointerEventScope { while (isActive) {
block() } }` and its contract REQUIRES the block to await at least one
pointer event. This block wrote a timestamp and returned — so from the
**first touch on the app** the loop spun the main thread at 100% CPU
inside the touch dispatch, forever: our watchdog fires at 5 s, Android's
input-dispatch ANR fires at ~5 s ("Callmony isn't responding" → Close app
— the reported "crushing"), and the :diag-process diagnostics screen pops
with the report.

This also retro-explains the entire freeze saga:

- **v1.6.20's report** (BLOCKED in `SnapshotKt.readable` while writing
  `lastActive` state) was the SAME spin — with a `mutableStateOf` write
  every lap, which is what collided with the snapshot system.
- **v1.6.21's fix** (plain-field `lastActive` + the single-writer snapshot
  rule) removed the snapshot collision — genuinely — but the spin itself
  kept spinning; the report just changed shape (RUNNABLE at the loop
  condition instead of BLOCKED at a state write).
- The Robolectric boot harnesses never caught it because no touch events
  are ever dispatched in the harness: the handler coroutine stays parked
  at its first await and the spin never starts.

## The fix

The observer now **awaits the down FIRST**, then refreshes the idle timer
per event until all pointers release:

```kotlin
.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)   // suspends — the loop parks between gestures
        lastActive.v = now()
        while (true) {
            val event = awaitPointerEvent()
            lastActive.v = now()
            if (event.changes.all { !it.pressed }) break
        }
    }
}
```

Still a pure observer — `requireUnconsumed = false` and nothing consumed —
so children receive every tap, swipe and child gesture exactly as before.
Every other `pointerInput` in the app was audited (SwipeRow ×3, KeypadSheet
long-press, Calculator hold-to-repeat): all use `detectTapGestures` /
`detectHorizontalDragGestures`, framework detectors that always suspend.

## Diagnostics hardened

`captureFreezeReport` now embeds a **3-sample main-thread spin probe**
(three stacks, 200 ms apart): identical stacks → hard block; the same top
frames wandering → spin/livelock; different stacks each sample →
saturation. The v1.6.21 report's single sample made a busy-spin look like
an innocent transient ("RUNNABLE at an atomic get"); three samples settle
it at a glance.

## Verification

- BootDiagTest, UpgradeBootDiagTest, StartupTest, DiagTest + the pure
  battery green with the fix.
- The 5 Recents Robolectric timeouts (RecentsScrollTest ×2,
  RecentsV1615Test ×3 — "Condition still not satisfied after 20000 ms"
  waiting for `hydrated`) reproduce identically on **unmodified v1.6.21
  code** (A/B via `git stash`) — sandbox-load flakiness on the 4 GB box,
  not a regression.
- `compileKotlinIosArm64` + `compileKotlinIosSimulatorArm64` clean (the
  fix is in commonMain).
- Release APK verified: `com.callmony 1.6.22 versionCode 32`, minSdk 23,
  targetSdk 34 — published as
  [v1.6.22](https://github.com/maca14313/callmony/releases/tag/v1.6.22).

---

# v1.6.21 — KMP: the first diagnostics report named the freeze — fixed at the exact stack (single-writer snapshot rule) (2026-10-26)

## Why

v1.6.20's diagnostics did their job: the user sent the freeze report and it
named the problem precisely instead of leaving us guessing.

```
kind: freeze   phase: ui.main
problem: Main thread blocked — no progress for 5991 ms
Thread: main
  at androidx.compose.runtime.snapshots.SnapshotKt.readable(...)
  at androidx.compose.runtime.snapshots.SnapshotKt.current(...)
  at androidx.compose.runtime.SnapshotMutableStateImpl.setValue(...)
  at com.callmony.ui.CallmonyAppKt.CallmonyApp$lambda$10(CallmonyApp.kt:1978)
  at com.callmony.ui.CallmonyAppKt$CallmonyApp$10$1$1.invokeSuspend(CallmonyApp.kt:296)
  at androidx.compose.foundation.gestures.ForEachGestureKt$awaitEachGesture$2...
deviceContacts=1239 systemCalls=400 hydrated=true onboarded=false
```

Line 296 is the **autolock touch observer** — `awaitEachGesture { lastActive
= now() }` — and `lastActive` was Compose `mutableStateOf` state, so **every
touch on the whole app** entered the Compose snapshot system. The stack shows
that write blocked for ~6 s inside `Snapshot.current()` waiting on the
snapshot machinery while background `Dispatchers.Default` workers were
publishing store state across threads (hydrate's 16 assignments, the
1239-contact list, the 400-call sync — all direct snapshot writes from
worker threads on a low-end Samsung A12). Main stalled past Android's 5 s
input-dispatch line → "Callmony isn't responding" → the app closes itself.

## What was fixed

1. **The per-touch snapshot write is gone** (`CallmonyApp.kt`). Autolock
   never needs recomposition — only the 10 s idle poller reads the
   timestamp — so `lastActive` is now a plain field on a remembered `TimeRef`
   (gesture observer and poller both run on the main dispatcher, single
   thread, zero snapshot traffic per touch). The exact frozen frame is now
   physically impossible.
2. **The single-writer snapshot rule** (`Store.kt`, `KeypadIndex.kt`,
   `MiscOverlays.kt`, `RecentsScreen.kt`, `NotifyActuals.kt`): no background
   thread reads or writes Compose snapshot state anymore. Heavy work (JSON
   hydration, address-book cursors, call-log cursors, T9 index build,
   contacts binder edits) stays on `Dispatchers.Default`; every state
   mutation publishes through ONE batched `withContext(Dispatchers.Main)`
   hop — hydrate lands as a single recomposition, the 1239-contact publish
   can no longer contend with a touch, and the call-log sync decides what to
   fetch on main, fetches on the worker, publishes back on main.
   `AppStore.publish { }` covers non-suspend callbacks (permission results);
   the boot alarm re-arm captures its reminder list + localized labels on
   main (`remindersForRearm()`) and the worker hop is binder-only; the
   notification-action receivers read the live flag in `onReceive` (main)
   and post the live store update back to main.
3. **Diagnostics upgraded with the same lesson** (`DiagAndroid.kt`): the
   freeze report only carried the 6 deepest-stacked threads × 4 frames, and
   the snapshot-lock holder was among the missing — freeze captures now dump
   **every** thread, RUNNABLE/BLOCKED first (a lock holder is never idle),
   16 frames each, with the main thread's state printed too.

## Verification

- Pure battery 55/55 (Diag 9, V1618 16, ReminderV1617 9, SearchContacts 12,
  CalcFormat 5, KmpDate 4).
- `BootDiagTest` (fresh install) GREEN — boot reaches `ui.idle` in ~23 s
  through the new main-dispatcher publish path.
- `UpgradeBootDiagTest` (onboarded + 400 cached calls + 5 reminders) GREEN.
- Android + both iOS targets (iosArm64, iosSimulatorArm64) compile clean.
- Release APK verified: `com.callmony 1.6.21 versionCode 31`, minSdk 23,
  targetSdk 34, signed CN=Callmony ET.

---

# v1.6.20 — KMP: the app now SHOWS the problem when it freezes or crashes (in-app freeze/crash diagnostics) (2026-10-26)

## Why

The v1.6.19 root-cause fix was verified in the boot harnesses (fresh-install
boot went from "hangs forever" to 23 s), but the reporting device still froze
at the first welcome screen and closed itself. Rather than guess at a cause we
cannot reproduce in the sandbox, the app now diagnoses itself and **shows the
problem on screen the moment it is trying to close**.

## What was built

- **ANR watchdog** (`androidMain …/lib/DiagAndroid.kt`, pure accounting in
  `commonMain lib/Diag.kt`): a daemon thread posts a heartbeat to the main
  handler every second and catches BOTH freeze shapes —
  *hard blockage* (the heartbeat doesn't run for 5 s: main is stuck inside
  one message — binder call, deadlock, tight loop) and *saturation* (the
  heartbeat runs but ≥ 4 s late: main returns to the looper yet every slot
  is eaten — the v1.6.18 forever-spinner class). On detection it captures
  the main-thread stack, the boot phase and a full app-state snapshot.
- **Diagnostics screen in its own `:diag` process** (`diag/DiagActivity`):
  painted with plain Views (no Compose, no app runtime) so it comes up even
  while the main process is frozen solid or dying — it pops OVER the frozen
  app with the headline, the exact phase (`ui.onboarding`, `store.read`, …),
  the main-thread stack and a monospace report card, plus **Copy report** /
  **Reopen app** / **Close**. Labels reuse the app's 5 i18n catalogs.
- **Crash firewall**: a chained uncaught-exception handler saves the same
  kind of report (exception + main-thread stack + phase + state), shows the
  diagnostics screen, then hands over to the platform handler so the process
  still dies cleanly.
- **In-app overlay** (`ui/overlays/DiagnosticsOverlay.kt`): a live freeze
  caught this session ("The app just froze — here's what was blocking it")
  and any unseen report from the previous run ("Callmony froze and was
  closed last time") render ABOVE everything, even the lock screen, with
  Copy / Got it. If the system ANR-kills the app anyway, the report persists
  (own `filesDir/diag` store — deliberately outside the app's Persist queue
  so a frozen app can't deadlock its own diagnostics) and shows at next
  launch. Settings gained a **Diagnostics** section listing every stored
  report (expandable, copyable, clearable) with a red badge for unseen ones.
- **Boot-phase tracker**: every milestone from `app.init` through
  `store.read`, `ui.splash`, `ui.onboarding` to `ui.idle` is stamped, so a
  captured report answers "WHERE was it" with an exact machine-observed
  phase — "it freezes at the welcome screen" becomes `phase: ui.onboarding`
  plus the stack that was blocking.
- Reports are capped at 12, thread-safe, and the watchdog only runs while
  the app is on screen; the `:diag` process and Robolectric unit tests skip
  the whole machinery (tests keep the exact pre-v1.6.20 behavior).
- i18n: 20 new `diag.*` keys across all 5 languages (1045/1045 parity, 0
  missing). `BootDiagTest` now also asserts the boot phase advanced past the
  splash; new `DiagTest` (8 tests) covers the detector accounting
  (hard-blockage, saturation, cooldown) and the record/seen/report flow.
  Pure battery: 54/54 green. Android + both iOS targets compile.

Android release APK 1.6.20 (versionCode 30) built + aapt/apksigner verified.

---

# v1.6.19 — KMP: the welcome-screen freeze root-caused (infinite boot spinner) + centered empty states (2026-10-26)

## The two v1.6.18 follow-up reports

1. **"The app starts freezing at the first welcome screen, then closes
   itself — 'Callmony isn't responding'."** Root cause (found with a thread
   -dump harness): whenever the call-log permission was `UNKNOWN` and no
   calls were cached, the Recents tab rendered an indeterminate
   `CircularProgressIndicator` — a Compose `InfiniteTransition`, i.e. an
   endless Choreographer frame loop. On a fresh install that spinner
   composed from the very first frame and spun INVISIBLY UNDER the opaque
   onboarding sheet, never stopping: the main thread was permanently busy
   producing frames, input dispatch starved on the user's device → the ANR
   dialog → the app "closes itself". The same loop left the Robolectric
   boot harness unable to finish `ActivityScenario.launch()` at all — the
   v1.6.18 round's "killed at 570 s while booting" was this bug, not
   sandbox slowness. Fix:
   - new `AppState.callLogBusy` — true only while a call-log ask or fetch is
     actually in flight (set/cleared in a `try/finally` around the whole
     `loadSystemCalls` coroutine, including the early-return paths);
   - the loading spinner renders only when `callLogBusy` is true AND the
     permission is still `UNKNOWN` AND the screen is actually visible
     (onboarding completed) — an unresolved permission falls through to the
     no-calls `EmptyState`, which never animates;
   - boot-time notification-channel creation + alarm re-arming
     (`ensureChannel` / `rearmReminders`) moved off the main thread onto
     `Dispatchers.Default` (binder calls to system services on low-end
     devices janked boot);
   - two permanent regression harnesses: `BootDiagTest` (fresh-install boot
     must idle: 23 s green, previously hung forever) and
     `UpgradeBootDiagTest` (onboarded boot seeded with 400 cached calls,
     entries, repeating/snoozed/done reminders).
2. **"Some components sit at the left top but should be centered — e.g. the
   'No matching contacts' empty state (mint circle, history icon)."** The
   shared `EmptyState` composable was a bare padded `Column`, so inside any
   sized content area it started at the top-left corner. It now fills its
   container and centers (`fillMaxSize` + `wrapContentSize(Center)`), which
   fixes Recents, Reminders, Trash, Notes, Banks, the contact picker and the
   call-log denied card; inside scrollables (LazyColumn items, vertical
   scroll columns) the height constraint is unbounded, so `fillMaxSize`
   falls back to wrap-content and list empty states keep their natural
   in-flow placement, centering horizontally only — the RN behavior in both
   contexts.

Verification: 46/46 pure unit tests green (V1618 16, ReminderV1617 9,
SearchContacts 12, CalcFormat 5, KmpDate 4), both boot harnesses green,
Android release APK 1.6.19 (versionCode 29) built + aapt/apksigner verified,
both iOS targets compile.

---

# v1.6.18 — KMP: the freeze/ANR fix (async persistence), any-interval repeat reminders + alarm mode, one-tap entry editing, Record payment, attachments, block contacts, advanced search, rebuilt contact detail (2026-10-26)

## The seventeen user reports

1. **"The app is freezing, lagging and closing itself — what did you do?"**
   Root cause: every store mutation (toggle favourite, save an entry, mark a
   reminder, patch a contact…) ran its ENTIRE persistence inline on the MAIN
   thread — full-collection JSON encode plus a synchronous SharedPreferences
   `commit()` writing the value TWICE (row + safety mirror). The contact cache
   re-serialized the whole address book on main-thread contact edits. Stacked
   commits crossed Android's 5-second input-dispatch limit → ANR → the system
   closes the app. Fix: `lib/Persist.kt` — a single-writer FIFO queue on
   Dispatchers.IO (`persistDispatcher` expect/actual, IO on Android / Default on
   iOS). `Db.save*`, the settings save, `wipeStorage`, the contacts cache and
   the calls cache all post through it. Receivers wrap load-modify-save in
   `Persist.postAndWait` blocks, ordered with every UI write. Unit tests keep
   synchronous behavior (the queue flips to async only from the real app entry
   points). The autolock default changed 1 min → **Never** (-1, + a Never chip)
   because a lock dropping in while the user reads the screen read as "the app
   closed itself".
2. **Repeat reminders at ANY interval + alarm** — `repeat` now understands
   `custom:<n><m|h|d|w|mo>`; `parseCustomRepeat` + `nextOccurrence` handle every
   unit (minutes/hours are pure epoch arithmetic; months clamp short months);
   the editor gained a Custom chip with a number + unit row and a live
   "Every 2 weeks" summary; reminder cards show a human repeat label.
   **Alarm** reminders (`Reminder.isAlarm`) fire on a dedicated
   `callmony-reminder-alarms` channel on the ALARM audio stream (default alarm
   sound, USAGE_ALARM — audible over silent / muted media), carry the alarm icon
   + CATEGORY_ALARM, and ride through every receiver/reschedule path.
3. **"Why do I have to click two save buttons when editing a credit/debit?"**
   Tapping the entry row now opens the editor DIRECTLY (was row-tap → expand →
   Edit button). The chevron alone expands the detail (payments history). The
   always-open item form read as a second Save: for existing entries without
   items it starts collapsed behind "＋ Add items", and its button reads
   "Save item" instead of "Save".
4. **"Add payment is not working and its name is confusing"** — renamed
   **Record payment** (+ a hint line + the entry amount), hoisted to a
   root-level host sheet: it used to be composed INSIDE the scrollable card,
   where unbounded height constraints made it pop up at the top / float
   mid-scroll (which read as "not working"). The date became the native
   calendar picker (was a hand-typed YYYY-MM-DD field — typos produced garbage
   dates). Payment-method chips are translated; Fill-remaining yields clean
   numbers ("500", not "500.0").
5. **Single-entries tabs ＋** — the "Single entries" header carries a ＋ that
   creates an entry of the KIND the active tab shows (Income/Expense on
   General, Credit/Debit on a contact, plain new entry for All/Unpaid/Overdue).
6. **Attachments** — lists / notes / reminders ride on any credit/debit/expense/
   income via `entry.refs` (LinkRef). Editor: attached chips with detach ✕ + a
   "＋ Attach" picker sheet (Lists/Notes/Reminders tabs, tap to toggle). The
   entry detail shows them as tappable chips that open the entity.
7. **Settle up removed** for now (chip gone; `AppStore.settleUp` kept for API
   compatibility, unsurfaced).
8. **"Some modals open at the top"** — new root-level `HostSheets` slot in the
   app shell (`ui/kit/Kit.kt` + `HostSheetsHost()` beside Toaster/ConfirmHost):
   sheets that were composed inside scrollables (Record payment, list row
   payments, group menus…) render through it, where the host Box is the
   full-size app root — bottom-center placement is guaranteed.
9. **Block contacts** — the block list existed in the store model
   (`settings.blockedNumbers`, `Calls.isBlocked`) but was never surfaced. The
   More sheet now has Block/Unblock with a hint line; blocked contacts carry a
   red Blocked badge on the detail navbar; calls from the keypad/detail/search
   show the blocked notice.
10. **Search** — the header entry is a solid brand pill with a white magnify
    (was a ghost-grey icon that vanished against the wordmark). The overlay
    gained advanced filter tabs (All/Contacts/Entries/Lists/Notes/Reminders/
    Banks) with live result counts, gating each result group.
11. **Contact detail More** — "＋ Add to contact" became a More sheet: a
    sideways-scrolling social strip with LABELED tiles (every number's
    WhatsApp/Telegram + saved socials + add), the add-to options (add phone /
    add email / add social), the general actions, and Block/Unblock.
12. **Number rows carry the action icon** — the call/message chooser rows show
    a call icon (message icon for SMS, WhatsApp icon for WhatsApp) next to
    copy, so the list reads like a row of dial buttons.
13. **Section separation** — the tab body sits on its own soft card with a
    hairline + generous spacing; every InfoBlock is a soft outlined card. The
    page read as one flat slab before.
14. **Navbar number** — tap = place the call (a brand call-icon rides beside
    the number, `Sheet.onSubtitleTap`), long-press = copy (as before).

## Also in this round

- 12 new i18n keys × 5 languages (st.never, rm.customInterval, rm.everyN,
  rm.unit*, rm.alarm*, lg.recordPayment, lg.paymentHint, lg.saveItem,
  lg.addItems, lg.pm*, cd.more, cd.addTo, cd.moreSocial, cd.blockContact,
  cd.unblockContact, cd.blockedHint, ee.noAttachments, ls.untitled,
  sr.filterAll) — `check_i18n_precise.py` 1025 defined / 0 missing.
- Tests: **46/46 green** (V1618 ×16 new — custom repeat parse/nextOccurrence
  for every unit, month clamping, repeat labels, isAlarm serialization
  round-trip, autolock Never default, Persist sync-mode inline+flush;
  ReminderV1617 ×9, SearchContacts ×12, CalcFormat ×5, KmpDate ×4). The
  Robolectric full-boot smoke does not fit the 10-minute window of this
  degraded sandbox (same as v1.6.17); compile + pure battery + verified
  release build cover the boot path this round.
- Android + both iOS targets compile clean; release APK 1.6.18 vc28 built,
  aapt + apksigner verified, committed under `releases/` and attached to the
  GitHub Release.
- `sandbox/` (worklog, chat logs, SDK installer) is committed from now on so
  nothing is lost if the sandbox or the chat ever disappears.

## v1.6.17 — KMP: working reminders with live countdowns, centered sheets, a real PIN, compact tabs/buttons, i18n key leaks fixed (2026-10-25)

User-reported round seven on the KMP edition:

1. **Reminders fire, validate honestly, show the time left** —
   - EDITING a reminder seeded `dueDate` with the DISPLAY format
     (`fdEpoch` → "Oct 25, 2026") instead of the machine format the parser
     expects, so Save always refused with "enter a valid date and time" —
     the editor now seeds `dateStr()` (yyyy-MM-dd) and the parse round-trips.
   - A live NOW clock ticks beside the due-time field (one glance answers
     "how far away is 14:30?"), with an inline 12 h ↔ 24 h toggle; the
     setting lives in Settings → Region and drives EVERY rendered time
     (`ft()`), the due-time field ("9:00 AM" vs "09:00") and the native
     time picker's mode.
   - Saving a future reminder toasts the exact time left ("Saved · in 2h
     15m"); every reminder card carries a live countdown ("in 45m", green /
     "3h late", red, ticking every 30 s).
   - Repeating reminders advance AND re-arm while the app is CLOSED —
     `AlarmReceiver` now runs the next-occurrence surgery on the persisted
     store in a `goAsync` thread and schedules the next alarm (the RN
     edition got this from notifee's repeatFrequency; AlarmManager alarms
     are one-shot, so the second occurrence of a daily reminder never fired
     before this).
   - Closing the in-app alarm dialog now resolves the reminder like the RN
     edition: repeats advance to the next occurrence, one-shots are marked
     done (they used to linger overdue forever).
   - The in-app watcher only pops alarms that JUST became due (45 s window)
     and each due moment fires exactly once (`markReminderFired` persists
     `lastFiredAt` / fired leads); stale overdue reminders no longer ambush
     the user on every launch.
   - The notification permission is requested when a reminder is SAVED
     (RN v1.6.2 parity), not only from the Reminders tab status card.
2. **Bottom sheets are bottom-centered** — `BottomSheet` carries its own
   full-size scrim and pins its content to the BOTTOM-CENTER of the host
   (it used to render as a bare column at TOP-START, which is why the PIN
   keypad and several long-press menus hugged the top-left corner). The
   PIN flow column also fills the width so the dots + pad truly center.
3. **The PIN works** — a fresh launch (or process restore) with a PIN set
   now opens on the lock screen: nothing ever set `UiStore.locked` before,
   so the PIN never guarded anything until the autolock timer happened to
   fire. Autolock tracks REAL touches (an observe-only `awaitEachGesture`
   refreshes the idle timer; the 1/5-minute options used to lock mid-use)
   and "immediately" locks when the app actually leaves the foreground
   (`onResume`/`onPause` drive `appInForeground`).
4. **Tabs: no overlap, compact everywhere** — every `Segmented` strip
   carries its own 5 dp vertical padding so it can never sit flush against
   the container above or below it (Reminder due-date row, Ledger strips),
   and the reminder editor's due date/time became `PickerField`s with the
   exact "label above + 48 dp box" metrics of the Field beside them.
5. **No more raw i18n keys** — `act.saveContact`, `act.confirm`,
   `act.saved`, `cd.setPrimary`, `lg.giveItemName`, `misc.contacts`,
   `spd.speedDialSet/Cleared`, `rm.repeatNone` are now defined in ALL FIVE
   languages (a precise scanner verified every `t()` key has a definition
   and every language reaches EN parity at 993/993 keys).
6. **UI alignment + no ugly wraps** — side-by-side inputs share identical
   metrics (the ledger date box beside the method field, the reminder
   date/time pair), and long text/numbers ellipsize on one line (buttons,
   money, the reminder card meta row) instead of wrapping in tight spaces.
7. **Compact buttons** — Btn paddings/heights tightened (44/34 dp minimums,
   15/13 sp labels) with single-line ellipsized labels.

Tests: +9 (`ReminderV1617Test` — the dateStr round-trip that fixes the
"valid date and time" false positive, spanStr/timeLeftStr formats, 12/24 h
`ft()`, next-occurrence advance, `{left}` placeholder filling, model
fields) → pure battery 30/30 green; the full-app Robolectric boot smoke
(`StartupTest.appBootsWithoutCrashing` + logo decode) verified green
through the new shell; iOS arm64 + simulator compile clean.

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
