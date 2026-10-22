# callmony — React Native (Android + iOS)

Contacts, calls & money ledger for the phone. **100% offline** — all app data
lives on the device. Contacts and the call log come from the phone itself.

> **Repo layout:** this root is the React Native app. The original web app
> (Next.js PWA) lives in [`web/`](web/) — same features, browser edition.

## Highlights

- **Real immediate calls** — tapping a contact (or the keypad's call button)
  places the call **instantly through the phone's default dialer app**: our own
  `ImmediateCall` native module fires Android's `ACTION_CALL` with the
  `CALL_PHONE` permission, so the number is **never dropped into the keypad** —
  no simulation, no extra tap. If the permission was permanently denied we
  fall back to the pre-filled system dialer (one tap to call). On iOS the call
  goes through the native `tel:` handoff (CallKit recents).
- **Opens like a real dialer** — on first launch the app asks for contacts +
  call log + phone (exactly like built-in dialer apps), then loads the whole
  address book and the **real system call log** (our own `SysCallLog` native
  module reads the same `CallLog.Calls` provider the stock dialer uses) before
  you finish reading the splash screen. Contacts and Recents are ready
  instantly, and refresh whenever you return to the app.
- **Instant cold starts (big address books)** — the address book is kept as a
  disk snapshot: opening the app with 5000 contacts is as fast as with 50.
  The OS is only re-queried when the snapshot is older than 5 minutes (or
  right after you edit a contact in-app) — never on every open.
- **Social links built from phone numbers** — WhatsApp · Telegram · Signal ·
  Viber chats open straight from the contact's number (`wa.me`, `t.me/+`,
  `signal.me/#p`, `viber://chat`) — no usernames needed, and any number of
  extra socials can be saved per contact.
- **Auto email sync** — on a schedule (daily / weekly) callmony builds a full
  backup (JSON: ledger, lists, notes, reminders, banks, settings) **plus a
  vCard of every contact** and opens your email app with both attached and
  the recipient pre-filled — you just press Send. No accounts, no SMTP
  credentials, nothing ever leaves the device except that email.
- **Contacts** — the whole address book in a fast searchable list (A–Z
  sections, swipe actions, favorites, speed dial, merge duplicates, per-number
  actions), edited through the native OS editors.
- **Money ledger** — credit / debit / income / expense entries with **line
  items: name × qty (+ unit) × unit price → line total** (e.g.
  `ball 3 pic × 100 = 300`, `orange 4 kg × 200 = 800`, `t-shirt 1 bag × 1000` →
  totals: **8 & 2,100**), editable item lines, payments (amount, date, method,
  note), settle-up, groups (inline create), categories (built-in + custom), due
  dates + auto reminders, running balances, WhatsApp-prefilled statements,
  share (text / copy / CSV), full trash with restore + undo toasts.
- **Calculating lists** — shopping lists with tick-to-pay rows, per-row
  discount/tax/date/note, whole-list discount/tax/extra, templates, repeat.
- **Notes / Reminders / Bank accounts** — local notifications (notifee), lead-time
  pre-alerts, repeats, snooze; masked account numbers with reveal/copy/share.
- **Calculator** — expression display, backspace with hold-to-repeat, memory
  keys, history, "use value" into any amount field.
- **5 languages** — English · አማርኛ (Amharic) · Afaan Oromoo · العربية (RTL) ·
  ትግርኛ (Tigrinya).
- **Security** — 4-digit PIN lock with activity-based autolock.
- **Resilient boot** — the store hydrates with a 5-second bail-out and the app
  runs inside an error boundary, so it can never get stuck on the splash screen.

## Get the APK

Every push to `main` builds a **signed release APK** and publishes it to the
[Releases page](https://github.com/maca14313/callmony/releases) automatically
(`.github/workflows/build-android.yml`). Grab the latest `.apk`, open it on
your phone, and allow "Install from unknown sources" when prompted.

## Build the APK locally

```bash
npm install
cd android && ./gradlew assembleRelease   # → app/build/outputs/apk/release/
# or: npx react-native run-android         # debug install on a connected device
```

Local build notes:
- Requires a **full JDK 17** (must include `jlink` — JRE-style installs fail at
  `notifee:compileReleaseJavaWithJavac`). Set `JAVA_HOME` to it.
- Write `sdk.dir=<path-to-android-sdk>` into `android/local.properties`.
- `gradle.properties` is tuned for machines with 2–4 GB RAM (1280 MB heap,
  in-process Kotlin). With more memory you can raise `org.gradle.jvmargs`.
- The build produces `callmony-v1.4.0-release.apk` (~61 MB, all 4 ABIs).

The release keystore (`android/app/callmony-release.keystore`) is committed for
easy sideloading. For Play Store distribution, generate your own keystore and
wire it through `android/app/keystore.properties` + GitHub secrets.

## iOS

```bash
cd ios && pod install && cd ..
npx react-native run-ios
```

All code is written for both platforms; the iOS build runs locally from a Mac
(Xcode 15+). Permissions are pre-declared in `Info.plist`. (The system call
log is not readable on iOS — Recents shows calls started from callmony there.)

## Web app

The Next.js web edition lives in `web/` (run `cd web && bun install && bun run
dev`); it mirrors the app's money features in the browser.

## Permissions (Android)

| Permission | Why |
|---|---|
| `CALL_PHONE` | start calls directly (ACTION_CALL — instant, via the default dialer app) |
| `READ_CONTACTS` / `WRITE_CONTACTS` | read the address book · merge duplicates |
| `READ_CALL_LOG` | Recents shows the real call history |
| `POST_NOTIFICATIONS` | reminder notifications |
| `VIBRATE` | haptic feedback |

Contacts + call log + phone permissions are requested once, dialer-style, at
first launch (already-granted ones resolve silently); everything else is
requested in-context with an explanation.
