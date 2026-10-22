/* Crash firewall — the app can NEVER close itself from a JS error again.
   In RELEASE React Native routes every uncaught exception (module-eval,
   effect body, timer callback, event handler, unhandled rejection)
   through ErrorUtils' global handler → ExceptionsManager → FATAL native
   crash → Android kills the process ("the app closes itself"). The
   error boundary in App.tsx only catches RENDER errors, so all of those
   other vectors slipped past it. This module replaces the global handler
   BEFORE the app module graph is evaluated: errors are recorded here and
   surfaced as a recoverable screen instead of killing the process.
   In __DEV__ the default behavior is kept (red boxes) for visibility. */

import { AppState } from "react-native";
import AsyncStorage from "@react-native-async-storage/async-storage";

interface CrashState {
  error: Error | null;
  message: string;
  count: number;
  since: number;
}

const STARTED_AT = Date.now();
const BOOT_FAILS_KEY = "callmony.bootFails";

/** true while the app is young — full recovery card only in this window;
 * later errors surface as a dismissible banner instead. */
export const isBootPhase = (): boolean => Date.now() - STARTED_AT < 30000;

let state: CrashState = { error: null, message: "", count: 0, since: 0 };
const listeners = new Set<() => void>();

const notify = (): void => listeners.forEach((l) => { try { l(); } catch { /* listener died — ignore */ } });

const toMessage = (err: unknown): string => {
  if (err instanceof Error) return `${err.name || "Error"}: ${err.message}`;
  try { return String(err); } catch { return "Unknown error"; }
};

export const getCrashState = (): CrashState => state;

export const clearCrash = (): void => {
  state = { error: null, message: "", count: 0, since: 0 };
  notify();
};

export const subscribeCrash = (fn: () => void): (() => void) => {
  listeners.add(fn);
  return () => { listeners.delete(fn); };
};

/* consecutive-boot-failure tracking (persisted) — drives the "Reset data"
   recommendation when the app keeps failing at startup */
export const readBootFails = async (): Promise<number> => {
  try {
    const v = await AsyncStorage.getItem(BOOT_FAILS_KEY);
    return Number(v) || 0;
  } catch { return 0; }
};

const bumpBootFails = async (): Promise<void> => {
  try {
    const next = (await readBootFails()) + 1;
    await AsyncStorage.setItem(BOOT_FAILS_KEY, String(next));
  } catch { /* storage unavailable — in-memory state still works */ }
};

export const resetBootFails = async (): Promise<void> => {
  try { await AsyncStorage.removeItem(BOOT_FAILS_KEY); } catch { /* noop */ }
};

/** Wipe EVERYTHING (AsyncStorage + disk caches) — the recovery hatch.
 *  RNFS is required lazily so this module (imported from index.js) keeps a
 *  zero-risk import chain. */
export const wipeAllAppData = async (): Promise<void> => {
  try { await AsyncStorage.clear(); } catch { /* noop */ }
  try {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const RNFS = require("react-native-fs").default as typeof import("react-native-fs");
    await RNFS.unlink(`${RNFS.DocumentDirectoryPath}/contacts-cache.json`).catch(() => undefined);
    await RNFS.unlink(`${RNFS.DocumentDirectoryPath}/calllog-cache.json`).catch(() => undefined);
  } catch { /* RNFS optional here */ }
  clearCrash();
};

let installed = false;

export const installCrashFirewall = (): void => {
  if (installed) return;
  installed = true;

  const handle = (err: unknown, isFatal?: boolean): void => {
    const msg = toMessage(err);
    const now = Date.now();
    /* the SAME message within 5 s counts once — no render loops */
    const repeated = state.message === msg && now - state.since < 5000;
    state = {
      error: err instanceof Error ? err : new Error(msg),
      message: msg,
      count: repeated ? state.count + 1 : 1,
      since: now,
    };
    if (state.count === 1) {
      /* count boot failures only while the app is young (< 30 s uptime) */
      if (now - STARTED_AT < 30000) void bumpBootFails();
      notify();
    }
    void isFatal; /* recorded, never forwarded — the native process stays alive */
  };

  /* synchronous exceptions + RN-forwarded unhandled rejections all land here */
  const errorUtils = (globalThis as { ErrorUtils?: { setGlobalHandler?: (h: (e: unknown, f?: boolean) => void) => void } }).ErrorUtils;
  if (errorUtils?.setGlobalHandler) {
    errorUtils.setGlobalHandler(handle);
  }

  /* keep an AppState subscription alive so background event dispatch stays
     on a code path our handler already guards */
  AppState.addEventListener("change", () => undefined);
};
