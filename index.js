/**
 * @format
 */

import { AppRegistry } from "react-native";
import { name as appName } from "./app.json";
import { installCrashFirewall } from "./src/boot/crash-firewall";
import SafeRoot from "./src/boot/SafeRoot";

/* v1.5.1 — CRASH FIREWALL (must run before the app's module graph is
   evaluated): replaces React Native's release-fatal global error handler
   so NO js error (module-eval, effect, timer, handler, rejection) can
   ever kill the process again — the "app closes itself on open" bug.
   __DEV__ keeps RN's default behavior (red boxes) for development. */
if (!__DEV__) {
  installCrashFirewall();
}

/* SafeRoot lazy-loads ./App inside a guarded render and shows a recovery
   card (Continue / Reset app data) instead of ever dying at startup. */
AppRegistry.registerComponent(appName, () => SafeRoot);
