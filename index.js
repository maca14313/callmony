/**
 * @format
 */

import { AppRegistry } from "react-native";
import { name as appName } from "./app.json";
import { installCrashFirewall } from "./src/boot/crash-firewall";
import SafeRoot from "./src/boot/SafeRoot";
import notifee from "@notifee/react-native";

/* v1.5.1 — CRASH FIREWALL (must run before the app's module graph is
   evaluated): replaces React Native's release-fatal global error handler
   so NO js error (module-eval, effect, timer, handler, rejection) can
   ever kill the process again — the "app closes itself on open" bug.
   __DEV__ keeps RN's default behavior (red boxes) for development. */
if (!__DEV__) {
  installCrashFirewall();
}

/* v1.6.2 — reminder notification ACTIONS while the app is CLOSED: notifee
   wakes this headless task when the user taps "Done" / "Snooze 10m" on a
   reminder notification. The handler (and the store it needs) is required
   lazily so the headless path never slows down normal startup. */
notifee.onBackgroundEvent(async (event) => {
  try {
    const { handleNotifeeEvent } = require("./src/native/notify");
    await handleNotifeeEvent(event);
  } catch {
    /* never crash the headless task */
  }
});

/* SafeRoot lazy-loads ./App inside a guarded render and shows a recovery
   card (Continue / Reset app data) instead of ever dying at startup. */
AppRegistry.registerComponent(appName, () => SafeRoot);
