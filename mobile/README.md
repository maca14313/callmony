# callmony — React Native (Android + iOS)

Contacts, calls & money ledger for the phone. **100% offline** — all app data
lives on the device. Contacts and the call log come from the phone itself.

- **Real immediate calls** — `react-native-immediate-phone-call` fires Android's
  `ACTION_CALL` (with the `CALL_PHONE` permission requested at runtime), so the
  native calling system starts instantly — no number dropped into the keypad,
  no simulation. On iOS the same library opens `tel:` and the call registers
  with iOS CallKit; `react-native-callkeep` registers the app as a call-capable
  app at launch.
- **Contacts** — `react-native-contacts` fetches the whole address book on
  launch into a searchable FlatList (A–Z sections, swipe actions, favorites,
  merge-duplicates, per-number actions).
- **Recents** — Android: the real system call log via `react-native-call-log`,
  grouped by day with direction filters. iOS: calls started from the app
  (iOS does not expose the system call log to third-party apps).
- **Money ledger** — credit / debit / income / expense entries with
  **line items: name × qty (+ unit) × unit price → line total** (e.g.
  `ball 3 pic × 100 = 300`, `orange 4 kg × 200 = 800`, `t-shirt 1 bag × 1000` →
  totals: **8 & 2,100**), payments, settle-up, groups, categories (built-in +
  custom), due dates + auto reminders, running balances, share statements
  (text / CSV), full trash with restore.
- **Calculating lists** — shopping lists with tick-to-pay rows, discounts, tax,
  extra charges, templates, repeat.
- **Notes / Reminders / Bank accounts** — with local notifications (notifee),
  repeats, snooze; masked account numbers with reveal/copy/share.
- **Calculator** — expression in the main display, backspace with
  hold-to-repeat, memory keys, history, "use value" into any amount field.
- **5 languages** — English · አማርኛ (Amharic) · Afaan Oromoo · العربية (RTL) ·
  ትግርኛ (Tigrinya).
- **Security** — 4-digit PIN lock with autolock (data stays on-device).

## Build the APK

GitHub Actions builds a **signed release APK** on every push to `main`
(`.github/workflows/build-android.yml`) — download the `callmony-release-apk`
artifact from the run page and sideload it.

Locally:

```bash
cd mobile
npm install
cd android && ./gradlew assembleRelease   # → app/build/outputs/apk/release/
# or: npx react-native run-android         # debug install on a connected device
```

The release keystore (`android/app/callmony-release.keystore`) is committed for
easy sideloading. For Play Store distribution, generate your own keystore and
wire it through `android/app/keystore.properties` + GitHub secrets.

## iOS

```bash
cd mobile/ios && pod install && cd ..
npx react-native run-ios
```

All code is written for both platforms; the iOS build runs locally from a Mac
(Xcode 15+). Permissions are pre-declared in `Info.plist`.

## Permissions (Android)

| Permission | Why |
|---|---|
| `CALL_PHONE` | start calls directly (ACTION_CALL) |
| `READ_CONTACTS` / `WRITE_CONTACTS` | read the address book · merge duplicates |
| `READ_CALL_LOG` | Recents shows the real call history |
| `POST_NOTIFICATIONS` | reminder notifications |
| `VIBRATE` | haptic feedback |

All runtime permissions are requested in-context with an explanation.

## Enable the CI APK build (one-time, ~2 minutes)

The repo's fine-grained access token doesn't carry the **Workflows**
permission, so `.github/workflows/build-android.yml` couldn't be committed at
the repo root by automation (a copy ships at
`mobile/.github/workflows/build-android.yml`). Enable CI yourself, in the
browser — no token needed:

1. Open https://github.com/maca14313/callmony/new/main
2. Set the file name to exactly:
   `.github/workflows/build-android.yml`
3. Open `mobile/.github/workflows/build-android.yml` in the repo
   (raw view), copy **all** of it, paste it into the new file.
4. Click **Commit changes**.

From then on, every push to `main` (or a manual **Run workflow**) builds the
signed APK — grab it from the run's **Artifacts → callmony-release-apk**.
